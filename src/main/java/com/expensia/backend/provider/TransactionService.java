package com.expensia.backend.provider;

import com.expensia.backend.dto.TransactionRequest;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.utils.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final AuthUser authUser;

  /**
   * Retrieves all transactions for the authenticated user
   * 
   * @return List of transactions
   */
  public List<Transaction> getAllTransactions() {
    try {
      String userId = authUser.getCurrentUserId();
      return transactionRepository.findByUserId(new ObjectId(userId));
    }
    catch (Exception e) {
      log.error("Error retrieving transactions", e);
      throw new RuntimeException("Error retrieving transactions", e);
    }
  }

  /**
   * Creates a new transaction for the authenticated user
   *
   * @param request Transaction data
   * @return ResponseEntity containing the created transaction
   */
  public ResponseEntity<Transaction> createTransaction(TransactionRequest request) {
    try {
      User user = authUser.getCurrentUser();
      
      if (user.getId() == null) {
        throw new RuntimeException("User ID not available");
      }
      
      // Convert string to Currency enum
      com.expensia.backend.utils.TransactionEnums.Currency currency;
      try {
        currency = com.expensia.backend.utils.TransactionEnums.Currency.valueOf(request.getCurrency());
      } catch (IllegalArgumentException e) {
        currency = com.expensia.backend.utils.TransactionEnums.Currency.INR;
      }
      
      // Convert string to TransactionMethod enum
      com.expensia.backend.utils.TransactionEnums.TransactionMethod transactionMethod;
      try {
        transactionMethod = com.expensia.backend.utils.TransactionEnums.TransactionMethod.valueOf(request.getTransactionMethod());
      } catch (IllegalArgumentException e) {
        transactionMethod = com.expensia.backend.utils.TransactionEnums.TransactionMethod.CREDIT_CARD;
      }

      Transaction transaction = Transaction.builder()
          .userId(new ObjectId(user.getId()))
          .amount(request.getAmount())
          .description(request.getDescription())
          .date(request.getDate() != null ? request.getDate() : new Date())
          .category(request.getCategory())
          .type(request.getType())
          .currency(currency)
          .notes(request.getNotes())
          .transactionMethod(transactionMethod)
          .build();

      Transaction savedTransaction = transactionRepository.save(transaction);
      return ResponseEntity.ok(savedTransaction);
    } catch (Exception e) {
      log.error("Error creating transaction", e);
      throw new RuntimeException("Error creating transaction", e);
    }
  }
}
