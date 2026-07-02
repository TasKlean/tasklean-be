# CLAUDE.md

For deep context — domain model, entity relationships, database schema, API catalog, security architecture, coding patterns, and gotchas — see [PROJECT_BIBLE.md](PROJECT_BIBLE.md).

## Commands

Build tool is Maven via the wrapper (Windows: `mvnw.cmd`).

```
mvnw.cmd clean compile
mvnw.cmd test
mvnw.cmd test -Dtest=ApiApplicationTests
mvnw.cmd spring-boot:run
mvnw.cmd clean package
```

Local dev database: `docker-compose up -d` (PostgreSQL 16 on port 5432, credentials from `.env`).

Java 21, Spring Boot 4.1.0, Maven 3.9.16.

## Architecture

Package root: `com.tasklean.api`

- **`domain/<entity>`** — one package per aggregate. Each has: `Entity.java`, `Controller.java`, `Service.java`, `Repository.java`, plus `dto/EntityRequest.java` and `dto/EntityResponse.java`. Entities with uid (`user`, `group`, `task`) use uid as the external identifier in REST paths. Other entities (`category`, `tag`, `groupmember`, `device`, `taskcompletion`) use Long id. Read-only entities (`notification`, `auditlog`) have no request DTO.
- **`auth`** — `AuthController`, `AuthService`, `JwtService`, `GoogleOAuthService`, with DTOs for `LoginRequest`, `RegisterRequest`, `AuthResponse`. All still stubs awaiting implementation.
- **`common`** — `BaseEntity` (mapped superclass with `dateCreated`/`dateUpdated`), `ApiResponse<T>` (response envelope with `success`/`error` factory methods), `exception/` (`ResourceNotFoundException`, `DuplicateResourceException`, `GlobalExceptionHandler`).
- **`config`** — `SecurityConfig` (basic permit-all filter chain), `JwtConfig`, `CorsConfig`. `JwtConfig` and `CorsConfig` are still stubs.

## Conventions

- **Primary keys**: `id_<entity>` column, `BIGSERIAL` in SQL, `Long` with `@GeneratedValue(IDENTITY)` in Java.
- **Soft deletes**: all entities use `is_active` boolean, never hard-delete rows.
- **UIDs**: user-facing identifier (`uid` column) separate from internal PK. Present on `user`, `group`, `task`.
- **Timestamps**: `BaseEntity` provides `date_created`/`date_updated` via Hibernate `@CreationTimestamp`/`@UpdateTimestamp`. Entities not extending `BaseEntity` (`GroupMember`, `Notification`, `AuditLog`, `TaskCompletion`, `TaskTag`) manage their own `date_created`.
- **Flyway migrations**: `src/main/resources/db/migration/V<N>__description.sql`. Next available version is V12.
- **Profiles**: `application.properties` (base), `application-dev.properties` (local Docker Postgres), `application-prod.properties` (Supabase Postgres). Active profile set via `SPRING_PROFILES_ACTIVE` env var (defaults to `dev`).
- **Lombok**: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on entities. Use `@Builder.Default` on fields with initializers. Use `@RequiredArgsConstructor` for constructor injection in services/controllers.
- **Reserved words**: `user` and `group` table names are quoted (`"user"`, `"group"`) in both `@Table` annotations and migrations.
- **API response**: all endpoints return `ApiResponse<T>` envelope. Use `ApiResponse.success(data)` or `ApiResponse.error(message)`.

## Environment

`.env` holds `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `SPRING_PROFILES_ACTIVE`. Never commit real secrets — the checked-in `.env` has placeholder values only.
