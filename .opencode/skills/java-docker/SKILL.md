---
name: java-docker
description: Containerize Spring Boot services — multi-stage Gradle Dockerfile, container-aware JVM flags, non-root security, layered caching, health checks. Use when writing or reviewing Dockerfile / OCI image for a Java backend.
origin: ECC
---

# Java Docker

Production-grade containerization for Spring Boot services on Gradle Kotlin DSL. Covers Dockerfile structure, JVM container flags, image size, security hardening, and health checks.

## When to use

- Writing a new `Dockerfile` / `Containerfile` for a Spring Boot module.
- Reviewing a PR that changes container image build.
- Prepping a service for K8s / ECS / Cloud Run deploy.
- Diagnosing OOMKilled / slow-startup / oversized-image issues.

## When NOT to use

- Bare-JAR deploy on a VM with systemd — no containerization needed.
- Pure build-tool questions (`./gradlew` targets, dependencies) → `AGENTS.md` + `java-build-resolver` agent.
- Kafka / HTTP / async runtime issues → `integration-reviewer` agent.
- JVM tuning unrelated to containers (GC, heap-dump analysis) → `java-performance` skill.

## Step-by-step process

1. **Enable layered JARs in Gradle.** In the module's `build.gradle.kts`:
   ```kotlin
   tasks.bootJar {
     layered { enabled.set(true) }
   }
   ```
   Spring Boot 2.4+ default is enabled, but verify — layered JARs are the basis of efficient layer caching.

2. **Write a multi-stage Dockerfile.** See canonical template below.
3. **Set container-aware JVM flags** via `JAVA_OPTS`. Mandatory flags listed below.
4. **Drop privileges** — create and use a non-root user; never run as root in runtime stage.
5. **Add a health check** — `HEALTHCHECK` pointing to Spring Boot Actuator `/actuator/health/liveness`.
6. **Verify after build:**
   ```bash
   docker build -t <service>:dev .
   docker images <service>:dev                       # size check
   docker run --rm --memory=512m <service>:dev       # OOM smoke test
   docker scan <service>:dev                         # vulnerabilities (Docker Desktop) OR trivy image
   ```

## Canonical multi-stage Dockerfile (Gradle Kotlin DSL)

```dockerfile
# syntax=docker/dockerfile:1.7

# ---- build stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Wrapper + build files for dependency caching (order matters)
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY build.gradle.kts ./
# Per-module build files (adapt to your multi-module layout)
COPY service-app/build.gradle.kts service-app/

# Warm dependency cache — this layer is reused unless build files change
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :service-app:dependencies -q || true

# Source + build (tests skipped in image build; run them in CI separately)
COPY service-app/src service-app/src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :service-app:bootJar -x test

# Extract Spring Boot layers for per-layer COPY in runtime stage
RUN mkdir -p /layers && \
    java -Djarmode=layertools -jar service-app/build/libs/*.jar extract --destination /layers

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

# Copy layers ordered by change frequency (slowest-changing first → best cache)
COPY --from=builder --chown=app:app /layers/dependencies/          ./
COPY --from=builder --chown=app:app /layers/spring-boot-loader/    ./
COPY --from=builder --chown=app:app /layers/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /layers/application/           ./

USER app

ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heapdump.hprof \
  -XX:+UseG1GC"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

Replace `service-app` with the actual module name for your project.

## Container-aware JVM flags (mandatory)

| flag | зачем |
|------|-------|
| `-XX:+UseContainerSupport` | JVM читает cgroup limits вместо host'а. Включено по умолчанию в JDK 10+; держим явно для прозрачности |
| `-XX:MaxRAMPercentage=75.0` | heap — процент от container memory limit. 75% оставляет 25% на metaspace, thread stacks, direct buffers. Не `-Xmx` — числа не переживают изменения memory request |
| `-XX:InitialRAMPercentage=50.0` | начальный heap — избегает warm-up expansion |
| `-XX:+ExitOnOutOfMemoryError` | контейнер крашится → K8s перезапускает pod. Лучше, чем полу-живой процесс |
| `-XX:+HeapDumpOnOutOfMemoryError` | heap dump для post-mortem анализа |
| `-XX:HeapDumpPath=/tmp/heapdump.hprof` | в /tmp (обычно writable); при K8s — смонтировать volume для сохранения |
| `-XX:+UseG1GC` | default в JDK 9+, указываем явно. Для high-throughput long-pause-tolerant сервисов — рассмотреть ZGC (`-XX:+UseZGC`) |

## Base image comparison

| Image | Size (приблизительно) | Shell? | Use case |
|-------|----------------------|--------|----------|
| `eclipse-temurin:21-jre` | ~280 MB | `bash` | dev, debug, legacy |
| `eclipse-temurin:21-jre-alpine` | ~170 MB | `sh` | staging/prod, tolerates musl libc |
| `gcr.io/distroless/java21-debian12` | ~180 MB | **нет** | prod, максимальная security surface reduction |
| `amazoncorretto:21-alpine` | ~170 MB | `sh` | prod, AWS-native |

Alpine использует musl libc — большинство Spring Boot приложений работают; если возникнут `UnsatisfiedLinkError` на native-зависимостях (например, Netty epoll) — переключись на `-jammy` или `-ubuntu` варианты.

## Security hardening (обязательно)

```dockerfile
# 1. Non-root user
RUN addgroup -S app && adduser -S -G app app
USER app

# 2. Read-only root filesystem — настраивается при запуске:
#    docker run --read-only --tmpfs /tmp <image>
#    K8s: securityContext: { readOnlyRootFilesystem: true }
#    + emptyDir volume для /tmp (heap dumps, jcmd output)

# 3. Drop capabilities — на уровне runtime:
#    docker run --cap-drop=ALL <image>
#    K8s: securityContext: { capabilities: { drop: [ALL] } }

# 4. Distroless + static labels для SBOM
LABEL org.opencontainers.image.source="https://github.com/<org>/<repo>" \
      org.opencontainers.image.licenses="Apache-2.0"
```

## Guardrails — чего нельзя делать

- **Не запускать контейнер от root** в runtime stage. Если base image не имеет non-root user — создай его сам (`adduser`).
- **Не использовать `latest` tag** для base image — pin к конкретной версии (`21.0.5_11-jre-alpine`). Иначе воспроизводимость build'а теряется.
- **Не отключать `UseContainerSupport`.** Без него JVM видит host memory, не cgroup — гарантированный OOMKill.
- **Не фиксить heap через `-Xmx` в мегабайтах** — при изменении memory request нужно пересобирать image. Используй `MaxRAMPercentage`.
- **Не бэкать secrets в image** (`ENV API_KEY=...`, `COPY .env`). Docker layer history хранит их вечно. Передавай через K8s secrets / env at runtime.
- **Не копировать `.git/`, `build/`, `.gradle/`, `node_modules/`** — используй `.dockerignore`.
- **Не пропускать `HEALTHCHECK`.** Без него K8s liveness/readiness probes работают на HTTP-уровне отдельно; для `docker run` standalone проверок нет.
- **Не включать shell в distroless-run** (`ENTRYPOINT ["sh","-c",...]`). Для distroless используй exec form: `ENTRYPOINT ["java","-jar","app.jar"]` — но тогда `JAVA_OPTS` env не разворачивается. Альтернатива: прописать флаги в ENTRYPOINT массиве.

## Типичные ошибки

| симптом | причина | фикс |
|---------|---------|------|
| Pod OOMKilled вскоре после старта | heap > memory limit; `MaxRAMPercentage` слишком агрессивен | снизить до 70%; увеличить K8s memory limit; проверить direct buffer usage (`-XX:MaxDirectMemorySize`) |
| Image ~800 MB+ | base JDK (не JRE) + non-layered JAR | перейти на `jre-alpine` + enable layered JAR + multi-stage |
| Долгий rebuild даже при изменении одной строки в контроллере | deps layer копируется после source | COPY build files и `./gradlew dependencies` ПЕРЕД COPY src |
| `java.lang.UnsatisfiedLinkError: libpthread.so.0` | Alpine musl + glibc-зависимая native lib | переключиться на `-jammy` / `-ubuntu` base |
| Permission denied при записи в `/tmp` | read-only filesystem без tmpfs mount | в K8s добавить `emptyDir` volume на `/tmp`; в docker — `--tmpfs /tmp` |
| HEALTHCHECK fails during startup | `start-period` слишком короткий для Spring Boot init | увеличить до 40-60s; проверить что `/actuator/health/liveness` exposed в `management.endpoints.web.exposure.include` |
| `jcmd` / `jstack` не работают в контейнере | distroless не имеет JDK tools | `exec` в sidecar с JDK, или временно переключиться на `jdk-alpine` для диагностики |

## Verification checklist (перед PR)

- [ ] `docker build` собирается с `--mount=type=cache` и ре-build при изменении `*.java` файла проходит быстро (кэш dependencies не инвалидируется).
- [ ] Финальный image < 250 MB для Alpine JRE базы.
- [ ] `docker history <image>` — нет слоёв с `.env`, `src/test/`, `.git/`.
- [ ] Контейнер запускается с `--memory=512m` без OOMKill на warm-up.
- [ ] `HEALTHCHECK` возвращает `0` после полного старта приложения.
- [ ] `trivy image <image>` (или эквивалент) — 0 CRITICAL уязвимостей.
- [ ] `USER` в финальном stage — не root (`docker inspect <image> | jq '.[].Config.User'` → не пусто и не 0).

## Ожидаемый результат

После применения skill'а: multi-stage Dockerfile с layered JAR'ами, non-root user, container-aware JVM heap, HEALTHCHECK, образ в 150-200 MB на Alpine базе, воспроизводимая сборка с кэшируемыми слоями. Деплой в K8s не зависит от heap-size в `-Xmx` — автоматически адаптируется к `resources.limits.memory`.
