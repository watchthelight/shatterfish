---
name: rig
description: Run the Shatterfish rig on a seed set for one brain or an A/B pair, summarize depth, turns, cause of death and the SPRT verdict, and optionally publish docs/results/<date>-<sha>.md. Use when the user says "run the rig", "rig N seeds", "compare brains", "publish rig numbers", or invokes /rig.
---

# rig

Nothing about the brain is believed until the rig says so. This skill runs it and writes the
numbers where the project expects them.

## Contract with the `rig` module (E3 implements this; this skill is the spec)

```sh
./gradlew :rig:run --args="--brain <name> [--baseline <name>] --seeds <set-name|N> [--seed-start K] [--parallel P] --out <dir>"
```

- `--brain` / `--baseline`: registered brain names (`random`, `baseline`, ...). With a baseline,
  the rig runs an SPRT comparison; without one, a single-brain measurement.
- `--seeds`: a named seed set from `rig/seeds/<name>.txt` (committed, reproducible) or an
  integer count drawn from `--seed-start` (default 1).
- `--parallel`: how many single-Run processes run at once (one process per Run; never threads inside one game).
- `--out`: directory receiving `runs.jsonl` (one record per run: seed, brain, depth, turns,
  cause of death, Observation hash chain), `summary.json`, and `sprt.json` when comparing.
- Oracle mode cannot be enabled through this command. If any flag or config would enable it,
  the rig must refuse, and so must this skill.

## Steps

1. **Is the rig built?** If `shatterfish/rig/src/main/java/org/shatterfish/rig/` contains no
   `RigMain.java`, report that the rig is E3 work
   (https://github.com/watchthelight/shatterfish/milestone/4) and stop.
2. **Resolve the request.** Default: `--brain <current brain> --baseline <last published baseline> --seeds smoke`
   for a quick look; `--seeds standard` for numbers that will be published. Ask nothing; state
   the choice.
3. **Run** from a clean, built tree (`./gradlew build` first). Record the Shatterfish commit SHA
   and the upstream tag (`docs/UPSTREAM.md`).
4. **Summarize** in a short table: brains, seed set and size, depth reached (median, p90, max),
   turns, top three causes of death, and for comparisons the SPRT result (accept / reject /
   undecided, LLR, bounds). Include the `--out` path.
5. **Publish** when asked or when the run used a standard seed set: write
   `docs/results/<YYYY-MM-DD>-<short sha>.md` in the format described in
   `docs/results/index.md`, attach the `runs.jsonl` path, and add the file to the current branch
   so it lands in the same PR as the brain change. Never publish an oracle run.

## Rules

- Same (tag, seed, action list) must reproduce the same run; if two runs of one seed differ,
  that is a determinism bug and outranks whatever was being measured. Report it first.
- Do not compare brains built from different upstream tags.
- From E3 onward, no brain change merges without a results file in the PR.
