package com.example.devflowbackend.chats.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The per-chat payload returned by {@code GET /api/chats} and by every group-chat
 * write endpoint.
 *
 * <p>Fields {@code name}, {@code ownerId}, {@code memberAddPolicy}, and
 * {@code workspaceId} are populated only for {@code type = GROUP}. For DMs
 * they are {@code null} and are suppressed from the JSON output via
 * {@link JsonInclude}, so the DM wire format stays byte-compatible with the
 * pre-parity contract.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        long id,
        String type,
        Instant createdAt,
        List<ParticipantResponse> participants,
        LastMessageResponse lastMessage,
        String name,
        Long ownerId,
        String memberAddPolicy,
        Long workspaceId
) {
}
