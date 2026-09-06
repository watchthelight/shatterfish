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
| `HooksLedgerTest`: each upstream file's diff digests to exactly what the ledger declares | Any unlisted change to upstream, whatever shape it takes. A public accessor added to an already-hooked file carries no marker and deletes no line; a line swapped for another line inside a hook block changes no count. Every other check here misses both |
| `HooksLedgerTest`: no row id is used twice | Two reasons under one id, which ADR-0008 forbids and which hides a row from the budget |
| `HooksLedgerTest`: nothing looks like a marker without being one | A mistyped marker, which is a comment the id comparison cannot see and a reader takes for a declaration |
| `HooksLedgerTest`: every upstream file that is changed, added, deleted or renamed relative to the pinned tag carries a marker | An edit to upstream that nobody wrote down; a second Shatterfish class added inside an upstream module, next to the game's own privates; and an upstream file deleted or moved, none of which a modified-files-only check can see |
| `HooksVanillaTest`: a hook wraps vanilla code, it never deletes it | A vanilla statement lost because a guard replaced it rather than enclosing it, including when an upgrade merge rewrites the site |
| `HooksVanillaTest`: the guarded sites do nothing when no scene exists | A guard dropped by an upgrade merge, which would otherwise surface much later as a headless crash |
| `HooksVanillaTest`: `add(EmoIcon)` reaches the scene when a scene exists | A guard that changes vanilla behaviour rather than only adding a null case |
| `HooksVanillaTest`: `Hooks.clear()` nulls every point declared in `Hooks` | A listener belonging to a finished Run being reachable from the next one |
| `HooksLedgerTest`: nothing tells git to stop reading upstream files as text | The one edit that disarms every check in this table at once. A `.gitattributes` line marking source binary empties every diff, so the digest, the line counts and the wrap rule all go quiet together — and the file itself is a root-level addition nothing else looks at |
| `HooksLedgerTest`: nothing under an upstream module is hidden from git by a rule of ours | The same move through `.gitignore`: an ignored file is in neither the diff nor the untracked listing. Upstream's own ignore rules at the pinned tag are still honoured |

Both classes compare the tree against the pin, and they resolve it by **commit** rather than by tag
name. The tag lives in upstream's repository: this fork carries it only if someone pushes it, and
continuous integration clones the fork, so a tag-based check passes on a developer's machine and
fails in CI for a reason that has nothing to do with the code. The commit needs no such arrangement,
because `main` descends from it and any checkout with full history has it — and it is the stricter
of the two, since a tag can be moved and this is a pin. Continuous integration checks out with
`fetch-depth: 0`; a shallow clone fails with a message saying so rather than skipping.

## Hooks

| # | File(s) | Why | Guard | Introduced | Verified at tag |
|---|---|---|---|---|---|
| 1 | `settings.gradle` | Desktop and headless builds must never need the Android SDK or Xcode; Shatterfish modules need including | Marked `// shatterfish-hook:1`. The two mobile `include` lines are replaced by one `apply from: 'shatterfish/settings.gradle'`; that file includes `android`/`ios` only with `-Pshatterfish.mobile=on` (default `off`) and includes the six Shatterfish modules. This is the one hook that moves a vanilla line rather than wrapping it, so `HooksVanillaTest` names `shatterfish/settings.gradle` as its relocation target and checks both lines are still there. | 2026-09-03, E0 S2 (#1) | v3.3.8 |
| 2 | `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java` (new file) | The registry itself, row 2 of [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md). Upstream code that has to call Shatterfish calls a nullable listener field here instead of importing a Shatterfish module, so the dependency edges of [ADR-0003](adr/0003-module-layout.md) are never reversed: `harness` and `overlay` depend on `core`, never the other way round. This is the only Shatterfish-authored source file outside `shatterfish/`. Adding a listener point later edits this file only and consumes no new row; adding a *site* that calls one belongs to that site's row | The file is new, so vanilla behaviour cannot change by its presence, and `HooksLedgerTest` asserts hook id 2 appears in this file and nowhere else, which is what stops a hook being hidden inside the registry as one marker instead of many. With nothing registered every point is null and every site takes the vanilla branch; `HooksVanillaTest` asserts that, and that `Hooks.clear()` nulls every point declared, including ones added after it was written | 2026-09-04, E1 story 1.2 ([#15](https://github.com/watchthelight/shatterfish/issues/15)) | v3.3.8 |
| 4 | `core/.../sprites/CharSprite.java` (1 site: the emote accessor) | ADR-0006's mob-state row: the only AI state the Observation carries is the emote the sprite shows, and `CharSprite.emo` is protected with no getter (`CharSprite.java:116`), written only by the show and hide methods (`:655-737`). Reading it needs an accessor; the alternatives are reflection, which `HarnessReflectionTest` confines to the stepper, and a class of ours in the sprite's package, which `HarnessPackageAnchorTest` forbids | One public method, `shatterfishEmote()`, returning the field under the lock the show and hide methods take; it reads and never writes, so vanilla behaviour cannot change by its presence, and with nothing calling it the method is dead code. Ten added lines, a marker, three comment lines, the method and a blank line; nothing wrapped, nothing removed, which `HooksVanillaTest` holds by the rule that a hook encloses vanilla code and never deletes it. `ActorLeakTest` reads it through the Observer and holds that a mob's state, target and seen flag change nothing the accessor shows | 2026-09-06, E1 story 1.9 ([#22](https://github.com/watchthelight/shatterfish/issues/22)) | v3.3.8 |
| 5 | `core/.../scenes/GameScene.java` (3 sites: `selectCell`, `resetKeyHold`, `add(EmoIcon)`), `core/.../actors/hero/Hero.java` (1 site: `act()`) | Row 5 is "let the actor loop run with no `GameScene`" ([ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md), superseding ADR-0008's table). The actor thread reaches all three on an ordinary turn: `Hero.ready()` (`Hero.java:945`) reaches `selectCell` by way of `GameScene.ready()` (`GameScene.java:1643`), `Hero.interrupt()` calls `resetKeyHold`, and a sleeping mob's sprite builds an `EmoIcon` on update. None can be avoided by constructing the object the way `TargetHealthIndicator` and `AttackIndicator` were: `cellSelector` is `private static` (`GameScene.java:178`) and assigned only inside `create()` (`:368`), and `scene` is package-private (`:159`), assigned at `:242` and set back to null in `destroy()` (`:779`) | A null check on the static each site already uses. Vanilla is unaffected: `cellSelector` is never set back to null once assigned, and `scene` is null only between `destroy()` and `create()`, when no sprite is updating. The random stream is unchanged because `EmoIcon.Sleep` draws after the guarded call returns. Tested by `HooksVanillaTest`. The guard firing (the statics null, the headless case) is exercised at runtime for all three sites: without these guards `selectCell` dereferences `cellSelector` and then `Dungeon.hero` on every Input wait. The guard not firing (the vanilla case) is exercised for `add(EmoIcon)`, whose state is a `GameScene` instance — which constructs headlessly — and its `emoicons` group (`GameScene.java:196`, assigned at `:305`). The other two need a `CellSelector`, and every `DungeonTilemap` builds a `TextureFilm` from a texture in the abstract constructor (`DungeonTilemap.java:40-41`, `TextureFilm.java:53-55`), so any subclass needs a graphics binding and the game's assets on the classpath; `CellSelector` then reads `map.camera()` (`CellSelector.java:55-56`), which needs `Camera.main`. All five concrete subclasses name `Dungeon.level`; four of them require it, and the fifth guards with `if (Dungeon.level != null)` (`TerrainFeaturesTilemap.java:49`). Reaching those two sites therefore means the booted headless application of ADR-0015. Story 1.3 built it, and `HeadlessSceneTest` exercises both vanilla branches against the real scene constructed headlessly: `selectCell` installs the listener on the real `CellSelector` and asks it for its prompt, and `resetKeyHold` clears the selector's held action. Beyond the runtime checks the property is held against the pinned tag by the rule that a hook encloses vanilla code and never deletes it, which covers hooks not yet written. The fourth site is the Input-wait notification of ADR-0015: the first statement of the branch of `Hero.act()` that runs when the hero begins an act unready (`Hero.java:840` at the tag) reads `Hooks.inputWait` once into a local and calls it when set. Nine added lines, a marker, five comment lines, two statements and an import; nothing wrapped, nothing removed; with nothing registered the branch is vanilla, which every scene test that runs without the driver exercises (`SceneDrawParityTest` counts its draws). The branch runs once before every transition to ready and also on each step of a move and each turn of resting, so the listener is a notification to confirm, not a wait; `InputWaitCountTest` holds that sixty wake-ups of a parked hero notify nothing, a move of several steps notifies once per step, and an interruption is a wait of its own | 2026-09-04, E1 story 1.1 ([#14](https://github.com/watchthelight/shatterfish/issues/14)); the `Hero.act()` site 2026-09-05, E1 story 1.5 ([#18](https://github.com/watchthelight/shatterfish/issues/18)) | v3.3.8 |

Hook ids are assigned by the ledger in [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md), not sequentially, so gaps in this table are rows that have not landed yet rather than rows that are missing.

`README.md` and `.gitignore` are also modified (rewritten README; appended ignore
entries). They are documentation, not build hooks, and are re-applied on upgrade
by taking "ours" in the merge. `HooksLedgerTest` exempts exactly these two paths from
both the marker requirement and the diff budget. Two things are outside its reach by
construction rather than by exemption, and are recorded here so nobody mistakes silence
for a check: a new file at the repository root, which cannot be an upstream file because
upstream had no such file; and anything git is configured not to report.

A third thing is outside the ledger's reach because it is not an edit at all: Shatterfish code
reaching a private upstream member by reflection. Harness main code does this for two fields,
both from `shatterfish/harness/src/main/java/org/shatterfish/harness/scene/SceneStepper.java`. It
writes `GameScene.actorThread` once per scene, starting the actor thread itself before the first
frame where the scene would start it in the middle of the first `update()`
(`GameScene.java:866-882` at the tag); that reorders the hero's first turn ahead of the first
frame and changes nothing the game computes, which is why it is recorded here rather than as a
row. It reads `Actor.current` and never writes it, to name the actor a stalled Run is waiting on
in the driver's diagnostic and in the stepper's own failure messages (story 1.4); no Run outcome
depends on it. Each fails immediately and by name if an upgrade moves its field, and row 4
(read-only accessors) is where they move if that happens. `HarnessReflectionTest` confines
reflection in harness main code to that one class, by dependency rather than by call so that a
method reference or a reflective call to `getDeclaredField` itself does not slip past, and asserts
that the fields it reaches are exactly the two named here, so a third cannot arrive unannounced.
Tests are not confined: the ledger's own tests, the scene fixtures, the row 5 checks and the
driver's test reach `Random.generators`, `Badges.global`, `Journal.loaded`, `GameScene.scene`,
`GameScene.emoicons`, `GameScene.cellSelector`, `CellSelector.heldAction1`, `Actor.current` and
`Group.members` to set up or observe what they test. A fourth thing is outside the ledger's reach
for the same reason and needs no reflection at all: a class of ours declared in one of upstream's
packages, which would share that package's private members. `HarnessPackageAnchorTest` keeps every
class file compiled into or shipped with `harness` under `org.shatterfish.harness`, walking the
resources output as well as the classes and refusing a class file in the source tree, as
`BrainPackageAnchorTest` does for the brain. Reflection into upstream from any other module is not covered by anything here and
would need its own rule.

### The site index

The table above abbreviates paths so a person can read it. The same information for a machine is
below: one line per row per file, as `<id> <markers> <path>`. `HooksLedgerTest` asserts equality
with the tree, which is what catches a new site added under an id that already exists — a change
that alters no id set and would otherwise be invisible.

<!-- site-index -->

```text
1  1  settings.gradle
2  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java
4  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/CharSprite.java
5  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java
5  3  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java
```

### The diff budget

Every other check here keys off something a change announces about itself: a marker, a deleted line,
a new file. So none of them sees a change that announces nothing. Two were demonstrated against
earlier drafts of this document. A `public static Object peekEverything()` returning `Dungeon.level`
was added to `GameScene`, which already carries a row — no marker, no deleted line, and a public
accessor to every hidden mob and secret door in the file most likely to be attacked. And a comment
line inside the `selectCell` hook block was replaced by `Dungeon.hero.viewDistance = 999`, which
makes the hero see the whole level and leaves the added and removed line counts exactly as they
were.

So the ledger declares what each upstream file's difference from the pinned tag *is*, not how large
it is: a digest of the changed lines, with the counts alongside for a reader. `HooksLedgerTest`
recomputes both. Any change to any line of any upstream file, comment or code, is a change to the
block below.

Files added under an upstream module are here too, the registry included. For an added file the
difference from the tag is its whole content, which is the right thing to digest, and leaving it out
would leave the one file inside `core` that upstream code is meant to call into governed by nothing
but its marker.

The digest is the first eight bytes of the SHA-256 of the changed lines with hunk headers dropped
and carriage returns stripped, so it depends on content and not on where the lines sit or how the
checkout stores line endings. To regenerate after a real hook, run the check and read the expected
value out of the failure.

<!-- diff-budget -->

```text
0c77a8387dc8790f   9  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java
0cc6e6af39752ff6  18  2  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java
2db03cb3ecac7191  98  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java
b5c38c7c41565c37  10  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/CharSprite.java
02a9a5d3158a7ab2   5  2  settings.gradle
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
