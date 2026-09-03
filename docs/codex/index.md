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

_No Codex has been generated yet. This page is replaced by the generator's index in E2._
