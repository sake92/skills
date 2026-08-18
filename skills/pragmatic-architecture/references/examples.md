# Examples: hexagonal structure across languages

The shape is always the same: `domain` depends on nothing but the standard library and pure utilities; adapters depend on `domain` and know how to talk to the outside world; `composition` is the only thing that knows about everyone.

```
domain/       -- entities, value objects, ports (interfaces), pure business logic
adapters/
  db/          -- implements domain ports against a specific database
  http-in/     -- entry point: REST/GraphQL/CLI — calls domain through ports
  http-out/    -- outbound calls to third-party services
composition/  -- wiring / dependency injection / main
```

## Scala (sbt multi-module)

```scala
// modules/domain/src/main/scala/app/domain/OrderId.scala
opaque type OrderId = String
object OrderId:
  def apply(raw: String): OrderId = raw
  extension (id: OrderId) def value: String = id

// modules/domain/src/main/scala/app/domain/OrderRepository.scala
trait OrderRepository:
  def find(id: OrderId): Option[Order]
  def save(order: Order): Unit

// modules/db/src/main/scala/app/db/SqliteOrderRepository.scala
final class SqliteOrderRepository(conn: Connection) extends OrderRepository:
  def find(id: OrderId): Option[Order] = ??? // SQL, mapping
  def save(order: Order): Unit = ???

// modules/composition/src/main/scala/app/Main.scala
@main def run(): Unit =
  val repo: OrderRepository = SqliteOrderRepository(connect())
  val routes = OrderRoutes(repo)
  startServer(routes)
```

`domain`'s `build.sbt` entry has no dependency on `db`, `http-in`, or a DB driver. `db` and `http-in` each depend only on `domain`, not on each other. Only `composition` depends on everything.

## Java (Maven/Gradle modules)

```java
// domain/src/main/java/app/domain/OrderId.java
public record OrderId(String value) {}

// domain/src/main/java/app/domain/OrderRepository.java
public interface OrderRepository {
    Optional<Order> find(OrderId id);
    void save(Order order);
}

// db/src/main/java/app/db/SqliteOrderRepository.java
public final class SqliteOrderRepository implements OrderRepository {
    private final Connection conn;
    // constructor, find(), save() — SQL lives here, never in domain
}

// composition/src/main/java/app/Main.java
public class Main {
    public static void main(String[] args) {
        OrderRepository repo = new SqliteOrderRepository(connect());
        var routes = new OrderRoutes(repo);
        startServer(routes);
    }
}
```

Same rule: `domain`'s `pom.xml`/`build.gradle` has zero dependency on JDBC, a web framework, or Jackson annotations on domain classes.

## Rust (Cargo workspace)

```rust
// domain/src/order_id.rs
#[derive(Clone, Copy, PartialEq, Eq)]
pub struct OrderId(String);

// domain/src/order_repository.rs
pub trait OrderRepository {
    fn find(&self, id: OrderId) -> Option<Order>;
    fn save(&self, order: Order);
}

// db/src/sqlite_order_repository.rs
pub struct SqliteOrderRepository { conn: Connection }
impl OrderRepository for SqliteOrderRepository {
    fn find(&self, id: OrderId) -> Option<Order> { /* SQL */ }
    fn save(&self, order: Order) { /* SQL */ }
}

// composition/src/main.rs
fn main() {
    let repo: Box<dyn OrderRepository> = Box::new(SqliteOrderRepository::new(connect()));
    start_server(OrderRoutes::new(repo));
}
```

`domain` crate's `Cargo.toml` has no `rusqlite`/`sqlx`/`reqwest` dependency. `cargo-modules` or `cargo-deny` can assert this in CI.

## TypeScript (npm/pnpm workspaces)

```ts
// packages/domain/src/orderId.ts
export type OrderId = string & { readonly __brand: "OrderId" };
export const OrderId = (raw: string): OrderId => raw as OrderId;

// packages/domain/src/orderRepository.ts
export interface OrderRepository {
  find(id: OrderId): Promise<Order | undefined>;
  save(order: Order): Promise<void>;
}

// packages/db/src/sqliteOrderRepository.ts
export class SqliteOrderRepository implements OrderRepository {
  constructor(private db: Database) {}
  async find(id: OrderId) { /* SQL */ }
  async save(order: Order) { /* SQL */ }
}

// packages/composition/src/main.ts
const repo: OrderRepository = new SqliteOrderRepository(connect());
startServer(createOrderRoutes(repo));
```

`packages/domain/package.json` has no dependency on a DB driver or HTTP client.

---

## Frontend mapping (React or HTMX)

The same shape applies, with "domain" meaning something thinner: pure state/business rules with no framework dependency.

```
domain/     -- pure TS: cart math, validation rules, a state machine for a flow
             -- testable with zero DOM, zero fetch mocks
adapters/
  api/       -- fetch/axios wrappers, one function per endpoint
  storage/   -- localStorage/IndexedDB access
ui/         -- components: thin, mostly presentation, call into domain/adapters
composition/ -- App.tsx, providers, hooks that wire domain+adapters to ui
```

Checkable rule: if testing a component's business logic requires mocking `fetch` or rendering the whole tree, the logic is in the wrong layer — pull it out of the component into a plain, framework-free function in `domain/`.

Don't barrel-export everything from `index.ts` "just in case" — export only what another module actually imports today (this is §1, Minimal API surface, applied to a frontend package).

**HTMX case:** the same domain/adapters split still holds even without a client-side framework — `domain/` is server-side pure logic, `adapters/` are DB/HTTP calls, and the HTML-fragment-returning route handlers are the equivalent of `ui/`. Reach for this instead of a React SPA when the feature is mostly server-rendered CRUD without heavy client-side state (see SKILL.md §5, principle of least power).

## Edge validation and generated schemas

**Validate at the edge (SKILL.md §7)** — the adapter turns raw input into known-good types, so core logic never re-validates:

```scala
// http-in adapter: parse + validate at the boundary
case class CreateOrderRequest(email: String, quantity: Int)

def handle(raw: CreateOrderRequest): Either[ApiError, OrderId] =
  for
    email <- ValidatedEmail.parse(raw.email).left.map(_.toApiError)
    qty   <- PositiveInt.from(raw.quantity).left.map(_.toApiError)
    id    <- orders.create(email, qty).left.map(_.toApiError)   // domain: assumes known-good email/qty; its error is translated to the consumer's shape at the edge
  yield id
```

**Generate, don't hand-maintain (SKILL.md §8)** — for a RabbitMQ/Kafka pair, check in one Avro or protobuf schema and generate both producer and consumer types from it (`buf generate` / avro-maven-plugin or sbt-avro in CI, plus regeneration + `git diff --exit-code`). Two hand-written JSON payload definitions will drift, and the queue will not tell you when.
