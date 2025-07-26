package com.expensia.backend.repository;

import com.expensia.backend.model.Token;
import jakarta.validation.constraints.NotNull;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends MongoRepository<Token,String> {
  Optional<Token> findByAccessToken(@NotNull String accessToken);
  Optional<Token> findByRefreshTokenAndRevokedFalse(String refreshToken);
  List<Token> findAllByUserIdAndRevokedFalse(ObjectId userId);
}
