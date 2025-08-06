package com.expensia.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.expensia.backend.type.UserStatistics;
import com.expensia.backend.exception.TransactionServiceException;
import com.expensia.backend.provider.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/statistics")
    public ResponseEntity<?> getUserStatistics() {
        try {
            UserStatistics stats = userService.getUserStatistics();
            return ResponseEntity.ok(stats);
        } catch (TransactionServiceException e) {
            log.error("Transaction service error: {}", e.getDetailedMessage());
            
            HttpStatus status = switch (e.getErrorType()) {
                case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
                case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            
            return ResponseEntity.status(status)
                                 .body("Failed to retrieve user statistics: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error retrieving user statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to retrieve user statistics");
        }
    }

    @GetMapping("/statistics/detailed")
    public ResponseEntity<?> getDetailedUserStatistics() {
        try {
            UserStatistics stats = userService.getDetailedUserStatistics();
            return ResponseEntity.ok(stats);
        } catch (TransactionServiceException e) {
            log.error("Transaction service error: {}", e.getDetailedMessage());
            
            HttpStatus status = switch (e.getErrorType()) {
                case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
                case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            
            return ResponseEntity.status(status)
                                 .body("Failed to retrieve detailed user statistics: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error retrieving detailed user statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to retrieve detailed user statistics");
        }
    }
}
