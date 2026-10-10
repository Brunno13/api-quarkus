# ⚡ API Quarkus (Kotlin)

Reusable **Kotlin + Quarkus REST API starter** for mobile and web backends. The project is designed for independent forks, with a feature-first modular monolith, pragmatic Clean Architecture, explicit environment configuration, and incremental quality/security checks.

> **Project status (October 2026):** Gradle **9.8.1**, Quarkus **3.40.1 LTS**, Kotlin **2.4.10**, and Java **21** bytecode are validated. MongoDB Panache Kotlin, a `User` domain/repository adapter, and the first versioned unique-email index migration are implemented. A **Linux ARM64 Native Image** was successfully built using **Mandrel 25** and run in a minimal **JVM-free UBI Micro** container, with HTTP and MongoDB health checks passing. **MongoDB repository integration passed against MongoDB 8.0 (1 JUnit test, 0 failures)**, and the native image has not yet been tested on a physical ARM64 production host. Real authentication, automated quality/security gates, and CI/CD publication remain pending.

---

## 🚀 Features and Architecture

- **Reusable template:** Fork into a new backend without coupling code to a particular product or hosting provider.
- **Feature-first modular monolith:** `api`, `application`, `domain`, and `infrastructure` boundaries.
- **REST & API documentation:** Quarkus REST, Jackson, Jakarta Bean Validation, SmallRye OpenAPI / Swagger UI.
- **Dependency injection:** Quarkus CDI / Arc and constructor injection.
- **Persistence foundation:** MongoDB Panache Kotlin with domain-level repository contracts, UUID-based user IDs, BSON mapping, normalized e-mail addresses, and explicitly versioned indexes.
- **Developer-friendly tests:** Unit and database-independent HTTP tests do not require a local MongoDB or Docker daemon. Database integration uses a separate test task.
- **Native ARM64 delivery:** Native Image compiled with Mandrel/GraalVM **25** for `linux/arm64`, packaged into an OCI image with **no JVM/JDK at runtime**.
- **Security foundation:** Development/test-only mock login. **Real password authentication, JWT issuance, RBAC, refresh tokens, and sessions are not implemented yet.**

## 🗺️ Implementation status

| Area | Status |
|---|---|
| Kotlin, Gradle, Java 21, Quarkus | Validated |
| REST, validation, JSON, OpenAPI, CDI, health | Configured and exercised |
| User domain, `UserRepository`, MongoDB document/mapper/adapter | Implemented; isolated mapping tests validated |
| Database migration `V001__users_email_unique.js` | Tested against MongoDB 8.0, including idempotency and negative paths |
| Ordinary `clean check` without MongoDB | PASS on development machine and Linux builder |
| MongoDB repository integration (`mongoIntegrationTest`) | **PASS** against MongoDB 8.0 (1 JUnit test, 0 failures) |
| Linux ARM64 Native Image with Mandrel 25 | Built successfully and identified as AArch64 ELF |
| JVM-free UBI Micro ARM64 runtime | Startup, `/status`, `/q/health` and MongoDB connectivity PASS in emulation |
| `Dockerfile.native-arm64` container build | Built and started successfully; executable SHA-256 preserved |
| Physical ARM64 device and published deployment | Not yet validated |
| Real login/JWT/RBAC, Detekt/scanners, Woodpecker pipeline | Planned |

### Next milestones

1. Extend repository integration coverage against isolated MongoDB while keeping the default test suite independent of MongoDB.
2. Complete negative-path and repository/domain tests, including uniqueness and normalization behavior.
3. Integrate Detekt, coverage, duplication analysis, secrets/SAST/dependency/image scanning and meaningful quality gates.
4. Add repeatable project identity/rebranding for forks and implement production authentication.
5. Automate native build, image publication and release verification in CI; run a smoke test on the actual ARM64 target.

---

## 🛠️ Technology stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.4.10, Java 21 bytecode |
| Build | Gradle Wrapper 9.8.1, Gradle Kotlin DSL |
| Framework | Quarkus 3.40.1 LTS |
| HTTP and API | Quarkus REST, Jackson, Validator, OpenAPI |
| Persistence | MongoDB, Quarkus MongoDB Panache Kotlin |
| Tests | JUnit 5, `@QuarkusTest`, REST Assured, explicit MongoDB integration suite |
| Native compiler | Quarkus Native Image using Mandrel / GraalVM 25 |
| Container runtime | OCI `linux/arm64` image based on Quarkus UBI9 Micro |
| Future quality/CI | Detekt, coverage, CPD, Gitleaks, Opengrep, Trivy, Woodpecker CI |

The Gradle wrapper, plugins, and platform properties remain the authoritative source for installed tool versions.

## 🏗️ Code organization

```text
src/main/kotlin/br/com/brunno/
├── features/
│   ├── auth/
│   │   ├── api/
│   │   ├── application/
│   │   └── ...
│   └── users/
│       ├── domain/           # User, UserRole, UserRepository and e-mail rules
│       └── infrastructure/   # Mongo document, mapper and repository adapter
└── shared/                   # Cross-cutting HTTP / operational concerns

db/
├── migrations/
│   └── V001__users_email_unique.js
└── tests/
    └── test_v001.sh

src/main/docker/
└── Dockerfile.native-arm64
```

The domain layer is independent of HTTP, BSON/MongoDB and Quarkus. Infrastructure adapters implement domain contracts. Do not place feature-specific business logic in shared modules.

---

## 💻 Local development

**Prerequisite:** Java 21 toolchain (the Gradle wrapper and daemon criteria select the relevant JDK). The normal unit/API test flow requires neither Docker nor a database.

Linux / macOS:

```bash
./gradlew quarkusDev
./gradlew clean check --console=plain
```

Windows / PowerShell:

```powershell
.\gradlew.bat quarkusDev
.\gradlew.bat clean check --console=plain
```

Useful endpoints:

```text
GET  http://localhost:8080/status
GET  http://localhost:8080/q/health
     http://localhost:8080/q/swagger-ui
POST http://localhost:8080/auth/login  # dev/test mock only
```

The mock login must never be treated as production authentication. It is restricted to development/test profiles; verify that it remains absent from production builds after relevant changes.

### MongoDB configuration and migration

Production MongoDB connection values must be supplied securely at runtime, for example through `QUARKUS_MONGODB_CONNECTION_STRING` and `QUARKUS_MONGODB_DATABASE`. Do not commit real credentials, database URLs or secrets.

User e-mail addresses are normalized (trim + lowercase using `Locale.ROOT`) before persistence. The `users` collection has a unique index named `ux_users_email`, created by the versioned migration `db/migrations/V001__users_email_unique.js` with a `schema_migrations` record. Migration tooling is separate from application startup and should be run with appropriate database permissions before a release.

The migration and its destructive-test harness have passed against an isolated MongoDB 8.0 instance. The test harness requires explicit permission for dropping **dedicated test databases** and must never target production data. Review `db/tests/test_v001.sh` before running it.

### MongoDB repository integration suite

```bash
# Run only when an isolated MongoDB instance and migrated test database exist.
# The test profile rejects database names without the api_quarkus_it_ prefix.
export MONGODB_URI='mongodb://localhost:27017'
export MONGODB_DATABASE='api_quarkus_it_users'
./gradlew mongoIntegrationTest --console=plain
```

The integration task passed against MongoDB 8.0 (1 JUnit test, 0 failures). Integration tests are excluded from the ordinary `test` / `check` tasks to preserve local database independence.

---

## 🧱 Native Image and Linux ARM64

Native compilation is a **supported delivery target for this template**, not an optional investigation. It requires an architecture-compatible compiler: running a native-image builder on AMD64 normally produces an AMD64 binary. A Linux ARM64 build therefore uses an **ARM64 Mandrel 25 builder**, executed on ARM64 hardware or via an ARM64 emulation environment such as QEMU/binfmt.

The workflow below was validated on a Linux AMD64 build host with Docker Buildx, registered QEMU AArch64 emulation, and a separate JDK 21 container. **Build and native compilation run on the builder, never on the runtime device.**

### A. Generate native-image inputs with Java 21

Run Gradle under a JDK 21 environment:

```bash
./gradlew quarkusBuild \
  -Dquarkus.package.jar.enabled=false \
  -Dquarkus.native.enabled=true \
  -Dquarkus.native.sources-only=true \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 \
  --console=plain
```

Quarkus creates `build/native-sources/`, including `native-image.args`, the intermediate application JAR, dependencies and builder metadata. The JAR output must be disabled for this native-only packaging invocation; normal JVM builds are unaffected.

### B. Compile an ARM64 executable using Mandrel 25

With the `linux/arm64` binfmt/QEMU handler installed and operational on the builder:

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

The quoted argument-file substitution shown here reflects the **validated application-specific invocation**; if source paths or arguments contain spaces, replace this with a robust argument-file parser or equivalent CI wrapper. The generated executable is architecture-specific. Verify its format using `readelf -h <runner-binary>` and check that the machine is `AArch64`.

**Measured reference build:** a Quarkus application with MongoDB and REST took approximately **25 min 32 s** for Native Image under ARM64 emulation, with **3.22 GB peak RSS** reported by the compiler. This is a reference measurement, not a speed or memory guarantee for other applications.

### C. Package the compiled ARM64 binary

The prepared `src/main/docker/Dockerfile.native-arm64` uses the Quarkus UBI9 Micro runtime image and directly executes the native binary. This production-style container **does not require Java, a JRE, or Mandrel**.

```bash
# From the repository root, after completing the Native Image build:
docker buildx build \
  --platform linux/arm64 --load \
  -f src/main/docker/Dockerfile.native-arm64 \
  -t api-quarkus:native-arm64 \
  build/native-sources

docker image inspect api-quarkus:native-arm64 \
  --format 'OS={{.Os}} ARCH={{.Architecture}}'
```

The image was tested using an isolated MongoDB 8.0 instance: native startup, `GET /status`, `/q/health`, and both synchronous/reactive MongoDB health checks passed. The executable copied into the image was byte-identical (SHA-256 comparison) to the compiled AArch64 binary.

**Not yet proven:** repository CRUD from the Native Image, a physical ARM64 host, performance under load, automated CI/release and production deployment.

---

## ⚙️ Quality, security and delivery

The ordinary Gradle `check` remains independent of MongoDB. The explicit MongoDB integration task, native build, SAST, dependencies/image vulnerability scanning and artifact verification will become separate pipeline stages. Detekt is required but not yet installed; baseline and failing-path checks will be established before enforcing it.

Woodpecker CI is the planned orchestrator for build, quality and publication. Runners may compile native ARM64 directly or execute an ARM64 containerized builder through emulation. Production runtime nodes only **pull and run** published OCI images; they must not install a JDK or execute Gradle/native-image.

## 🧩 Reusing this template

When forking, review the Gradle group and artifact identity, application name, Kotlin base package, OpenAPI metadata, MongoDB collection/database names, JWT issuer/audience, allowed origins and proxy rules, image/release tags, CI variables, and all runtime secrets. Centralized, idempotent `--check`/`--apply` rebranding tooling is planned, not implemented.

Do not commit authentication keys or production connection strings. Do not expose development mock credentials or issue mock tokens in production.

## 📚 Related projects

- [`Brunno13/api-bun`](https://github.com/Brunno13/api-bun) — Bun / Elysia backend reference.
- [`Brunno13/app-kmp`](https://github.com/Brunno13/app-kmp) — Kotlin Multiplatform application template.
- [`Brunno13/app-react-native`](https://github.com/Brunno13/app-react-native) — React Native application template.
- [`Brunno13/app-flutter`](https://github.com/Brunno13/app-flutter) — Flutter application template.

---

**Release policy:** Mark a milestone completed only when implementation and its associated tests or runtime evidence are available. A successful ARM64 native smoke test is not a substitute for MongoDB CRUD integration tests or validation on the intended production host.
