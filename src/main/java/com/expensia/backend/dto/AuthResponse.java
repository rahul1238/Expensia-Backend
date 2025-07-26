package com.expensia.backend.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private  boolean success;
    private String accessToken;
    private String refreshToken;
    private String message;
    private UserDTO user;

    public static AuthResponse auth(Boolean success, String accessToken, String refreshToken, String message, UserDTO user) {
        return AuthResponse.builder()
                .success(success)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .message(message)
                .user(user)
                .build();
    }

    public static AuthResponse logout(Boolean success, String message) {
        return AuthResponse.builder()
                .success(success)
                .message(message)
                .build();
    }

    public static AuthResponse error(String message) {
        return AuthResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
