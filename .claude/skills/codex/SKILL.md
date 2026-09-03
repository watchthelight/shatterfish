---
name: codex
description: Regenerate the Shatterfish Codex (reflection dump of upstream mechanics tables) with the Gradle task and report what changed. Use when the user says "regenerate the codex", "codex diff", "is the codex current", or invokes /codex.
---

# codex

The Codex (`codex/<tag>/*.json` and the pages under `docs/codex/`) is generated from the pinned
upstream tag by the `codex` module and is never hand-edited. CI regenerates it and fails on
drift. This skill does the same locally and explains any difference.

## Steps

1. **Is the generator built?** If `shatterfish/codex/src/main/java/org/shatterfish/codex/` has no
   generator entry point, report that the Codex is E2 work
   (https://github.com/watchthelight/shatterfish/milestone/3) and stop.
2. **Regenerate** from a clean tree:
   ```sh
   ./gradlew :codex:generate
   git status --short codex/ docs/codex/
   git diff --stat codex/ docs/codex/
   ```
3. **Explain the diff.** For each changed file say which table changed (mobs, items, generator
   weights, mob rotation, traps, recipes, changelog, vocabulary) and give one example row before
   and after. Then classify the cause:
   - upstream tag changed (`docs/UPSTREAM.md` pinned tag differs from `codex/<tag>/`): expected;
   - the generator changed in this branch: expected, and the change must be described in the PR;
   - neither: the generator is non-deterministic or reads something it should not (time, RNG,
     environment). That is a bug; report it before anything else.
4. **Never edit** anything under `codex/` or `docs/codex/` by hand. If a page is wrong, the fix is
   in the generator.
5. **Commit** the regenerated output in the same branch as whatever caused it.

## Downstream

- `docs/rules/` citations and lore-pipeline variant classification depend on the Codex; after a
  regeneration that changes a table, list the rules pages that cite the affected classes so they
  are re-verified (`/upstream-sync` does this on tag upgrades).
