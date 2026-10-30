package org.shatterfish.api;

/**
 * The look-ahead a search Brain is lent at one Input wait (story 10.24, ADR-0025): "if the hidden state were this, and I
 * did that, what would I see?". The harness implements it over a scrubbed copy of the Run and an isolated copy of the
 * game; the Brain sees Observations come back and nothing else, and the live Run is neither read nor touched.
 *
 * <p>The contract is in the shape. The only things that go in are a {@link Hypothesis} (draws and claims) and a
 * {@link Playout} (a function of Observations); the only thing that comes out is a {@link RolloutResult}. There is no
 * handle, no snapshot and no way to name the live Run's own state, so a search cannot be handed more than the
 * scrubber's copy by asking.
 */
public interface Lookahead {

    /**
     * Plays the hypothesis forward from the wait this look-ahead was lent at, taking Actions from {@code playout}, for at
     * most {@code horizon} Actions.
     *
     * @return the Observations the rollout reached, or null when the world cannot be sampled at this wait (a scrub
     *         refused it); a refusal is no evidence about the hidden state and a search treats it as no sample
     */
    RolloutResult roll(Hypothesis hypothesis, Playout playout, int horizon);
}
