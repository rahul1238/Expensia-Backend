package com.expensia.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for the test profile
 * Provides basic test setup without external dependencies
 */
@TestConfiguration
@Profile("test")
public class TestConfig {
    
    // This class can be extended to provide test-specific beans if needed
    
}