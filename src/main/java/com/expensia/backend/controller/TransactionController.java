package com.expensia.backend.controller;

import com.expensia.backend.model.Transaction;
import com.expensia.backend.provider.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
