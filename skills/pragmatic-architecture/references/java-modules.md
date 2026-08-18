# Java modules (JPMS): compiler-enforced encapsulation

`module-info.java` makes the module boundary from SKILL.md §2 enforceable by `javac` and `java` instead of by convention or lint. A `public` class in a non-exported package cannot be compiled against or accessed from outside its module — not even by reflection, unless the package is opened. Official guide: https://dev.java/learn/modules/

## The core idea

Before JPMS, `public` meant "everyone". Now the module graph decides:

- `exports com.app.domain` — the package is part of the module's API surface.
- Everything not exported is private to the module, no matter how many `public` keywords are in it.

## Minimal example

```java
// domain/src/main/java/module-info.java
module app.domain {
    exports com.app.domain;
}

// db/src/main/java/module-info.java
module app.db {
    requires app.domain;
    requires java.sql;
    // exports nothing — SqliteOrderRepository is an implementation detail
}

// composition/src/main/java/module-info.java
module app.composition {
    requires app.domain;
    requires app.db;
    requires app.httpin;
}
```

## Directive reference

| Directive | Meaning |
|---|---|
| `exports pkg` | `pkg` is readable by every module that `requires` this one |
| `exports pkg to other.mod` | `pkg` is readable only by `other.mod` (qualified export) |
| `requires mod` | this module reads `mod` at compile and run time |
| `requires transitive mod` | this module re-exports `mod` — use when your exported API *signatures* use types from `mod`, otherwise consumers can't compile against your API |
| `requires static mod` | compile-time only; optional at run time (e.g. annotation processors) |
| `uses Interface` | this module consumes the service `Interface` (ServiceLoader) |
| `provides Interface with Impl` | this module provides an implementation of the service (ServiceLoader) |
| `opens pkg` | deep reflection allowed (private members) — e.g. for serialization frameworks |
| `opens pkg to other.mod` | reflection allowed only for `other.mod` — the narrowest escape hatch |

## Hexagonal mapping (SKILL.md §3)

- `domain` — exports entities + ports, nothing else. `requires` only `java.base` + pure libraries.
- adapters (`db`, `http-in`, `http-out`) — export nothing; `requires` `domain` plus their driver/HTTP library. Ports are implemented here.
- `composition` — the wiring becomes declarative via services instead of a growing `Main`:

```java
// composition/src/main/java/module-info.java
module app.composition {
    requires app.domain;
    requires app.db;
    uses com.app.domain.OrderRepository;
}

// db/src/main/java/module-info.java
module app.db {
    requires app.domain;
    requires java.sql;
    provides com.app.domain.OrderRepository
        with com.app.db.SqliteOrderRepository;
}
```

Then `ServiceLoader.load(OrderRepository.class)` in `Main` gets the wired implementation without `composition` mentioning `SqliteOrderRepository` in code. Qualified `exports ... to` fits the case where exactly one module should see a package — but in hexagonal code, needing it is usually a sign the type belongs on a port instead.

## Incremental adoption

1. **Classpath first** — everything lands in the *unnamed module*, which reads all modules and is read by none; nothing changes.
2. **Move JARs to the module path** — plain JARs become *automatic modules* named after the JAR file; they export all packages and read everything.
3. **Write `module-info.java` module by module, leaf-first** (domain first, composition last). Bootstrap a draft from a plain JAR with `jdeps --generate-module-info out app.jar`.
4. **Verify:** `jdeps` (bundled with the JDK) prints the module graph; `jdeps --check <module>` validates a declared module. In Maven/Gradle, the compiler plugin handles `module-info.java` since JDK 9.

## Gotchas

- **Reflection frameworks need `opens`.** Jackson, Hibernate, Mockito read private fields or generate subclasses. Without `opens` (or `--add-opens`) they throw `InaccessibleObjectException` — that's the module system working as designed. Open the narrowest package to the specific module: `opens com.app.domain to org.hibernate.orm.core`.
- **Split packages are illegal** — the same package name in two modules fails resolution. (Another reason the §1/§2 discipline must exist before JPMS does.)
- **Tests usually run on the classpath** (Surefire/JUnit don't require modules). Mockito's inline mockmaker may need `--add-opens java.base/java.lang=ALL-UNNAMED` etc.; once everything has `module-info.java`, run tests on the module path too so visibility violations show up in tests as well as main code.
- **`requires transitive`** when your exported API uses types from another module — otherwise consumers can't compile against your API.
- **Scala/Kotlin:** `module-info.java` is Java-only source; Scala/Kotlin classes can *live inside* a Java-declared module but cannot declare one. For those projects, build-tool modules + ArchUnit (references/enforcement.md) is the equivalent guarantee.
- **Don't modularize a 3-class app.** JPMS pays off when modules multiply, when you ship a library, or when internal API leakage is an actual recurring bug — adopt it when the §2 boundary needs teeth, not as ceremony.
