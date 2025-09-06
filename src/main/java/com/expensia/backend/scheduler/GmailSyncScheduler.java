package com.expensia.backend.scheduler;

import com.expensia.backend.service.gmail.GmailSyncOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class GmailSyncScheduler {

    private final GmailSyncOrchestrator orchestrator;

    // Every 30 minutes
    @Scheduled(fixedRate = 30 * 60 * 1000L, initialDelay = 30 * 1000L)
    public void run() {
        try {
            log.info("Starting scheduled Gmail sync sweep for logged-in users");
            orchestrator.startServerSync();
        } catch (Exception e) {
            log.warn("Background Gmail sync sweep failed: {}", e.getMessage());
        }
    }
}
