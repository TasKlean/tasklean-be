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
- **`common`** — `BaseEntity` (mapped superclass with `dateCreated`/`dateUpdated`), `ApiResponse<T>` (response envelope with `success`/`error` factory methods), `ErrorMessages` (centralized "not found" string constants), `exception/` (`ResourceNotFoundException`, `DuplicateResourceException`, `GlobalExceptionHandler` handling 400/401/404/409 plus a catch-all 500, and unknown routes via `NoResourceFoundException` → JSON 404), `email/EmailService` (shared email infrastructure using Spring Mail + Mailtrap in dev), `logging/` (`RequestLoggingFilter` — per-request MDC + one summary line; `LogFields` — MDC key constants).
- **`config`** — `SecurityConfig` (stateless JWT filter chain, BCrypt encoder), `JwtConfig` (`@ConfigurationProperties` for `jwt.secret`/`jwt.expiration`/`jwt.refresh-expiration`), `CorsConfig` (configurable origins, credentials enabled), `ClockConfig` (UTC `Clock` bean for consistent timestamps). `@EnableScheduling` on `TaskleanApiApplication` for refresh token cleanup job. Spring Boot Actuator exposes a public `/actuator/health` endpoint (health only, no details) whitelisted in `SecurityConfig`.

## Conventions

- **Primary keys**: `id_<entity>` column, `BIGSERIAL` in SQL, `Long` with `@GeneratedValue(IDENTITY)` in Java.
- **Soft deletes**: all entities use `is_active` boolean, never hard-delete rows.
- **UIDs**: user-facing identifier (`uid` column) separate from internal PK. Present on `user`, `group`, `task`.
- **Timestamps**: `BaseEntity` provides `date_created`/`date_updated` via Hibernate `@CreationTimestamp`/`@UpdateTimestamp`. Entities not extending `BaseEntity` (`GroupMember`, `Notification`, `AuditLog`, `TaskCompletion`, `TaskTag`) manage their own `date_created`.
- **Flyway migrations**: `src/main/resources/db/migration/V<N>__description.sql`. Next available version is V16.
- **Row Level Security**: every table has RLS enabled (V14) to close Supabase's PostgREST/anon-key access path; the backend connects as the table owner and bypasses it. **Any migration that creates a new table must end with `ALTER TABLE <name> ENABLE ROW LEVEL SECURITY;`** — no policies (deny-all through the API is the goal), and never `FORCE ROW LEVEL SECURITY` (it would make the owner obey policies too).
- **Profiles**: `application.properties` (base — shared config including database, JWT, mail via env vars), `application-dev.properties` (verbose logging, seed data, Flyway clean enabled, 1-year JWT), `application-prod.properties` (minimal logging, ECS JSON structured logging, Flyway clean disabled). Active profile set via `SPRING_PROFILES_ACTIVE` env var (defaults to `dev`).
- **Lombok**: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on entities. Use `@Builder.Default` on fields with initializers. Use `@RequiredArgsConstructor` for constructor injection in services/controllers.
- **Reserved words**: `user` and `group` table names are quoted (`"user"`, `"group"`) in both `@Table` annotations and migrations.
- **API response**: all endpoints return `ApiResponse<T>` envelope. Use `ApiResponse.success(data)` or `ApiResponse.error(message)`.
- **Timestamps are UTC**: all `LocalDateTime.now()` calls use an injected `Clock` bean (`ClockConfig`) pinned to UTC. Never call `LocalDateTime.now()` without the clock — use `LocalDateTime.now(clock)`. Tests use `@Spy Clock clock = Clock.systemUTC()` and `LocalDateTime.now(ZoneOffset.UTC)` for fixture data.
- **Error messages**: "not found" strings are centralized in `common/ErrorMessages.java`. Use constants from there instead of inline strings.
- **Logging**: SLF4J via Lombok `@Slf4j` (never `System.out`). Levels: **ERROR** (unexpected failures / 500s — only the catch-all in `GlobalExceptionHandler` logs a stack trace), **WARN** (handled-but-abnormal, security events like failed auth), **INFO** (significant business events, e.g. entity created/deleted — log the `uid`, not full entities), **DEBUG** (dev-only flow detail). **Never log** secrets, password hashes, JWTs/refresh tokens, verification codes, or full request/response bodies (PII). Correlation fields (`requestId`, `userId`) are auto-injected into MDC by `RequestLoggingFilter` + `JwtAuthenticationFilter`, so every line within a request already carries them — don't pass them manually. **Baseline is automatic**: every endpoint gets a request-summary line and centralized exception logging for free; a new feature only adds an explicit `INFO` for a genuine business event. Prod emits ECS JSON to stdout (`logging.structured.format.console=ecs`) for log-drain ingestion (Kibana/Grafana/Loki); dev stays human-readable. Logs are ops telemetry — distinct from the DB-persisted audit trail; don't duplicate it.
- **Comments**: three kinds, each with a specific job. Keep them current — a stale comment is worse than none, so update or delete a comment whenever you change the code beneath it.
  - **File header** — every `.java` file opens with a Javadoc block (`/** ... */`) giving a one-to-three-line overview of the file's *responsibility* (what the class is for, not how it works). Trivial DTOs and entities where the name says everything may use a single line or omit it.
  - **Method-level** — public and protected methods get a Javadoc comment (`/** ... */`): a sentence describing what the method does, followed by `@param` for **every** parameter, `@return` for **any** non-`void` return, and `@throws` for each exception a caller should anticipate (e.g. `ResourceNotFoundException`). A no-argument `void` method needs only the description. Private helpers don't need Javadoc; comment them inline only when non-obvious.
  - **Applies to all logic-bearing classes** — services, controllers, filters, config, and other components. Trivial data carriers (entities, request/response DTOs) are exempt from the file header and method Javadoc.
  - **Inline** — use `//` on its own line *above* the code (not trailing) to explain the *why* behind complex, non-obvious, or unconventional logic. Explain intent and decisions; never restate what the code plainly does (`// increment i` is noise).
- **Commit message prefixes**: used for auto-versioning on production releases. `fix:` → patch bump, `feat:` → minor bump, `BREAKING CHANGE:` → major bump. No prefix defaults to patch.

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

`.env` holds `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `SPRING_PROFILES_ACTIVE`. Loaded natively via `spring.config.import=optional:file:.env[.properties]` in `application.properties`. The `.env` file is gitignored — create it locally from `.env.example`. Never commit real secrets.
