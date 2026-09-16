# Step Tracker API — Frontend Integration Guide

Audience: Android and iOS client developers. This is the contract between the mobile apps and
the backend. Base URL, exact status codes, and field names here are the source of truth for
building client requests — if anything looks wrong or missing, flag it against this doc rather
than guessing.

> Status: **draft, auth-only scope**. Only account creation/login (via Google) is implemented
> right now — see [`PROJECT_PLAN.md`](../PROJECT_PLAN.md) for what's built vs. planned. Profile,
> devices, steps, and goals endpoints were deliberately removed pending proper table design and
> will be added back deliberately, table by table.

## Conventions

- Base path: `/api/v1`
- All requests/responses: `application/json`, UTF-8.
- Timestamps: ISO-8601 with offset, e.g. `2026-09-12T14:30:00+05:30`.
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
First call for a given Google account creates the account (email/name come from the Google
token); subsequent calls log the same account in.

### `POST /auth/refresh`

Request: `{ "refreshToken": "..." }`
Response `200`: `{ "accessToken": "jwt", "expiresIn": 900 }`

### `POST /auth/logout`

Request: `{ "refreshToken": "..." }` — revokes the refresh token.
Response `204`.

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
