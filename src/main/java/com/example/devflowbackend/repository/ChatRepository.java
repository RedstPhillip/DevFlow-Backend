package com.example.devflowbackend.repository;

import com.example.devflowbackend.model.ChatEntity;
import com.example.devflowbackend.model.ChatType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
                "SELECT id, type, created_at FROM chats WHERE id = ?",
                CHAT_ROW_MAPPER,
                id
        );
        return chats.stream().findFirst();
    }

    public List<ChatEntity> findAllByUserIdOrderedByLastMessage(long userId) {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.type, c.created_at
                FROM chats c
                JOIN chat_participants cp ON cp.chat_id = c.id
                LEFT JOIN messages m ON m.chat_id = c.id
                WHERE cp.user_id = ?
                GROUP BY c.id, c.type, c.created_at
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
        return new ChatEntity(id, type, createdAt);
    }

    public void addParticipant(long chatId, long userId, Instant joinedAt) {
        jdbcTemplate.update(
                "INSERT INTO chat_participants(chat_id, user_id, joined_at) VALUES (?, ?, ?)",
                chatId,
                userId,
                Timestamp.from(joinedAt)
        );
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
        return new ChatEntity(
                rs.getLong("id"),
                ChatType.valueOf(rs.getString("type")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public record ParticipantRecord(long id, String username) {
    }
}
