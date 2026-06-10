#!/usr/bin/env python3
"""
context-fit footprint sweep  —  Phase 1 (prove the signal). Maintainer-only.

Reads Claude Code session transcripts (main session + Task sub-agents + any
sibling sessions) and extracts the READ / SEARCH footprint of a unit of work,
so we can see what the agent looked up, what it re-read, and what it searched
for and didn't find.

This is the orchestrator-proof capture path: it sweeps the persisted JSONL
transcripts rather than relying on PostToolUse hooks (which don't observe
sub-agent tool calls). See references/context-fit-design.md §5a.

Usage:
  python3 footprint.py --project-dir <encoded-dir-name> [--branch B] [--since ISO] [--json OUT]
  python3 footprint.py --cwd /abs/project/path        [--branch B] [--since ISO] [--json OUT]
  python3 footprint.py --session <session-uuid>       [--json OUT]

Notes:
  - Transcripts live under ~/.claude/projects/<cwd-derived-dir>/<session>.jsonl
    with sub-agents in <session>/subagents/agent-*.jsonl
  - --cwd matches on the in-transcript `cwd` field (robust against the
    path->dir encoding), and spans every session whose cwd is under that path.
"""
import argparse, glob, json, os, re, sys
from collections import Counter, defaultdict

PROJECTS = os.path.expanduser("~/.claude/projects")

# Bash sub-commands we treat as part of the read/search footprint.
SEARCH_BIN = re.compile(r"(?:^|[|;&]|\s)(?:rg|grep|egrep|fgrep|ag|ack|find)\b")
READ_BIN   = re.compile(r"(?:^|[|;&]|\s)(?:cat|head|tail|less|bat|sed -n|awk)\b")
NO_MATCH   = re.compile(r"no (?:matches|files) found|0 matches|^\s*$", re.I)


def iter_entries(path):
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    yield json.loads(line)
                except json.JSONDecodeError:
                    continue
    except OSError:
        return


def session_files(args):
    """Return [(label, path), ...] — main session(s) + their sub-agent files."""
    out = []
    if args.session:
        dirs = glob.glob(f"{PROJECTS}/*/{args.session}.jsonl")
        mains = dirs
    elif args.project_dir:
        base = os.path.join(PROJECTS, args.project_dir)
        mains = sorted(glob.glob(f"{base}/*.jsonl"))
    else:  # --cwd : scan all project dirs, keep sessions whose cwd is under it
        mains = []
        target = os.path.abspath(args.cwd).rstrip("/")
        for f in glob.glob(f"{PROJECTS}/*/*.jsonl"):
            for e in iter_entries(f):
                cwd = e.get("cwd")
                if cwd:
                    if os.path.abspath(cwd).rstrip("/").startswith(target):
                        mains.append(f)
                    break
    for m in mains:
        out.append(("main:" + os.path.basename(m)[:8], m))
        sub_dir = m[:-6] + "/subagents"  # strip .jsonl
        for s in sorted(glob.glob(f"{sub_dir}/agent-*.jsonl")):
            aid = os.path.basename(s).replace("agent-", "").replace(".jsonl", "")[:10]
            out.append(("sub:" + aid, s))
    return out


def looks_like_path(t):
    t = t.strip().strip("\"'")
    return bool(t) and not t.startswith("-") and not t.isdigit() and ("/" in t or "." in t)


def classify_bash(cmd):
    """Return ('search'|'read'|None, query)."""
    if SEARCH_BIN.search(cmd):
        return "search", cmd.strip()
    if READ_BIN.search(cmd):
        # count as a file read only if a real path argument follows — NOT a
        # pipe-tail `head -20` reading stdin (no file), which would capture "-20"
        m = re.search(r"\b(?:cat|bat|less|head|tail)\s+(?:-\S+\s+)*([^\s|;&><]+)", cmd)
        if m:
            tgt = m.group(1).strip("\"'")
            if looks_like_path(tgt):
                return "read", tgt
    return None, None


def extract(files, branch=None, since=None):
    calls = []                      # one row per read/search tool_use
    results = {}                    # tool_use_id -> result text
    for label, path in files:
        for e in iter_entries(path):
            if branch and e.get("gitBranch") not in (branch, None):
                continue
            if since and (e.get("timestamp") or "") < since:
                continue
            msg = e.get("message") or {}
            content = msg.get("content")
            if not isinstance(content, list):
                continue
            ts = e.get("timestamp")
            side = bool(e.get("isSidechain"))
            for b in content:
                if not isinstance(b, dict):
                    continue
                bt = b.get("type")
                if bt == "tool_result":
                    txt = b.get("content")
                    if isinstance(txt, list):
                        txt = " ".join(str(x.get("text", "")) for x in txt if isinstance(x, dict))
                    results[b.get("tool_use_id")] = str(txt)[:400]
                elif bt == "tool_use":
                    name, inp = b.get("name", ""), (b.get("input") or {})
                    uid = b.get("id")
                    row = {"ts": ts, "src": label, "side": side, "uid": uid,
                           "tool": name, "kind": None, "target": None}
                    if name == "Read":
                        row.update(kind="read", target=inp.get("file_path"))
                    elif name in ("Grep", "Glob"):
                        row.update(kind="search",
                                   target=inp.get("pattern"),
                                   scope=inp.get("path") or inp.get("glob"))
                    elif name == "Bash":
                        k, q = classify_bash(inp.get("command", ""))
                        if k:
                            row.update(kind=k, target=q)
                        else:
                            continue
                    else:
                        continue
                    calls.append(row)
    # attach result + zero-match flag to searches
    for c in calls:
        if c["kind"] == "search":
            res = results.get(c["uid"], "")
            c["empty"] = bool(NO_MATCH.search(res)) or (res.strip() == "")
    return calls


def summarize(calls):
    reads   = [c for c in calls if c["kind"] == "read" and c["target"]]
    searches = [c for c in calls if c["kind"] == "search" and c["target"]]
    read_ct = Counter(canon(c["target"]) for c in reads)
    by_src  = Counter(c["src"] for c in calls)

    print(f"\n{'='*70}\nCONTEXT-FIT FOOTPRINT  —  read-only (Phase 1)\n{'='*70}")
    print(f"tool calls: {len(calls)}  |  reads: {len(reads)} ({len(read_ct)} unique)  "
          f"|  searches: {len(searches)}")
    print(f"sources (agents) captured: {len(by_src)}")
    for s, n in by_src.most_common():
        print(f"    {s:<28} {n} calls")

    print(f"\n--- RE-READ FILES (>=2 reads = reconstruction signal -> Right/Fast) ---")
    rr = [(f, n) for f, n in read_ct.most_common() if n >= 2]
    if rr:
        for f, n in rr[:20]:
            print(f"  {n:>3}x  {trunc(f)}")
    else:
        print("  (none re-read)")

    print(f"\n--- SEARCHES (* = zero result -> Miss signal) ---")
    if searches:
        seen = set()
        for c in searches:
            key = (c["target"], c["src"])
            if key in seen:
                continue
            seen.add(key)
            flag = " *" if c.get("empty") else "  "
            print(f" {flag} [{c['src']}] {str(c['target'])[:90]}")
    else:
        print("  (no searches captured)")

    print(f"\n--- CO-READ CLUSTERS (files read near each other -> index-split hints) ---")
    for pair, n in coread(reads).most_common(10):
        if n >= 2:
            print(f"  {n:>2}x  {trunc(pair[0], 32)}  +  {trunc(pair[1], 32)}")
    print()


def coread(reads, window=4):
    pairs = Counter()
    seq = [canon(c["target"]) for c in reads]
    for i, a in enumerate(seq):
        for b in seq[i + 1:i + 1 + window]:
            if a != b:
                pairs[tuple(sorted((a, b)))] += 1
    return pairs


# Repo-relative canonicalization — collapses worktree-vs-main duplicates of the
# same file so re-read counts are exact (e.g. both .../worktree-epic-28/src/x and
# .../main-checkout/src/x -> src/x).
ANCHORS = {"src", "docs", "components", "lib", "app", "apps", "backend", "frontend",
           "research", "ui", "test", "tests", "__tests__", "packages", "agent", "pages",
           "api", "server", "scripts", "public", "styles", "hooks", "store", "stores",
           "services", "utils", "types", "feature", "features", "config", "modules",
           "internal", "core", "shared", "db", "migrations", "routes", "views", "models"}


def canon(p):
    parts = str(p).split("/")
    for i, seg in enumerate(parts):
        if seg in ANCHORS:
            return "/".join(parts[i:])
    return parts[-1] if parts else str(p)


def trunc(s, n=72):
    s = str(s)
    return s if len(s) <= n else "…" + s[-(n - 1):]


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--project-dir", help="exact ~/.claude/projects/<name>")
    g.add_argument("--cwd", help="match sessions whose cwd is under this path")
    g.add_argument("--session", help="a single session uuid")
    ap.add_argument("--branch")
    ap.add_argument("--since", help="ISO timestamp lower bound (e.g. 2026-06-09T00:00:00Z)")
    ap.add_argument("--json", help="write full footprint JSON here")
    args = ap.parse_args()

    files = session_files(args)
    if not files:
        sys.exit("no transcripts matched")
    print(f"swept {len(files)} transcript file(s):")
    for label, path in files:
        print(f"    {label:<28} {os.path.getsize(path)//1024} KB")
    calls = extract(files, branch=args.branch, since=args.since)
    summarize(calls)
    if args.json:
        with open(args.json, "w") as fh:
            json.dump(calls, fh, indent=2)
        print(f"full footprint -> {args.json}  ({len(calls)} rows)")


if __name__ == "__main__":
    main()
