package org.shatterfish.harness.driver;

/**
 * What happens at every Input wait, in order: ADR-0013's per-wait sequence with each part owned by
 * the story that builds it. The driver increments the wait index {@code k} when it confirms the
 * wait, then calls these five in this order, once each, with the same {@code k}
 * ({@link HeadlessDriver#run}).
 *
 * <p>Every method has a default that does nothing, so a caller fills in what exists: story 1.15
 * reseeds the generator from the salt and {@code k}; stories 1.8 to 1.11 observe; the brain, or an
 * agent, decides; story 1.13 executes; E3 records. Until then a test can supply all five and see
 * the order. {@code O} is what {@code observe} produces and {@code D} what {@code decide} produces;
 * they become the Observation and the Decision of {@code api} when those exist.
 *
 * @param <O> what an observation is
 * @param <D> what a decision is
 */
public interface WaitSequence<O, D> {

    /** Reseeds the game's generator for wait {@code k}; story 1.15. */
    default void reseed(long k) {
    }

    /** Reads what the player could see at wait {@code k}; the Observer stories. */
    default O observe(long k) {
        return null;
    }

    /** Decides what to do at wait {@code k} from what was observed; the brain, or an agent. */
    default D decide(long k, O observation) {
        return null;
    }

    /** Does what was decided at wait {@code k}, the way the player would; story 1.13. */
    default void execute(long k, D decision) {
    }

    /** Writes the record of wait {@code k}; E3. */
    default void record(long k, O observation, D decision) {
    }
}
