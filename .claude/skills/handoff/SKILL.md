---
name: handoff
description: Write the end-of-turn handoff for Shatterfish in the standard shape (done with links, artifacts changed, verification, exact next step, blocking questions, incidents) and update sprint-status.yaml, the story file, the GitHub issue, docs/roadmap.md and the memory note first. Use at the end of every turn, when the user says "hand off", "wrap up", or invokes /handoff.
---

# handoff

Every turn ends with a handoff. State lives on disk and on GitHub, not in chat, so the updates
come first and the message last.

## Update first

1. `sprint-status.yaml` (if it exists): the story's status matches reality (`in-progress`,
   `review`, `done`); `last_updated` refreshed. `bmad-build` normally does this; verify.
2. The story spec file: status frontmatter, a "Dev notes" or "Review" section reflecting what
   happened, any design note with links to ADRs.
3. The GitHub issue (`-R watchthelight/shatterfish`): for a story, a comment with the PR link
   and status, closed by the PR's `Closes #N` on merge; for a bootstrap session, tick the
   session's box on the E0 epic issue (#1) and any definition-of-done boxes it completed.
4. `docs/roadmap.md` if an epic or session state changed.
5. `docs/ideas.md` for anything that came up and was not done.
6. The memory note (`shatterfish-program` in the memory directory): status line, next step,
   new gotchas.
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
