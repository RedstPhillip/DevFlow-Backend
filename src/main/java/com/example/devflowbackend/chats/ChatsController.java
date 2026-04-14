package com.example.devflowbackend.chats;

import com.example.devflowbackend.chats.dto.ChatResponse;
import com.example.devflowbackend.chats.dto.CreateDmRequest;
import com.example.devflowbackend.chats.dto.DmChatResponse;
import com.example.devflowbackend.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats")
public class ChatsController {

    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    public ChatsController(ChatService chatService, CurrentUserProvider currentUserProvider) {
        this.chatService = chatService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<ChatResponse> getChats(Authentication authentication) {
        var user = currentUserProvider.require(authentication);
        return chatService.getChatsForUser(user.id());
    }

    @PostMapping("/dm")
    public ResponseEntity<DmChatResponse> createDm(
            Authentication authentication,
            @Valid @RequestBody CreateDmRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        ChatService.CreateDmResult result = chatService.createOrFindDm(user.id(), request.otherUserId());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.chat());
        }
        return ResponseEntity.ok(result.chat());
    }
}
