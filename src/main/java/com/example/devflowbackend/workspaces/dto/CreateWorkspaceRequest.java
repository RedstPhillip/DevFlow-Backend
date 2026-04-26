package com.example.devflowbackend.workspaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/workspaces}.
 *
 * <p>The caller automatically becomes the {@code OWNER} — no role field on
 * the request. The invite code is server-generated and returned in the
 * response; there is no way to supply a custom one.</p>
 */
public record CreateWorkspaceRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name
) {
}
