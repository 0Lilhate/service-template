---
name: java
description: Robust Java — null/equality/concurrency gotchas, collection choices, and common runtime traps. Use when writing or reviewing Java code outside framework-specific concerns.
origin: ECC
---

# Java

Language-level pitfalls that show up in production and in code review. Not Spring-specific — for framework patterns see `springboot-patterns`, `jpa-patterns`, `springboot-security`.

## Critical Rules

- `==` compares references, not content — always use `.equals()` for strings
- Override `equals()` must also override `hashCode()` — HashMap/HashSet break otherwise
- `Optional.get()` throws if empty — use `orElse()`, `orElseGet()`, or `ifPresent()`
- Modifying a collection while iterating throws `ConcurrentModificationException` — use `Iterator.remove()`
- Type erasure: generic type info gone at runtime — can't do `new T()` or `instanceof List<String>`
- `volatile` ensures visibility, not atomicity — `count++` still needs synchronization
- Unboxing null throws NPE — `Integer i = null; int x = i;` crashes
- `Integer == Integer` uses reference for values outside -128 to 127 — use `.equals()`
- Try-with-resources auto-closes — implement `AutoCloseable`, Java 7+
- Inner classes hold reference to outer — use static nested class if not needed
- Streams are single-use — can't reuse after terminal operation
- `thenApply` vs `thenCompose` — use `thenCompose` for chaining CompletableFutures
- Records are implicitly final — can't extend, components are final
- `serialVersionUID` mismatch breaks deserialization — always declare explicitly

## Collection Selection

| Need | Use | Reason |
|------|-----|--------|
| Indexed access | `ArrayList` | O(1) random access |
| Unique elements | `HashSet` | O(1) contains |
| Sorted unique | `TreeSet` | O(log n), ordered |
| Key-value pairs | `HashMap` | O(1) get/put |
| Thread-safe map | `ConcurrentHashMap` | lock-striped, no full synchronization |
| FIFO queue | `ArrayDeque` | faster than `LinkedList` as deque |

## Concurrency Primer

For deep concurrency guidance see the Async section in `springboot-patterns`. At language level:

- **Thread pool sizing:** CPU-bound tasks → pool size ≈ available cores. I/O-bound → `cores × (1 + wait/compute)`.
- **Virtual Threads (Java 21+):** prefer `Executors.newVirtualThreadPerTaskExecutor()` for high-fanout I/O; do NOT use inside `synchronized` blocks (pins carrier thread).
- **CompletableFuture timeouts:** always bound with `.orTimeout(Duration, TimeUnit)` to avoid hanging chains.
- **Atomic over synchronized for counters:** `AtomicInteger.incrementAndGet()` is cheaper than `synchronized` block for single-variable updates.
- **`ReentrantLock.tryLock(timeout, unit)`:** prefer over `synchronized` when you need timed acquisition or fairness.

## Common Runtime Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `NullPointerException` | method/field on null reference | validate at boundary, use `Optional`, apply `@NotNull`/`@Nullable` consistently |
| `ConcurrentModificationException` | mutate collection while iterating | use `Iterator.remove()`, `CopyOnWriteArrayList`, or collect-then-mutate |
| `ClassCastException` | unchecked cast, raw generics | parameterize generics, `instanceof` pattern matching (Java 16+) |
| `StackOverflowError` | unbounded recursion | convert to iterative, increase `-Xss` only as last resort |
| `OutOfMemoryError: Java heap space` | retained references, oversized caches | heap dump + `jcmd <pid> GC.heap_info`; see future `java-performance` skill |
