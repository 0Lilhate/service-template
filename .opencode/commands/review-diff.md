---
description: Run java-reviewer on the current git diff
agent: java-reviewer
subtask: true
---

# /review-diff

Review the current branch's Java/Kotlin changes using the `java-reviewer` agent. Use before opening a PR, before pushing to release, or after significant local refactoring.

## Usage

```
/review-diff                     # default: HEAD vs. tracking branch (or last commit if no tracking)
/review-diff HEAD~3..HEAD        # explicit range
/review-diff origin/main...HEAD  # branch triple-dot
/review-diff <base-branch>       # shorthand for <base-branch>...HEAD
```

$ARGUMENTS

## What you must do

1. Determine the diff range:
   - If `$ARGUMENTS` is empty, use `origin/main...HEAD` if `origin/main` exists, otherwise `HEAD~1..HEAD`.
   - If `$ARGUMENTS` looks like a branch name (no `..`), expand to `<arg>...HEAD`.
   - Otherwise pass `$ARGUMENTS` through as-is.

2. Gather the target file list:
   ```bash
   git diff --name-only <range> -- '*.java' '*.kt' '*.kts' 'src/main/resources/application*.yml' 'src/main/resources/application*.properties'
   ```

3. If the changed set is empty, report "no Java/Kotlin changes in range" and stop.

4. For each file in the list, run the `java-reviewer` checklist against the diff hunks. Do not review unchanged code. Do not propose unrelated refactors.

5. Output structured findings grouped by severity (CRITICAL / HIGH / MEDIUM / LOW) with:
   - file:line
   - issue
   - suggested fix (code pointer, not a rewrite)

6. End with an explicit verdict:
   - **Approve** — no CRITICAL/HIGH.
   - **Warn** — only HIGH with acknowledged mitigation.
   - **Block** — CRITICAL found; list them first.

## Do not

- Do not review migrations here — route to `/migration-check`.
- Do not review Kafka/HTTP integration flows here — route to the `integration-reviewer` agent or `/backend-java --mode deep`.
- Do not rewrite files. Review only.

## Verification commands you may run

- `./gradlew :<module>:check --no-daemon` — if you want to confirm findings compile/fail.
- `./gradlew :<module>:checkstyleMain --no-daemon` — if the finding is style-adjacent.
