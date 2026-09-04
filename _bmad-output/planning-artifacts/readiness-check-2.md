---
title: Implementation-readiness check (re-run) — epics.md
artifact: _bmad-output/planning-artifacts/epics.md
supersedes: _bmad-output/planning-artifacts/readiness-check.md
reviewed: '2026-09-04'
reviewer: readiness check (independent, documents only)
---

# Implementation-readiness re-check: `epics.md`

## Verdict

**READY WITH FIXES**

The revision did the hard work. All four spine contradictions are corrected, every ADR the stories
obey is `accepted`, the three broken epic sequences are resequenced, the five missing stories or
story-halves exist, and eight of the nine oversized stories are split. 72 stories across 10 epics,
and the story keys renumber cleanly except in four places noted below.

Nothing left is blocking: an engineer can start E1.S1 tomorrow, and E1.S2's boundary rules — the
enforcement of non-negotiable #1 — now exist and are owned. What remains is a set of high findings
that will each cost a story or produce a wrong artifact if left, plus four regressions the
renumbering introduced.

### Previous findings: closure

| Previous severity | Count | Closed | Partially closed | Open |
|---|---|---|---|---|
| Blocking | 8 | 6 | 2 | 0 |
| High | 32 | 26 | 4 | 2 |
| **Blocking + high** | **40** | **32** | **6** | **2** |

### Counts by severity, still outstanding

| Severity | Count |
|---|---|
| Blocking | 0 |
| High | 9 |
| Medium | 30 |
| Low | 6 |
| **Total** | **45** |

Scope: `epics.md`, `ARCHITECTURE-SPINE.md` (AD-1, AD-10, AD-11, AD-14 and the Consistency
Conventions), `prd.md` sections 4, 6.1, 8 and 10, `docs/adr/index.md`, ADR-0002, ADR-0006, ADR-0007,
ADR-0008, ADR-0015, and repository state where a document makes a claim about it
(`shatterfish/codex/build.gradle`, `.github/workflows/`, `_bmad-output/implementation-artifacts/`).

---

## 1. The eight blocking findings

### F-1 — the `brain` boundary rules — **CLOSED**

E1.S2 is now titled "The Hooks registry, the counting test, and the boundary rules" and adds
`BrainBoundaryTest`, which asserts `brain` depends on no game package, no Shatterfish module but
`api`, and none of `java.io`, `java.nio.file`, `java.net` or `java.lang.reflect`, plus
`ApiBoundaryTest` for `api`. The ArchUnit 1.5.0 bump lands in the same story "with every boundary
rule green". The FR-7 coverage row (`E1 | E1.S2`) is now accurate, and E4.S1's criterion "the
boundary rules stay green, including the ban on file, network and reflection access" now asserts a
rule that exists.

*Residual (medium, M-30):* the spine's Network convention bans `java.net` "in every Shatterfish
module". E1.S2 covers `brain` and `api` only; no story owns the rule for `harness`, `codex`, `rig`
and `overlay`, and NFR-8 is a release-class requirement.

### F-30 — E1.S4 to E1.S8 depended on E1.S9 — **PARTIALLY CLOSED**

The sequence is fixed where it mattered. E1 is now: S1 spike, S2 hooks and boundary, S3 scene, S4
driver loop, S5 Input-wait detection, S6–S7 schema, S8–S11 Observer, S12 `Action` + `validActions`,
S13 executor, S14 random agent, S15–S16 determinism, S17 fairness tests, S18 oracle, S19 threads,
S20 snapshot, S21 numbers. The random agent (E1.S14) now follows the Action type and the executor,
and the per-wait sequence and turn cap it needs are in E1.S5 and in its own body.
`CodecReflectionTest` moved to E1.S6, which covers header, map and actor only, so it no longer needs
`Action`.

*What remains (medium, M-13):* E1.S7 implements "the hero, inventory, journal, log, **actions** and
prompt sections ... in `api`", but the sealed `Action` type those entries are made of arrives in
E1.S12, five stories later. No story states which story first *populates* the `actions` section;
E1.S12's "the valid set is computed from the Observation alone" implies it, but the previous
report's fix asked for it to be said.

### F-31 — E3.S1 depended on E3.S2 and E3.S3 — **CLOSED** (with a new forward dependency, R-2)

E3 now runs S1 seed sets, S2 Run logs with the hash chain, S3 the parallel runner. The runner is
third, so `--seeds <set>` has its sets and "recorded as incomplete ... with its partial log kept"
has its writer.

### F-32 — E4.S6 depended on E4.S11; E4.S4–S10 depended on E4.S12 — **CLOSED**

E4 is now: S1 skeleton, S2 Beliefs, S3 safeTest, **S4 Decision output and strategy log**,
**S5 Evaluation with weights as data**, then the seven Policies (S6 explore, S7 fight, S8 pick-up
and equip, S9 eat and heal, S10 test items, S11 prompts, S12 descend), then S13 smoke gate, S14
registered gate. The Decision shape and the Evaluation both precede every Policy that depends on
them, including S8's "it equips a weapon or armour when the Evaluation prefers it".

### F-54 — no `codex → api` edge, but E2.S1 requires api-typed output — **PARTIALLY CLOSED**

The spine chose the "add the edge" option: AD-1's graph now carries `codex --> api`, the layer table
says the Codex is "written as `api`-typed JSON", AD-13 is unchanged, and E2's preamble says so
explicitly ("`codex` depends on `api` as well as on the game (AD-1 as corrected in session 13)").

*What remains (high, H-4):* **PRD section 10 still reads "`codex` on `core`"** in the permitted-edge
list, immediately followed by "The build enforces the edges", and `shatterfish/codex/build.gradle`
still declares `implementation project(':core')` only. Two of the three artifacts that define this
boundary now contradict the third. E1.S2's boundary tests assert `brain` and `api` and say nothing
about `codex`, so nothing catches the disagreement.

### F-55 — AD-11 put the salts in the Registration — **CLOSED**

AD-11 now reads: "(Hypothesis id, `p0`, `p1`, `α`, `β`, `n0`, `nmax`, Seed set version, both Brains'
commits, budget, machine class, and **not** the salts, which the runner draws when each pair
executes so that a Brain's author cannot precompute the stream)". This matches ADR-0007, ADR-0012
and E3.S5 exactly. E3.S5's field list adds the configuration hashes and the burn-in — additive, not
contradictory.

### F-56 — AD-10 said eight, everything else said ten — **CLOSED**

AD-10 now ends "the v1 budget is ten (ADR-0008, PRD v4 section 10)", matching ADR-0008's ledger, PRD
section 10 and its `[NOTE FOR PM]`, E1.S1's "fails if that number exceeds the ledger's budget of
ten" and E1.S2's `HooksLedgerTest`.

*Residual (low, L-5):* `docs/adr/0006-observer-visibility-rules.md:45` and
`docs/adr/0007-rng-seeding-strategy.md:33` still say "a budget of eight (PRD §10)" in their prose.

### F-62 — every governing ADR was `proposed` — **CLOSED**

`docs/adr/index.md` now shows ADR-0005 to ADR-0015 as `accepted`, dated 2026-09-04. The "confirmed
assumption" mechanism is defined too: E1.S1's last criterion is now "the findings are written to
`docs/results/e1-touchpoint-audit.md`, and each ADR-0015 assumption the spike confirms or refutes is
recorded there" — a named file rather than an undefined status transition.

---

## 2. The thirty-two high findings

### Closed (26)

| # | Finding | Evidence in the revision |
|---|---|---|
| F-2 | FR-39's Explain control unowned | E5.S3's last criterion: "the Explain control expands the Decision card in place to show the Policy that fired, the alternatives' reasons in full and the Safety flags that applied ... this is a v1 control (FR-39), not the v2 Explain view". FR-39's row names E5.S3. |
| F-4 | FR-11's E3 half unowned | E3.S3: "the runner refuses any Run whose log header has the oracle flag set, which is the E3 half of FR-11". E1.S18's pointer now reads "owned by E3.S3" and resolves. FR-11's row is `E1, E3, E5 \| E1.S18, E3.S3, E5.S11`. |
| F-5 | Four ADR-0006 whitelist rows unowned | New E1.S11 ("The Observer, part four: the remaining rows") covers blobs, danger count, level feeling, transitions seen and the sealed flag, with `EnvironmentLeakTest` **and** "a checklist test asserts every row of the ADR-0006 table has at least one leak test naming it". |
| F-6 | FR-38's state-table test unowned | New E5.S14 implements Brain error, no valid action, hero busy and no Run, and `PanelStateTest` "reaches each of these states in a scripted test and asserts the Panel's content and enablement matrix, so every state of UX-DR7 is covered by a test". |
| F-13 | E1.S3 was three stories | Split into E1.S3 (no-op GL, atlases, scene, `SceneDrawParityTest`) and E1.S4 (driver loop, runnable queue, `HeadlessBootTest`, Prompt windows). Input-wait detection became its own E1.S5. |
| F-14 | E1.S5 was two stories | Split into E1.S6 (header/map/actor records, `ObservationCodec`, section hashing, `CodecCanonicalTest`, `CodecReflectionTest`) and E1.S7 (remaining sections, JSON writer, `CodecEqualityTest`). |
| F-15 | E1.S9 was two or three stories | Split into E1.S12 (`Action`, `validActions`) and E1.S13 (executor, `ActionCompletenessTest`, `ActionValidityPropertyTest`). Two halves rather than three; acceptable, though E1.S13 still carries the selector paths and the completeness enumeration. |
| F-16 | E1.S11 was two stories | Split into E1.S15 (salt, `mix`, Profile, `MixTestVectorTest`) and E1.S16 (identity order, `DeterminismTwoJvmTest`, the `UPSTREAM.md` row). |
| F-17 | E2.S3 was the largest Codex story | Split into E2.S3 (decks, weights, item catalogue) and E2.S4 (guarantee schedules, tier tables, limited drops) — and E2.S4 gained `GuaranteeArithmeticTest`, which the old story had no equivalent of. |
| F-18 | E3.S7 was a study plus an implementation | Split into E3.S7 (calibration, realized rates, tie fraction, the declared margin) and E3.S8 (the e-process implemented alongside and compared on the same distributions). |
| F-19 | E4.S13 was a campaign plus two features | Split into E4.S13 (smoke gate, gallery comparison view) and E4.S14 (registered `goo` gate, behavioural differential, with-Brain throughput, retrospective). |
| F-21 | E5.S7 was two stories | Split into E5.S7 (take over / hand back, mid-animation dimming) and E5.S8 (human-Action recording, `unsupported`, shadow Decisions, `HumanTurnReplayTest`). |
| F-33 | E1.S16 depended on E4 | E1.S21: "the benchmark with a Brain attached is named as deferred to E4"; E4.S14: "the throughput benchmark is re-run with the Brain attached, completing the measurement E1.S21 deferred". Both keys resolve. |
| F-34 | E2.S9 depended on E4 | E2.S10 keeps the checker and the 275-row pass and states "the Brain's own Rules index ... is named as owned by E4.S4"; E4.S4 creates it. FR-17's row is `E2, E4 \| E2.S10, E4.S4`. |
| F-35 | E1.S15 asserted an E6 component | E1.S20: "a test asserts a handle whose scrubbed flag is false is refused by **the interface contract**" and "the rollout host itself is explicitly deferred to E6". |
| F-36 | E3.S8 depended on E3.S9 | Results pages are now E3.S10, after E3.S7 (bounds), E3.S8 (e-process) and E3.S9 (baseline and the deliberately worse Brain), so the "real undecided run" has a source. |
| F-37 | E5.S5 specified PAUSED before E5.S6 made it real | The input gate is now E5.S5 and the controls and speed modes are E5.S6. `PausedInputTest` lands before anything lands in PAUSED. |
| F-41 | Six E4 stories carried no Rig numbers | All fourteen E4 stories now carry a smoke-set direction check (S1, S2, S3, S4, S5 and S11 gained the criterion verbatim); S13 publishes the smoke rate as a direction check and S14 is the registered comparison. |
| F-43 | E5.S2 substituted a checklist for a test | "`OverlayToolkitTest` is an ArchUnit rule asserting that `overlay` imports nothing from Swing, AWT, JavaFX or any web-view package, so the native-UI rule is a check rather than a habit." The review-checklist wording is gone. |
| F-45 | E3.S7's margin was undeclared | E3.S7: "the acceptable margin between realized and nominal error is declared here as a number, so that E3.S8 has a criterion to test against"; E3.S8 refers to "the margin E3.S7 declared". |
| F-57 | AD-11 said gzip | AD-11 now says "every Run writes the **plain** JSONL log of ADR-0011 with its hash chain, keyed by `k`", matching ADR-0011, NFR-9, the spine's own Logging convention and E3.S2. |
| F-63 | E1.S1 did not say where the spike lives | "the spike lives on the story branch under `shatterfish/harness/src/test/java` as a throwaway test, and the report says explicitly which parts E1.S3 and E1.S4 inherit and which are discarded". |
| F-64 | No story file, no sprint-status, no issue | The artifact now says it: "The story files, `sprint-status.yaml` and the GitHub issues that the session ritual reads do not exist yet; sprint planning and issue mirroring are the next session's work, and no story above can start before them." (`_bmad-output/implementation-artifacts/` still holds only `.gitkeep`, as stated.) |
| F-67 | FR-7's row credited a story that did not deliver it | E1.S2 now delivers it. |
| F-68 | FR-11's row named an epic with no story | E3.S3 owns the E3 half and is listed. |
| F-69 | The closing coverage claim was false | Explain (E5.S3) and the comparison ledger (E3.S5) now exist. The claim is true at story level; the one residual is FR-25's ledger *count* on the Results page (H-3). |

### Partially closed (4)

**F-3 (FR-22's peeking ledger and FR-25's ledger count) — the ledger exists, the Results page still
does not carry the count.** E3.S5 gained "a comparison ledger records every Registration, its
outcome and every `holdout` use, so that the count of prior attempts behind a published claim is
public (FR-22, FR-25)". But E3.S10's Results-page field list — tag, commit, seed set and version,
both Brains, hypothesis id and Registration commit, bounds and units, outcome with trace, per-Run
aggregates, pair correlation, survival curve, boss staircase, log links, fairness status, oracle off,
reproduction command — **still omits the ledger count**, which FR-25 lists explicitly ("the measured
paired-seed correlation and the ledger count of prior Runs") and which SM-1 depends on ("the Results
page records the ledger count"). Carried forward as **H-3**.

**F-20 (E5.S5 was two stories) — split at the wrong seam.** The old story's PAUSED half became
E5.S5, but E5.S6 still carries all four speed modes with their different timing sources, both
steppers with their ranges, the queue-a-press-while-THINKING path, the start-in-PAUSED rule **and**
"the enablement matrix of UX-DR7 is implemented" — twelve states' worth. Worse, seven of those twelve
states (Brain error, hero busy, no valid action, Run over, no Run, save and resume, collapsed) do not
exist until E5.S13 and E5.S14, so the matrix E5.S6 implements cannot be the matrix UX-DR7 defines.
Carried forward as **H-9**.

**F-44 (E1.S3's parity test states no basis of comparison) — restated, not resolved.**
`SceneDrawParityTest` now "asserts the draw counts: a scripted sequence consumes the same number of
random draws headlessly as the same sequence consumes with the real scene, which is the testable form
of parity and does not require a graphics context in the test". The metric is right (ADR-0015's
actual concern), but the comparison basis is still "with the real scene", which is the thing that
cannot be instantiated in the test. The story should say the expected counts come from a recorded
baseline or an enumerated list. Low residual (L-3).

**F-58 (the weight file has no loader) — weights fixed, the Codex is not.** E4.S5 now says "the file
is read by the caller, never by `brain`, which cannot open a file, and is handed to the Brain as an
`api` value at construction" — exactly right. But AD-13 says the same of the Codex ("`api`-typed data
loaded by the caller"), and E4.S1's contract still does not name the loader, while E4.S2 ("weighted
from the Codex spawn weights"), E4.S3 ("using the Codex tables") and E4.S5 ("Observation features
derived from Codex tables") all consume it. Carried forward as **H-5**.

### Open (2)

**F-42 — no E5 story carries Rig numbers, and E5.S12 needs them.** The overview still says "from E3
onward every Brain-affecting story names the Rig numbers its pull request must carry". E5's preamble
states no exemption. E5.S12 routes "the emitter, music and emote random draws ... to the base
generator", changing what the game's random stream consumes within a turn; `OverlayReplayTest` proves
cross-driver equality and says nothing about whether measured outcomes moved. Carried forward as
**H-1**.

**F-46 — E4.S8's pick-up criterion is unmeasurable.** Verbatim unchanged: "the bot picks up items
whose expected value justifies the turn, given the Codex tables". No threshold, no unit test, no named
behaviour; the only evidence is a whole-PR smoke direction check that cannot attribute a change to
this rule. Carried forward as **H-2**.

---

## 3. Regressions introduced by the revision

### R-1 (high) — the E4 preamble now names the wrong story for the epic's own gate

E4's preamble reads: "Every story in this epic carries Rig numbers in its pull request: a smoke-set
direction check against the previous Brain at minimum, **and for E4.S13 a registered standard-set
comparison**." After the split, E4.S13 is the *smoke*-set gate whose own last criterion is "no
acceptance claim is made from the smoke set, which is a direction check by definition"; the
registered comparison is E4.S14. The key is stale and the two halves of the sentence contradict each
other. The set is wrong too: SM-3, FR-20, FR-31 and E4.S14 all use the `goo` set of 400 Warrior
triples, not `standard`. **Fix:** "and for E4.S14 a registered comparison on the `goo` Seed set".

### R-2 (high) — E3's holdout rule is specified three times, in two forms, one of them forward

- E3.S1 (seed sets): "the runner refuses a development comparison on `holdout` and records every
  `holdout` use in a ledger" — a runner behaviour and a ledger, two stories before the runner exists
  (E3.S3) and four before the comparison ledger (E3.S5).
- E3.S3 (runner): "refuses a comparison whose Seed set is `holdout` unless **the Registration
  declares a release-level claim**" — the Registration is E3.S5, two stories later, so this criterion
  cannot be implemented where it sits.
- E3.S5 (Registration): "a comparison ledger records every Registration, its outcome and **every
  `holdout` use**".

Two ledgers and two refusal rules (development-versus-not, versus release-level-Registration-or-not)
for one requirement. **Fix:** state the rule once in E3.S3 in its E3.S1 form (no Registration
concept), have E3.S5 extend it, and fold E3.S1's ledger into E3.S5's.

### R-3 (high) — the splits left seven E5 stories with no named test class, up from four

The overview's second binding convention is "Every story names the test class that proves it".
E5.S3, E5.S4, **E5.S6**, **E5.S7**, E5.S9, E5.S11 and E5.S13 name none. E5.S6 and E5.S7 are new
cases created by the splits: the old combined stories' tests went to the halves that became E5.S5
and E5.S8, and nothing was written for the other halves. E5.S7 is the sharpest — take over, hand
back, mid-animation dimming and stale-Decision skipping, all of it observable, none of it with
evidence named. (E5.S1, S2, S5, S8, S10, S12 and S14 do name tests, so this is inconsistency rather
than intent.)

### R-4 (medium) — the coverage map's FR-36 row lost its E5 epic

The row is now `FR-36 | E4 | E4.S4`. In the previous version the Epic column read "E4, E5". E5's own
preamble still says it covers "the E5 consequences of FR-4, FR-11, FR-12, FR-23, FR-27 and FR-36",
PRD section 6.1 says the same, and FR-36's own text is tagged `[E5]` for the Panel half, which E5.S3
and E5.S4 deliver. The renumbering dropped the epic rather than adding the stories.

### R-5 (medium) — E5.S7 and E5.S8 carry the same criterion

Both stories require, in nearly identical words, that "a Decision tagged with a wait index that is no
longer current is logged as skipped and never executed". Two owners for one behaviour; the split
should have left it in one.

### R-6 (medium) — E4.S1's Rig criterion has no previous Brain to compare against

Every E4 story, E4.S1 included, requires "a smoke-set direction check **against the previous Brain**".
E4.S1 is the first Brain; the only prior comparator is E3.S9's random-agent Baseline. The epic's rule
should name the Baseline as the comparator for E4.S1.

---

## 4. Fresh checks on the revised spine

- **AD-1's dependency graph** — correct and internally consistent: `api → jdk`, `harness → core,
  api`, `codex → core, api`, `brain → api`, `rig → harness, brain`, `overlay → core, harness, brain`.
  Matches the layer table, AD-13 and E2's preamble. The only disagreement left is downstream of the
  spine (PRD section 10 and `codex/build.gradle`, H-4).
- **AD-10's hook budget** — ten, consistent with ADR-0008's ledger, PRD section 10 and E1.S1/E1.S2.
- **AD-11's Registration fields** — bounds, both Brains, seed set version, budget, machine class,
  explicitly not the salts. Consistent with ADR-0007, ADR-0012 and E3.S5.
- **AD-11's log format** — plain JSONL with the hash chain, keyed by `k`. Consistent with ADR-0011,
  NFR-9, the Logging convention and E3.S2.
- **Still stale (medium, M-24):** the Consistency Conventions' Identifiers row reads
  `Run id = <tag>-<class>-<challenges>-<seedcode>-<salt>`, while AD-14 in the same document,
  ADR-0011 and E3.S2 ("whose id includes the Brain, so the two Runs of a pair never collide") all
  include `-<brain>`. This is the previous F-60, untouched by the revision, and it sits in the row an
  engineer would copy the format from.

---

## 5. Everything still outstanding

### High (9)

| # | Finding |
|---|---|
| H-1 | No E5 story carries Rig numbers and E5's preamble states no exemption; E5.S12 changes what the random stream consumes within a turn and needs a smoke direction check (F-42). |
| H-2 | E4.S8's "picks up items whose expected value justifies the turn" is a wish, not a check: no threshold, no test (F-46). |
| H-3 | E3.S10's Results-page field list omits FR-25's ledger count, so the anti-peeking mechanism is recorded in the ledger but never published; FR-25's coverage row also omits E3.S11, which owns the nightly results pull request (F-3, F-70). |
| H-4 | PRD section 10 still lists `codex` on `core` only and `shatterfish/codex/build.gradle` declares only `:core`, against AD-1, AD-13, the E2 preamble and E2.S1's fourth criterion (F-54). |
| H-5 | E4.S1's contract does not say who loads the Codex; AD-1 forbids `brain` from reading files and E4.S2, E4.S3 and E4.S5 all consume Codex data (F-58). |
| H-6 | E4's preamble names E4.S13 for "a registered standard-set comparison"; after the split that is E4.S14, on the `goo` set (R-1). |
| H-7 | E3's holdout refusal is specified in E3.S1, E3.S3 and E3.S5 with two different rules and two ledgers, and E3.S3's form forward-depends on E3.S5's Registration (R-2). |
| H-8 | Seven E5 stories name no test class (S3, S4, S6, S7, S9, S11, S13), against the overview's own convention; E5.S6 and E5.S7 lost theirs in the splits (R-3, F-51). |
| H-9 | E5.S6 still bundles four speed modes, two steppers, the THINKING queue and the whole twelve-state enablement matrix, seven of whose states do not exist until E5.S13 and E5.S14 (F-20). |

### Medium (30)

Carried over unchanged from the previous check unless noted.

1. **M-1 (F-8)** FR-29's "Belief update is a pure function ... with its own leak tests" — E4.S2 still
   names only `BeliefConsistencyTest`; purity and the belief-side leak test are unowned.
2. **M-2 (F-9)** FR-1's natives failure message appears in no story (the word "natives" occurs only
   in the stack list).
3. **M-3 (F-10)** FR-22's "refuses a comparison whose Registration commit postdates its first Run" is
   in no story; E3.S5 has only the no-Registration refusal.
4. **M-4 (F-11)** FR-19's eleven per-Run result fields are enumerated in no criterion; E3.S3 covers
   isolation and throughput, E3.S2 names the `end` record kind without its fields.
5. **M-5 (F-22)** E1.S21 publishes throughput *and* runs the leaf-correlation and disambiguation
   measurement; the second is a research session of its own.
6. **M-6 (F-23)** E2.S6 is four unrelated catalogues (traps, recipes, levels, rooms).
7. **M-7 (F-24)** E2.S7 is four dumps (strings, assets, changelog, journal documents).
8. **M-8 (F-25)** E3.S3 is process spawn, per-Run Profile and working directory, crash and hang
   detection, partial-log retention, throughput reporting, isolation, plus two refusal rules.
9. **M-9 (F-26)** E3.S10 is a sixteen-field Results generator, the methodology page and a
   demonstration undecided run.
10. **M-10 (F-27)** E4.S2 is four belief subsystems.
11. **M-11 (F-28)** E5.S2 is frame, docking, camera offset and re-application, width targets, collapse
    rules and the toolkit rule.
12. **M-12 (F-29)** E5.S13 is Run-over state, the Ankh prompt path, the save boundary record and
    resume.
13. **M-13 (F-30 residual)** E1.S7 declares the `actions` section before E1.S12 defines `Action`, and
    no story says which first populates it.
14. **M-14 (F-38)** E2.S5's `CombatTableStabilityTest` must reproduce "across the two supported
    platforms" and **E1.S16's `DeterminismTwoJvmTest` now requires the same, sixteen stories into
    E1** — but ADR-0002 puts the Windows job in the nightly `rig` workflow "from E3", and the
    repository has only `build.yml` and `docs.yml`, both `ubuntu-latest`. Neither epic has a vehicle
    and no story creates one. (E1.S16's version of this is new in this revision.)
15. **M-15 (F-39)** E1.S2's `HooksVanillaTest` "boots with no listener registered" — booting `core`
    in a test is E1.S3 and E1.S4. This is now the earliest unresolved ordering defect in the
    programme and should be settled before E1.S2 is picked up: either say it is a `:desktop:run`
    smoke check or move it after E1.S4.
16. **M-16 (F-47)** E1.S18's `OracleGateTest` still asserts a universal negative ("there is no code
    path from true identities or unseen positions into anything the Brain can hold").
17. **M-17 (F-48)** E4.S6's secret-door search still has "a bounded number of attempts" with no bound
    and no test.
18. **M-18 (F-49)** E4.S10 ("prefers testing when the information is worth most") names no test;
    E4.S12's descend rule now names its inputs (explored fraction, remaining guaranteed drops,
    hunger) but still no test or threshold.
19. **M-19 (F-50)** E2.S6, E2.S7 and E2.S8 name no test class at all; E2.S8 additionally has no
    consumer until E7.
20. **M-20 (F-52)** E4.S2's `BeliefConsistencyTest` covers two of its five criteria; floor facts,
    chapter counters and lost-monster memory have no evidence named.
21. **M-21 (F-59)** Four stories land a hook without requiring the same-PR `docs/UPSTREAM.md` row
    that ADR-0008, `CLAUDE.md` and NFR-6 demand: E1.S3 (scene seam and headless guards), E1.S5 (the
    Input-wait notification), E1.S9 (the `CharSprite` emote accessor) and E5.S10 (the key-binding
    registration). E1.S16, E5.S5 and E5.S12 do require it.
22. **M-22 (F-65 residual)** E1.S1's exit criterion is "one hero melee attack resolves end to end";
    ADR-0015's is "a Run completes with no upstream edit outside the hook ledger". The hook-count
    method is now sound ("lists every static dereference that had to be guarded, with a `path:line`
    for each"), the exit bar is still the weaker of the two.
23. **M-23 (F-66)** New docs pages (E1.S1, E1.S21, E2.S8, E3.S10, E3.S11, E5.S15) need a
    `mkdocs.yml` nav entry under `--strict`; only E2.S9 mentions `--strict` and no story mentions the
    nav. One line in the overview's shared conventions would cover all of them.
24. **M-24 (F-60)** The spine's Identifiers convention omits `-<brain>` from the Run id (see §4).
25. **M-25 (F-70 residual)** Seven coverage rows are still incomplete against the story bodies:
    FR-2 (add E4.S1, `BrainDeterminismTest`), FR-4 (add E5.S8, the `unsupported` half tagged `[E5]`),
    FR-19 (add E3.S2, the `end` record), FR-21 (add E3.S9, "a deliberately worse Brain is rejected"),
    FR-25 (add E3.S11, the nightly results pull request), FR-27 (add E5.S7/E5.S8, the takeover
    consequence tagged `[E5]`), FR-37 (add E5.S13, save, resume and the boundary record). Five rows
    from the previous list are fixed: FR-23, FR-26, FR-36's stories, FR-38 and FR-39.
26. **M-26 (F-71)** FR-22's row still asserts full coverage by E3.S5, which delivers the
    Registration, the salt discipline, the no-Registration refusal and the ledger, but not the
    postdating refusal (M-3).
27. **M-27 (R-4)** The coverage map's FR-36 row lost its E5 epic.
28. **M-28 (R-5)** E5.S7 and E5.S8 duplicate the stale-Decision criterion.
29. **M-29 (R-6)** E4.S1's "direction check against the previous Brain" has no previous Brain.
30. **M-30 (F-1 residual)** The spine's Network convention bans `java.net` "in every Shatterfish
    module"; only `brain` and `api` have an owner (E1.S2).

### Low (6)

1. **L-1 (F-12)** E1.S16 says the determinism test "runs on both supported platforms in CI" but not
   "on every pull request", which FR-2's consequence list and NFR-1 both require.
2. **L-2 (F-40)** E1.S6's header "carries ... the Codex version" without saying what it holds in E1,
   before E2 exists.
3. **L-3 (F-44 residual)** `SceneDrawParityTest`'s comparison basis is still "with the real scene".
4. **L-4 (F-53)** E3.S11's "a failure or an undecided outcome is visible without opening the logs" —
   visible where, to whom.
5. **L-5 (F-61)** `docs/adr/0006-observer-visibility-rules.md:45` and
   `docs/adr/0007-rng-seeding-strategy.md:33` still cite "a budget of eight (PRD §10)".
6. **L-6 (F-2 residual)** UX-DR15 still files "Explain expansion" under "v2 surfaces must not be
   precluded", and E8's story titles still list "the Explain expansion", although E5.S3 now ships the
   v1 control. Only FR-44's Evaluation-terms-and-Search half is v2; the wording should say so.

**Closed and worth recording:** F-72 — the "62 stories across 10 epics" framing is gone; the document
no longer states a story count, and E6 to E9 are explicitly "story titles, to be specified when the
epic opens".

---

## What would make this READY

1. Nine corrections of a line or a paragraph each: H-1 (E5's Rig exemption or E5.S12's check), H-3
   (the ledger count in E3.S10's field list, E3.S11 in FR-25's row), H-4 (PRD section 10's edge list,
   and `codex/build.gradle` when E2.S1 lands), H-5 (the loader sentence in E4.S1), H-6 (E4.S14 and
   `goo` in the E4 preamble), H-7 (one holdout rule, one ledger), R-4, R-5, R-6.
2. Two criteria to replace with checks: H-2 (E4.S8's pick-up threshold) and the seven E5 stories that
   name no test (H-8).
3. One more split: E5.S6 into the steppers and the enablement matrix (H-9), with the matrix landing
   with or after E5.S14, which is where its states become reachable.
4. Then the medium list as each epic opens, with M-15 (E1.S2's `HooksVanillaTest`) settled *before*
   E1.S2 is picked up, since it is the second story in the programme, and M-14 (cross-platform CI)
   settled before E1.S16.

Items 1 to 3 are a single planning turn. Nothing on this list stops sprint planning, issue mirroring
and E1.S1 from starting now.
