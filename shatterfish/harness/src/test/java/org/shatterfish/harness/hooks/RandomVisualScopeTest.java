package org.shatterfish.harness.hooks;

import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hook row 8 (story 5.13): draws made inside a visual scope come from the base generator and leave the
 * stack the Run owns where it was; the scope belongs to the thread that opened it.
 */
class RandomVisualScopeTest {

    private static final long SEED = 0x5EEDL;

    @BeforeEach
    void ownAStack() {
        Random.pushGenerator(SEED);
    }

    @AfterEach
    void releaseIt() {
        Random.popGenerator();
        // The floor is zero, so a scope a failed test left open on this thread is closed by enough ends.
        for (int i = 0; i < 8; i++) {
            Random.visualEnd();
        }
    }

    /** What a stack seeded as the test's is going to give, n draws deep, read from a twin. */
    private static float[] stackDraws(int n) {
        Random.pushGenerator(SEED);
        try {
            float[] out = new float[n];
            for (int i = 0; i < n; i++) {
                out[i] = Random.Float();
            }
            return out;
        } finally {
            Random.popGenerator();
        }
    }

    @Test
    @DisplayName("inside a scope no draw advances the Run's stack, in any of the forms")
    void the_stack_is_left_alone() {
        float[] expected = stackDraws(3);
        Random.visualBegin();
        try {
            Random.Float();
            Random.Int();
            Random.Int(10);
            Random.Long();
            Random.Float(5f);
            Random.Int(3, 9);
        } finally {
            Random.visualEnd();
        }
        for (float e : expected) {
            assertEquals(e, Random.Float(), "the stack gave its own sequence, as if the scope had not been");
        }
    }

    @Test
    @DisplayName("outside a scope a draw answers from the stack, as vanilla")
    void vanilla_outside_a_scope() {
        float[] expected = stackDraws(2);
        assertEquals(expected[0], Random.Float());
        assertEquals(expected[1], Random.Float());
    }

    @Test
    @DisplayName("scopes nest, and a stray end cannot push the depth below zero")
    void nesting_and_the_floor() {
        float[] expected = stackDraws(2);
        Random.visualBegin();
        Random.visualBegin();
        Random.visualEnd();
        Random.Float(); // still inside the outer scope
        Random.visualEnd();
        Random.visualEnd(); // strays: the floor is zero
        Random.visualEnd();
        assertEquals(expected[0], Random.Float(), "the scope held until its own end, and the strays opened nothing");
        Random.visualBegin(); // and a scope opened after the strays still routes
        Random.Float();
        Random.visualEnd();
        assertEquals(expected[1], Random.Float());
    }

    @Test
    @DisplayName("the scope is the calling thread's: another thread's draws still come from the stack")
    void the_scope_is_per_thread() throws InterruptedException {
        float[] expected = stackDraws(1);
        Random.visualBegin();
        try {
            AtomicReference<Float> other = new AtomicReference<>();
            Thread actor = new Thread(() -> other.set(Random.Float()));
            actor.start();
            actor.join();
            assertEquals(expected[0], other.get(), "the actor thread's roll is the Run's");
        } finally {
            Random.visualEnd();
        }
    }
}
