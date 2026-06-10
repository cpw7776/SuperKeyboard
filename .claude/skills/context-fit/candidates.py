#!/usr/bin/env python3
"""
context-fit candidate generation + coverage check — Phase 2. Maintainer-only.

Consumes a footprint JSON (from footprint.py --json) and emits candidate
subsystems: directories the agent reconstructed by hand (concentrated re-reads)
that NO existing docs/context file plausibly owns ("HOMELESS").

Clustering is **stack-agnostic**: files are grouped by their directory RELATIVE
to the project root (passed via --project-root, else inferred), not by a
hardcoded web anchor list — so Swift `Sources/`, Go `cmd/`, Rust `crates/` etc.
cluster correctly, not collapse to per-file basenames.

It also **self-diagnoses**: if clustering looks degenerate (most reads outside
the root, or zero multi-file subsystems), it emits a ⚠ DIAGNOSTIC so a 0-card
result can never masquerade as "clean" (see context-fit-design.md §5a). This is
the MECHANICAL half; the judgment half is context-fit-analysis.md.

Usage:
  python3 candidates.py --footprint fp.json --project-root /proj \
      [--context-dir /proj/docs/context] [--min-rereads 2] [--top 12] [--json OUT]
"""
import argparse, json, os, re, sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from footprint import canon  # fallback for paths outside the project root

# generic segments — never a coverage keyword (clustering no longer depends on these)
GENERIC = {"src", "lib", "app", "apps", "components", "component", "agent", "agents",
           "ui", "pages", "api", "server", "backend", "frontend", "test", "tests",
           "__tests__", "core", "internal", "shared", "utils", "util", "modules",
           "services", "feature", "features", "docs", "public", "scripts", "config",
           "types", "hooks", "store", "stores", "views", "routes", "models", "db",
           "styles", "packages", "dist", "build", "epic", "wave", "main", "sources"}


def detect_root(reads, given):
    if given:
        return os.path.abspath(os.path.expanduser(given)).rstrip("/")
    dirs = [os.path.dirname(c["target"]) for c in reads if str(c.get("target", "")).startswith("/")]
    try:
        cp = os.path.commonpath(dirs) if dirs else None
    except ValueError:
        cp = None
    return cp if cp and cp.count("/") >= 3 else None


def rel_or_canon(target, root):
    """Path relative to the project root (stack-agnostic). Returns (key, under_root)."""
    t = str(target)
    if root and t.startswith(root + "/"):
        return t[len(root) + 1:], True
    return canon(t), False


def subsystem_key(path):
    parts = path.split("/")
    return "/".join(parts[:-1]) if len(parts) > 1 else path


def distinctive(key):
    out = []
    for t in re.split(r"[/_\-.]", key):
        tl = t.lower()
        if tl in GENERIC or re.match(r"^[\d.]+$", tl) or len(tl) < 4:
            continue
        out.append(tl)
    return out


def score(d, min_rr):
    rr = [(f, n) for f, n in d["files"].items() if n >= min_rr]
    return sum(n for _, n in rr), len(rr)


def coverage(key, ctx_lower):
    for kw in distinctive(key):
        for fl in ctx_lower:
            if kw in fl:
                return "COVERED", kw
    return "HOMELESS", None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--footprint", required=True)
    ap.add_argument("--project-root", help="cluster paths relative to this (stack-agnostic)")
    ap.add_argument("--context-dir", help="project docs/context dir for the homelessness check")
    ap.add_argument("--min-rereads", type=int, default=2)
    ap.add_argument("--top", type=int, default=12)
    ap.add_argument("--json")
    args = ap.parse_args()

    calls = json.load(open(args.footprint))
    reads = [c for c in calls if c.get("kind") == "read" and c.get("target")]
    root = detect_root(reads, args.project_root)

    in_root = out_root = 0
    file_ct = Counter()
    for c in reads:
        key, under = rel_or_canon(c["target"], root)
        in_root += under
        out_root += (not under)
        file_ct[key] += 1

    subs = defaultdict(lambda: {"files": Counter()})
    for f, n in file_ct.items():
        subs[subsystem_key(f)]["files"][os.path.basename(f)] = n

    ctx_files = []
    if args.context_dir and os.path.isdir(args.context_dir):
        ctx_files = sorted(f for f in os.listdir(args.context_dir) if f.endswith(".md"))
    ctx_lower = [f.lower() for f in ctx_files]

    ranked = sorted(subs.items(), key=lambda kv: score(kv[1], args.min_rereads), reverse=True)

    print(f"\n{'='*72}\nCONTEXT-FIT CANDIDATES (Phase 2)\n{'='*72}")
    print(f"footprint: {os.path.basename(args.footprint)}  |  reads: {len(reads)}  "
          f"|  existing context files: {len(ctx_files)}")
    print(f"root: {root or '(none — basename fallback)'}")
    print(f"\n  {'subsystem':<44} {'reread':>8}  coverage")
    out = []
    for key, d in ranked[: args.top]:
        if key.split("/", 1)[0] == "docs":
            continue  # documentation dirs aren't code subsystems
        s_reads, s_files = score(d, args.min_rereads)
        if s_files < 1:
            continue
        cov, kw = coverage(key, ctx_lower)
        kws = distinctive(key)
        out.append({
            "subsystem": key, "scope": key + "/**",
            "reread_total": s_reads, "reread_files": s_files,
            "coverage": cov, "coverage_keyword": kw, "keywords": kws,
            "top_files": d["files"].most_common(6),
            "suggested_doc": (kws[0].capitalize() + "_Context.md") if kws else None,
            "suggested_index": (f"indexes/{kws[0]}.md") if kws else None,
        })
        tag = "HOMELESS" if cov == "HOMELESS" else f"covered (~{kw})"
        print(f"  {key[:44]:<44} {s_reads:>4}/{s_files:<2}  {tag}")

    print(f"\n--- HOMELESS candidates  →  Phase-2 proposal inputs ---")
    homeless = [r for r in out if r["coverage"] == "HOMELESS"]
    if not homeless:
        print("  (none — existing context covers the reconstructed subsystems)")
    for r in homeless:
        ev = " · ".join(f"{f} {n}x" for f, n in r["top_files"][:5])
        print(f"\n  ▸ {r['subsystem']}/**   (re-read {r['reread_total']} across {r['reread_files']} files)")
        print(f"     evidence:  {ev}")
        print(f"     suggest:   docs/context/{r['suggested_doc']}   +   docs/context/{r['suggested_index']}")

    # --- #2 self-diagnostic: a clean run must be distinguishable from a failed one ---
    multi = sum(1 for r in out if r["reread_files"] >= 2)
    fb_pct = round(100 * out_root / max(1, len(reads)))
    diagnostic = None
    if len(reads) >= 40 and (multi == 0 or fb_pct >= 40):
        diagnostic = (f"INCONCLUSIVE — clustering likely FAILED: {fb_pct}% of reads fell outside the "
                      f"project root and {multi} multi-file subsystems formed. A 0-card result here is "
                      f"NOT 'clean' — suspect an unrecognized layout or a wrong/missing --project-root.")
        print(f"\n  ⚠⚠ DIAGNOSTIC: {diagnostic}")
    else:
        print(f"\n  ✓ clustering healthy  ({fb_pct}% out-of-root, {multi} multi-file subsystems)")

    if args.json:
        json.dump({"root": root, "reads": len(reads), "out_of_root_pct": fb_pct,
                   "multi_file": multi, "diagnostic": diagnostic, "candidates": out},
                  open(args.json, "w"), indent=2)
        print(f"\ncandidates -> {args.json}  ({len(out)} subsystems, {len(homeless)} homeless"
              + (", DIAGNOSTIC SET" if diagnostic else "") + ")")


if __name__ == "__main__":
    main()
