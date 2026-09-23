package org.shatterfish.rig;

import org.shatterfish.api.Decider;
import org.shatterfish.harness.agent.RandomAgent;

import java.util.List;

/**
 * The Brains the Rig can be told to run (story 3.3), by the names it answers to on the command
 * line.
 *
 * <p>There is one so far. {@code random} is the harness's own random agent, which is not a Brain at
 * all — it is the Baseline every later Brain is measured against (story 3.9), and having it here
 * now is what lets the runner be built and measured before a Brain exists. A real Brain joins this
 * list in E4 by being named here; nothing else about the runner changes.
 *
 * <p>A Brain is a {@link Decider}: an Observation in, an Action out, and no second argument to ask
 * for anything else. The seed a Decider draws from is the Run's own salt, so two Runs of one tuple
 * hand the same Decider the same stream — a Brain that made its own randomness from the clock would
 * not be reproducible, and the rule is enforced by giving it the salt rather than by asking.
 */
public final class Brains {

    /** The Baseline: a uniform choice from the valid set, which is what "no Brain" measures as. */
    public static final String RANDOM = "random";

    private Brains() {
    }

    /** Every name the Rig answers to, in the order it lists them. */
    public static List<String> names() {
        return List.of(RANDOM);
    }

    /**
     * The Decider named {@code name}, drawing from {@code salt}, refusing a name the Rig does not
     * have and saying which it does.
     */
    public static Decider of(String name, long salt) {
        if (RANDOM.equals(name)) {
            return new RandomAgent(salt);
        }
        throw new IllegalArgumentException("there is no Brain named " + name + "; the Rig knows " + names());
    }

    /**
     * What identifies the build of a Brain, for the Run log's header. The Baseline has no source of
     * its own beyond the harness, so its commit is the Shatterfish commit the invocation states and
     * its configuration is the empty one.
     */
    public static String configHash(String name) {
        of(name, 0L);
        return "0".repeat(64);
    }
}
