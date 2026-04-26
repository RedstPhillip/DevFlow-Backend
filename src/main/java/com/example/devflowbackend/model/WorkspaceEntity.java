package com.example.devflowbackend.model;

import java.time.Instant;

/**
 * A row in the {@code workspaces} table.
 *
 * <p>{@code ownerId} is {@code Long} (nullable) because the underlying FK is
 * {@code ON DELETE SET NULL} — deleting the user who owned a workspace leaves
 * the workspace behind rather than wiping everyone's chats with it. In Phase
 * 2b practice {@code ownerId} is always populated; the nullability is a
 * forward compatibility hook for later user-delete support.
 *
 * <p>{@code isPersonal} is {@code true} for the auto-created "Persönlich"
 * workspace that {@code AuthService.register()} produces for every new user.
 * The flag gates delete-protection in the service layer. Rename stays allowed.
 *
 * <p>{@code inviteCode} is an 8-char confusable-free string (alphabet
 * {@code ABCDEFGHJKLMNPQRSTUVWXYZ23456789} — no I/O/0/1). Unique across the
 * whole table. Rotation is out of scope for 2b but the column is sized to
 * allow in-place rewrite later.
 */
public record WorkspaceEntity(
        long id,
        String name,
        Long ownerId,
        String inviteCode,
        boolean isPersonal,
        Instant createdAt
) {
}
