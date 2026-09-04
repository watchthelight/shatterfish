# Rubric review — ARCHITECTURE-SPINE.md (Shatterfish)

**Reviewed:** `_bmad-output/planning-artifacts/architecture/architecture-shatterfish-2026-09-03/ARCHITECTURE-SPINE.md`
(status: draft, 2026-09-03) and its companions `docs/adr/0003`, `0005` through `0013`.
**Against:** `CLAUDE.md` non-negotiables 1 to 8; `prd.md` §4 (FR-1 to FR-53) and §8 (NFR-1 to NFR-9);
the working tree at `v3.3.8`; the repository's own build files.
**Reviewer:** rubric walker, 2026-09-03.

---

## Overall verdict

**Adequate with two blockers.** The spine is unusually strong for its altitude — twelve architecture
decisions that each name what they prevent, nine companion ADRs with rejected options and
pre-mortems, and a paradigm (ports-and-adapters around one immutable Observation) that genuinely
makes the first non-negotiable structural rather than aspirational. It ratifies the brownfield
tree accurately: every `path:line` sampled resolves and supports its claim, and every named version
is confirmable from `build.gradle`, `shatterfish/java-module.gradle`,
`gradle/wrapper/gradle-wrapper.properties` and `docs/requirements.txt`. But two contracts that four
of the five epics all touch are missing entirely — the **Action** vocabulary (the spine's own
paradigm names `Action` as one of two ports and never says what one *is*) and the **ownership of the
headless render loop** (who runs `Gdx.app`'s posted runnables, without which no Prompt can appear).
Two stories written independently from this spine will choose incompatibly on both. Everything else
is high or below and can be fixed in the epics workflow or in a spine revision alongside the story
specs.

## Counts by severity

| Severity | Count |
|---|---|
| Critical | 2 |
| High | 10 |
| Medium | 10 |
| Low | 5 |
| **Total** | **27** |

---

## 1. Does it fix the real divergence points for E1 to E5, and miss none?

**Judgment: adequate, with two critical gaps.** The spine fixes the divergence points that a
reviewer would expect it to miss: the unit of work (AD-5's Input wait as the primary key `k` across
Observation, Decision, Action, Run-log record and RNG reseed) is the single best decision in the
document, because it is the join key every module would otherwise have invented separately. Module
edges (AD-1), the encoder-and-hash monopoly (AD-2), the thread roles (AD-8), the hook shape (AD-10)
and the Registration-plus-log discipline (AD-11) are all real cross-story invariants, decided once,
each with a named enforcement. But the spine's own paradigm sentence names two ports — `Observer`
and `ActionExecutor` — and gives the first an entire ADR (0005 for the value, 0006 for the reading
rules) while the second gets four lines in AD-4 that describe *dispatch mechanics* and never define
the value. `Action` is an `api` type consumed by `harness` (execute + validate), `api` (the
`actions` section), `brain` (Decision), `rig` (Replay comparison, canonical JSON) and `overlay`
(human Action recording, `unsupported` records). Five modules, four epics, no schema. The second
gap is narrower but harder: the spine says the headless driver thread "drives `scene.update(dt)`
with a large `dt`" and never says what owns `Gdx.app`'s runnable queue, which is where every quest,
shop and trade window in the game is delivered from (`SPD-classes/.../noosa/Game.java:306`,
`docs/rules/game-loop.md` row 22). Beyond those two, the misses are single-contract (Belief hashing,
Codex data path, Run id) rather than structural.

### Findings

- **C-1 (critical) — The Action vocabulary is undefined.** Nothing in the spine or any companion
  ADR states what an `Action` is: its kinds, its parameters, how a targeted use carries a cell or a
  bag item, how `ActionExecutor` answers a `CellSelector` prompt or a `WndBag` that the game opens
  *during* execution, how the `actions` section enumerates parameterized Actions (one entry per
  cell?), or how the set is versioned as the public surface PRD §9 says it is. ADR-0011 leaks a
  single kind name (`answer`) and ADR-0005 says "one entry per Action the ActionExecutor would
  accept now"; that is the whole specification. E1's executor story, E1's codec story, E3's Replay
  story, E4's Decision story and E5's human-Action recording story would each invent a shape.
  *Fix:* an ADR-0014 "Action grammar and the valid-Action set", written before the E1 story specs,
  fixing kinds, parameters, canonical encoding (it falls out of ADR-0005's codec for the
  Observation but not for the Run log's "canonical Action"), the fixed point between
  `validActions(observation)` and `execute`, and mid-execution prompt handling.

- **C-2 (critical) — Ownership of the headless main loop and `Gdx.app.postRunnable` is
  undecided.** `harness/build.gradle` declares `gdx-backend-headless`, so a `HeadlessApplication`
  exists and runs its own loop thread that drains posted runnables and calls the listener; the
  spine simultaneously says the *driver* thread is the UI-role thread and drives `scene.update(dt)`
  itself. Both cannot be true without either a second thread touching the scene (breaking AD-8 and
  the determinism argument of AD-6) or a driver that never drains the queue (in which case
  `Game.runOnRenderThread` callbacks never fire and quest, shop, trade, subclass and resurrect
  windows never appear — so AD-5's "recognised Prompt window" branch is dead code headless and the
  Overlay and headless paths diverge in behavior, not just in timing). `docs/rules/game-loop.md`
  records the mechanism and leaves it open ("Not confirmed": `WndResurrect`/`WndGame` interaction
  was not traced). E1's boot story and E1's driver story choose this independently today.
  *Fix:* decide it in the spine (AD-8 or a new AD): either the Harness owns the loop and explicitly
  pumps `Gdx.app.postRunnable` at a stated point in the per-wait sequence, or `HeadlessApplication`
  owns it and the driver runs *as* that thread. Add the pump step to ADR-0013's per-wait sequence.

- **H-1 (high) — The Run id collides for a pair.** The conventions table fixes
  `Run id = <tag>-<class>-<challenges>-<seedcode>-<salt>` and ADR-0011 names the log file
  `<run-id>.jsonl.gz` in one `--out` directory. ADR-0012's Per-pair statistic runs **both Brains on
  the same (seed, salt) pair** — so the two Runs of every pair share a Run id and a filename. The
  Brain (name/commit/config hash) is a header field but not part of the identity. E3's runner story
  and E3's results story would patch this differently (suffix, subdirectory, hash), and the Run id
  is a published surface that Results pages cite.

- **H-6 (high) — Alchemy is classified as a Prompt window but is a scene switch.** ADR-0006's
  Prompt row and EXPERIENCE.md's stepping model both list "alchemy" among the Prompt kinds; the
  pinned code makes `actAlchemy` call `ready()` then `switchScene(AlchemyScene.class)`
  (`docs/rules/game-loop.md`, row 24, Tier 1). AD-5's Input-wait definition ("no `Window` or a
  recognised Prompt window") therefore has no branch for the state the hero is actually in, and
  `AlchemyScene` appears nowhere in the spine. ADR-0013 handles `InterlevelScene` (re-attach through
  the scene seam, keep `k`) and by omission implies alchemy is a window. An E1 story implementing
  Prompts and an E5 story implementing the Panel's Prompt display would resolve this in opposite
  directions (treat the scene as a Prompt vs forbid the Action entirely).

- **H-7 (high) — The Input-wait hook site is ambiguous, and its citation is mislabeled.**
  ADR-0013 says the notification "fires from `Hero.ready()` on the actor thread the first time the
  hero becomes ready (the branch that calls `Dungeon.observe()`,
  `…/actors/hero/Hero.java:935-946`)". Those two descriptions are different sites: `:935-946` is
  `ready()`, which calls `GameScene.ready()` and no `observe()`, and which fires on **every** actor
  wake-up (~60/s) and from ~20 call sites including `actBuy` at `:1023` immediately before a shop
  window is posted; the `!ready` branch that calls `Dungeon.observe()` is in `act()` at
  `…/actors/hero/Hero.java:839-846` and fires once per transition into readiness. The pre-mortem's
  mitigation ("the hook fires only on the `!ready` branch") points at the second, ADR-0008's hook
  row 5 at the first. This is the trigger for the unit of everything; two E1 stories will not pick
  the same line. The same paragraph hides a second hazard C-2 makes worse: `ready()` is set true
  *before* the shop/quest window is posted, so an Observation can be taken in the window between
  readiness and the window's appearance — the pre-mortem only considers the reverse (a Prompt
  closing).

- **H-10 (high) — A Run that crashes, hangs or hits the turn cap has no defined outcome.** The PRD
  sets a 20,000-turn cap (Glossary) and the Rig is process-per-Run over thousands of Runs; nothing
  in AD-11, ADR-0011 or ADR-0012 says what the `end` record's `outcome` is for a capped Run, what
  the runner writes when a process dies mid-Run (the log has a readable prefix and no `end`), or how
  `PairScore` scores a pair with one missing or invalid Run (drop the pair? score 0.5? abort the
  comparison?). That last question is a statistics question with a bias attached, and it lands in a
  different story (runner) from the one that owns it (Gsprt).

---

## 2. Is every AD's Rule enforceable, and does it prevent its stated divergence?

**Judgment: mostly strong, with three Rules that are wishes.** The best Rules in this spine name
their check: AD-1 (three independent enforcements — declared edges, a resolution-time check that
already exists in `shatterfish/brain/build.gradle`, an ArchUnit rule that already exists as
`BrainImportsNoGameCodeTest`, plus the new io/nio/net/reflect rule, which is exactly the right
answer to ADR-0007's "a Brain could read the salt off disk" pre-mortem), AD-10 (a marker-count test
against the `docs/UPSTREAM.md` table, including the budget), AD-11 (a Registration whose commit
predates the first Run is mechanically checkable), AD-6 (a two-JVM determinism test), AD-8 (thread
assertions on entry to every public method) and AD-5 (one record per `k` is a test over a Run log).
Those genuinely prevent what they claim. Three do not. AD-3's Rule is the load-bearing one and the
weakest: "the per-rule table of ADR-0006 is the whitelist" is enforced by "a leak test per row",
which catches only leaks someone already thought of — nothing mechanically constrains *which* game
fields the Observer reads, so a new field added in a hurry (or after an upstream merge) leaks by
default, which is precisely the failure mode ADR-0005 option 4 and ADR-0006 option 5 were rejected
for. AD-7's purity claims ("the Brain holds no thread", "state lives only in the returned Belief")
have no check at all, though both are cheaply checkable with ArchUnit. AD-12's "it reads only
Observations and Decisions" is a wish in the one module (`overlay`) that can see both the game and
the Panel.

### Findings

- **H-5 (high) — Three Rules assert enforcement they do not have.** (a) AD-3: no check restricts
  the Observer's reads to the ADR-0006 table; the enforcement is per-row leak tests plus review, so
  the whitelist is a whitelist only by discipline. Recommended: an ArchUnit rule limiting `Observer`
  to an allowlist of game classes (it will not catch field-level over-reads but caps the surface),
  plus a required "new row or new leak test" checklist item in the `fairness` PR label path — and
  say in AD-3 that the Rule is review-enforced, so the epics do not believe otherwise. (b) AD-7:
  add ArchUnit rules to `brain` for no non-final static fields, no `java.lang.Thread`/
  `java.util.concurrent`, and a determinism test that calls `decide` twice on the same
  (Observation, Belief, seeded generator) and compares. (c) AD-12: an ArchUnit rule that Panel
  classes reference no `com.shatteredpixel..` *model* package (they must reference the toolkit), or
  demote the sentence to a review rule.

- **M-1 (medium) — AD-2's field-level parity Rule is judgment, not a check.** "Every field is
  something the screen, HUD, log or journal shows" cannot be tested; the enforceable parts (no
  secret enum members, one encoder, canonical list order, version bump on encoding change, the
  reflection test that every record component is encoded) are stated in ADR-0005 and are good. Say
  which half is mechanical so a story does not claim the whole Rule is covered by the codec test.

- **M-2 (medium) — AD-11's "the runner refuses `holdout` for development" has no mechanism.**
  Nothing tells the runner whether an invocation is development or a release claim. FR-20 allows
  `holdout` "at most once per Brain version" for a release-level number or SM-1; FR-22's ledger is
  the natural place to enforce it, but the spine does not connect them. Two E3 stories (seed sets,
  pre-registration) would each implement half.

- **L-1 (low) — AD-5's "one RNG reseed per Input wait" is enforceable only through `RngControl`.**
  ADR-0007 already commits to asserting stack depth; state that assertion in AD-5 so the invariant
  has a named check rather than a description.

---

## 3. Could anything under Deferred let two units diverge before its revisit point?

**Judgment: adequate, one row is wrong.** The Deferred table is disciplined in the way that matters:
every deferral either fixes a default that stories build against (process-per-Run for classloader
isolation; option 2's one-ply model for the search design; the GSPRT for the test) or reserves an
interface in E1 that the deferred work implements later (AD-9's `Snapshot`, `BeliefSample` and
`Redeterminer` in `api` at E1, `SnapshotStore` and the restore-and-replay test at E1, the scrubber
at E6). That is exactly the right pattern, and it means E6 cannot silently change what E1 built.
The GSPRT/e-process deferral is likewise safe because ADR-0012 fixes the Registration's field list
now. One row is simply mistaken about its own blast radius: "Codex generation mechanics ... no
cross-module divergence risk" is false, because the Codex's *consumption* shape is a contract
between `codex` (E2), `brain` (E4) and both of the Brain's hosts, and no module edge or `api` type
exists for it.

### Findings

- **H-2 (high) — The Codex-to-Brain data path is unspecified and its deferral claims no risk.**
  AD-1 says "the Codex reaches the Brain as JSON data loaded by the caller through `api` types,
  never as classes"; the Layer table and Structural Seed list no Codex types in `api`, and the
  declared edges give `codex` a dependency on `core` only — so the module that *generates* the
  tables cannot produce the `api` types the Brain must receive, and each host (`rig`, `overlay`)
  would write its own loader and its own in-memory shape. Neither the Codex JSON's schema nor its
  versioning is fixed (only "tag-named folders"). E4's Evaluation (FR-33) and Beliefs (FR-29, Codex
  spawn weights) both depend on this. *Fix:* declare the Codex value types and a `CodexTables`
  loader boundary in `api` (data records, no io in `brain`), fix a schema version, and correct the
  Deferred row.

- **M-3 (medium) — Overlay snapshot cadence is decided twice.** ADR-0013's per-wait sequence takes
  a `snapshot` every Input wait in the Overlay; ADR-0009's pre-mortem says take it "only on Take
  over and Pause" if it stutters. Both are defensible; pick one so the E5 driver story and the E5
  takeover story agree.

- **L-2 (low) — The e-process/GSPRT deferral has no schema slot.** ADR-0012 lets the e-process
  replace the GSPRT after calibration, but the Registration's fixed field list has no "test kind"
  field and `sprt.json` has no discriminator. Add one now; it costs a line.

---

## 4. Is the named technology verified-current and does it fit?

**Judgment: strong — every claim in the Stack table is confirmed by the repository's own build
files, with one forward-looking item that cannot be.** Checked line by line:

| Spine claim | Confirmed in | Verdict |
|---|---|---|
| Java 21 for Shatterfish modules | `shatterfish/java-module.gradle` (`VERSION_21`, plus `-Xlint:all -Werror`) | confirmed |
| upstream compiles for 11 | `build.gradle` `appJavaCompatibility = JavaVersion.VERSION_11` | confirmed |
| Gradle 9.4 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` `gradle-9.4.0-bin.zip` | confirmed |
| libGDX 1.14.0 | `build.gradle` `gdxVersion = '1.14.0'` | confirmed |
| `gdx-backend-headless` + `gdx-platform:natives-desktop` for the Harness | `shatterfish/harness/build.gradle` (`implementation` + `runtimeOnly`, with the reason in a comment) | confirmed |
| JUnit 5.11.4 | `shatterfish/java-module.gradle` `junitVersion` + `junit-bom` | confirmed |
| ArchUnit 1.3.0 | `shatterfish/java-module.gradle` `archunitVersion` | confirmed |
| MkDocs Material "per `docs/requirements.txt`" | `docs/requirements.txt` (`mkdocs-material==9.7.7`, `mkdocs==1.6.1`, uv-compiled) | confirmed; the spine wisely names no version |
| hand-ported GSPRT, no statistics library | no such dependency anywhere | confirmed |

The fit is right for the constraints: no dependency in `api` (ADR-0005 rejects Jackson, protobuf and
Java serialization for the correct reasons — canonical bytes across JVMs and ADR-0003's "api depends
on nothing", which `ApiDependsOnNothingTest` already enforces); JUnit 5 and ArchUnit on every module
through one shared script; `java.util.logging` rather than a logging dependency. The one thing I
cannot confirm from the repository is the planned bump.

### Findings

- **L-3 (low) — "bump to 1.5.0 in E1 after a web check" is unverifiable here, and the PRD already
  names 1.5.** PRD §11 lists "ArchUnit 1.5" as the dependency; the spine says 1.3.0 with a planned
  bump. Neither is wrong, but the E1 story should close the loop and the PRD line should be
  reconciled rather than left as two numbers in two artifacts. No repository evidence exists for
  1.5.0's release status; treat the version as unpinned until the E1 check.

- **L-4 (low) — The Android Gradle plugin (9.1.0, root `build.gradle` buildscript) is unmentioned.**
  Harmless because `shatterfish/settings.gradle` excludes `android`/`ios` unless `-Pshatterfish.mobile=on`,
  and CI passes `off` explicitly, but the Stack table is the place a reader checks what the build
  resolves; one row would remove the question.

---

## 5. Does it ratify the brownfield codebase?

**Judgment: strong.** This is the spine's best dimension, and it is what non-negotiable 8 asks for.
Three citations sampled at random, all read at the working tree (= `v3.3.8`):

1. **ADR-0006, cell visibility, `…/tiles/FogOfWar.java:288-299`.** Resolves to `getCellFog(int)`,
   which returns `VISIBLE` / `VISITED` / `MAPPED` / `INVISIBLE` in exactly that precedence — the
   ADR's `Fog` enum and its ordering are the renderer's own. The companion cite `:200-208` resolves
   to the `!discoverable[cell] || (!visible && !visited && !mapped)` skip, which is the ADR's
   "non-discoverable cells are `UNKNOWN`". **Supports the claim.**
2. **ADR-0006, mobs, `…/scenes/GameScene.java:1441-1448`.** Resolves to `afterObserve()`, whose
   general branch is `mob.sprite.visible = Dungeon.level.heroFOV[mob.pos]` — the ADR's "a char is
   present iff `heroFOV[ch.pos]`" — and whose exception is the passive stealthy Mimic. **Supports
   the claim**, with a nuance the ADR under-specifies (see M-4).
3. **ADR-0007, `…/Dungeon.java:242-254`.** Resolves exactly: `Random.pushGenerator(seed+1)`, the
   label/color/gem and room initialisation, `Generator.fullReset()`, then `Random.resetGenerators()`
   at `:254`. The ADR's whole seeding argument (option 2 rejected because `init` discards the stack)
   stands on this line and the line is there. **Supports the claim.**

Two further spot checks (`GameScene.java:826-828` and `:865-888` for the "SHPD Actor Thread" and its
60 Hz notify; `WndHero.java:188-211` for the seed on the hero window) also resolve and support their
claims — and the second one incidentally confirms that the game *does* draw the seed for a seeded
Run (`custom_seed`), so ADR-0005's exclusion of it is a deliberate over-restriction, correctly
argued (FR-9, seed fingerprinting) rather than a parity error. The only citation defect found is the
mislabeled one in ADR-0013 (H-7 above). Nothing in the spine contradicts the tree.

### Findings

- **M-4 (medium) — The mimic-as-chest presence predicate is under-specified.** ADR-0006 says a
  neutral passive mimic "is emitted here as a CHEST at its cell" but gives it no fog predicate,
  while the game keeps such a sprite visible **only** when `((Mimic) mob).stealthy()` and
  `Dungeon.level.visited[mob.pos]` (`…/scenes/GameScene.java:1441-1448`), and a non-stealthy
  neutral mimic follows `heroFOV` like any char. The Observer story and the "stealthy mimic equals a
  chest" differential test are different stories and would pick different predicates. State the
  predicate in the row.

- **L-5 (low) — `docs/rules/game-loop.md` "Not confirmed" items feed two ADRs.** The unresolved
  `WndResurrect`/`WndGame` tracing and the `CharSprite.move()` null-parent NPE risk both bear on
  AD-5's "any other window is an assertion failure" and on the headless scene. Name them as E1 story
  inputs so they are not rediscovered mid-implementation.

---

## 6. Does it cover the PRD's capabilities?

**Judgment: adequate, with a hole at the bottom of §8.** The Capability → Architecture Map covers
every FR group in §4 and lands each in a module with governing ADs; the mapping is honest (4.3 is
marked "AD-1 (data only), conventions", which is a fair admission that the Codex is thinly
governed). Coverage of the FRs themselves is good: FR-1 to FR-6 (AD-2 to AD-6, AD-9), FR-7 to FR-13
(AD-1, AD-3, AD-8, AD-9 plus the two ArchUnit rules and the classpath check), FR-19 to FR-26
(AD-6, AD-11 plus ADR-0011 and ADR-0012, which between them decide format, chain, statistic and
Registration), FR-27 to FR-36 (AD-7, AD-9, ADR-0010's choice rule), FR-37 to FR-47 (AD-4, AD-8,
AD-10, AD-12 plus ADR-0013's state and thread detail). The NFRs are where it thins: the frontmatter
binds `NFR-1..NFR-6` and the PRD has nine. NFR-7 (Windows and Linux, macOS best effort) is arguably
covered in spirit by ADR-0005's cross-JVM canonical encoding and ADR-0002's nightly Windows build,
but nothing in the spine says so; NFR-9 (everything a person can read without tooling) is satisfied
by ADR-0011's JSONL and Markdown Results; NFR-8 (no network calls at runtime, no telemetry) is
governed by nothing at all, in a codebase whose upstream ships `services/` and update checks.

### Findings

- **H-8 (high) — NFR-7, NFR-8 and NFR-9 are unbound, and NFR-8 has no convention or check.**
  The spine's `binds` line stops at NFR-6. NFR-8 is the one that needs a Rule rather than a
  sentence: AD-1 bans `java.net` in `brain` only, so nothing prevents `harness`, `rig` or `overlay`
  from reaching the network (upstream's own `services` module and its news/update fetches are on the
  `overlay`'s classpath through `core`). Add a convention row plus an ArchUnit rule ("no Shatterfish
  module uses `java.net`/`java.net.http`; the standard Profile disables upstream's update and news
  checks"), and bind NFR-7 and NFR-9 to the ADs that already satisfy them.

- **H-9 (high) — The Config convention contradicts the PRD and FR-11 on `--oracle`.** The
  conventions table reads "Launcher flags and Rig CLI (`--brain --baseline --seeds --parallel --out
  --oracle`)", which puts `--oracle` in the Rig's flag list; PRD §9 says of the Rig command line
  "Oracle mode cannot be enabled through it", FR-11 makes that safety-class, and AD-11's own Rule
  says the runner refuses any Oracle Run. The list also omits `--seed-start`, which PRD §9 names as
  part of the contract. A story that implements the conventions table literally builds a fairness
  violation. Split the two flag sets and restate the refusal.

- **M-5 (medium) — The Codex's own guarantees have no AD.** FR-14's seed-free property ("generating
  it twice with different seeds and different Profiles produces byte-identical output") and its leak
  test are named in NFR-1 as PR-gate tests, and the Capability map assigns 4.3 no AD. Determinism of
  a generator that boots the game is not trivial (it is AD-6's problem in a different module) and
  deserves at least a sentence: which module runs it, in what Profile, under what seeding.

- **M-6 (medium) — FR-16's second pinned source has no home.** The vocabulary diff needs vanilla
  Pixel Dungeon at a pinned tag (PRD §11: `00-Evan/pixel-dungeon-gradle`, tag chosen in E2). The
  spine's Structural Seed and module table have no place for it and say nothing about how it is
  obtained (submodule, vendored copy, read-only checkout, generated fixture) — an integration
  dimension the initiative altitude owns. Two E2 stories would choose differently, and one of the
  choices (a submodule) changes the build and CI.

- **M-7 (medium) — NFR-3 is bound but ungoverned.** No AD or convention says where the E1 benchmark
  lives, what it measures (the codec time and gzip cost that ADR-0005 and ADR-0011 each promise to
  report "in the E1 benchmark"), or where its numbers are published. Two ADRs defer to a benchmark
  the spine never places.

- **L-6 (low) — PRD FR-38 points at a "spine's Layout section" that does not exist.** The sizes
  live in `DESIGN.md` "Layout & Spacing" and AD-12 points there; the PRD sentence should be
  corrected so an E5 story does not hunt for a section in the wrong document.

---

## 7. Is every dimension the initiative altitude owns decided, deferred, or an open question?

**Judgment: adequate for the operational envelope, thin for error handling and testing strategy.**
Where things run is decided (one process hosts one Run, each with its own working directory and
fresh versioned Profile; `--parallel` sets concurrency; the Overlay in the desktop JVM), and where
the remaining question sits is honestly deferred with a revisit point (the `standard` host, E3, PRD
open question 11). Environments and CI are decided outside the spine but correctly cited: ADR-0002
fixes the PR gate (`build` and `docs` on ubuntu/JDK 21), the nightly `smoke` rig plus a Windows
build, and the results-PR mechanism on branch `rig/nightly`. Publication is decided (AD-11 plus
ADR-0002: `docs/results/<date>-<sha>.md`, reviewed, tied to a SHA). Storage of Run logs is decided
for the *file* (ADR-0011: `<run-id>.jsonl.gz` under `--out` or the Overlay Profile) but not for the
*archive*. The upgrade procedure is decided (FR-50's nine steps, ADR-0008's re-verification, the
citation checker as the drift detector) — a genuinely good answer to non-negotiable 3. Configuration
is decided and pleasingly small ("no config files besides Seed sets and Registrations, which are
committed"), with the flag-list defect at H-9. Logging is decided at one line. Error handling is
decided for the case the PRD asked about (the Brain throwing) and undecided everywhere else.
Testing strategy is decided for the fairness suite by name and for nothing else.

### Findings

- **M-8 (medium) — Published Run logs have no durable home.** NFR-2 and UJ-3 require a third party
  to verify a published Run from its Run log; ADR-0002 attaches nightly JSONL logs as workflow
  artifacts, which expire (ADR-0002 says so: "artifacts are retained 90 days at most"), and FR-25
  requires Results pages to carry "links to the Run logs". After 90 days the links rot and the
  skeptic's journey fails. Nobody has decided whether logs are committed (size?), attached to a
  release, kept only for `standard`/`holdout` claims, or reconstructed by Replay from the tuple in
  the Results page. This is the operational dimension the initiative owns and it interacts with the
  deferred "where `standard` runs" row.

- **M-9 (medium) — Error handling outside the Brain is undecided.** The conventions cover a Brain
  exception and an invalid Action. Undecided: what the driver does when the Observer's AD-5
  assertion fails mid-Run (crash the Run? log and abort? write `end` with a cause?); what happens on
  a boot failure with missing natives (FR-1 wants a named message — no owner); what "no valid
  action" means headless (EXPERIENCE.md's three-strikes-then-PAUSED is an Overlay rule with no
  headless counterpart); and how a Run reaching the 20,000-turn cap terminates. Pairs with H-10.

- **M-10 (medium) — Testing strategy is named only for the fairness suite.** The Structural Seed
  lists `harness/src/test` with its five suites, and the conventions fix test-class suffixes. Not
  decided: where the ArchUnit rules for `api`/`brain` live relative to the modules they constrain
  (the tree already answers this — `ApiDependsOnNothingTest`, `BrainImportsNoGameCodeTest` — but the
  spine does not ratify it), which suites are PR-gate vs nightly beyond ADR-0002's table, whether
  `rig`, `codex` and `overlay` have required test kinds at all (FR-38 wants a scripted test per
  Panel state; nothing places it), and what a story's Definition of Done requires in tests. NFR-1's
  list of PR-gate tests is the de-facto answer and should be lifted into the spine as a convention
  row.

- **M-11 (medium) — Seed set and Registration file formats are unspecified.** Both are committed
  configuration and public surfaces (PRD §9: "Seed set files versioned by content"), and the spine
  fixes neither the format nor how the version string is derived from content. E3's seed-set story
  and E3's results story both need it.

- **L-7 (low) — The logging convention is one line.** `java.util.logging` with the module as logger
  name says nothing about levels, destination, or the one rule that actually matters in a
  process-per-Run runner: Shatterfish logging must never be interleaved into the Run log stream or
  the runner's stdout protocol. One more sentence closes it.

---

## Cross-cutting findings not owned by a single rubric item

- **H-3 (high) — The Belief's canonical serialization and hash are undefined.** AD-7 makes the
  Belief "an `api` value whose hash is in the Run log"; the Layer table lists `Belief` among "api …
  interfaces"; ADR-0011's `wait` record carries "`belief` (SHA-256 of the serialized Belief; the
  full Belief only with `--log-beliefs`)". If `Belief` is an interface implemented in `brain`, then
  `api`'s codec cannot encode it, `brain` cannot write it (AD-1 bans `java.io`), and no component
  owns the canonical bytes. The epic order makes this concrete: E3 writes the Run-log record before
  E4 creates the first Belief. Decide whether `Belief` is a closed `api` record tree (encodable by
  a codec beside `ObservationCodec`) or an opaque `brain` value with an `api`-declared
  `canonicalBytes()` contract — and say what E3 writes in the field meanwhile.

- **H-4 (high) — `k` is inside the hashed Observation header, which contradicts two other rules.**
  ADR-0005 puts "Input wait index `k`" in `header`; AD-2 says "neither the seed, the salt, a turn
  counter nor any oracle data is a field of the Observation"; ADR-0006's "Seed and turn" row says
  the game draws no turn counter and "the Brain counts Input waits itself". `k` *is* a turn counter
  by another name, it is information no screen shows (confirmed: `WndHero` draws the seed, gold and
  deepest floor, and no turn count), and putting it in the hash has two side effects: FR-9's
  differential test and FR-2's determinism test still work (both hold `k` fixed), but ADR-0013's
  wake-up guard — "the UI-role thread also checks that `k`'s Observation hash changed" — becomes
  vacuous, because a header carrying `k` guarantees the hash changes every wait. Decide: either `k`
  leaves the Observation (and the Run log keys it, as it already does), or AD-2 and ADR-0006 are
  amended to say why a wait index is fair. The leak-test story and the codec story will otherwise
  assert opposite things.

- **M-12 (medium) — The Run tuple is stated three ways.** AD-6: `(tag, class, challenges, seed,
  salt, Action list)`. PRD FR-2 and NFR-2: the same without the salt. ADR-0007: "the Profile has a
  version that is part of the Run tuple", and ADR-0009's `Snapshot` and ADR-0011's header both carry
  `profileVersion` as if it were. The spine's version is the right one and its reasoning
  (ADR-0007 option 4's red-team pass: a seed-derived stream is predictable from the HUD) is the
  strongest argument in the companion set — but the PRD is not amended, so a story whose acceptance
  test is copied from FR-2 tests the wrong tuple. Amend the PRD in the same pass and pick one
  canonical sentence for the tuple, including or excluding the Profile version.

- **L-8 (low) — `header` carries the open Prompt's kind and there is also a `prompt` section.**
  Harmless duplication in ADR-0005, but it is exactly the kind of thing two encoders disagree about;
  say which is authoritative.

---

## Findings index

| ID | Severity | Rubric item | One line |
|---|---|---|---|
| C-1 | critical | 1 | The `Action` type — kinds, parameters, targeting, canonical form, versioning — is defined nowhere, though five modules and four epics consume it. |
| C-2 | critical | 1 | Ownership of the headless main loop and of `Gdx.app.postRunnable` is undecided, so Prompt delivery and AD-8's thread invariant are both unresolved. |
| H-1 | high | 1 | `Run id` omits the Brain, so the two Runs of a Per-pair comparison collide on id and log filename. |
| H-2 | high | 3, 6 | The Codex-to-Brain data path has no `api` type, no module edge and no schema version; its Deferred row wrongly claims no cross-module risk. |
| H-3 | high | 1, 2 | The Belief's canonical serialization and hash are undefined, yet the Run log records a Belief hash from E3. |
| H-4 | high | 1, 2 | `k` sits in the hashed Observation header, contradicting AD-2's "no turn counter" and ADR-0006's "the Brain counts Input waits itself". |
| H-5 | high | 2 | AD-3's whitelist, AD-7's purity and AD-12's "reads only Observations" are review discipline presented as enforced Rules. |
| H-6 | high | 1, 5 | Alchemy is listed as a Prompt window but is a scene switch at the tag; `AlchemyScene` is absent from AD-5. |
| H-7 | high | 1, 5 | The Input-wait hook site is ambiguous between `Hero.ready()` and `act()`'s `!ready` branch, and ADR-0013's citation labels the wrong lines. |
| H-8 | high | 6 | The spine binds only NFR-1 to NFR-6; NFR-8 (no network, no telemetry) has no convention and no check in any module but `brain`. |
| H-9 | high | 6, 7 | The Config convention lists `--oracle` on the Rig CLI, contradicting PRD §9, FR-11 and AD-11's own refusal; `--seed-start` is missing. |
| H-10 | high | 1, 7 | Crashed, hung and turn-capped Runs have no defined outcome, and `PairScore` has no rule for a pair with a missing Run. |
| M-1 | medium | 2 | AD-2's "every field is something the screen shows" is judgment; the mechanical half should be named. |
| M-2 | medium | 2 | "The runner refuses `holdout` for development" has no mechanism distinguishing development from a release claim. |
| M-3 | medium | 3 | Overlay snapshot cadence is per-wait in ADR-0013 and on-demand in ADR-0009. |
| M-4 | medium | 5 | The mimic-as-chest row omits the `stealthy()` + `visited[pos]` fog predicate the game actually uses. |
| M-5 | medium | 6 | The Codex's seed-free guarantee and leak test are PR-gate requirements with no governing AD. |
| M-6 | medium | 6 | FR-16's second pinned source (vanilla Pixel Dungeon) has no place in the structure and no acquisition mechanism. |
| M-7 | medium | 6 | NFR-3's E1 benchmark is deferred to by two ADRs and placed by none. |
| M-8 | medium | 7 | Published Run logs have no durable storage decision; workflow artifacts expire at 90 days while Results pages link to them. |
| M-9 | medium | 7 | Error handling outside the Brain (assertion failure, boot failure, no-valid-action headless, turn cap) is undecided. |
| M-10 | medium | 7 | Testing strategy is named only for the fairness suite; NFR-1's PR-gate list should be a convention row. |
| M-11 | medium | 7 | Seed set and Registration file formats and version derivation are unspecified though both are public surfaces. |
| M-12 | medium | 1 | The Run tuple is stated three different ways across AD-6, the PRD and ADR-0007/0009/0011. |
| L-1 | low | 2 | AD-5's per-wait reseed invariant should name `RngControl`'s stack-depth assertion as its check. |
| L-2 | low | 3 | No "test kind" discriminator in the Registration or `sprt.json` for the GSPRT/e-process swap. |
| L-3 | low | 4 | The ArchUnit 1.5.0 bump cannot be verified from the repository and the PRD already names 1.5. |
| L-4 | low | 4 | The Android Gradle plugin version in the root buildscript is absent from the Stack table. |
| L-5 | low | 5 | `docs/rules/game-loop.md`'s "Not confirmed" items bear on AD-5 and the headless scene; name them as E1 inputs. |
| L-6 | low | 6 | PRD FR-38 points at a "spine Layout section" that lives in `DESIGN.md`. |
| L-7 | low | 7 | The logging convention omits the one rule that matters: logging must never contaminate the Run log or the runner's stdout. |
| L-8 | low | 1 | `header`'s Prompt kind duplicates the `prompt` section with no authority stated. |

## Recommended order of repair

1. **C-1** and **C-2** before any E1 story spec is written — an ADR each (Action grammar; headless
   loop ownership and the postRunnable pump), both then referenced from AD-4 and AD-8.
2. **H-4**, **H-6**, **H-7**, **H-9** in a single spine revision — they are one-paragraph
   corrections with fairness or epic-blocking consequences.
3. **H-1**, **H-2**, **H-3**, **H-10** before E3 and E4 story specs; **H-5** and **H-8** as new
   convention rows plus ArchUnit rules in E1 (the cheapest fairness improvement available).
4. Mediums into the epics workflow as story inputs; lows into the same spine revision as 2.
