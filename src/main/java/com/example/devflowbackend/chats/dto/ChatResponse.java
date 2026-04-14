package com.example.devflowbackend.chats.dto;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        long id,
        String type,
        Instant createdAt,
        List<ParticipantResponse> participants,
        LastMessageResponse lastMessage
) {
}
