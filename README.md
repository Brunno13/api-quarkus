# ⚡ API Quarkus (Kotlin) 

Reusable **Kotlin + Quarkus REST API starter** for mobile and web backends. Designed to be forked into independent production projects, with a feature-first modular monolith, clear architecture boundaries, environment-based configuration, and an incremental quality and security strategy.

> **Current stage (November 2026):** Foundation validated on **Gradle 9.8.1**, **Quarkus 3.40.1 LTS**, **Kotlin 2.4.10**, and **Java 21**. The ADMIN-protected users API, BCrypt (cost 12), MongoDB repositories, migrations V001/V002, and one-time first-ADMIN bootstrap are implemented. Production `POST /auth/login` verifies persisted credentials, issues RS256 Bearer tokens, and now enforces **MongoDB-backed fixed-window rate limiting** by normalized identity and network origin (`429` with `Retry-After`); the `dev`/`test` mock remains separate. The phase 2.14 baseline passed **150 standard tests** and **seven dedicated MongoDB bootstrap integration tests**. On **2026-11-16**, the phase 2.15 `clean check` and `quarkusBuild` passed, as did separate private-network HTTP E2E tests for login, JWT/RBAC, `401`/`429`, HMAC/TTL counters and audited deletion of both disposable databases. The latest rate-limit MongoDB JUnit integration suite is separate from the default `check`. The older ARM64 Native Image smoke tests remain valid for the foundation, but Native Image has **not** been rebuilt with the latest authentication features. Managed key rotation, recovery/refresh/revocation, CI security gates, and production deployment remain pending.

---

## 🚀 Key Features

- **Credential Persistence and Provisioning:** BCrypt (cost 12), isolated `UserCredential` domain, MongoDB repository and migrations, ADMIN-only initial provisioning, and verified credential-based production login.
- **First ADMIN Bootstrap:** One-time interactive Quarkus command-mode entrypoint (not an HTTP route), with exclusive MongoDB reservation, fail-closed writes, and separate JVM packaging.
- **Reusable Backend Foundation:** Start a new API from a fork without coupling business code to a specific product or hosting environment.
- **Modular Architecture:** Feature-first organization with pragmatic Clean Architecture (`api`, `application`, `domain`, `infrastructure`).
- **REST & API Documentation:** Quarkus REST, Jackson, Bean Validation, OpenAPI / Swagger UI.
- **Dependency Injection:** CDI / Arc with constructor-based dependencies.
- **Persistence Foundation:** MongoDB Panache Kotlin, domain repository contracts, normalized e-mail addresses, and explicit versioned index migrations.
- **User Administration API:** `GET /users/{id}` and `POST /users` with `ADMIN` authorization, use-case isolation, input validation, and HTTP 200/201/400/401/403/404/409 contracts.
- **Development Bootstrap:** Local Quarkus dev mode and current unit/API tests run without requiring Docker or a database.
- **Authentication:** Profile-limited mock (`dev`/`test`); persisted MongoDB + BCrypt login in `prod`; RS256 JWT issuance/verification with matching external RSA keys, bounded access-token lifetime, and RBAC. Key rotation, refresh, revocation, and session controls remain planned.
- **Production Login Rate Limiting:** Shared MongoDB fixed-window quotas per normalized e-mail and network origin, HMAC-SHA256 pseudonymous keys, TTL cleanup, fail-closed admission and generic HTTP `429` / `Retry-After`.
- **Quality-First Direction:** Database-independent `gradle check` and an explicitly invoked MongoDB integration test; Detekt, coverage, CPD, Gitleaks, Opengrep, and Trivy remain planned gates.
- **Portable Delivery:** Validated Native Image for `linux/arm64` in a JVM-free OCI runtime. Automated Woodpecker delivery and native `linux/amd64` builds are planned for later phases.

---

## 🗺️ Project Roadmap

### ✅ Completed (validated checkpoints)

- [x] **Phase 2.15 — production login rate limiting:** admission before BCrypt/JWT, configurable independent identity/origin quotas, shared atomic MongoDB counters with a TTL index and externally supplied HMAC-SHA256 key, generic `429` with `Retry-After`, and fail-closed store failures. Private Docker/MongoDB HTTP E2E validated both negative attempts and a real bootstrapped ADMIN login (200), JWT RS256 and RBAC, invalid password (401), identity/origin blocking (429), HMAC/TTL counter shape and audited cleanup of both disposable databases. `clean check`, `quarkusBuild` and whitespace validation passed on **2026-11-16**; tagged MongoDB JUnit tests remain a separate task.
- [x] Implemented and validated a one-time first-ADMIN CLI: explicit database confirmation, hidden password input, `prod` profile, HTTP-disabled command-mode artifact, and permanent MongoDB `_id` reservation with journal-acknowledged credential storage. **150/150** standard tests and **7/7** dedicated MongoDB bootstrap integration tests passed. Separate disposable-container E2E validated creation of the ADMIN, rejection of a second attempt without data changes, persisted ADMIN login (200), RS256 JWT signature and ADMIN authorization (200), rejected missing token and bad password (401), and audited database cleanup.
- [x] Implemented real `prod` `POST /auth/login`: normalized e-mail lookup, persisted BCrypt credential verification, RS256 signing (`sub` UUID / `groups` roles), external PKCS#8 private/public PEM matching, CDI startup validation and generic 401 for invalid credentials. `dev`/`test` retain their isolated mock.
- [x] Validated production login with **119/119** standard tests, a successful `quarkusBuild`, startup smoke tests for valid/missing/mismatched keys, and a disposable MongoDB HTTP E2E run with signed tokens and USER/ADMIN RBAC checks. Test credentials, keys, database, and staging files were cleaned up.
- [x] Validated the existing `POST /users`, `PUT /users/{id}/credentials`, `GET /users/{id}`, and production `POST /auth/login` in an isolated MongoDB HTTP E2E run, including startup, 401/403/409 responses, JWT signature/claims, and cleanup (at the time, the ADMIN token was test-generated; persisted ADMIN login was covered later in phase 2.14).
- [x] Implemented ADMIN-only initial credential provisioning through `PUT /users/{id}/credentials`, with 5 application tests, 8 HTTP tests, and 5 signed RS256 JWT HTTP tests. The **phase 2.12 baseline** passed 75 standard tests.
- [x] Implemented BCrypt password hashing (cost 12, five tests), separate credential domain (three tests), MongoDB mapping (five tests), and credential repository (four MongoDB 8.0.32 integration tests). Validated V002 migration, idempotency, uniqueness, and negative scenarios using disposable test databases.
- [x] Kotlin / Gradle Kotlin DSL / Quarkus project bootstrap and feature-first structure.
- [x] Quarkus REST, Jackson, CDI, Bean Validation, Swagger/OpenAPI, Health, and `GET /status`.
- [x] `POST /auth/login` as a **development/test-only mock**, with a standardized HTTP 401 response for invalid credentials.
- [x] Local `dev`/`test` profiles run database-independent code and tests without Docker.
- [x] Four unit tests (`LoginUseCaseTest`) and four HTTP tests (`AuthResourceTest`): **8 passed, 0 failures**.
- [x] `clean check` and JVM packaging validated after upgrades to **Gradle 9.8.1**, **Quarkus 3.40.1 LTS**, **Kotlin 2.4.10**, and **Java 21**.
- [x] Java 21 bytecode verified (`major version: 65`); toolchain upgrade pushed in commit `8d5fa0b`.
- [x] Mock login returned HTTP 404 in a local `prod` JVM smoke test after the MongoDB persistence migration.
- [x] Replaced legacy SQL dependencies with **MongoDB Panache Kotlin** and implemented `User`, `UserRepository`, document mapping, and a repository adapter.
- [x] Normalized user e-mail addresses and validated `db/migrations/V001__users_email_unique.js` against MongoDB 8.0, including idempotency and negative paths.
- [x] Validated `mongoIntegrationTest` against MongoDB 8.0: **5 JUnit tests, 0 failures**, covering creation and retrieval, missing UUID/e-mail lookups, normalization, unique-index duplicate rejection (`11000`), roles, and BSON identity.
- [x] Implemented and unit-tested `CreateUserUseCase`, `GetUserByIdUseCase`, and `GetUserByEmailUseCase`, with normalized e-mail validation and default `USER` assignment on creation.
- [x] Implemented `GET /users/{id}` and `POST /users`, protected by `@RolesAllowed("ADMIN")`; **11 HTTP tests** cover 200/201 success paths, 401/403 authorization, 404 missing users, 400 validation, and 409 duplicate e-mail handling.
- [x] Added `quarkus-smallrye-jwt-build` and validated **5 real RS256 JWT HTTP tests** without `@TestSecurity`: ADMIN 200, USER 403, wrong issuer 401, altered signature 401, and expired token 401. Signing/verification keys are automatically supplied in the test environment; this does not establish a production login flow.
- [x] Previously validated external RSA 3072 JWT verification in a `prod` JVM smoke test (before production login existed): `/status` 200, mock `/auth/login` 404, protected endpoint without JWT 401, valid USER JWT 403, and tampered JWT 401.
- [x] Generated a **Linux ARM64 Native Image** with Mandrel 25 via QEMU, then validated startup, `/status`, `/q/health`, and MongoDB connections in a **JVM-free UBI Micro** container.
- [x] Built the ARM64 Docker image with `Dockerfile.native-arm64`; verified the packaged binary matches the compiled executable by SHA-256.

### 🔜 Next Steps

- [ ] Establish managed JWT key provisioning and rotation per environment; formalize issuer/audience policies and validate deployed secrets management. RS256 signing with externally supplied files is already implemented.
- [ ] Implement credential recovery and a stronger password policy; the one-time first-ADMIN bootstrap and production login are now implemented. A consumed bootstrap reservation requires explicit administrative recovery if any later write fails.
- [ ] Establish secure production MongoDB configuration and extend persistence coverage as new use cases require it (including update/delete if added).
- [ ] Validate repository CRUD through the deployed Native Image and on the physical ARM64 target.
- [ ] Integrate **Detekt** (required), business coverage, CPD, Gitleaks, Opengrep, Trivy, and architecture checks; establish measured baselines and verify failing paths.
- [ ] Centralize template identity and introduce repeatable fork configuration (`--check` / `--apply`).
- [ ] Complete remaining authentication hardening: operational access-token expiry/audience policy, refresh-token rotation, revocation/logout, session controls, abuse monitoring, and additional negative security tests. Login rate limiting is implemented; issued tokens already have a configurable bounded TTL.
- [ ] Establish stable rate-limit HMAC secret provisioning/rotation across replicas and explicitly validate trusted-proxy client-IP configuration in the intended deployment topology.
- [ ] Implement self-hosted Woodpecker pipelines, target-platform OCI image publication, and deploy smoke tests.
- [ ] Harden readiness, logging, observability, and production configuration.
- [ ] Add native **Linux x86-64 (`linux/amd64`)** build and runtime validation in the final delivery phases; ARM64 remains the currently validated native target.

---

## 🛠️ Tech Stack

| Area | Technology | Status |
|---|---|---|
| Language & Build | **Kotlin 2.4.10**, **Gradle 9.8.1**, **Java 21 bytecode** | Validated |
| Framework & DI | **Quarkus 3.40.1 LTS**, REST, Jackson, CDI / Arc | Validated |
| Validation & Docs | Hibernate Validator, SmallRye OpenAPI / Swagger | Configured |
| Database | **MongoDB 8.0 + Panache Kotlin (Repository pattern)** | Adapter implemented; migration V001 and isolated integration validated |
| Authentication | Profile-scoped mock, persisted login, first-ADMIN CLI, BCrypt, SmallRye JWT Verify / Build, RBAC | RS256 issuance and external-key startup checks validated; one-time ADMIN bootstrap and persisted ADMIN login E2E PASS; managed key rotation/recovery pending |
| Login protection | MongoDB fixed-window rate limiting, atomic counters, TTL, HMAC-SHA256, HTTP 429 | Production HTTP negative/positive E2E PASS in isolated MongoDB; operational proxy/key management pending |
| Observability | SmallRye Health, JSON logging | Basic setup; hardening pending |
| Testing | JUnit 5, Quarkus Test, REST Assured, test-security | Phase 2.14: **150/150** standard tests and **7/7** bootstrap MongoDB tests PASS; phase 2.15: `clean check`, `quarkusBuild` and private HTTP E2E PASS; rate-limit MongoDB JUnit tests excluded from default `check` |
| Quality & Security | **Detekt required**; PMD CPD, Gitleaks, Opengrep, Trivy, coverage | Tool integration pending; Gradle `check` PASS |
| Native Image | Mandrel / GraalVM 25; Quarkus UBI9 Micro | Linux ARM64 build and JVM-free container tested; native x86-64 planned |
| CI/CD & Delivery | Woodpecker CI, Docker / OCI | ARM64 Dockerfile validated; automated CI, publication, deploy pending |

Version numbers are declared in the Gradle wrapper, plugins, and Quarkus platform properties; those files remain the source of truth for future forks.

---

## 🏗️ Project Architecture

The backend is a **modular monolith**: each feature owns its HTTP contracts, use cases, domain, and infrastructure adapters.

```text
src/main/kotlin/br/com/brunno/
├── features/
│   ├── auth/
│   │   ├── api/             # Profile-specific login REST resources and DTOs
│   │   ├── cli/             # Isolated one-time first-ADMIN command entrypoint
│   │   ├── application/     # Credential verification, login, rate-limit admission, bootstrap
│   │   ├── domain/          # Credential domain and repository contract
│   │   └── infrastructure/  # BCrypt, MongoDB credentials/rate limiting, RSA/JWT
│   └── users/
│       ├── api/             # ADMIN-protected users and credential provisioning API
│       ├── application/     # Create and lookup use cases; validation errors
│       ├── domain/          # User, UserRole, UserRepository, normalization
│       └── infrastructure/  # MongoDB document, mapper, repository adapter
└── shared/
    ├── health/             # Shared operational endpoints
    └── error/              # HTTP error response and exception mapping

db/
├── migrations/
│   ├── V001__users_email_unique.js
│   └── V002__user_credentials.js
└── tests/
    ├── test_v001.sh
    └── test_v002.sh

src/main/docker/
└── Dockerfile.native-arm64
```

### 📏 Architecture Guidelines

- **API** handles HTTP, DTOs, validation, and mapping; business rules belong elsewhere.
- **Application** orchestrates use cases using explicit contracts.
- **Domain** must not depend on Quarkus, HTTP, persistence, or infrastructure details.
- **Infrastructure** implements technical contracts, including repositories and token services.
- **Shared** contains genuinely cross-cutting code, not unrelated feature business logic.
- Cross-feature interactions should use explicit public contracts rather than importing another feature's internals.

The `users` feature implements application use cases and ADMIN-protected `GET /users/{id}`, `POST /users`, and `PUT /users/{id}/credentials`. The user API is enabled when `app.persistence.mongodb.enabled=true`; regular development defaults do not expose it without that configuration. The isolated HTTP test profile enables the API with an alternative repository, without MongoDB. `POST /auth/login` is a non-functional mock in `dev`/`test` and a separate persisted-credential/JWT resource in `prod`. Other routes include `/status`. Update/delete user endpoints are not implemented.

Architecture enforcement is a **planned quality gate**, not an existing check.

---

## 🧩 Template Configuration & Reuse

This repository is a **starter**, not a backend for a single product. When creating a fork, review:

```text
service name / application name
Gradle group and artifact identity
Kotlin base package
OpenAPI title and version
database name and connection settings
JWT issuer, audience, and key management
allowed origins / trusted proxies
container image and artifact naming
CI secrets and deployment environment
```

A centralized, versioned **non-secret identity contract**, together with an idempotent check/apply tool, is planned. It has **not** been implemented yet. Never commit passwords, signing keys, or production tokens.

---

## 🔐 Security & Environments

- **Initial credential provisioning:** `PUT /users/{id}/credentials` requires the ADMIN role, accepts a password without returning it, and returns HTTP `204` on success. Invalid passwords return `400`, unauthenticated requests `401`, insufficient roles `403`, missing users `404`, and existing credentials `409`. The operation does not replace an existing password. Current hashing validation requires nonblank input of at most 72 UTF-8 bytes; a complete password policy is still pending.
- **Credential storage:** Passwords are hashed using BCrypt (cost 12), with rejection of invalid inputs and hashes. Credentials are stored separately in `user_credentials`, indexed by the user UUID as MongoDB `_id`. Password hashes are excluded from the `User` domain and API responses. ADMIN provisioning, first-ADMIN bootstrap, and production login have been validated in isolated MongoDB runs; password recovery remains pending.
- **Development and tests:** The ordinary `dev`/`test` configuration avoids an external MongoDB dependency. `users` HTTP tests use `UsersApiTestProfile` to enable the resource and inject an alternative test repository; `@TestSecurity` covers simulated identities, while separate JWT HTTP tests authenticate real signed Bearer tokens.
- **Production:** Inject MongoDB connection settings securely at runtime using `MONGODB_URI` and `MONGODB_DATABASE` (the configured property expressions). Disposable MongoDB 8.0.32 HTTP E2E validation passed; real deployment, network restrictions, backup, and secret provisioning have not been validated.
- **Mock login:** The `dev`/`test` `/auth/login` uses fixed development credentials and returns a non-functional example token. It is excluded from `prod`, where a distinct `/auth/login` resource authenticates persisted credentials and issues RS256 JWTs. The earlier `prod` HTTP 404 result applies only to the pre-2.13 build. **Never treat the mock as real authentication.**
- **JWT and RBAC:** `quarkus-smallrye-jwt` and `quarkus-smallrye-jwt-build` are installed. RS256 issuer and verifier use externally supplied matching PEM keys in `prod`; startup rejects missing or mismatched private keys. Claims include UUID `sub`, e-mail `upn`, `groups`, `iss`, `iat`, and `exp`. Five original signed-JWT HTTP tests and the production E2E validate verification and role enforcement. Managed key provisioning/rotation remains pending.
- **Remaining security work:** Managed JWT/HMAC key deployment and rotation, password recovery and policy, refresh/revocation, session handling, CORS/trusted-proxy policy, abuse monitoring, bootstrap incident-recovery procedure, and production deployment hardening.
- Secrets must come from environment injection or a secret manager, never source control or logs.

### First ADMIN bootstrap (separate administrative artifact)

The initial administrator is created **only via an interactive command**, never an HTTP endpoint. Its `@QuarkusMain(name = "bootstrap-admin")` entrypoint must be selected **at build time**, separately from the normal `build/quarkus-app/` API. This functionality is not intended as a public registration flow.

**Preconditions:** provision a restricted MongoDB database and verify migrations V001/V002 before first use; verify there is no existing ADMIN or `admin_bootstrap` reservation; arrange maintenance access, backups and externally managed matching RSA keys; restrict the administrative runtime to the MongoDB network, with **no published HTTP ports**. Do not invoke bootstrap on an unfamiliar or previously used database.

Example build on Linux/macOS from the repository root (use equivalent process environment variables on PowerShell):

```bash
QUARKUS_PACKAGE_MAIN_CLASS=bootstrap-admin \
QUARKUS_PACKAGE_OUTPUT_DIRECTORY=bootstrap-admin \
QUARKUS_HTTP_HOST_ENABLED=false \
./gradlew quarkusBuild --rerun-tasks --no-daemon --console=plain
```

The complete fast-jar package is `build/bootstrap-admin/` (including `quarkus-run.jar`, `app/`, `lib/`, and `quarkus/`). Keep it separate from the normal API package at `build/quarkus-app/`. Build-time selection does not replace **runtime** HTTP disablement.

At runtime, supply `QUARKUS_PROFILE=prod`, `QUARKUS_HTTP_HOST_ENABLED=false`, `MONGODB_URI`, `MONGODB_DATABASE`, `JWT_ISSUER`, `JWT_PUBLIC_KEY_LOCATION`, and `APP_AUTH_JWT_PRIVATE_KEY_PATH` through a protected environment or secret manager. The matching RSA keys must already be present: `prod` startup validates them. Run with a real system console (for example an interactive, network-restricted container), **not** piped stdin. Do not pass passwords in CLI arguments, shell history, logs, or environment variables. No HTTP ports should be published.

The command displays the database name and an exact `bootstrap <database-name>` confirmation line **without quotation marks**. Only then does it request administrator name, e-mail, password and password confirmation. It returns **0** on success and **2** on cancellation or denial; it prints the created UUID, but no password or token.

For each MongoDB database, `admin_bootstrap` receives a permanent `_id = "initial-admin"` reservation with journal acknowledgement. The command checks for an existing ADMIN, writes the BCrypt credential with journal acknowledgement, and only then persists the ADMIN user document. **It does not use a multi-document transaction.** If a failure happens after the reservation, the reservation intentionally remains consumed even if the account is incomplete. **Never retry blindly, delete the reservation automatically, or reset it to repeat bootstrap**; preserve evidence and perform an explicit, backup-aware manual recovery review. The current implementation does not provide a recovery command.

**Validation status:** the default Quarkus API package and separate admin fast-jar were both built, with no directory overlap. An isolated MongoDB 8.0 + Java 21 E2E verified initial ADMIN creation, BCrypt cost 12, second-attempt denial with unchanged documents, real `POST /auth/login` (200), RS256 signature/claims, ADMIN access (200), missing token (401), wrong password (401), and guarded cleanup of the disposable database. The administrative command has **not** been deployed to a real production environment; operational controls and production rollout require separate review.

### Production login and JWT key configuration

The `prod` resource accepts `POST /auth/login` with `email` and `password`, reserves rate-limit capacity **before** checking persisted BCrypt credentials or issuing JWTs, and returns `accessToken`, `tokenType` (`Bearer`), `expiresIn` (seconds), and `user` (`id` as UUID string, `name`, `email`, `roles`). Invalid credentials return **401**, exceeded quotas return **429**, and request validation failures return **400**. No password hash is returned. The `dev`/`test` mock is a separate build-profile resource.

| Runtime setting | Source / behavior |
|---|---|
| `MONGODB_URI` | Required by `%prod.quarkus.mongodb.connection-string` |
| `MONGODB_DATABASE` | MongoDB database name; explicitly set for production (a local default exists) |
| `JWT_PUBLIC_KEY_LOCATION` | Absolute file path or `file:` URI for the X.509 `BEGIN PUBLIC KEY` PEM; used by `mp.jwt.verify.publickey.location` |
| `JWT_ISSUER` | Required issuer in `prod`, shared by JWT signing and verification |
| `APP_AUTH_JWT_PRIVATE_KEY_PATH` | Quarkus environment mapping for `app.auth.jwt.private-key-path`; absolute path or `file:` URI to an **unencrypted PKCS#8** RSA `BEGIN PRIVATE KEY` PEM |
| `APP_AUTH_JWT_ACCESS_TOKEN_TTL_SECONDS` | Optional mapping for `app.auth.jwt.access-token-ttl-seconds`; defaults to **900**, valid range **1–3600** seconds |

Keys must be RSA **2048 bits or stronger**, stored **outside the repository** with access restricted to the runtime identity. The producer uses `@Startup` to load and validate the signing key against the public verification key before serving requests: missing, invalid, or mismatched keys fail the production startup. There is no embedded key or development signing fallback. Token rotation, secret distribution, and an audience policy are **not implemented**. A new key requires coordinating the signing and verification sides; do not replace active keys without a rotation plan.

Example response shape (illustrative values, not an actual credential):

```json
{
  "accessToken": "<signed-rs256-jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "<uuid>",
    "name": "Example User",
    "email": "user@example.com",
    "roles": ["USER"]
  }
}
```

Do not log passwords, JWTs, or private key material. Even with login throttling implemented, do not expose `prod` publicly without a separate deployment security review, TLS, correct proxy/IP handling, and operational secret controls.

### Production login rate limiting (phase 2.15)

The production-only `LoginRateLimitAdmissionService` runs before BCrypt verification and JWT signing. A shared MongoDB collection, `login_rate_limit_windows`, tracks **fixed UTC time windows** with atomic per-bucket `findOneAndUpdate` operations. Origin quota is reserved **first**, then the normalized e-mail identity quota; a rejected identity request still consumes its origin attempt. Requests count regardless of valid/invalid password, and counters are shared between application replicas using the same MongoDB database and HMAC key. This limiter is not active in the `dev`/`test` mock or the separate offline first-ADMIN bootstrap command.

| Production property | Environment variable | Default / constraint |
|---|---|---|
| `app.auth.rate-limit.hmac-key-base64` | `APP_AUTH_RATE_LIMIT_HMAC_KEY_BASE64` | **Required** for production HTTP login; no default. Standard Base64 encoding of **32–128 random bytes**. |
| `app.auth.rate-limit.identity.max-attempts` | `APP_AUTH_RATE_LIMIT_IDENTITY_MAX_ATTEMPTS` | `5`; positive integer. |
| `app.auth.rate-limit.identity.window-seconds` | `APP_AUTH_RATE_LIMIT_IDENTITY_WINDOW_SECONDS` | `300`; range `1–86400`. |
| `app.auth.rate-limit.origin.max-attempts` | `APP_AUTH_RATE_LIMIT_ORIGIN_MAX_ATTEMPTS` | `30`; positive integer. |
| `app.auth.rate-limit.origin.window-seconds` | `APP_AUTH_RATE_LIMIT_ORIGIN_WINDOW_SECONDS` | `60`; range `1–86400`. |

The HMAC key is **not** the JWT private key. Generate and provision it through a secret manager or protected runtime environment, never the repository, CLI arguments, test logs or documentation examples containing real values. Keep it **stable across restarts and replicas**: replacing the key creates different counter identifiers and effectively starts a new set of quotas. A deliberate rotation requires an operational plan. A missing or invalid key cannot fall back to an in-memory/unprotected limiter when the production login limiter is resolved.

MongoDB stores only pseudonymous `_id` keys (`v1:<dimension>:<bucket>:<HMAC-SHA256>`), attempt counts and `expireAt`; it does not persist the raw e-mail or IP in the counter identifiers. The TTL index on `expireAt` (`expireAfterSeconds: 0`) permits asynchronous MongoDB cleanup after each fixed window ends; **TTL deletion is not immediate or a guarantee of exact deletion at the boundary**. An over-quota request receives a generic JSON **HTTP 429** response with a positive, numeric `Retry-After` header (seconds remaining in that window); it does not receive a JWT. MongoDB/index/unknown write failures propagate and prevent an unthrottled successful login rather than silently admitting the request.

The origin address comes from the Vert.x request's **direct network peer** (`remoteAddress().hostAddress()`); the login resource does **not** parse user-provided `Forwarded` or `X-Forwarded-For` headers. **Reverse-proxy deployment requires explicit review and configuration of Quarkus trusted-proxy handling**: if every request appears to come from a proxy IP, they will share that origin quota. Do not enable trust in arbitrary forwarding headers to work around this behavior.

**Operational validation (2026-11-16):** isolated Java 21 + MongoDB 8.0 Docker runs, no published ports, validated incorrect/unknown credentials (`401`), identity and shared-origin quotas (`429`), `Retry-After`, forwarded-header spoofing resistance, correct persisted ADMIN login (`200`), BCrypt, JWT RS256 signature/claims, protected endpoint RBAC, HMAC counter shape and TTL index. Both dedicated test databases were removed only after ownership checks, explicit confirmation and verified cleanup. These are **laboratory E2E checks**, not a production deployment or a load/availability assessment. The positive run reused the previously validated **phase 2.14 bootstrap CLI** and the current **phase 2.15 API JVM artifact**; it does not constitute a fresh bootstrap CLI build validation with the phase 2.15 code.

---

## 🚀 Running Locally

### Prerequisites

- **JDK 21** for compilation and execution (the Gradle wrapper selects a compatible toolchain).
- No local Docker or MongoDB installation is needed for the current **database-independent development and test flows**.

### 💻 Development (Live Reload)

Linux / macOS:

```bash
./gradlew quarkusDev
```

Windows / PowerShell:

```powershell
.\gradlew.bat quarkusDev
```

Endpoints to verify:

```text
GET  http://localhost:8080/status
GET  http://localhost:8080/q/health
     http://localhost:8080/q/swagger-ui
POST http://localhost:8080/auth/login   (dev/test mock; prod persisted credentials + RS256 JWT)
```

`quarkusDev` runs continuously, so the Gradle task remains active until stopped.

---

## 🧪 Testing & Quality

### Unit Tests

JUnit 5 tests cover the `LoginUseCase` directly, without HTTP, CDI, Docker, or a database:

- Valid credentials and authenticated user data.
- E-mail normalization.
- Invalid password and unknown e-mail rejection.

```bash
./gradlew test
```

```powershell
.\gradlew.bat clean check --console=plain
```

**Validated original baseline: 4 unit tests passing.** Additional tests cover persisted credential verification, redaction, login orchestration, RS256 token issuance, external-key loading, REST response mapping and rate-limit contracts/settings/admission/HMAC/mapping. The **phase 2.14 database-independent `clean check` passed 150/150 tests**. The expanded **phase 2.15 `clean check` and `quarkusBuild` passed on 2026-11-16**; that console run did not report a per-test count.

### API & Integration Tests

`@QuarkusTest` and REST Assured exercise the mock-login HTTP contract, including successful login, invalid credentials (401), invalid e-mail (400), and blank password (400). The administrative `users` API adds **11 HTTP scenarios** covering `GET /users/{id}` and `POST /users`: 200/201 success, 401 unauthenticated, 403 unauthorized, 404 missing user, 400 invalid input, and 409 duplicate e-mail. These use an alternative test repository and do not connect to MongoDB.

A separate **5-test RS256 JWT HTTP suite** (`UserResourceJwtTest`) sends actual signed Bearer tokens through the Quarkus verifier and RBAC layer, without `@TestSecurity`: ADMIN accepted (200), USER forbidden (403), wrong issuer (401), altered signature (401), and expired token (401). These tests use generated keys. The later `prod` startup smoke tests validate real external key loading, fail-closed startup, and the production login route; a separate MongoDB E2E run validates the real persisted USER login.

```powershell
.\gradlew.bat test --tests "*UserResourceJwtTest" --console=plain
```

**Validated original baseline: 4 HTTP tests passing; 8/8 baseline overall.** These are historical milestones. The phase 2.14 standard suite contained **150 tests**. The phase 2.15 standard suite includes new rate-limit unit/profile/mapper tests; `test` / `check` still exclude tagged MongoDB integration tests and require no local Docker or database.

### Production HTTP end-to-end smoke test (separate from JUnit)

An isolated **MongoDB 8.0.32 + Java 21 JVM** run validated the production resource with a temporary database named `api_quarkus_it_auth_*`, matching generated RSA PEM files and network-local HTTP. It checked ADMIN-authorized user creation and initial credential provisioning (201/204), duplicate provisioning (409), persisted USER login (200), RS256 signature and claims, USER rejection on an ADMIN route (403), ADMIN acceptance (200), missing token (401), wrong password / unknown user (401), and invalid e-mail (400).

In the earlier phase 2.13 E2E, the ADMIN Bearer token used for setup was generated by the test, not obtained through an ADMIN credential login. The later phase 2.14 bootstrap E2E independently verified **persisted ADMIN login** and ADMIN JWT authorization with a disposable MongoDB database. In phase 2.15, separate negative and positive private-network E2E runs validated production MongoDB rate limiting, `401`/`429`, `Retry-After`, real ADMIN JWT/RBAC and guarded disposable database cleanup. These runs are **not** part of Gradle's default `check` task and do not certify managed key rotation or a public deployment. Test databases and temporary keys were cleaned up as applicable.

### MongoDB Integration Tests

The phase 2.15 rate limiter adds a separate tagged `MongoLoginAttemptRateLimiterIntegrationTest` suite for fixed-window quotas, concurrency, shared counters, TTL and error paths. Its presence in the code does **not** mean it was executed by the latest standard `clean check` or the private HTTP E2E harness. The bootstrap adds **seven additional dedicated MongoDB integration tests** (six reservation/edge-case tests plus one CDI/repository login test). The credential repository adds four MongoDB integration tests covering creation, UUID lookup, BCrypt hash preservation, and duplicate-key rejection (`11000`). Migration `V002__user_credentials.js` and its destructive test harness `db/tests/test_v002.sh` were validated against MongoDB 8.0.32 using isolated disposable databases. The original user repository suite contains five additional MongoDB integration tests; both suites remain separate from the default database-independent `clean check`.

The dedicated `mongoIntegrationTest` task was **validated against an isolated MongoDB 8.0 instance** (**5 JUnit tests, 0 failures, 0 errors, 0 skipped**). Coverage includes user creation and retrieval, missing UUID/e-mail lookups returning `null`, normalized e-mail persistence and BSON `_id`, duplicate e-mails rejected by the unique MongoDB index (`11000`), and duplicates after case/whitespace normalization. Tests clean up only the documents they create. Database names must start with `api_quarkus_it_`; use restricted credentials and an isolated environment.

```bash
# Run against a dedicated database with migration V001 already applied.
export MONGODB_URI='mongodb://localhost:27017'
export MONGODB_DATABASE='api_quarkus_it_users'
./gradlew mongoIntegrationTest --console=plain
```

User e-mails are normalized (trim + `Locale.ROOT` lowercase). The migration `db/migrations/V001__users_email_unique.js` creates a unique `ux_users_email` index on the `users` collection and records its application in `schema_migrations`. The migration and its explicit destructive-test harness (`db/tests/test_v001.sh`) were validated with idempotency and negative cases. **Never run the destructive test harness on production data.**

### Coverage & Quality Gates

The intended toolchain is **Detekt** (required for Kotlin), **PMD CPD** (duplication), **Gitleaks** (secrets and Git history), **Opengrep** (SAST), **Trivy** (dependencies and images), and a coverage reporter (JaCoCo or Kover, to be selected). The database-independent Gradle `check`, bootstrap's seven-test MongoDB integration suite, and earlier repository integration suites have passed; scanner and coverage integration is pending.

Quality policy:

```text
measure → review findings → fix real issues → establish baseline
        → validate positive and negative paths → block regressions
```

Coverage and duplication thresholds will be set from real measurements, not arbitrary percentages. Detekt and external security/coverage gates are **not** marked as implemented yet.

---

## 🧱 Native Image and Linux ARM64

Native Image is a **required delivery path for Linux ARM64**, not an optional experiment. Building on an AMD64 host does not automatically produce an ARM64 executable: the target architecture requires an ARM64-compatible Mandrel/GraalVM compiler. The validated workflow used an ARM64 Mandrel **25** container under QEMU/binfmt on an AMD64 build host, with Gradle/Kotlin preparation on **JDK 21 AMD64**. Runtime hosts do **not** install Java, Gradle, or Mandrel.

### A. Generate native-image inputs with Java 21

Run Gradle in an environment with a Java 21 toolchain:

```bash
./gradlew quarkusBuild \
  -Dquarkus.package.jar.enabled=false \
  -Dquarkus.native.enabled=true \
  -Dquarkus.native.sources-only=true \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 \
  --console=plain
```

This prepares `build/native-sources/`, including `native-image.args`, the intermediate JAR, dependencies, and compiler metadata. The native-only invocation disables additional JAR packaging; normal JVM development and tests remain available.

### B. Compile for Linux ARM64 using Mandrel 25

With a working `linux/arm64` QEMU/binfmt handler (or on an ARM64 build host):

```bash
cd build/native-sources

docker run --rm --platform linux/arm64 \
  --cpus=4 --memory=5g --memory-swap=6g \
  --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$PWD:/work" -w /work \
  --entrypoint /bin/bash \
  quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 \
  -lc 'native-image $(cat native-image.args) -J-Xmx3g --parallelism=2'
```

The invocation above was validated for this project's generated arguments. Scripts that must handle paths containing spaces should parse the generated argument file robustly. Verify the ELF architecture with `readelf -h <runner-binary>` (`Machine: AArch64`).

**Reference measurement:** Native Image compilation took **25 min 32 s** under ARM64 emulation (approximately 25 min 50 s end-to-end), with **3.22 GB peak RSS** reported by Mandrel. This is not a guaranteed build time or memory requirement for future releases.

### C. Package and run the JVM-free ARM64 image

`src/main/docker/Dockerfile.native-arm64` uses `quay.io/quarkus/ubi9-quarkus-micro-image:2.0`, which does **not** require a JDK or JRE to run the native executable.

```bash
# From the repository root after the Native Image build:
docker buildx build \
  --platform linux/arm64 --load \
  -f src/main/docker/Dockerfile.native-arm64 \
  -t api-quarkus:native-arm64 \
  build/native-sources

docker image inspect api-quarkus:native-arm64 \
  --format 'OS={{.Os}} ARCH={{.Architecture}}'
```

The packaged ARM64 image started in a minimal UBI Micro container and returned `UP` from `GET /status` and `GET /q/health`, including synchronous and reactive MongoDB checks against an isolated MongoDB 8.0 instance. The packaged executable matched the original binary by SHA-256. These are **runtime smoke tests**, not full native CRUD tests or a production-host validation.

**Planned native x86-64:** A dedicated **Linux x86-64 (`linux/amd64`) Native Image build and runtime target** is part of the longer-term release roadmap. It has **not been implemented or tested**; compiler selection, build validation, and image publication will be addressed in the final project phases, without changing the currently validated ARM64 path.

**Pending for ARM64:** native CRUD behavior, execution on physical ARM64 hardware, load tests, automated release publication, and production deployment.

---

## ⚙️ CI/CD & Containerization

Woodpecker CI is the intended self-hosted pipeline orchestrator:

```text
Push / Pull Request / Tag
          │
          ▼
Dedicated build runner
  ├── Compile / test / coverage
  ├── Quality and security scans
  ├── Prepare Java 21 artifacts for Native Image
  ├── Compile linux/arm64 with Mandrel 25
  └── Build and publish target-platform OCI image
          │
          ▼
Target runtime host / container platform
```

Compilation and scanning must run on **build runners**, not production runtime nodes. The primary ARM64 deployment model uses a native executable with **no JVM/JRE on the runtime host**. The ARM64 Dockerfile is validated; automated pipelines, image publication, native x86-64 builds, and physical-host deployment remain pending.

---

## 📐 Project Guidelines

- Keep feature boundaries explicit and verify them through tests or static analysis.
- Prefer constructor injection and domain-independent interfaces.
- Keep business logic testable without an HTTP server or database.
- Never ship development credentials, fake authentication, or debug exceptions to production.
- Avoid artificial test coverage, unnecessary abstractions, and broad lint suppressions.
- Keep product identity and environment configuration centralized for forks.
- Separate build-time operations from lightweight runtime deployment.

---

## 📚 Related Projects

- [`Brunno13/api-bun`](https://github.com/Brunno13/api-bun) — Bun / Elysia backend and architectural reference.
- [`Brunno13/app-kmp`](https://github.com/Brunno13/app-kmp) — Kotlin Multiplatform reusable base app.
- [`Brunno13/app-react-native`](https://github.com/Brunno13/app-react-native) — React Native / Expo reusable base app.
- [`Brunno13/app-flutter`](https://github.com/Brunno13/app-flutter) — Flutter reusable base app.

---

This repository is a reusable backend foundation. The users REST API, persisted production login, external-key RS256 issuance/verification, and **phase 2.15 MongoDB-backed login rate limiting** passed the latest database-independent build gates and private HTTP E2E checks. The phase 2.14 baseline of **150 standard tests** remains historical; tagged MongoDB JUnit integration tests are separate. The earlier ARM64 Native Image smoke test remains a foundation milestone; native rebuild with updated authentication, native x86-64 delivery, managed secrets/rotation, automated security gates, and release/deploy automation remain future work.
