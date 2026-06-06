package com.ryn.creativeai.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank
            @Size(min = 8, max = 128, message = "password must contain between 8 and 128 characters")
            String password
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record UserDto(
            String id,
            String email,
            String role
    ) {}

    public record AuthResponse(
            String token,
            UserDto user
    ) {}
}
