package com.expensia.backend.controller;

import com.expensia.backend.service.gmail.EnhancedGmailSyncService;
import com.expensia.backend.service.gmail.GmailSyncOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
@Slf4j
public class GmailSyncController {

    private final GmailSyncOrchestrator orchestrator;

    // syncUserEmails(userId) - on demand
    @PostMapping("/sync/{userId}")
    public ResponseEntity<?> syncForUser(@PathVariable String userId) {
        try {
            log.info("Manual sync requested for user: {}", userId);
            EnhancedGmailSyncService.SyncResult result = orchestrator.syncUserEmails(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Manual sync failed for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Sync failed: " + e.getMessage());
        }
    }
}
