# Upstream statics that outlive a Run

Settled by issue #167: `RunStatics` puts back, at every Run start, the eleven statics a Run can leave
for the next, and `docs/architecture.md` ("Statics that outlive a Run") has the sweep of all 358 and
how each is classified. The history, kept for the reasoning: the upgrade to `v4.0.0` first showed it,
two Runs of one tuple in one process logging different numbers of guidebook lines because
`Snake.dodges` is a private counter nothing resets (`core/.../actors/mobs/Snake.java:58-70`); the test
fixtures cleared it by hand, and story 5.1 found `Bones` the same way. The choice this section once
posed, a reset list the harness applies or a rule that a process hosts one Run, went to the reset
list; its private fields are reached by reflection named in `docs/UPSTREAM.md`, as the stepper's are.

What is still open here: a Run restored from a snapshot in the same process starts from the statics
as the process left them, not as the snapshot had them, since only the saved game is restored; and
the statics that read the wall clock (`Holiday.cached`, `DimensionalSundial`'s night check) are issue
#139's.
