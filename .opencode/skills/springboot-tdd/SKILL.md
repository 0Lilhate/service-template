---
name: springboot-tdd
description: Test-driven development for Spring Boot using JUnit 5, Mockito, MockMvc, Testcontainers, and JaCoCo. Use when adding features, fixing bugs, or refactoring.
origin: ECC
---

# Spring Boot TDD Workflow

TDD guidance for Spring Boot services with 80%+ coverage (unit + integration).

## When to Use

- New features or endpoints
- Bug fixes or refactors
- Adding data access logic or security rules

## Workflow

1) Write tests first (they should fail)
2) Implement minimal code to pass
3) Refactor with tests green
4) Enforce coverage (JaCoCo)

## TDD Discipline: RED → GREEN → REFACTOR gates

### RED gate (mandatory before writing production code)

Before modifying business logic, verify a valid RED state via one of:

- **Runtime RED** — the test compiles, is executed, and fails for the intended business-logic reason.
- **Compile-time RED** — the new test references the buggy / missing code path; the compile failure itself is the intended RED signal.

In both cases the failure must be caused by the intended bug or missing implementation — **not** by unrelated syntax errors, broken setup, or missing dependencies. A test that was only written but not compiled and executed does NOT count as RED.

### GREEN gate

Rerun the same relevant test target after the minimal fix and confirm the previously failing test now passes. Proceed to refactor only after a valid GREEN result on that specific test.

### Git checkpoints (if the repo is under git)

Preferred compact checkpoint workflow:
- one commit for RED (failing test added and validated): `test: add reproducer for <feature or bug>`
- one commit for GREEN (minimal fix applied and test passes): `fix: <feature or bug>`
- one optional commit for REFACTOR: `refactor: clean up after <feature or bug>`

Before treating a checkpoint as satisfied, verify the commit is reachable from the current `HEAD` on the active branch. Do not treat commits from other branches or earlier unrelated work as valid checkpoint evidence.

## Unit Tests (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {
  @Mock MarketRepository repo;
  @InjectMocks MarketService service;

  @Test
  void createsMarket() {
    CreateMarketRequest req = new CreateMarketRequest("name", "desc", Instant.now(), List.of("cat"));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Market result = service.create(req);

    assertThat(result.name()).isEqualTo("name");
    verify(repo).save(any());
  }
}
```

Patterns:
- Arrange-Act-Assert
- Avoid partial mocks; prefer explicit stubbing
- Use `@ParameterizedTest` for variants

### Parameterized Test Example

```java
@ParameterizedTest
@CsvSource({
    "valid@email.com, true",
    "invalid-email,   false",
    "'',              false"
})
void validatesEmail(String email, boolean expected) {
  assertThat(validator.isValid(email)).isEqualTo(expected);
}
```

Use when asserting the same invariant across many inputs. Keep the parameter list focused — if you need branching logic per case, write separate tests instead.

## Web Layer Tests (MockMvc)

```java
@WebMvcTest(MarketController.class)
class MarketControllerTest {
  @Autowired MockMvc mockMvc;
  @MockBean MarketService marketService;

  @Test
  void returnsMarkets() throws Exception {
    when(marketService.list(any())).thenReturn(Page.empty());

    mockMvc.perform(get("/api/markets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }
}
```

## Integration Tests (SpringBootTest)

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketIntegrationTest {
  @Autowired MockMvc mockMvc;

  @Test
  void createsMarket() throws Exception {
    mockMvc.perform(post("/api/markets")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"Test","description":"Desc","endDate":"2030-01-01T00:00:00Z","categories":["general"]}
        """))
      .andExpect(status().isCreated());
  }
}
```

## Persistence Tests (DataJpaTest)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
class MarketRepositoryTest {
  @Autowired MarketRepository repo;

  @Test
  void savesAndFinds() {
    MarketEntity entity = new MarketEntity();
    entity.setName("Test");
    repo.save(entity);

    Optional<MarketEntity> found = repo.findByName("Test");
    assertThat(found).isPresent();
  }
}
```

## Testcontainers

- Use reusable containers for Postgres/Redis to mirror production
- Wire via `@DynamicPropertySource` to inject JDBC URLs into Spring context

## Coverage (JaCoCo)

Maven snippet:
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.14</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>verify</phase>
      <goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```

## Assertions

- Prefer AssertJ (`assertThat`) for readability
- For JSON responses, use `jsonPath`
- For exceptions: `assertThatThrownBy(...)`

## Test Data Builders

```java
class MarketBuilder {
  private String name = "Test";
  MarketBuilder withName(String name) { this.name = name; return this; }
  Market build() { return new Market(null, name, MarketStatus.ACTIVE); }
}
```

## Edge cases to cover

For any non-trivial method or endpoint, test at minimum:

- **Null / empty** — `null` argument, empty string, empty collection, empty `Optional`.
- **Boundary values** — `Integer.MIN_VALUE` / `MAX_VALUE`, `BigDecimal.ZERO`, single-element collections, epoch dates.
- **Invalid types / malformed input** — wrong format, out-of-enum values, Unicode / emoji / SQL-meta characters.
- **Error paths** — downstream failure, DB exception, timeout, authorization rejection. Test the error path, not just the happy path.
- **Concurrency** — for code with shared state, test race-sensitive ordering (see `skill: java-concurrency`).
- **Large data** — 10k+ items, large payload body, pagination beyond one page.

## Best practices checklist

Before marking tests complete:

- [ ] One assert per test — each test focuses on a single observable behavior.
- [ ] Descriptive test names (`should_return_404_when_user_not_found`, not `test1`).
- [ ] Edge cases from checklist above are covered, not just the happy path.
- [ ] External dependencies mocked to isolate unit tests.
- [ ] Tests are independent — no shared mutable state; each test sets up its own data in `@BeforeEach`.
- [ ] Unit tests run fast (< 50ms each). Integration tests marked and run in a separate phase.
- [ ] No `@Disabled` / `@Ignore` / commented-out tests left behind.
- [ ] Coverage report reviewed to identify gaps; not chasing coverage % blindly.

## CI Commands

- Maven: `mvn -T 4 test` or `mvn verify`
- Gradle: `./gradlew test jacocoTestReport`

**Remember**: Keep tests fast, isolated, and deterministic. Test behavior, not implementation details.

## Common Failure Modes

| Symptom | Root cause | Fix |
|---------|------------|-----|
| Mock returns null / `@Mock` not injected | `@ExtendWith(MockitoExtension.class)` missing | add the extension; verify `@InjectMocks` target matches constructor |
| NPE inside test on a dependency | `@MockBean` vs `@Mock` mix-up — `@MockBean` needed for Spring context (`@WebMvcTest`, `@SpringBootTest`) | use `@MockBean` in Spring-loaded tests, `@Mock` in plain JUnit |
| Flaky test — passes locally, fails in CI | shared mutable state or time dependency | `@BeforeEach` reset; replace `Thread.sleep` with `Awaitility`; avoid `Instant.now()` in assertions |
| Context load fails | missing bean or conflicting `@ComponentScan` | narrow to slice test (`@WebMvcTest`, `@DataJpaTest`); use `@MockBean` for external dependencies |
| Integration test ignores `@Transactional` rollback | direct JDBC bypasses JPA cache | flush with `TestEntityManager.flush()` or use `@DirtiesContext` sparingly |
| Testcontainers container leaks between tests | `static` container + no `@Container` registration | mark container `static` + annotate with `@Container`; use reusable config for speed |
| Tests assert on internal state / `verify()` call counts | testing implementation details instead of observable behavior | assert on return values, side effects, HTTP response shape, DB state — not private fields or exact mock invocation counts |
| Brittle selectors (`jsonPath("$[0].field")` against unstable ordering, XPath coupled to layout) | tests break on unrelated refactors | pin to semantic keys (`jsonPath("$.fieldName")`), stable IDs, `@JsonProperty` contract fields |
| Test B depends on data from test A | shared mutable state / not resetting between tests | each test sets up its own data; use `@Transactional` rollback for DB tests; fresh `@MockBean` per class |
