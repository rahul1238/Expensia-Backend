package com.expensia.backend.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class GmailStartupSync {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            log.info("Server startup complete - Gmail sync temporarily disabled for Cloud Run reliability");
        } catch (Exception e) {
            log.warn("Startup event handling failed: {}", e.getMessage());
        }
    }
}
