# CLAUDE.md

For deep context — domain model, entity relationships, database schema, API catalog, security architecture, coding patterns, and gotchas — see [PROJECT_BIBLE.md](PROJECT_BIBLE.md).

## Quick reference

```bash
# === Everyday ===
mvnw.cmd spring-boot:run              # Start API on :8080 (dev profile, with seed data)
mvnw.cmd clean compile                # Compile (catches errors fast)
mvnw.cmd test                         # Run all tests

# === Database ===
docker-compose up -d                   # Start local Postgres (port 5432)
docker-compose stop                    # Stop Postgres (keeps data)
docker-compose down -v                 # Wipe database completely (fresh start)

# === Build & package ===
mvnw.cmd clean package                 # Build JAR
mvnw.cmd test -Dtest=ApiApplicationTests  # Run single test class

# === Database inspection ===
docker exec -it tasklean-db psql -U dev -d tasklean_dev    # Open psql shell
docker exec tasklean-db psql -U dev -d tasklean_dev -c "\dt"  # List tables
```

Java 21, Spring Boot 4.1.0, Maven 3.9.16.

### Seed data

Dev profile auto-loads seed data via Flyway repeatable migration (`src/main/resources/db/seed/R__seed_data.sql`). Runs on every startup if checksum changed. To reset to clean seeded state: `docker-compose down -v` then restart.

## Architecture

Package root: `com.tasklean.api`

- **`domain/<entity>`** — one package per aggregate. Each has: `Entity.java`, `Controller.java`, `Service.java`, `Repository.java`, plus `dto/EntityRequest.java` and `dto/EntityResponse.java`. Entities with uid (`user`, `group`, `task`) use uid as the external identifier in REST paths. Other entities (`category`, `tag`, `groupmember`, `device`, `taskcompletion`) use Long id. Read-only entities (`notification`, `auditlog`) have no request DTO.
- **`auth`** — `AuthController` (register/login endpoints), `AuthService` (credential validation, user creation), `JwtService` (token generation/validation), `JwtAuthenticationFilter` (extracts Bearer token, sets SecurityContext), `JwtAuthenticationEntryPoint` (401 JSON response). DTOs: `LoginRequest`, `RegisterRequest`, `AuthResponse`. `GoogleOAuthService` still a stub.
- **`common`** — `BaseEntity` (mapped superclass with `dateCreated`/`dateUpdated`), `ApiResponse<T>` (response envelope with `success`/`error` factory methods), `exception/` (`ResourceNotFoundException`, `DuplicateResourceException`, `GlobalExceptionHandler` handling 400/401/404/409).
- **`config`** — `SecurityConfig` (stateless JWT filter chain, BCrypt encoder), `JwtConfig` (`@ConfigurationProperties` for `jwt.secret`/`jwt.expiration`), `CorsConfig` (configurable origins, credentials enabled).

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
