---
name: upstream-sync
description: Upgrade Shatterfish to a newer Shattered Pixel Dungeon release tag by the documented procedure (merge the tag, re-verify every hook, regenerate the Codex, rerun fairness and determinism tests, publish the diff). Use when the user says "upgrade upstream", "merge the new tag", "sync with upstream", or invokes /upstream-sync.
---

# upstream-sync

Shatterfish is a permanent downstream repository pinned to a release tag. Upgrades happen only by
merging a newer upstream **tag** through this procedure, never `upstream/master`. The procedure
is the one in `docs/UPSTREAM.md`; this skill executes it with the required checkpoints.

## Checkpoint: the human approves the tag

1. `git fetch upstream --tags`; list tags newest first; pick the newest stable tag (no `beta`,
   `rc`, or similar suffix). Show the current pin from `docs/UPSTREAM.md`, the candidate, its
   commit and date, and `git log --oneline <current>..<candidate> | wc -l`.
2. **Stop and ask** the human to approve the target tag. Merging an upstream tag is on the
   ask-first list in CLAUDE.md. Do nothing further until they answer.

## Procedure (after approval)

3. `git checkout main && git pull --ff-only && git checkout -b upgrade/<tag>`.
4. `git merge <tag>`. For every conflict:
   - in a **hooked file** (rows of the hooks table in `docs/UPSTREAM.md`): re-apply the hook
     minimally, then update its row (guard, "Verified at tag");
   - in `README.md` or `.gitignore`: take ours, then re-check upstream's version for new
     entries worth carrying;
   - anywhere else: it is an upstream file we should not have touched; investigate why it
     differs before resolving.
5. **Re-verify every hook**, including the ones that merged cleanly: open each hooked file at
   the merge result and confirm the hook is present, still minimal, and still guarded. Record
   the tag in the "Verified at tag" column.
6. `./gradlew build` with no Android SDK. Fix only what the upgrade broke; anything else is a
   separate story.
7. `/codex` to regenerate `codex/<newtag>/`; keep the previous tag's folder until the PR is
   merged so the diff between tags is reviewable; summarize the mechanics that changed.
8. `./gradlew :harness:test` (fairness, differential, toggle, ArchUnit, determinism). A failure
   here blocks the upgrade until understood; it usually means `Observer` must learn a new
   visibility rule.
9. `/rig` on the standard seed set against the last published baseline; publish
   `docs/results/<date>-<sha>.md` even if nothing changed, so the tag has a baseline.
10. **Docs in the same PR**: `docs/UPSTREAM.md` (pinned table, hooks table), `README.md` (tag),
    `docs/codebase-map.md` for anything the new tag contradicts, every `docs/rules/` page whose
    citations touched changed files (re-read the code, update `path:line`, re-tier), and
    `docs/glossary.md` if upstream renamed something.
11. PR titled `Upgrade upstream to <tag>`, label `touches-upstream`, body listing every hook
    re-verified and every rule re-cited. Merge only when CI is green and the rig numbers are in.
12. After merge: delete `codex/<oldtag>/`, update the memory note, `/handoff`.

## Never

- Merge `upstream/master` or any non-tag ref.
- File anything at the upstream repository.
- Resolve a hook conflict by taking upstream's side without re-applying the hook.
