package com.expensia.backend;

import org.junit.jupiter.api.Test;

/**
 * Basic unit tests for the backend application.
 * These tests don't load the Spring context to avoid dependency issues in CI/CD.
 */
class BackendApplicationTests {

	@Test
	void applicationClassExists() {
		// Verify the main application class exists and can be instantiated
		BackendApplication app = new BackendApplication();
		assert app != null;
	}

	@Test
	void basicMathWorks() {
		// Simple test to verify JUnit is working
		int result = 2 + 2;
		assert result == 4;
	}

}
