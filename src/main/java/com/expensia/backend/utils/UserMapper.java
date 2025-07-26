package com.expensia.backend.utils;

import com.expensia.backend.dto.UserDTO;
import com.expensia.backend.model.User;

public class UserMapper {
  public static UserDTO toDto(User user) {
    if (user == null) return null;

    return UserDTO.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .age(user.getAge())
        .phoneNumber(user.getPhoneNumber())
        .occupation(user.getOccupation())
        .build();
  }
}
