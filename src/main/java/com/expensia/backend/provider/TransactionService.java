package com.expensia.backend.provider;

import com.expensia.backend.dto.TransactionRequest;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;

  /**
   * Gets the currently authenticated user
   * @return The authenticated user
   * @throws RuntimeException if user is not authenticated or not found
   */
  private User getAuthenticatedUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new RuntimeException("User not authenticated");
    }

    String userName = authentication.getName();
    Optional<User> user = userRepository.findByUsername(userName);

    if (user.isEmpty()) {
      log.error("User not found with email: {}", userName);
      throw new RuntimeException("User not found");
    }

    return user.get();
  }

  public List<Transaction> getAllTransactions() {
    try {
      User user = getAuthenticatedUser();
      log.info("Retrieving transactions for user: {}", user.getUsername());
      return transactionRepository.findByUserId(new ObjectId(user.getId()));
    }
    catch (Exception e) {
      log.error("Error retrieving transactions", e);
      throw new RuntimeException("Error retrieving transactions: " + e.getMessage(), e);
    }
  }

  public Transaction createTransaction(TransactionRequest request) {
    try {
      User user = getAuthenticatedUser();

      Transaction transaction = Transaction.builder()
          .userId(new ObjectId(user.getId()))
          .amount(request.getAmount())
          .description(request.getDescription())
          .date(new Date())
          .category(request.getCategory())
          .type(request.getType())
          .currency(request.getCurrency())
          .notes(request.getNotes())
          .build();

      return transactionRepository.save(transaction);
    } catch (Exception e) {
      log.error("Error creating transaction", e);
      throw new RuntimeException("Error creating transaction: " + e.getMessage(), e);
    }
  }
}
