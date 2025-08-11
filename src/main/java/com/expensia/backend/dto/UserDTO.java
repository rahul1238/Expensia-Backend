package com.expensia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
  private String id;
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private Integer age;
  private String phoneNumber;
  private String occupation;
  private String createdAt;
  private String updatedAt;
  
  // OAuth2 fields
  private String provider;
  private String profilePictureUrl;
  private boolean emailVerified;
}
