# Query Patterns Reference

Full code examples for every query pattern used in this project.

---

## Derived Query Methods

Use for simple single-field lookups. Spring Data generates the SQL automatically.

```java
// Exact match
Optional<User> findByEmail(String email);

// Multiple conditions
List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

// Existence check (no entity load)
boolean existsByEmail(String email);

// Count
long countByStatus(OrderStatus status);
```

**Rule**: if the derived name exceeds two conditions, switch to `@Query`.

---

## @Query — JPQL

Prefer JPQL over native SQL for portability. Use text blocks for readability.

```java
@Query("""
    SELECT o FROM Order o
    JOIN FETCH o.items i
    WHERE o.userId = :userId
      AND o.status = :status
    ORDER BY o.createdAt DESC
    """)
List<Order> findWithItemsByUserAndStatus(
    @Param("userId") Long userId,
    @Param("status") OrderStatus status
);
```

---

## DTO Projection — Interface

Zero entity materialization. Best for list/search endpoints.

```java
// Projection interface
public interface OrderSummary {
    Long getId();
    String getOrderNumber();
    OrderStatus getStatus();
    LocalDateTime getCreatedAt();

    // Computed via SpEL
    @Value("#{target.firstName + ' ' + target.lastName}")
    String getFullName();
}

// Repository
List<OrderSummary> findByUserId(Long userId);
Page<OrderSummary> findByStatus(OrderStatus status, Pageable pageable);
```

---

## DTO Projection — Record (recommended for complex queries)

```java
// Record DTO
public record OrderItemDto(Long orderId, String itemName, int quantity) {}

// Repository — constructor expression
@Query("""
    SELECT new roundtrip.order.dto.OrderItemDto(o.id, i.name, i.quantity)
    FROM Order o JOIN o.items i
    WHERE o.userId = :userId
    """)
List<OrderItemDto> findItemsByUserId(@Param("userId") Long userId);
```

---

## Pagination

```java
// Service
public Page<OrderSummary> getOrders(Long userId, int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    return orderRepository.findByUserId(userId, pageable);
}

// Repository
Page<OrderSummary> findByUserId(Long userId, Pageable pageable);
```

Always use projections with pagination — never `Page<Entity>` for public APIs.

---

## Bulk Update / Delete

```java
// Bulk update (no entity load)
@Modifying
@Query("UPDATE Order o SET o.status = :status WHERE o.userId = :userId")
int cancelAllByUserId(@Param("userId") Long userId,
                      @Param("status") OrderStatus status);

// Bulk delete
@Modifying
@Query("DELETE FROM Item i WHERE i.orderId = :orderId")
void deleteByOrderId(@Param("orderId") Long orderId);
```

- `@Modifying` requires a `@Transactional` write transaction on the calling service method.
- Returns `int` (rows affected) for updates.

---

## Native SQL

Use only when the query requires database-specific features (PostgreSQL functions, CTEs, etc.).

```java
@Query(
    value = """
        SELECT o.id, o.order_number, COUNT(i.id) AS item_count
        FROM orders o
        LEFT JOIN items i ON i.order_id = o.id
        WHERE o.user_id = :userId
        GROUP BY o.id, o.order_number
        """,
    nativeQuery = true
)
List<Object[]> findOrderSummaryNative(@Param("userId") Long userId);
```

Prefer JPQL whenever possible; native SQL breaks on schema changes more easily.
