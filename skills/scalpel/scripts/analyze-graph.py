#!/usr/bin/env python3
"""Analyze a codeps graph JSON and print refactoring-relevant metrics.

Usage:
    python3 analyze-graph.py graph.json
    cat graph.json | python3 analyze-graph.py -

Accepts both codeps output shapes:
  - `-f json` display format:  {"nodes": [...], "edges": [[src, dst], ...], "nodeInfo": {pkg: {"files", "classes"}}}
  - raw / `json` subcommand:  {"own": [...], "edges": [{"source", "target"}], "stats": {pkg: {"fileCount", "classCount"}}}

Output: totals, cycles (SCCs with >1 node, with member edges), per-package table
(fan-in, fan-out, files, classes, instability), top fan-in, top fan-out, top size.
Python 3 stdlib only.
"""
import json
import sys


def load(path: str) -> dict:
    if path == "-":
        return json.load(sys.stdin)
    with open(path) as f:
        return json.load(f)


def parse(data: dict):
    """Return (nodes, edges, sizes) where edges is a set of (src, dst) tuples."""
    if "own" in data:
        nodes = list(data.get("own", []))
        edges = {(e["source"], e["target"]) for e in data.get("edges", [])}
        stats = data.get("stats", {})
        sizes = {p: (s.get("fileCount", 0), s.get("classCount", 0)) for p, s in stats.items()}
    else:
        nodes = list(data.get("nodes", []))
        edges = {tuple(e) for e in data.get("edges", [])}
        info = data.get("nodeInfo", {})
        sizes = {p: (s.get("files", 0), s.get("classes", 0)) for p, s in info.items()}
    return nodes, edges, sizes


def sccs(nodes, edges):
    """Tarjan SCC; returns list of components (lists of nodes)."""
    index, low, stack, onstack, comps = {}, {}, [], set(), []
    counter = [0]

    def strong(v):
        index[v] = low[v] = counter[0]
        counter[0] += 1
        stack.append(v)
        onstack.add(v)
        for w in nodes:
            if (v, w) in edges:
                if w not in index:
                    strong(w)
                    low[v] = min(low[v], low[w])
                elif w in onstack:
                    low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                onstack.discard(w)
                comp.append(w)
                if w == v:
                    break
            comps.append(comp)

    for v in nodes:
        if v not in index:
            strong(v)
    return comps


def report(nodes, edges, sizes):
    comps = sccs(nodes, edges)
    cycles = [c for c in comps if len(c) > 1 or any((v, v) in edges for v in c)]
    fan_in = {n: 0 for n in nodes}
    fan_out = {n: 0 for n in nodes}
    for s, t in edges:
        fan_in[t] = fan_in.get(t, 0) + 1
        fan_out[s] = fan_out.get(s, 0) + 1

    print(f"# graph: {len(nodes)} nodes, {len(edges)} edges")
    if not nodes:
        print("(empty graph)")
        return

    print(f"\n## Cycles ({len(cycles)})")
    if not cycles:
        print("  none — graph is acyclic")
    for c in sorted(cycles, key=len, reverse=True):
        members = sorted(c)
        mem = set(members)
        in_cycle = sorted((s, t) for (s, t) in edges if s in mem and t in mem)
        print(f"  {len(members)}-node cycle: {' <-> '.join(members)}")
        for s, t in in_cycle:
            print(f"      {s} -> {t}")

    print("\n## Per-package")
    print(f"  {'package':<22} {'fan-in':>6} {'fan-out':>7} {'files':>5} {'classes':>7}  instability")
    for n in sorted(nodes):
        fi, fo = fan_in.get(n, 0), fan_out.get(n, 0)
        files, classes = sizes.get(n, (None, None))
        inst = fo / (fi + fo) if (fi + fo) > 0 else 0.0
        f = str(files) if files is not None else "-"
        c = str(classes) if classes is not None else "-"
        print(f"  {n:<22} {fi:>6} {fo:>7} {f:>5} {c:>7}  {inst:.2f}")

    def top(key, label, limit=10):
        ranked = sorted(fan_in.items(), key=lambda kv: -kv[1]) if key == "in" else sorted(
            fan_out.items(), key=lambda kv: -kv[1])
        interesting = [(n, v) for n, v in ranked if v > 0][:limit]
        if interesting:
            print(f"\n## Top {label}")
            for n, v in interesting:
                print(f"  {n:<22} {v}")

    top("in", "fan-in (most dependents — hub risk)")
    top("out", "fan-out (depends on most — wide package)")

    with_size = [(n, sum(sizes[n])) for n in nodes if n in sizes]
    if with_size:
        print("\n## Top size (files+classes)")
        for n, v in sorted(with_size, key=lambda kv: -kv[1])[:10]:
            print(f"  {n:<22} {v}  (files={sizes[n][0]}, classes={sizes[n][1]})")


def main():
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    data = load(sys.argv[1])
    nodes, edges, sizes = parse(data)
    report(nodes, edges, sizes)


if __name__ == "__main__":
    main()
