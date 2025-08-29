package com.expensia.backend.service.gmail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;

@Service
@RequiredArgsConstructor
public class GmailOAuthService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;
    @Value("${app.oauth2.authorized-redirect-uris}")
    private String redirectUris;

    public static final String GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private GoogleAuthorizationCodeFlow buildFlow(String redirectUri) throws Exception {
        var jsonFactory = GsonFactory.getDefaultInstance();
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
    GoogleClientSecrets secrets = new GoogleClientSecrets().setWeb(details);

        return new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, secrets, List.of(GMAIL_SCOPE))
        .setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
        .setAccessType("offline")
                .build();
    }

    public String buildConsentUrl(String redirectUri, String loginHint) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow(redirectUri);
    var url = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
        .setAccessType("offline");
    url.set("prompt", "consent");
        return url.build();
    }

    public GoogleTokenResponse exchangeCode(String code, String redirectUri) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow(redirectUri);
        return flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();
    }
}
