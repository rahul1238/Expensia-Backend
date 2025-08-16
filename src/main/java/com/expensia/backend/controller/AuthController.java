package com.expensia.backend.controller;

import com.expensia.backend.dto.AuthResponse;
import com.expensia.backend.dto.LoginRequest;
import com.expensia.backend.dto.RegisterRequest;
import com.expensia.backend.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
// removed unused RestTemplate-related imports after refactor
import java.util.Map;
// removed unused direct repository and JWT imports after delegating to AuthService
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import com.expensia.backend.utils.CookieUtil;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    private Cookie buildCookie(String name, String value, int maxAge) { return CookieUtil.authCookie(name, value, maxAge); }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(registerRequest);
        if (authResponse.isSuccess()) {
            if (authResponse.getAccessToken() != null) {
                response.addCookie(buildCookie("accessToken", authResponse.getAccessToken(), 86400));
            }
            if (authResponse.getRefreshToken() != null) {
                response.addCookie(buildCookie("refreshToken", authResponse.getRefreshToken(), 604800));
            }

            authResponse.setAccessToken(null);
            authResponse.setRefreshToken(null);
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.login(request);
            if (authResponse.isSuccess()) {
                if (authResponse.getAccessToken() != null)
                    response.addCookie(buildCookie("accessToken", authResponse.getAccessToken(), 86400));
                if (authResponse.getRefreshToken() != null)
                    response.addCookie(buildCookie("refreshToken", authResponse.getRefreshToken(), 604800));

                authResponse.setAccessToken(null);
                authResponse.setRefreshToken(null);
                return ResponseEntity.ok(authResponse);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.error("Login failed: " + e.getMessage()));
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.error("Logout failed: " + e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            @CookieValue(value = "accessToken", required = false) String accessToken) {
        try {
            AuthResponse response = authService.getCurrentUser(accessToken);
            if (!response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.error("Failed to retrieve user: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Refresh token is required"));
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken);
        if (authResponse.isSuccess()) {
            if (authResponse.getAccessToken() != null)
                response.addCookie(buildCookie("accessToken", authResponse.getAccessToken(), 86400));

            authResponse.setAccessToken(null);
            authResponse.setRefreshToken(null);
            return ResponseEntity.ok(authResponse);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
    }

    @GetMapping("/csrf-token")
    public ResponseEntity<?> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok().body(Collections.singletonMap("token", csrf.getToken()));
    }

    @GetMapping("/google")
    public ResponseEntity<?> getGoogleAuthUrl() {
        String authUrl = "/oauth2/authorization/google";
        return ResponseEntity.ok().body(Collections.singletonMap("authUrl", authUrl));
    }

    // Google Identity Services credential endpoint (One-Tap)
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody Map<String, String> payload, HttpServletResponse response) {
        String credential = payload.get("credential");
        if (credential == null || credential.isEmpty()) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Missing Google credential"));
        }

        AuthResponse authResponse = authService.loginWithGoogleCredential(credential, googleClientId);
        if (authResponse.isSuccess()) {
            if (authResponse.getAccessToken() != null) {
                response.addCookie(buildCookie("accessToken", authResponse.getAccessToken(), 86400));
            }
            if (authResponse.getRefreshToken() != null) {
                response.addCookie(buildCookie("refreshToken", authResponse.getRefreshToken(), 604800));
            }
            authResponse.setAccessToken(null);
            authResponse.setRefreshToken(null);
            return ResponseEntity.ok(authResponse);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
    }
}
