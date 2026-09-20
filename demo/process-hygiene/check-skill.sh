#!/usr/bin/env bash
# Mechanical checks for skills/process-hygiene. Exit non-zero on any failure.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
skill="$root/skills/process-hygiene"
fail=0
say() { printf '%s\n' "$*" >&2; }

# frontmatter: name matches dir, description present and single-line
name=$(sed -n '2p' "$skill/SKILL.md" 2>/dev/null || true)
[[ "$name" == "name: process-hygiene" ]] || { say "bad name line: $name"; fail=1; }
sed -n '3p' "$skill/SKILL.md" 2>/dev/null | grep -q '^description: .\{200,\}' || { say "description missing or too short"; fail=1; }
[[ "$(sed -n '4p' "$skill/SKILL.md" 2>/dev/null)" == "---" ]] || { say "frontmatter must close on line 4 (single-line description)"; fail=1; }

# line budget
lines=$(wc -l < "$skill/SKILL.md" 2>/dev/null || echo 0)
(( lines >= 150 && lines <= 220 )) || { say "SKILL.md has $lines lines, want 150-220"; fail=1; }

# banned words anywhere in the skill and demo
if grep -rniE 'snowplow|TBD|TODO' "$skill" "$root/demo/process-hygiene" --include='*.md' --include='*.sh' --include='*.java' --include='*.py' 2>/dev/null | grep -v check-skill.sh; then
  say "banned word found"; fail=1
fi

# every references/*.md mentioned in SKILL.md exists, and every file in references/ is mentioned
for f in $(grep -o 'references/[a-z-]*\.md' "$skill/SKILL.md" 2>/dev/null | sort -u); do
  [[ -f "$skill/$f" ]] || { say "SKILL.md references missing file $f"; fail=1; }
done
for f in "$skill"/references/*.md; do
  [[ -e "$f" ]] || continue
  b="references/$(basename "$f")"
  grep -q "$b" "$skill/SKILL.md" || { say "$b not mentioned in SKILL.md"; fail=1; }
done

(( fail == 0 )) && say "check-skill: OK"
exit $fail
