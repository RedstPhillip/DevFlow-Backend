package com.example.devflowbackend.messages;

import com.example.devflowbackend.chats.ChatService;
import com.example.devflowbackend.messages.dto.CreateMessageRequest;
import com.example.devflowbackend.messages.dto.MessageResponse;
import com.example.devflowbackend.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats/{chatId}/messages")
public class MessagesController {

    private final MessageService messageService;
    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    public MessagesController(
            MessageService messageService,
            ChatService chatService,
            CurrentUserProvider currentUserProvider
    ) {
        this.messageService = messageService;
        this.chatService = chatService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<MessageResponse> getMessages(
            Authentication authentication,
            @PathVariable long chatId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Integer limit
    ) {
        var user = currentUserProvider.require(authentication);
        chatService.ensureParticipant(chatId, user.id());
        return messageService.getMessages(chatId, afterId, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createMessage(
            Authentication authentication,
            @PathVariable long chatId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        chatService.ensureParticipant(chatId, user.id());
        return messageService.createMessage(chatId, user.id(), request.content());
    }
}
