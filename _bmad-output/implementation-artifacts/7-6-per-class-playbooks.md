---
title: 'Story 7.6: Per-class playbooks'
type: 'feature'
created: '2026-10-26'
status: 'in-progress'
baseline_commit: '44d1e31f2e4b5fb9be48d1170e7bc2b183a809da'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** every class plays the Warrior's Playbook. The `classes` section the format gained in 7.2 can override the
`identify` and `upgrade` fields, but nothing the other classes start with is used: the Mage's staff is swung as a club, and the
Mage dies on depth 1 to 3 in 28 of 40 Runs.

**Approach:** measure each class on the `standard` set, name where the Warrior's rules are plainly wrong from the Run logs, and
give a class an entry only where a measured change wins on both slices. The entries override named fields and nothing else; the
Warrior's is empty. A class feature that needs a new Action is filed as its own issue and left out.

## Acceptance Criteria (from epics.md Story 7.6, with the tests that hold them)

1. The story file counts the Brain's Runs by class on the `smoke` set and on a 40-Run `standard` slice per class (depth reached,
   deaths by depth), and names the classes for which the Warrior Playbook is plainly wrong, from the Run logs. Held by the Results
   section below. The class-kit claims are Rules cited to `v4.0.2` with rows in the Brain's Rules index (`staff-zap`, and the
   Spirit Bow row in `docs/rules/combat.md`); held by `BrainRulesIndexTest` and the Codex citation tests.
2. Each `classes` entry overrides named fields of the base for one class and nothing else, a class without an entry plays the
   base, an unknown class is refused by the validator, and the Warrior's entry is empty. Held by `ClassPlaybookTest` (the override
   rule, the refusals by field, the committed entries, and the Warrior's smoke Runs equal wait for wait under the committed and
   the empty Playbook), `PlaybookTest.kit` and `PlaybookValidatorTest`.
3. Class features go through Actions that exist (`UseItemAt` ZAP for the Mage's staff). Held by `ZapPolicyTest`. A feature that
   needs a new Action is filed as its own issue: the Huntress's Spirit Bow (`SHOOT`, a cell selector `ValidActions` does not know), issue #309.
4. The Rig runs `standard` per class (`--class <C> --first 40 --salt-base 1000`, then `--first 80 --salt-base 3000` for a kept
   entry), and each table reports the class's own depth distribution beside reach depth 5.
5. A kept entry is decided by the Sequential test on the class's slice in ADR-0012's Per-pair unit. The Rig has no second
   registered Brain that plays the old Playbook, so the Registration cannot be run; the story says so and keeps an entry only on
   the two-slice direction tables. An entry that does not separate from noise on both slices is reverted and recorded as an idea.
6. `HeuristicLinkTest`, `PolicyArbitrationTest` and the rest of `:api:test :brain:test :rig:test` stay green; PR labelled
   `fairness` with the fairness-reviewer verdict.

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Playbook.java`: the new `Kit` section (`zap_reach`), a `kit` component on the Playbook and on `ClassEntry`,
  class entries sorted by class name (the order the file reader requires; the enum order made a two-class file unreadable).
- `shatterfish/harness/.../PlaybookFile.java`, `PlaybookValidator.java`: read and validate the `kit` section, base and per class.
- `shatterfish/brain/.../Zap.java`, `Brain.java`: the `zap` Policy above `ranged`, entered only for a class whose reach is above zero.
- `playbooks/shatterfish.json` (version 2), `playbooks/oneply.json`: the committed text, canonical.
- `docs/rules/combat.md`, `docs/rules/index.md`, `docs/brain-rules.md`: the kit Rules and the `staff-zap` row.

## Decisions

- **Micro-brainstorm (what a class entry may hold).** Five options: (1) only the 7.2 fields, retuned per class; (2) a `kit` section
  of typed numbers for items only some classes hold (chosen); (3) a Policy per class in Java with no Playbook field; (4) item names
  in the file; (5) a `talents` section naming talents. (1) has no class-specific mechanic to tune. (3) hides the number from the
  Registration. (4) and (5) put free text the Brain interprets into the file, which the format forbids. A `kit` field is a number of
  cells and the Policy reads the item from the screen.
- **Reach zero is the Brain before the story.** The base states 0, so a class without an entry, the Warrior included, plays
  exactly what it played.
- **The Huntress's bow is not used.** `SHOOT` opens a cell selector (`SpiritBow.java:88-94`) and is not in `ValidActions`' cell
  table, so offering it is a `ValidActions` and `ActionExecutor` change, a fairness-reviewed story of its own: filed.
- **Pre-mortem.** The zap could waste charges on trivial enemies and leave none for the Goo; it does not target a Goo fight. The
  entry could make the Mage better at depths 1 to 3 and worse past 5, which is why each table reports deaths by depth.

## Verification

- `./gradlew :api:test :brain:test :rig:test -Pshatterfish.testForks=1` and `mkdocs build --strict`.
- `:rig:direction --set standard --class <C> --first 40 --salt-base 1000 --baseline origin/main --jobs 2`, and the second slice.

## Results

(filled in as the tables land)
