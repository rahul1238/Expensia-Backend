package com.expensia.backend.repository;

import com.expensia.backend.model.Token;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TokenRepository extends MongoRepository<Token,String> {
  Optional<Token> findByAccessToken(@NotNull String accessToken);
}
