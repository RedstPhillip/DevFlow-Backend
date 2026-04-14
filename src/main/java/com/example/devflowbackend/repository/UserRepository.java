package com.example.devflowbackend.repository;

import com.example.devflowbackend.model.UserEntity;
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
public class UserRepository {

    private static final RowMapper<UserEntity> USER_ROW_MAPPER = UserRepository::mapUser;

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserEntity> findByUsername(String username) {
        List<UserEntity> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, created_at FROM users WHERE username = ?",
                USER_ROW_MAPPER,
                username
        );
        return users.stream().findFirst();
    }

    public Optional<UserEntity> findById(long id) {
        List<UserEntity> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, created_at FROM users WHERE id = ?",
                USER_ROW_MAPPER,
                id
        );
        return users.stream().findFirst();
    }

    public List<UserEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT id, username, password_hash, created_at FROM users ORDER BY id ASC",
                USER_ROW_MAPPER
        );
    }

    public UserEntity create(String username, String passwordHash, Instant createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO users(username, password_hash, created_at) VALUES (?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setTimestamp(3, Timestamp.from(createdAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return new UserEntity(id, username, passwordHash, createdAt);
    }

    private static UserEntity mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserEntity(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
