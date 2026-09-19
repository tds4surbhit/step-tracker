# Step Tracker — Backend Development Plan

## 1. Overview

Backend service for a mobile-first Step Tracker app (Android + iOS). The backend owns user
accounts and daily step data; the mobile clients own local sensing/health-API integration and
push aggregated step data to the backend.

**Scope for this plan:** MVP — accounts/profile + step tracking. Social features
(leaderboards, friends, challenges) are explicitly deferred to a later phase (see §7).

## Implementation Status (as of 2026-09-17)

**Only auth is built right now.** Profile, device, steps, and goal APIs that existed earlier
were deliberately removed — they'd been built without first properly deciding their table
schemas. The plan going forward is to design and add each one back deliberately, table by table,
using the Neon `dev` branch + jOOQ codegen workflow in [`docs/TECH_GUIDE.md`](docs/TECH_GUIDE.md)
§5, rather than resurrecting the old code as-is.

- ✅ Phase 0 — Spring Boot foundation (Maven/Spring Boot 3.3.4, package structure, Docker +
  docker-compose for local Postgres, springdoc-openapi wired at `/docs`).
- ✅ Flyway baseline migration (`accounts`, `refresh_token` — see §3).
- ✅ Phase 1 (auth only) — Google Sign-In (client verifies with Google, backend exchanges the
  Google ID token for our own JWT access token + opaque, hashed refresh token), refresh, logout.
  See [`docs/TECH_GUIDE.md`](docs/TECH_GUIDE.md) for the full auth flow.
- ✅ jOOQ codegen verified end-to-end: `V1__init.sql` applied to the real Neon `dev` branch via
  `mvn flyway:migrate`, `Accounts`/`RefreshToken` generated via `mvn jooq-codegen:generate`,
  `mvn compile` succeeds clean. See `docs/TECH_GUIDE.md` §4–5 for the exact commands and the
  local JDK-version gotcha that had been masking this as a Lombok problem.
- ⏳ **Not yet done / reverted, pending deliberate table design:** profile edit API, device
  registration, step sync/reporting, step goals (previously Phases 2–3), Phase 4 hardening (rate
  limiting, load test, finalized OpenAPI review).

The open questions in §8 (timezone handling, source de-dup rule) apply to the steps table once
it's redesigned — not resolved, just deferred until that table is actually being built.

## 2. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language / runtime | Java 17 | Matches existing `pom.xml` |
| Framework | Spring Boot 3.x | Spring Web, Spring Security, Spring Validation |
| Build | Maven | Existing project already uses Maven |
| Database | PostgreSQL via **Neon** (serverless) | AWS Singapore (`ap-southeast-1`), scale-to-zero — see [`docs/INFRASTRUCTURE.md`](docs/INFRASTRUCTURE.md) |
| Data access | **jOOQ** (generated typed SQL, no ORM) | Codegen against a Neon `dev` branch, not local Postgres — see [`docs/TECH_GUIDE.md`](docs/TECH_GUIDE.md) |
| Migrations | Flyway | Versioned SQL migrations, run on startup |
| Auth | JWT (access + refresh tokens) | Stateless, mobile-friendly |
| API style | REST + JSON, versioned under `/api/v1` | Simple, well-understood by mobile teams |
| Containerization | Docker → **Cloud Run** | GCP Singapore (`asia-southeast1`), scale-to-zero, colocated with Neon |
| Docs | OpenAPI/Swagger (springdoc-openapi) | Auto-generated from controllers, published for frontend |
| Testing | JUnit 5, Testcontainers (Postgres) | Integration tests against a real Postgres, not mocks |
| Observability | Spring Actuator + cloud-native logging/metrics | Health checks, basic metrics from day one |

## 3. Core Domain Model

**Built:**
- **Account** (table `accounts`) — id, email, google_sub, name, date_of_birth, height_cm,
  weight_kg, created_at, updated_at. No password (Google Sign-In only, see
  [`docs/TECH_GUIDE.md`](docs/TECH_GUIDE.md)). Deliberately bare-minimum — extend only when a
  real feature needs the extra column.
- **RefreshToken** (table `refresh_token`) — id, user_id, token_hash, expires_at, revoked,
  created_at.

**Not designed yet** (removed from an earlier, premature version of this plan — will be
re-designed from scratch when actually needed): a device-registration table, a daily step
summary table, a step-goal table. The notes below on double-counting are carried forward as
design input for *whenever the steps table gets built*, not a decision already made.

### Design input for a future steps table: avoiding double-counted steps

Since the app may read from a native health API (HealthKit / Health Connect) **and** a raw
device sensor, the same steps could be counted twice if both are synced naively. Candidate
approach, to revisit when this table is actually designed:

- Treat the platform health API as the **primary source** on both platforms — HealthKit on
  iOS, Health Connect on Android — because both already de-duplicate across other apps writing
  to the same store.
- Use direct sensor reads only as a **fallback** when health permissions are denied or
  unavailable, and mark that day's summary with `source = sensor` so the backend/analytics can
  distinguish data quality.
- Store **one summary row per (user_id, date)**, upserted by the client's sync call — not per
  source — so a client switching sources mid-day still produces one authoritative number,
  decided client-side before syncing.

This still needs to be validated with you before that table is built — see open questions in §8.

## 4. API Surface

All endpoints under `/api/v1`. Full request/response contracts are in
[`docs/API_COLLECTION.md`](docs/API_COLLECTION.md), which is the doc meant for the
frontend/mobile teams.

**Built:**
- **Auth:** `POST /auth/google` (Google Sign-In), `POST /auth/refresh`, `POST /auth/logout`

**Planned, not yet built (design each table first — see §3):**
- **Profile:** `GET /users/me`, `PUT /users/me`
- **Devices:** register a device + its health source
- **Steps:** sync daily summaries, today/daily/range queries
- **Goals:** get/set a daily step goal

## 5. Non-Functional Requirements

- HTTPS only; JWT access tokens short-lived (~15 min), refresh tokens longer-lived and revocable.
- Input validation on every endpoint (Bean Validation).
- Rate limiting on auth endpoints to blunt credential-stuffing/brute force.
- Structured logging + Actuator health/metrics endpoints from day one.
- Database migrations only via Flyway — no manual schema changes.
- Integration tests run against a real Postgres via Testcontainers, not mocked repositories.

## 6. Delivery Phases

1. ✅ **Phase 0 — Foundation:** Spring Boot on the existing Maven project, project structure,
   Flyway baseline, Docker image, Postgres provisioning.
2. ✅ **Phase 1 — Auth:** Google Sign-In verification, JWT issuance/refresh/logout.
3. ⏳ **Phase 2 — Accounts/Profile:** design the profile-edit API surface and rebuild
   `GET`/`PUT /users/me` deliberately (the old version was removed).
4. ⏳ **Phase 3 — Step Ingestion:** design the device/step tables from scratch (see §3), then
   device registration + `/steps/sync` batch upsert.
5. ⏳ **Phase 4 — Reporting & Goals:** design the goal table, then daily/range queries + goal
   CRUD, response shapes finalized with frontend.
6. ⏳ **Phase 5 — Hardening:** Security review, rate limiting, load test of the sync endpoint,
   finalize OpenAPI doc, publish to frontend.
7. **Phase 6 — Post-MVP (future):** Social/leaderboards, push notifications, streaks/badges,
   calories/distance derived metrics.

## 7. Explicitly Out of Scope for MVP

- Friends, groups, leaderboards, challenges.
- Push notifications.
- Achievements/badges, streak logic.
- Sign in with Apple — required before iOS store submission (see §8) but not yet built; Google
  Sign-In is the only auth method implemented so far.

## 8. Open Questions

- **Timezone handling:** once a steps table exists, a "day" boundary should probably follow the
  device's local timezone, not UTC — confirm this is acceptable, since it means the same UTC
  instant can land in different rows for users in different timezones.
- **Source de-dup:** confirm the primary/fallback approach in §3, or specify a different rule
  (e.g., always take the max of available sources for a given day), once that table is designed.
- **Team size / timeline:** not yet specified — the phase breakdown above has no dates attached
  yet; let me know if you want target dates per phase.
- **Social login:** **resolved** — Google Sign-In is the only auth method for MVP (no
  email/password). Per Apple's rules, offering any third-party login (Google) means **Sign in
  with Apple is mandatory** for the iOS app before store submission — not yet implemented,
  tracked as a follow-up.

## 9. Repo Layout (current)

```
StepTracker/
├── pom.xml
├── Dockerfile
├── docker-compose.yml       <- local Postgres + app for dev
├── PROJECT_PLAN.md          <- this file
├── docs/
│   ├── API_COLLECTION.md    <- frontend-facing API contract
│   ├── TECH_GUIDE.md        <- internal tech spec (architecture, auth flow, conventions)
│   └── INFRASTRUCTURE.md    <- hosting/deployment architecture
└── src/
    └── main/
        ├── java/org/example/steptracker/
        │   ├── controller/  <- REST controllers (AuthController) — HTTP binding only
        │   ├── operation/   <- business logic (AuthOperation) — one per controller
        │   ├── dao/         <- jOOQ queries only (AccountDao, RefreshTokenDao)
        │   ├── dto/         <- request/response records, shared across controllers
        │   ├── security/    <- JWT + Google-token-verification infrastructure
        │   ├── config/      <- Spring Security config
        │   ├── common/      <- shared exceptions, error response, current-user helper
        │   └── jooq/        <- generated by jOOQ codegen, committed to git (see TECH_GUIDE.md §4)
        └── resources/
            ├── application.yml
            └── db/migration/   <- Flyway SQL scripts (V1: accounts + refresh_token)
```
