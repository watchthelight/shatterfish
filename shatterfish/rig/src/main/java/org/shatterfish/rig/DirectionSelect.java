package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.SeedSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which triples a direction check plays, and under which salts.
 *
 * <p>A direction check is a development tool, so it reads a set only through
 * {@link SeedSets#load} (which refuses {@code holdout}) and refuses {@code goo} and {@code tengu}
 * itself: those sets are the E4 and E10 gates' and are played under a Registration, never to see which
 * way a change points.
 * Salts are not drawn: entry {@code i} of the selection plays under {@code saltBase + i}, so the
 * current Brain and a baseline play the same dungeon at the same moments and a rerun of the same
 * flags is the same measurement.
 */
final class DirectionSelect {

    private DirectionSelect() {
    }

    /** One triple and the salt it plays under. */
    record Pick(SeedSet.Entry entry, long salt) {
    }

    /** Refuses a set a direction check may not play, saying why. */
    static void allow(String set) {
        if (SeedSets.TENGU.equals(set)) {
            throw new IllegalArgumentException("the tengu set is the E10 gate's and is played only under a"
                    + " Registration (./gradlew :rig:run --registration ...), never as a direction check or"
                    + " for tuning: a dungeon that has been tuned on is no longer a test of the Tengu fight");
        }
        if (SeedSets.GOO.equals(set)) {
            throw new IllegalArgumentException("the goo set is the E4 gate's and is played only under a"
                    + " Registration (./gradlew :rig:run --registration ...), never as a direction check");
        }
        // An unknown name is refused by name, and the set that is never for development is refused
        // by the one door every development read goes through (SeedSets.load), not by a second copy.
        SeedSets.definition(set);
    }

    /**
     * The triples of {@code set}, filtered to {@code heroClass} when given (null means all), cut to
     * the first {@code first} in file order (a negative number means all), each with the salt
     * {@code saltBase + index} where the index counts the selection, not the file.
     */
    static List<Pick> select(Path root, String set, HeroClass heroClass, int first, long saltBase) {
        allow(set);
        List<SeedSet.Entry> entries = SeedSets.load(root, set).set().entries();
        List<Pick> picks = new ArrayList<>();
        for (SeedSet.Entry entry : entries) {
            if (heroClass != null && entry.heroClass() != heroClass) {
                continue;
            }
            if (first >= 0 && picks.size() >= first) {
                break;
            }
            picks.add(new Pick(entry, saltBase + picks.size()));
        }
        return List.copyOf(picks);
    }

    /** The value of {@code --class} that means every class. */
    static final String ALL = "ALL";

    /**
     * The first {@code first} triples of each of the six classes in turn, in the game's class order,
     * each class's picks exactly what {@link #select} gives for that class alone (so the salt of a
     * class's entry {@code i} is {@code saltBase + i} whatever the other classes are).
     */
    static List<Pick> selectAll(Path root, String set, int first, long saltBase) {
        List<Pick> picks = new ArrayList<>();
        for (HeroClass heroClass : HeroClass.values()) {
            picks.addAll(select(root, set, heroClass, first, saltBase));
        }
        return List.copyOf(picks);
    }

    /** The class named by {@code name}, in any case. */
    static HeroClass heroClass(String name) {
        return SeedSets.heroClass(name.toUpperCase(Locale.ROOT));
    }
}
