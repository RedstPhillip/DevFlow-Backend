package com.example.devflowbackend.users;

import com.example.devflowbackend.security.CurrentUserProvider;
import com.example.devflowbackend.users.dto.UserResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public UsersController(UserService userService, CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/me")
    public UserResponse getMe(Authentication authentication) {
        var user = currentUserProvider.require(authentication);
        return userService.getUserById(user.id());
    }
}
