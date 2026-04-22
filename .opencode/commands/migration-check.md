---
description: Safety review for Flyway/Liquibase migrations
agent: migration-reviewer
subtask: true
---

# /migration-check

Safety review for database migrations. Use before merging any PR that touches `src/main/resources/db/migration/` or `src/main/resources/db/changelog/`.

## Usage

```
/migration-check                           # all migration files changed on current branch
/migration-check <file-or-glob>            # explicit target
/migration-check --range HEAD~5..HEAD      # migrations changed in range
```

$ARGUMENTS

## What you must do

1. Determine the target migration files:
   - If `$ARGUMENTS` empty: `git diff --name-only origin/main...HEAD -- '**/db/migration/**' '**/db/changelog/**'`.
   - If explicit file/glob: use as-is and verify each path exists.
   - If `--range <range>`: use `git diff --name-only <range> -- '**/db/migration/**' '**/db/changelog/**'`.

2. If the target set is empty, report "no migration changes in range" and stop.

3. For each migration file, apply the full `migration-reviewer` checklist (schema DDL, data migration, Flyway/Liquibase mechanics, cross-deploy safety).

4. Read `build.gradle.kts` to confirm Flyway or Liquibase is in use and which version.

5. Output the risk-classified report per file plus overall verdict:
   - SAFE / BACKWARD-COMPAT / LOCKING / BREAKING / NEEDS TWO-PHASE
   - required remediation
   - release ordering plan
   - rollback plan

## Do not

- Do not rewrite migration SQL. Propose changes, do not apply them.
- Do not review non-migration files here — route to `/review-diff`.
- Do not approve BREAKING or NEEDS TWO-PHASE without the explicit multi-release plan in the PR.

## Hard stop conditions

- Migration introduces `ADD COLUMN NOT NULL` without `DEFAULT` on a known non-empty table → block and demand the expand/backfill/contract plan.
- `CREATE INDEX` without `CONCURRENTLY` on PostgreSQL on a production-sized table → block.
- Liquibase changeset with duplicate `id` + `author` → block.
- Flyway version prefix not strictly greater than last applied in production → block.
