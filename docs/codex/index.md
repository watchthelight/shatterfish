# Codex

Generated. Never hand-edited.

The `codex` module (E2) dumps, by reflection from the pinned upstream tag, every mob, item,
generator table, mob rotation, trap, alchemy recipe, and changelog entry, parameterised by depth
and challenge flags, into `codex/<tag>/*.json`, and renders the pages under this section from
those files. One Gradle task regenerates everything:

```sh
./gradlew :codex:generate
```

CI regenerates and fails if the committed output differs from the working tree, so the Codex can
never drift from the tag it claims to describe. Edits to these pages are rejected in review;
change the generator instead.

The Codex is the source of the "general game knowledge" the bot is allowed to have
([Fairness](../fairness.md)) and the ground truth the lore pipeline's variant classifier checks
against (the PD-vs-SPD vocabulary diff lives here too).

The generator's skeleton is story 2.1's (ADR-0017): `codex/v4.0.0/` holds the manifest with the
Codex version and the tag, the hero classes with their subclasses, and the challenge flags with
their masks, each entry cited to the `path:line` it was read from at generation. Story 2.2 adds
`mobs.json`, every concrete mob class of the game with its hit points, defense skill,
experience, maximum level, alignment, properties, loot, the three rolls as cited expressions,
and its variants by depth and by challenge (the depth-scaled mobs, the Stronger Bosses
variants), and `spawn-rotation.json`, the standard rotation per depth, the random families with
their odds, the rare additions, the alternates and the champion rule. The tables that follow
are their stories', and this page is replaced by the generator's index when story 2.9 renders
it.
