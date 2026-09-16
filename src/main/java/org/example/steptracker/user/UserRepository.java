package org.example.steptracker.user;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.jooq.tables.pojos.Accounts;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.example.steptracker.jooq.Tables.ACCOUNTS;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final DSLContext dsl;

    public Optional<Accounts> findById(UUID id) {
        return dsl.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(id)).fetchOptionalInto(Accounts.class);
    }

    public Optional<Accounts> findByGoogleSub(String googleSub) {
        return dsl.selectFrom(ACCOUNTS).where(ACCOUNTS.GOOGLE_SUB.eq(googleSub)).fetchOptionalInto(Accounts.class);
    }

    public Accounts insert(Accounts account) {
        return dsl.insertInto(ACCOUNTS)
                .set(ACCOUNTS.ID, UUID.randomUUID())
                .set(ACCOUNTS.EMAIL, account.getEmail())
                .set(ACCOUNTS.GOOGLE_SUB, account.getGoogleSub())
                .set(ACCOUNTS.NAME, account.getName())
                .returning()
                .fetchOne()
                .into(Accounts.class);
    }

    public Accounts update(Accounts account) {
        return dsl.update(ACCOUNTS)
                .set(ACCOUNTS.NAME, account.getName())
                .set(ACCOUNTS.DATE_OF_BIRTH, account.getDateOfBirth())
                .set(ACCOUNTS.HEIGHT_CM, account.getHeightCm())
                .set(ACCOUNTS.WEIGHT_KG, account.getWeightKg())
                .set(ACCOUNTS.UPDATED_AT, OffsetDateTime.now())
                .where(ACCOUNTS.ID.eq(account.getId()))
                .returning()
                .fetchOne()
                .into(Accounts.class);
    }
}
