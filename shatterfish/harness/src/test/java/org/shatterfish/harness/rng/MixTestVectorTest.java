package org.shatterfish.harness.rng;

import com.watabou.utils.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mix, against the vector published on the methodology page, and against what a wait actually
 * draws.
 *
 * <p>The vector is the point of the function. A skeptic who does not trust this code can compute
 * `splitmix64_finalize(salt + k * 0x9E3779B97F4A7C15)` in any language and check these numbers, and
 * from there check that a Run drew what it says it drew. If the values here ever change, the
 * published page changes in the same commit or the promise is broken.
 */
class MixTestVectorTest {

    /**
     * The published vector: salt, wait index, and the seed the mix gives. Computed by this
     * implementation and checked against an independent one written from ADR-0007's text (the
     * SplitMix64 finalizer applied to salt + k·γ), which is the only reason to trust it.
     */
    private static final long[][] VECTOR = {
            {0x0L, 0L, 0x0L},
            {0x0L, 1L, 0xE220A8397B1DCDAFL},
            {0x1L, 0L, 0x5692161D100B05E5L},
            {0x0123456789ABCDEFL, 0L, 0xB2C058E4EBB5112CL},
            {0x0123456789ABCDEFL, 1L, 0x157A3807A48FAA9DL},
            {-1L, -1L, 0xDE0A564CBCD060C4L},
    };

    @Test
    @DisplayName("the mix is the published vector, bit for bit")
    void the_published_vector() {
        List<String> wrong = new ArrayList<>();
        for (long[] row : VECTOR) {
            long got = Mix.mix(row[0], row[1]);
            if (got != row[2]) {
                wrong.add(String.format("mix(%#x, %d) is %#x and the page says %#x",
                        row[0], row[1], got, row[2]));
            }
        }
        assertEquals(List.of(), wrong, "the mix and the published vector disagree. One of them is"
                + " wrong and the page is what a skeptic reads, so decide which before changing either:\n  "
                + String.join("\n  ", wrong));
    }

    @Test
    @DisplayName("the mix is the finalizer of ADR-0007, computed a second way")
    void the_definition_holds() {
        // The same definition, written out again from the ADR's own words rather than called, so
        // that a change to Mix has to be a change to this too — and so that the vector above is
        // checked against something other than the code it came from.
        for (long salt : new long[]{0L, 1L, -1L, 0x0123456789ABCDEFL, Long.MIN_VALUE}) {
            for (long k : new long[]{0L, 1L, 2L, 1000L, Long.MAX_VALUE}) {
                long z = salt + k * 0x9E3779B97F4A7C15L;
                z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
                z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
                z = z ^ (z >>> 31);
                assertEquals(z, Mix.mix(salt, k), "salt " + salt + ", wait " + k);
            }
        }
    }

    @Test
    @DisplayName("the shifts are unsigned, which is the difference between this function and another")
    void the_shifts_are_unsigned() {
        // A negative intermediate is what tells >>> from >>, and it is the likeliest way for this
        // to be written wrong, so one case is held explicitly.
        assertNotEquals(0L, Mix.mix(Long.MIN_VALUE, 1L));
        assertEquals(Mix.mix(-1L, 3L), Mix.mix(-1L, 3L), "and it is a function, not a generator");
    }

    @Test
    @DisplayName("nearby salts give unrelated seeds, which is what the finalizer is for")
    void nearby_salts_are_not_nearby_seeds() {
        long first = Mix.mix(1000L, 0L);
        long second = Mix.mix(1001L, 0L);
        assertTrue(Long.bitCount(first ^ second) > 16,
                "two salts one apart differ in " + Long.bitCount(first ^ second) + " bits");
        assertTrue(Long.bitCount(Mix.mix(0L, 5L) ^ Mix.mix(0L, 6L)) > 16,
                "and so do two waits one apart");
    }

    @Test
    @DisplayName("the numbers drawn at a wait are the numbers the mix says")
    void the_stream_at_a_wait_is_the_mix() {
        // This is the check that stands in for ADR-0007's stack-depth assert: the deque is private
        // upstream, so instead of asking how deep the stack is, ask what comes off the top. A
        // generator the game pushed and did not pop would answer differently.
        long salt = 0xDEADBEEFL;
        RngControl rng = new RngControl(salt);
        try {
            List<List<Integer>> perWait = new ArrayList<>();
            for (long k = 0; k < 8; k++) {
                rng.reseed(k);
                List<Integer> drawn = new ArrayList<>();
                for (int draw = 0; draw < 16; draw++) {
                    drawn.add(Random.Int(1_000_000));
                }
                // The expectation is computed from the mix and the salt, not from the control's own
                // idea of its seed: asking the thing under test what it meant to do lets a control
                // that seeds every wait the same way agree with itself, which is how the mutation
                // that does exactly that survived this test's first draft.
                assertEquals(expected(Mix.mix(salt, k)), drawn, "the draws at wait " + k);
                perWait.add(drawn);
            }
            assertEquals(perWait.size(), perWait.stream().distinct().count(),
                    "and no two waits draw the same numbers, which is what the index is for");
        } finally {
            rng.release();
        }
    }

    @Test
    @DisplayName("a Run's generator is taken off the stack when the Run lets it go")
    void the_stack_is_left_as_it_was() {
        // Nothing can read the stack's depth, so this reads its behaviour: after release the draws
        // are the base generator's again, which is to say they are not this Run's.
        RngControl rng = new RngControl(7L);
        rng.reseed(0);
        List<Integer> ours = new ArrayList<>();
        for (int draw = 0; draw < 8; draw++) {
            ours.add(Random.Int(1_000_000));
        }
        rng.release();

        rng.reseed(0);
        List<Integer> again = new ArrayList<>();
        for (int draw = 0; draw < 8; draw++) {
            again.add(Random.Int(1_000_000));
        }
        rng.release();
        assertEquals(ours, again, "reseeding the same wait gives the same numbers, whatever was"
                + " underneath, which is what makes release safe to call");

        // And a Run leaves the stack exactly as deep as it found it. Nothing can read the depth, so
        // a generator with a known stream is put underneath and asked afterwards: whatever the Run
        // did in between, the next draws must be that generator's next draws. A Run that pushed a
        // generator a wait and never popped one would answer with its own instead.
        Random.pushGenerator(999L);
        try {
            List<Integer> sentinelFirst = new ArrayList<>();
            for (int draw = 0; draw < 4; draw++) {
                sentinelFirst.add(Random.Int(1_000_000));
            }

            RngControl deeper = new RngControl(11L);
            for (long k = 0; k < 3; k++) {
                deeper.reseed(k);
            }
            for (int draw = 0; draw < 5; draw++) {
                Random.Int(1_000_000);
            }
            deeper.release();

            List<Integer> sentinelNext = new ArrayList<>();
            for (int draw = 0; draw < 4; draw++) {
                sentinelNext.add(Random.Int(1_000_000));
            }
            assertEquals(sentinelContinuation(999L, sentinelFirst.size(), sentinelNext.size()), sentinelNext,
                    "after a Run releases, the generator underneath is the one being drawn from, and"
                            + " it has advanced by exactly the draws made before the Run began");
        } finally {
            Random.popGenerator();
        }
    }

    /** What a generator seeded {@code seed} gives after {@code skip} draws, for the next {@code take}. */
    private static List<Integer> sentinelContinuation(long seed, int skip, int take) {
        Random.pushGenerator(seed);
        try {
            for (int draw = 0; draw < skip; draw++) {
                Random.Int(1_000_000);
            }
            List<Integer> drawn = new ArrayList<>();
            for (int draw = 0; draw < take; draw++) {
                drawn.add(Random.Int(1_000_000));
            }
            return drawn;
        } finally {
            Random.popGenerator();
        }
    }

    /** What a generator seeded with {@code seed} gives, through the game's own push. */
    private static List<Integer> expected(long seed) {
        Random.pushGenerator(seed);
        try {
            List<Integer> drawn = new ArrayList<>();
            for (int draw = 0; draw < 16; draw++) {
                drawn.add(Random.Int(1_000_000));
            }
            return drawn;
        } finally {
            Random.popGenerator();
        }
    }
}
