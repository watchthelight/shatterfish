package org.shatterfish.harness.practice;

import java.util.Locale;

/**
 * The bosses a practice Run can start in front of, and the floor each one lives on
 * ({@code core/.../Dungeon.java:297-355} picks the level class by depth; {@code :441-443} names the
 * boss floors). The names are the ones a Run's log states in its header, in lower case.
 */
public enum PracticeBoss {

    /** The sewers' boss, on the floor {@code SewerBossLevel} builds. */
    GOO(5),

    /** The prison's boss, on the floor {@code PrisonBossLevel} builds. */
    TENGU(10);

    private final int depth;

    PracticeBoss(int depth) {
        this.depth = depth;
    }

    /** The depth the Run starts on. */
    public int depth() {
        return depth;
    }

    /** The name a header and a command line use. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The boss named {@code id}, in any case; anything else is a refusal that lists the ones there are. */
    public static PracticeBoss of(String id) {
        for (PracticeBoss boss : values()) {
            if (boss.id().equalsIgnoreCase(id)) {
                return boss;
            }
        }
        throw new IllegalArgumentException("practice starts in front of goo or tengu, not " + id);
    }
}
