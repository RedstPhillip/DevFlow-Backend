package com.example.devflowbackend.model;

import java.time.Instant;

/**
 * A row in the {@code workspace_members} join table. Composite primary key is
 * ({@code workspaceId}, {@code userId}) — no surrogate id by design.
 */
public record WorkspaceMemberEntity(
        long workspaceId,
        long userId,
        WorkspaceRole role,
        Instant joinedAt
) {
}
