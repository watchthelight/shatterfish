package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RigJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.rng.Mix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The five Seed sets (FR-20, story 3.1): the committed, versioned files of (seed, hero class,
 * challenge flags) triples every published number is measured on, and the one door a Run reads
 * them through.
 *
 * <p><b>Derived, not drawn.</b> A set drawn from an unseeded source and committed is a set a
 * skeptic has to take on trust: the file is the only evidence, and nothing in it says the draw was
 * not repeated until it flattered someone. Every triple here is computed from the set's own name
 * and the triple's index, so a stranger with a SplitMix64 and no copy of this repository can
 * reproduce all 1,525 of them and check the committed files rather than believe them. The
 * committed file is then a convenience the drift check proves, which is the argument the Codex
 * makes about its tables.
 *
 * <p>The derivation, in full:
 *
 * <pre>
 * constant(name) = the name's ASCII bytes, big-endian, in the low bytes of a 64-bit word
 * seed(name, i)  = floorMod(mix(constant(name), i), DungeonSeed.TOTAL_SEEDS)
 * class(name, i) = the set's classes, cycled: classes[i % classes.size()]
 * flags(name, i) = the set's flags, which are 0 in schema version 1
 * </pre>
 *
 * where {@code mix} is the project's own published mixing function
 * ({@link Mix}, ADR-0007), which is SplitMix64's step and finalizer and has a test vector on the
 * methodology page. The constant is the set's own name and nothing else, so there is no free
 * parameter anyone could have shopped for: a new set's constant is forced the moment it is named.
 *
 * <p><b>{@code holdout} is guarded at the door.</b> FR-20 makes it testable: the Rig refuses a
 * development comparison on {@code holdout}. A guard further in is a guard someone routes around,
 * so {@link #load} — what development calls — refuses that set outright, and the only way to read
 * it is {@link #publish}, which takes the reason it is being published and hands it back on the
 * value so the Results page has it.
 */
public final class SeedSets {

    /** Where the committed sets live, relative to the repository root. */
    public static final String FOLDER = "seeds";

    /** The one command that writes them; every refusal that can be fixed by rerunning names it. */
    public static final String COMMAND = "./gradlew :rig:seeds";

    /** The smoke set: a direction check, not an acceptance (ADR-0012). */
    public static final String SMOKE = "smoke";

    /** The standard set: the one a development comparison accepts or rejects on. */
    public static final String STANDARD = "standard";

    /** The held-out set: never run during development, and refused to {@link #load} for it. */
    public static final String HOLDOUT = "holdout";

    /** The bosses set, for fight-specific comparisons. */
    public static final String BOSSES = "bosses";

    /** The E4 gate's set: Warrior only, no challenge flags, sized by the PRD's bound. */
    public static final String GOO = "goo";

    /** The six classes in the game's own declaration order ({@code HeroClass.java:87-92}). */
    private static final List<HeroClass> SIX = List.of(HeroClass.values());

    /** How many bits a byte of the name's constant takes. */
    private static final int BYTE = 8;

    /** The longest name whose ASCII fits a 64-bit constant, which is what forces the constant. */
    private static final int LONGEST_NAME = 8;

    /**
     * The five sets, in the order the task writes them. The sizes are FR-20's and are revisable by
     * ADR once E3 has measured throughput (ADR-0018); the classes and the flags are this story's.
     */
    private static final Map<String, Definition> DEFINITIONS = definitions();

    private SeedSets() {
    }

    /**
     * What a set is: how many triples, which hero classes it cycles through, and which challenge
     * flags every triple carries. A name and a schema version therefore fix the classes and the
     * flags a comparison ran under, which is what a Results page cites.
     */
    public record Definition(String name, int size, List<HeroClass> classes, int challengeFlags) {

        public Definition {
            if (name == null || !name.matches(SeedSet.NAME_PATTERN)) {
                throw new IllegalArgumentException("a set's name is " + SeedSet.NAME_PATTERN + ": " + name);
            }
            if (name.length() > LONGEST_NAME) {
                throw new IllegalArgumentException("a set's name is at most " + LONGEST_NAME
                        + " letters, because the name is the derivation's constant: " + name);
            }
            if (size < 1) {
                throw new IllegalArgumentException("a set holds at least one triple: " + size);
            }
            classes = List.copyOf(classes);
            if (classes.isEmpty()) {
                throw new IllegalArgumentException("a set names the hero classes it fixes: " + name);
            }
            if (challengeFlags < 0 || challengeFlags > SeedSet.MAX_CHALLENGE_VALUE) {
                throw new IllegalArgumentException("challenge flags are 0 through "
                        + SeedSet.MAX_CHALLENGE_VALUE + " (Challenges.MAX_VALUE): " + challengeFlags);
            }
        }
    }

    /**
     * A set that was read, and the reason the read was allowed to happen. A development read
     * states no reason and carries none; a published read states one and carries it, so a Results
     * page can print why a held-out set was opened (FR-20).
     */
    public record Read(SeedSet set, String reason) {

        public Read {
            if (set == null) {
                throw new IllegalArgumentException("a read carries the set it read");
            }
            if (reason == null) {
                throw new IllegalArgumentException("a read carries a reason or the empty string");
            }
        }

        /** Whether this read stated a reason, which is what makes it a published one. */
        public boolean published() {
            return !reason.isEmpty();
        }
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> sets = new LinkedHashMap<>();
        // FR-20's sizes. The classes each set fixes are this story's: the four general sets cycle
        // the game's six in its own declaration order, so a comparison is not a claim about one
        // class, and `goo` is Warrior only with no flags because that is the E4 gate FR-20 names.
        // A size that is not a multiple of six gives the first classes in the cycle one triple
        // more than the last; that is stated here rather than corrected, because the sizes are the
        // PRD's and the cycle is the thing a reviewer can check.
        put(sets, new Definition(SMOKE, 25, SIX, 0));
        put(sets, new Definition(STANDARD, 500, SIX, 0));
        put(sets, new Definition(HOLDOUT, 500, SIX, 0));
        put(sets, new Definition(BOSSES, 100, SIX, 0));
        put(sets, new Definition(GOO, 400, List.of(HeroClass.WARRIOR), 0));
        return Map.copyOf(sets);
    }

    private static void put(Map<String, Definition> sets, Definition definition) {
        if (sets.put(definition.name(), definition) != null) {
            throw new IllegalStateException("two sets named " + definition.name());
        }
    }

    /**
     * The hero class the game names {@code name}, refusing a name the game does not have and
     * saying what the six are.
     *
     * <p>It lives here rather than in {@code api} for the reason story 2.1 gave: a method in
     * {@code api} that turns text into a value of the schema is a reader, and {@code api} does not
     * read. Only a reader of a committed file can meet a class the game never had, and
     * {@code HeroClass.valueOf} would name it without saying what it should have been.
     */
    public static HeroClass heroClass(String name) {
        for (HeroClass heroClass : HeroClass.values()) {
            if (heroClass.name().equals(name)) {
                return heroClass;
            }
        }
        throw new IllegalArgumentException("the game has no hero class " + name + "; its six are " + SIX);
    }

    /** Every set's name, in the order the task writes them. */
    public static List<String> names() {
        return List.of(SMOKE, STANDARD, HOLDOUT, BOSSES, GOO);
    }

    /** What the set {@code name} is, refusing a name no set has. */
    public static Definition definition(String name) {
        Definition definition = DEFINITIONS.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("there is no seed set named " + name + "; the sets are " + names());
        }
        return definition;
    }

    /**
     * The derivation's constant for a set: the name's ASCII letters as a big-endian 64-bit word,
     * so that the constant is the name and nothing else. A name longer than eight letters has no
     * constant, which is why {@link Definition} refuses one.
     */
    public static long constant(String name) {
        Definition definition = definition(name);
        long value = 0;
        for (int i = 0; i < definition.name().length(); i++) {
            value = (value << BYTE) | definition.name().charAt(i);
        }
        return value;
    }

    /** The set {@code name}, derived: the same value on every machine, at every hour. */
    public static SeedSet set(String name) {
        Definition definition = definition(name);
        long constant = constant(name);
        List<SeedSet.Entry> entries = new ArrayList<>(definition.size());
        for (int i = 0; i < definition.size(); i++) {
            long seed = Math.floorMod(Mix.mix(constant, i), SeedSet.TOTAL_SEEDS);
            HeroClass heroClass = definition.classes().get(i % definition.classes().size());
            entries.add(new SeedSet.Entry(seed, heroClass, definition.challengeFlags(), SeedSet.code(seed)));
        }
        return new SeedSet(definition.name(), SeedSet.VERSION, entries);
    }

    /** The file name a set is committed under. */
    public static String file(String name) {
        return definition(name).name() + ".json";
    }

    /** Every file the task writes, by file name, in the order {@link #names()} gives. */
    public static Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<>();
        for (String name : names()) {
            files.put(file(name), RigJson.seedSet(set(name)));
        }
        return files;
    }

    /**
     * The committed set {@code name}, for a development read: a comparison, a smoke run, a test.
     *
     * <p>It refuses {@link #HOLDOUT}. A set that has been run during development is no longer a
     * held-out set, whatever anyone intended by the run, and there is no way to un-run it — so the
     * refusal is here, at the only door, rather than in the runner that would have to remember.
     */
    public static Read load(Path root, String name) {
        Definition definition = definition(name);
        if (HOLDOUT.equals(definition.name())) {
            throw new IllegalArgumentException("the " + HOLDOUT + " set is refused to a development read:"
                    + " it exists to publish a release-level number at most once per Brain version, and a"
                    + " set that has been run during development is no longer held out (FR-20). Read it"
                    + " with publish(root, \"" + HOLDOUT + "\", <the reason it is being published>), which"
                    + " records the reason in the Results.");
        }
        return new Read(read(root, definition), "");
    }

    /**
     * The committed set {@code name} for a published read, carrying the reason it was opened. This
     * is the only way to read {@link #HOLDOUT}, and the reason it takes is what FR-20 requires the
     * Results to record.
     */
    public static Read publish(Path root, String name, String reason) {
        Definition definition = definition(name);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a published read of the " + definition.name()
                    + " set states the reason it is published, which the Results page then carries (FR-20)");
        }
        return new Read(read(root, definition), reason.strip());
    }

    // ------------------------------------------------------------------ the reader of a set file

    /** The whole file: an object of the three keys, in the order the canonical writer sorts them. */
    private static final Pattern HEAD = Pattern.compile("^\\{$");

    private static final Pattern ENTRIES = Pattern.compile("^\"entries\":\\[$");

    private static final Pattern ENTRY = Pattern.compile("^ {2}\\{\"challengeFlags\":(\\d{1,3}),"
            + "\"heroClass\":\"([A-Za-z_]{1,32})\",\"seed\":(\\d{1,19}),"
            + "\"seedCode\":\"(" + SeedSet.CODE_PATTERN + ")\"\\}(,?)$");

    private static final Pattern CLOSE_ENTRIES = Pattern.compile("^\\],$");

    private static final Pattern NAME = Pattern.compile("^\"name\":\"(" + SeedSet.NAME_PATTERN + ")\",$");

    private static final Pattern VERSION = Pattern.compile("^\"version\":(\\d{1,9})$");

    private static final Pattern TAIL = Pattern.compile("^\\}$");

    /**
     * The committed file, read as the canonical text {@code RigJson} writes and nothing else.
     *
     * <p>{@code api} has no JSON reader by rule (story 2.1), so the reader is here, and it is a
     * reader of this one shape rather than of JSON: the file is generated, every deviation from
     * the shape is a hand edit or a drift, and a refusal that names the line is more use than a
     * parse that quietly accepts a file the writer would never produce.
     */
    private static SeedSet read(Path root, Definition definition) {
        Path file = root.resolve(FOLDER).resolve(file(definition.name()));
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the seed set " + definition.name() + " is not committed at "
                    + file + "; run " + COMMAND + " and commit", e);
        }
        if (text.indexOf('\r') >= 0) {
            throw new IllegalStateException(file + " holds a carriage return; run"
                    + " git add --renormalize " + FOLDER + "/ and commit");
        }
        Cursor at = new Cursor(file, text.split("\n", -1));
        at.expect(HEAD, "an object");
        at.expect(ENTRIES, "the entries");
        List<SeedSet.Entry> entries = new ArrayList<>();
        boolean more = true;
        while (more) {
            // Required rather than looked for, so that a comma after the last triple is a refusal
            // and not a file this reader accepts and the writer would never produce.
            Matcher entry = at.require(ENTRY, "a triple");
            long seed = at.number(entry.group(3), "a seed");
            String code = entry.group(4);
            long meant = SeedSet.seed(code);
            if (meant != seed) {
                throw at.refuse("the code " + code + " means the seed " + meant + ", not " + seed);
            }
            int flags = (int) at.number(entry.group(1), "challenge flags");
            try {
                entries.add(new SeedSet.Entry(seed, heroClass(entry.group(2)), flags, code));
            } catch (IllegalArgumentException refused) {
                // The records refuse a value outside the game's domains; the line is what turns
                // that into something a person can fix.
                throw at.refuse(refused.getMessage());
            }
            more = !entry.group(5).isEmpty();
            at.next();
        }
        at.expect(CLOSE_ENTRIES, "the end of the entries");
        String read = at.require(NAME, "the set's name").group(1);
        at.next();
        int version = (int) at.number(at.require(VERSION, "the set's schema version").group(1),
                "a schema version");
        at.next();
        at.expect(TAIL, "the end of the object");
        at.end();
        if (!definition.name().equals(read)) {
            throw new IllegalStateException(file + " names the set " + read + " and is committed as "
                    + file(definition.name()) + "; run " + COMMAND + " and commit");
        }
        if (version != SeedSet.VERSION) {
            throw new IllegalStateException(file + " is seed-set schema version " + version
                    + " and this Rig reads " + SeedSet.VERSION + "; run " + COMMAND + " and commit");
        }
        SeedSet set = new SeedSet(read, version, entries);
        if (set.size() != definition.size()) {
            throw new IllegalStateException(file + " holds " + set.size() + " triples and the set "
                    + definition.name() + " is defined as " + definition.size() + "; run " + COMMAND
                    + " and commit");
        }
        return set;
    }

    /** Where the reader is in a file, so that every refusal names the file and the line. */
    private static final class Cursor {

        private final Path file;
        private final String[] lines;
        private int at;

        Cursor(Path file, String[] lines) {
            this.file = file;
            this.lines = lines;
            if (lines.length < 2 || !lines[lines.length - 1].isEmpty()) {
                throw new IllegalStateException(file + " does not end in a line feed; run " + COMMAND
                        + " and commit");
            }
        }

        /** The line's groups if it matches, or nothing; the cursor does not move. */
        Matcher match(Pattern pattern) {
            if (at >= lines.length - 1) {
                return null;
            }
            Matcher matcher = pattern.matcher(lines[at]);
            return matcher.matches() ? matcher : null;
        }

        /** The line's groups, refusing the line if it does not match. The cursor does not move. */
        Matcher require(Pattern pattern, String what) {
            Matcher matcher = match(pattern);
            if (matcher == null) {
                throw refuse("expected " + what);
            }
            return matcher;
        }

        /** Requires the line and moves past it. */
        void expect(Pattern pattern, String what) {
            require(pattern, what);
            next();
        }

        void next() {
            at++;
        }

        /** Refuses anything after the one root value. */
        void end() {
            if (at != lines.length - 1) {
                throw refuse("there is text after the set");
            }
        }

        long number(String written, String what) {
            try {
                return Long.parseLong(written);
            } catch (NumberFormatException tooLong) {
                throw refuse(what + " is not a number this reader can hold: " + written);
            }
        }

        IllegalStateException refuse(String said) {
            String held = at < lines.length ? lines[at] : "";
            return new IllegalStateException(file + ":" + (at + 1) + " " + said + ", found: " + held
                    + "; run " + COMMAND + " and commit");
        }
    }
}
