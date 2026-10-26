---
title: 'Story 7.2: Playbooks as data'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: '16f1c8266d2e4333ab518e186dac34596675c7c4'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** the Brain's strategy numbers (when to try an unknown potion, how long to rest after a test, where a scroll of
upgrade goes) are `static final` constants in Java. A change is a rebuild, a per-class value is a branch, and the Registration
cannot see which numbers moved.

**Approach:** a typed `Playbook` in `api`, read by the Rig from `playbooks/<brain>.json` and handed to the Brain at
construction, versioned and hashed into the configuration like the weights. A validator refuses anything that could carry a
seed, a cell or a layout. The first file holds exactly the values the constants held, and the play does not change.

## Acceptance Criteria (from epics.md Story 7.2, with the tests that hold them)

1. An api-typed `Playbook` with `format`, `version` and optional sections `identify`, `upgrade`, `bosses`, `classes`, typed
   numbers and enums only. Held by `PlaybookTest` (api) and `PlaybookFileTest` (the committed files, the omitted-section rule,
   class entries).
2. The Rig reads `playbooks/<brain>.json` and hands it to the Brain. Held by `PlaybookFileTest.configured_by_the_playbook`, and
   by `Runner`/`RunOne` passing `--playbook` to the child.
3. An ADR with five options scored against the non-negotiables, one of them "keep the constants in Java", recording the format,
   why the Playbook sits beside the weights, and how a Registration pins it: ADR-0023.
4. The Run log header records the Playbook name and hash as it does the weights: the Playbook's canonical text is in
   `Brain.configuration`, so the header's configuration hash carries it. Held by `PlaybookFileTest` and `PolicyArbitrationTest`.
5. `PlaybookValidator` rejects a seed or seed string, a coordinate, cell index or layout, a hero class not in
   `hero-classes.json`, an unknown key, an out-of-range number and an enum value outside its set, with a message naming the
   field. Held by `PlaybookValidatorTest`, one rejecting case per rule and an accepting case. It runs over the committed
   Playbooks in the build (`PlaybookFileTest.the_committed_files`) and in the Rig before a Registration is accepted
   (`PlaybookFileTest.refusals`, through `Runner.weighed`).
6. `playbooks/shatterfish.json` holds exactly the current values for `identify` and `upgrade`. Held by
   `PlaybookEquivalenceTest` (the committed text equals the constants' text) and `PlaybookFileTest.the_committed_files`.
7. `PlaybookEquivalenceTest` plays the `smoke` set on the constants and on the Playbook and asserts equal Runs, with a control
   that edits values and asserts the Decisions differ.
8. Moved heuristics keep their `@Heuristic` slug, now on the Playbook component. `HeuristicLinkTest` stays green.
9. PR labelled `fairness` with the fairness-reviewer verdict. The logs are equal, so no Sequential test applies.

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Playbook.java`: the record, its `Identify`, `Upgrade` and `ClassEntry` sections, the SPECS that both the
  constructors and the validator read, `reference(name)`, `canonical()`.
- `shatterfish/api/.../Heuristic.java`: moved from `brain` so a Playbook component can carry the annotation (the `api`
  boundary test allows `java.lang.annotation`).
- `shatterfish/harness/.../knowledge/PlaybookValidator.java` and `PlaybookFile.java`: the rules and the reader.
- `shatterfish/brain/.../TestItem.java`, `Beliefs.java`, `Brain.java`: the seven constants are gone; the Playbook is a field,
  `restOwed`, `escape`, `upgrade` and `target` take the section they use.
- `shatterfish/rig/.../Brains.java`, `RunOne.java`, `Runner.java`, `Direction.java`: reading, `--playbook`, the configuration
  hash, validation before a Registration, and the baseline-ref compatibility.
- `shatterfish/overlay/.../LaunchOptions.java`, `OverlayAgents.java`: the same file, `--playbook`.
- `playbooks/shatterfish.json`, `playbooks/oneply.json`.
- `docs/adr/0023-playbooks-as-data.md`, `docs/methodology.md`, `docs/brain-rules.md` (the regenerated heuristics table).

## Decisions

- **Micro-brainstorm.** Five options in ADR-0023: keep the constants in Java, fold the numbers into the weights, a free-form
  map, generated Java source, a typed record with a validated JSON file (chosen).
- **Odds are per-mille integers.** The canonical text has no floats, and `perMille / 1000.0` is bit-equal to the literals
  `0.2` and `0.25`, which is what makes the equivalence exact.
- **`walks` is derived** as `2 * reach`: one number to edit, and no way to set them apart.
- **`upgrade` is two fields, not a flag.** `weapon_lead` and `tie` reproduce the old rule (weapon unless armour is ahead)
  at `0` and `WEAPON`, and allow "armour until the weapon is two ahead" without a code change.
- **`tie` is validated against a schema-declared set.** The Codex has no slot table; which of two slots is the Brain's
  preference, not a game fact.
- **Omitted sections mean the reference values; class entries state only what they change.** The canonical text writes every
  section whole, so two spellings of one Playbook hash alike.
- **Baseline-ref compatibility.** `:rig:direction --baseline <ref>` plays the ref's brain jar under the current Rig. The
  Playbook is passed only when that checkout has `playbooks/`, and `Brains.of` and `configHash` fall back to the old
  constructor and configuration when it is null.
- **The equivalence test fixes its salts.** The Runner draws a fresh salt per invocation (FR-22), so two invocations are
  not one game. The test starts the `RunOne` children itself under fixed salts.
- **`thinkMs` is excluded from equality.** It is the decider's wall time, the one field the hash chain leaves out, and it
  cannot match between two runs of the same Brain.

## Verification

- `./gradlew :api:test :brain:test :overlay:test :codex:test :harness:test :rig:test -Pshatterfish.testForks=1`.
- `mkdocs build --strict`.
- `:rig:direction --set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main --jobs 2`: see the PR.

## Results

(Filled in below after the runs.)
