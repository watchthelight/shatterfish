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
