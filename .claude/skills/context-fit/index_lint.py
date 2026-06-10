#!/usr/bin/env python3
"""
index_lint — Phase 3. Maintainer-only.

Checks that every PRIMARY entry in a scoped index falls under that index's
declared `Scope` glob. A primary entry outside Scope is the detectable form of
"the agent put the file in the wrong index" — see context-fit-design.md §4 and
Indexing_Guide.md. Cross-references (marked) are exempt.

Usage:
  python3 index_lint.py <project>/docs/context/indexes
Exit code 1 if any issue is found (suitable as a gate check).
"""
import argparse, fnmatch, glob, os, re, sys


def parse_index(path):
    scopes, root, entries, crossrefs = [], None, [], set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = re.search(r"<!--\s*Scope:\s*(.+?)\s*-->", line)
            if m:
                scopes += [s.strip() for s in m.group(1).split(",") if s.strip()]
            r = re.search(r"<!--\s*Root:\s*(.+?)\s*-->", line)
            if r:
                root = r.group(1).strip().rstrip("/")
            cr = re.search(r"<!--\s*cross-ref:\s*`?([^\s`]+)", line)
            if cr:
                crossrefs.add(cr.group(1))
            # primary entry = the FIRST-column backtick path of a table row (NOT a
            # comment, NOT the inline-code gotchas in later columns like
            # `request.signal`). Require path shape: contains '/' and ends in .ext.
            if line.lstrip().startswith("|") and "<!--" not in line:
                cells = line.split("|")
                m = re.search(r"`([^`]+)`", cells[1]) if len(cells) > 1 else None
                if m and "/" in m.group(1) and re.search(r"\.[A-Za-z0-9]+$", m.group(1)):
                    entries.append(m.group(1))
    return scopes, root, entries, crossrefs


def under_scope(entry, scopes, root):
    # fnmatch '*' spans '/', so 'a/**/research/**' correctly requires '/research/'
    # in the path — a prefix-only check would wrongly pass any file under 'a/'.
    full = f"{root}/{entry}" if root else entry
    for sc in scopes:
        if "*" in sc:
            if fnmatch.fnmatch(full, sc):
                return True
        elif full == sc or full.startswith(sc.rstrip("/") + "/"):
            return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("indexes_dir")
    args = ap.parse_args()
    files = sorted(glob.glob(os.path.join(args.indexes_dir, "*.md")))
    if not files:
        sys.exit(f"no index files in {args.indexes_dir}")

    issues = 0
    for f in files:
        scopes, root, entries, crossrefs = parse_index(f)
        name = os.path.basename(f)
        if not scopes:
            print(f"  ⚠ {name}: no <!-- Scope: … --> declared")
            issues += 1
            continue
        for e in entries:
            if e in crossrefs:
                continue
            if not under_scope(e, scopes, root):
                print(f"  ✗ {name}: '{e}' is outside Scope {scopes}"
                      f"{f' (Root {root})' if root else ''} — mis-filed, or mark it `cross-ref`")
                issues += 1

    print("  ✓ all primary entries match their index Scope" if issues == 0
          else f"\n  {issues} issue(s) found")
    sys.exit(1 if issues else 0)


if __name__ == "__main__":
    main()
