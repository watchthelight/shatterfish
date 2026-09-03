---
title: 'Reconciliation: Product Brief -> PRD'
input: _bmad-output/planning-artifacts/briefs/brief-shatterfish-2026-09-03/brief.md (status: ready)
against:
  - prd.md
  - addendum.md
created: '2026-09-03'
---

# Reconciliation: brief.md against the PRD and its addendum

Method: every substantive statement in the brief (claims, scope lines, success criteria, audience statements, risks, tone/feel ideas) is listed with where the PRD or PRD addendum carries it. Status values: **carried**, **gap** (no home), **contradiction** (PRD says something different), **weakened** (diluted or ambiguous), **dropped-qualitative** (an idea about tone, feel, or posture the FR structure has no slot for). The brief's own addendum was not an input to this pass; where the brief delegates to it ("addendum, Audience sequencing", "Technical constraints") the PRD addendum states it deliberately does not repeat that content.

## 1. Executive summary

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "open-source engine for Shattered Pixel Dungeon (SPD) in the spirit of Stockfish" | §1 para 1 | carried | Near-verbatim. |
| "four parts: the game's own code driven headlessly and reproducibly; a hand-built symbolic bot; a Fishtest-style rig; an overlay" | §1 para 1; §4.1, 4.4, 4.5, 4.6 | carried | |
| "a person can watch it think, pause it, step it, and take over" | §1; FR-39 | carried | |
| "permanent downstream fork of SPD, pinned to a release tag, unofficial and unaffiliated" | §1; §10 License; §4.7 | carried | |
| "Nobody can currently say, with evidence, how well a strategy for SPD works" | absent | gap | The PRD has no problem statement. §0 says it "builds on the brief ... does not duplicate", but the motivating problem appears nowhere, not even as a pointer. |
| "two disciplines enforced by architecture rather than intentions: information parity ... and measurement" | §1 para 2; §4.2; §10 Safety | carried | "rather than intentions" survives as "enforced by build structure and tests, not conventions" (§4.2). |
| "Why now: the field is empty and the community small and active" | §12 | weakened | "the field is empty" carried; "community small and active" dropped. The community's activity is the reason readers/contributors exist (Vision). |
| "upstream is quiet while its next major version is built privately" | §12 | carried | PRD adds "so the hook surface is stable for months". |
| "every foundation (headless boot, native overlay, statistics, symbolic-bot design) has a known shape and a known cost" | §12 | carried | |

## 2. The problem

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "No ground truth for strategy ... There is no way to run the question a thousand times" | absent as problem; §2.1 developer job and §1 measurement discipline carry the response | gap | The FRs carry the solution (Rig) but the problem the Rig answers is unstated in the PRD. |
| "Knowledge is unreliable at the source. The Fandom wiki hosts vanilla Pixel Dungeon and Shattered pages on one site under prefixes; the newer community wiki gives formulas that name code identifiers but cite no file" | FR-16 (vocabulary diff), FR-17 (Rules with citations), FR-18 (Lore pipeline) | weakened | Solution carried; the reason the vocabulary diff and variant classifier exist (two games on one wiki, uncited formulas) is not in the PRD, so FR-16's purpose reads as arbitrary. |
| "Claims about mechanics rot across versions and across the two games" | Glossary Tier F "false or obsolete for a tag"; FR-17 citation checker | carried | |
| "Tooling has nowhere to live. Upstream's repository states it does not accept pull requests. Every SPD tool is therefore a fork, and the existing forks either stopped tracking upstream or never did" | §2.1 Contextual ("a game whose author accepts no contributions"); §4.7 | weakened | The failure mode of prior forks (stopped tracking upstream) is the motivation for FR-50/NFR-5 tag-by-tag upgrades; the PRD states the procedure without the motivation. |
| "No precedent, fair or measured. The only bot project for SPD is an abandoned course fork and the seedfinders drive one function of the game inside an invisible window: no gym, no RL agent, no headless harness" | §12 "no bot, RL agent, gym, or headless harness exists for SPD" | carried | Specific prior art (course fork, seedfinders) dropped; acceptable. |
| "the NetHack bots that beat every learned agent consume a privileged semantic feed and the DCSS bot runs inside the game's scripting layer; none publishes a testing culture a skeptic could audit" | §1 para 2 ("the symbolic-bot tradition from NetHack supplies the play"); UJ-3 skeptic | weakened | The brief cites NetHack/DCSS bots as *unfair* precedents; the PRD cites the NetHack tradition only as a positive source of play. The point that no prior bot is fair *or* auditable is lost. |

## 3. The solution

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "Four parts, shipped in order of dependence" | §6.1 E1 to E5 ordering | carried | |
| "SPD's `core` booted on libGDX's headless backend inside a harness-owned scene, seeded end to end, driven turn by turn" | FR-1, FR-2; Glossary Headless scene | carried | |
| "a fair Observation/Action interface" | Glossary Observation, Action; FR-3, FR-4 | carried | |
| "One class, `Observer`, builds the Observation from what the game already computes for drawing" | Glossary Observer; FR-3 | carried | |
| "one class, `ActionExecutor`, drives the hero through the same code paths the UI uses" | Glossary ActionExecutor; FR-4 | carried | |
| "Brain. Belief state, scripted policies, tactical search, strategic playbooks, an evaluation function" | Glossary Brain; FR-28 to FR-35 | carried | |
| "It depends on the Observation types alone; the build fails if it imports game code" | FR-7 | carried | |
| "Identical code runs headless and in the overlay" | §4.5 description prose; §3 Brain | weakened | Stated as description only; no FR or testable consequence guarantees that HeadlessDriver and EmbeddedDriver run the same Brain artifact. FR-37 says the EmbeddedDriver applies bot Actions through the ActionExecutor but not that the Brain is the same class/jar. |
| "Rig. Thousands of seeded runs in parallel, sequential statistical comparison of two brains (GSPRT), JSONL run logs with a hash chain, replay, published results" | FR-19 to FR-25; NFR-3 | carried | |
| "Overlay ... in the game's own UI toolkit: it shows its goal, chosen action with reasons, beliefs, and safety flags" | FR-38 | carried | |
| "a human can pause, step, run N, or take over and hand back" | FR-39 | carried | PRD adds Resume and Speed. |
| "The brain re-plans from the current Observation every turn, so a human can act at any time without desync" | FR-27, FR-40 | carried | |
| "a Codex generated from the pinned code with `path:line` citations" | FR-14, Glossary Codex | carried | |
| "a lore pipeline that admits community knowledge only with provenance and a verification tier" | FR-18 (E7); Glossary Lore, Tier | carried | |

## 4. What makes this different

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "Honestly stated: there is no technical moat. Anyone can fork the same game" | absent | dropped-qualitative | A posture statement (honesty about defensibility) with no FR slot. It matters for how Results and docs are written (no overclaiming) and for the Vision's "reason anyone trusts its numbers". |
| "a pair of rules that are expensive to keep and easy to drop, and the infrastructure that keeps them" | §1 para 2; §10 Safety "a violation is a release blocker regardless of Results" | carried | |
| "Fairness by architecture. Parity is enforced by module boundaries, a single Observer, leak and differential tests in CI, and an oracle mode that is off by default, visibly flagged, and impossible in ranked runs" | FR-7 to FR-11; NFR-1 | carried | "Ranked" is undefined in the Glossary in both documents; FR-11/FR-22 imply ranked = pre-registered comparison but never say so. |
| "Every strong roguelike bot in the literature skipped this" | absent | dropped-qualitative | The competitive claim that fairness is the differentiator against all prior bots is not in the PRD. |
| "Stockfish's testing culture, not its search. SPD is stochastic, partially observed, single-player; chess search does not transfer" | §1 para 2 | carried | |
| "GSPRT, paired seeds, pre-registration, and published negatives do" | FR-21, FR-22, FR-25 | weakened | "Published negatives" has no home. FR-25 publishes "a Results page for a comparison" and SM-7 requires Results on *merged* Brain changes; nothing requires that rejected or undecided comparisons are published. The norm from Fishtest (every test visible, including failures) is silently dropped. |
| "The bot explains itself in the game's own words. Native UI, Codex vocabulary, a strategy log a human can follow" | FR-32 ("in Codex vocabulary"), FR-36, FR-38, NFR-9 | carried | |
| "The overlay is the debugger for everything built after it and, later, a coach" | §6.2 E8 note; FR-47 | carried | |
| "Knowledge from code, not folklore. The Codex is regenerated by one build task and CI fails on drift; every mechanics claim in the docs cites the pinned tag" | FR-14, FR-15, FR-17, NFR-6 | carried | |

## 5. Who this serves

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "Primary at v1: Shatterfish's developer. v1 is a one-user product with a public codebase" | §2.1 Functional (the developer, v1) | carried | |
| "the product owner is the developer's reviewer and domain expert, not a separate user" | UJ-1 "Bash, the developer and product owner"; §0 "This PRD is for the product owner" | contradiction | The brief separates two roles: the developer (builds) and the product owner (reviews, supplies domain expertise). UJ-1 collapses them into one person. If the developer is the agent and the PO is the human reviewer, UJ-1 mis-describes who edits the policy and who accepts the result; the adversarial fairness review (NFR-1) and "no brain change merges without rig numbers" both assume a reviewer distinct from the author. |
| "Needs: fast headless runs, trustworthy numbers, an overlay that shows why the bot did what it did" | §2.1 developer job; NFR-3; §4.2; FR-38 | carried | |
| "Success: they can change the brain, run the rig, and know within an hour whether it got better" | §2.1; UJ-1; NFR-3 justification; §10 Cost | carried | |
| "Then, in order: SPD players who want to learn (coach mode and autoexplore-with-brains, overlay v2); the community as readers ... ; and, later, researchers and spectators" | §2.1 lists community reader second, learner third; §2.2 researchers as v1 non-users; spectators absent | weakened | The brief's priority order (learners before readers) is reversed in §2.1. §6.2's PM note ("coach mode is the feature most likely to bring a second user") agrees with the brief, so the PRD is internally split on which audience is second. Spectators are not mentioned anywhere in the PRD. |
| "the community as readers of published seed sets, results with confidence intervals, and replayable logs" | §2.1 community reader; SM-2 CI; FR-24 | carried | |
| "community members improve its playbooks with rig numbers attached" (Vision) | FR-35 "a Playbook change is a pull request with Results" | weakened | The mechanism exists but the *community contributor* persona is absent from §2; the only community role in the PRD is reader/skeptic. Nothing in §2 or §5 says outside contributions are wanted. |

## 6. Success criteria

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "Winning is a ladder, measured on public seed sets; the headline is a win, and every rung below it is how the program knows it is getting there" | §1 para 3; §7 | carried | |
| Headline: "The bot kills the final boss, Yog-Dzhewa, and wins a run, reproducible from its run log on a public seed" | SM-1 | carried | |
| "from there, win rate per class on the standard seed set is the canonical number" | SM-2 | carried | |
| E1: "Three measured numbers replace guesses: fast-forwarded turns per second, paired-seed correlation on a smoke set, and the tactics' leaf correlation and disambiguation" | SM-4; FR-5 assumption; addendum E1 done-when | weakened | SM-4 places all three numbers in E1, but only turns per second has a producing FR in E1 (FR-5). Paired-seed correlation needs two Brains and FR-21 (E3), and §13 Q2 itself says "E3 measurement". Leaf correlation and disambiguation need a tactical simulator, and §13 Q3 says "E1/E6". The E1 rung as written cannot be met by the E1 requirement set; the PRD carries the brief's wording without resolving it. |
| E1: "Same seed twice is byte-identical; all fairness tests pass" | FR-2; SM-4 "determinism test green"; SM-7; addendum E1 "fairness suite green" | carried | Split across SM-4 and SM-7; addendum's E1 done-when restores the full sentence. |
| E3: "A reproducible random-agent baseline is published" | SM-5; FR-5 | carried | |
| E3: "a deliberately worse agent is rejected by the sequential test" | SM-5; FR-21 consequence | carried | |
| E3: "every published number carries tag, seed set, commit, hypothesis ID, and the command that reproduces it" | FR-25 field list; addendum Results page fields; UJ-3 | weakened | "The command that reproduces it" is in UJ-3 narrative only. FR-25's field list (tag, commit, Seed set, both Brains, Hypothesis ID, outcome, distributions, log links) and the addendum's Results page fields both omit the reproduction command. As a requirement it has been dropped. |
| E4: "Kills Goo on a large majority of the standard seed set, with the survival curve and boss-kill staircase published" | SM-3; FR-31 | carried | "Large majority" stays unquantified in both. No FR names the survival curve or boss-kill staircase as a Results artifact; FR-25 says "distributions". |
| E5: "A human watches a full sewers run and takes over mid-fight without desync" | SM-6; UJ-2 | carried | |
| Long run: "Win rate on default settings per class (canonical)" | SM-2 | carried | "on default settings" (no challenges) dropped from SM-2; the addendum's headline-options note keeps challenges as far horizon, so this is implied. |
| Long run: "relative strength chain between versions" | SM-2 "published per Brain version"; addendum "Options considered" | weakened | A chain (each version compared against its predecessor, transitively) is stronger than "per version"; no SM or FR requires the version-to-version comparison to be published. |
| Long run: "ascension and challenges as the far horizon" | addendum "Options considered" only | carried | Addendum-only; fine for a far horizon. |
| "no brain change merges without rig numbers" | SM-7; FR-35 | carried | |
| "every fairness test runs on every pull request" | NFR-1; SM-7 | carried | |
| "docs change with the code" | NFR-6; FR-52 | carried | |

## 7. Scope

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| In v1: "bootstrap and planning" | §6.1 E0 | carried | |
| In v1: "headless-scene harness with Observer, ActionExecutor, seeding, determinism, and the fairness test suite" | §6.1 E1 | carried | |
| In v1: "Codex generation" | §6.1 E2 (FR-14 to FR-17) | carried | PRD adds FR-16 vocabulary diff to E2 though its only consumer (FR-18 classifier) is E7. Not a contradiction; a scope addition. |
| In v1: "rig with seed sets, GSPRT, run logs, replay, nightly results" | §6.1 E3 | carried | PRD adds FR-26 death replay gallery to E3 (from the ideas ledger). |
| In v1: "a scripted baseline brain with beliefs and a worst-case item-test check" | §6.1 E4; FR-29, FR-30 | carried | |
| In v1: "overlay v1 with pause, step, run N, speed, take over, path highlight, native styling" | §6.1 E5 (FR-37 to FR-43) | carried | See next two rows for what E5 adds beyond the brief. |
| Deferred (E6 to E8): "overlay v2 (explain view, pause-on conditions, replay scrubber, beliefs view, hotkeys)" | FR-42 Hotkeys is in E5 (v1) | contradiction | The brief lists hotkeys as overlay v2; the PRD puts FR-42 in E5 and §6.1 lists E5 as FR-37 to FR-43. Scope moved from deferred to v1 without an assumption tag. |
| Deferred: "beliefs view" (overlay v2) | FR-38 "a Belief summary" in v1; no v2 beliefs-view FR; FR-46 shows Beliefs in the scrubber | weakened | The v2 beliefs view has no FR of its own; a reduced form (summary) is pulled into v1. Whether the full view is E8 scope is now undecidable from the PRD. |
| Deferred: "tactical search (decision gated on measured properties)" | FR-34; §6.2 E6 | carried | |
| Deferred: "strategy and lore pipeline" | FR-18, FR-35; §6.2 E7 | carried | |
| Deferred: "coach mode" | FR-47; §6.2 E8 | carried | With the flagged assumption that it lives in E8. |
| Out: "learned evaluation (optional E9)" | §5; §6.2 | carried | |
| Out: "any second implementation of game rules in any language" | §5 | carried | |
| Out: "any process boundary between bot and game" | §5; §10 Runtime | carried | |
| Out: "any UI framework other than the game's own" | §5; FR-38 consequence | carried | |
| Out: "Android and iOS" | §5; §2.2; FR-49 | carried | |
| Out: "leaderboards and community challenges" | §5 "...or online services in v1" | weakened | The brief lists these under *Out* (unqualified), separate from *Deferred*. The PRD adds "in v1", reopening them for later. |
| Out: "other games" | §5 "will not become a general roguelike framework or support other games in v1" | weakened | Same "in v1" qualifier added; the brief's Out is unqualified. |
| Out: "proposing changes upstream" | §5; §2.2; §10 | carried | |
| Constraint: "no external deadline" | absent | gap | §10 Constraints has no schedule statement. Minor, but it is the justification for "measure before promising budgets" and for the optional E9. |
| Constraint: "one upstream upgrade after the E3 baseline, when 4.0 stable lands" | §11 "4.0 stable expected as one large drop"; UJ-5; FR-50 | weakened | The PRD describes *how* to upgrade but never *when*: neither "exactly one upgrade in v1" nor "not before the E3 baseline exists" appears as a constraint. The brief's risk mitigation "upgrade only after a baseline exists" is therefore unenforced. |
| Constraint: "Java 21; one JVM; GPL-3.0-or-later" | §10 Runtime, License | carried | |
| Constraint: "every edit to an upstream file is a documented hook" | FR-48; Glossary Hook | carried | |

## 8. Feasibility and risks

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "two numbers that cannot be researched, only measured: what paired seeds buy the rig (chess gets 15%) and whether SPD tactics reward search" | §13 Q2, Q3 | carried | The chess benchmark (15%) is dropped; acceptable. |
| "Headless is a scene, not a stub" | Glossary Headless scene; addendum ADR row | carried | |
| "the sequential test is 20 lines to port" | absent | gap | Feasibility note only; low value. |
| "search starts at one ply over Codex tables" | FR-34; addendum ADR row | carried | |
| "the brain's cost is maintenance, not feasibility" | absent | dropped-qualitative | The reason Playbooks are data (FR-35) and Policies have cheap predicates (FR-28) is maintenance cost; the PRD states the mechanisms without the concern. No NFR addresses maintainability of the Brain. |
| Risk: "Rendering is coupled to turn resolution more deeply than the audit finds" / "The first E1 story is the touchpoint audit; the research's inventory is its starting point" | FR-1 consequence (animation-dependent paths complete headless); addendum ADR "round-4 digest is the inventory" | weakened | The risk is not named. The mitigation's ordering constraint ("first E1 story") is absent; the epics workflow will not see it unless it reads the brief. Addendum says "risks referenced, not repeated" but no section of the PRD actually references the brief's risk table. |
| Risk: "Pairing buys little and the rig is slow to decide" / "Depth and boss milestones as early metrics; composite outcome; measure before promising budgets" | Glossary Composite outcome; FR-21 reports correlation; SM-C3; §13 Q5 | carried | |
| Risk: "Solo engineer stalls before the first win" / "Overlay v1 ships right after the baseline brain as motivation and debugger; publish the first Goo kill" | §6.1 order E4 then E5; §2.1 Emotional job; SM-3 | weakened | The ordering is carried, the *reason* (motivation against stalling) is not, and "publish the first Goo kill" as a milestone announcement has no FR or SM. |
| Risk: "Upstream 4.0 lands as one large drop" / "Hooks capped and listed; merge measured with one API call; upgrade only after a baseline exists" | FR-48 lists hooks; SM-C4 fewer hooks; §11 | weakened | "Capped" has no number in either document but the PRD drops the word entirely (SM-C4 says only "fewer is better"). "Merge measured with one API call" (size the merge before attempting it) is absent. "Upgrade only after a baseline exists" is absent (see Scope constraint above). |
| Risk: "A parity leak slips in" / "Three enforcement layers, leak and differential tests, an adversarial fairness review on every relevant change" | FR-7 (three checks), FR-8, FR-9, NFR-1 adversarial review | carried | |

## 9. Vision

| Brief statement | PRD location | Status | Note |
|---|---|---|---|
| "In two to three years Shatterfish is the reference engine for SPD" | absent | dropped-qualitative | No time horizon anywhere in the PRD; no statement of the end state beyond SM-1. |
| "publishes a reproducible strength ladder per class and per version" | SM-2 | carried | See "relative strength chain" above. |
| "its coach mode explains a run in the game's own words" | FR-47; UJ-4 | carried | |
| "community members improve its playbooks with rig numbers attached" | FR-35 | weakened | See §5 above: contributor persona absent. |
| "the community treats its Codex and rules corpus as the shared, cited ground truth for how the game works" | absent | gap | No success metric or non-goal touches external adoption of the Codex; FR-14/FR-17 make it citable but nothing says it is meant for anyone but the Brain and the docs. Glossary Codex says "The Brain's only source of general game knowledge", which narrows the audience to the bot. |
| "It upgrades tag by tag behind upstream without ever needing upstream's cooperation" | FR-50; §5 | carried | |
| "its fairness guarantee is the reason anyone trusts its numbers" | §4.2 description | carried | |

## Summary by category

### (a) Gaps: no home in the PRD or addendum
1. The problem statement ("No ground truth for strategy"; no way to run a question a thousand times) is absent; the PRD opens at Vision.
2. "No external deadline" constraint is absent from §10.
3. Community adoption of the Codex/rules corpus as shared ground truth (Vision) has no metric, non-goal, or audience line; Glossary narrows the Codex to "the Brain's only source".
4. Spectators (later audience) are not mentioned.
5. "The sequential test is 20 lines to port" (low value).

### (b) Contradictions
1. **Hotkeys**: brief defers to overlay v2; PRD FR-42 is in E5 (v1), listed in §6.1 without an assumption tag.
2. **Developer vs product owner**: brief says the PO is the developer's reviewer and domain expert, not the same person; UJ-1 names Bash as "the developer and product owner", collapsing author and reviewer, which undercuts NFR-1's adversarial review and SM-7's merge gate.
3. **Audience order**: brief says "in order: learners, then community readers"; §2.1 lists readers before learners, while §6.2's PM note agrees with the brief.

### (c) Weakened
1. **Reproduction command**: brief requires every published number to carry "the command that reproduces it"; FR-25's field list and the addendum's Results page fields omit it; it survives only in UJ-3 narrative.
2. **Published negatives**: brief names it as one of the four Stockfish practices that transfer; no FR or SM requires rejected/undecided comparisons to be published (SM-7 covers merged changes only).
3. **E1 three numbers**: SM-4 places paired-seed correlation and tactics' leaf correlation/disambiguation in E1, but no E1 FR produces them and §13 Q2/Q3 place them in E3 and E1/E6. The E1 rung is unmeetable by the E1 requirement set as written.
4. **Upgrade timing**: "one upstream upgrade after the E3 baseline" and the risk mitigation "upgrade only after a baseline exists" have no requirement or constraint; FR-50 says how, never when.
5. **"Out" softened to "in v1"**: leaderboards/community challenges and other games move from unqualified Out (brief) to "not in v1" (§5).
6. **Beliefs view (v2)**: no E8 FR; a "Belief summary" is pulled into v1 FR-38.
7. **Identical Brain code headless and in the overlay**: description prose only, no testable consequence.
8. **Relative strength chain between versions**: SM-2 says "per Brain version", not version-to-version comparison.
9. **Risks table**: the addendum claims risks are "referenced, not repeated" but the PRD never references it; the touchpoint-audit-first ordering and "merge measured with one API call" mitigations are absent.
10. **Prior art framing**: NetHack bots appear only as a positive tradition; the brief's point that they are privileged-feed (unfair) and unauditable is lost.
11. **Motivation for FR-16/FR-50**: wiki conflation of two games and prior forks that stopped tracking upstream are not stated, so the requirements read as unmotivated.

### (d) Qualitative ideas the FR structure silently drops
1. "Honestly stated: there is no technical moat" — the posture of not overclaiming, which should shape how Results and methodology pages are written.
2. "Every strong roguelike bot in the literature skipped this" — fairness as the differentiator against all prior work.
3. "The brain's cost is maintenance, not feasibility" — no maintainability NFR for the Brain despite Playbooks-as-data existing for this reason.
4. "Overlay v1 ships right after the baseline brain as motivation" — the human-factors reason for the E4/E5 ordering.
5. "In two to three years ... the reference engine for SPD" — the time horizon and end state.
6. "The community small and active" — the reason a reader/contributor audience exists at all.

### PRD additions beyond the brief (for the record; not defects)
- FR-6 snapshot/restore interface reserved in E1 (brief: nothing before E6).
- FR-16 vocabulary diff in E2; FR-26 death replay gallery in E3; FR-33 tunable Evaluation with SPSA (E6+); FR-42 hotkeys and FR-43 oracle marking in E5.
- NFR-3 throughput target (10,000 turns/s target, 1,000 minimum); NFR-7 portability; NFR-8 no network; NFR-9 plain-text observability.
- §9 public surfaces and versioning; §7 counter-metrics SM-C1 to SM-C5.
