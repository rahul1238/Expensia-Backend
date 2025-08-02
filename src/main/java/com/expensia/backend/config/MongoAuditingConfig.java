package com.expensia.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
    // This configuration enables automatic auditing for @CreatedDate and @LastModifiedDate annotations
}
