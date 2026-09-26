package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Rewindable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;

/**
 * Takes a uniformly random Action from the set the screen offers. This is not a Brain and never
 * becomes one: it lives in {@code harness} because it is a tool for exercising the loop, and
 * {@code brain} stays empty until an epic gives it something to hold (epic 1's own rule).
 *
 * <p>It is deliberately without preference. A chooser that liked stepping over resting, or that
 * skipped an Action kind it found awkward, would put its taste into every number measured through
 * it — the depth a random Run reaches is a property of the game, and it stops being one the moment
 * the agent has an opinion. The only thing it reads is {@link Observation#actions()}, which is the
 * same door the Brain will use.
 */
public final class RandomAgent implements Decider, Rewindable {

    private Random choices;

    /** An agent whose stream is this seed's, so a Run of the same tuple makes the same choices. */
    public RandomAgent(long seed) {
        this.choices = new Random(seed);
    }

    /**
     * The Action to take at this wait, or null when the screen offers nothing at all — which is not
     * a case the valid set is supposed to produce, and the caller ends the Run by name rather than
     * inventing an input for it.
     */
    @Override
    public Action decide(Observation observation) {
        List<Action> offered = observation.actions().actions();
        if (offered.isEmpty()) {
            return null;
        }
        return offered.get(choices.nextInt(offered.size()));
    }

    /**
     * The stream's position, for the Overlay's Run to put back when it drops an answer that went stale
     * (story 5.1, {@link Rewindable}). {@code Random} keeps its seed private and is serializable, so
     * the mark is the stream serialized.
     */
    @Override
    public Object mark() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(choices);
        } catch (IOException e) {
            throw new UncheckedIOException("the random agent's stream could not be marked", e);
        }
        return new Mark(bytes.toByteArray());
    }

    @Override
    public void rewind(Object mark) {
        if (!(mark instanceof Mark(byte[] stream))) {
            throw new IllegalArgumentException("not a mark of a random agent: " + mark);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(stream))) {
            choices = (Random) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("the random agent's stream could not be rewound", e);
        }
    }

    private record Mark(byte[] stream) {
    }
}
