---
story: 2.1
key: 2-1-the-generator-skeleton-and-the-seed-free-guarantee
title: "The generator skeleton and the seed-free guarantee"
epic: 2
issue: 35
type: 'feature'
status: 'review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '7a577688c'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain's general knowledge of the game (FR-14) has no home: no task writes a
Codex, nothing proves a Codex value cannot derive from a seed, a Profile or a Run, and no entry
carries the `path:line` that non-negotiable 8 demands. Every table story of E2 needs the skeleton
first.

**Approach:** One Gradle task, `:codex:generate`, writes `codex/<tag>/`: a manifest with the Codex
version and the upstream tag, and the first two tables, the hero classes with their subclasses
and the challenge flags with their masks, as `api`-typed records rendered by a canonical JSON
encoder in `api`. Every entry's citation is computed at generation by locating its declaration
in the pinned source, so a citation cannot be typed from memory and moves when the source
moves. `CodexSeedFreeTest` and `CodexLeakTest` hold the guarantee: two generations under
different seeds and Profiles are byte-identical, generation during a live Run equals generation
from a cold start, and no class of the generator can reach the Run's statics, the game's RNG or
the harness.

## Boundaries & Constraints

**Always:** `codex` depends on `core` and `api` only; the leak test's live Run comes from the
harness on the test classpath. Output is UTF-8 with `\n` line ends, lists in a stated canonical
order, no floats, no timestamps, no machine names; the same bytes on Windows and Linux. The tag
folder is `v` plus upstream's `appVersionName`, checked against the pinned tag in
`docs/UPSTREAM.md`. Every generated entry carries a citation whose file exists under the
repository root and whose line holds the anchor it was found by. The Codex version is an `api`
constant carried by the manifest; ADR-0017 records the decisions; the architecture row, the ADR
index, the navigation and the glossary change in this pull request.

**Ask First:** Filling the Observation header's `codexVersion` (a hash change for every
Observation); any hook; any dependency beyond `core` and `api` for the generator.

**Never:** No table another story owns (mobs 2.2, items and decks 2.3, guarantees 2.4, combat
2.5, traps and levels 2.6, text 2.7); no drift check in CI (2.9); no citation checker over
`docs/` (2.10); no hand-written citation string; no reading of a Codex by the Brain (E4).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Generate | `./gradlew :codex:generate` at the repository root | `codex/v4.0.0/manifest.json`, `hero-classes.json`, `challenges.json`, byte-identical on every run | A citation whose anchor is not found, or found twice, fails generation naming the file and the anchor |
| Seed-free | Two generations with `Dungeon.seed` set differently and two Profiles prepared | Identical bytes per file | N/A |
| Leak | One generation before any boot, one at an Input wait of a live Run | Identical bytes per file | N/A |
| Committed copy | `codex/v4.0.0/` in the tree | Equal to a fresh generation, byte for byte | The first differing file is named |
| Tag | `appVersionName` = `4.0.0` | Folder `v4.0.0`, equal to the tag `docs/UPSTREAM.md` pins | A mismatch fails the test naming both |
| Static gate | The generator's classes | None reaches `Dungeon`'s Run statics, `com.watabou.utils.Random`, `org.shatterfish.harness`, `java.net`, or declares `synchronized` | ArchUnit names the class and the access |

</frozen-after-approval>

## Code Map

- `core/.../actors/hero/HeroClass.java:87-92` -- the six classes with their subclasses (`HeroClass(HeroSubClass...)` at `:96`, `subClasses()` at `:275`); the first table.
- `core/.../Challenges.java:30-38` -- the nine masks as `public static final int`; `NAME_IDS` `:43`, `MASKS` `:55`; the second table.
- `core/.../Dungeon.java:185`, `:190`, `:213` -- `hero`, `depth`, `seed`: the Run statics the gate forbids.
- `shatterfish/api/.../Challenge.java`, `HeroClass.java`, `HeroSubclass.java` -- the `api` enums the entries carry (names match the game's).
- `shatterfish/api/.../JsonWriter.java` -- the canonical writer (`beginObject`, `key`, `value(String|long|int|boolean)`, no floats); `ObservationJson.java` -- the pattern for a renderer.
- `shatterfish/api/.../Canon.java:25`, `:35` -- `text`, `sorted` for record validation.
- `shatterfish/api/src/test/.../JsonRenderingTest.java` -- `HELPERS` lists every api class that is not a section; new api classes go there (story 1.20's pattern).
- `shatterfish/harness/build.gradle` -- `processResources` stamping `upstream.properties` from the root build's `appVersionName`, and the `gameAssets` classpath lines; both copied for `codex`.
- `shatterfish/harness/.../driver/HeadlessDriver.java:242` -- `start(seed, heroClass, salt)` for the leak test's live Run; `boot/Profile.java:60` -- `prepare(boot, directory)` for the seed-free test's two Profiles; `boot/HeadlessBoot.java:76` -- `ensure()`.
- `docs/UPSTREAM.md:13` -- `| Tag | \`v4.0.0\` |`, the pin the tag test reads.
- `docs/architecture.md:34` -- the `codex` row (says `core` only; the `api` edge is missing); `docs/adr/index.md` and `mkdocs.yml:127` -- where ADR-0017 is listed.
- `shatterfish/brain/src/test/.../BrainBoundaryTest.java` -- the `java.net` ban's shape, reused for the codex gate.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/src/main/java/org/shatterfish/api/Codex.java` -- `VERSION = 1`; records `Citation(path, line)`, `Manifest(version, upstreamTag, tables)`, `HeroClassEntry(heroClass, subclasses, citation)`, `ChallengeEntry(challenge, mask, citation)`, validated with `Canon`; `CodexJson` rendering each list to canonical JSON -- the api-typed output (AD-13).
- [x] `shatterfish/api/src/test/java/org/shatterfish/api/CodexJsonTest.java` -- golden rendering of a manifest and one entry of each table; `JsonRenderingTest.HELPERS` extended -- the encoder held.
- [x] `shatterfish/codex/src/main/java/org/shatterfish/codex/Citations.java` -- `at(root, path, anchorRegex)` returns `Citation` for the one line matching; zero or several matches throw naming both -- no citation from memory.
- [x] `shatterfish/codex/src/main/java/org/shatterfish/codex/Generate.java` -- `generate(root)` builds the manifest and the two tables, `main` writes them under `codex/<tag>/` with `\n` and UTF-8 in a fixed file order; the tag from `upstream.properties` -- the task's body, testable without files.
- [x] `shatterfish/codex/build.gradle` -- `processResources` stamping, the `generate` JavaExec task with `workingDir rootDir`, `testImplementation project(':harness')`, the game assets on the test runtime classpath -- the command and the tests' Run.
- [x] `codex/v4.0.0/manifest.json`, `hero-classes.json`, `challenges.json`; `.gitattributes` `codex/** text eol=lf` -- the committed output, stable across checkouts.
- [x] `shatterfish/codex/src/test/java/org/shatterfish/codex/CodexSeedFreeTest.java` -- two generations under different `Dungeon.seed` and Profiles, byte-identical; the committed folder equals a fresh generation; the folder name equals the ledger's tag -- FR-14.
- [x] `shatterfish/codex/src/test/java/org/shatterfish/codex/CodexLeakTest.java` -- cold generation versus generation at a live Run's Input wait, byte-identical; every citation resolves; the ArchUnit gate over `org.shatterfish.codex..` -- NFR-1.
- [x] `docs/adr/0017-codex-generation-and-citations.md`, `docs/adr/index.md`, `mkdocs.yml`, `docs/architecture.md`, `docs/glossary.md` -- the decisions, the row, the terms -- NFR-6.

**Acceptance Criteria:**
- Given the repository at the tag, when `./gradlew :codex:generate` runs twice, then the bytes of every file under `codex/v4.0.0/` are equal to the committed copy (`CodexSeedFreeTest`).
- Given a live Run at an Input wait, when the generator runs, then its output equals a cold generation's, and no generator class reaches the Run's statics, the RNG or the harness (`CodexLeakTest`).
- Given any entry, when its citation is opened, then the file exists under the root and the cited line holds the entry's declaration (`CodexLeakTest`).
- Given the manifest, when read, then it carries `Codex.VERSION` and the tag `docs/UPSTREAM.md` pins (`CodexSeedFreeTest`, `CodexJsonTest`).

## Spec Change Log

## Design Notes

Micro-brainstorm, recorded in ADR-0017. Citations: hand-maintained strings checked later by
2.10 (folklore until then), an AST parse of the source (a dependency the skeleton does not
need), or a regex anchor resolved against the pinned file at generation, which is chosen: the
line is read, not remembered, and an anchor that stops matching fails the task. Output: `api`
records with the existing canonical writer, chosen over a JSON library (floats, non-canonical
order) and over the binary codec (not readable). The Codex version is an `api` constant the
manifest carries; the Observation header keeps its empty field until the story that wires it,
since filling it changes every hash. Pre-mortem: a game class whose static init needs libGDX
would fail a bare generation; the two tables were probed in a bare JVM and load; a later table
that needs `Messages` decides then whether the generator boots. Line ends differ by checkout
unless declared, hence `.gitattributes`.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green, `CodexSeedFreeTest`, `CodexLeakTest`, `CodexJsonTest` among them.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.

## Dev notes

Implemented on `story/2-1-the-generator-skeleton-and-the-seed-free-guarantee` from `7a577688c`.
The generator is `org.shatterfish.codex.Generate`, run by `:codex:generate` at the repository
root with no boot; the records and their canonical text are `api`'s (`Codex`, `CodexJson`); the
citations are read from the pinned source by `Citations.at`. The harness is on the codex test
classpath only, for the leak test's live Run and the seed-free test's two Profiles. No hook, no
upstream file, no change to the fair path or to the Observation schema.

## Acceptance criteria and how each was met

- **Two generations, byte-identical, equal to the committed copy**: `CodexSeedFreeTest`, three
  tests (two seeds and two Profiles; the committed folder file by file, naming the first
  difference; the tag against the ledger's pin).
- **A live Run changes nothing, and no generator class reaches Run state**: `CodexLeakTest`,
  four tests (the static gate over the generator's classes; generation at an Input wait of a
  live Run against the committed folder; every citation opened to its declaration; a bad anchor
  refused by name).
- **Every entry cites `path:line` into the tag**: the citations are computed at generation and
  the leak test opens each.
- **api-typed JSON with a Codex version**: `Codex.Manifest` carries `Codex.VERSION`; `CodexJsonTest`
  holds the golden text and the records' refusals.

## What was built

- `api`: `Codex` (`VERSION`, `Citation`, `Manifest`, `HeroClassEntry`, `ChallengeEntry`),
  `CodexJson`; `JsonRenderingTest.HELPERS` extended; `CodexJsonTest`.
- `codex`: `Generate`, `Citations`, `Upstream`, the stamped `upstream.properties`; the build file
  with the `generate` task, the stamping, the harness on the test classpath and the game assets;
  `CodexSeedFreeTest`, `CodexLeakTest`.
- `codex/v4.0.0/manifest.json`, `challenges.json`, `hero-classes.json`; `.gitattributes`.
- ADR-0017; the ADR index and navigation; the architecture row and edge; the glossary (Codex,
  Codex version, citation); the Codex index page.

## What the story found

- **The game's tables load in a bare JVM.** `HeroClass`, `Generator.Category` and `Challenges`
  initialise with `Gdx.app` null; the generator boots nothing, and the story that adds a table
  needing the game's text decides then.
- **The canonical writer sorts keys.** The first golden strings assumed insertion order; the
  Codex's key order is the writer's, which is what makes two generations the same bytes.
- **A cold start is the task's, by construction.** JUnit runs a module's tests in one JVM, so
  "before any boot" cannot be promised inside the leak test; the committed folder, written by
  the task in a process that never booted, is the cold generation the live one is compared to.
- **The first gate held six fields and no method.** The fairness reviewer put a Run value through
  `Dungeon.isChallenged` and a Profile value through `SPDSettings.language` and the tests stayed
  green, since the live Run had no challenges and the two Profiles held the same settings. The
  gate now bans the state classes, the toolkit and libGDX whole, the api's denied list, reflection
  and file I/O outside three classes; the live Run plays under challenges in German, and the
  second Profile differs in what it holds.
- **The tag was stamped, and the generator's only `Class` use was reading the stamp.** The tag is
  now read from the root build script the way a citation is read, and `Class` is denied.
- **Nothing on floor one is a secret to the skeleton.** The two tables carry no Run-mutable
  static; the deck probabilities of story 2.3 are the first that a live Run mutates, and
  ADR-0017's pre-mortem tells that story to arrange a Run that has drawn.

## Decisions taken inside the story

- **Citations read, not remembered** (ADR-0017): an anchor per entry, resolved at generation,
  failing on zero or several matches.
- **Masks named constant by constant**, not read by reflection: a renamed constant fails to
  compile rather than to resolve.
- **The Observation header stays empty.** Filling `codexVersion` changes every hash; the story
  that wires the Run-log header (E3) or the drift check (2.9) decides it, per the spec's
  ask-first.
- **The committed-copy comparison lives in the seed-free test now**, ahead of 2.9's CI wiring:
  drift fails the build locally from day one, with the committed folder, the ledger and the
  pinned source declared as the test's inputs so a warm build reruns it.
- **The cold half is a static ban, not a forked process.** A generation from the task's own JVM
  is not compared by a test; the generator's classes cannot depend on the toolkit, so nothing in
  that process can boot, and 2.9's CI drift check runs the task itself.
- **A previous pin's folder is allowed beside the current one**, since the upgrade procedure
  keeps it until the upgrade's PR merges; the seed-free test checks the current tag's folder only.

## Evidence

- `:api:test` green, 337 tests, with `CodexJsonTest` (3); `:codex:test` green, 7 tests
  (`CodexSeedFreeTest` 3, `CodexLeakTest` 4).
- `./gradlew :codex:generate` twice: `git status --short codex/` empty after the commit.
- Mutation battery, seven mutations of the generator, the citations and the writer, each run
  against `CodexJsonTest` and the codex tests:
    - M1 an anchor matching several lines is accepted, the first taken: caught by CodexLeakTest.
    - M2 the hero class citation is a remembered line, not a read one: caught by CodexLeakTest, CodexSeedFreeTest.
    - M3 a challenge mask is read from the Run (Dungeon.challenges) rather than the constant: caught by CodexLeakTest.
    - M4 the generator draws from the game RNG: caught by CodexLeakTest.
    - M5 the manifest drops the upstream tag: caught by CodexJsonTest, CodexLeakTest, CodexSeedFreeTest.
    - M6 the files end with a carriage return and a line feed: caught by CodexJsonTest, CodexLeakTest, CodexSeedFreeTest.
    - M7 the subclasses are listed in reverse: caught by CodexLeakTest, CodexSeedFreeTest.

## Deviations

- None from the spec's tasks.

## Known limitations, handed forward

- **Two tables.** The skeleton carries the hero classes and the challenge flags; every other
  table is its story's.
- **The citation anchors are regular expressions** over one line; a declaration split across
  lines needs an anchor for its first line, and story 2.10's checker verifies `docs/` by the same
  rule.
- **The static gate names `Dungeon`'s fields one by one**; a new Run static in a later tag
  needs a row here, which the upgrade procedure's fairness re-run is where to add it.

## Follow-ups for later stories

- 2.3: the deck probabilities are Run-mutable; its leak test arranges a Run that has drawn.
- 2.9: the drift check in CI (`git diff --exit-code codex/`) and the generated index page.
- 2.10: the citation checker over `docs/`, by the rule `Citations.at` applies.
- E3: the Run-log header's `codex` field from `Codex.VERSION`; the Observation header with it.
