#!/usr/bin/env python3
"""Summarize a codeps `report` JSON into a scannable text summary.

Usage:
    python3 summarize-report.py report.json
    cat report.json | python3 summarize-report.py -
    codeps report deps.json | python3 summarize-report.py -

Pure formatting — all computation (cycles, grading, metrics, suggestions)
happens in `codeps report`. This script only picks out what matters for
refactoring decisions: graded cycles, break candidates, hub metrics, knots.
Python 3 stdlib only.
"""
import json
import sys

LEVELS = ["package", "file", "type", "member"]
HUB_LIMIT = 5
EDGE_CHARS = 110


def load(path: str) -> dict:
    if path == "-":
        return json.load(sys.stdin)
    with open(path) as f:
        return json.load(f)


def ellipsis(s: str, limit: int = EDGE_CHARS) -> str:
    return s if len(s) <= limit else s[: limit - 3] + "..."


def fmt_cycle(c: dict) -> None:
    print(f"  [{c['severity']}] {' -> '.join(c['members'])}")
    if c.get("breakCandidate"):
        print(f"      breakCandidate: {c['breakCandidate']}")
    for e in c.get("edges", []):
        w = e.get("weight", 1)
        print(f"      {ellipsis(e['source'])} -> {ellipsis(e['target'])} (w={w})")


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    data = load(sys.argv[1])
    levels = data.get("levels", {})

    for level in LEVELS:
        d = levels.get(level)
        if not d:
            continue
        graph = d.get("graph") or {}
        nodes, edges = graph.get("nodes", []), graph.get("edges", [])
        cycles = d.get("cycles") or []
        metrics = d.get("metrics") or {}
        sug = d.get("suggestions") or {}

        print(f"\n## {level} ({len(nodes)} nodes, {len(edges)} edges, {len(cycles)} cycle(s))")

        if cycles:
            for c in cycles:
                fmt_cycle(c)
        else:
            print("  no cycles")

        if metrics:
            top = sorted(metrics.items(), key=lambda kv: -(kv[1].get("hub") or 0))[:HUB_LIMIT]
            print(f"  top hubs (in x out):")
            for nid, m in top:
                print(f"    {ellipsis(nid):<60} in={m.get('in', 0):<3} out={m.get('out', 0):<3} hub={m.get('hub', 0)}")

        if sug.get("hardestKnots"):
            print(f"  hardestKnots: {', '.join(sug['hardestKnots'])}")
        if sug.get("easyWins"):
            print(f"  easyWins: {', '.join(sug['easyWins'])}")
        if sug.get("breakEdges"):
            print(f"  breakEdges ({len(sug['breakEdges'])}):")
            for be in sug["breakEdges"]:
                e = be["edge"]
                print(f"    {ellipsis(e['source'])} -> {ellipsis(e['target'])} (breaks {be.get('breaks', '?')} cycle(s))")

    print()


if __name__ == "__main__":
    main()
