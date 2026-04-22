---
description: Analyze which modules depend on a given Gradle module (multi-module impact)
agent: build
---

# /impact

Map reverse dependencies in a multi-module Gradle (Kotlin DSL) build. Use before refactoring a shared module to know which downstream modules must rebuild and retest.

## Usage

```
/impact :orders-core
/impact orders-core
/impact <path-like-src/main/...>   # the command resolves the owning module
```

$ARGUMENTS

## What you must do

1. Resolve the target module:
   - If `$ARGUMENTS` starts with `:` — use as-is (`:orders-core`).
   - If `$ARGUMENTS` is a bare name — prepend `:` (`orders-core` → `:orders-core`).
   - If `$ARGUMENTS` is a source path — walk up from the path until you find the nearest directory containing `build.gradle.kts`; the module is its project path.
   - If `$ARGUMENTS` empty — stop and ask the user for a module.

2. Verify the module exists:
   ```bash
   ./gradlew projects --no-daemon | grep -E "\\+--- Project '<module>'"
   ```
   If not listed, report and stop.

3. Enumerate all project modules:
   ```bash
   ./gradlew projects --no-daemon
   ```
   Parse the list of `+--- Project ':xxx'`.

4. For each OTHER module, probe reverse dependency:
   ```bash
   ./gradlew :<other>:dependencyInsight --dependency <target-module-name> --configuration runtimeClasspath --no-daemon 2>&1 | grep -E "^project :|Requested by" | head -20
   ```
   Collect modules where `project :<target>` appears as a dependency.

5. Also probe `implementation` / `api` graph:
   ```bash
   ./gradlew :<target>:dependencies --configuration runtimeClasspath --no-daemon
   ```
   (forward deps — informational, not the main output)

6. Output:

```text
## Impact for :<target>

### Direct reverse dependencies (modules that link :<target>)
- :module-a  (scope: implementation)
- :module-b  (scope: api)

### Transitive impact (modules that depend on direct dependents)
- :module-c  via :module-a
- :module-d  via :module-b

### Retest set
./gradlew :module-a:test :module-b:test :module-c:test :module-d:test

### Rebuild set (bootJar for deployable modules)
./gradlew :module-a:bootJar :module-b:bootJar

### Notes
- Public API surface of :<target> that ripples to dependents: <list classes if readily visible from changed files>
- Any dependents with `api` scope → changes to :<target> public types are source-breaking for them.
```

## Do not

- Do not run full `./gradlew build` — too slow. Use targeted queries only.
- Do not guess dependents. If `dependencyInsight` is ambiguous, report uncertainty.
- Do not propose refactors — this command is read-only impact analysis.

## Tips

- For a single-module repo this command returns "no impact outside current module; run `./gradlew check` after changes".
- If `settings.gradle.kts` uses dynamic `include()` patterns, verify the parsed module list matches `./gradlew projects`.
