---
title: 'Reconciliation: PRD against the Architecture Spine'
type: reconciliation
status: final
created: '2026-09-03'
input: _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/prd.md (§4 FR-1..FR-53, §8 NFR-1..NFR-9, §10 hook budget, §13 open questions) + addendum.md
spine: _bmad-output/planning-artifacts/architecture/architecture-shatterfish-2026-09-03/ARCHITECTURE-SPINE.md
companions: docs/adr/0005 through docs/adr/0013
---

# Reconciliation — PRD ↔ Architecture Spine

## 0. Method and verdict scale

Every FR and NFR is placed in exactly one bucket.

- **(a) Governed** — a named AD or ADR fixes the mechanism, or fixes enough of it that two
  independent stories cannot diverge. The governing artifact is named.
- **(b) Left to a story, no divergence risk** — the spine deliberately does not decide, and the
  reason there is no risk is stated (single module, no cross-module contract, or a public
  surface already fixed elsewhere).
- **(c) Dropped or contradicted** — the spine or an ADR loses the requirement, or says something
  the requirement forbids.

Two more findings classes are collected outside the buckets: **quiet requirements** (a tone, a
constraint or a testable consequence that survives nowhere even though its FR is otherwise
governed), and **cross-reference defects** (a pointer that resolves to nothing, or an ADR that
contradicts its own spine).

### Counts

| Bucket | FRs | NFRs (1–6) | NFRs (all, 1–9) |
|---|---|---|---|
| (a) governed | 30 | 4 | 4 |
| (b) story, no risk | 17 | 1 | 1 |
| (c) dropped or contradicted | 6 | 1 | 4 |
| **Total** | **53** | **6** | **9** |

The spine's frontmatter declares `binds: [FR-1..FR-53, NFR-1..NFR-6]`. NFR-7 (portability),
NFR-8 (privacy and network) and NFR-9 (observability) are therefore outside the spine's own
statement of scope. They are counted in the "all" column and are three of the four NFR (c)s.

---

## 1. Section 4.1 — Headless engine (FR-1 to FR-6)

### FR-1 Boot a Run headlessly — (a)

Governed by **AD-6** (fresh versioned standard Profile per Run in its own working directory; one
process hosts one Run), the **Stack** table (`gdx-backend-headless` + `gdx-platform:natives-desktop`),
and the **Structural Seed** (`org.shatterfish.harness.scene` — HeadlessScene, no-op GL, Pixmap
atlases). ADR-0008 hook row 5 (`Hero.ready()` / `Hero.interrupt()` guards for scene statics under
a headless scene) presupposes the same design.

Caveat: the design is *adopted* in a Structural Seed comment, not *decided* in an ADR. See §6,
addendum row 1.

Consequence with no home: "Boot succeeds with the desktop natives shipped and fails with a
message that names the missing natives if they are absent." No AD, ADR or convention carries the
failure-message contract. Low risk (single module), recorded here so the E1 story keeps it.

### FR-2 Determinism from (tag, class, challenges, seed, Action list) — (a)

Governed by **AD-5** (one reseed per Input wait, `k` is the primary key), **AD-6** (the Run tuple
including the salt, `mix(salt, k)`, identity-hash order removed, render-thread draws routed, two-JVM
determinism test) and **ADR-0007** in full. The PRD tuple is extended by the salt; AD-6 states the
extension and ADR-0007's consequences own the cost ("the Run tuple grows by the salt").

The Brain half of the FR ("two Runs with the same tuple produce the same Action list") is held by
**AD-7** (`decide` deterministic given the generator the caller seeds from `mix(salt, k)`; the
Overlay's thinking budget delays a Decision and never changes it) and by ADR-0011's Replay
section.

### FR-3 Observer produces the Observation — (a)

Governed by **AD-2** (one Observation type, whitelist by construction) and **AD-3** (drawing
predicates only), realized by **ADR-0005** (the record tree, canonical binary codec, SHA-256 over
section hashes, schema version in the header) and **ADR-0006** (the per-rule table, one row per
visibility rule, each citing the renderer's line). The `actions` section is a field of the
Observation in both ADRs, so the leak and differential tests cover the valid-Action set as FR-3
requires.

### FR-4 ActionExecutor applies an Action — (a)

Governed by **AD-4** (`Hero.handle`, `Item.execute`, `Hero.rest`, `Hero.search`, window buttons;
validation against the Observation's `actions` section before touching state; rejection with a
reason) and **ADR-0013** ("Executing": the same list, on the UI-role thread, asserting thread and
validity first). ADR-0006's last table row assigns `validActions(observation)` to the
ActionExecutor and forbids computing it from game state.

The completeness test ("enumerates the game's hero-affecting inputs and asserts each maps to an
Action or is documented as unsupported") survives: ADR-0006's pre-mortem cites it, and ADR-0011
makes `unsupported` a first-class record kind and `verifiable` a first-class field, which is the
E5 consequence the FR names.

### FR-5 Random-action agent and throughput measurement — **(c)**

Dropped. The random valid-action agent has no module in the Structural Seed (`harness` lists the
scene, the drivers and the fairness suite; `rig` lists the Runner, SeedSets, Registration, Gsprt,
Results, Replay). The benchmark exists only as two passing references inside other ADRs'
consequences — ADR-0005's pre-mortem ("the E1 benchmark (FR-5) reports codec time separately")
and ADR-0011's consequences ("measured in the E1 benchmark beside the codec") — both of which
*consume* a benchmark the architecture never *creates*. No AD binds FR-5, and its partner NFR-3
is bound by no AD either (§3). SM-4, the E1 rung, rests entirely on this FR.

Recommendation: give `harness` a `RandomAgent` and a `Benchmark` in the Structural Seed, and add
FR-5 to AD-6's Binds or to a new AD.

### FR-6 Snapshot, restore, redetermination — (a)

Governed by **AD-9** ("hidden state never enters a rollout"; `Snapshot`, `BeliefSample` and
`Redeterminer` are `api` types declared in E1, `SnapshotStore` and its restore-and-replay test in
E1, the scrubber in E6; the rollout host asserts the scrubbed flag) and **ADR-0009** (the hidden-
element table, the scrub, the differential test, the swap-in-place host). The PRD's "the interface
is reserved in E1" and "no Search may run on a snapshot that still holds hidden state" are both
verbatim rules in AD-9.

---

## 2. Section 4.2 — Fairness enforcement (FR-7 to FR-13)

### FR-7 The Brain cannot depend on game code — (a)

Governed by **AD-1**, which is stronger than the FR: declared edges, a resolution-time check in
`brain/build.gradle`, an ArchUnit rule against `com.shatteredpixel..` and `com.watabou..`
(ADR-0003), *plus* an ArchUnit rule that `brain` uses no `java.io`, `java.nio.file`, `java.net` or
`java.lang.reflect`. AD-1 also states the FR's last consequence ("the Codex reaches the Brain as
JSON data loaded by the caller through `api` types, never as classes and never by the Brain
itself").

### FR-8 Leak tests — (a)

Governed by **AD-3** ("each row cites the game line the renderer uses and has a leak test") and
**ADR-0006** (whose decision table is the row set). The Structural Seed puts `LeakTest` per
ADR-0006 row in `harness/src/test`. The naming convention pins the class suffix (`LeakTest`).

### FR-9 Differential test — (a), with a lost consequence

Governed by **AD-2** and **ADR-0005** (section hashes exist precisely so the differential test can
name the differing section) and by ADR-0006's decision to exclude the seed "so a Brain cannot
fingerprint published seeds (FR-9)".

Lost consequence: the FR has *three* forms and the architecture carries one. The byte form is in
the Structural Seed as `DifferentialTest`. The **behavioral form** ("a Brain given both worlds
produces identical Decisions until the Observations diverge" [E4]) and the **seed-permutation
form** ("also runs on real seeds from `standard` with hidden identities permuted per seed, so that
no component can have memorized seed-to-identity" [E4; required for any learned component, E9])
have no home in the spine, in ADR-0005, in ADR-0006, or in the E4 row of the addendum's epic map.
The seed-permutation form is the only defense the PRD has against a memorizing learned component,
which makes it the more load-bearing of the two.

### FR-10 Toggle tests — (a)

Governed by **AD-3** (ADR-0006 has explicit rows for mind vision, blindness and magic mapping) and
the Structural Seed's `ToggleTest`.

### FR-11 Oracle mode gating — (a), with a lost consequence

Governed by **AD-3** (`OracleObserver` is the only extension and is refused by the Rig),
**ADR-0005** (an `oracle` boolean in the Observation header, true only for an `OracleObserver`
Run), **ADR-0006** ("Oracle mode is a separate class, `OracleObserver`"), **ADR-0011** (`oracle` in
the log header), **ADR-0012** ("the Rig refuses ... any Run with `oracle` true"), and **AD-12**
("the launcher owns the Profile and the oracle flag"). The Overlay marking is in AD-12's
`DESIGN.md` reference (Oracle border component).

Lost consequence: **"Label Runs for E9 use seeds disjoint from every committed Seed set."** The
word *disjoint* appears nowhere in the spine or in ADRs 0005–0013. Nothing in the Seed-set design,
the Registration, or the Run-log header records or enforces disjointness, and no E9 revisit point
holds it. This is the second of the two guards against a learned component memorizing seeds (the
first is FR-9's seed-permutation form, also lost).

### FR-12 Thread confinement — (a)

Governed by **AD-8** (three thread roles; Observer and ActionExecutor assert the UI-role thread on
entry), the Consistency Conventions Threads row ("every public method of `Observer`,
`ActionExecutor` and the Panel starts with a thread assertion"), and **ADR-0013**'s role table
(the Brain worker "never touches a game object, the scene, or the log file").

Minor: the Structural Seed's named fairness suite is "LeakTest per ADR-0006 row, DifferentialTest,
ToggleTest, DeterminismTest (two JVMs), HooksTest" — the thread-confinement test is not in the
list even though NFR-1 requires it on every pull request. See §3 NFR-1.

### FR-13 Search leak test — (a)

Governed by **AD-9** (the rollout host asserts the scrubbed flag) and **ADR-0010**, whose
measurement table makes the search leak test one of the three E6 gates and whose choice rule
makes it a veto: "If the search leak test fails, no engine-rollout design ships until it passes."

---

## 3. Section 4.3 — Codex and knowledge (FR-14 to FR-18)

### FR-14 Generate the Codex from the pinned tag — (b), with a lost constraint

(b): the spine's **Deferred** table says so explicitly — "Codex generation mechanics (reflection
per table, measured combat tables) — E2 stories; no cross-module divergence risk — E2". The reason
holds: `codex` depends on `core` only, emits JSON into `codex/<tag>/`, and reaches the Brain as
data through `api` (AD-1). Two E2 stories cannot diverge in a way another module can observe.

Lost constraint: **the Codex being seed-free.** FR-14's consequence — "generating it twice with
different seeds and different Profiles produces byte-identical output, and no Codex value depends
on a Run (a Codex leak test)" — is what makes the Codex *not a second door into the Brain*, which
is the reason 4.3's description gives for its existence. The string "seed-free" appears zero times
in the spine and zero times in ADRs 0005–0013; "Codex leak" appears zero times. The Structural
Seed's `codex` module lists "generators per table, completeness check, citation checker,
vocabulary diff" — no leak or determinism check. NFR-1 names the Codex leak test among the tests
that must run on every pull request, so the requirement is stated in the PRD twice and lands
nowhere. This is a parity guarantee, not a nicety: AD-1's "data only" rule is only safe if the
data is seed-free.

### FR-15 Codex drift check — (b)

CI concern, governed by ADR-0002's PR gate and the `:codex:generate` command in `CLAUDE.md`. No
cross-module contract: the check compares generated bytes to committed bytes inside one module.

### FR-16 Vocabulary diff — (b)

The Structural Seed names `vocabulary diff` inside `codex`; the second pinned source is a §11
dependency and a build-input decision, not an architectural one. No divergence risk: one producer,
one consumer (the lore classifier, E7).

### FR-17 Rules with citations and the codebase map — (b)

The Structural Seed names `citation checker` inside `codex` and `docs/rules/` in the layout;
ADR-0008 depends on it ("the citation checker (FR-17) flags a row whose `path:line` no longer
resolves") and ADR-0009 depends on it for the scrubber's bundle keys. Single producer, no
divergence risk.

Unheld sub-clause: "the set of claims the Brain relies on is enumerated in the Brain's own Rules
index so 'every' is checkable." No module in the Structural Seed holds a Brain Rules index. Low
risk (a docs artifact), noted for the E2/E4 stories.

### FR-18 Lore pipeline — (b)

Deferred to E7 by the addendum's epic map. The spine reserves the ground: the layer table lists
`lore/` under Knowledge and the Capability map maps 4.3 to `codex`, `codex/<tag>/`, `docs/rules/`
and `lore/`. The frontmatter fields are in the addendum. No cross-module contract until E7.

Unheld constraints (both E7, recorded so they are not lost between now and then): "a claim that
references a seed or a specific dungeon layout is rejected at intake", and "every Brain heuristic
links to a Lore entry or a Rule (a Rule is the stronger form) with a Tier". The first is a parity
guard of the same family as FR-35's Playbook validation, which is itself contradicted (§5).

---

## 4. Section 4.4 — Rig (FR-19 to FR-26)

### FR-19 Parallel runner — (a)

Governed by **AD-6** ("one process hosts one Run"; fresh Profile per Run in its own working
directory), **AD-11**, the Structural Seed (`rig`: "Runner (process per Run)"), and the Config
convention, which fixes the CLI (`--brain --baseline --seeds --parallel --out --oracle`).
Per-Run result fields are fixed by ADR-0011's `end` record (`win`, `ascended`, `score`, `depth`,
`turns`, `cause`, `bosses`) and its `runs.jsonl` line (run-id, outcome, chain, log path).

Note: the Config convention's flag list includes `--oracle`, while §9 of the PRD and ADR-0012 both
say Oracle mode **cannot** be enabled through the Rig CLI, and the addendum's CLI sketch omits it
(`--seed-start` is present there and absent from the spine's list). The spine's flag list is the
launcher's and the Rig's merged; as written it reads as if the Rig accepts `--oracle`. A
one-word fix; recorded as a cross-reference defect (§8).

### FR-20 Seed sets — **(c)**

The mechanism is governed — **AD-11** requires the Seed set name and version in every
Registration and says "the runner refuses `holdout` for development and any Oracle Run", which is
exactly FR-20 — but **ADR-0012 contradicts it**: "The Rig refuses a Registration whose Seed set is
`holdout` and any Run with `oracle` true." Unqualified, that makes `holdout` unusable for any
purpose, which forbids the one use FR-20 grants it ("may be used only to publish a release-level
number or the SM-1 claim, at most once per Brain version") and therefore forbids **SM-1**, the
program's primary success metric, from being claimed on `holdout` at all. AD-11's qualifier
("for development") is the correct rule; ADR-0012 dropped it.

Also lost from FR-20: the **initial Seed-set sizes** (`smoke` 25, `standard` 500, `holdout` 500,
`bosses` 100, `goo` 400 Warrior triples sized so a 75% Goo rate clears a 70% Wilson lower bound)
and the clause "revisable by ADR once throughput is measured, together with SM-3's bound". No AD,
no ADR and no Deferred row carries a size, the sizing rationale, or the revision trigger — even
though ADR-0012's own Registration list requires "Seed set and version" and the addendum's
mechanism table explicitly assigned "Seed-set sizes" to the E3 statistics ADR, which did not take
them (§6). The `goo` set's size is the E4 gate (SM-3) and is derived from a statistical bound;
losing it loses the derivation.

### FR-21 Sequential test — (a)

Governed by **AD-11** ("the Sequential test is the Per-pair GSPRT of ADR-0012 with the e-process
as the calibrated alternative") and **ADR-0012** in full: the Per-pair statistic, burn-in `n0`,
`nmax`, calibration by simulation on the Rig's own outcome distribution, recalibration per tag,
`smoke` labelled a direction check, and the report list (log-likelihood trace, pair count, pair-
score mean with an interval, the measured within-pair correlation, win rate with a Wilson
interval, survival curve, median death depth, boss staircase, fixed-sample cross-check).

Unheld: the identity of the *deliberately worse Brain* ("the E3 reference is the random agent with
the descend Action removed ... from E4 on it is the Baseline with its heal Policy removed"). This
is a test fixture, so no divergence risk, but it is the acceptance criterion of SM-5 and should be
in the E3 story.

### FR-22 Pre-registration — (a), with a lost consequence

Governed by **AD-11** (a comparison runs only under a committed Registration; the Registration's
field list is in both AD-11 and ADR-0012, including the Hypothesis id, the bounds, the Seed set
version, the salts, both Brains' commits, budget and machine class) and by ADR-0011's `header`
record, which carries `registration` (id or null).

Lost consequence: **the anti-peeking ledger.** FR-22's second half — "The Rig keeps a local ledger
of every comparison it has run for a Brain commit on a Seed set, and every Results page states how
many prior Runs of that pair the ledger holds, so a register-after-peeking pattern is visible even
though it cannot be prevented" — survives nowhere. AD-11's Results contents are "the chain, the
trace, the measured pair correlation and the command that reproduces it"; ADR-0012's report list
does not include it; ADR-0011's per-invocation outputs are `runs.jsonl`, `summary.json`,
`sprt.json` with no ledger; the addendum's Results-page field list omits it too, and the PRD's own
FR-25 field list *includes* it ("the measured paired-seed correlation and the ledger count of
prior Runs"). SM-1 also requires it ("the Results page records the ledger count"). The Rig has no
component that persists across invocations, which is why this fell out — that is exactly the gap
to close.

### FR-23 Run logs with a Hash chain — (a)

Governed by **AD-5** (exactly one Run-log record per Input wait; `k` is the primary key; the Run
log, not the Observation, records the turn as `Statistics.duration + Actor.now()` in fixed-point
thousandths), **AD-11**, and **ADR-0011** (record kinds, chain formula, canonical JSON). The
`actor` field (`bot`, `human`) is in ADR-0011's `wait` record as the FR requires.

### FR-24 Replay with verification — (a)

Governed by **AD-6**, **AD-11** and **ADR-0011**'s Replay and Verification paragraphs, including
the FR's exact failure mode ("stop with 'unverifiable from k' at an `unsupported` record") and the
first-divergent-wait report (section hashes are compared on mismatch to name the section).
`ReplayDriver` is in the Structural Seed. ADR-0007's option 15 hook (render-thread draws routed to
the base generator) is called out as "required for FR-24".

### FR-25 Results publication, including negatives — (a), partial

Governed by **AD-11** (the Results page carries the chain, the trace, the measured pair
correlation and the reproducing command), **ADR-0012** (the fuller report list), and **ADR-0002**
(the nightly `smoke` job under a standing Registration that opens or updates one results pull
request on `rig/nightly`, and the `rig-pr` job required for brain changes from E3 onward).

Two clauses do not survive intact: the **ledger count** (see FR-22), and the discipline word
**"whatever its outcome (accept, reject, undecided)"**. AD-11's rule is phrased as a gate on
publishing *a number* ("every published number is a Registration plus a Run log"); nothing states
the converse obligation — that a rejected or undecided comparison must be published anyway. That
obligation is the difference between a results page and a highlight reel, and it is the tone the
PRD sets in 4.4's description ("whatever the outcome"). SM-C6 counterbalances the opposite
failure, and the addendum's headline-metric section keeps negatives "retained for the record", so
the intent is well attested — it just has no rule.

### FR-26 Death replay gallery — **(c)**

Dropped. No AD binds it (AD-11 binds FR-19 to FR-26 as a range, but no AD-11 rule mentions a
gallery), no ADR decides it, no Structural Seed component holds it, and no Deferred row schedules
it. Its single trace in the architecture is a mis-cited aside in ADR-0009's decision outcome:
"Snapshots are never written to disk by the Rig except on request (the death gallery, **FR-25**)"
— which points at the wrong FR while being the only place the feature is named. The addendum's
epic map splits it across E3 and E4 ("the E3 half of FR-26", "the E4 half of FR-26"), so the
requirement is scheduled and unarchitected. Its E4 half (the Runs with the largest Evaluation drop
in their final 10 Input waits) needs an Evaluation trace in the Run log that ADR-0011's `wait`
record does not carry — a real, checkable gap, not a naming one.

---

## 5. Section 4.5 — Brain (FR-27 to FR-36)

### FR-27 Re-plan every Input wait — (a)

Governed by **AD-7** (`decide` and `update` are pure; "the Brain holds no game object and no
thread; state lives only in the returned `Belief`"; a Decision is tagged with its `k` and a stale
one is never executed) and **AD-5**. The FR's second consequence is verbatim in the Consistency
Conventions Errors row: "the Brain throwing produces `Decision.wait` with the exception class in
the Run log and the Panel; the game never crashes because of the Brain."

### FR-28 Arbitration of Policies — (a)

Bound by **AD-7** and realized in the Structural Seed (`brain`: Beliefs, Policies, Arbitration,
Evaluation). The Decision's `policy` field is fixed by ADR-0011's `wait` record, which is what
makes "the active Policy and its reason are part of the Decision" checkable. "Entry predicates
that evaluate without side effects and without simulation" is a `brain`-internal rule with no
cross-module consequence.

### FR-29 Beliefs — (b), with a lost consequence

(b) because the Belief's *content* is one module's business: AD-7 fixes the only thing another
component sees (an `api` value whose hash is in the Run log), and ADR-0011 fixes the log field
(`belief`: SHA-256 of the serialized Belief; the full Belief only with `--log-beliefs`). Two E4
stories cannot diverge observably.

Lost consequence: **"Belief update is a pure function of (previous Beliefs, Observation) with its
own leak tests."** The purity half is AD-7. The leak-test half is not in the Structural Seed's
fairness suite, not in ADR-0006 (whose rows are Observer rows), and not in NFR-1's enumerated
suite. Since the Belief is where an unfair inference would accumulate across waits, this is a
fairness test with no owner.

### FR-30 safeTest — (b)

`safeTest` is named in the Structural Seed's `brain` line. Pure function of Observation, Belief
and Codex data; no cross-module contract; E4 story.

### FR-31 Scripted baseline Policies — (b)

The Policy list is `brain`-internal. Its gate (75% Goo on the `goo` set with a 70% lower bound) is
SM-3 and lives in the PRD and the epic map; the measurement machinery is ADR-0012's. No divergence
risk — but see FR-20 for the loss of the `goo` set's size, which is the denominator of this gate.

### FR-32 Decision output — (a)

Bound by **AD-7** and fixed field-for-field by **ADR-0011**'s `wait.decision` (`goal`, `chosen`
{action, score}, `alternatives` — at most three, each {action, score, why} — `flags`, `policy`),
with scores as integers in ten-thousandths to satisfy the "no floats in hashed data" convention.

Tension worth one line: FR-32 requires the Decision to be phrased "in Codex vocabulary"; ADR-0011
says "strings are the Observation's own display strings". Those are two different vocabularies
(the Observation's strings are what the screen shows; the Codex's are generated names). See §7,
the Explain voice.

### FR-33 Evaluation — (a)

Bound by **AD-7**. "The Evaluation is a hand-tuned weighted function ... its weights are data so
the Rig can tune them" is realized by the Structural Seed's `Evaluation` in `brain` and by
ADR-0012's Registration field "config hash" per Brain. SPSA tuning is scheduled by the addendum's
epic map (E6) and by the PRD's own provenance line.

**But**: "its weights are data" collides with the Consistency Conventions Config row — see FR-35.

### FR-34 Tactical Search — (a)

Governed by **AD-9** ("the search design is chosen by ADR-0010's measurements and the Rig, never
by this spine"), **ADR-0010** (the three measurements, the four-step choice rule, the fairness rule
for any winner), and the spine's Deferred table (first row, revisit at E6). `brain/search/` is
reserved in the Structural Seed. The FR's own consequence — "rollouts on the raw saved game are
impossible by construction (FR-6)" — is AD-9's rule.

### FR-35 Playbooks as data — **(c)**

Contradicted. The Consistency Conventions **Config** row states: "Launcher flags and Rig CLI ...;
**no config files besides Seed sets and Registrations**, which are committed." FR-35 requires
"per-class, per-boss, item-identification, and upgrade-allocation strategy [to live] in versioned
data files the Rig tests", and PRD §9 lists **Playbook files** as a versioned public surface
("versioned with the Brain; a Playbook change is a pull request with Results"). FR-33's tunable
weights are a second committed data file the same row forbids. The rule as written outlaws two
requirements it was not aimed at.

The word "Playbook" appears zero times in the spine and zero times in ADRs 0005–0013; the
Structural Seed reserves `search/` for E6 but reserves nothing for Playbooks, and NFR-1 requires
an adversarial fairness review for any pull request touching Playbooks — a review trigger for an
artifact the architecture does not contain. Also lost with it: "a Playbook that references a seed
or a layout fails validation", the parity guard on the format.

Fix is small: amend the Config row to "no config files besides Seed sets, Registrations,
Evaluation weights and Playbooks, all committed", and give `brain` a `playbooks/` line in the
Structural Seed with the validation rule attached.

### FR-36 Strategy log — (a)

Governed by **ADR-0011** (`policy` in the `wait.decision` record, which the Run log stores) and
**AD-12** (the Panel's scrolling decision log; ADR-0011's consequence "the Overlay's Decision log
is a view over the same records").

---

## 6. Section 4.6 — Overlay (FR-37 to FR-47)

### FR-37 EmbeddedDriver and launcher — (a), with a lost consequence

Governed by **AD-12** ("the launcher owns the Profile and the oracle flag"), **AD-8**/**ADR-0013**
(the EmbeddedDriver as the UI-role thread on the render thread; "`RUN OVER` and level changes are
seen as the scene being destroyed and recreated (`InterlevelScene`), so the driver re-attaches
through the scene seam hook each time and keeps `k` across floors"), and **ADR-0008** hook rows 3
and 4. "The observation point at the hero's Input wait is a listed Hook" is hook row 3;
"Two Overlay Runs never share a Profile" is AD-6.

Lost consequence: **"Saving and quitting inside a Run records the boundary in the Run log; a
resume through the launcher re-attaches in PAUSED."** ADR-0011's record kinds are `header`,
`wait`, `prompt`, `mode`, `unsupported`, `end` — there is no save/resume boundary record, and
`mode` is a Mode change, not a session boundary. The PRD ties this to open question 10, which is
still open (§9), so the record kind and the question fall together.

### FR-38 Native Panel — (a)

Governed by **AD-12** (a Noosa `Component` using `Chrome`, `renderTextBlock`, `RedButton`,
`Icons`, `ScrollPane`, attached through the `GameScene` seam hook; it reads only Observations and
Decisions), **AD-8** (every Panel write on the UI-role thread; the Brain never touches a Panel
object), **ADR-0013** (Modes, and the accessor row that makes the toolbar and inventory pane
reachable), and `EXPERIENCE.md`'s state table, which AD-12 and FR-38 both make binding.

Cross-reference defect: FR-38 says "the spine's Layout section carries the sizes"; **the spine has
no Layout section.** AD-12 redirects to `DESIGN.md` Layout, and `DESIGN.md` §"Layout & Spacing"
does carry them (width target 200 / minimum 160, padding 4, rows 2, sections 6, the collapse
thresholds, the 427×240-at-zoom-3 worked example, the horizontal camera re-offset after every
`GameScene.layoutTags`). No divergence risk — the numbers exist and one document owns them — but
the PRD's pointer resolves to nothing and should be repointed at `DESIGN.md`.

### FR-39 Controls and speed modes — (a)

Governed by **AD-12** (hotkeys are `SPDAction`s with defaults F6 to F11) and **ADR-0013**'s Speed
modes paragraph, which realizes every consequence: the future polled each frame, `Next Step`
waiting for the key, `Run N` and Human play speed scheduling execution `interval` seconds later on
the render thread's clock, `Fast as it can` executing on the completion frame with hook row 7
shortening the sprite motion interval, and "a budget overrun sets `THINKING` and nothing else; the
computation is never cancelled (AD-7)". "A Run starts in PAUSED with speed mode Next Step" is in
`EXPERIENCE.md`'s state table.

But the *hook* FR-39 and FR-42 need is over budget — see FR-42.

### FR-40 Interjection semantics — (a)

Governed by **AD-4**, **AD-5** ("every transition happens only at an Input wait") and
**ADR-0013**'s Modes paragraph, which is a line-by-line realization: PAUSED installs its own
`CellSelector.Listener` and deactivates the toolbar and inventory pane; HUMAN restores the game's
listener, records each human Action from `Hero.curAction` after `handle` and from the
`Item.execute` notification, and calls `update` at every Input wait; Take over and Hand back apply
at the next Input wait.

### FR-41 Map highlights — (b)

`DESIGN.md`'s Components section owns the Map highlight (planned path, target, considered cells,
never filled, never over sprites, oracle outlines only under the flag). Low divergence risk: one
document, one drawing surface, one E5 story.

Noted gap, small: AD-12's rule describes the Panel as a docked `Component` added through the scene
seam and says nothing about a second drawing layer in the dungeon view, and FR-41's testable
clause "never draws them in HUMAN Mode" has no home in the spine, `EXPERIENCE.md`'s state table or
ADR-0013's Modes paragraph.

### FR-42 Hotkeys — **(c)**

Contradicted by the hook ledger. AD-12 requires hotkeys to be `SPDAction`s with defaults F6 to
F11, and PRD §10's expected-hook list names "the key-binding registration" as one of the eight.
**ADR-0008's v1 ledger has no key-binding row**: its eight are (1) `settings.gradle`, (2)
`Hooks.java`, (3) the `GameScene` seam, (4) read-only accessors, (5) `Hero.ready`/`interrupt`
guards, (6) identity-order removal, (7) sprite-wait bypass, (8) render-thread draw routing. Rows 6
and 8 are ADR-0007's two new RNG hooks, which were not in PRD §10's expectation; they consumed the
budget line the key-binding hook was holding.

Adding entries to upstream's `SPDAction` enum and to its key-binding settings screen is an upstream
edit, so FR-42 cannot ship without a ninth row — and ADR-0008 states the consequence itself: "If
the E1 touchpoint audit needs a ninth, the PRD's budget is revisited in an ADR, not by adding a
row." The budget of eight therefore survives as a number while the requirement it was budgeting
for does not. FR-42's own fallback ("if the Hook is not small, v1 ships buttons only and hotkeys
return to E8") is the escape hatch, but nothing in the architecture has taken it: AD-12 states the
F6–F11 defaults as an adopted rule.

Decide one of: (i) an ADR raising the budget to nine or ten with the reason; (ii) invoke FR-42's
fallback and move hotkeys to E8, striking the F6–F11 clause from AD-12; (iii) fold the key-binding
registration into hook row 3 or 4 as a same-kind site, if it truly is one.

### FR-43 Oracle overlay marking — (a)

Governed by **AD-12**, **ADR-0005** (the `oracle` header field), **ADR-0006** (`OracleObserver`),
and `DESIGN.md`'s Oracle border component (a 2-pixel frame plus the `ORACLE` label in the Mode
strip, drawn above everything; unseen enemies outlined in the oracle color only under the flag).

### FR-44 Explain view (v2) — (b)

Deferred to E8 by the epic map. The architecture leaves room deliberately: ADR-0011's pre-mortem
anticipates it ("a Decision field grows (Explain's full reasons, search statistics) and bloats the
log. Mitigation: optional sections behind flags (`--log-beliefs`, `--log-search`)"). No
cross-module contract until the Evaluation terms exist (E6). The *voice*, however, is lost — §7.

### FR-45 Pause-on conditions (v2) — **(c)**

Same defect as FR-42, one epic later. FR-45 requires its conditions to be "set in an Overlay
section of the game's own settings screen (**one Hook**)"; the eight-row ledger has no settings-
screen row and no spare row. E8 sits outside v1, so the v1 budget is not formally breached — but
the PRD's §10 list and ADR-0008's ledger together imply that v1's eight rows are the whole
long-term surface, and two named requirements need rows that do not exist. Recorded together with
FR-42 as one decision.

### FR-46 Replay scrubber and Beliefs view (v2) — (b)

E8, and the ground is held: ADR-0009 keeps "one snapshot per Input wait ... in memory for Take
over and the v2 scrubber", and ADR-0011 states "the v2 scrubber needs no second format". The
`--log-beliefs` flag exists for the Beliefs view. No divergence risk.

### FR-47 Coach mode and autoexplore (v2) — (b)

E8 (or its own epic — open question 7, still open). The mechanism is a Mode the Panel already has
room for and a Decision the Brain already produces without executing. No cross-module contract
until E8. The *voice* is lost — §7.

---

## 7. Sections 4.7 and 4.8 — Upstream and program hygiene (FR-48 to FR-53)

### FR-48 Pinned tag and hook registry — (a)

Governed by **AD-10** (one-line sites, the `// shatterfish-hook:<id>` marker, a row in
`docs/UPSTREAM.md`, a test counting markers against rows, the v1 budget of eight) and **ADR-0008**
in full (the `Hooks.java` registry, vanilla-equivalence test, the counting-and-budget test, the
change guards, the field-visibility accessor pattern, the ledger). SM-C5 (fewer hooks is better)
is served by the ledger's per-epic column.

### FR-49 Mobile modules opt-in — (b)

Hook row 1 (`settings.gradle`) already exists and is listed; the command
(`./gradlew build -Pshatterfish.mobile=on`) is in `CLAUDE.md`. Single site, already shipped, no
divergence risk.

### FR-50 Upgrade procedure and timing — (b)

Process, not structure. The nine steps live in `docs/UPSTREAM.md` and the `upstream-sync` skill;
the architecture holds the two steps that bind it — ADR-0008's change guards ("the `upstream-sync`
skill's step 9 re-reads each site after a merge") and ADR-0012's recalibration rule ("bounds are
recalibrated per Upstream tag and per Composite-outcome change (a new Registration, never an
edit)"). ADR-0005's schema version and ADR-0011's `v`/`obsv`/`tag`/`profile` refusal cover the
Replay side of an upgrade. No divergence risk.

### FR-51 Docs site — (b)

ADR-0004 (documentation system) owns it; the Capability map routes 4.7 to `docs/`, `.github/`,
`.claude/` under AD-10, ADR-0002 and ADR-0004.

### FR-52 Decisions and ideas are recorded — (b)

ADR-0001 (record architecture decisions) and `CLAUDE.md` own it. The annual learned-frontier review
has a home in the spine's Deferred table ("Learned components — optional E9; annual review of the
learned frontier — 2027").

### FR-53 Issues mirror Epics and Stories — (b)

The `sync-issues` skill owns it; `CLAUDE.md` §"Where things live" is the contract. Process, no
architectural surface.

---

## 8. Section 8 — Non-functional requirements

### NFR-1 Fairness — (a), with two narrowings

Governed by **AD-1**, **AD-3**, **AD-9** and the Structural Seed's fairness suite, and reinforced
by ADR-0006's one-test-per-row rule and ADR-0010's veto.

Two narrowings, both worth closing:

1. **The suite is enumerated twice and the lists differ.** NFR-1 requires "the leak, differential
   (both forms), toggle, thread-confinement, boundary (ArchUnit and classpath), Codex leak, and
   determinism tests" on every pull request. The Structural Seed lists "LeakTest per ADR-0006 row,
   DifferentialTest, ToggleTest, DeterminismTest (two JVMs), HooksTest". Missing from the
   architecture's list: the differential test's second (behavioral) form, the thread-confinement
   test, the boundary test (AD-1 has the rules but no named test in the suite), and the Codex leak
   test (which has no home at all, §3 FR-14). Present in the architecture and absent from NFR-1:
   `HooksTest`, which is a fine addition.
2. **The adversarial-review trigger is narrower.** NFR-1 triggers a fairness review on a pull
   request touching "the Observer, the ActionExecutor, the Brain, the `api` schema, the Codex
   generator, Playbooks, Lore intake, or the Replay tool". `CLAUDE.md`'s rule (and the spine's
   implicit surface) is "any diff near `Observer`, `ActionExecutor`, or `brain`". The `api` schema,
   the Codex generator, Playbooks, Lore intake and the Replay tool are outside it — and those are
   precisely the surfaces where a leak would not look like a leak.

### NFR-2 Reproducibility — (a), with a lost testable consequence

Governed by **AD-6** and **AD-11**, realized by **ADR-0007** (the tuple, the salt, `mix(salt, k)`,
the two-JVM determinism test), **ADR-0011** (the hash chain, the tamper check, the reproduction
check, "a Results page carries both results") and **ADR-0005** (byte-identical serialization
across JVMs and operating systems is its first decision driver).

Lost consequence: **"a nightly job Replays a random published Run and compares Hash chains across
Windows and Linux."** ADR-0002's nightly job runs the full seed set plus `build` on
`windows-latest`; it does not Replay a published Run, and it does not compare chains across the
two platforms. ADR-0005 names the cross-platform Replay check as the driver for its whole codec
design ("the nightly cross-platform Replay check, NFR-2") — so the architecture designed *for* a
job that nothing schedules. Cheapest close: one row in ADR-0002's job table.

### NFR-3 Headless throughput — **(c)**

In the spine's `binds` list and bound by no AD. The spine contains no throughput rule, no
benchmark, no Runs-per-minute or Input-waits-per-second concept, and — more importantly — none of
NFR-3's *actual* requirement, which is not a rate at all: "the `smoke` direction check of UJ-1
(two Brains over `smoke`) fits within a working session on the development laptop and the
`standard` acceptance run fits overnight", met "by choosing the number of parallel processes and
the Seed-set sizes (FR-20), not by assuming an engine rate". That is a sizing constraint that
couples FR-19's `--parallel`, FR-20's Seed-set sizes (also lost, §4) and the choice of where
`standard` runs (deferred, §9 OQ11) — three things the architecture treats independently. The
Deferred table's last row is about the *host* of `standard`, not its cost.

With FR-5 (c) and NFR-3 (c) together, the E1 rung SM-4 has no architectural owner.

### NFR-4 Overlay responsiveness — (a)

Governed by **AD-7** (the thinking budget delays a Decision and never changes it), **AD-8**, and
**ADR-0013** (the render thread is never blocked; the future is polled without blocking; the
deadlock rule; "a budget overrun sets `THINKING` and nothing else; the computation is never
cancelled"). Configurability of the budget is ADR-0012's Registration field ("the per-Decision
budget for the Overlay-relevant comparisons") and ADR-0010's ("the budget itself is a Registration
field, not a constant in this ADR").

### NFR-5 Upstream upgrade — (b)

Process; see FR-50. AD-10 and ADR-0008 hold the parts that bind the architecture (hook
re-verification, the counting test, the citation checker), ADR-0012 holds the recalibration, and
ADR-0002 holds the CI shape it runs under. No divergence risk.

### NFR-6 Documentation currency — (a), mis-bound

The requirement is met — the Capability map routes 4.7 to ADR-0002 and ADR-0004, `CLAUDE.md`
carries "docs and ADRs change in the same PR as the code; generated files are never hand-edited",
FR-17's citation checker enforces `path:line`, and ADR-0002's PR gate builds the docs strictly.

But **AD-12's `Binds` line reads "FR-37 to FR-47, NFR-6"**, and NFR-6 is *Documentation currency*.
AD-12 is the Overlay rule; binding it to the documentation NFR is a mis-citation. The plausible
intent was the PRD's non-negotiable #6 (Native UI) from `BOOTSTRAP-PROMPT.md`, whose numbering
collides with §8's. Two costs: NFR-6 appears bound when its real home is a Capability-map cell,
and AD-12 appears to bind an NFR it has nothing to do with. Fix the `Binds` line and add NFR-6 to
the Capability map's 4.7 row explicitly.

### NFR-7 Portability — **(c)**

Outside the spine's `binds`. Partially rescued in passing: ADR-0007's driver "the same tuple must
give identical Observation hashes on Windows and Linux (NFR-2)" and ADR-0002's job table (ubuntu
PR gate, `build` on `windows-latest` nightly, with the OS matrix rejected for the PR gate for a
stated reason). Not rescued: NFR-7's actual sentence — "Windows and Linux are supported for the
Harness and Rig (CI on Linux, nightly on Windows); macOS is best effort" — and specifically the
macOS best-effort position, which appears nowhere and which a contributor will ask about. "macOS"
appears zero times in ADRs 0005–0013 and zero times in the spine.

### NFR-8 Privacy and network — **(c)**

Outside `binds`, and unheld anywhere: "Shatterfish makes no network calls at runtime and collects
no telemetry; the Rig and Overlay work offline." The words "network" and "telemetry" appear zero
times in the spine and zero times in ADRs 0005–0013. AD-1 forbids `java.net` **in `brain` only**,
and for a different reason (the Observation must be the Brain's only channel). Nothing constrains
`harness`, `rig`, `overlay` or `codex`.

This is the cheapest lost requirement to restore and the one most likely to be violated by
accident (a metrics library, a crash reporter, an update check pulled in transitively by a
dependency). It has an obvious home: an ArchUnit rule in the shared test conventions, plus a
dependency check — the same shape AD-1 already uses.

### NFR-9 Observability — **(c)**, and contradicted

Outside `binds`, and directly contradicted. NFR-9: "Every Run log, Results page, and strategy log
is **plain text (JSONL or Markdown) that a person can read without tooling**." ADR-0011's decision:
"**File**: `<run-id>.jsonl.gz`", and AD-11 restates it — "every Run writes the **gzip** JSONL log of
ADR-0011". A gzip file is not readable without tooling; that is the whole point of the compression.

The tension is resolvable and worth resolving deliberately rather than by omission: ADR-0011's
consequences already note "gzip per file costs CPU on the Rig; measured in the E1 benchmark", so
the compression is not yet justified by a measurement (and the benchmark that would justify it is
FR-5, itself dropped). Options: keep `.gz` and amend NFR-9 to "plain text, gzip-framed, with a
one-command decompression documented on the methodology page"; or make compression a Rig flag with
uncompressed as the default for published Runs. Either way the choice belongs in ADR-0011, not in
a silent divergence.

---

## 9. Quiet requirements the AD structure lost

The FR or NFR may be governed; these are the specific tones, constraints and testable
consequences that no artifact now carries. Ordered by how much a later story could break without
noticing.

1. **The Codex being seed-free.** FR-14's Codex leak test and its byte-identical-across-seeds-and-
   Profiles property. "seed-free" and "Codex leak": zero occurrences in the spine and in ADRs
   0005–0013, despite NFR-1 listing the Codex leak test among the per-PR suite. AD-1's "the Codex
   reaches the Brain as data" is only a parity guarantee if the data is seed-free.
2. **Oracle labels on disjoint seeds.** FR-11's "Label Runs for E9 use seeds disjoint from every
   committed Seed set". "disjoint": zero occurrences. No Seed-set, Registration or Run-log field
   records or enforces it.
3. **FR-9's behavioral and seed-permutation differential forms.** The architecture carries the
   byte form only. The seed-permutation form ("real seeds from `standard` with hidden identities
   permuted per seed, so that no component can have memorized seed-to-identity") is the PRD's only
   defense against a memorizing learned component and is a stated precondition of E9.
4. **The anti-peeking ledger.** FR-22's per-(Brain commit, Seed set) ledger and FR-25's/SM-1's
   "ledger count of prior Runs" on the Results page. Absent from AD-11's Results contents,
   ADR-0012's report list, ADR-0011's outputs and the addendum's field list. The Rig as designed
   has no state that survives an invocation.
5. **The hook budget of eight.** Kept as a number (AD-10, ADR-0008's budget test) and spent
   without a key-binding row, so FR-42's hotkeys and FR-45's settings section have no hook. The
   constraint survives; the requirements it was protecting do not. See FR-42.
6. **The Overlay as a visibly separate instrument.** AD-12 keeps "instrument built from the game's
   toolkit" and the toolkit list, and drops the separateness: translucent over the dungeon, never
   over the game's own HUD, the horizontal camera re-offset re-applied after every
   `GameScene.layoutTags`, the collapse-to-Mode-strip rule, and the respect for the game's
   interface-size setting. All of it lives in `DESIGN.md` alone, and FR-38's pointer to "the
   spine's Layout section" resolves to nothing (the spine has no Layout section). The numbers are
   safe; the *reason* for them — that a player must never mistake the Panel for the game — is
   stated in no binding artifact.
7. **The Explain voice.** "Explain" appears zero times in the spine; "Codex vocabulary" zero
   times; "one plain sentence" zero times. FR-32 requires Decisions phrased in Codex vocabulary
   while ADR-0011 specifies "the Observation's own display strings"; FR-39's Explain control,
   FR-44's expansion and FR-47's coach voice ("in Codex vocabulary and at most one plain
   sentence") have no owner. This is the difference between an instrument that explains itself and
   a debug dump.
8. **"Whatever the outcome."** FR-25's obligation to publish rejects and undecideds. AD-11 gates
   publishing a number on a Registration; nothing obliges publishing a negative. SM-C6 guards the
   opposite failure only.
9. **NFR-2's cross-platform nightly Replay.** ADR-0005 names it as the driver for the whole codec
   design; ADR-0002's nightly runs a Windows `build` instead. Designed for, never scheduled.
10. **FR-20's Seed-set sizes and their revision trigger** (`smoke` 25, `standard` 500, `holdout`
    500, `bosses` 100, `goo` 400 with its 70%-lower-bound derivation; "revisable by ADR once
    throughput is measured"). The `goo` size is the denominator of the E4 gate (SM-3).
11. **FR-29's Belief-update leak tests.** The Belief is where an unfair inference would accumulate
    across waits; AD-7 makes it pure, nothing tests it for leaks.
12. **FR-37's save/resume boundary record.** ADR-0011 has no record kind for it (`mode` is a Mode
    change). Falls together with open question 10.
13. **NFR-1's wider fairness-review trigger** (`api` schema, Codex generator, Playbooks, Lore
    intake, Replay tool), narrowed in practice to Observer / ActionExecutor / `brain`.
14. **FR-18's intake guard** ("a claim that references a seed or a specific dungeon layout is
    rejected at intake") and **FR-35's Playbook validation** ("a Playbook that references a seed
    or a layout fails validation") — the same parity guard on two data formats, neither held.
15. **FR-41's "never draws map highlights in HUMAN Mode"** — no home in the spine,
    `EXPERIENCE.md`'s state table, or ADR-0013's Modes paragraph.

### Checked and found intact

- **The standard Profile.** Held in three places: AD-6 ("every Run starts in a fresh versioned
  standard Profile (English, intro off, all guide pages read, no bones, badges or rankings) in its
  own working directory"), ADR-0007's decision outcome (option 13, versioned in `harness`
  resources, with the consequence "the standard Profile makes Shatterfish's world slightly
  different from a first-time human's ... recorded in the Codex and in every Results page"), and
  ADR-0011's `header.profile` version field, which ADR-0011's Replay refuses to cross. Not lost.
- **The hook budget as a number** (AD-10, ADR-0008's counting-and-budget test, "a ninth forces an
  ADR"). Intact as a mechanism; see item 5 for what it now excludes.
- **Information parity as an architectural rather than intentional guarantee** (AD-1's four
  independent enforcement mechanisms, AD-2's whitelist-by-construction, AD-3's one-test-per-row).
  Strengthened relative to the PRD, not weakened.

---

## 10. Addendum: "Mechanism decisions deferred to ADRs"

Every row should now point at an ADR or a named later epic.

| # | Decision | Epic | Lands at | Verdict |
|---|---|---|---|---|
| 1 | Headless-scene design: harness-owned scene, no-op GL, Pixmap atlases; which static helpers need hooks | E1 | — | **No home** |
| 2 | Observation schema and hashing | E1 | ADR-0005 | ✓ |
| 3 | Observer visibility rules (FOV, mapped, secret doors, traps, heaps, identification, mind vision, blindness, magic mapping) | E1 | ADR-0006 | ✓ |
| 4 | RNG seeding strategy | E1 | ADR-0007 | ✓ |
| 5 | How hooks are guarded and tracked | E1 | ADR-0008 | ✓ |
| 6 | Parallelism: processes versus classloader isolation | E1 spike | Spine Deferred row 4 ("Classloader isolation … E1 spike report") | ✓ |
| 7 | Run log format | E3 | ADR-0011 | ✓ |
| 8 | Rig statistics (GSPRT, bounds, burn-in, calibration, recalibration, e-process; **Seed-set sizes**; where `standard` runs) | E3 | ADR-0012 + Spine Deferred rows 2 and 7 | **Partial** |
| 9 | Threading model for the Overlay | E5 | ADR-0013 | ✓ |
| 10 | Snapshot/restore and redetermination | E6 | ADR-0009 + Spine Deferred row 3 | ✓ |
| 11 | Abstract tactical model vs engine rollouts vs information-set search | E6 | ADR-0010 + Spine Deferred row 1 | ✓ |

**Row 1 — no home.** The only E1 mechanism decision without an ADR. The spine adopted its
starting position inside a Structural Seed comment (`harness.scene # HeadlessScene (harness-owned
Scene), no-op GL, Pixmap atlases`), and ADR-0008's hook row 5 and ADR-0007's "headless Runs have
no render or audio thread; the headless scene creates the same sprites and emotes as the game"
both build on it — so three artifacts depend on a decision with no rejected alternatives and no
pre-mortem. The row's second half ("which static helpers need hooks") is the E1 touchpoint audit
whose exit criterion ADR-0008's pre-mortem names as the hook count — the audit is scheduled, its
decision record is not. FR-1's (a) rests on this row.

**Row 8 — partial.** ADR-0012 decides the statistic, the Registration fields, calibration and
recalibration; the spine's Deferred table carries the bounds (row 2, "E3 calibration story per
ADR-0012") and the `standard` host (row 7, "E3 per PRD open question 11"). **Seed-set sizes** —
explicitly named in this row — are decided nowhere: ADR-0012 does not mention a size, no Deferred
row schedules them, and FR-20's own "revisable by ADR once throughput is measured" names an ADR
that does not exist. This is the same loss as §9 item 10 and it couples to NFR-3 (c).

---

## 11. PRD section 13 — open questions

| # | Question | State | Where |
|---|---|---|---|
| 1 | Headless-scene rate; does the actor thread ever block under fast-forward? | **Still open** | — |
| 2 | Paired-seed correlation and the real sample-size saving; is `smoke` at 25 informative? | Deferred with a revisit | ADR-0012 (reported beside every verdict, "the answer to research open question 2"); AD-11 requires it on every Results page; E3 |
| 3 | Leaf correlation, bias and disambiguation of SPD tactics | Deferred with a revisit | ADR-0010 measurement table; Spine Deferred row 1 (E6) |
| 4 | Does the seed determine anything beyond generation; which generators must the Harness seed? | **Answered** | ADR-0007 |
| 5 | Which think budgets, if any, does v1 publish? | **Still open** | Partial only: ADR-0010 and ADR-0012 make the per-Decision budget a Registration field, but "which, if any, v1 publishes" has no answer and no revisit point |
| 6 | Which human win-rate source calibrates "beats the median human"? | **Still open** | — (E7 in the PRD; no spine or ADR trace) |
| 7 | Is coach mode part of E8 or its own Epic? | **Still open** | — (epics workflow; the spine's scope line stops at E5 with E6 interfaces reserved) |
| 8 | What Score target and which class define the first SM-2 goal? | **Still open** | — (set at SM-1 per the PRD) |
| 9 | Fishtest's current default bounds, as text, for the E3 statistics ADR's starting values | Deferred with a revisit | ADR-0012 ports Fishtest's GSPRT (option 6) but explicitly declines defaults — "Nothing here has a default in this ADR; the first values are set by the E3 calibration story"; Spine Deferred row 2 |
| 10 | Does SPD's save file preserve enough generator state for a Replay to verify across save-and-resume? | **Still open** | ADR-0009 covers Shatterfish's own snapshot/restore, not the game's save across a resume; no Deferred row; FR-37's boundary record falls with it |
| 11 | Where does the `standard` Seed set run once its cost is known? | Deferred with a revisit | Spine Deferred row 7 ("ADR-0002 fixes the CI shape … the `standard` host is decided when its cost is measured — E3") |
| 12 | Session-10 codebase facts | Resolved before this reconciliation | `docs/codebase-map.md`; consumed by ADR-0005, 0006, 0007, 0013 |

**Still open: 1, 5, 6, 7, 8, 10** (six of the eleven live questions).

Of these, **1** and **10** are the ones the architecture is currently exposed to: OQ1 underwrites
NFR-3 (c) and FR-5 (c) and the addendum's row-1 gap, and OQ10 underwrites FR-37's missing Run-log
record kind. OQ5, 6, 7 and 8 are product questions with later natural homes (E6, E7, the epics
workflow, and the SM-1 event respectively) and carry no architectural exposure — but none of them
has a revisit point written anywhere, which is the fixable part.

---

## 12. Cross-reference defects (small, mechanical)

1. **AD-12 `Binds` cites NFR-6** (Documentation currency) where the Overlay rule means the
   Native-UI non-negotiable. §8 NFR-6.
2. **FR-38 points at "the spine's Layout section"**, which does not exist; the sizes are in
   `DESIGN.md` §"Layout & Spacing", where AD-12 correctly redirects. §6 FR-38.
3. **ADR-0009 cites FR-25 for the death replay gallery**, which is FR-26. §4 FR-26.
4. **ADR-0012's holdout refusal is unqualified** where AD-11's is correctly scoped ("for
   development"). §4 FR-20.
5. **The Config convention's flag list includes `--oracle`** while PRD §9 and ADR-0012 both say
   Oracle mode cannot be enabled through the Rig CLI; the addendum's CLI sketch also carries
   `--seed-start`, which the spine's list omits. §4 FR-19.
6. **The spine's `binds` line stops at NFR-6**, silently excluding NFR-7, NFR-8 and NFR-9. §8.

---

## 13. Recommended edits, smallest first

1. Spine, Consistency Conventions **Config** row: allow Evaluation weights and Playbooks as
   committed data files. (Unblocks FR-33 and FR-35.)
2. Spine, **AD-12 `Binds`**: drop NFR-6; add NFR-6 to the Capability map's 4.7 row.
3. Spine, `binds` frontmatter: extend to NFR-9, or state why 7–9 are out of scope.
4. Spine, add an **AD or a conventions row for no network at runtime** (an ArchUnit rule of AD-1's
   shape, applied to every module). Closes NFR-8.
5. ADR-0012: qualify the holdout refusal to development comparisons, matching AD-11.
6. ADR-0002: add a nightly row that Replays a random published Run and compares chains on both
   platforms. Closes NFR-2's lost consequence.
7. ADR-0011: decide the `.gz` question against NFR-9 explicitly (amend NFR-9, or make compression
   a flag with uncompressed the published default).
8. Structural Seed: add `RandomAgent` and `Benchmark` to `harness`, `playbooks/` to `brain`, a
   ledger to `rig`, and the Codex determinism/leak check to `codex`. Bind FR-5 and NFR-3 to an AD.
9. Spine, fairness suite line: bring it into agreement with NFR-1's list (behavioral differential,
   thread confinement, boundary, Codex leak) and widen the fairness-review trigger to NFR-1's
   surfaces.
10. Write the **headless-scene ADR** (addendum row 1) and a **Seed-sets ADR** (addendum row 8's
    remainder), or add both to the spine's Deferred table with revisit points.
11. Decide the **key-binding hook**: raise the budget by ADR, invoke FR-42's buttons-only fallback
    (and strike AD-12's F6–F11 clause), or fold the site into hook row 3/4.
12. Give the six still-open PRD questions revisit points in the Deferred table, at minimum OQ1
    (E1 spike) and OQ10 (E5 or E8 story).
