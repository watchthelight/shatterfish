---
name: next-story
description: Pick the next Shatterfish story from sprint-status.yaml and the GitHub milestone, enforce one story at a time, open the story branch, and hand the story to bmad-build. Use when the user says "next story", "start the next story", "what's next in the sprint", or invokes /next-story.
---

# next-story

One story moves through its whole lifecycle before the next begins. This skill finds that story,
prepares the branch and the issue, and hands it to `bmad-build`, which owns spec creation,
implementation, adversarial review, and the `sprint-status.yaml` sync.

## Inputs

| Input | Path | If missing |
|---|---|---|
| Sprint status | `_bmad-output/implementation-artifacts/sprint-status.yaml` | Say that sprint planning has not run; suggest `bmad-sprint-planning`; stop |
| Epics | `_bmad-output/planning-artifacts/epics*.md` (or `epics/`) | Stop; the story text lives there |
| Issues | `gh issue list -R watchthelight/shatterfish --milestone "<epic title>" --state open --json number,title,body` | Run `/sync-issues` first, then continue |
| ADRs | `docs/adr/*.md` | none |

## Steps

1. **Preconditions.** On `main`, clean tree, `git pull --ff-only`. Otherwise stop and say what is
   in the way. Never start a story on top of another story's branch.
2. **Choose the story**, in this order, from `development_status` in sprint-status:
   1. Any story `in-progress` or `review`: that is the story; resume it. Two active stories is a
      turn-discipline violation, so report it and ask which one to finish first.
   2. Else a story `ready-for-dev`.
   3. Else the first `backlog` story, in file order, of the lowest-numbered epic that is not
      `done`.
   Never pick a story from an epic beyond the current and the next one (bootstrap prompt,
   section 9). Never pick two.
3. **Find its issue.** Search open and closed issues for the marker
   `<!-- shatterfish:story <key> -->` in the body. If absent, run `/sync-issues` and search
   again. Record the number `N`.
4. **Brief the human** in a short block: key, title, acceptance criteria summary, issue `#N`, the
   ADRs that mention the modules the story touches, and a **fairness flag** if the story touches
   `Observer`, `ActionExecutor`, anything in `brain`, or the Observation schema. Do not wait for
   an answer unless the human asked to be asked.
5. **Branch.** `git checkout -b story/<key>`.
6. **Hand off to `bmad-build`.** Invoke the `bmad-build` skill with this intent, verbatim:

   > Implement story `<key>` ("<title>") from `<epics file>`. Sprint-status key `<key>`.
   > GitHub issue #N. Branch `story/<key>`. Follow CLAUDE.md. The acceptance criteria must name
   > the tests they require; from E3 onward, the rig numbers too.

   `bmad-build` writes the spec into `_bmad-output/implementation-artifacts/`, moves the story
   to `in-progress`, implements, runs its own review layers, and moves it to `review`. Do not
   duplicate any of that here.
7. **After `bmad-build` returns.** If the fairness flag was set, run the `fairness-reviewer`
   subagent on `git diff main...HEAD` and address every finding before opening the PR. Then:
   `./gradlew build` green, docs updated in the same branch, PR from the template with
   `Closes #N`, labels `epic:E<n>`, `area:<module>`, plus `fairness` / `touches-upstream` when
   they apply, milestone set. Watch CI. Merge only when green (and, from E3, with rig numbers
   in the PR). Then `/handoff`.

## Rules

- One story per turn. If the story cannot finish in this turn, stop at a clean, buildable
  boundary and hand off with the story left `in-progress`.
- Do not create story issues beyond the current and next epic.
- If the story reveals the plan is wrong, stop and run `bmad-correct-course`; never drift.
