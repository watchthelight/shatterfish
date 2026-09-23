---
title: 'Story 3.5: Registration and the salt discipline'
type: 'feature'
created: '2026-09-23'
status: 'in-review' # draft | ready-for-dev | in-progress | in-review | done
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing stops a number from being chosen after it is seen. The Rig will run a
comparison, look at the outcome, and a bound, a seed set or a stopping rule can be adjusted until
the answer is the wanted one — with no record that anything was adjusted. FR-22 answers this with a
Registration committed *before* the first Run; FR-20's holdout budget and FR-25's public count of
prior attempts have nowhere to live until it exists (issue #116).

**Approach:** A `Registration` is an api value with a canonical form and a hash: hypothesis id, both
Brains with commits and config hashes, seed set and version, α, β, burn-in, maximum, per-Decision
budget, machine class, and whether it claims a release-level result. It lives in `registrations/`
and the Rig refuses one that is not committed to git. The salts are **not** in it — they are drawn
when each pair executes and written to both Run logs, so a Brain's author cannot precompute the
random stream (ADR-0007). An append-only ledger records every Registration, its outcome and every
holdout use, which is where FR-20's budget is finally enforced.

## Boundaries & Constraints

**Always:** A Registration is read from a file git says is committed and unmodified, and its content
hash is what a Run log records — an id alone would let the file be edited after the Runs. The salt
is drawn at execution, never registered, never defaulted (ADR-0007). The holdout rule lives in
exactly one place. The ledger is append-only. Bounds and stopping rules are chosen by the human and
recorded verbatim; this story invents no numbers.

**Ask First:** Changing what `RunLog.Header.registration` accepts in a way that would refuse a log
already published. Adding a Registration field ADR-0012 does not name.

**Never:** Do not put a salt, or anything a salt can be derived from, in a Registration. Do not let
the Rig write a Registration — it reads them; a runner that could author its own defeats the point.
Do not edit a Registration to correct it; a change is a new Registration (ADR-0012). Do not build
the pair loop or the sequential test here — a *comparison* of two Brains is story 3.6, and this
story delivers the Registration it will run under, the refusals, the ledger and the standing
Registration. Do not weaken `SeedSets.publish`'s existing holdout door.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Ranked Run | `--registration H-0001`, file committed | Runs; every log's header carries `H-0001@<hash>` | N/A |
| Not committed | The file exists, git says untracked or modified | Refused before any Run starts, naming the file and what git said | Refusal |
| Absent | `--registration` names an id with no file | Refused, listing the ids that do exist | Refusal |
| Edited after commit | Committed file, working copy differs | Refused; the hash is over the committed bytes, not the working copy | Refusal |
| Holdout, development | Registration does not claim a release-level result, seed set `holdout` | Refused, citing FR-20 | Refusal |
| Holdout, second use | Release-level Registration, ledger already records a holdout use for this Brain version | Refused, naming the earlier use and its date | Refusal |
| Holdout, first use | Release-level Registration, no prior use for this Brain version | Runs; the ledger records the use | N/A |
| No Registration | No `--registration`, development seed set | Runs unranked; logs carry an empty registration, and the summary says the invocation is not ranked | N/A |
| Salt in a Registration | A file carrying a `salt` or `salts` member | Refused when read, naming the member | Refusal |
| Ledger unreadable | `registrations/ledger.jsonl` has a malformed line | Refused rather than appended to — a ledger nobody can read is not a record | Refusal |

</frozen-after-approval>

## Code Map

- `shatterfish/api/src/main/java/org/shatterfish/api/RunLog.java` -- `Header.registration` (line 176,
  validated line 198) is already a chained field carrying `""` in every log to date. `RunLog.salt`,
  `PART_PATTERN` and `Canon` are the patterns to follow for a new canonical value.
- `shatterfish/api/src/main/java/org/shatterfish/api/RunLogJson.java` -- the canonical writer and
  `UNCHAINED` (line 51). A Registration is hashed with the same canonicalization; reuse `JsonWriter`.
- `shatterfish/api/src/main/java/org/shatterfish/api/SeedSet.java` -- `name`, `version` (line 25) are
  what a Registration names; `VERSION` is the schema the Registration records.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/rng/Salt.java` -- `draw()` and the
  javadoc naming ADR-0007's attack. The salt discipline is already correct; this story must not
  weaken it, and the Registration must not carry what it draws.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/log/Json.java` -- the one production
  reader of the canonical grammar (story 3.4). A Registration file is read through it.
- `shatterfish/rig/src/main/java/org/shatterfish/rig/Runner.java` -- `KNOWN`/`SWITCHES`/`MODES`
  (flag list asserted by name in `RunnerTest`), `arguments`, `run`, `commitOf(root)` (the git call to
  copy for the committed-file check), `machine()`, and `index.summary(...)`.
- `shatterfish/rig/src/main/java/org/shatterfish/rig/Brains.java` -- `names()`, `configHash(name)`
  (line 95), `RANDOM`. A Registration names two Brains and their config hashes; these must agree.
- `shatterfish/rig/src/main/java/org/shatterfish/rig/SeedSets.java` -- `publish(root, name, reason)`
  is the existing and only door to `holdout`; `load` refuses it. The budget rule joins that door, it
  does not replace it.
- `shatterfish/rig/src/main/java/org/shatterfish/rig/RunIndex.java` -- `summary(...)` and the atomic
  `.writing` + `ATOMIC_MOVE` write to copy for the ledger.
- `docs/adr/0012-rig-statistics.md:72-75` -- the field list, verbatim. `:95` the holdout rule,
  `:118` the salt mitigation this story implements.
- `docs/adr/0007-rng-seeding-strategy.md:55-62` -- why a salt is never registered.
- GitHub issue #116 -- the half of FR-20 story 3.1 deferred to here.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/.../Registration.java` -- NEW: the record and its canonical form. Fields
  exactly ADR-0012's list plus `releaseLevel`; `hash()` over the canonical text with the log's own
  rules; `id()`; a compact constructor refusing a member named `salt`. In `api` because a Results
  page (3.10) and the Rig both read it, and neither owns it.
- [x] `shatterfish/rig/.../Registrations.java` -- NEW: read `registrations/<id>.json`, refuse one git
  reports untracked or modified, hash the **committed** bytes (`git show HEAD:<path>`), and refuse a
  file carrying a salt. One reader, so "committed before the Run" is checked in one place.
- [x] `shatterfish/rig/.../Ledger.java` -- NEW: `registrations/ledger.jsonl`, append-only, one record
  per invocation: registration id and hash, the commit it was read at, the seed set, the outcome,
  and whether it consumed a holdout use. Refuses a malformed existing line rather than appending.
- [x] `shatterfish/rig/.../Runner.java` -- `--registration <id>`; pass it to every child so the logs
  carry it; refuse `holdout` unless the Registration claims a release-level result; refuse a second
  holdout use for a Brain version; append to the ledger when the invocation ends; say in the summary
  whether the invocation was ranked.
- [x] `shatterfish/api/.../RunLog.java` -- constrain a non-empty `registration` to `<id>@<hash>` so a
  log pins the Registration's content and not merely its name.
- [x] `registrations/H-0001-nightly-smoke.json` -- NEW, committed: the standing Registration story
  3.11's nightly job runs under. Its numbers are the E3 defaults and are stated, not derived.
- [x] `shatterfish/rig/src/test/.../RegistrationsTest.java` -- every row of the I/O matrix.
- [x] `shatterfish/rig/src/test/.../LedgerTest.java` -- append-only, the budget count, a malformed
  line refused.
- [x] `shatterfish/api/src/test/.../RegistrationTest.java` -- the canonical form is stable and the
  hash changes with every field; a salt member is refused.
- [x] `docs/methodology.md` -- what a Registration fixes, why the salt is not in it, the holdout
  budget, and how to read the ledger.

**Acceptance Criteria:**
- Given a Registration committed to git, when a Run is played under it, then every log's header
  carries `<id>@<hash>` where the hash is over the committed bytes (`RegistrationsTest`).
- Given a Registration file that git reports as modified, when the Rig is asked to run under it,
  then it refuses before any Run starts and names what git said (`RegistrationsTest`).
- Given a Registration that does not claim a release-level result, when the `holdout` set is asked
  for, then the Rig refuses citing FR-20, and this is the only place that rule is written
  (`RegistrationsTest`, and a test that no other production class names `holdout`).
- Given a ledger already recording a holdout use for a Brain version, when a second is attempted,
  then it is refused naming the first (`LedgerTest`). Closes the half of #116 story 3.1 deferred.
- Given any Registration, when it is read, then it carries no salt and no member from which one can
  be derived (`RegistrationTest`), and the salts of the Runs it governs appear only in their logs.
- Rig numbers: the cost of the committed-file check and the ledger append on a `standard`
  invocation, in the story's Evidence and beside the E3 throughput numbers on the methodology page —
  a pre-flight that costs a measurable fraction of the measurement is worth knowing before 3.11
  schedules it nightly.

## Evidence

**Rig numbers.** The pre-flight of a ranked invocation -- three questions to git, the ledger read,
the Brain's version -- costs **146 ms**, printed by the Rig on every ranked invocation. Against the
smoke set (25 Runs, 24 processes, 6,438 ms) that is 2.3%; against the standard set (500 Runs, about
160,000 ms, story 3.4's measurement) it is 0.09%. Both are on the methodology page beside the command.

**Deviations from the task list, argued.** `brainA` is optional: ADR-0012 describes comparisons, but
E3's own done-when is a published *baseline* and the nightly job compares nothing, so the first
Registration this project commits would otherwise have had to name a Brain that does not exist. A
Brain's version is the commit that last changed the Brain's own source, not the invocation's
commit: a README typo would otherwise mint a fresh holdout allowance, and a release-level
Registration could never name the commit that carries it. The committed ledger lines from
development runs are real attempts under the standing Registration and stay; they read as
development invocations of the nightly baseline, not failed measurements.

**Four reviews, all blocking.** The blocking finding, found independently by two lenses with a
working exploit: the holdout budget was checked against `commitOf(root)` and recorded against
`--commit`, so the held-out set -- whose triples are derived from a published formula and committed
-- could be spent without limit, each ledger line reading as a lawful first use. Also: "hashes the
committed bytes" was not what the code did, and the test compared the same method on both sides;
with `--root` inside a repository, git's pathspec questions and `git show HEAD:` were about two
different files; `Brains.configHash` was called unconditionally, making every Brain but `random`
unrunnable by the Rig; the salt guard was a word blacklist that could not catch sixteen hex digits
and covered none of the fields shaped like one; a held-out set was recorded after the Runs, so
Ctrl-C left it spent and unrecorded; a refusal burned the allowance; a message-less exception
replaced the refusal that voids a folder; deleting the ledger restored every budget. Every one is
fixed and tested.

**Mutation battery: 29 mutations, 28 killed, 1 deliberate survivor.** Eleven survived their first
run, and **every one was a guard written in answer to a review**: the budget key, the claim before
the set is read, the Seed set version, the null-message note, the id check, the git-answerable
check, the subdirectory prefix, `commit()` failing closed, the newline before an append, the
committed-prefix check, and the header's stamp pattern. Each now has a test that kills it. M12 --
hashing the committed text versus the re-rendering -- survives because the canonical check makes the
two equal by construction; it is kept as written and says so in the code.

**Process.** The battery harness restores only the file it mutated, and twice this story an
uncommitted edit in that file was discarded by the restore. Commit before each batch.

## Design Notes

**Why the hash and not the id.** The header's `registration` was free text carrying `""`. If a log
records only `H-0001`, the file it names can be edited and re-committed after the Runs and every log
still agrees with it — git would show the edit, but the log would not pin it. `H-0001@<16 hex>` is
readable, greppable, and pins content. No published log carries a non-empty value, so constraining
the field tightens schema 2 rather than needing a 3.

**Why git is the authority.** "Committed before the Run" is a claim about time that a file cannot
make about itself. `git ls-files` plus `git status --porcelain` answers whether the bytes the Rig
hashed are the bytes the repository holds; `git show HEAD:<path>` is what gets hashed, so an
uncommitted edit changes nothing the Rig will use. The Registration's own commit goes in the ledger,
which is what makes "before" checkable by a stranger.

**Where the comparison is.** A *comparison* of two Brains is story 3.6. This story delivers the
Registration, the refusals, the ledger and the standing Registration, and the Runner requires a
Registration for a ranked invocation. 3.6 wires the pair loop into the same door rather than a
second one.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` -- green, every module.
- `./gradlew :rig:run --args="--brain random --seeds smoke --out <a> --registration H-0001-nightly-smoke"`
  -- runs, every log's header carries the id and hash, and `registrations/ledger.jsonl` gains one line.
- `./gradlew :rig:run --args="--brain random --seeds holdout --out <b> --registration H-0001-nightly-smoke"`
  -- refused, citing FR-20.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- green.
