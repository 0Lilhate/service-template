---
name: java-microservices
description: Spring Cloud microservices design patterns — service decomposition (bounded contexts, DDD, strangler fig), saga (orchestration vs choreography), CQRS, event sourcing (store schema, projections, snapshots, upcasters), Resilience4j tuning (circuit breaker, bulkhead, retry). Use for architectural design / review of microservice boundaries and cross-service consistency.
origin: newskill
---

# Java Microservices — architecture reference

Production Spring Cloud microservices patterns. Use for **design decisions**, not implementation of individual endpoints. For operational review of individual Kafka / HTTP clients — see `AGENTS.md` → Integration flows, not this skill.

Scope is **architecture**, not reference chasing. Each section gives the decision framework + a minimal Spring-specific example. Deep API details — pull via `/docs "<library>" "<question>"`.

## When to read this skill

- Designing a new service or splitting an existing monolith.
- Reviewing service boundaries or cross-service data flow.
- Choosing between saga orchestration / choreography.
- Deciding if CQRS or event sourcing is justified.
- Tuning Resilience4j for an SLO.

Not for: single-endpoint Kafka consumer config (`AGENTS.md` integration flows), JVM tuning (`java-performance`), container packaging (`java-docker`).

## When NOT to use microservices

Splitting is expensive. Reject microservices when **any** of:

| anti-indicator | why |
|----------------|-----|
| Single team < 20 engineers, single product | Coordination overhead > gain; use modular monolith |
| No clear bounded contexts yet | You will split on wrong seams and pay re-merge cost |
| Strong cross-entity transactions dominate | Distributed transactions / sagas will be most of your work — monolith wins |
| Latency budget < 50 ms end-to-end | Each hop adds ms; service mesh adds more |
| Ops maturity low (no distributed tracing, no auto-rollback) | Debug cost will eclipse dev cost |

Prefer a **modular monolith** (package-level boundaries, internal-only API) until at least two of: team autonomy pressure, separate scaling profiles, separate SLO tiers, separate release cadences.

---

## Service decomposition

Decomposition precedes everything else. Wrong boundaries poison saga, CQRS, and projection design.

### Bounded contexts

A bounded context is the largest scope inside which a single ubiquitous language holds. "Customer" in Billing and "Customer" in Support are different aggregates — same name, different invariants, different schemas.

**Rule of thumb:** if two teams disagree on what a noun means, it is two bounded contexts.

Mapping output: a context map showing relationships — **Customer/Supplier**, **Conformist**, **Anticorruption Layer**, **Shared Kernel**, **Published Language**. Use the ACL pattern whenever integrating a legacy system you cannot change.

### DDD aggregates = service boundaries

An aggregate is the **consistency boundary** — invariants inside the aggregate are enforced transactionally, across aggregates they are enforced eventually.

A service owns **one or more aggregate roots** and their data. It never writes to another service's aggregates — only requests or publishes events.

```java
// Inside Order service — Order aggregate root
public class Order {
    private final OrderId id;
    private final List<OrderLine> lines;
    private OrderStatus status;

    // invariant: cannot add lines to shipped order
    public void addLine(Product product, int qty) {
        if (status == OrderStatus.SHIPPED) throw new IllegalStateException();
        lines.add(new OrderLine(product.id(), qty, product.price()));
    }
    // status transitions, compensation hooks, etc.
}
```

Cross-aggregate calls → async event or explicit command via outbox.

### Strangler fig — monolith decomposition

Do not rewrite. Route incrementally:

1. Put API gateway (Spring Cloud Gateway) in front of the monolith.
2. Identify a single bounded context to extract (lowest coupling first — auth, notifications, reporting).
3. Build new service; write path goes to new service; read path still hits monolith via ACL.
4. Backfill historical data via batch.
5. Flip read path. Verify parity on synthetic traffic.
6. Retire monolith code path. Repeat next context.

Anti-pattern: big-bang rewrite of all contexts in one release. Failure cost is total.

### Decomposition smell checks

- Service needs data from 3+ others per request → bad seam; merge or denormalise via read model.
- Deploys of service A require coordinated deploy of B → shared schema or wrong boundary.
- Service has its own DB + 2 others' DBs — wrong ownership.

---

## Saga — distributed transactions

Saga executes a business transaction as a sequence of local transactions; each step has a compensating action to undo earlier steps if a later step fails.

### Orchestration vs Choreography — decision

| axis | Orchestration | Choreography |
|------|---------------|--------------|
| Coordination | central orchestrator service | each service reacts to events |
| Visibility of flow | single place — easy to reason about | scattered across consumers |
| Coupling | orchestrator knows all participants | participants know event schemas only |
| Evolution | add step = modify orchestrator | add step = new consumer, no central change |
| Debugging | one log for the saga | must correlate across services |
| When to choose | complex flows, compensations, timeouts, human-in-loop | simple flows, 3-4 steps, autonomous teams |

Default to **orchestration** for anything with > 3 steps or any non-trivial compensation. Choreography scales poorly past 5 services — state becomes implicit and untestable.

### Saga state storage

State **must** be persisted — crashes during a saga are normal. Schema (PostgreSQL minimum):

```sql
CREATE TABLE saga_instance (
    saga_id         UUID PRIMARY KEY,
    saga_type       VARCHAR(128) NOT NULL,
    current_step    VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,      -- RUNNING | COMPENSATING | COMPLETED | FAILED
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lock_token      UUID,
    lock_expires_at TIMESTAMPTZ
);
CREATE INDEX idx_saga_status_updated ON saga_instance (status, updated_at) WHERE status IN ('RUNNING','COMPENSATING');
```

`lock_token` + `lock_expires_at` enable a recovery scanner to pick up stuck sagas.

### Orchestration saga — Spring skeleton

```java
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {
    private final PaymentClient payment;
    private final InventoryClient inventory;
    private final SagaRepository sagas;

    @Transactional
    public void start(OrderId id, OrderPayload payload) {
        SagaInstance s = sagas.create(id, "ORDER_PLACEMENT", payload);
        advance(s);
    }

    public void advance(SagaInstance s) {
        switch (s.currentStep()) {
            case "RESERVE_INVENTORY" -> inventory.reserveAsync(s);
            case "CHARGE_PAYMENT"    -> payment.chargeAsync(s);
            case "CONFIRM_ORDER"     -> s.complete();
            // ... compensation branch
        }
        sagas.save(s);
    }

    @EventListener
    public void onStepResult(StepResultEvent e) {
        SagaInstance s = sagas.findById(e.sagaId()).orElseThrow();
        if (e.success()) s.nextStep(); else s.beginCompensation();
        advance(s);
    }
}
```

### Compensating transactions + idempotency

Every step and every compensation **must** be idempotent. Network retries, crash recovery, and duplicate Kafka delivery will cause re-invocation.

Pattern: idempotency key = `(saga_id, step_name)`. Receiver stores `(idempotency_key, result)`; duplicate submissions return stored result without re-executing the side effect.

```java
@Transactional
public ChargeResult charge(UUID sagaId, String stepName, Money amount) {
    var key = new IdempotencyKey(sagaId, stepName);
    return idempotencyStore.findByKey(key)
        .orElseGet(() -> {
            var result = gateway.charge(amount);
            idempotencyStore.save(key, result);
            return result;
        });
}
```

Compensation is a **new business transaction**, not a SQL rollback. `refund` is not `DELETE FROM payment` — it is a new payment of opposite sign.

### Timeout & stuck-saga recovery

A background scheduler (Spring `@Scheduled`, ideally in a dedicated executor — see `java-concurrency`) scans for sagas stuck in `RUNNING` past a timeout:

```java
@Scheduled(fixedDelay = 30_000)
public void recoverStuckSagas() {
    var stuck = sagas.findStuck(Duration.ofMinutes(5));
    for (var s : stuck) {
        if (s.retryCount() >= MAX_RETRIES) { s.failTerminally(); continue; }
        orchestrator.advance(s);           // idempotent — safe to replay
    }
}
```

Always bound `retryCount` and surface terminally-failed sagas to a DLQ table for human review.

---

## CQRS — command / query separation

CQRS splits the model used for writes (**Commands**) from the model used for reads (**Queries**). Commands mutate the write model; queries read from a projection optimised for a screen.

### When to apply — and when not to

| apply | reject |
|-------|--------|
| Read / write ratios wildly asymmetric (10:1+) | Simple CRUD with 1:1 entity ↔ screen |
| Read needs joins across aggregates | Single aggregate view |
| Different availability tiers for read vs write | Same availability tier |
| Multiple read shapes for same aggregate | Single read shape |

CQRS adds a projection pipeline, eventual consistency, and rebuild procedures. Do not pay that tax on a form screen.

### Spring structure — handler separation

```java
// --- WRITE SIDE ---
public sealed interface OrderCommand
    permits CreateOrder, AddLine, SubmitOrder {}

@Service
public class OrderCommandHandler {
    @Transactional
    public OrderId handle(CreateOrder cmd) {
        var order = Order.create(cmd.customerId(), cmd.lines());
        orderRepo.save(order);
        events.publish(new OrderCreated(order.id(), order.snapshot()));
        return order.id();
    }
}

// --- READ SIDE ---
public record OrderSummaryView(UUID id, String customer, Money total, OrderStatus status) {}

@Service
public class OrderQueryHandler {
    private final JdbcClient jdbc;

    public List<OrderSummaryView> activeOrders(UUID customerId) {
        return jdbc.sql("""
            SELECT id, customer_name, total_cents, status
            FROM order_summary_view
            WHERE customer_id = :cid AND status IN ('OPEN','SUBMITTED')
            """)
            .param("cid", customerId)
            .query(OrderSummaryView.class).list();
    }
}
```

The read side **does not** touch the write aggregates. It reads a projection table maintained by event handlers.

### Read-model projections — event-driven vs on-demand

- **Event-driven projection** — a consumer subscribes to domain events and updates a projection table. Default choice for CQRS. Eventual consistency window = consumer lag.
- **On-demand projection** — materialised view / cached query, recomputed on read miss. Use when event volume is high and the view is rarely accessed.

Projection updater sketch:

```java
@KafkaListener(topics = "order.events", groupId = "order-summary-projector")
public void on(OrderEvent e) {
    switch (e) {
        case OrderCreated c -> summaryRepo.insert(c.id(), c.customer(), c.total(), OPEN);
        case OrderLineAdded l -> summaryRepo.incrementTotal(l.orderId(), l.price());
        case OrderSubmitted s -> summaryRepo.updateStatus(s.orderId(), SUBMITTED);
    }
}
```

Projector must be idempotent — Kafka may redeliver. Use `ON CONFLICT DO UPDATE` or track last processed offset.

### Consistency handling

User just posted a command and now reads — projection may lag. Options:
1. **Read-your-writes**: return the projected view from the command handler directly (write-through).
2. **Version token**: command returns a version; query blocks until projection reaches it.
3. **UX**: return acknowledgement, tell user "processing".

Option 3 is cheapest and usually acceptable. Do not fake consistency with sleeps.

---

## Event Sourcing

Store aggregate state as an ordered **sequence of events**, not as the current row. Current state = fold over events. Only adopt when you need full audit, time travel, or complex projections.

### Event store schema (PostgreSQL minimum)

```sql
CREATE TABLE event_store (
    global_position  BIGSERIAL    PRIMARY KEY,
    stream_id        UUID         NOT NULL,
    stream_version   BIGINT       NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    event_version    INT          NOT NULL DEFAULT 1,
    payload          JSONB        NOT NULL,
    metadata         JSONB        NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (stream_id, stream_version)
);
CREATE INDEX idx_event_stream ON event_store (stream_id, stream_version);
```

The `UNIQUE (stream_id, stream_version)` constraint is what gives you **optimistic concurrency** — two writers loading version 17 and both appending version 18 will have one commit succeed and one fail with a constraint violation. Convert that to a retry-or-fail at the repository layer.

### Projection rebuild

Because projections are derived, a bug in the projector ⇒ wipe and replay.

```
1. Pause consumers for the projection.
2. TRUNCATE projection tables (or create shadow tables).
3. Read events from global_position = 0 in batches of 10_000.
4. Apply updated projector. Track last applied position.
5. Resume consumers (they'll replay events past last position).
6. Cut over to new projection tables atomically.
```

Always design projections so rebuild is **routine**, not scary. If rebuild takes > 4 h on prod data, add snapshots (below) or partition by time.

### Snapshots for long streams

For aggregates with > 10k events, fold-from-zero is too slow. Take periodic snapshots:

```sql
CREATE TABLE snapshot (
    stream_id      UUID         PRIMARY KEY,
    stream_version BIGINT       NOT NULL,
    payload        JSONB        NOT NULL,
    taken_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Load: read snapshot, then apply events with `stream_version > snapshot.version`. Snapshot cadence: every N events (e.g., 100) or on scheduled job. Snapshots are derived — can be deleted and regenerated.

### Event versioning — upcasters

Domain events live forever in the store; their shape will change. Never rewrite history. Use **upcasters** that transform an old-version event JSON into current-version on read.

```java
public interface Upcaster {
    String fromType();
    int fromVersion();
    JsonNode upcast(JsonNode oldPayload);
}

@Component
public class OrderCreatedV1toV2 implements Upcaster {
    public String fromType() { return "OrderCreated"; }
    public int fromVersion() { return 1; }
    public JsonNode upcast(JsonNode v1) {
        var v2 = v1.deepCopy();
        ((ObjectNode) v2).put("currency", "USD");   // field added in V2
        return v2;
    }
}
```

Chain upcasters: V1→V2→V3. The stream is immutable; the upcaster pipeline reshapes on read.

Anti-pattern: mutating stored events to new schema. You lose the audit property that justified event sourcing in the first place.

---

## Resilience4j tuning

Default configs from the starter are fine for hello-world, not for production. Every resilience config must map to an **SLO** and a **downstream contract**.

### Circuit breaker

```yaml
resilience4j.circuitbreaker:
  instances:
    paymentGateway:
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 50            # last 50 calls
      minimumNumberOfCalls: 20         # don't open on 3-call sample
      failureRateThreshold: 50         # %; open at 50% failure
      slowCallRateThreshold: 80        # %; open at 80% slow
      slowCallDurationThreshold: 2s    # what counts as slow
      waitDurationInOpenState: 30s     # cool-off before half-open
      permittedNumberOfCallsInHalfOpenState: 5
      automaticTransitionFromOpenToHalfOpenEnabled: true
```

**Tuning cues.**
- `minimumNumberOfCalls` too low → flapping on normal jitter.
- `waitDurationInOpenState` must exceed the downstream's recovery SLA — else CB flips back to OPEN immediately.
- Use **separate CB instances per downstream** — one bad dependency must not open a CB for a healthy one.

### Bulkhead

```yaml
resilience4j.bulkhead:
  instances:
    paymentGateway:
      maxConcurrentCalls: 20
      maxWaitDuration: 100ms
```

Size `maxConcurrentCalls` by **Little's law**: `concurrency = throughput * latency`. For 100 rps avg, 200 ms p95: `≈ 20`. Add 20% headroom. Never set it higher than the downstream's concurrency budget.

Thread-pool bulkhead only when calls are blocking — prefer semaphore bulkhead with virtual threads (`java-concurrency`).

### Retry — with jitter, not raw exponential

```yaml
resilience4j.retry:
  instances:
    paymentGateway:
      maxAttempts: 3
      waitDuration: 500ms
      enableExponentialBackoff: true
      exponentialBackoffMultiplier: 2
      enableRandomizedWait: true
      randomizedWaitFactor: 0.5
      retryExceptions:
        - java.net.SocketTimeoutException
        - org.springframework.web.client.ResourceAccessException
```

Raw exponential without jitter causes **retry storms** — all retrying clients wake at the same instant, amplify load, crash downstream. `randomizedWaitFactor ≥ 0.5` is mandatory. Only retry **idempotent** calls.

### Compose correctly

Order from outermost to innermost:
`Retry → CircuitBreaker → Bulkhead → TimeLimiter → remote call`.

If Retry is inside CircuitBreaker, retries count as failures toward opening the breaker — that is usually wrong.

---

## Spring Cloud Gateway — condensed reference

For routing specifics, pull via `/docs "spring cloud gateway" "<question>"`. Minimum production-grade route:

```java
@Bean
public RouteLocator routes(RouteLocatorBuilder b) {
    return b.routes()
        .route("orders", r -> r
            .path("/api/orders/**")
            .filters(f -> f
                .stripPrefix(1)
                .circuitBreaker(c -> c.setName("orders-cb").setFallbackUri("forward:/fallback/orders"))
                .retry(cfg -> cfg.setRetries(2).setMethods(HttpMethod.GET))
                .requestRateLimiter(rl -> rl.setRateLimiter(redisRateLimiter())))
            .uri("lb://order-service"))
        .build();
}
```

Never retry POST/PUT without idempotency-key verification on the downstream.

---

## Observability — pointer

Tracing / MDC / structured logging lives in **`logging-patterns`** — do not duplicate here. Minimum required:

- `spring-boot-starter-actuator` + Micrometer Prometheus registry.
- Trace-id propagation in Kafka headers and HTTP headers (`traceparent`).
- Correlation-id in MDC for every inbound request / event.

See `logging-patterns` for MDC filters; `java-performance` for tuning SLO dashboards.

---

## Decision checklist before designing microservices

- [ ] Bounded contexts named, owned by teams, mapped with relationship labels.
- [ ] Each service owns one or more aggregates; no cross-service writes.
- [ ] For each cross-service flow: saga style chosen (orchestration vs choreography) with justification.
- [ ] Saga state is persisted with a recovery scanner + terminal failure path.
- [ ] Every external-effect step is idempotent with stored idempotency key.
- [ ] CQRS adopted only where read/write asymmetry or read-shape count justifies it.
- [ ] Event sourcing rejected by default; justify with audit/time-travel/projection needs.
- [ ] If ES adopted: event store has `UNIQUE(stream_id, version)`; upcaster pipeline in place; projection rebuild procedure documented and runnable.
- [ ] Every external call has a Resilience4j CB + bulkhead + timeout, sized per Little's law, per-downstream instance.
- [ ] Retry only on idempotent operations, with jitter, with bounded attempts.
- [ ] API gateway does not retry POST/PUT without idempotency key.

---

## Related skills

| task | skill |
|------|-------|
| Kafka consumer / producer operational config | `AGENTS.md` → Integration flows |
| Virtual Threads, executors, `@Async` wiring | `java-concurrency` |
| MDC, structured JSON logs, trace propagation | `logging-patterns` |
| Spring Boot app config, `@Configuration` | `springboot-patterns` |
| Circuit-breaker fallback + security concerns | `springboot-security` |
| Migration design for event store / projections | `database-migrations` |
| PostgreSQL-specific schema / index decisions | `postgres-patterns` |
| JFR / GC tuning for latency SLO | `java-performance` |

## Out of scope

- Individual Kafka-listener / HTTP-client review — route to integration review per `AGENTS.md`.
- Container/K8s packaging — see `java-docker`.
- Implementation of `@Configuration` beans / properties — see `springboot-patterns`.
- Non-Spring frameworks (Micronaut, Quarkus) — this skill assumes Spring Cloud.
