package com.expensia.backend.service.gmail;

import com.expensia.backend.config.BankDomainConfig;
import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.model.GmailCredential;
import com.expensia.backend.repository.EmailTransactionRepository;
import com.expensia.backend.repository.GmailCredentialRepository;
import com.expensia.backend.service.ai.TransactionAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedGmailSyncService {

    private final GmailCredentialRepository credentialRepository;
    private final EmailTransactionRepository emailTransactionRepository;
    private final TransactionAIService transactionAIService;
    private final BankDomainConfig bankDomainConfig;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Data
    @Builder
    public static class SyncResult {
        private boolean success;
        private int processed;
        private int added;
        private int skipped;
        private String error;
        private List<String> details;
    }

    public SyncResult syncForUser(String userId) {
        log.info("Starting enhanced Gmail sync for user: {}", userId);
        OAuthToken token = getOAuthToken(userId);
        if (token == null) {
            log.warn("No OAuth token found for user: {}", userId);
            return SyncResult.builder().success(false).error("No OAuth token found").build();
        }

        try {
            // Build query with incremental constraints
            String query = buildIncrementalQuery(userId);
            log.info("Gmail incremental search query: {}", query);

            // Fetch messages with pagination
            List<String> messageIds = fetchAllMessageIds(token.accessToken, query);
            log.info("Found {} candidate emails for user: {}", messageIds.size(), userId);

            SyncResult result = processMessages(userId, token.accessToken, messageIds);
            // Update last synced markers if we processed any
            updateLastSynced(userId, token.accessToken, messageIds);
            log.info("Enhanced sync completed for user {}: {} processed, {} added, {} skipped", 
                     userId, result.processed, result.added, result.skipped);
            return result;
        } catch (Exception e) {
            log.error("Enhanced Gmail sync failed for user {}: {}", userId, e.getMessage(), e);
            return SyncResult.builder().success(false).error(e.getMessage()).build();
        }
    }

    private String buildIncrementalQuery(String userId) {
    // Include bank domains to improve precision
        Set<String> domains = bankDomainConfig.getDomains();
        String domainClause = domains.isEmpty() ? "" : " from:(" + String.join(" OR ", domains) + ")";

        String keywords = "subject:(transaction OR payment OR credited OR debited OR alert OR receipt OR invoice OR " +
                "spent OR withdrawn OR transfer OR deposit OR refund OR purchase OR bill OR emi OR " +
                "upi OR imps OR neft OR rtgs OR wallet OR autopay OR cashback OR reward)" + domainClause;

        // Apply after: filter using lastSyncedInternalDateMs
        long afterMs = Optional.ofNullable(credentialRepository.findByUserId(userId).orElse(null))
                .map(GmailCredential::getLastSyncedInternalDateMs)
                .orElse(0L);

        String afterClause;
        if (afterMs > 0) {
            LocalDate afterDate = Instant.ofEpochMilli(afterMs).atZone(ZoneId.systemDefault()).toLocalDate();
            String formatted = afterDate.toString().replace('-', '/'); // YYYY/MM/DD
            afterClause = " after:" + formatted;
        } else {
            // Initial sync window to avoid excessive history fetch
            afterClause = " newer_than:30d";
        }

        return keywords + afterClause;
    }

    private List<String> fetchAllMessageIds(String accessToken, String query) throws Exception {
        String pageToken = null;
        List<String> messageIds = new ArrayList<>();

        do {
            String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=100&q=" +
                    java.net.URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    (pageToken != null ? "&pageToken=" + pageToken : "");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("messages")) {
                for (JsonNode msg : root.get("messages")) {
                    messageIds.add(msg.get("id").asText());
                }
            }
            pageToken = root.has("nextPageToken") ? root.get("nextPageToken").asText(null) : null;
        } while (pageToken != null);

        return messageIds;
    }

    private SyncResult processMessages(String userId, String accessToken, List<String> messageIds) {
        int processed = 0, added = 0, skipped = 0;
        List<String> details = new ArrayList<>();
        
        for (String messageId : messageIds) {
            try {
                // Fast duplicate check by messageId
                if (emailTransactionRepository.findByUserIdAndMessageId(userId, messageId).isPresent()) {
                    skipped++;
                    details.add("Duplicate: " + messageId);
                    continue;
                }
                processed++;
                if (processMessage(userId, accessToken, messageId)) {
                    added++;
                    details.add("Added: " + messageId);
                } else {
                    skipped++;
                    details.add("Skipped: " + messageId);
                }
            } catch (DuplicateKeyException e) {
                skipped++;
                details.add("Duplicate: " + messageId);
                log.debug("Duplicate transaction for message {}: {}", messageId, e.getMessage());
            } catch (Exception e) {
                skipped++;
                details.add("Error: " + messageId + " - " + e.getMessage());
                log.warn("Failed to process message {}: {}", messageId, e.getMessage());
            }
        }
        
        return SyncResult.builder()
                .success(true)
                .processed(processed)
                .added(added)
                .skipped(skipped)
                .details(details)
                .build();
    }

    private boolean processMessage(String userId, String accessToken, String messageId) throws Exception {
        // Fetch message details
        String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + messageId + "?format=full";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode message = objectMapper.readTree(response.getBody());
        
        // Extract email components
        JsonNode payload = message.get("payload");
        String subject = extractHeader(payload, "Subject");
        String sender = extractHeader(payload, "From");
        String dateHeader = extractHeader(payload, "Date");
        String body = extractBody(payload);
        // Use Gmail's internalDate as the authoritative email timestamp
        LocalDate emailDate = null;
        try {
            long internalMs = message.has("internalDate") ? message.get("internalDate").asLong() : 0L;
            if (internalMs > 0) {
                emailDate = java.time.Instant.ofEpochMilli(internalMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {}
        
        log.debug("Processing message from {}: {}", sender, subject);
        
        // Use TransactionAIService to evaluate
        Optional<EmailTransaction> transaction = transactionAIService.evaluate(
                userId, messageId, subject, body, sender, dateHeader);
        
        if (transaction.isPresent()) {
            EmailTransaction tx = transaction.get();
            if (emailDate != null) {
                tx.setDate(emailDate);
                try {
                    // Recompute unique hash since the date changed
                    String newHash = transactionAIService.computeHash(userId, tx.getAmount(), tx.getDate(), tx.getMerchant());
                    tx.setUniqueHash(newHash);
                } catch (Exception e) {
                    log.debug("Failed to recompute unique hash: {}", e.getMessage());
                }
            }
            emailTransactionRepository.save(tx);
            log.info("Saved transaction: {} {} from {}", tx.getAmount(), tx.getCurrency(), tx.getMerchant());
            return true;
        }
        
        return false;
    }

    private void updateLastSynced(String userId, String accessToken, List<String> processedMessageIds) {
        if (processedMessageIds == null || processedMessageIds.isEmpty()) return;
        try {
            long[] maxInternalDateBox = new long[] {0L};
            String[] lastMsgIdBox = new String[] {null};
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            for (String msgId : processedMessageIds) {
                String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId + "?format=metadata";
                ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                JsonNode message = objectMapper.readTree(resp.getBody());
                long internalDate = message.has("internalDate") ? message.get("internalDate").asLong() : 0L;
                if (internalDate > maxInternalDateBox[0]) {
                    maxInternalDateBox[0] = internalDate;
                    lastMsgIdBox[0] = msgId;
                }
            }

            if (maxInternalDateBox[0] > 0) {
                var opt = credentialRepository.findByUserId(userId);
                if (opt.isPresent()) {
                    GmailCredential cred = opt.get();
                    cred.setLastSyncedInternalDateMs(maxInternalDateBox[0]);
                    cred.setLastSyncedMessageId(lastMsgIdBox[0]);
                    credentialRepository.save(cred);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update last synced marker for user {}: {}", userId, e.getMessage());
        }
    }

    private String extractHeader(JsonNode payload, String headerName) {
        if (payload.has("headers")) {
            for (JsonNode header : payload.get("headers")) {
                if (headerName.equalsIgnoreCase(header.get("name").asText())) {
                    return header.get("value").asText();
                }
            }
        }
        return "";
    }

    private String extractBody(JsonNode payload) {
        StringBuilder body = new StringBuilder();
        extractBodyRecursive(payload, body);
        return body.toString();
    }

    private void extractBodyRecursive(JsonNode part, StringBuilder body) {
        if (part.has("body") && part.get("body").has("data")) {
            String data = part.get("body").get("data").asText();
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(data);
                body.append(new String(decoded, StandardCharsets.UTF_8)).append("\n");
            } catch (Exception e) {
                log.debug("Failed to decode body part: {}", e.getMessage());
            }
        }
        
        if (part.has("parts")) {
            for (JsonNode subPart : part.get("parts")) {
                extractBodyRecursive(subPart, body);
            }
        }
    }

    private OAuthToken getOAuthToken(String userId) {
        try {
            GmailCredential cred = credentialRepository.findByUserId(userId).orElse(null);
            if (cred == null || cred.getRefreshToken() == null) return null;
            
            var tokenResponse = new GoogleRefreshTokenRequest(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    cred.getRefreshToken(),
                    clientId,
                    clientSecret
            ).setGrantType("refresh_token").execute();
            
            return new OAuthToken(tokenResponse.getAccessToken());
        } catch (Exception e) {
            log.error("Failed to get OAuth token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private RestTemplate createRestTemplate() {
        return new RestTemplate();
    }

    private static class OAuthToken {
        final String accessToken;
        OAuthToken(String accessToken) { this.accessToken = accessToken; }
    }
}
