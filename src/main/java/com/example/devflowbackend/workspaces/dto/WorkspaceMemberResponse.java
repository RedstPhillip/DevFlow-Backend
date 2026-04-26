package com.example.devflowbackend.workspaces.dto;

import java.time.Instant;

/**
 * One row in the response of {@code GET /api/workspaces/{workspaceId}/members}.
 * {@code role} is the enum name as string, matching the rest of the API.
 */
public record WorkspaceMemberResponse(
        long userId,
        String username,
        String role,
        Instant joinedAt
) {
}
