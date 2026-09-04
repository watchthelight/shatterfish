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

## What enforces this

The table below is a promise; these tests are what make it true. All of them run in
`./gradlew :harness:test` and read the tree and the pinned tag, never runtime state.

| Check | What it catches |
|---|---|
| `HooksLedgerTest`: the hook ids marked in the upstream tree equal the rows here | A hook that was added without a row, and a row left behind after its hook was removed |
| `HooksLedgerTest`: at most ten rows | The budget being raised by an edit instead of by an ADR |
| `HooksLedgerTest`: hook id 2 appears in `Hooks.java` and nowhere else | A hook hidden inside the registry, where many sites would count as one marker |
| `HooksLedgerTest`: the site index equals the markers in the tree | A new site added under an id that already has a row, which changes no id set. `GameScene` is where an Observer-adjacent leak would be added, and it already carries row 5 |
| `HooksLedgerTest`: no row id is used twice | Two reasons under one id, which ADR-0008 forbids and which hides a row from the budget |
| `HooksLedgerTest`: nothing looks like a marker without being one | A mistyped marker, which is a comment the id comparison cannot see and a reader takes for a declaration |
| `HooksLedgerTest`: every upstream file that is changed, added, deleted or renamed relative to the pinned tag carries a marker | An edit to upstream that nobody wrote down; a second Shatterfish class added inside an upstream module, next to the game's own privates; and an upstream file deleted or moved, none of which a modified-files-only check can see |
| `HooksVanillaTest`: a hook wraps vanilla code, it never deletes it | A vanilla statement lost because a guard replaced it rather than enclosing it, including when an upgrade merge rewrites the site |
| `HooksVanillaTest`: the guarded sites do nothing when no scene exists | A guard dropped by an upgrade merge, which would otherwise surface much later as a headless crash |
| `HooksVanillaTest`: `add(EmoIcon)` reaches the scene when a scene exists | A guard that changes vanilla behaviour rather than only adding a null case |
| `HooksVanillaTest`: `Hooks.clear()` nulls every point declared in `Hooks` | A listener belonging to a finished Run being reachable from the next one |

Both classes compare the tree against the pinned tag, so they need the tag present: continuous
integration checks out with full history, and they fail with a message saying so rather than
skipping when it is missing.

## Hooks

| # | File(s) | Why | Guard | Introduced | Verified at tag |
|---|---|---|---|---|---|
| 1 | `settings.gradle` | Desktop and headless builds must never need the Android SDK or Xcode; Shatterfish modules need including | Marked `// shatterfish-hook:1`. The two mobile `include` lines are replaced by one `apply from: 'shatterfish/settings.gradle'`; that file includes `android`/`ios` only with `-Pshatterfish.mobile=on` (default `off`) and includes the six Shatterfish modules. This is the one hook that moves a vanilla line rather than wrapping it, so `HooksVanillaTest` names `shatterfish/settings.gradle` as its relocation target and checks both lines are still there. | 2026-09-03, E0 S2 (#1) | v3.3.8 |
| 2 | `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java` (new file) | The registry itself, row 2 of [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md). Upstream code that has to call Shatterfish calls a nullable listener field here instead of importing a Shatterfish module, so the dependency edges of [ADR-0003](adr/0003-module-layout.md) are never reversed: `harness` and `overlay` depend on `core`, never the other way round. This is the only Shatterfish-authored source file outside `shatterfish/`. Adding a listener point later edits this file only and consumes no new row; adding a *site* that calls one belongs to that site's row | The file is new, so vanilla behaviour cannot change by its presence, and `HooksLedgerTest` asserts hook id 2 appears in this file and nowhere else, which is what stops a hook being hidden inside the registry as one marker instead of many. With nothing registered every point is null and every site takes the vanilla branch; `HooksVanillaTest` asserts that, and that `Hooks.clear()` nulls every point declared, including ones added after it was written | 2026-09-04, E1 story 1.2 ([#15](https://github.com/watchthelight/shatterfish/issues/15)) | v3.3.8 |
| 5 | `core/.../scenes/GameScene.java` (3 sites: `selectCell`, `resetKeyHold`, `add(EmoIcon)`) | Row 5 is "let the actor loop run with no `GameScene`" ([ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md), superseding ADR-0008's table). The actor thread reaches all three on an ordinary turn: `Hero.ready()` calls `selectCell`, `Hero.interrupt()` calls `resetKeyHold`, and a sleeping mob's sprite builds an `EmoIcon` on update. None can be avoided by constructing the object the way `TargetHealthIndicator` and `AttackIndicator` were: `cellSelector` is `private static` (`GameScene.java:178`) and assigned only inside `create()` (`:368`), and `scene` is package-private (`:159`), assigned at `:242` and set back to null in `destroy()` (`:779`) | A null check on the static each site already uses. Vanilla is unaffected: `cellSelector` is never set back to null once assigned, and `scene` is null only between `destroy()` and `create()`, when no sprite is updating. The random stream is unchanged because `EmoIcon.Sleep` draws after the guarded call returns. Tested by `HooksVanillaTest`. The guard firing (the statics null, the headless case) is exercised at runtime for all three sites: without these guards `selectCell` dereferences `cellSelector` and then `Dungeon.hero` on every Input wait. The guard not firing (the vanilla case) is exercised for `add(EmoIcon)`, whose state is a `GameScene` instance — which constructs headlessly — and its `emoicons` group (`GameScene.java:196`, assigned at `:305`). The other two need a `CellSelector`, whose constructor dereferences a `DungeonTilemap` (`CellSelector.java:55-56`) and whose only concrete tilemap reads `Dungeon.level` and a texture, so reaching them means booting a graphics binding and generating a level: that is story 1.3's driver, and story 1.3 owns those two. Beyond the runtime checks the property is held against the pinned tag by the rule that a hook encloses vanilla code and never deletes it, which covers hooks not yet written, once a harness-owned scene makes the statics non-null | 2026-09-04, E1 story 1.1 ([#14](https://github.com/watchthelight/shatterfish/issues/14)) | v3.3.8 |

Hook ids are assigned by the ledger in [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md), not sequentially, so gaps in this table are rows that have not landed yet rather than rows that are missing.

`README.md` and `.gitignore` are also modified (rewritten README; appended ignore
entries). They are documentation, not build hooks, and are re-applied on upgrade
by taking "ours" in the merge. They are the only two files `HooksLedgerTest` allows
to differ from the pinned tag without carrying a marker; every other upstream file
that differs must be a row above.

### The site index

The table above abbreviates paths so a person can read it. The same information for a machine is
below: one line per row per file, as `<id> <markers> <path>`. `HooksLedgerTest` asserts equality
with the tree, which is what catches a new site added under an id that already exists — a change
that alters no id set and would otherwise be invisible.

<!-- site-index -->

```text
1  1  settings.gradle
2  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java
5  3  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java
```

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
