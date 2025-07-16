package com.expensia.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document("transactions")
public class Transaction {
    private String id;
    private ObjectId userId;
    private String description;
    private double amount;
    private String currency;
    private String date;
    private String category;
    private String type;
    private String notes;
}
