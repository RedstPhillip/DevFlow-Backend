package com.example.devflowbackend.workspaces;

import com.example.devflowbackend.security.CurrentUserProvider;
import com.example.devflowbackend.workspaces.dto.CreateWorkspaceRequest;
import com.example.devflowbackend.workspaces.dto.JoinWorkspaceRequest;
import com.example.devflowbackend.workspaces.dto.UpdateWorkspaceRequest;
import com.example.devflowbackend.workspaces.dto.WorkspaceMemberResponse;
import com.example.devflowbackend.workspaces.dto.WorkspaceResponse;
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
@RequestMapping("/api/workspaces")
public class WorkspacesController {

    private final WorkspaceService workspaceService;
    private final CurrentUserProvider currentUserProvider;

    public WorkspacesController(WorkspaceService workspaceService, CurrentUserProvider currentUserProvider) {
        this.workspaceService = workspaceService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<WorkspaceResponse> getWorkspaces(Authentication authentication) {
        var user = currentUserProvider.require(authentication);
        return workspaceService.getWorkspacesForUser(user.id());
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            Authentication authentication,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        WorkspaceResponse created = workspaceService.createWorkspace(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(
            Authentication authentication,
            @PathVariable long workspaceId
    ) {
        var user = currentUserProvider.require(authentication);
        return workspaceService.getWorkspace(user.id(), workspaceId);
    }

    @PutMapping("/{workspaceId}")
    public WorkspaceResponse updateWorkspace(
            Authentication authentication,
            @PathVariable long workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        return workspaceService.updateWorkspace(user.id(), workspaceId, request);
    }

    @PostMapping("/join")
    public ResponseEntity<WorkspaceResponse> joinWorkspace(
            Authentication authentication,
            @Valid @RequestBody JoinWorkspaceRequest request
    ) {
        var user = currentUserProvider.require(authentication);
        WorkspaceResponse joined = workspaceService.joinWorkspace(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(joined);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> getMembers(
            Authentication authentication,
            @PathVariable long workspaceId
    ) {
        var user = currentUserProvider.require(authentication);
        return workspaceService.getMembers(user.id(), workspaceId);
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable long workspaceId,
            @PathVariable long userId
    ) {
        var user = currentUserProvider.require(authentication);
        workspaceService.removeMember(user.id(), workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}
