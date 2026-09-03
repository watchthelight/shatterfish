---
title: 'Reconciliation: technical research vs PRD'
input: _bmad-output/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md
against:
  - prd.md
  - addendum.md
created: '2026-09-03'
status: extract for the PRD finalize step
---

# Reconciliation: technical research report vs PRD and addendum

Scope: the research's Executive summary, Recommendations 1 to 10, Open questions, and Contrary evidence A, B, C; dimension sections 1 to 7 only where a recommendation points into them. For each item: the research phrase, where the PRD carries it, and a verdict (**carried**, **ADR** = correctly left to an ADR, **weakened**, **gap**, **contradiction**).

## 1. Recommendations

### Rec 1 — E1 as a headless scene

| Research phrase | PRD location | Verdict |
|---|---|---|
| "harness-owned `Scene` with the Groups `GameScene` provides ... driver loop that fast-forwards `scene.update()` until `Actor.processing()` is false" | Glossary "Headless scene" ("supplies what sprites attach to, a no-op graphics layer, and a fast-forwarded update loop"); FR-1 consequence 2 ("Turn resolution paths that depend on sprite animation (attack, zap, throw, use) complete without a real render loop"); addendum ADR row "Headless-scene design" (E1) | carried; mechanism to ADR |
| "a no-op `GL20`/`GL30` installed before any `Texture` class loads, atlases via `Pixmap`, `updatesPerSecond = 0`" | Addendum ADR row: "no-op GL, Pixmap atlases, fast-forwarded updates" | ADR (correct) |
| "desktop natives shipped" | FR-1 consequence 3 ("Boot succeeds with the desktop natives shipped and fails with a clear message if they are missing") | carried |
| "per-instance preferences directory" (and section 1: "Preferences work but write real files under `preferencesDirectory`, so parallel instances sharing one directory would collide") | **absent**. FR-19 requires "parallel processes" and FR-2 excludes wall-clock, scheduling and hash-map order from Observations, but nothing requires per-Run isolation of on-disk state (libGDX preferences, SPD settings/saves/`keybinds.dat`) or excludes prior Runs' files from influencing a Run | **gap** (small, but it is a determinism and parallel-safety requirement, not a mechanism) |
| "First story: the touchpoint audit, seeded with the round-4 digest's inventory" | Addendum ADR row: "round-4 digest is the inventory" | carried, with a traceability note: the "round-4 digest" is not among the PRD's listed inputs and its only visible form is Contrary evidence A in the research; the epics workflow will need a pointer |
| Section 1: `HeadlessApplication.exit()` is asynchronous; `Timer` is JVM-global and rebinds to whichever `Gdx.files` is current | absent | ADR-level detail; not a gap while processes are the default |

### Rec 2 — Parallelism, classloader spike, three measured numbers

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Parallelism is process-per-game" | FR-19 consequence ("One process per Run by default"); section 6.2 ("processes are the default"); addendum row "Parallelism: processes versus classloader isolation" | carried |
| "the classloader-isolation spike must put libGDX and natives in a shared parent loader and only game classes per child" | Addendum row: "libGDX in a shared parent loader if isolation is tried (JNI rule)" | carried |
| "and report measured turns per second as its exit criterion" | Section 6.2: "a spike in E1, not a requirement" — no exit criterion | **weakened**: the spike has no done-when in the PRD or the epic map |
| "Replace the '1,000 runs in seconds' done-when with three measured numbers: fast-forwarded turns per second, pair correlation on a smoke seed set, and Long et al.'s leaf correlation and disambiguation" | FR-5 `[ASSUMPTION]`; SM-4; addendum E1 done-when ("Three measured numbers (SM-4)"); open questions 1 to 3; section 14 | carried — but see the placement note below |
| Placement of pair correlation: research Open questions says "Run the smoke seed set with two brains in E3 and compute it"; Executive summary says "Both are E1/E3 measurements"; Rec 2 itself lists it under E1 | SM-4 and the addendum put "paired-seed correlation on `smoke`" in the **E1** done-when, before the Rig (E3) or any Brain (E4) exists. At E1 only the random agent exists; its pair correlation is at best a prior, not "the real sample-size saving" (OQ2) | **internal inconsistency to resolve**: the research is itself split; the PRD should say E1 publishes the random-agent correlation and E3 the two-Brain number, or move the number to E3's done-when. As written SM-4 promises in E1 a number the research says needs two Brains |
| Open question: "does the actor thread ever block on a sprite wait under it?" (contrary A: "a live sprite that is never updated blocks forever") | Open question 1 | carried |

### Rec 3 — E3 statistics ADR (GSPRT)

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Port Fishtest's GSPRT (approximation 2.1, regularization, clamp) to Java" | Glossary "Sequential test"; addendum ADR row "Rig statistics" | carried; port details to ADR |
| "paired-seed differences of a composite outcome ordered win, depth, turns" | Glossary "Composite outcome"; FR-21 | carried |
| "state bounds in standardized units and pre-register them with a hypothesis id" | FR-21 ("bounds stated in standardized units"); FR-22; Glossary "Hypothesis ID" | carried |
| "require a burn-in and validate realized error rates by simulation on the rig's own outcome distribution before any bound is trusted" | FR-21 consequence 2 | carried |
| "re-calibrate bounds per upstream tag" | FR-50 and NFR-5 require "Rig re-baseline" only; no bound re-calibration | **gap**: re-baselining is not re-calibrating; the outcome distribution shifts with a tag (contrary B: "Bounds also drift with the outcome distribution and had to be re-tuned in Fishtest's history") |
| "report win rate with a confidence interval and never gate on it while near zero" | SM-2 ("with a confidence interval"); SM-C3 | carried |
| "evaluate an e-process or mixture-SPRT design as the alternative in the same ADR" | Addendum ADR row ("e-process alternative") | ADR (correct) |
| Section 3: "adopt two stages (a small seed set, then the standard set) like Fishtest's STC/LTC" | FR-20 seed sets; UJ-1 edge case ("undecided at the smoke budget; Bash runs the standard set") | carried |
| Contrary B: "the correlation must be measured on the rig before any budget is promised" and "Fishtest's own analysis ... variance saving of roughly 15%" | FR-21 consequence 3 ("The measured paired-seed correlation is reported with every comparison"); OQ2 — but OQ2 drops the 15% prior, and UJ-1 ("know within an hour"), NFR-3 justification and section 10 Cost ("the Rig must fit its smoke loop in an hour on that laptop") promise a budget before the number exists | **weakened / soft contradiction**: the hour is a promised budget resting on two unmeasured numbers (pair correlation, throughput). UJ-1's "undecided" edge case hedges it; the Cost constraint does not. Suggest wording the hour as a design goal restated after E1/E3, and adding the 15% prior to OQ2 |
| Contrary B: "GSPRT's guarantees are asymptotic ... a zero-inflated integer metric estimated from its first few dozen pairs is exactly that regime" | FR-21 burn-in and simulation validation | carried |
| Contrary B: "a depth-gated bot has the mirror incentive to dive and die deeper" | SM-C2; Composite outcome | carried |
| "Fishtest's current default bounds ... could not be quoted as text" | Open question 9 | carried |

### Rec 4 — PRD non-functional requirements

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Fairness tests as listed in the bootstrap prompt plus a search leak test" | NFR-1; FR-8 to FR-13 | carried |
| "reproducibility as (tag, seed, action list) with the seed defined as generation-only until the code says otherwise" | Glossary "Run"; FR-2; NFR-2; OQ4. FR-2 goes further: "Every random source the game uses, including the general-purpose generator used for combat rolls, is seeded by the Harness" | carried and strengthened (not a contradiction): the PRD resolves the research's caution by making the Harness responsible for seeding whatever the game seed does not; OQ4 keeps the code question open |
| "headless throughput stated as a measured target after E1, not a guess" (also Exec summary: "the roadmap must not assume either"; contrary A: "the E1 throughput target becomes conditional on how fast a fast-forwarded scene update runs") | NFR-3: "Target: at least 10,000 game turns per second per process ... the minimum acceptable is 1,000 turns per second, below which the Rig design changes" | **contradiction**: NFR-3 states a numeric target and a numeric minimum before E1 measures anything, which is exactly what Rec 4 says not to do. The hedge ("a target to measure in E1 and restate, not a requirement to design around") does not remove the 1,000 t/s floor, which is a requirement. Two further problems inside NFR-3: (a) its justification "the research shows the per-turn engine cost is not the ceiling" cites contrary A's figure, but that figure (about 400k turns per second) is for "a small roguelike engine", not SPD, and the same sentence says the real ceiling is "per-decision policy overhead", which NFR-3's harness-only turns-per-second number does not include; (b) the 200 s arithmetic rests on the indexed 2,000-turn assumption. Suggest: keep NFR-3 as "measured in E1 and restated", move 10,000 and 1,000 to a labeled `[ASSUMPTION]` or to the E1 spike's hypothesis, and state that the hour budget (UJ-1) counts Brain think time |
| "overlay responsiveness as 'brain thinks off-thread, all panel writes posted to the render thread, no `RenderedTextBlock` touched off it'" | NFR-4; FR-38 consequence 2 ("Every Panel write happens on the render thread; the Brain never touches a Panel object"); FR-12; addendum ADR row "Threading model for the Overlay" | carried; the `RenderedText.measure()` trap itself is ADR-level |

### Rec 5 — Brain architecture

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Arbitration as a priority list of interruptible behaviors with cheap entry predicates" | FR-28; Glossary "Policy", "Arbitration" | carried |
| "explicit belief tables for identification; persistent belief about unseen monsters (AutoAscend lacks it)" | FR-29 and its `[ASSUMPTION]`; FR-30 | carried |
| "one-step combat scoring over an abstract action set first" | Not stated. FR-31 lists "fight in corridors" as a Policy; FR-33 (Evaluation with tunable weights) is "Promoted ... to E6+"; FR-34 Search is E6. The Glossary says Policies use the Evaluation, but no v1 FR says combat in E4 is one-step scoring | **gap** (small): E4 has no requirement for how combat is scored, and the Glossary's Evaluation is implicitly deferred with FR-33. Suggest a consequence under FR-31: "combat Policies score one step over the valid Action set using the Evaluation; no lookahead in v1" |
| "playbooks and priorities as data" (section 5 lesson 4: "what let non-programmers tune a bot"; contrary C mitigation for opaque rule stacks) | FR-35 Playbooks, deferred to E7; FR-28 says nothing about priorities being data | **weakened**: v1's Arbitration order and Policy parameters are code; the research's mitigation for the "rule stacks go opaque" cost is not in the MVP. Acceptable if deliberate; note it in section 6.2 |
| "a strategy log shown in the overlay" | FR-36; FR-38 (scrolling decision log) | carried |
| "a scheduled annual review of the learned frontier" (contrary C: "'learned components are late' is a scheduling choice to revisit annually, not a law"; BALROG slope 1.6% to 6.8%) | Section 2.2 ("learned components are optional and late"); section 5 non-goal; E9 optional. No review trigger | **gap** (process): the PRD fixes "late" without the revisit the research attaches to it. One line under section 6.2 E9 or FR-52 would carry it |
| Contrary C: "the perception and belief layer is work AutoAscend never did and E1 must budget it" | FR-3 (Observer, E1); FR-29 (Beliefs, E4); the E1 done-when in the addendum is numbers, determinism and fairness, not Observation completeness | carried in substance; the cost warning is for the epics workflow. Consider adding "Observation covers every element of FR-3's list for the sewers" to E1's done-when |
| Contrary C: "AutoAscend's rule network was opaque ... mitigations are playbooks as data, a strategy log the overlay can show, and per-epic retrospectives" | FR-35, FR-36; retrospectives are process, not PRD | carried / not a PRD matter |
| Section 5 lesson 5: "Bots that assume their own action stream break under human takeover; observation-driven re-planning is the fix" | FR-27; FR-40 | carried |
| Section 5 lesson 6: "an optimization target must reward progress, not points" | SM-C2; addendum "in-game score (rejected as headline: gameable)" | carried |
| Section 5 lesson 7: learned components "at the tactical leaf under symbolic arbitration (RAPH), which is where E9 puts them" | E9 "Learned eval (optional)" in the addendum; no FR | carried at roadmap level |

### Rec 6 — E6 search ADR

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Start with one-ply expectimax over Codex tables; then measure the simulator's speed and the Long et al. properties; choose between depth-limited sampled search ... and ISMCTS on those numbers" | FR-34 ("the design ... is chosen on measured properties"); OQ3; addendum ADR row "Abstract tactical model versus engine rollouts versus information-set search" ("One-ply over Codex tables first; measure Long et al.'s properties and simulator speed"); section 6.2 | carried; ADR (correct) |
| "horizon 2 to 4 hero turns, split swept" | FR-34 ("two to four hero turns") | carried; split to ADR |
| "keep item identification in belief reasoning, not search" | FR-30 (safeTest is a Belief computation); FR-34 is scoped to "when enemies are visible" | carried implicitly; not stated as a rule. Suggest one clause in FR-34: "Search does not decide item identification; that is Beliefs (FR-29, FR-30)" |
| "The search leak test (identical decisions across hidden-state variants) is the fairness gate for any of the three" | FR-13; FR-34 consequence 1; NFR-1 | carried |
| Section 4: "a simulator that cannot deliver thousands of simulated turns per second per decision cannot support this class of search at all" | FR-34 "chosen on measured properties"; OQ1 | carried; ties to the NFR-3 issue above (the measured number decides E6's design, another reason NFR-3 should not pre-state a floor) |
| Cross-dimension: "The redetermined simulator E6 may need is the headless scene fed a scrubbed snapshot. Building E1 this way makes E6's expensive option cheaper" | FR-6 ("Deferred to E6; the interface is reserved in E1"); addendum ADR row "Snapshot/restore and redetermination" | carried |

### Rec 7 — Overlay UX and E5 architecture

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Panel as a `Component` with `Chrome.TOAST_TR`, `renderTextBlock`, `RedButton`, and a `ScrollPane` log, added at the end of `GameScene.create()` and placed in `layoutTags()`'s free column" | FR-38 ("built from the game's own UI toolkit ... respects the game's interface-size setting"); FR-38 `[ASSUMPTION]` (free column between menu pane and inventory pane; UX decides smaller sizes); FR-38 consequence 1 (no Swing/JavaFX/ImGui/web view) | carried; widget choice to UX/ADR (correct) |
| "hotkeys as `SPDAction` constants" | FR-42 ("bindable through the game's own key-binding system and appear in its settings screen") | carried |
| "two hooks plus one for sprite-wait bypass if the embedded driver must run faster than animation speed" (section 7: "a bot that drives the hero inside the real game runs at animation speed unless it also controls sprites") | FR-39 Speed is "a turns-per-second cap" that does not affect animation speed; FR-39 "Run N"; SM-C4 counts Hooks. Nothing asks the Overlay to run faster than animation | decided by omission: the PRD does not require faster-than-animation play in the Overlay, so the third hook is not needed. Confirm this is intended (UX may want a fast "Run N"); if so, say so in FR-39 so the hook is not added by default |
| Section 7: "Mode changes only when the hero is waiting for input" maps to the input seam (`Hero.ready()`/`GameScene.ready()`) | FR-39 consequence 1; UJ-2 edge case; section 10 Human control | carried |
| Section 7: interface size 1 has no inventory pane, "the panel replaces the inventory pane at interface size 1" | FR-38 assumption leaves placement at smaller sizes to UX | carried (UX) |

### Rec 8 — Build and CI (ArchUnit)

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Bump ArchUnit to 1.5.0; keep the three boundary layers" | FR-7 consequences 1 to 3 (no declared edge; resolution-time check; ArchUnit bytecode rule); section 11 ("ArchUnit 1.5") | carried |
| "add `jdeps` only if a fourth is ever wanted" | absent | not a gap |
| Section 2: fail-on-empty default as the companion check against package renames; `resolveMissingDependenciesFromClassPath` performance | absent | ADR / CI detail; not a gap |

### Rec 9 — Brief: feasibility and positioning

| Research phrase | PRD location | Verdict |
|---|---|---|
| "No bot, RL agent, gym, or headless harness exists for SPD; the seedfinder lineage is the only precedent" | Section 12 Why now | carried |
| "Both wikis are tier-3 lore; Evan's blog is the design-intent source" | Glossary "Tier" (3 = hypothesis); FR-17, FR-18, NFR-6 (`path:line` on every mechanics claim); FR-16 vocabulary diff addresses the Fandom PD/SPD confusion | carried by definition; FR-18 could name the two wikis and the blog as `source_type` values, but that is the lore pipeline's detail |
| "the seed fixes generation" (section 6: "seeds are guaranteed only within a build") | OQ4; FR-2; Results name the tag; NFR-5 re-baseline on upgrade | carried |

### Rec 10 — Roadmap

| Research phrase | PRD location | Verdict |
|---|---|---|
| "Land E1's hooks before 4.0 stable" | Section 12 ("the hook surface is stable for months") | **weakened**: stated as context, not as a constraint or sequencing note; no PRD/epic-map line makes E1's hooks time-sensitive |
| "measure the 4.0 merge with `compare` when it appears" (research OQ: "What 4.0 stable changes structurally, and the merge cost — One `compare` API call when the tag lands") | absent from FR-50, NFR-5, UJ-5 and the PRD open questions. Section 11 only notes "4.0 stable expected as one large drop" | **gap** (small): the upgrade procedure has no pre-merge measurement step; add to FR-50 or OQ list |
| "expect content, not restructuring" | Section 11 | carried |

## 2. Research open questions vs PRD section 13

| Research open question | PRD | Verdict |
|---|---|---|
| Turns per second of a fast-forwarded headless scene; does the actor thread block | OQ1 | carried |
| Within-pair correlation for two brains; "Fishtest's 15% is the prior" | OQ2 (prior dropped; placed in E3, which conflicts with SM-4's E1 placement, see Rec 2) | carried; note prior and placement |
| Leaf correlation, bias, disambiguation for SPD tactics | OQ3 ("E1/E6 measurement") | carried |
| Classloader isolation with libGDX in a shared parent loader: N games per JVM without collisions | Section 6.2 (spike); not in OQ list; no exit criterion | weakened (see Rec 2) |
| Seed beyond dungeon generation (combat rolls, identification order) | OQ4 | carried |
| Fishtest's default STC/LTC bounds as text | OQ9 | carried |
| `SharedLibraryLoader` source path in gdx-jnigen | absent | not a PRD matter (behavior verified empirically) |
| What 4.0 stable changes structurally; merge cost | absent | gap (see Rec 10) |
| Does a hand-written no-op `GL20` exist? ("write one, about 200 lines") | absent; addendum ADR row says "no-op GL" | ADR (correct) |

PRD open questions with no research counterpart (5 think budgets, 6 human win-rate source, 7 coach mode epic, 8 target win rate) come from the brief and bootstrap; no conflict.

## 3. Contrary evidence: what changed and whether the PRD reflects the amended position

- **A (headless harness).** Overturned "no scene" boot. PRD reflects the amended verdict everywhere it matters: Glossary "Headless scene", FR-1 consequence 2, addendum ADR row. No trace of the original "scene-less boot" survives. Correct.
- **B (rig statistics).** Amended: measure pairing, burn-in, calibrate, composite, e-process alternative. PRD carries all in FR-21/FR-22/Glossary/addendum except bound re-calibration per tag (gap) and the "no budget before measurement" caution, which UJ-1, NFR-3 and section 10 Cost cut across (see Rec 3 and Rec 4).
- **C (brain).** Overturned the 40% LLM claim; PRD's symbolic-first stance stands. Surviving cautions: plateau and opacity (mitigated by FR-35 deferred, FR-36 carried), privileged input (FR-3 carried), annual review (absent).

## 4. Summary lists

**Contradictions**
1. NFR-3 states a throughput target (10,000 t/s) and a hard minimum (1,000 t/s) that Rec 4 says must be "a measured target after E1, not a guess"; its justification borrows a non-SPD figure and omits the per-decision Brain cost the research names as the real ceiling.
2. SM-4 / addendum E1 done-when promise "paired-seed correlation on `smoke`" in E1, while the research's open question places the meaningful measurement in E3 with two Brains (the research is itself split; the PRD must pick and say which number E1 publishes).
3. (Soft) UJ-1 "within the hour" and section 10 Cost "smoke loop in an hour" promise a budget the research says not to promise "before any budget is promised" (contrary B); the UJ-1 edge case hedges, the constraint does not.

**Gaps**
4. Per-instance preferences/on-disk state isolation for parallel Runs (Rec 1; section 1) — absent from FR-2/FR-19.
5. Re-calibrate Sequential-test bounds per upstream tag (Rec 3) — FR-50/NFR-5 re-baseline only.
6. Scheduled annual review of the learned frontier (Rec 5; contrary C) — absent.
7. Measure the 4.0 merge with `compare` when the tag lands (Rec 10; research OQ) — absent from FR-50 and OQ list.
8. No v1 statement that combat is one-step scoring over the Action set (Rec 5) — FR-31/FR-33 leave E4 combat scoring undefined.

**Weakened**
9. Classloader spike has no exit criterion ("measured turns per second") — section 6.2.
10. "Playbooks and priorities as data" is E7 only; v1 Arbitration priorities are code — FR-28/FR-35.
11. "Land E1's hooks before 4.0 stable" is context in section 12, not a sequencing constraint.
12. OQ2 drops the 15% Fishtest prior.

**Correctly left to ADRs (not gaps)**
- No-op GL, Pixmap atlases, `updatesPerSecond`, driver loop shape (E1 headless-scene ADR).
- GSPRT port details, clamp/regularization, e-process alternative (E3 statistics ADR).
- Search family choice, determinization/iteration split (E6 ADR).
- Panel widgets, `RenderedText.measure()` thread trap (E5 threading ADR and UX spec).
- ArchUnit fail-on-empty and classpath-resolution settings (CI).
- Snapshot/redetermination design (E6 ADR).

**Decided by omission — confirm intent**
- FR-39 Speed is a cap only; no requirement to run the Overlay faster than animation, so the research's third hook (sprite-wait bypass) is not needed. State it so the hook is not added by default (SM-C4).
