package com.expensia.backend.model;

import com.expensia.backend.utils.Currency;
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

    private ObjectId userId;
    private String description;
    private double amount;
    private Currency currency;
    private Date date;
    private String category;
    private String type;
    private String notes;
}
