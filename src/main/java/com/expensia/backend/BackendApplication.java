package com.expensia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		// Set system property to make MongoDB connection more resilient
		System.setProperty("spring.data.mongodb.repositories.enabled", "true");
		SpringApplication.run(BackendApplication.class, args);
	}

}
