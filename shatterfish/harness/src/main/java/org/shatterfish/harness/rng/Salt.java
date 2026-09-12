package org.shatterfish.harness.rng;

import java.security.SecureRandom;

/**
 * Where a Run's salt comes from: drawn by whoever runs the Run, from a secret of that invocation.
 *
 * <p>This exists because the alternative is worse in a way that is easy to miss. A Run needs a salt
 * to be reproducible, and the obvious place to get one is a default — zero, say — so that a caller
 * who does not care need not think about it. ADR-0007 rejected that in advance and named the attack
 * (`docs/adr/0007-rng-seeding-strategy.md:55-62`): the mix is published, so a salt anyone can
 * predict lets a Brain's author compute the game's coming draws as pure data, with no game code and
 * no Observation — whether the next attack hits, what the next chest holds, where the next floor
 * puts its stairs. A Brain that knows the roll table is not playing the game the rest of us are.
 *
 * <p>So there is no default anywhere, and a Run that wants one asks here. The salt is written down
 * with the Run — that is what makes a Replay possible — and shown to nothing that plays.
 */
public final class Salt {

    private static final SecureRandom SECRET = new SecureRandom();

    private Salt() {
    }

    /**
     * A salt for one Run, drawn now. The caller is the runner and owes the Run one thing in return:
     * record it, or the Run cannot be replayed and its numbers cannot be checked.
     */
    public static long draw() {
        return SECRET.nextLong();
    }
}
