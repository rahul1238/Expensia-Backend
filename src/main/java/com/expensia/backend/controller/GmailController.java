package com.expensia.backend.controller;

import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.model.GmailCredential;
import com.expensia.backend.service.gmail.GmailOAuthService;
import com.expensia.backend.service.gmail.GmailSyncService;
import com.expensia.backend.utils.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
public class GmailController {

    private final GmailOAuthService gmailOAuthService;
    private final GmailSyncService gmailSyncService;
    private final AuthUser authUser;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    // 1) Start consent flow
    @GetMapping("/connect")
    public ResponseEntity<?> startConnect() {
        try {
            String userEmail = authUser.getCurrentUser().getEmail();
            String redirectUri = appBaseUrl + "/api/gmail/callback";
            String url = gmailOAuthService.buildConsentUrl(redirectUri, userEmail);
            return ResponseEntity.ok(Map.of("authUrl", url));
        } catch (Exception e) {
            log.error("Failed to start Gmail connect", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to start Gmail connect"));
        }
    }

    // 2) OAuth callback to exchange code and save refresh token
    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam("code") String code, 
                                    @RequestParam(value = "error", required = false) String error,
                                    @RequestParam(value = "error_description", required = false) String errorDescription) {
        try {
            // Check for OAuth errors first
            if (error != null) {
                log.error("OAuth error in callback: {} - {}", error, errorDescription);
                return ResponseEntity.status(302)
                    .location(URI.create(frontendBaseUrl + "/settings?gmail=error&reason=" + error))
                    .build();
            }

            if (code == null || code.isBlank()) {
                log.error("No authorization code received in Gmail callback");
                return ResponseEntity.status(400).body(Map.of("error", "No authorization code received"));
            }

            String redirectUri = appBaseUrl + "/api/gmail/callback";
            log.info("Exchanging code for tokens with redirect URI: {}", redirectUri);
            
            var tokenResp = gmailOAuthService.exchangeCode(code, redirectUri);

            String refreshToken = tokenResp.getRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("No refresh token received. Access token: {}", tokenResp.getAccessToken() != null ? "present" : "missing");
                return ResponseEntity.status(400).body(Map.of("error", "No refresh token received. Ensure prompt=consent and access_type=offline"));
            }

            GmailCredential cred = gmailSyncService.getOrCreateCredentialForCurrentUser();
            cred.setRefreshToken(refreshToken);
            gmailSyncService.saveCredential(cred);

            log.info("Gmail OAuth callback successful for user");
            // redirect back to frontend success page
            return ResponseEntity.status(302).location(URI.create(frontendBaseUrl + "/settings?gmail=connected")).build();
        } catch (Exception e) {
            log.error("Gmail OAuth callback error: {}", e.getMessage(), e);
            return ResponseEntity.status(302)
                .location(URI.create(frontendBaseUrl + "/settings?gmail=error&reason=callback_failed"))
                .build();
        }
    }

    // 3) Trigger sync
    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            log.info("Starting Gmail sync request");
            List<EmailTransaction> saved = gmailSyncService.syncForCurrentUser();
            return ResponseEntity.ok(Map.of("synced", saved.size()));
        } catch (RuntimeException re) {
            String errorMessage = re.getMessage();
            log.error("Gmail sync runtime error: {}", errorMessage, re);
            
            // Check if it's an authentication-related error
            if (errorMessage != null && (errorMessage.contains("Invalid JWT") || 
                errorMessage.contains("JWT signature") || 
                errorMessage.contains("User not authenticated") ||
                errorMessage.contains("not trusted"))) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "Authentication expired. Please log in again.", 
                    "code", "AUTH_EXPIRED"
                ));
            }
            
            return ResponseEntity.status(400).body(Map.of("error", errorMessage));
        } catch (Exception e) {
            log.error("Gmail sync failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Sync failed"));
        }
    }

    // startServerSync: optional manual trigger for admins (kept simple here)
    // Note: actual server-start sync is wired via ApplicationReadyEvent in GmailStartupSync

    // 4) List stored email transactions
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions() {
        return ResponseEntity.ok(gmailSyncService.listForCurrentUser());
    }

    // 5) Disconnect and revoke
    @DeleteMapping("/disconnect")
    public ResponseEntity<?> disconnect() {
        try {
            GmailCredential cred = gmailSyncService.getOrCreateCredentialForCurrentUser();
            try { gmailSyncService.revokeRefreshToken(cred.getRefreshToken()); } catch (Exception ignored) {}
            gmailSyncService.deleteCredentialForCurrentUser();
            return ResponseEntity.ok(Map.of("message", "Disconnected Gmail"));
        } catch (Exception e) {
            log.error("Failed to disconnect Gmail", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to disconnect"));
        }
    }
}
