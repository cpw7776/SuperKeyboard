---
name: cmux-orchestrator
description: Run several epics or tasks in parallel as separate Claude Code sessions tiled inside one cmux workspace. Use when the user asks to "orchestrate", run "orchestrator mode", "run these in parallel", "spin up N sessions", or work multiple epics/features at once across cmux panes. Lays out balanced panes, names tabs, launches each task into its own session, and monitors them.
allowed-tools: Bash(cmux *), Bash(cp *), Bash(cmp *), Bash(git worktree *), Bash(ln *), Read, Write
---

# cmux Orchestrator Mode

Use this when the user asks me to **orchestrate** — run several epics/tasks in parallel as separate Claude sessions inside cmux. The low-level cmux operations live in the **cmux skills** (`cmux`, `cmux-workspace`, `cmux-settings`, etc., at `~/.claude/skills/`); this skill is the *recipe* for combining them into orchestrator mode. Look up any specific operation with `cmux <command> --help` or those skills' `references/` rather than guessing.

## 0. Sync & fallback — do this FIRST, every run

This skill is **canonical at the global path** `~/.claude/skills/cmux-orchestrator/SKILL.md`. Claude Code skill precedence is **personal (global) > project**, so the two copies never collide — the global one wins wherever it's installed:

- **If the global skill is installed, it is the copy running right now** (it overrides any project copy). You are canonical. If the current project carries a copy at `./.claude/skills/cmux-orchestrator/SKILL.md` that differs from the global file, refresh the project copy from global so the in-repo copy stays current for other machines:
  ```bash
  cmp -s ~/.claude/skills/cmux-orchestrator/SKILL.md ./.claude/skills/cmux-orchestrator/SKILL.md 2>/dev/null \
    || cp ~/.claude/skills/cmux-orchestrator/SKILL.md ./.claude/skills/cmux-orchestrator/SKILL.md 2>/dev/null
  ```
  If that copied a newer version in, **tell the user the in-repo skill copy changed so they can commit it** — committing is how the latest skill (including learned fixes) travels to other laptops.
- **If the global skill is NOT installed** (e.g. a second laptop without `~/.claude/skills/`), you are running from the project copy — the fallback. It is fully self-contained; proceed normally.

## Hard rules (user preferences — do not deviate)

1. **One workspace, never a new one.** Run all parallel sessions as **tabs/panes inside the user's current workspace**. Do NOT call `new-workspace`/`workspace create` for the work — the user can't find sessions that land in a separate workspace. If a temp workspace ever gets created, `move-surface` its surface back into the caller workspace and `close-workspace` the empty one.
2. **Always balanced, equal-on-screen tiling.** Split panes into symmetric halves so every session gets equal space. Orientation (left/right vs top/bottom) doesn't matter; balance does: 2 → 1+1 side by side; 4 → 2×2; 6 → 3+3 (e.g. three top, three bottom). Use `cmux split-off --surface <surface> <up|down|left|right>` to peel each surface into its own visible pane; aim for even halves.
3. **Name every tab.** `cmux tab-action --tab <surface> --action rename --title "Task · Short"`.

## How to launch a task into a tab (proven flow)

1. If isolation is wanted, create a git worktree + share root deps + a self-contained kickoff (copy the scope into the worktree or inline it in the kickoff file):
   `git worktree add -b feature/<slug> .worktree/feature/<slug> <base>` then `ln -s ../../../node_modules .worktree/feature/<slug>/node_modules` (adjust depth for the repo's package manager / structure).
2. Make a terminal tab in the caller's pane and send the launch command (`new-surface` has no `--cwd`/`--command`, so use `send` + `send-key`):
   ```bash
   OUT=$(cmux new-surface --type terminal --pane <callerPane> --focus false)
   S=$(echo "$OUT" | grep -oE 'surface:[0-9]+' | head -1)
   cmux send --surface "$S" 'cd "<abs-path>" && claude --model opus "Read <KICKOFF>.md and follow it; if this project has a dev/feature lifecycle doc, follow it and STOP at its approval gates."'
   cmux send-key --surface "$S" Enter
   ```
3. Then `split-off` each session's surface into a balanced layout and rename it.

## Verify & monitor

- `cmux identify --json` — caller pane/surface.
- `cmux tree --workspace <ws>` — the layout.
- `cmux top --workspace <ws> --processes --flat` — confirm each tab's session is alive.

The user only answers decision questions in each pane; I coordinate merge order and keep sessions in separate file lanes to avoid conflicts.

## Self-improvement — keep this skill alive

When you hit a cmux orchestration problem and **solve it** (a flag that didn't work, a layout quirk, a surface/race issue, a better launch incantation), capture the durable fix so the next session benefits:

1. Append a concise, **verified** fix to `## Troubleshooting — learned fixes` below. Write it into the **global** file (`~/.claude/skills/cmux-orchestrator/SKILL.md`) if it exists; otherwise into this project copy.
2. **Discipline (same as the kit's lesson-capture):** only durable, generalizable, *confirmed* fixes — one or two lines each. Don't manufacture lessons, don't log one-offs; a clean run adds nothing.
3. **Lifecycle caveat:** Claude reads a skill file once per session, so an edit you make takes effect **next** session, not this one. That's expected — it's a learning doc.
4. If you wrote to the global file and the project carries a copy, refresh the project copy from global (the `cp` in section 0) and tell the user to commit it, so the fix travels to other laptops.

## Troubleshooting — learned fixes

- **An empty surface ref makes `cmux send` fall back to YOUR OWN pane — always guard it.** `cmux new-surface --pane <P>` fails (prints `Error: not_found: Pane not found`, no `surface:N` ref) when `<P>` was closed since you last saw it — e.g. a finished session's pane that the user or cmux cleaned up. If you then run `cmux send --surface "" '<launch cmd>'`, the **empty `--surface` falls back to the focused/caller surface — the orchestrator's own pane** — so the launch command gets typed into and submitted to *your own* session instead of the new one. Always capture the ref and hard-abort if empty before any `send`/`send-key`: `S=$(cmux new-surface ... | grep -oE 'surface:[0-9]+' | head -1); [ -z "$S" ] && { echo ABORT; exit 1; }`. And **re-read `cmux tree` before targeting a pane you created in an earlier step** — don't assume a prior session's pane still exists; create the new surface in a pane you just confirmed (your own caller pane is always safe), then `split-off`.
- **Monitor/drive an orchestrated interactive session with `cmux read-screen --surface <S> [--lines N] [--scrollback]`** — it returns the pane's rendered text so you can detect gates and respond with `send` + `send-key Enter`. Have the launched agent print agreed markers (e.g. a line starting `⏸ ORCHESTRATOR-DECISION:`) so you can poll for them deterministically instead of guessing screen state.
- **Poll-monitoring MUST be idle-aware — `read-screen` includes scrollback, so an already-answered marker keeps re-matching.** Only treat a `⏸ …DECISION:` marker as LIVE when the session is also **idle** — the TUI shows NO `esc to interrupt` and no thinking spinner (`Baked for…`, `Choreographing…`, `Transfiguring…`, `Cogitat…`, `↓ Nk`, `↑ Nk`). When busy, ignore markers (it is mid-task and the marker is stale scrollback). Also fire on a silent stall: idle for ~3 consecutive polls with no marker (unexpected stop/error). Robust background watcher shape: each poll read the last ~45 lines, keep an `idle` counter (reset when a busy-token is present, else `idle++`), and exit on — DONE marker · (`idle>=1` AND DECISION marker) · (`idle>=3` → investigate) · pane-gone · timeout. Run it with `run_in_background` so the harness wakes you when it fires; re-arm after each answered gate.
- **Queue input while the session is busy — it does not corrupt the current step.** Typing `cmux send` + `send-key Enter` into an auto-mode Claude pane mid-generation queues the message for its next turn (it will pick up a standing note before its next relevant action). Use this for standing directives (e.g. "keep CLAUDE.md edits concise") without interrupting the current step.
