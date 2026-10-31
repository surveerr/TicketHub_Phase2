# TicketHub Auth Service

Owns **identity, credentials, roles, permissions, tokens and account security** - nothing else
(no bookings, payments or events). Spring Boot 4.1.1 / Java 17 / Maven / MySQL, port **8084**,
registers in Eureka as `auth-service`. Schema is managed by Flyway (`V1` tables, `V2` seed roles/permissions).

## Run
1. MySQL on `:3306` (`auth_db` is created automatically; Flyway creates the tables and seeds roles).
2. `mvn spring-boot:run` (add `EUREKA_ENABLED=false` if no Eureka server is running).
3. Swagger UI: http://localhost:8084/swagger-ui.html
4. A bootstrap admin is created on first start: `admin@tickethub.local` / `Admin@12345`
   (override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`, or `BOOTSTRAP_ADMIN_ENABLED=false`).

Email is **not sent yet**: verification / reset links are written to the log (`LoggingEmailService`).
Implement `EmailService` with SMTP/SES/etc. when ready.

## Endpoints
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | creates USER, sends verification email, 201 |
| POST | `/api/v1/auth/login` | public | `{email,password,deviceInfo?}` -> access + refresh token |
| POST | `/api/v1/auth/refresh` | public | rotates the refresh token |
| POST | `/api/v1/auth/logout` | public | revokes the given refresh token |
| POST | `/api/v1/auth/logout-all` | Bearer | revokes every session |
| POST | `/api/v1/auth/forgot-password` | public | always 202 (no account enumeration) |
| POST | `/api/v1/auth/reset-password` | public | `{token,newPassword}`, revokes all sessions |
| POST | `/api/v1/auth/verify-email` | public | `{token}` |
| POST | `/api/v1/auth/resend-verification` | public | `{email}`, always 202 |
| POST | `/api/v1/auth/change-password` | Bearer | revokes all sessions, returns a fresh token pair |
| GET / PUT / DELETE | `/api/v1/users/me` | Bearer | profile / update / soft-delete |
| GET | `/api/v1/admin/users?q=&status=&page=&size=` | `VIEW_USERS` | search |
| GET | `/api/v1/admin/users/{id}` | `VIEW_USERS` | |
| PATCH | `/api/v1/admin/users/{id}/status` | `BLOCK_USER` | `{"status":"ACTIVE\|INACTIVE\|BLOCKED"}` |
| PATCH | `/api/v1/admin/users/{id}/roles` | `ASSIGN_ROLE` | `{"assign":["EVENT_MANAGER"],"remove":[]}` |
| DELETE | `/api/v1/admin/users/{id}` | `DELETE_USER` | soft delete |

Errors use the same body as event-service: `{timestamp,status,code,message,path}`.

## Access token contract (for the other services)
HS256, signed with `auth.jwt.secret` (`JWT_SECRET`). Claims: `sub` = user id, `email`,
`roles` = `["USER"]` (no `ROLE_` prefix), `permissions` = `["VIEW_EVENT", ...]`, `token_type=access`, `iss`, `exp`.
event-service's existing `JwtAuthenticationFilter` already understands `sub` + `roles`, so it accepts these tokens as long as
**`JWT_SECRET` is identical** in both services. Access tokens live 15 min, refresh tokens 7 days (both configurable).

## Security behaviour
- Passwords: BCrypt (strength 12); policy 8-64 chars with upper, lower, digit, special.
- Refresh / reset / verification tokens: random 256-bit, **only the SHA-256 hash is stored**.
- Refresh rotation with **reuse detection**: presenting an already-revoked refresh token revokes all of that user's sessions.
- Lockout: 5 failed logins -> locked 15 min (`423 ACCOUNT_LOCKED`). Blocked/inactive status is only revealed after a correct password.
- Rate limiting (per IP + endpoint, in-memory) on login, register, forgot/reset password, verify/resend.
- Blocking, deactivating, deleting, password reset/change revoke refresh tokens. An already-issued access token stays valid until it expires (<= 15 min).
- Audit trail in `audit_logs` (logins, failures, lockouts, resets, role/status changes, ...).
- Admins cannot block/delete themselves or remove their own ADMIN role.

## Roles and permissions (seeded in `V2`)
`USER`: VIEW_EVENT, BOOK_TICKET, CANCEL_BOOKING, VIEW_BOOKING - `ADMIN`: everything -
`THEATRE_OWNER` / `EVENT_MANAGER`: VIEW_EVENT, VIEW_BOOKING, CREATE_EVENT, UPDATE_EVENT.
Change them with a new Flyway migration.

## Differences from the diagram
- Login uses **email** (the schema has no username column).
- Added `users.failed_login_attempts`, `locked_until`, `password_changed_at` (needed for lockout) and an
  `email_verification_tokens` table, plus `audit_logs`.
- Added permissions `ASSIGN_ROLE` and `DELETE_USER` for the admin endpoints.
- `require-email-verification` defaults to `false` (set `REQUIRE_EMAIL_VERIFICATION=true` to enforce it).
- Paths are prefixed `/api/v1` like the other services.

## Not implemented (diagram Phase 3 / optional)
MFA/OTP, CAPTCHA, social login, password-expiration policy, login-history / "my devices" endpoints,
suspicious-login detection, real email delivery.

## Wiring it into the rest of TicketHub
- **Gateway (8080):** route `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/admin/**` to `auth-service`.
  event-service currently has its own static `POST /api/v1/auth/login`; remove it (or move it) so the route is not ambiguous.
- booking-service reads `X-User-Id`; have the gateway validate the JWT and set `X-User-Id` from `sub`
  (and strip any client-supplied `X-User-Id`).
- Behind the gateway, make sure it overwrites `X-Forwarded-For`, because the rate limiter and audit log trust its first value.
- Production: real `JWT_SECRET` from a secret manager, no default admin password, real `EmailService`.
