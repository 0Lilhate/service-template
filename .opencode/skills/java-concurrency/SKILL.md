---
name: java-concurrency
description: Java concurrency patterns for Spring Boot services — thread safety, sync primitives (synchronized / Lock / ReadWriteLock), atomics (CAS, LongAdder), concurrent collections, BlockingQueue, CompletableFuture composition, executor sizing, Virtual Threads (Java 21+), Spring @Async, Spring Batch multi-threading, deadlock/race/contention review.
origin: ECC
---

# Java Concurrency

Operational skill for multi-threaded code in Spring Boot services. Covers thread-safety decisions, synchronization primitives, executor configuration, Virtual Threads (Java 21+), `CompletableFuture` composition, and concurrency code review. Written for enterprise backend work — not a textbook.

## When to use

- Code touches threads, executors, `@Async`, `@Scheduled`, `CompletableFuture`, `synchronized`, `Lock`, `Atomic*`, `BlockingQueue`.
- Kafka consumer with parallel partition processing.
- Spring Batch step with multi-threaded reader/writer.
- Reviewing PR that introduces shared mutable state.
- Diagnosing «works locally, fails under load» class of bugs (race, deadlock, lost update).
- Deciding between platform threads vs Virtual Threads for a new async workload.

## When NOT to use

- Single-threaded request handling with standard Spring MVC controller — framework already handles request isolation.
- Pure CPU profiling / GC tuning → `skill: java-performance`.
- Spring Async-bean configuration without custom thread-safety concern → `skill: springboot-patterns` Async section already covers the bean setup.
- Kafka consumer review for DLQ / idempotency / retries → `@integration-reviewer` agent (concurrency is one aspect among many).
- «Просто добавить многопоточку для ускорения» без анализа — часто правильный ответ: не делать (см. «Когда НЕ применять многопоточность» ниже).

## Step-by-step process

1. **Decide: нужна ли вообще concurrency.** 80% случаев «медленно» решается JPA fix'ом, кешированием или индексом — не потоками. См. «Когда НЕ применять» ниже.
2. **Classify workload:** CPU-bound / I/O-bound / mixed. От этого зависит executor и пул-сайзинг.
3. **Identify shared mutable state** — что пишется из нескольких потоков? Если ничего — thread-safety не проблема.
4. **Pick the right primitive** — immutability > atomic > synchronized > Lock > concurrent collection (в порядке предпочтения).
5. **Bound the executor** — никогда unbounded (`SimpleAsyncTaskExecutor`, `newCachedThreadPool` без лимитов).
6. **Protect against hangs** — `CompletableFuture.orTimeout`, `tryLock(timeout)`, `poll(timeout)` вместо blocking без лимита.
7. **Review for deadlock / race / contention** — см. review checklist в конце.
8. **Verify** — stress test / thread dump при нагрузке / async-profiler lock mode (`skill: java-performance` для диагностики).

---

## 1. Thread-safety thinking

Priority-ladder — от самого безопасного к самому рискованному:

1. **Immutability** — `record`, `final` fields, неизменяемые коллекции (`List.copyOf`, `Map.copyOf`). 0 concurrency bugs by design.
2. **Thread confinement** — данные принадлежат одному потоку (request scope, `ThreadLocal` с правильной чисткой).
3. **Atomic operations** — `AtomicInteger`, `AtomicReference`, `LongAdder` для счётчиков.
4. **Concurrent collections** — `ConcurrentHashMap`, `CopyOnWriteArrayList`.
5. **Synchronized blocks** — для простых critical sections с коротким hold time.
6. **Lock / ReadWriteLock** — для сложной синхронизации (timeout, fairness, read-heavy).
7. **Messaging / queues** — передавать сообщения между потоками вместо shared state (`BlockingQueue`).

**Если можно подняться по ladder выше — поднимайся.** Не переусложняй синхронизацию там, где хватает immutable-объекта.

## 2. Synchronization primitives

### `synchronized` и `volatile`

```java
// synchronized — implicit monitor, short critical sections
public synchronized void increment() { counter++; }

// volatile — гарантирует visibility, НЕ atomicity
private volatile boolean running = true;  // OK для shutdown flag
private volatile int counter;              // НЕ OK для counter++ — тут гонка
```

**Правило:** `volatile` — только для single-writer / read-anywhere флагов (`boolean running`). Любая операция read-modify-write (`counter++`, `list.add`) требует atomic или Lock.

### `ReentrantLock`

```java
ReentrantLock lock = new ReentrantLock();     // non-fair по умолчанию
// ReentrantLock lock = new ReentrantLock(true); // fair — медленнее, но без starvation

if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try {
        // critical section
    } finally {
        lock.unlock();                         // ALWAYS в finally
    }
} else {
    // acquire failed — fall back / error
}
```

Применять вместо `synchronized` когда:
- нужен **timeout** на acquire (`tryLock(timeout, unit)`);
- нужна **fairness** (FIFO queue of waiters — цена: throughput);
- нужно **interruptible locking** (`lockInterruptibly`);
- нужно разделить **lock и unlock на разные методы** (осторожно — легко забыть unlock).

### `ReadWriteLock` / `StampedLock`

Когда **reads сильно превалируют над writes** (соотношение 10:1+).

```java
ReadWriteLock rwLock = new ReentrantReadWriteLock();
rwLock.readLock().lock();  try { return data.get(key); }  finally { rwLock.readLock().unlock(); }
rwLock.writeLock().lock(); try { data.put(key, value); }  finally { rwLock.writeLock().unlock(); }
```

`StampedLock` (Java 8+) — быстрее в read-mostly сценариях через optimistic read, но сложнее в использовании.

## 3. Atomics, CAS, `LongAdder`

```java
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();                   // atomic ++
counter.compareAndSet(expected, newValue);    // CAS — основа lock-free алгоритмов

AtomicReference<State> state = new AtomicReference<>(State.INITIAL);
state.updateAndGet(prev -> prev.transition()); // lambda — retry при CAS failure

// LongAdder — быстрее AtomicLong в high-contention счётчиках (striped counters)
LongAdder requestCount = new LongAdder();
requestCount.increment();                    // HOT path
long total = requestCount.sum();             // SLOW path (агрегация, редко)
```

**Правило выбора:**
- Одна переменная, redkaja contention → `AtomicInteger`/`AtomicLong`.
- Counter с высокой contention (metrics, request count) → `LongAdder` / `DoubleAdder`.
- Reference с retry-logic → `AtomicReference.updateAndGet`.
- Complex state → Lock или `synchronized`.

## 4. Concurrent collections

| нужно | коллекция | когда |
|-------|-----------|-------|
| Key-value thread-safe map | `ConcurrentHashMap` | default выбор; lock-striped |
| Read-heavy list | `CopyOnWriteArrayList` | редкие писатели, частые читатели (listeners, config) |
| Thread-safe queue (unbounded) | `ConcurrentLinkedQueue` | lock-free, без backpressure |
| Producer-consumer | `BlockingQueue` (`ArrayBlockingQueue` / `LinkedBlockingQueue`) | см. ниже |
| Priority queue thread-safe | `PriorityBlockingQueue` | обработка по приоритету |
| Delay-based processing | `DelayQueue` | scheduled задачи |
| Thread-safe set | `ConcurrentHashMap.newKeySet()` | вместо `Collections.synchronizedSet` |

**Не делай:** `Collections.synchronizedMap(new HashMap<>())` — coarse-grained locking; `ConcurrentHashMap` лучше почти всегда.

**Важно:** iteration over `ConcurrentHashMap` — weakly consistent (может пропустить / показать concurrent updates). Для snapshot — `new HashMap<>(concurrentMap)`.

## 5. BlockingQueue patterns

Producer-consumer через очередь — один из безопасных способов обмена между потоками.

```java
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000);  // BOUNDED — backpressure

// Producer
queue.put(task);                       // blocks if full
queue.offer(task, 5, TimeUnit.SECONDS); // timeout fallback

// Consumer
Task t = queue.take();                  // blocks if empty
Task t = queue.poll(1, TimeUnit.SECONDS); // timeout — не зависнуть навечно
```

**Правило:** всегда **BOUNDED** queue (`ArrayBlockingQueue` с capacity или `LinkedBlockingQueue(capacity)`). Unbounded (`new LinkedBlockingQueue<>()` без size) — тихий источник OOM под нагрузкой.

## 6. Executors — sizing и конфигурация

### Thread pool sizing

| Workload | Formula | Пример |
|----------|---------|--------|
| CPU-bound | `cores` | 8 threads на 8-core машине |
| I/O-bound | `cores × (1 + wait/compute)` | ~80 для 10:1 wait-to-compute |
| Mixed | старт с I/O formula, профилирование под реальной нагрузкой | — |
| Virtual Threads | без ограничения пула — by design | см. секцию ниже |

### Bounded `ThreadPoolExecutor`

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    10,                                            // core
    50,                                            // max
    60L, TimeUnit.SECONDS,                         // keepAlive
    new ArrayBlockingQueue<>(1000),                // bounded queue
    new ThreadFactoryBuilder().setNameFormat("order-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy()      // backpressure на сабмиттера
);
```

Rejected-execution handler — выбирай осознанно:
- `CallerRunsPolicy` — backpressure, тормозит submitter'а. Default выбор для batch.
- `AbortPolicy` — throws; fast-fail для critical workflow.
- `DiscardPolicy` — молча дропает; только для best-effort задач (metrics).
- `DiscardOldestPolicy` — заменяет старые; только для latest-wins сценариев.

### Spring: не `new Thread()`, а `ThreadPoolTaskExecutor` bean

См. `skill: springboot-patterns` Async section — там bounded `ThreadPoolTaskExecutor` bean с `@Async("taskExecutor")`. Не дублирую здесь.

### `ForkJoinPool` и `parallelStream`

```java
// Дефолтный parallelStream использует ForkJoinPool.commonPool() — SHARED между всем JVM
List<X> result = items.parallelStream().map(...).toList();

// Изолированный ForkJoinPool под свою задачу
ForkJoinPool pool = new ForkJoinPool(8);
List<X> result = pool.submit(() -> items.parallelStream().map(...).toList()).join();
```

**Правило:** никогда не использовать `parallelStream` на коде, который делает I/O / DB / HTTP — он займёт common pool и положит остальные `parallelStream` в JVM. Для I/O — выделенный executor или Virtual Threads.

## 7. CompletableFuture composition

```java
CompletableFuture<Order> future = CompletableFuture
    .supplyAsync(() -> fetchOrder(id), executor)       // явный executor
    .thenCompose(order -> fetchCustomerAsync(order.customerId()))  // flatMap
    .thenApply(enriched -> enriched.normalize())        // map
    .exceptionally(ex -> Order.empty())                 // ОБЯЗАТЕЛЬНО
    .orTimeout(5, TimeUnit.SECONDS);                    // ОБЯЗАТЕЛЬНО

// Combine two independent futures
CompletableFuture<Result> combined = user.thenCombine(orders, Result::new);

// Wait for all / any
CompletableFuture<Void>  all  = CompletableFuture.allOf(f1, f2, f3);
CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3);

// handle — получает (result, throwable), заменяет exceptionally+thenApply
future.handle((value, ex) -> ex != null ? fallback() : value);

// whenComplete — observe, не трансформирует
future.whenComplete((value, ex) -> metrics.record(value, ex));
```

**Мандатные элементы:**
- **Всегда `orTimeout` / `completeOnTimeout`.** Без timeout'а chain может висеть вечно (blocking downstream).
- **Всегда `exceptionally` / `handle`.** Unhandled exception в CompletableFuture не выбрасывается, а хранится внутри — silent failure.
- **Всегда explicit `executor`** в `supplyAsync` / `runAsync`. Default `ForkJoinPool.commonPool()` — shared; смешивать I/O и CPU там нельзя.

**`thenApply` vs `thenCompose`** — `thenApply` для map (`Function<T,R>`), `thenCompose` для flatMap когда лямбда возвращает `CompletableFuture` (иначе получишь `CompletableFuture<CompletableFuture<R>>`).

## 8. Virtual Threads (Java 21+)

### Что это

Lightweight threads, managed by JVM. Thousands/millions одновременно. **Не заменяют платформенные потоки для CPU-bound** — предназначены для I/O-heavy concurrency без thread-pool-tuning.

```java
// Low-level
Thread.ofVirtual().name("vt-order").start(() -> processOrder(id));

// Executor
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10_000).forEach(i ->
        executor.submit(() -> processRequest(i)));
}
```

### Spring integration (Spring Boot 3.2+)

```java
@Bean(name = "virtualTaskExecutor")
AsyncTaskExecutor virtualTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
}
```

Либо в `application.yml`:
```yaml
spring.threads.virtual.enabled: true
```
это превращает Tomcat / Jetty / scheduling в virtual-thread backed.

### Limitations — жёсткие ограничения

- **НЕ использовать внутри `synchronized`** — carrier thread pins'ится, вся польза теряется. Заменяй на `ReentrantLock`.
- **НЕ использовать для CPU-bound.** Virtual threads — про блокирующий I/O. CPU-bound — обычный `newFixedThreadPool(cores)`.
- **`ThreadLocal` + Virtual Threads = memory overhead.** Миллион VT × ThreadLocal data = heap pressure. Рассмотри `ScopedValue` (JEP 446, preview в JDK 21-22, final в 25+).
- **JNI / native calls** тоже pins carrier thread — аналогично `synchronized`.
- **`stop()`, `suspend()`, `destroy()`** — deprecated и для VT недоступны.
- **Deep stack** — virtual thread ленится на unmount, при deep stack всё равно занимает память.

### Когда применять

- HTTP endpoint вызывает 5 downstream REST API последовательно и ждёт — идеально для VT.
- Kafka consumer с blocking per-message обработкой — VT per message.
- Spring Batch chunk с blocking writer — VT executor.

### Когда НЕ применять

- CPU-intensive обработка (encryption, compression, image processing).
- Код с `synchronized` на hot path — перепиши на Lock или останься на platform threads.
- Простые fire-and-forget задачи — обычный `@Async` достаточен.

## 9. Spring Batch + Virtual Threads

```java
@Configuration
public class VirtualThreadBatchConfig {

    @Bean
    public TaskExecutor batchTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public Job accountProcessingJob(JobRepository jobRepository, Step step) {
        return new JobBuilder("accountProcessingJob", jobRepository)
            .start(step)
            .build();
    }

    @Bean
    public Step accountProcessingStep(
        JobRepository jobRepository,
        PlatformTransactionManager tx,
        ItemReader<Input> reader,
        ItemProcessor<Input, Output> processor,
        ItemWriter<Output> writer,
        TaskExecutor batchTaskExecutor
    ) {
        return new StepBuilder("accountProcessingStep", jobRepository)
            .<Input, Output>chunk(100, tx)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .taskExecutor(batchTaskExecutor)  // VT pool
            .build();
    }
}
```

**Caveats:**
- `ItemProcessor` / `ItemWriter` должны быть thread-safe — multi-threaded step вызывает их concurrently.
- `ItemReader` **не** thread-safe по умолчанию — оберни в `SynchronizedItemStreamReader` или используй `JdbcPagingItemReader` (native thread-safe).
- Не использовать `synchronized` в processor (pin'ит VT carrier).
- Chunk size — баланс: слишком маленький = overhead tx commit; слишком большой = long rollback при failure. Start with 100, измерь.

## 10. Spring `@Async` и `@Scheduled`

```java
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class NotificationService {
    @Async("taskExecutor")    // ОБЯЗАТЕЛЬНО named executor
    public CompletableFuture<Void> send(Notification n) { /* ... */ }
}

@Scheduled(fixedDelayString = "PT30S")
public void cleanup() { /* ... */ }
```

**Правила:**
- `@Async` без `"taskExecutor"` = `SimpleAsyncTaskExecutor` — unbounded threads, OOM под нагрузкой.
- `@Scheduled(fixedRate=...)` с задачей дольше периода → overlapping executions. Используй `fixedDelay` или оберни в `@Async` + lock.
- Multi-instance deploy → `@Scheduled` выполнится на всех инстансах. Нужен distributed lock (ShedLock, Redisson) или leader election.
- `@Async` self-invocation (метод вызывает другой `@Async` метод того же бина) — обойдёт proxy, выполнится синхронно. Разделяй на 2 бина.

## 11. Common pitfalls — race, deadlock, contention

### Race condition

```java
// BUG: non-atomic
if (map.containsKey(key)) return map.get(key);
return map.computeIfAbsent(key, this::expensiveCompute);

// FIX: использовать сам computeIfAbsent (atomic по контракту ConcurrentHashMap)
return map.computeIfAbsent(key, this::expensiveCompute);
```

Симптомы: «не воспроизводится локально, падает под нагрузкой», «иногда дубль записывается».

### Deadlock

Классика: два потока берут locks в разном порядке (`A→B` и `B→A`).

**Fix:** lock ordering — всегда acquire locks в одном глобальном порядке (например, по hash / по id). Или `tryLock(timeout)` + fallback.

**Диагностика:** `jcmd <pid> Thread.print` / `jstack -l <pid>` → ищи `Found one Java-level deadlock`.

### Starvation / contention

Симптом: pool full + queue full + requests waiting, при этом CPU не 100%.

Причины:
- Единственный `ReentrantLock` на hot path.
- `synchronized` method вокруг long-running I/O.
- Fair lock при high throughput (снижает throughput ради fairness).

**Fix:** уменьшить критическую секцию; перейти на lock-striped (`ConcurrentHashMap`); async + messaging вместо shared state.

### Lost wake-ups / spurious wake-ups

```java
// WRONG
if (!condition) queue.wait();  // может пропустить сигнал

// CORRECT
while (!condition) queue.wait();
```

Всегда `while`, не `if`, при `wait()` / `Condition.await()`.

### `ThreadLocal` leak

```java
private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

public void handleRequest(Request r) {
    CURRENT_USER.set(r.user());
    try {
        processInternal();
    } finally {
        CURRENT_USER.remove();   // ОБЯЗАТЕЛЬНО в finally
    }
}
```

Без `remove()` ThreadLocal хранится всё время жизни потока. В thread pool это = утечка.

## 12. Когда НЕ применять многопоточность

- **Задача I/O-bound и один downstream** — достаточно async I/O (`WebClient`, R2DBC). Не ускорит.
- **Simple CRUD endpoint** — Spring MVC уже параллелит запросы.
- **Нет measured bottleneck** — сначала профилируй (`skill: java-performance`). 80% «медленно» — не threads.
- **Критичная корректность без тестов на concurrency** — bug будет найден в проде.
- **Работа со legacy кодом без thread-safety analysis** — добавить потоки к коду, где кто-то мутирует shared state без sync — гарантированный race.
- **Batch размером 100-1000 записей** — sequential быстрее из-за thread overhead.

## 13. Concurrency code review checklist

Когда ревьюишь PR с threading:

- [ ] Все shared mutable state либо immutable, либо защищены (`Atomic*`, `synchronized`, `Lock`, concurrent collection).
- [ ] Все executors bounded (queue capacity установлен).
- [ ] Все `@Async` явно указывают named executor.
- [ ] Все `CompletableFuture.supplyAsync` передают executor.
- [ ] Все `CompletableFuture` chains заканчиваются `.orTimeout(…)` + `.exceptionally(…)` / `.handle(…)`.
- [ ] Нет `synchronized` на hot path при использовании Virtual Threads.
- [ ] Нет `parallelStream` на коде с I/O.
- [ ] Locks всегда в `try/finally` с `unlock()` в finally.
- [ ] `wait()` / `Condition.await()` всегда в `while`-loop.
- [ ] `ThreadLocal.remove()` в `finally` при использовании в request-scoped коде.
- [ ] `@Scheduled` в multi-instance deploy защищён distributed lock (ShedLock / Redisson).
- [ ] `BlockingQueue` всегда bounded; `put`/`take` имеют timeout-fallback где релевантно.
- [ ] `parallelStream` не на коде, делающем DB/HTTP.
- [ ] Для multi-threaded Spring Batch step: `ItemReader` thread-safe (обёрнут в `SynchronizedItemStreamReader` или natively thread-safe).

## Guardrails — чего не делать

- **Не использовать `Thread.stop()`, `Thread.suspend()`, `Thread.destroy()`** — deprecated, unsafe, в VT недоступны.
- **Не использовать double-checked locking без `volatile`** — broken memory model. Просто используй `ConcurrentHashMap.computeIfAbsent` или `AtomicReference`.
- **Не использовать `Executors.newFixedThreadPool(Integer.MAX_VALUE)` / `newCachedThreadPool()` без bounds** — OOM под нагрузкой.
- **Не смешивать I/O и CPU в одном `ForkJoinPool.commonPool`** — I/O блокирует common pool, parallelStream по всему JVM тормозит.
- **Не использовать `synchronized` на hot path с Virtual Threads** — pin'ит carrier thread.
- **Не добавлять потоки «на всякий случай»** — без measured baseline это spekulation. См. `skill: java-performance` для профилирования.
- **Не использовать `System.currentTimeMillis()` для timeout'ов на границе час/сутки** — clock может прыгнуть. Используй `System.nanoTime()`.

## Typical mistakes

| симптом | причина | фикс |
|---------|---------|------|
| Bug «не воспроизводится локально, падает под нагрузкой» | race condition на shared mutable state | audit по checklist'у выше; `ConcurrentHashMap.computeIfAbsent`; atomics |
| Thread pool забит, CPU = 30%, requests висят | blocking I/O в executor'е для CPU-bound | разделить на два пула (CPU + I/O); или перевести I/O на VT |
| OOMKilled под нагрузкой, heap в норме | unbounded thread pool = unbounded native memory (thread stacks) | bounded `ThreadPoolExecutor` |
| `@Async` метод никогда не попадает в пул | нет `@EnableAsync` или self-invocation | добавить `@EnableAsync`; разделить на 2 бина |
| `@Scheduled` выполняется на 3 pod'ах одновременно | нет distributed lock | ShedLock / Redisson; или leader election |
| `parallelStream()` замедлил batch | работа в common pool + задача I/O-bound | isolated executor; или Virtual Threads |
| CompletableFuture chain молча «пропал» | unhandled exception | `.exceptionally(…)` / `.handle(…)` обязательно |
| Virtual Thread performance такая же, как platform threads | `synchronized` на hot path или JNI-call | переписать на `ReentrantLock`; профилировать pinning: `-Djdk.tracePinnedThreads=full` |
| Deadlock в prod | lock ordering нарушен | `jstack` для анализа; ввести lock ordering policy; `tryLock(timeout)` с fallback |

## Verification checklist (после внедрения concurrency)

- [ ] Stress test под 2-3× expected production load — нет thread pool exhaustion, нет queue overflow, нет rejected executions.
- [ ] Thread dump под нагрузкой (`jcmd <pid> Thread.print`) — нет deadlock, нет thread starvation.
- [ ] Async-profiler в `lock` mode — нет unexpected contention (`skill: java-performance` section 3).
- [ ] `-Djdk.tracePinnedThreads=full` при использовании Virtual Threads — нет pin'ов на hot path.
- [ ] Chaos test — инъекция delays в downstream; chain-ы корректно таймаутят.
- [ ] Coverage 80%+ на concurrent classes (`skill: springboot-tdd`).

## Expected result

После применения skill'а: потокобезопасный код с обоснованным выбором примитивов (immutable > atomic > sync > lock), bounded executors с explicit rejected-handler policy, все CompletableFuture chains имеют timeout + error handler, Virtual Threads применены только на I/O-bound коде без `synchronized`, review-checklist пройден. Нет unbounded pools, нет unhandled async exceptions, нет deadlock под нагрузкой.

Для diagnosis existing concurrency issues → `skill: java-performance` section 3 (CPU / lock profiling).
