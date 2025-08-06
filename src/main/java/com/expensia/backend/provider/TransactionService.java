package com.expensia.backend.provider;

import com.expensia.backend.dto.TransactionFilterRequest;
import com.expensia.backend.dto.TransactionRequest;
import com.expensia.backend.exception.TransactionServiceException;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.utils.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final MongoTemplate mongoTemplate;
  private final AuthUser authUser;

  /**
   * Retrieves all transactions for the authenticated user
   * 
   * @return List of transactions
   * @throws TransactionServiceException if retrieval fails
   */
  public List<Transaction> getAllTransactions() {
    try {
      String userId = authUser.getCurrentUserId();
      return transactionRepository.findByUserId(new ObjectId(userId));
    } catch (IllegalArgumentException e) {
      log.error("Invalid user ID format", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
          "Invalid user ID format",
          "getAllTransactions",
          e
      );
    } catch (DataAccessException e) {
      log.error("Database error retrieving transactions", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to retrieve transactions from database",
          "getAllTransactions",
          e
      );
    } catch (Exception e) {
      log.error("Unexpected error retrieving transactions", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error retrieving transactions",
          "getAllTransactions",
          e
      );
    }
  }

  /**
   * Retrieves transactions for the authenticated user with filters applied
   * Uses MongoDB Criteria API for proper null handling and efficient querying
   * 
   * @param filter Filter criteria for transactions
   * @return List of filtered transactions
   */
  public List<Transaction> getFilteredTransactions(TransactionFilterRequest filter) {
    try {
      String userId = authUser.getCurrentUserId();
      ObjectId userObjectId = new ObjectId(userId);
      
      if (isEmptyFilter(filter)) {
        return transactionRepository.findByUserId(userObjectId);
      }
      
      Criteria criteria = Criteria.where("userId").is(userObjectId);
      
      if (filter.getCategory() != null && !filter.getCategory().trim().isEmpty()) {
        criteria = criteria.and("category").is(filter.getCategory());
      }
      
      if (filter.getType() != null && !filter.getType().trim().isEmpty()) {
        criteria = criteria.and("type").is(filter.getType());
      }
      
      if (filter.getCurrency() != null) {
        criteria = criteria.and("currency").is(filter.getCurrency());
      }
      
      if (filter.getTransactionMethod() != null) {
        criteria = criteria.and("transactionMethod").is(filter.getTransactionMethod());
      }
      
      if (filter.getMinAmount() != null) {
        criteria = criteria.and("amount").gte(filter.getMinAmount());
      }
      
      if (filter.getMaxAmount() != null) {
        criteria = criteria.and("amount").lte(filter.getMaxAmount());
      }
      
      if (filter.getStartDate() != null) {
        criteria = criteria.and("date").gte(filter.getStartDate());
      }
      
      if (filter.getEndDate() != null) {
        criteria = criteria.and("date").lte(filter.getEndDate());
      }
      
      if (filter.getDescription() != null && !filter.getDescription().trim().isEmpty()) {
        criteria = criteria.and("description").regex(filter.getDescription(), "i");
      }
      
      if (filter.getCreatedAfter() != null) {
        criteria = criteria.and("createdAt").gte(filter.getCreatedAfter());
      }
      
      if (filter.getCreatedBefore() != null) {
        criteria = criteria.and("createdAt").lte(filter.getCreatedBefore());
      }
      
      if (filter.getUpdatedAfter() != null) {
        criteria = criteria.and("updatedAt").gte(filter.getUpdatedAfter());
      }
      
      if (filter.getUpdatedBefore() != null) {
        criteria = criteria.and("updatedAt").lte(filter.getUpdatedBefore());
      }
      
      Query query = new Query(criteria);
      return mongoTemplate.find(query, Transaction.class);
      
    } catch (IllegalArgumentException e) {
      log.error("Invalid user ID format during filtering", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
          "Invalid user ID format",
          "getFilteredTransactions",
          e
      );
    } catch (DataAccessException e) {
      log.error("Database error during transaction filtering", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to retrieve filtered transactions from database",
          "getFilteredTransactions",
          e
      );
    } catch (Exception e) {
      log.error("Unexpected error retrieving filtered transactions", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error retrieving filtered transactions",
          "getFilteredTransactions",
          e
      );
    }
  }
  
  /**
   * Checks if the filter is empty (no criteria provided)
   * 
   * @param filter TransactionFilterRequest to check
   * @return true if filter is empty, false otherwise
   */
  private boolean isEmptyFilter(TransactionFilterRequest filter) {
    return filter == null ||
           (filter.getCategory() == null &&
            filter.getType() == null &&
            filter.getCurrency() == null &&
            filter.getTransactionMethod() == null &&
            filter.getMinAmount() == null &&
            filter.getMaxAmount() == null &&
            filter.getStartDate() == null &&
            filter.getEndDate() == null &&
            filter.getDescription() == null &&
            filter.getCreatedAfter() == null &&
            filter.getCreatedBefore() == null &&
            filter.getUpdatedAfter() == null &&
            filter.getUpdatedBefore() == null);
  }

  /**
   * Creates a new transaction for the authenticated user
   *
   * @param request Transaction data
   * @return ResponseEntity containing the created transaction
   * @throws TransactionServiceException if creation fails
   */
  public ResponseEntity<Transaction> createTransaction(TransactionRequest request) {
    try {
      User user = authUser.getCurrentUser();
      
      if (user.getId() == null) {
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.USER_ERROR,
            "User ID not available for transaction creation",
            "createTransaction"
        );
      }
      
      com.expensia.backend.utils.TransactionEnums.Currency currency;
      try {
        currency = com.expensia.backend.utils.TransactionEnums.Currency.valueOf(request.getCurrency());
      } catch (IllegalArgumentException e) {
        log.warn("Invalid currency '{}', defaulting to INR", request.getCurrency());
        currency = com.expensia.backend.utils.TransactionEnums.Currency.INR;
      }
      
      com.expensia.backend.utils.TransactionEnums.TransactionMethod transactionMethod;
      try {
        transactionMethod = com.expensia.backend.utils.TransactionEnums.TransactionMethod.valueOf(request.getTransactionMethod());
      } catch (IllegalArgumentException e) {
        log.warn("Invalid transaction method '{}', defaulting to CREDIT_CARD", request.getTransactionMethod());
        transactionMethod = com.expensia.backend.utils.TransactionEnums.TransactionMethod.CREDIT_CARD;
      }

      Transaction transaction = Transaction.builder()
          .userId(new ObjectId(user.getId()))
          .amount(request.getAmount())
          .description(request.getDescription())
          .date(request.getDate() != null ? request.getDate() : LocalDate.now())
          .category(request.getCategory())
          .type(request.getType())
          .currency(currency)
          .notes(request.getNotes())
          .transactionMethod(transactionMethod)
          .build();

      Transaction savedTransaction = transactionRepository.save(transaction);
      return ResponseEntity.ok(savedTransaction);
      
    } catch (TransactionServiceException e) {
      // Re-throw custom exceptions without wrapping
      throw e;
    } catch (IllegalArgumentException e) {
      log.error("Invalid data for transaction creation", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.VALIDATION_ERROR,
          "Invalid transaction data: " + e.getMessage(),
          "createTransaction",
          e
      );
    } catch (DataAccessException e) {
      log.error("Database error during transaction creation", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to save transaction to database",
          "createTransaction",
          e
      );
    } catch (Exception e) {
      log.error("Unexpected error creating transaction", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error creating transaction",
          "createTransaction",
          e
      );
    }
  }
}
