# ⚡ API Quarkus (Kotlin)

Reusable **Kotlin + Quarkus REST API starter** for mobile and web backends. Designed to be forked into independent production projects, with a feature-first modular monolith, clear architecture boundaries, environment-based configuration, and an incremental quality and security strategy.

> **Current stage:** Early foundation. The authentication endpoint currently uses a **development mock**, not production authentication. Database integration, real JWT/RBAC, automated tests, security gates, and CI/CD are planned, not yet validated.

---

## 🚀 Key Features

- **Reusable Backend Foundation:** Start a new API from a fork without coupling business code to a specific product or hosting environment.
- **Modular Architecture:** Feature-first organization with pragmatic Clean Architecture (`api`, `application`, `domain`, `infrastructure`).
- **REST & API Documentation:** Quarkus REST, Jackson, Bean Validation, OpenAPI / Swagger UI.
- **Dependency Injection:** CDI / Arc with constructor-based dependencies.
- **Development Bootstrap:** Local Quarkus dev mode without requiring Docker or MariaDB for database-independent endpoints.
- **Authentication Foundation:** Mock login endpoint for architectural prototyping; secure JWT / RBAC remains on the roadmap.
- **Quality-First Direction:** Unit and integration tests, business coverage, Detekt, CPD, Gitleaks, Opengrep, and Trivy.
- **Portable Delivery:** Woodpecker CI and OCI containers, with builds separated from runtime hosts; target architectures are validated per release.

---

## 🗺️ Project Roadmap

### ✅ Completed (reported in the repository inventory)

- [x] Kotlin / Gradle Kotlin DSL / Quarkus project bootstrap.
- [x] REST, Jackson, CDI, validation, OpenAPI / Swagger, and health extensions configured.
- [x] `GET /status` endpoint and basic `/q/health` response.
- [x] Feature-first folder structure for `auth` and shared code.
- [x] `POST /auth/login` mock implementation committed (`c6813a7`); **not production-safe**.
- [x] Local development profile with database and Dev Services disabled.

### 🔜 Next Steps

- [ ] Audit current sources, build, application configuration, and API behavior.
- [ ] Centralize template identity and introduce repeatable fork configuration (`--check` / `--apply`).
- [ ] Define architecture boundaries, consistent errors, and production-safe environment handling.
- [ ] Add unit / API tests and a measured coverage baseline.
- [ ] Implement and validate Detekt, CPD, Gitleaks, Opengrep, Trivy, and architecture gates.
- [ ] Integrate MariaDB, Panache repositories, and Flyway migrations.
- [ ] Replace mock authentication with password hashing, signed JWT, RBAC, refresh tokens, and session controls.
- [ ] Build self-hosted Woodpecker pipelines and publish versioned OCI images.
- [ ] Harden readiness, logging, observability, and production configuration.
- [ ] Evaluate Native Image only after measuring JVM runtime and supporting target-architecture builds.

---

## 🛠️ Tech Stack

| Area | Technology | Status |
|---|---|---|
| Language & Build | Kotlin, Gradle Kotlin DSL, JDK 17 bytecode target | Configured |
| Framework & DI | Quarkus REST, Jackson, CDI / Arc | Configured |
| Validation & Docs | Hibernate Validator, SmallRye OpenAPI / Swagger | Configured |
| Database | MariaDB, Hibernate ORM Panache Kotlin, Flyway | Dependencies configured; integration pending |
| Authentication | SmallRye JWT, role-based authorization | Dependency configured; secure flow pending |
| Observability | SmallRye Health, JSON logging | Basic setup; hardening pending |
| Testing | JUnit / Quarkus Test, REST Assured | Dependencies configured; tests pending |
| Quality & Security | Detekt, PMD CPD, Gitleaks, Opengrep, Trivy | Planned |
| CI/CD & Delivery | Woodpecker CI, Docker / OCI | Planned |

Version numbers are defined by the Gradle project. Refer to its wrapper, plugins, and Quarkus platform properties for the authoritative versions.

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
    └── health/             # Shared operational endpoints
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

- **Development:** Database-dependent components are intentionally disabled in the current local bootstrap.
- **Production:** MariaDB credentials and deployment settings must be provided securely at runtime; production startup has not yet been validated.
- The current mock login uses example credentials and returns a fake token. **Do not expose it in production or treat its token as authentication.**
- Real JWT signing/verification, refresh-token lifecycle, RBAC, secure error handling, and CORS policy are future milestones.
- Secrets must come from environment injection or a secret manager, never source control or logs.

---

## 🚀 Running Locally

### Prerequisites

- JDK compatible with the Gradle wrapper and configured Quarkus version.
- No local Docker installation is needed for the current **database-independent dev mode**.

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
POST http://localhost:8080/auth/login   (mock only)
```

`quarkusDev` runs continuously, so the Gradle task remains active until stopped.

---

## 🧪 Testing & Quality

### Unit Tests

Planned: JUnit tests for domain rules, use cases, input edge cases, and error mapping, without starting MariaDB or relying on Docker.

```bash
./gradlew test
```

```powershell
.\gradlew.bat test
```

**Current inventory:** No files were found under `src/test`. The commands above are the standard Gradle test tasks, **not evidence of passing tests**. The test profile must be reviewed before relying on the command locally.

### API & Integration Tests

Planned: `@QuarkusTest` and REST Assured for HTTP status codes, request validation, error contracts, and authorization. MariaDB/Flyway integration tests will use an isolated database environment; they must not silently require Docker on every developer machine.

### Coverage & Quality Gates

The intended toolchain is **Detekt** (Kotlin), **PMD CPD** (duplication), **Gitleaks** (secrets and Git history), **Opengrep** (SAST), **Trivy** (dependencies and images), plus automated tests and a coverage reporter (JaCoCo or Kover, to be selected).

Quality policy:

```text
measure → review findings → fix real issues → establish baseline
        → validate positive and negative paths → block regressions
```

Coverage and duplication thresholds will be set from real measurements, not arbitrary percentages. None of these gates is marked as implemented yet.

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

This repository is intended to grow as a reusable, production-oriented backend foundation. Roadmap items only become completed once implementation and validation are confirmed in the repository.
