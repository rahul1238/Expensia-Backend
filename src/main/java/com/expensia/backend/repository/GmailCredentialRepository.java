package com.expensia.backend.repository;

import com.expensia.backend.model.GmailCredential;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GmailCredentialRepository extends MongoRepository<GmailCredential, String> {
    Optional<GmailCredential> findByUserId(String userId);
    void deleteByUserId(String userId);
}
