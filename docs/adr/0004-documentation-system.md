---
status: accepted
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0004: Documentation system

## Context and problem statement

Non-negotiable #7: docs carry knowledge; issues track state; stories carry content. The
bootstrap prompt (session 3) asks for MkDocs Material, `mkdocs build --strict` in CI, GitHub
Pages via Actions, a fixed skeleton, and BMAD's output folder in the nav. Three things needed a
decision: how the Python toolchain is pinned in a Java repository, how BMAD's artifacts (which
live in `_bmad-output/`, outside `docs/`) reach the site, and what to do with upstream's own
`docs/` files, which link to repository files and would fail a strict build.

Non-negotiables touched: #3 (upstream files untouched), #5 (reproducibility), #7.

## Considered options

**Python toolchain**

1. Root `pyproject.toml` with a docs dependency group. Rejected: a Python project file at the
   root of a Java repository confuses tooling and readers.
2. `pip install mkdocs-material` unpinned. Rejected: not reproducible.
3. **`docs/requirements.in` compiled to a fully pinned `docs/requirements.txt` with
   `uv pip compile`; every invocation is
   `uv run --no-project --with-requirements docs/requirements.txt mkdocs ...`.** Chosen: one
   file to bump, identical environment locally and in CI, no virtualenv to manage, and `uv` is
   already required by BMAD.

**BMAD artifacts on the site**

4. Change BMAD's output folder to `docs/bmad/` through `_bmad/custom/config.toml`. Rejected:
   BMAD 6.11 still carries the legacy `_bmad/bmm/config.yaml` with hard-coded paths, so some
   skills would write to one folder and some to the other. Silently split artifacts is the
   worst outcome.
5. Copy `_bmad-output/**/*.md` into a gitignored `docs/bmad/` before each build. Rejected: a
   second tree that goes stale between builds; `mkdocs serve` does not see changes.
6. `docs_dir: .` with a large `exclude_docs`. Rejected: fragile and slow.
7. A symlink. Rejected: Windows.
8. **A MkDocs hook (`docs/hooks/bmad_artifacts.py`) that registers every Markdown file under
   `_bmad-output/` as a generated page at `bmad/<relative path>` and writes a generated
   `bmad/index.md`.** Chosen: nothing copied, nothing moved, the nav has one stable entry, and
   the pages are subject to the strict build like everything else.

**Upstream `docs/` files**

9. Keep them as site pages and downgrade link validation. Rejected: weakens the check for the
   whole site.
10. Fix their links. Rejected: edits to upstream files for no functional gain.
11. **Exclude them with `exclude_docs` and link to them on GitHub from the nav.** Chosen. Also
    excluded: `docs/README.md` (its body is included in `index.md` as a snippet so the file
    keeps its GitHub-facing name), `FUNDING.yml`, `PULL_REQUEST_TEMPLATE.md`, the requirements
    files, and the hook.

**Pull request template**

12. GitHub reads PR templates from `docs/`, so upstream's "this repository does not accept pull
    requests" template would greet every Shatterfish PR. A template in `.github/` takes
    precedence, so `.github/PULL_REQUEST_TEMPLATE.md` carries the PR body shape from the
    working agreements instead. Upstream's file is untouched.

## Decision outcome

MkDocs Material 9.7 pinned via `uv`, strict build on every PR, deploy from `main` to GitHub
Pages with `actions/deploy-pages` (`.github/workflows/docs.yml`), the hook above for BMAD
artifacts, upstream guides excluded and linked, `docs/README.md` as the GitHub-facing
explanation with its body snippeted into the home page. Nav and skeleton as listed in the
bootstrap prompt, session 3.

### Consequences

- Good: a broken link anywhere, including inside a BMAD artifact, fails the PR.
- Good: no upstream file edited; the docs system is entirely additive.
- Bad: the hook is a small piece of Python to maintain against MkDocs' `File.generated` API
  (1.6+).
- Bad: `docs/adr/index.md` and `mkdocs.yml` nav must be updated by hand when an ADR is added
  (see `docs/ideas.md` for generating it).

## Pre-mortem

*If this is wrong in six months, why?*

- A BMAD artifact with a broken relative link blocks an unrelated PR. Mitigation: it is the
  right failure; the fix is one link. If it becomes a nuisance, link validation for the
  `bmad/` pages could be relaxed in the hook, but that is a deliberate loosening to record in a
  new ADR. (The pages are already registered as `NOT_IN_NAV`, which only exempts them from the
  omitted-from-nav warning; links are still validated.)
- MkDocs changes the hook API. Mitigation: versions are pinned; bumps are a PR with the strict
  build as the test.
- GitHub Pages deployment from Actions needs the repository's Pages source set to "GitHub
  Actions"; enabled once via the API in session 3. If the repository is ever recreated,
  re-run `gh api -X POST repos/watchthelight/shatterfish/pages -f build_type=workflow`.
