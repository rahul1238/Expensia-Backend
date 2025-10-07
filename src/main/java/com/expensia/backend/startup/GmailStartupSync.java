package com.expensia.backend.startup;

import com.expensia.backend.service.gmail.GmailSyncOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class GmailStartupSync {

    private final GmailSyncOrchestrator orchestrator;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            log.info("Server startup complete - Starting Gmail sync for all connected users");
            orchestrator.startServerSync();
            log.info("Gmail startup sync initiated successfully");
        } catch (Exception e) {
            log.warn("Gmail startup sync failed, but server continues normally: {}", e.getMessage());
        }
    }
}
