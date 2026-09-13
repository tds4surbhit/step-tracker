package org.example.steptracker.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.UnauthorizedException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds())))
                .signWith(signingKey())
                .compact();
    }

    public UUID parseUserId(String accessToken) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload()
                    .getSubject();
            return UUID.fromString(subject);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired access token");
        }
    }
}
