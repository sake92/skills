# Java modules (JPMS): optional compiler-enforced encapsulation

Use JPMS when build modules or libraries need stronger encapsulation. It prevents consumers from accessing a `public` class in a non-exported package. It does not decide the right feature or layer boundaries; define and test those separately.

## Feature-module mapping

Usually start with one build module containing feature packages. If a feature genuinely becomes an independently owned or reusable build module, export only its deliberate API package:

```java
module app.orders {
    exports app.orders.api;
    requires java.sql;
}
```

Keep `app.orders.http`, `app.orders.domain`, and `app.orders.db` unexported. The feature's own source can use them; other modules cannot. `main` requires feature modules and uses their exported API.

Do not turn each layer into a JPMS module by default. That creates dependency and build ceremony that the feature-first layout is intended to avoid.

## Directives

| Directive | Meaning |
|---|---|
| `exports pkg` | Makes a package accessible to modules that require this module. |
| `exports pkg to mod` | Makes a package accessible only to a named consumer module. |
| `requires mod` | Reads a dependency at compile and run time. |
| `requires transitive mod` | Re-exports a dependency used by an exported API signature. |
| `requires static mod` | Needed at compile time but optional at run time. |
| `opens pkg [to mod]` | Permits deep reflection; scope it as narrowly as possible. |

## Adoption and caveats

1. Add `module-info.java` one build module at a time, starting with leaves.
2. Use `jdeps --generate-module-info` as a draft, then reduce exports and requires deliberately.
3. Reflection-heavy frameworks may need narrow `opens`; review the boundary before opening everything.
4. JPMS prevents access to non-exported packages but does not prevent an undesirable declared dependency. Keep ArchUnit or equivalent rules for layer direction.
5. Avoid split packages: the same package cannot exist in two named modules.

Official overview: <https://dev.java/learn/modules/>.
