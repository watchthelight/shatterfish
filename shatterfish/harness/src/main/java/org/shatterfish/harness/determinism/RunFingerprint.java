package org.shatterfish.harness.determinism;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;

/**
 * One hash per Input wait of a Run played by a fixed policy: the question a second process can be
 * asked so that its answer can be compared with this one's.
 *
 * <p>This is the shape the determinism test needs and nothing more. The tuple is the seed, the class
 * and the salt; the policy is the random agent on a seed of its own, which is a fixed function of
 * the Observations it is shown, so two processes that see the same screens make the same choices
 * and two that do not diverge at the first screen that differs — which is what the test wants to
 * see. The output is plain text, one line a wait, because the comparison is made by another JVM and
 * a person reading a failure should be able to see the wait at which the two disagreed.
 *
 * <p>A Run that ends before the requested number of waits — the hero dies, or the game asks for
 * another scene — ends the fingerprint with the reason, which both processes must also agree on.
 */
public final class RunFingerprint {

    private RunFingerprint() {
    }

    /**
     * The fingerprint of a Run: {@code "<k> <hash>"} for each wait served, then {@code "end <why>"}
     * if the Run ended before {@code waits} of them.
     */
    public static List<String> of(long seed, HeroClass heroClass, long salt, long agentSeed, int waits) {
        List<String> lines = new ArrayList<>();
        RandomAgent agent = new RandomAgent(agentSeed);
        ActionExecutor executor = new ActionExecutor();
        HeadlessDriver driver = HeadlessDriver.start(seed, heroClass, salt);
        try {
            for (int served = 0; served < waits; served++) {
                HeadlessDriver.Halt halt = driver.stepToInputWait();
                if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                    lines.add("end " + halt.reason());
                    return lines;
                }
                Observation observation = new Observer().observe();
                lines.add(halt.waitIndex() + " " + observation.hash());
                Action chosen = agent.decide(observation);
                if (chosen == null) {
                    lines.add("end NOTHING_OFFERED");
                    return lines;
                }
                // A refusal is part of the fingerprint too: the wait is served again and the next
                // Observation is the same, which both processes will also agree on.
                executor.execute(observation, chosen);
            }
            return lines;
        } finally {
            driver.close();
        }
    }

    /**
     * Prints the fingerprint for {@code seed salt agentSeed waits}, one line each, after a header a
     * person can read. The class is the Warrior, which is the class every E1 number is about.
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("usage: RunFingerprint <seed> <salt> <agentSeed> <waits>");
            System.exit(2);
        }
        long seed = Long.parseLong(args[0]);
        long salt = Long.parseLong(args[1]);
        long agentSeed = Long.parseLong(args[2]);
        int waits = Integer.parseInt(args[3]);
        System.out.println("RunFingerprint: seed " + seed + ", Warrior, salt " + Long.toHexString(salt)
                + ", agent " + agentSeed + ", up to " + waits + " waits");
        for (String line : of(seed, HeroClass.WARRIOR, salt, agentSeed, waits)) {
            System.out.println(line);
        }
        System.out.flush();
        // The game leaves threads behind that are not daemons; a main is allowed to end the process.
        System.exit(0);
    }
}
