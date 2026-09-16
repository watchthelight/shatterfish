---
story: 2.1
key: 2-1-the-generator-skeleton-and-the-seed-free-guarantee
title: "The generator skeleton and the seed-free guarantee"
epic: 2
issue: 35
type: 'feature'
status: 'in-progress'
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
- [ ] `shatterfish/api/src/main/java/org/shatterfish/api/Codex.java` -- `VERSION = 1`; records `Citation(path, line)`, `Manifest(version, upstreamTag, tables)`, `HeroClassEntry(heroClass, subclasses, citation)`, `ChallengeEntry(challenge, mask, citation)`, validated with `Canon`; `CodexJson` rendering each list to canonical JSON -- the api-typed output (AD-13).
- [ ] `shatterfish/api/src/test/java/org/shatterfish/api/CodexJsonTest.java` -- golden rendering of a manifest and one entry of each table; `JsonRenderingTest.HELPERS` extended -- the encoder held.
- [ ] `shatterfish/codex/src/main/java/org/shatterfish/codex/Citations.java` -- `at(root, path, anchorRegex)` returns `Citation` for the one line matching; zero or several matches throw naming both -- no citation from memory.
- [ ] `shatterfish/codex/src/main/java/org/shatterfish/codex/Generate.java` -- `generate(root)` builds the manifest and the two tables, `main` writes them under `codex/<tag>/` with `\n` and UTF-8 in a fixed file order; the tag from `upstream.properties` -- the task's body, testable without files.
- [ ] `shatterfish/codex/build.gradle` -- `processResources` stamping, the `generate` JavaExec task with `workingDir rootDir`, `testImplementation project(':harness')`, the game assets on the test runtime classpath -- the command and the tests' Run.
- [ ] `codex/v4.0.0/manifest.json`, `hero-classes.json`, `challenges.json`; `.gitattributes` `codex/** text eol=lf` -- the committed output, stable across checkouts.
- [ ] `shatterfish/codex/src/test/java/org/shatterfish/codex/CodexSeedFreeTest.java` -- two generations under different `Dungeon.seed` and Profiles, byte-identical; the committed folder equals a fresh generation; the folder name equals the ledger's tag -- FR-14.
- [ ] `shatterfish/codex/src/test/java/org/shatterfish/codex/CodexLeakTest.java` -- cold generation versus generation at a live Run's Input wait, byte-identical; every citation resolves; the ArchUnit gate over `org.shatterfish.codex..` -- NFR-1.
- [ ] `docs/adr/0017-codex-generation-and-citations.md`, `docs/adr/index.md`, `mkdocs.yml`, `docs/architecture.md`, `docs/glossary.md` -- the decisions, the row, the terms -- NFR-6.

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
