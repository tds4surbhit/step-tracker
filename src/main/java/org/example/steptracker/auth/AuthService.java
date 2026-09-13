package org.example.steptracker.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.auth.dto.*;
import org.example.steptracker.common.TokenHasher;
import org.example.steptracker.common.UnauthorizedException;
import org.example.steptracker.goal.GoalService;
import org.example.steptracker.user.User;
import org.example.steptracker.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final GoalService goalService;

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());
        String googleSub = payload.getSubject();

        User user = userRepository.findByGoogleSub(googleSub).orElseGet(() -> {
            User created = new User();
            created.setGoogleSub(googleSub);
            created.setEmail(payload.getEmail());
            created.setDisplayName((String) payload.get("name"));
            created = userRepository.save(created);
            goalService.createDefaultGoal(created.getId());
            return created;
        });

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
