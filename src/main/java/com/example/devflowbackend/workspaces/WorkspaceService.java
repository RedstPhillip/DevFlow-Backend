package com.example.devflowbackend.workspaces;

import com.example.devflowbackend.common.ApiException;
import com.example.devflowbackend.model.UserEntity;
import com.example.devflowbackend.model.WorkspaceEntity;
import com.example.devflowbackend.model.WorkspaceMemberView;
import com.example.devflowbackend.model.WorkspaceRole;
import com.example.devflowbackend.repository.UserRepository;
import com.example.devflowbackend.repository.WorkspaceRepository;
import com.example.devflowbackend.workspaces.dto.CreateWorkspaceRequest;
import com.example.devflowbackend.workspaces.dto.JoinWorkspaceRequest;
import com.example.devflowbackend.workspaces.dto.UpdateWorkspaceRequest;
import com.example.devflowbackend.workspaces.dto.WorkspaceMemberResponse;
import com.example.devflowbackend.workspaces.dto.WorkspaceResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    /**
     * Confusable-free alphabet for invite codes — no I, O, 0, or 1, so users
     * reading a code aloud don't mis-type it. 32 characters → 32^8 ≈ 1.1 × 10^12
     * possibilities, collision-proof for anything short of an adversarial load.
     */
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 5;
    private static final String PERSONAL_WORKSPACE_NAME = "Persönlich";

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public WorkspaceService(WorkspaceRepository workspaceRepository, UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<WorkspaceResponse> getWorkspacesForUser(long userId) {
        return workspaceRepository.findAllByUserIdOrdered(userId).stream()
                .map(w -> toResponse(w, userId))
                .toList();
    }

    public WorkspaceResponse getWorkspace(long currentUserId, long workspaceId) {
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace not found"));
        requireMember(workspaceId, currentUserId);
        return toResponse(workspace, currentUserId);
    }

    public List<WorkspaceMemberResponse> getMembers(long currentUserId, long workspaceId) {
        // 404 first, then 403 — same ordering as the chat endpoints.
        if (workspaceRepository.findById(workspaceId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        requireMember(workspaceId, currentUserId);
        return workspaceRepository.findMembers(workspaceId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    @Transactional
    public WorkspaceResponse createWorkspace(long currentUserId, CreateWorkspaceRequest request) {
        Instant now = Instant.now();
        String code = generateUniqueInviteCode();
        WorkspaceEntity created = workspaceRepository.create(
                request.name(),
                currentUserId,
                code,
                false,
                now
        );
        workspaceRepository.addMember(created.id(), currentUserId, WorkspaceRole.OWNER, now);
        return toResponse(created, currentUserId);
    }

    /**
     * Called from {@code AuthService.register()} inside the same transaction.
     * Creates the per-user "Persönlich" workspace so every user starts with
     * at least one workspace — the invariant the rest of the service relies
     * on (e.g. the K7 fallback in {@code ChatService.createGroupChat}).
     */
    @Transactional
    public WorkspaceEntity createPersonalWorkspace(long userId) {
        Instant now = Instant.now();
        String code = generateUniqueInviteCode();
        WorkspaceEntity created = workspaceRepository.create(
                PERSONAL_WORKSPACE_NAME,
                userId,
                code,
                true,
                now
        );
        workspaceRepository.addMember(created.id(), userId, WorkspaceRole.OWNER, now);
        return created;
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(long currentUserId, long workspaceId, UpdateWorkspaceRequest request) {
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace not found"));
        requireOwner(workspaceId, currentUserId);

        if (request.name() != null) {
            workspaceRepository.updateName(workspaceId, request.name());
        }

        WorkspaceEntity updated = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Workspace disappeared during update"));
        return toResponse(updated, currentUserId);
    }

    @Transactional
    public WorkspaceResponse joinWorkspace(long currentUserId, JoinWorkspaceRequest request) {
        String normalized = request.inviteCode().toUpperCase();
        WorkspaceEntity workspace = workspaceRepository.findByInviteCode(normalized)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invite code not recognized"));

        if (workspaceRepository.isMember(workspace.id(), currentUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Already a member of this workspace");
        }

        try {
            workspaceRepository.addMember(workspace.id(), currentUserId, WorkspaceRole.MEMBER, Instant.now());
        } catch (DataIntegrityViolationException ex) {
            // Race between the isMember check and the insert — treat as 409
            // rather than bubbling a 500.
            throw new ApiException(HttpStatus.CONFLICT, "Already a member of this workspace");
        }
        return toResponse(workspace, currentUserId);
    }

    @Transactional
    public void removeMember(long currentUserId, long workspaceId, long targetUserId) {
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace not found"));

        boolean isSelf = currentUserId == targetUserId;
        if (isSelf) {
            // Leave-self path. Non-members can't leave; owners can't leave
            // either (would orphan the workspace) — for personal workspaces
            // the 400 makes the invariant explicit.
            WorkspaceRole callerRole = workspaceRepository.findRole(workspaceId, currentUserId)
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Not a member of this workspace"));
            if (callerRole == WorkspaceRole.OWNER) {
                if (workspace.isPersonal()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Personal workspace cannot be left");
                }
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Owner cannot leave the workspace; delete it instead");
            }
            int rows = workspaceRepository.removeMember(workspaceId, currentUserId);
            if (rows == 0) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Not a member of this workspace");
            }
            return;
        }

        // Kick path — only the OWNER may remove someone else.
        requireOwner(workspaceId, currentUserId);
        WorkspaceRole targetRole = workspaceRepository.findRole(workspaceId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User is not a member of this workspace"));
        if (targetRole == WorkspaceRole.OWNER) {
            // Only one owner exists and currentUserId != targetUserId, so this
            // branch is effectively unreachable in 2b. Keep it as a defensive
            // guard for the future (co-owners / owner transfer).
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Owner cannot be removed; delete the workspace instead");
        }
        int rows = workspaceRepository.removeMember(workspaceId, targetUserId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User is not a member of this workspace");
        }
    }

    // -------------------------------------------------------------------------
    // Auth / membership helpers used by this service *and* by ChatService (K7).
    // -------------------------------------------------------------------------

    /** Throws 403 if the user is not a member. Returns their role otherwise. */
    public WorkspaceRole requireMember(long workspaceId, long userId) {
        return workspaceRepository.findRole(workspaceId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Not a member of this workspace"));
    }

    /** Throws 403 unless the user is the OWNER of the workspace. */
    public void requireOwner(long workspaceId, long userId) {
        WorkspaceRole role = requireMember(workspaceId, userId);
        if (role != WorkspaceRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the workspace owner may perform this action");
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private WorkspaceResponse toResponse(WorkspaceEntity workspace, long currentUserId) {
        int memberCount = workspaceRepository.countMembers(workspace.id());
        String roleName = workspaceRepository.findRole(workspace.id(), currentUserId)
                .map(WorkspaceRole::name)
                .orElse(null);
        return new WorkspaceResponse(
                workspace.id(),
                workspace.name(),
                workspace.ownerId(),
                workspace.inviteCode(),
                workspace.isPersonal(),
                workspace.createdAt(),
                memberCount,
                roleName
        );
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMemberView view) {
        return new WorkspaceMemberResponse(
                view.userId(),
                view.username(),
                view.role().name(),
                view.joinedAt()
        );
    }

    // -------------------------------------------------------------------------
    // Invite-code generation
    // -------------------------------------------------------------------------

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = generateInviteCode();
            if (!workspaceRepository.isInviteCodeTaken(candidate)) {
                return candidate;
            }
        }
        // 5 successive collisions at 32^8 ≈ 10^12 means something is wrong with
        // SecureRandom, not with the caller — 500 rather than looping forever.
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not generate a unique invite code");
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_ALPHABET.charAt(random.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    // Silence unused-parameter warning — kept for future use (e.g. owner
    // transfer bookkeeping).
    @SuppressWarnings("unused")
    private UserEntity findUserOr404(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }
}
