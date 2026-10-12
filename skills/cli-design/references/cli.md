# CLI interface details

Consult the relevant section when the main skill leaves an interface decision open. Based on [Command Line Interface Guidelines](https://clig.dev/). Core implementation and verification rules live in SKILL.md.

## Parser selection

Compare native facilities and mature libraries against the command's required flags, subcommands, typed values, generated help, and errors. Confirm compatibility with the project runtime and existing invocations. Use current official documentation and briefly state the choice. Preserve contracts when replacing handwritten parsing; manual parsing requires an explicit user request.

## Help and invocation

- Support `-h`/`--help` and `--version` without requiring ordinary inputs. For subcommand tools, provide help for individual commands.
- Lead help with common examples and options. Do not silently perform destructive work when arguments are missing. Filters may legitimately read stdin with no arguments.
- Use consistent command naming and long flags. Positionals suit an obvious primary input; use named options for settings that may grow.
- Use familiar flags where applicable: `--output`, `--force`, `--dry-run`, `--quiet`, `--verbose`, `--no-input`, `--no-color`.
- Reject unknown commands and options. Offer corrections without executing guesses. Do not silently match abbreviations.
- Support `-` for stdin/stdout when the file-oriented interface calls for it. Never accept secret values in command-line arguments.

## Output contracts

- Decide who consumes each output mode. Machine formats have stable field names and types; renaming/removing fields is a compatibility change.
- Human modes may use headings and summaries. Plain pipeline modes should avoid decoration and unsolicited headers.
- Machine stdout is exclusively the payload. Saving the payload to a file leaves stdout empty; report completion on stderr. Do not apply legacy human completion messages to a new machine mode.
- Color, spinners, and pagers depend on the stream and interactivity. Honor NO_COLOR, TERM=dumb, and explicit disabling flags. Use a pager only for interactive human output.

## Errors and confirmation

- Explain the failed operation and relevant input/path, the reason, and how to proceed. Include parser location information when available. Avoid leaking secrets or dumping stack traces by default.
- Confirm destructive operations only with interactive stdin and prompting enabled. Check this before reading; piped `yes` or closed stdin must not accidentally approve an action. Provide an explicit override flag for unattended operation.
- Default confirmation to refusal. Validate inputs before writing or replacing files. Preserve outputs when validation fails.

## Compatibility

Preserve existing invocations, documented output, defaults, and failure codes unless the task authorizes changes. New modes can introduce cleaner contracts without changing legacy modes. If a breaking change is required, explain the migration and deprecation path.

For shutdown, interruption, subprocesses, and resource ownership, use process-hygiene. For configuration sources and precedence, read configuration.md only when those concerns are being changed.
