package com.expensia.backend.controller;

import com.expensia.backend.dto.TransactionRequest;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.provider.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
   * Retrieves all transactions for the authenticated user
   * @return List of transactions
   */
  @GetMapping
  public List<Transaction> getTransactions() {
    return transactionService.getAllTransactions();
  }

  /**
   * Creates a new transaction
   * @param request Transaction request data
   * @return ResponseEntity containing the created transaction
   */
  @PostMapping("/create")
  public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
    log.debug("Processing transaction creation request");
    return transactionService.createTransaction(request);
  }
}
