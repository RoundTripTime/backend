---
name: spring-base
description: Spring Boot 4 enterprise development guide. Use when building new features, designing layers, handling exceptions, or when the user asks for Spring best practices, project structure, or architecture guidelines.
---

# Spring Base

Comprehensive guide for enterprise Spring Boot 4 development on this project.
Stack: **Java 21 · Spring Boot 4.0.6 · Spring Security 7 · Gradle 9 (Kotlin DSL)**

---

## Core Principles

1. **Constructor injection only** — no `@Autowired` on fields; never field-inject.
2. **Layered architecture** — `Controller → Service → Repository`. Controllers do not call repositories directly.
3. **No business logic in controllers** — they validate input, delegate, and map responses.
4. **One responsibility per class** — services orchestrate; entities model; repositories query.
5. **No hardcoded secrets** — always from `application.yml` or environment variables.
6. **Type-safe configuration** — use `@ConfigurationProperties` records, not `@Value` scatter.

---

## Workflow

For any non-trivial feature, follow these steps in order:

```
1. Analyse   — read related code, identify boundaries, note side effects
2. Design    — sketch layers (entity · repo · service · controller · DTO)
3. Implement — write from the bottom up: entity → repo → service → controller
4. Secure    — add @PreAuthorize / SecurityFilterChain rules
5. Test      — unit tests for services, integration tests with Testcontainers
6. Document  — Swagger @Operation / @Parameter on every public endpoint
```

Never skip Step 1. Read before writing.

---

## Layer Templates

### Entity

```java
@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Factory method — no public constructor
    public static Item create(String name) {
        var item = new Item();
        item.name = name;
        return item;
    }
}
```

- Prefer `@GeneratedValue(IDENTITY)` for PostgreSQL.
- Protect the default constructor (`PROTECTED`); expose factory methods.
- Use `@Column(nullable = false)` to reflect DB constraints in the model.

### Repository

```java
public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByName(String name);

    @Query("SELECT i FROM Item i WHERE i.status = :status")
    List<Item> findByStatus(@Param("status") ItemStatus status);
}
```

- Extend `JpaRepository<T, ID>` for standard CRUD.
- Derived queries for simple lookups; `@Query` (JPQL) for joins and filters.
- See [../jpa-patterns/SKILL.md](../jpa-patterns/SKILL.md) for N+1 and performance patterns.

### Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)    // default; override on writes
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemResponse getItem(Long id) {
        return itemRepository.findById(id)
            .map(ItemResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Item", id));
    }

    @Transactional
    public ItemResponse createItem(CreateItemRequest request) {
        var item = Item.create(request.name());
        return ItemResponse.from(itemRepository.save(item));
    }
}
```

- Class-level `@Transactional(readOnly = true)` — override with `@Transactional` only on write methods.
- Return DTOs, not entities, from the service layer.
- Throw domain exceptions (`EntityNotFoundException`, `BusinessRuleException`); never `RuntimeException` directly.

### Controller

```java
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
@Tag(name = "Items")
public class ItemController {

    private final ItemService itemService;

    @GetMapping("/{id}")
    @Operation(summary = "Get item by ID")
    public ResponseEntity<ItemResponse> getItem(@PathVariable Long id) {
        return ResponseEntity.ok(itemService.getItem(id));
    }

    @PostMapping
    @Operation(summary = "Create a new item")
    public ResponseEntity<ItemResponse> createItem(
            @RequestBody @Valid CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(itemService.createItem(request));
    }
}
```

- All request bodies must be `@Valid`.
- Return `ResponseEntity<T>` with explicit status codes.
- Annotate every endpoint with `@Operation`.

### DTO (Java Record)

```java
public record CreateItemRequest(
    @NotBlank @Size(max = 100) String name
) {}

public record ItemResponse(Long id, String name) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName());
    }
}
```

- Use Java records for all request/response DTOs.
- Validation annotations belong on the record component.
- Static factory methods (`from`) for mapping from entities.

---

## Global Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList());
        return problem;
    }
}
```

- Use RFC 9457 `ProblemDetail` (built into Spring 6+).
- Never expose stack traces or internal class names in responses.
- Always log the exception before returning.

---

## Configuration Properties

```java
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long accessExpiryMs,
    long refreshExpiryMs
) {}
```

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET}
  access-expiry-ms: ${JWT_ACCESS_EXPIRY_MS:1800000}
  refresh-expiry-ms: ${JWT_REFRESH_EXPIRY_MS:1209600000}
```

- Always use `@ConfigurationProperties` records over scattered `@Value`.
- Validate with `@Validated` + Bean Validation annotations where needed.

---

## Architecture Constraints

| Rule | Rationale |
|------|-----------|
| Controllers don't import `Repository` | Bypasses service/transaction layer |
| Services return DTOs, not entities | Prevents lazy-load explosions outside transactions |
| Entities have no service/repo imports | Keeps domain model pure |
| No `@Transactional` on controllers | Transaction scope must align with service logic |
| Flyway for all schema changes | Schema must be version-controlled and reproducible |

---

## Checklist Before Opening a PR

- [ ] No field injection (`@Autowired` on fields)
- [ ] All request bodies annotated `@Valid`
- [ ] Write paths have `@Transactional`; read paths `readOnly = true`
- [ ] No entity returned from service layer
- [ ] Global exception handler covers the new exception types
- [ ] SpringDoc annotations on every new endpoint
- [ ] Flyway migration added for schema changes
