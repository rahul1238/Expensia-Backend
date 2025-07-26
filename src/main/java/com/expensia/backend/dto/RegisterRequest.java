package com.expensia.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RegisterRequest {
    @NotNull
    private String username;
    @NotNull
    private String password;
    @NotNull
    private String email;
    @NotNull
    private String firstName;
    private String lastName;
    private int age;
    @NotNull
    @Size(min=10, max=10, message="Phone number must be 10 digits")
    private String phoneNumber;
    private String occupation;
}
