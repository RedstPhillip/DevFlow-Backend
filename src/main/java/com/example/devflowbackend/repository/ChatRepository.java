package com.example.devflowbackend.repository;

import com.example.devflowbackend.model.ChatEntity;
import com.example.devflowbackend.model.ChatType;
import com.example.devflowbackend.model.MemberAddPolicy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {

    private static final RowMapper<ChatEntity> CHAT_ROW_MAPPER = ChatRepository::mapChat;

    private final JdbcTemplate jdbcTemplate;

    public ChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ChatEntity> findById(long id) {
        List<ChatEntity> chats = jdbcTemplate.query(
                "SELECT id, type, created_at, name, owner_id, member_add_policy, workspace_id FROM chats WHERE id = ?",
                CHAT_ROW_MAPPER,
                id
        );
        return chats.stream().findFirst();
    }

    public List<ChatEntity> findAllByUserIdOrderedByLastMessage(long userId) {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.type, c.created_at, c.name, c.owner_id, c.member_add_policy, c.workspace_id
                FROM chats c
                JOIN chat_participants cp ON cp.chat_id = c.id
                LEFT JOIN messages m ON m.chat_id = c.id
                WHERE cp.user_id = ?
                GROUP BY c.id, c.type, c.created_at, c.name, c.owner_id, c.member_add_policy, c.workspace_id
                ORDER BY COALESCE(MAX(m.created_at), c.created_at) DESC, c.id DESC
                """,
                CHAT_ROW_MAPPER,
                userId
        );
    }

    public Optional<Long> findDmChatId(long user1Id, long user2Id) {
        long min = Math.min(user1Id, user2Id);
        long max = Math.max(user1Id, user2Id);
        List<Long> ids = jdbcTemplate.query(
                "SELECT chat_id FROM dm_pairs WHERE user1_id = ? AND user2_id = ?",
                (rs, rowNum) -> rs.getLong("chat_id"),
                min,
                max
        );
        return ids.stream().findFirst();
    }

    public ChatEntity create(ChatType type, Instant createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO chats(type, created_at) VALUES (?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, type.name());
            ps.setTimestamp(2, Timestamp.from(createdAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return new ChatEntity(id, type, createdAt, null, null, null, null);
    }

    /**
     * Insert a new GROUP chat row. {@code ownerId} is required; the owner is
     * NOT added as a participant here — the caller is responsible for adding
     * the owner (and any other initial members) via {@link #addParticipant}.
     * Kept orthogonal so this method mirrors {@link #create} in scope.
     *
     * <p>{@code workspaceId} is nullable during the Phase 2b migration window:
     * the service layer resolves a null to the caller's personal workspace
     * before calling this method, so in steady state this argument is always
     * populated for GROUP rows. DMs still bypass this method entirely.</p>
     */
    public ChatEntity createGroup(String name, long ownerId, MemberAddPolicy policy, Long workspaceId, Instant createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO chats(type, created_at, name, owner_id, member_add_policy, workspace_id) VALUES (?, ?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, ChatType.GROUP.name());
            ps.setTimestamp(2, Timestamp.from(createdAt));
            ps.setString(3, name);
            ps.setLong(4, ownerId);
            ps.setString(5, policy.name());
            if (workspaceId == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, workspaceId);
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return new ChatEntity(id, ChatType.GROUP, createdAt, name, ownerId, policy, workspaceId);
    }

    /**
     * Patch a GROUP chat's name and/or policy. Nulls are skipped (partial update
     * — the client sends {@code null} to leave a field unchanged). If both
     * parameters are null this is a no-op.
     */
    public void updateGroup(long chatId, String name, MemberAddPolicy policy) {
        if (name == null && policy == null) return;
        StringBuilder sql = new StringBuilder("UPDATE chats SET ");
        List<Object> args = new ArrayList<>(3);
        boolean first = true;
        if (name != null) {
            sql.append("name = ?");
            args.add(name);
            first = false;
        }
        if (policy != null) {
            if (!first) sql.append(", ");
            sql.append("member_add_policy = ?");
            args.add(policy.name());
        }
        sql.append(" WHERE id = ?");
        args.add(chatId);
        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public Optional<ChatType> findType(long chatId) {
        List<String> types = jdbcTemplate.query(
                "SELECT type FROM chats WHERE id = ?",
                (rs, rowNum) -> rs.getString("type"),
                chatId
        );
        return types.stream().findFirst().map(ChatType::valueOf);
    }

    public Optional<Long> findOwnerId(long chatId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT owner_id FROM chats WHERE id = ?",
                (rs, rowNum) -> {
                    long v = rs.getLong("owner_id");
                    return rs.wasNull() ? null : v;
                },
                chatId
        );
        return ids.stream().filter(Objects::nonNull).findFirst();
    }

    public int countParticipants(long chatId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_participants WHERE chat_id = ?",
                Integer.class,
                chatId
        );
        return count == null ? 0 : count;
    }

    public void addParticipant(long chatId, long userId, Instant joinedAt) {
        jdbcTemplate.update(
                "INSERT INTO chat_participants(chat_id, user_id, joined_at) VALUES (?, ?, ?)",
                chatId,
                userId,
                Timestamp.from(joinedAt)
        );
    }

    /** Returns rows affected. 0 means the user wasn't a participant. */
    public int removeParticipant(long chatId, long userId) {
        return jdbcTemplate.update(
                "DELETE FROM chat_participants WHERE chat_id = ? AND user_id = ?",
                chatId,
                userId
        );
    }

    /**
     * Delete the chat row. The FK cascade defined in {@code schema.sql} removes
     * rows from {@code chat_participants}, {@code messages}, and {@code dm_pairs}.
     */
    public int deleteChat(long chatId) {
        return jdbcTemplate.update("DELETE FROM chats WHERE id = ?", chatId);
    }

    public void addDmPair(long chatId, long user1Id, long user2Id) {
        long min = Math.min(user1Id, user2Id);
        long max = Math.max(user1Id, user2Id);
        jdbcTemplate.update(
                "INSERT INTO dm_pairs(chat_id, user1_id, user2_id) VALUES (?, ?, ?)",
                chatId,
                min,
                max
        );
    }

    public boolean isParticipant(long chatId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_participants WHERE chat_id = ? AND user_id = ?",
                Integer.class,
                chatId,
                userId
        );
        return count != null && count > 0;
    }

    public List<ParticipantRecord> findParticipantsByChatId(long chatId) {
        return jdbcTemplate.query(
                """
                SELECT u.id, u.username
                FROM chat_participants cp
                JOIN users u ON u.id = cp.user_id
                WHERE cp.chat_id = ?
                ORDER BY u.id ASC
                """,
                (rs, rowNum) -> new ParticipantRecord(rs.getLong("id"), rs.getString("username")),
                chatId
        );
    }

    private static ChatEntity mapChat(ResultSet rs, int rowNum) throws SQLException {
        long ownerIdRaw = rs.getLong("owner_id");
        Long ownerId = rs.wasNull() ? null : ownerIdRaw;
        String policyStr = rs.getString("member_add_policy");
        MemberAddPolicy policy = policyStr == null ? null : MemberAddPolicy.valueOf(policyStr);
        long workspaceIdRaw = rs.getLong("workspace_id");
        Long workspaceId = rs.wasNull() ? null : workspaceIdRaw;
        return new ChatEntity(
                rs.getLong("id"),
                ChatType.valueOf(rs.getString("type")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("name"),
                ownerId,
                policy,
                workspaceId
        );
    }

    public record ParticipantRecord(long id, String username) {
    }
}
