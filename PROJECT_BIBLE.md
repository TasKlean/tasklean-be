# TasKlean Project Bible

Deep context, architecture decisions, domain model, and patterns for the TasKlean backend API.

## What TasKlean is

TasKlean is a group-based household task management app. Users form groups (households/roommates), create recurring and one-off cleaning tasks, assign them to members, and track completions with optional photo proof. The backend is a REST API consumed by a frontend client (expected at `localhost:3000` based on the OAuth redirect URI).

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
```

### Core aggregate: Group

Everything revolves around the Group. A User does nothing alone — they must be a GroupMember to create tasks, be assigned tasks, or complete tasks. The Group is the authorization boundary: users can only interact with entities belonging to groups they're members of.

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

### Ownership chain

```
User → GroupMember → Task → TaskCompletion
                  → Task → TaskTag
Group → Category
Group → Tag
Group → AuditLog
User  → Device
User  → Notification
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

### AuditLog design

Polymorphic audit trail using `entity_type` (VARCHAR) + `entity_id` (BIGINT). Not a JPA `@Inheritance` — just string-based type discrimination. Scoped to both a group and optionally a group member.

## Database design

### Schema conventions

- **Column naming**: `snake_case` throughout. PK is `id_<entity>`, FKs are `<referenced_entity>_id` or descriptive (`created_by`, `assigned_to`, `added_by`).
- **Types**: `BIGSERIAL` for PKs, `VARCHAR(N)` for bounded strings, `TEXT` for unbounded, `TIMESTAMP` for dates (no timezone — uses JVM timezone), `BOOLEAN` for flags, `JSONB` for structured data.
- **Defaults**: `is_active DEFAULT TRUE`, `date_created DEFAULT NOW()`, `date_updated DEFAULT NOW()`, `is_read DEFAULT FALSE`.
- **Reserved words**: `user` and `group` are quoted as `"user"` and `"group"` everywhere — SQL, JPA `@Table`, and Spring Data queries.
- **Cascades**: `ON DELETE CASCADE` only on `device → user` and `task_tag` FKs. All other FKs have no cascade — application handles deletion logic.

### Indexes

Every FK column is indexed. Additional indexes on:
- `user`: `email`, `uid`, `google_sub`
- `group`: `uid`, `invite_code`
- `task`: `group_id`, `assigned_to`, `status`, `priority`, `uid`
- `notification`: `(user_id, is_read)` composite for unread queries
- `audit_log`: `(entity_type, entity_id)` composite, `date_created` for time-range queries

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

Next available version: **V12**.

### Hibernate strategy

`ddl-auto=validate` — Hibernate validates entities against the Flyway-managed schema at startup but never modifies it. All schema changes go through migrations.

## API design

### Response envelope

Every endpoint returns `ApiResponse<T>`:
```json
{ "success": true, "message": null, "data": { ... } }
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

- `200` — success (GET, PUT, PATCH, DELETE)
- `201` — created (POST)
- `400` — validation failure (MethodArgumentNotValidException)
- `404` — ResourceNotFoundException
- `409` — DuplicateResourceException

### Identifiers in URLs

Entities with a `uid` field (User, Group, Task) use `/{uid}` as the path variable. All other entities use `/{id}` (the Long PK). This is intentional — UIDs are opaque external identifiers, while internal PKs are used for subordinate entities that are never referenced outside the API.

## Security architecture (planned)

### Current state

SecurityConfig has a permit-all filter chain with CSRF disabled. No authentication is enforced.

### Intended design

- **JWT-based auth**: `JwtService` will issue and validate tokens. JWT secret and expiration configured per profile (`jwt.secret`, `jwt.expiration` = 24h).
- **Google OAuth**: `GoogleOAuthService` for social login. Client ID/secret in env vars. Redirect URI is `localhost:3000` in dev, configurable in prod.
- **Auth endpoints**: `/api/auth/**` are permit-all. All other endpoints will require authentication.
- **Password storage**: `password_hash` column on User — supports both password-based and OAuth login (OAuth users have null `password_hash`).

### Stubs still to implement

- `AuthController` — login, register, Google OAuth callback
- `AuthService` — credential validation, user creation, token generation
- `JwtService` — token generation, validation, claims extraction
- `GoogleOAuthService` — Google token verification, user upsert
- `JwtConfig` — JWT properties binding
- `CorsConfig` — CORS policy for frontend origin

### Authorization model (not yet built)

The GroupMember role system (`ADMIN` / `MEMBER`) exists in the schema but is not enforced in the API layer. When implemented, expect:
- ADMIN: can manage group settings, members, categories, tags
- MEMBER: can create/complete tasks, view group data

## Configuration strategy

### Profile hierarchy

```
application.properties          ← base (shared across all profiles)
  └── application-dev.properties   ← local Docker Postgres, verbose logging
  └── application-prod.properties  ← Supabase Postgres, minimal logging
```

Active profile set via `SPRING_PROFILES_ACTIVE` env var (defaults to `dev`).

### Environment variables

| Variable | Used in | Purpose |
|---|---|---|
| `DB_USERNAME` | dev, prod | Database user |
| `DB_PASSWORD` | dev, prod | Database password |
| `DATABASE_URL` | prod only | Full JDBC URL (Supabase) |
| `JWT_SECRET` | dev, prod | HMAC signing key (min 256 bits) |
| `GOOGLE_CLIENT_ID` | dev, prod | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | dev, prod | Google OAuth client secret |
| `GOOGLE_REDIRECT_URI` | prod only | OAuth callback URL (dev hardcodes `localhost:3000`) |
| `SPRING_PROFILES_ACTIVE` | base | Profile selector |

Dev profile has hardcoded fallback defaults for all values so the app starts without a `.env` file.

### Key base settings

- `server.port=8080`
- `spring.jpa.hibernate.ddl-auto=validate` (Flyway owns schema)
- `spring.flyway.baseline-on-migrate=true` (safe for first run)
- `spring.servlet.multipart.max-file-size=5MB` / `max-request-size=10MB` (task photos)
- `spring.flyway.clean-disabled=false` in dev, `true` in prod

## Infrastructure

### Local development

```
docker-compose up -d        # PostgreSQL 16 on port 5432 (tasklean_dev database)
mvnw.cmd spring-boot:run    # API on port 8080
```

Docker Compose only runs Postgres — the app runs on the host. The Compose file reads `DB_USERNAME` and `DB_PASSWORD` from `.env` with defaults.

### Production

Supabase-hosted PostgreSQL. The Dockerfile builds a multi-stage image (`eclipse-temurin:21`) but does not copy the Maven wrapper (the `RUN ./mvnw` will fail — needs `COPY mvnw` and `COPY .mvn`). This is a known issue to fix before deploying.

## Coding patterns

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

### What's NOT in the codebase yet

- No pagination (all list endpoints return full results)
- No sorting parameters
- No filtering beyond `?groupId=` / `?userId=` / `?taskId=`
- No `@OneToMany` collections on entities (all relationships are `@ManyToOne` only)
- No cascade operations in JPA (cascades are in SQL only, for `device` and `task_tag`)
- No DTOs for auth flow (LoginRequest, RegisterRequest, AuthResponse are empty stubs)
- No tests beyond the smoke test `ApiApplicationTests.contextLoads()`
- No input validation beyond `@NotBlank` / `@NotNull` (no regex, no size, no custom validators)
- No password hashing configuration (BCrypt bean etc.)

## Gotchas

1. **`"user"` and `"group"` are reserved words** in PostgreSQL. Always quote them in raw SQL. JPA entities handle this via `@Table(name = "\"user\"")`.

2. **User creation has no REST endpoint.** The `UserController` has GET/PUT/DELETE but no POST. Users are created through the auth flow (register or Google OAuth), which is not yet implemented.

3. **GroupMember is the actor, not User.** Tasks reference `GroupMember` for `created_by`, `assigned_to`, and `TaskCompletion.completed_by`. Any authorization logic must resolve the current user to their GroupMember within the relevant group.

4. **TaskTag is in the `tag` package**, not `task`. It was placed there because it's the junction entity for the tag side of the many-to-many. It has its own repository (`TaskTagRepository`) but no controller/service — tag assignment is expected to go through the task or tag service.

5. **Dockerfile is broken for production** — it runs `./mvnw` but doesn't copy the Maven wrapper files (`mvnw`, `.mvn/`). Needs fixing before any Docker-based deployment.

6. **No timezone in timestamps.** All `TIMESTAMP` columns are without timezone. The JVM's default timezone is used. This works if all instances run in the same timezone but will cause issues in a distributed setup.

7. **String-typed enums.** Priority, status, recurrence type, and role are all `VARCHAR` + CHECK constraints in SQL, stored as plain `String` in Java. There are no Java enums — consider adding them for type safety when the domain stabilizes.

8. **`invite_code` generation** uses UUID substring (first 8 chars, uppercased) with a retry loop checking uniqueness. Collision probability is low but the approach isn't cryptographically strong.

9. **Flyway `baseline-on-migrate=true`** means if you point the app at an existing database without Flyway history, it will baseline at V1 and skip already-applied migrations. Safe for dev, be cautious in prod.

10. **`flyway.clean-disabled=false` in dev** means `Flyway.clean()` can wipe the entire schema. This is intentional for dev resets but must never leak to prod (prod config sets it to `true`).
