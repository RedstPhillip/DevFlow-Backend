package com.example.devflowbackend.model;

/**
 * The role a user holds within a single workspace.
 *
 * <p>Phase 2b MVP keeps this deliberately two-valued:
 * <ul>
 *   <li>{@link #OWNER} — creator of the workspace; may rename it and remove
 *       other members. There is always exactly one OWNER per workspace.</li>
 *   <li>{@link #MEMBER} — everybody else who joined via invite code.</li>
 * </ul>
 *
 * <p>A future ADMIN tier (rename / kick without delete) is called for in
 * {@code api-endpoints.md} but explicitly deferred. The enum name is the wire
 * value — workspace responses serialize via {@code .name()} / {@code valueOf(...)}.
 */
public enum WorkspaceRole {
    OWNER,
    MEMBER
}
