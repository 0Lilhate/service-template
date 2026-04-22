---
name: sage-ide-integration
description: SAGE/GRACE IDE setup — VS Code settings (file associations, XML validation, rulers), code snippets for SAGE anchors, pre-commit validation script (paired START/END, unique IDs). Optional — apply only when developer actually configures IDE for SAGE. Russian methodology doc.
origin: newskill
---

# skill IDE-интеграция

> **Основной документ:** [SAGE.md](../../../SAGE.md) — методология SAGE/GRACE в репозитории. Workflow-триггеры зафиксированы в `AGENTS.md` → «SAGE markup triggers».
>
> **Версия:** 2.0 (core IDE scope).

Это **optional** skill. Активировать только при реальной настройке IDE под SAGE. Neo4j knowledge-graph / Prompt Caching / MCP-server implementation вынесены из этого skill'а (см. «External references» в конце файла).

---

## Когда использовать

- Настройка рабочей среды (VS Code / Cursor) для SAGE-проекта.
- Добавление file-associations и XML-валидации для SAGE-артефактов.
- Установка code snippets для якорной разметки.
- Настройка pre-commit hook для проверки парности `SAGE_*_START/END` тегов и уникальности id.

**Не использовать** если:
- SAGE-разметка в проекте не применяется (см. triggers в `AGENTS.md`).
- Нужен only runtime contract-lookup — это scope MCP-сервера (external, не IDE).

---

## Связь с методологией SAGE

| Принцип SAGE | Реализация в IDE |
|:-------------|:-----------------|
| XML-структурирование | `files.associations` + `xml.format` в `settings.json` |
| SAGE-разметка (якоря) | Code snippets для `SAGE_FUNCTION_START/END`, `SAGE_METHOD_START/END`, `SAGE_BLOCK_START/END`, `SAGE_MODULE_CONTRACT`, `SAGE_CROSS_LINKS` |
| Contract integrity | Pre-commit validation scripts (paired tags, unique ids) |
| Contextual retrieval | External — MCP-серверы (см. «External references») |

---

## Быстрый старт

Минимальная настройка. Создать `.vscode/settings.json` в корне проекта:

```json
{
  "editor.snippetSuggestions": "top",
  "editor.quickSuggestions": { "comments": true, "strings": true },
  "files.associations": {
    "*.sage.xml": "xml",
    "requirements_*.xml": "xml",
    "development_*.xml": "xml",
    "technology.xml": "xml",
    "knowledge_graph.xml": "xml"
  },
  "xml.format.enabled": true,
  "xml.format.splitAttributes": true,
  "xml.format.splitAttributesIndentSize": 2,
  "editor.rulers": [80, 120]
}
```

Этого достаточно для распознавания SAGE-артефактов в XML-формате и колонок ~80/120 для блоков разметки.

---

## VS Code — базовая настройка

### Рекомендуемые расширения

| Расширение | Назначение | id |
|:-----------|:-----------|:---|
| XML Tools | Форматирование / валидация XML-артефактов | `DotJoshJohnson.xml` |
| Python | SAGE-разметка чаще всего в Python (для Java аналог — встроенная поддержка) | `ms-python.python` |
| GitHub Copilot / Continue | AI-ассистент (опционально) | `GitHub.copilot`, `continue.continue` |

### Подсветка SAGE-комментариев (опционально)

Добавить в `settings.json`:

```json
{
  "editor.tokenColorCustomizations": {
    "textMateRules": [
      {
        "scope": "comment.line.sage",
        "settings": { "foreground": "#569CD6", "fontStyle": "bold" }
      }
    ]
  }
}
```

Стандартные language-grammars не распознают `# SAGE_*` как особую категорию — custom scope `comment.line.sage` работает только если установлено расширение, которое её инъектирует. Без расширения строка цвет не меняет, но и вреда нет.

---

## Code snippets — `.vscode/sage.code-snippets`

Набор для Python-разметки (под Java/Kotlin адаптируется заменой `#` → `//`):

```json
{
  "SAGE_FUNCTION": {
    "prefix": "sage-func",
    "body": [
      "# SAGE_FUNCTION_START id=\"F_${1:name}_${2:001}\"",
      "def ${1:name}(${3:params}) -> ${4:return_type}:",
      "    ${5:pass}",
      "# SAGE_FUNCTION_END id=\"F_${1:name}_${2:001}\""
    ],
    "description": "SAGE function with paired anchors"
  },
  "SAGE_METHOD": {
    "prefix": "sage-method",
    "body": [
      "# SAGE_METHOD_START id=\"M_${1:class}_${2:method}_${3:001}\"",
      "def ${2:method}(self, ${4:params}) -> ${5:return_type}:",
      "    ${6:pass}",
      "# SAGE_METHOD_END id=\"M_${1:class}_${2:method}_${3:001}\""
    ]
  },
  "SAGE_BLOCK": {
    "prefix": "sage-block",
    "body": [
      "# SAGE_BLOCK_START id=\"B_${1:name}_${2:001}\"",
      "${3:code}",
      "# SAGE_BLOCK_END id=\"B_${1:name}_${2:001}\""
    ]
  },
  "SAGE_MODULE_CONTRACT": {
    "prefix": "sage-contract",
    "body": [
      "# SAGE_MODULE_CONTRACT_START id=\"MC_${1:module}_${2:001}\"",
      "# PURPOSE: [${3:purpose}]",
      "# RESPONSIBILITIES:",
      "#   - ${4:responsibility}",
      "# DEPENDENCIES: [${5:dependencies}]",
      "# ERROR_HANDLING: [${6:error_handling}]",
      "# SAGE_MODULE_CONTRACT_END id=\"MC_${1:module}_${2:001}\""
    ]
  },
  "SAGE_CROSS_LINKS": {
    "prefix": "sage-links",
    "body": [
      "# SAGE_CROSS_LINKS_START id=\"XL_${1:module}_${2:001}\"",
      "#   ${3:function} -> ${4:dependency}",
      "# SAGE_CROSS_LINKS_END id=\"XL_${1:module}_${2:001}\""
    ]
  }
}
```

В редакторе: набрать `sage-func` → `Tab` → плейсхолдеры последовательно. Одинаковые `${N}` синхронизированы, поэтому имя и id меняются согласованно.

---

## Pre-commit validation

SAGE-разметка ломается при неспаренном `START/END` или дубликате id. Ловить это в pre-commit:

### `.pre-commit-config.yaml`

```yaml
repos:
  - repo: local
    hooks:
      - id: validate-sage-markup
        name: Validate SAGE Markup
        entry: python scripts/validate_sage.py
        language: python
        types: [python]
      - id: validate-xml-artifacts
        name: Validate XML Artifacts
        entry: python scripts/validate_xml.py
        language: python
        files: \.xml$
```

### `scripts/validate_sage.py`

```python
import re
import sys

START = re.compile(r'#\s*SAGE_(\w+)_START(?:\s+id="([^"]+)")?')
END   = re.compile(r'#\s*SAGE_(\w+)_END(?:\s+id="([^"]+)")?')

def validate(path: str) -> list[str]:
    errors: list[str] = []
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()

    starts: dict[str, tuple[int, str]] = {}
    ends:   dict[str, tuple[int, str]] = {}
    for i, line in enumerate(lines, 1):
        if m := START.search(line):
            starts[m.group(2)] = (i, m.group(1))
        if m := END.search(line):
            ends[m.group(2)] = (i, m.group(1))

    for aid, (ln, typ) in starts.items():
        if aid not in ends:
            errors.append(f"{path}:{ln}: missing END for {typ} id='{aid}'")
    for aid, (ln, typ) in ends.items():
        if aid not in starts:
            errors.append(f"{path}:{ln}: missing START for {typ} id='{aid}'")

    all_ids = list(starts) + list(ends)
    dups = {i for i in all_ids if all_ids.count(i) > 1}
    if dups:
        errors.append(f"{path}: duplicate ids: {sorted(dups)}")
    return errors

def main() -> None:
    all_err: list[str] = []
    for path in sys.argv[1:]:
        all_err.extend(validate(path))
    if all_err:
        print("SAGE validation failed:")
        for e in all_err:
            print(f"  {e}")
        sys.exit(1)
    print("SAGE validation passed.")

if __name__ == "__main__":
    main()
```

### Пример срабатывания hook'а

```bash
$ git commit -m "add create_task"
Validating SAGE Markup...
  src/task_service.py:45: missing END for FUNCTION id='F_create_task_001'
SAGE validation failed.
Commit aborted.
```

Исправление — дописать парный `SAGE_FUNCTION_END id="F_create_task_001"`; повторный commit проходит.

---

## CI — minimum

Дублировать pre-commit в CI (защита от `--no-verify` на dev-машинах). Минимальный GitHub Actions:

```yaml
# .github/workflows/sage-validation.yml
name: SAGE validation
on:
  push:    { branches: [main] }
  pull_request:

jobs:
  sage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - name: Validate SAGE markup
        run: python scripts/validate_sage.py $(git ls-files '*.py')
      - name: Validate XML artifacts
        run: python scripts/validate_xml.py $(git ls-files '*.xml')
```

Держать это как отдельный workflow (или отдельный job) — чтобы SAGE-проверка не прятала другие fail'ы build'а.

---

## Чек-лист настройки

- [ ] VS Code + расширение XML Tools установлены.
- [ ] `.vscode/settings.json` содержит `files.associations` для SAGE XML-артефактов.
- [ ] `.vscode/sage.code-snippets` содержит 5 snippet'ов (function / method / block / contract / cross-links).
- [ ] `scripts/validate_sage.py` в репо, исполняется локально без ошибок.
- [ ] `.pre-commit-config.yaml` ссылается на `validate_sage.py` и `validate_xml.py`.
- [ ] `pre-commit install` выполнен разработчиком.
- [ ] CI workflow `sage-validation.yml` активен в `.github/workflows/`.

---

## Антипаттерны

### 1. Нет валидации разметки

SAGE-якоря без pre-commit — накапливаются unpaired tags и дубликаты id. Статический анализ AI-навигации ломается в тот момент, когда один `SAGE_FUNCTION_START` без `_END` делает весь файл нечитаемым для tooling'а. Fix — pre-commit hook выше.

### 2. Секреты в `settings.json` / скриптах

```python
# ❌
api_key = "sk-ant-api03-..."

# ✅
import os
api_key = os.environ.get("ANTHROPIC_API_KEY")
```

`.vscode/settings.json` коммитится в репо — не хранить там `env`, `apiKey`, tokens. Использовать VS Code user-settings или `.env` с gitignore.

### 3. Сниппеты с hard-coded id

Не писать id как `"F_001"` без плейсхолдера `${N}` — все snippet-вставки получат одинаковый id, валидатор упадёт на duplicates. Корректно — `"F_${1:name}_${2:001}"` с синхронизацией.

---

## External references (вне scope этого skill'а)

Эти темы ранее жили в этом skill'е и были вынесены в `SAGE.md` как pointer'ы — не являются IDE-настройкой:

- **Neo4j knowledge graph** — хранилище SAGE CrossLinks в графовой БД. Это infrastructure-layer, не IDE. При необходимости — стандартные Neo4j docs + driver библиотеки.
- **MCP servers для contract retrieval** — runtime server, который ИИ-ассистент опрашивает для контекстной выборки контрактов. Это Anthropic MCP feature (см. Anthropic docs для Model Context Protocol), не связан с VS Code setup.
- **Prompt caching** — Anthropic API feature (`cache_control: {"type": "ephemeral"}`). Используется на стороне API-клиента, не в IDE. См. Anthropic API docs.

---

## Связанные skills

| task | skill |
|------|-------|
| Якорная разметка (методология, rules, примеры) | `sage-anchor-markup` |
| Код с разметкой (component contracts + docstrings) | `sage-code-markup` |

---

## Источник

Методология SAGE/GRACE — `Инструкция_SAGE_GRACE_Полная_v2.md`. В этом skill'е — только IDE-layer; core методология — в `SAGE.md` и `sage-anchor-markup` / `sage-code-markup` skills.
