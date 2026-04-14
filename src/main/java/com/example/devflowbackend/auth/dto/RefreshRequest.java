package com.example.devflowbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "Refresh token cannot be empty") String refreshToken) {
}
