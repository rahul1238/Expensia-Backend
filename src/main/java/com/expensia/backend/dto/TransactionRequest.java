package com.expensia.backend.dto;

import com.expensia.backend.utils.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransactionRequest {
  private String description;
  private double amount;
  private Currency currency;
  private String category;
  private String type;
  private String notes;
}
