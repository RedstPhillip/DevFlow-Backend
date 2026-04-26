package com.example.devflowbackend.chats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for {@code POST /api/chats/group}.
 *
 * <p>The caller is implicitly the owner and does not need to be in {@code memberIds};
 * the server filters the caller out if present (so the client can send the full
 * selection without tracking who they are). {@code memberIds} must contain at
 * least one other user — the client UI enforces {@code size >= 2}, but the
 * server accepts {@code size >= 1} to avoid being stricter than the contract
 * documented in api-endpoints.md.</p>
 *
 * <p>{@code workspaceId} is <em>optional</em> for backward compatibility with
 * the pre-Phase-2b frontend. When {@code null}, the server falls back to the
 * caller's personal workspace (and logs a WARN). When present, the server
 * verifies that the caller and every entry in {@code memberIds} is a member of
 * that workspace — 403 for the caller, 400 for any non-member id. The
 * 2b-UI will always send this field; once that ships the nullable can be
 * tightened or left as a safety net.</p>
 */
public record CreateGroupChatRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotNull(message = "memberAddPolicy must not be null")
        @Pattern(
                regexp = "OWNER_ONLY|ALL_MEMBERS",
                message = "memberAddPolicy must be OWNER_ONLY or ALL_MEMBERS"
        )
        String memberAddPolicy,

        @NotEmpty(message = "memberIds must not be empty")
        List<@Positive(message = "memberIds must contain only positive ids") Long> memberIds,

        @Positive(message = "workspaceId must be positive if provided")
        Long workspaceId
) {
}
