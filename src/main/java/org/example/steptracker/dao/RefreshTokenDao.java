package org.example.steptracker.dao;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.jooq.tables.pojos.RefreshToken;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.example.steptracker.jooq.Tables.REFRESH_TOKEN;

@Repository
@RequiredArgsConstructor
public class RefreshTokenDao {

    private final DSLContext dsl;

    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return dsl.selectFrom(REFRESH_TOKEN).where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash)).fetchOptionalInto(RefreshToken.class);
    }

    public RefreshToken insert(UUID userId, String tokenHash, Instant expiresAt) {
        return dsl.insertInto(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.ID, UUID.randomUUID())
                .set(REFRESH_TOKEN.USER_ID, userId)
                .set(REFRESH_TOKEN.TOKEN_HASH, tokenHash)
                .set(REFRESH_TOKEN.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .set(REFRESH_TOKEN.REVOKED, false)
                .returning()
                .fetchOne()
                .into(RefreshToken.class);
    }

    public void revoke(UUID id) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED, true)
                .where(REFRESH_TOKEN.ID.eq(id))
                .execute();
    }
}
