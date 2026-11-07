package com.abhik.ledgerlens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Email is required.")
    private String email;
    @NotBlank
    @Size(min = 6, message = "Password must be at least 6 characters long.")
    @Pattern(regexp = "^(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).*$",
            message = "Password must contain at least one number and one special character.")
    private String password;

}
