
## Amendment: story 1.15 (2026-09-12)

**What the generator stack turned out to allow.** `Random.resetGenerators()`, `pushGenerator(long)`
and `popGenerator()` are public and static, and the deque behind them is private with no accessor
(`SPD-classes/.../utils/Random.java:37-73`). So the harness can drive the stack and cannot read its
depth, and this ADR's pre-mortem — "the Harness only swaps its own generator when the stack depth
equals what it pushed, and asserts otherwise" — cannot be written as an assert without a hook row.
The row is not this story's to spend: this ADR reserves the next one for identity order, which is
story 1.16's.

What replaces it is stronger and needs no row. At wait `k` the numbers drawn must be exactly those
of a generator seeded `mix(salt, k)`, and a generator the game pushed and did not pop would change
them. `MixTestVectorTest` computes the expected draws itself and compares, at eight consecutive
waits. If the game ever does leave a generator across a wait, that test says so, and the row can be
spent then with evidence rather than in anticipation.

**Where the reseed happens.** At the wait itself, inside `HeadlessDriver.stepToInputWait`, as it
confirms one and before it returns. This ADR and ADR-0013 put the reseed at the head of the wait,
before the Observation is read; doing it in the driver's own `run` loop would have left every other
caller — the agent's loop, a test, a Replay — drawing from whatever the last Run left behind. A Run
is a function of its tuple however it is driven, or it is not one.

**The published vector.** `docs/methodology.md` carries the mix, its definition in words, a six-row
test vector and the same function in Python, so a skeptic can check the numbers without running
anything of ours. The vector is held from two directions: the implementation is checked against the
table, and the table against a second implementation written from this ADR's text.

**The Profile is version 1.** English strings, the intro off, the support prompt answered, the
compact interface, and an empty history. A directory carrying another version's stamp is refused
before the Run starts rather than compared after it. Two of those choices have reasons worth
repeating: the compact interface is the one whose item selector is a window an Action can answer
(story 1.14), and the empty history matters because the game reads it — a snake stops dodging after
four misses only once the first boss has been slain (`…/actors/mobs/Snake.java:66`).

**What this story did not close.** Two Runs of one tuple now draw the same numbers, which is what
`ProfileTest.one_tuple_one_run` holds. That is the randomness half of issue #70. The other half is
iteration order over identity hashes, which story 1.16 owns, and until it lands a Run can still
diverge for reasons that have nothing to do with what it drew.
