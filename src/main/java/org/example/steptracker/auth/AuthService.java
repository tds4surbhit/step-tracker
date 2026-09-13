package org.example.steptracker.auth;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.auth.dto.*;
import org.example.steptracker.common.ConflictException;
import org.example.steptracker.common.TokenHasher;
import org.example.steptracker.common.UnauthorizedException;
import org.example.steptracker.goal.GoalService;
import org.example.steptracker.user.User;
import org.example.steptracker.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final GoalService goalService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user = userRepository.save(user);
        goalService.createDefaultGoal(user.getId());
        return issueTokens(user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return issueTokens(user.getId());
    }

    @Transactional
    public AccessTokenResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }
        String accessToken = jwtService.generateAccessToken(token.getUserId());
        return new AccessTokenResponse(accessToken, jwtProperties.getAccessTokenTtlSeconds());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(request.refreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse issueTokens(UUID userId) {
        String accessToken = jwtService.generateAccessToken(userId);
        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken(
                userId,
                TokenHasher.sha256(rawRefreshToken),
                Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds())
        );
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(userId, accessToken, rawRefreshToken, jwtProperties.getAccessTokenTtlSeconds());
    }
}
