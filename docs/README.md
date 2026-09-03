# Documentation system

This folder is both the MkDocs source for <https://watchthelight.github.io/shatterfish/> and
the place GitHub readers land. The body below is pulled into the site's home page.

<!-- --8<-- [start:body] -->

**Build locally**

```sh
uv run --no-project --with-requirements docs/requirements.txt mkdocs serve
uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict
```

Dependencies are pinned in `docs/requirements.txt`, compiled from `docs/requirements.in` with
`uv pip compile`. CI runs the strict build on every pull request and deploys `main` to GitHub
Pages (`.github/workflows/docs.yml`). A warning is a failure.

**Hand-written, generated, mirrored**

| Kind | Where | Who edits it |
|---|---|---|
| Hand-written knowledge | `docs/*.md`, `docs/adr/`, `docs/rules/` | People and the engineer, in the same PR as the code it describes |
| Generated | `docs/codex/` | The `:codex:generate` Gradle task only (E2); CI fails if the committed copy drifts. Never hand-edited |
| Published measurements | `docs/results/<date>-<sha>.md` | The rig (E3), through a reviewed PR |
| Mirrored | `bmad/` on the site | Rendered from `_bmad-output/` by `docs/hooks/bmad_artifacts.py` at build time; edit the BMAD files, not the site |
| Kept verbatim from upstream | `docs/getting-started-*.md`, `docs/recommended-changes.md` | Nobody; they are upstream files served from GitHub, excluded from the site because they link to repo files |
| Program seed | `docs/BOOTSTRAP-PROMPT.md` | Frozen; approved BMAD artifacts override it |

**Rules**

1. Docs change in the same PR as the code they describe. A PR that changes behaviour and not
   `docs/` needs a sentence in its description saying why.
2. Every statement about a game mechanic carries a `path:line` citation into the pinned upstream
   tag and, once it exists, a link to the test that checks it. See [Rules](rules/index.md).
3. Decisions go in ADRs (`docs/adr/`, MADR format). Ideas that are not being acted on go in
   `docs/ideas.md`, one line each with a rationale.
4. Generated files are never hand-edited.
5. `docs/UPSTREAM.md` is updated in the same PR as any hook (`touches-upstream`).

<!-- --8<-- [end:body] -->
