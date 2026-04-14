package com.example.devflowbackend.messages.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank(message = "Content cannot be empty")
        @Size(max = 4000, message = "Content is too long")
        String content
) {
}
