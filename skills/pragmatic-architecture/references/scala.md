# Scala decisions

- Default to `private`; use package visibility only for a real feature-level collaborator, never as a test escape hatch.
- Put feature packages under one root such as `app.orders.{http,domain,db}`. Use Scalafix forbidden-import rules or ArchUnit to forbid upward dependencies such as `..orders.db..` → `..orders.domain..` and `..orders.domain..` → `..orders.http..`.
- For cycle prevention, consider [Acyclic](https://github.com/com-lihaoyi/acyclic): it turns marked file or package dependency cycles into compiler errors. For Scala 3 layer-direction rules, consider [layers-dotty-plugin](https://github.com/lolgab/layers-dotty-plugin), which validates package dependencies declared with `@dependsOn` annotations. Use either only when a compiler-enforced rule adds value beyond the repository's existing checks.
- Prefer immutable `case class` values and opaque types for IDs when their safety benefit exceeds call-site friction.
- Keep framework annotations, HTTP models, and persistence mapping out of domain models; map at the feature layer boundary.
- Test pure domain functions directly and exercise routes with the feature wired to real infrastructure.

```scala
// app/orders/domain/OrderService.scala
final class OrderService(store: OrderStore):
  def create(command: CreateOrder): Either[OrderError, Order] =
    store.insert(Order.from(command))

// app/orders/http/OrderRoutes.scala
final class OrderRoutes(service: OrderService):
  def create(request: CreateOrderRequest): ApiResponse =
    service.create(request.toCommand).fold(ApiResponse.fromError, ApiResponse.fromOrder)
```

For sbt, Mill, or Deder module boundaries, see `build-tools.md`.
