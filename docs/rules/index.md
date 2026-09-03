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

## Example

| Rule | Cites | Test | Tier | Since |
|---|---|---|---|---|
| The `Random` class that combat rolls use is separate from the level-generation seed and is reseeded from the clock unless the harness seeds it | [`SPD-classes/src/main/java/com/watabou/utils/Random.java`](https://github.com/watchthelight/shatterfish/blob/v3.3.8/SPD-classes/src/main/java/com/watabou/utils/Random.java) (line to be cited by the E1 determinism story) | none yet (E1 determinism test, [#1](https://github.com/watchthelight/shatterfish/issues/1)) | 3 | session 3 (unverified, from the bootstrap prompt) |

The example is deliberately at tier 3: it was written from the bootstrap prompt, not from the
code, and stays a hypothesis until an E1 story reads `Random.java` and cites the line.

## Pages

_None yet._ The first pages arrive with bootstrap session 10 (codebase documentation) and E1
(visibility and RNG).
