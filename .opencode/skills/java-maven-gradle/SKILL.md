---
name: java-maven-gradle
description: Gradle Kotlin DSL for Java backend — plugin block, version catalogs (libs.versions.toml), type-safe project accessors, configuration-avoidance, buildSrc/build-logic convention plugins, composite builds, CI/CD.
---

# Gradle Kotlin DSL — Java backend build reference

This project uses **Gradle Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`). This skill is the on-demand reference for build-config changes. Maven is **not** used — do not introduce POM files.

Scope: multi-module Spring Boot 3.x, JDK 21+ toolchain, Gradle 8.x.

## When to read this skill

- Editing any `*.gradle.kts` or `libs.versions.toml`.
- Adding a new module to `settings.gradle.kts`.
- Writing a convention plugin in `buildSrc` / `build-logic`.
- Dependency-conflict diagnosis (`dependencyInsight`).
- CI pipeline config touching Gradle caching or toolchains.

Not for: application-level Spring config, runtime bugs, or Kotlin application code (see `springboot-patterns`, `java`).

## Plugins block

Use the `plugins { }` DSL with version catalog aliases — not `apply(plugin = ...)`. Pure `plugins { }` enables the plugin DSL's version resolution and IDE support.

```kotlin
// service-app/build.gradle.kts
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("build-logic.java-convention")
}
```

`build-logic.java-convention` is a precompiled script plugin — see **Convention plugins** below.

## Version catalog (`libs.versions.toml`)

Catalog is the single source of truth for versions. Located at `gradle/libs.versions.toml`.

```toml
[versions]
spring-boot = "3.3.4"
junit       = "5.11.0"
testcontainers = "1.20.2"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
junit-jupiter           = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
testcontainers-postgres = { module = "org.testcontainers:postgresql",  version.ref = "testcontainers" }

[bundles]
# one alias → many deps. Use when several libs are always used together.
testing = ["spring-boot-starter-test", "junit-jupiter", "testcontainers-postgres"]

[plugins]
spring-boot            = { id = "org.springframework.boot",            version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.6" }
```

### Platform (BOM) coordinates

Do not hard-code transitive versions when a BOM exists. Import via `platform(...)`:

```kotlin
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.web)      // version inherited from BOM
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.bundles.testing)           // bundle from catalog
}
```

The catalog entry for a BOM points at `spring-boot-dependencies`:

```toml
[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
```

### Version rules (`dependencyConstraints` / `components`)

Pin indirect deps to CVE-patched versions without adding them to runtime:

```kotlin
dependencies {
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-databind") {
            version { require("2.17.2") }
            because("CVE-2023-XXXXX: pin to patched line")
        }
    }
}
```

## Type-safe project accessors

Enable in `settings.gradle.kts`, then reference modules via camel-case accessors — compile-time safe, refactor-friendly:

```kotlin
// settings.gradle.kts
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "hakaton-backend"
include("service-app", "service-common", "service-integration-kafka")
```

```kotlin
// service-app/build.gradle.kts
dependencies {
    implementation(projects.serviceCommon)              // → :service-common
    implementation(projects.serviceIntegrationKafka)    // → :service-integration-kafka
}
```

Replaces stringly `project(":service-common")` — IDE renames propagate.

## Configuration-avoidance (`tasks.register`)

**Always** `tasks.register<T>(...)` — never `tasks.create(...)`. `register` is lazy: the task graph resolves only if the task is actually requested.

```kotlin
// GOOD — lazy; no configuration cost unless invoked
val generateBuildInfo by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated/build-info.properties")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText("build.time=${System.currentTimeMillis()}")
    }
}

// BAD — eager; runs configuration phase even on unrelated tasks
tasks.create("generateBuildInfo") { ... }
```

Use lazy `Provider<T>` / `Property<T>` for task inputs — never read `project.version` directly inside `doLast`.

## Convention plugins (`build-logic`)

Multi-module boilerplate lives in **precompiled script plugins** under `build-logic/` (preferred over legacy `buildSrc`). A convention plugin is just a `*.gradle.kts` file whose filename becomes the plugin id.

Layout:

```
build-logic/
├── settings.gradle.kts
├── build.gradle.kts
└── src/main/kotlin/
    ├── build-logic.java-convention.gradle.kts
    └── build-logic.spring-boot-service.gradle.kts
```

`build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "build-logic"
```

`build-logic/build.gradle.kts`:

```kotlin
plugins { `kotlin-dsl` }
repositories { gradlePluginPortal() }
```

`build-logic.java-convention.gradle.kts` — shared Java/test config:

```kotlin
plugins { java }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
```

Consume in module: `plugins { id("build-logic.java-convention") }`. Root `settings.gradle.kts` must `includeBuild("build-logic")`.

## Composite builds

`includeBuild(path)` substitutes a local project for a published dependency — essential for `build-logic` and for cross-repo dev.

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
}
// or for library substitution:
includeBuild("../shared-lib-repo")
```

## Build cache

Local cache is on by default. For team/CI, enable remote cache (Develocity / HTTP cache node) in `settings.gradle.kts`:

```kotlin
buildCache {
    local { isEnabled = true }
    remote<HttpBuildCache> {
        url = uri("https://cache.example.com/")
        isPush = System.getenv("CI") == "true"
    }
}
```

See Gradle docs for Develocity (`/docs "gradle" "develocity build cache"`) before wiring remote cache in production.

## Dependency diagnostics

```bash
./gradlew :service-app:dependencies --configuration runtimeClasspath --no-daemon
./gradlew :service-app:dependencyInsight --dependency jackson-databind --configuration runtimeClasspath --no-daemon
./gradlew buildEnvironment --no-daemon        # plugin classpath
./gradlew :service-app:tasks --all --no-daemon
```

## CI — canonical GitHub Actions workflow

One workflow, no matrix duplication. Uses `setup-java` built-in Gradle cache (toolchain, wrapper, dependency cache).

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push:    { branches: [main] }
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'
      - run: ./gradlew check --no-daemon --stacktrace
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: reports
          path: '**/build/reports/**'
```

Do not add `--refresh-dependencies` by default — it defeats caching. Use only on cache-poisoning incidents.

## Common pitfalls

| Pitfall | Fix |
|---------|-----|
| `tasks.create` used in convention plugin | Replace with `tasks.register` — lazy configuration |
| `project(":foo")` string reference | Use type-safe accessor `projects.foo` |
| Version pinned inline in `build.gradle.kts` | Move to `libs.versions.toml` |
| `buildSrc` drift from module classpath | Migrate to `build-logic` included build |
| `--refresh-dependencies` in CI | Remove; rely on catalog + lock files |
| Mixed Kotlin/Groovy DSL in same module | Normalise to Kotlin DSL |

## Out of scope

- Maven / POM files — this project does not use Maven.
- Scala / Groovy DSL — use Kotlin DSL form only.
- Application-layer Spring config (`@Configuration`, `application.yml`) — see `springboot-patterns`.
- Runtime JVM flags / container sizing — see `java-docker`, `java-performance`.

## Related skills

| task | skill |
|------|-------|
| Container build args / image size | `java-docker` |
| JVM/GC tuning, JFR, heap dumps | `java-performance` |
| Application YAML + bean config | `springboot-patterns` |
| Migration scripts (Liquibase) | `database-migrations` |
