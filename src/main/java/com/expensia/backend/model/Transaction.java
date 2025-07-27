package com.expensia.backend.model;

import com.expensia.backend.utils.TransactionEnums.Currency;
import com.expensia.backend.utils.TransactionEnums.TransactionMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document("transactions")
public class Transaction {
    @Id
    private String id;
    @NotNull
    private ObjectId userId;
    private String description;
    @NotNull
    private double amount;
    @NotNull
    private Currency currency;
    private Date date;
    private String category;
    private String type;
    private String notes;
    @NotNull
    private TransactionMethod transactionMethod;
}
