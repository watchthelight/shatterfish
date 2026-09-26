# Rules

Every claim about a game mechanic that Shatterfish relies on lives here, and nowhere else is a
mechanic asserted from memory. Non-negotiable #8 of the [bootstrap prompt](../BOOTSTRAP-PROMPT.md):
any claim is settled by reading the pinned code and citing `path:line`, never by memory or a
forum post.

## Format

One page per area (`rules/<area>.md`: visibility, identification, generation, combat, rng, ...),
each a table:

| Column | Content |
|---|---|
| Rule | One sentence, in the present tense, about the pinned tag |
| Cites | `path:line` (or `path:line-line`) at the pinned tag, one or more, as links into the repository at that tag |
| Test | The Shatterfish test that would fail if the rule stopped being true; "none yet" is allowed only with an issue number |
| Tier | 1 = the code confirms it, 2 = the harness confirms it, 3 = hypothesis for the rig, F = false or obsolete for this tag, needs-review = its citation stopped resolving at an upgrade and nobody has re-read it yet |
| Since | Session or PR that added or last re-verified it, and the tag that flipped it if it is waiting |

Rules are re-verified on every upstream upgrade (`docs/UPSTREAM.md`, upgrade procedure steps 9 and
10): a citation that no longer resolves, or resolves to different code, flips the rule to
needs-review until re-read.

Since story 2.10 there is an instrument for the first half of that, and it runs on every build.
`./gradlew :codex:citations` resolves every `path:line` in `docs/` against the tree at the tag that
citation names — not against the pin, because a row deliberately left at an older tag is still true
of that tag — and prints the page, the line, the file and which way each one failed: a file gone
from the tree, a line past the end of it, a code span and the link beside it saying different
things, a bare name that is now two files, a ref this checkout does not have. It also holds the
needs-review convention in both directions: a row at an older tag has to say needs-review, and a row
that says needs-review has to be at an older tag, so a row that was quietly re-cited cannot keep the
flag. `DocsCitationTest` runs the same sweep inside `./gradlew build`, so a citation that stops
resolving turns the build red rather than waiting for an upgrade. What it does not do is read the
sentence: that the cited lines still *mean* what the row says is what re-reading is for, and the
paragraph below still applies.

The upgrade to `v4.0.0` (2026-09-11) ran that check line by line. Every citation's text at the old
tag was looked for at the new one: where it sits unchanged, the link now points at `v4.0.0`, at the
line it moved to if upstream inserted above it; where it is gone, changed, or in more than one
place, the row is flipped to needs-review and its link is left at `v3.3.8`, where it was true. A
block too generic to identify itself, under twenty-five characters of text, is never matched either
way: `}` is `}` everywhere, and a tag that moved the rule's code and left something equally plain
behind would otherwise pass. Of the 315 rows with citations, **219 resolve to the same code** and
**94 are waiting to be re-read**; two rows cite in plain text rather than as links and the check
cannot see them. Most of the 219 moved to new line numbers, and three were re-read by hand rather
than by the check, because they are the blob-drawing rules upstream itself changed at this tag;
their Since column says so.

A needs-review row is not known to be wrong: it is known to be unchecked, and the story that next
depends on it re-reads it (E2's Codex pipeline is the bulk buyer). The check is narrower than it
sounds, and the review of the upgrade drew the line: it verifies that the cited *lines* still hold
the same text, not that the rule's sentence is still true. A tag that adds a member to a table the
row describes, without touching the lines the row cites, leaves the row standing and stale. Only
reading does that; this is what keeps a citation from pointing at the wrong code in the meantime.

## Pages

Written in bootstrap session 10 from the pinned code (275 rows, all tier 1), and re-verified
against `v4.0.0` on 2026-09-11 with the outcome above. Each page ends with a
"Not confirmed" list: what the reader looked for and could not settle, which is where the E1
stories start. The [Codebase map](../codebase-map.md) indexes the files behind every page and
records where the code contradicted the bootstrap prompt.

| Page | Rows | Covers |
|---|---|---|
| [Game loop and hero input](game-loop.md) | 24 | Actor scheduling, the actor thread, how a hero turn starts and ends, sprite-gated turn resolution, windows and scene switches. |
| [Visibility](visibility.md) | 24 | What `Dungeon.observe` computes, field of view, fog, secret doors and traps, heaps, mob sprites, and every leak of unseen mobs found. |
| [Items and identification](identification.md) | 34 | What an unidentified item shows the player, how appearances are shuffled from the seed, what identifies on use or equip, and what the Observer must never read. |
| [Mobs, AI and combat](combat.md) | 36 | AI states, noticing, `Char.hit`, damage and armor rolls, spawn tables, boss floors and the stair lock. |
| [Level generation and floors](levels.md) | 28 | Depths and branches, per-floor seeding, room lists, special and secret rooms, guaranteed solution items, traps, hidden doors, stairs. |
| [Item generation and guarantees](generation.md) | 22 | `Generator` decks and weights, the strength potion and upgrade scroll schedules, `LimitedDrops`, bones, heap types. |
| [Random numbers and seeding](rng.md) | 21 | The generator stack, what the seed fixes, what runs on the unseeded base generator, and every other source of nondeterminism found. |
| [Buffs, status effects and blobs](buffs.md) | 29 | How buffs reach the HUD, what each vision buff does, hunger, food and regeneration, the potion of healing, dangerous effects with their numbers, gas and fire visibility. |
| [UI toolkit and layout](ui.md) | 32 | UI pixels and `defaultZoom`, the full desktop layout with its sizes, `Chrome` types, text sizes, default key bindings, the camera offset, what an overlay can reach. |
| [Save, score, win and profiles](save-score-win.md) | 26 | `Bundle` saves, slots and save paths, the score formula, what a Win is, ascension, the class list, challenges, cross-run state. |
| [Text, assets, changelog and build](text-assets.md) | 24 | `Messages` key derivation, the journal documents, the changelog, version constants, the Gradle modules and toolchain. |

Every test column still reads "none yet": the tests arrive with the epics that rely on each row
(E1 for the game loop, visibility, RNG and identification pages; E3 for score and win; E5 for the
UI page), tracked under [#1](https://github.com/watchthelight/shatterfish/issues/1) until the
story issues exist.

## The Brain's own Rules index

These pages are every claim about the game the project has read. Which of them the *Brain* relies
on is a different list, and it does not exist yet, because no Brain exists to rely on anything.
It is story 4.4's: the Decision and the strategy log arrive there, and the index is created with
them — one entry per mechanics claim a heuristic rests on, each pointing at the row of a page here,
so that "every heuristic is cited" is a number somebody can count rather than a thing the project
says about itself (FR-17). Until then, a claim about the game has exactly one home, which is here.
