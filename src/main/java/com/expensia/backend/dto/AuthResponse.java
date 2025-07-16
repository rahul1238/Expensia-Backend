package com.expensia.backend.dto;

import com.expensia.backend.model.User;
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
    private User user;

    public static AuthResponse login(Boolean success, String accessToken, String refreshToken, String message) {
        return AuthResponse.builder()
                .success(success)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .message(message)
                .build();
    }

    public static AuthResponse register(Boolean success, String accessToken, String refreshToken, String message, User user) {
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
