package com.example.devflowbackend.chats;

import com.example.devflowbackend.chats.dto.ChatResponse;
import com.example.devflowbackend.chats.dto.CreateGroupChatRequest;
import com.example.devflowbackend.chats.dto.DmChatResponse;
import com.example.devflowbackend.chats.dto.LastMessageResponse;
import com.example.devflowbackend.chats.dto.ParticipantResponse;
import com.example.devflowbackend.chats.dto.UpdateGroupChatRequest;
import com.example.devflowbackend.common.ApiException;
import com.example.devflowbackend.model.ChatEntity;
import com.example.devflowbackend.model.ChatType;
import com.example.devflowbackend.model.MemberAddPolicy;
import com.example.devflowbackend.model.MessageEntity;
import com.example.devflowbackend.model.UserEntity;
import com.example.devflowbackend.repository.ChatRepository;
import com.example.devflowbackend.repository.MessageRepository;
import com.example.devflowbackend.repository.UserRepository;
import com.example.devflowbackend.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;

    public ChatService(
            ChatRepository chatRepository,
            UserRepository userRepository,
            MessageRepository messageRepository,
            WorkspaceRepository workspaceRepository
    ) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public List<ChatResponse> getChatsForUser(long userId) {
        return chatRepository.findAllByUserIdOrderedByLastMessage(userId).stream()
                .map(this::toChatResponse)
                .toList();
    }

    @Transactional
    public CreateDmResult createOrFindDm(long currentUserId, long otherUserId) {
        if (currentUserId == otherUserId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot create DM with yourself");
        }

        UserEntity otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        Long existingChatId = chatRepository.findDmChatId(currentUserId, otherUser.id()).orElse(null);
        if (existingChatId != null) {
            ChatEntity existing = chatRepository.findById(existingChatId)
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Chat not found"));
            return new CreateDmResult(toDmChatResponse(existing), false);
        }

        Instant now = Instant.now();
        try {
            ChatEntity created = chatRepository.create(ChatType.DM, now);
            chatRepository.addParticipant(created.id(), currentUserId, now);
            chatRepository.addParticipant(created.id(), otherUser.id(), now);
            chatRepository.addDmPair(created.id(), currentUserId, otherUser.id());
            return new CreateDmResult(toDmChatResponse(created), true);
        } catch (DataIntegrityViolationException ex) {
            Long existingId = chatRepository.findDmChatId(currentUserId, otherUser.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create chat"));
            ChatEntity existing = chatRepository.findById(existingId)
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Chat not found"));
            return new CreateDmResult(toDmChatResponse(existing), false);
        }
    }

    @Transactional
    public ChatResponse createGroupChat(long currentUserId, CreateGroupChatRequest request) {
        MemberAddPolicy policy = parsePolicy(request.memberAddPolicy());

        // Resolve the workspace this group chat lives in.
        // - If the client omits workspaceId (legacy pre-2b-UI frontend), fall
        //   back to the caller's personal workspace and log a WARN so the
        //   gap is visible. Every registered user is guaranteed to have a
        //   personal workspace via AuthService.register().
        // - If the client provides workspaceId, the caller must be a member;
        //   otherwise we refuse with 403 (don't leak existence of foreign
        //   workspaces).
        Long requestedWorkspaceId = request.workspaceId();
        long resolvedWorkspaceId;
        if (requestedWorkspaceId == null) {
            log.warn("POST /api/chats/group without workspaceId — falling back to personal workspace for user={}", currentUserId);
            resolvedWorkspaceId = workspaceRepository.findPersonalWorkspaceId(currentUserId)
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Caller has no personal workspace"));
        } else {
            if (!workspaceRepository.isMember(requestedWorkspaceId, currentUserId)) {
                throw new ApiException(HttpStatus.FORBIDDEN,
                        "Not a member of the target workspace");
            }
            resolvedWorkspaceId = requestedWorkspaceId;
        }

        // Filter the caller out in case the client included them, deduplicate,
        // preserve insertion order so the Owner lands first and the rest keep
        // whatever order the client used (nice for display).
        Set<Long> memberSet = new LinkedHashSet<>();
        for (Long id : request.memberIds()) {
            if (id != null && id != currentUserId) memberSet.add(id);
        }
        if (memberSet.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "memberIds must contain at least one other user");
        }

        // Validate every candidate resolves to an existing user AND is a
        // member of the target workspace before any INSERT runs — otherwise
        // the FK violation would bubble up as a 500, and we'd also leak
        // cross-workspace membership.
        for (Long id : memberSet) {
            if (userRepository.findById(id).isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "User not found: " + id);
            }
            if (!workspaceRepository.isMember(resolvedWorkspaceId, id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "User is not a member of the target workspace: " + id);
            }
        }

        Instant now = Instant.now();
        ChatEntity created = chatRepository.createGroup(request.name(), currentUserId, policy, resolvedWorkspaceId, now);
        chatRepository.addParticipant(created.id(), currentUserId, now);
        for (Long id : memberSet) {
            chatRepository.addParticipant(created.id(), id, now);
        }
        return toChatResponse(created);
    }

    @Transactional
    public ChatResponse updateGroupChat(long currentUserId, long chatId, UpdateGroupChatRequest request) {
        ChatEntity chat = requireGroupChat(chatId);
        requireOwner(chat, currentUserId);

        MemberAddPolicy policy = request.memberAddPolicy() == null
                ? null
                : parsePolicy(request.memberAddPolicy());
        chatRepository.updateGroup(chatId, request.name(), policy);

        ChatEntity updated = chatRepository.findById(chatId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Chat disappeared during update"));
        return toChatResponse(updated);
    }

    @Transactional
    public ChatResponse addMember(long currentUserId, long chatId, long targetUserId) {
        ChatEntity chat = requireGroupChat(chatId);

        boolean isOwner = chat.ownerId() != null && chat.ownerId() == currentUserId;
        boolean isMember = chatRepository.isParticipant(chatId, currentUserId);
        boolean openPolicy = chat.memberAddPolicy() == MemberAddPolicy.ALL_MEMBERS;
        if (!isOwner && !(openPolicy && isMember)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Not allowed to add members to this chat");
        }

        if (userRepository.findById(targetUserId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found: " + targetUserId);
        }
        if (chatRepository.isParticipant(chatId, targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "User is already a member");
        }

        chatRepository.addParticipant(chatId, targetUserId, Instant.now());
        return toChatResponse(chat);
    }

    @Transactional
    public void removeMember(long currentUserId, long chatId, long targetUserId) {
        ChatEntity chat = requireGroupChat(chatId);
        requireOwner(chat, currentUserId);

        if (chat.ownerId() != null && chat.ownerId() == targetUserId) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Owner cannot be removed; use the leave endpoint to dissolve the group chat");
        }
        int rows = chatRepository.removeParticipant(chatId, targetUserId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User is not a member of this chat");
        }
    }

    @Transactional
    public void leaveGroupChat(long currentUserId, long chatId) {
        ChatEntity chat = requireGroupChat(chatId);
        if (!chatRepository.isParticipant(chatId, currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a participant of this chat");
        }

        if (chat.ownerId() != null && chat.ownerId() == currentUserId) {
            // Owner leaves → dissolve the whole group. FK cascade wipes
            // chat_participants, messages, dm_pairs.
            chatRepository.deleteChat(chatId);
        } else {
            chatRepository.removeParticipant(chatId, currentUserId);
        }
    }

    public void ensureParticipant(long chatId, long userId) {
        if (!chatRepository.isParticipant(chatId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a participant of this chat");
        }
    }

    private ChatEntity requireGroupChat(long chatId) {
        ChatEntity chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chat not found"));
        if (chat.type() != ChatType.GROUP) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This operation is only valid for group chats");
        }
        return chat;
    }

    private void requireOwner(ChatEntity chat, long userId) {
        if (chat.ownerId() == null || chat.ownerId() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the owner may perform this action");
        }
    }

    private MemberAddPolicy parsePolicy(String value) {
        try {
            return MemberAddPolicy.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "memberAddPolicy must be OWNER_ONLY or ALL_MEMBERS");
        }
    }

    private ChatResponse toChatResponse(ChatEntity chat) {
        List<ParticipantResponse> participants = chatRepository.findParticipantsByChatId(chat.id()).stream()
                .map(p -> new ParticipantResponse(p.id(), p.username()))
                .toList();
        LastMessageResponse lastMessage = messageRepository.findLastByChatId(chat.id())
                .map(this::toLastMessage)
                .orElse(null);
        String policy = chat.memberAddPolicy() == null ? null : chat.memberAddPolicy().name();
        return new ChatResponse(
                chat.id(),
                chat.type().name(),
                chat.createdAt(),
                participants,
                lastMessage,
                chat.name(),
                chat.ownerId(),
                policy,
                chat.workspaceId()
        );
    }

    private DmChatResponse toDmChatResponse(ChatEntity chat) {
        List<ParticipantResponse> participants = chatRepository.findParticipantsByChatId(chat.id()).stream()
                .map(p -> new ParticipantResponse(p.id(), p.username()))
                .toList();
        return new DmChatResponse(chat.id(), chat.type().name(), chat.createdAt(), participants);
    }

    private LastMessageResponse toLastMessage(MessageEntity message) {
        return new LastMessageResponse(
                message.id(),
                message.content(),
                message.transmitterId(),
                message.createdAt()
        );
    }

    public record CreateDmResult(DmChatResponse chat, boolean created) {
    }
}
