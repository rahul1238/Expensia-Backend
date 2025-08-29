package com.expensia.backend.model;

import com.expensia.backend.utils.TransactionEnums.Currency;
import com.expensia.backend.utils.TransactionEnums.TransactionMethod;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("emailTransaction")
public class EmailTransaction {
    @Id
    private String id;
    @Indexed
    private String userId;
    // Deterministic fingerprint for deduplication: sha256(userId + amount + date + merchant)
    @Indexed(unique = true, sparse = true)
    private String uniqueHash;
    // Fields aligned with Transaction
    private String description;
    private double amount;
    private Currency currency;
    private LocalDate date;
    private String category;
    private String type; // "credit" | "debit"
    private String notes;
    private TransactionMethod transactionMethod;
    // Email-specific
    private String merchant;
    private String sourceEmail;
    private String messageId;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
