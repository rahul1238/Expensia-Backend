package com.expensia.backend.repository;

import com.expensia.backend.model.Transaction;
import jakarta.validation.constraints.NotNull;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction,String> {
    List<Transaction> findByUserId(@NotNull ObjectId userId);
}
