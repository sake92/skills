# jdeps input (Java/JVM)

Use this reference for Java/JVM projects when the build has class files but no richer compiler dependency export. Generate `jdeps` class-detail output from the build's existing classes:

```bash
jdeps -verbose:class -filter:none -cp classes classes > jdeps.txt
```

Configure the resulting file relative to the Codeps project root:

```yaml
projects:
  app:
    root: .
    source: jdeps
    inputs: [jdeps.txt]
    exclude: [java.**, javax.**]
```

`jdeps` gives Codeps package structure only: there is no `files` report and public-surface/use metrics are unavailable (`ports` and `mutPorts` are zero; `dependentsPerPublicPort` is null). Use cycles and propagators, and do not interpret the unavailable surface metrics as evidence of good encapsulation.
