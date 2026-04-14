package com.example.devflowbackend.chats.dto;

import jakarta.validation.constraints.Positive;

public record CreateDmRequest(@Positive(message = "otherUserId must be positive") long otherUserId) {
}
