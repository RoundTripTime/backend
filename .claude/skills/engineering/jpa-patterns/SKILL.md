---
name: jpa-patterns
description: JPA/Hibernate persistence patterns for this project. Use when dealing with N+1 queries, lazy loading issues, repository design, projections, bulk operations, or any Spring Data JPA performance concern.
---

# JPA Patterns

Performance-first persistence guide.
Stack: **Spring Data JPA · Hibernate ORM 7.3 · PostgreSQL · Flyway 12**

---

## Philosophy

> "Always load LAZY. Fetch explicitly when needed."

- All associations default to `FetchType.LAZY`. Never use `EAGER`.
- Repositories exist **only at aggregate-root boundaries** — never create a repo for every entity.
- Prefer **DTO projections** for read-heavy paths; avoid loading entities you won't mutate.
- ID references over entity navigation for loose coupling across aggregates.

---

## Workflow

When you have a JPA problem, follow this sequence:

```
1. Identify   — what query is slow or causing LazyInitializationException?
2. Choose     — select the pattern from the table below
3. Apply      — use the template; verify with SQL logging enabled
4. Validate   — run the query and count SELECT statements (should be 1–2)
```

Enable SQL logging during development:
```yaml
spring.jpa.properties.hibernate.format_sql: true
logging.level.org.hibernate.SQL: DEBUG
logging.level.org.hibernate.orm.jdbc.bind: TRACE
```

---

## Pattern Selection

| Situation | Pattern |
|-----------|---------|
| Simple lookup by field | Derived query method |
| Join or complex filter | `@Query` (JPQL) |
| Read-only, flat shape | DTO projection interface |
| Read-only, computed fields | `@Query` + record projection |
| Association fetch in list | `JOIN FETCH` or `@EntityGraph` |
| Update/delete many rows | `@Modifying @Query` |
| Large write batches | `saveAll()` + batch config |
| Read-heavy endpoint | `@Transactional(readOnly = true)` |
| Cross-aggregate reference | Store ID, not entity reference |

See [query-patterns.md](query-patterns.md) for full code examples.
See [performance-guide.md](performance-guide.md) for tuning checklist.

---

## N+1 Problem

The most common JPA performance trap. Occurs when loading a collection triggers one extra SELECT per element.

### Diagnosis

```java
// BAD — N+1: 1 query for orders + N queries for each order's items
List<Order> orders = orderRepository.findAll();
orders.forEach(o -> o.getItems().size()); // triggers N lazy loads
```

### Fix 1 — JOIN FETCH (single collection)

```java
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.userId = :userId")
List<Order> findWithItemsByUserId(@Param("userId") Long userId);
```

**Limit**: only one collection per query — Hibernate throws `MultipleBagFetchException` with two.

### Fix 2 — @EntityGraph (declarative)

```java
@EntityGraph(attributePaths = {"items", "payments"})
@Query("SELECT o FROM Order o WHERE o.userId = :userId")
List<Order> findWithItemsAndPayments(@Param("userId") Long userId);
```

Use when you need multiple associations without writing a complex JPQL join.

### Fix 3 — DTO Projection (read-only, best performance)

```java
public interface OrderSummary {
    Long getId();
    String getStatus();
    int getItemCount();
}

List<OrderSummary> findByUserId(Long userId);
```

No entity materialized — directly maps query result to interface. Use for all list/search endpoints.

### Fix 4 — @BatchSize (lazy-load in chunks)

```java
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@BatchSize(size = 25)
private List<Item> items;
```

Spring Boot config equivalent:
```yaml
spring.jpa.properties.hibernate.default_batch_fetch_size: 25
```

Use as a global safety net when JOIN FETCH is impractical.

---

## Repository Design

```java
// Aggregate root: Order owns Items
// → Only OrderRepository exists; no ItemRepository

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Simple lookup
    Optional<Order> findByOrderNumber(String orderNumber);

    // Read-only projection
    List<OrderSummary> findByUserId(Long userId);

    // JPQL with join
    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") Long id);

    // Pagination
    Page<OrderSummary> findByStatus(OrderStatus status, Pageable pageable);

    // Bulk update
    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.userId = :userId")
    int updateStatusByUserId(@Param("userId") Long userId,
                             @Param("status") OrderStatus status);
}
```

---

## Transaction Rules

```java
@Service
@Transactional(readOnly = true)   // default for all methods
public class OrderService {

    // Read — inherits readOnly = true
    public OrderResponse getOrder(Long id) { ... }

    // Write — overrides to readOnly = false
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) { ... }

    // Bulk update — explicit flush for @Modifying
    @Transactional
    public void cancelUserOrders(Long userId) {
        orderRepository.updateStatusByUserId(userId, OrderStatus.CANCELLED);
    }
}
```

- `readOnly = true` allows Hibernate to skip dirty checking and flush → measurable speedup on reads.
- `@Modifying` queries require a surrounding `@Transactional` write transaction.
- Never open a transaction in a controller.

---

## Bulk Operations

```java
// BAD — one INSERT per iteration
items.forEach(item -> itemRepository.save(item));

// GOOD — single batched write
itemRepository.saveAll(items);
```

Configure batch size in `application.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 25
        order_inserts: true
        order_updates: true
```

---

## Entity Association Rules

```java
// Aggregate boundary: Order → Items (owns)
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
private List<Item> items = new ArrayList<>();

// Cross-aggregate reference: store ID, not entity
@Column(name = "user_id", nullable = false)
private Long userId;           // NOT: @ManyToOne User user
```

- Within an aggregate: full `@OneToMany` / `@ManyToOne` with cascade.
- Across aggregates: store the foreign key as `Long`; load the other aggregate via its own service if needed.

---

## LazyInitializationException Checklist

| Symptom | Root Cause | Fix |
|---------|-----------|-----|
| Exception inside `@RestController` | Entity returned from service; transaction closed | Return DTO from service |
| Exception in `toString()` / logging | Logs trigger lazy load outside transaction | Don't log entities; log IDs |
| Exception in async method | New thread has no transaction context | Open new `@Transactional` in async method |
| Exception in test | No active transaction in test | Annotate test with `@Transactional` or use DTO assertion |

---

## Checklist Before Opening a PR

- [ ] No `FetchType.EAGER` anywhere
- [ ] Collections annotated `@BatchSize` or fetched with JOIN FETCH / @EntityGraph
- [ ] List/search endpoints use DTO projections, not entity lists
- [ ] Bulk writes use `saveAll()` with batch config
- [ ] All write service methods override to `@Transactional`
- [ ] No entity object crosses the service → controller boundary
- [ ] Cross-aggregate references store ID, not `@ManyToOne`
