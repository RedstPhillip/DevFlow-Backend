package com.example.devflowbackend.auth;

import com.example.devflowbackend.auth.dto.AuthResponse;
import com.example.devflowbackend.auth.dto.AuthUserResponse;
import com.example.devflowbackend.auth.dto.TokenRefreshResponse;
import com.example.devflowbackend.common.ApiException;
import com.example.devflowbackend.model.RefreshTokenEntity;
import com.example.devflowbackend.model.UserEntity;
import com.example.devflowbackend.repository.RefreshTokenRepository;
import com.example.devflowbackend.repository.UserRepository;
import com.example.devflowbackend.security.JwtProperties;
import com.example.devflowbackend.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already taken");
        }

        Instant now = Instant.now();
        UserEntity user;
        try {
            user = userRepository.create(normalizedUsername, passwordEncoder.encode(password), now);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already taken");
        }

        return issueAuthResponse(user);
    }

    public AuthResponse login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        UserEntity user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return issueAuthResponse(user);
    }

    @Transactional
    public TokenRefreshResponse refresh(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        if (existing.revoked() || existing.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        UserEntity user = userRepository.findById(existing.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        refreshTokenRepository.revokeById(existing.id());

        JwtService.TokenDetails accessToken = jwtService.createAccessToken(user.id(), user.username());
        String newRefreshToken = generateRefreshToken();
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(jwtProperties.getRefreshTokenDays(), ChronoUnit.DAYS);
        refreshTokenRepository.create(user.id(), hashToken(newRefreshToken), refreshExpiresAt, now);

        return new TokenRefreshResponse(accessToken.token(), newRefreshToken, accessToken.expiresAt());
    }

    private AuthResponse issueAuthResponse(UserEntity user) {
        JwtService.TokenDetails accessToken = jwtService.createAccessToken(user.id(), user.username());

        String refreshToken = generateRefreshToken();
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(jwtProperties.getRefreshTokenDays(), ChronoUnit.DAYS);
        refreshTokenRepository.create(user.id(), hashToken(refreshToken), refreshExpiresAt, now);

        AuthUserResponse authUserResponse = new AuthUserResponse(user.id(), user.username(), user.createdAt());
        return new AuthResponse(accessToken.token(), refreshToken, accessToken.expiresAt(), authUserResponse);
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
