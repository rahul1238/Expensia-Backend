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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    private void configureTokenCookie(Cookie cookie, int maxAge) {
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        cookie.setPath("/");
        // Uncomment for production
        // cookie.setSecure(true);
        // cookie.setAttribute("SameSite", "Strict");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletResponse response) {
        AuthResponse authResponse = authService.register(registerRequest);
        if (authResponse.isSuccess()) {
            if (authResponse.getAccessToken() != null) {
                Cookie accessTokenCookie = new Cookie("accessToken", authResponse.getAccessToken());
                configureTokenCookie(accessTokenCookie, 86400);
                response.addCookie(accessTokenCookie);
            }
            if (authResponse.getRefreshToken() != null) {
                Cookie refreshTokenCookie = new Cookie("refreshToken", authResponse.getRefreshToken());
                configureTokenCookie(refreshTokenCookie, 604800);
                response.addCookie(refreshTokenCookie);
            }

            authResponse.setAccessToken(null);
            authResponse.setRefreshToken(null);
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response){
        try {
            AuthResponse authResponse = authService.login(request);
            if (authResponse.isSuccess()) {
                if (authResponse.getAccessToken() != null) {
                    Cookie accessTokenCookie = new Cookie("accessToken", authResponse.getAccessToken());
                    configureTokenCookie(accessTokenCookie, 86400);
                    response.addCookie(accessTokenCookie);
                }
                if (authResponse.getRefreshToken() != null) {
                    Cookie refreshTokenCookie = new Cookie("refreshToken", authResponse.getRefreshToken());
                    configureTokenCookie(refreshTokenCookie, 604800);
                    response.addCookie(refreshTokenCookie);
                }

                authResponse.setAccessToken(null);
                authResponse.setRefreshToken(null);
                return ResponseEntity.ok(authResponse);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
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
        if (authResponse.isSuccess()) {
            if (authResponse.getAccessToken() != null) {
                Cookie accessTokenCookie = new Cookie("accessToken", authResponse.getAccessToken());
                configureTokenCookie(accessTokenCookie, 86400); // 1 day
                response.addCookie(accessTokenCookie);
            }

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
}
