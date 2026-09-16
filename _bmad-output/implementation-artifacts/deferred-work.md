# Deferred work

Findings surfaced by a story's review that are not that story's problem, one entry each.

- source_spec: `_bmad-output/implementation-artifacts/1-17-the-differential-and-toggle-tests.md`
  summary: The mutation batteries every story runs live in the session scratchpad and are not committed, so a story's "verified once" evidence is prose a reviewer cannot rerun.
  evidence: Stories 1.13 to 1.17 each record a battery's output in the story file and keep the script (`mutations<story>.py`) outside the repository; the blind review of 1.17 asked for it to be committed under a tools directory, which is a program-level convention to settle once for every story rather than in one.
- source_spec: `_bmad-output/implementation-artifacts/1-19-thread-confinement.md`
  summary: The monitor rule of story 1.19 runs over the harness's test classpath only; `codex` (E2) and `overlay` (E5) depend on the game and need the same rule in their own test trees when they gain classes.
  evidence: `MonitorConfinementTest` imports `org.shatterfish` from the harness test classpath, which cannot see the modules built on the harness; both are package-info-only today.
