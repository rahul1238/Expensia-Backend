package com.expensia.backend.service.gmail;

import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.model.GmailCredential;
import com.expensia.backend.repository.EmailTransactionRepository;
import com.expensia.backend.repository.GmailCredentialRepository;
import com.expensia.backend.utils.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;

import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailSyncService {

    private final GmailCredentialRepository credentialRepository;
    private final EmailTransactionRepository emailTransactionRepository;
    private final GmailParsingService parsingService;
    private final AuthUser authUser;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    public List<EmailTransaction> syncForCurrentUser() throws Exception {
        String userId = authUser.getCurrentUserId();
        GmailCredential cred = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Gmail not connected"));

        // 1) Exchange refresh token for access token
        var tokenResponse = new GoogleRefreshTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                cred.getRefreshToken(),
                clientId,
                clientSecret
        ).setGrantType("refresh_token").execute();

        String accessToken = tokenResponse.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Unable to obtain access token");
        }

        String query = "subject:(transaction OR payment OR credited OR debited OR receipt OR invoice) OR " +
                "from:(no-reply@amazon.in OR alerts@hdfcbank.net OR icicibank.com OR axisbank.com OR flipkart.com OR paypal.com OR noreply@phonepe.com OR alerts@sbi.co.in)";
        URI listUri = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=" + urlEncode(query) + "&maxResults=50");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> listResp = restTemplate.exchange(listUri, HttpMethod.GET, entity, String.class);
        if (!listResp.getStatusCode().is2xxSuccessful() || listResp.getBody() == null) {
            throw new RuntimeException("Gmail list API failed");
        }

        JsonNode root = objectMapper.readTree(listResp.getBody());
        JsonNode messages = root.get("messages");
        if (messages == null || !messages.isArray()) {
            return Collections.emptyList();
        }

        List<EmailTransaction> saved = new ArrayList<>();
        for (JsonNode node : messages) {
            String messageId = node.get("id").asText();
            if (emailTransactionRepository.findByUserIdAndMessageId(userId, messageId).isPresent()) {
                continue; // skip duplicates
            }

            URI getUri = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/" + messageId + "?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date");
            ResponseEntity<String> getResp = restTemplate.exchange(getUri, HttpMethod.GET, entity, String.class);
            if (!getResp.getStatusCode().is2xxSuccessful() || getResp.getBody() == null) {
                continue;
            }
            JsonNode msg = objectMapper.readTree(getResp.getBody());
            String from = header(msg, "From");
            String subject = header(msg, "Subject");
            String snippet = Optional.ofNullable(msg.get("snippet")).map(JsonNode::asText).orElse("");

            var parsed = parsingService.parse(userId, messageId, extractEmailAddress(from), subject, snippet);
            parsed.ifPresent(tx -> {
                emailTransactionRepository.save(tx);
                saved.add(tx);
            });
        }

        return saved;
    }

    public List<EmailTransaction> listForCurrentUser() {
        String userId = authUser.getCurrentUserId();
        return emailTransactionRepository.findByUserId(userId);
    }

    public GmailCredential getOrCreateCredentialForCurrentUser() {
        String userId = authUser.getCurrentUserId();
        return credentialRepository.findByUserId(userId)
                .orElse(GmailCredential.builder().userId(userId).build());
    }

    public GmailCredential saveCredential(GmailCredential credential) {
        return credentialRepository.save(credential);
    }

    public void deleteCredentialForCurrentUser() {
        String userId = authUser.getCurrentUserId();
        credentialRepository.deleteByUserId(userId);
    }

    public void revokeRefreshToken(String refreshToken) {
        try {
            var uri = URI.create("https://oauth2.googleapis.com/revoke?token=" + urlEncode(refreshToken));
            restTemplate.postForEntity(uri, null, String.class);
        } catch (Exception e) {
            log.warn("Failed to revoke Google token: {}", e.getMessage());
        }
    }

    private String header(JsonNode msg, String name) {
        JsonNode payload = msg.get("payload");
        if (payload == null) return null;
        JsonNode headers = payload.get("headers");
        if (headers == null || !headers.isArray()) return null;
        for (JsonNode h : headers) {
            if (name.equalsIgnoreCase(h.get("name").asText())) {
                return h.get("value").asText();
            }
        }
        return null;
    }

    private String extractEmailAddress(String from) {
        if (from == null) return null;
        int lt = from.indexOf('<');
        int gt = from.indexOf('>');
        if (lt >= 0 && gt > lt) {
            return from.substring(lt + 1, gt);
        }
        return from;
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
