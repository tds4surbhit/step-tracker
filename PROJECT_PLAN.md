# Step Tracker — Backend Development Plan

## 1. Overview

Backend service for a mobile-first Step Tracker app (Android + iOS). The backend owns user
accounts and daily step data; the mobile clients own local sensing/health-API integration and
push aggregated step data to the backend.

**Scope for this plan:** MVP — accounts/profile + step tracking. Social features
(leaderboards, friends, challenges) are explicitly deferred to a later phase (see §7).

## Implementation Status (as of 2026-09-12)

Phases 0–3 are code-complete against the contract in `docs/API_COLLECTION.md`:

- ✅ Phase 0 — Spring Boot foundation (Maven/Spring Boot 3.3.4, package structure, Docker +
  docker-compose for local Postgres, springdoc-openapi wired at `/docs`).
- ✅ Flyway baseline migration (`users`, `devices`, `daily_step_summary`, `step_goal`,
  `refresh_token`).
- ✅ Phase 1 — Auth (Google Sign-In only: client verifies with Google, backend exchanges the
  Google ID token for our own JWT access token + opaque, hashed refresh token; refresh/logout
  unchanged) and Profile (`GET`/`PUT /users/me`). See [`docs/TECH_GUIDE.md`](docs/TECH_GUIDE.md)
  for the full auth flow.
- ✅ Phase 2 — Device registration (`POST /devices`) and `POST /steps/sync` upsert-per-day.
- ✅ Phase 3 — `GET /steps/today|daily|range` and `GET`/`PUT /users/me/goal`.
- ⏳ **Not yet done:** a real `mvn compile`/boot-up verification against a running Postgres, and
  Phase 4 hardening (rate limiting, load test, finalized OpenAPI review) — see task #8 in the
  active task list.

The open questions in §8 (timezone handling, source de-dup rule, social login) are still
unresolved — current code defaults to server-clock `LocalDate.now()` for "today" and to
whatever `source` the client sends per entry, which is a placeholder pending your answer.

## 2. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language / runtime | Java 17 | Matches existing `pom.xml` |
| Framework | Spring Boot 3.x | Spring Web, Spring Data JPA, Spring Security, Spring Validation |
| Build | Maven | Existing project already uses Maven |
| Database | PostgreSQL via **Neon** (serverless) | AWS Singapore (`ap-southeast-1`), scale-to-zero — see [`docs/INFRASTRUCTURE.md`](docs/INFRASTRUCTURE.md) |
| Migrations | Flyway | Versioned SQL migrations, run on startup |
| Auth | JWT (access + refresh tokens) | Stateless, mobile-friendly |
| API style | REST + JSON, versioned under `/api/v1` | Simple, well-understood by mobile teams |
| Containerization | Docker → **Cloud Run** | GCP Singapore (`asia-southeast1`), scale-to-zero, colocated with Neon |
| Docs | OpenAPI/Swagger (springdoc-openapi) | Auto-generated from controllers, published for frontend |
| Testing | JUnit 5, Testcontainers (Postgres) | Integration tests against a real Postgres, not mocks |
| Observability | Spring Actuator + cloud-native logging/metrics | Health checks, basic metrics from day one |

## 3. Core Domain Model (MVP)

- **User** — id, email, password_hash, display_name, date_of_birth, height_cm, weight_kg,
  gender, created_at, updated_at
- **Device** — id, user_id, platform (`ios` / `android`), health_source
  (`healthkit` / `health_connect` / `sensor` / `manual`), last_synced_at
- **DailyStepSummary** — id, user_id, date (local calendar date), step_count, source,
  timezone, synced_at
- **StepGoal** — user_id, daily_goal_steps, updated_at

### Key design decision: avoiding double-counted steps

Since the app may read from a native health API (HealthKit / Health Connect) **and** a raw
device sensor, the same steps could be counted twice if both are synced naively. Recommended
approach:

- Treat the platform health API as the **primary source** on both platforms — HealthKit on
  iOS, Health Connect on Android — because both already de-duplicate across other apps writing
  to the same store.
- Use direct sensor reads only as a **fallback** when health permissions are denied or
  unavailable, and mark that day's summary with `source = sensor` so the backend/analytics can
  distinguish data quality.
- The backend stores **one summary row per (user_id, date)**, upserted by the client's sync
  call — not per source — so a client switching sources mid-day still produces one authoritative
  number, decided client-side before syncing.

This needs to be validated with you before Phase 2 — see open questions in §8.

## 4. API Surface (MVP)

All endpoints under `/api/v1`. Full request/response contracts are in
[`docs/API_COLLECTION.md`](docs/API_COLLECTION.md), which is the doc meant for the
frontend/mobile teams.

- **Auth:** `POST /auth/google` (Google Sign-In), `POST /auth/refresh`, `POST /auth/logout`
- **Profile:** `GET /users/me`, `PUT /users/me`
- **Devices:** `POST /devices` (register a device + its health source)
- **Steps:** `POST /steps/sync` (batch upsert daily summaries), `GET /steps/today`,
  `GET /steps/daily?date=YYYY-MM-DD`, `GET /steps/range?start=&end=`
- **Goals:** `GET /users/me/goal`, `PUT /users/me/goal`

## 5. Non-Functional Requirements

- HTTPS only; JWT access tokens short-lived (~15 min), refresh tokens longer-lived and revocable.
- Input validation on every endpoint (Bean Validation).
- Rate limiting on auth endpoints to blunt credential-stuffing/brute force.
- Structured logging + Actuator health/metrics endpoints from day one.
- Database migrations only via Flyway — no manual schema changes.
- Integration tests run against a real Postgres via Testcontainers, not mocked repositories.

## 6. Delivery Phases

1. **Phase 0 — Foundation:** Add Spring Boot to the existing Maven project, project structure,
   Flyway baseline, Docker image, CI pipeline (build + test), Postgres provisioning.
2. **Phase 1 — Auth & Profile:** Google Sign-In verification, JWT issuance/refresh, `users/me`
   CRUD.
3. **Phase 2 — Step Ingestion:** Device registration, `/steps/sync` batch upsert, de-dup rules
   from §3 implemented and tested.
4. **Phase 3 — Reporting & Goals:** Daily/range queries, goal CRUD, response shapes finalized
   with frontend.
5. **Phase 4 — Hardening:** Security review, rate limiting, load test of the sync endpoint,
   finalize OpenAPI doc, publish to frontend.
6. **Phase 5 — Post-MVP (future):** Social/leaderboards, push notifications, streaks/badges,
   calories/distance derived metrics.

## 7. Explicitly Out of Scope for MVP

- Friends, groups, leaderboards, challenges.
- Push notifications.
- Achievements/badges, streak logic.
- Sign in with Apple — required before iOS store submission (see §8) but not yet built; Google
  Sign-In is the only auth method implemented so far.

## 8. Open Questions (need your input before Phase 2)

- **Timezone handling:** a "day" boundary should probably follow the device's local timezone,
  not UTC — confirm this is acceptable, since it means the same UTC instant can land in
  different `DailyStepSummary` rows for users in different timezones.
- **Source de-dup:** confirm the primary/fallback approach in §3, or specify a different rule
  (e.g., always take the max of available sources for a given day).
- **Team size / timeline:** not yet specified — the phase breakdown above has no dates attached
  yet; let me know if you want target dates per phase.
- **Social login:** **resolved** — Google Sign-In is now the only auth method for MVP (no
  email/password). Per Apple's rules, offering any third-party login (Google) means **Sign in
  with Apple is mandatory** for the iOS app before store submission — not yet implemented,
  tracked as a follow-up.

## 9. Repo Layout (proposed)

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
        │   ├── auth/        <- Google Sign-In verification, refresh/logout, JWT issuance
        │   ├── user/        <- profile GET/PUT
        │   ├── device/      <- device registration
        │   ├── steps/       <- sync + daily/today/range queries
        │   ├── goal/        <- daily step goal
        │   ├── config/      <- Spring Security config
        │   └── common/      <- shared exceptions, error response, current-user helper
        └── resources/
            ├── application.yml
            └── db/migration/   <- Flyway SQL scripts
```
