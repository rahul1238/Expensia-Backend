package com.expensia.backend.service;

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
          e);
    } catch (DataAccessException e) {
      log.error("Database error retrieving transactions", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to retrieve transactions from database",
          "getAllTransactions",
          e);
    } catch (Exception e) {
      log.error("Unexpected error retrieving transactions", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error retrieving transactions",
          "getAllTransactions",
          e);
    }
  }

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
          e);
    } catch (DataAccessException e) {
      log.error("Database error during transaction filtering", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to retrieve filtered transactions from database",
          "getFilteredTransactions",
          e);
    } catch (Exception e) {
      log.error("Unexpected error retrieving filtered transactions", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error retrieving filtered transactions",
          "getFilteredTransactions",
          e);
    }
  }

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

  public ResponseEntity<Transaction> createTransaction(TransactionRequest request) {
    try {
      User user = authUser.getCurrentUser();

      if (user.getId() == null) {
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.USER_ERROR,
            "User ID not available for transaction creation",
            "createTransaction");
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
        transactionMethod = com.expensia.backend.utils.TransactionEnums.TransactionMethod
            .valueOf(request.getTransactionMethod());
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
      throw e;
    } catch (IllegalArgumentException e) {
      log.error("Invalid data for transaction creation", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.VALIDATION_ERROR,
          "Invalid transaction data: " + e.getMessage(),
          "createTransaction",
          e);
    } catch (DataAccessException e) {
      log.error("Database error during transaction creation", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to save transaction to database",
          "createTransaction",
          e);
    } catch (Exception e) {
      log.error("Unexpected error creating transaction", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error creating transaction",
          "createTransaction",
          e);
    }
  }

  public ResponseEntity<Transaction> updateTransaction(String id, TransactionRequest request) {
    try {
      User user = authUser.getCurrentUser();

      if (user.getId() == null) {
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.USER_ERROR,
            "User ID not available for transaction update",
            "updateTransaction");
      }

      try {
        new ObjectId(id);
      } catch (IllegalArgumentException e) {
        log.error("Invalid transaction ID format: {}", id, e);
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.VALIDATION_ERROR,
            "Invalid transaction ID format",
            "updateTransaction",
            e);
      }

      Transaction existingTransaction = transactionRepository.findById(id)
          .orElseThrow(() -> new TransactionServiceException(
              TransactionServiceException.TransactionErrorType.TRANSACTION_NOT_FOUND,
              "Transaction not found with ID: " + id,
              "updateTransaction"));

      if (!existingTransaction.getUserId().equals(new ObjectId(user.getId()))) {
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
            "Transaction does not belong to current user",
            "updateTransaction");
      }

      com.expensia.backend.utils.TransactionEnums.Currency currency;
      try {
        currency = com.expensia.backend.utils.TransactionEnums.Currency.valueOf(request.getCurrency());
      } catch (IllegalArgumentException e) {
        log.warn("Invalid currency '{}', keeping existing currency", request.getCurrency());
        currency = existingTransaction.getCurrency();
      }

      com.expensia.backend.utils.TransactionEnums.TransactionMethod transactionMethod;
      try {
        transactionMethod = com.expensia.backend.utils.TransactionEnums.TransactionMethod
            .valueOf(request.getTransactionMethod());
      } catch (IllegalArgumentException e) {
        log.warn("Invalid transaction method '{}', keeping existing method", request.getTransactionMethod());
        transactionMethod = existingTransaction.getTransactionMethod();
      }
      existingTransaction.setAmount(request.getAmount());
      existingTransaction.setDescription(request.getDescription());
      existingTransaction.setDate(request.getDate() != null ? request.getDate() : existingTransaction.getDate());
      existingTransaction.setCategory(request.getCategory());
      existingTransaction.setType(request.getType());
      existingTransaction.setCurrency(currency);
      existingTransaction.setNotes(request.getNotes());
      existingTransaction.setTransactionMethod(transactionMethod);

      Transaction savedTransaction = transactionRepository.save(existingTransaction);
      return ResponseEntity.ok(savedTransaction);

    } catch (TransactionServiceException e) {
      throw e;
    } catch (IllegalArgumentException e) {
      log.error("Invalid data for transaction update", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.VALIDATION_ERROR,
          "Invalid transaction data: " + e.getMessage(),
          "updateTransaction",
          e);
    } catch (DataAccessException e) {
      log.error("Database error during transaction update", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to update transaction in database",
          "updateTransaction",
          e);
    } catch (Exception e) {
      log.error("Unexpected error updating transaction", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error updating transaction",
          "updateTransaction",
          e);
    }
  }

  public ResponseEntity<String> deleteTransaction(String id) {
    try {
      log.debug("Starting transaction deletion for ID: {}", id);
      
      User user = authUser.getCurrentUser();
      log.debug("Current user: {}", user != null ? user.getId() : "null");

      if (user == null || user.getId() == null) {
        log.error("User or User ID is null during transaction deletion");
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.USER_ERROR,
            "User ID not available for transaction deletion",
            "deleteTransaction");
      }

      try {
        new ObjectId(id);
        log.debug("Transaction ID format is valid: {}", id);
      } catch (IllegalArgumentException e) {
        log.error("Invalid transaction ID format: {}", id, e);
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.VALIDATION_ERROR,
            "Invalid transaction ID format",
            "deleteTransaction",
            e);
      }

      Transaction existingTransaction = transactionRepository.findById(id)
          .orElseThrow(() -> {
            log.error("Transaction not found with ID: {}", id);
            return new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.TRANSACTION_NOT_FOUND,
                "Transaction not found with ID: " + id,
                "deleteTransaction");
          });

      log.debug("Found transaction: {} owned by user: {}", id, existingTransaction.getUserId());
      log.debug("Current user ObjectId: {}", new ObjectId(user.getId()));

      if (!existingTransaction.getUserId().equals(new ObjectId(user.getId()))) {
        log.error("Transaction {} does not belong to current user {}. Transaction owner: {}", 
                 id, user.getId(), existingTransaction.getUserId());
        throw new TransactionServiceException(
            TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
            "Transaction does not belong to current user",
            "deleteTransaction");
      }
      
      log.debug("Authorization check passed. Deleting transaction: {}", id);
      
      transactionRepository.deleteById(id);
      
      log.info("Successfully deleted transaction: {}", id);
      return ResponseEntity.ok("Transaction deleted successfully");

    } catch (TransactionServiceException e) {
      throw e;
    } catch (DataAccessException e) {
      log.error("Database error during transaction deletion", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
          "Failed to delete transaction from database",
          "deleteTransaction",
          e);
    } catch (Exception e) {
      log.error("Unexpected error deleting transaction", e);
      throw new TransactionServiceException(
          TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
          "Unexpected error deleting transaction",
          "deleteTransaction",
          e);
    }
  }
}
