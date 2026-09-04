---
status: proposed
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0008: How hooks are guarded and tracked

## Context and problem statement

Every edit to an upstream file is a hook: minimal, justified, labeled `touches-upstream`, listed
in `docs/UPSTREAM.md` (non-negotiable #3). The PRD caps v1 at eight hooks (§10). Session 10
showed where hooks will be needed: `GameScene`'s HUD fields are private and `cellSelector` is a
private static (`…/scenes/GameScene.java:159`, `:171-178`, `:200-209`), `Hero.ready()` and
`Hero.interrupt()` dereference scene statics (`…/actors/hero/Hero.java:935-946` and `:948-964`), turn resolution
runs through sprite callbacks, and two RNG edits are decided in ADR-0007. Decide how a hook is
written so that the game is unchanged when Shatterfish is absent, how hooks are counted, and how
an upgrade finds every one of them.

Non-negotiables touched: #3 (hooks), #5 (reproducible: a hook must not change vanilla behavior).

## Decision drivers

- Vanilla behavior byte-identical when no Shatterfish component is registered (the unmodified
  game must still be `./gradlew :desktop:run`).
- An upstream tag merge must surface every hook as a conflict or a visible diff, never silently
  drop one.
- The count of hooks must be mechanically checkable against `docs/UPSTREAM.md`.
- Prefer new classes and subclasses over edits; where an edit is unavoidable, one line.

## Considered options

1. Plain edits, tracked by hand in `docs/UPSTREAM.md`. Rejected as the whole answer: nothing
   checks the table against the tree, and a merge can drop an edit without a conflict when the
   surrounding lines also changed.
2. Bytecode instrumentation at build or launch time (ByteBuddy or an agent) so that no upstream
   source is edited. Rejected: a hook site is still a hook, only invisible in the diff; debugging
   through rewritten bytecode is hostile to a solo engineer; and an upgrade that moves a method
   breaks the rewrite silently at runtime instead of at compile time.
3. Patch files applied by the build (quilt-style). Rejected: the tree in the repository would
   not be what compiles; every tool and reviewer would see the wrong source.
4. Fork-and-replace whole classes in a Shatterfish module that shadows upstream's on the
   classpath. Rejected: classpath shadowing is order-dependent and duplicates hundreds of lines
   per class; the merge cost is the whole class.
5. **A single registry class added to `core`, `com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks`,
   holding one nullable static listener field per hook point; each hook site is one line of the
   form `if (Hooks.<point> != null) Hooks.<point>.on…(…); else <vanilla>` (or a pure
   notification with no `else`), marked with the comment `// shatterfish-hook:<id>`.** Chosen.
   The registry has no dependency on any Shatterfish module (listeners are interfaces declared in
   the registry itself); `harness` and `overlay` register listeners at start-up. With nothing
   registered every site runs the vanilla branch.
6. **Subclasses and new classes wherever the game exposes a seam** (a Harness-owned `Scene`
   instead of `GameScene`; the Overlay `Panel` as a `Component` added to the scene). Chosen in
   addition; these are not hooks unless they need an upstream edit to be reached.
7. Reflection into private fields (`GameScene.scene`, `cellSelector`) instead of a hook.
   Rejected: reflection is a hook the diff cannot see and `-Werror`-clean code should not depend
   on it; an accessor hook is one honest line.

## Decision outcome

- Hook points are declared in `Hooks.java` (a new file under `core`, itself hook #2, listed once).
  Each point is a small interface and a `public static volatile` field; each site is one line
  with the marker comment and the id from `docs/UPSTREAM.md`.
- Vanilla equivalence: no listener registered means the vanilla branch executes; a `HooksTest`
  in `harness` registers nothing, boots a headless Run, and asserts that no hook fired where the
  vanilla branch has side effects.
- Counting: `harness` has a test that greps the upstream tree for `shatterfish-hook:` markers and
  fails if the set of ids differs from the rows in `docs/UPSTREAM.md` (parsed from the table). A
  row is one change-set with one id; several one-line sites may share an id when they are the
  same kind of edit for the same reason (the accessor row, the identity-order row), and the row
  lists every site. The budget check (at most ten rows in v1) lives in the same test.
- Change guards: every hook site's surrounding method is named in `docs/UPSTREAM.md`; the
  `upstream-sync` skill's step 9 re-reads each site after a merge, and the citation checker
  (FR-17) flags a row whose `path:line` no longer resolves.
- Field-visibility hooks (`GameScene` HUD fields, `cellSelector`) are accessors added next to the
  fields, not visibility changes, so the diff is additive.
- The RNG hooks of ADR-0007 (`LinkedHashSet`, sorted `chances`) are edits with no listener; they
  carry the marker comment and a row, and their "vanilla equivalence" is argued in the row
  (semantically neutral) and tested by the determinism suite.

Expected v1 hook ledger (each row lands with the epic that needs it; the budget is ten):

`[NOTE FOR PM]` The PRD's section 10 caps v1 at eight hooks. The session 12 reviewer gate showed
that two capabilities the PRD itself requires cannot ship inside eight: blocking hero input while
the Overlay is PAUSED (FR-40) needs a gate the game consults, because `GameScene.ready()`
reinstalls the default cell listener on every wake-up and `CellSelector.processKeyHold` bypasses
the listener entirely; and hotkeys (FR-42) plus the v2 Pause-on settings section (FR-45) need an
`SPDAction` registration point, since `SPDAction`'s defaults live in a private static map in
`core`. The budget is therefore ten, and the PRD is amended to match. Each new row is one line at
its site.

| Id | Where | Epic | Why |
|---|---|---|---|
| 1 | `settings.gradle` | done | mobile modules opt-in, Shatterfish modules included |
| 2 | `Hooks.java` (new file) | E1 | the registry |
| 3 | `GameScene` seam: scene creation and destruction notifications | E1, E5 | a Harness-owned scene; the Overlay attaches; the Observer re-registers its `GLog.update` listener, which `GameLog`'s constructor replaces on every scene creation (`…/ui/GameLog.java:47`) |
| 4 | read-only accessors for private state the screen shows: `GameScene` HUD fields and `cellSelector`, `CharSprite.emo` | E1, E5 | ADR-0006 (emote row); the Overlay reaches the panes |
| 5 | `Hero.act()` Input-wait notification at the `Dungeon.observe()` site, plus `Hero.ready()` / `Hero.interrupt()` guards for scene statics under a headless scene | E1 | ADR-0015; `docs/rules/game-loop.md` |
| 6 | identity-order removal: `Actor.all`/`chars`, `Level.mobs` as `LinkedHashSet`, `Level.blobs` as `LinkedHashMap`, sorted class keys in `Random.chances(HashMap)` and `Random.element` | E1 | ADR-0007 option 10 |
| 7 | sprite-wait bypass for "Fast as it can" (`CharSprite` motion interval) | E5 | FR-39 |
| 8 | `Emitter`, `Music` and `EmoIcon` draws to the base generator | E5 | ADR-0007 option 15 (required for FR-24) |
| 9 | input gate: `CellSelector.select` and `CellSelector.processKeyHold` consult `Hooks.inputGate` before acting | E5 | PAUSED must ignore hero input (FR-40); a listener cannot do it (ADR-0013) |
| 10 | `SPDAction` registration point for Overlay actions and an Overlay section in the settings screen | E5, E8 | hotkeys (FR-42) and Pause-on conditions (FR-45) |

If the E1 touchpoint audit needs an eleventh, the budget is revisited in an ADR, not by adding a
row.

### Consequences

- Good: the game runs unchanged without Shatterfish; a reviewer reads `Hooks.java` and the
  table and knows every edit.
- Good: a merge that drops a marker fails the counting test; a merge that moves a site keeps
  the marker with it.
- Bad: one new file under upstream's package tree; it is the only Shatterfish source outside
  `shatterfish/`, and it is documented as such in `docs/UPSTREAM.md`.
- Bad: listener interfaces in `core` mean `harness` and `overlay` implement `core` types; that is
  already allowed by their dependency edges (ADR-0003) and never touches `brain`.

## Pre-mortem

*If this is wrong in six months, why?*

- Hooks multiply past eight during the E1 touchpoint audit (969 sprite dereferences). Mitigation:
  the headless scene strategy needs zero edits to actor or item code by design (research
  recommendation 1); the audit story's exit criterion is the hook count, and a ninth forces an ADR.
- A hook site changes semantics for vanilla because the `else` branch was mis-copied.
  Mitigation: sites are notifications wherever possible; the vanilla-equivalence test and the
  determinism suite run on every pull request.
- Upstream 4.0 restructures `GameScene` and the seam moves. Mitigation: the merge procedure
  measures the diff first (`docs/UPSTREAM.md`, upgrade procedure); the registry keeps the hook
  points stable even when sites move.
- The marker-count test is gamed by moving a hook into `Hooks.java` itself. Mitigation: the test
  counts sites outside `Hooks.java` and the rows describe sites, not the registry.
