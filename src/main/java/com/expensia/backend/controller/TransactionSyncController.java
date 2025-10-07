package com.expensia.backend.controller;

import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.service.gmail.GmailSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionSyncController {

    private final GmailSyncService gmailSyncService;

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            // Check if user has valid Gmail credentials first
            if (!gmailSyncService.hasValidCredentials()) {
                log.info("Gmail sync attempted but no valid credentials found for user");
                return ResponseEntity.ok(Map.of(
                    "count", 0,
                    "transactions", List.of(),
                    "message", "Gmail not connected. Please connect Gmail first.",
                    "action", "connect_gmail"
                ));
            }

            List<EmailTransaction> saved = gmailSyncService.syncForCurrentUser();
            return ResponseEntity.ok(Map.of(
                    "count", saved.size(),
                    "transactions", saved,
                    "message", saved.size() > 0 ? "Sync completed successfully" : "No new transactions found"
            ));
        } catch (RuntimeException re) {
            String errorMessage = re.getMessage();
            if (errorMessage != null && (errorMessage.contains("403") || 
                errorMessage.contains("access_denied") || 
                errorMessage.contains("unverified"))) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Access denied. Please try reconnecting Gmail."
                ));
            }
            return ResponseEntity.badRequest().body(Map.of(
                "error", errorMessage
            ));
        } catch (Exception e) {
            log.error("Transaction sync failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Sync failed: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/gmail-status")
    public ResponseEntity<?> checkGmailStatus() {
        try {
            boolean hasCredentials = gmailSyncService.hasValidCredentials();
            return ResponseEntity.ok(Map.of(
                "connected", hasCredentials,
                "message", hasCredentials ? "Gmail is connected and ready" : "Gmail not connected",
                "nextStep", hasCredentials ? "sync" : "connect_gmail"
            ));
        } catch (Exception e) {
            log.error("Failed to check Gmail status", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to check status: " + e.getMessage()
            ));
        }
    }
}
