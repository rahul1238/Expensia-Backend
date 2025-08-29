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
            List<EmailTransaction> saved = gmailSyncService.syncForCurrentUser();
            return ResponseEntity.ok(Map.of(
                    "count", saved.size(),
                    "transactions", saved
            ));
        } catch (RuntimeException re) {
            return ResponseEntity.badRequest().body(Map.of("error", re.getMessage()));
        } catch (Exception e) {
            log.error("Transaction sync failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Sync failed"));
        }
    }
}
