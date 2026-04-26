package com.example.devflowbackend.repository;

import com.example.devflowbackend.model.WorkspaceEntity;
import com.example.devflowbackend.model.WorkspaceMemberView;
import com.example.devflowbackend.model.WorkspaceRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@code workspaces} and {@code workspace_members}. Mirrors the
 * style of {@link ChatRepository}: JdbcTemplate + KeyHolder inserts, nullable
 * columns via {@code rs.wasNull()}, composite-PK membership table co-located
 * here rather than split into a second repository.
 */
@Repository
public class WorkspaceRepository {

    private static final RowMapper<WorkspaceEntity> WORKSPACE_ROW_MAPPER = WorkspaceRepository::mapWorkspace;

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------------------------
    // workspaces
    // -------------------------------------------------------------------------

    public WorkspaceEntity create(String name, long ownerId, String inviteCode, boolean isPersonal, Instant createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO workspaces(name, owner_id, invite_code, is_personal, created_at) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, name);
            ps.setLong(2, ownerId);
            ps.setString(3, inviteCode);
            ps.setBoolean(4, isPersonal);
            ps.setTimestamp(5, Timestamp.from(createdAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return new WorkspaceEntity(id, name, ownerId, inviteCode, isPersonal, createdAt);
    }

    public Optional<WorkspaceEntity> findById(long workspaceId) {
        List<WorkspaceEntity> rows = jdbcTemplate.query(
                "SELECT id, name, owner_id, invite_code, is_personal, created_at FROM workspaces WHERE id = ?",
                WORKSPACE_ROW_MAPPER,
                workspaceId
        );
        return rows.stream().findFirst();
    }

    /**
     * List every workspace the given user is a member of. Personal workspaces
     * are surfaced first (so the UI's default selection falls back to the
     * user's own namespace), then the rest ordered by creation time.
     */
    public List<WorkspaceEntity> findAllByUserIdOrdered(long userId) {
        return jdbcTemplate.query(
                """
                SELECT w.id, w.name, w.owner_id, w.invite_code, w.is_personal, w.created_at
                FROM workspaces w
                JOIN workspace_members wm ON wm.workspace_id = w.id
                WHERE wm.user_id = ?
                ORDER BY w.is_personal DESC, w.created_at ASC, w.id ASC
                """,
                WORKSPACE_ROW_MAPPER,
                userId
        );
    }

    public Optional<WorkspaceEntity> findByInviteCode(String code) {
        List<WorkspaceEntity> rows = jdbcTemplate.query(
                "SELECT id, name, owner_id, invite_code, is_personal, created_at FROM workspaces WHERE UPPER(invite_code) = UPPER(?)",
                WORKSPACE_ROW_MAPPER,
                code
        );
        return rows.stream().findFirst();
    }

    public boolean isInviteCodeTaken(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspaces WHERE invite_code = ?",
                Integer.class,
                code
        );
        return count != null && count > 0;
    }

    /** Returns rows affected. 0 means the workspace didn't exist. */
    public int updateName(long workspaceId, String name) {
        return jdbcTemplate.update(
                "UPDATE workspaces SET name = ? WHERE id = ?",
                name,
                workspaceId
        );
    }

    /**
     * Find the id of the user's personal workspace (is_personal = TRUE). Used
     * by the K7 group-chat fallback when a client POSTs to
     * {@code /api/chats/group} without a {@code workspaceId}.
     */
    public Optional<Long> findPersonalWorkspaceId(long userId) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT w.id FROM workspaces w
                JOIN workspace_members wm ON wm.workspace_id = w.id
                WHERE wm.user_id = ? AND w.is_personal = TRUE
                ORDER BY w.id ASC
                """,
                (rs, rowNum) -> rs.getLong("id"),
                userId
        );
        return ids.stream().findFirst();
    }

    // -------------------------------------------------------------------------
    // workspace_members
    // -------------------------------------------------------------------------

    public void addMember(long workspaceId, long userId, WorkspaceRole role, Instant joinedAt) {
        jdbcTemplate.update(
                "INSERT INTO workspace_members(workspace_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)",
                workspaceId,
                userId,
                role.name(),
                Timestamp.from(joinedAt)
        );
    }

    /** Returns rows affected. 0 means the user wasn't a member. */
    public int removeMember(long workspaceId, long userId) {
        return jdbcTemplate.update(
                "DELETE FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                workspaceId,
                userId
        );
    }

    public boolean isMember(long workspaceId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                Integer.class,
                workspaceId,
                userId
        );
        return count != null && count > 0;
    }

    public Optional<WorkspaceRole> findRole(long workspaceId, long userId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                (rs, rowNum) -> rs.getString("role"),
                workspaceId,
                userId
        );
        return roles.stream().filter(Objects::nonNull).findFirst().map(WorkspaceRole::valueOf);
    }

    public int countMembers(long workspaceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ?",
                Integer.class,
                workspaceId
        );
        return count == null ? 0 : count;
    }

    public List<WorkspaceMemberView> findMembers(long workspaceId) {
        return jdbcTemplate.query(
                """
                SELECT u.id AS user_id, u.username, wm.role, wm.joined_at
                FROM workspace_members wm
                JOIN users u ON u.id = wm.user_id
                WHERE wm.workspace_id = ?
                ORDER BY wm.role = 'OWNER' DESC, wm.joined_at ASC, u.id ASC
                """,
                (rs, rowNum) -> new WorkspaceMemberView(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        WorkspaceRole.valueOf(rs.getString("role")),
                        rs.getTimestamp("joined_at").toInstant()
                ),
                workspaceId
        );
    }

    // -------------------------------------------------------------------------
    // RowMapper helpers
    // -------------------------------------------------------------------------

    private static WorkspaceEntity mapWorkspace(ResultSet rs, int rowNum) throws SQLException {
        long ownerIdRaw = rs.getLong("owner_id");
        Long ownerId = rs.wasNull() ? null : ownerIdRaw;
        return new WorkspaceEntity(
                rs.getLong("id"),
                rs.getString("name"),
                ownerId,
                rs.getString("invite_code"),
                rs.getBoolean("is_personal"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
