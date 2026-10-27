---
status: accepted
date: 2026-10-26
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0022: The variant classifier reads the vocabulary diff for items and mobs, and classifies mechanics by source and declaration

## Context and problem statement

FR-16 says the vocabulary diff covers "items, mobs, and mechanics". `codex/v4.0.2/vocabulary.json` has two
kinds, `item` and `mob`: 449 names, 114 in both games, 288 only here and 47 only in vanilla. Story 7.8 builds the
classifier that decides, for a lore claim, whether it describes Shattered Pixel Dungeon, vanilla Pixel Dungeon, a
mod, or cannot be told (FR-18), and has to say what happens to a claim about a mechanic: a rule of the game that
has no name in the table. Story 7.8 gives two ways out. Extend the Codex generator to emit mechanics names from the
cited properties (a Codex change under E2's drift rule), or state here that mechanics claims classify by source type
and date only. This record says which, and why.

## Decision drivers

- **A name is only evidence if the other game's side exists.** The table's strength is that a name found only on
  one side points to that side. A mechanic has no second side to look at.
- **The smaller change, unless it fails the story's intent.** The intent is that a forum claim about vanilla is not
  applied to this game by mistake. That is a harm only when the classifier says `spd` or `pd` wrongly. `unknown` is
  always safe.
- **E2's drift rule.** Any change to `vocabulary.json` regenerates the Codex, the pages under `docs/codex/`, and the
  tests that count them.
- **No second implementation of the game's rules** (non-negotiable 4). A mechanics table for the vanilla side would be
  one.

## Considered options

1. **Extend the generator to emit mechanics names from the cited properties.** Rejected. The cited properties are
   this game's `Char` and `Mob` fields, and vanilla's tree has no table of mechanics to diff against: a mechanics
   name from this side alone says "this game has it" and nothing about vanilla, so it cannot point to `spd` over `pd`.
   Making it point anywhere needs a hand-written list of vanilla mechanics, which is a second rules source and
   a folklore list.
2. **Extend the generator and also hand-write the vanilla mechanics names.** Rejected on the same ground and on
   non-negotiable 8: a claim about the game is settled by the code, not by a list someone typed.
3. **State in an amendment that mechanics claims classify by source type and what the text declares.** Chosen.
4. **Classify mechanics by date against the changelog.** Deferred to story 7.9. The changelog has no calendar date on
   any of its 195 entries, and date gating is that story's whole subject. Using a date here would take 7.9's answer
   before it is designed.
5. **Leave mechanics out of the classifier and refuse them.** Rejected: most claims in the 144 name a mob, but the
   intake format of story 7.7 admits any claim, and a classifier that throws on a mechanic is one more thing for the
   research skill to wrap.

## Decision outcome

Option 3, and no Codex change: `vocabulary.json` and `docs/codex/` do not move.

A claim about a mechanic with no subject in the table is classified by three things, in this order of weight:

1. a mod named in the text ("X Pixel Dungeon", "X PD"), which gives `mod:<x>` and outranks everything, since a
   mod carries this game's names;
2. what the text declares of itself: "Shattered Pixel Dungeon" or "SPD" (`spd`), "the original Pixel Dungeon",
   "vanilla" or "Watabou's game" (`pd`);
3. the source type: a `developer` post about this game is `spd` by construction.

If these point both ways, or none exists, the answer is `unknown`. Names in the table add their votes as the story
says: a name only here is `spd`, only in vanilla `pd`; a shared name is silent unless the claim states a comparable
figure (health or defence) that matches one side. Every result carries the evidence that decided it.

Date is not used here (option 4); story 7.9 designs the gating and may add it.

### Consequences

- A forum claim about a mechanic is `unknown` unless it says what it is about. That is the point: the classifier
  does not guess.
- `vocabulary.json`'s own `consumer` line, "the input the epic 7 variant classifier will use; nothing reads it yet",
  is now half stale: the classifier reads it, from the Codex module's test sources. Changing the sentence means a
  regeneration and an edit to `VocabularyTest`, and was left alone to keep this story off the Codex. The next story
  that touches `Vocabulary` should change it.
- The classifier lives in the Codex module's test sources beside `Lore`, for ADR-0021's reason, and reads the committed
  JSON with the harness's `Json` reader. No dependency is added.
- The classifier does not write: a variant a person set stays, and the Results page lists where the two differ.

## Pre-mortem

It is a year on and the classifier is not trusted. Why?

- **Shared names dominate and almost everything is `unknown`.** 114 of 449 names are shared and most claims are
  about mobs that exist in both games. True, and by design: 106 of the 144 are `unknown` on the first run. The Results
  page keeps the count, and the answer to it is more evidence (source URL, date), not a looser rule.
- **A mod name matches too much.** "Goo PD" or a sentence-initial capitalised word before "Pixel Dungeon" reads as a
  mod. The article and preposition list is a list; it is short, and a wrong `mod:` is visible because the evidence
  quotes the match.
- **A `developer` source is wrong about a vanilla mention.** The rule votes `spd`, and a vanilla-only name in the same
  claim votes `pd`: the result is `unknown` with both, which is the right outcome.
- **The thresholds are met by construction.** The fixture was written with the rules in mind. The story file says so,
  and the comparison with the 144 carried variants is the second check.
