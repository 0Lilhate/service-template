---
name: sage-anchor-markup
description: SAGE/GRACE methodology — anchor-based code navigation for AI. Unique-ID paired tags (SAGE_FUNCTION_START/END with id=F_xxx_001), CrossLinks forming dependency graph, contract-oriented boundaries. Use when AI modifies codebases >800 lines with similarly-named functions. Russian-language methodology doc.
origin: newskill
---

# skill якорная разметка

> **Основной документ:** [SAGE.md](../../../SAGE.md) — методология SAGE/GRACE в репозитории. Workflow-триггеры зафиксированы в `AGENTS.md` раздел «SAGE markup triggers».
>
> **Версия:** 1.0 | **Методология:** SAGE/GRACE

---

## 🎯 Когда использовать

Используйте этот skill, когда:

- ✅ Код будет модифицироваться ИИ-ассистентами
- ✅ Проект содержит >800 строк кода
- ✅ Нужна точечная навигация по коду
- ✅ Множественные функции с похожими именами
- ✅ Требуется явный граф зависимостей

### Overlap с `sage-code-markup`

| | `sage-anchor-markup` (этот skill) | `sage-code-markup` |
|:--|:--|:--|
| Что даёт | Boundary tags `SAGE_*_START / _END` с уникальным id | Full component contract docstrings + XML-like tags + derived knowledge graph |
| Можно ли применять отдельно | **Да** — даёт AI-навигацию и граф зависимостей | **Нет** — contract docstrings опираются на anchor-границы |

Anchor — фундамент; code-markup надстраивается поверх. Если сомнение «какой skill применять» — начинать с anchor.

---

## ⚡ Quick reference

Карточка для быстрого применения без чтения всей методологии.

### Тег-пары — основные

| Сущность | START / END | ID-формат |
|:--|:--|:--|
| Функция | `# SAGE_FUNCTION_START id="F_discount_calc_001"` / `# SAGE_FUNCTION_END id="F_discount_calc_001"` | `F_<имя>_<nnn>` |
| Метод класса | `# SAGE_METHOD_START id="M_cart_total_001"` / `# SAGE_METHOD_END id="M_cart_total_001"` | `M_<класс>_<метод>_<nnn>` |
| Логический блок | `# SAGE_BLOCK_START id="B_discount_val_001"` / `# SAGE_BLOCK_END id="B_discount_val_001"` | `B_<имя>_<nnn>` |
| Класс | `# SAGE_CLASS_START id="C_calculator_001"` / `# SAGE_CLASS_END id="C_calculator_001"` | `C_<имя>_<nnn>` |
| Cross-links (зависимости) | `# SAGE_CROSS_LINKS_START id="XL_discount_001"` / `# SAGE_CROSS_LINKS_END id="XL_discount_001"` | `XL_<модуль>_<nnn>` |

Полный список (`FIELD`, `CONST`, `ENUM`, `MODULE_CONTRACT`) — см. «Типы якорей» ниже.

Для Java / Kotlin заменить `#` на `//` в префиксе тега — остальной синтаксис идентичен.

### Три правила

1. **Каждый `START` имеет парный `END`** — с **одинаковым** id.
2. **ID уникален** в пределах проекта — `F_calc_001` может существовать только один раз.
3. **ID семантически говорящий** — `F_discount_calc_001`, не `F_func_001`.

Валидация парности + уникальности — см. `sage-ide-integration` → `scripts/validate_sage.py`.

### Минимальный пример

```python
# SAGE_FUNCTION_START id="F_discount_calc_001"
def calculate_discount(amount, is_vip):
    return amount * 0.2 if is_vip else 0
# SAGE_FUNCTION_END id="F_discount_calc_001"
```

### Когда НЕ применять

- Файл `<` 200 строк с плоской структурой.
- DTO / record / POJO / enum.
- Тесты (сами — документация).
- Config-файлы (`application.yml`, `build.gradle.kts`).
- Мелкие utility-классы.

Полные триггеры — `AGENTS.md` → «SAGE markup triggers».

---

## 🔗 Связь с методологией SAGE

Этот skill является практической реализацией принципов методологии SAGE (Semantic-Anchored Graph Engineering), описанных в основном документе SAGE.md.

### Ключевые принципы SAGE, реализованные в skill:

| Принцип SAGE | Реализация в skill |
|:--|:--|
| **XML-подобные якоря** | `SAGE_FUNCTION_START/END`, `SAGE_BLOCK_START/END` и др. — парные теги для структурирования кода |
| **Уникальные ID-теги** | Каждый якорь имеет уникальный идентификатор (например, `id="F_discount_calc_001"`), что решает проблему полисемии при обработке больших контекстов ИИ |
| **CrossLinks** | Явные связи между компонентами через `SAGE_CROSS_LINKS_START/END` — формируют граф зависимостей проекта |
| **Контрактно-ориентированный подход** | Якоря определяют границы функциональных блоков с семантическими метками для точечной навигации |

### Почему это важно:

Согласно исследованиям Anthropic, при обработке контекстов >4K токенов ИИ может путаться в парных тегах с одинаковыми именами. Уникальные ID в обоих тегах (START и END) устраняют эту проблему и обеспечивают:

- **Точечную навигацию** — ИИ может точно определить границы функции или блока
- **Построение Knowledge Graph** — якоря становятся узлами графа знаний проекта
- **Контекстную изоляцию** — модификация одного блока не влияет на другие

---

## 🚀 Быстрый старт

**Минимальный пример SAGE-разметки функции:**

```python
# SAGE_FUNCTION_START id="F_discount_calc_001"
def calculate_discount(amount: float, is_vip: bool) -> float:
    # SAGE_BLOCK_START id="B_discount_val_001"
    if amount < 0:
        raise ValueError("Amount must be positive")
    # SAGE_BLOCK_END id="B_discount_val_001"
    
    # SAGE_BLOCK_START id="B_discount_calc_001"
    rate = 0.2 if is_vip else 0.0
    return amount * rate
    # SAGE_BLOCK_END id="B_discount_calc_001"
# SAGE_FUNCTION_END id="F_discount_calc_001"

# SAGE_CROSS_LINKS_START id="XL_discount_001"
#   calculate_discount -> payment_processor.apply_discount()
#   calculate_discount -> logger.log_transaction()
# SAGE_CROSS_LINKS_END id="XL_discount_001"
```

---

## 📚 Подробное описание

### Типы якорей

| Тип якоря | Описание | Пример |
|:--|:--|:--|
| **FUNCTION_START/END** | Обрамление функций | `# SAGE_FUNCTION_START id="F_name_001"` |
| **METHOD_START/END** | Обрамление методов класса | `# SAGE_METHOD_START id="M_name_001"` |
| **BLOCK_START/END** | Логические блоки кода | `# SAGE_BLOCK_START id="B_name_001"` |
| **CLASS_START/END** | Обрамление классов | `# SAGE_CLASS_START id="C_name_001"` |
| **FIELD_START/END** | Поля данных класса | `# SAGE_FIELD_START id="F_field_001"` |
| **CONST_START/END** | Константы модуля | `# SAGE_CONST_START id="CONST_name_001"` |
| **ENUM_START/END** | Перечисления | `# SAGE_ENUM_START id="E_name_001"` |

### Правила именования якорей

**Требование:** Каждый якорь должен иметь уникальный идентификатор в пределах проекта.

```python
# ❌ ПЛОХО: Обобщённые имена (полисемия)
# SAGE_FUNCTION_START: add
# SAGE_CLASS_START: Calculator

# ✅ ХОРОШО: Уникальные семантические имена
# SAGE_FUNCTION_START id="F_add_two_numbers_001"
# SAGE_CLASS_START id="C_calculator_basic_ops_001"

# ✅ ИДЕАЛЬНО: Контекстно-осмысленные имена с ID
# SAGE_FUNCTION_START id="F_math_add_int_v1_001"
# SAGE_CLASS_START id="C_todo_task_mgr_v1_001"

# ✅ ЛУЧШЕ ВСЕГО: Уникальные ID-теги (по Anthropic)
# SAGE_FUNCTION_START id="F_math_add_001"
# SAGE_FUNCTION_END id="F_math_add_001"
```

**Почему уникальные ID важны?**

По исследованиям Anthropic, при обработке больших контекстов (>4K токенов) ИИ может путаться в парных тегах. Уникальные идентификаторы в обоих тегах устраняют эту проблему:

```python
# Проблема: ИИ может перепутать, какой END к какому START
# SAGE_BLOCK_START: validation
# SAGE_BLOCK_START: db_operation
# SAGE_BLOCK_END: ???  # Какой это блок?

# Решение: Уникальные ID в обоих тегах
# SAGE_BLOCK_START id="B_task_val_001"
# SAGE_BLOCK_END id="B_task_val_001"
```

### CrossLinks: Явные связи между компонентами

CrossLinks описывают зависимости между функциями и модулями.

**Формат:**
```python
# SAGE_CROSS_LINKS_START id="XL_module_name_001"
#   function_name -> dependency.function()
#   function_name -> logger.log()
# SAGE_CROSS_LINKS_END id="XL_module_name_001"
```

**Расширенный формат с контекстом:**
```xml
<sage_cross_links>
  <link type="CALLS" target="database_module_execute_sql_query">
    <context>Выполняет SQL запросы через модуль database</context>
    <frequency>high</frequency>
    <criticality>critical</criticality>
  </link>

  <link type="LOGS_TO" target="logging_system_info_channel">
    <context>Записывает информационные сообщения в системный лог</context>
    <level>info</level>
  </link>

  <link type="DEPENDS_ON" target="config_app_settings_loader">
    <context>Использует настройки приложения из конфигурационного файла</context>
    <optional>false</optional>
  </link>
</sage_cross_links>
```

**Типы связей:**
- `CALLS` - вызов функции
- `LOGS_TO` - запись в лог
- `DEPENDS_ON` - зависимость от модуля
- `USES` - использование компонента
- `PROVIDES` - предоставление функциональности

### Интеграция с Knowledge Graph

Якоря автоматически интегрируются в граф знаний проекта:

```
SAGE_FUNCTION_START id="F_task_create_001"
    ↓
Knowledge Graph Node
    ↓
Связи через CrossLinks
    ↓
Навигация ИИ по проекту
```

---

## 📝 Шаблоны

### Шаблон 1: Функция с уникальным ID

```python
# SAGE_FUNCTION_START id="F_[domain]_[action]_[version]_[number]"
def function_name(param1: type1, param2: type2) -> return_type:
    # SAGE_BLOCK_START id="B_[function]_[block_name]_[number]"
    # Логический блок кода
    # SAGE_BLOCK_END id="B_[function]_[block_name]_[number]"
    
    return result
# SAGE_FUNCTION_END id="F_[domain]_[action]_[version]_[number]"

# SAGE_CROSS_LINKS_START id="XL_[function]_[number]"
#   function_name -> dependency.function()
# SAGE_CROSS_LINKS_END id="XL_[function]_[number]"
```

### Шаблон 2: Метод класса

```python
# SAGE_CLASS_START id="C_[domain]_[class_name]_[version]_[number]"
class ClassName:
    """Описание класса."""
    
    # SAGE_FIELD_START id="F_[class]_[field_name]_[number]"
    field_name: type = default_value
    # SAGE_FIELD_END id="F_[class]_[field_name]_[number]"
    
    # SAGE_METHOD_START id="M_[class]_[method_name]_[number]"
    def method_name(self, param: type) -> return_type:
        # SAGE_BLOCK_START id="B_[method]_[block_name]_[number]"
        # Логика метода
        # SAGE_BLOCK_END id="B_[method]_[block_name]_[number]"
        
        return result
    # SAGE_METHOD_END id="M_[class]_[method_name]_[number]"
# SAGE_CLASS_END id="C_[domain]_[class_name]_[version]_[number]"
```

### Шаблон 3: Логический блок

```python
# SAGE_BLOCK_START id="B_[context]_[block_type]_[number]"
try:
    # Операция
    result = perform_operation()
except Exception as e:
    # Обработка ошибки
    logger.error(f"Operation failed: {e}")
    raise
# SAGE_BLOCK_END id="B_[context]_[block_type]_[number]"
```

### Шаблон 4: CrossLinks

```python
# SAGE_CROSS_LINKS_START id="XL_[module]_[number]"
#   current_function -> dependency.function()
#   current_function -> logger.log()
#   current_function -> database.insert()
# SAGE_CROSS_LINKS_END id="XL_[module]_[number]"
```

### Шаблон 5: Класс с полями

```python
# SAGE_CLASS_START id="C_[domain]_[entity]_[version]_[number]"
@dataclass
class EntityName:
    """Описание сущности."""
    
    # SAGE_FIELD_START id="F_[entity]_[field1]_[number]"
    field1: type1
    # SAGE_FIELD_END id="F_[entity]_[field1]_[number]"
    
    # SAGE_FIELD_START id="F_[entity]_[field2]_[number]"
    field2: type2 = default_value
    # SAGE_FIELD_END id="F_[entity]_[field2]_[number]"
    
    # SAGE_METHOD_START id="M_[entity]_[method]_[number]"
    def method(self) -> return_type:
        return result
    # SAGE_METHOD_END id="M_[entity]_[method]_[number]"
# SAGE_CLASS_END id="C_[domain]_[entity]_[version]_[number]"
```

### Шаблон 6: Enum

```python
# SAGE_ENUM_START id="E_[domain]_[enum_name]_[number]"
class EnumName(str, Enum):
    """Описание перечисления."""
    VALUE1 = "value1"
    VALUE2 = "value2"
    VALUE3 = "value3"
# SAGE_ENUM_END id="E_[domain]_[enum_name]_[number]"
```

---

## ✅ Чек-лист качества

Перед завершением разметки проверьте:

| # | Проверка | ✅/❌ |
|:--:|:--|:--:|
| 1 | Все функции имеют `FUNCTION_START/END` с уникальными ID | |
| 2 | Все методы класса имеют `METHOD_START/END` с уникальными ID | |
| 3 | Критичные логические блоки имеют `BLOCK_START/END` | |
| 4 | ID в START и END тегах совпадают | |
| 5 | Имена якорей уникальны в пределах проекта | |
| 6 | CrossLinks описывают все внешние зависимости | |
| 7 | Типы связей в CrossLinks указаны корректно | |
| 8 | Якоря не создают лишней вложенности | |

---

## 💡 Примеры

### Пример 1: Функция валидации

```python
# SAGE_FUNCTION_START id="F_task_validate_001"
def validate_task_data(title: str, description: str) -> bool:
    """
    Валидирует данные задачи.
    
    Args:
        title: Название задачи
        description: Описание задачи
        
    Returns:
        bool: True если данные валидны
    """
    # SAGE_BLOCK_START id="B_validate_title_001"
    if not title or not title.strip():
        raise ValueError("Task title cannot be empty")
    if len(title) > 200:
        raise ValueError("Task title cannot exceed 200 characters")
    # SAGE_BLOCK_END id="B_validate_title_001"
    
    # SAGE_BLOCK_START id="B_validate_desc_001"
    if description and len(description) > 2000:
        raise ValueError("Description too long")
    # SAGE_BLOCK_END id="B_validate_desc_001"
    
    return True
# SAGE_FUNCTION_END id="F_task_validate_001"

# SAGE_CROSS_LINKS_START id="XL_task_validate_001"
#   validate_task_data -> logging.error()
# SAGE_CROSS_LINKS_END id="XL_task_validate_001"
```

### Пример 2: Метод создания задачи

```python
# SAGE_METHOD_START id="M_taskmgr_create_001"
def create_task(self, title: str, description: str = "") -> Dict:
    """
    Создает новую задачу с автоматической категоризацией.
    
    CONTRACT:
    - title: обязательный, 1-200 символов
    - description: опциональный, до 2000 символов
    - Returns: Dict с полями id, title, category
    """
    # SAGE_BLOCK_START id="B_create_validation_001"
    if not title or not title.strip():
        self.logger.error("[VALIDATION] Empty title provided")
        raise ValueError("Task title cannot be empty")
    # SAGE_BLOCK_END id="B_create_validation_001"
    
    # SAGE_BLOCK_START id="B_create_ai_categorization_001"
    try:
        category = self.ai.categorize_task(title)
        self.logger.info(f"[AI] Task categorized as '{category}'")
    except AIError as e:
        self.logger.warning(f"[AI] Categorization failed: {e}")
        category = "UNCATEGORIZED"
    # SAGE_BLOCK_END id="B_create_ai_categorization_001"
    
    # SAGE_BLOCK_START id="B_create_db_operation_001"
    task_data = {
        "title": title,
        "description": description,
        "category": category,
        "created_at": datetime.now(),
        "completed": False
    }
    
    try:
        task_id = self.db.insert("tasks", task_data)
        task_data["id"] = task_id
        self.logger.info(f"[DB] Task created with ID: {task_id}")
    except DatabaseError as e:
        self.logger.error(f"[DB] Failed to create task: {e}")
        raise
    # SAGE_BLOCK_END id="B_create_db_operation_001"
    
    return task_data
# SAGE_METHOD_END id="M_taskmgr_create_001"
```

### Пример 3: Класс сущности

```python
# SAGE_CLASS_START id="C_task_entity_001"
@dataclass
class Task:
    """
    Представляет задачу в TODO-приложении.
    
    CONTRACT:
    - id: уникальный идентификатор (автогенерация)
    - title: обязательный, 1-200 символов
    - status: по умолчанию PENDING
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
    
    # SAGE_METHOD_START id="M_task_validate_001"
    def __post_init__(self):
        """Валидирует задачу после инициализации."""
        # SAGE_BLOCK_START id="B_task_validate_title_001"
        if not self.title or not self.title.strip():
            raise ValueError("Task title cannot be empty")
        if len(self.title) > 200:
            raise ValueError("Task title cannot exceed 200 characters")
        # SAGE_BLOCK_END id="B_task_validate_title_001"
    # SAGE_METHOD_END id="M_task_validate_001"
    
    # SAGE_METHOD_START id="M_task_complete_001"
    def mark_completed(self) -> None:
        """Отмечает задачу как выполненную."""
        # SAGE_BLOCK_START id="B_task_complete_logic_001"
        if self.status == TaskStatus.COMPLETED:
            raise ValueError("Task is already completed")
        self.status = TaskStatus.COMPLETED
        self.completed_at = datetime.now()
        # SAGE_BLOCK_END id="B_task_complete_logic_001"
    # SAGE_METHOD_END id="M_task_complete_001"
# SAGE_CLASS_END id="C_task_entity_001"

# SAGE_CROSS_LINKS_START id="XL_task_entity_001"
#   Task.mark_completed() -> datetime.now()
#   Task.__post_init__() -> ValueError (validation)
# SAGE_CROSS_LINKS_END id="XL_task_entity_001"
```

### Пример 4: AI-классификатор

```python
# SAGE_CLASS_START id="C_ai_categorizer_001"
class AICategorizer:
    """
    Классифицирует задачи с использованием Claude API.
    
    CONTRACT:
    - categorize(title) -> str (одна из CATEGORIES или DEFAULT_CATEGORY)
    - Retries: до 3 попыток
    - Timeout: 5 секунд
    """
    
    # SAGE_CONST_START id="CONST_categories_001"
    CATEGORIES: List[str] = ["work", "personal", "shopping", "health", "finance", "other"]
    # SAGE_CONST_END id="CONST_categories_001"
    
    # SAGE_CONST_START id="CONST_default_cat_001"
    DEFAULT_CATEGORY = "uncategorized"
    # SAGE_CONST_END id="CONST_default_cat_001"
    
    # SAGE_METHOD_START id="M_ai_categorize_001"
    def categorize(self, title: str) -> str:
        """
        Классифицирует задачу по названию.
        
        Args:
            title: Название задачи для классификации
            
        Returns:
            str: Название категории или DEFAULT_CATEGORY при ошибке
        """
        # SAGE_BLOCK_START id="B_ai_build_prompt_001"
        prompt = f"""
<task_title>{title}</task_title>

<categories>
{chr(10).join(f'  <category>{c}</category>' for c in self.CATEGORIES)}
</categories>

Analyze the task title and select the most appropriate category.
Return ONLY the category name, nothing else.
"""
        # SAGE_BLOCK_END id="B_ai_build_prompt_001"
        
        # SAGE_BLOCK_START id="B_ai_call_api_001"
        try:
            message = self.client.messages.create(
                model=self.model,
                max_tokens=50,
                messages=[{"role": "user", "content": prompt}]
            )
            
            category = message.content[0].text.strip().lower()
            
            if category in self.CATEGORIES:
                self.logger.info(f"[AI] Categorized '{title[:30]}' -> '{category}'")
                return category
            else:
                self.logger.warning(f"[AI] Invalid category '{category}'")
                return self.DEFAULT_CATEGORY
                
        except Exception as e:
            self.logger.error(f"[AI] Categorization failed: {e}")
            return self.DEFAULT_CATEGORY
        # SAGE_BLOCK_END id="B_ai_call_api_001"
    # SAGE_METHOD_END id="M_ai_categorize_001"
# SAGE_CLASS_END id="C_ai_categorizer_001"

# SAGE_CROSS_LINKS_START id="XL_ai_categorizer_001"
#   AICategorizer.categorize() -> anthropic.Messages.create()
#   AICategorizer.categorize() -> logging.info/warning/error
# SAGE_CROSS_LINKS_END id="XL_ai_categorizer_001"
```

### Пример 5: Enum для статусов

```python
# SAGE_ENUM_START id="E_task_status_001"
class TaskStatus(str, Enum):
    """Статус задачи в TODO-приложении."""
    PENDING = "pending"
    COMPLETED = "completed"
    CANCELLED = "cancelled"
# SAGE_ENUM_END id="E_task_status_001"
```

---

## ⚠️ Антипаттерны

### Антипаттерн 1: Обобщённые имена якорей

```python
# ❌ ПЛОХО
# SAGE_FUNCTION_START: process
def process_data(data):
    pass
# SAGE_FUNCTION_END: process

# ✅ ПРАВИЛЬНО
# SAGE_FUNCTION_START id="F_payment_process_001"
def process_payment_transaction(payment_data):
    pass
# SAGE_FUNCTION_END id="F_payment_process_001"
```

### Антипаттерн 2: Отсутствие ID в закрывающем теге

```python
# ❌ ПЛОХО
# SAGE_BLOCK_START id="B_validation_001"
if not data:
    raise ValueError("Empty data")
# SAGE_BLOCK_END

# ✅ ПРАВИЛЬНО
# SAGE_BLOCK_START id="B_validation_001"
if not data:
    raise ValueError("Empty data")
# SAGE_BLOCK_END id="B_validation_001"
```

### Антипаттерн 3: Неуникальные ID

```python
# ❌ ПЛОХО: Дублирование ID
# SAGE_FUNCTION_START id="F_process_001"
def process_orders():
    pass
# SAGE_FUNCTION_END id="F_process_001"

# SAGE_FUNCTION_START id="F_process_001"  # ДУБЛИКАТ!
def process_payments():
    pass
# SAGE_FUNCTION_END id="F_process_001"

# ✅ ПРАВИЛЬНО: Уникальные ID
# SAGE_FUNCTION_START id="F_orders_process_001"
def process_orders():
    pass
# SAGE_FUNCTION_END id="F_orders_process_001"

# SAGE_FUNCTION_START id="F_payments_process_001"
def process_payments():
    pass
# SAGE_FUNCTION_END id="F_payments_process_001"
```

### Антипаттерн 4: Отсутствие CrossLinks

```python
# ❌ ПЛОХО: Нет явных связей
# SAGE_FUNCTION_START id="F_create_order_001"
def create_order(items):
    user = get_current_user()  # Неявная зависимость
    order = save_to_db(items)  # Неявная зависимость
    log_activity(order)        # Неявная зависимость
    return order
# SAGE_FUNCTION_END id="F_create_order_001"

# ✅ ПРАВИЛЬНО: Явные CrossLinks
# SAGE_FUNCTION_START id="F_create_order_001"
def create_order(items):
    user = get_current_user()
    order = save_to_db(items)
    log_activity(order)
    return order
# SAGE_FUNCTION_END id="F_create_order_001"

# SAGE_CROSS_LINKS_START id="XL_create_order_001"
#   create_order -> auth.get_current_user()
#   create_order -> database.save_to_db()
#   create_order -> logging.log_activity()
# SAGE_CROSS_LINKS_END id="XL_create_order_001"
```

### Антипаттерн 5: Избыточная вложенность

```python
# ❌ ПЛОХО: Слишком много вложенных блоков
# SAGE_FUNCTION_START id="F_calc_001"
def calculate():
    # SAGE_BLOCK_START id="B_step1_001"
    # SAGE_BLOCK_START id="B_step1a_001"
    # SAGE_BLOCK_START id="B_step1a_i_001"
    value = 1 + 1
    # SAGE_BLOCK_END id="B_step1a_i_001"
    # SAGE_BLOCK_END id="B_step1a_001"
    # SAGE_BLOCK_END id="B_step1_001"
    return value
# SAGE_FUNCTION_END id="F_calc_001"

# ✅ ПРАВИЛЬНО: Разумная гранулярность
# SAGE_FUNCTION_START id="F_calc_001"
def calculate():
    # SAGE_BLOCK_START id="B_calc_logic_001"
    value = 1 + 1
    # SAGE_BLOCK_END id="B_calc_logic_001"
    return value
# SAGE_FUNCTION_END id="F_calc_001"
```

---

## 📖 Связанные skill

- **skill код с разметкой.md** - Расширенная разметка модулей с AI-контрактами
- **skill XML-структурирование.md** - XML-структурирование для точных ответов LLM
- **skill enterprise-workflow.md** - Полный цикл GRACE-разработки
- **skill IDE-интеграция.md** - Интеграция с IDE и инструментами

---

## 📚 Источник

Методология SAGE (Semantic-Anchored Graph Engineering) из документа:
- `Инструкция_SAGE_GRACE_Полная_v2.md`, раздел 6
