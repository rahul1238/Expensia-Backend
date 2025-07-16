package com.expensia.backend.controller;

import com.expensia.backend.dto.AuthResponse;
import com.expensia.backend.dto.LoginRequest;
import com.expensia.backend.dto.RegisterRequest;
import com.expensia.backend.security.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest registerRequest) {
        return authService.register(registerRequest);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request){
        try {
            return authService.login(request);
        } catch (Exception e) {
            return AuthResponse.error("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public AuthResponse logout(@RequestBody String accessToken) {
        try {
            return authService.logout(accessToken);
        } catch (Exception e) {
            return AuthResponse.error("Logout failed: " + e.getMessage());
        }
    }


}
