# CLAUDE.md

For deep context — domain model, entity relationships, database schema, API catalog, security architecture, coding patterns, and gotchas — see [PROJECT_BIBLE.md](PROJECT_BIBLE.md).

## Quick reference

```bash
# === Everyday ===
./mvnw spring-boot:run              # Start API on :8080 (dev profile, with seed data)
./mvnw clean compile                # Compile (catches errors fast)
./mvnw test                         # Run all tests

# === Database ===
docker-compose up -d                   # Start local Postgres (port 5432)
docker-compose stop                    # Stop Postgres (keeps data)
docker-compose down -v                 # Wipe database completely (fresh start)

# === Testing ===
./mvnw test                              # Run all tests
./mvnw test -Dtest=JwtServiceTest        # Run single test class
./mvnw test -Dtest="AuthServiceTest#login_validCredentials_returnsToken"  # Single method
./mvnw test -Dtest="com.tasklean.api.auth.**"  # Run all tests in a package

# === Build & package ===
./mvnw clean package                 # Build JAR

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
- **`auth`** — `AuthController` (register/login/verify/resend/refresh/logout endpoints), `AuthService` (credential validation, user creation). DTOs: `LoginRequest`, `RegisterRequest`, `AuthResponse` (includes `refreshToken` field). `GoogleOAuthService` still a stub.
  - **`auth/jwt`** — `JwtService` (token generation/validation), `JwtAuthenticationFilter` (extracts Bearer token, sets SecurityContext), `JwtAuthenticationEntryPoint` (401 JSON response).
  - **`auth/verification`** — `VerificationService` (email verification code generation, validation, resend), `EmailVerification` (entity), `EmailVerificationRepository`. DTOs in `verification/dto`: `VerifyEmailRequest`, `ResendVerificationRequest`.
  - **`auth/refresh`** — `RefreshTokenService` (create with SHA-256 hashing, rotate on refresh, revoke on logout, scheduled cleanup), `RefreshToken` (entity), `RefreshTokenRepository`. DTOs in `refresh/dto`: `RefreshRequest`.
- **`common`** — `BaseEntity` (mapped superclass with `dateCreated`/`dateUpdated`), `ApiResponse<T>` (response envelope with `success`/`error` factory methods), `exception/` (`ResourceNotFoundException`, `DuplicateResourceException`, `GlobalExceptionHandler` handling 400/401/404/409), `email/EmailService` (shared email infrastructure using Spring Mail + Mailtrap in dev).
- **`config`** — `SecurityConfig` (stateless JWT filter chain, BCrypt encoder), `JwtConfig` (`@ConfigurationProperties` for `jwt.secret`/`jwt.expiration`), `CorsConfig` (configurable origins, credentials enabled). `@EnableScheduling` on `TaskleanApiApplication` for refresh token cleanup job.

## Conventions

- **Primary keys**: `id_<entity>` column, `BIGSERIAL` in SQL, `Long` with `@GeneratedValue(IDENTITY)` in Java.
- **Soft deletes**: all entities use `is_active` boolean, never hard-delete rows.
- **UIDs**: user-facing identifier (`uid` column) separate from internal PK. Present on `user`, `group`, `task`.
- **Timestamps**: `BaseEntity` provides `date_created`/`date_updated` via Hibernate `@CreationTimestamp`/`@UpdateTimestamp`. Entities not extending `BaseEntity` (`GroupMember`, `Notification`, `AuditLog`, `TaskCompletion`, `TaskTag`) manage their own `date_created`.
- **Flyway migrations**: `src/main/resources/db/migration/V<N>__description.sql`. Next available version is V14.
- **Profiles**: `application.properties` (base), `application-dev.properties` (local Docker Postgres), `application-prod.properties` (Supabase Postgres). Active profile set via `SPRING_PROFILES_ACTIVE` env var (defaults to `dev`).
- **Lombok**: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on entities. Use `@Builder.Default` on fields with initializers. Use `@RequiredArgsConstructor` for constructor injection in services/controllers.
- **Reserved words**: `user` and `group` table names are quoted (`"user"`, `"group"`) in both `@Table` annotations and migrations.
- **API response**: all endpoints return `ApiResponse<T>` envelope. Use `ApiResponse.success(data)` or `ApiResponse.error(message)`.

## Testing

```bash
mvnw.cmd test                                    # Run all tests
mvnw.cmd test -Dtest=JwtServiceTest              # Run single test class
mvnw.cmd test -Dtest="JwtServiceTest#testName"   # Run single test method
```

### Test structure

Tests mirror `src/main/java` under `src/test/java`. Test class naming:
- `<Class>Test.java` — unit tests (no Spring context, mocked dependencies)
- `<Class>IntegrationTest.java` — integration tests with `@SpringBootTest` (future, needs Testcontainers)

### Conventions

- **Unit tests only mock direct dependencies** — use `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- **Test method naming**: `methodName_scenario_expectedResult` (e.g., `login_wrongPassword_throwsBadCredentials`)
- **One assertion per concept** — a test can have multiple asserts if they verify one logical outcome
- **Test what matters**: business rules, edge cases, security boundaries. Don't test getters/setters or framework wiring.
- **No test for the sake of coverage** — every test should prove a behaviour or guard a bug

## Environment

`.env` holds `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `MAILTRAP_USERNAME`, `MAILTRAP_PASSWORD`, `SPRING_PROFILES_ACTIVE`. Loaded natively via `spring.config.import=optional:file:.env[.properties]` in `application.properties`. Never commit real secrets — the checked-in `.env` has placeholder values only.
