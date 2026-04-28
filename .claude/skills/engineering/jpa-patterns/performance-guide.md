# Performance Guide

Tuning checklist for JPA/Hibernate on this project (PostgreSQL + Hibernate ORM 7.3).

---

## Quick Wins (apply to all new features)

| Setting | Value | Where |
|---------|-------|-------|
| Default fetch type | `LAZY` | All `@OneToMany`, `@ManyToOne` |
| Batch fetch size | `25` | `hibernate.default_batch_fetch_size` in `application.yml` |
| Read-only service default | `@Transactional(readOnly = true)` | Service class level |
| Connection pool | HikariCP (Boot default) | `spring.datasource.hikari.*` |

---

## N+1 Decision Tree

```
Is the query a list/search endpoint?
  └── YES → Use DTO projection (interface or record)
       No entity loaded at all.

Is the query loading one aggregate with its children?
  └── Single collection → JOIN FETCH
  └── Multiple collections → @EntityGraph

Is it not feasible to rewrite the query?
  └── Add @BatchSize(size = 25) on the collection
       Loads in chunks instead of 1-by-1.
```

---

## HikariCP Tuning

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10          # (CPU cores × 2) + spindle disks
      minimum-idle: 5
      connection-timeout: 20000      # 20s
      idle-timeout: 600000           # 10min
      max-lifetime: 1800000          # 30min
```

Pool size rule: `(core_count * 2) + effective_disk_spindles`. For a 4-core machine with SSD: 8–10.

---

## Batch Insert / Update

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

Verify with SQL logging: you should see `insert into ... (batch)` lines, not individual INSERTs.

**Note**: PostgreSQL requires `reWriteBatchedInserts=true` in the JDBC URL for true batching:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/roundtrip?reWriteBatchedInserts=true
```

---

## Indexing Checklist

After writing a new query, verify the following columns have DB indexes:

- All `WHERE` clause columns
- All `JOIN` columns (FK columns on the child side)
- All `ORDER BY` columns used in paginated queries
- Unique constraints double as indexes — no duplicate needed

Add indexes via Flyway migration:

```sql
-- V3__add_order_indexes.sql
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
```

---

## Read-Heavy Service Pattern

```java
@Service
@Transactional(readOnly = true)   // all methods read-only by default
public class OrderQueryService {

    private final OrderRepository orderRepository;

    // Hibernate skips dirty-check + flush on readOnly transactions
    public Page<OrderSummary> search(OrderSearchRequest req, Pageable pageable) {
        return orderRepository.search(req, pageable);
    }
}
```

---

## Identifying Slow Queries in Development

1. Enable Hibernate SQL logging:
   ```yaml
   logging.level.org.hibernate.SQL: DEBUG
   logging.level.org.hibernate.orm.jdbc.bind: TRACE
   ```

2. Count SELECT statements per request. More than 3 for a list endpoint is a red flag.

3. Use `EXPLAIN ANALYZE` in PostgreSQL for slow queries:
   ```sql
   EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 1;
   ```

4. Wrap Testcontainers tests with `@Sql` to seed data and verify query counts.

---

## Anti-Patterns to Avoid

| Anti-Pattern | Problem | Fix |
|---|---|---|
| `FetchType.EAGER` on any association | Forces join on every load | Remove; use JOIN FETCH when needed |
| `findAll()` on large tables | Full table scan, loads everything | Add `Pageable` parameter |
| Returning `List<Entity>` from service | Leaks lazy proxy outside transaction | Return DTO projection |
| Calling `save()` in a loop | One INSERT per iteration | Use `saveAll()` |
| `Optional.get()` without check | `NoSuchElementException` | Use `orElseThrow()` with domain exception |
| Hardcoded `PageRequest.of(0, 1000)` | Unbounded result set | Accept `Pageable` from caller |
