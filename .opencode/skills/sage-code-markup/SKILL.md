---
name: sage-code-markup
description: SAGE/GRACE methodology — code annotation for AI-assisted modification. XML-like tags (SAGE_FUNCTION_START/END, SAGE_BLOCK_START/END) with unique IDs, AI-contract docstrings, CrossLinks between components, knowledge-graph derivation. Use for enterprise codebases with 100% AI code generation or legacy AI-contract adoption. Russian-language methodology doc.
origin: newskill
---

# skill код с разметкой

> **Основной документ:** [SAGE.md](../../../SAGE.md) — методология SAGE/GRACE в репозитории. Workflow-триггеры зафиксированы в `AGENTS.md` раздел «SAGE markup triggers».
>
> **Версия:** 1.0 | **Методология:** SAGE/GRACE

---

## 🎯 Когда использовать

Используйте этот skill, когда:

- ✅ Enterprise-проекты с 100% генерацией кода ИИ
- ✅ Нужна сквозная прослеживаемость от требований до кода
- ✅ Команда работает с ИИ-ассистентами (Cursor, Kilo Code, Claude Code)
- ✅ Legacy-системы с внедрением AI-контрактов
- ✅ Проекты >800-1000 строк кода

### Relation к `sage-anchor-markup`

> **Этот skill — расширение `sage-anchor-markup` через AI-contract docstrings + knowledge graph. Применяется поверх anchor markup, не вместо.**

| | `sage-anchor-markup` | `sage-code-markup` (этот skill) |
|:--|:--|:--|
| Что даёт | Boundary tags `SAGE_*_START / _END` с уникальным id | `MODULE_CONTRACT` / `MODULE_MAP` / AI-contract docstrings + CrossLinks → derived knowledge graph |
| Prerequisite | Нет | **Anchor-markup обязателен** — contract docstrings описывают то, что anchor-tags обрамляют |
| Когда достаточно | AI-навигация + граф зависимостей | AI-навигация + full traceability требований → кода + контрактный documentation |

Если нужен только AI-lookup по функциям — хватит `sage-anchor-markup`. Code-markup применять когда enterprise-policy требует audit-trail «requirement → contract → implementation».

---

## ⚡ Quick reference

Карточка для быстрого применения без чтения всей методологии. Anchor-разметка — см. `sage-anchor-markup` Quick reference; здесь — только что code-markup добавляет **поверх**.

### Трёхуровневая иерархия

```
Уровень 1: MODULE_CONTRACT   — назначение модуля, responsibilities, dependencies, error handling
Уровень 2: FUNCTION / CLASS  — контракты функций/классов (AI-contract docstring + anchor tags)
Уровень 3: BLOCK             — семантические блоки внутри функций (anchor tags + короткий контракт)
```

### `MODULE_CONTRACT` — минимальный шаблон

```python
# SAGE_MODULE_CONTRACT_START id="MC_<module>_<nnn>"
# PURPOSE: [что делает модуль, одна строка]
# RESPONSIBILITIES:
#   - [ответственность 1]
#   - [ответственность 2]
# DEPENDENCIES: [ext1, ext2]
# ERROR_HANDLING: [стратегия — validation / retry / graceful degradation]
# SAGE_MODULE_CONTRACT_END id="MC_<module>_<nnn>"
```

Располагать первым блоком в файле, до import'ов и анкерованного кода.

### AI-contract docstring — функция

```python
# SAGE_FUNCTION_START id="F_calc_discount_001"
def calculate_discount(amount: float, is_vip: bool) -> float:
    """CONTRACT:
    PURPOSE:     Рассчитать скидку для клиента.
    INPUTS:      amount — положительное число; is_vip — флаг VIP.
    OUTPUT:      Сумма скидки (float).
    RAISES:      ValueError если amount < 0.
    SIDE_EFX:    Отсутствуют.
    INVARIANT:   Возврат ∈ [0, amount].
    """
    if amount < 0:
        raise ValueError("Amount must be positive")
    return amount * 0.2 if is_vip else 0.0
# SAGE_FUNCTION_END id="F_calc_discount_001"
```

Docstring читаем и человеком, и AI — ключевые поля (`PURPOSE` / `INPUTS` / `OUTPUT` / `RAISES` / `SIDE_EFX` / `INVARIANT`) делают contract машинно-извлекаемым для knowledge graph.

### Три правила code-markup

1. **Code-markup предполагает anchor-разметку** — без anchor-tags контракт не имеет границ.
2. **Каждая функция / класс / блок** с code-markup имеет AI-contract docstring (не только anchor-tags).
3. **`MODULE_CONTRACT` + `MODULE_MAP` обязательны** на уровне файла — они — корневые узлы knowledge graph.

### Когда НЕ применять

- Экспериментальный / prototype код (ещё нет стабильного контракта).
- Обычные feature-ветки без enterprise-policy requirements.
- Проекты без адептов AI-ассистентов в команде (контракты останутся не поддерживаемыми).
- Единичные utility-функции — anchor-markup хватит.

Полные триггеры — `AGENTS.md` → «SAGE markup triggers» (раздел «Когда применять code markup»).

---

## 🔗 Связь с методологией SAGE

Этот skill является практическим руководством по применению методологии SAGE для разметки программного кода. 

### Ключевые принципы SAGE в разметке кода:

| Принцип SAGE | Применение в разметке кода |
|:--|:--|
| **Семантическая разметка** | XML-подобные теги `SAGE_*_START/END` создают структурированные контейнеры для каждого элемента кода |
| **AI-контракты** | Docstring-контракты описывают намерения, параметры, возвращаемые значения и побочные эффекты на естественном языке |
| **Knowledge Graph** | CrossLinks (`SAGE_CROSS_LINKS`) явно связывают компоненты между собой, создавая граф зависимостей |
| **Уникальные ID-теги** | Каждый блок имеет уникальный идентификатор (например, `id="F_calc_discount_001"`), что предотвращает полисемию при парсинге |

### Почему XML-подобная разметка эффективна:

1. **Снижение семантического шума** — LLM лучше понимают структурированные данные в XML-тегах, чем в JSON
2. **Детерминированный парсинг** — уникальные ID гарантируют однозначное сопоставление открывающих и закрывающих тегов
3. **Устойчивость к редактированию** — добавление/удаление строк не ломает связь между тегами благодаря ID

### Трёхуровневая система разметки:

```
Уровень 1: MODULE_CONTRACT  → Контракт модуля (назначение, обязанности, зависимости)
Уровень 2: FUNCTION/CLASS   → Контракты функций и классов (интерфейсы, поведение)
Уровень 3: BLOCK            → Семантические блоки внутри функций (логические участки)
```

Эта иерархия обеспечивает сквозную прослеживаемость от требований до конкретных строк кода.

---

## 🚀 Быстрый старт

**Минимальный пример полной разметки модуля:**

```python
# SAGE_FILE: src/calculator.py
# SAGE_VERSION: 1.0.0
# SAGE_MODULE_CONTRACT_START id="MC_calc_001"
# PURPOSE: [Калькулятор для финансовых расчётов]
# RESPONSIBILITIES:
#   - Расчёт скидок
#   - Валидация сумм
#   - Логирование операций
# DEPENDENCIES: [logger, config]
# ERROR_HANDLING: [validation, graceful degradation]
# SAGE_MODULE_CONTRACT_END id="MC_calc_001"

# SAGE_MODULE_MAP_START id="MM_calc_001"
# FUNCTION calculate_discount => Расчёт скидки
# FUNCTION validate_amount => Валидация суммы
# SAGE_MODULE_MAP_END id="MM_calc_001"

# SAGE_CONST_START id="CONST_max_discount_001"
MAX_DISCOUNT = 0.3
# SAGE_CONST_END id="CONST_max_discount_001"

# SAGE_FUNCTION_START id="F_calc_discount_001"
def calculate_discount(amount: float, customer_type: str) -> float:
    """
    CONTRACT:
    - amount: положительное число
    - customer_type: "regular" | "vip"
    - Returns: сумма скидки (0 <= result <= amount * MAX_DISCOUNT)
    """
    # SAGE_BLOCK_START id="B_calc_validate_001"
    validate_amount(amount)
    # SAGE_BLOCK_END id="B_calc_validate_001"
    
    # SAGE_BLOCK_START id="B_calc_logic_001"
    rate = MAX_DISCOUNT if customer_type == "vip" else 0.0
    discount = amount * rate
    # SAGE_BLOCK_END id="B_calc_logic_001"
    
    return discount
# SAGE_FUNCTION_END id="F_calc_discount_001"

# SAGE_CROSS_LINKS_START id="XL_calc_001"
#   calculate_discount -> validate_amount()
#   calculate_discount -> logger.log_transaction()
# SAGE_CROSS_LINKS_END id="XL_calc_001"
```

---

## 📚 Подробное описание

### MODULE_CONTRACT

**MODULE_CONTRACT** - контракт модуля, описывающий назначение, обязанности и ограничения.

**Обязательные поля:**

| Поле | Описание | Пример |
|:--|:--|:--|
| **PURPOSE** | Назначение модуля | `[Управление задачами ToDo-приложения]` |
| **RESPONSIBILITIES** | Обязанности (список) | `- CRUD операции для задач` |
| **DEPENDENCIES** | Зависимости (список) | `[database, ai_classifier, logger]` |
| **ERROR_HANDLING** | Обработка ошибок | `[graceful degradation, retry logic]` |

**Опциональные поля:**

| Поле | Описание | Пример |
|:--|:--|:--|
| **VERSION** | Версия модуля | `1.2.0` |
| **AUTHOR** | Автор | `AI Architect` |
| **CRITICALITY** | Критичность | `critical` |
| **PERFORMANCE** | Требования к производительности | `response_time < 100ms` |

**Формат:**

```python
# SAGE_MODULE_CONTRACT_START id="MC_[module]_[number]"
# PURPOSE: [Описание назначения модуля]
# RESPONSIBILITIES:
#   - Обязанность 1
#   - Обязанность 2
#   - Обязанность 3
# DEPENDENCIES: [dependency1, dependency2, dependency3]
# ERROR_HANDLING: [strategy1, strategy2]
# SAGE_MODULE_CONTRACT_END id="MC_[module]_[number]"
```

### MODULE_MAP

**MODULE_MAP** - карта модуля, краткий указатель по ключевым функциям.

**Формат:**

```python
# SAGE_MODULE_MAP_START id="MM_[module]_[number]"
# CLASS ClassName => Описание класса
# METHOD method_name => Описание метода
# FUNCTION function_name => Описание функции
# CONSTANT CONSTANT_NAME => Описание константы
# SAGE_MODULE_MAP_END id="MM_[module]_[number]"
```

### AI-контракты функций

**AI-контракт** - это "микро-ТЗ" для LLM, описывающее намерение, входы/выходы на естественном языке.

**8 элементов AI-контракта:**

| # | Элемент | Описание |
|:--:|:--|:--|
| 1 | **Имя функции** | Уникальное имя функции |
| 2 | **Описание** | Что делает функция |
| 3 | **Параметры** | Входные данные с ограничениями |
| 4 | **Возвращаемое значение** | Что возвращает функция |
| 5 | **Предусловия** | Что должно быть истинно до вызова |
| 6 | **Постусловия** | Что гарантируется после выполнения |
| 7 | **Побочные эффекты** | Изменение состояния, вызовы внешних систем |
| 8 | **Примеры** | Few-shot примеры использования |

**Формат AI-контракта:**

```python
def function_name(param1: type1, param2: type2) -> return_type:
    """
    CONTRACT:
    - param1: описание, ограничения
    - param2: описание, ограничения
    - Returns: описание возвращаемого значения
    - Precondition: условия до вызова
    - Postcondition: условия после вызова
    - Side effects: побочные эффекты
    - Example: function_name(value1, value2) -> result
    """
```

**XML-формат AI-контракта:**

```xml
<function name="calculateDiscount">
  <description>Рассчитывает скидку на основе суммы и типа клиента</description>
  <param name="amount">Сумма покупки (положительное число)</param>
  <param name="customerType">Тип клиента: "regular", "vip"</param>
  <returns>Сумма скидки (неотрицательное число)</returns>
  <precondition>amount >= 0</precondition>
  <postcondition>result >= 0 and result <= amount * 0.3</postcondition>
  <side_effects>Записывает транзакцию в лог</side_effects>
  <example>calculateDiscount(1000, "vip") -> 300</example>
</function>
```

**Зачем нужны AI-контракты:**

- **Семантический щит:** При модификации кода ИИ сверяет правки с контрактами
- **Замена документации:** Граф + контракты полностью заменяют внешнюю документацию
- **Предотвращение дрейфа:** Контракты фиксируют замысел и не дают ИИ уйти в сторону

### Разметка полей, констант, enum

**Поля класса:**

```python
# SAGE_CLASS_START id="C_entity_001"
class Entity:
    # SAGE_FIELD_START id="F_entity_field1_001"
    field1: type1 = default_value
    # SAGE_FIELD_END id="F_entity_field1_001"
    
    # SAGE_FIELD_START id="F_entity_field2_001"
    field2: type2
    # SAGE_FIELD_END id="F_entity_field2_001"
# SAGE_CLASS_END id="C_entity_001"
```

**Константы модуля:**

```python
# SAGE_CONST_START id="CONST_max_retries_001"
MAX_RETRIES = 3
# SAGE_CONST_END id="CONST_max_retries_001"

# SAGE_CONST_START id="CONST_timeout_001"
TIMEOUT_SECONDS = 5
# SAGE_CONST_END id="CONST_timeout_001"
```

**Enum:**

```python
# SAGE_ENUM_START id="E_status_001"
class Status(str, Enum):
    """Статус сущности."""
    PENDING = "pending"
    ACTIVE = "active"
    COMPLETED = "completed"
    CANCELLED = "cancelled"
# SAGE_ENUM_END id="E_status_001"
```

### Синхронизация контрактов с кодом

**Принцип:** Любое изменение кода требует синхронного обновления контрактов.

**Правила синхронизации:**

| Изменение в коде | Действие с контрактом |
|:--|:--|
| Добавление параметра | Добавить в AI-контракт |
| Изменение типа возвращаемого значения | Обновить Returns |
| Добавление новой зависимости | Добавить в DEPENDENCIES |
| Изменение логики обработки ошибок | Обновить ERROR_HANDLING |
| Добавление нового метода | Добавить в MODULE_MAP |

**Автоматизация:**

```python
# При генерации кода ИИ:
# 1. Считывает MODULE_CONTRACT
# 2. Генерирует код в соответствии с контрактом
# 3. Добавляет AI-контракты к функциям
# 4. Обновляет CrossLinks

# При модификации кода ИИ:
# 1. Считывает AI-контракты
# 2. Проверяет соответствие изменений контрактам
# 3. Вносит правки
# 4. Обновляет контракты при необходимости
```

---

## 📝 Шаблоны

### Шаблон 1: MODULE_CONTRACT

```python
# SAGE_FILE: src/[module_name].py
# SAGE_VERSION: [version]
# SAGE_MODULE_CONTRACT_START id="MC_[module]_[number]"
# PURPOSE: [Краткое описание назначения]
# RESPONSIBILITIES:
#   - Обязанность 1
#   - Обязанность 2
#   - Обязанность 3
# DEPENDENCIES: [dependency1, dependency2, dependency3]
# ERROR_HANDLING: [strategy1, strategy2]
# PERFORMANCE: [требования к производительности]
# CRITICALITY: [low | medium | high | critical]
# SAGE_MODULE_CONTRACT_END id="MC_[module]_[number]"

# SAGE_MODULE_MAP_START id="MM_[module]_[number]"
# CLASS ClassName => Описание класса
# METHOD method_name => Описание метода
# FUNCTION function_name => Описание функции
# SAGE_MODULE_MAP_END id="MM_[module]_[number]"
```

### Шаблон 2: MODULE_MAP

```python
# SAGE_MODULE_MAP_START id="MM_[module]_[number]"
# CLASS TaskManager => Менеджер задач с AI-категоризацией
# METHOD create_task => Создание задачи
# METHOD get_tasks_by_date => Получение задач по дате
# METHOD _validate_task_data => Приватная валидация
# FUNCTION format_task => Форматирование задачи для вывода
# CONSTANT MAX_TASKS => Максимальное количество задач
# SAGE_MODULE_MAP_END id="MM_[module]_[number]"
```

### Шаблон 3: AI-контракт функции

```python
# SAGE_FUNCTION_START id="F_[domain]_[action]_[number]"
def function_name(param1: type1, param2: type2 = default) -> return_type:
    """
    Краткое описание функции.
    
    CONTRACT:
    - param1: описание параметра, ограничения
    - param2: описание параметра (опциональный)
    - Returns: описание возвращаемого значения
    - Precondition: условия, которые должны быть истинны до вызова
    - Postcondition: условия, которые гарантированы после выполнения
    - Side effects: побочные эффекты (изменение состояния, вызовы API)
    - Raises: исключения, которые может выбросить функция
    - Example: function_name(value1, value2) -> result
    """
    # Реализация функции
    pass
# SAGE_FUNCTION_END id="F_[domain]_[action]_[number]"
```

### Шаблон 4: Enum с контрактом

```python
# SAGE_ENUM_START id="E_[domain]_[enum_name]_[number]"
class EnumName(str, Enum):
    """
    CONTRACT:
    - Используется для [назначение]
    - Значения: VALUE1, VALUE2, VALUE3
    - Default: VALUE1
    """
    VALUE1 = "value1"
    VALUE2 = "value2"
    VALUE3 = "value3"
# SAGE_ENUM_END id="E_[domain]_[enum_name]_[number]"
```

### Шаблон 5: Класс с полным контрактом

```python
# SAGE_CLASS_START id="C_[domain]_[class_name]_[number]"
class ClassName:
    """
    Описание класса.
    
    CONTRACT:
    - Назначение: [описание]
    - Зависимости: [dependency1, dependency2]
    - Критичность: [low | medium | high | critical]
    - Thread-safe: [yes | no]
    """
    
    # SAGE_CONST_START id="CONST_[class]_[constant]_[number]"
    CONSTANT_NAME: type = value
    # SAGE_CONST_END id="CONST_[class]_[constant]_[number]"
    
    # SAGE_FIELD_START id="F_[class]_[field1]_[number]"
    field1: type1
    # SAGE_FIELD_END id="F_[class]_[field1]_[number]"
    
    # SAGE_FIELD_START id="F_[class]_[field2]_[number]"
    field2: type2 = default_value
    # SAGE_FIELD_END id="F_[class]_[field2]_[number]"
    
    # SAGE_METHOD_START id="M_[class]_[method]_[number]"
    def method_name(self, param: type) -> return_type:
        """
        CONTRACT:
        - param: описание параметра
        - Returns: описание возвращаемого значения
        - Side effects: побочные эффекты
        """
        pass
    # SAGE_METHOD_END id="M_[class]_[method]_[number]"
    
# SAGE_CLASS_END id="C_[domain]_[class_name]_[number]"

# SAGE_CROSS_LINKS_START id="XL_[class]_[number]"
#   ClassName.method_name() -> dependency.function()
# SAGE_CROSS_LINKS_END id="XL_[class]_[number]"
```

---

## ✅ Чек-лист качества

Перед завершением разметки модуля проверьте:

| # | Проверка | ✅/❌ |
|:--:|:--|:--:|
| 1 | MODULE_CONTRACT заполнен полностью (PURPOSE, RESPONSIBILITIES, DEPENDENCIES, ERROR_HANDLING) | |
| 2 | MODULE_MAP содержит все ключевые функции/методы | |
| 3 | Все публичные функции имеют AI-контракты | |
| 4 | AI-контракты содержат все 8 элементов | |
| 5 | Параметры в контрактах соответствуют сигнатуре функции | |
| 6 | Returns в контракте соответствует реальному возвращаемому значению | |
| 7 | Предусловия и постусловия указаны явно | |
| 8 | Побочные эффекты задокументированы | |
| 9 | CrossLinks описывают все внешние зависимости | |
| 10 | Контракты синхронизированы с кодом | |

---

## 💡 Примеры

### Пример 1: Полный файл - Task Entity

```python
# SAGE_FILE: src/domain/task.py
# SAGE_VERSION: 1.0.0
# SAGE_MODULE_CONTRACT_START id="MC_task_domain_001"
# PURPOSE: [Task entity and business rules for TODO application]
# RESPONSIBILITIES:
#   - Define Task entity with validation
#   - Define TaskStatus enum
#   - Enforce business rules
# DEPENDENCIES: []
# ERROR_HANDLING: [validation errors]
# SAGE_MODULE_CONTRACT_END id="MC_task_domain_001"

# SAGE_MODULE_MAP_START id="MM_task_domain_001"
# CLASS Task => Task entity with validation
# METHOD __post_init__ => Validation after initialization
# METHOD mark_completed => Mark task as completed
# ENUM TaskStatus => Status of a task
# SAGE_MODULE_MAP_END id="MM_task_domain_001"

from datetime import datetime
from enum import Enum
from dataclasses import dataclass
from typing import Optional

# SAGE_ENUM_START id="E_task_status_001"
class TaskStatus(str, Enum):
    """
    Status of a task in the TODO application.
    
    CONTRACT:
    - Используется для отслеживания состояния задачи
    - Значения: PENDING, COMPLETED, CANCELLED
    - Default: PENDING
    """
    PENDING = "pending"
    COMPLETED = "completed"
    CANCELLED = "cancelled"
# SAGE_ENUM_END id="E_task_status_001"

# SAGE_CLASS_START id="C_task_entity_001"
@dataclass
class Task:
    """
    Represents a task in the TODO application.
    
    CONTRACT:
    - Назначение: Сущность задачи с валидацией
    - Зависимости: [TaskStatus, datetime]
    - Критичность: high
    - Thread-safe: no
    """
    
    # SAGE_FIELD_START id="F_task_id_001"
    id: Optional[int] = None
    # SAGE_FIELD_END id="F_task_id_001"
    
    # SAGE_FIELD_START id="F_task_title_001"
    title: str = ""
    # SAGE_FIELD_END id="F_task_title_001"
    
    # SAGE_FIELD_START id="F_task_description_001"
    description: str = ""
    # SAGE_FIELD_END id="F_task_description_001"
    
    # SAGE_FIELD_START id="F_task_status_001"
    status: TaskStatus = TaskStatus.PENDING
    # SAGE_FIELD_END id="F_task_status_001"
    
    # SAGE_FIELD_START id="F_task_category_001"
    category: str = "uncategorized"
    # SAGE_FIELD_END id="F_task_category_001"
    
    # SAGE_FIELD_START id="F_task_created_001"
    created_at: Optional[datetime] = None
    # SAGE_FIELD_END id="F_task_created_001"
    
    # SAGE_FIELD_START id="F_task_completed_001"
    completed_at: Optional[datetime] = None
    # SAGE_FIELD_END id="F_task_completed_001"

    # SAGE_METHOD_START id="M_task_validate_001"
    def __post_init__(self):
        """
        Validates task after initialization.
        
        CONTRACT:
        - Precondition: Task object created
        - Postcondition: Task is valid or raises ValueError
        - Raises: ValueError if title is empty or too long
        - Example: Task(title="Test") -> valid task
        """
        # SAGE_BLOCK_START id="B_validate_title_001"
        if not self.title or not self.title.strip():
            raise ValueError("Task title cannot be empty")
        if len(self.title) > 200:
            raise ValueError("Task title cannot exceed 200 characters")
        # SAGE_BLOCK_END id="B_validate_title_001"
        
        # SAGE_BLOCK_START id="B_validate_status_001"
        if self.status not in TaskStatus:
            raise ValueError(f"Invalid status: {self.status}")
        # SAGE_BLOCK_END id="B_validate_status_001"
    # SAGE_METHOD_END id="M_task_validate_001"

    # SAGE_METHOD_START id="M_task_complete_001"
    def mark_completed(self) -> None:
        """
        Marks the task as completed.
        
        CONTRACT:
        - Precondition: task.status != TaskStatus.COMPLETED
        - Postcondition: task.status = TaskStatus.COMPLETED
        - Side effects: Sets completed_at to current datetime
        - Raises: ValueError if task is already completed
        - Example: task.mark_completed() -> status=COMPLETED
        """
        # SAGE_BLOCK_START id="B_complete_logic_001"
        if self.status == TaskStatus.COMPLETED:
            raise ValueError("Task is already completed")
        self.status = TaskStatus.COMPLETED
        self.completed_at = datetime.now()
        # SAGE_BLOCK_END id="B_complete_logic_001"
    # SAGE_METHOD_END id="M_task_complete_001"

# SAGE_CLASS_END id="C_task_entity_001"

# SAGE_CROSS_LINKS_START id="XL_task_domain_001"
#   Task.mark_completed() -> datetime.now()
#   Task.__post_init__() -> ValueError (validation)
#   TaskStatus used by Task
# SAGE_CROSS_LINKS_END id="XL_task_domain_001"
```

### Пример 2: Полный файл - AI Categorizer

```python
# SAGE_FILE: src/infrastructure/ai_categorizer.py
# SAGE_VERSION: 1.0.0
# SAGE_MODULE_CONTRACT_START id="MC_ai_categorizer_001"
# PURPOSE: [AI-based task categorization using LLM]
# RESPONSIBILITIES:
#   - Categorize tasks based on title
#   - Handle API errors gracefully
#   - Return fallback category on failure
# DEPENDENCIES: [anthropic, logging]
# ERROR_HANDLING: [retry logic, timeout, graceful degradation]
# PERFORMANCE: [response_time < 1s]
# CRITICALITY: medium
# SAGE_MODULE_CONTRACT_END id="MC_ai_categorizer_001"

# SAGE_MODULE_MAP_START id="MM_ai_categorizer_001"
# CLASS AICategorizer => AI categorizer using Claude API
# METHOD categorize => Categorize task by title
# CONSTANT CATEGORIES => List of valid categories
# CONSTANT DEFAULT_CATEGORY => Fallback category
# SAGE_MODULE_MAP_END id="MM_ai_categorizer_001"

import anthropic
from typing import List
import logging

# SAGE_CONST_START id="CONST_categories_001"
CATEGORIES: List[str] = ["work", "personal", "shopping", "health", "finance", "other"]
# SAGE_CONST_END id="CONST_categories_001"

# SAGE_CONST_START id="CONST_default_cat_001"
DEFAULT_CATEGORY = "uncategorized"
# SAGE_CONST_END id="CONST_default_cat_001"

# SAGE_CLASS_START id="C_ai_categorizer_001"
class AICategorizer:
    """
    Categorizes tasks using Claude API.
    
    CONTRACT:
    - Назначение: Классификация задач по названию
    - Зависимости: [anthropic, logging]
    - Критичность: medium
    - Thread-safe: yes
    """
    
    # SAGE_METHOD_START id="M_ai_init_001"
    def __init__(self, api_key: str, model: str = "claude-3-haiku-20240307"):
        """
        Initialize AI categorizer.
        
        CONTRACT:
        - api_key: Anthropic API key (required)
        - model: Model to use (default: Haiku for speed/cost)
        - Side effects: Creates Anthropic client, initializes logger
        - Example: AICategorizer("sk-...") -> categorizer instance
        """
        # SAGE_BLOCK_START id="B_init_client_001"
        self.client = anthropic.Anthropic(api_key=api_key)
        self.model = model
        self.logger = logging.getLogger(__name__)
        # SAGE_BLOCK_END id="B_init_client_001"
    # SAGE_METHOD_END id="M_ai_init_001"

    # SAGE_METHOD_START id="M_ai_categorize_001"
    def categorize(self, title: str) -> str:
        """
        Categorizes a task based on its title.
        
        CONTRACT:
        - title: Task title to categorize (non-empty string)
        - Returns: Category name or DEFAULT_CATEGORY on failure
        - Precondition: title is non-empty string
        - Postcondition: result in CATEGORIES or result == DEFAULT_CATEGORY
        - Side effects: Logs categorization result, calls Claude API
        - Example: categorize("Buy groceries") -> "shopping"
        """
        # SAGE_BLOCK_START id="B_build_prompt_001"
        prompt = f"""
<task_title>{title}</task_title>

<categories>
{chr(10).join(f'  <category>{c}</category>' for c in CATEGORIES)}
</categories>

Analyze the task title and select the most appropriate category.
Return ONLY the category name, nothing else.
"""
        # SAGE_BLOCK_END id="B_build_prompt_001"
        
        # SAGE_BLOCK_START id="B_call_api_001"
        try:
            message = self.client.messages.create(
                model=self.model,
                max_tokens=50,
                messages=[{"role": "user", "content": prompt}]
            )
            
            category = message.content[0].text.strip().lower()
            
            if category in CATEGORIES:
                self.logger.info(f"[AI] Categorized '{title[:30]}' -> '{category}'")
                return category
            else:
                self.logger.warning(f"[AI] Invalid category '{category}', using default")
                return DEFAULT_CATEGORY
                
        except Exception as e:
            self.logger.error(f"[AI] Categorization failed: {e}")
            return DEFAULT_CATEGORY
        # SAGE_BLOCK_END id="B_call_api_001"
    # SAGE_METHOD_END id="M_ai_categorize_001"

# SAGE_CLASS_END id="C_ai_categorizer_001"

# SAGE_CROSS_LINKS_START id="XL_ai_categorizer_001"
#   AICategorizer.categorize() -> anthropic.Messages.create()
#   AICategorizer.categorize() -> logging.info/warning/error
#   AICategorizer.__init__() -> anthropic.Anthropic()
# SAGE_CROSS_LINKS_END id="XL_ai_categorizer_001"
```

---

## ⚠️ Антипаттерны

### Антипаттерн 1: Отсутствие MODULE_CONTRACT

```python
# ❌ ПЛОХО: Нет контракта модуля
import logging

def process_data(data):
    return data

# ✅ ПРАВИЛЬНО: Есть MODULE_CONTRACT
# SAGE_MODULE_CONTRACT_START id="MC_processor_001"
# PURPOSE: [Data processing utilities]
# RESPONSIBILITIES:
#   - Process incoming data
#   - Validate data format
# DEPENDENCIES: [logging]
# ERROR_HANDLING: [validation]
# SAGE_MODULE_CONTRACT_END id="MC_processor_001"

def process_data(data):
    return data
```

### Антипаттерн 2: Неполный AI-контракт

```python
# ❌ ПЛОХО: Неполный контракт
# SAGE_FUNCTION_START id="F_calc_001"
def calculate_discount(amount, customer_type):
    """
    CONTRACT:
    - amount: число
    - Returns: скидка
    """
    pass
# SAGE_FUNCTION_END id="F_calc_001"

# ✅ ПРАВИЛЬНО: Полный контракт
# SAGE_FUNCTION_START id="F_calc_001"
def calculate_discount(amount: float, customer_type: str) -> float:
    """
    CONTRACT:
    - amount: Сумма покупки (положительное число)
    - customer_type: Тип клиента ("regular" | "vip")
    - Returns: Сумма скидки (0 <= result <= amount * 0.3)
    - Precondition: amount >= 0
    - Postcondition: result >= 0 and result <= amount * 0.3
    - Side effects: Логирует транзакцию
    - Raises: ValueError if amount < 0
    - Example: calculate_discount(1000, "vip") -> 300
    """
    pass
# SAGE_FUNCTION_END id="F_calc_001"
```

### Антипаттерн 3: Рассинхронизация контракта и кода

```python
# ❌ ПЛОХО: Контракт не соответствует коду
# SAGE_FUNCTION_START id="F_process_001"
def process_order(items: List[str]) -> Dict:
    """
    CONTRACT:
    - items: Список товаров
    - Returns: Словарь с заказом
    """
    # Код возвращает строку, а не Dict!
    return "Order processed"
# SAGE_FUNCTION_END id="F_process_001"

# ✅ ПРАВИЛЬНО: Контракт соответствует коду
# SAGE_FUNCTION_START id="F_process_001"
def process_order(items: List[str]) -> str:
    """
    CONTRACT:
    - items: Список товаров
    - Returns: Строка с подтверждением заказа
    - Example: process_order(["item1"]) -> "Order processed"
    """
    return "Order processed"
# SAGE_FUNCTION_END id="F_process_001"
```

### Антипаттерн 4: Отсутствие CrossLinks

```python
# ❌ ПЛОХО: Нет явных связей
# SAGE_FUNCTION_START id="F_create_001"
def create_order(items):
    user = auth.get_user()      # Неявная зависимость
    order = db.save(items)      # Неявная зависимость
    notify.send(user, order)    # Неявная зависимость
    return order
# SAGE_FUNCTION_END id="F_create_001"

# ✅ ПРАВИЛЬНО: Явные CrossLinks
# SAGE_FUNCTION_START id="F_create_001"
def create_order(items):
    user = auth.get_user()
    order = db.save(items)
    notify.send(user, order)
    return order
# SAGE_FUNCTION_END id="F_create_001"

# SAGE_CROSS_LINKS_START id="XL_create_001"
#   create_order -> auth.get_user()
#   create_order -> db.save()
#   create_order -> notify.send()
# SAGE_CROSS_LINKS_END id="XL_create_001"
```

---

## 📖 Связанные skill

- **skill якорная разметка.md** - Базовые семантические якоря
- **skill enterprise-workflow.md** - Полный цикл GRACE-разработки
- **skill IDE-интеграция.md** - Интеграция с IDE и инструментами
- **skill legacy-миграция.md** - Внедрение контрактов в legacy-код

---

## 📚 Источник

Методология SAGE/GRACE из документа:
- `Инструкция_SAGE_GRACE_Полная_v2.md`, раздел 6-7
