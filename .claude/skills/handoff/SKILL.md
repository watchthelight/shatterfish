---
name: handoff
description: Write the end-of-turn handoff for Shatterfish in the standard shape (done with links, artifacts changed, verification, exact next step, blocking questions, incidents) and update sprint-status.yaml, the story file, the GitHub issue, docs/roadmap.md and the memory note first. Use at the end of every turn, when the user says "hand off", "wrap up", or invokes /handoff.
---

# handoff

Every turn ends with a handoff. State lives on disk and on GitHub, not in chat, so the updates
come first and the message last.

## Update first

After a merge, `python tools/after-merge.py <PR>` does items 1, 2 and the README check, and
says whether the codex needs regenerating.

1. The GitHub issue and PR (`-R watchthelight/shatterfish`): the PR carries the numbers, the
   issue a comment with the PR link and status; `Closes #N` closes it on merge.
2. `sprint-status.yaml` (E-stories only): the story's status matches reality and `last_updated`
   is today. Fixes have no sprint-status key and no story file.
3. The story spec file (E-stories only): status frontmatter and a "Review" section.
4. `README.md` "Where it stands": one line for each merged story or fix.
5. `docs/ideas.md` for anything that came up and was not done; `docs/roadmap.md` only if an
   epic changed state.
6. One watchthelight note (project `shatterfish`): revise the open-loop status note rather than
   adding a new one; echo the receipt.
7. Tree state: everything committed and pushed, or a clean explanation of what is not and why.
   Never hand off an unbuildable tree.

## Then write the message

```markdown
<Session N | Story <key>> complete | paused at <boundary>.

**Done**
- <what>, with links: PR #, commit, file paths, site URL

**Artifacts changed**
- sprint-status / story file / issue / docs / ADRs

**Verification**
- ./gradlew build: <tests, failures>; CI: <workflow: result>; docs strict: <result>; rig: <file or n/a>

**Incidents** (omit if none)
- what happened, what was done, what remains

**Next step**
- exactly one step: bootstrap session N+1 (what it contains) or `/next-story` (which story)

**Blocking questions**
- none | the question, and what happens under each answer
```

Keep it readable by someone who did not watch the turn. Links over prose. If context is heavy,
end with a note that the next session should start with `/compact` preserving decisions, open
questions, and the next step.
