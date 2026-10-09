# ⚡ API Quarkus (Kotlin)

Reusable **Kotlin + Quarkus REST API starter** for mobile and web backends. Designed to be forked into independent production projects, with a feature-first modular monolith, clear architecture boundaries, environment-based configuration, and an incremental quality and security strategy.

> **Current stage:** Foundation validated on **Gradle 9.8.1**, **Quarkus 3.40.1 LTS**, **Kotlin 2.4.10**, and **Java 21** (class-file version 65). **Eight unit/API tests pass**, and the mock login was verified unavailable in a production-profile smoke test before the toolchain upgrade. **MongoDB**, real JWT/RBAC, Detekt/security gates, and CI/CD integration remain planned.

---

## 🚀 Key Features

- **Reusable Backend Foundation:** Start a new API from a fork without coupling business code to a specific product or hosting environment.
- **Modular Architecture:** Feature-first organization with pragmatic Clean Architecture (`api`, `application`, `domain`, `infrastructure`).
- **REST & API Documentation:** Quarkus REST, Jackson, Bean Validation, OpenAPI / Swagger UI.
- **Dependency Injection:** CDI / Arc with constructor-based dependencies.
- **Development Bootstrap:** Local Quarkus dev mode and current unit/API tests run without requiring Docker or a database.
- **Authentication Foundation:** Profile-limited mock login (`dev`/`test`), standardized HTTP 401 errors, and Bean Validation; secure JWT / RBAC remains on the roadmap.
- **Quality-First Direction:** Eight passing JUnit / REST Assured tests and `gradle check`; Detekt, coverage, CPD, Gitleaks, Opengrep, and Trivy are planned gates.
- **Portable Delivery:** Woodpecker CI and OCI containers, with builds separated from runtime hosts; target architectures are validated per release.

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

### 🔜 Next Steps

- [ ] Replace the unused MariaDB/JPA/Flyway dependencies and configuration with **MongoDB Panache Kotlin**, using repository adapters and domain-independent contracts.
- [ ] Add MongoDB integration tests, document/index evolution, and a secure production connection strategy; keep database-free tests available locally.
- [ ] Integrate **Detekt** (required), business coverage, CPD, Gitleaks, Opengrep, Trivy, and architecture checks; establish measured baselines and verify failing paths.
- [ ] Centralize template identity and introduce repeatable fork configuration (`--check` / `--apply`).
- [ ] Replace mock authentication with secure password hashing, signed JWT, RBAC, refresh tokens, and session controls.
- [ ] Implement self-hosted Woodpecker pipelines, target-platform OCI image publication, and deploy smoke tests.
- [ ] Harden readiness, logging, observability, and production configuration.
- [ ] Evaluate Native Image after measuring JVM runtime and validating target-architecture builds.

---

## 🛠️ Tech Stack

| Area | Technology | Status |
|---|---|---|
| Language & Build | **Kotlin 2.4.10**, **Gradle 9.8.1**, **Java 21 bytecode** | Validated |
| Framework & DI | **Quarkus 3.40.1 LTS**, REST, Jackson, CDI / Arc | Validated |
| Validation & Docs | Hibernate Validator, SmallRye OpenAPI / Swagger | Configured |
| Database | **MongoDB + Panache Kotlin (Repository pattern)** | Chosen; not yet integrated. Legacy SQL dependencies still present |
| Authentication | Profile-scoped mock, SmallRye JWT, RBAC | Mock and error contract validated; secure flow pending |
| Observability | SmallRye Health, JSON logging | Basic setup; hardening pending |
| Testing | JUnit 5, Quarkus Test, REST Assured | **8 tests PASS** (4 unit + 4 HTTP) |
| Quality & Security | **Detekt required**; PMD CPD, Gitleaks, Opengrep, Trivy, coverage | Tool integration pending; Gradle `check` PASS |
| CI/CD & Delivery | Woodpecker CI, Docker / OCI | Planned |

Version numbers are declared in the Gradle wrapper, plugins, and Quarkus platform properties; those files remain the source of truth for future forks.

---

## 🏗️ Project Architecture

The backend is a **modular monolith**: each feature owns its HTTP contracts, use cases, domain, and infrastructure adapters.

```text
src/main/kotlin/br/com/brunno/
├── features/
│   └── auth/
│       ├── api/             # REST resource and request/response DTOs
│       ├── application/     # Use cases and application contracts
│       ├── domain/          # Domain models and rules
│       └── infrastructure/  # Persistence, token providers, external adapters (planned)
└── shared/
    ├── health/             # Shared operational endpoints
    └── error/              # HTTP error response and exception mapping
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
- **Production:** MongoDB connection details and deployment settings will be injected securely at runtime **after the migration**; a production database has not been validated yet.
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

**Validated baseline: 4 unit tests passing.**

### API & Integration Tests

`@QuarkusTest` and REST Assured exercise the HTTP contract, including successful login, invalid credentials (401), invalid e-mail (400), and blank password (400).

**Validated baseline: 4 HTTP tests passing; 8/8 overall.** These tests use the `test` profile with database-dependent services disabled. MongoDB integration tests are a separate future phase and will require an isolated MongoDB instance.

### Coverage & Quality Gates

The intended toolchain is **Detekt** (required for Kotlin), **PMD CPD** (duplication), **Gitleaks** (secrets and Git history), **Opengrep** (SAST), **Trivy** (dependencies and images), and a coverage reporter (JaCoCo or Kover, to be selected). The existing Gradle `check` and eight tests are passing; scanner and coverage integration is pending.

Quality policy:

```text
measure → review findings → fix real issues → establish baseline
        → validate positive and negative paths → block regressions
```

Coverage and duplication thresholds will be set from real measurements, not arbitrary percentages. Detekt and external security/coverage gates are **not** marked as implemented yet.

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
  ├── Package JVM application
  └── Build and publish target-platform OCI image
          │
          ▼
Target runtime host / container platform
```

Compilation and scanning must run on **build runners**, not production runtime nodes. A JVM artifact may be packaged with the appropriate target-platform JRE. Native executables, unlike JVM bytecode, require an architecture-compatible native build process. The pipeline, image publication, and deploy are still pending.

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

This repository is a reusable backend foundation. Features are marked complete only when their implementation and validation are confirmed; the approved MongoDB migration and quality gates remain upcoming work.
