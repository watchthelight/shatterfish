---
title: Implementation-readiness check — epics.md
artifact: _bmad-output/planning-artifacts/epics.md
reviewed: '2026-09-04'
reviewer: readiness check (independent, documents only)
---

# Implementation-readiness check: `epics.md`

## Verdict

**NOT READY**

The artifact is unusually strong on content: every v1 functional requirement except two has a named
owner, most acceptance criteria name the test class that proves them, and the epic goals are
measurable. It is not ready to hand to an engineer because (a) three epics are sequenced so that
early stories depend on later ones in the same epic, (b) two architecture statements the stories
must obey contradict the ADRs they cite and the stories pick sides silently, (c) the module graph
does not permit E2.S1 as written, (d) the ArchUnit rules that AD-1 makes the enforcement of
non-negotiable #1 are owned by no story while a later story asserts they are green, and (e) every
governing ADR is still `proposed`. None of these is deep: the remedy is a resequencing pass, five
new stories or story-halves, and four one-line corrections to the spine and the ADR statuses.

### Counts by severity

| Severity | Count |
|---|---|
| Blocking | 8 |
| High | 32 |
| Medium | 27 |
| Low | 5 |
| **Total** | **72** |

Scope of review: `epics.md`, `prd.md` (v4) and its addendum, `ARCHITECTURE-SPINE.md` (AD-1 to
AD-14), ADR-0005 to ADR-0015, `EXPERIENCE.md`, `DESIGN.md`, `CLAUDE.md`. Repository state was
consulted only to check claims the documents make about it (`shatterfish/*/build.gradle`,
`docs/adr/index.md`, `docs/rules/`, `docs/UPSTREAM.md`, `.github/workflows/`,
`_bmad-output/implementation-artifacts/`).

---

## 1. Coverage

Requirements in scope are the PRD's section 6.1: E0 (FR-48 to FR-53, complete), E1 (FR-1 to FR-5,
FR-7 to FR-12, FR-6 reserved), E2 (FR-14 to FR-17), E3 (FR-19 to FR-25 plus the E3 half of FR-26),
E4 (FR-27 to FR-33, FR-36, the E4 halves of FR-9 and FR-26), E5 (FR-37 to FR-43 plus the E5-tagged
consequences of FR-4, FR-11, FR-12, FR-23, FR-27, FR-36).

Most rows are genuinely covered. The failures below are requirements whose stories would leave them
unmet, or stories that claim a requirement they do not deliver.

### F-1 (blocking) — FR-7's boundary rules are owned by no story, and a later story asserts they pass

AD-1 requires, beyond the package rules E0 already shipped, "an ArchUnit rule that `brain` uses no
`java.io`, `java.nio.file`, `java.net` or `java.lang.reflect`", and the spine's Network convention
requires a `java.net` ban "in every Shatterfish module". The repository's
`BrainImportsNoGameCodeTest` implements only the two package rules. No story in E1 to E5 creates the
four additional rules. E1.S2's body delivers the Hooks registry, the counting test and the ArchUnit
1.5.0 bump — nothing more — yet the FR coverage map credits it with FR-7, and E4.S1's acceptance
criteria state "the boundary rules stay green, including the ban on file, network and reflection
access", asserting a rule that does not exist. An engineer following E4.S1 would either discover the
gap mid-story or read the criterion as satisfied by an already-green build.

**Fix:** add the four rules to E1.S2's acceptance criteria (it already touches ArchUnit) or split a
small E1 story for them; correct the FR-7 row.

### F-2 (high) — FR-39's Explain control and expansion have no story

PRD FR-39 lists Explain among v1's controls ("expand the current Decision to its Policy,
alternatives, and Safety flags"); `EXPERIENCE.md` gives it a default key (F10), a Component row, a
State Patterns entry, an Information Architecture surface and Flow 1 step 4; FR-44 defers only the
*deeper* v2 expansion (Evaluation terms and Search outcomes). No E5 story mentions Explain: E5.S3
builds the Decision card without it, E5.S5's control list omits it, E5.S9's hotkey story names no
control set. Compounding this, `epics.md`'s own UX-DR15 row files "Explain expansion" under "v2
surfaces must not be precluded", which is a misreading of both the PRD and `EXPERIENCE.md`.

**Fix:** add the Explain control and the v1 expansion to E5.S3 (the card's owner) or a new E5 story;
correct UX-DR15 to say only the Evaluation-terms half is v2.

### F-3 (high) — FR-22's peeking ledger and FR-25's ledger count are unowned

FR-22 requires that "the Rig keeps a local ledger of every comparison it has run for a Brain commit
on a Seed set, and every Results page states how many prior Runs of that pair the ledger holds, so a
register-after-peeking pattern is visible". FR-25 repeats it in the Results field list ("the measured
paired-seed correlation and the ledger count of prior Runs"); the PRD addendum's Results sketch
repeats it again; SM-1 depends on it ("the Results page records the ledger count"). E3.S5 covers the
Registration but not the ledger. E3.S2 introduces a *different* ledger (holdout uses only). E3.S8's
Results-page field list — otherwise an exhaustive transcription of FR-25 — omits the ledger count.
The anti-gaming mechanism the PRD calls out by name would not ship.

### F-4 (high) — FR-11's E3 half (the Rig refuses an oracle Run) is owned by no story

E1.S13's final criterion says "the Rig's runner refuses any Run whose header has `oracle` true
(asserted in E3)". No E3 story asserts it: E3.S1 covers isolation, E3.S2 covers holdout refusal,
E3.S5 covers Registration refusal. ADR-0012 states the rule ("Any Run with `oracle` true is refused
outright") and NFR-1 makes oracle-in-a-ranked-Run a release blocker. E3.S8 only requires the Results
page to *record* that oracle was off, which is a statement, not a gate.

### F-5 (high) — Four ADR-0006 whitelist rows have no Observer story

AD-3 states "the per-rule table of ADR-0006 is the whitelist; each row cites the game line the
renderer uses **and has a leak test**". E1.S6 to E1.S8 partition the table, but four rows fall
between them and are named nowhere: **blobs** (per-visible-cell kinds, never a volume), **danger
count** (`hero.visibleEnemies()`), **level feeling** and **transitions the player has seen** (the
last two are ADR-0005 `map` section fields). E1.S6's criteria stop at terrain, fog, traps and heaps.
An engineer building the map section from E1.S6 alone would ship an Observation that fails ADR-0005's
schema and has no leak test for `Blob.cur` outside `heroFOV`.

### F-6 (high) — FR-38's state-table test is unowned

FR-38's testable consequences include "Each state in the table is reachable in a scripted test and
shows the text the table prescribes" — twelve states in `EXPERIENCE.md`. E5.S5 requires "the
enablement matrix of UX-DR7 is implemented", which is the control-enablement half only. No story
requires the reachability-and-text test, and three states (Brain error, No valid action, Hero busy)
appear in no story's criteria at all.

### F-7 (medium) — FR-27's Brain-error behaviour is unowned

FR-27's consequence "A Brain exception yields a Decision of 'wait' with the error recorded, never a
crash of the game" is restated in the spine's Errors convention and in `EXPERIENCE.md`'s Brain error
and No valid action rows (including "if it recurs three times in a row the Overlay enters PAUSED").
Neither E4.S1 nor any E5 story names it.

### F-8 (medium) — FR-29's Belief leak test is unowned

FR-29 requires "Belief update is a pure function of (previous Beliefs, Observation) **with its own
leak tests**". E4.S2 names only `BeliefConsistencyTest` (probability sums, collapse on
identification). Purity and the belief-side leak test are unowned, although NFR-1 lists the fairness
suite as a per-PR gate and the Belief hash rides in the Run log (ADR-0011).

### F-9 (medium) — FR-1's natives failure message is unowned

FR-1's third consequence, "Boot succeeds with the desktop natives shipped and fails with a message
that names the missing natives if they are absent", appears in no story. `harness/build.gradle`
already carries the `natives-desktop` runtime dependency, so this is exactly the failure a
contributor on a fresh clone hits.

### F-10 (medium) — FR-22's postdating refusal is absent from E3.S5

FR-22: the Rig "refuses a comparison whose Registration commit postdates its first Run". E3.S5
requires only that it "refuses to start a comparison with no committed Registration".

### F-11 (medium) — FR-19's per-Run result fields are enumerated in no E3.S1 criterion

FR-19's consequence lists eleven fields per Run (seed, class, flags, Brain, outcome, Score, bosses
killed, Floor, turns, final Observation hash). E3.S1's criteria cover process isolation, throughput
reporting and incomplete-Run handling. The fields exist only in ADR-0011's `end` record, which E3.S3
owns — see F-31 for the ordering consequence.

### F-12 (low) — FR-2's "runs in CI on every pull request" is not in E1.S11

E1.S12 explicitly requires its two tests to run per PR; E1.S11 does not, although the determinism
test is the more expensive one and NFR-1 lists it.

---

## 2. Story sizing

The rule (`CLAUDE.md` turn discipline; the `epics.md` overview) is one story = one working session,
taken through spec, implementation and adversarial review in a single turn. The stories below cannot
be. Split points are given.

### F-13 (high) — E1.S3 is three stories

`HeadlessScene` requires: a no-op `GL20`/`GL30` (ADR-0015 estimates "about two hundred lines of
stubs with no upstream precedent"), `Pixmap` atlas loading, recreating every Group, sprite and emote
icon `GameScene` creates, a driver loop that drives `update(dt)` and drains the `postRunnable` queue,
headless Prompt windows, plus three tests including a scene-parity test. **Split:** (a) no-op GL and
atlas loading, boot to a live scene; (b) the driver loop, the runnable queue and `HeadlessBootTest`;
(c) scene parity (sprites, emotes) and the Prompt window path.

### F-14 (high) — E1.S5 is two stories

Nine record sections, a hand-written canonical binary codec (ADR-0005: "about 400 lines"), a JSON
writer (~150 lines), plus four tests including a reflection test and a corpus equality test.
**Split:** (a) the records, `ObservationCodec`, section hashing, `CodecCanonicalTest` and
`CodecReflectionTest`; (b) `JsonWriter`, the corpus and `CodecEqualityTest`.

### F-15 (high) — E1.S9 is two or three stories

Fourteen sealed `Action` kinds, `validActions` computed from the Observation, `execute` driving the
game's own selector, rejection reasons, plus a completeness test that must enumerate *every*
hero-affecting input the game has. **Split:** (a) the `Action` type and `validActions(Observation)`
with the property test; (b) `ActionExecutor.execute` for movement, attack, interact, pick up, chest,
unlock and transitions; (c) item use with selectors, talents, abilities and prompts, plus
`ActionCompletenessTest` (itself an upstream-reading exercise).

### F-16 (high) — E1.S11 is two stories

`mix` with its published test vector, `RngControl` reseeding after `Dungeon.init`, a fresh versioned
standard Profile with its own refusal rule, the identity-order hook across six sites (`Actor.all`,
`chars`, `Level.mobs`, `Level.blobs`, `Random.chances`, `Random.element`), a two-JVM determinism
harness, and an `UPSTREAM.md` row. **Split:** (a) `mix`, `RngControl`, the identity-order hook row
and `MixTestVectorTest`; (b) the Profile, its versioning and refusal, and `DeterminismTwoJvmTest`.

### F-17 (high) — E2.S3 is the largest Codex story and needs at least two

Two category decks, every per-category deck, tier tables by depth region, the exotic swap chance,
three guarantee schedules as formulas, and per-item display name, appearance pool, value, strength
formula and actions, with completeness over *every* concrete item subclass. **Split:** (a) the
generator's decks, weights, tier tables and guarantee schedules; (b) the item catalogue and its
completeness check.

### F-18 (high) — E3.S7 is a simulation study plus a second statistical implementation

Bootstrapping outcome distributions from real Runs, simulating both procedures, reporting realized
α and β, publishing the methodology, *and* implementing `EProcess` alongside `Gsprt`. **Split:**
(a) implement `EProcess` with its own reference test; (b) the calibration simulation, the chosen
bounds and the methodology page.

### F-19 (high) — E4.S13 is a measurement campaign plus two features

400 registered Runs, a Results page with survival curve and boss staircase, the behavioural
differential test on permuted standard seeds (the E4 half of FR-9), and the death gallery's
per-Brain comparison view (the E4 half of FR-26). **Split:** (a) the behavioural differential test;
(b) the gallery comparison view; (c) the registered `goo` gate and its Results page.

### F-20 (high) — E5.S5 is two stories

Four speed modes with different timing sources, two steppers with ranges, the queue-a-press-while-
THINKING path, and the whole twelve-state enablement matrix. **Split:** (a) Next Step, Run N, Human
play speed, Fast as it can, and the steppers; (b) the enablement matrix and the THINKING queue.

### F-21 (high) — E5.S7 is two stories

Take over and Hand back, the input gate's open path, shadow Decisions in HUMAN mode (a second Brain
call per wait), human-Action recording across `Hero.curAction` / `Item.execute` / rest / search /
talent / window sites, `unsupported` records, mid-animation dimming and stale-Decision skipping.
**Split:** (a) Take over / Hand back and human-Action recording including `unsupported`; (b) shadow
Decisions, mid-animation dimming and stale-Decision handling.

### F-22 to F-29 (medium) — borderline; split if the first half runs long

- **F-22 E1.S16**: publishes throughput *and* runs the leaf-correlation and disambiguation
  measurement with random playouts by the research's recipe. The second is a research session of its
  own.
- **F-23 E2.S5**: four unrelated catalogues (traps, recipes, level structure, rooms). Split
  traps+recipes from levels+rooms.
- **F-24 E2.S6**: four dumps (strings, assets, changelog, journal documents). Split strings+assets
  from changelog+documents.
- **F-25 E3.S1**: process spawn, per-Run Profile and working directory, crash and hang detection,
  partial-log retention, throughput reporting, isolation test.
- **F-26 E3.S8**: a Results-page generator with sixteen fields, the methodology page (statistic,
  canonicalization, mix test vector, platform disagreement) and a demonstration undecided run.
- **F-27 E4.S2**: four belief subsystems (candidate sets from Codex weights, floor facts, chapter
  counters, lost-monster memory). Split candidate sets from floor knowledge.
- **F-28 E5.S2**: frame, docking, the camera offset and its re-application after `layoutTags`, the
  width targets, the collapse rules and the toolkit conformance review.
- **F-29 E5.S12**: Run-over state, the Ankh prompt path, the save boundary record and resume.

---

## 3. Dependency order

### F-30 (blocking) — E1.S4 to E1.S8 depend on E1.S9

The Observation's `actions` section is part of the schema (ADR-0005: "`actions` | the valid-Action
set (FR-3), one entry per `Action` (ADR-0014)"), and ADR-0006's Valid Actions row says it is computed
by `ActionExecutor.validActions(observation)`. So:

- **E1.S5** ("the records ... are implemented in `api`", with `CodecReflectionTest` failing "if any
  record component is not encoded") cannot be completed without the `Action` type from E1.S9.
- **E1.S6 to E1.S8** build an Observation that must carry the `actions` section; none of the three
  claims it, and the section's owner comes after them.
- **E1.S4** requires an agent that "takes a uniformly random Action from the valid set at every Input
  wait" — the Action type, the valid set and the executor, all from E1.S9 — while sitting five
  stories earlier. Its escape hatch ("explicitly allowed to use a placeholder Observation") covers
  the Observation, not the Action set. E1.S4 also needs the per-wait sequence and the 20,000-turn cap
  that E1.S10 owns.

**Fix:** move E1.S9 (or its `Action` + `validActions` half, per F-15) ahead of E1.S4, and say
explicitly which story first emits the `actions` section.

### F-31 (blocking) — E3.S1 depends on E3.S2 and E3.S3

E3.S1's command line is `--seeds <set|N>`, and Seed sets as committed versioned files are E3.S2. Its
third criterion, "a crashed or hung Run is recorded as incomplete rather than lost, **with its
partial log kept**", is the ADR-0011 log writer, which is E3.S3 — and "incomplete" is an ADR-0011
concept ("a Run that ends without an `end` record"). **Fix:** order E3.S3, E3.S2, then E3.S1, or
narrow E3.S1 to process management with a stub writer and move the incomplete-Run criterion to E3.S3.

### F-32 (blocking) — E4.S6 depends on E4.S11, and E4.S4 to E4.S10 depend on E4.S12

E4.S6's criterion is "it equips a weapon or armour **when the Evaluation prefers it**"; the
Evaluation is E4.S11, five stories later. Separately, every policy story from E4.S4 onward must emit
the Decision shape (Goal, chosen Action with a score, up to three alternatives with scores and
reasons, Safety flags, the Policy that fired), and each carries a smoke-set direction check whose
comparison presupposes scores — but the Decision output is E4.S12, after all of them. **Fix:** move
E4.S12 to immediately after E4.S1 (it is the Brain's output contract, not a late feature) and move
E4.S11 ahead of E4.S6.

### F-33 (high) — E1.S16 depends on E4

E1.S16 requires the benchmark to run "with **and without** a Brain attached" (FR-5's wording). There
is no Brain until E4.S1. Either the criterion means a stub Brain of stated cost — which the story
must say — or the with-Brain half belongs in E4.

### F-34 (high) — E2.S9 depends on E4

E2.S9's first criterion is "the index enumerates the claims **the Brain relies on**, each pointing at
a rule page row". In E2 there is no Brain and no heuristic. The citation-checker half (criteria two,
three and four) is genuinely E2 work — FR-50 depends on it. **Fix:** keep the checker and the
275-row pass in E2; move the Brain's Rules index to E4 (or E7, where FR-18's heuristic-to-Rule link
check lives).

### F-35 (high) — E1.S15 asserts an E6 component

E1.S15's last criterion: "a test asserts the rollout host refuses a handle whose scrubbed flag is
false". ADR-0009 places the rollout host ("the swap-in-place rollout host") in E6 alongside
`SnapshotScrubber`; in E1 there is nothing to refuse and no scrubbed handle to construct. **Fix:**
reduce the E1 criterion to the interface and its precondition contract, and move the host assertion
to the E6 story list.

### F-36 (high) — E3.S8 depends on E3.S9

E3.S8's last criterion is that negatives and undecided results are published on the same terms,
"which this story demonstrates with a **real** undecided run". A real undecided run needs the
baseline and the deliberately worse Brain of E3.S9 and the calibrated bounds of E3.S7. **Fix:** swap
E3.S8 and E3.S9, or move the demonstration criterion into E3.S9.

### F-37 (high) — E5.S5 specifies PAUSED before E5.S6 makes it real

E5.S5 requires "Run N advances N and lands in PAUSED" and "the Run starts in PAUSED with Next Step".
Until E5.S6's input-gate hook lands, PAUSED does not ignore hero input (UX-DR8), so the mode E5.S5
lands in is not the mode the spine defines, and a reviewer accepting E5.S5 accepts a half-state.
**Fix:** land E5.S6 before or with E5.S5, or state in E5.S5 that PAUSED is Overlay-side only until
E5.S6.

### F-38 (medium) — E2.S4 requires cross-platform reproduction before cross-platform CI exists

`CombatTableStabilityTest` must assert reproducibility "across the two supported platforms".
ADR-0002 puts the Windows job inside the nightly `rig` workflow "from E3"; the repository has only
`build.yml` (ubuntu) and `docs.yml`. E2 has no vehicle for the assertion.

### F-39 (medium) — E1.S2's `HooksVanillaTest` needs E1.S3

"`HooksVanillaTest` boots the game with no listener registered and asserts the vanilla branch runs at
every site." Booting the game in a test is the headless scene of E1.S3; the existing placeholder
`HeadlessDriver` boots the libGDX backend only, not `core`. Either the test is a `:desktop:run`
smoke check (say so) or it follows E1.S3.

### F-40 (low) — E1.S5's header carries the Codex version before E2 exists

Acceptable (a version field can be a constant), but the story should say what it holds in E1.

---

## 4. Acceptance criteria quality

The general standard is good: most criteria name a test class, and the epic overview makes that a
rule. The failures are of three kinds — wishes, missing tests, and missing Rig numbers.

### F-41 (high) — Six E4 stories carry no Rig numbers, against the epic's own rule

E4's preamble: "Every story in this epic carries Rig numbers in its pull request: a smoke-set
direction check against the previous Brain at minimum". E4.S4, S5, S6, S7, S8 and S10 comply.
**E4.S1, E4.S2, E4.S3, E4.S9, E4.S11 and E4.S12 do not.** E4.S11 is the sharpest case: it changes the
Evaluation weights, which by its own criterion "changes behaviour", and it introduces the weight-file
version into the Brain's configuration hash — precisely a change the Rig exists to measure. E4.S1
replaces the whole decision path. **Fix:** add the direction check to each, or state in the preamble
which story kinds are exempt and why (E4.S9's prompt coverage plausibly is).

### F-42 (high) — No E5 story carries Rig numbers, and E5.S11 needs them

The overview says "from E3 onward every Brain-affecting story names the Rig numbers its pull request
must carry". No E5 story does. Most are defensible (the Panel does not change Decisions), but
**E5.S11 routes emitter, music and emote draws to the base generator**, changing what the game's
random stream consumes within a turn. Its `OverlayReplayTest` covers cross-driver equality; it does
not cover whether the change moves measured outcomes. **Fix:** either state in E5's preamble that the
Overlay is not Brain-affecting and name E5.S11 as the exception with a smoke direction check, or add
the check.

### F-43 (high) — E5.S2 substitutes a review checklist for a test

"it uses only nine-patch frames, the game's text renderer and the documented sizes, **asserted by a
review checklist in the pull request**". FR-38 states the machine-checkable half ("No Swing, JavaFX,
ImGui, or web view is on the classpath") and `DESIGN.md` says "the build fails on anything else". A
checklist is not a check, and this is the one story where the non-negotiable #6 promise is enforced.
**Fix:** require a classpath/ArchUnit test banning `javax.swing`, `javafx`, `imgui` and web-view
packages in `overlay`, plus a test that the Panel's components come from the toolkit types; keep the
checklist for the visual half only.

### F-44 (high) — E1.S3's parity test states no basis of comparison

"`HeadlessSceneParityTest` asserts the scene creates the same sprite and emote objects the real scene
does". The real scene cannot be instantiated in the same test without a GL context — that is the
problem the story exists to solve. ADR-0015 states the actual purpose: a headless scene "that skipped
them would consume a different number of draws than an Overlay Run". **Fix:** restate as an assertion
over the count and order of base-generator draws consumed during scene construction, or over an
enumerated list of expected constructions.

### F-45 (high) — E3.S7's decision rule turns on an undeclared quantity

"the e-process ... replaces the sequential test if the latter's realized error exceeds its nominal
rate **by more than the declared margin**". The margin is declared nowhere — not in ADR-0012, not in
the PRD, not in the story. A criterion whose threshold does not exist cannot pass or fail.

### F-46 (high) — E4.S6's pick-up criterion is unmeasurable

"the bot picks up items whose **expected value justifies the turn**, given the Codex tables." No
threshold, no unit test, no named behaviour. The only evidence the story requires is a smoke
direction check for the whole pull request, which cannot attribute a change to this rule.

### F-47 (medium) — E1.S13's gate test asserts a universal negative

"`OracleGateTest` asserts that with no flag **there is no code path** from true identities or unseen
positions into anything the Brain can hold." As written this is a proof obligation, not a test.
**Fix:** express it as ArchUnit reachability (`OracleView` is referenced only by `OracleObserver`, the
launcher's oracle path and the E9 tool; the Brain-facing interface never mentions it) plus a runtime
test that constructing the fair path yields no `OracleView`.

### F-48 (medium) — E4.S4's search bound is unspecified

"it searches for secret doors when the floor is otherwise exhausted, **with a bounded number of
attempts**" — the bound is not given and no test is named.

### F-49 (medium) — E4.S8 and E4.S10 state preferences, not checks

E4.S8: "it prefers testing when the information is worth most, early on a floor rather than during a
fight." E4.S10: "the bot descends when the floor's **remaining value falls below the risk of
staying**." Neither names a test or a measurable proxy; both are the kind of statement the overview
calls "It works".

### F-50 (medium) — E2.S5, E2.S6 and E2.S7 name no test at all

Every other Codex story names `CodexCompletenessTest`, `CodexSeedFreeTest`,
`CombatTableStabilityTest` or the drift check. These three name none; E2.S7 additionally has no
consumer ("consumed by nothing yet"), so nothing would notice if it were wrong.

### F-51 (medium) — Four E5 stories name no test class

E5.S3 ("every state is stated in words, so colour never carries meaning alone"), E5.S8 ("never filled
and never over sprites"), E5.S10 ("the oracle colour appears nowhere else in the instrument" — a
source or token scan would settle it), E5.S12 (criteria checkable but unattributed). E5.S2, S6, S9
and S11 do name tests, so the omission is inconsistent rather than deliberate.

### F-52 (medium) — E4.S2's named test covers one of its four criteria

`BeliefConsistencyTest` covers probability sums and collapse-on-identification. Floor facts, chapter
counters and lost-monster memory — three of the story's five criteria — have no evidence named.

### F-53 (low) — E3.S10's failure criterion is vague

"a failure or an undecided outcome is visible without opening the logs" — visible where, to whom?

---

## 5. Architecture conformance

Checked specifically: module dependency edges, the Observation whitelist, one step per Action,
Input-wait detection, the salt discipline, snapshot opacity, the ten-row hook ledger, thread roles.

**Conforming, and worth recording as such:** one step per Action (E1.S9 and E1.S10 both state it,
matching AD-4 and ADR-0014, and E1.S10 tests the sixty-wake-ups case ADR-0015 warns about);
Input-wait detection at the `observe()` site in the `!ready` branch (E1.S10, ADR-0015); snapshot
opacity (E1.S15 asserts `Snapshot` never leaves `harness` and `api` carries only `SnapshotHandle` and
`Simulator`, matching AD-9 and ADR-0009); thread roles (E1.S14 and E5.S1 match AD-8 and ADR-0013,
including the render thread as UI-role and the Brain on its own worker); the Observation whitelist's
negative fields (E1.S5's "carries neither the seed, the salt nor a turn counter" matches ADR-0005);
the oracle sidecar shape (E1.S13 matches ADR-0005 and ADR-0006).

The failures follow.

### F-54 (blocking) — The module graph has no `codex → api` edge, but E2.S1 requires api-typed output

AD-1's graph and PRD section 10 both give `codex` one edge: `codex → core`. The repository agrees
(`shatterfish/codex/build.gradle` declares `implementation project(':core')` only). But AD-13 lists
"the Codex tables" among the `api` types with a codec and a version, and **E2.S1's fourth criterion is
"the output is `api`-typed JSON with a Codex version"**. As specified, E2.S1 cannot be implemented
without adding an edge the architecture forbids. **Fix:** either add `codex → api` to AD-1, the PRD's
boundary list and `codex/build.gradle` (harmless: `api` depends on nothing), or change E2.S1 to emit
plain JSON whose schema `api` declares for readers. Decide before E2 starts; the two options produce
different code.

### F-55 (blocking) — AD-11 puts the salts in the Registration; ADR-0012, ADR-0007 and E3.S5 say the opposite

AD-11: "a comparison runs only under a committed Registration (Hypothesis id, `p0`, `p1`, `α`, `β`,
`n0`, `nmax`, Seed set version, **the salts**, both Brains' commits, budget, machine class)".
ADR-0012: salts are "drawn by the runner when the pair executes from a per-invocation secret and
written to both Run logs ... never published before a comparison completes". ADR-0007: "a Registration
records the salts it used" — after the fact. E3.S5 is emphatic and correct: "the salts ... are **not**
in the Registration, so a Brain's author cannot precompute the random stream". The spine is the
document the architecture workflow treats as binding, and it states the one thing the discipline
exists to prevent. **Fix:** correct AD-11 to say the Registration fixes the bounds and both Brains and
that salts are drawn at execution and recorded afterwards.

### F-56 (blocking) — AD-10 says the hook budget is eight; ADR-0008, PRD section 10 and E1.S2 say ten

AD-10's rule ends "the v1 budget is **eight**". ADR-0008 explicitly amends it to ten with a
`[NOTE FOR PM]` and lists ten rows; PRD section 10 says ten with the same note; E1.S2's
`HooksLedgerTest` "fails ... if the row count exceeds ten"; E1.S1 checks the spike's prediction
against "the ledger's budget of ten". ADR-0006's prose also still says "a budget of eight". One of
these numbers goes into a test in the second story of E1. **Fix:** correct AD-10 and ADR-0006's prose
to ten.

### F-57 (high) — AD-11 says the Run log is gzip; ADR-0011, NFR-9 and E3.S3 say plain text

AD-11: "every Run writes the **gzip** JSONL log of ADR-0011". ADR-0011: `<run-id>.jsonl`, with gzip
reserved for archives; the spine's own Logging convention agrees ("the Run log is plain `.jsonl` a
person can read with standard tools (NFR-9), and the Rig may gzip only archived Runs"); NFR-9 makes
plain text a requirement; E3.S3 says "each Run writes plain `<run-id>.jsonl`". The story is right and
the AD is wrong.

### F-58 (high) — E4.S11's weight file has no loader, and AD-1 forbids the Brain from reading files

"the weights are a committed, versioned `api`-typed data file, not constants in code" **and**
"changing a weight changes behaviour with no recompilation of `brain`". AD-1 bans `java.io`,
`java.nio.file` and reflection in `brain`, so the Brain cannot load its own weights; AD-13 says the
Codex "reaches the Brain as `api`-typed data **loaded by the caller**". E4.S11 names no caller, and
the same silence applies to the Codex tables in E4.S2, E4.S3 and E4.S5. Without it an engineer writes
`Files.readAllBytes` in `brain` and hits the boundary rule (once F-1 is fixed) or, worse, does not.
**Fix:** state in E4.S1's contract that the driver loads the weights and the Codex and hands them to
the Brain as `api` values.

### F-59 (medium) — Three E1 hook rows land without a same-PR `UPSTREAM.md` row in their criteria

ADR-0008 requires a row per hook in `docs/UPSTREAM.md`; `CLAUDE.md` requires it in the same pull
request; NFR-6 requires docs to change with the code. E1.S2 (registry), E1.S11 (identity order),
E5.S6 (input gate) and E5.S11 (two rows) all say so explicitly. **E1.S3** (row 3, the `GameScene`
seam, plus row 5's headless guards), **E1.S7** (row 4's `CharSprite.emo` accessor) and **E1.S10**
(row 5, the Input-wait notification) do not. E1.S2's counting test would fail the build, which is the
saving grace, but the criteria should say it rather than rely on a failure elsewhere.

### F-60 (medium) — The spine's Identifiers row omits the Brain from the Run id

Consistency Conventions: "Run id = `<tag>-<class>-<challenges>-<seedcode>-<salt>`". AD-14 and
ADR-0011 both include `-<brain>` and give the reason (the two Runs of a pair would collide on one
file); E3.S3 requires it. The convention row is stale.

### F-61 (low) — ADR-0006's prose still cites a budget of eight

"Rejected: many hooks against a budget of eight (PRD §10)." Cosmetic, but it is the ADR the Observer
stories are specified against.

---

## 6. First-story executability

Could an engineer start E1.S1 tomorrow morning with only these documents? Nearly — the story is well
framed, the tree is pinned, `docs/codebase-map.md` and `docs/rules/game-loop.md` give the
touchpoints, and `harness/build.gradle` already carries `gdx-backend-headless` and the desktop
natives. Five things are missing.

### F-62 (blocking) — Every ADR the stories obey is still `proposed`

`docs/adr/index.md` shows ADR-0005 through ADR-0015 as `status: proposed`; only 0001 to 0004 are
accepted. ADR-0001 makes an ADR immutable *once accepted*, which implies a proposed one may still
change. Every E1 story is written as "Given ADR-000N's decision", and E1.S1's last criterion asks the
engineer to mark "the ADR-0015 assumptions that survive ... confirmed" — a status transition with no
defined mechanism, no owner and no place to record it (a `status:` field? a new row? a supersession?).
An engineer starting E1.S1 is implementing against decisions that are formally not yet decisions.
**Fix:** accept 0005 to 0015 (or state that E1.S1's report is the acceptance gate for 0015 and name
what "confirmed" edits), and define how a confirmed assumption is recorded.

### F-63 (high) — E1.S1 does not say where the spike code lives or what happens to it

The spike needs a no-op `GL20` (~200 lines), a harness-owned `Scene` and a driver loop — the substance
of E1.S3. The story names no module, no source set and no disposition (kept and grown by E1.S3, or
discarded and rewritten). Without a decision the engineer either writes throwaway code twice or lands
E1.S3's implementation inside E1.S1 and breaks the one-story rule.

### F-64 (high) — There is no story file, no `sprint-status.yaml` and no issue for E1.S1

`CLAUDE.md`'s session ritual is: read `sprint-status.yaml`, read the epic's open issues, take one
story through its lifecycle, hand off by updating the story file, the issue and the status file.
`_bmad-output/implementation-artifacts/` contains only `.gitkeep`. The `next-story` and `bmad-build`
paths have no input. The engineer's first act cannot be E1.S1; it must be sprint planning and issue
mirroring (FR-53). That is a fine answer — but the artifact set should say so, because "62 stories
across 10 epics" reads as ready-to-start.

### F-65 (medium) — E1.S1's exit criterion is weaker than ADR-0015's, and its hook-count criterion has no method

The story: "one hero melee attack resolves end to end". ADR-0015: "the exit criterion is that a Run
completes with no upstream edit outside the hook ledger". These are different bars, and the second is
the one the epic depends on. Separately, "the report states how many hook rows the full implementation
will need, and the story fails if that number exceeds ... ten" asks for a prediction with no stated
method; the honest form is "lists each guard site found, maps it to a ledger row or proposes a new
one, and fails if the mapping needs an eleventh row".

### F-66 (medium) — New docs pages need a `mkdocs.yml` nav entry, which no story mentions

CI runs `mkdocs build --strict` on every pull request (ADR-0002, and `docs.yml` exists). E1.S1 adds
`docs/results/e1-touchpoint-audit.md`; E1.S16, E2.S8, E3.S8, E3.S9, E3.S11 and E5.S13 all add pages
too. No story requires the nav entry, and `--strict` is unforgiving. One line in the epic overview's
shared conventions would cover all of them.

---

## 7. Traceability

The FR coverage map is close but not accurate against the story bodies. The pattern is that the map
records the *owning* epic's story and drops the halves the PRD explicitly tags to another epic — even
where the map's own Epic column names both epics.

### F-67 (high) — FR-7's row credits a story that does not deliver it

`FR-7 | E0 (done), E1 | E1.S2`. E1.S2 delivers the Hooks registry, its counting test and the ArchUnit
version bump. See F-1.

### F-68 (high) — FR-11's row names an epic with no story

`FR-11 | E1, E5 | E1.S13, E5.S10`. The E3 half (the runner refuses an oracle Run) is neither listed
nor owned. See F-4.

### F-69 (high) — The closing coverage claim is false

"Every v1 requirement (E0 to E5) has at least one story." FR-39's Explain control (F-2) and FR-22's
comparison ledger (F-3) have none.

### F-70 (medium) — Twelve rows are incomplete against the story bodies

| Row | Says | Should also say | Why |
|---|---|---|---|
| FR-2 | E1.S11 | E4.S1 | the Brain-determinism consequence is `BrainDeterminismTest` |
| FR-4 | E1.S9 | E5.S7 | the PRD tags the unsupported-input consequence `[E5]`; E5.S7 delivers it |
| FR-19 | E3.S1 | E3.S3 | the per-Run result fields are the `end` record |
| FR-21 | E3.S6, E3.S7 | E3.S9 | "a deliberately worse Brain is rejected" is FR-21's own consequence |
| FR-23 | E3.S3 | E5.S7 | the `actor: human` half is tagged `[E5]` |
| FR-25 | E3.S8, E3.S9 | E3.S10 | FR-25's last paragraph is the nightly results pull request |
| FR-26 | E3.S11 (Epic column says "E3, E4") | E4.S13 | the E4 half is E4.S13's last criterion |
| FR-27 | E4.S1 | E5.S7 | the takeover consequence is tagged `[E5]` |
| FR-36 | E4.S12 (Epic column says "E4, E5") | E5.S3, E5.S4 | the Panel half |
| FR-37 | E5.S1 | E5.S12 | save, resume and the boundary record |
| FR-38 | E5.S2, S3, S4 | E5.S12 | the Run-over Panel state |
| FR-39 | E5.S5 | E5.S11 | E5.S11's own "Given" line cites FR-39 |

### F-71 (medium) — FR-22's row overclaims E3.S5

E3.S5 delivers the Registration, the salt discipline and the no-Registration refusal. It does not
deliver the peeking ledger or the postdating refusal (F-3, F-10), so the row asserts full coverage of
a requirement that is two-thirds covered.

### F-72 (low) — "62 stories across 10 epics" overstates what is specified

E6 to E9 carry story *titles* in prose, not stories; E0 is complete. The 62 numbered stories are E1
(16), E2 (9), E3 (11), E4 (13), E5 (13). The count is right; the framing invites a reader to think E6
to E9 are specified.

---

## What would make this READY

1. Resequence E1 (F-30), E3 (F-31) and E4 (F-32); move E1.S15's host assertion (F-35), E2.S9's Brain
   index (F-34) and E1.S16's with-Brain benchmark (F-33) to the epics that can hold them.
2. Correct four spine and ADR statements: AD-11's salts (F-55), AD-10's budget (F-56), AD-11's gzip
   (F-57), and the `codex → api` edge (F-54, which needs a decision, not just an edit).
3. Accept ADR-0005 to ADR-0015 and define what "confirmed" means for an assumption (F-62).
4. Add five stories or story-halves: the `brain` boundary rules (F-1), Explain (F-2), the comparison
   ledger (F-3), the Rig's oracle refusal (F-4), the remaining ADR-0006 whitelist rows (F-5).
5. Split the nine over-sized stories (F-13 to F-21) at the points given.
6. Add Rig numbers to the six E4 stories that lack them and settle E5's exemption (F-41, F-42).
7. Replace the five wish-criteria with checks (F-43 to F-46, F-49) and name tests in the eleven
   stories that name none (F-50, F-51).
8. Fix the fifteen coverage-map rows (F-67 to F-71).

Items 1 to 4 are the blocking set and are a single planning turn's work. The rest can land as the
epics open, provided item 1 lands before E1.S2 is picked up.
