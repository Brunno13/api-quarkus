# API Quarkus Kotlin

API backend leve, modular e preparada para atender aplicações **mobile** e **web**, construída com **Quarkus**, **Kotlin**, **MariaDB** e autenticação baseada em **JWT RBAC**.

O projeto será desenvolvido como um **monólito modular**, organizado por features e inspirado em **Feature-Sliced Design (FSD)**, mantendo uma abordagem pragmática de **Clean Architecture** dentro de cada domínio.

> Status: em planejamento / bootstrap inicial.

---

## Recursos Principais

* **API leve e performática:** Backend Quarkus com foco em baixo consumo de recursos e execução eficiente em containers ARM64.
* **Arquitetura modular:** Organização por `features/`, evitando crescimento desordenado da raiz do projeto.
* **Clean Architecture pragmática:** Separação entre API, casos de uso, domínio e infraestrutura sem excesso de boilerplate.
* **Autenticação e autorização:** JWT com RBAC, roles e proteção de endpoints via `@RolesAllowed`.
* **Persistência relacional:** MariaDB com Hibernate ORM Panache Kotlin e versionamento de schema com Flyway.
* **Validação de entrada:** DTOs Kotlin com Bean Validation, substituindo o papel de schemas como Zod no backend JVM.
* **Documentação automática:** OpenAPI/Swagger UI para exploração e validação dos endpoints.
* **Observabilidade:** Logs JSON no stdout, healthchecks e integração futura com OpenTelemetry/Jaeger.
* **Deploy containerizado:** Docker com foco em imagem `linux/arm64`, preparado para Portainer e futuro Docker Swarm.

---

## Roadmap do Projeto

Este roadmap será usado como norte de implementação da API.

### ✅ Definições Concluídas

- [x] **Stack principal:** Kotlin + Quarkus.
- [x] **Banco padrão:** MariaDB.
- [x] **Modelo inicial:** Monólito modular.
- [x] **Arquitetura:** FSD no nível de pastas + Clean Architecture pragmática dentro de cada feature.
- [x] **Autenticação:** JWT RBAC.
- [x] **Persistência:** Hibernate ORM Panache Kotlin + Flyway.
- [x] **Validação:** Bean Validation em DTOs.
- [x] **Documentação:** OpenAPI/Swagger.
- [x] **Observabilidade inicial:** Logs JSON + healthcheck.
- [x] **Deploy alvo:** Docker ARM64, com possibilidade futura de native image.

---

### 🧱 Fase 1 — Bootstrap do Projeto

- [ ] Criar projeto Quarkus com Kotlin.
- [ ] Escolher Gradle Kotlin DSL como build tool.
- [ ] Configurar extensões iniciais:
  - [ ] `quarkus-kotlin`
  - [ ] `quarkus-rest-jackson`
  - [ ] `quarkus-hibernate-validator`
  - [ ] `quarkus-smallrye-openapi`
  - [ ] `quarkus-hibernate-orm-panache-kotlin`
  - [ ] `quarkus-jdbc-mariadb`
  - [ ] `quarkus-flyway`
  - [ ] `quarkus-smallrye-jwt`
  - [ ] `quarkus-smallrye-health`
  - [ ] `quarkus-logging-json`
- [ ] Configurar `all-open` e `no-arg/jpa` para Kotlin + Hibernate.
- [ ] Criar endpoint inicial `/hello` ou `/q/health` para validar o servidor.
- [ ] Padronizar `application.properties` com perfis `dev`, `test` e `prod`.

---

### 🧭 Fase 2 — Arquitetura Base

- [ ] Criar estrutura principal:
  - [ ] `features/`
  - [ ] `shared/`
  - [ ] `bootstrap/` apenas se necessário.
- [ ] Definir convenção interna das features:
  - [ ] `api/`
  - [ ] `application/`
  - [ ] `domain/`
  - [ ] `infrastructure/`
- [ ] Criar primeira feature `auth`.
- [ ] Criar primeira feature `users`.
- [ ] Criar pacote `shared/error` para tratamento global de erros.
- [ ] Criar pacote `shared/security` para helpers e tipos comuns de segurança.
- [ ] Criar pacote `shared/logging` para request id e logs estruturados.
- [ ] Definir regra de importação entre camadas.
- [ ] Definir conceito de Public API/Facade por feature.

---

### 🗄️ Fase 3 — Banco de Dados e Migrations

- [ ] Subir MariaDB local via Docker Compose.
- [ ] Configurar datasource do Quarkus.
- [ ] Configurar Flyway.
- [ ] Criar migration `V1__create_users.sql`.
- [ ] Criar migration `V2__create_refresh_tokens.sql`.
- [ ] Criar entidade JPA de usuário.
- [ ] Criar entidade JPA de refresh token.
- [ ] Criar repositories usando Panache Kotlin.
- [ ] Validar conexão com banco no profile `dev`.
- [ ] Definir estratégia de IDs:
  - [ ] `Long` auto increment, ou
  - [ ] UUID.
- [ ] Definir timestamps padrão:
  - [ ] `created_at`
  - [ ] `updated_at`
  - [ ] `deleted_at`, se soft delete for adotado.

---

### 🔐 Fase 4 — Autenticação JWT RBAC

- [ ] Criar domínio de autenticação.
- [ ] Criar roles iniciais:
  - [ ] `USER`
  - [ ] `ADMIN`
- [ ] Criar serviço de hash de senha.
- [ ] Definir algoritmo de hash:
  - [ ] BCrypt, ou
  - [ ] Argon2, se adotado depois.
- [ ] Implementar endpoint `POST /auth/register`.
- [ ] Implementar endpoint `POST /auth/login`.
- [ ] Implementar emissão de access token JWT.
- [ ] Implementar refresh token persistido no banco.
- [ ] Salvar apenas hash do refresh token.
- [ ] Implementar endpoint `POST /auth/refresh`.
- [ ] Implementar endpoint `POST /auth/logout`.
- [ ] Implementar endpoint `GET /auth/me`.
- [ ] Proteger endpoints com `@Authenticated` e `@RolesAllowed`.
- [ ] Definir tempo de expiração:
  - [ ] Access token curto.
  - [ ] Refresh token longo.
- [ ] Definir estratégia para web:
  - [ ] Access token em memória.
  - [ ] Refresh token em cookie HttpOnly, se aplicável.
- [ ] Definir estratégia para mobile:
  - [ ] Refresh token no secure storage do app.

---

### 👤 Fase 5 — Usuários e Perfil

- [ ] Criar feature `users`.
- [ ] Criar caso de uso para buscar usuário atual.
- [ ] Criar endpoint `GET /users/me`.
- [ ] Criar endpoint `PATCH /users/me`.
- [ ] Criar endpoint `PATCH /users/me/password`.
- [ ] Criar endpoint administrativo `GET /users`.
- [ ] Criar endpoint administrativo `GET /users/{id}`.
- [ ] Criar endpoint administrativo para ativar/desativar usuário.
- [ ] Definir DTOs de request e response.
- [ ] Garantir que entidades JPA não vazem diretamente na API.
- [ ] Adicionar validações com Bean Validation.

---

### 🧪 Fase 6 — Testes

- [ ] Configurar testes com JUnit 5.
- [ ] Configurar `@QuarkusTest`.
- [ ] Criar testes unitários para casos de uso.
- [ ] Criar testes de integração para endpoints REST.
- [ ] Criar testes de validação dos DTOs.
- [ ] Criar testes de autorização por role.
- [ ] Avaliar uso de Testcontainers para MariaDB.
- [ ] Criar profile de teste com banco isolado.
- [ ] Adicionar testes no pipeline do Woodpecker CI.

---

### 📚 Fase 7 — OpenAPI, Health e Erros

- [ ] Configurar título, versão e descrição da OpenAPI.
- [ ] Documentar endpoints principais.
- [ ] Padronizar responses de erro.
- [ ] Criar `GlobalExceptionMapper`.
- [ ] Criar modelo `ApiError`.
- [ ] Criar códigos internos de erro.
- [ ] Configurar Swagger UI em dev.
- [ ] Decidir se Swagger UI ficará desabilitado em produção.
- [ ] Configurar SmallRye Health.
- [ ] Validar endpoints:
  - [ ] `/q/health`
  - [ ] `/q/health/live`
  - [ ] `/q/health/ready`

---

### 📈 Fase 8 — Logs e Observabilidade

- [ ] Configurar logs JSON no console.
- [ ] Adicionar `requestId` por requisição.
- [ ] Incluir `requestId` nos logs.
- [ ] Incluir `traceId` e `spanId` quando OpenTelemetry estiver ativo.
- [ ] Padronizar campos de log para compatibilidade com Dozzle.
- [ ] Avaliar OpenTelemetry.
- [ ] Integrar com Jaeger via OTLP.
- [ ] Definir sampling inicial de traces.
- [ ] Criar endpoint ou metadata de versão da aplicação.
- [ ] Definir convenção de logs para eventos de segurança:
  - [ ] Login bem-sucedido.
  - [ ] Login inválido.
  - [ ] Refresh token revogado.
  - [ ] Acesso negado por role.

---

### 🐳 Fase 9 — Docker e Execução Local

- [ ] Criar `Dockerfile.jvm`.
- [ ] Criar `Dockerfile.native`, se native image for adotado.
- [ ] Criar `docker-compose.yml` para desenvolvimento local.
- [ ] Adicionar serviço MariaDB ao compose.
- [ ] Adicionar healthcheck do container da API.
- [ ] Configurar variáveis de ambiente.
- [ ] Criar `.env.example`.
- [ ] Validar build `linux/arm64`.
- [ ] Validar execução em ambiente ARM64.
- [ ] Configurar limites de memória recomendados.
- [ ] Documentar comandos de build e execução.

---

### 🚀 Fase 10 — CI/CD e Deploy

- [ ] Criar pipeline Woodpecker CI.
- [ ] Executar lint/check/test no pipeline.
- [ ] Gerar imagem Docker.
- [ ] Publicar imagem em registry.
- [ ] Definir estratégia de versionamento por tag.
- [ ] Criar pipeline de release.
- [ ] Criar stack inicial para Portainer.
- [ ] Preparar deploy futuro em Docker Swarm.
- [ ] Definir secrets:
  - [ ] `DB_PASSWORD`
  - [ ] `JWT_PRIVATE_KEY`
  - [ ] `JWT_PUBLIC_KEY`
  - [ ] `MARIADB_ROOT_PASSWORD`
- [ ] Criar documentação de rollback.

---

### 🛡️ Fase 11 — Hardening e Segurança

- [ ] Configurar CORS por ambiente.
- [ ] Definir política de cookies para web, se usada.
- [ ] Configurar HTTPS no reverse proxy.
- [ ] Validar headers de segurança.
- [ ] Bloquear Swagger UI em produção ou proteger acesso.
- [ ] Garantir que secrets não sejam logados.
- [ ] Implementar rate limit, se necessário.
- [ ] Implementar lockout temporário para login inválido, se necessário.
- [ ] Revisar expiração e rotação de refresh tokens.
- [ ] Criar rotina de limpeza de refresh tokens expirados.
- [ ] Criar usuário admin inicial apenas via variável de ambiente ou comando controlado.
- [ ] Evitar senha padrão hardcoded.

---

### 📦 Fase 12 — Native Image

- [ ] Validar build JVM primeiro.
- [ ] Criar build native local.
- [ ] Testar serialização/deserialização de DTOs Kotlin em native image.
- [ ] Testar autenticação JWT em native image.
- [ ] Testar Hibernate ORM/Panache em native image.
- [ ] Testar Flyway em native image.
- [ ] Medir consumo de memória.
- [ ] Comparar imagem JVM vs native.
- [ ] Decidir se produção usará:
  - [ ] JVM container otimizado.
  - [ ] Native image.
- [ ] Criar pipeline específica para native image ARM64, se adotado.

---

## Tecnologias Utilizadas

* **Framework:** Quarkus
* **Linguagem:** Kotlin
* **Build Tool:** Gradle Kotlin DSL
* **Servidor HTTP/API:** Quarkus REST
* **Validação:** Hibernate Validator / Jakarta Bean Validation
* **Documentação:** SmallRye OpenAPI + Swagger UI
* **Banco de Dados:** MariaDB
* **ORM:** Hibernate ORM Panache Kotlin
* **Migrations:** Flyway
* **Autenticação:** SmallRye JWT
* **Autorização:** JWT RBAC + `@RolesAllowed`
* **Healthcheck:** SmallRye Health
* **Logs Estruturados:** Quarkus Logging JSON
* **Tracing:** OpenTelemetry + Jaeger
* **Containerização:** Docker
* **CI/CD:** Woodpecker CI
* **Deploy:** Portainer e futuro Docker Swarm

---

## Arquitetura do Projeto

O projeto seguirá uma organização inspirada em **Feature-Sliced Design**, mas adaptada para backend.

A raiz do código será pequena e previsível:

```text
src/main/kotlin/br/com/seuprojeto/
├── features/      # Domínios de negócio da aplicação
├── shared/        # Infraestrutura comum e código agnóstico de domínio
└── bootstrap/     # Inicialização da aplicação, se necessário
```

A pasta `bootstrap/` é opcional. Ela só deve existir se houver código real de inicialização, como seed controlado, logs de startup, observers do Quarkus ou tarefas de boot.

---

## Estrutura de Pastas

Estrutura prevista:

```text
src/
├── main/
│   ├── kotlin/
│   │   └── br/
│   │       └── com/
│   │           └── seuprojeto/
│   │               ├── features/
│   │               │   ├── auth/
│   │               │   │   ├── api/
│   │               │   │   │   ├── AuthResource.kt
│   │               │   │   │   └── dto/
│   │               │   │   ├── application/
│   │               │   │   │   ├── LoginUseCase.kt
│   │               │   │   │   ├── RefreshTokenUseCase.kt
│   │               │   │   │   └── LogoutUseCase.kt
│   │               │   │   ├── domain/
│   │               │   │   │   ├── Account.kt
│   │               │   │   │   ├── Role.kt
│   │               │   │   │   ├── RefreshToken.kt
│   │               │   │   │   └── AuthRepository.kt
│   │               │   │   ├── infrastructure/
│   │               │   │   │   ├── AccountJpaEntity.kt
│   │               │   │   │   ├── RefreshTokenJpaEntity.kt
│   │               │   │   │   ├── AuthRepositoryJpa.kt
│   │               │   │   │   ├── JwtTokenService.kt
│   │               │   │   │   └── PasswordHasher.kt
│   │               │   │   └── AuthFacade.kt
│   │               │   │
│   │               │   └── users/
│   │               │       ├── api/
│   │               │       │   ├── UserResource.kt
│   │               │       │   └── dto/
│   │               │       ├── application/
│   │               │       ├── domain/
│   │               │       ├── infrastructure/
│   │               │       └── UsersFacade.kt
│   │               │
│   │               ├── shared/
│   │               │   ├── error/
│   │               │   │   ├── ApiError.kt
│   │               │   │   └── GlobalExceptionMapper.kt
│   │               │   ├── logging/
│   │               │   │   └── RequestIdFilter.kt
│   │               │   ├── security/
│   │               │   │   ├── CurrentUser.kt
│   │               │   │   └── Roles.kt
│   │               │   ├── validation/
│   │               │   ├── persistence/
│   │               │   └── openapi/
│   │               │
│   │               └── bootstrap/
│   │                   ├── StartupLogger.kt
│   │                   └── DevDataSeeder.kt
│   │
│   └── resources/
│       ├── application.properties
│       └── db/
│           └── migration/
│               ├── V1__create_users.sql
│               └── V2__create_refresh_tokens.sql
│
└── test/
    └── kotlin/
```

---

## Diretrizes de Arquitetura

### 1. Fluxo de Dependências

As dependências devem seguir um fluxo previsível:

```text
api -> application -> domain
infrastructure -> domain/application
shared -> não depende de features
```

Regras principais:

* `features/*/api` pode chamar `application`.
* `features/*/application` pode depender de `domain`.
* `features/*/domain` não deve depender de Quarkus, Hibernate, REST, banco ou JWT.
* `features/*/infrastructure` implementa contratos do domínio ou da aplicação.
* `shared` não deve conter regra de negócio específica.
* Uma feature não deve acessar detalhes internos de outra feature.

---

### 2. Public API das Features

Cada feature pode expor uma fachada pública quando precisar ser consumida por outra feature.

Exemplo:

```text
features/users/UsersFacade.kt
features/auth/AuthFacade.kt
```

Permitido:

```kotlin
import br.com.seuprojeto.features.users.UsersFacade
```

Evitar:

```kotlin
import br.com.seuprojeto.features.users.infrastructure.UserJpaEntity
import br.com.seuprojeto.features.users.application.CreateUserUseCase
```

A ideia é manter encapsulamento semelhante ao uso de Barrel Files no frontend.

---

### 3. DTOs não são Entidades

Entidades de persistência não devem ser retornadas diretamente pela API.

A API deve usar DTOs:

```text
CreateUserRequest
UpdateUserRequest
UserResponse
LoginRequest
LoginResponse
```

Entidades JPA ficam em `infrastructure`.

Modelos de domínio ficam em `domain`.

---

### 4. Kotlin + JPA

Diretrizes específicas:

* Usar `data class` para DTOs, commands, results e value objects.
* Evitar `data class` para entidades JPA.
* Usar classes abertas via plugin `all-open`.
* Usar plugin `jpa/no-arg`.
* Usar `@field:` em anotações de validação.
* Testar native image cedo.

Exemplo:

```kotlin
data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    val password: String
)
```

---

## Ambientes

A API deverá suportar pelo menos três ambientes:

### Desenvolvimento

```text
Profile: dev
Banco: MariaDB via Docker Compose
Swagger UI: habilitado
Logs: JSON ou pretty, conforme necessidade local
```

### Testes

```text
Profile: test
Banco: Testcontainers ou banco isolado
Migrations: Flyway
JWT: chaves de teste
```

### Produção

```text
Profile: prod
Banco: MariaDB persistente
Swagger UI: desabilitado ou protegido
Logs: JSON no stdout
Secrets: variáveis de ambiente ou Docker secrets
Deploy: Docker/Portainer/Swarm
```

---

## Configuração Local

### Pré-requisitos

* JDK 21
* Gradle ou Wrapper do Gradle
* Docker
* Docker Compose
* MariaDB via container
* Quarkus CLI, opcional

---

### Variáveis de Ambiente

Criar um arquivo `.env` local baseado em `.env.example`.

```env
APP_ENV=dev

DB_HOST=localhost
DB_PORT=3306
DB_NAME=appdb
DB_USER=app
DB_PASSWORD=app_password

JWT_ISSUER=api-quarkus
JWT_PRIVATE_KEY_PATH=privateKey.pem
JWT_PUBLIC_KEY_PATH=publicKey.pem
```

---

## Como Rodar Localmente

### 1. Subir banco local

```bash
docker compose up -d mariadb
```

### 2. Rodar migrations

As migrations serão executadas automaticamente pelo Flyway no startup da aplicação.

```bash
./gradlew quarkusDev
```

### 3. Acessar endpoints úteis

```text
API:        http://localhost:8080
Health:     http://localhost:8080/q/health
OpenAPI:    http://localhost:8080/q/openapi
Swagger UI: http://localhost:8080/q/swagger-ui
```

---

## Docker Compose de Desenvolvimento

Placeholder inicial:

```yaml
services:
  api:
    build:
      context: .
      dockerfile: src/main/docker/Dockerfile.jvm
    environment:
      QUARKUS_PROFILE: dev
      DB_HOST: mariadb
      DB_PORT: 3306
      DB_NAME: appdb
      DB_USER: app
      DB_PASSWORD: app_password
    ports:
      - "8080:8080"
    depends_on:
      - mariadb

  mariadb:
    image: mariadb:lts
    environment:
      MARIADB_DATABASE: appdb
      MARIADB_USER: app
      MARIADB_PASSWORD: app_password
      MARIADB_ROOT_PASSWORD: root_password
    ports:
      - "3306:3306"
    volumes:
      - mariadb_data:/var/lib/mysql

volumes:
  mariadb_data:
```

---

## Build

### Build JVM

```bash
./gradlew build
```

### Rodar em modo dev

```bash
./gradlew quarkusDev
```

### Build Docker JVM

```bash
docker build -f src/main/docker/Dockerfile.jvm -t api-quarkus:jvm .
```

### Build Native

```bash
./gradlew build -Dquarkus.native.enabled=true
```

### Build Docker Native

```bash
docker build -f src/main/docker/Dockerfile.native -t api-quarkus:native .
```

> A estratégia de produção JVM vs Native será validada durante o roadmap. A prioridade inicial é ter a API funcional e bem testada.

---

## Autenticação e Autorização

A API usará autenticação baseada em JWT.

Fluxo previsto:

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /auth/me
```

Modelo de sessão:

```text
Access Token:
  JWT curto
  contém subject, roles e expiração

Refresh Token:
  token longo
  persistido no banco apenas como hash
  pode ser revogado no logout
```

Autorização:

```kotlin
@RolesAllowed("ADMIN")
```

Roles iniciais:

```text
USER
ADMIN
```

---

## Banco de Dados

Banco padrão:

```text
MariaDB
```

Migrations:

```text
Flyway
```

Tabelas iniciais previstas:

```text
users
refresh_tokens
```

Estratégia inicial:

* Schema versionado por migrations SQL.
* Nenhuma alteração automática de schema em produção.
* Repositories usando Panache Kotlin.
* Entidades JPA isoladas em `infrastructure`.
* DTOs separados das entidades.

---

## Observabilidade

### Logs

A API deverá emitir logs estruturados em JSON no stdout.

Campos desejados:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "level": "INFO",
  "service": "api-quarkus",
  "env": "dev",
  "requestId": "uuid",
  "traceId": "trace-id",
  "spanId": "span-id",
  "message": "request completed"
}
```

### Healthcheck

Endpoints Quarkus:

```text
/q/health
/q/health/live
/q/health/ready
```

### Tracing

Integração futura:

```text
OpenTelemetry -> Jaeger
```

---

## CI/CD

Pipeline prevista com Woodpecker CI.

Fluxo esperado:

1. Push ou Pull Request.
2. Executar checks.
3. Rodar testes.
4. Gerar build.
5. Gerar imagem Docker.
6. Publicar imagem no registry.
7. Deploy via Portainer ou Docker Swarm.
8. Criar release ao gerar tag.

Placeholder de etapas:

```yaml
steps:
  test:
    image: gradle:jdk21
    commands:
      - ./gradlew test

  build:
    image: gradle:jdk21
    commands:
      - ./gradlew build

  docker:
    image: plugins/docker
    settings:
      repo: registry.local/api-quarkus
      tags:
        - latest
        - ${CI_COMMIT_TAG}
```

---

## Deploy

### Estratégia Inicial

```text
Docker container
Portainer
MariaDB persistente
Reverse proxy externo
```

### Estratégia Futura

```text
Docker Swarm
Serviços stateless replicáveis
Banco fixado no nó com storage persistente
Secrets gerenciados pelo Swarm
Healthchecks configurados
Logs via Docker/Dozzle
Tracing via Jaeger
```

### Portas previstas

```text
8080  -> API
3306  -> MariaDB, apenas rede interna
4317  -> OTLP gRPC, se tracing for ativado
4318  -> OTLP HTTP, se tracing for ativado
```

---

## Segurança

Diretrizes iniciais:

* Não commitar secrets.
* Não usar senha padrão hardcoded.
* Criar admin inicial apenas via variável de ambiente ou processo controlado.
* Armazenar hash de senha, nunca senha pura.
* Armazenar hash do refresh token, nunca o refresh token puro.
* Usar HTTPS em produção.
* Configurar CORS por ambiente.
* Desabilitar ou proteger Swagger UI em produção.
* Não expor stacktrace ao cliente.
* Não logar tokens, senhas ou dados sensíveis.

---

## Convenções de Código

Placeholder inicial:

```text
Classes de API:
  AuthResource
  UserResource

DTOs:
  LoginRequest
  LoginResponse
  UserResponse

UseCases:
  LoginUseCase
  RegisterUserUseCase
  RefreshTokenUseCase

Repositories:
  UserRepository
  UserRepositoryJpa

Entities:
  UserJpaEntity
  RefreshTokenJpaEntity

Facades:
  AuthFacade
  UsersFacade
```

---

## Próximo Passo Imediato

A próxima etapa será criar o projeto Quarkus do zero com Kotlin e validar o primeiro ciclo completo:

```text
Quarkus sobe
MariaDB conecta
Flyway executa
Healthcheck responde
Swagger abre
Primeiro endpoint funciona
```

Depois disso, a implementação seguirá o roadmap por fases.
