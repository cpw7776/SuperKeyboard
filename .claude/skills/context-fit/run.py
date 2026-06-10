#!/usr/bin/env python3
"""
run — Context-Fit one-shot runner (read-only). Maintainer-only.

Point it at a PROJECT and it does the rest: finds the project's docs/context,
sweeps EVERY recent session's transcript (main + sub-agents + cmux panes),
clusters the reconstructed subsystems, and flags the homeless ones. No session
ids, ever.

Usage:
  python3 run.py --project /path/to/project          # the only thing you need
  python3 run.py --session <uuid> --context-dir …     # advanced: a single session
  python3 run.py --cwd /path --since ISO --context-dir …   # advanced: time-scoped
"""
import argparse, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SKIP = {"node_modules", ".git", ".next", "dist", "build", ".venv", "__pycache__", "coverage"}


def find_context_dir(root):
    direct = os.path.join(root, "docs", "context")
    if os.path.isdir(direct):
        return direct
    for d, dirs, _ in os.walk(root):
        dirs[:] = [x for x in dirs if x not in SKIP]
        if os.path.basename(d) == "context" and os.path.basename(os.path.dirname(d)) == "docs":
            return d
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", help="project root — auto-finds docs/context + sweeps all its sessions")
    ap.add_argument("--session")
    ap.add_argument("--cwd")
    ap.add_argument("--branch")
    ap.add_argument("--since")
    ap.add_argument("--context-dir")
    ap.add_argument("--out-dir", default="/tmp/context-fit")
    a = ap.parse_args()

    context_dir = a.context_dir
    if a.project:
        root = os.path.abspath(os.path.expanduser(a.project))
        if not os.path.isdir(root):
            sys.exit(f"no such project dir: {root}")
        a.cwd = a.cwd or root
        if not context_dir:
            context_dir = find_context_dir(root)
            if not context_dir:
                sys.exit(f"couldn't find docs/context under {root} — pass --context-dir")
        print(f"project : {root}\ncontext : {context_dir}")

    if not (a.session or a.cwd):
        sys.exit("give --project (easy) OR --session / --cwd (advanced)")
    if not context_dir:
        sys.exit("need --context-dir (or --project to auto-find it)")

    os.makedirs(a.out_dir, exist_ok=True)
    fp = os.path.join(a.out_dir, "footprint.json")
    cand = os.path.join(a.out_dir, "candidates.json")

    fp_cmd = ["python3", os.path.join(HERE, "footprint.py"), "--json", fp]
    if a.session:
        fp_cmd += ["--session", a.session]
    else:
        fp_cmd += ["--cwd", a.cwd] + (["--branch", a.branch] if a.branch else [])
    if a.since:
        fp_cmd += ["--since", a.since]

    print("\n### 1/2  FOOTPRINT")
    sys.stdout.flush()
    if subprocess.run(fp_cmd).returncode:
        sys.exit("footprint step failed")

    print("\n### 2/2  CANDIDATES")
    sys.stdout.flush()
    cand_cmd = ["python3", os.path.join(HERE, "candidates.py"),
                "--footprint", fp, "--context-dir", context_dir, "--json", cand]
    if a.cwd:  # the project root — stack-agnostic clustering anchors here
        cand_cmd += ["--project-root", a.cwd]
    if subprocess.run(cand_cmd).returncode:
        sys.exit("candidates step failed")

    print(f"""
### NEXT — judgment (read-only, default No)
Follow  {os.path.join(HERE, 'context-fit-analysis.md')}  over:
  footprint  : {fp}
  candidates : {cand}
  context dir: {context_dir}
Gauntlet the HOMELESS candidates, consolidate, route, emit the CONTEXT-FIT GATE. Apply nothing.""")


if __name__ == "__main__":
    main()
