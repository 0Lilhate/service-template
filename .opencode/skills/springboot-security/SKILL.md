---
name: springboot-security
description: Spring Security best practices for authn/authz, validation, CSRF, secrets, headers, rate limiting, and dependency security in Java Spring Boot services.
origin: ECC
---

# Spring Boot Security Review

Use when adding auth, handling input, creating endpoints, or dealing with secrets.

## When to Activate

- Adding authentication (JWT, OAuth2, session-based)
- Implementing authorization (@PreAuthorize, role-based access)
- Validating user input (Bean Validation, custom validators)
- Configuring CORS, CSRF, or security headers
- Managing secrets (Vault, environment variables)
- Adding rate limiting or brute-force protection
- Scanning dependencies for CVEs

## OWASP Top 10 quick reference

Systematic checklist — before any PR touching sensitive paths, run through:

| # | category | Java/Spring countermeasure | see section |
|---|----------|----------------------------|-------------|
| A01 | Broken Access Control | `@PreAuthorize`, method security, ownership checks; deny-by-default | Authorization |
| A02 | Cryptographic Failures | BCrypt/Argon2 for passwords; HTTPS enforced; no custom crypto | Password Encoding + deploy-level |
| A03 | Injection (SQL / command / LDAP) | Spring Data derived queries; `:param` bindings in `@Query`; no `ProcessBuilder` with user input | SQL Injection Prevention |
| A04 | Insecure Design | threat model at architecture phase; document risk decisions | `skill: java-microservices` for distributed flow design |
| A05 | Security Misconfiguration | deny-by-default security config; prod profile without debug; `spring.jpa.open-in-view=false` | Security Headers + Authentication |
| A06 | Vulnerable Components | OWASP Dependency Check / Snyk in CI; pin Spring Boot LTS | Dependency Security |
| A07 | Identification / Auth Failures | JWT revocation list; session fixation; lockout on brute-force | Authentication + Rate Limiting |
| A08 | Software / Data Integrity | signed artefacts; `gitleaks` / `trufflehog` for leaks; `npm ci` / `./gradlew --no-daemon` reproducible build | `skill: springboot-verification` Phase 4 |
| A09 | Logging / Monitoring Failures | structured JSON logs; MDC correlation-id; no PII at INFO | Logging and PII + `skill: logging-patterns` |
| A10 | SSRF | validate + allowlist URL targets for outbound calls; no `RestTemplate.getForObject(userInput)` | Input Validation (URL case) |

For Java-specific review checklist, also consult `@java-reviewer` agent — security section covers SQL injection, command injection, path traversal, hardcoded secrets.

## Authentication

- Prefer stateless JWT or opaque tokens with revocation list
- Use `httpOnly`, `Secure`, `SameSite=Strict` cookies for sessions
- Validate tokens with `OncePerRequestFilter` or resource server

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      Authentication auth = jwtService.authenticate(token);
      SecurityContextHolder.getContext().setAuthentication(auth);
    }
    chain.doFilter(request, response);
  }
}
```

## Authorization

- Enable method security: `@EnableMethodSecurity`
- Use `@PreAuthorize("hasRole('ADMIN')")` or `@PreAuthorize("@authz.canEdit(#id)")`
- Deny by default; expose only required scopes

```java
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/users")
  public List<UserDto> listUsers() {
    return userService.findAll();
  }

  @PreAuthorize("@authz.isOwner(#id, authentication)")
  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
```

## Input Validation

- Use Bean Validation with `@Valid` on controllers
- Apply constraints on DTOs: `@NotBlank`, `@Email`, `@Size`, custom validators
- Sanitize any HTML with a whitelist before rendering

```java
// BAD: No validation
@PostMapping("/users")
public User createUser(@RequestBody UserDto dto) {
  return userService.create(dto);
}

// GOOD: Validated DTO
public record CreateUserDto(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email String email,
    @NotNull @Min(0) @Max(150) Integer age
) {}

@PostMapping("/users")
public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserDto dto) {
  return ResponseEntity.status(HttpStatus.CREATED)
      .body(userService.create(dto));
}
```

## SQL Injection Prevention

- Use Spring Data repositories or parameterized queries
- For native queries, use `:param` bindings; never concatenate strings

```java
// BAD: String concatenation in native query
@Query(value = "SELECT * FROM users WHERE name = '" + name + "'", nativeQuery = true)

// GOOD: Parameterized native query
@Query(value = "SELECT * FROM users WHERE name = :name", nativeQuery = true)
List<User> findByName(@Param("name") String name);

// GOOD: Spring Data derived query (auto-parameterized)
List<User> findByEmailAndActiveTrue(String email);
```

## Password Encoding

- Always hash passwords with BCrypt or Argon2 — never store plaintext
- Use `PasswordEncoder` bean, not manual hashing

```java
@Bean
public PasswordEncoder passwordEncoder() {
  return new BCryptPasswordEncoder(12); // cost factor 12
}

// In service
public User register(CreateUserDto dto) {
  String hashedPassword = passwordEncoder.encode(dto.password());
  return userRepository.save(new User(dto.email(), hashedPassword));
}
```

## CSRF Protection

- For browser session apps, keep CSRF enabled; include token in forms/headers
- For pure APIs with Bearer tokens, disable CSRF and rely on stateless auth

```java
http
  .csrf(csrf -> csrf.disable())
  .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

## Secrets Management

- No secrets in source; load from env or vault
- Keep `application.yml` free of credentials; use placeholders
- Rotate tokens and DB credentials regularly

```yaml
# BAD: Hardcoded in application.yml
spring:
  datasource:
    password: mySecretPassword123

# GOOD: Environment variable placeholder
spring:
  datasource:
    password: ${DB_PASSWORD}

# GOOD: Spring Cloud Vault integration
spring:
  cloud:
    vault:
      uri: https://vault.example.com
      token: ${VAULT_TOKEN}
```

## Security Headers

```java
http
  .headers(headers -> headers
    .contentSecurityPolicy(csp -> csp
      .policyDirectives("default-src 'self'"))
    .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
    .xssProtection(Customizer.withDefaults())
    .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
```

## CORS Configuration

- Configure CORS at the security filter level, not per-controller
- Restrict allowed origins — never use `*` in production

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
  CorsConfiguration config = new CorsConfiguration();
  config.setAllowedOrigins(List.of("https://app.example.com"));
  config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
  config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
  config.setAllowCredentials(true);
  config.setMaxAge(3600L);

  UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
  source.registerCorsConfiguration("/api/**", config);
  return source;
}

// In SecurityFilterChain:
http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
```

## Rate Limiting

- Apply Bucket4j or gateway-level limits on expensive endpoints
- Log and alert on bursts; return 429 with retry hints

```java
// Using Bucket4j for per-endpoint rate limiting
@Component
public class RateLimitFilter extends OncePerRequestFilter {
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  private Bucket createBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
        .build();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String clientIp = request.getRemoteAddr();
    Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createBucket());

    if (bucket.tryConsume(1)) {
      chain.doFilter(request, response);
    } else {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
    }
  }
}
```

## Dependency Security

- Run OWASP Dependency Check / Snyk in CI
- Keep Spring Boot and Spring Security on supported versions
- Fail builds on known CVEs

## Logging and PII

- Never log secrets, tokens, passwords, or full PAN data
- Redact sensitive fields; use structured JSON logging
- See `skill: logging-patterns` for MDC / structured-logs patterns

### Error messages — generic for user, detailed for server

```java
// BAD: leaks stack trace / internal details to client
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, String>> handleAny(Exception ex) {
  return ResponseEntity.status(500).body(Map.of(
      "error", ex.getMessage(),
      "stack", Arrays.toString(ex.getStackTrace())));  // NEVER
}

// GOOD: generic for client, detailed for server log
@ExceptionHandler(Exception.class)
public ResponseEntity<ProblemDetail> handleAny(Exception ex) {
  log.error("unhandled_request_error", ex);   // full context stays in server logs
  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "An unexpected error occurred. Please try again.");  // opaque message
  return ResponseEntity.of(problem).build();
}
```

For Spring Boot 3+, prefer `ProblemDetail` (RFC 7807) — do not hand-craft error envelopes with stack traces.

## File Uploads

Validate all three independently — attackers bypass single-check easily:

```java
private static final long MAX_SIZE = 5L * 1024 * 1024;  // 5 MB
private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif");
private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif");

public void validateUpload(MultipartFile file) {
  if (file.getSize() > MAX_SIZE) {
    throw new InvalidUploadException("File too large (max 5 MB)");
  }
  if (!ALLOWED_TYPES.contains(file.getContentType())) {
    throw new InvalidUploadException("Unsupported content type");
  }
  String name = Optional.ofNullable(file.getOriginalFilename())
      .orElseThrow(() -> new InvalidUploadException("Missing filename"));
  String ext = name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
  if (!ALLOWED_EXTENSIONS.contains(ext)) {
    throw new InvalidUploadException("Unsupported extension");
  }
  // Additional: verify magic bytes match content type (e.g., Apache Tika / `com.j256.simplemagic`).
}
```

- Store outside the web root. For persistent storage, use object storage (S3, Azure Blob) with signed URLs.
- Normalize filenames (strip path components, reject `../`, limit to `[A-Za-z0-9._-]`).
- Virus-scan uploads if they can be downloaded by other users (ClamAV / managed scanner).

## Security testing

Automated tests cheaper than post-incident fixes. Target patterns:

- **Auth boundary test:** hit protected endpoint without credentials → expect `401`; with valid non-admin token → expect `403` on admin route.
- **Input validation test:** POST malformed payload (missing `@NotBlank` field, email without `@`, `age=-1`) → expect `400` with field error; verify error does not leak stack trace.
- **Rate-limit test:** submit > N requests in window → expect `429` with `Retry-After` header.
- **SQL-injection regression test:** submit `' OR 1=1 --` as parameter → expect normal `404` / `400`, not data leak. Combined with `@DataJpaTest` that uses Spring Data derived queries catches most issues.
- **CSRF test (for session apps):** state-changing POST without CSRF token → `403`.
- **Auth token expiry:** expired JWT → `401`; valid refresh-token flow works.

Use `@SpringBootTest` + MockMvc for HTTP-level assertions (`skill: springboot-tdd`); run full suite in CI before merge.

## Checklist Before Release

- [ ] Auth tokens validated and expired correctly
- [ ] Authorization guards on every sensitive path (`@PreAuthorize` or filter-level)
- [ ] All inputs validated (`@Valid` + Bean Validation) and sanitized
- [ ] No string-concatenated SQL; native `@Query` uses `:param` bindings
- [ ] CSRF posture correct for app type (enabled for sessions, disabled for stateless JWT with documented reason)
- [ ] Secrets externalized (env vars / Vault); none committed
- [ ] Security headers configured (CSP, X-Frame-Options, XSS protection, Referrer-Policy)
- [ ] Rate limiting on APIs + stricter limits on expensive endpoints (search, auth, token issuance)
- [ ] Dependencies scanned and up to date (OWASP Dependency Check / Snyk in CI)
- [ ] Logs free of sensitive data; structured JSON; MDC correlation-id
- [ ] Error messages opaque for clients; full details only in server logs (`ProblemDetail` for Spring Boot 3+)
- [ ] HTTPS enforced in production; `server.ssl.*` or behind TLS-terminating proxy
- [ ] CORS configured to specific origins — not `*` in production
- [ ] File uploads validated: size + content type + extension + magic bytes
- [ ] Automated security tests present (auth / validation / rate-limit / SQL-injection regression)

**Remember**: Deny by default, validate inputs, least privilege, and secure-by-configuration first.
