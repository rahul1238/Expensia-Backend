package com.expensia.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
}
