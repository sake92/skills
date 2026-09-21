# Configuration reference (SKILL.md §1, §2)

Read this when adding a setting, writing a config loader, or reviewing how a program reads its environment.

## Three kinds of setting

| Kind | Examples | Home |
|---|---|---|
| Varies per invocation | output path, `--dry-run`, verbosity, target file | flag |
| Varies per deployment or per user | listen port, database host, log format, region, feature toggle for one environment | env var, and usually a flag too |
| Stable for everyone on the project | lint rules, list of enabled modules, team-wide defaults | versioned config file in the repo |

A setting may live in more than one home. That is what precedence is for. What a setting must not do is live in *one surprising* home: a per-deployment value hard-coded in a source file, or a per-project rule that each developer has to export in their shell.

## Precedence walk-through

Order, highest wins: **flag > env var > project config file > user config file > system config file > built-in default.**

Worked example for the listen port of `myapp`:

| Source | Value | Wins? |
|---|---|---|
| built-in default | 8080 | |
| `/etc/myapp/config.yaml` | (unset) | |
| `~/.config/myapp/config.yaml` | 8090 | |
| `./myapp.yaml` (project) | 9000 | |
| `MYAPP_PORT` | 9100 | |
| `--port 9200` | 9200 | yes |

The loader records the winning source for every value. That makes error messages precise:

```
myapp: port: 9200 (from flag --port) is out of range 1024-65535
```

versus the useless `Invalid port`. The same recorded source feeds the effective-config dump below.

## Environment variable naming

- Uppercase letters, digits, and underscores. Never starts with a digit.
- Prefixed with the application name. `MYAPP_DB_HOST`, `MYAPP_LOG_FORMAT`.
- Single-line values. Multi-line certificates or JSON blobs go in a file; the env var holds the path.

| Good | Bad | Why |
|---|---|---|
| `MYAPP_DB_HOST` | `DBHOST` | no prefix, will collide with another program |
| `MYAPP_PORT` | `PORT` | shared by half the ecosystem; acceptable only when the platform itself sets it |
| `MYAPP_LOG_FORMAT` | `myapp_log_format` | lowercase breaks convention and some shells |
| `MYAPP_REGION` | `1MYAPP_REGION` | cannot start with a digit |
| `MYAPP_DEBUG` | `DEBUG` | widely used generic; honor it as input, do not redefine it |

Standard variables to honor rather than reinvent, when they apply: `NO_COLOR`, `FORCE_COLOR`, `DEBUG`, `EDITOR`, `PAGER`, `SHELL`, `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`, `NO_PROXY`, `TERM`, `TMPDIR`, `HOME`, `XDG_CONFIG_HOME`, `XDG_STATE_HOME`, `XDG_CACHE_HOME`.

## Where files live

Follow the XDG Base Directory Specification for anything the program writes on the user's behalf:

| Purpose | Path | Default |
|---|---|---|
| user config | `$XDG_CONFIG_HOME/myapp/config.yaml` | `~/.config/myapp/config.yaml` |
| state that should persist (history, last run) | `$XDG_STATE_HOME/myapp/` | `~/.local/state/myapp/` |
| cache that can be deleted at any time | `$XDG_CACHE_HOME/myapp/` | `~/.cache/myapp/` |
| system-wide config | `/etc/myapp/config.yaml` | |

- On macOS, XDG paths are the accepted convention for command-line tools; `~/Library/Application Support/myapp/` is for GUI applications.
- On Windows, `%APPDATA%\myapp\` for config and `%LOCALAPPDATA%\myapp\` for cache.
- Project-level config sits at the repository root: `./myapp.yaml` or `./.myapp/config.yaml`, committed.
- Never `~/.myapprc`. Dotfiles in `$HOME` are the pre-XDG habit and clutter the one directory users see most.

If the program has to modify a config file it does not own (a shell rc file, another tool's settings), ask first and print exactly what will change.

## .env files

A `.env` file is a local-development convenience: it saves developers from exporting five variables before every run. Treat it as such:

- Non-secret values only, or clearly placeholder credentials for a local container.
- Gitignored. A committed `.env.example` with placeholder values documents the shape.
- Loaded only when present, and only in development. Production sets real environment variables through the platform.
- Not the configuration format. `.env` has no types, no nesting, no comments worth the name, and no history. A setting that is stable for the whole project belongs in the versioned config file.

## Load once, validate once

One loader, one typed object, built at startup, handed to everything else. The rest of the program never calls `getenv`.

```
type Config = {
  port: Port                 // validated 1024..65535
  dbUrl: URI                 // parsed, scheme checked
  dbPasswordFile: Path       // exists and is readable, checked here
  logFormat: LogFormat       // enum: json | text
  region: Region             // enum, not a free string
}

fun loadConfig(args, env, files): Config
  // 1. gather raw values from each source, remember the source per key
  // 2. apply precedence
  // 3. parse into typed fields; on failure exit 2 with "<key>: <value> (from <source>) <reason>"
  // 4. cross-field checks (e.g. tls requires certFile)
  // 5. return the immutable Config
```

Consequences:

- Business code receives `Config` or a slice of it (`config.db`) as a constructor argument. It cannot be misconfigured at runtime because misconfiguration is impossible past the loader.
- Tests construct `Config` directly, no environment fiddling.
- Fields are typed. A port is a `Port`, not an `Int`; a URL is a `URI`, not a `String`. See pragmatic-architecture §4 on newtypes.
- Cross-field rules live in the loader, once.

Exit code 2 for configuration errors is a common convention, distinct from 1 for runtime failure.

## Effective-config dump

At startup, at debug level, log the resolved configuration with each value's source and secrets redacted. It is the first thing anyone needs when a deployment behaves unexpectedly.

```
config: port=9200 (flag --port)
config: dbUrl=postgres://db.internal:5432/app (env MYAPP_DB_URL)
config: dbPasswordFile=/run/secrets/db (env MYAPP_DB_PASSWORD_FILE)
config: dbPassword=*** (file /run/secrets/db)
config: logFormat=json (project ./myapp.yaml)
config: region=eu-west-1 (default)
```

Redaction is a property of the field, decided in the config type, not something each log call remembers to do.
