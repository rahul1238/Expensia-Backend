package com.expensia.backend.controller;

import com.expensia.backend.dto.AuthResponse;
import com.expensia.backend.dto.LoginRequest;
import com.expensia.backend.dto.RegisterRequest;
import com.expensia.backend.security.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletResponse response) {
        AuthResponse authResponse = authService.register(registerRequest);
        if (authResponse.getAccessToken() != null) {
            Cookie accessTokenCookie = new Cookie("accessToken", authResponse.getAccessToken());
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setPath("/");
            response.addCookie(accessTokenCookie);
        }
        if (authResponse.getRefreshToken() != null) {
            Cookie refreshTokenCookie = new Cookie("refreshToken", authResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setPath("/");
            response.addCookie(refreshTokenCookie);
        }
        authResponse.setAccessToken(null);
        authResponse.setRefreshToken(null);
        if (!authResponse.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(authResponse);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response){
        try {
            AuthResponse authResponse = authService.login(request);
            if (authResponse.getAccessToken() != null) {
                Cookie accessTokenCookie = new Cookie("accessToken", authResponse.getAccessToken());
                accessTokenCookie.setHttpOnly(true);
                accessTokenCookie.setMaxAge(86400);
                accessTokenCookie.setPath("/");
                response.addCookie(accessTokenCookie);
            }
            if (authResponse.getRefreshToken() != null) {
                Cookie refreshTokenCookie = new Cookie("refreshToken", authResponse.getRefreshToken());
                refreshTokenCookie.setHttpOnly(true);
                refreshTokenCookie.setMaxAge(604800);
                refreshTokenCookie.setPath("/");
                response.addCookie(refreshTokenCookie);
            }

            authResponse.setAccessToken(null);
            authResponse.setRefreshToken(null);
            if (!authResponse.isSuccess()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
            }
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.error("Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@CookieValue(value = "accessToken", required = false) String accessToken,
                                               HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.logout(accessToken);
            Cookie accessTokenCookie = new Cookie("accessToken", null);
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(0);
            response.addCookie(accessTokenCookie);

            Cookie refreshTokenCookie = new Cookie("refreshToken", null);
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(0);
            response.addCookie(refreshTokenCookie);

            if (!authResponse.isSuccess()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
            }
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.error("Logout failed: " + e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@CookieValue(value="accessToken", required = false) String accessToken) {
        try {
            AuthResponse response = authService.getCurrentUser(accessToken);
            if (!response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.error("Failed to retrieve user: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Refresh token is required"));
        }
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        if (authResponse.getAccessToken() != null) {
            Cookie accessTokenCookie = new Cookie("accessToken", authResponse.getAccessToken());
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setPath("/");
            response.addCookie(accessTokenCookie);
        }

        authResponse.setAccessToken(null);
        authResponse.setRefreshToken(null);
        if (!authResponse.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
        }
        return ResponseEntity.ok(authResponse);
    }


}
