# Upstream

Shatterfish is a permanent downstream repository of Shattered Pixel Dungeon.
Upstream does not accept pull requests, so nothing here is ever proposed
upstream, and no Shatterfish issue is ever filed against upstream.

## Pinned release

| | |
|---|---|
| Repository | <https://github.com/00-Evan/shattered-pixel-dungeon> |
| Remote name | `upstream` |
| Tag | `v4.0.2` |
| Commit | `57a4e06a4caf162446d1c28caa7983f0493fecf0` |
| Tag date | 2026-10-06 |
| Pinned on | 2026-10-24 (v4.0.0 from 2026-09-11 to 2026-10-24; v3.3.8 from 2026-09-03 to 2026-09-11) |
| `main` origin | `git checkout -b main v3.3.8` (full upstream history retained); upgraded to `v4.0.0`, then `v4.0.2`, by merge, the documented procedure |

Note: the first pin, `v3.3.8`, was chosen over a lightweight `4.0.0-beta` tag that pointed at the
same commit; `v4.0.0` proper was released on 2026-09-09 and is the pin from 2026-09-11.

## The second pinned source, read only

The Codex's vocabulary diff (FR-16, story 2.8) compares this game against the one it descends
from, because most of the internet's Pixel Dungeon knowledge is about the original and a claim
that is true there may be false here. That comparison needs the original's source to read and to
cite, so the Codex pins a second tree.

| | |
|---|---|
| Repository | <https://github.com/00-Evan/pixel-dungeon-gradle> |
| Tag | `archive` (the only tag the repository carries) |
| Commit | `6fffc0768905b5b1f167a05df7274acc10a7ae34` |
| Licence | GPL-3.0, Pixel Dungeon by Watabou, packaged for gradle by 00-Evan |
| Pinned on | 2026-09-19, story 2.8 |
| Where it lives | `vanilla-src/`, not committed; `tools/fetch-vanilla.sh` puts it there and continuous integration runs the same script |
| How it arrives | `git fetch <url> <tag>`, no named remote: a remote left behind is a second repository for `gh` to target, and would put `git merge` against this tree one command away |
| How it is verified | the script writes the commit it extracted to `vanilla-src/.pinned`, and the generator refuses to read or cite the tree unless that file matches the pin |
| The pin itself | `vanilla.pin` at the repository root, which the script reads and the generator cites |

This tree is **read only** and the rules below do not apply to it. It is never merged, never
built, never on a compile path, and no Shatterfish class imports `com.watabou.pixeldungeon`.

That last sentence is a test rather than an intention: `CodexLeakTest`'s *the other game is read
and nothing else* reads `settings.gradle` and every Shatterfish source file and fails on an import
of that package, on a project under `vanilla-src/`, and on anything outside `codex` and `api`
naming the vocabulary diff. ArchUnit cannot hold this one — it sees compiled classes, and the
whole point is that this tree is never compiled, so its rules would pass whether or not the claim
were true.

`./gradlew build` therefore needs the fetch to have run: the vocabulary diff reads this tree on
every generation, and a clean clone that skips the script fails with a message naming it. An upgrade of the first pin is the procedure in this
document; this pin moves only if the vocabulary diff needs a different tag, which would be its own
story. Nothing about it is a hook, and it spends no row of the hook budget.

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

The table below is a promise; these tests are what make it true. All but the last run in
`./gradlew :harness:test`, and all of them read the tree and the pinned tag, never runtime state.

| Check | What it catches |
|---|---|
| `HooksLedgerTest`: the hook ids marked in the upstream tree equal the rows here | A hook that was added without a row, and a row left behind after its hook was removed |
| `HooksLedgerTest`: at most eleven rows (ten until the owner raised it for row 11 on 2026-09-26, ADR-0016's story 5.9 amendment) | The budget being raised by an edit instead of by an ADR |
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
| `DocsCitationTest` (in `./gradlew :codex:test`): every `path:line` citation in `docs/` resolves at the tag that citation names | A claim about the game still carrying an authority it lost: a cited file gone from the tree, a line past the end of it, a code span and the link beside it saying different things, a bare name that is now two files, a rules row at an older tag that nobody flagged and a flag that outlived its re-citation. Print the report with `./gradlew :codex:citations` |

`HooksLedgerTest` and `HooksVanillaTest` compare the tree against the pin, and `DocsCitationTest`
compares each citation against the tag that citation names — which is the one thing in this table
that is deliberately not the pin, because a rules row left at an older tag is still true of it. All
three resolve a ref by **commit** rather than by tag
name. The tag lives in upstream's repository: this fork carries it only if someone pushes it, and
continuous integration clones the fork, so a tag-based check passes on a developer's machine and
fails in CI for a reason that has nothing to do with the code. The commit needs no such arrangement,
because `main` descends from it and any checkout with full history has it — and it is the stricter
of the two, since a tag can be moved and this is a pin. Continuous integration checks out with
`fetch-depth: 0`; a shallow clone fails with a message saying so rather than skipping.

## Hooks

| # | File(s) | Why | Guard | Introduced | Verified at tag |
|---|---|---|---|---|---|
| 1 | `settings.gradle` | Desktop and headless builds must never need the Android SDK or Xcode; Shatterfish modules need including | Marked `// shatterfish-hook:1`. The two mobile `include` lines are replaced by one `apply from: 'shatterfish/settings.gradle'`; that file includes `android`/`ios` only with `-Pshatterfish.mobile=on` (default `off`) and includes the six Shatterfish modules. This is the one hook that moves a vanilla line rather than wrapping it, so `HooksVanillaTest` names `shatterfish/settings.gradle` as its relocation target and checks both lines are still there. | 2026-09-03, E0 S2 (#1) | v4.0.2 |
| 2 | `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java` (new file) | The registry itself, row 2 of [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md). Upstream code that has to call Shatterfish calls a nullable listener field here instead of importing a Shatterfish module, so the dependency edges of [ADR-0003](adr/0003-module-layout.md) are never reversed: `harness` and `overlay` depend on `core`, never the other way round. This is the only Shatterfish-authored source file outside `shatterfish/`. Adding a listener point later edits this file only and consumes no new row; adding a *site* that calls one belongs to that site's row | The file is new, so vanilla behaviour cannot change by its presence, and `HooksLedgerTest` asserts hook id 2 appears in this file and nowhere else, which is what stops a hook being hidden inside the registry as one marker instead of many. With nothing registered every point is null and every site takes the vanilla branch; `HooksVanillaTest` asserts that, and that `Hooks.clear()` nulls every point declared, including ones added after it was written | 2026-09-04, E1 story 1.2 ([#15](https://github.com/watchthelight/shatterfish/issues/15)) | v4.0.2 |
| 3 | `core/.../scenes/GameScene.java` (1 site: `create()`, after the log pane) | Row 3 is the scene seam ([ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md)): creation and destruction notifications, including where the Observer re-registers its log listener. The play scene constructs its log pane in `create()` (`GameScene.java:530-533`), and the pane's constructor replaces every listener on `GLog.update` with itself (`…/ui/GameLog.java:47`; `SPD-classes/…/utils/Signal.java:58-61`), on every scene creation, so a listener the harness added is gone the moment a floor changes; and `create()` goes on to emit the floor's own lines, the descent and the feeling (`:596-599`, `:663-689`), which a listener re-added after `create()` returns would miss. The Overlay's scene is upstream's own, so a subclass cannot carry the seam | One listener point, `Hooks.logReplaced`, read once into a local and called if set, right after `add( log )`; six added lines and one import, nothing wrapped, nothing removed, which `HooksVanillaTest` holds. With nothing registered, the vanilla case, the local is null and nothing happens. The site is exercised at runtime by every Run the harness starts: `LogListenerTest` changes floor twice with the scene destroyed and recreated each time, as the game does, and holds the log section to the descent line each `create()` emits and to a message after it, and holds the signal to two listeners, the pane and the Observer's, after each creation. Destruction has no site yet; the row's second site lands with the Overlay | 2026-09-06, E1 story 1.10 ([#23](https://github.com/watchthelight/shatterfish/issues/23)) | v4.0.2 |
| 4 | `core/.../sprites/CharSprite.java` (1 site: the emote accessor), `SPD-classes/.../noosa/Group.java` (1 site: the members accessor) | ADR-0006's mob-state row: the only AI state the Observation carries is the emote the sprite shows, and `CharSprite.emo` is protected with no getter (`CharSprite.java:118`), written only by the show and hide methods (`:655-737`). Reading it needs an accessor; the alternatives are reflection, which `HarnessReflectionTest` confines to the stepper, and a class of ours in the sprite's package, which `HarnessPackageAnchorTest` forbids. The second site is ADR-0006's Prompt row: a Prompt window's title, text and buttons are members of its members, `Group.members` is protected with no getter (`Group.java:33`), and the same two alternatives are closed the same way; reading the members of `Game.scene()` is also how the Observer finds the window in front, as the scene does for itself (`GameScene.java:1471-1479`) | One public method, `shatterfishEmote()`, returning the field under the lock the show and hide methods take; it reads and never writes, so vanilla behaviour cannot change by its presence, and with nothing calling it the method is dead code. Ten added lines, a marker, three comment lines, the method and a blank line; nothing wrapped, nothing removed, which `HooksVanillaTest` holds by the rule that a hook encloses vanilla code and never deletes it. `ActorLeakTest` reads it through the Observer and holds that a mob's state, target and seen flag change nothing the accessor shows. On `Group`: one public method, `shatterfishMembers()`, returning a copy of the list under the class's own lock, the one every other method takes; it reads and never writes, so vanilla behaviour cannot change by its presence, and with nothing calling it the method is dead code. Nine added lines, a marker, four comment lines, the method and a blank line; nothing wrapped, nothing removed. `PromptGateTest` reads the chasm prompt, a potion's warning and an options window through it and holds the title, text and labels to what the windows draw, and a window that is not a Prompt to a failure of every read | 2026-09-06, E1 story 1.9 ([#22](https://github.com/watchthelight/shatterfish/issues/22)); the `Group` site 2026-09-06, E1 story 1.10 ([#23](https://github.com/watchthelight/shatterfish/issues/23)) | v4.0.2 |
| 5 | `core/.../scenes/GameScene.java` (3 sites: `selectCell`, `resetKeyHold`, `add(EmoIcon)`), `core/.../actors/hero/Hero.java` (1 site: `act()`) | Row 5 is "let the actor loop run with no `GameScene`" ([ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md), superseding ADR-0008's table). The actor thread reaches all three on an ordinary turn: `Hero.ready()` (`Hero.java:946`) reaches `selectCell` by way of `GameScene.ready()` (`GameScene.java:1653`), `Hero.interrupt()` calls `resetKeyHold`, and a sleeping mob's sprite builds an `EmoIcon` on update. None can be avoided by constructing the object the way `TargetHealthIndicator` and `AttackIndicator` were: `cellSelector` is `private static` (`GameScene.java:185`) and assigned only inside `create()` (`:368`), and `scene` is package-private (`:159`), assigned at `:242` and set back to null in `destroy()` (`:779`) | A null check on the static each site already uses. Vanilla is unaffected: `cellSelector` is never set back to null once assigned, and `scene` is null only between `destroy()` and `create()`, when no sprite is updating. The random stream was unchanged because `EmoIcon.Sleep` draws after the guarded call returns (row 8, story 5.13, now sends that draw to the base generator). Tested by `HooksVanillaTest`. The guard firing (the statics null, the headless case) is exercised at runtime for all three sites: without these guards `selectCell` dereferences `cellSelector` and then `Dungeon.hero` on every Input wait. The guard not firing (the vanilla case) is exercised for `add(EmoIcon)`, whose state is a `GameScene` instance — which constructs headlessly — and its `emoicons` group (`GameScene.java:185`, assigned at `:305`). The other two need a `CellSelector`, and every `DungeonTilemap` builds a `TextureFilm` from a texture in the abstract constructor (`DungeonTilemap.java:40-41`, `TextureFilm.java:53-55`), so any subclass needs a graphics binding and the game's assets on the classpath; `CellSelector` then reads `map.camera()` (`CellSelector.java:55-56`), which needs `Camera.main`. All five concrete subclasses name `Dungeon.level`; four of them require it, and the fifth guards with `if (Dungeon.level != null)` (`TerrainFeaturesTilemap.java:49`). Reaching those two sites therefore means the booted headless application of ADR-0015. Story 1.3 built it, and `HeadlessSceneTest` exercises both vanilla branches against the real scene constructed headlessly: `selectCell` installs the listener on the real `CellSelector` and asks it for its prompt, and `resetKeyHold` clears the selector's held action. Beyond the runtime checks the property is held against the pinned tag by the rule that a hook encloses vanilla code and never deletes it, which covers hooks not yet written. The fourth site is the Input-wait notification of ADR-0015: the first statement of the branch of `Hero.act()` that runs when the hero begins an act unready (`Hero.java:847` at the tag) reads `Hooks.inputWait` once into a local and calls it when set. Nine added lines, a marker, five comment lines, two statements and an import; nothing wrapped, nothing removed; with nothing registered the branch is vanilla, which every scene test that runs without the driver exercises (`SceneDrawParityTest` counts its draws). The branch runs once before every transition to ready and also on each step of a move and each turn of resting, so the listener is a notification to confirm, not a wait; `InputWaitCountTest` holds that sixty wake-ups of a parked hero notify nothing, a move of several steps notifies once per step, and an interruption is a wait of its own | 2026-09-04, E1 story 1.1 ([#14](https://github.com/watchthelight/shatterfish/issues/14)); the `Hero.act()` site 2026-09-05, E1 story 1.5 ([#18](https://github.com/watchthelight/shatterfish/issues/18)) | v4.0.2 |
| 6 | `core/.../actors/Actor.java` (1 site: the static sets), `core/.../levels/Level.java` (2 sites: `create()` and `restore()`), `SPD-classes/.../utils/Random.java` (1 site: `chances`), `core/.../levels/rooms/standard/entrance/EntranceRoom.java` (1 site: `placeEarlyGuidePages`) | Row 6 is "remove identity-hash ordering from anything that decides an outcome" ([ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md)), widened by that ADR's story 1.16 amendment to "remove whatever makes the same tuple a different Run in another process", because the last source found was not an ordering. `Actor.process()` walks `all` to break a tie between actors due at the same moment (`Actor.java:250-264`); a `HashSet` walks in identity-hash order, identity hashes differ between processes, and so the same Run takes a different turn order in a second JVM. `Level.mobs` and `Level.blobs` are re-inserted on every load and walked wherever the level asks which mob or blob it reaches first (`Level.java:183`, `:185`). `Random.chances` lays a class-keyed map's slices out in `keySet().toArray()` order (`Random.java:202-229`), so one seeded draw picks a different key in another process. `EntranceRoom.placeEarlyGuidePages` pushes an unseeded generator on purpose, to keep meta progression away from level generation (`EntranceRoom.java:102-105`), and pays for it with a draw from the system: story 1.15 measured the guidebook landing on a different cell in every Run of one tuple (issue #70) | Every site is add-only: the vanilla lines stand and the hook follows them, which `HooksVanillaTest`'s wrap rule holds. `Actor`: the two vanilla initialisers stand and a static block reassigns both fields to `LinkedHashSet`s — a `HashSet` with a defined order, so the declared types, the accessors at the bottom of the class and every caller are unchanged, and what the game decides is unchanged; only which of two equally-due actors goes first stops depending on the process. `Level`: the vanilla `new HashSet<>()` and `new HashMap<>()` stand at both sites and are reassigned to their linked forms on the next lines. `Random.chances`: the key array is sorted by name before the slices are laid out, and only where vanilla's own order is undefined — a `HashMap` keyed by `Class`. A `LinkedHashMap` is left exactly as vanilla lays it out, because the generator's category maps are ones, filled in declaration order and drawn from inside the floor's seeded push (`Generator.java:622-623`, `:676`, `:695`), and sorting them made this fork's seed generate a different floor from upstream's, which the fairness review of story 1.16 caught; a map keyed by anything but `Class` walks in an order its keys' own hashes fix and is left alone too. Every key keeps its own weight, and a class's name is stable where its hash is not. `EntranceRoom`: the vanilla unseeded push stands, is popped on the next line, and a generator seeded from the floor's own seed plus an offset is pushed in its place — constructing an unseeded generator draws nothing from the game's stream, so vanilla's draw count is unchanged; the seed keeps upstream's intent, since it depends on the dungeon and not on what any player has read, and the offset keeps the stream distinct from the floor's layout stream, which `Level.create` pushes from the same seed (`Level.java:221`). Twelve, twelve, thirteen and fourteen added lines, none removed. Held two ways. `IdentityOrderTest` holds the three ordering sites for what they are and do, because two JVMs started the same way on one machine give the same objects the same identity hashes and a two-process test run here cannot see an ordering reversion at all; `DeterminismTwoJvmTest` holds the guidebook site, whose reversion two fresh processes disagree on at once, and holds the whole — the same tuple played the same way gives the same Observation hash at every wait in this JVM and in two fresh ones. What the row does not reach, `Random.element` over class-keyed collections and the plain copies `Actor.all()` and `Actor.chars()` return, is [#73](https://github.com/watchthelight/shatterfish/issues/73) | 2026-09-12, E1 story 1.16 ([#29](https://github.com/watchthelight/shatterfish/issues/29)) | v4.0.2 |
| 8 | `SPD-classes/.../utils/Random.java` (5 markers: the scope and the four `useGeneratorStack` draws), `SPD-classes/.../noosa/particles/Emitter.java` (3 scopes: `start`, the `emit` in `update`, the particle update), `SPD-classes/.../noosa/audio/Music.java` (2 scopes: the track rolls), `core/.../effects/EmoIcon.java` (4 scopes: the constructors' scale), `core/.../sprites/ItemSprite.java` (1 scope: the pitch of the drop sound), `core/.../levels/SewerLevel.java` (1 scope: the drain's ripple delay), `core/.../effects/BadgeBanner.java` (1 scope: the shine's `Speck` reset) | Row 8 is "keep what the game draws for the eye off the Run's random stream" (story 5.13, issue #158, [ADR-0007](adr/0007-rng-seeding-strategy.md) option 15 and its story 5.13 amendment). The render thread draws particles, emote icons, the pitch of a sound and the music's track rolls from `Random.Float()` and its siblings, which the stack the Run owns answers; and how many frames the render thread draws between two waits is the wall clock's, not the tuple's. So an Overlay Run and a headless Run of one tuple consumed different draws between two waits and parted (found by tracing every draw made on the render thread). `Random.visualBegin()` and `visualEnd()` mark a per-thread scope in which the four `useGeneratorStack` draws answer from the base generator, which nothing reads for an outcome; the scope is per thread because the actor thread plays the Run while the render thread draws. The sites are the drawing calls only. The badge banner's shine was found late (issue #245): the banner adds a `Speck` to its badge on the frame it reaches its static state, a frame the wall clock picks, and the speck's constructor draws from the Run's stream; a takeover that played on past a badge parted from its replay at the next fight. Combat rolls made on the render thread (`onAttackComplete`) stay on the Run's stream. | Pure additions: a counter in `Random`, a marker and one line at the top of each draw, and a begin and an end around each call at the sites. Nothing is wrapped in a way that removes a vanilla line, which `HooksVanillaTest` holds; with no scope open (every call outside these sites, and the whole game when nothing sets one) each draw takes the branch it took before. `RandomVisualScopeTest` holds the routing, the per-thread scope and the floor at zero, and `OverlayReplayTest` records Overlay-style Runs with jittered frames and replays them under the Rig, every Observation hash matching (a control run with the routing forced off made it fail on 2026-10-25; that is not an automated check). The Profile is version 5 because every Run's stream moved, and 6 for the badge banner (issue #245), which `BadgeBannerStreamTest` holds. | 2026-10-25, E5 story 5.13 ([#158](https://github.com/watchthelight/shatterfish/issues/158)); BadgeBanner site 2026-10-25 ([#245](https://github.com/watchthelight/shatterfish/issues/245)) | v4.0.2 |
| 9 | `core/.../scenes/CellSelector.java` (3 sites: `select`, `processKeyHold`, `moveFromActions`) | Row 9 is "let the Overlay shut a person's input out of the hero while PAUSED" (story 5.5, [ADR-0013](adr/0013-overlay-threading-model.md) option 9, which the session 12 gate chose over a listener). A listener cannot do it: `Hero.ready()` reinstalls the game's own listener on every wake-up (`GameScene.java:1642-1643`), so a paused click would still run `hero.handle(cell); hero.next()` unlogged. Three methods are where a person's cell and key input funnel: `select` (every click, reached from `onClick` and from `GameScene.handleCell`, `CellSelector.java:152-171`), `moveFromActions` (every key and stick move: the held-key path, and the key-release and stick-release paths that call it without going through `processKeyHold`, `:324-327`, `:384-387`, `:400-427`), and `processKeyHold` (a held direction key or stick, which forces `enabled` and `Dungeon.hero.ready` true before it calls `moveFromActions`, `:469-486`, so no listener ever sees it) | One listener point, `Hooks.inputGate`, read once into a local at each site; if it is set and answers `open()` false, `select` returns before it does anything, `moveFromActions` returns false, and `processKeyHold` resets the held keys (so a key still down when input opens does not walk the hero) and returns before it forces the hero ready. Twenty added lines at the three sites and the nulling in `Hooks.clear()`; nothing wrapped, nothing removed, which `HooksVanillaTest` holds. With nothing registered, the vanilla case, both sites run as before. The gate is `HeroInputGate` in the harness: shut for every Run that is not HUMAN, open to the Run's own `ActionExecutor` for the length of its call (the executor reaches `select` through `GameScene.handleCell`) and to nothing else. `PausedInputTest` drives a click, a held direction key and a key-release move with the gate shut (no Action in the Run log, the hero where it was, not busy, not forced ready, the held key dropped), with it open again (the same inputs move the hero), and a Brain Run through a shut gate. **Not covered by this row:** the toolbar, the quickslots and the inventory pane call `Hero` and `Item` directly (`Toolbar.java:203`, `QuickSlotButton.java:102`, `InventoryPane.java:567`) and never reach the selector. A mouse or a key reaches them only through the game's input multiplexer, where the Overlay's `InputLock` stands first (`InputLockTest`); a game controller's button writes the key queue past the lock and is not gated (see `docs/ideas/2026-10-25-gate-hero-and-item-entry-points.md`) | 2026-10-25, E5 story 5.5 ([#150](https://github.com/watchthelight/shatterfish/issues/150)) | v4.0.2 |
| 10 | `core/.../SPDAction.java` (1 site: `addDefaultBinding`, next to `getDefaults`), `core/src/main/assets/messages/windows/windows.properties` (1 site: six strings) | Row 10 is "let the Overlay's controls be the game's own key-bound actions" (story 5.11, FR-42, UX-DR12; the registration point ADR-0008's ledger reserved for hotkeys). Everything else the game's key-binding system needs is already open: a `GameAction` is created by any subclass (`SPD-classes/.../input/GameAction.java:30-41`) and joins `GameAction.allActions()`, which is what `loadBindings` and `saveBindings` walk (`SPDAction.java:216`, `:386`) and what the key-binding screen lists (`WndKeyBindings.java:139`); a missing default only means no key. What is closed is the defaults: they live in a private static map (`SPDAction.java:93`), so a default key for a new action needs one method, and the screen's label for an action is `Messages.get(WndKeyBindings.class, action.name())` (`WndKeyBindings.java:258`), which is the windows string file's `windows.wndkeybindings.<name>` keys. | `addDefaultBinding(int, GameAction)` puts one default into the map and answers whether it did; it declines (answers false, adds nothing) a key the game already binds as a keyboard default, a controller default or a hard binding (`KeyBindings.isKeyBound`), so a registration can never shadow a binding the game ships, and a future tag that binds F6 leaves the Overlay's action unbound and rebindable instead of crashing the launch. Nothing is wrapped or removed. The six strings are new lines with the marker above them, labelled `Shatterfish: ...`; other languages fall back to them, as for any key a translation lacks. Nothing calls the method but the Overlay's `Hotkey.register()` in `OverlayGame.create()`, before `super.create()` loads the bindings, so the unmodified game and every headless and Rig Run never register an action. Held by `KeyBindingConflictTest` (F6 to F11 shadow no keyboard default, controller default or hard binding, and a taken key is declined), `HotkeyTest` (the six actions are in the game's list with their strings, a rebinding is followed, the game's bindings file keeps it) and `InputLockTest`. The FR-45 settings section is not part of this row: Pause-on conditions are E8's | 2026-10-25, E5 story 5.11 ([#156](https://github.com/watchthelight/shatterfish/issues/156)) | v4.0.2 |
| 11 | `core/.../actors/hero/Hero.java` (4 sites: `handle`, `rest`, `search`, `upgradeTalent`), `core/.../items/Item.java` (1 site: `execute(Hero, String)`), `core/.../scenes/CellSelector.java` (1 site: `select`) | Row 11 is "hear a player's input to the hero, so the Overlay can record a human's turns" (story 5.9, [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md)'s story 5.9 amendment, which raised the budget to eleven on the owner's decision of 2026-09-26). The only existing notification, row 5's, fires at the start of an act the hero began unready (`Hero.java:846-854`), after the input is gone: a click resolves in `Hero.handle` on the render thread (`Hero.java:1920-2008`) and the action it chose is consumed by the actor thread later in the same frame; the wait, rest, search, talent and item buttons all leave the hero merely busy with no action in hand, so what was pressed cannot be read back from the game. The six methods are where every such input converges: `handle` is reached from a click (`GameScene.java:1842-1848`), a held direction key (`update` calls `processKeyHold` at `CellSelector.java:388`, which calls `moveFromActions` from `:464-478`, which calls `handle` at `:395-417`; v4.0.2 reordered the test in `update` at `:383-389` so the stored cell is reset whether or not the hero is ready, and left the path to `handle` as it was), the attack and loot indicators (`AttackIndicator.java:184`, `LootIndicator.java:52`) and the wait-or-pick-up key (`Toolbar.java:256`); `rest` and `search(true)` only from the toolbar (`Toolbar.java:203`, `:225`, `:296`, `:313`); `upgradeTalent` only from the talent button (`TalentButton.java:251`); `execute(Hero, String)` from every item window and quickslot (`WndUseItem`, `WndQuickBag`, `QuickSlotButton`, `InventoryPane`, `RightClickMenu`, `Toolbar`), its other callers being only subclasses' `super.execute`; and `select` hands a targeting listener its cell (`CellSelector.java:152-171`) | One listener point, `Hooks.heroInput`, read once into a local and called if set, at the first statement of `rest`, `search` (only when `intentional`), `upgradeTalent` and `execute`, just before `handle` returns `true`, and just before `select` calls its listener; five lines per site (a marker, one comment line, the read, the call; the two sites outside `Hero` spell the registry's name in full instead of adding an import line), nothing wrapped, nothing removed, which `HooksVanillaTest` holds. The listener only records: it changes nothing the game computes and draws nothing. Nothing is registered outside an Overlay Run in HUMAN mode (`EmbeddedRun.attachHuman`), so every headless and Rig Run and the unmodified game take the vanilla branch; the executor's own calls pass the same sites and are heard by nobody. Exercised at runtime by `HumanTurnReplayTest` and `HumanActionMappingTest` | 2026-09-26, E5 story 5.9 ([#154](https://github.com/watchthelight/shatterfish/issues/154)) | v4.0.2 |

Hook ids are assigned by the ledger in [ADR-0016](adr/0016-hook-ledger-corrected-by-story-1-1.md), not sequentially, so gaps in this table are rows that have not landed yet rather than rows that are missing.

`README.md` and `.gitignore` are also modified (rewritten README; appended ignore
entries). They are documentation, not build hooks, and are re-applied on upgrade
by taking "ours" in the merge. `HooksLedgerTest` exempts exactly these two paths from
both the marker requirement and the diff budget. Two things are outside its reach by
construction rather than by exemption, and are recorded here so nobody mistakes silence
for a check: a new file at the repository root, which cannot be an upstream file because
upstream had no such file; and anything git is configured not to report.

A third thing is outside the ledger's reach because it is not an edit at all: Shatterfish code
reaching a private upstream member by reflection. Harness main code does this in two classes. The first reaches two fields,
both from `shatterfish/harness/src/main/java/org/shatterfish/harness/scene/SceneStepper.java`. It
writes `GameScene.actorThread` once per scene, starting the actor thread itself before the first
frame where the scene would start it in the middle of the first `update()`
(`GameScene.java:902-914` at the tag); that reorders the hero's first turn ahead of the first
frame and changes nothing the game computes, which is why it is recorded here rather than as a
row. It reads `Actor.current` and never writes it, to name the actor a stalled Run is waiting on
in the driver's diagnostic and in the stepper's own failure messages (story 1.4); no Run outcome
depends on it. Each fails immediately and by name if an upgrade moves its field, and row 4
(read-only accessors) is where they move if that happens. A second class does it for seven more
(issue #167): `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/RunStatics.java`
puts back, at the start of every Run, the private upstream statics a Run can leave behind for the
next one in the same process, each to the value its declaration gives a fresh process:
`Snake.dodges`, `GnollGeomancer.rocksInFlight`, `GnollGeomancer.knockedChars`, `Char.hitMissIcon`,
`Flail.spinBoost` and `RingOfWealth.latestDropTier`, and one more that is pinned rather than put
back (issue #139): `Holiday.cached`, which the game fills from the wall clock on first use, is set
to `Holiday.NONE` so that a Run is determined by (tag, seed, actions) and not by the date it was
played. It writes and never reads them, and apart from the holiday it writes
only what a fresh process already holds, so a Run in a process of its own, which every Rig Run and
every Overlay Run is, is unchanged by it on any day that is not a holiday; the sweep behind the list is in `docs/architecture.md`
("Statics that outlive a Run"). `HarnessReflectionTest` confines reflection in harness main code to
those two classes, by dependency rather than by call so that a method reference or a reflective
call to `getDeclaredField` itself does not slip past, and asserts that the fields each reaches are
exactly the ones named here, so another cannot arrive unannounced; for `RunStatics` it also asserts
no `Field` read of any kind, so the class can put state back and never carry any out. One more class
is exempt from the rule, and reaches no member at all: `NoOpGL`, the headless GL stand-in, is a
dynamic proxy, and building one means naming `java.lang.reflect.Proxy`, `InvocationHandler` and
`Method`; a separate rule in the same test holds it to that, with no field, no opening and no
invocation through `Method`.
Tests are not confined: the ledger's own tests, the scene fixtures, the row 5 checks and the
driver's test reach `Random.generators`, `Badges.global`, `Journal.loaded`, `GameScene.scene`,
`GameScene.emoicons`, `GameScene.cellSelector`, `CellSelector.heldAction1`, `Actor.current` and
`Group.members` to set up or observe what they test, and the identity-order checks reach
`Actor.all` and `Actor.chars` to hold row 6 in place. A fourth thing is outside the ledger's reach
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
3  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java
4  1  SPD-classes/src/main/java/com/watabou/noosa/Group.java
4  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/CharSprite.java
5  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java
5  3  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java
6  1  SPD-classes/src/main/java/com/watabou/utils/Random.java
6  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Actor.java
6  2  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java
6  1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/standard/entrance/EntranceRoom.java
8  5  SPD-classes/src/main/java/com/watabou/utils/Random.java
8  6  SPD-classes/src/main/java/com/watabou/noosa/particles/Emitter.java
8  4  SPD-classes/src/main/java/com/watabou/noosa/audio/Music.java
8  8  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/effects/EmoIcon.java
8  2  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/ItemSprite.java
8  2  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerLevel.java
8  2  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/effects/BadgeBanner.java
9  3  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/CellSelector.java
10 1  core/src/main/assets/messages/windows/windows.properties
10 1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/SPDAction.java
11 4  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java
11 1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Item.java
11 1  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/CellSelector.java
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
e3f44ab90ffa9d2a  25  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java
b8eb4ac001962d23  12  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Actor.java
76a4da67b0d6f851  12  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java
1fa2f0bede15aaa9  14  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/standard/entrance/EntranceRoom.java
23187ecdbde1bd72  25  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/CellSelector.java
25da1e43728094a5  25  2  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java
c3104d767d97c3a8   5  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Item.java
70a878edca3fe9c7 159  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java
b5c38c7c41565c37  10  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/CharSprite.java
76cc92a3ce10f1c6   9  0  SPD-classes/src/main/java/com/watabou/noosa/Group.java
fcd86efe6a8b4610  12  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/SPDAction.java
a33335c781021eac   7  0  core/src/main/assets/messages/windows/windows.properties
d71fa3674b2f7676  40  0  SPD-classes/src/main/java/com/watabou/utils/Random.java
adf259a5ae3ec5c8  12  0  SPD-classes/src/main/java/com/watabou/noosa/particles/Emitter.java
cac4b2402448c2ca   8  0  SPD-classes/src/main/java/com/watabou/noosa/audio/Music.java
3bee9125c4f592b2  16  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/effects/EmoIcon.java
47819a033ded794e   4  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/effects/BadgeBanner.java
8944078b9998438a   4  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/ItemSprite.java
0d206322f4893ee9   4  0  core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerLevel.java
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
4a. Re-verify every **mirrored body**. A hook is an edit to an upstream file and this table counts
   those; a mirrored body is the other thing, an upstream method whose source the harness has copied
   out because it is private and cannot be called. Nothing marks the upstream file, so an upgrade
   that rewrites one leaves the build green and the harness playing a slightly different game.
   `MirroredUpstreamTest` holds each to a digest and fails on drift: read the new upstream body,
   decide what the change means for the copy, change the copy, and only then record the new digest.
   The bodies mirrored today are `InterlevelScene.descend`, `.ascend` and `.fall`, copied by
   `RunLoop.crossFloor` and `HeadlessDriver.newGame`.
5. `./gradlew build` with no Android SDK.
6. Regenerate the Codex (`./gradlew :codex:generate`, once it exists) and commit
   the diff under `codex/<newtag>/`; publish a summary of what changed in
   mechanics tables.
7. Rerun fairness, differential, toggle, ArchUnit, and determinism tests.
8. Rerun the rig baseline and publish `docs/results/<date>-<sha>.md`.
9. Update this file (pinned table, hooks' "Verified at tag" column), `README.md`,
   and `docs/codebase-map.md` for anything the new tag contradicts.
10. Re-verify every citation: `./gradlew :codex:citations`. This is the instrument for the
   promise in rule 2 and for the re-verification `docs/rules/index.md` describes — it resolves
   every `path:line` in `docs/` against the tree at the tag that citation names, and prints the
   page, the line, the file and which way each one failed. Read the report and act on it row by
   row: where the cited text is still there, move the link to the line it moved to; where it is
   gone, changed, or now in more than one place, flip the row to `needs-review` and leave its link
   at the tag where it was true. Never re-cite a document to make the report empty. The same check
   runs as `DocsCitationTest` in `./gradlew build`, so the upgrade cannot merge with it red.
11. PR with label `touches-upstream`; merge only when CI is green.

### What moves with the pin, and what does not

A tag move rewrites some documents and deliberately leaves others alone, so that a reader who finds
`v3.3.8` in one of them knows it is a date and not a stale link.

| Moves with the pin | Stays at the tag it was written against |
|---|---|
| This file's pinned table and every hook row's "Verified at tag" | The accepted decision records under `docs/adr/`, whose citations are the evidence for a decision taken at that tag; an upgrade adds a dated note when a decision's ground moved, as `v4.0.0` did to ADR-0006's Blobs row |
| `README.md` and `docs/codebase-map.md`, which name files rather than lines | `docs/BOOTSTRAP-PROMPT.md` and anything under `docs/results/`, which are records of a moment |
| Every `docs/rules/` row whose cited text is still there, at the line it moved to | A rules row flipped to needs-review: its link stays where it was true until someone re-reads the code |
| The harness's own assertions about the release it runs, and the Brain's hand-written worst-case table (`SafeTest`, story 4.3): its classes are held to the regenerated Codex by `SafeTestCodexTest`, and its `docs/rules/identification.md` row is re-read like every other | A story file in `_bmad-output/`, which says what was true when the story ran |

Since story 2.10 the left-hand column is checked rather than remembered. `DocsCitationTest` resolves
every citation at the tag that citation itself names, so a rules row deliberately left behind is not
reported — and the convention holds in both directions instead of one: a rules row at an older tag
has to say `needs-review`, and a row that says `needs-review` has to be at an older tag. The
right-hand column is why the tag-age rule is a rules-table rule and nothing wider: a decision
record's citations are the evidence for a decision taken at that tag, so they are resolved there and
never flagged for it.
