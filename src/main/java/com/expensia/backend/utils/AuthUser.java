package com.expensia.backend.utils;

import com.expensia.backend.dto.UserDTO;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthUser {

  private final UserRepository userRepository;

  public User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated() ||
        authentication.getPrincipal().equals("anonymousUser")) {
      throw new RuntimeException("User not authenticated");
    }

    // Try to get the user details from the principal
    if (authentication.getPrincipal() instanceof User) {
      return (User) authentication.getPrincipal();
    } else if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
      // Spring Security's User class
      String email = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
      return userRepository.findByEmail(email)
          .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    // Fallback to using the name (which should be the email)
    String email = authentication.getName();
    log.debug("Looking up user by email: {}", email);
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
