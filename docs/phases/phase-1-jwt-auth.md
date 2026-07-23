# Phase 1 — JWT Authentication

**Status:** ✅ Complete
**Focus:** Establish stateless authentication *before* any user-owned data module exists,
so resume parsing, JD analysis, and everything downstream has an identity to attach to.

---

## What we built

**Endpoints** (public; every other route now requires a Bearer token)
- `POST /api/v1/auth/register` → `201` + token; `409` on duplicate email; `400` on validation failure
- `POST /api/v1/auth/login` → `200` + token; `401` on bad credentials

**Components** (`auth/` package)
- **`JwtService`** — HS256 token generation and verification. Signature, issuer, and expiry
  are all enforced.
- **`JwtAuthenticationFilter`** — an `OncePerRequestFilter` that reads the
  `Authorization: Bearer` header and populates the security context.
- **`SecurityConfig`** — stateless session policy, CSRF disabled, a public allowlist for the
  auth endpoints, and filter wiring.
- **`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`** — emit an `ApiResponse` for
  401/403 responses.
- **`AppUserDetailsService`** — loads users by email and maps `Role` → `ROLE_*` authority.
- **`AuthService`** — BCrypt password hashing and the register/login orchestration.
- **Liquibase changeset 005** → the `users` table.

---

## Why we built it this way

- **Auth before data (locked decision)** — any module that owns user data must come *after*
  authentication infrastructure, not before. Building identity first means `user_id` columns
  and ownership checks have something real to point at.
- **Stateless JWT over server sessions** — no session store to run or scale; each request
  carries its own proof of identity. This fits the multi-service architecture, where the
  Python intelligence service can eventually verify the same token without shared session state.
- **HS256 with enforced issuer + expiry** — a symmetric secret is the simplest correct choice
  for a single-issuer system; validating issuer and expiry (not just signature) closes off
  replayed or foreign tokens.
- **Secret from `JWT_SECRET` env var** — the yaml default is local-dev only and must be
  overridden in every real environment. Secrets never live in committed config.
- **BCrypt for passwords** — adaptive hashing with a per-hash salt; plaintext is never stored,
  only the hash lands in `password_hash`.
- **Emails normalized to lowercase** — with a DB-enforced unique constraint, so
  `Foo@x.com` and `foo@x.com` can't both register.

---

## Problems we faced & how we fixed them

### 1. A bad token was becoming a 500 instead of an auth failure
If `JwtService` threw on a malformed/expired/foreign token, that exception would surface as a
server error — implying *we* broke, when really the caller sent a bad token.

**Fix:** verification returns **empty rather than throwing**. A bad token is treated as an
*auth miss* (no authenticated principal), which the filter chain turns into a clean 401 — not
a 500.

### 2. Filter-chain rejections never reach `@ControllerAdvice`
Our global exception handler only sees exceptions from inside controllers. But 401/403 are
decided in the security filter chain, *before* a controller is ever invoked — so those
responses were escaping the standard `ApiResponse` envelope.

**Fix:** custom `RestAuthenticationEntryPoint` (401) and `RestAccessDeniedHandler` (403) that
serialize an `ApiResponse` directly, so auth failures match the shape of every other response.

### 3. Login was leaking which emails exist (account enumeration)
Different messages for "unknown email" vs "wrong password" let an attacker discover which
emails are registered.

**Fix:** login returns an **identical message** for both cases, so account existence is not
leaked through the error text.

---

## What we deferred to later phases

- **`RawResumeController` / `RawResumeService` use `orElseThrow()` on a missing id** — the
  catch-all handler turns that into a **500 instead of a 404**. Scheduled to fix in Phase 2.
- **`raw_resume.user_id` is still nullable and unconstrained** — now that `users` exists,
  Phase 2 will wire it to `users.id` and make it non-nullable.
- **Token refresh / logout (revocation)** — intentionally out of scope for Phase 1; a
  short-lived token with no server state is acceptable for now.

---

## Metrics — current functionality

| Metric                        | Value                                        |
|-------------------------------|----------------------------------------------|
| Auth endpoints live           | 2 (register, login)                          |
| Password storage              | BCrypt hash (never plaintext)                |
| Token algorithm               | HS256; signature, issuer, expiry enforced    |
| Invalid-token behavior        | Empty → 401 (never 500)                       |
| Email handling                | Lowercased; DB unique constraint             |
| Account enumeration           | Prevented (uniform login error)              |
| Unit tests passing            | 11 / 11 (`JwtServiceTest` 5, `AuthServiceTest` 6) |
| Tests requiring a DB          | 0 (fully isolated)                           |
| Liquibase changesets applied  | 5 / 5 (001–005)                              |
