package com.expensia.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.gmail.integration.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class GmailIntegrationConfig {
    
    public GmailIntegrationConfig() {
        log.info("Gmail integration is ENABLED");
        log.debug("Configuration: Testing phase mode (up to 100 users, no CASA required)");
        log.debug("OAuth consent screen may show unverified app warning");
    }
}