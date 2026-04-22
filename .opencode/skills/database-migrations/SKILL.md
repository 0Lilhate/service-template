---
name: database-migrations
description: Database migration safety — Liquibase (XML changeSets, preconditions, rollback blocks, contexts/labels, tags, DATABASECHANGELOGLOCK) + PostgreSQL DDL patterns (expand-contract, CONCURRENTLY, batched backfill). Use when authoring or reviewing a schema change.
origin: ECC
---

# Database Migration Patterns

Safe, reversible schema and data changes for production Spring Boot systems using **Liquibase**. Language-agnostic SQL patterns apply anywhere; tool-specific sections cover Liquibase only — this project does not use Flyway.

## When to Activate

- Authoring a new changeSet (DDL or DML).
- Planning a zero-downtime column/table change.
- Reviewing a changeSet before merge.
- Debugging a stuck Liquibase run (checksum drift, stale lock, CONCURRENTLY-in-tx).

## Core Principles

1. **Every change is a changeSet** — never alter production schema manually.
2. **Migrations are forward-only in production** — rollback via a new corrective changeSet; never edit history.
3. **Schema and data migrations are separate** — never mix DDL and DML in one changeSet.
4. **Test against production-sized data** — a migration that works on 100 rows may lock on 10M.
5. **Applied changeSets are immutable** — once a changeSet ran in any env, its body must not change.

## Safety checklist

Before merging any changeSet:

- [ ] Has an explicit `<rollback>` block, or `<rollback>empty</rollback>` if intentionally irreversible.
- [ ] No full-table `ACCESS EXCLUSIVE` locks on large tables — uses concurrent operations.
- [ ] New columns are nullable OR have a constant default (PG 11+: instant, no rewrite).
- [ ] Indexes on existing large tables built `CONCURRENTLY` in a dedicated changeSet with `runInTransaction="false"`.
- [ ] Data backfill is a **separate changeSet** from the schema change.
- [ ] `preConditions` declare required state (`tableExists`, `columnExists`) for idempotency across envs.
- [ ] Tested against a restore of production data, not just schema.

Risk-classification (SAFE / BACKWARD-COMPAT / LOCKING / BREAKING) is the scope of the `migration-reviewer` agent. This skill is about **how to write** a migration; the agent classifies what was written.

## PostgreSQL DDL patterns (language-agnostic SQL)

Apply whether the SQL lives in a Liquibase `<sql>` changeSet, `<sqlFile>`, or a raw `.sql` file referenced from a changeSet.

### Adding a column safely

```sql
-- GOOD: Nullable, no rewrite
ALTER TABLE users ADD COLUMN avatar_url TEXT;

-- GOOD: PG 11+ — constant default is metadata-only, no rewrite
ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- BAD: NOT NULL without default on existing table → full rewrite + ACCESS EXCLUSIVE lock
ALTER TABLE users ADD COLUMN role TEXT NOT NULL;
```

### Adding an index without downtime

```sql
-- BAD: Blocks writes for the duration of the build
CREATE INDEX idx_users_email ON users (email);

-- GOOD: Concurrent build, no write lock
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);
```

`CREATE INDEX CONCURRENTLY` **cannot run inside a transaction**. In Liquibase that means a dedicated changeSet with `runInTransaction="false"` and (if the SQL has one statement without a terminator) `splitStatements="false"`.

### Rename column

Never rename directly. Apply the **Zero-downtime expand-contract** pattern below: three changeSets (add new → backfill → drop old) with app deploys between phases.

### Removing a column safely

1. Remove all application references.
2. Deploy app without the column.
3. Drop column in the next changeSet — not the same deploy.

### Large data backfill — batched

```sql
DO $$
DECLARE
  batch_size INT := 10000;
  rows_updated INT;
BEGIN
  LOOP
    UPDATE users SET normalized_email = LOWER(email)
    WHERE id IN (
      SELECT id FROM users WHERE normalized_email IS NULL
      LIMIT batch_size FOR UPDATE SKIP LOCKED
    );
    GET DIAGNOSTICS rows_updated = ROW_COUNT;
    EXIT WHEN rows_updated = 0;
    COMMIT;
  END LOOP;
END $$;
```

Put this in its own changeSet with `runInTransaction="false"` — the inner `COMMIT` is what makes batching meaningful, and Liquibase would otherwise wrap the whole block in one transaction.

## Liquibase

Formats: XML, YAML, or SQL-formatted. Pick **one** per repo; this project uses **XML changelogs + raw SQL for complex DDL**.

### File layout

```
src/main/resources/db/changelog/
├── master.xml                  # includes everything in order
├── v1.0/
│   ├── 001-create-users.xml
│   └── 002-create-orders.xml
└── v1.1/
    ├── 010-add-user-avatar.xml
    └── 011-index-orders-status.xml
```

`master.xml`:

```xml
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                                       http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">
    <includeAll path="db/changelog/v1.0" relativeToChangelogFile="false"/>
    <includeAll path="db/changelog/v1.1" relativeToChangelogFile="false"/>
</databaseChangeLog>
```

### changeSet identity — `(id, author, filename)` is the unique key

```xml
<changeSet id="010-add-user-avatar" author="alice">
    <addColumn tableName="users">
        <column name="avatar_url" type="TEXT"/>
    </addColumn>
    <rollback>
        <dropColumn tableName="users" columnName="avatar_url"/>
    </rollback>
</changeSet>
```

Liquibase computes a checksum over the changeSet body — **any edit to an applied changeSet changes the checksum** and breaks subsequent deploys with a validation failure. To fix content of an applied changeSet: author a **new** corrective changeSet. Never re-number, never edit, never delete an applied entry.

### Rollback blocks

Liquibase auto-generates rollback only for a small set of refactorings (`createTable`, `addColumn`, `createIndex`, etc.). For everything else — and always for `<sql>` changeSets — declare rollback explicitly:

```xml
<changeSet id="011-backfill-display-name" author="alice" runInTransaction="false">
    <sql>UPDATE users SET display_name = username WHERE display_name IS NULL;</sql>
    <rollback>
        <sql>UPDATE users SET display_name = NULL WHERE display_name = username;</sql>
    </rollback>
</changeSet>
```

If there is no meaningful rollback — state it explicitly:

```xml
<rollback>empty</rollback>
```

This is `<rollback>` with no content; Liquibase treats it as a deliberate no-op. Omitting `<rollback>` entirely for a `<sql>` changeSet makes `rollback` to a prior tag fail halfway.

### `runOnChange` — anti-pattern for regular DDL

`runOnChange="true"` re-executes the changeSet whenever its checksum changes. **Never use for ordinary DDL** — it silently violates immutability.

Legit uses (narrow): stored-procedure / view definitions that are idempotent (`CREATE OR REPLACE`). Even then, prefer a new changeSet per revision — it keeps history readable.

### Preconditions — idempotency + env guard

```xml
<changeSet id="020-add-legacy-flag" author="bob">
    <preConditions onFail="MARK_RAN">
        <not><columnExists tableName="users" columnName="legacy_flag"/></not>
    </preConditions>
    <addColumn tableName="users">
        <column name="legacy_flag" type="BOOLEAN" defaultValueBoolean="false">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>
```

- `onFail="MARK_RAN"` — skip and record as applied when the precondition already holds. Use when some envs acquired the column via a hotfix and the changeSet must be tolerant.
- `onFail="HALT"` (default) — stop the whole changelog on unmet precondition. Use when mismatch is a bug.

### Contexts and labels — env-specific changeSets

```xml
<changeSet id="030-seed-test-users" author="alice" context="test,dev">
    <sqlFile path="seed/test-users.sql"/>
</changeSet>
```

`context` evaluates against `--contexts` at runtime; `labels` is an alternative tag filter (`labels="demo OR qa"`). Use for seed data and env-specific fixtures — **never** for production schema branching.

### Tags — rollback points

```xml
<changeSet id="099-release-v1.1" author="alice">
    <tagDatabase tag="release-v1.1"/>
</changeSet>
```

`liquibase rollback release-v1.1` reverts everything applied after the tag. Works only if each of those changeSets has a working `<rollback>`.

### Liquibase + PostgreSQL — concurrent index gotcha

```xml
<changeSet id="040-idx-orders-status" author="bob" runInTransaction="false">
    <sql splitStatements="false">
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_status ON orders (status)
    </sql>
    <rollback>
        <sql>DROP INDEX IF EXISTS idx_orders_status</sql>
    </rollback>
</changeSet>
```

- `runInTransaction="false"` is mandatory — `CONCURRENTLY` fails inside a transaction.
- `IF NOT EXISTS` + `IF EXISTS` make the changeSet safe to retry after a partial crash.

### DATABASECHANGELOGLOCK — stale lock recovery

Liquibase takes a row lock in `DATABASECHANGELOGLOCK` at startup. A crashed process leaves a stale lock that blocks subsequent runs. To release:

```bash
liquibase releaseLocks
```

Or manually if the CLI is unavailable:

```sql
UPDATE databasechangeloglock
SET locked = false, lockedby = NULL, lockgranted = NULL
WHERE id = 1;
```

Do **not** truncate or drop `DATABASECHANGELOGLOCK` / `DATABASECHANGELOG` — that loses applied-state history.

### Spring Boot integration — minimum

```yaml
spring:
  liquibase:
    change-log: classpath:db/changelog/master.xml
    contexts: ${LIQUIBASE_CONTEXTS:prod}
    enabled: true
```

Keep `enabled: true` in prod — the application refuses to start on unapplied required changeSets, which is the right behaviour for a deployable artifact.

## Zero-downtime expand-contract

```
Phase 1: EXPAND
  - Add new column/table (nullable or with default)
  - Deploy app vN+1: writes to BOTH old and new
  - Backfill existing data (separate changeSet, batched)

Phase 2: MIGRATE
  - Deploy app vN+2: reads NEW, writes BOTH
  - Verify consistency (counts, sample diffs)

Phase 3: CONTRACT
  - Deploy app vN+3: reads and writes NEW only
  - Drop old column/table in a later changeSet (never in same deploy as the read-flip)
```

Never collapse phases. An EXPAND+CONTRACT combined deploy cannot be forward-rolled — the old column is already gone.

## Anti-patterns

| Anti-pattern | Why it fails | Fix |
|--------------|--------------|-----|
| Manual SQL in prod | No audit, unrepeatable across envs | Always a changeSet |
| Editing an applied changeSet | Checksum drift → startup validation failure | New corrective changeSet |
| `NOT NULL` without default | Full rewrite + `ACCESS EXCLUSIVE` lock | Nullable → backfill → add constraint |
| Non-concurrent index on large table | Blocks writes during build | `CONCURRENTLY` + `runInTransaction="false"` |
| Schema + data in one changeSet | Hard to rollback, long transactions | Split into two changeSets |
| Drop column before app stops reading | Runtime errors on next deploy | Remove code → deploy → drop next |
| `runOnChange` on regular DDL | Silently re-applies, breaks history | New changeSet per revision |
| Missing `<rollback>` on `<sql>` | Tag-rollback fails halfway | Always declare rollback or `<rollback>empty</rollback>` |
| Truncating `DATABASECHANGELOGLOCK` | Loses lock state, risks concurrent runs | `liquibase releaseLocks` |

## Related

| task | where |
|------|-------|
| Risk-classification of a specific diff | `migration-reviewer` agent |
| PostgreSQL index / query tuning | `postgres-patterns` |
| JPA / Hibernate mapping that drove the schema | `jpa-patterns` |
| Spring Data / `application.yml` beyond `spring.liquibase` | `springboot-patterns` |
