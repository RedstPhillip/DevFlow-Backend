CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL
);

-- A workspace is the top-level container. Every user gets one auto-created
-- "Persönlich" workspace during register() (is_personal = TRUE) and may own/join
-- more. Rollen-Enum ist Phase-2b-MVP zweistufig: OWNER | MEMBER. ADMIN +
-- workspace_groups (Ordner) kommen in späteren Phasen.
--
-- owner_id ist nullable + ON DELETE SET NULL: verwaister Workspace statt
-- kaskadiertem Daten-Killer. Personal-Workspaces werden heute nicht extra
-- geschützt, da User-Delete-Endpoint ebenfalls noch nicht existiert — beides
-- in HANDOVER festhalten.
CREATE TABLE IF NOT EXISTS workspaces (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    owner_id BIGINT NULL,
    invite_code CHAR(8) NOT NULL UNIQUE,
    is_personal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_workspaces_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,             -- 'OWNER' | 'MEMBER'
    joined_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (workspace_id, user_id),
    INDEX idx_workspace_members_user_id (user_id),
    CONSTRAINT fk_workspace_members_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- DM rows use only id / type = 'DM' / created_at. GROUP rows additionally carry
-- name, owner_id, member_add_policy (Phase 2a.5) and an optional workspace_id
-- (Phase 2b). Keeping everything in one table avoids a per-type join while
-- leaving DM paths untouched. DMs always have workspace_id = NULL by
-- convention.
--
-- NOTE on migrations: this file uses CREATE TABLE IF NOT EXISTS. A pre-existing
-- chats table WITHOUT the trailing columns will NOT be altered by this script.
-- For a local dev DB, drop/recreate. For production in-place migration:
--   ALTER TABLE chats
--       ADD COLUMN name VARCHAR(100) NULL,
--       ADD COLUMN owner_id BIGINT NULL,
--       ADD COLUMN member_add_policy VARCHAR(20) NULL,
--       ADD COLUMN workspace_id BIGINT NULL,
--       ADD CONSTRAINT fk_chats_owner
--           FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL,
--       ADD CONSTRAINT fk_chats_workspace
--           FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE SET NULL;
--   CREATE INDEX idx_chats_workspace_id ON chats(workspace_id);
CREATE TABLE IF NOT EXISTS chats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    name VARCHAR(100) NULL,
    owner_id BIGINT NULL,
    member_add_policy VARCHAR(20) NULL,
    workspace_id BIGINT NULL,
    INDEX idx_chats_workspace_id (workspace_id),
    CONSTRAINT fk_chats_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_chats_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS chat_participants (
    chat_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP(3) NOT NULL,
    INDEX idx_chat_participants_user_id (user_id),
    PRIMARY KEY (chat_id, user_id),
    CONSTRAINT fk_chat_participants_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_participants_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dm_pairs (
    chat_id BIGINT PRIMARY KEY,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    CONSTRAINT fk_dm_pairs_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_pairs_user1 FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_pairs_user2 FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_dm_pairs UNIQUE (user1_id, user2_id),
    CONSTRAINT chk_dm_pairs_order CHECK (user1_id < user2_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chat_id BIGINT NOT NULL,
    transmitter_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    INDEX idx_messages_chat_created_at (chat_id, created_at, id),
    CONSTRAINT fk_messages_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_transmitter FOREIGN KEY (transmitter_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_refresh_tokens_user_id (user_id),
    INDEX idx_refresh_tokens_expires_at (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
