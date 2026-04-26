package com.example.devflowbackend.workspaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Wire format for every workspace write/read endpoint.
 *
 * <p>{@code role} carries the current caller's role within this workspace
 * ({@code "OWNER"} or {@code "MEMBER"}) — important in list contexts so the
 * frontend can gate UI affordances without another round-trip. {@code
 * memberCount} is always populated.</p>
 *
 * <p>{@code @JsonInclude(NON_NULL)} lets the {@code ownerId} disappear from
 * JSON for workspaces where the owner was deleted (future-proofing; in Phase
 * 2b practice this should never happen).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceResponse(
        long id,
        String name,
        Long ownerId,
        String inviteCode,
        boolean isPersonal,
        Instant createdAt,
        int memberCount,
        String role
) {
}
