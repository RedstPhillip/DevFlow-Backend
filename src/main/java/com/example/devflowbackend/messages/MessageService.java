package com.example.devflowbackend.messages;

import com.example.devflowbackend.common.ApiException;
import com.example.devflowbackend.messages.dto.MessageResponse;
import com.example.devflowbackend.model.MessageEntity;
import com.example.devflowbackend.repository.MessageRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public List<MessageResponse> getMessages(long chatId, Long afterId, Integer limit) {
        int resolvedLimit = normalizeLimit(limit);
        if (afterId != null && afterId < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "afterId must be non-negative");
        }
        return messageRepository.findByChatIdAfterId(chatId, afterId, resolvedLimit).stream()
                .map(MessageService::toResponse)
                .toList();
    }

    public MessageResponse createMessage(long chatId, long transmitterId, String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Content cannot be empty");
        }
        MessageEntity created = messageRepository.create(chatId, transmitterId, normalized, Instant.now());
        return toResponse(created);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        if (limit <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "limit must be positive");
        }
        return Math.min(limit, 200);
    }

    private static MessageResponse toResponse(MessageEntity message) {
        return new MessageResponse(
                message.id(),
                message.chatId(),
                message.transmitterId(),
                message.content(),
                message.createdAt()
        );
    }
}
