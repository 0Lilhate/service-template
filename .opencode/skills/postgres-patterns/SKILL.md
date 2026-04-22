---
name: postgres-patterns
description: PostgreSQL patterns — index types, data type choices, composite/covering/partial indexes, generic Row Level Security via current_setting (with Supabase note), cursor pagination, queue processing, connection pooling caveats (PgBouncer).
origin: ECC
---

# PostgreSQL Patterns

Quick reference for PostgreSQL best practices. Vendor-neutral — works on any managed or self-hosted Postgres deployment.

## When to Activate

- Writing SQL queries or migrations.
- Designing database schemas, choosing index types.
- Troubleshooting slow queries.
- Implementing Row Level Security.
- Setting up connection pooling (PgBouncer).

## Quick Reference

### Index Cheat Sheet

| Query Pattern | Index Type | Example |
|--------------|------------|---------|
| `WHERE col = value` | B-tree (default) | `CREATE INDEX idx ON t (col)` |
| `WHERE col > value` | B-tree | `CREATE INDEX idx ON t (col)` |
| `WHERE a = x AND b > y` | Composite | `CREATE INDEX idx ON t (a, b)` |
| `WHERE jsonb @> '{}'` | GIN | `CREATE INDEX idx ON t USING gin (col)` |
| `WHERE tsv @@ query` | GIN | `CREATE INDEX idx ON t USING gin (col)` |
| Time-series ranges | BRIN | `CREATE INDEX idx ON t USING brin (col)` |

### Data Type Quick Reference

| Use Case | Correct Type | Avoid |
|----------|-------------|-------|
| IDs | `bigint` | `int`, random UUID |
| Strings | `text` | `varchar(255)` |
| Timestamps | `timestamptz` | `timestamp` |
| Money | `numeric(10,2)` | `float` |
| Flags | `boolean` | `varchar`, `int` |

### Common Patterns

**Composite Index Order:**
```sql
-- Equality columns first, then range columns
CREATE INDEX idx ON orders (status, created_at);
-- Works for: WHERE status = 'pending' AND created_at > '2024-01-01'
```

**Covering Index:**
```sql
CREATE INDEX idx ON users (email) INCLUDE (name, created_at);
-- Avoids table lookup for SELECT email, name, created_at
```

**Partial Index:**
```sql
CREATE INDEX idx ON users (email) WHERE deleted_at IS NULL;
-- Smaller index, only includes active users
```

**RLS Policy — generic (current_setting):**
```sql
-- App sets the current user per connection / transaction:
--   SELECT set_config('app.current_user_id', '<uuid>', true);
-- (`true` = LOCAL to the current transaction)

CREATE POLICY orders_owner ON orders
  USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;  -- apply to table owners too
```

Use `true` as the second argument to `current_setting` so a missing setting returns `NULL` (row excluded) instead of raising an error. Grant direct read only to the application role; never expose the `BYPASSRLS` attribute in app code paths.

> **Supabase note:** Supabase projects expose an equivalent `auth.uid()` helper that reads the authenticated JWT; in that environment the policy becomes `USING ((SELECT auth.uid()) = user_id)`. The `SELECT` wrapper lets Postgres cache the call once per statement — outside Supabase, the same caching applies to any `STABLE` function or `current_setting`.

**UPSERT:**
```sql
INSERT INTO settings (user_id, key, value)
VALUES (123, 'theme', 'dark')
ON CONFLICT (user_id, key)
DO UPDATE SET value = EXCLUDED.value;
```

**Cursor Pagination:**
```sql
SELECT * FROM products WHERE id > $last_id ORDER BY id LIMIT 20;
-- O(1) vs OFFSET which is O(n)
```

**Queue Processing:**
```sql
UPDATE jobs SET status = 'processing'
WHERE id = (
  SELECT id FROM jobs WHERE status = 'pending'
  ORDER BY created_at LIMIT 1
  FOR UPDATE SKIP LOCKED
) RETURNING *;
```

### Anti-Pattern Detection

```sql
-- Find unindexed foreign keys
SELECT conrelid::regclass, a.attname
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
WHERE c.contype = 'f'
  AND NOT EXISTS (
    SELECT 1 FROM pg_index i
    WHERE i.indrelid = c.conrelid AND a.attnum = ANY(i.indkey)
  );

-- Find slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
WHERE mean_exec_time > 100
ORDER BY mean_exec_time DESC;

-- Check table bloat
SELECT relname, n_dead_tup, last_vacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC;
```

### Connection Pooling — PgBouncer

For Spring Boot services in front of Postgres, put **PgBouncer** between HikariCP and the DB when you need to keep PG `max_connections` low (managed DBs often cap it at 100-500) while supporting higher app-side concurrency.

- **Pool mode:** prefer **transaction** (not session) — each pooled backend is released at `COMMIT`/`ROLLBACK`, giving real connection reuse.
- **Prepared statements caveat:** in transaction mode, server-side prepared statements are not reusable across transactions — pgjdbc / HikariCP may log `prepared statement "S_1" does not exist` errors. Fix with JDBC URL flag `prepareThreshold=0` (pgjdbc) or PgBouncer 1.21+ `server_reset_query_always=0` + Postgres 14+ protocol-level prepared statements.
- **Session-scoped features break under transaction pooling:** `SET`, `SET LOCAL` (safe within one tx), `LISTEN/NOTIFY`, session advisory locks, temporary tables that outlive a tx — either keep them inside a single transaction or use session-mode pool.
- Size HikariCP `maximumPoolSize` ≤ PgBouncer `default_pool_size`; both must fit under Postgres `max_connections` with headroom for admin/migrations.

### Configuration Template

```sql
-- Connection limits (adjust for RAM)
ALTER SYSTEM SET max_connections = 100;
ALTER SYSTEM SET work_mem = '8MB';

-- Timeouts
ALTER SYSTEM SET idle_in_transaction_session_timeout = '30s';
ALTER SYSTEM SET statement_timeout = '30s';

-- Monitoring
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Security defaults
REVOKE ALL ON SCHEMA public FROM public;

SELECT pg_reload_conf();
```

## Related

| task | where |
|------|-------|
| Migration DDL safety review | agent: `migration-reviewer` |
| Liquibase changeSet authoring | skill: `database-migrations` |
| JPA/Hibernate mapping, lazy-loading, N+1 | skill: `jpa-patterns` |
| Spring Boot datasource / HikariCP wiring | skill: `springboot-patterns` |

---

*Originally derived from Supabase Agent Skills (credit: Supabase team, MIT License); adapted for vendor-neutral Postgres.*
