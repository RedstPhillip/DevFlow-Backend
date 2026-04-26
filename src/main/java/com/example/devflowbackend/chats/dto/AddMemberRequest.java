package com.example.devflowbackend.chats.dto;

import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /api/chats/{chatId}/members}.
 */
public record AddMemberRequest(
        @Positive(message = "userId must be positive") long userId
) {
}
