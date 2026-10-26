package org.shatterfish.harness.redetermine;

import org.shatterfish.harness.rng.Mix;

import java.util.SplittableRandom;

/**
 * The named random streams a scrub draws from (audit of story 6.2: never draw sequentially from one stream in
 * bundle order). A stream is a function of the sample's draws seed, a role, and a key the player can see: a cell, an
 * inventory slot, a class name. So the numbers an entity gets do not depend on how many other entities the true
 * state held, in what order, or whether the true state held the entity at all; two snapshots whose Observations are
 * equal draw alike, and the true state has no way to reach a draw through the order of its own bundle.
 */
public final class Streams {

    private final long draws;

    public Streams(long draws) {
        this.draws = draws;
    }

    /** The seed of stream {@code role} at {@code key}. */
    public long seed(String role, long key) {
        return Mix.mix(Mix.mix(draws, fnv(role)), key);
    }

    /** The seed of stream {@code role} at the name {@code key}. */
    public long seed(String role, String key) {
        return seed(role, fnv(key));
    }

    public SplittableRandom of(String role, long key) {
        return new SplittableRandom(seed(role, key));
    }

    public SplittableRandom of(String role, String key) {
        return new SplittableRandom(seed(role, key));
    }

    private static long fnv(String text) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }
}
