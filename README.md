# ⚡ API Quarkus (Kotlin)

Reusable **Kotlin + Quarkus REST API starter** for mobile and web backends. Designed to be forked into independent production projects, with a feature-first modular monolith, clear architecture boundaries, environment-based configuration, and an incremental quality and security strategy.

> **Current stage (October 2026):** Foundation validated on **Gradle 9.8.1**, **Quarkus 3.40.1 LTS**, **Kotlin 2.4.10**, and **Java 21** (class-file version 65). MongoDB Panache Kotlin, the `User` repository adapter, and the first versioned migration are implemented. Database-independent `clean check` and the dedicated **MongoDB 8.0 repository integration test** pass. A **Linux ARM64 Native Image** was compiled with **Mandrel 25** and validated in a JVM-free UBI Micro container. Real authentication, CI/CD publication, quality/security gates, and deployment to physical ARM64 hardware remain pending.

---

## 🚀 Key Features

- **Reusable Backend Foundation:** Start a new API from a fork without coupling business code to a specific product or hosting environment.
- **Modular Architecture:** Feature-first organization with pragmatic Clean Architecture (`api`, `application`, `domain`, `infrastructure`).
- **REST & API Documentation:** Quarkus REST, Jackson, Bean Validation, OpenAPI / Swagger UI.
- **Dependency Injection:** CDI / Arc with constructor-based dependencies.
- **Persistence Foundation:** MongoDB Panache Kotlin, domain repository contracts, normalized e-mail addresses, and explicit versioned index migrations.
- **Development Bootstrap:** Local Quarkus dev mode and current unit/API tests run without requiring Docker or a database.
- **Authentication Foundation:** Profile-limited mock login (`dev`/`test`), standardized HTTP 401 errors, and Bean Validation; secure JWT / RBAC remains on the roadmap.
- **Quality-First Direction:** Database-independent `gradle check` and an explicitly invoked MongoDB integration test; Detekt, coverage, CPD, Gitleaks, Opengrep, and Trivy remain planned gates.
- **Portable Delivery:** Validated Native Image for `linux/arm64` in a JVM-free OCI runtime. Automated Woodpecker delivery and native `linux/amd64` builds are planned for later phases.

---

## 🗺️ Project Roadmap

### ✅ Completed (validated checkpoints)

- [x] Kotlin / Gradle Kotlin DSL / Quarkus project bootstrap and feature-first structure.
- [x] Quarkus REST, Jackson, CDI, Bean Validation, Swagger/OpenAPI, Health, and `GET /status`.
- [x] `POST /auth/login` as a **development/test-only mock**, with a standardized HTTP 401 response for invalid credentials.
- [x] Local `dev`/`test` profiles run database-independent code and tests without Docker.
- [x] Four unit tests (`LoginUseCaseTest`) and four HTTP tests (`AuthResourceTest`): **8 passed, 0 failures**.
- [x] `clean check` and JVM packaging validated after upgrades to **Gradle 9.8.1**, **Quarkus 3.40.1 LTS**, **Kotlin 2.4.10**, and **Java 21**.
- [x] Java 21 bytecode verified (`major version: 65`); toolchain upgrade pushed in commit `8d5fa0b`.
- [x] Mock endpoint returned HTTP 404 in an earlier production-profile smoke test with the legacy database disabled; **revalidate after persistence migration**.
- [x] Replaced legacy SQL dependencies with **MongoDB Panache Kotlin** and implemented `User`, `UserRepository`, document mapping, and a repository adapter.
- [x] Normalized user e-mail addresses and validated `db/migrations/V001__users_email_unique.js` against MongoDB 8.0, including idempotency and negative paths.
- [x] Validated `mongoIntegrationTest` against MongoDB 8.0: **1 JUnit test, 0 failures**, covering creation, lookup by UUID/e-mail, normalization, roles, and BSON identity.
- [x] Generated a **Linux ARM64 Native Image** with Mandrel 25 via QEMU, then validated startup, `/status`, `/q/health`, and MongoDB connections in a **JVM-free UBI Micro** container.
- [x] Built the ARM64 Docker image with `Dockerfile.native-arm64`; verified the packaged binary matches the compiled executable by SHA-256.

### 🔜 Next Steps

- [ ] Expand MongoDB repository integration tests (missing users, duplicate e-mail, normalized duplicates, and failure paths) and establish secure production database configuration.
- [ ] Validate repository CRUD through the deployed Native Image and on the physical ARM64 target.
- [ ] Integrate **Detekt** (required), business coverage, CPD, Gitleaks, Opengrep, Trivy, and architecture checks; establish measured baselines and verify failing paths.
- [ ] Centralize template identity and introduce repeatable fork configuration (`--check` / `--apply`).
- [ ] Replace mock authentication with secure password hashing, signed JWT, RBAC, refresh tokens, and session controls.
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
| Authentication | Profile-scoped mock, SmallRye JWT, RBAC | Mock and error contract validated; secure flow pending |
| Observability | SmallRye Health, JSON logging | Basic setup; hardening pending |
| Testing | JUnit 5, Quarkus Test, REST Assured | Baseline 8 unit/HTTP tests; 1 MongoDB integration test PASS; database-free `check` PASS |
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
│   │   ├── api/             # REST resource and request/response DTOs
│   │   ├── application/     # Use cases and application contracts
│   │   └── ...
│   └── users/
│       ├── domain/          # User, UserRole, UserRepository, normalization
│       └── infrastructure/  # MongoDB document, mapper, repository adapter
└── shared/
    ├── health/             # Shared operational endpoints
    └── error/              # HTTP error response and exception mapping

db/
├── migrations/
│   └── V001__users_email_unique.js
└── tests/
    └── test_v001.sh

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

- **Development and tests:** The current database-independent endpoints run without an external database.
- **Production:** MongoDB connection settings must be injected securely at runtime (for example, `QUARKUS_MONGODB_CONNECTION_STRING` and `QUARKUS_MONGODB_DATABASE`). Only isolated MongoDB 8.0 tests have been validated; production database deployment remains pending.
- The mock login is included only in `dev` and `test` builds and returns a non-functional example token. Its absence was verified in a production-profile smoke test before the toolchain upgrade; **never treat it as real authentication**.
- Signed JWT, refresh-token lifecycle, RBAC, and CORS policy are future milestones; the current invalid-credentials path already returns a standardized HTTP 401.
- Secrets must come from environment injection or a secret manager, never source control or logs.

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
POST http://localhost:8080/auth/login   (dev/test mock only)
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

**Validated original baseline: 4 unit tests passing.** Additional user domain, mapper, and e-mail normalization tests have since been added; the full database-independent `clean check` passes.

### API & Integration Tests

`@QuarkusTest` and REST Assured exercise the HTTP contract, including successful login, invalid credentials (401), invalid e-mail (400), and blank password (400).

**Validated original baseline: 4 HTTP tests passing; 8/8 baseline overall.** The regular `test` / `check` tasks exclude MongoDB integration tests, so the development quality gate does not require Docker or a database.

### MongoDB Integration Tests

The dedicated `mongoIntegrationTest` task was **validated against an isolated MongoDB 8.0 instance** (1 JUnit test, 0 failures). The test exercises user creation, lookup by UUID and normalized e-mail, role mapping, BSON `_id`, and cleanup of its generated document. Database names must start with `api_quarkus_it_`; use restricted credentials and an isolated environment.

```bash
# Run against a dedicated database with migration V001 already applied.
export MONGODB_URI='mongodb://localhost:27017'
export MONGODB_DATABASE='api_quarkus_it_users'
./gradlew mongoIntegrationTest --console=plain
```

User e-mails are normalized (trim + `Locale.ROOT` lowercase). The migration `db/migrations/V001__users_email_unique.js` creates a unique `ux_users_email` index on the `users` collection and records its application in `schema_migrations`. The migration and its explicit destructive-test harness (`db/tests/test_v001.sh`) were validated with idempotency and negative cases. **Never run the destructive test harness on production data.**

### Coverage & Quality Gates

The intended toolchain is **Detekt** (required for Kotlin), **PMD CPD** (duplication), **Gitleaks** (secrets and Git history), **Opengrep** (SAST), **Trivy** (dependencies and images), and a coverage reporter (JaCoCo or Kover, to be selected). The database-independent Gradle `check` and the explicit MongoDB integration test have passed; scanner and coverage integration is pending.

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

This repository is a reusable backend foundation. Features are marked complete only when implementation and validation are confirmed. MongoDB repository integration and ARM64 native startup are validated; production authentication, native x86-64 delivery, automated quality gates, and release/deploy automation remain future work.
