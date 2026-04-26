package com.example.devflowbackend.chats.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /api/chats/{chatId}}.
 *
 * <p>Both fields are nullable and represent a partial update: {@code null}
 * means "leave unchanged". This mirrors the frontend {@code ChatService.updateGroupChat}
 * which sends only the fields the user touched. An entirely empty body is a
 * valid (but pointless) no-op rather than a 400.</p>
 *
 * <p>If {@code name} is sent, it must not be an empty string — send {@code null}
 * to leave the name alone. Enforced via {@code @Size(min = 1)}.</p>
 */
public record UpdateGroupChatRequest(
        @Size(min = 1, max = 100, message = "name must be 1-100 characters")
        String name,

        @Pattern(
                regexp = "OWNER_ONLY|ALL_MEMBERS",
                message = "memberAddPolicy must be OWNER_ONLY or ALL_MEMBERS"
        )
        String memberAddPolicy
) {
}
