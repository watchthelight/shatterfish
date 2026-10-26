package org.shatterfish.harness.redetermine;

import org.shatterfish.api.BeliefSample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * What the scrubber reads of a {@link BeliefSample}, version 1: the dungeon seed the scrubbed copy generates its
 * unvisited floors from, the salt the rollout draws its waits from, the seed of the stream every other draw comes
 * from (see {@link Streams}), and the mobs the sample believes stand on cells the player has visited and left. The
 * belief sampler that writes these from the Codex priors is a later story's (E6.3); the tests write them by hand,
 * and the scrubber trusts nothing in them beyond their form: a remembered mob on a cell that is not visited and free
 * is dropped, not placed.
 */
public record ScrubSample(long dungeonSeed, long rolloutSalt, long draws, List<Remembered> remembered) {

    public static final int VERSION = 1;

    /** A mob the sample puts on a visited cell: the game's class name and the cell. */
    public record Remembered(String mobClass, int cell) {
    }

    public ScrubSample {
        remembered = List.copyOf(remembered);
    }

    public static ScrubSample of(long dungeonSeed, long rolloutSalt, long draws) {
        return new ScrubSample(dungeonSeed, rolloutSalt, draws, List.of());
    }

    public ScrubSample withRemembered(List<Remembered> mobs) {
        return new ScrubSample(dungeonSeed, rolloutSalt, draws, mobs);
    }

    public BeliefSample toBelief() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeLong(dungeonSeed);
            out.writeLong(rolloutSalt);
            out.writeLong(draws);
            out.writeInt(remembered.size());
            for (Remembered mob : remembered) {
                out.writeUTF(mob.mobClass());
                out.writeInt(mob.cell());
            }
            return new BeliefSample(VERSION, bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ScrubSample parse(BeliefSample sample) {
        if (sample.version() != VERSION) {
            throw new IllegalArgumentException("the scrubber reads sample version " + VERSION + ", and this is " + sample.version());
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(sample.bytes()))) {
            long seed = in.readLong();
            long salt = in.readLong();
            long draws = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > 4096) {
                throw new IllegalArgumentException("a sample of " + count + " remembered mobs");
            }
            List<Remembered> mobs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                mobs.add(new Remembered(in.readUTF(), in.readInt()));
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException("a sample with " + in.available() + " bytes left over");
            }
            return new ScrubSample(seed, salt, draws, mobs);
        } catch (IOException e) {
            throw new IllegalArgumentException("a sample that does not parse as version " + VERSION, e);
        }
    }
}
