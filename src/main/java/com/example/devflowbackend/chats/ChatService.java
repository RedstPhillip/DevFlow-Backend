package com.example.devflowbackend.chats;

import com.example.devflowbackend.chats.dto.ChatResponse;
import com.example.devflowbackend.chats.dto.DmChatResponse;
import com.example.devflowbackend.chats.dto.LastMessageResponse;
import com.example.devflowbackend.chats.dto.ParticipantResponse;
import com.example.devflowbackend.common.ApiException;
import com.example.devflowbackend.model.ChatEntity;
import com.example.devflowbackend.model.ChatType;
import com.example.devflowbackend.model.MessageEntity;
import com.example.devflowbackend.model.UserEntity;
import com.example.devflowbackend.repository.ChatRepository;
import com.example.devflowbackend.repository.MessageRepository;
import com.example.devflowbackend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ChatService(ChatRepository chatRepository, UserRepository userRepository, MessageRepository messageRepository) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
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

    public void ensureParticipant(long chatId, long userId) {
        if (!chatRepository.isParticipant(chatId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a participant of this chat");
        }
    }

    private ChatResponse toChatResponse(ChatEntity chat) {
        List<ParticipantResponse> participants = chatRepository.findParticipantsByChatId(chat.id()).stream()
                .map(p -> new ParticipantResponse(p.id(), p.username()))
                .toList();
        LastMessageResponse lastMessage = messageRepository.findLastByChatId(chat.id())
                .map(this::toLastMessage)
                .orElse(null);
        return new ChatResponse(chat.id(), chat.type().name(), chat.createdAt(), participants, lastMessage);
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
