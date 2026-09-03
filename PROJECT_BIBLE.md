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
```

### Core aggregate: Group

Everything revolves around the Group. A User does nothing alone — they must be a GroupMember to create tasks, be assigned tasks, or complete tasks. A user can be a member of **multiple groups** simultaneously (e.g., one for family, one for roommates). The Group is the authorization boundary: users can only interact with entities belonging to groups they're members of.

### Entity details

| Entity | PK | Has UID | Extends BaseEntity | Soft-delete | Notes |
|---|---|---|---|---|---|
| User | `id_user` | Yes (`uid`, 100 chars) | Yes | `is_active` | Also has `google_sub` for OAuth |
| Group | `id_group` | Yes (`uid`, 500 chars) | Yes | `is_active` | Has `invite_code` for joining |
| GroupMember | `id_group_member` | No | No | `is_active` + `date_left` | Junction with role; unique on `(user_id, group_id)` |
| Task | `id_task` | Yes (`uid`, 500 chars) | Yes | `is_active` | Central domain object |
| Category | `id_category` | No | Yes | `is_active` | Scoped to group; unique on `(group_id, name)` |
| Tag | `id_tag` | No | Yes | `is_active` | Scoped to group; unique on `(group_id, name)` |
| TaskTag | `id_task_tag` | No | No | No (hard delete via CASCADE) | Junction; unique on `(task_id, tag_id)` |
| TaskCompletion | `id_task_completion` | No | No | None | Immutable completion record |
| Device | `id_device` | No | Yes | `is_active` | Push notification tokens |
| Notification | `id_notification` | No | No | None | Has `is_read` + `read_at` |
| AuditLog | `id_audit_log` | No | No | None | Immutable; polymorphic via `entity_type` + `entity_id` |
| RefreshToken | `id_refresh_token` | No | No | `is_revoked` | SHA-256 hashed token; 14-day expiry (configurable via `jwt.refresh-expiration`); `CASCADE` on user delete |

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

The `role` column is CHECK-constrained to `'ADMIN'` or `'MEMBER'`. Self-referential FKs `added_by` and `removed_by` track who added/removed whom.

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

**Writing entries** — `AuditLogService.record(entityType, entityId, action, message, group)` is the single write path. It runs in the caller's transaction (an entry is persisted only if the operation commits) and resolves the actor and IP from the request context via the `AuditContext` helper. Actions are the `AuditAction` enum; entity types are `AuditEntityType` constants (both uppercase, e.g. `TASK`/`CREATE`). Pass a `null` group for group-less events (authentication/account), which the schema allows.

**Actor attribution** — `actor_user_id` (nullable, V15) records the authenticated caller from the security context, so the actor is captured even for group-less events and when actor ≠ subject (e.g. one user deleting another's account). `group_member_id` still records the actor's membership/role context for group-scoped events. Public `/api/auth/**` routes have no authenticated caller, so `actor_user_id` is null there and the user is identified by `entity_id` instead.

## Database design

### Schema conventions

- **Column naming**: `snake_case` throughout. PK is `id_<entity>`, FKs are `<referenced_entity>_id` or descriptive (`created_by`, `assigned_to`, `added_by`).
- **Types**: `BIGSERIAL` for PKs, `VARCHAR(N)` for bounded strings, `TEXT` for unbounded, `TIMESTAMP` for dates (no timezone in column — application enforces UTC via injected `Clock` bean), `BOOLEAN` for flags, `JSONB` for structured data.
- **Defaults**: `is_active DEFAULT TRUE`, `date_created DEFAULT NOW()`, `date_updated DEFAULT NOW()`, `is_read DEFAULT FALSE`.
- **Reserved words**: `user` and `group` are quoted as `"user"` and `"group"` everywhere — SQL, JPA `@Table`, and Spring Data queries.
- **Cascades**: `ON DELETE CASCADE` on `device → user`, `task_tag` FKs, and `refresh_token → user`. All other FKs have no cascade — application handles deletion logic.

### Indexes

Every FK column is indexed. Additional indexes on:
- `user`: `email`, `uid`, `google_sub`
- `group`: `uid`, `invite_code`
- `task`: `group_id`, `assigned_to`, `status`, `priority`, `uid`
- `notification`: `(user_id, is_read)` composite for unread queries
- `audit_log`: `(entity_type, entity_id)` composite, `date_created` for time-range queries, `actor_user_id` for by-user activity queries
- `refresh_token`: `token` (unique, for lookup by hashed value), `user_id` (for bulk revocation)

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

Next available version: **V16**.

### Dev seed data

Dev-only seed data lives in `src/main/resources/db/seed/R__seed_data.sql` — a Flyway **repeatable migration** that runs after all versioned migrations. The dev profile adds `classpath:db/seed` to `spring.flyway.locations`; prod only has `classpath:db/migration`, so seed data never touches production.

The seed creates a realistic dataset: 3 users (1 Google-linked, 2 email-based), 2 groups, 5 group members (including a multi-group user), categories, tags, 8 tasks with varied statuses/priorities/recurrence, task-tag associations, completions (one with photo proof), notifications (read/unread mix), audit log entries, and devices.

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
```

### Endpoint catalog

| Resource | Base path | Identifier | CRUD | Notes |
|---|---|---|---|---|
| User | `/api/users` | `/{uid}` | GET, GET all, PUT, DELETE | No POST — creation via auth flow |
| Group | `/api/groups` | `/{uid}` | Full CRUD | POST generates uid + invite code |
| Task | `/api/tasks` | `/{uid}` | Full CRUD | GET all requires `?groupId=` |
| Category | `/api/categories` | `/{id}` | Full CRUD | GET all requires `?groupId=` |
| Tag | `/api/tags` | `/{id}` | Full CRUD | GET all requires `?groupId=` |
| GroupMember | `/api/group-members` | `/{id}` | POST, GET, GET all, PATCH role, DELETE | GET all requires `?groupId=`; PATCH `/{id}/role?role=` |
| Device | `/api/devices` | `/{id}` | POST, GET, GET all, PUT, DELETE | GET all requires `?userId=` |
| TaskCompletion | `/api/task-completions` | — | POST, GET by task, GET by member | GET requires `?taskId=`; `/member/{id}` |
| Notification | `/api/notifications` | — | GET, GET unread, count, PATCH read | All GETs require `?userId=`; PATCH `/{id}/read` |
| AuditLog | `/api/audit-logs` | — | GET by group, entity, member | GET requires `?groupId=`; `/entity?entityType=&entityId=`; `/member/{id}` |

### HTTP status codes

- `200` — success (GET, PUT, PATCH, DELETE, login)
- `201` — created (POST, register)
- `400` — validation failure (MethodArgumentNotValidException)
- `401` — unauthorized (missing/invalid token, bad credentials, deactivated account)
- `404` — ResourceNotFoundException, or an unknown route (NoResourceFoundException → JSON envelope, not the Whitelabel HTML page)
- `409` — DuplicateResourceException
- `500` — catch-all `@ExceptionHandler(Exception.class)` for anything unhandled; logs the stack trace (with requestId/userId) and returns a generic message — never leaks internal details

### Identifiers in URLs

Entities with a `uid` field (User, Group, Task) use `/{uid}` as the path variable. All other entities use `/{id}` (the Long PK). This is intentional — UIDs are opaque external identifiers, while internal PKs are used for subordinate entities that are never referenced outside the API.

## Security architecture

### Current state

Email/password authentication is fully implemented. All endpoints except `/api/auth/**` require a valid JWT Bearer token. Google OAuth is planned but not yet built.

### Row Level Security (Supabase)

Supabase auto-exposes every `public`-schema table through its PostgREST API using the public `anon` key. To close that path, **RLS is enabled on all tables** (V14 migration) with **no policies** — which denies all access to the `anon`/`authenticated` API roles. The Spring backend is unaffected because it connects as the table **owner**, and owners bypass RLS (we deliberately do *not* use `FORCE ROW LEVEL SECURITY`). All authorization stays in the service layer with our own JWT; we do not use Supabase Auth, so `auth.uid()` policies aren't applicable. **Every new table's migration must enable RLS** (see CLAUDE.md conventions).

### Token architecture (implemented)

**Access token (JWT)**:
- HMAC-SHA signed JWT with claims: `sub` (email), `userId` (internal PK), `uid` (public identifier), `iat`, `exp`
- Expiration: **15 minutes** (configurable via `jwt.expiration` in ms; dev profile overrides to 1 year for easier local testing)
- Secret: min 256 bits, configured per profile via `jwt.secret`
- Library: jjwt 0.12.6

**Refresh token (opaque)**:
- 32-byte cryptographically random value (via `SecureRandom`), Base64url-encoded
- Stored in DB **SHA-256 hashed** (not plaintext) — raw token only sent to client
- Expiration: **14 days** (configurable via `jwt.refresh-expiration` in ms)
- **Single-use with rotation**: each refresh revokes the old token and issues a new pair
- Logout revokes **all** refresh tokens for the user
- Scheduled cleanup (`@Scheduled`, every 6 hours) purges expired and revoked tokens from DB
- Entity: `RefreshToken` (table `refresh_token`, V13 migration)

### Request flow

0. `RequestLoggingFilter` (highest precedence, runs before the Spring Security chain) mints/adopts a `requestId`, puts request context into MDC, and logs a summary line on completion — so even 401s are logged and correlated
1. `JwtAuthenticationFilter` (runs before Spring's authorization check) extracts the Bearer token from the `Authorization` header
2. If valid: looks up user by email, places `User` entity into `SecurityContextHolder`
3. If missing/invalid: request continues as anonymous
4. Spring's `authorizeHttpRequests` rules decide allow/deny based on whether SecurityContext has an authenticated principal
5. Denied requests → `JwtAuthenticationEntryPoint` returns 401 JSON matching `ApiResponse` envelope

### Security filter chain config

- CSRF disabled (stateless API, tokens in JSON body — will need revisiting if refresh tokens move to httpOnly cookies)
- Session policy: `STATELESS` (no server-side sessions)
- CORS: configurable origins (`cors.allowed-origins`), credentials enabled, `Authorization` + `Content-Type` headers
- Public paths: `/api/auth/**`, `/actuator/health`, `/error`
- All other paths: `authenticated()`
- Password encoding: BCrypt

### Auth endpoints

| Endpoint | Status | Response |
|---|---|---|
| `POST /api/auth/register` | 201 | User info + "Verification code sent" message (no tokens until verified) |
| `POST /api/auth/login` | 200 | Access JWT + refresh token + user info (rejects unverified users) |
| `POST /api/auth/verify-email` | 200 | Access JWT + refresh token + user info (on valid code) |
| `POST /api/auth/resend-verification` | 200 | Generic success message (enumeration-safe, always 200) |
| `POST /api/auth/refresh` | 200 | New access JWT + new refresh token (rotates old refresh token) |
| `POST /api/auth/logout` | 200 | "Logged out successfully" (revokes all user refresh tokens) |
| `POST /api/auth/google` | Not implemented | — |

### Authentication flows

**Email registration**: validate input (`@Email`, `@Size(min=8)` password, `@NotBlank` name/lastName) → check duplicate email (409) → hash password (BCrypt) → create User with random UID → generate & send 6-digit verification code (5-min expiry) → return `AuthResponse` with message (no JWT).

**Email verification**: find user by email, filter out already-verified → find matching non-expired code → mark user `is_email_verified=true` → delete all user's codes → generate JWT → return `AuthResponse`. All failure cases (unknown email, already verified, wrong/expired code) return same generic "Invalid email or code" error to prevent email enumeration.

**Resend verification**: find user by email, filter out already-verified → if found and unverified, generate & send new code. Always returns 200 with generic message regardless of email state (enumeration-safe).

**Email login**: find user by email (401 if not found) → check `is_active` (401 if deactivated) → check not OAuth-only account (401 if no password hash) → check `is_email_verified` (401 if unverified) → verify password against hash (401 if mismatch) → generate access JWT + refresh token → return `AuthResponse`.

**Token refresh**: hash incoming refresh token (SHA-256) → look up non-revoked match in DB → check expiry (revoke + 401 if expired) → revoke old token → generate new access JWT + new refresh token → return `AuthResponse`. Unknown/revoked tokens return 401.

**Logout**: hash incoming refresh token → look up non-revoked match → revoke all refresh tokens for that user. Invalid token returns 401.

**Google OAuth (planned)**: redirect to Google → callback with authorization code → exchange code for access token → verify `id_token` → extract email/name/`google_sub` → find existing user by `google_sub` or create new one → generate JWT → return token.

### Still to implement

- `GoogleOAuthService` — Google token verification, user upsert
- `POST /api/auth/google` endpoint in AuthController
- Role-based authorization (ADMIN/MEMBER enforcement in API layer)
- Rate limiting on public auth endpoints (prevent brute force / spam)
- httpOnly cookies for refresh token transport (currently sent in JSON response body)

### Authorization model (not yet built)

The GroupMember role system (`ADMIN` / `MEMBER`) exists in the schema but is not enforced in the API layer. When implemented:

- **GroupAdmin** (extends GroupMember permissions): create/edit group, invite/remove members, manage categories and tags
- **GroupMember**: join/leave group, create/edit/delete own tasks, complete tasks, view group tasks, ping task assignees

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
| `GOOGLE_CLIENT_ID` | base (all profiles) | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | base (all profiles) | Google OAuth client secret |
| `GOOGLE_REDIRECT_URI` | prod only | OAuth callback URL (dev hardcodes `localhost:3000`) |
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

The Dockerfile builds a multi-stage image (`eclipse-temurin:21-jdk-alpine` → `eclipse-temurin:21-jre-alpine`). Deployed on Render with `JAVA_TOOL_OPTIONS=-Xmx384m` to fit within the 512MB free-tier container.

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

**Production** (`ci-production.yml`) — triggers on push to `production` and PRs to `production`:
```
compile → test → sonarcloud → auto-version tag → build & push Docker image → deploy to Render → create GitHub Release
```
Version/build/deploy/release only run on push (not PRs). Uses GitHub environment `Production` (requires manual approval).

**Auto-versioning**: the `version` job uses `mathieudutour/github-tag-action` to bump the version tag automatically based on commit message prefixes:
- `fix:` → patch bump (e.g., `v1.0.0` → `v1.0.1`)
- `feat:` → minor bump (e.g., `v1.0.0` → `v1.1.0`)
- `BREAKING CHANGE:` → major bump (e.g., `v1.0.0` → `v2.0.0`)
- No prefix → defaults to patch

Docker images are tagged with the version (`v1.0.1`), commit sha (`sha-abc123`), and `latest`.

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
- Controller routing/serialization (these would be `@WebMvcTest` slice tests, not yet added)

**Current test coverage:**

| Class | Tests | Covers |
|-------|-------|--------|
| `JwtServiceTest` | 9 | Token generation, validation (valid/tampered/expired/wrong secret), claim extraction |
| `AuthServiceTest` | 10 | Register (success, duplicate email, password hashing, UID generation); Login (success, wrong password, missing email, deactivated, OAuth-only, unverified email) |
| `JwtAuthenticationFilterTest` | 8 | Valid token sets SecurityContext, no/bad/invalid token passes through, inactive/deleted user rejected, auth endpoints skipped |
| `VerificationServiceTest` | 8 | createAndSend (code generation + email); verifyEmail (valid code, invalid code, already verified, unknown email — all enumeration-safe); resendVerification (unverified sends, already verified silent, unknown email silent) |
| `RefreshTokenServiceTest` | 7 | createRefreshToken (hashed storage); refresh (valid rotation, expired revoke+throw, revoked throw, unknown throw); logout (valid revokes all, invalid throws) |

Total: **42 tests** across 5 test classes.

### What's NOT in the codebase yet

- No pagination (all list endpoints return full results)
- No sorting parameters
- No filtering beyond `?groupId=` / `?userId=` / `?taskId=`
- No `@OneToMany` collections on entities (all relationships are `@ManyToOne` only)
- No cascade operations in JPA (cascades are in SQL only, for `device` and `task_tag`)
- No `@WebMvcTest` slice tests or integration tests (only unit tests and a smoke test)
- No role-based authorization enforcement (ADMIN/MEMBER roles exist in schema but not checked in API)
- Google OAuth not yet implemented (`GoogleOAuthService` is a stub)

## Gotchas

1. **`"user"` and `"group"` are reserved words** in PostgreSQL. Always quote them in raw SQL. JPA entities handle this via `@Table(name = "\"user\"")`.

2. **User creation has no REST endpoint.** The `UserController` has GET/PUT/DELETE but no POST. Users are created through the auth flow (`POST /api/auth/register` or Google OAuth).

3. **GroupMember is the actor, not User.** Tasks reference `GroupMember` for `created_by`, `assigned_to`, and `TaskCompletion.completed_by`. Any authorization logic must resolve the current user to their GroupMember within the relevant group.

4. **TaskTag is in the `tag` package**, not `task`. It was placed there because it's the junction entity for the tag side of the many-to-many. It has its own repository (`TaskTagRepository`) but no controller/service — tag assignment is expected to go through the task or tag service.

5. **Render free tier constraints** — 512MB RAM, IPv4 only. The app needs `JAVA_TOOL_OPTIONS=-Xmx384m` and Supabase connection pooler (port 6543) with `?prepareThreshold=0` since the pooler doesn't support prepared statements.

6. **Timestamps are UTC but columns have no timezone.** All `TIMESTAMP` columns are `WITHOUT TIME ZONE`. The application enforces UTC via an injected `Clock` bean (`ClockConfig`). All services use `LocalDateTime.now(clock)`, never bare `LocalDateTime.now()`. The frontend must convert UTC to local time for display.

7. **String-typed enums.** Priority, status, recurrence type, and role are all `VARCHAR` + CHECK constraints in SQL, stored as plain `String` in Java. There are no Java enums — consider adding them for type safety when the domain stabilizes.

8. **`invite_code` generation** uses UUID substring (first 8 chars, uppercased) with a retry loop checking uniqueness. Collision probability is low but the approach isn't cryptographically strong.

9. **Flyway `baseline-on-migrate=true`** means if you point the app at an existing database without Flyway history, it will baseline at V1 and skip already-applied migrations. Safe for dev, be cautious in prod.

10. **`flyway.clean-disabled=false` in dev** means `Flyway.clean()` can wipe the entire schema. This is intentional for dev resets but must never leak to prod (prod config sets it to `true`).
