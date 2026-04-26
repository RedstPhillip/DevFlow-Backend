package com.example.devflowbackend.model;

import java.time.Instant;

/**
 * Join-projection for {@code GET /api/workspaces/{id}/members}. Combines the
 * {@code workspace_members} row with the matching {@code users.username} so
 * the controller can respond without a second round-trip.
 */
public record WorkspaceMemberView(
        long userId,
        String username,
        WorkspaceRole role,
        Instant joinedAt
) {
}
