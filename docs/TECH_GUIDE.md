# Step Tracker — Tech Guide

Internal tech spec for this backend. Audience: whoever (human or AI) picks up this codebase
next. Read this before making changes — it captures the *why* behind decisions that aren't
obvious from the code alone. [`PROJECT_PLAN.md`](../PROJECT_PLAN.md) has scope/phases;
[`API_COLLECTION.md`](API_COLLECTION.md) has the wire contract; [`INFRASTRUCTURE.md`](INFRASTRUCTURE.md)
has hosting/deployment. This doc is the glue between them — architecture and flows.

> Keep this file up to date when architecture-level decisions change (auth, data model,
> package structure). Don't duplicate what's already in the other three docs.

## 1. Stack

Java 17, Spring Boot 3.3.4, Maven, PostgreSQL (Neon, serverless), Flyway migrations, JWT
(`jjwt`), springdoc-openapi. Package-by-feature under `org.example.steptracker`: `auth`,
`user`, `device`, `steps`, `goal`, `config`, `common`.

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
    |                                        |--- find or create User
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

**User identity:** `users.google_sub` (Google's stable subject id) is the unique key, not email
(Google accounts can change email; `sub` doesn't change). `email`/`display_name` are populated
from the Google token's claims at first login and are editable afterward via `PUT /users/me` —
they are profile data, not auth data.

**Key files:**
- `auth/GoogleTokenVerifier.java` — verifies the Google ID token (audience = our OAuth client
  IDs, configured via `app.google.client-ids` / `GOOGLE_CLIENT_IDS` env var, comma-separated
  Android + iOS client IDs).
- `auth/AuthService.java` — `loginWithGoogle()` (find-or-create user + issue tokens),
  `refresh()`, `logout()`.
- `auth/JwtService.java` — mint/verify our own JWT (HMAC, `app.jwt.secret`).
- `auth/JwtAuthenticationFilter.java` — runs before every request, populates
  `SecurityContextHolder` from a valid Bearer token; unauthenticated requests to protected
  routes are rejected downstream by Spring Security (`RestAuthEntryPoint`).
- `config/SecurityConfig.java` — `/auth/**`, `/docs/**`, `/api-docs/**`, `/actuator/health` are
  public; everything else requires a valid access token.

**Known follow-up:** Apple requires **Sign in with Apple** if any other third-party login
(Google) is offered — mandatory before iOS store submission, not yet implemented. It would slot
in the same way: a new `AppleTokenVerifier`, a new `POST /auth/apple`, same `issueTokens()` /
`User` find-or-create pattern, one more unique column (`apple_sub`) or a generic
`(provider, provider_sub)` pair on `users` if a second provider is added — worth revisiting the
single-`google_sub`-column design at that point rather than bolting on a second nullable column.

## 3. Core domain model

- **User** — `id`, `email`, `google_sub` (unique, not null), `display_name`, `date_of_birth`,
  `height_cm`, `weight_kg`, `gender`, `created_at`, `updated_at`. No password field.
- **Device** — one row per physical device a user has registered, tags which health source it
  syncs from (`healthkit` / `health_connect` / `sensor` / `manual`).
- **DailyStepSummary** — one row per `(user_id, date)`, upserted by the client's sync call. The
  backend does not sum multiple sources for the same day — the client decides the authoritative
  count per day before syncing (see §3 of `PROJECT_PLAN.md` for the de-dup rationale).
- **StepGoal** — one row per user, `daily_goal_steps`.
- **RefreshToken** — `user_id`, `token_hash` (SHA-256 of the opaque token, not the token
  itself), `expires_at`, `revoked`.

Migrations: `V1__init.sql` (baseline schema, originally with `password_hash`),
`V2__google_auth.sql` (drops `password_hash`, adds `google_sub`).

## 4. Conventions worth knowing

- Package-by-feature, not by layer — each feature package (`auth`, `steps`, ...) has its own
  controller/service/repository/entity/dto.
- DTOs are Java records under a feature's `dto/` subpackage; entities are Lombok
  `@Getter @Setter` classes (not records — JPA needs mutability + a no-args constructor).
- Errors: `common/*Exception.java` (`NotFoundException`, `UnauthorizedException`,
  `ConflictException`, `BadRequestException`) + `GlobalExceptionHandler` map to the error shape
  documented in `API_COLLECTION.md`.
- `common/CurrentUser.java` reads the authenticated user id out of `SecurityContextHolder`
  (populated by `JwtAuthenticationFilter`) — this is how controllers get "who is calling" without
  re-parsing the token themselves.
- Stateless everywhere — no server-side session, Cloud Run runs N instances (see
  `INFRASTRUCTURE.md` §5).
- Flyway is the only way the schema changes — no `ddl-auto: update`, ever
  (`spring.jpa.hibernate.ddl-auto: validate` in `application.yml`).
