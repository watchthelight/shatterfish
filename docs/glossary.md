# Glossary

Terms as used across Shatterfish's code, docs, stories, and issues. When a term here disagrees
with upstream's use of the same word, this page says so.

**Observation**
:   Immutable, serializable, content-hashed snapshot of everything the player could see at a
    given turn: the known map as drawn (terrain through the tilemap's own mapping, fog memory,
    discovered traps, seen heaps), visible actors with what the UI shows, hero stats and buffs,
    inventory with identification status exactly as presented (an unknown potion is a
    "turquoise potion"), equipment and quickslots, journal state, recent log lines, depth, turn.
    Lives in `api`; produced only by the Observer.

**Observer**
:   The single class in `harness` allowed to read game state and turn it into an Observation.
    Every change to it ships with leak tests. See [Fairness](fairness.md).

**Action**
:   What the bot may do: move-to, attack, use / throw / zap / read / drink / equip / drop an item
    at a target, rest, search, descend / ascend, talent or ability use, wait. Executed through the
    same code paths the UI uses, by the ActionExecutor.

**ActionExecutor**
:   The single class in `harness` that drives the hero. Applies an Action on the UI-role thread,
    which it asserts on entry (story 1.19).

**UI-role thread**
:   The one thread that observes and executes (ADR-0013): the driver thread headless, the render
    thread in the overlay. `UiRole` holds it as a claimed thread identity; the Observer, the
    executor and the driver's stepping fail loudly on any other thread.

**Decision**
:   The brain's output for one turn: the chosen Action, the top alternatives with scores and
    one-line reasons, the current goal, and safety flags. Shown in the overlay, written to the
    run log.

**Belief**
:   What the brain thinks about what it cannot see: per-unidentified-item candidate sets with
    probabilities, floor facts ("a pool room was seen, so an invisibility potion is on this
    floor"), chapter counters for guaranteed drops. Updated from every Observation, regardless
    of who acted.

**Harness**
:   The `harness` module: Observer, ActionExecutor, RNG control, snapshot/restore (`SnapshotStore`, story 1.20),
    redetermination, and the two drivers.

**Driver**
:   The thing that owns the game loop for the bot. `HeadlessDriver` runs the game on libGDX's
    headless backend with no scene; `EmbeddedDriver` runs inside the real desktop game.

**Codex**
:   Generated tables of every mob, item, drop table, spawn weight, trap, recipe, and changelog
    entry, dumped by reflection from the pinned tag into `codex/<tag>/*.json` and rendered under
    [Codex](codex/index.md). Never hand-edited. The source of "general game knowledge" the bot
    is allowed to have. Static and seed-free: a Codex value describes a type or a table, never a
    Run, and `CodexLeakTest` holds it (ADR-0017, story 2.1).

**Codex version**
:   The Codex's own version (`Codex.VERSION` in `api`), carried by the manifest of every
    `codex/<tag>/` and recorded by the Run-log header (ADR-0011): it changes when a table's
    meaning or shape changes, which is not derivable from the upstream tag, and it ties a Brain's
    behaviour to the knowledge it had.

**Citation (Codex)**
:   The `path:line` a Codex entry was read from, computed at generation by finding the
    declaration's line in the pinned source with an anchor (`Citations.at`), never typed from
    memory; an anchor that matches no line or two fails the task (ADR-0017).

**Rig**
:   Fishtest-style statistical testing: thousands of seeded runs in parallel, SPRT comparisons
    between two brains, JSONL run logs, replay. Nothing about the brain is believed until the rig
    says so. Numbers are published under [Results](results/index.md).

**SPRT**
:   Sequential probability ratio test. Runs games until it can accept or reject "brain B is
    better than brain A by at least the chosen margin" at the chosen error rates, instead of
    fixing the sample size in advance. Stockfish's Fishtest uses it for Elo; Shatterfish adapts
    it to win rate and depth reached.

**Lore**
:   Knowledge about the game that came from outside the code: the wiki, the subreddit, Evan's
    blog. Enters only through the lore pipeline as one markdown file per claim in `lore/` with
    provenance, a variant (`spd`, `pd`, `mod:<name>`, `unknown`), and a verification tier.

**Tier**
:   Verification level of a lore claim or rule: 1 = the code confirms it, 2 = the harness
    confirms it, 3 = a hypothesis for the rig, F = false or obsolete for a given tag.

**Rule**
:   A claim about a game mechanic that Shatterfish relies on, written in `docs/rules/` with a
    `path:line` citation into the pinned tag and a link to the test that checks it.

**Oracle mode**
:   A debugging and labelling mode in which the true hidden state (item identities, unseen
    enemies) is exposed. Off by default, enabled only by `--oracle`, visibly flagged in the
    overlay, never allowed in ranked rig runs. In the harness it is `OracleObserver`, whose
    Observation carries the header's oracle bit and whose `OracleView` sidecar is a harness type
    no Brain can reach (E1 story 1.18).

**Redetermination**
:   Before each search rollout, re-sample everything hidden (unknown item identities, unseen
    mob positions, RNG) from the belief state, so search never sees the real hidden state. The
    technique bridge and Scrabble engines use for imperfect information.

**Hook**
:   A minimal, justified edit to an upstream file. Labelled `touches-upstream`, listed in
    [Upstream](UPSTREAM.md), re-verified on every upstream upgrade. Prefer new modules over
    hooks.

**Upstream**
:   Shattered Pixel Dungeon at the pinned release tag. Never `upstream/master`.

**Overlay**
:   The bot running inside the real desktop game with a docked, native-style panel: mode line,
    current goal, chosen action and alternatives, beliefs, safety flags, decision log, map
    highlights, and controls to pause, step, run N, change speed, take over, and hand back.

**Story**
:   One unit of work small enough for one session: a BMAD story file under
    `_bmad-output/implementation-artifacts/` carrying context, acceptance criteria (naming the
    tests and rig numbers required), and its own history. Mirrored to a GitHub issue.

**Epic**
:   A group of stories with a measurable "done when". Mirrored to a GitHub milestone and an
    epic issue with a task list. See [Roadmap](roadmap.md).

**Turn** (program)
:   One instruction from the human to one handoff from the engineer. One numbered bootstrap
    step, one BMAD workflow, or one story through its full lifecycle. Not to be confused with a
    game turn.

**Leak test, differential test, toggle test, determinism test**
:   The four families of fairness tests; see [Fairness](fairness.md).
