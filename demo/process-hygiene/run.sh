#!/usr/bin/env bash
# Ask a small model to review each fixture with and without the skill.
# Usage: demo/process-hygiene/run.sh [model]   (default: haiku)
# Run from a plain terminal; `claude -p` cannot be nested inside a Claude Code session.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
demo="$root/demo/process-hygiene"
model="${1:-haiku}"
out="$demo/results"
mkdir -p "$out"
skill="$root/skills/process-hygiene"

review_prompt() {
  local fixture="$1"
  cat <<P
Review the file below for production-readiness problems. List concrete findings, one per line, each with a one-sentence fix. If you find nothing wrong, say so.

FILE: $fixture
\`\`\`
$(cat "$demo/fixtures/$fixture")
\`\`\`
P
}

for fixture in jvm/Main.java shell/dev.sh cli/tool.py negative/Pricing.java; do
  name="${fixture%%/*}"
  echo "== $name (without skill)" >&2
  review_prompt "$fixture" | claude -p --model "$model" > "$out/$name.without.md"
  echo "== $name (with skill)" >&2
  {
    echo "You must follow this skill while reviewing. Read it fully, then use only the reference files it points you to for the file type at hand."
    echo; echo "SKILL.md:"; cat "$skill/SKILL.md"
    for ref in "$skill"/references/*.md; do echo; echo "$(basename "$ref"):"; cat "$ref"; done
    echo; review_prompt "$fixture"
  } | claude -p --model "$model" > "$out/$name.with.md"
done
echo "results in $out" >&2
