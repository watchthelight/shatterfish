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
    entry, read from the pinned classes and their declarations into `codex/<tag>/*.json` (no
    reflection into a private member, no Run) and rendered under [Codex](codex/index.md). Never
    hand-edited. The source of "general game knowledge" the bot is allowed to have. Static and
    seed-free: a Codex value describes a type or a table, never a Run, and `CodexLeakTest` holds
    it (ADR-0017, story 2.1).

**Codex version**
:   The Codex's own version (`Codex.VERSION` in `api`), carried by the manifest of every
    `codex/<tag>/`, to be recorded by the Run-log header (ADR-0011, E3): it changes when a table's
    meaning or shape changes, which is not derivable from the upstream tag, and it ties a Brain's
    behaviour to the knowledge it had.

**Citation (Codex)**
:   The `path:line` a Codex entry was read from, computed at generation by finding the
    declaration's line in the pinned source with an anchor (`Citations.at`), never typed from
    memory; an anchor that matches no line or two fails the task (ADR-0017).

**Variant (Codex)**
:   A mob's fields under a depth or a challenge where they differ from the base at depth 1 with
    no challenge, read by constructing the mob under those values through the generator's one
    door to `Dungeon.depth` and `Dungeon.challenges` (`GameContext`, story 2.2), under a
    generator of the Codex's own. A depth's variant and a challenge's variant compose. A field a
    constructor takes from the hero of a Run is `runDependent`, dumped as zero and not compared;
    a facet a constructor draws is named random, not dumped, and the draw cited; a class whose
    stats the game sets after construction is `statsSetLater`.

**Deck (Codex)**
:   The generator's item decks as `decks.json` carries them: every `Generator.Category` with
    its weight in each of the two category decks, its superclass, how many decks it draws by
    (none, one or two) and its classes with their first- and second-deck weights, read from the
    enum's public defaults and never from a Run's mutable copy (`probs`, `seed`, `dropped`),
    which the leak test holds unchanged. A category that draws another way (an armor by the
    floor's tier table, a weapon by a tier category) draws by no deck and its classes carry no
    weight.

**Label pool (Codex)**
:   The appearance labels of one identifiable family (the potion colours, the scroll runes,
    the ring gems) in the game's order, each key with its display name from the English bundle
    for the regular family and for its exotics, cited to the `put` line and the bundle lines;
    what a player sees before an identification. The mapping from label to class is a Run's and
    is never in the Codex.

**Schedule (Codex)**
:   A limited drop's chances as `guarantees.json` carries them: for every depth and every
    state of the Run's counter, the thousandths that the game's method says the drop is
    needed, that the floor places it and that it places it under Forbidden Runes. A floor
    places it only where the level class the main branch builds there empties the floor's spawn
    list, which a boss floor and the amulet floor do not. Computed by a mirror of the method
    pinned to its exact source text and held to the game's own method by
    `GuaranteeArithmeticTest`.

**Trap pool (Codex)**
:   One arm of a level's trap list as `traps.json` carries it: the trap classes that level draws
    and the weight it gives each, with the condition that chooses this arm where the level has
    more than one (the sewers draw one trap on the first floor and eleven after it) and an empty
    condition where it has one. Read from the two array literals the methods return in whichever
    class declares them, never from the method text as a whole, since the condition itself contains
    numbers. A pool names the class it was read from when that is not the level's own, and carries
    how many traps the floor lays, so a floor that draws from a pool and lays none says so.

**Recipe registry (Codex)**
:   One of the private lists the alchemy pot tries, in the order its own method walks them, which
    is where the generator reads their names rather than holding a list of its own. `recipes.json`
    carries every recipe of every registry, named by the entry that constructs it. A recipe that
    states fixed inputs carries them, its output and its energy cost; one that does not carries the
    text of `testIngredients`, `cost` and `sampleOutput` instead and is marked as stating no list.
    A quantity the recipe names rather than writes is resolved to the constant's own declaration.

**Sealed floor (Codex)**
:   A floor that locks behind the hero while a fight is on, which a player observes. `levels.json`
    marks one when the level class either overrides the base's sealing or calls it in its own
    source, says which of the two it was, and cites the line. The five boss floors and the vault
    seal; nothing else does.

**Level feeling (Codex)**
:   One of the eight moods a floor can be built with. `levels.json` carries each with the chance
    the game's roll gives it in thousandths, the arm that sets it as cited text, and every place
    under the levels of the game that reads it, since most feelings do their work elsewhere than in
    the arm that names them. The gate on the roll is carried beside them: a boss floor, a floor of
    a branch and the first floor never roll one.

**Text key (Codex)**
:   The name the game looks a string up by: a class's own name, lower-cased, with the root package
    dropped and a nested class's `$` kept, plus the suffix the caller asks for. `strings.json`
    reads that rule backwards, taking the longest prefix of a key that is a class the game
    compiles. A key matching none carries an empty class and says so; the game keeps text for
    classes it no longer has, and keys plenty of text to no class at all.

**Asset index (Codex)**
:   `assets.json`: every path the game names, whether a constant of its asset class names it or a
    literal string at the place that loads it does, with the group that holds it (a nested group by
    its whole nesting), which of the game's two asset folders holds the file, and the line that
    names it. One name is dead at this tag and is carried as absent with its reason.

**Version record (Codex)**
:   The version the pinned tree builds as and the save codes the game still names, carried in
    `changelog.json` beside every entry of the game's own changelist, in the order the game shows
    them. An entry carries every date its own text states; no entry states one at this tag, and all
    93 dates the game states are on headings, which the table shows rather than tidies. A heading
    the game shows only on one platform carries that condition.

**Vocabulary diff (Codex)**
:   `vocabulary.json`: the one Codex table read from two pinned games. A row is a display name,
    since that is what a forum post carries and the two games share almost no class names. It says
    which games give the name, names and cites the classes that carry it on each side, and lists
    the mechanics the two state differently. Nothing reads it yet.

**Not comparable (Codex)**
:   What the vocabulary diff says where both games state a fact but in shapes that cannot be set
    against each other: a roll this game measures and the other writes as a method, or an item's
    numbers one game passes to a constructor. The row carries the text each game states and judges
    nothing, because deciding what such a difference means is not a thing the source says.

**The second pinned source**
:   Vanilla Pixel Dungeon at tag `archive`, named in `vanilla.pin` and fetched by
    `tools/fetch-vanilla.sh` into `vanilla-src/`, which is not committed. Read only: never merged,
    never built, never on a compile path, and imported by nothing. See `docs/UPSTREAM.md`.

**Tier table (Codex)**
:   The generator's `floorSetTierProbs` as `tiers.json` carries it: per floor set
    (`depth / 5`, gated so the last row covers every deeper floor) the five weights by tier,
    with the rules that an armor picks its class by the drawn index and a weapon or missile
    picks a tier category, and the tier arrays they index.

**Measurement (Codex)**
:   A Codex number produced by running the engine's own method and counting what came back,
    rather than by restating its arithmetic (FR-14). A measured entry names the method, cites
    its declaration and carries the samples behind it, and draws under a measurement seed of its
    own so that moving the Codex's construction seed moves nothing measured. A method the
    generator cannot run, because reaching it needs the toolkit, is named with that reason
    instead of guessed at.

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
