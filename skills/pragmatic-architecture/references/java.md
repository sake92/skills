# Java decisions

- Default members and nested helpers to `private`. Use package-private for a top-level helper shared within one package; make a type `public` only when a real caller in another package or module needs it.
- Structure packages as `app.orders.http`, `app.orders.domain`, and `app.orders.db`. Use ArchUnit in CI to forbid upward and cross-feature-internal dependencies; name the repository's actual package patterns in the rule.
- Prefer records for immutable transport/value data and small value types for IDs where mistaken interchange is plausible.
- Keep Jackson/JPA annotations and their models at HTTP/DB edges rather than on domain models.
- Use JPMS only for build modules that need stronger encapsulation; see `java-modules.md`.

```java
// app/orders/domain/OrderService.java
final class OrderService {
  private final OrderStore store;

  OrderService(OrderStore store) {
    this.store = Objects.requireNonNull(store);
  }

  Result<Order> create(CreateOrder command) {
    return store.insert(Order.from(command));
  }
}

// app/orders/db/OrderStore.java -- concrete until a real abstraction earns its cost
final class OrderStore {
  private final Map<OrderId, Order> orders = new HashMap<>();

  Result<Order> insert(Order order) {
    orders.put(order.id(), order);
    return Result.ok(order);
  }
}
```

For Maven or Gradle module boundaries, see `build-tools.md`.
