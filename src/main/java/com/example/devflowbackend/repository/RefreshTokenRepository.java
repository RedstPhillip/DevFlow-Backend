package com.example.devflowbackend.repository;

import com.example.devflowbackend.model.RefreshTokenEntity;
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
public class RefreshTokenRepository {

    private static final RowMapper<RefreshTokenEntity> TOKEN_ROW_MAPPER = RefreshTokenRepository::mapToken;

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RefreshTokenEntity create(long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO refresh_tokens(user_id, token_hash, expires_at, created_at, revoked) VALUES (?, ?, ?, ?, false)",
                    new String[]{"id"}
            );
            ps.setLong(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.setTimestamp(4, Timestamp.from(createdAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return new RefreshTokenEntity(id, userId, tokenHash, expiresAt, createdAt, false);
    }

    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        List<RefreshTokenEntity> tokens = jdbcTemplate.query(
                "SELECT id, user_id, token_hash, expires_at, created_at, revoked FROM refresh_tokens WHERE token_hash = ?",
                TOKEN_ROW_MAPPER,
                tokenHash
        );
        return tokens.stream().findFirst();
    }

    public void revokeById(long id) {
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = true WHERE id = ?", id);
    }

    private static RefreshTokenEntity mapToken(ResultSet rs, int rowNum) throws SQLException {
        return new RefreshTokenEntity(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("token_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("revoked")
        );
    }
}
