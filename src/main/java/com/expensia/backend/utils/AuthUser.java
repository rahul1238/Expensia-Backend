package com.expensia.backend.utils;

import com.expensia.backend.dto.UserDTO;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthUser {

  private final UserRepository userRepository;

  public User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new RuntimeException("User not authenticated");
    }

    Object principal = authentication.getPrincipal();

    if (principal instanceof User) {
      return (User) principal;
    }

    String email = authentication.getName();
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
  }

  public UserDTO getCurrentUserDTO() {
    return UserMapper.toDto(getCurrentUser());
  }

  public String getCurrentUserId() {
    return getCurrentUser().getId();
  }

  public boolean isAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.isAuthenticated() &&
        !(authentication.getPrincipal().equals("anonymousUser"));
  }
}
