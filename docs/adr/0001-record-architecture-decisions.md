---
status: accepted
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0001: Record architecture decisions

## Context and problem statement

Shatterfish will be built over many sessions by one engineer with a product owner reviewing. Decisions made in chat evaporate; decisions made in code lose their rejected alternatives. The bootstrap prompt (`docs/BOOTSTRAP-PROMPT.md` §2.2) requires a micro-brainstorm before every design decision and a written record of the option chosen and the options rejected, so that nothing is reopened without new information.

## Decision drivers

- State must live on disk, not in chat history (BMAD principle, bootstrap prompt §2.3, §8).
- Reviewers need the rejected alternatives to judge a decision, not just the outcome.
- Docs change in the same PR as the code they describe (§8).

## Considered options

1. **MADR files under `docs/adr/`**, one per decision, numbered, immutable once accepted (superseded by a new ADR).
2. Decision log as a single growing `docs/decisions.md`.
3. Decisions recorded only in BMAD story files.
4. Decisions recorded in GitHub Discussions or issues.
5. Decisions recorded in the architecture document only.

## Decision outcome

Option 1, **MADR** (Markdown Any Decision Records), in `docs/adr/NNNN-slug.md` with the frontmatter `status`, `date`, `deciders`, and the sections *Context and problem statement*, *Decision drivers*, *Considered options*, *Decision outcome* (with *Consequences*), and *Pre-mortem*. The MkDocs site (Session 3) lists them; the `adr` project skill (Session 4) drafts them.

Rules:

- A micro-brainstorm that changes a design decision produces an ADR or a design note in the story file; small in-story decisions go in the story, cross-cutting ones here.
- An ADR is never edited after acceptance except to change `status` to `superseded by ADR-NNNN`.
- BMAD's architecture document points at ADRs for its rationale rather than restating it.

### Consequences

- Good: every reviewer, including future sessions, sees why and what else was considered.
- Bad: one more file per decision; mitigated by the `adr` skill and a strict one-page target.

### Rejected

- (2) one file grows unreadable and merges badly.
- (3) stories are per-task; cross-cutting decisions get lost.
- (4) off-repo; violates "docs carry knowledge".
- (5) the architecture doc should state invariants, not carry every rejected option.

## Pre-mortem

*If this is wrong in six months, why?* ADRs went stale because nobody read them. Mitigation: the `next-story` skill lists ADRs touching the story's modules, and the fairness-reviewer subagent is given the fairness ADRs as input.
