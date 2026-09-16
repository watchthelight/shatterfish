# Deferred work

Findings surfaced by a story's review that are not that story's problem, one entry each.

- source_spec: `_bmad-output/implementation-artifacts/1-17-the-differential-and-toggle-tests.md`
  summary: The mutation batteries every story runs live in the session scratchpad and are not committed, so a story's "verified once" evidence is prose a reviewer cannot rerun.
  evidence: Stories 1.13 to 1.17 each record a battery's output in the story file and keep the script (`mutations<story>.py`) outside the repository; the blind review of 1.17 asked for it to be committed under a tools directory, which is a program-level convention to settle once for every story rather than in one.
