---
name: api-design
description: REST API design principles — resource naming, HTTP status codes, pagination (cursor + offset), filtering, error format (custom envelope vs Spring Boot 3 ProblemDetail RFC 7807), versioning, rate limiting. Ships a Spring Boot @RestController + @Valid + @ControllerAdvice reference implementation.
origin: ECC
---

# API Design Patterns

Design conventions for consistent, developer-friendly REST APIs. Principles are language-agnostic; the runnable reference implementation is Spring Boot 3 (matches project stack).

Scope is **API contract design** — request/response shape, status codes, pagination, error format, versioning. Spring-specific wiring, `@Configuration`, filters — see `springboot-patterns`.

## When to Activate

- Designing new endpoints or reviewing existing contracts.
- Adding pagination, filtering, or sorting to a list endpoint.
- Choosing an error format (custom envelope vs ProblemDetail).
- Planning API versioning or deprecation.
- Building public or partner-facing APIs.

## Resource Design

### URL structure

```
# Resources are nouns, plural, lowercase, kebab-case
GET    /api/v1/users
GET    /api/v1/users/{id}
POST   /api/v1/users
PUT    /api/v1/users/{id}
PATCH  /api/v1/users/{id}
DELETE /api/v1/users/{id}

# Sub-resources for ownership
GET    /api/v1/users/{id}/orders
POST   /api/v1/users/{id}/orders

# Actions that don't map to CRUD (use verbs sparingly)
POST   /api/v1/orders/{id}/cancel
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
```

### Naming rules

```
# GOOD
/api/v1/team-members          # kebab-case for multi-word resources
/api/v1/orders?status=active  # query params for filtering
/api/v1/users/123/orders      # nested resource for ownership

# BAD
/api/v1/getUsers              # verb in URL
/api/v1/user                  # singular (use plural)
/api/v1/team_members          # snake_case in path
/api/v1/users/123/getOrders   # verb in nested resource
```

## HTTP Methods and Status Codes

### Method semantics

| Method | Idempotent | Safe | Use for |
|--------|-----------|------|---------|
| GET | Yes | Yes | Retrieve resources |
| POST | No | No | Create, trigger actions |
| PUT | Yes | No | Full replacement of a resource |
| PATCH | No* | No | Partial update |
| DELETE | Yes | No | Remove a resource |

\* PATCH can be made idempotent with `If-Match` / revision fields.

### Status code reference

```
# Success
200 OK                    — GET, PUT, PATCH with response body
201 Created               — POST; include Location header
204 No Content            — DELETE, PUT without body

# Client errors
400 Bad Request           — malformed JSON, type coercion failure
401 Unauthorized          — missing / invalid authentication
403 Forbidden             — authenticated but not authorized
404 Not Found             — resource does not exist
409 Conflict              — duplicate entry, state conflict
422 Unprocessable Entity  — semantically invalid (valid JSON, bad data)
429 Too Many Requests     — rate limit exceeded

# Server errors
500 Internal Server Error — unexpected failure; never expose details
502 Bad Gateway           — upstream service failed
503 Service Unavailable   — overload, temporary; include Retry-After
```

### Common mistakes

- `HTTP 200` with body-level `success: false` — use the HTTP status, not a duplicated body flag.
- `500` for validation errors — `400` for malformed, `422` for semantically invalid.
- `200` for created resources — `201 Created` with a `Location` header.

## Error Format — custom envelope vs Spring Boot 3 ProblemDetail

Two canonical choices. **Pick one per service** and use it for every error.

### Option A — `ProblemDetail` (RFC 7807) — default for Spring Boot 3+

Spring Boot 3 ships `org.springframework.http.ProblemDetail` with built-in content negotiation (`application/problem+json`). Schema:

```json
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type":   "https://api.example.com/problems/validation-failed",
  "title":  "Validation failed",
  "status": 422,
  "detail": "Request body has 2 field errors.",
  "instance": "/api/v1/users",
  "errors": [
    { "field": "email", "code": "invalid_format", "message": "Must be a valid email address" },
    { "field": "age",   "code": "out_of_range",   "message": "Must be between 0 and 150" }
  ]
}
```

Extension fields (`errors` here) are allowed — the RFC explicitly permits them. `type` must be a stable, dereferenceable URI.

### Option B — custom envelope

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "message": "Must be a valid email address", "code": "invalid_format" },
      { "field": "age",   "message": "Must be between 0 and 150",     "code": "out_of_range" }
    ]
  }
}
```

### Decision

**For Spring Boot 3+ services, `ProblemDetail` (RFC 7807) is the canonical error format.** Use the custom envelope only when there is an explicit cross-stack compatibility requirement (existing consumers, gateway transformation rules, legacy API contract). Mixing both styles in one service is the worst option — it forces clients to parse two error shapes.

## Response Format

### Success response

```json
{
  "data": {
    "id": "abc-123",
    "email": "alice@example.com",
    "name": "Alice",
    "created_at": "2025-01-15T10:30:00Z"
  }
}
```

### Collection response with pagination

```json
{
  "data": [
    { "id": "abc-123", "name": "Alice" },
    { "id": "def-456", "name": "Bob" }
  ],
  "meta": {
    "total": 142,
    "page": 1,
    "per_page": 20,
    "total_pages": 8
  },
  "links": {
    "self": "/api/v1/users?page=1&per_page=20",
    "next": "/api/v1/users?page=2&per_page=20",
    "last": "/api/v1/users?page=8&per_page=20"
  }
}
```

### Envelope variants

- **Envelope** `{ data, meta?, links? }` — recommended for public APIs; easy to extend without breaking clients.
- **Flat** — return the resource directly; distinguish outcomes by HTTP status. Simpler; acceptable for internal APIs.

Do not mix both styles in one service.

## Pagination

### Offset-based (simple)

```
GET /api/v1/users?page=2&per_page=20
```
```sql
SELECT * FROM users ORDER BY created_at DESC LIMIT 20 OFFSET 20;
```

**Pros:** easy, supports jump-to-page-N. **Cons:** slow on large offsets (`OFFSET 100000`), inconsistent under concurrent inserts.

### Cursor-based (scalable)

```
GET /api/v1/users?cursor=eyJpZCI6MTIzfQ&limit=20
```
```sql
-- fetch limit+1 to determine has_next
SELECT * FROM users WHERE id > :cursor_id ORDER BY id ASC LIMIT 21;
```

```json
{
  "data": [...],
  "meta": { "has_next": true, "next_cursor": "eyJpZCI6MTQzfQ" }
}
```

Works cleanly in JPA — native query or `@Query("SELECT u FROM User u WHERE u.id > :cursor ORDER BY u.id ASC")` with `Pageable` of size `limit+1`.

**Pros:** consistent performance regardless of depth, stable under concurrent inserts. **Cons:** no jump-to-page, cursor opaque.

### When to use which

| Case | Pagination |
|------|-----------|
| Admin dashboards, small datasets (<10K) | Offset |
| Infinite scroll, feeds, large datasets | Cursor |
| Public APIs | Cursor by default; offset optional |
| Search results with page numbers | Offset |

## Filtering, Sorting, Search

### Filtering

```
# Simple equality
GET /api/v1/orders?status=active&customer_id=abc-123

# Comparison operators (bracket notation)
GET /api/v1/products?price[gte]=10&price[lte]=100
GET /api/v1/orders?created_at[after]=2025-01-01

# Multiple values (comma-separated)
GET /api/v1/products?category=electronics,clothing

# Nested fields (dot notation)
GET /api/v1/orders?customer.country=US
```

### Sorting

```
# Single field; prefix "-" for descending
GET /api/v1/products?sort=-created_at

# Multiple fields
GET /api/v1/products?sort=-featured,price,-created_at
```

### Full-text search

```
GET /api/v1/products?q=wireless+headphones
GET /api/v1/users?email=alice
```

### Sparse fieldsets

```
GET /api/v1/users?fields=id,name,email
```

## Authentication and Authorization

### Token-based auth

```
# Bearer token
GET /api/v1/users
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

# API key (server-to-server)
GET /api/v1/data
X-API-Key: sk_live_abc123
```

### Authorization

Two axes — apply both where they make sense:

- **Role / scope** — does the caller's principal have permission? Enforce at the controller or via method-level checks.
- **Ownership / tenancy** — is the resource owned by this caller? Enforce in the service layer, after loading. `404 Not Found` (not `403 Forbidden`) on ownership mismatch avoids leaking resource existence.

Spring-specific wiring (`@PreAuthorize`, method security, filter chain) — see `springboot-security`.

## Rate Limiting

### Headers

```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640000000

# When exceeded
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/problem+json

{
  "type":   "https://api.example.com/problems/rate-limited",
  "title":  "Too many requests",
  "status": 429,
  "detail": "Rate limit exceeded. Try again in 60 seconds."
}
```

### Tiers — indicative budgets

| Tier | Limit |
|------|-------|
| Anonymous (per IP) | 30/min |
| Authenticated (per user) | 100/min |
| Premium (per API key) | 1000/min |
| Internal (per service) | 10000/min |

Tune against real traffic; the table is a starting point, not a contract.

## Versioning

### URL path versioning (recommended)

```
/api/v1/users
/api/v2/users
```

**Pros:** explicit, easy to route, cacheable. **Cons:** URL changes per version.

### Header versioning

```
GET /api/users
Accept: application/vnd.myapp.v2+json
```

**Pros:** clean URLs. **Cons:** harder to test, easy to forget.

### Strategy

1. Start with `/api/v1/` — do not version until you need to.
2. Keep at most **2 active versions** (current + previous).
3. Deprecation timeline:
   - Announce (6 months notice for public APIs).
   - Add `Sunset: Sat, 01 Jan 2026 00:00:00 GMT` header.
   - Return `410 Gone` after sunset.
4. Non-breaking changes stay in-version: new optional fields, new optional params, new endpoints.
5. Breaking changes require a new version: removed/renamed fields, type changes, URL restructure, auth method change.

## Reference implementation — Spring Boot 3

Full POST-create-user endpoint with Bean Validation, `ProblemDetail` error responses, and centralised exception handling.

### DTOs

```java
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 1, max = 100) String name) {}

public record UserResponse(
        UUID id, String email, String name, Instant createdAt) {}
```

### Controller

```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest body) {

        UserResponse created = users.create(body);
        URI location = URI.create("/api/v1/users/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));
    }
}
```

Return 201 with `Location` via `ResponseEntity.created(...)`. Use `@Valid` on `@RequestBody` for DTO validation; Bean Validation throws `MethodArgumentNotValidException` which the advice below translates.

### Global exception handler — ProblemDetail

```java
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create("https://api.example.com/problems/validation-failed"));
        pd.setTitle("Validation failed");
        pd.setDetail("Request body has " + ex.getBindingResult().getFieldErrorCount() + " field errors.");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field",   fe.getField(),
                        "code",    fe.getCode(),
                        "message", fe.getDefaultMessage()))
                .toList());
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create("https://api.example.com/problems/not-found"));
        pd.setTitle("Resource not found");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    // Add further @ExceptionHandler methods per domain exception
    // (409 for duplicates, 403 for authorization, etc.) — same shape.
}
```

Extending `ResponseEntityExceptionHandler` gives consistent handling of Spring-internal exceptions (`HttpMessageNotReadableException`, `MethodArgumentNotValidException`, `HttpRequestMethodNotSupportedException`) out of the box — customise only the overrides you need.

### Notes

- Never return `Exception.getMessage()` directly in production — the message may leak SQL text or internal class names. Put a stable business-friendly string in `setDetail`, log the raw exception server-side.
- Set `spring.mvc.problemdetails.enabled=true` if you want built-in Spring MVC errors (404 on unmapped routes, 405 on wrong method) to be emitted as `ProblemDetail` automatically.

## Checklist

Before shipping a new endpoint:

- [ ] Resource URL follows naming conventions (plural, kebab-case, no verbs).
- [ ] Correct HTTP method (GET for reads, POST for creates, etc.).
- [ ] Appropriate status codes — not 200 for everything.
- [ ] Input validated with Bean Validation (`@Valid` + `@NotBlank` / `@Size` / `@Email` / custom constraints).
- [ ] Errors use the service's chosen format consistently (ProblemDetail for Spring Boot 3+).
- [ ] Pagination on list endpoints (cursor for large datasets, offset for dashboards).
- [ ] Authentication required, or explicitly marked public.
- [ ] Authorization checked — ownership enforced server-side, not via client-supplied filters.
- [ ] Rate limiting configured.
- [ ] Responses do not leak internal details (stack traces, SQL, class names).
- [ ] Field naming consistent with existing endpoints (camelCase vs snake_case).
- [ ] OpenAPI spec updated (springdoc generates one from the controller — verify).

## Related

| task | where |
|------|-------|
| Spring filter chain, method security, `@PreAuthorize` | `springboot-security` |
| `@Configuration` beans, `application.yml` wiring | `springboot-patterns` |
| JPA repositories for cursor pagination queries | `jpa-patterns` |
| Structured logs + MDC correlation on API calls | `logging-patterns` |
| Integration test for the controller | `springboot-tdd` |
