package org.shatterfish.harness.isolate;

import org.shatterfish.api.Action;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.harness.driver.ScrubbedStart;

import java.util.List;

/**
 * One isolated copy of the game, as the live Run's class loader sees it (E10 spike, ADR-0025): the interface is the whole
 * surface between the two loaders, and every type in its signatures is either the JDK's, {@code api}'s, or
 * {@link ScrubbedStart}, which is delegated by name and can be made only from a complete scrubbed copy. A caller can hand
 * it nothing from the live game and gets back Observations already made.
 *
 * <p>This interface and {@link WorldFactory} are among the few classes {@link IsolationLoader} delegates to the loader
 * that defined them, so the live side and the isolated side name one type.
 *
 * <p>A world belongs to the thread that opened it (the UI-role rule of ADR-0013, held by the isolated copy of the
 * harness) and to its own copy of the game's statics.
 */
public interface RolloutWorld extends AutoCloseable {

    /**
     * Plays {@code actions} from the scrubbed copy and says where the line ended. The world is put back to its baseline
     * (the game's statics and the profile's files, as the swap host puts the live Run's back) before this returns.
     */
    RolloutResult rollout(ScrubbedStart start, List<Action> actions);

    /**
     * The nanoseconds the last {@link #rollout} spent, in this order: holding the profile, loading the copy and stepping
     * it to its wait, playing the Actions, and putting the baseline back; then the four parts of the load (ending the old
     * scene, writing the files, loading the game and floor, building the new scene) and the stepping of the new scene to
     * its first wait. Durations only, for measurement.
     */
    long[] lastCostsNanos();

    /** Ends the world's Run. The class loader goes with the last reference to it. */
    @Override
    void close();
}
