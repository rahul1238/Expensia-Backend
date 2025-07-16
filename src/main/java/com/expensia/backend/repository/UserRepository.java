package com.expensia.backend.repository;

import com.expensia.backend.model.User;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User,String>{

    boolean existsByUsername(@NotNull String username);

    Optional<User> findByEmail(@NotNull String email);
}
