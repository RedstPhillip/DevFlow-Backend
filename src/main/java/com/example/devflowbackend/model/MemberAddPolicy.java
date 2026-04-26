package com.example.devflowbackend.model;

/**
 * Who may add new members to a group chat.
 *
 * <ul>
 *   <li>{@link #OWNER_ONLY} — only the owner adds members. This is the safer
 *       default and matches the typical "admin-managed group" pattern.</li>
 *   <li>{@link #ALL_MEMBERS} — any current participant may add members, for
 *       open / collaborative groups.</li>
 * </ul>
 *
 * For DMs the policy is {@code null} — DMs are inherently two-party and do
 * not support adding members at all.
 *
 * <p>The enum name is the wire value: the client {@code Chat} model already
 * ships with matching {@code POLICY_OWNER_ONLY} / {@code POLICY_ALL_MEMBERS}
 * string constants, so serialization is a straight {@code .name()} / {@code valueOf(...)}.</p>
 */
public enum MemberAddPolicy {
    OWNER_ONLY,
    ALL_MEMBERS
}
