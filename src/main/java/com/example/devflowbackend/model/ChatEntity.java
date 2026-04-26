package com.example.devflowbackend.model;

import java.time.Instant;

/**
 * A row in the {@code chats} table.
 *
 * <p>DMs carry only {@code id}, {@code type = DM}, and {@code createdAt}; the
 * trailing fields ({@code name}, {@code ownerId}, {@code memberAddPolicy},
 * {@code workspaceId}) are {@code null} for DMs and populated for group
 * chats. {@code workspaceId} is nullable even for GROUPs during the Phase
 * 2b → 2b-UI migration window: when a legacy client POSTs a group chat
 * without {@code workspaceId}, {@link com.example.devflowbackend.chats.ChatService}
 * falls back to the caller's personal workspace (and logs a WARN). Once the
 * frontend always sends {@code workspaceId}, the nullable stays as a safety
 * net for the DM case.</p>
 */
public record ChatEntity(
        long id,
        ChatType type,
        Instant createdAt,
        String name,
        Long ownerId,
        MemberAddPolicy memberAddPolicy,
        Long workspaceId
) {
}
