package com.expensia.backend.startup;

import com.expensia.backend.model.GmailCredential;
import com.expensia.backend.repository.GmailCredentialRepository;
import com.expensia.backend.service.gmail.EnhancedGmailSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailStartupSync {

    private final GmailCredentialRepository credentialRepository;
    private final EnhancedGmailSyncService enhancedGmailSyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            List<GmailCredential> creds = credentialRepository.findAll();
            for (GmailCredential c : creds) {
                if (c.getUserId() == null || c.getUserId().isBlank()) continue;
                if (c.getRefreshToken() == null || c.getRefreshToken().isBlank()) continue;
                try {
                    var result = enhancedGmailSyncService.syncForUser(c.getUserId());
                    log.info("Startup sync for user {}: {} processed, {} added, {} skipped", 
                             c.getUserId(), result.getProcessed(), result.getAdded(), result.getSkipped());
                } catch (Exception e) {
                    log.warn("Startup Gmail sync failed for user {}: {}", c.getUserId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Startup Gmail sync sweep failed: {}", e.getMessage());
        }
    }
}
