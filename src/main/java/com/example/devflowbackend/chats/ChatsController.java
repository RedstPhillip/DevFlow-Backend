package com.example.devflowbackend.chats;

import com.example.devflowbackend.chats.dto.AddMemberRequest;
import com.example.devflowbackend.chats.dto.ChatResponse;
import com.example.devflowbackend.chats.dto.CreateDmRequest;
import com.example.devflowbackend.chats.dto.CreateGroupChatRequest;
import com.example.devflowbackend.chats.dto.DmChatResponse;
import com.example.devflowbackend.chats.dto.UpdateGroupChatRequest;
import com.example.devflowbackend.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(
            Authentication authentication,
            @Valid @RequestBody CreateGroupChatRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        ChatResponse created = chatService.createGroupChat(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{chatId}")
    public ChatResponse updateGroupChat(
            Authentication authentication,
            @PathVariable long chatId,
            @Valid @RequestBody UpdateGroupChatRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        return chatService.updateGroupChat(user.id(), chatId, request);
    }

    @PostMapping("/{chatId}/members")
    public ResponseEntity<ChatResponse> addMember(
            Authentication authentication,
            @PathVariable long chatId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        ChatResponse updated = chatService.addMember(user.id(), chatId, request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @DeleteMapping("/{chatId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable long chatId,
            @PathVariable long userId
    ) {
        var user = currentUserProvider.require(authentication);
        chatService.removeMember(user.id(), chatId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{chatId}/leave")
    public ResponseEntity<Void> leaveGroupChat(
            Authentication authentication,
            @PathVariable long chatId
    ) {
        var user = currentUserProvider.require(authentication);
        chatService.leaveGroupChat(user.id(), chatId);
        return ResponseEntity.noContent().build();
    }
}
