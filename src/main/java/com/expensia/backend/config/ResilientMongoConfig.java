package com.expensia.backend.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class ResilientMongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/expensia}")
    private String mongoUri;

    @Override
    protected String getDatabaseName() {
        try {
            ConnectionString connectionString = new ConnectionString(mongoUri);
            return connectionString.getDatabase() != null ? connectionString.getDatabase() : "expensia";
        } catch (Exception e) {
            log.warn("Could not parse database name from URI, using default: {}", e.getMessage());
            return "expensia";
        }
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        try {
            ConnectionString connectionString = new ConnectionString(mongoUri);
            
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToConnectionPoolSettings(builder -> 
                        builder.maxConnectionIdleTime(30, TimeUnit.SECONDS)
                               .maxConnectionLifeTime(60, TimeUnit.SECONDS)
                               .minSize(1)
                               .maxSize(10))
                    .applyToSocketSettings(builder -> 
                        builder.connectTimeout(5, TimeUnit.SECONDS)
                               .readTimeout(10, TimeUnit.SECONDS))
                    .applyToServerSettings(builder -> 
                        builder.heartbeatFrequency(30, TimeUnit.SECONDS)
                               .minHeartbeatFrequency(10, TimeUnit.SECONDS))
                    .build();
            
            log.info("Creating MongoDB client with resilient settings");
            return MongoClients.create(settings);
        } catch (Exception e) {
            log.error("Failed to create MongoDB client: {}", e.getMessage());
            // Create a minimal client that will fail fast
            return MongoClients.create();
        }
    }
}