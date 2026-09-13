# Step Tracker API — Frontend Integration Guide

Audience: Android and iOS client developers. This is the contract between the mobile apps and
the backend. Base URL, exact status codes, and field names here are the source of truth for
building client requests — if anything looks wrong or missing, flag it against this doc rather
than guessing.

> Status: **draft, MVP scope**. Endpoints are being implemented per the phases in
> [`PROJECT_PLAN.md`](../PROJECT_PLAN.md). This doc will be updated as each phase lands, and a
> generated OpenAPI/Swagger spec will supplement it once the server is running.

## Conventions

- Base path: `/api/v1`
- All requests/responses: `application/json`, UTF-8.
- Dates: `YYYY-MM-DD` (calendar date, no time component). Timestamps: ISO-8601 with offset,
  e.g. `2026-09-12T14:30:00+05:30`.
- Auth: `Authorization: Bearer <access_token>` on every endpoint except `/auth/google` and
  `/auth/refresh`.
- Errors follow a consistent shape (see [Error Format](#error-format)).

## Auth

Sign-in is Google-only: the client performs Google Sign-In natively (Android/iOS SDK), then
hands the resulting **Google ID token** to the backend, which verifies it and issues our own
JWT access/refresh token pair. See [`TECH_GUIDE.md`](TECH_GUIDE.md) for the full flow and why
the JWT is separate from the Google token.

### `POST /auth/google`

Request:
```json
{ "idToken": "google-id-token" }
```
Response `200`:
```json
{
  "userId": "uuid",
  "accessToken": "jwt",
  "refreshToken": "opaque-token",
  "expiresIn": 900
}
```
First call for a given Google account creates the `User` (email/displayName come from the
Google token); subsequent calls log the same account in.

### `POST /auth/refresh`

Request: `{ "refreshToken": "..." }`
Response `200`: `{ "accessToken": "jwt", "expiresIn": 900 }`

### `POST /auth/logout`

Request: `{ "refreshToken": "..." }` — revokes the refresh token.
Response `204`.

## Profile

### `GET /users/me`

Response `200`:
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "displayName": "Jane Doe",
  "dateOfBirth": "1995-04-10",
  "heightCm": 170,
  "weightKg": 65,
  "gender": "female"
}
```

### `PUT /users/me`

Request: any subset of the profile fields above (partial update).
Response `200`: full updated profile object.

## Devices

### `POST /devices`

Registers a device and declares which step data source it will sync from. Call this once per
device on first launch / when the user changes their permission choice.

Request:
```json
{
  "platform": "ios",
  "healthSource": "healthkit"
}
```
`platform`: `"ios" | "android"`
`healthSource`: `"healthkit" | "health_connect" | "sensor" | "manual"`

Response `201`: `{ "deviceId": "uuid" }`

## Steps

### `POST /steps/sync`

Batch upload of daily step summaries. The client is responsible for producing **one authoritative
step count per calendar day** before syncing — see "Avoiding double counting" below.

Request:
```json
{
  "deviceId": "uuid",
  "entries": [
    { "date": "2026-09-10", "stepCount": 8342, "source": "healthkit", "timezone": "Asia/Kolkata" },
    { "date": "2026-09-11", "stepCount": 6120, "source": "healthkit", "timezone": "Asia/Kolkata" }
  ]
}
```
Response `200`:
```json
{ "synced": 2, "entries": [ /* stored summaries, echoed back */ ] }
```

This call is idempotent per `(userId, date)` — resyncing the same date overwrites the prior
value for that date rather than adding to it. Safe to retry on network failure.

### `GET /steps/today`

Response `200`:
```json
{ "date": "2026-09-12", "stepCount": 3120, "goal": 10000 }
```

### `GET /steps/daily?date=YYYY-MM-DD`

Response `200`: `{ "date": "...", "stepCount": 8342, "source": "healthkit" }`
`404` if no data exists for that date.

### `GET /steps/range?start=YYYY-MM-DD&end=YYYY-MM-DD`

Response `200`: `{ "entries": [ { "date": "...", "stepCount": ... }, ... ] }`
Max range: 90 days per request (paginate on the client for longer ranges).

## Goals

### `GET /users/me/goal`

Response `200`: `{ "dailyGoalSteps": 10000 }`

### `PUT /users/me/goal`

Request: `{ "dailyGoalSteps": 12000 }`
Response `200`: `{ "dailyGoalSteps": 12000 }`

## Avoiding Double-Counted Steps (client responsibility)

If a device can read from both a platform health API (HealthKit / Health Connect) and the raw
motion sensor, **do not sync both for the same day**. Recommended client logic:

1. Prefer the platform health API as the source of truth (HealthKit on iOS, Health Connect on
   Android) — it already de-dupes across other apps.
2. Only fall back to direct sensor reads if health permissions are unavailable/denied, and tag
   that day's entry with `"source": "sensor"`.
3. Sync one `stepCount` per `date` — the backend stores the latest sync per day, it does not sum
   multiple entries for the same date.

## Error Format

All non-2xx responses:
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "email must be a valid email address",
    "details": {}
  }
}
```
Common `code` values: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401, e.g. invalid/expired
Google ID token or refresh token), `NOT_FOUND` (404), `RATE_LIMITED` (429).

## Open Items Affecting This Contract

See §8 of [`PROJECT_PLAN.md`](../PROJECT_PLAN.md) — timezone handling and source de-dup rules
are still pending confirmation and could adjust the `/steps/sync` request shape slightly.
