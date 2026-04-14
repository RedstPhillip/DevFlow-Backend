package com.example.devflowbackend.users;

import com.example.devflowbackend.common.ApiException;
import com.example.devflowbackend.model.UserEntity;
import com.example.devflowbackend.repository.UserRepository;
import com.example.devflowbackend.users.dto.UserResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserService::toResponse).toList();
    }

    public UserResponse getUserById(long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(user);
    }

    public static UserResponse toResponse(UserEntity user) {
        return new UserResponse(user.id(), user.username(), user.createdAt());
    }
}
