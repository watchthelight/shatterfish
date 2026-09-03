# Upstream

Shatterfish is a permanent downstream repository of Shattered Pixel Dungeon.
Upstream does not accept pull requests, so nothing here is ever proposed
upstream, and no Shatterfish issue is ever filed against upstream.

## Pinned release

| | |
|---|---|
| Repository | <https://github.com/00-Evan/shattered-pixel-dungeon> |
| Remote name | `upstream` |
| Tag | `v3.3.8` |
| Commit | `7b8b845a76fe76c6b7c031ae9e570852411f56db` |
| Tag date | 2026-03-19 |
| Pinned on | 2026-09-03 |
| `main` origin | `git checkout -b main v3.3.8` (full upstream history retained) |

Note: at pin time upstream also carried a lightweight tag `4.0.0-beta` pointing
at the same commit. It is a pre-release name and was not chosen.

## Rules

1. **Prefer new modules over edits.** Shatterfish code lives in `api`, `harness`,
   `codex`, `brain`, `rig`, `overlay`. Upstream modules (`SPD-classes`, `core`,
   `desktop`, `android`, `ios`, `services`) are edited only through hooks.
2. **A hook is** a minimal, justified edit to an upstream file. Every hook:
   - is as small as it can be and guarded so upstream behaviour is unchanged when
     Shatterfish is not active;
   - carries the GitHub label `touches-upstream` on its PR;
   - is listed in the hooks table below in the same PR;
   - is re-verified on every upstream upgrade.
3. **Never merge `upstream/master`.** Only release tags.
4. Upstream's `LICENSE.txt` is kept unchanged. See `NOTICE.md`.

## Hooks

| # | File(s) | Why | Guard | Introduced | Verified at tag |
|---|---|---|---|---|---|
| _none yet_ | | | | | |

Hook #1 is expected in Session 2: a guarded `include` in `settings.gradle` so
the build does not require the Android SDK.

## Upgrade procedure

Run only after the human has approved the target tag. The `upstream-sync`
project skill (Session 4) automates these steps.

1. `git fetch upstream --tags`; pick the newest stable tag (no `beta`/`rc`).
2. Branch: `git checkout -b upgrade/<newtag> main`.
3. `git merge <newtag>`. Resolve conflicts file by file; for every conflict in a
   hooked file, re-apply the hook minimally and update its row above.
4. Re-verify every hook in the table, even those that merged cleanly, by reading
   the merged file.
5. `./gradlew build` with no Android SDK.
6. Regenerate the Codex (`./gradlew :codex:generate`, once it exists) and commit
   the diff under `codex/<newtag>/`; publish a summary of what changed in
   mechanics tables.
7. Rerun fairness, differential, toggle, ArchUnit, and determinism tests.
8. Rerun the rig baseline and publish `docs/results/<date>-<sha>.md`.
9. Update this file (pinned table, hooks' "Verified at tag" column), `README.md`,
   and `docs/codebase-map.md` for anything the new tag contradicts.
10. PR with label `touches-upstream`; merge only when CI is green.
