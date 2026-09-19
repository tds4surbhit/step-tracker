package org.example.steptracker.operation;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.TokenHasher;
import org.example.steptracker.common.UnauthorizedException;
import org.example.steptracker.dao.AccountDao;
import org.example.steptracker.dao.RefreshTokenDao;
import org.example.steptracker.dto.AccessTokenResponse;
import org.example.steptracker.dto.AuthResponse;
import org.example.steptracker.dto.GoogleAuthRequest;
import org.example.steptracker.dto.RefreshRequest;
import org.example.steptracker.jooq.tables.pojos.Accounts;
import org.example.steptracker.jooq.tables.pojos.RefreshToken;
import org.example.steptracker.security.GoogleTokenVerifier;
import org.example.steptracker.security.JwtProperties;
import org.example.steptracker.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Business logic for authentication: Google Sign-In, our own JWT issuance, refresh, logout. */
@Service
@RequiredArgsConstructor
public class AuthOperation {

    private final AccountDao accountDao;
    private final RefreshTokenDao refreshTokenDao;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());
        String googleSub = payload.getSubject();

        Accounts account = accountDao.findByGoogleSub(googleSub).orElseGet(() -> {
            Accounts toCreate = new Accounts();
            toCreate.setGoogleSub(googleSub);
            toCreate.setEmail(payload.getEmail());
            toCreate.setName((String) payload.get("name"));
            return accountDao.insert(toCreate);
        });

        return issueTokens(account.getId());
    }

    @Transactional
    public AccessTokenResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenDao.findByTokenHash(TokenHasher.sha256(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (token.getRevoked() || token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }
        String accessToken = jwtService.generateAccessToken(token.getUserId());
        return new AccessTokenResponse(accessToken, jwtProperties.getAccessTokenTtlSeconds());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenDao.findByTokenHash(TokenHasher.sha256(request.refreshToken()))
                .ifPresent(token -> refreshTokenDao.revoke(token.getId()));
    }

    private AuthResponse issueTokens(UUID userId) {
        String accessToken = jwtService.generateAccessToken(userId);
        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        refreshTokenDao.insert(
                userId,
                TokenHasher.sha256(rawRefreshToken),
                Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds())
        );
        return new AuthResponse(userId, accessToken, rawRefreshToken, jwtProperties.getAccessTokenTtlSeconds());
    }
}
