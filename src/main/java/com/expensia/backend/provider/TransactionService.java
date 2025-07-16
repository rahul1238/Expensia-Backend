package com.expensia.backend.provider;

import com.expensia.backend.model.Transaction;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;

  public List<Transaction> getAllTransactions() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null || !authentication.isAuthenticated()) {
        throw new RuntimeException("User not authenticated");
      }

      String email = authentication.getName();
      log.info("Retrieving transactions for user: {}", email);

      // Get the principal (which should be our User object)
      Object principal = authentication.getPrincipal();

      if (principal instanceof User) {
        // If principal is already a User, use it directly
        log.info("Principal is a User instance");
        User user = (User) principal;
        log.info("User ID from principal: {}", user.getId());
        return transactionRepository.findByUserId(user.getId());
      } else {
        // Fall back to finding by email
        log.info("Principal is not a User instance, trying to find by email");
        Optional<User> user = userRepository.findByEmail(email);

        if (user.isEmpty()) {
          log.error("User not found with email: {}", email);
          throw new RuntimeException("User not found");
        }

        String userId = user.get().getId();
        log.info("Found user with ID: {}", userId);
        return transactionRepository.findByUserId(userId);
      }
    }
    catch (Exception e) {
      log.error("Error retrieving transactions", e);
      throw new RuntimeException("Error retrieving transactions: " + e.getMessage(), e);
    }
  }
}
