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
| Tier | 1 = the code confirms it, 2 = the harness confirms it, 3 = hypothesis for the rig, F = false or obsolete for this tag |
| Since | Session or PR that added or last re-verified it |

Rules are re-verified on every upstream upgrade (`docs/UPSTREAM.md`, upgrade procedure step 9):
a citation that no longer resolves, or resolves to different code, flips the rule to needs-review
until re-read.

## Pages

Written in bootstrap session 10 from the pinned code (275 rows, all tier 1). Each page ends with a
"Not confirmed" list: what the reader looked for and could not settle, which is where the E1
stories start. The [Codebase map](../codebase-map.md) indexes the files behind every page and
records where the code contradicted the bootstrap prompt.

| Page | Rows | Covers |
|---|---|---|
| [Game loop and hero input](game-loop.md) | 24 | Actor scheduling, the actor thread, how a hero turn starts and ends, sprite-gated turn resolution, windows and scene switches. |
| [Visibility](visibility.md) | 24 | What `Dungeon.observe` computes, field of view, fog, secret doors and traps, heaps, mob sprites, and every leak of unseen mobs found. |
| [Items and identification](identification.md) | 28 | What an unidentified item shows the player, how appearances are shuffled from the seed, what identifies on use or equip, and what the Observer must never read. |
| [Mobs, AI and combat](combat.md) | 29 | AI states, noticing, `Char.hit`, damage and armor rolls, spawn tables, boss floors and the stair lock. |
| [Level generation and floors](levels.md) | 27 | Depths and branches, per-floor seeding, room lists, special and secret rooms, guaranteed solution items, traps, hidden doors, stairs. |
| [Item generation and guarantees](generation.md) | 22 | `Generator` decks and weights, the strength potion and upgrade scroll schedules, `LimitedDrops`, bones, heap types. |
| [Random numbers and seeding](rng.md) | 21 | The generator stack, what the seed fixes, what runs on the unseeded base generator, and every other source of nondeterminism found. |
| [Buffs, status effects and blobs](buffs.md) | 24 | How buffs reach the HUD, what each vision buff does, hunger and regeneration, dangerous effects with their numbers, gas and fire visibility. |
| [UI toolkit and layout](ui.md) | 26 | UI pixels and `defaultZoom`, the full desktop layout with its sizes, `Chrome` types, text sizes, default key bindings, the camera offset, what an overlay can reach. |
| [Save, score, win and profiles](save-score-win.md) | 26 | `Bundle` saves, slots and save paths, the score formula, what a Win is, ascension, the class list, challenges, cross-run state. |
| [Text, assets, changelog and build](text-assets.md) | 24 | `Messages` key derivation, the journal documents, the changelog, version constants, the Gradle modules and toolchain. |

Every test column still reads "none yet": the tests arrive with the epics that rely on each row
(E1 for the game loop, visibility, RNG and identification pages; E3 for score and win; E5 for the
UI page), tracked under [#1](https://github.com/watchthelight/shatterfish/issues/1) until the
story issues exist.
