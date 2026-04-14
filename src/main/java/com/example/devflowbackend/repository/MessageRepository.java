package com.example.devflowbackend.repository;

import com.example.devflowbackend.model.MessageEntity;
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
public class MessageRepository {

    private static final RowMapper<MessageEntity> MESSAGE_ROW_MAPPER = MessageRepository::mapMessage;

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MessageEntity create(long chatId, long transmitterId, String content, Instant createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO messages(chat_id, transmitter_id, content, created_at) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, chatId);
            ps.setLong(2, transmitterId);
            ps.setString(3, content);
            ps.setTimestamp(4, Timestamp.from(createdAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return new MessageEntity(id, chatId, transmitterId, content, createdAt);
    }

    public List<MessageEntity> findByChatIdAfterId(long chatId, Long afterId, int limit) {
        if (afterId == null) {
            return jdbcTemplate.query(
                    """
                    SELECT id, chat_id, transmitter_id, content, created_at
                    FROM messages
                    WHERE chat_id = ?
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    """,
                    MESSAGE_ROW_MAPPER,
                    chatId,
                    limit
            );
        }

        return jdbcTemplate.query(
                """
                SELECT id, chat_id, transmitter_id, content, created_at
                FROM messages
                WHERE chat_id = ? AND id > ?
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """,
                MESSAGE_ROW_MAPPER,
                chatId,
                afterId,
                limit
        );
    }

    public Optional<MessageEntity> findLastByChatId(long chatId) {
        List<MessageEntity> messages = jdbcTemplate.query(
                """
                SELECT id, chat_id, transmitter_id, content, created_at
                FROM messages
                WHERE chat_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                MESSAGE_ROW_MAPPER,
                chatId
        );
        return messages.stream().findFirst();
    }

    private static MessageEntity mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new MessageEntity(
                rs.getLong("id"),
                rs.getLong("chat_id"),
                rs.getLong("transmitter_id"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
