# CLAUDE.md

Shatterfish: an open-source engine for Shattered Pixel Dungeon in the spirit of Stockfish. It drives
SPD's own code headlessly (harness), plays it with a hand-built symbolic bot (brain), measures every
change with Fishtest-style SPRT testing (rig), and runs the bot inside the real desktop game (overlay).
It is a permanent downstream fork pinned to an upstream release tag; unofficial and unaffiliated.
You are the sole engineer; the human (watchthelight, "Bash" in BMAD config) is product owner and reviewer.

## Commands

```sh
./gradlew build                            # every module, JUnit 5 + ArchUnit; must be green before any PR
./gradlew :harness:test                    # fairness + determinism tests (E1 onward)
./gradlew :desktop:run                     # the unmodified game
./gradlew build -Pshatterfish.mobile=on    # only if you really want upstream's android/ios modules
./gradlew :codex:generate                  # regenerate codex/<tag>/ (E2); CI fails on drift
./gradlew :rig:run --args="..."            # the rig (E3); see /rig
uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict   # docs, as CI runs it
```

If Gradle test workers fail with Windows error 740 ("requires elevation"), a stale daemon from
another process is in the way: `./gradlew --stop` and retry.

## Non-negotiables (verbatim from docs/BOOTSTRAP-PROMPT.md, section 1)

These apply to every artifact, every story, every line of code, and they are not up for re-litigation in brainstorming.

1. **Information parity — the only rule of play.** The bot may use only information a human player at the same screen could have: what the renderer draws, the game log, the journal, and general game knowledge (the wiki-level facts the Codex extracts). It never reads the true identity of an unidentified item, the position of an enemy it cannot currently see, hidden traps or secret doors, RNG state, or the seed. Mind vision, magic mapping, and similar count only when the in-game effect is active. This is enforced by architecture, not intentions: the `brain` module cannot import game code (build fails if it tries), a single class named `Observer` is the only door from game state to the bot, and every change to `Observer` ships with leak tests. An `oracle` mode may exist for debugging and for training labels; it is off by default, visibly flagged in the UI, and cannot be enabled in ranked rig runs.
2. **License and attribution.** SPD is GPLv3, by Evan Debenham (00-Evan), based on Pixel Dungeon by Watabou. Keep upstream's LICENSE, add `NOTICE.md`, and state in the README that Shatterfish is unofficial and unaffiliated. Never file Shatterfish bugs against the upstream repository.
3. **Upstream does not accept pull requests.** Shatterfish is a permanent downstream repository pinned to a release tag. Every edit to an upstream file is a *hook*: minimal, justified, labeled `touches-upstream`, and listed in `docs/UPSTREAM.md`. Prefer new modules over edits. Upgrades happen only by merging a newer upstream *tag* through the documented procedure, never `upstream/master`.
4. **Java, in-process, v1.** No Rust, no second implementation of the game's rules in any language, no separate bot process over a socket. The bot runs in the same JVM as the game. Search uses either an abstract tactical model derived from Observations or the real engine with hidden state re-sampled (see §4).
5. **Everything is measured and reproducible.** A run is fully determined by (upstream tag, seed, action list). Once the rig exists, no brain change merges without rig numbers in the PR.
6. **Native UI.** The overlay uses SPD's own toolkit: Chrome nine-patch frames, the pixel font through `PixelScene.renderTextBlock`, `RedButton`, `Icons`, sizes consistent with `StatusPane` and `Toolbar`. No Swing, JavaFX, ImGui, or web views.
7. **Issues track state; stories carry content; docs carry knowledge.** GitHub Issues say what is open and done. BMAD story files say what each story is and how it went. `docs/` says how the system works and why. `CLAUDE.md` says how to work. No TODO comment in code without an issue number.
8. **Codex over folklore.** Any claim about game mechanics is settled by reading the pinned code and citing `path:line`, never by memory or a forum post. Forum knowledge enters only through the lore pipeline with provenance and a verification tier.

## Where things live

- **State**: GitHub Issues and milestones at `watchthelight/shatterfish` (always pass `-R watchthelight/shatterfish`; the `upstream` remote points at 00-Evan's repository and `gh` will target it if you let it). `_bmad-output/implementation-artifacts/sprint-status.yaml` once sprint planning has run.
- **Content**: BMAD story/spec files in `_bmad-output/implementation-artifacts/`; planning artifacts in `_bmad-output/planning-artifacts/`.
- **Knowledge**: `docs/` (MkDocs site, `docs/README.md` explains it). ADRs in `docs/adr/`, mechanics rules with `path:line` citations in `docs/rules/`, hooks in `docs/UPSTREAM.md`, ideas in `docs/ideas.md`, published numbers in `docs/results/`.
- **How to work**: this file. Program seed: `docs/BOOTSTRAP-PROMPT.md` (approved BMAD artifacts override it). Tool inventory: `docs/tooling.md`.
- **Shatterfish code**: `shatterfish/<module>/`, package `org.shatterfish.<module>`. Upstream code is everything else; edit it only through hooks.

## Session ritual

1. Read this file. Run `/bmad-help` for where the method thinks you are.
2. Read `sprint-status.yaml` (if it exists) and `gh issue list -R watchthelight/shatterfish --milestone "<current epic>" --state open`.
3. Confirm the one step for this turn with the human (or take it from the last handoff).
4. Do it. Hand off with `/handoff`.

## Turn discipline

- **One step per turn**: one numbered bootstrap step, one BMAD workflow, or one story through its full lifecycle (spec, implement, review). Never start a second story in the same turn. Never try to finish the whole bootstrap in one turn.
- If a step is too big, split at a clean boundary, finish the first half, hand off. Never leave the tree unbuildable at a handoff.
- Every turn ends with a handoff: what was done (with links), what changed in the artifacts, the exact next step, any blocking question. Update `sprint-status.yaml`, the story file, and the GitHub issue first.
- When context grows heavy, `/compact` with the instruction to preserve decisions, open questions, and the next step.
- Ask the human only at the bootstrap checkpoints (A-F) or before irreversible or costly actions: creating remotes, enabling services, installing software, deleting anything, merging an upstream tag.
- Before any non-trivial step: restate goal and binding constraints, list at least three alternatives, pick one and say why, run a short pre-mortem; write the outcome into the artifact (ADR, story file, design note), not just chat. Add *ultrathink* for anything hard, especially anything touching `Observer`.
- Micro-brainstorm before any design decision, even small ones (`/adr` carries the protocol). New ideas mid-story go to `docs/ideas.md`, not into the story.

## Git and GitHub

- Branch per story (`story/<key>`) or bootstrap session (`e0/s<N>-<slug>`); PR per branch; PR body per `.github/PULL_REQUEST_TEMPLATE.md`; `Closes #N` for stories. CI must be green.
- Commit as `watchthelight <admin@watchthelight.org>` (repo-local config), no `Co-Authored-By` trailer. Merge locally with `git merge --no-ff` and push `main`, so the merge commit keeps that identity. No force-push to `main`.
- Any edit to an upstream file: label `touches-upstream`, row in `docs/UPSTREAM.md`, same PR. Any diff near `Observer`, `ActionExecutor`, or `brain`: label `fairness` and run the `fairness-reviewer` subagent before opening the PR.
- Docs and ADRs change in the same PR as the code. Generated files are never hand-edited. No TODO in code without an issue number.

## Skills and subagents

Project skills (`.claude/skills/`): `/next-story`, `/rig`, `/codex`, `/upstream-sync`, `/adr`, `/sync-issues`, `/handoff`. Subagents (`.claude/agents/`): `fairness-reviewer`, `upstream-reader`. Never answer a mechanics question from memory: ask `upstream-reader` or read the pinned code and cite `path:line`. BMAD skills are `bmad-*`; the implementation path is `bmad-build` (spec, implement, adversarial review, sprint-status sync in one workflow). BMAD Method 6.11 with bmm, cis, tea; the `gds` module is installed but unused (see `_bmad/custom/config.toml`).

## Style

Chat replies follow the user's global caveman preference (terse, fragments fine). Code, commits, docs, PR bodies, ADRs, and anything committed are written normally and in full.
