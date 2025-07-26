package com.expensia.backend.controller;

import com.expensia.backend.dto.TransactionRequest;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.provider.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionController {

  private final TransactionService transactionService;

  @GetMapping
  public List<Transaction> getTransactions() {
    return transactionService.getAllTransactions();
  }

  @PostMapping("/create")
  public Transaction createTransaction(@RequestBody TransactionRequest request) {
    return transactionService.createTransaction(request);
  }
}
