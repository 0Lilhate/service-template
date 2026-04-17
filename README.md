# service-template

Reusable Spring Boot 3 / Java 21 / Gradle Kotlin DSL template repo.

Infrastructure skeleton only — **no domain logic**. Fork it, rename, add your OpenAPI contract and migrations.

## Stack

| Area | Choice | Why |
|---|---|---|
| Language | Java 21 (LTS) | Virtual threads, records, pattern matching |
| Framework | Spring Boot 3.3.x | Current stable, Jakarta EE 10 |
| Build | Gradle 8 + Kotlin DSL + version catalog | Single source of truth, better DSL than Maven |
| API contract | OpenAPI 3 + `org.openapi.generator` + delegate pattern | Contract-first, generated interfaces |
| DB migrations | Liquibase (YAML) | Less noise than XML, same capabilities |
| DB | PostgreSQL (driver) | Default enterprise choice |
| Messaging | Kafka via **feature toggle** (`app.kafka.enabled`) | Zero cost when off; no separate module to maintain |
| Observability | Actuator + Micrometer + Prometheus + JSON logs (Logstash encoder) | Enterprise defaults |
| Tests | JUnit 5 + Testcontainers (postgres + kafka) | Integration tests against real infra |

## Modules

```
service-api   ← OpenAPI spec + generated Spring controllers/DTOs (delegate pattern)
service-app   ← Spring Boot app: main class, configs, delegate implementations
service-db    ← Liquibase changelog only (resource-only module)
```

No `service-impl` (runtime module is `-app`). No `service-common` — add it only when two modules actually need to share non-generated code.

## Prerequisites

* JDK 21 (toolchain will auto-provision if missing)
* Docker (for Testcontainers / local Postgres / local Kafka)
* Gradle wrapper — run `gradle wrapper` once locally, then commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`

## Build & Run

```bash
./gradlew build                             # builds all modules, runs OpenAPI generation
./gradlew :service-app:bootRun --args='--spring.profiles.active=local'
```

Health: <http://localhost:8080/actuator/health>
Swagger UI: <http://localhost:8080/swagger-ui.html>

## Database migrations (CLI)

The `service-db` module has the `org.liquibase.gradle` plugin applied, so you can drive migrations from the command line without starting the app:

```bash
./gradlew :service-db:update            # apply pending changesets
./gradlew :service-db:status            # what would be applied?
./gradlew :service-db:validate          # validate changelog syntax
./gradlew :service-db:rollbackCount -PliquibaseCommandValue=1
./gradlew :service-db:history           # list applied changesets
./gradlew :service-db:tag -PliquibaseCommandValue=v1.0.0
```

All connection parameters live in [`service-db/liquibase.properties`](service-db/liquibase.properties) (Liquibase native `--defaults-file`). Edit it for your local DB or override per-command via `-Pliquibase<Name>` Gradle properties (built-in plugin behaviour — any `-PliquibaseXxx` overrides the `xxx` key).

CI / staging / prod override:

```bash
./gradlew :service-db:update \
  -PliquibaseUrl=jdbc:postgresql://prod-db:5432/payments \
  -PliquibaseUsername=ci \
  -PliquibasePassword=$DB_PASSWORD_SECRET
```

For per-developer overrides without touching the committed file: copy `liquibase.properties` → `liquibase.local.properties` (gitignored) and pass `-PliquibaseDefaultsFile=...` if you want a different path.

**Migrations do NOT run at application startup.** The `service-app` module has no dependency on Liquibase by design — schema management is fully decoupled from the app lifecycle. Reasons:

* the app runs with a low-privilege DB user (no DDL grants in prod)
* rolling deployments don't race on schema changes
* failed migrations don't crash-loop the app

Workflow:
1. CI/CD runs `./gradlew :service-db:update` as a **pre-deploy** step
2. Only then the new app version is deployed
3. Locally: run `./gradlew :service-db:update` before first `bootRun`

## Bootstrap a new service from this template (15–30 min checklist)

1. `git clone` → rename repo directory.
2. Rename root in `settings.gradle.kts`: `rootProject.name = "<service>"`.
3. Rename modules on disk: `service-api` → `<service>-api`, `service-app` → `<service>-app`, `service-db` → `<service>-db`. Update `include(...)` in `settings.gradle.kts`.
4. Change Maven coordinates in `gradle.properties`: `group=com.company.<service>`.
5. Rename Java package `com.example.service` → `com.company.<service>` (IDE refactor). Update `apiPackage` / `modelPackage` / `invokerPackage` in `service-api/build.gradle.kts` and `mainClass` in `service-app/build.gradle.kts`.
6. Replace `service-api/openapi/service-api.yaml` with the real API contract. Rename file to `<service>-api.yaml`.
7. In `application.yml`: change `spring.application.name`, `DB_URL`, consumer `group-id` (defaults to app name — usually fine).
8. Add first migration as `service-db/src/main/resources/db/changelog/changes/001-<your-change>.yaml`. Delete `000-init.yaml` when no longer needed.
9. (Optional Kafka) set `app.kafka.enabled=true`, add `@KafkaListener` / `KafkaTemplate` where needed.
10. Add CI (GitHub Actions / GitLab CI / Jenkins) — out of scope here.
11. `./gradlew build && ./gradlew :<service>-app:bootRun` — service starts.

## Adding Kafka (no template pollution)

Kafka libraries are always on the classpath (small dep cost) but autoconfig is gated by `app.kafka.enabled`:

```yaml
app:
  kafka:
    enabled: true
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092
```

Then just write listeners / `KafkaTemplate<String, String>` — Spring Boot auto-configures factories from `spring.kafka.*`. No manual bean wiring required. `KafkaConfig` class enables `@EnableKafka` only when the toggle is on.

If you never use Kafka: leave the toggle off. Autoconfiguration still runs producer/consumer factory beans based on `spring.kafka.*`, but they won't be invoked. If you want to strip Kafka entirely from a service: remove `spring-kafka` dep from `service-app/build.gradle.kts` and delete `KafkaConfig.java`.

## Naming conventions

| Layer | Pattern | Example |
|---|---|---|
| Root project | `<service>` | `payments` |
| Modules | `<service>-{api,app,db}` | `payments-api`, `payments-app`, `payments-db` |
| Group | `com.<org>.<service>` | `com.alfa.payments` |
| Base package | same as group | `com.alfa.payments` |
| Generated API package | `<group>.api.controller` / `.api.model` | `com.alfa.payments.api.controller` |
| OpenAPI file | `<service>-api.yaml` | `payments-api.yaml` |
| Liquibase changes | `NNN-<verb>-<object>.yaml` | `001-create-invoice.yaml` |

## What is intentionally empty / placeholder

| File | State | Action on bootstrap |
|---|---|---|
| `service-api/openapi/service-api.yaml` | Single placeholder `/api/v1/ping` endpoint | Replace with real API |
| `service-db/.../changes/000-init.yaml` | `tagDatabase` only, no DDL | Delete after first real migration |
| `config/OpenApiConfig.java` | Minimal title/version bean | Edit title/description |
| `config/KafkaConfig.java` | Empty marker class (`@EnableKafka` + toggle) | Keep, or delete if service never uses Kafka |
| `application-local.yml` | Local profile overrides | Keep / adjust |

## Decisions & trade-offs

1. **No `-common` module.** Shared types that are generated already live in `-api`. If two modules ever need non-generated shared code, create it then — not preemptively.
2. **No separate `-kafka` module.** Kafka is a single config class + a toggle. A module would add Gradle overhead for ~20 lines of code. If a service grows a large Kafka surface area (20+ listeners, schema registry, etc.), extract then.
3. **No Spring Security in baseline.** Auth is opinionated (OAuth2 / JWT / mTLS / gateway-delegated). The template must not choose for the team. Add the starter when you know which.
4. **Liquibase YAML over Flyway.** Flyway is simpler but Liquibase wins on: contexts/labels, rollback, `includeAll` for scalable changelogs, multi-DB support — all relevant at enterprise scale.
5. **OpenAPI delegate pattern.** Clean separation: generated `*ApiController` handles HTTP, team writes `*ApiDelegate` implementation in `-app`.
6. **Virtual threads on.** Java 21 + Spring Boot 3.3 has first-class support; for typical blocking-IO microservices this is a free throughput win.
7. **Version catalog.** All versions in `gradle/libs.versions.toml`. Bumping Spring Boot is one line.
8. **Gradle configuration cache on.** Faster repeated builds; subprojects are written to be compatible.
