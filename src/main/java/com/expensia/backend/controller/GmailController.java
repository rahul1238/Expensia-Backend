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
    public ResponseEntity<?> callback(@RequestParam("code") String code) {
        try {
            String redirectUri = appBaseUrl + "/api/gmail/callback";
            var tokenResp = gmailOAuthService.exchangeCode(code, redirectUri);

            String refreshToken = tokenResp.getRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "No refresh token received. Ensure prompt=consent and access_type=offline"));
            }

            GmailCredential cred = gmailSyncService.getOrCreateCredentialForCurrentUser();
            cred.setRefreshToken(refreshToken);
            gmailSyncService.saveCredential(cred);

            // redirect back to frontend success page
            return ResponseEntity.status(302).location(URI.create(frontendBaseUrl + "/settings?gmail=connected")).build();
        } catch (Exception e) {
            log.error("Gmail OAuth callback error", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to handle Gmail callback"));
        }
    }

    // 3) Trigger sync
    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            List<EmailTransaction> saved = gmailSyncService.syncForCurrentUser();
            return ResponseEntity.ok(Map.of("synced", saved.size()));
        } catch (RuntimeException re) {
            return ResponseEntity.status(400).body(Map.of("error", re.getMessage()));
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
