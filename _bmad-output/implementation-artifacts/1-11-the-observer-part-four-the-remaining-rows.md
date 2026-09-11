---
story: 1.11
key: 1-11-the-observer-part-four-the-remaining-rows
title: "The Observer, part four: the remaining rows"
epic: 1
issue: 24
status: review
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 1
baseline_commit: 'e8b5339ab4af52039215ed8c8afd6f40885f88b1'
---

# Story 1.11: The Observer, part four: the remaining rows

As the bot,
I want the environment facts the screen shows and nothing more,
So that no row of the whitelist is left unimplemented and untested.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`).

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given the rows of ADR-0006 not covered by stories 1.8 to 1.10, when the Observer builds them, then a gas or fire blob appears as the set of blob kinds present in cells the hero can see, with no volume | **Met.** `Observer.map()` carries the kinds of every blob whose emitter is emitting on a cell the fog paints seen; the volume reaches nothing, and `EnvironmentLeakTest` holds a gas at two hundred times the amount to the same bytes |
| And the danger count is the number the indicator shows, which includes invisible enemies in view | **Met.** ADR-0005 had already settled that it is no field but the enemies among the actors; `EnvironmentLeakTest` holds that count equal to `hero.visibleEnemies()` at the first wait, with an enemy moved into view, and with that enemy invisible |
| And the sealed flag reflects the boss lock, so the valid-Action set never offers a descent the game refuses | **Met.** The header carried `Level.locked` from story 1.8; `FloorSectionTest` seals and unseals the floor and holds the flag and the `LockedFloor` buff's icon to each other |
| And `EnvironmentLeakTest` asserts blob volumes and blobs outside the field of view are absent | **Met.** By the differential over the volume, by the absence of the amount from the JSON and the bytes, and by a gas on a remembered cell being no blob cell and its name being absent |
| And a checklist test asserts every row of the ADR-0006 table has at least one leak test naming it | **Met.** `VisibilityChecklistTest` reads the whitelist out of the record, collects the rows each observer suite claims in its own `ADR_0006_ROWS` field, and fails when a row is claimed by nobody, when a claim names no row, or when a claiming suite declares no test; one row is listed as pending with the issue that closes it (Valid Actions, #25) |

The floor feeling and the transitions, which the story's Given names with the others, are carried by
`map()` as well, and `Observer.observe()` now returns every section as one Observation.

## What was built

- `shatterfish/harness/.../observer/Observer.java`: the blobs, the feeling and the transitions in
  `map()`; `STAIRS`, the tiles a way up or down is drawn as; and `observe()`, the whole read, with
  `ActionsSection.NONE` until story 1.12. No upstream file is edited and no hook is spent:
  everything read here is public.
- `shatterfish/api/.../MapSection.java`: the note on the always-visible blobs corrected and named.
- Tests: `EnvironmentLeakTest` (the blobs, the danger count, the seed and the clock),
  `FloorSectionTest` (the feeling, the transitions, the boss lock), `ObserveTest` (the whole read),
  `VisibilityChecklistTest` (the checklist); every existing observer suite declares the rows it
  holds, and `Skeleton.everything` is now `observe()`.
- Docs: three rows in ADR-0006's whitelist and the story's amendment; a correction in ADR-0005;
  four Test cells and three new rows in the rules pages; an entry in `docs/ideas.md`.

## What the story found

**A blob draws only through an emitter a scene gave it.** `Blob.seed` puts the blob on the level
and no sprite in the scene (`…/actors/blobs/Blob.java:254-272`); the idiom every caller uses is
`GameScene.add(Blob.seed(...))` (`…/items/potions/PotionOfToxicGas.java:49`), and the scene makes
one emitter per blob at creation and one per blob added during play
(`…/scenes/GameScene.java:343-348`, `:1055-1058`, `:1131-1136`). An emitter emits only while `on`,
which a blob's `use(BlobEmitter)` sets by giving it a particle factory
(`SPD-classes/…/noosa/particles/Emitter.java:46`, `:116-128`); the base `Blob.use` only keeps the
emitter, and five blobs never override it, upstream's own well marker among them, whose comment
says what it is for: "we use a blob to track visibility of the well"
(`…/levels/rooms/special/WeakFloorRoom.java:105-127`).

**The fog is drawn over the gases, and paints a cell seen only where the hero sees.** The scene
adds `gases` before `fog` (`GameScene.java:343-353`), so the fog covers the particles; and every
path to `Fog.VISIBLE` runs through the Observer's `own()`, which asks `heroFOV`. So one test of the
painted fog is both the emitter's own gate and the fog's, and the emitter's other gate, a blob
marked always visible, cannot change what the method emits.

**A grep that misses a formatting variant tells a lie.** `grep "use( BlobEmitter"` found no
override on the three always-visible blobs, and a first draft of this story wrote in three
documents that they draw nothing at all. They spell it `use(BlobEmitter` and they all pour
particles (`…/actors/mobs/Tengu.java:913-918`, `:1089-1094`;
`…/items/artifacts/SkeletonKey.java:549-553`), so the game draws them through the fog of a
remembered cell and the section cannot carry them: a loss, not a nothing.

**A transition is in the level from generation, whatever its cell draws.** The review found the
leak: the Halls boss floor builds its exit under the wall of the centrepiece and paints `EXIT` only
in `unseal()`, when Yog dies (`…/levels/HallsBossLevel.java:135`, `:164`, `:170-174`, `:288-293`),
so the first draft told the bot where the exit of that fight was while the screen drew a decorated
wall; upstream itself asks `map[exit()] != Terrain.EXIT` for "not open yet" (`:88`, `:244`). The
vault's entrance room is the second site: a branch entrance on a cell with neither stairs nor a
visual (`…/levels/rooms/quest/vault/VaultEntranceRoom.java:41-60`). The gate now asks what the cell
draws, which is also the whole of asking whether the player has seen it, since a cell the fog hides
draws no tile at all.

**`ENTRANCE_SP` is decoration, not a branch.** It is the entrance variant six entrance rooms paint
(`…/levels/rooms/standard/entrance/RingEntranceRoom.java:48` and its siblings), and a branch
transition is marked by the custom tilemap over it, not by its terrain, so a transition's kind comes
from the transition and never from the tile.

**The feeling is drawn from the moment the floor is.** The depth button takes an icon per value at
scene creation (`…/ui/MenuPane.java:88-89`; `…/ui/Icons.java:478-497`), names it in its hover text
and titles a window with it (`MenuPane.java:98-116`); the arrival line
(`…/scenes/GameScene.java:670-699`) is a second channel and not the only one.

**The battery found four gaps in the tests before it found none in the code**, and a fifth
afterwards: a state the tag's own code never reaches has to be put there by the test, as story
1.10's hidden window member was, and a clause that cannot fail is a clause to delete.

## Decisions taken inside the story

**A blob is read through the emitter's own predicate.** Alternatives: (a) `cur[cell] > 0` in
`heroFOV`, the ADR row as written; (b) the emitter's whole condition, including that the emitter
exists and is emitting; (c) the cell info's rule, `tileDesc() != null`. Chosen (b): (a) carries
blobs the screen never draws, upstream's well marker for one; (c) drops the regrowth and the
alchemy pot, whose particles the player sees. Pre-mortem: a later tag that gives a marker blob a
factory starts carrying it, which is right by the same rule.

**A blob cell is gated on the painted fog, not on `heroFOV`.** Alternatives: (a) the emitter's
`heroFOV`; (b) the painted fog. Chosen (b): the two agree except on a wall face the fog paints
dark, where the fog is what the player sees, and the record requires it. The always-visible clause
is then dead code, and the losses are recorded instead.

**A transition is carried at its designated cell, and only where a way up or down is drawn.**
Alternatives: (a) the cell, once seen, which is what the first draft did; (b) every seen cell of
the transition's rectangle; (c) the cell, once it draws as stairs. Chosen (c): (a) leaks the Halls
exit and the vault entrance; (b) names floor and wall cells of a boss region as ways down.
Pre-mortem: a later tag that draws a transition some third way, a custom tilemap with no stairs
terrain, would drop it — a loss, and the upgrade procedure's rules review is where it surfaces.

**The whole read is one method over the section methods.** Alternatives: (a) `observe()` builds
the sections itself and the section methods go; (b) `observe()` calls them. Chosen (b): each
section method is what a leak test holds one rule with, and the record holds the sections to each
other whichever way they are built.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 452 tests across 37 suites, sixty-nine of them
the fifteen observer suites. `mkdocs build --strict`: clean.

**Mutation battery**, twenty-three mutations of `Observer.java`, of ADR-0006's table and of a
suite's claim, each applied to a committed clean tree, run against `EnvironmentLeakTest`,
`FloorSectionTest`, `ObserveTest` and `VisibilityChecklistTest`, restored with `git checkout`, and
the tree verified clean after each:

| # | Mutation | Caught by |
|---|---|---|
| M1 | a blob is carried wherever the fog paints it | `EnvironmentLeakTest` (a gas on a remembered cell; the record refuses a blob off a seen cell) |
| M2 | a blob with no emitter is carried | `EnvironmentLeakTest` (the gas the scene never drew) |
| M3 | an emitter that was given no factory draws | `EnvironmentLeakTest` (the emitter switched off by hand; it survived the first run, and the test now puts the emitter in that state) |
| M4 | a blob of no volume is carried | `EnvironmentLeakTest` (the volume set to zero; it survived the first run, and the test now sets it) |
| M5 | the kind carries the amount | `EnvironmentLeakTest` (the kinds on the cell) |
| M6 | the kind is not the class the game draws | `EnvironmentLeakTest` (the kinds on the cell) |
| M7 | every cell in view holds every blob | `EnvironmentLeakTest` (the first floor has no blob) |
| M8 | a cell is named once per blob on it | `EnvironmentLeakTest` (two kinds on one cell; the record refuses a repeat) |
| M9 | a transition is read from the terrain, not from what is drawn | `FloorSectionTest` (the way down under a wall, and the unseen one; the record refuses a transition on a cell never seen) |
| M10 | a transition is carried at the corner of its rectangle, not its own cell | `FloorSectionTest` (a region of nine cells; it survived the first run, and the test now widens one) |
| M11 | every transition is a regular exit | `FloorSectionTest` (the surface transition's kind) |
| M12 | the floor has no feeling | `FloorSectionTest` (every value of the enum) |
| M13 | the feeling is the next one along | `FloorSectionTest` (every value of the enum) |
| M14 | the floor is never sealed | `FloorSectionTest` (the boss lock) |
| M15 | an invisible enemy in view is not an actor | `EnvironmentLeakTest` (the danger count) |
| M16 | the whole read is not the sections | `ObserveTest` (an actor in view; it survived the first run, and the test now brings one into view) |
| M17 | a row joins the whitelist with no test | `VisibilityChecklistTest` (the row, by name) |
| M18 | a suite drops its claim on a row | `VisibilityChecklistTest` (the row, by name) |
| M19 | a suite claims a row the record does not have | `VisibilityChecklistTest` (the claim and its suite) |
| M20 | a transition is carried whatever the cell draws | `FloorSectionTest` (the way down under a wall; the vault's floor) |
| M21 | a wall and a floor count as a way down | `FloorSectionTest` (both new cases) |
| M22 | an exit is not a way down | `FloorSectionTest` (the way down, mapped) |
| M23 | an entrance is not a way up | `FloorSectionTest` (the surface transition) |

Fifteen of nineteen were caught at `a4a1065f0`; the four survivors were gaps in this story's own
tests, not leaks, and closed in `6cdaae11b`. The review's fixes added four more mutations, of which
M9 then survived and showed the new gate's fog test could not fail on its own, a clause deleted
with the commit that re-aimed the mutation. All twenty-three are caught at the head of the branch.

## The fairness review

Run as an isolated `fairness-reviewer` on `a4a1065f0..6cdaae11b`. Verdict: **BLOCK**, and it was
right.

1. **A transition was carried on a cell that draws a wall.** The blocking finding, with the Halls
   boss floor and the vault's entrance room as its two sites; the gate now asks what the cell
   draws, `FloorSectionTest` holds both cases, and four mutations stand behind them. This is the
   leak this story would otherwise have shipped.
2. **The comment that justified the rule was false** ("the cell the stairs or the quest visual
   stand on at every site of the tag"), and the same sentence was in the rules page, the ADR and
   the story. All four corrected against the tag.
3. **The claim that every blob of the tag gives its emitter a factory was false**: five never
   override `use`, and the review named them. The rules row is corrected, and
   `EnvironmentLeakTest` now holds upstream's own well marker to drawing nothing, which is a
   better defence of the gate than the hand-set flag it had.
4. **ADR-0006's Blobs row still granted what the code refuses**, an always-visible blob out of
   view; the row is amended, not only the prose at the end of the record.
5. **The checklist's javadoc overclaimed**: it holds that a suite claims a row and has tests, not
   that the tests earn the claim. Said plainly now, in the class and in the amendment.
6. **An undocumented loss**: blobs that draw through the terrain rather than an emitter, the
   Cleric's wall of light and hallowed ground, are not carried at all, and the wall of light even
   makes its cells solid. Recorded in the amendment's losses and in `docs/ideas.md` for the schema
   story that closes it.
7. **Five cite ranges had drifted**; they were already corrected in `dff88eb0b`, a commit after
   the range the review was given.

The review also checked and confirmed what the story claims: that `Fog.VISIBLE` implies `heroFOV`,
that the blob's bounding rectangle always contains its cells so `setupArea()` need not be called,
that naming a blob by its class is fair where its particles are drawn, that the feeling is drawn at
every moment and not only logged, that the danger count equals the indicator's number with the
cache fresh at a wait, that `observe()` opens no path the sections did not, that no hash or thread
order reaches the bytes, and that nothing here writes game state or reflects into upstream.

## Deviations

- No upstream file is touched and no hook row is spent, so the PR carries no `touches-upstream`
  label; the reading this story needs is all public.
- The battery was run once against an uncommitted tree, and its restore threw away the review fixes
  in the three files it mutates. They were rewritten from the same scripts; the rule that it runs
  only on a committed clean tree is in the runner's own comment now.

## Known limitations, handed forward

- **The valid Actions are `ActionsSection.NONE`** until story 1.12, which is also the checklist's
  one pending row.
- **A blob that draws through the terrain or a custom tilemap is not carried** (`docs/ideas.md`).
- **An always-visible blob outside the field of view is not carried**, though a player sees it.
- **A blob on a wall face the fog paints dark is not carried.**
- **A multi-cell transition region is carried by its designated cell alone**, so its extent is not
  known and a player who sees only its edge sees a way down the section does not name.
- **A transition drawn some way other than by its terrain would be dropped**; none is at the tag.
- **The feeling's own words are not carried**, only the value: the text is the Codex's (E2).

## Follow-ups for later stories

- Story 1.12 (#25): `Action`, `validActions(Observation)`, and the checklist's pending row moved to
  the suite that holds it.
- Story 1.13 (#26): the executor, which is the first caller of `observe()` in a loop.
- The schema row for what a blob does to a cell's terrain (`docs/ideas.md`).
