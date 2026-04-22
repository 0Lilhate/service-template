---
name: java-performance
description: JVM performance diagnosis and tuning for Spring Boot services — GC pauses, heap / memory leaks, CPU hot-spots, micro-benchmarks. Use for production incident response, capacity planning, release tuning.
origin: ECC
---

# Java Performance

Concrete commands and decision paths for JVM performance work on Spring Boot services. Not a theoretical GC tutorial — every section lists what to run and what output to read.

## When to use

- **Production incident:** high p95/p99 latency, OOMKilled pods, frequent full GC, CPU saturation.
- **Capacity planning:** sizing pods before a traffic spike or new deploy.
- **Release tuning:** validating that a new build doesn't regress latency or memory.
- **Post-load-test analysis:** reading JFR / async-profiler output after a benchmark.

## When NOT to use

- Functional bugs (NPE, wrong result) → `java-reviewer` agent.
- Build / compile failures → `java-build-resolver` agent.
- Slow Kafka consumer / HTTP timeout / saga issues → `integration-reviewer` agent.
- JPA N+1 / slow queries → `jpa-patterns` + `postgres-patterns`.
- Test performance (flaky-slow-tests) → `springboot-tdd`.

## Step-by-step process

1. **Classify the symptom** (GC / heap / CPU / throughput) — see decision table.
2. **Capture evidence** with one of: jcmd snapshot, JFR recording, async-profiler flamegraph, heap dump.
3. **Read the artefact** with the tool appropriate to the class (GC log analyzer, Eclipse MAT, Flamegraph viewer).
4. **Form one hypothesis** and test with a minimal config change.
5. **Re-measure** — accept only if delta is reproducible across multiple runs.
6. **Revert experimental flags** before committing; only persist configuration you can justify.

### Symptom → tool decision table

| Symptom | First diagnostic | If confirmed |
|---------|-------------------|--------------|
| p99 latency spikes in regular intervals | GC log + `jcmd <pid> GC.heap_info` | section 1 (GC) |
| OOMKilled / `OutOfMemoryError` | Heap dump from container `/tmp/heapdump.hprof` + Eclipse MAT | section 2 (Memory) |
| Steady CPU 100% on one thread | async-profiler CPU flamegraph | section 3 (CPU) |
| Unknown method is 30% of total time | async-profiler wall-clock mode | section 3 (CPU) |
| Suspect code is slower than alternative | JMH benchmark | section 4 (Benchmarking) |
| Thread count grows over time | `jstack <pid>` + thread name grouping | section 2 (Memory — thread leak) |

---

## 1. GC diagnostics and tuning

### Enable GC logging

Spring Boot (Java 11+), add to `JAVA_OPTS` or `application.yml`:

```bash
# Unified logging (JDK 11+)
-Xlog:gc*,gc+age=trace,safepoint:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=20M
```

Output: pause times, heap regions, promotion rates. Do NOT parse manually — feed into **GCeasy.io** / **GCViewer** / **gceasy-cli** for visual timeline.

### Live snapshot without restart

```bash
# Process ID
jcmd -l | grep <app-name>

# Heap summary + region breakdown
jcmd <pid> GC.heap_info

# Force a full GC (prod: avoid; staging: OK for diagnostic)
jcmd <pid> GC.run

# Class histogram — top objects by count and bytes
jcmd <pid> GC.class_histogram | head -30
```

### GC selector decision

| Workload | Collector | Flag |
|----------|-----------|------|
| Default, balanced | **G1** (JDK 9+ default) | `-XX:+UseG1GC` |
| Low-latency, < 10 ms pause target, large heap | **ZGC** (JDK 17+) | `-XX:+UseZGC` |
| Throughput-max, batch | **Parallel** | `-XX:+UseParallelGC` |
| < 100 MB heap, simple service | **Serial** | `-XX:+UseSerialGC` |

### Tuning flags (only with evidence)

```bash
# G1 — pause target (JVM best-effort, not guarantee)
-XX:MaxGCPauseMillis=200

# G1 — avoid humongous allocations for large objects
-XX:G1HeapRegionSize=16m

# ZGC — reserve and soft-max
-XX:+UseZGC -Xmx4g -XX:SoftMaxHeapSize=3500m
```

**Do not tune blindly.** Each flag requires GC log evidence of the specific failure mode it addresses.

---

## 2. Memory analysis and heap dumps

### Get a heap dump

```bash
# Live — does NOT require OOM
jcmd <pid> GC.heap_dump /tmp/heap.hprof

# Via JMX / Actuator (Spring Boot + management.endpoints.web.exposure.include=heapdump)
curl -o heap.hprof http://localhost:8080/actuator/heapdump

# Auto-dump on OOM — enable in JVM_OPTS
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof
```

### Analyze with Eclipse MAT

Open `heap.hprof` → **Leak Suspects Report**. Look for:

- **Dominator tree** — which object retains the most memory.
- **GC roots** — path from root to suspected leak.
- **Duplicate strings** — if > 20% of string memory is duplicates, `String.intern()` candidate.
- **Classloader count** — growing classloaders → classloader leak (common in hot-reload / scripting).

### Common leak classes

| Pattern | Symptom | Investigation |
|---------|---------|---------------|
| `ConcurrentHashMap` growing unbounded | heap grows linearly with requests | who owns the map, is there eviction? LRU cache? |
| `ThreadLocal` not cleared | heap grows + thread count stable | `ThreadLocal.remove()` in filter after request |
| `HttpClient` / `WebClient` not reused | many FD + native memory | reuse single instance, not per-request |
| Static `List<T>` cache | permanent growth | move to bounded Caffeine / Guava cache |
| Disabled connection pool close | thread count grows | always `@PreDestroy` / try-with-resources |

### Native memory tracking (when heap looks fine but process memory grows)

```bash
# Enable at startup
-XX:NativeMemoryTracking=summary

# Snapshot
jcmd <pid> VM.native_memory summary

# Differential
jcmd <pid> VM.native_memory baseline
# ... later ...
jcmd <pid> VM.native_memory summary.diff
```

Buckets to watch: **Thread** (thread stack leak), **Internal** (JNI), **Class** (classloader leak).

---

## 3. CPU profiling

### async-profiler (recommended — low overhead, no safepoint bias)

```bash
# Install: download from https://github.com/async-profiler/async-profiler releases

# CPU flamegraph, 60 seconds, HTML output
./profiler.sh -d 60 -f /tmp/cpu-flame.html <pid>

# Wall-clock (for I/O-bound analysis)
./profiler.sh -d 60 -e wall -f /tmp/wall-flame.html <pid>

# Allocation profiling — who allocates most garbage
./profiler.sh -d 60 -e alloc -f /tmp/alloc-flame.html <pid>

# Lock contention
./profiler.sh -d 60 -e lock -f /tmp/lock-flame.html <pid>
```

Read flamegraph: **width = time spent**, **top frames = leaf methods executing CPU**. Hot frame that shouldn't be hot = optimization candidate.

### JFR (Java Flight Recorder — built-in, lower detail but zero install)

```bash
# Start 60s recording
jcmd <pid> JFR.start duration=60s filename=/tmp/recording.jfr

# Check status
jcmd <pid> JFR.check

# Stop manually if needed
jcmd <pid> JFR.stop name=<recording-id>
```

Open `.jfr` file in **JDK Mission Control (JMC)** → Method Profiling tab. Less sharp than async-profiler but no dependency install, works in distroless with JDK.

### Reading a flamegraph — what to look for

- Wide frames in `java.lang.String` / `StringBuilder` → string allocation hot-spot; consider `String.format` → direct concatenation or `StringBuilder`.
- Wide frames in `HashMap.resize` → undersized initial capacity; pre-size with `new HashMap<>(expectedSize)`.
- Wide frames in JSON serialization → consider caching serialization result or switching to streaming writer.
- Wide frames in lock acquisition → section 2 (lock contention analysis).
- Flat, uniform distribution with no dominant frame → likely I/O-bound; switch to wall-clock profile.

---

## 4. Micro-benchmarking with JMH

### Setup (Gradle Kotlin DSL)

```kotlin
// build.gradle.kts
plugins {
  id("me.champeau.jmh") version "0.7.2"
}
dependencies {
  jmh("org.openjdk.jmh:jmh-core:1.37")
  jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
jmh {
  warmupIterations.set(3)
  iterations.set(5)
  fork.set(2)
  resultFormat.set("JSON")
}
```

### Benchmark class structure

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgs = {"-Xms1g", "-Xmx1g"})
public class SerializationBenchmark {

  private byte[] payload;
  private ObjectMapper mapper;

  @Setup
  public void setup() {
    mapper = new ObjectMapper();
    payload = Files.readAllBytes(Path.of("sample.json"));
  }

  @Benchmark
  public Object jackson() throws IOException {
    return mapper.readValue(payload, Map.class);
  }

  @Benchmark
  public Object jacksonStreaming() throws IOException {
    try (JsonParser parser = mapper.getFactory().createParser(payload)) {
      // custom streaming logic
      return null;
    }
  }
}
```

Run: `./gradlew :module:jmh`.

### JMH guardrails

- **Always use `Blackhole.consume(result)`** or return result — otherwise JIT eliminates dead code.
- **Multiple forks** (≥ 2) — JIT profile differs run-to-run; one fork = unreliable.
- **Explicit `@Fork(jvmArgs=...)`** — lock heap size so GC is not a noise source.
- **Don't benchmark database / HTTP calls** — JMH is for microseconds. Integration latency → load testing tools (Gatling, k6).
- **Read JSON output, not console** — `Error` column in JSON = statistical confidence; if > 5% of score, increase iterations.

---

## Guardrails — чего нельзя делать

- **Не тюнить на прод без baseline.** Сначала измерение, потом change, потом повторное измерение. Без before/after — любая правка это суеверие.
- **Не использовать `System.gc()` в продакшене.** Игнорируется с `-XX:+DisableExplicitGC`, либо провоцирует stop-the-world. Для диагностики — `jcmd GC.run`.
- **Не оставлять `-XX:+PrintGCDetails` навсегда.** Это legacy JDK 8 флаг; в JDK 9+ заменён unified logging (`-Xlog:gc*`).
- **Не использовать `-XX:MaxHeapSize` в MB** для контейнеризованных сервисов — используй `MaxRAMPercentage` (см. `java-docker`).
- **Не запускать async-profiler без `-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints`** на JDK 11-16 — получишь sampling bias к safepoints.
- **Не удалять heap dump сразу после OOM** — это единственный артефакт для post-mortem. Копируй из pod/container до рестарта.
- **Не изменять несколько JVM-флагов одновременно.** Один change за итерацию; иначе вклад каждого флага непонятен.
- **Не делать `parallelStream()` на бизнес-логике,** использующей DB / HTTP / shared state — fork-join pool используется всем JVM, блокирующий taken стрим положит всю параллелизацию. Используй выделенный `ExecutorService`.

## Типичные ошибки

| симптом / вывод | причина | корректировка |
|------------------|---------|---------------|
| «Увеличил heap — стало хуже» | CMS / старый G1 на большой heap дают длинные паузы | профилировать паузы в GC log, а не только средний throughput; возможно, нужен ZGC |
| «Добавил `@Async` — упала пропускная способность» | `SimpleAsyncTaskExecutor` создаёт unbounded threads | bounded executor (см. `springboot-patterns` Async); virtual threads |
| «OOM на heap 4 GB, хотя в классе histogram всё маленькое» | native memory leak (direct buffer, JNI) | `NativeMemoryTracking=summary` + `jcmd VM.native_memory` |
| «JFR recording показывает ровный CPU без hot-spot» | приложение I/O-bound, CPU profile не релевантен | переключиться на wall-clock mode (`-e wall`) или on-CPU + off-CPU |
| «Flamegraph показывает `Unsafe.park` сверху» | поток блокирован, а не работает | не CPU hot-spot, а lock contention — `-e lock` profile |
| «JMH показывает `Benchmark: 1.5 ns/op`» | JIT dead-code-eliminated результат | вернуть значение или использовать `Blackhole.consume()` |
| «GC pause 500 ms раз в час на heap 2 GB» | mixed collection очищает старое поколение; evacuation failure | снизить `-XX:G1HeapRegionSize`, убедиться `InitiatingHeapOccupancyPercent` не слишком высокий |
| «Прод CPU 100%, локально — нет» | разная версия JDK или флаги; serialization / crypto hot-spot на прод-трафике | async-profiler на проде (low-overhead sampling), diff с локальным |

## Verification checklist (перед закрытием задачи)

- [ ] Есть **baseline** метрика (p95/p99 latency, throughput, GC pause p99, RSS).
- [ ] Есть **target** метрика с quantitative goal («p99 < 200 ms» вместо «сделать быстрее»).
- [ ] Изменение прогнано **минимум на 2 итерациях load test**'а, результаты воспроизводимы.
- [ ] Изменённые JVM-флаги документированы в `AGENTS.md` или `README` с обоснованием.
- [ ] Heap dump / flamegraph / JFR артефакты сохранены (минимум один before + один after).
- [ ] Нет экспериментальных флагов (`-XX:+UnlockDiagnosticVMOptions`, debug-фичи) в finalized конфиге.

## Ожидаемый результат

После применения skill'а: конкретная root cause с evidence (heap dump / flamegraph / GC log), целевой metric достигнут с воспроизводимым delta, изменения закоммичены в виде явного JVM-флага или code-level правки с комментарием про ROI. Никаких «попробуем увеличить heap — посмотрим что будет».
