package com.example.devflowbackend.workspaces.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /api/workspaces/{workspaceId}}.
 *
 * <p>Partial update semantics: {@code name = null} means "leave unchanged".
 * Sending an empty body is a pointless but valid no-op. If {@code name} is
 * present it must be non-empty — send {@code null} to opt out instead. The
 * personal workspace may be renamed; the {@code is_personal} flag is the
 * invariant keeper, not the name.</p>
 */
public record UpdateWorkspaceRequest(
        @Size(min = 1, max = 100, message = "name must be 1-100 characters")
        String name
) {
}
