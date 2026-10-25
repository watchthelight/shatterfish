# From story 5.1 (the launcher and the embedded driver)

- **The Overlay's Brain knows no Codex.** `--agent brain` attaches `shatterfish` with the committed
  weights but an empty Codex: the Rig's Brain is built on the Codex, which only the rig reads
  (`rig/CodexKnowledge`), and the Overlay may not depend on the rig. Moving the reader to a module
  both can reach (the harness, or a small `codex-read` module on `api`) gives the Overlay the Rig's
  Brain; at the latest story 5.16, whose sewers Run needs it, has to do it.
  **Done** with the bestiary's lever 0 (branch `brain/bestiary-speed`), which made the empty Codex a
  regression (the statues became enemies): `CodexKnowledge` and `CodexManifest` moved to the harness
  (`org.shatterfish.harness.knowledge`), and the Overlay's Brain reads `codex/<tag>/` and
  `tactics/bestiary.json` from the repository root (`--codex` to point elsewhere), refusing to launch
  without them.
- **The descend and explore Policies can hold the hero between two cells.** In the desktop launch of
  story 5.1 (seed 12345, the Warrior, the Overlay's Brain), from wait 501 the hero stepped between
  two cells for about 1,100 waits until it starved: the descend Policy chose the exit (`overstayed`),
  and on the next wait the explore Policy's `away` step (a region the fight Policy retreated from
  covers the path) took it back. A test that plays the two against one fled region would show it;
  the fix belongs to the Brain, not the Overlay.
  **Done** for the region case by issue #174 (a Step into an avoided region lifts it; `DescendFledTest` plays
  the descend and explore Policies against a region across the only way to the exit and shows they
  no longer undo each other), and for the stairs retreat of issue #165's second report by the
  linger (`Explore.LINGER`).
- **Bones is one more upstream static that outlives a Run** (see "Upstream statics that outlive a
  Run" above). The determinism test of story 5.1 found it: a hero who dies in one Run leaves remains
  on the next Run's floor in the same process (`core/.../Bones.java:50-54`, `:154-160`). The Profile
  now clears it through `Bones.leave()`'s daily branch, a public door. The sweep was done under issue
  #167 (`docs/architecture.md`, "Statics that outlive a Run").
- **The frames the desktop adds.** An Overlay Run equals the Rig's Run of its tuple only where the
  frames between two waits are the same; the render thread's draws in the frames the desktop adds
  come from the Run's generator. Story 5.13 routes them away. Until then an Overlay Run is not
  reproducible from its tuple or its Action list, its log says `driver: embedded`, and the Rig refuses
  it; a replay of an Overlay log would part from it at the first moved roll.
- **A gamepad reaches the game while a Run plays.** The Overlay closes the game's input multiplexer
  (`InputLock`), but the controller handler writes the key queue directly
  (`SPD-classes/…/input/ControllerHandler.java:122-134`). Story 5.5's input-gate hook closes it.
- **The region intro.** The Overlay does not click through the story page the loading scene shows on a
  first descent to depths 6, 11, 16 and 21, so a Run reaching depth 6 ends as an unknown window after
  the budget. Clicking it reads a journal page the headless Run does not; if the Overlay should
  play past the sewers, clicking it and reading that page in both drivers is the choice to make.
- **Reading subprocess output.** `Brains`, `Registrations`, `Results` (rig) and `DocsCitations` (codex)
  read a subprocess's output to its end before its errors, which deadlocks once the errors fill a pipe;
  the hook-ledger tests' `Ledger.git` did (story 5.1). Their outputs are small today.
