---
name: sync-issues
description: Mirror Shatterfish epics and stories from the BMAD epics file and sprint-status.yaml to GitHub milestones and issues, idempotently (marker comments in issue bodies), and refresh docs/roadmap.md. Use when the user says "sync issues", "mirror the stories to GitHub", "update the roadmap", or invokes /sync-issues.
---

# sync-issues

GitHub Issues say what is open and done; story files say what each story is. This skill keeps
the two aligned in one direction: BMAD artifacts are the source, GitHub is the mirror. It is
safe to run any number of times.

## Sources

- Epics: `_bmad-output/planning-artifacts/epics*.md` (or a sharded `epics/` folder), parsed the
  way BMAD's `sprint_plan.py` does: `## Epic N: Title` and `### Story N.M: Title`; story key
  `N-M-<kebab title>`.
- Status: `_bmad-output/implementation-artifacts/sprint-status.yaml`, `development_status`.
- Story spec files: `_bmad-output/implementation-artifacts/*.md` whose frontmatter names the
  story, for the link in the issue body.

If sprint-status does not exist yet, mirror epics only and say so.

## Targets, all at `-R watchthelight/shatterfish`

| Object | Identity | Contents |
|---|---|---|
| Milestone per epic | title starts with `E<N> ` (E0-E9 already exist; create beyond that) | description = the epic's "done when" |
| Epic issue | body contains `<!-- shatterfish:epic N -->` | title `E<N> <title>`, labels `type:epic`, `epic:E<N>`, milestone; body: goal, "done when", a task list with one `- [ ] #<story issue>` per story (ticked when the story is `done`) |
| Story issue | body contains `<!-- shatterfish:story <key> -->` | title `E<N>.<M> <title>`, labels `type:story`, `epic:E<N>`, `area:<...>` from the modules it names, plus `fairness` if it touches `Observer`/`ActionExecutor`/`brain`, `touches-upstream` if it names an upstream file; milestone; body: link to the spec file on GitHub (`main` branch path), acceptance criteria, current sprint status |

State mapping: `done` closes the issue; anything else keeps or reopens it. Do not touch issues
without a marker (they are humans' issues).

## Scope

Create story issues only for the current epic and the next one (bootstrap prompt, section 9).
Epic issues for every epic are fine. Never delete an issue; if a story disappears from the
epics file, comment on its issue and add the `needs-triage` note, leave it open.

## Steps

1. Parse the sources; build the desired state.
2. Read the current state: `gh issue list -R watchthelight/shatterfish --state all --limit 500 --json number,title,body,state,labels,milestone`
   and `gh api repos/watchthelight/shatterfish/milestones?state=all`.
3. **Dry run first**: print the plan as create / update / close / reopen lines. Apply without
   asking unless the plan deletes or closes more than three issues at once.
4. Apply with `gh api` (REST) or `gh issue create/edit -R watchthelight/shatterfish`. Always
   pass the repository explicitly.
5. Update `docs/roadmap.md`: the epic table's status column and the per-epic story lists
   (issue links). Run the strict docs build.
6. Report counts: created, updated, closed, unchanged, and anything skipped with the reason.

## Rules

- Idempotent: a second run right after the first changes nothing.
- The marker comment is the identity; titles may change freely.
- Never create anything at the upstream repository.
