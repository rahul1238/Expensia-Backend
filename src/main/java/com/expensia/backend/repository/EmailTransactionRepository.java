package com.expensia.backend.repository;

import com.expensia.backend.model.EmailTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmailTransactionRepository extends MongoRepository<EmailTransaction, String> {
    List<EmailTransaction> findByUserId(String userId);
    Optional<EmailTransaction> findByUserIdAndMessageId(String userId, String messageId);
    Optional<EmailTransaction> findByUniqueHash(String uniqueHash);
}
