package com.expensia.backend.dto;

import com.expensia.backend.utils.TransactionEnums.Currency;
import com.expensia.backend.utils.TransactionEnums.TransactionMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionFilterRequest {
    private String category;
    private String type;
    private Currency currency;
    private TransactionMethod transactionMethod;
    private Double minAmount;
    private Double maxAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
    
    // Timestamp filters
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private LocalDateTime updatedAfter;
    private LocalDateTime updatedBefore;
}
