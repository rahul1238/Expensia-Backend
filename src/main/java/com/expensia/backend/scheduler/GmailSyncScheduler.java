package com.expensia.backend.scheduler;

import com.expensia.backend.model.GmailCredential;
import com.expensia.backend.repository.GmailCredentialRepository;
import com.expensia.backend.service.gmail.EnhancedGmailSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailSyncScheduler {

    private final GmailCredentialRepository credentialRepository;
    private final EnhancedGmailSyncService enhancedGmailSyncService;

    // Every 30 minutes
    @Scheduled(fixedRate = 30 * 60 * 1000L, initialDelay = 30 * 1000L)
    public void run() {
        try {
            List<GmailCredential> creds = credentialRepository.findAll();
            for (GmailCredential c : creds) {
                if (c.getUserId() == null || c.getUserId().isBlank()) continue;
                try {
                    var result = enhancedGmailSyncService.syncForUser(c.getUserId());
                    log.info("Scheduled sync for user {}: {} processed, {} added, {} skipped", 
                             c.getUserId(), result.getProcessed(), result.getAdded(), result.getSkipped());
                } catch (Exception e) {
                    log.warn("Background Gmail sync failed for user {}: {}", c.getUserId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Background Gmail sync sweep failed: {}", e.getMessage());
        }
    }
}
