package com.example.devflowbackend.workspaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code POST /api/workspaces/join}.
 *
 * <p>The confusable-free alphabet is {@code ABCDEFGHJKLMNPQRSTUVWXYZ23456789}
 * (no I/O/0/1). The validator accepts any case — the service normalizes to
 * upper-case before looking up. 8 characters is fixed; changing the length
 * breaks old invites.</p>
 */
public record JoinWorkspaceRequest(
        @NotBlank(message = "inviteCode must not be blank")
        @Pattern(
                regexp = "[A-Za-z0-9]{8}",
                message = "inviteCode must be exactly 8 alphanumeric characters"
        )
        String inviteCode
) {
}
