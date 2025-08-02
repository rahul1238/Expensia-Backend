package com.expensia.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("accessTokens")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {
  @Id
  private String id;
  private ObjectId userId;
  private String accessToken;
  private String refreshToken;
  private long expiresAt;
  private boolean revoked;
  
  @CreatedDate
  private LocalDateTime createdAt;
  
  @LastModifiedDate
  private LocalDateTime updatedAt;
}
