package com.expensia.backend.controller;

import com.expensia.backend.dto.TransactionFilterRequest;
import com.expensia.backend.dto.TransactionRequest;
import com.expensia.backend.exception.TransactionServiceException;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.provider.TransactionService;
import com.expensia.backend.utils.TransactionEnums;
import com.expensia.backend.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionController {

  private final TransactionService transactionService;

  /**
   * Retrieves transactions for the authenticated user with optional filtering
   * @param category Filter by category
   * @param type Filter by type
   * @param currency Filter by currency
   * @param transactionMethod Filter by transaction method
   * @param minAmount Filter by minimum amount
   * @param maxAmount Filter by maximum amount
   * @param startDate Filter by start date (ISO format: yyyy-MM-dd)
   * @param endDate Filter by end date (ISO format: yyyy-MM-dd)
   * @param description Filter by description (partial match)
   * @param createdAfter Filter by creation time after (ISO format: yyyy-MM-ddTHH:mm:ss)
   * @param createdBefore Filter by creation time before (ISO format: yyyy-MM-ddTHH:mm:ss)
   * @param updatedAfter Filter by update time after (ISO format: yyyy-MM-ddTHH:mm:ss)
   * @param updatedBefore Filter by update time before (ISO format: yyyy-MM-ddTHH:mm:ss)
   * @return List of transactions (filtered if parameters provided, all if no parameters)
   */
  @GetMapping
  public ResponseEntity<?> getTransactions(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String transactionMethod,
      @RequestParam(required = false) Double minAmount,
      @RequestParam(required = false) Double maxAmount,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String createdAfter,
      @RequestParam(required = false) String createdBefore,
      @RequestParam(required = false) String updatedAfter,
      @RequestParam(required = false) String updatedBefore) {
    
    log.debug("Processing transaction request with query parameters");
    
    try {
      TransactionFilterRequest filter = TransactionFilterRequest.builder()
          .category(category)
          .type(type)
          .currency(ValidationUtils.parseEnum(currency, TransactionEnums.Currency.class))
          .transactionMethod(ValidationUtils.parseEnum(transactionMethod, TransactionEnums.TransactionMethod.class))
          .minAmount(minAmount)
          .maxAmount(maxAmount)
          .startDate(ValidationUtils.parseDate(startDate))
          .endDate(ValidationUtils.parseDate(endDate))
          .description(description)
          .createdAfter(ValidationUtils.parseDateTime(createdAfter))
          .createdBefore(ValidationUtils.parseDateTime(createdBefore))
          .updatedAfter(ValidationUtils.parseDateTime(updatedAfter))
          .updatedBefore(ValidationUtils.parseDateTime(updatedBefore))
          .build();
      
      List<Transaction> transactions = transactionService.getFilteredTransactions(filter);
      return ResponseEntity.ok(transactions);
      
    } catch (IllegalArgumentException e) {
      log.warn("Invalid parameter value: {}", e.getMessage());
      return ResponseEntity.badRequest()
          .body("Invalid parameter value: " + e.getMessage());
    } catch (TransactionServiceException e) {
      log.error("Transaction service error: {}", e.getDetailedMessage());
      
      // Map specific error types to appropriate HTTP status codes
      HttpStatus status = switch (e.getErrorType()) {
        case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
        case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
        case TRANSACTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
        case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        case BUSINESS_LOGIC_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
        case EXTERNAL_SERVICE_ERROR -> HttpStatus.BAD_GATEWAY;
        default -> HttpStatus.INTERNAL_SERVER_ERROR;
      };
      
      return ResponseEntity.status(status)
          .body("Transaction error: " + e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error retrieving transactions", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Unexpected error retrieving transactions: " + e.getMessage());
    }
  }

  /**
   * Creates a new transaction
   * @param request Transaction request data
   * @return ResponseEntity containing the created transaction
   */
  @PostMapping("/create")
  public ResponseEntity<?> createTransaction(@Valid @RequestBody TransactionRequest request) {
    log.debug("Processing transaction creation request");
    
    try {
      return transactionService.createTransaction(request);
    } catch (TransactionServiceException e) {
      log.error("Transaction service error during creation: {}", e.getDetailedMessage());
      
      // Map specific error types to appropriate HTTP status codes
      HttpStatus status = switch (e.getErrorType()) {
        case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
        case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
        case USER_ERROR -> HttpStatus.BAD_REQUEST;
        case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        case BUSINESS_LOGIC_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
        default -> HttpStatus.INTERNAL_SERVER_ERROR;
      };
      
      return ResponseEntity.status(status)
          .body("Transaction creation error: " + e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error creating transaction", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Unexpected error creating transaction: " + e.getMessage());
    }
  }

}
