package org.shatterfish.harness.scene;

import com.watabou.utils.Random;

import java.lang.reflect.Field;
import java.util.ArrayDeque;

/**
 * A generator on top of the game's stack that counts how many times it is asked for bits.
 *
 * <p>{@code com.watabou.utils.Random} keeps a stack of {@code java.util.Random} generators and
 * draws from the top ({@code SPD-classes/.../utils/Random.java:37-45}). Pushing one of these
 * after {@code Dungeon.init()}, which resets the stack, puts it where the harness will later put
 * its own seeded generator, so everything the game draws on the actor thread and on the driver
 * thread from then on passes through {@link #next(int)}: combat, loot, sprite linking, emote
 * icons, music choice. Generators the game pushes for its own scoped work, level generation for
 * one, sit above it while they last and are not counted; those are seeded from the level seed and
 * cannot differ between two scenes.
 *
 * <p>Every public method of {@code java.util.Random} funnels through {@code next(int)}, so the
 * count is exact and independent of which overload the game called. The stack is private, so this
 * reaches it by reflection; it is test code in {@code harness}, where that is allowed.
 */
final class DrawCounter extends java.util.Random {

    private static final long serialVersionUID = 1L;
    private static final Field GENERATORS = generatorsField();

    private long draws;

    private DrawCounter(long seed) {
        super(seed);
    }

    /** Pushes a counter seeded with {@code seed} on top of the game's stack. */
    static DrawCounter install(long seed) {
        DrawCounter counter = new DrawCounter(seed);
        stack().push(counter);
        return counter;
    }

    @Override
    protected int next(int bits) {
        draws++;
        return super.next(bits);
    }

    /** Times the game asked this generator for a value since it was installed. */
    long draws() {
        return draws;
    }

    /**
     * Pops this counter, which must be the top of the stack: anything else means the game pushed
     * a generator during the run and never popped it, which is a bug worth failing on.
     */
    void remove() {
        if (stack().peekFirst() != this) {
            throw new IllegalStateException("the game's generator stack does not have the counter on top;"
                    + " a pushGenerator was not matched by a popGenerator");
        }
        Random.popGenerator();
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<java.util.Random> stack() {
        try {
            return (ArrayDeque<java.util.Random>) GENERATORS.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field generatorsField() {
        try {
            Field field = Random.class.getDeclaredField("generators");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("com.watabou.utils.Random.generators is not where the pinned"
                    + " upstream had it", e);
        }
    }
}
