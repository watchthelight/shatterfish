---
status: proposed
date: 2026-10-25
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0020: Split the Brain's Memory into versioned sections, one per Policy

## Context and problem statement

`Memory` (`shatterfish/brain/src/main/java/org/shatterfish/brain/Memory.java`, 938 lines) is one
record of 49 components, written into one `Belief` as a single byte stream under a single
`VERSION`. That integer is 13 today. The history in its Javadoc shows what it costs:

- Every Policy that needs to remember something adds components to the same record, a line to
  `belief()` and a line to `of()`, an edit to `START`, and a telescoping constructor so older
  tests still compile (seven of them, plus about 20 `new Memory(` calls in tests). The dozen
  `with`-style methods (`chasing`, `lifting`, `avoiding`, ...) each re-pass all 49 components.
- Any of those changes bumps the one version, so the bump says nothing about which Policy
  changed. Version 12 was two branches that each bumped to 12 for different fields (the chase,
  issue #174, and the locked-exit memory, issue #163), and version 13 exists only to union them.
  That is a merge conflict made permanent in the version history.
- The integer reaches further than the Brain. `Brain.configuration` writes `memory=<VERSION>`
  into the string the Rig hashes into the Run log's `brain` config hash (ADR-0011), and a
  Registration pins that hash (ADR-0012).

What the log does with a Belief decides how much freedom we have. The `belief` field of every
`wait` record is `SHA-256(int32 version || bytes)` (`Belief.hash()`), and it is inside the hash
chain (ADR-0011, "all but `think_ms`"). So any change to the bytes, or to the integer, changes
every `belief` hash and every chain after it, on every wait of every Run. A Replay reproduces a
Run by following the recorded Actions and comparing chains, and the chain covers the
Shatterfish `commit` in the header, so a log is only replayable at the commit that wrote it
anyway (`Replay`, the chained header fields). A `Belief` is carried inside one Run of one build;
nothing found in `harness` or `overlay` writes one to disk for a later build to read. That makes a
layout change cheap here, and a hash change visible rather than silent.

The goal, from the plan: one versioned section per Policy (chase, unlock, avoided zones and the
others), so one Policy's change bumps only its own section.

Question: how do we cut `Memory` into sections, what does the log see, and in what order do we
get there without ever leaving the tree unbuildable?

Non-negotiables touched: #1 (the `Belief` stays an opaque value the Brain writes, so `harness`
still hashes it without importing `brain`, AD-13 and AD-14; no new information reaches the
Brain), #5 (a Run stays a function of tag, seed and action list, and the log must say what
changed), #7 (the version history moves out of a Javadoc and into the sections that own it).

## Decision drivers

- A Policy change must show up as that Policy's section changing, in the config string and in
  review, and nowhere else.
- Two branches touching two Policies must not collide on a version number again.
- Before the wire format changes, prove that the refactor changes nothing: the same Runs must
  produce the same `belief` hashes on `main` and on the branch.
- The wire change, when it comes, happens once, on purpose, in one PR that can be reviewed alone.
- `api` and `harness` stay as they are. `Belief` is one opaque value (AD-14).
- Every PR leaves `./gradlew build` green and the Brain's behaviour unchanged.
- Honesty about hashes: a Policy change really does change behaviour, so the config hash and the
  `belief` hashes of Runs that exercise it must still change. Stable hashes are not a goal.

## Considered options

1. **Status quo with a rule.** Keep one record and one integer; add a rule that branches rebase
   and renumber. This is what produced 12 and 13. Rejected: it fixes neither the attribution
   problem, the 49-component constructor, nor the conflict hot spot, and it depends on memory.

2. **Split in memory only; keep one flat wire format and one integer.** `Memory` becomes a
   composition of section records, but `belief()` still writes the legacy byte order under one
   `VERSION`. Bytes are identical, but only until the next Policy adds a field, and then every
   bump is global again. It fixes the code shape and none of the versioning. Rejected as an end
   state, kept as the first half of the chosen option because it is the only step that can be
   proven byte-identical.

3. **Framed wire format: one `Belief`, many versioned sections.** The `Belief` stays one value.
   Its bytes become a frame: a section count, then for each section, in fixed registry order, its
   id, its own version and its length-prefixed payload. `Belief.version()` becomes the version of
   the frame only. Each section owns its codec and its invariants. `Brain.configuration` lists
   the section versions. **Chosen**, in two phases (below).

4. **Several `Belief` objects (a map of section to `Belief`) across `api`.** Each section is its
   own opaque value and the log records one hash per section. Best attribution in the log.
   Rejected: it changes `api`, `RunLog`, `Replay`, `EmbeddedRun`, `RunLoop`, the overlay panel and
   the log schema (an ADR-0011 bump) for a gain that option 3 gets from the config string and
   from `--log-beliefs` output. It also puts the section list into `api`, which is the knowledge
   AD-14 keeps out of `harness`.

5. **Self-describing payloads (tagged fields, protobuf-style or JSON).** Fields carry ids, so
   adding one needs no bump. Rejected: the hash chain needs canonical bytes, so we would have to
   specify ordering and number forms ourselves; size and decode cost rise on every wait; and a
   second serialization scheme appears next to `Bytes`. Also, "no bump" is the wrong aim: a
   Policy whose memory means something new should change its version.

6. **Derive each section's version from a hash of its field layout.** Nothing to forget.
   Rejected: renaming or reordering a component without changing meaning would change the
   version, and a change of meaning with the same layout (a counter that now counts something
   else) would not. Versions are about meaning. The useful part survives as a test instead
   (golden bytes per section, below).

7. **Move state into each Policy object** (`Fight` holds its own chase state). Rejected: a
   Policy is a function of Observation and memory (`Policy.java`); `Brain.update` folds the
   screen and the Action into memory. Hidden state in Policy instances would break the property
   that a Belief is all the Brain carries (FR-27, FR-28) and would make Replay and re-sampled
   rollouts unsafe.

## Decision outcome

Chosen option: **3, delivered as an in-memory split first (byte-identical, `VERSION` stays 13) and
a framed wire format second (one deliberate bump).**

### The sections

Each section is a package-private record in `org.shatterfish.brain` with its invariants in its
compact constructor, a `NONE` value, an id, a `VERSION` and a codec. The proposed cut, from who
writes a field and who reads it (a PR may move a component if the code shows a different owner,
and says so in its description):

| Section | Components | Writer; readers |
|---|---|---|
| `screen` | waits, deepest, at, streak, calm, last, stepped, tried, prior, bounces | `Beliefs`, `Brain.update`; nearly every Policy |
| `finds` | facts, found, held, known, labels, pending | `Beliefs`; Pickup, TestItem |
| `fight` | monsters, holds, near, before, flights, avoid | `Beliefs`, `Brain.update`; Fight, Explore, Chests |
| `chase` | chase, chased | `Brain.update`; Fight |
| `unlock` | triedExits | `Brain.update` (`unlockRefused`); Descend |
| `descend` | arrived, rests | `Beliefs`, `Brain.update`; Descend |
| `explore` | dwelt, blocked, fleeting | `Beliefs`; Explore, Chests, Traps |
| `pickup` | underfoot, refused, pack, aim | `Beliefs`, `Brain.update`; Pickup |
| `heal` | drank | `Brain.update`; Heal |
| `testitem` | trial, balked, walking, tested, clouds, refuge | `Beliefs`, `Brain.update`; TestItem, Explore |
| `prompt` | windows | `Brain.update`; Answers |
| `larder` | hunger, hp, food | `Beliefs`, `Brain.update`; Larder and the other files that read `hunger()` |
| `goo` | tail, pump, pumpWait | `Beliefs`; Goo |

`Memory` shrinks to a record of 13 section components plus the cross-section helpers that exist
today (`count`, `quantity`, `avoided`). The `with`-style methods become one-line section
replacements. The seven telescoping constructors go away; the tests that used them move to
`Memory.START` plus section builders.

Cross-section reads stay allowed and go through accessors. The rule is about who owns the
version: the section whose meaning changed bumps, and the readers that need the new meaning are
reviewed with it. `hunger()` is read in nine files; that is a fact about the Brain, and giving it
a section makes the coupling visible instead of hiding it among 49 components.

### The wire format (phase B)

```
frame:   int32 sectionCount, then per section:
         text id, int32 version, int32 length, `length` bytes of payload
order:   the registry's fixed order, never the order sections were added in
Belief:  Belief(Memory.FRAME, the frame as its bytes)
```

`Belief.version()` is the Brain's one integer, so it carries the frame version. It continues the
sequence at 14, and `Memory.VERSION` is replaced by `Memory.FRAME`. A section's payload keeps the
encoding its components have today (`Bytes.Writer` numbers, integers and text); phase B regroups
fields, it does not re-encode them. A reader refuses a frame with an unknown section, a missing
section, a section version it does not carry, or trailing bytes, as `Memory.of` refuses a wrong
version or wrong length today (`PolicyArbitrationTest` and `BeliefConsistencyTest` already pin
those refusals). There is no migration code: a Belief never outlives its build, so a mismatch is
an error, not something to upgrade.

`Brain.configuration` changes from `memory=13` to `memory=14[screen.1,finds.1,fight.1,...]` in
registry order. A later Policy change then produces a one-token diff in that string, which
`docs/results` and a Registration show.

### How the Run logs stay identical, and where they cannot

- **Phase A, the in-memory split: byte-identical, no version change.** `belief()` and `of()` keep
  the legacy interleaved order; they pull components out of section records, and build section
  records after reading, instead of using 49 locals. `VERSION` stays 13, so `Belief.hash()`,
  every `belief` field, every chain after it, and the config hash are unchanged, and existing
  Registrations stay valid.
- **Phase B, the frame: one deliberate break.** The bytes cannot stay the same, because the
  frame adds structure and groups fields that the legacy order interleaves. So the version goes
  to 14 once, and from that commit every `belief` hash and chain differs from logs written by
  earlier commits. Nothing depends on those: `RunLogVerifier` recomputes a chain from the file
  alone, a Replay needs the writing commit in any case, and the Rig's `--baseline <git-ref>`
  compares Runs by outcome, not by `belief` hash. The config hash changes once, so an open
  Registration is re-registered under the new hash, not edited. All 13 sections start at
  version 1 at that moment, so there is exactly one such break and not one per Policy.
- **After phase B**, a change to one Policy's section changes that section's payload and version
  entry, and every other section's payload bytes stay identical, which a per-section golden test
  asserts. The frame hash and the config hash still change, because the Brain's behaviour did.

### Verification

1. **Corpus golden test (PR 1, before any refactor).** A test builds a fixed set of `Memory`
   values (START, one with every component non-default, and the states reached by folding the
   `Screens` fixtures that `BeliefConsistencyTest` already uses) and asserts the hash of their
   `belief().bytes()` against hashes committed as a test resource. It passes on `main` and fails
   the moment a layout moves. It guards phase A, and with new golden files, phase B.
2. **Per-wait identity on the Rig (every phase A PR).** Run the `smoke` Warrior triples on `main`
   and on the branch, and compare wait by wait the `action` and `belief` fields (not `chain`,
   which includes the header's commit). Expected: zero differences. The PR links the output in
   place of Rig numbers, since a refactor has no direction table to show.
3. **Per-section golden bytes (phase B onward).** Each section has a committed exemplar and the
   bytes and version it encodes to. Changing an encoding without bumping that section's version
   fails the test, which makes the one rule enforceable: change a section's layout or meaning,
   bump its version, update its golden file, and touch nothing else.

### Migration order

Phase A, one section per PR, smallest and most isolated first, each PR green on its own:

1. `chase` is the pilot. It was bumped most recently, has one writer and one reader, and already
   carries an invariant across its two components (`(chased < 0) == (chase.cell() < 0)`) that
   belongs inside its section. It also forces the shared pieces to exist: the section shape, the
   accessor convention, the section-aware `START`, and the golden corpus.
2. `unlock` (`triedExits`), the other recent bump, a single list.
3. `goo`, `heal`, `larder`: small and self-contained.
4. `prompt`, `pickup`, `descend`.
5. `fight`, with `avoid` and `flights` (`Brain.update` writes `avoid` and `retreatedSealed`).
6. `explore` and `testitem`, which have the most tests (six test files touch `blocked`).
7. `finds` and `screen` last: they are the widest and `Beliefs.fold` (777 lines) writes both; by
   then everything else has left `Memory`.

Phase B follows once all 13 sections exist, so the one-time break moves every field once.

### Sized PR plan

Sizes are changed lines, tests included. PRs 2 to 9 touch `brain` and carry the `fairness`
label and a `fairness-reviewer` pass (CLAUDE.md); the Observer is not touched. Every PR is
labelled `area:brain`, plus `area:docs` where docs change.

| PR | Content | Size |
|---|---|---|
| 1 | Characterization only: the corpus golden test and the documented wait-by-wait comparison. No production code. | S, about 250 |
| 2 | Section shape, accessor convention, `Memory.START` builders, `chase` section (pilot); call sites in `Brain`, `Fight`, `Beliefs`. | M, about 450 |
| 3 | `unlock` section. | S, about 200 |
| 4 | `goo`, `heal`, `larder`. | M, about 400 |
| 5 | `prompt`, `pickup`, `descend`. | M, about 500 |
| 6 | `fight`. | M, about 450 |
| 7 | `explore`, `testitem`; drop the remaining telescoping constructors; migrate the test constructions. | L, about 900 |
| 8 | `finds`, `screen`; `Memory` is now 13 components and its helpers, under 250 lines. | L, about 800 |
| 9 | Phase B: frame, registry, section codecs, `Memory.FRAME = 14`, `Brain.configuration`, `BeliefSummaries` and the overlay panel if they read the old shape, per-section golden files, re-registration note. Behaviour unchanged. | L, about 700 |
| 10 | Docs: a short "how to bump a section" page; the version history leaves the Javadoc. | S, about 120 |

Total about 4,800 changed lines in 10 PRs, most of it moves. PRs 7 and 8 are the ones to split
further if the diff reads badly. One issue per PR is created from this table; none exists yet.
This ADR changes no code.

### Consequences

- Good: a Policy change bumps one section, shows as one token in the config string, and cannot
  collide with another Policy's change.
- Good: `Memory` stops being the file every Brain change edits; the 49-argument constructor and
  the seven telescoping ones go.
- Good: each section's invariants sit beside its fields instead of in one 25-line compact
  constructor.
- Bad: a one-time break in `belief` hashes and the config hash at PR 9, and ten PRs of churn.
- Bad: `Beliefs.fold` stays a 777-line file that writes most sections. This ADR does not split
  it; moving each section's fold next to it is a separate idea.
- Neutral: the frame costs 13 short ids, versions and lengths per Belief, a few dozen bytes on a
  value the log only hashes unless `--log-beliefs` is on.

## Pre-mortem

If this is wrong in six months, why?

- **The refactor changed bytes and nobody saw.** A reordered field passes tests that only
  round-trip. Mitigation: the golden corpus lands first (PR 1), and every phase A PR compares
  `belief` per wait on the `smoke` set. Zero differences is the merge condition.
- **The sections are not independent.** `hunger()` is read in nine files, and `blocked` is
  written for Explore and read by Chests. A bump in `larder` could still need edits elsewhere,
  and we would have moved the coupling instead of cutting it. Mitigation: ownership is by writer,
  readers are listed in the table, and a bump is reviewed with its readers. If two sections always
  change together after phase B, a later ADR merges them.
- **People bump the wrong section, or forget.** Mitigation: the per-section golden test fails
  when bytes change and the version in the golden file does not.
- **A Belief turns out to outlive a build** (a saved profile that resumes a Run after an
  upgrade). Then refusing on mismatch loses data. Mitigation: nothing like it was found in
  `harness` or `overlay`; if it appears, per-section versions make one upgrader for one section
  possible, which a single integer never did.
- **The one-time break invalidates something nobody listed.** Mitigation: PR 9 searches for
  `memory=` and stored config hashes in `docs/results` and open Registrations first. None was
  found in `docs/results` while writing this, and `RunLogVerifier` and `Replay` do not read
  `Memory`.
- **The frame costs think time.** Mitigation: PR 9 measures `Brain.update` on the `smoke` set
  before and after, and the number goes in the PR.
- **Tests get harder to write.** Positional `new Memory(...)` was awkward but explicit.
  Mitigation: `Memory.START` plus section builders arrive in PR 2, so every later PR has them.
- **The plan stalls halfway.** Each PR is byte-identical and green, so a half-migrated tree is a
  valid tree: some sections out, the rest still in the flat record. That is why phase A keeps
  writing the legacy order until PR 9.
