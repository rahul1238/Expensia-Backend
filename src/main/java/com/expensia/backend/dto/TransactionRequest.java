package com.expensia.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransactionRequest {
  @NotNull
  private String description;
  @NotNull
  private double amount;
  @NotNull
  private String currency; // Changed from Currency enum to String for frontend compatibility
  @NotNull
  private String category;
  @NotNull
  private String type;
  private String notes;
  private Date date;
  @NotNull
  private String transactionMethod; // Changed from TransactionMethod enum to String for frontend compatibility
}
