# Ideas

Things worth doing that are not being done now. One line each with a rationale. New ideas that
come up mid-story go here instead of expanding the story; they re-enter through BMAD's
correct-course workflow or the next create-story.

| Idea | Why | Raised |
|---|---|---|
| Citation checker for `docs/rules/`: a Gradle task (in `codex`) that resolves every `path:line` against the pinned tag and fails if the cited line changed | Rules silently rot on upgrade otherwise; makes step 9 of the upgrade procedure mechanical | session 3 |
| Generate `docs/adr/index.md` from ADR frontmatter with the MkDocs hook | Hand-maintained lists drift; the hook already scans one tree | session 3 |
| Promote the Windows build from nightly to the PR gate if it ever catches something ubuntu did not | Product owner develops on Windows; see ADR-0002 pre-mortem | session 2 |
| Convention plugin (`buildSrc`) instead of `apply from` once the shared Gradle script grows past a screen | IDE support and type safety; see ADR-0003 option 6 | session 2 |
| Public leaderboard of brain versions on the standard seed set | Community visibility once E3 numbers exist | session 5 brainstorm |
| Bot-vs-seed challenge issues: anyone submits a seed, the rig runs it, result published | Cheap engagement and a stream of hard seeds | session 5 brainstorm |
| Decision narration as a text stream (screen-reader-friendly view of a run) | Accessibility tangent; falls out of the decision log | session 5 brainstorm |
| GitHub Actions matrix as distributed rig workers, Fishtest-style | Free compute for public repos; needs sharded seed sets (ADR-0002 pre-mortem) | session 5 brainstorm |
| Headroom metric: oracle-assisted per-seed upper bound, measurement only | Shows how much is left to gain; must never touch play | session 5 brainstorm |
| Strength per think budget (bullet/blitz/classical) once search exists | Makes speed/strength trade-offs explicit | session 5 brainstorm |
| Post per epic on the docs site telling the numbers as a story | Motivation and transparency | session 5 brainstorm |
