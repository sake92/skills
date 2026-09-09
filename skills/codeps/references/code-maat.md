# Evolutionary evidence with Code Maat

Use Code Maat alongside Codeps when deciding a structural boundary from actual work patterns: which files repeatedly change together, where work is concentrated, and which maintainers need to validate a change. It is not a dependency extractor and it does not prove that two files belong in one module.

The bundled runner is `scripts/code-maat-cli`. It pins the standalone JAR, downloads it on first use, and accepts normal Code Maat arguments.

## Reproducible Git input

Run from the repository root. Pick a recent window that represents the architecture being changed—typically the last 6–18 months, or since the last large migration—and keep it fixed while comparing proposals. Limit the history to source paths and exclude generated, vendored, lock-file, documentation, and bulk-migration noise. Save the exact command, start date, path filters, and Git `HEAD` with the CSV results; do not commit a repository's raw history unless that is explicitly intended.

Code Maat's documented `git2` format is the preferred input:

```bash
analysis_dir="/tmp/code-maat-$(git rev-parse --short HEAD)"
mkdir -p "$analysis_dir"

git log --all --numstat --date=short --pretty=format:'--%h--%ad--%aN' \
  --no-renames --after=2025-03-01 -- \
  src ':(exclude)src/generated/**' ':(exclude)vendor/**' \
  > "$analysis_dir/git.log"

bash "/abs/path/to/skills/codeps/scripts/code-maat-cli" \
  -l "$analysis_dir/git.log" -c git2 -a summary \
  > "$analysis_dir/summary.csv"
```

Use an explicit source path instead of `src` when the repository has a different layout. A very large log may need `JAVA_TOOL_OPTIONS=-Xmx4g`. Code Maat operates in memory, so narrow the date range and path scope before increasing memory.

`--no-renames` makes the supported `git2` input robust but means that an old and a new path are separate entities. For a move proposal, either use history after the move, inspect both paths as one conceptual component, or rely on package-level grouping below. Do not treat a missing old path as proof that a component stopped changing.

## Analyses to run

Start with `summary`, then run only the analysis that answers the decision at hand:

```bash
# Pairs that co-change. Keep verbose shared-revision counts for noise checks.
bash "/abs/path/to/skills/codeps/scripts/code-maat-cli" \
  -l "$analysis_dir/git.log" -c git2 -a coupling \
  --verbose-results > "$analysis_dir/coupling.csv"

# Files with many co-change relationships: prioritize a hotspot investigation.
bash "/abs/path/to/skills/codeps/scripts/code-maat-cli" \
  -l "$analysis_dir/git.log" -c git2 -a soc > "$analysis_dir/soc.csv"

# Concentrated or fragmented knowledge: involve these people in design review.
bash "/abs/path/to/skills/codeps/scripts/code-maat-cli" \
  -l "$analysis_dir/git.log" -c git2 -a authors > "$analysis_dir/authors.csv"
bash "/abs/path/to/skills/codeps/scripts/code-maat-cli" \
  -l "$analysis_dir/git.log" -c git2 -a entity-effort > "$analysis_dir/entity-effort.csv"
```

`coupling` reports `entity`, `coupled`, a percentage `degree`, and `average-revs`; verbose output adds each entity's revision count and shared revisions. Do not rank a pair only by percentage: a high degree supported by very few shared revisions is weak evidence. Code Maat defaults to at least five revisions, five shared revisions, coupling at least 30%, and ignores changesets larger than 30 entities. Keep these thresholds for a baseline; adjust them only deliberately and record the change. Large commits (formatters, dependency upgrades, generated-code refreshes) can manufacture co-change, so remove them from the source history or treat their pairs as noise.

`soc` (sum of coupling) is a prioritization list, not a mandate to centralize a file. Pair it with Codeps propagator, cycle, and public-surface evidence to determine whether the hotspot needs an API boundary, better locality, or simply smaller changes.

## Aggregate to proposed components or teams

Code Maat can aggregate history using a committed group file passed with `-g`. Each non-comment mapping is `regex_pattern => logical_group_name`; the first useful mapping should represent proposed feature/component boundaries, not people. Example:

```text
^src/orders/.* => orders
^src/billing/.* => billing
^src/shared/money/.* => money
```

Run the same analysis with `-g "$analysis_dir/components.txt"` to ask whether *components* co-change, rather than letting one noisy file decide the result:

```bash
bash "/abs/path/to/skills/codeps/scripts/code-maat-cli" \
  -l "$analysis_dir/git.log" -c git2 -a coupling \
  -g "$analysis_dir/components.txt" --verbose-results \
  > "$analysis_dir/component-coupling.csv"
```

For a move or team-boundary decision, create one mapping for the current layout and one for each plausible proposed layout. Run them against the *same* `git.log` and thresholds. A proposal that turns repeated file-level co-change into mostly within-component change, while Codeps keeps dependencies one-way, is evidence for that grouping. It is not proof: inspect the high-coupling pairs that remain across groups before accepting the design.

For a proposed team split, use component coupling and `authors`/`entity-effort` to estimate coordination cost and identify reviewers. Do not generate groups from author names and do not infer reporting lines or individual performance from commit metadata. Git authorship is distorted by pairing, rebases, bot accounts, bulk commits, and prior reorganizations.

## Decision rules

| Combined finding | Likely decision | Check before acting |
|---|---|---|
| Same package/file pair has a Codeps edge or cycle and high, well-supported co-change | Co-locate the feature logic or remove the dependency through a smaller contract. | Inspect the shared revisions for a schema/config/test-fixture cause. |
| High co-change across an otherwise low-coupled static boundary | Find and make the implicit dependency explicit; do not merge by default. | Read representative commits and verify it is not release/bulk noise. |
| High Codeps propagator but low recent co-change | Keep the stable shared component narrow; a disruptive split may not repay itself. | Check whether the static hub is a deliberate platform/API. |
| Low cross-component co-change plus one-way Codeps dependency | Candidate for independent team ownership and, when build cost warrants it, a module boundary. | Confirm public API/contract is intentional and the dependency direction remains acyclic. |
| One component has many authors and high co-change with many components | Prioritize it as a coordination hotspot; reduce its responsibilities or establish an explicit owning interface. | Involve frequent contributors; do not assign people based only on the report. |

After a move, run Codeps again to verify the static change. Re-run Code Maat only after enough normal work has accumulated to make a behavioral comparison meaningful; a single refactoring commit cannot demonstrate reduced future coordination.
