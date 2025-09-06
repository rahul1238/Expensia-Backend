package com.expensia.backend.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("gmailCredentials")
public class GmailCredential {
    @Id
    private String id;
    @Indexed(unique = true)
    private String userId;
    private String refreshToken;

    // Incremental sync tracking
    // Gmail message internalDate in milliseconds since epoch for the newest message we've scanned
    private Long lastSyncedInternalDateMs;
    // Optional: last synced Gmail message id (can assist in debugging)
    private String lastSyncedMessageId;

    // Bookkeeping timestamps
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
