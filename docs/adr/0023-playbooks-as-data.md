---
status: accepted
date: 2026-10-26
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0023: The Playbook is a typed, versioned data file beside the weights, pinned by the configuration hash

## Context and problem statement

Story 7.1 counted the Brain's thresholds: 72 declared heuristics, 31 of them on the `unlinked`
allowlist. Most of those numbers are strategy, not mechanics: how likely an unknown potion has to be
healing before the Brain drinks it, how many turns it rests after a test, how far it will walk to a
safe testing spot, which of weapon and armour a scroll of upgrade goes to. They live as `static
final` constants in Java. Changing one is a code change, a rebuild and a new Brain commit, and the
strategy work of epic 7 (per-class tuning, boss plans, lore-driven changes) needs to change them by
the dozen and to say, per hero class, which differ.

Story 7.2 moves the first two groups, `identify` and `upgrade`, out of Java into a `Playbook`: a
value in `api`, read by the Rig from `playbooks/<brain>.json` and handed to the Brain at
construction, the way `weights/<brain>.json` already is. The play must not change by a single
Decision. Three things bind the design:

- **Non-negotiable 1 (information parity).** A Playbook is read before the Run, by a Brain that
  cannot open a file. If it could hold a seed, a cell index or a layout it would be a side door to
  the one dungeon, however innocent the first file looks. It must hold typed numbers and enums only,
  and the validator must refuse the rest by name.
- **Non-negotiable 5 (everything is measured and reproducible).** A Run is determined by (tag, seed,
  action list) and, for a Brain, by its configuration. A strategy number that is not in the
  configuration hash is a Brain that changes under a Registration without the ledger knowing.
- **Non-negotiable 8 (Codex over folklore).** A class a Playbook names must be a class the game has,
  and an enum value must be one the Codex knows, so a typo is a refusal and not a silent default.

## Decision drivers

- A strategy value changes by editing one small file, with a diff a reviewer can read, and no
  recompile.
- The file is part of the Brain's identity: change a value and the configuration hash moves, so a
  Registration cannot be won by one Playbook and replayed with another.
- The format has a place for per-class and per-boss entries from the start, without a migration when
  stories 7.3 to 7.6 fill them in.
- The Brain cannot read files (ADR-0003, the `brain` module's boundary), so the loading stays in the
  Rig and the Overlay, and `api` holds the type.
- The play is unchanged by the move, and a test can say so.

## Considered options

1. **Keep the constants in Java.** The status quo: seven `static final` values in `TestItem`, each
   annotated `@Heuristic`. Nothing to build, nothing to break, and the 7.1 index already lists them.
   Rejected on drivers 1 and 3: a per-class value is a branch in code, a change is a rebuild, and
   non-negotiable 5 is met only by the commit hash, which says nothing about which numbers moved.
2. **Put the strategy numbers inside the weights file.** `weights/<brain>.json` is already versioned,
   canonical, hashed and handed to the Brain. Rejected: the weights are the Evaluation, integers a
   linear scorer multiplies by features, tuned by search and read in ten-thousandths. A Playbook value
   is an odds, a count of waits or an enum, named by a Policy, edited by a person on purpose. One file
   would give them one version, so tuning an Evaluation weight would bump the version of the strategy
   and a reviewer could not see which of the two changed. Two files, two versions, one hash over both.
3. **A free-form map file** (`Map<String, Object>` read by key, any JSON). The cheapest to extend.
   Rejected on non-negotiable 1: a map takes any key, so a seed or a cell index is storable, and the
   only guard is a validator that must know every spelling of the leak. A typed record has a closed
   set of fields, and the validator then refuses the unknown key instead of chasing it.
4. **A generated Java source** from a file, compiled into the Brain. Gives typed access and no loader.
   Rejected on drivers 1 and 2: a change is still a rebuild, and the hash is the source's, which the
   Registration cannot tell from any other source change.
5. **A typed record in `api`, one JSON file per Brain under `playbooks/`, validated by a harness
   class, hashed into the configuration.** Chosen.

## Decision outcome

Option 5.

### The format

`playbooks/<brain>.json`, in canonical spelling like the weights: keys sorted, no whitespace, integers
and enum names only, one trailing newline.

| Key | Meaning |
|---|---|
| `format` | `1`, the version of this layout |
| `name` | the Brain it belongs to; a file for another Brain is refused |
| `version` | the Playbook's own version, moved by hand with any value |
| `identify` | when and how the Brain tests an unknown potion or scroll |
| `upgrade` | where a scroll of upgrade goes, and when |
| `bosses` | one entry per boss (`dm300`, `goo`, `tengu`), story 7.5: how the fight is approached and how fit the hero is sent down to it |
| `classes` | per hero class, only the values that class changes |

`identify` holds `healing_odds_per_mille`, `gain_odds_per_mille`, `inventory_odds_per_mille`
(1 to 1000 each), `rest_waits` (0 to 500), `reach` (0 to 40) and `escape_waits` (0 to 100). `upgrade`
holds `armour_pick` (`WORN` or `HEAVIEST`; #305), `weapon_lead` (-5 to 5), `tie` (`ARMOR` or `WEAPON`), `hold_until_depth` (0 to 26) and
`unknown_weapon` (`NO` or `YES`; story 7.4, which also moved the committed `tie` to `ARMOR`). Odds are per-mille integers, so the file
has no floats; `permille / 1000.0` gives the same double the old literals gave (0.2, 0.25).
The Brain's `walks` value is `2 * reach`, derived, so there is one number to edit.

`bosses.goo` holds `descent_rest_per_mille` (1 to 1000: the share of the hit points the hero rests up to beside the stairs down to depth 5),
`drink_per_mille` (1 to 1000: the share at or under which a healing potion is drunk against Goo alone), `meet_range` and `wash_range` (0 to 12 Steps:
the walk to ground a pump can be dodged on, and to water for caustic ooze). `bosses.tengu` and `bosses.dm300` hold `descent_rest_per_mille` and `status`
(`UNTESTED` or `TESTED`); an `UNTESTED` entry holds the reference values and nothing has been measured on it. No boss entry holds a layout,
a cell or a coordinate: the validator refuses the words, and the Brain reads the boss from the screen. The key names avoid `position` and `room`, which
the validator reads as places.

The Java type is the schema. `Playbook.Identify.SPECS` and `Playbook.Upgrade.SPECS` declare each key,
its range or its allowed names, and both the record constructors and `PlaybookValidator` read them,
so a rule cannot exist in one place and not the other. An omitted section means the reference values
(the numbers the constants held), and a class entry states only what it changes and inherits the rest
from the base. The canonical text always writes every section whole, which is what the hash covers, so
`{"format":1,...}` with no sections and the full file are the same Playbook and the same Brain.

`upgrade.tie` is an enum the Codex has no table for: which of two slots a scroll goes to is the
Brain's preference, not a game fact. It is validated against the set the schema declares, and the
validator's message says which values that is. The Codex check applies where the Codex has the data:
a key under `classes` must be in `hero-classes.json`.

### What the validator refuses

`PlaybookValidator` returns every problem with a field path, and refuses: a seed, salt or rng key, and
a string shaped like a seed code; a key that names a place (cell, coordinate, layout, map); a hero
class not in `hero-classes.json`; an unknown key; an out-of-range number; an enum value outside its
set; a wrong `format`, `name` or a missing `version`. It runs in `./gradlew build` over every committed
Playbook (`PlaybookFileTest`), and in the Rig before a Registration is accepted or a Run starts.
The seed, place and seed-code checks are defence in depth: the guarantee is the closed key set and the small
range on every number, so no field is wide enough to hold a seed or a cell and the only string is an enum.

### Why the Playbook sits beside the weights

Two files in two folders, read by the same code path, named for the same Brain. The weights are what
a search tunes; the Playbook is what a person decides. They change for different reasons at different
speeds and are reviewed by different questions (does the Evaluation rank well, versus is this a
sensible thing to do), so they carry separate versions. They are not separated in the configuration
hash: `Brain.configuration(weights, playbook, model)` appends `;playbook=<canonical text>` to the
weights' part, so the hash in every log header moves with either. `Brains.configHash` and
`Brains.of` take the Playbook for a Brain that reads one (`shatterfish`, `oneply`) and leave the
random Brain alone.

### How a Registration pins a Playbook

A Registration names a Brain and the configuration hash taken over the Brain's Policies, its memory
version, the canonical weights and the canonical Playbook. The pin is therefore the Brain name plus
the file's content: edit one value or only the `version`, and the hash differs, so the Registration is
a different Brain and the ledger shows it. The Run log header records the hash as it does for the
weights, and the Playbook's name and version are inside the text that is hashed. `Brains.sourceOf`
includes `playbooks/<brain>.json` among the files a Brain's commit covers. The Overlay reads
`playbooks/shatterfish.json` through the same reader (`--playbook` overrides the path), so a live
session and a Rig Run of one commit play by one file.

### Heuristic links

A moved constant keeps its `@Heuristic` slug, now on the Playbook record component that carries the
value (`Playbook.Identify.reach`). `HeuristicIndex` scans the brain classes and `Playbook`, so the
7.1 table lists them, and `HeuristicLinkTest` stays green with its allowlist ceiling of 31 unchanged:
one `TestItem` name left it and one `Playbook` name joined.

### Consequences

- `TestItem`, `Beliefs.balked` and the Brain's constructors take the Playbook. The three older
  constructors default to `Playbook.reference("shatterfish")`, which is the old constants, so the
  tests that build a Brain without one are unchanged.
- `:rig:direction` against a baseline that predates the Playbook plays that baseline's own jar with
  the old configuration (`--playbook` is optional in the child). The comparison stays valid across
  this commit.
- Stories 7.3 to 7.6 add fields to a section, or fill `classes` and `bosses`, by editing this record
  and its SPECS, and the validator learns them with it.
- `PlaybookEquivalenceTest` plays the `smoke` set under fixed salts on the old values, written out
  in the test, and on the committed file. It asserts equal waits (the decider's wall-clock
  `thinkMs`, the one field no hash covers, set aside), and a control that edits values and asserts
  the Decisions differ, so the equality is not the equality of a Brain that ignores its Playbook.

## Pre-mortem

If this is wrong in six months:

- **A field slips in that the validator does not know to refuse.** Mitigation: the record is closed.
  A new field needs a SPEC entry, a Java component and a test; the place-key and seed-key rules run
  over every key at any depth, not only the known ones.
- **The Playbook becomes a second place to hide a coordinate in a number** (a `reach` of 655 meaning a
  cell). Mitigation: every range is small and named, and fairness review reads each new field's range
  as part of the diff.
- **Two files drift, a weight tuned against a Playbook that later changes.** Mitigation: one hash over
  both, and `:rig:direction` against `origin/main` before any value moves.
- **The omitted-section rule hides an edit.** A reader sees a short file and assumes the references.
  Mitigation: committed files are written whole (`PlaybookFileTest` asserts a committed file is its
  own canonical text), so the committed text is never short.
- **`tie` is a guess at a two-value enum.** If a third slot ever matters, the SPEC changes and
  `format` moves to 2.
