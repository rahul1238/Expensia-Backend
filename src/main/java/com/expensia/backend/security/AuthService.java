package com.expensia.backend.security;

import com.expensia.backend.dto.AuthResponse;
import com.expensia.backend.dto.LoginRequest;
import com.expensia.backend.dto.RegisterRequest;
import com.expensia.backend.model.Token;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TokenRepository;
import com.expensia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final TokenRepository tokenRepository;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    public AuthResponse register(RegisterRequest request) {
        try {
            if (userRepository.existsByUsername(request.getUsername())) {
                return AuthResponse.error("Username already exists");
            }

            User user = User.builder()
                    .username(request.getUsername())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .email(request.getEmail())
                    .age(request.getAge())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .occupation(request.getOccupation())
                    .build();

            User savedUser=userRepository.save(user);

            String accessToken = jwtService.generateAccessToken(user.getEmail());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail());

            saveToken(savedUser, accessToken, refreshToken);

            return AuthResponse.register(true, accessToken, null, "Registration successful: " + user.getUsername(), user);
        } catch (Exception e) {
            return AuthResponse.error("Registration failed: " + e.getMessage());
        }
    }
    public AuthResponse login(LoginRequest request) {
            try {
                User user = userRepository.findByEmail(request.getEmail())
                        .orElseThrow(() -> new RuntimeException("User not found"));

                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                    return AuthResponse.error( "Invalid credentials" );
                }

                String accessToken = jwtService.generateAccessToken(user.getEmail());
                String refreshToken = jwtService.generateRefreshToken(user.getEmail());

                saveToken(user, accessToken, refreshToken);

                return AuthResponse.login(true, accessToken, refreshToken, "Login successful: " + user.getUsername());
            } catch (Exception e) {
                return AuthResponse.error("Login failed: " + e.getMessage());
            }
    }

    public void saveToken(User user, String accessToken,String refreshToken) {
        Token token = Token.builder()
            .userId(new ObjectId(user.getId()))
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresAt(System.currentTimeMillis()+ refreshTokenExpiration)
            .revoked(false)
            .build();

        tokenRepository.save(token);
    }

    public AuthResponse logout(String accessToken) {
        try {
            String email = jwtService.extractEmail(accessToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Token token = tokenRepository.findByAccessToken(accessToken)
                    .orElseThrow(() -> new RuntimeException("Token not found"));

            token.setRevoked(true);
            tokenRepository.save(token);

            return AuthResponse.logout(true, "Logout successful for user: " + user.getUsername());
        } catch (Exception e) {
            return AuthResponse.error("Logout failed: " + e.getMessage());
        }
    }

}
