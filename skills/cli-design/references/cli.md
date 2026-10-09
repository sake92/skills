# CLI design reference

Read this when building or reviewing a command-line tool. Distilled from the Command Line Interface Guidelines (clig.dev).

Core interface and configuration rules live in SKILL.md. This file expands the terminal conversation and scripting contract. For signal handling and resource cleanup use process-hygiene.

## Basics

- Use an argument-parsing library. Hand-rolled `argv` walking gets help text, `--flag=value`, combined short flags, and typo suggestions wrong.
- Exit 0 on success, non-zero on failure. Map distinct failure modes a script would branch on to distinct codes.
- Primary output to stdout, everything else to stderr.
- If startup or work takes long enough to leave users uncertain, report progress on stderr without contaminating the data stream.

## Help

- `-h` and `--help` print full help. `myapp help` and `myapp help <subcommand>` also work.
- Choose no-argument behavior from the tool's purpose. A command requiring a subcommand prints concise help; a filter may read stdin. Do not silently perform an unexpected destructive action.
- Lead with examples, especially of the common complex invocation. People copy examples; they do not read flag tables.
- Most common flags and subcommands first, not alphabetical.
- Link to web documentation, and give a support path (issue tracker, chat).
- Formatting that survives any terminal: bold headings via the terminal's capabilities, never hard-coded escape codes.

## Flags and arguments

- Prefer flags to positional arguments. `myapp deploy --env prod --region eu` reads at a glance six months later; `myapp deploy prod eu` does not, and cannot grow.
- Positionals are fine for the one obvious primary input: `rm file1 file2`, `cat file`.
- Every flag has a long form. Single-letter forms only for the handful used constantly.
- Standard names, so knowledge transfers between tools:

| Flag | Meaning |
|---|---|
| `-a, --all` | include everything |
| `-d, --debug` | debug output |
| `-f, --force` | skip confirmation, overwrite |
| `--json` | machine-readable output |
| `--plain` | line-oriented output for `grep`/`awk`, no decoration |
| `-h, --help` | help |
| `-n, --dry-run` | show what would happen |
| `--no-input` | never prompt; fail if input is needed |
| `-o, --output` | output file |
| `-p, --port` | port |
| `-q, --quiet` | suppress non-essential output |
| `-v, --verbose` | more output |
| `--version` | version |
| `--no-color` | disable color |

- `-` as a filename means stdin or stdout.
- Never a secret in a flag. Use a file, stdin, or a platform secret mechanism; command-line arguments may be exposed by process listings and diagnostics.
- Make flags order-independent where the parser allows it.
- On a typo or unknown subcommand, suggest the nearest match. Never apply the guess silently.
- Optional flag values may accept special words such as `none` or `auto`.

## Subcommands

- Consistency across subcommands: the same flag names, the same output formatting, the same help layout.
- Consistent noun-verb order at every level: `myapp container create`, `myapp container list`. Pick one order and keep it.
- Avoid pairs that sound alike: `update` and `upgrade` cannot both exist.
- No catch-all default subcommand. `myapp <anything>` must be either a known subcommand or an error, or you can never add a new subcommand without breaking someone's script.
- No abbreviation matching (`myapp del` for `delete`). Every accepted abbreviation is a permanent, undocumented alias.

## Output

- Human-first by default. Detect whether stdout is a TTY and format for the audience.
- `--json` for machines. Stable field names; additions are fine, renames and removals are breaking.
- `--plain` for the shell pipeline: one record per line, tab or space separated, no headers unless asked, no decoration.
- On success, say what happened, briefly. `Created deployment d-1234` beats silence and beats three paragraphs.
- No debug output by default. No stack traces on stderr unless `--debug`.
- Color only when stdout is a TTY, `NO_COLOR` is unset, `TERM` is not `dumb`, and `--no-color` was not passed. Same rule for spinners and progress bars.
- Long output through a pager only when interactive: `less -FIRX` semantics, so short output is not paged.
- Suggest the next command where a workflow has an obvious next step.

## Errors

- Catch expected errors and rewrite them for a human: what went wrong, and what to do about it. `config.yaml: line 12: unknown key "regon"; did you mean "region"?`
- Group repeated errors under one heading instead of printing the same line forty times.
- Put the most important line last. Eyes land at the bottom of the terminal.
- Unexpected errors: say where the log is and how to report the problem. A pre-filled issue URL with the version and a short trace is ideal.

## Interactivity

- Prompt only when stdin is a TTY and `--no-input` was not passed.
- Everything a prompt asks must also be answerable with a flag. Scripts cannot answer prompts.
- Confirm destructive actions unless `--force`. Confirmation is the default; `--force` is the opt-out.

## Robustness

- Validate input before doing anything with side effects.
- Timeouts on every network call, with reasonable defaults.
- Progress indication for anything that takes more than a couple of seconds.
- Make interrupted operations resumable where the domain allows (downloads, migrations, bulk imports).
- Crash-only cleanup: temporary files go in a location that is safe to delete on next start, so a killed run leaves nothing that breaks the next one.

## Future-proofing

- Additive changes only. New flags and new subcommands; never repurpose an existing flag.
- Before removing or changing behavior, warn for at least one release cycle, with the replacement named in the warning.
- Human-readable output may change. `--json` and `--plain` output is a contract; scripts depend on it.
- Never block on an external service the tool could function without. Tools outlive their servers.

## Distribution and analytics

- One binary where the language allows. Otherwise the platform's package manager; never `curl | sh` as the only option.
- Uninstall instructions next to install instructions.
- No telemetry without explicit opt-in. If usage data would help, ask, explain what is collected and for how long, and make the answer easy to change.

## CLI checklist

- [ ] Argument-parsing library, not hand-rolled `argv` handling?
- [ ] `-h`/`--help` full help; no-argument run prints concise help with an example?
- [ ] `--version`?
- [ ] Flags rather than positionals for anything beyond the primary input? Long form for every flag?
- [ ] Standard flag names (`--json`, `--dry-run`, `--force`, `--quiet`, `--verbose`, `--no-color`, `--no-input`) where the concept exists?
- [ ] No secret accepted as a flag value? `--*-file` or stdin instead?
- [ ] Data on stdout, messages on stderr, and a `--json` or `--plain` mode that a script can rely on?
- [ ] Color and spinners off when not a TTY, when `NO_COLOR` is set, or with `--no-color`?
- [ ] Exit codes distinguish the failure modes a caller would branch on?
- [ ] Expected errors rewritten for humans with a suggested action, most important line last?
- [ ] Prompts only on a TTY, every prompt answerable by flag, destructive actions confirmed unless `--force`?
- [ ] No catch-all default subcommand, no abbreviation matching?
- [ ] Typo suggestions offered, never applied silently?
- [ ] Ctrl-C exits promptly, cleanup is bounded, a second Ctrl-C exits immediately?
