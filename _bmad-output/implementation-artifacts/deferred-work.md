# Deferred work

Findings surfaced by a story's review that are not that story's problem, one entry each.

- source_spec: `_bmad-output/implementation-artifacts/1-17-the-differential-and-toggle-tests.md`
  summary: The mutation batteries every story runs live in the session scratchpad and are not committed, so a story's "verified once" evidence is prose a reviewer cannot rerun.
  evidence: Stories 1.13 to 1.17 each record a battery's output in the story file and keep the script (`mutations<story>.py`) outside the repository; the blind review of 1.17 asked for it to be committed under a tools directory, which is a program-level convention to settle once for every story rather than in one.
- source_spec: `_bmad-output/implementation-artifacts/1-19-thread-confinement.md`
  summary: The monitor rule of story 1.19 runs over the harness's test classpath only; `codex` (E2) and `overlay` (E5) depend on the game and need the same rule in their own test trees when they gain classes.
  evidence: `MonitorConfinementTest` imports `org.shatterfish` from the harness test classpath, which cannot see the modules built on the harness; both are package-info-only today.
- source_spec: `_bmad-output/implementation-artifacts/3-7-calibrate-the-bounds.md`
  summary: A comparison Registration is not held to calibrated bounds; nothing refuses one with n0 = 10, which the calibration shows overshoots its false-accept rate.
  evidence: Gsprt.of(Registration) builds the test from any Registration's p1, burn-in and missing cap; story 3.9 writes the first comparison Registration and is where Calibration.CHOSEN should be required or cited.
- source_spec: `_bmad-output/implementation-artifacts/3-8-the-e-process-alternative.md`
  summary: A Registration does not fix which sequential test it was registered under, so flipping SequentialTest.GATE would run earlier Registrations under the other design.
  evidence: SequentialTest.of(registration) reads a compile-time constant; the Registration's canonical text carries p0, p1, alpha, beta and n0 but no statistic. Story 3.9 writes the first comparison Registration and is where a statistic field, or a refusal on mismatch, belongs.
