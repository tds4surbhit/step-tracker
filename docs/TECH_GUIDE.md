# Step Tracker — Tech Guide

Internal tech spec for this backend. Audience: whoever (human or AI) picks up this codebase
next. Read this before making changes — it captures the *why* behind decisions that aren't
obvious from the code alone. [`PROJECT_PLAN.md`](../PROJECT_PLAN.md) has scope/phases;
[`API_COLLECTION.md`](API_COLLECTION.md) has the wire contract; [`INFRASTRUCTURE.md`](INFRASTRUCTURE.md)
has hosting/deployment. This doc is the glue between them — architecture and flows.

> Keep this file up to date when architecture-level decisions change (auth, data model,
> package structure). Don't duplicate what's already in the other three docs.

> **Current scope: auth only.** The device/steps/goal/profile features that existed earlier in
> this project's history were removed deliberately — they were built before their tables had
> been properly thought through. The plan now is to design and add each table/feature back one
> at a time, starting from a real decision on its schema, using the Neon `dev` branch + jOOQ
> codegen loop described in §5. Don't reintroduce those features by copying old patterns without
> re-deciding the schema first.

## 1. Stack

Java 17, Spring Boot 3.3.4, Maven, PostgreSQL (Neon, serverless), Flyway migrations, **jOOQ**
for data access, JWT (`jjwt`), springdoc-openapi. **Package-by-layer** (not by feature — see §7)
under `org.example.steptracker`: `controller`, `operation`, `dao`, `dto`, `security`, `config`,
`common`.

## 2. Auth flow — Google Sign-In + our own JWT

**Decision:** no email/password. The client authenticates the user against Google; the backend
never sees or stores a password. We still mint our own JWT for API calls, so every endpoint
downstream of login is provider-agnostic.

```
Mobile app                         Our backend                      Google
    |--- Google Sign-In (native SDK) --------------------------------->|
    |<---------------------------------------------- Google ID token --|
    |--- POST /auth/google { idToken } ---->|
    |                                        |--- verify signature/aud/exp
    |                                        |    (GoogleIdTokenVerifier,
    |                                        |     Google's public JWKs) --->|
    |                                        |<---------------- valid/claims |
    |                                        |--- find or create account
    |                                        |    by google_sub
    |                                        |--- mint our own JWT
    |                                        |    (JwtService, HMAC, our secret)
    |                                        |--- generate + hash opaque
    |                                        |    refresh token, store hash
    |<--- { userId, accessToken, refreshToken, expiresIn } -------------|
    |
    |--- subsequent calls: Authorization: Bearer <accessToken> -------->|
    |                                        |--- JwtAuthenticationFilter verifies
    |                                        |    OUR signature (not Google's) --|
```

**Two distinct tokens, don't confuse them:**
- **Google ID token** — only used once, at `/auth/google`, to prove who the user is. Verified
  against Google's public keys (`GoogleTokenVerifier` / `GoogleIdTokenVerifier`). Never stored,
  never sent to any endpoint other than `/auth/google`.
- **Our JWT (access token)** — signed with our own HMAC secret (`app.jwt.secret`), what every
  other endpoint trusts. `JwtAuthenticationFilter` verifies its signature on every request; it
  does not call Google again. Short-lived (~15 min).
- **Our refresh token** — opaque random string, not a JWT. Only the SHA-256 hash is persisted
  (`refresh_token` table); the raw value is returned to the client once and never stored
  server-side. `POST /auth/refresh` trades it for a new access token without re-doing Google
  Sign-In. Revocable via `POST /auth/logout` (sets `revoked = true`).

**Why keep our own JWT instead of just trusting the Google ID token on every call:** Google ID
tokens are short-lived and re-verifying one on every request means either a network call to
Google or bundling their JWKS/rotation logic everywhere. Minting our own token means only the
login step depends on Google; everything else is a fast local signature check with our own key,
and revocation (logout) works even though JWTs themselves aren't revocable — the *refresh*
token is what we actually revoke, and access tokens are short-lived enough that revocation lag
is acceptable.

**Account identity:** `accounts.google_sub` (Google's stable subject id) is the unique key, not
email (Google accounts can change email; `sub` doesn't change). `email`/`name` are populated
from the Google token's claims at first login; there's no profile-edit API yet (see the scope
note above).

**Key files:**
- `controller/AuthController.java` — HTTP layer only: binds `/auth/google|refresh|logout` to
  `AuthOperation`, no logic of its own.
- `operation/AuthOperation.java` — the business logic: `loginWithGoogle()` (find-or-create
  account + issue tokens), `refresh()`, `logout()`.
- `dao/AccountDao.java` / `dao/RefreshTokenDao.java` — the only classes that touch `DSLContext`
  for these tables.
- `security/GoogleTokenVerifier.java` — verifies the Google ID token (audience = our OAuth client
  IDs, configured via `app.google.client-ids` / `GOOGLE_CLIENT_IDS` env var, comma-separated
  Android + iOS client IDs).
- `security/JwtService.java` — mint/verify our own JWT (HMAC, `app.jwt.secret`).
- `security/JwtAuthenticationFilter.java` — runs before every request, populates
  `SecurityContextHolder` from a valid Bearer token; unauthenticated requests to protected
  routes are rejected downstream by Spring Security (`RestAuthEntryPoint`).
- `config/SecurityConfig.java` — `/auth/**`, `/docs/**`, `/api-docs/**`, `/actuator/health` are
  public; everything else requires a valid access token (nothing else exists yet).

**Known follow-up:** Apple requires **Sign in with Apple** if any other third-party login
(Google) is offered — mandatory before iOS store submission, not yet implemented. It would slot
in the same way: a new `AppleTokenVerifier`, a new `POST /auth/apple`, same `issueTokens()` /
account find-or-create pattern, one more unique column (`apple_sub`) or a generic
`(provider, provider_sub)` pair on `accounts` if a second provider is added — worth revisiting
the single-`google_sub`-column design at that point rather than bolting on a second nullable
column.

## 3. Core domain model (current)

- **Account** (table `accounts`) — `id`, `email`, `google_sub` (unique, not null), `name`,
  `date_of_birth`, `height_cm`, `weight_kg`, `created_at`, `updated_at`. No password field, no
  `gender`, no stored `age` (always derive it from `date_of_birth` at read time if/when needed —
  storing both goes stale). Deliberately bare-minimum; extend via a new migration once a real
  feature needs more, not speculatively.
- **RefreshToken** (table `refresh_token`) — `id`, `user_id` (FK → `accounts.id`), `token_hash`
  (SHA-256 of the opaque token, not the token itself), `expires_at`, `revoked`, `created_at`.

Migration: `V1__init.sql` — creates both tables. (This squashes what were previously three
separate migrations — `password_hash` → `google_sub` → rename-to-`accounts` — into one clean
baseline, since nothing had been deployed yet and carrying that history forward added nothing.)

**Not designed yet:** devices, step data, goals, or any profile-edit API. When one of these is
actually needed, design its table deliberately (what columns, what constraints, why) before
writing any code against it — that's the whole point of the `dev`-branch + jOOQ workflow in §5.

## 4. Data access — jOOQ, not JPA/Hibernate

**Decision:** no ORM, no entity dirty-checking. Every query is explicit SQL built through jOOQ's
typed DSL against generated table/column constants, and the objects services work with are
jOOQ-generated POJOs, not hand-written `@Entity` classes.

**Generated code:** `org.example.steptracker.jooq.*` (`Tables.ACCOUNTS`/`REFRESH_TOKEN` for typed
column refs, `tables.pojos.*` for the plain data-holder classes services use — note table names
are plural where the table itself is plural, e.g. `Accounts`, not `Account`). **These files are
committed to git**, not gitignored — see "Regenerating" below for why.

**DAO pattern:** each table gets a `@Repository`-annotated DAO class in the `dao` package
(`AccountDao`, `RefreshTokenDao`) wrapping a `DSLContext`, with explicit `insert(...)`/
`update(...)` methods — there's no Hibernate save-and-it-figures-out-insert-vs-update. DAOs are
also where id/timestamp bookkeeping lives: generating `UUID.randomUUID()` for id columns (the
schema has no DB-side UUID default), and setting `updated_at` to `now()` explicitly on updates
(Postgres `DEFAULT now()` only fires on insert, not on an `UPDATE`). DAOs contain jOOQ queries
only — no business logic, no validation, no orchestration; that all lives one layer up, in
`operation` (see §7).

**Timestamps are `java.time.OffsetDateTime`**, not `Instant` — that's jOOQ's default Java
mapping for Postgres `timestamptz`, and using it directly avoids writing a converter that adds
nothing. `date`/`numeric`/`integer`/`boolean` columns map to `LocalDate`/`BigDecimal`/
`Integer`/`Boolean` as you'd expect, no config needed. Nothing in the current schema needs a
`forcedType`/custom `org.jooq.Converter` — that pattern will come back if a future table has an
enum-like `VARCHAR` + `CHECK` column (it did, for the removed `devices`/`daily_step_summary`
tables — same approach applies whenever that's redesigned).

**Regenerating (only when the schema changes):** neither Flyway (standalone) nor jOOQ codegen
runs as part of a normal build — `mvn compile`/`mvn package`/the `Dockerfile` build never touch
a database. Both are invoked by hand, pointed at the Neon **`dev` branch's direct connection**
(see §5 for *why direct, not pooled*):

```bash
# 1. Apply migrations to the dev branch
mvn flyway:migrate \
    -Ddb.direct.url=<dev-branch-DIRECT-jdbc-url> \
    -Ddb.direct.user=<user> \
    -Ddb.direct.password=<password>

# 2. Generate jOOQ classes from the now-migrated schema
mvn jooq-codegen:generate \
    -Ddb.direct.url=<dev-branch-DIRECT-jdbc-url> \
    -Ddb.direct.user=<user> \
    -Ddb.direct.password=<password>
```

Then review the diff under `src/main/java/org/example/steptracker/jooq/` and commit it like any
other code change. Verify what Flyway created first, in the Neon SQL Editor or DBeaver:
```sql
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
```
Expect `accounts`, `refresh_token`, `flyway_schema_history` — nothing else, and don't create
tables by clicking them into existence in a console; if Flyway didn't create it, the next person
to run migrations gets a schema `V1__init.sql` doesn't describe.

If either command times out or hangs on the first try, just retry — Neon suspends compute after
~5 minutes idle, so the very first connection after a break pays a cold-start of a few seconds.

## 5. Neon branches — and why direct vs. pooled matters

`main` is the real deployed database (see `INFRASTRUCTURE.md`). A separate `dev` branch exists
purely so Flyway/jOOQ codegen (and any manual local testing) has a live Postgres to point at
without needing Docker — Neon branches are copy-on-write and billed from the same project-wide
storage/CU-hour pool as `main`, so an occasionally-used `dev` branch costs close to nothing on
the free tier (see cost breakdown discussed when this was set up). `dev` is never touched by
deploys; only `main` is. **Confirmed working end-to-end** (2026-09-17): `flyway:migrate` applied
`V1__init.sql`, `jooq-codegen:generate` produced `Accounts`/`RefreshToken`, `mvn compile` passes.

Neon gives you two connection strings per branch — a **pooled** one (hostname has `-pooler` in
it, goes through PgBouncer in transaction mode) and a **direct** one (no `-pooler`, straight to
the compute). They are not interchangeable:

- **App runtime datasource** (`DB_URL` in `application.yml`) → **pooled**. This is what
  `INFRASTRUCTURE.md` §5 already recommends (Cloud Run can spin up many instances; the pooler is
  what keeps that from exhausting Neon's connection limit).
- **Flyway migrations** and **jOOQ codegen** → **direct**. Migrations take DDL locks and run in
  long-lived transactions; PgBouncer's transaction-mode pooling actively breaks that. Codegen is
  reading catalog metadata, which is also happier off a direct connection. Both are configured
  via the `db.direct.*` Maven properties (`pom.xml`) / `FLYWAY_DB_URL` env var
  (`application.yml`) — kept deliberately separate from the app's own `DB_URL`.

## 6. Local environment gotchas (things that look like bugs but aren't)

- **Build with JDK 17, not whatever `java`/`mvn` resolves to by default.** `pom.xml` pins
  `<java.version>17</java.version>`, but a machine's default JDK can be something newer that
  Lombok's pinned version (from the Spring Boot 3.3.4 BOM) doesn't yet support hooking into. When
  that happens, Lombok's annotation processor silently produces nothing — no error about Lombok
  itself, just a wall of `cannot find symbol: getX()/setX()` compile errors on every
  `@Getter`/`@Setter`/`@RequiredArgsConstructor` class, which reads exactly like a real code bug.
  If you hit that: check `mvn -version`'s reported Java version first, before debugging the code.
  Fix: point `JAVA_HOME` at a JDK 17 install (`/usr/libexec/java_home -V` lists what's installed
  on macOS). On this dev machine, `~/.zshrc` sets `JAVA_HOME` to a JDK 17 (Corretto) permanently.
- **`mvn clean` before running `flyway:migrate`/`jooq-codegen:generate` standalone**, if you've
  changed which `.sql` files exist under `db/migration` since the last build. Invoking a single
  plugin goal directly (`mvn flyway:migrate`) does **not** run the `process-resources` phase
  first, so `target/classes/db/migration` can still hold migration files you deleted or renamed
  in `src/main/resources`. Flyway reads the classpath, not `src/` directly, so a stale
  `target/classes` means it applies migrations that no longer exist in source — this is exactly
  how an old `V2__google_auth.sql` briefly got applied to the Neon `dev` branch during setup, and
  why that branch needed a `DROP SCHEMA public CASCADE` reset. `mvn clean process-resources`
  first avoids it.

## 7. Conventions worth knowing

**Package-by-layer (changed 2026-09-18 — was package-by-feature before).** Three layers, one
flat package per layer, spanning every feature:

- `controller/` — every `@RestController`. HTTP binding only: request/response mapping,
  `@Valid`, status codes. No business logic — a controller method should read as "call one
  operation method, return its result."
- `operation/` — every `@Service` that holds business logic: orchestration, validation beyond
  Bean Validation, deciding what to call in what order, transaction boundaries (`@Transactional`
  lives here, not on DAOs). Named `<Feature>Operation`, e.g. `AuthOperation`.
- `dao/` — every class that talks to `DSLContext`. Named `<Table>Dao`, e.g. `AccountDao`
  (table `accounts`), `RefreshTokenDao` (table `refresh_token`). Jooq queries only, as described
  in §4 — no logic beyond mapping a query's shape to Java.
- `dto/` — flat, shared across all controllers. Java records.
- `security/` — JWT + Google-token-verification infrastructure (`JwtService`,
  `JwtAuthenticationFilter`, `GoogleTokenVerifier`, their `@ConfigurationProperties` classes).
  Deliberately **not** folded into `operation`: this code doesn't implement a business decision,
  it's infrastructure the security filter chain and every operation depend on — putting it in
  `operation` would make "business logic" a dumping ground for anything auth-adjacent.
- `config/`, `common/` — unchanged: Spring config beans, and cross-cutting utilities
  (exceptions, `CurrentUser`, `TokenHasher`) used from any layer.

**Naming rule for adding a new feature:** one controller method → one operation method → one or
more DAO calls. If a controller method needs logic beyond "unwrap request, call operation,
return response," that logic belongs in the operation class, not the controller. If an operation
method needs a query DAO doesn't have yet, add the method to the DAO — never reach for
`DSLContext` directly from `operation` or `controller`.

- Errors: `common/*Exception.java` (`NotFoundException`, `UnauthorizedException`,
  `ConflictException`, `BadRequestException`) + `GlobalExceptionHandler` map to the error shape
  documented in `API_COLLECTION.md`.
- `common/CurrentUser.java` reads the authenticated user id out of `SecurityContextHolder`
  (populated by `JwtAuthenticationFilter`) — this is how controllers get "who is calling" without
  re-parsing the token themselves.
- Stateless everywhere — no server-side session, Cloud Run runs N instances (see
  `INFRASTRUCTURE.md` §5).
- Flyway is the only way the schema changes — no auto-DDL, ever. `@Transactional` on services
  still works unchanged under jOOQ: `spring-boot-starter-jooq` auto-configures a
  `DataSourceTransactionManager` since there's no JPA `EntityManagerFactory` on the classpath.
