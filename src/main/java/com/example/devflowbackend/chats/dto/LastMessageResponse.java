package com.example.devflowbackend.chats.dto;

import java.time.Instant;

public record LastMessageResponse(long id, String content, long transmitterId, Instant createdAt) {
}
