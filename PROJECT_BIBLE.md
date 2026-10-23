# TasKlean Project Bible

Deep context, architecture decisions, domain model, and patterns for the TasKlean backend API.

## What TasKlean is

TasKlean is a household task management **PWA** (Progressive Web App) for families, roommates, and couples. Tagline: **"Clean tasks, clear minds"**. Domain: **tasklean.app**.

It solves the problem of household chores slipping through the cracks by providing task visibility, assignment, accountability, and notifications across group members. Users form groups (households), create recurring and one-off cleaning tasks, assign them to members, and track completions with optional photo proof.

The backend is a Spring Boot REST API. The frontend will be **Next.js + TailwindCSS** in a separate repo (not started yet). Expected frontend origin is `localhost:3000` in dev (based on OAuth redirect URI).

### MVP features

- User registration (email/password + Google OAuth) and JWT authentication
- Groups with invite codes — users can be in **multiple groups**
- Two roles: GroupAdmin (create/manage group, invite/remove members) and GroupMember
- Task creation with: name, description, photo, priority, time estimate, recurrence, tags, categories
- Task types: recurring vs one-off
- Task assignment to group members
- Task completion with optional photo proof
- Notifications and pinging members about tasks
- Filter/search tasks by assignee, priority, status, tags, categories
- List and calendar views

### Future features (not MVP)

- Task rotation and fairness dashboard
- Reward/gamification system
- Task templates
- Task dependencies
- Analytics and insights

## Domain model

### Entity relationship map

```
User (1) ──── (*) Device
  │
  │ (1)
  ▼
GroupMember (*) ──── (1) Group
  │                      │
  │                      ├──── (*) Category
  │                      ├──── (*) Tag
  │                      └──── (*) AuditLog
  │
  ├──── (*) Task (created_by, assigned_to)
  │         │
  │         ├──── (*) TaskTag ────── Tag
  │         ├──── (*) TaskCompletion
  │         └──── (0..1) Category
  │
  └──── (*) AuditLog (group_member_id)

User (1) ──── (*) Notification
User (1) ──── (*) RefreshToken
User (1) ──── (0..1) UserTwoFactor
User (1) ──── (*) TwoFactorRecoveryCode
User (1) ──── (*) TwoFactorChallenge (pending sign-ins)
User (1) ──── (0..1) UserSettings
User (1) ──── (*) NotificationSettings (one per notification type)
```

### Core aggregate: Group

Everything revolves around the Group. A User does nothing alone — they must be a GroupMember to create tasks, be assigned tasks, or complete tasks. A user can be a member of **multiple groups** simultaneously (e.g., one for family, one for roommates). The Group is the authorization boundary: users can only interact with entities belonging to groups they're members of.

### Entity details

| Entity | PK | Has UID | Extends BaseEntity | Soft-delete | Notes |
|---|---|---|---|---|---|
| User | `id_user` | Yes (`uid`, 100 chars) | Yes | `is_active` | Also has `google_sub` for OAuth; `role` (platform role: `SUPER_ADMIN`/`ADMIN`/`USER`) |
| Group | `id_group` | Yes (`uid`, 500 chars) | Yes | `is_active` | Has `invite_code` for joining |
| GroupMember | `id_group_member` | No | No | `is_active` + `date_left` | Junction with `role` (`GROUP_ADMIN`/`GROUP_MEMBER`); unique on `(user_id, group_id)` |
| Task | `id_task` | Yes (`uid`, 500 chars) | Yes | `is_active` | Central domain object |
| Category | `id_category` | No | Yes | `is_active` | Scoped to group; unique on `(group_id, name)` |
| Tag | `id_tag` | No | Yes | `is_active` | Scoped to group; unique on `(group_id, name)` |
| TaskTag | `id_task_tag` | No | No | No (hard delete via CASCADE) | Junction; unique on `(task_id, tag_id)` |
| TaskCompletion | `id_task_completion` | No | No | None | Immutable completion record |
| Device | `id_device` | No | Yes | `is_active` | Per-device record: sign-in sessions + push tokens. `client_device_id` (stable id from `X-Device-Id`), `ip_address` (last seen, refreshed on token rotation — not the sign-in address), `user_agent`; push `device_token` nullable |
| Notification | `id_notification` | No | No | None | Has `is_read` + `read_at`; `type` is a free-form column, but the canonical values live in the `NotificationType` enum (`TASK_ASSIGNED`, `TASK_COMPLETED`, `TASK_REMINDER`, `MEMBER_JOINED`), which is also what `notification_settings` is keyed by |
| AuditLog | `id_audit_log` | No | No | None | Immutable; polymorphic via `entity_type` + `entity_id` |
| RefreshToken | `id_refresh_token` | No | No | `is_revoked` | SHA-256 hashed token; 14-day expiry (configurable via `jwt.refresh-expiration`); `CASCADE` on user delete; nullable `device_id` (rotation keeps the same device = stable session identity) |

### Ownership chain

```
User → GroupMember → Task → TaskCompletion
                  → Task → TaskTag
Group → Category
Group → Tag
Group → AuditLog
User  → Device
User  → Notification
User  → RefreshToken
```

Tasks are created by and assigned to GroupMembers, not Users directly. This is deliberate — a user's permissions within a group are mediated through their membership.

### GroupMember roles

The `role` column is CHECK-constrained to `'GROUP_ADMIN'` or `'GROUP_MEMBER'` (the `GroupRole` enum, V17). Self-referential FKs `added_by` and `removed_by` track who added/removed whom.

### Task lifecycle

- **Statuses**: `TODO`, `IN_PROGRESS`, `COMPLETED`, `ARCHIVED` (CHECK constraint in migration)
- **Priorities**: `HIGH`, `MEDIUM`, `LOW` (CHECK constraint in migration)
- **Recurrence types**: `DAILY`, `WEEKLY`, `MONTHLY`, `CUSTOM` (CHECK constraint, nullable)
- **Recurrence pattern**: JSONB column for flexible recurrence rules (e.g., specific days of week)
- **Photo proof**: `requires_photo_proof` flag; completion photos stored as URLs in `TaskCompletion.completion_photo_url`
- **Completion**: each completion is a separate `TaskCompletion` record (supports recurring tasks being completed multiple times)

### Notification types

The `type` column is a free-form VARCHAR(50) — no CHECK constraint in the migration. Types are application-defined (e.g., task assigned, task completed, member joined).

**Design decision**: Notifications reference `user_id` (not `group_member_id`) because some notifications — like group invites — happen before the user has a membership in that group. The `task_id` and `group_id` columns are denormalized for query performance (avoids joining through task → group).

### AuditLog design

Polymorphic audit trail using `entity_type` (VARCHAR) + `entity_id` (BIGINT). Not a JPA `@Inheritance` — just string-based type discrimination. Scoped to both a group and optionally a group member.

**Writing entries** — `AuditLogService.recordEvent(entityType, entityId, action, message, group)` is the single write path. It runs in the caller's transaction (an entry is persisted only if the operation commits) and resolves the actor and IP from the request context via the `AuditContext` helper. Actions are the `AuditAction` enum; entity types are `AuditEntityType` constants (both uppercase, e.g. `TASK`/`CREATE`). Pass a `null` group for group-less events (authentication/account), which the schema allows.

**Actor attribution** — `actor_user_id` (nullable, V15) records the authenticated caller from the security context, so the actor is captured even for group-less events and when actor ≠ subject (e.g. one user deleting another's account). `group_member_id` still records the actor's membership/role context for group-scoped events. Public `/api/auth/**` routes have no authenticated caller, so `actor_user_id` is null there and the user is identified by `entity_id` instead.

**What's audited** — the mutating service methods call `recordEvent(...)`:
- Domain (group-scoped): `Task`, `Group`, `Category`, `Tag` CREATE/UPDATE/DELETE; `GroupMember` MEMBER_ADDED/ROLE_CHANGED/MEMBER_REMOVED; `TaskCompletion` COMPLETE.
- Auth/account (group-less): REGISTER, LOGIN, LOGOUT, ACCOUNT_DELETED, user profile UPDATE, EMAIL_VERIFIED, PASSWORD_RESET, and LOGIN_FAILED on every credential rejection where the user is known. A login attempt for an **unknown email** is not audited — `entity_id` is `NOT NULL` and there is no user id to record (it stays a WARN in the ops log only). These run on anonymous endpoints, so they use `AuditLogService.recordAccountEvent(userId, …)`, which sets the actor to the subject (the security context has no caller) so `actor_user_id` is populated.

## Database design

### Schema conventions

- **Column naming**: `snake_case` throughout. PK is `id_<entity>`, FKs are `<referenced_entity>_id` or descriptive (`created_by`, `assigned_to`, `added_by`).
- **Types**: `BIGSERIAL` for PKs, `VARCHAR(N)` for bounded strings, `TEXT` for unbounded, `TIMESTAMP` for dates (no timezone in column — application enforces UTC via injected `Clock` bean), `BOOLEAN` for flags, `JSONB` for structured data.
- **Defaults**: `is_active DEFAULT TRUE`, `date_created DEFAULT NOW()`, `date_updated DEFAULT NOW()`, `is_read DEFAULT FALSE`.
- **Reserved words**: `user` and `group` are quoted as `"user"` and `"group"` everywhere — SQL, JPA `@Table`, and Spring Data queries.
- **Cascades**: `ON DELETE CASCADE` on `device → user`, `task_tag` FKs, `refresh_token → user`, `refresh_token → device`, `user_settings → user`, `notification_settings → user`, and all three two-factor tables → `user`. All other FKs have no cascade — application handles deletion logic.

### Indexes

Every FK column is indexed. Additional indexes on:
- `user`: `email`, `uid`, `google_sub`
- `group`: `uid`, `invite_code`
- `task`: `group_id`, `assigned_to`, `status`, `priority`, `uid`
- `notification`: `(user_id, is_read)` composite for unread queries
- `audit_log`: `(entity_type, entity_id)` composite, `date_created` for time-range queries, `actor_user_id` for by-user activity queries
- `refresh_token`: `token` (unique, for lookup by hashed value), `user_id` (for bulk revocation), `device_id` (per-device revocation + active-session lookup)
- `user_settings`: unique `user_id` (one row per user; the UNIQUE constraint backs every lookup, so no separate index)
- `notification_settings`: unique `(user_id, type)` (one row per user per type; leftmost-prefixed on `user_id`, so it also backs the per-user list)
- `user_two_factor`: unique `user_id` (one factor per user; the UNIQUE constraint backs every lookup)
- `two_factor_recovery_code`: unique `(user_id, code_hash)` — per user, not global: two users colliding is not an error, one user holding the same code twice is
- `two_factor_challenge`: unique `token_hash` (lookup by hashed token), `user_id`, `expires_at` (for scheduled cleanup)
- `device`: unique `(user_id, client_device_id)` (one row per user per device; multiple NULLs allowed for push-only/legacy rows)

### Migration sequence (V1–V11)

| Version | Table | Dependencies |
|---|---|---|
| V1 | `user` | None |
| V2 | `device` | `user` |
| V3 | `group` | None |
| V4 | `group_member` | `user`, `group`, self-referential |
| V5 | `category` | `group` |
| V6 | `tag` | `group` |
| V7 | `task` | `group`, `group_member`, `category` |
| V8 | `task_tag` | `task`, `tag` |
| V9 | `task_completion` | `task`, `group_member` |
| V10 | `notification` | `user`, `task`, `group` |
| V11 | `audit_log` | `group_member`, `group` |
| V12 | `email_verification` + `user.is_email_verified` column | `user` |
| V13 | `refresh_token` (with indexes on `token` and `user_id`) | `user` |
| V14 | Enables Row Level Security on all tables (no new table) | all tables |
| V15 | Adds `audit_log.actor_user_id` column + index (no new table) | `user` |
| V16 | Adds `user.role` column (platform role, CHECK-constrained; no new table) | `user` |
| V17 | Renames `group_member.role` values `ADMIN`/`MEMBER` → `GROUP_ADMIN`/`GROUP_MEMBER` (migrates data + CHECK) | `group_member` |
| V18 | `password_reset_token` | `user` |
| V19 | Repurposes `device` for sessions (nullable `device_token`; adds `client_device_id`/`ip_address`/`user_agent` + unique `(user_id, client_device_id)`); adds `refresh_token.device_id` FK (no new table) | `device`, `refresh_token` |

| V20 | `user_settings` (one row per user: `theme`, `locale`; unique `user_id`, cascade on user delete) | `user` |
| V21 | `notification_settings` (one row per user per notification type: `in_app`, `email`; unique `(user_id, type)`, cascade on user delete) | `user` |
| V22 | `user_two_factor` (one factor per user: `provider`, `factor_ref`, `secret_encrypted`, `digest`, `is_enabled`, `confirmed_at`, `last_used_step`), `two_factor_recovery_code` (hashed, single-use), `two_factor_challenge` (hashed opaque token, `expires_at`, `attempts`, `consumed_at`); all cascade on user delete | `user` |

Next available version: **V23**.

### Dev seed data

Dev-only seed data lives in `src/main/resources/db/seed/R__seed_data.sql` — a Flyway **repeatable migration** that runs after all versioned migrations. The dev profile adds `classpath:db/seed` to `spring.flyway.locations`; prod only has `classpath:db/migration`, so seed data never touches production.

The seed creates a realistic dataset: 3 users (alice@ Google-linked; bob@/charlie@ email+password, dev password `DevPass1!`), 2 groups, 5 group members (including a multi-group user), categories, tags, 8 tasks with varied statuses/priorities/recurrence, task-tag associations, completions (one with photo proof), notifications (read/unread mix), audit log entries, devices, and settings (only alice@ has saved user settings and two notification-settings rows — bob@/charlie@ exercise the lazy default path with no rows at all).

All inserts use `ON CONFLICT DO NOTHING` or `NOT EXISTS` checks for idempotency. To add data, edit `R__seed_data.sql` — Flyway detects the checksum change and re-runs it on next startup.

**Fresh reset**: `docker-compose down -v` + restart wipes everything and re-applies all migrations + seed.

### Hibernate strategy

`ddl-auto=validate` — Hibernate validates entities against the Flyway-managed schema at startup but never modifies it. All schema changes go through migrations.

## API design

### Response envelope

Every endpoint returns `ApiResponse<T>`:
```json
{ "success": true, "message": null, "data": { ... } }:
{ "success": false, "message": "User not found", "data": null }
{ "success": false, "message": "Email not verified…", "code": "EMAIL_NOT_VERIFIED", "data": null }
```

### Error codes

An error envelope may carry a machine-readable `code` next to the human `message` (serialized only when
set). Constants live in `common/ErrorCodes.java`; a 401 gets one by throwing
`AuthFailureException(message, code)`.

| Code | Thrown by | Meaning for the client |
|---|---|---|
| `EMAIL_NOT_VERIFIED` | login, after a correct password | send them to the verify-code screen |
| `ACCOUNT_DEACTIVATED` | login / Google / 2FA verify, after credentials check out | account is soft-deleted, offer support |
| `PASSWORD_INVALID` | `/api/account/**` re-auth (change password, deactivate, 2FA setup/disable/regenerate) | flag the password field, keep the form open |
| `TWO_FACTOR_CODE_INVALID` | 2FA enable / verify / disable | flag the code field, let them retype |
| `TWO_FACTOR_CHALLENGE_INVALID` | 2FA verify: unknown, expired, spent, out of attempts, factor removed | restart the sign-in |

Rules for adding one:
- A code is never finer-grained than the message it accompanies. The five unusable-challenge cases share
  one code *and* one message on purpose, so a caller learns nothing about a token they don't hold.
- The generic login failure (unknown email / wrong password / OAuth-only) stays **uncoded** — a code there
  would be an account-enumeration oracle. `AuthServiceTest` asserts the thrown type carries no code.
- Codes that name account state (`EMAIL_NOT_VERIFIED`, `ACCOUNT_DEACTIVATED`) are only reachable *after*
  the caller proved ownership, which is why the login flow checks the password first.

### Generated contract

The authoritative contract is **`docs/openapi.json`**, generated by springdoc (3.x, the line that tracks Spring Boot 4.x) from the controllers, DTOs and Bean Validation annotations. The catalog below is a human orientation summary, not the contract — when the two disagree, the spec is right.

- **Live spec** at `/v3/api-docs`, **Swagger UI** at `/swagger-ui.html`. Both are generated from the running context, so they always match the code. Both are **disabled in prod** (`springdoc.api-docs.enabled=false`) — the endpoint map is not something to publish.
- **The committed file keeps itself current**: `OpenApiSpecExporter` (dev profile only) rewrites `docs/openapi.json` on startup whenever the generated spec differs, and leaves it alone otherwise. Output is key-sorted and pretty-printed so diffs are readable and don't churn, and `servers` is pinned to a relative `/` so the file doesn't depend on which port the app ran on.
- **Descriptions come from Javadoc**, not annotations: therapi retains it at runtime, so a method's Javadoc becomes the operation summary/description, `@param` documents path parameters and `@return` the response. Improving a comment improves the spec; there is no second copy to maintain.
- **Auth is modelled, authorization is not.** A global bearer-JWT requirement marks every endpoint as needing a token, and the public auth endpoints opt out with an empty `@SecurityRequirements`. springdoc does not read `@PreAuthorize`, so **which role** an endpoint needs is not in the spec — a client cannot predict a 403 and must handle it at runtime.
- **Shared error responses** (`400`/`401`/`403`/`429`/`500`) are attached by rule in `OpenApiConfig` rather than annotated per operation: `400` only where an operation takes a body or parameters, `403` only where it is not public. `404` and `409` are left per-operation on purpose — any automatic rule would be wrong somewhere (`POST /api/groups/join` returns 404 with no path variable; a list endpoint with one never does).
- **What the spec cannot say**: semantics. "Rejoining does not restore an admin role", "unknown and soft-deleted invite codes return the same error" — that knowledge lives in this document. The spec is authoritative on shapes, this file on behaviour.

### Endpoint catalog

| Resource | Base path | Identifier | CRUD | Notes |
|---|---|---|---|---|
| User | `/api/users` | `/{uid}` | GET `/me`, GET, GET all, PUT, DELETE, PUT `/{uid}/role` | No POST — creation via auth flow; `/me` is the caller's own profile (no identifier, resolved from the JWT); role change and DELETE are SUPER_ADMIN-only (users deactivate their own account via `/api/account/deactivate`) |
| Account | `/api/account` | — | POST `/change-password`, POST `/set-password`, GET + POST + DELETE `/connected-accounts`, POST `/deactivate`, GET `/2fa`, POST `/2fa/setup`, POST `/2fa/enable`, POST `/2fa/disable`, POST `/2fa/recovery-codes` | Self-scoped account security — caller from the JWT. Change/set password rotate sessions (revoke all, keep current device, return new tokens); `/connected-accounts` lists + links/unlinks Google (link requires the Google email match the account email; unlink blocked without a password — lockout guard); `/deactivate` soft-deletes + revokes all sessions; `/2fa*` enrolls and manages the authenticator second factor (not yet enforced at sign-in) |
| Group | `/api/groups` | `/{uid}` | Full CRUD, GET `/mine`, POST `/join`, POST `/{uid}/invite-code` | POST generates uid + invite code, and makes the creator GROUP_ADMIN. `/mine` lists the caller's own groups with their `myRole`; `/join` redeems an invite code; `/{uid}/invite-code` rotates the code (GROUP_ADMIN) |
| Task | `/api/tasks` | `/{uid}` | Full CRUD | GET all requires `?groupId=` |
| Category | `/api/categories` | `/{id}` | Full CRUD | GET all requires `?groupId=` |
| Tag | `/api/tags` | `/{id}` | Full CRUD | GET all requires `?groupId=` |
| GroupMember | `/api/group-members` | `/{id}` | POST, GET, GET all, PATCH role, DELETE | GET all requires `?groupId=`; PATCH `/{id}/role?role=` |
| Device | `/api/devices` | `/{id}` | GET (list mine), POST (register push token), PATCH (rename), POST `/{id}/logout`, DELETE | Self-scoped — caller resolved from the JWT, no `userId` param. GET returns the caller's devices with `activeSession`/`currentDevice` flags; `/{id}/logout` revokes that device's session; DELETE removes the device (+revokes) |
| UserSettings | `/api/user-settings` | — | GET (own), PUT (own) | Self-scoped, no identifier in the path — caller from the JWT. `theme` (LIGHT/DARK/SYSTEM) + `locale` (EN/DE/SL). The row is created lazily on the first PUT; a GET for a user with none returns the defaults without writing. Two first-time PUTs racing is safe — the one losing the `unique(user_id)` insert retries in a fresh transaction and updates the winner's row |
| NotificationSettings | `/api/notification-settings` | `/{type}` | GET (own matrix), PUT (own, one type), PUT `/batch` | Self-scoped, no user identifier in the path — caller from the JWT; the only path variable is the notification type. GET always returns one entry per `NotificationType` (`TASK_ASSIGNED`/`TASK_COMPLETED`/`TASK_REMINDER`/`MEMBER_JOINED`), merging the defaults for types with no row, so a client never needs to know them. PUT replaces one type's channels and creates the row lazily; a racing first write retries like `user-settings`. PUT `/batch` applies several entries in one transaction (type in the body, omitted types untouched, duplicate types → 400) and returns the whole matrix read back after the writes — the literal `/batch` outranks the `/{type}` template, so it is not parsed as a type. These are **preferences only** — nothing reads them yet, the delivery pipeline is a later phase |
| TaskCompletion | `/api/task-completions` | — | POST, GET by task, GET by member | GET requires `?taskId=`; `/member/{id}` |
| Notification | `/api/notifications` | — | GET, GET unread, count, PATCH read | All GETs require `?userId=`; PATCH `/{id}/read` |
| AuditLog | `/api/audit-logs` | — | GET by group, entity, member | GET requires `?groupId=`; `/entity?entityType=&entityId=`; `/member/{id}` |

### HTTP status codes

- `200` — success (GET, PUT, PATCH, DELETE, login)
- `201` — created (POST, register)
- `400` — validation failure (MethodArgumentNotValidException), malformed body (HttpMessageNotReadableException), or bad param/enum type (MethodArgumentTypeMismatchException)
- `401` — unauthorized (missing/invalid token, bad credentials, deactivated account)
- `403` — authenticated but not authorized (AccessDeniedException from `@PreAuthorize`)
- `404` — ResourceNotFoundException, or an unknown route (NoResourceFoundException)
- `405` — wrong HTTP method on a known path (HttpRequestMethodNotSupportedException)
- `409` — DuplicateResourceException, or a violated domain invariant (BusinessRuleException)
- `500` — catch-all `@ExceptionHandler(Exception.class)` for anything unhandled; logs the stack trace (with requestId/userId) and returns a generic message — never leaks internal details

All error responses use the JSON `ApiResponse` envelope, never the Whitelabel HTML page: `ApiErrorController` (a custom `ErrorController`) renders any `/error` dispatch — including errors raised outside the MVC handler — as JSON, for every `Accept` header.

### Identifiers in URLs

Entities with a `uid` field (User, Group, Task) use `/{uid}` as the path variable. All other entities use `/{id}` (the Long PK). This is intentional — UIDs are opaque external identifiers, while internal PKs are used for subordinate entities that are never referenced outside the API.

## Security architecture

### Current state

Email/password and Google sign-in are both implemented. All endpoints except `/api/auth/**` require a valid JWT Bearer token. Two-factor (authenticator-app TOTP) can be enrolled at `/api/account/2fa` and **is enforced at sign-in**: login and Google sign-in return a challenge instead of tokens, and `POST /api/auth/2fa/verify` completes it.

### Row Level Security (Supabase)

Supabase auto-exposes every `public`-schema table through its PostgREST API using the public `anon` key. To close that path, **RLS is enabled on all tables** (V14 migration) with **no policies** — which denies all access to the `anon`/`authenticated` API roles. The Spring backend is unaffected because it connects as the table **owner**, and owners bypass RLS (we deliberately do *not* use `FORCE ROW LEVEL SECURITY`). All authorization stays in the service layer with our own JWT; we do not use Supabase Auth, so `auth.uid()` policies aren't applicable. **Every new table's migration must enable RLS** (see CLAUDE.md conventions).

### Token architecture (implemented)

**Access token (JWT)**:
- HMAC-SHA signed JWT with claims: `sub` (email), `userId` (internal PK), `uid` (public identifier), `role` (platform role), `iat`, `exp`
- Expiration: **15 minutes** (configurable via `jwt.expiration` in ms; dev profile overrides to 1 year for easier local testing, itself overridable via the `JWT_EXPIRATION` env var — e.g. set it low in Compose to exercise refresh/rotation)
- Secret: min 256 bits, configured per profile via `jwt.secret`
- Library: jjwt 0.12.6

**Stateless authentication**: the token carries everything needed to authenticate *and* authorize a request (identity + platform role), so `JwtAuthenticationFilter` builds an `AuthPrincipal` (record: `userId`, `uid`, `email`, `role`) straight from the claims with **no database load per request**. The trade-off is *bounded staleness*: a role change or account deactivation only takes effect at the next token **refresh**, so `RefreshTokenService.refresh` re-checks `is_active` (revokes all tokens + denies if deactivated) and re-reads the role into the new access token. The access-token TTL (15 min in prod) is that staleness window. Instant revocation would need a store-backed blocklist (e.g. Redis) — deliberately deferred.

**Refresh token (opaque)**:
- 32-byte cryptographically random value (via `SecureRandom`), Base64url-encoded
- Stored in DB **SHA-256 hashed** (not plaintext) — raw token only sent to client
- Expiration: **14 days** (configurable via `jwt.refresh-expiration` in ms)
- **Single-use with rotation**: each refresh revokes the old token and issues a new pair
- Logout revokes **all** refresh tokens for the user
- Scheduled cleanup (`@Scheduled`, every 6 hours) purges expired and revoked tokens from DB
- Entity: `RefreshToken` (table `refresh_token`, V13 migration)

### Client session architecture (decided)

The Spring API stays a **pure, stateless Bearer API for all clients** — it never issues cookies or handles CSRF. Each client stores tokens the way that's safe for its platform, and the browser-only cookie/CSRF concern is pushed to the web layer:

- **Web (Next.js)** — a **BFF (Backend-For-Frontend)**: the Next.js *server* logs in against Spring, holds the access + refresh tokens server-side, and hands the browser only an **httpOnly, Secure, SameSite cookie**. Browser JS never sees a JWT (immune to XSS token theft). Browser ↔ Next.js uses the cookie (+ CSRF token on mutations, `SameSite=Lax`); Next.js ↔ Spring uses `Authorization: Bearer` server-to-server. On refresh, the BFF gets the rotated pair from Spring and **re-writes the cookie** (persistent cookie ~ refresh TTL gives a rolling 14-day session; an absolute cap is a future hardening).
- **Mobile (React Native or Flutter)** — talks to Spring **directly** with `Authorization: Bearer`, storing tokens in **OS-backed secure storage** (iOS Keychain / Android Keystore — never AsyncStorage/SharedPreferences). On rotation it overwrites the stored tokens.

Rationale: mobile wants Bearer + secure device storage (cookies are unnatural on native), so making Spring cookie-based would burden mobile. Keeping Spring Bearer-only serves web (via BFF) and mobile (directly) from one unchanged API. This is why CSRF stays disabled on Spring — no browser talks to it directly.

### Request flow

0. `RequestLoggingFilter` (highest precedence, runs before the Spring Security chain) mints/adopts a `requestId`, puts request context into MDC, and logs a summary line on completion — so even 401s are logged and correlated
1. `JwtAuthenticationFilter` (runs before Spring's authorization check) extracts the Bearer token from the `Authorization` header
2. If valid: builds an `AuthPrincipal` from the token claims (no DB load) and places it into `SecurityContextHolder` with a `ROLE_<platform role>` authority
3. If missing/invalid: request continues as anonymous
4. `RateLimitFilter` (after the JWT filter) applies token-bucket limits — per-user **and** per-IP when authenticated, per-IP by tier otherwise (IP via `ClientIpResolver`, never `getRemoteAddr()`); over-limit → 429 + `Retry-After`
5. Spring's `authorizeHttpRequests` rules decide allow/deny based on whether SecurityContext has an authenticated principal
6. Denied requests → `JwtAuthenticationEntryPoint` returns 401 JSON matching `ApiResponse` envelope

### Security filter chain config

- CSRF disabled (stateless API, tokens in JSON body — will need revisiting if refresh tokens move to httpOnly cookies)
- Session policy: `STATELESS` (no server-side sessions)
- CORS: configurable origins (`cors.allowed-origins`), credentials enabled, `Authorization` + `Content-Type` headers
- Public paths: `/api/auth/**`, `/actuator/health`, `/error`
- All other paths: `authenticated()`
- Password encoding: BCrypt

### Password policy

Registration passwords are enforced by a custom Bean Validation constraint — `@ValidPassword` → `PasswordPolicyValidator` (`common/validation`) — on `RegisterRequest.password`. Rules: **8–64 characters**; at least one each of **uppercase, lowercase, digit, and a special** (any non-alphanumeric, so a space counts); and **not obviously guessable** (a short common-password list, seen through lowercasing, leet-substitution, a stripped trailing digit/symbol tail, all-same-character, and consecutive runs). Enforced **only at register** — login and Google provisioning are untouched (no lockouts, no policy leak on login). Empty/missing password is part of the same constraint (no separate `@NotBlank`), so one constraint owns the whole policy.

**Messages are deliberately descriptive** (per-rule 400s, e.g. "Add a number."), not generic: the policy isn't a secret, a policy miss is a client 400 not a server error, and the mobile client (direct Bearer, no BFF) needs the specific message to tell the user what to fix.

**It is a 1:1 mirror of the frontend** (`tasklean-fe/src/lib/validation/password/policy.ts` + `rules.ts`) — same rules, messages, and check order, reporting only the first failing rule. The frontend is the UX layer; the backend is the real boundary (the API can be called directly). The two are kept honest by **shared contract vectors**: `src/test/resources/password-policy-vectors.json` (canonical here, mirrored in the FE) lists `(password → expected message)` cases both suites run (`PasswordPolicyContractTest`), so drift fails on whichever side changed. Change the policy → edit both implementations and the vectors.

**Alternatives considered:** [Passay](https://www.passay.org) (the standard Java password-policy library) and Spring Security 6.3's `CompromisedPasswordChecker` / `HaveIBeenPwnedRestApiPasswordChecker`. Hand-porting won because the requirement was byte-identical parity with the FE's wording — Passay would need a custom message resolver and custom dictionary/sequence rules to match, adding moving parts and divergence risk. `CompromisedPasswordChecker` is the clean built-in for the (still-open) real breached-password check.

**Caveats (both intentional, matching the FE):** BCrypt truncates at 72 bytes, so a 64-character *multibyte* password is effectively capped (still strong — not a hole); and `[A-Z]`/`[a-z]` are ASCII-only, so an all-non-Latin password is nudged to "add a letter."

### Rate limiting

Token-bucket rate limiting (Bucket4j) in `RateLimitFilter`, added to the security chain right after `JwtAuthenticationFilter` so it can key by user id. In-memory (Caffeine) — fine for a single instance; the `BucketRegistry` interface is the seam to swap in Redis if the app ever runs more than one instance.

- **Keying:** an **authenticated** request must pass **both** a per-user bucket and a per-IP bucket; an **unauthenticated** request is limited per IP by the endpoint's tier.
- **Tiers** (`ratelimit.*` in `RateLimitProperties`, overridable per env): auth 10/min, register 5/hour, refresh 20/min, forgot-password 5/hour, reset-password 10/min, two-factor verify 10/min, api 120/min per user + 300/min per IP, join 10/hour per user. The two-factor tier is keyed by IP and caps code guessing across fresh challenges; the 5-attempt cap only covers one challenge.
- **The join tier is additive, not alternative.** `POST /api/groups/join` is authenticated, so it passes the per-user and per-IP api buckets *and* its own tighter `join` bucket. Every other tier replaces the default; this one stacks, so a guessable 8-character invite code keeps the general api protections as well.
- **Client IP** comes from `ClientIpResolver`, not `getRemoteAddr()`. Behind Cloudflare the socket address is an edge node shared by unrelated users and rotating per connection, so keying on it made one abuser's quota everybody's: a single attacker could exhaust the 10/min `auth` tier for every user at once. The resolver yields the end user's address for our own frontend's traffic and the caller's own address for anyone else — see **Client IP resolution**.
- **Rejection:** 429 with a `Retry-After` header and the JSON `ApiResponse` envelope (written directly — filters run before Spring MVC). Skips `/actuator/**` and `/error`; disable entirely with `ratelimit.enabled=false`.
- **Deferred (seams, not built):** a Redis-backed store (only if >1 instance) and a Cloudflare edge layer (volumetric DDoS shield). Per-account buckets on login and forgot-password are the natural next control, since an attacker can rotate IPs but not the victim's email.

### Client IP resolution

`common/web/ClientIpResolver` is the single place the end user's IP is determined, for two consumers with different needs: attribution (the audit trail, new-sign-in emails) wants *who acted*; `RateLimitFilter` wants a key *the caller cannot choose*. It returns both the address and whether it came from a trusted hop.

Browsers never reach the API directly — the Next.js BFF calls it server-to-server — and Render fronts both services with Cloudflare. So `getRemoteAddr()` (with `server.forward-headers-strategy=native`, which resolves the *immediate* caller) is a Cloudflare edge address, never the user. The BFF therefore reads `CF-Connecting-IP` off the browser's request and forwards it as `X-Device-Ip`.

Any caller can send that header, so it is honoured only when `X-Internal-Hop` carries a configured `BFF_HOP_SECRETS` value — possession proves the request came from our own frontend. Without that gate a `curl` loop rotating `X-Device-Ip` would mint a fresh bucket per request and there would be no rate limit at all.

| Condition | IP | `trustedHop` |
| --- | --- | --- |
| `X-Internal-Hop` matches a secret **and** `X-Device-Ip` is a valid IP literal | `X-Device-Ip` | `true` |
| else `CF-Connecting-IP` is a valid IP literal (a direct caller — Cloudflare sets it from the TCP peer, so it is unforgeable) | `CF-Connecting-IP` | `false` |
| else | `getRemoteAddr()` | `false` |

- **Validation** uses Guava `InetAddresses.isInetAddress` — literal-only, so a crafted value can never reach a DNS resolver (`InetAddress.getByName` would have). Plus a 45-character cap, which is both the longest valid IPv6 literal and the `ip_address` column width on `device` and `audit_log`, so a client-supplied value cannot fail an insert. Anything else falls to the next rung.
- **Secrets are a comma-separated list** so rotation is possible: add the new value, switch the frontend, drop the old. Entries under 32 characters fail at startup. Empty means no caller is ever trusted — the safe degradation, and the dev default.
- **Throttling keys narrow IPv6 to its /64** (`throttleKey`), since one user is routinely handed a whole /64 and would otherwise rotate inside it for free. Storage keeps the exact address.
- **Two deliberate warnings:** a present-but-unmatched `X-Internal-Hop` (a rotation gone wrong, or probing), and a trusted hop that sent no usable `X-Device-Ip` (a frontend bug that silently collapses every user into one bucket). Neither ever logs the secret.
- **Nothing depends on the undocumented `CF-*` passthrough.** If that header never arrives, rung two is skipped and behaviour is the pre-change behaviour.

`RequestContext` exposes the result two ways, and the difference is load-bearing:

| | Returns | Used by |
| --- | --- | --- |
| `clientIp()` | the best address available, whatever its provenance | the audit trail, new-sign-in emails, a brand-new `device` row |
| `trustedClientIp()` | the address **only** when a trusted hop vouched for it, else `null` | every write that would *overwrite* a stored address |

No rung is forgeable — Cloudflare sets rung two, rung three is the socket — so the distinction is about **accuracy**, not spoofing. Untrusted BFF traffic resolves to the frontend's own egress address, so overwriting a real user address with it would put infrastructure's location on the devices screen. Hence: write anything on create (nothing better exists, and the new-sign-in email reads it), overwrite only what a trusted hop vouches for. A future native client calling the API directly is untrusted yet does send the user's own address, so that condition has to widen when one ships.

### Auth endpoints

| Endpoint | Status | Response |
|---|---|---|
| `POST /api/auth/register` | 201 | Neutral message echoing only the email, no tokens/uid/name (identical whether or not the email exists — enumeration-safe) |
| `POST /api/auth/login` | 200 | Access JWT + refresh token + user info (rejects unverified users) |
| `POST /api/auth/verify-email` | 200 | Access JWT + refresh token + user info (on valid code) |
| `POST /api/auth/resend-verification` | 200 | Generic success message (enumeration-safe, always 200) |
| `POST /api/auth/forgot-password` | 200 | Neutral message — emails a reset link if the account exists (enumeration-safe, always 200) |
| `POST /api/auth/reset-password` | 200 | Sets the new password from a valid token; revokes all sessions (then sign in again) |
| `POST /api/auth/refresh` | 200 | New access JWT + new refresh token (rotates old refresh token; keeps the same device) |
| `POST /api/auth/logout` | 200 | "Logged out successfully" (revokes the current device's refresh tokens — per-device) |
| `POST /api/auth/logout-all` | 200 | "Logged out of all sessions" (revokes every refresh token for the user the token identifies) |
| `POST /api/auth/google` | 200 | Access JWT + refresh token + user info (verifies a Google ID token; provisions or links the account) |
| `POST /api/auth/2fa/verify` | 200 | Completes a challenged sign-in: challenge token + authenticator or recovery code → access JWT + refresh token (401 for a wrong code, an expired/spent challenge, or 5 failed attempts) |

### Account (settings) endpoints

Authenticated, self-scoped (`AccountController` at `/api/account`, caller from the JWT — not public, so bearer is required).

| Endpoint | Status | Response |
|---|---|---|
| `POST /api/account/change-password` | 200 | Verifies the current password, sets the new one, rotates sessions (revoke all, keep current device); returns a fresh access + refresh pair |
| `POST /api/account/set-password` | 200 | Sets a password on a Google-only account (none yet); same session rotation + new tokens |
| `GET /api/account/connected-accounts` | 200 | `{passwordSet, googleLinked}` |
| `POST /api/account/connected-accounts/google` | 200 | Links Google (verified token; refused if already linked, the Google email differs from the account email, or the Google account belongs to another user) |
| `DELETE /api/account/connected-accounts/google` | 200 | Unlinks Google (refused if no password is set — would leave no way to sign in) |
| `POST /api/account/deactivate` | 200 | Soft-deletes the account + revokes all sessions; a password account must confirm its password, a Google-only account is confirmed by the session |
| `GET /api/account/2fa` | 200 | `{enabled, pendingSetup, confirmedAt, recoveryCodesRemaining}` |
| `POST /api/account/2fa/setup` | 200 | `{secret, otpauthUri}` for the authenticator app; stores the secret unconfirmed (409 if two-factor is already on) |
| `POST /api/account/2fa/enable` | 200 | Confirms setup with a code → 10 one-time recovery codes + a fresh access/refresh pair (all other sessions revoked) |
| `POST /api/account/2fa/disable` | 200 | Removes the factor + every recovery code; returns a fresh pair (other sessions revoked) |
| `POST /api/account/2fa/recovery-codes` | 200 | 10 new recovery codes, invalidating the previous set; sessions untouched |

### Authentication flows

**Email registration**: validate input (`@Email`, `@ValidPassword` password — see *Password policy*, `@NotBlank` name/lastName) → look up the email. **If it already exists**: send the owner an "account already exists" email and return the same neutral response (no create, no 409) — so the caller can't tell the address is taken (enumeration-safe). **If new**: hash password (BCrypt) → create User with random UID → generate & send a 6-digit verification code (5-min expiry). Either way the response is `AuthResponse.registrationPending(email)` — just the email + a neutral message, no JWT/uid/name. The verification email carries a prefilled link `${APP_BASE_URL}/verify-email?email=&code=` (the link only lands on the FE page — nothing verifies on GET, so a mail scanner's prefetch can't spend the single-use code) plus the raw code as a fallback.

**Email verification**: find user by email, filter out already-verified → find matching non-expired code → mark user `is_email_verified=true` → delete all user's codes → generate JWT → return `AuthResponse`. All failure cases (unknown email, already verified, wrong/expired code) return same generic "Invalid email or code" error to prevent email enumeration.

**Resend verification**: find user by email, filter out already-verified → if found and unverified, generate & send new code. Always returns 200 with generic message regardless of email state (enumeration-safe).

**Forgot password**: find user by email → unknown or deactivated is a silent no-op; a Google-only account (no password) gets a "you use Google" email; a password account gets a single-use reset token (256-bit random, stored SHA-256-hashed via `SecureTokens`, 15-min expiry, prior tokens invalidated) emailed as `${APP_BASE_URL}/reset-password?token=`. Always returns the same neutral 200 (enumeration-safe). Rate-limited per IP (email-bomb protection).

**Reset password**: look up the token by its hash (must be unused and unexpired) → set the new password (`@ValidPassword`) → mark the email verified (mailbox control is proven) → mark the token used → **revoke all refresh tokens** (kills every session) → email a "password changed" notice → audit `PASSWORD_RESET`. No JWT is issued; the user signs in again. Invalid/used/expired tokens return a generic error.

**Email login**: find user by email (401 generic if not found) → **verify the password first** (401 generic "Invalid email or password" if wrong, or if the account is OAuth-only with no hash) → *only after a correct password* surface account state: `is_active` (401 "deactivated", code `ACCOUNT_DEACTIVATED`) then `is_email_verified` (401 `EmailNotVerifiedException`, whose envelope carries `code: "EMAIL_NOT_VERIFIED"` so clients branch on the code, not the message) → generate access JWT + refresh token → return `AuthResponse`. Checking the password before revealing any state keeps a wrong guess indistinguishable from an unknown email (anti-enumeration); the verify-prompt still reaches a legitimate user who proved ownership. An account that has **both** a password and a linked `google_sub` can sign in with either method (the old "different sign-in option" block was removed).

**Token refresh**: hash incoming refresh token (SHA-256) → look up non-revoked match in DB → check expiry (revoke + 401 if expired) → check `is_active` (revoke **all** user tokens + 401 if deactivated) → revoke old token → generate new access JWT (carrying the user's current role) + new refresh token → return `AuthResponse`. Unknown/revoked tokens return 401. This is the point where a deactivation or role change made mid-session takes effect.

**Logout (per-device)**: hash incoming refresh token → look up non-revoked match → revoke every refresh token on **that token's device** (a legacy token with no device revokes only itself). Invalid token returns 401. **Logout everywhere** (`/api/auth/logout-all`) is the same lookup but revokes every token for the user — the explicit "sign out all sessions" action from settings.

**Sessions & devices**: a sign-in is modelled as a `device` row plus its live refresh token. On login/verify/Google, `DeviceService.resolveLoginDevice` finds-or-creates the user's device by the `X-Device-Id` header (parsing the user-agent into a "Chrome on Windows"-style name via `UserAgentParser`, recording IP); the refresh token is bound to it, and rotation reissues on the same device so identity survives. Rotation also calls `DeviceService.recordActivity`, the single writer of device activity: it advances `last_used` and refreshes `ip_address` when the hop is trusted, so an address cannot sit on a network the user has left while the timestamp keeps moving. Both writes live in one method precisely so they cannot drift apart again. The address is therefore at most one access-token lifetime of *activity* stale — an idle session does not rotate, and so reports neither new time nor new place. A first sign-in on an unseen `(user, device)` emails a new-sign-in alert (keyed on device/UA, not IP, to avoid mobile-IP noise). A login carrying no device id simply has no device (degraded, no alert) — the Next.js BFF forwards `X-Device-Id` (a stable per-browser id it keeps), `X-Device-User-Agent` (the real browser UA; the transport `User-Agent` on the server→backend hop is the BFF's), `X-Device-Ip` (the browser's address, read from `CF-Connecting-IP`) and `X-Internal-Hop` (the shared secret that makes the IP trustworthy — see **Client IP resolution**) on login/google/verify/refresh. The caller manages their devices via the self-scoped `/api/devices` endpoints (list with session state, rename, per-device logout, remove).

**Two-factor at sign-in**: when the account has a confirmed factor, the password and Google paths stop short of issuing tokens — `TwoFactorChallengeService.challengeIfEnabled` deletes any earlier pending challenge, stores a new one (opaque 256-bit token, SHA-256 hashed, 5-minute TTL) and returns `{twoFactorRequired: true, challengeToken}` with no tokens, no profile fields and **no `LOGIN` audit entry** (nobody is signed in yet). `POST /api/auth/2fa/verify` then rejects an unknown, expired, spent or out-of-attempts challenge with one generic message, re-checks `is_active` (the account may have been deactivated since the password step), re-reads the factor (two-factor turned off meanwhile invalidates the challenge), and spends the code through `TwoFactorService.consumeCode`. A wrong code increments `attempts` (cap 5), audits `LOGIN_FAILED` and 401s; success consumes the challenge, issues the access + refresh pair (which creates the device row and any new-sign-in alert), audits `LOGIN`, and — if a recovery code was used — emails the owner. The challenge is deliberately not a JWT: `JwtAuthenticationFilter` accepts any validly-signed token and defaults a missing role claim to `USER`, so a purpose-scoped JWT would authenticate as a full session. `verify` is not `@Transactional` (so the attempt counter and `LOGIN_FAILED` survive the throw), which is why the challenge lookup fetches its user via `@EntityGraph`. Token refresh is **not** gated — it is already past the factor.

**Google sign-in**: the client runs Google Sign-In itself and posts the resulting **ID token** plus the raw per-login nonce secret `N` to `POST /api/auth/google` → `GoogleIdTokenVerifier` validates signature (JWKS), issuer, expiry, and audience (`google.client-id`) → verify the OIDC **nonce**: the token's `nonce` claim must equal `lowercaseHex(SHA-256(N))`, binding the token to this login (anti-replay, OIDC §3.2.2.1/§16.11); mismatch or missing → 401 generic → require `email_verified` (401 otherwise) → resolve the account: (1) match `google_sub` → log in; (2) match email → **link** Google to the existing password account and mark it verified; (3) no match → **provision** a new already-verified, password-less account → generate access JWT + refresh token → return `AuthResponse`. Deactivated accounts are rejected (401) with a `LOGIN_FAILED` audit entry; success writes `LOGIN` (returning/linked) or `REGISTER` (new). The backend needs only the client id (the token audience); no client secret is used for verification.

**Account settings (password, connected accounts, deactivation)**: self-service security actions on `/api/account`, all resolving the caller from the JWT. **Change password**: verify the current password (409 if the account has none — set instead; 401 if wrong) → set new (`@ValidPassword`) → **revoke every session, then reissue the current device** a fresh access + refresh pair (so this device stays signed in while others must re-authenticate) → "password changed" email → audit `PASSWORD_CHANGED`. **Set password** (Google-only account, no hash): same rotation + tokens → "password added" email → audit `PASSWORD_SET`. **Connected accounts**: `GET` returns `{passwordSet, googleLinked}`; **link** verifies a Google ID token (`GoogleOAuthService.verifyLinkToken`, mutating nothing) then refuses if a Google account is already linked, the Google-verified email differs from the account email, or the Google subject already belongs to another user — else sets `google_sub`, emails, audits `GOOGLE_LINKED`; **unlink** clears `google_sub` but is refused when no password is set (lockout guard), emails, audits `GOOGLE_UNLINKED`. **Deactivate**: a password account re-authenticates with its password (401 if missing/wrong), a Google-only account is confirmed by the session alone → set `is_active=false` → revoke all sessions → "account deactivated" email → audit `ACCOUNT_DELETED`. Self-service deactivation lives here (not the SUPER_ADMIN-only `DELETE /api/users/{uid}`).

**Two-factor (enrollment)**: authenticator-app TOTP on `/api/account/2fa`, caller from the JWT. **Setup**: re-authenticate (password required when the account has one; a Google-only account is confirmed by the session alone, as with deactivation) → refuse if two-factor is already on (409) → `TwoFactorProvider.enroll` → store the secret **encrypted and unconfirmed** (`is_enabled=false`) and return `{secret, otpauthUri}`. Re-running setup replaces an abandoned attempt, so nothing stale survives. **Enable**: verify a code against the pending secret (401 if wrong; no recovery codes exist until this step succeeds) → `is_enabled=true`, `confirmed_at`, and `last_used_step` set to the confirming code's step so it can't be replayed later → issue 10 recovery codes (stored SHA-256, returned once) → revoke all sessions and reissue this device → "two-factor is on" email → audit `TWO_FACTOR_ENABLED`. **Disable**: password + a current authenticator *or* recovery code → delete the factor row and all codes (credential tables have no `is_active`, and keeping a dead secret at rest would be worse) → rotate sessions → email → audit `TWO_FACTOR_DISABLED`. **Regenerate recovery codes**: same confirmation, replaces the whole set, leaves sessions alone → email → audit `RECOVERY_CODES_REGENERATED`. All code checking goes through `TwoFactorService.consumeCode`, which spends what it accepts: an authenticator code at or below `last_used_step` is rejected as a replay, and a recovery code is marked used and audited `TWO_FACTOR_RECOVERY_USED`. Recovery codes are 10 x 8 characters from a no-look-alike alphabet, shown as `abcd-efgh` but hashed separator-free and lowercase, so any formatting the user types matches. A wrong code answers **401**, like a wrong current password on `/change-password` — a client must not read it as an expired session.

**Staging/prod checklist** (the common  traps — all config, not code):
- `GOOGLE_CLIENT_ID` on the backend **exactly matches** the Client ID the frontend's button uses (a mismatch fails on token audience).
- The frontend's deployed origin is listed under **Authorized JavaScript origins** on that OAuth client.
- Consent screen is **published**, or the tester's account is whitelisted.
- Verify a token's `aud`/`email_verified` out-of-band with `https://oauth2.googleapis.com/tokeninfo?id_token=<token>` when debugging.

### Still to implement

- **Mobile Google login**: when native apps are added, each gets its own OAuth client id — widen the audience list in `GoogleOAuthConfig`. Use the system browser + PKCE (AppAuth), not an embedded webview (OWASP/IETF standard for native OAuth).
- Web-side httpOnly cookies live in the **Next.js BFF**, not Spring (see *Client session architecture* above) — no Spring change needed; the JSON-body token response is what the BFF/mobile consume.

### Authorization model

Two independent role systems, enforced declaratively with Spring method security (`@EnableMethodSecurity`):

**Platform role** (`User.role`: `SUPER_ADMIN` / `ADMIN` / `USER`) — global, carried in the JWT, checked with `@PreAuthorize("hasRole(...)")`. A `RoleHierarchy` bean makes `SUPER_ADMIN → ADMIN → USER` (a higher role satisfies any lower `hasRole` check).
- **SUPER_ADMIN**: full platform access, including changing platform roles (`PUT /api/users/{uid}/role`).
- **ADMIN**: support/staff — read across the platform (e.g. list all users); no destructive or role-granting actions.
- **USER**: own data + the groups they belong to.
- Self-access (a user acting on their own account) is expressed with the `@accountSecurity.isSelf(#uid)` bean, e.g. `@PreAuthorize("hasRole('SUPER_ADMIN') or @accountSecurity.isSelf(#uid)")` on update/delete.

**Group role** (`GroupMember.role`: `GROUP_ADMIN` / `GROUP_MEMBER`) — relationship-based, resolved per-request against the target group by the `@groupSecurity` bean (`isMember`/`isAdmin` by group UID, `isMemberOfGroup`/`isAdminOfGroup` by id, `canViewMember`/`canManageMember`/`isSelfMember` by membership id).
- **GROUP_ADMIN**: edit group, invite/remove members, change roles, rotate the invite code, manage categories and tags.
- **GROUP_MEMBER**: create/edit/delete own tasks, complete tasks, view group content, leave the group.
- The **creator of a group becomes its first `GROUP_ADMIN`** automatically (`GroupService.createGroup`).
- **Last-admin protection**: a group must keep ≥1 active `GROUP_ADMIN` — demoting or removing the last one is rejected with 409 (`BusinessRuleException`).
- **The invite code is admin-only.** Inviting is a `GROUP_ADMIN` power, so the code is returned only to an admin of that group: `GroupResponse.from(group)` omits it and `GroupResponse.withInviteCode(group)` includes it, making the exposure an explicit decision at each call site. A plain member gets `inviteCode: null` from `GET /api/groups/{uid}` and `GET /api/groups/mine`; the platform-admin listing `GET /api/groups` omits it too, since support staff have no reason to hold join codes.
- **Joining is self-service and audited.** `POST /api/groups/join` needs only authentication — holding a valid code *is* the authorization. It writes a `MEMBER_ADDED` audit entry, refuses a second join with 409, and returns the same 404 for an unknown code as for a soft-deleted group's code, so a redeemer cannot probe for codes that once existed. A user who previously left has their existing membership **reactivated** (the unique `(user_id, group_id)` constraint allows only one row) and comes back as a `GROUP_MEMBER` — rejoining never restores an admin role held before leaving, since a public code must not grant `GROUP_ADMIN`.
- **Rotation is the only revocation.** `POST /api/groups/{uid}/invite-code` issues a new code and invalidates the old one. It is immediate and total: outstanding legitimate invites die alongside a leaked code, because one code serves every invite. Per-invite expiring tokens would fix that properly and remain deferred.
- *Enforced on the group and group-member endpoints; task/category/tag endpoints get their group-scoped checks as those features are fleshed out.*

Denied requests (authenticated but unauthorized) return **403** via `GlobalExceptionHandler` (`AccessDeniedException`); unauthenticated requests return **401** via `JwtAuthenticationEntryPoint`.

## Configuration strategy

### Profile hierarchy

```
application.properties          ← base (database, JWT, mail, OAuth — all via env vars)
  └── application-dev.properties   ← verbose logging, seed data, Flyway clean enabled, 1-year JWT
  └── application-prod.properties  ← minimal logging, Flyway clean disabled
```

Active profile set via `SPRING_PROFILES_ACTIVE` env var (defaults to `dev`).

### Environment variables

| Variable | Used in | Purpose |
|---|---|---|
| `DATABASE_URL` | base (all profiles) | Full JDBC URL |
| `DB_USERNAME` | base (all profiles) | Database user |
| `DB_PASSWORD` | base (all profiles) | Database password |
| `JWT_SECRET` | base (all profiles) | HMAC signing key (min 256 bits) |
| `CORS_ALLOWED_ORIGINS` | required outside dev — unset fails startup (dev profile defaults to `http://localhost:3000`) | Comma-separated browser origins allowed to call `/api/**` |
| `APP_BASE_URL` | required outside dev — unset fails startup (dev profile defaults to `http://localhost:3000`) | Frontend origin for links in emails (e.g. the verify-email link) |
| `GOOGLE_CLIENT_ID` | base (all profiles) | Google OAuth client ID (token audience; the only Google value the backend uses) |
| `MAIL_HOST` | base (all profiles) | SMTP host (Mailtrap sandbox in dev) |
| `MAIL_PORT` | base (all profiles) | SMTP port |
| `MAIL_USERNAME` | base (all profiles) | SMTP username |
| `MAIL_PASSWORD` | base (all profiles) | SMTP password |
| `SPRING_PROFILES_ACTIVE` | base | Profile selector (defaults to `dev`) |

All secrets are loaded from `.env` at the project root via `spring.config.import=optional:file:.env[.properties]` in `application.properties`. No external library needed — Spring Boot 4.1 reads `.env` natively as a properties source. The `.env` file is gitignored — create it locally from `.env.example`. On Render (prod), these are set as environment variables directly.

### Key base settings

- `server.port=8080`
- `spring.jpa.hibernate.ddl-auto=validate` (Flyway owns schema)
- `spring.flyway.baseline-on-migrate=true` (safe for first run)
- `spring.servlet.multipart.max-file-size=5MB` / `max-request-size=8MB` (task photos)
- `jwt.expiration=900000` (15 min access token)
- `jwt.refresh-expiration=1209600000` (14 day refresh token)
- `spring.flyway.clean-disabled=false` in dev, `true` in prod

## Infrastructure

### Local development

```bash
docker-compose up -d          # Start PostgreSQL 16 on port 5432 (tasklean_dev database)
mvnw.cmd spring-boot:run      # Start API on port 8080 (dev profile active by default)

docker-compose stop           # Stop Postgres (preserves data)
docker-compose down -v        # Destroy Postgres container + volume (full reset)
```

Docker Compose only runs Postgres — the app runs on the host. The Compose file reads `DB_USERNAME` and `DB_PASSWORD` from `.env` with defaults. On fresh start after `down -v`, Flyway re-runs all migrations + seed data automatically.

### Production

- **Backend hosting**: Render (staging + production as separate services)
- **Database**: Supabase-hosted PostgreSQL (transaction pooler on port 6543, `?prepareThreshold=0`)
- **Frontend hosting**: Vercel (for the Next.js app, when built)
- **Image storage**: AWS S3 or Cloudflare (future — for task photos and completion photos)

The Dockerfile builds a multi-stage image (`eclipse-temurin:21-jdk-alpine` → `eclipse-temurin:21-jre-alpine`): deps resolve in their own layer (cached until `pom.xml` changes, incl. by CI's gha layer cache), then the fat jar is split into Spring Boot layers (`-Djarmode=tools … extract --layers`) so a code-only change rebuilds just the small application layer. Runs as a non-root user. The entrypoint sets `-XX:MaxRAMPercentage=75.0`; on Render the explicit `JAVA_TOOL_OPTIONS=-Xmx384m` takes precedence (fits the 512MB free tier), so the percentage only applies where no `-Xmx` is set (local/Compose).

**Health check**: Spring Boot Actuator exposes a public `/actuator/health` endpoint (whitelisted in `SecurityConfig`). Only the `health` endpoint is exposed and details are hidden (`show-details=never`), so it returns just `{"status":"UP"}` or `503` with `{"status":"DOWN"}`. The aggregate status includes a DB-connectivity check, so it doubles as a readiness probe — point Render's health check and uptime monitors at it.

### Observability (logging)

Logs are **operational telemetry** — separate from the DB-persisted **audit trail** (domain history). Don't conflate them; logs are ephemeral and for ops/debugging.

**Design** (see the Logging convention in `CLAUDE.md` for the level policy and rules):
- **Emit → ship → store.** The app only emits to **stdout** (12-factor); a drain on the infra side ships it to a store/UI. No file appenders.
- **Structured JSON in prod, human-readable in dev.** Prod sets `logging.structured.format.console=ecs` (Spring Boot 4 native structured logging — Elastic Common Schema, no extra dependency). ECS JSON is ingest-ready for Kibana and parseable by Grafana/Loki. Dev keeps the default console pattern.
- **Correlation via MDC.** `RequestLoggingFilter` (highest precedence) assigns each request a `requestId` (adopting an inbound `X-Request-Id` if present, and echoing it back in the response header) and puts `method`/`path` into MDC; `JwtAuthenticationFilter` adds `userId` once authenticated. In ECS output these become top-level fields, so logs are queryable by request or user. MDC is cleared per request to avoid leaking across pooled threads.
- **Automatic baseline.** Every request gets a summary line (method, path, status, durationMs — `/actuator/**` excluded to avoid uptime-monitor noise) and every unhandled exception is logged once (stack trace) by the `GlobalExceptionHandler` catch-all. New endpoints/services inherit this for free.

**Integrating a monitoring stack later** (not built yet): because the app already emits ECS JSON to stdout, wiring it up is an infra-side config change with zero code change — e.g. point Render's Log Stream (or a sidecar shipper like Promtail/Fluent Bit/Vector) at Grafana-Loki or Elasticsearch+Kibana. For metrics (as opposed to logs), add `micrometer-registry-prometheus` to expose `/actuator/prometheus` for Grafana to scrape.

### CI/CD

Two GitHub Actions workflows in `.github/workflows/`:

**Staging** (`ci-staging.yml`) — triggers on push to `main` and PRs to `main`:
```
compile → test → sonarcloud → build & push Docker image → deploy to Render
```
Build/push/deploy only run on push (not PRs). Uses GitHub environment `Staging` for deploy secrets.

**Production** (`ci-production.yml`) — triggers on push to `production`, PRs to `production`, and manual `workflow_dispatch` (dispatch runs compile/test/sonar only; version/build/deploy/release stay push-gated):
```
compile → test → sonarcloud → auto-version tag → build & push Docker image → deploy to Render → create GitHub Release
```
Version/build/deploy/release only run on push (not PRs). Uses GitHub environment `Production` (requires manual approval).

**Auto-versioning**: the `version` job uses `mathieudutour/github-tag-action` to bump the version tag automatically based on commit message prefixes:
- `fix:` → patch bump (e.g., `v1.0.0` → `v1.0.1`)
- `feat:` → minor bump (e.g., `v1.0.0` → `v1.1.0`)
- `BREAKING CHANGE:` → major bump (e.g., `v1.0.0` → `v2.0.0`)
- No prefix → defaults to patch

Docker image tags: **staging** pushes a moving `staging` tag plus the commit sha (`sha-abc123`); **production** pushes the version (`v1.0.1`), commit sha, and `latest`. Render points each service at its moving tag (`staging` / `latest`) so every deploy-hook fire pulls the newest image — the sha tag is the immutable rollback/audit handle. Pinning a Render service to a `sha-…` tag silently freezes it.

**Release flow**: merge `main` → `production` via PR → pipeline runs → auto-tags, builds, deploys, and creates a GitHub Release with auto-generated notes from merged PRs.

**GitHub secrets**:
- Repo-level (shared): `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `RENDER_API_KEY`, `SONAR_TOKEN`
- Staging environment: `RENDER_DEPLOY_HOOK`, `RENDER_SERVICE_ID`
- Production environment: `RENDER_DEPLOY_HOOK`, `RENDER_SERVICE_ID`

**Branch protection**: `main` and `production` branches require PRs, status checks (compile, test, sonar), and block force pushes.

## Coding patterns

### Comment conventions

Three kinds of comment, each with a job (full rules in [CLAUDE.md](CLAUDE.md#conventions)):
- **File header** — a Javadoc block atop every logic-bearing class (service, controller, filter, config, exception, component) summarising its responsibility.
- **Method-level** — Javadoc on `public`/`protected` methods with `@param` for every parameter, `@return` for non-`void` returns, and `@throws` for exceptions the caller should anticipate.
- **Inline** — `//` on its own line above complex or unconventional logic, explaining the *why* (never restating the code).

Entities and DTOs are exempt as data carriers. Repository interfaces get a file header only — their derived-query method names are self-documenting; only custom `@Query` methods get method Javadoc.

### Package layout per domain entity

```
domain/<entity>/
  Entity.java            ← JPA entity, Lombok annotations
  EntityRepository.java  ← Spring Data JPA interface
  EntityService.java     ← Business logic, @Transactional on writes
  EntityController.java  ← REST controller, thin delegation to service
  dto/
    EntityRequest.java   ← Input DTO with Jakarta validation
    EntityResponse.java  ← Output DTO with static from(Entity) factory
```

### Entity pattern

```java
@Entity
@Table(name = "entity_name")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntityName extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entity_name")
    private Long idEntityName;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private ParentEntity parent;
}
```

Key rules:
- Always `FetchType.LAZY` on `@ManyToOne`
- `@Builder.Default` on any field with an initializer
- Entities that don't need `date_updated` (junction tables, immutable records) skip `BaseEntity` and use `@CreationTimestamp` directly

### Response DTO pattern

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntityResponse {
    // flat fields, no nested objects — FK references are Long IDs
    public static EntityResponse from(Entity entity) {
        return EntityResponse.builder()
                .id(entity.getIdEntity())
                .parentId(entity.getParent().getIdParent())
                .nullableRef(entity.getNullable() != null ? entity.getNullable().getId() : null)
                .build();
    }
}
```

Response DTOs flatten relationships to IDs. Nullable FKs use ternary null-checks in the `from()` method.

### Service pattern

- `@Service @RequiredArgsConstructor` — constructor injection via Lombok
- Read methods: no annotation (implicit read-only transaction via Spring defaults)
- Write methods: `@Transactional`
- Fetch entity or throw `ResourceNotFoundException`
- Duplicate checks throw `DuplicateResourceException`
- UID generation: `UUID.randomUUID().toString()` at creation time

### Controller pattern

- `@RestController @RequestMapping("/api/<plural-resource>") @RequiredArgsConstructor`
- Returns `ResponseEntity<ApiResponse<T>>`
- POST returns `HttpStatus.CREATED`, everything else returns `200`
- `@Valid @RequestBody` on input DTOs
- Thin delegation — no business logic in controllers

### Unit testing

Tests live in `src/test/java`, mirroring the main source structure. No Spring context is loaded for unit tests — they're fast and isolated.

**Approach:**
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` for service tests
- Direct instantiation for classes with no Spring dependencies (e.g., `JwtService`)
- `MockHttpServletRequest` / `MockHttpServletResponse` for filter tests
- AssertJ for assertions (`assertThat`), Mockito for mocking

**Naming convention:** `methodName_scenario_expectedResult`

**What gets tested:**
- Business rules and edge cases in services (auth flows, validation logic)
- Security boundaries (filter behavior with valid/invalid/missing tokens)
- Pure logic (token generation, validation, claim extraction)
- Error paths (what exceptions are thrown and when)

**What does NOT get unit tested:**
- Getters/setters, builders, or Lombok-generated code
- Framework wiring (Spring context loading, bean registration)
- Repository queries (these would be integration tests with a real DB)
- Controller routing/serialization beyond authorization (the `@WebMvcTest` slices assert `@PreAuthorize` wiring and route precedence, not payload shaping)

**Current test coverage:**

| Class | Tests | Covers |
|-------|-------|--------|
| `JwtServiceTest` | 11 | Token generation, validation (valid/tampered/expired/wrong secret), claim extraction incl. role (and USER default) |
| `AuthServiceTest` | 14 | Register (new success, existing email → neutral + owner email, password hashing, UID generation); Login (success, linked account Option B, wrong password, wrong password hides unverified, missing email, deactivated, OAuth-only → generic, unverified → `EmailNotVerifiedException`, two-factor enabled → challenge instead of tokens); error codes (deactivated carries `ACCOUNT_DEACTIVATED`, a wrong password carries none — enumeration guard) |
| `EmailServiceTest` | 11 | Verification link (param names, URL-encoded email, trailing-slash safe); registration-attempt notice; password-reset link carries the token; Google-only reset notice (no link); password-changed notice; password-added notice; Google linked/unlinked notices; account-deactivated notice; new-sign-in notice (device + IP, IP omitted when blank) |
| `GoogleOAuthServiceTest` | 12 | New-user provisioning; returning Google user; link password account; invalid token; verifier failure; unverified Google email; nonce mismatch; nonce missing in token; deactivated account; `verifyLinkToken` (valid returns subject+email without touching users, unverified email throws, nonce mismatch throws) |
| `AccountServiceTest` | 17 | changePassword (success rotates + returns new tokens, wrong current pwd throws, Google-only throws); setPassword (success, already-has-pwd throws); getConnections; linkGoogle (success, already-linked, email mismatch, sub owned by another); unlinkGoogle (success, not-linked, no-password lockout); deactivate (password account ok/wrong/missing, Google-only no password needed) |
| `AccountControllerSecurityTest` | 4 | `@WebMvcTest` slice — connected-accounts/change-password/deactivate require authentication (anonymous denied), authenticated caller OK |
| `JwtAuthenticationFilterTest` | 6 | Valid token sets an `AuthPrincipal` + `ROLE_` authority from claims (no DB); no/non-Bearer/invalid token passes through anonymously; auth endpoints skipped |
| `VerificationServiceTest` | 8 | createAndSend (code generation + email); verifyEmail (valid code, invalid code, already verified, unknown email — all enumeration-safe); resendVerification (unverified sends, already verified silent, unknown email silent) |
| `RefreshTokenServiceTest` | 14 | createRefreshToken (no-device hashed storage, new device binds + alerts owner, known device no alert); refresh (valid rotation, keeps device + delegates the touch to `recordActivity`, deactivated revoke-all+throw, expired revoke+throw, revoked throw, unknown throw); logout (per-device revoke, legacy no-device revokes one, invalid throws); logoutAll (revokes all + audits, invalid throws) |
| `AuditLogServiceTest` | 4 | `record` write path — group present (actor/member/IP stamped), null group (group+member unset), null group skips membership lookup; `recordAccountEvent` stamps the subject as actor |
| `PasswordResetServiceTest` | 7 | requestReset (password account issues token + email, unknown/deactivated no-op, Google-only sends info email); resetPassword (valid sets password + revokes sessions + marks used, unknown/used token throws, expired throws) |
| `SecretCipherTest` | 9 | Round-trip; the same secret encrypts to different ciphertexts (fresh IV); output never contains the plaintext; tampered ciphertext, a value encrypted under another key, a malformed value and one shorter than the IV all rejected; a key of the wrong length or not Base64 fails construction rather than first use |
| `LocalTotpProviderTest` | 23 | All six RFC 6238 Appendix B vectors through the adapter (proves step size, digit count, SHA-1 and start time are wired as authenticator apps expect); ±1-step skew accepted and ±2 rejected; the matched step reported is the code's own step, so replay stays detectable; whitespace-separated codes accepted; malformed codes, nulls, a non-Base32 credential and another secret's code all rejected without throwing; enroll produces a 32-char Base32 secret + labelled `otpauth://` URI, differs every time, and round-trips through verify |
| `TwoFactorServiceTest` | 27 | getStatus (none / pending / enabled with remaining codes); startSetup (stores an encrypted unconfirmed secret, Google-only needs no password, wrong/missing password rejected, already-enabled refused, abandoned setup replaced, inactive account refused); enable (turns on + 10 codes + new tokens, codes stored hashed only, wrong code leaves it pending, no pending setup, already on); disable (authenticator code, recovery code, wrong password, invalid code, not enabled); regenerateRecoveryCodes (replaces the set, keeps sessions, not enabled); consumeCode (fresh step recorded, replayed step rejected, spent recovery code, any code formatting matches, blank code never reaches the provider) |
| `TwoFactorControllerSecurityTest` | 7 | `@WebMvcTest` slice — all five `/api/account/2fa` endpoints deny anonymous callers (a body is sent so the request reaches authorization), authenticated caller resolved from the token, missing code rejected by validation |
| `TwoFactorChallengeServiceTest` | 13 | challengeIfEnabled (no factor / pending-only → no challenge, enabled → hashed token + expiry + zero attempts, previous challenge dropped first, nothing about the account disclosed); verify (authenticator and recovery code complete the sign-in, recovery also emails the owner, wrong code counts the attempt and audits `LOGIN_FAILED`, attempts exhausted consumes the challenge without checking the code, expired / unknown / factor-removed / deactivated all refused with no tokens); scheduled purge |
| `AuditContextTest` | 11 | `currentUserId` from `AuthPrincipal` (authenticated / anonymous / non-principal); `currentUser` reference (authenticated / anonymous); `currentActor` (member / non-member / no auth / null group); IP delegated to `RequestContext` (resolved or none) |
| `ClientIpResolverTest` | 17 | Trusted hop returns the forwarded IP (incl. IPv6) and a rotated secret still counts; **an untrusted caller's `X-Device-Ip` is ignored** — absent, wrong and unconfigured secret alike; a hostname, a comma-joined list and an over-45-character value all fall through; Cloudflare rung and socket fallback; a guessable secret fails at construction; `throttleKey` narrows IPv6 to /64, leaves IPv4 and IPv4-mapped literals alone |
| `RequestContextTest` | 10 | `clientIp` returns the forwarded user IP on a trusted hop, ignores it otherwise, and is null with no bound request; `trustedClientIp` returns it only on a trusted hop (null for a resolvable-but-untrusted caller, null unbound); `userAgent` prefers `X-Device-User-Agent` over the transport header; `deviceId` present/absent |
| `AccountSecurityTest` | 4 | `isSelf` — matching uid / different uid / anonymous / non-principal |
| `GroupSecurityTest` | 12 | `isAdminOfGroup`/`isMemberOfGroup` (active admin / plain member / inactive / not-a-member / anonymous); `isAdmin`/`isMember` by uid (incl. group-not-found); `canManageMember`/`canViewMember`/`isSelfMember` by membership id |
| `GroupServiceTest` | 22 | createGroup (creator → GROUP_ADMIN); get/getAll/update/delete + not-found; getGroupByUid invite-code visibility (admin vs plain member); getMyGroups (role mapping, soft-deleted group skipped, invite-code visibility); joinByInviteCode (new member, case-insensitive code, unknown code, soft-deleted group, already a member, rejoin reactivates the same row without restoring admin); rotateInviteCode (replaces code + audits, not-found) |
| `GroupMemberServiceTest` | 7 | addMember (success / duplicate); role change and removal with last-admin protection (blocked when last, allowed with another admin); promote member |
| `UserServiceTest` | 7 | updateUserRole (+ audit / unknown); get/getAll/update/delete + not-found |
| `UserSettingsServiceTest` | 6 | getMySettings (existing row, no row returns defaults without writing); updateMySettings (creates the row on first write, updates an existing row in place without re-resolving the user, a lost unique-constraint race retries in a fresh transaction and updates the winner's row, unknown user → not found) |
| `UserSettingsControllerSecurityTest` | 6 | `@WebMvcTest` slice — GET/PUT require authentication (anonymous denied); the acted-on user comes only from the token, and id fields smuggled into the body (`userId`/`idUserSettings`/`user`) are ignored |
| `NotificationSettingsServiceTest` | 11 | getMySettings (no rows returns every type at the defaults without writing, saved rows merged over the defaults, types always in declaration order); updateMySetting (creates the row for the caller + type on first write, updates an existing row in place without re-resolving the user, a lost unique-constraint race retries in a fresh transaction and updates the winner's row, unknown user → not found); updateMySettings batch (applies every entry and returns the whole matrix, mixes create + update in a single transaction, a lost race retries the whole batch, unknown user → not found) |
| `NotificationSettingsControllerSecurityTest` | 13 | `@WebMvcTest` slice — GET/PUT/PUT-batch require authentication (anonymous denied); the caller comes only from the token; an unknown `{type}`, a missing channel field, an empty batch, a batch entry missing a channel and duplicate types in a batch are all 400; `/batch` routes to the batch handler rather than being read as a type; id fields smuggled into the body are ignored |
| `CategoryServiceTest` | 10 | get/getByGroup/create/update/delete + not-found + duplicate |
| `TagServiceTest` | 10 | get/getByGroup/create/update/delete + not-found + duplicate |
| `TaskServiceTest` | 13 | get/getByGroup/create/update/delete incl. assignee & category branches + not-found |
| `TaskCompletionServiceTest` | 5 | list by task/member; create (+ task/member not-found) |
| `DeviceServiceTest` | 14 | registerDevice (success / duplicate token / user missing); resolveLoginDevice (no device id, unseen creates + flags new, known updates but keeps user-renamed name, known device on an untrusted hop keeps its stored IP rather than taking infrastructure's); recordActivity (trusted hop updates IP + last-used and saves, untrusted keeps the IP but still advances last-used); listMyDevices (active-session + current-device flags); logout/delete/rename my device (owner-scoped; non-owned → not found) |
| `UserAgentParserTest` | 5 | Parses Chrome/Windows, Safari/iPhone (mobile, iOS before macOS), Edge-before-Chrome, Firefox/Linux, null → "Unknown device" |
| `DeviceControllerSecurityTest` | 4 | `@WebMvcTest` slice — list/logout require authentication (anonymous denied), authenticated caller OK |
| `UserControllerSecurityTest` | 10 | `@WebMvcTest` slice — list=ADMIN (USER 403 / ADMIN / SUPER_ADMIN via hierarchy), get self vs other, `/me` for any authenticated caller (also guarding route precedence over `/{uid}`), role change SUPER_ADMIN-only (ADMIN 403), delete SUPER_ADMIN-only (self 403), 403 envelope |
| `GroupControllerSecurityTest` | 10 | `@WebMvcTest` slice — getGroup member/platform-admin vs non-member 403; updateGroup group-admin vs plain-member 403 (via `@groupSecurity`); `/mine` and `/join` open to any authenticated caller (also guarding `/mine` route precedence over `/{uid}`); invite-code rotation group-admin only (plain member and platform ADMIN 403) |
| `ApiErrorControllerTest` | 4 | `/error` renders JSON per status (404/405/403/500) |
| `GlobalExceptionHandlerTest` | 13 | Every handler maps to the right status + message (404/409/401/403/400/405/500 + validation join); a coded `AuthFailureException` returns 401 with that code, unverified email with `EMAIL_NOT_VERIFIED` |
| `CaffeineBucketRegistryTest` | 3 | Same key → same bucket; distinct keys; bucket enforces tier capacity |
| `RateLimitFilterTest` | 13 | Within-limit passes; over-limit → 429 + `Retry-After`; per-user and per-IP trips; the additive join tier trips while the api tiers still have room, and does not apply to other endpoints; `shouldNotFilter` (disabled/actuator/error/normal); keying — a forged `X-Device-Ip` without the hop secret does not escape the bucket, two users on a trusted hop get separate buckets, and one user rotating inside an IPv6 /64 shares one |
| `PasswordPolicyValidatorTest` | 19 | Length bounds, each missing-class message/grammar, space-as-special, and the guessable variants (common/leet/tail/repeated/sequential); plus the `isValid` message wiring |
| `PasswordPolicyContractTest` | 11 | Runs the validator against the shared FE/BE contract vectors (`password-policy-vectors.json`) |

`AuthServiceTest` also asserts the audit security boundary: `LOGIN_FAILED` on every login rejection where the user is known, `LOGIN`/`REGISTER` on success, and no entry for an unknown email.

Total: **465 tests** across 43 test classes. Controller authorization is covered by `@WebMvcTest` security slices (no DB; caller injected per-request, method security behind a permissive filter chain — see `MethodSecuritySliceConfig`).

### What's NOT in the codebase yet

- No pagination (all list endpoints return full results)
- No sorting parameters
- No filtering beyond `?groupId=` / `?userId=` / `?taskId=`
- No `@OneToMany` collections on entities (all relationships are `@ManyToOne` only)
- No cascade operations in JPA (cascades are in SQL only, for `device` and `task_tag`)
- No `@SpringBootTest` integration tests yet (unit tests + `@WebMvcTest` security slices only)

## Gotchas

1. **`"user"` and `"group"` are reserved words** in PostgreSQL. Always quote them in raw SQL. JPA entities handle this via `@Table(name = "\"user\"")`.

2. **User creation has no REST endpoint.** The `UserController` has GET/PUT/DELETE but no POST. Users are created through the auth flow (`POST /api/auth/register` or Google OAuth).

3. **GroupMember is the actor, not User.** Tasks reference `GroupMember` for `created_by`, `assigned_to`, and `TaskCompletion.completed_by`. Any authorization logic must resolve the current user to their GroupMember within the relevant group.

4. **TaskTag is in the `tag` package**, not `task`. It was placed there because it's the junction entity for the tag side of the many-to-many. It has its own repository (`TaskTagRepository`) but no controller/service — tag assignment is expected to go through the task or tag service.

5. **Render free tier constraints** — 512MB RAM, IPv4 only. The app needs `JAVA_TOOL_OPTIONS=-Xmx384m` and Supabase connection pooler (port 6543) with `?prepareThreshold=0` since the pooler doesn't support prepared statements.

6. **Timestamps are UTC but columns have no timezone.** All `TIMESTAMP` columns are `WITHOUT TIME ZONE`, so UTC is enforced by the application in two places, and both are needed:
   - **Service-set times** use the injected `Clock` bean (`ClockConfig`), pinned to UTC — `LocalDateTime.now(clock)`, never bare `LocalDateTime.now()`. Covers `GroupMember.date_joined`/`date_left`, `TaskCompletion.date_completed`, `Notification.read_at`, `Device.last_used`, `RefreshToken.expires_at`, `EmailVerification.expires_at`.
   - **Hibernate-managed times** (`@CreationTimestamp`/`@UpdateTimestamp`) do **not** go through that bean — on a `LocalDateTime` they read the **JVM default zone**. So `TaskleanApiApplication.main` calls `TimeZone.setDefault(UTC)` before `SpringApplication.run`. Covers `date_created`/`date_updated` on every `BaseEntity` entity (`User`, `Group`, `Task`, `Category`, `Tag`, `Device`) plus `AuditLog`, `Notification`, `TaskCompletion`, `TaskTag`, `RefreshToken` and `EmailVerification`.

   Without the JVM pin, a host in a non-UTC zone writes the second family in local time while the first stays UTC — two time regimes in one table, off by the host's offset. It was easy to miss because containers (Render, the Postgres image) default to UTC, so it only showed up on a developer machine. The frontend must convert UTC to local time for display.

7. **String-typed enums.** Priority, status, recurrence type, and role are all `VARCHAR` + CHECK constraints in SQL, stored as plain `String` in Java. There are no Java enums — consider adding them for type safety when the domain stabilizes.

8. **`invite_code` generation** uses UUID substring (first 8 chars, uppercased) with a retry loop checking uniqueness. Collision probability is low but the approach isn't cryptographically strong.

9. **Flyway `baseline-on-migrate=true`** means if you point the app at an existing database without Flyway history, it will baseline at V1 and skip already-applied migrations. Safe for dev, be cautious in prod.

10. **`flyway.clean-disabled=false` in dev** means `Flyway.clean()` can wipe the entire schema. This is intentional for dev resets but must never leak to prod (prod config sets it to `true`).
