package org.shatterfish.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Seed set: the Runs a published number was measured on (FR-20, story 3.1). It is an {@code api}
 * value with a schema version of its own (AD-13), so what a Results page and a Registration name
 * is a type and a version rather than a filename, and the classes and challenge flags a
 * comparison ran under are fixed by that pair.
 *
 * <p>Every component is in the game's own domain, read from the pinned tag and not from memory
 * (non-negotiable 8): a seed is at least 0 and under {@link #TOTAL_SEEDS}
 * ({@code core/…/utils/DungeonSeed.java:31}), a hero class is one of the game's six
 * ({@code core/…/actors/hero/HeroClass.java:87-92}), and challenge flags are 0 through
 * {@link #MAX_CHALLENGE_VALUE} ({@code core/…/Challenges.java:30-40}). A value outside any of
 * them is refused where the value is built, so a set that reached a Run could not hold one.
 *
 * <p>Each entry carries the seed's {@code @@@-@@@-@@@} code beside the number, because the number
 * is what the engine takes and the code is what a person types into the game's own custom-seed
 * window; a stranger checking a published number will do the second. The set does not carry the
 * reason a read of it was allowed: that belongs to the read, not to the file.
 */
public record SeedSet(String name, int version, List<Entry> entries) {

    /**
     * The Seed set schema version, beside the Codex's {@link Codex#VERSION}: 1 is the five derived
     * sets of story 3.1, whose entries fix a hero class per set and no challenge flags. It changes
     * when what a name means changes — a size, a class, a flag, or the derivation — because a
     * Results page cites the name and the version and nothing else.
     */
    public static final int VERSION = 1;

    /**
     * How many seeds the game has: {@code 26^9}, the count
     * {@code DungeonSeed.TOTAL_SEEDS} declares ({@code core/…/utils/DungeonSeed.java:31}). A seed
     * is in {@code [0, TOTAL_SEEDS)}, which is exactly the range the game's own
     * {@code convertToCode} accepts ({@code core/…/utils/DungeonSeed.java:77-80}).
     *
     * <p>{@code randomSeed} ({@code core/…/utils/DungeonSeed.java:41-49}) draws from a smaller
     * set, skipping codes that hold a vowel so that random codes are not words. That is the
     * game's shareability rule for the seeds <em>it</em> invents, not a bound on what a seed may
     * be: the custom-seed window takes any code, so a Seed set is not restricted by it.
     */
    public static final long TOTAL_SEEDS = 5_429_503_678_976L;

    /**
     * The largest value the nine challenge flags can add up to: {@code Challenges.MAX_VALUE}
     * ({@code core/…/Challenges.java:39}), the union of the nine bits at
     * {@code core/…/Challenges.java:30-38}.
     */
    public static final int MAX_CHALLENGE_VALUE = 511;

    /** The shape of a set's name: lower case, so it is the same word in a file name and a citation. */
    public static final String NAME_PATTERN = "[a-z][a-z0-9-]{0,15}";

    /** The shape of a seed code: nine letters in three groups, as the game writes them. */
    public static final String CODE_PATTERN = "[A-Z]{3}-[A-Z]{3}-[A-Z]{3}";

    /** How many letters a seed code holds, and the base each letter is a digit in. */
    private static final int CODE_LETTERS = 9;

    private static final int BASE = 26;

    public SeedSet {
        name = Canon.text(name, "a seed set's name");
        Canon.require(name.matches(NAME_PATTERN),
                "a seed set's name is lower case, " + NAME_PATTERN + ": " + name);
        Canon.require(version >= 1, "a seed set's version is positive: " + version);
        entries = Canon.positional(entries, "a seed set's entries");
        Canon.require(!entries.isEmpty(), "a seed set holds at least one entry");
        Set<Entry> seen = new HashSet<>();
        for (Entry entry : entries) {
            Canon.require(seen.add(entry),
                    "the seed set " + name + " holds the triple " + entry.seed() + " " + entry.heroClass()
                            + " " + entry.challengeFlags() + " twice; a repeated triple is one Run measured"
                            + " as two");
        }
    }

    /** How many triples the set holds. */
    public int size() {
        return entries.size();
    }

    /**
     * One triple: the seed, the hero class and the challenge flags a Run of this set is played
     * with, and the seed's code beside the number.
     */
    public record Entry(long seed, HeroClass heroClass, int challengeFlags, String seedCode) {

        public Entry {
            Canon.require(seed >= 0 && seed < TOTAL_SEEDS,
                    "a seed is at least 0 and under " + TOTAL_SEEDS + " (DungeonSeed.TOTAL_SEEDS): " + seed);
            Canon.require(heroClass != null, "an entry names its hero class");
            Canon.require(challengeFlags >= 0 && challengeFlags <= MAX_CHALLENGE_VALUE,
                    "challenge flags are 0 through " + MAX_CHALLENGE_VALUE + " (Challenges.MAX_VALUE): "
                            + challengeFlags);
            seedCode = Canon.text(seedCode, "an entry's seed code");
            String expected = code(seed);
            Canon.require(seedCode.equals(expected),
                    "the code for seed " + seed + " is " + expected + ", not " + seedCode
                            + "; the code is the seed, written the way the game writes it");
        }
    }

    /**
     * The {@code @@@-@@@-@@@} code for {@code seed}: nine base-26 digits, most significant first,
     * with {@code A} for zero, grouped in threes.
     *
     * <p>This is the inverse of the game's own {@code convertFromCode}
     * ({@code core/…/utils/DungeonSeed.java:52-75}), which says what number a code means:
     * {@code seed = Σ (code[i] - 'A') · 26^(8-i)}. Since every seed is under {@code 26^9} there is
     * exactly one nine-letter code per seed, so the inverse is a function and agrees with the
     * game's own {@code convertToCode} ({@code core/…/utils/DungeonSeed.java:77-105}) everywhere;
     * {@code SeedSetsTest} holds both directions and the two ends of the range. It is written here
     * rather than called from the game because {@code api} depends on nothing (AD-13, ADR-0003):
     * what a Run is actually started with is still the game's own call
     * ({@code shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java:310}),
     * so a divergence would refuse the Run rather than play the wrong one.
     */
    public static String code(long seed) {
        Canon.require(seed >= 0 && seed < TOTAL_SEEDS,
                "a seed is at least 0 and under " + TOTAL_SEEDS + " (DungeonSeed.TOTAL_SEEDS): " + seed);
        char[] letters = new char[CODE_LETTERS];
        long left = seed;
        for (int i = CODE_LETTERS - 1; i >= 0; i--) {
            letters[i] = (char) ('A' + (int) (left % BASE));
            left /= BASE;
        }
        return new String(letters, 0, 3) + "-" + new String(letters, 3, 3) + "-" + new String(letters, 6, 3);
    }

    /**
     * The seed the code {@code code} means, as the game's own {@code convertFromCode} computes it
     * ({@code core/…/utils/DungeonSeed.java:52-75}), for the nine-letter dashed form a Seed set
     * carries. The reader needs it to check that a committed code and its number agree without
     * trusting {@link #code}; it takes the one shape the writer writes, and not the whitespace,
     * lower case and bare-text forms the game's custom-seed window also accepts.
     */
    public static long seed(String code) {
        Canon.text(code, "a seed code");
        Canon.require(code.matches(CODE_PATTERN), "a seed code is " + CODE_PATTERN + ": " + code);
        String letters = code.replace("-", "");
        long seed = 0;
        for (int i = 0; i < CODE_LETTERS; i++) {
            seed = seed * BASE + (letters.charAt(i) - 'A');
        }
        return seed;
    }
}
