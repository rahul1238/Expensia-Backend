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
            log.info("Starting server startup Gmail sync for logged-in users");
            orchestrator.startServerSync();
        } catch (Exception e) {
            log.warn("Startup Gmail sync sweep failed: {}", e.getMessage());
        }
    }
}
