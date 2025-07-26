package com.expensia.backend.security;

import com.expensia.backend.dto.AuthResponse;
import com.expensia.backend.dto.LoginRequest;
import com.expensia.backend.dto.RegisterRequest;
import com.expensia.backend.dto.UserDTO;
import com.expensia.backend.model.Token;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TokenRepository;
import com.expensia.backend.repository.UserRepository;
import com.expensia.backend.utils.UserMapper;
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

            if (userRepository.existsByEmail(request.getEmail())) {
                return AuthResponse.error("Email already exists");
            }

            if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                return AuthResponse.error("Phone number already exists");
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

            UserDTO userResponse = UserMapper.toDto(savedUser);

            return AuthResponse.auth(true, accessToken, null, "Registration successful: " + user.getUsername(), userResponse);
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

                UserDTO userResponse = UserMapper.toDto(user);

                return AuthResponse.auth(true, accessToken, refreshToken, "Login successful: " + user.getUsername(),userResponse);
            } catch (Exception e) {
                return AuthResponse.error("Login failed: " + e.getMessage());
            }
    }

    public void saveToken(User user, String accessToken,String refreshToken) {

        tokenRepository.findAllByUserIdAndRevokedFalse(new ObjectId(user.getId()))
            .forEach(existingToken -> {
                existingToken.setRevoked(true);
                tokenRepository.save(existingToken);
            });

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

    public AuthResponse getCurrentUser( String accessToken) {
        try {
            String email = jwtService.extractEmail(accessToken);
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

            UserDTO userResponse = UserMapper.toDto(user);
            return AuthResponse.auth(true, null, null, "User retrieved successfully", userResponse);
        } catch (Exception e) {
            return AuthResponse.error("Failed to retrieve user: " + e.getMessage());
        }
    }

    public AuthResponse refreshToken(String refreshToken) {
        try {
            String email = jwtService.extractEmail(refreshToken);
            if (email == null) {
                return AuthResponse.error("Invalid refresh token");
            }

            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

            Token tokenEntity = tokenRepository.findByRefreshTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found or revoked"));

            if (tokenEntity.getExpiresAt() < System.currentTimeMillis()) {
                tokenEntity.setRevoked(true);
                tokenRepository.save(tokenEntity);
                return AuthResponse.error("Refresh token expired");
            }

            String newAccessToken = jwtService.generateAccessToken(email);

            tokenEntity.setAccessToken(newAccessToken);
            tokenRepository.save(tokenEntity);

            return AuthResponse.auth(true, newAccessToken, refreshToken, "Token refreshed successfully", null);
        } catch (Exception e) {
            return AuthResponse.error("Failed to refresh token: " + e.getMessage());
        }
    }

}
