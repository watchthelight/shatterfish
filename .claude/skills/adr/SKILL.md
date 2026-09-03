---
name: adr
description: Draft a Shatterfish architecture decision record (MADR) after a micro-brainstorm of at least five options scored against the non-negotiables, with a pre-mortem, and wire it into docs/adr/index.md and the MkDocs nav. Use when the user says "write an ADR", "record this decision", "micro-brainstorm", or invokes /adr.
---

# adr

Every design decision, including small ones, gets a timeboxed micro-brainstorm and a written
record of the option chosen and the options rejected (bootstrap prompt, section 2.2). Do not
reopen a recorded decision without new information.

## Micro-brainstorm protocol

1. **The question** in one sentence, and the non-negotiables it touches (by number, from
   CLAUDE.md).
2. **At least five options.** Use the CIS techniques: first principles, inversion, SCAMPER, and
   analogies from chess engines, NetHack bots, debuggers, and game trainers. For a hard decision
   (anything touching `Observer`, search, fairness, threading) run `bmad-advanced-elicitation`
   with the pre-mortem or red-team method on the draft, or `/deepstorm` for a forced-tradeoff
   interrogation, before writing.
3. **Score** each option against the non-negotiables it touches and the current epic's goal.
   One line per option: keep / reject and why.
4. **Pick one**, then run the pre-mortem: "if this is wrong in six months, why?" with a
   mitigation per failure mode.

## Writing the record

- Number: one more than the highest `docs/adr/NNNN-*.md`. File: `docs/adr/NNNN-<slug>.md`.
- Template (identical to ADR-0001 to ADR-0004):

  ```markdown
  ---
  status: proposed | accepted | superseded by ADR-NNNN
  date: YYYY-MM-DD
  deciders: watchthelight (product owner), Claude (engineer)
  ---

  # ADR-NNNN: <title>

  ## Context and problem statement
  ## Decision drivers
  ## Considered options
  ## Decision outcome
  ### Consequences
  ## Pre-mortem
  ```

- Cite `path:line` at the pinned tag for any claim about upstream code (ask `upstream-reader`).
- Add the row to `docs/adr/index.md`, remove the item from its "Decisions still to make" list,
  add the nav entry in `mkdocs.yml` under `Decisions`, and run the strict docs build.
- If the decision was made inside a story, also add a one-paragraph design note with a link to
  the ADR in the story's spec file.
- An accepted ADR is never edited except to change `status`. A change of mind is a new ADR
  that supersedes it.

## Output

Report the file path, the chosen option in one sentence, and the rejected options in one line
each. If the decision has consequences for open stories, name them.
