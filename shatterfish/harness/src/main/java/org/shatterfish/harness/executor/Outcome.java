package org.shatterfish.harness.executor;

import org.shatterfish.api.Action;

/**
 * What became of an Action handed to the executor: it was applied, or it was rejected with a
 * reason (ADR-0014). Two cases and no third, so a caller's switch is exhaustive and a Run log
 * records one of them per Input wait.
 */
public sealed interface Outcome {

    /** Whether the game was asked to do the thing. */
    boolean applied();

    /** The Action reached the game through the call a person's click, key or button makes. */
    record Applied(Action action) implements Outcome {

        @Override
        public boolean applied() {
            return true;
        }
    }

    /**
     * The Action was refused. Unless the reason is {@link Reason#NO_SELECTOR}, which can only
     * happen after an item was executed, nothing was called and the game is as it was.
     *
     * @param action the Action as it was handed over
     * @param reason why it was refused
     * @param detail what a person would need to know to fix it, in words
     */
    record Rejected(Action action, Reason reason, String detail) implements Outcome {

        @Override
        public boolean applied() {
            return false;
        }
    }
}
