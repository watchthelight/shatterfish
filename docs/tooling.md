# Tooling inventory

*Written in Session 1 (2026-09-03). Re-verify when Claude Code, BMAD, or the skill set changes. Rule: for any task that BMAD has a workflow for, use the BMAD skill; use the user-level skill only where flagged below as complementary.*

## 1. Environment (laptop)

| Item | Found | Notes |
|---|---|---|
| OS / shell | Windows 11 Pro 10.0.26200; PowerShell 7.6 primary, Git Bash (MSYS) available | Commands in this repo are written for Git Bash unless noted; `pwsh` on PATH |
| git | 2.52.0.windows.1 | |
| gh | 2.93.0 | Logged in as `watchthelight`, SSH protocol; scopes `repo`, `admin:org`, `admin:public_key`, `delete_repo`, `gist` |
| Node.js / npm | v20.20.0 / 10.8.2 | Meets BMAD's 20+ requirement |
| Python | 3.11.9 (`python`), 3.13.14 (`python3`) | MkDocs will use `uv` |
| uv | 0.11.15 | Hosts `bmad-loop`; runs BMAD's `resolve_config.py` |
| JDK | Zulu 21.0.11 LTS (`JAVA_HOME`), also Microsoft OpenJDK 21.0.10 / 21.0.11 | Upstream recommends Java 21; `appJavaCompatibility = VERSION_11`; Gradle wrapper 9.4.0 |
| tmux / psmux | absent | Only matters for `bmad-loop run` (unattended loop); not needed for the interactive sessions |
| Android SDK | absent, by design | Must never be required |

## 2. BMAD Method

| Item | Value |
|---|---|
| Version | 6.11.0 (installed 2026-09-03) |
| Modules | core 6.11.0, **bmm** 6.11.0, **cis** v0.3.2, **tea** v1.24.0, bmb v2.2.2, gds v0.7.2, bmad-loop v0.11.1 |
| Tool | Claude Code; skills under `.claude/skills/` (110 dirs) |
| Output folder (actual) | `_bmad-output/` — `planning-artifacts/`, `implementation-artifacts/`, `test-artifacts/`. The bootstrap prompt asked for `docs/bmad/`; the installer default was kept. MkDocs nav will point at `_bmad-output/planning-artifacts/`. |
| Project knowledge folder | `docs/` |
| Config | `_bmad/config.toml` (installer-owned, read-only), `_bmad/bmm/config.yaml`; overrides go in `_bmad/custom/` |
| User name / language | Bash / English |
| Help catalog | `_bmad/_config/bmad-help.csv`; `/bmad-help` reads it |

### 2.1 Phase → skill name map (this BMAD version)

| Phase | Bootstrap-prompt name | Skill to invoke | Notes |
|---|---|---|---|
| 1 | brainstorming | `bmad-brainstorming` | CIS techniques are inside it; `bmad-cis-*` are the persona/agent variants |
| 1 | research | `bmad-deep-recon` | Types: market, domain, technical, competitive, user-voice, academic-lit. `bmad-technical-research` / `bmad-domain-research` / `bmad-market-research` are deprecated forwards |
| 1 | product brief | `bmad-product-brief` | |
| 2 | create PRD | `bmad-prd` | `bmad-create-prd` / `bmad-edit-prd` deprecated forwards |
| 2 | validate PRD | `bmad-prd` (validate intent) or `bmad-validate-prd` | Run in an isolated subagent per §2.1 of the bootstrap prompt |
| 2 | create UX design | `bmad-ux` | |
| 3 | document-project | `bmad-project-context` | `bmad-document-project` deprecated forward. Produces the AGENTS.md block; `docs/codebase-map.md` is hand-folded from it |
| 3 | create architecture | `bmad-architecture` | `bmad-create-architecture` deprecated forward |
| 3 | create epics and stories | `bmad-create-epics-and-stories` (also `bmad-spec` "break into stories") | |
| 3 | check implementation readiness | `bmad-sprint-planning` (readiness gate) | No standalone readiness skill in bmm 6.11; `gds-check-implementation-readiness` is the game-dev variant, not used |
| 4 | sprint planning / status | `bmad-sprint-planning` | `bmad-sprint-status` deprecated forward |
| 4 | create story / dev story | `bmad-build` | `bmad-create-story`, `bmad-dev-story`, `bmad-quick-dev` are deprecated; `bmad-build` is the official implementation path |
| 4 | code review | `bmad-code-review`, `bmad-review` (multi-lens) | `bmad-review-*` singles are deprecated forwards |
| 4 | correct-course | `bmad-correct-course` | |
| 4 | retrospective | `bmad-retrospective` | Supports `-H/--headless` |
| any | advanced elicitation / critique | `bmad-advanced-elicitation` | Pre-mortem, red team, first principles — use for the §2.1 "three alternatives + pre-mortem" ritual |
| any | idea pressure test | `bmad-forge-idea`, `bmad-prfaq` | |
| any | help | `bmad-help` | |
| TEA | test design / ATDD / automate / CI / NFR / trace / review | `bmad-testarch-*`, `bmad-tea` | Fairness and determinism tests in E1 should be designed through `bmad-testarch-test-design` |
| unattended | loop orchestrator | `bmad-loop` CLI (uv tool), `bmad-loop-resolve`, `bmad-loop-sweep` | Requires tmux/psmux and a `sprint-status.yaml`; not for interactive sessions |

Agent personas available: `bmad-agent-analyst` (Mary), `bmad-agent-pm` (John), `bmad-agent-architect` (Winston), `bmad-agent-dev` (Amelia), `bmad-agent-ux-designer` (Sally), `bmad-agent-builder`; CIS: brainstorming coach (Carson), creative problem solver (Dr. Quinn), design thinking (Maya), innovation strategist (Victor), presentation (Caravaggio), storyteller (Sophia); `bmad-party-mode` for roundtables.

The `gds-*` skills (Game Dev Studio module) are installed but **not used**: Shatterfish is an engine/bot project, not a game, and running both tracks would produce duplicate artifacts.

## 3. User-level skills (`~/.claude/skills/`)

| Skill | What it does | Shatterfish use | Overlap |
|---|---|---|---|
| **supersearch** | Maximum-effort research: decomposes topic, dynamic parallel agents, source audit, contradiction hunt, adversarial synthesis, quality gates, expert-grade dossier | **The super-search skill.** Lore intake for E7 (`lore/` claims with provenance) and the deep dives in Session 6 research | Complements `bmad-deep-recon`: run deep-recon as the BMAD artifact producer, call `/supersearch` inside it for the hard questions; never both as standalone artifacts |
| deepsearch | Lighter research pipeline (parallel web agents, synthesis, quality gates); Exa MCP if present, else WebSearch | Quick technical lookups where supersearch is overkill | Same overlap rule as above |
| deepstorm | BMAD-aligned aggressive interrogation: question rounds, forced tradeoffs, option trees → architecture-ready direction | Micro-brainstorms (§2.2) on design decisions | Overlaps `bmad-brainstorming` / `bmad-forge-idea`. Use BMAD for Phase 1 artifacts; deepstorm optional for in-session micro-brainstorms that don't produce a BMAD artifact |
| graphify | Any input → knowledge graph, communities, HTML/JSON/audit | Session 10 codebase mapping of upstream; `docs/codebase-map.md` input | Complements `bmad-project-context` |
| mirror | Answers Claude Code internals questions from local source | Debugging hooks/skills behaviour | none |
| caveman, caveman-commit, caveman-compress, caveman-review | Compressed communication; commit messages; memory-file compression; one-line reviews | Chat style (active by default); `caveman-commit` for commit messages | `caveman-review` overlaps `bmad-code-review`; BMAD wins for story reviews |
| humanize | Strip AI writing patterns | README / docs prose pass | none |
| whatif | xkcd-style physical estimates | none | — |
| allbyte, bug-ticket | Pawtropolis Discord bot tasks | none (other project) | — |
| cloudflare, wrangler, workers-best-practices, durable-objects, agents-sdk, sandbox-sdk, cloudflare-email-service, turnstile-spin | Cloudflare platform | none | — |
| web-perf | Chrome DevTools performance audit | none | — |
| decompile | Binary reverse engineering | none (upstream is source-available) | — |
| updateextrausage | Statusline credit balance | none | — |

Invocation of the super-search skill: `/supersearch <question>` or, in prose, "use supersearch on …". Its outputs go into `_bmad-output/planning-artifacts/research/` when produced for BMAD, or one file per claim in `lore/` per the lore intake format.

## 4. Bundled / plugin skills and subagents

| Item | Type | Use |
|---|---|---|
| `code-review`, `simplify`, `security-review` | bundled skills | Second-opinion reviews on PRs; `bmad-code-review` remains primary for stories |
| `init`, `update-config`, `keybindings-help`, `fewer-permission-prompts` | bundled | Claude Code housekeeping (Session 4) |
| `loop`, `schedule` | bundled | Nightly rig runs are GitHub Actions, not these; possible local use for long rig runs |
| `run` | bundled | Launch `./gradlew :desktop:run` / overlay for visual checks (E5) |
| `design`, `frontend-design`, `dataviz`, `artifact-*` | bundled/plugin | `dataviz` for `docs/results/` charts; `design` for overlay panel mockups in the UX spec (mockup only; real UI is Noosa) |
| `claude-api` | bundled | none (no LLM in v1) |
| `rust-analyzer-lsp` plugin | plugin | none (Java project; no Rust per non-negotiable 4) |
| `malware-static-analysis` plugin | plugin (project-scoped elsewhere) | none |
| Subagents: `Explore`, `Plan`, `general-purpose`, `claude-code-guide`, `statusline-setup` | built-in agent types | `Explore` for upstream code sweeps; `general-purpose` with an adversarial prompt for isolated BMAD validation; project subagents `fairness-reviewer` and `upstream-reader` are created in Session 4 |
| `~/.claude/agents/` | — | none exist |

## 5. MCP servers (`claude mcp list`)

| Server | Use |
|---|---|
| chrome-devtools, playwright, lighthouse | none for v1 (no web UI). Playwright could drive the MkDocs site check |
| blender | none |
| claude.ai Google Drive / Gmail / Calendar | none; not to receive project data |
| claude.ai Mermaid Chart | Architecture diagrams if MkDocs mermaid is insufficient |
| claude.ai Spotify / Booking.com / Expedia | none |

## 6. Global instructions

`~/.claude/CLAUDE.md`: graphify trigger only. `C:\Users\Claude-Code\CLAUDE.md`: folder conventions and caveman-mode default (prose only; code, commits, and warnings stay normal). Project `CLAUDE.md` is written in Session 4 and takes precedence for repo rules.
