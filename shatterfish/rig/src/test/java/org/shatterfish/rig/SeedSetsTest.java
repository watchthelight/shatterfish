package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RigJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Seed sets are what FR-20 says they are (story 3.1): five committed files of the stated
 * sizes, every triple in the game's own domains read from the pinned source rather than from
 * memory, a derivation two runs agree on byte for byte, a drift check that bites on a hand edit
 * and names the file and the command, a {@code holdout} that a development read cannot open, and a
 * {@code goo} set whose size is the one the PRD's bound asks for, computed here rather than
 * asserted.
 *
 * <p>The domains are read out of {@code core/src/main/java} as text, not imported: the rig depends
 * on the harness and the brain, not on the game (ADR-0003), so the constants a Seed set is bounded
 * by live in {@code api} as mirrors. A mirror nobody checks is folklore, so this reads the pinned
 * declarations the way the Codex's readers do and holds the mirrors against them. A divergence
 * therefore fails here, at the tag that caused it.
 */
class SeedSetsTest {

    /** The repository root, found from the working directory by what a checkout holds. */
    static final Path ROOT = root();

    private static final String DUNGEON_SEED =
            "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/utils/DungeonSeed.java";

    private static final String CHALLENGES =
            "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Challenges.java";

    private static final String HERO_CLASS =
            "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/HeroClass.java";

    static Path root() {
        Path here = Path.of("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("docs/UPSTREAM.md")) && Files.isDirectory(p.resolve("core/src/main/java"))) {
                return p;
            }
        }
        throw new IllegalStateException("no Shatterfish checkout above " + here);
    }

    private static String pinned(String path) {
        try {
            return Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the pinned source " + path + " is not in this checkout", e);
        }
    }

    /** The one group of the one match of {@code pattern} in the pinned file, or a failure. */
    private static String only(String path, String pattern) {
        Matcher found = Pattern.compile(pattern).matcher(pinned(path));
        assertTrue(found.find(), path + " no longer declares " + pattern + ", so this check proves nothing");
        String group = found.group(1);
        assertFalse(found.find(), path + " declares " + pattern + " more than once");
        return group;
    }

    // ------------------------------------------------------------------------------ the domains

    @Test
    @DisplayName("the bounds api carries are the ones the pinned game declares")
    void the_domains_are_the_games_own() {
        assertEquals(Long.parseLong(only(DUNGEON_SEED, "TOTAL_SEEDS\\s*=\\s*(\\d+)L")), SeedSet.TOTAL_SEEDS,
                "DungeonSeed.TOTAL_SEEDS and SeedSet.TOTAL_SEEDS");
        assertEquals(Integer.parseInt(only(CHALLENGES, "MAX_VALUE\\s*=\\s*(\\d+);")), SeedSet.MAX_CHALLENGE_VALUE,
                "Challenges.MAX_VALUE and SeedSet.MAX_CHALLENGE_VALUE");
        // The six classes, in the game's own declaration order, which is the order the api enum
        // has to be in for a Codex table and a Seed set to name the same class by the same name.
        List<String> declared = new ArrayList<>();
        Matcher constant = Pattern.compile("^\\t([A-Z_]+)\\(\\s*HeroSubClass\\.", Pattern.MULTILINE)
                .matcher(pinned(HERO_CLASS));
        while (constant.find()) {
            declared.add(constant.group(1));
        }
        assertEquals(6, declared.size(), "the game declares six hero classes: " + declared);
        assertEquals(declared, Stream.of(HeroClass.values()).map(Enum::name).toList(),
                "the game's hero classes and api's, in the game's order");
    }

    @Test
    @DisplayName("the seed code is the one the pinned source's own example gives, at both ends of the range")
    void the_seed_code_is_the_games_own() {
        // The pinned file states the largest seed and its code in prose, which is the one example
        // of the encoding the game itself publishes (DungeonSeed.java:33-37).
        String source = pinned(DUNGEON_SEED);
        assertTrue(source.contains("ZZZ-ZZZ-ZZZ") && source.contains("5,429,503,678,975"),
                DUNGEON_SEED + " no longer states its own example, so this check proves nothing");
        assertEquals("ZZZ-ZZZ-ZZZ", SeedSet.code(SeedSet.TOTAL_SEEDS - 1));
        assertEquals(5_429_503_678_975L, SeedSet.TOTAL_SEEDS - 1);
        assertEquals("AAA-AAA-AAA", SeedSet.code(0));
        assertEquals("AAA-AAA-AAB", SeedSet.code(1));
        assertEquals("AAA-AAA-ABA", SeedSet.code(26));
        // The code and the number are the same value in two alphabets, in both directions, over a
        // spread that crosses every digit.
        for (long seed : new long[] {0, 1, 25, 26, 675, 676, 1_000_000_007L, 3_343_871_708_117L,
                SeedSet.TOTAL_SEEDS - 2, SeedSet.TOTAL_SEEDS - 1}) {
            String code = SeedSet.code(seed);
            assertTrue(code.matches(SeedSet.CODE_PATTERN), code);
            assertEquals(seed, SeedSet.seed(code), code);
        }
    }

    @Test
    @DisplayName("a value outside the game's domains is refused where the value is built, naming the value and the bound")
    void the_records_refuse_what_the_game_could_not_run() {
        IllegalArgumentException past = assertThrows(IllegalArgumentException.class,
                () -> new SeedSet.Entry(SeedSet.TOTAL_SEEDS, HeroClass.WARRIOR, 0, "AAA-AAA-AAA"));
        assertTrue(past.getMessage().contains(String.valueOf(SeedSet.TOTAL_SEEDS))
                && past.getMessage().contains("TOTAL_SEEDS"), past.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSet.Entry(-1, HeroClass.WARRIOR, 0, "AAA-AAA-AAA"));

        IllegalArgumentException flags = assertThrows(IllegalArgumentException.class,
                () -> new SeedSet.Entry(0, HeroClass.WARRIOR, 512, "AAA-AAA-AAA"));
        assertTrue(flags.getMessage().contains("512") && flags.getMessage().contains("MAX_VALUE"),
                flags.getMessage());
        assertDoesNotThrow(() -> new SeedSet.Entry(0, HeroClass.WARRIOR, SeedSet.MAX_CHALLENGE_VALUE, "AAA-AAA-AAA"));

        // A hero class the game does not have can only arrive as a name, which is what a reader of
        // a committed file meets; the refusal names it and the six.
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> SeedSets.heroClass("PALADIN"));
        assertTrue(unknown.getMessage().contains("PALADIN"), unknown.getMessage());
        for (HeroClass heroClass : HeroClass.values()) {
            assertTrue(unknown.getMessage().contains(heroClass.name()), unknown.getMessage());
            assertEquals(heroClass, SeedSets.heroClass(heroClass.name()));
        }

        IllegalArgumentException code = assertThrows(IllegalArgumentException.class,
                () -> new SeedSet.Entry(1, HeroClass.WARRIOR, 0, "AAA-AAA-AAA"));
        assertTrue(code.getMessage().contains("AAA-AAA-AAB"), code.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> new SeedSet("Smoke", 1, List.of(entry(0))), "a name is lower case");
        assertThrows(IllegalArgumentException.class, () -> new SeedSet("smoke", 0, List.of(entry(0))));
        assertThrows(IllegalArgumentException.class, () -> new SeedSet("smoke", 1, List.of()));
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
                () -> new SeedSet("smoke", 1, List.of(entry(7), entry(7))));
        assertTrue(twice.getMessage().contains("twice"), twice.getMessage());
    }

    private static SeedSet.Entry entry(long seed) {
        return new SeedSet.Entry(seed, HeroClass.WARRIOR, 0, SeedSet.code(seed));
    }

    // -------------------------------------------------------------------------------- the sets

    @Test
    @DisplayName("the five sets hold their stated sizes, classes and flags, and every triple is in the game's domains")
    void the_five_sets_are_what_fr_20_says() {
        assertEquals(List.of("smoke", "standard", "holdout", "bosses", "goo"), SeedSets.names());
        for (String name : SeedSets.names()) {
            SeedSets.Definition definition = SeedSets.definition(name);
            SeedSet set = SeedSets.set(name);
            assertEquals(name, set.name());
            assertEquals(SeedSet.VERSION, set.version());
            assertEquals(definition.size(), set.size(), name + " holds its stated size");
            Set<Long> seeds = new HashSet<>();
            for (int i = 0; i < set.size(); i++) {
                SeedSet.Entry held = set.entries().get(i);
                assertTrue(held.seed() >= 0 && held.seed() < SeedSet.TOTAL_SEEDS, name + " " + held);
                assertTrue(held.challengeFlags() >= 0 && held.challengeFlags() <= SeedSet.MAX_CHALLENGE_VALUE,
                        name + " " + held);
                assertEquals(definition.challengeFlags(), held.challengeFlags(), name + " fixes its flags");
                assertEquals(definition.classes().get(i % definition.classes().size()), held.heroClass(),
                        name + " cycles the classes it fixes");
                assertEquals(SeedSet.code(held.seed()), held.seedCode(), name + " " + held);
                assertTrue(seeds.add(held.seed()), name + " draws the seed " + held.seed() + " twice");
            }
        }
        assertEquals(25, SeedSets.definition("smoke").size());
        assertEquals(500, SeedSets.definition("standard").size());
        assertEquals(500, SeedSets.definition("holdout").size());
        assertEquals(100, SeedSets.definition("bosses").size());
        SeedSets.Definition goo = SeedSets.definition("goo");
        assertEquals(400, goo.size());
        assertEquals(List.of(HeroClass.WARRIOR), goo.classes(), "the goo set is Warrior only (FR-20)");
        assertEquals(0, goo.challengeFlags(), "the goo set carries no challenge flags (FR-20)");

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> SeedSets.definition("standrad"));
        assertTrue(missing.getMessage().contains("standrad") && missing.getMessage().contains("standard"),
                missing.getMessage());
    }

    @Test
    @DisplayName("every triple is the published derivation, recomputed here from the formula rather than from the code that wrote it")
    void the_derivation_is_reproducible_from_the_formula() {
        for (String name : SeedSets.names()) {
            // The constant is the set's own name, so there is no free parameter: recomputed here
            // from the name alone, the way a stranger would.
            long constant = 0;
            for (int i = 0; i < name.length(); i++) {
                constant = (constant << 8) | name.charAt(i);
            }
            assertEquals(constant, SeedSets.constant(name), name + "'s derivation constant");
            SeedSet set = SeedSets.set(name);
            for (int i = 0; i < set.size(); i++) {
                assertEquals(Math.floorMod(splitmix64(constant, i), SeedSet.TOTAL_SEEDS),
                        set.entries().get(i).seed(), name + " triple " + i);
            }
        }
        assertEquals(0x736D6F6B65L, SeedSets.constant("smoke"), "\"smoke\" as ASCII bytes");
        assertEquals(0x676F6FL, SeedSets.constant("goo"), "\"goo\" as ASCII bytes");
    }

    /**
     * {@code splitmix64_finalize(constant + i * golden)}, written out here from ADR-0007's own
     * definition rather than called from the harness, so that this test would notice the harness
     * changing the function the derivation rests on.
     */
    private static long splitmix64(long constant, long index) {
        long z = constant + index * 0x9E3779B97F4A7C15L;
        z ^= z >>> 30;
        z *= 0xBF58476D1CE4E5B9L;
        z ^= z >>> 27;
        z *= 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return z;
    }

    @Test
    @DisplayName("no two sets share a seed, so the holdout is held out whatever class plays it")
    void the_sets_share_no_seed() {
        // Seeds, not triples. The same seed under a different hero class is the same dungeon, and
        // a dungeon development has run is not held out -- comparing Entry records would call two
        // Runs of one floor distinct because the hero differs, which is the leak, not the defence.
        // And every pair, not the holdout against a bag: an overlap between smoke and standard is
        // a measurement counted twice.
        List<String> names = SeedSets.names();
        for (int a = 0; a < names.size(); a++) {
            Set<Long> first = seedsOf(names.get(a));
            assertEquals(SeedSets.definition(names.get(a)).size(), first.size(),
                    names.get(a) + " repeats a seed inside itself");
            for (int b = a + 1; b < names.size(); b++) {
                Set<Long> shared = new HashSet<>(first);
                shared.retainAll(seedsOf(names.get(b)));
                assertEquals(Set.of(), shared,
                        names.get(a) + " and " + names.get(b) + " share these seeds, so a dungeon one"
                                + " set measures is a dungeon the other measures too");
            }
        }
    }

    /** The seeds one set names, read through the module's own derivation. */
    private static Set<Long> seedsOf(String name) {
        Set<Long> seeds = new HashSet<>();
        for (SeedSet.Entry held : SeedSets.set(name).entries()) {
            seeds.add(held.seed());
        }
        return seeds;
    }

    @Test
    @DisplayName("the four general sets cycle the game's six classes and version 1 carries no challenge flags")
    void the_sets_fix_the_classes_and_the_flags() {
        // Written out rather than read from the definitions: asserting definition.classes() against
        // definition.classes() is the shape that shipped three wrong tables in epic 2.
        List<HeroClass> six = List.of(HeroClass.WARRIOR, HeroClass.MAGE, HeroClass.ROGUE,
                HeroClass.HUNTRESS, HeroClass.DUELIST, HeroClass.CLERIC);
        for (String name : List.of("smoke", "standard", "holdout", "bosses")) {
            assertEquals(six, SeedSets.definition(name).classes(), name + " cycles the game's six");
        }
        assertEquals(List.of(HeroClass.WARRIOR), SeedSets.definition("goo").classes(),
                "the goo set is the E4 gate's, and FR-20 fixes it to the Warrior");
        for (String name : SeedSets.names()) {
            assertEquals(0, SeedSets.definition(name).challengeFlags(),
                    name + " carries no challenge flags; version 1 has none and a set that gains"
                            + " them is a new version");
        }
    }

    @Test
    @DisplayName("a definition refuses a name, a size, a class list or a flag value it cannot derive from")
    void a_definition_refuses_what_it_cannot_derive() {
        // Every one of these guards was unreachable as shipped: the five definitions are literals,
        // so nothing had ever constructed one that breaks a rule the ADR publishes.
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("toolonganame", 1, List.of(HeroClass.WARRIOR), 0),
                "a name longer than eight letters has no constant");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("Smoke", 1, List.of(HeroClass.WARRIOR), 0), "not lower case");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("", 1, List.of(HeroClass.WARRIOR), 0), "no name at all");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("ok", 0, List.of(HeroClass.WARRIOR), 0), "no triples");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("ok", 1, List.of(), 0), "no classes");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("ok", 1, null, 0), "no class list");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("ok", 1, List.of(HeroClass.WARRIOR), 512),
                "flags past the game's mask");
        assertThrows(IllegalArgumentException.class,
                () -> new SeedSets.Definition("ok", 6, List.of(HeroClass.WARRIOR, HeroClass.MAGE,
                        HeroClass.WARRIOR), 0),
                "a class named twice: the cycle covers two classes and the definition claims three");
    }

    // ------------------------------------------------------------------ the goo set's size

    @Test
    @DisplayName("the goo set is sized so that an observed 75% gives a lower confidence bound above 70%")
    void the_goo_sets_size_carries_the_prds_bound() {
        int n = SeedSets.definition("goo").size();
        double lower = wilsonLowerBound(0.75, n);
        assertTrue(lower > 0.70,
                "an observed 75% over " + n + " Runs gives a Wilson lower bound of " + lower
                        + ", which FR-20 requires to be above 0.70");
        // The size is load-bearing rather than decorative: the same observation over a smaller set
        // does not clear the bound, so a later ADR that shrinks the set has to say so.
        // 322 is the largest size that does not clear it, so this says where the edge actually
        // is. Against 300 the assertion passed for every size from 323 up, which would have let a
        // later ADR shrink the set by a fifth and still call the bound load-bearing.
        assertFalse(wilsonLowerBound(0.75, 322) > 0.70,
                "322 Runs clear the bound, so the size a later ADR may choose is not bounded here");
        assertTrue(wilsonLowerBound(0.75, 323) > 0.70, "and 323 is where it starts to clear");
        // Sanity: the bound is below the observation and rises towards it with n.
        assertTrue(lower < 0.75 && wilsonLowerBound(0.75, 4_000) > lower);
    }

    /**
     * The lower end of the Wilson score interval for {@code observed} successes in a share of
     * {@code n} trials, at the 95% two-sided level ({@code z = 1.96}):
     *
     * <pre>
     * centre = (p + z²/2n) / (1 + z²/n)
     * half   = (z / (1 + z²/n)) · sqrt(p(1-p)/n + z²/4n²)
     * lower  = centre - half
     * </pre>
     *
     * Written out here because the acceptance criterion is that the bound is computed and not
     * asserted by hand; the Rig's own statistics are ADR-0012's and arrive with the comparison.
     */
    private static double wilsonLowerBound(double observed, int n) {
        double z = 1.96;
        double zz = z * z;
        double denominator = 1 + zz / n;
        double centre = (observed + zz / (2.0 * n)) / denominator;
        double half = z / denominator * Math.sqrt(observed * (1 - observed) / n + zz / (4.0 * n * (double) n));
        return centre - half;
    }

    // ------------------------------------------------------------------ the committed files

    @Test
    @DisplayName("two derivations are byte-identical, and the committed seeds/ is a fresh one")
    void the_committed_folder_is_a_fresh_derivation() throws IOException {
        Map<String, String> first = SeedSets.files();
        Map<String, String> second = SeedSets.files();
        assertEquals(first.keySet(), second.keySet());
        for (String file : first.keySet()) {
            assertEquals(first.get(file), second.get(file), file + " differs between two derivations");
        }
        assertEquals(new TreeSet<>(List.of("bosses.json", "goo.json", "holdout.json", "smoke.json",
                "standard.json")), new TreeSet<>(first.keySet()));
        assertCommitted(ROOT.resolve(SeedSets.FOLDER), first);
    }

    /** {@code folder} holds exactly {@code fresh}, byte for byte, with line feeds only. */
    private static void assertCommitted(Path folder, Map<String, String> fresh) throws IOException {
        assertTrue(Files.isDirectory(folder), folder + " is committed");
        TreeSet<String> committed = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(f -> {
                assertTrue(Files.isRegularFile(f), f + " is not a file the seeds task writes");
                committed.add(f.getFileName().toString());
            });
        }
        assertEquals(new TreeSet<>(fresh.keySet()), committed,
                "the files under " + folder + "; run " + SeedSets.COMMAND + " and commit");
        for (String file : new TreeSet<>(fresh.keySet())) {
            byte[] onDisk = Files.readAllBytes(folder.resolve(file));
            assertFalse(new String(onDisk, StandardCharsets.UTF_8).contains("\r"),
                    file + " holds a carriage return; run git add --renormalize " + SeedSets.FOLDER
                            + "/ and commit");
            assertArrayEquals(fresh.get(file).getBytes(StandardCharsets.UTF_8), onDisk,
                    "the first differing file is " + SeedSets.FOLDER + "/" + file + "; run "
                            + SeedSets.COMMAND + " and commit");
        }
    }

    @Test
    @DisplayName("the drift check fails on a hand-edited set, naming the file and the command")
    void the_drift_check_bites(@TempDir Path folder) throws IOException {
        Map<String, String> fresh = SeedSets.files();
        Seeds.write(fresh, folder);
        assertDoesNotThrow(() -> assertCommitted(folder, fresh), "the folder as written is not drifted");
        String held = Files.readString(folder.resolve("smoke.json"), StandardCharsets.UTF_8);
        String from = "\"seed\":" + SeedSets.set("smoke").entries().get(0).seed();
        assertTrue(held.contains(from), "smoke.json no longer holds " + from + ", so this edit proves nothing");
        Files.writeString(folder.resolve("smoke.json"), held.replaceFirst(Pattern.quote(from), "\"seed\":1"),
                StandardCharsets.UTF_8);
        AssertionError caught = assertThrows(AssertionError.class, () -> assertCommitted(folder, fresh),
                "a hand-edited smoke.json is a drift");
        assertTrue(caught.getMessage().contains("seeds/smoke.json"), caught.getMessage());
        assertTrue(caught.getMessage().contains(SeedSets.COMMAND), caught.getMessage());
    }

    @Test
    @DisplayName("the task writes every set, deletes a stale file, and refuses a folder it did not write")
    void the_task_writes_the_folder(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("stale.json"), "[]\n", StandardCharsets.UTF_8);
        Seeds.main(new String[] {ROOT.toString(), folder.toString()});
        Map<String, String> fresh = SeedSets.files();
        TreeSet<String> written = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(f -> written.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(fresh.keySet()), written, "the task writes every set and deletes the stale one");
        for (String file : fresh.keySet()) {
            assertArrayEquals(fresh.get(file).getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(folder.resolve(file)), file);
        }
        Files.createDirectory(folder.resolve("pictures"));
        // A stale file beside the directory, and it sorts first: a task that deleted as it walked
        // would remove this one and only then refuse, so the refusal would arrive after the damage
        // it exists to prevent. Asserting the message alone never saw that.
        Files.writeString(folder.resolve("left-over.json"), "[]\n", StandardCharsets.UTF_8);
        IllegalStateException intruder = assertThrows(IllegalStateException.class,
                () -> Seeds.main(new String[] {ROOT.toString(), folder.toString()}));
        assertTrue(intruder.getMessage().contains("pictures"), intruder.getMessage());
        assertTrue(Files.exists(folder.resolve("left-over.json")),
                "the task deleted a file on its way to refusing the folder");
        for (String file : fresh.keySet()) {
            assertTrue(Files.exists(folder.resolve(file)), file + " was deleted before the refusal");
        }
        Files.delete(folder.resolve("pictures"));
        Files.delete(folder.resolve("left-over.json"));

        assertThrows(IllegalArgumentException.class, () -> Seeds.main(new String[] {ROOT.toString(), "a", "b"}));
        IllegalArgumentException notAcheckout = assertThrows(IllegalArgumentException.class,
                () -> Seeds.main(new String[] {folder.toString()}));
        assertTrue(notAcheckout.getMessage().contains("not a Shatterfish checkout"), notAcheckout.getMessage());
    }

    @Test
    @DisplayName("the task refuses a folder that is not the seed folder and holds something else, rather than emptying it")
    void the_task_will_not_empty_a_folder_it_does_not_own(@TempDir Path folder) throws IOException {
        // The argument is a path, and a path can be mistyped. Without this the task would have
        // taken any folder at all, deleted what it held and written the five sets into it in
        // whatever order the file system listed.
        Files.writeString(folder.resolve("letter.txt"), "dear reader\n", StandardCharsets.UTF_8);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Seeds.main(new String[] {ROOT.toString(), folder.toString()}));
        assertTrue(refused.getMessage().contains(SeedSets.FOLDER), refused.getMessage());
        assertTrue(refused.getMessage().contains(SeedSets.COMMAND), refused.getMessage());
        TreeSet<String> held = new TreeSet<>();
        try (Stream<Path> present = Files.list(folder)) {
            present.forEach(f -> held.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(List.of("letter.txt")), held,
                "the task wrote into, or emptied, a folder that was not its own");
    }

    @Test
    @DisplayName("the command every refusal names is the task that writes the sets")
    void the_command_is_the_task() throws IOException {
        // Nine refusals tell a reader to run this, and every assertion about it compared the
        // constant with itself. Rename the task and they would all point at nothing.
        String build = Files.readString(ROOT.resolve("shatterfish/rig/build.gradle"), StandardCharsets.UTF_8);
        String task = SeedSets.COMMAND.substring(SeedSets.COMMAND.lastIndexOf(':') + 1);
        assertTrue(build.contains("tasks.register('" + task + "'"),
                "the refusals name `" + SeedSets.COMMAND + "`, which has to be the task that writes them");
    }

    // ------------------------------------------------------------------------- the holdout door

    @Test
    @DisplayName("a development read of holdout is refused naming the set and what it is for; a published read carries its reason")
    void the_holdout_is_guarded_at_the_door() {
        for (String name : SeedSets.names()) {
            if (SeedSets.HOLDOUT.equals(name)) {
                continue;
            }
            SeedSets.Read read = SeedSets.load(ROOT, name);
            assertEquals(SeedSets.set(name), read.set(), name + " reads back as it was derived");
            assertEquals("", read.reason());
            assertFalse(read.published(), name + " was read for development");
        }

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> SeedSets.load(ROOT, "holdout"));
        assertTrue(refused.getMessage().contains("holdout"), refused.getMessage());
        assertTrue(refused.getMessage().contains("per Brain version"), refused.getMessage());
        assertTrue(refused.getMessage().contains("publish"), refused.getMessage());

        SeedSets.Read published = SeedSets.publish(ROOT, "holdout", "  the v0.1 release number  ");
        assertEquals(SeedSets.set("holdout"), published.set());
        assertEquals("the v0.1 release number", published.reason(), "the reason is carried, stripped");
        assertTrue(published.published());

        for (String blank : new String[] {"", "   ", "\t"}) {
            IllegalArgumentException silent = assertThrows(IllegalArgumentException.class,
                    () -> SeedSets.publish(ROOT, "holdout", blank));
            assertTrue(silent.getMessage().contains("reason"), silent.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> SeedSets.publish(ROOT, "holdout", null));
        assertThrows(IllegalArgumentException.class, () -> SeedSets.load(ROOT, "not-a-set"));
    }

    // ------------------------------------------------------------------------------ the reader

    @Test
    @DisplayName("the reader refuses a file the writer would never have produced, naming the line and the command")
    void the_reader_refuses_what_is_not_the_canonical_text(@TempDir Path root) throws IOException {
        Path seeds = Files.createDirectories(root.resolve(SeedSets.FOLDER));
        Seeds.write(SeedSets.files(), seeds);
        assertEquals(SeedSets.set("smoke"), SeedSets.load(root, "smoke").set());
        String good = RigJson.seedSet(SeedSets.set("smoke"));

        // Each of these is a way a committed file could be wrong that a looser reader would take.
        refuses(root, seeds, good.replace("\"version\":1", "\"version\":2"), "schema version 2");
        refuses(root, seeds, good.replace("\"name\":\"smoke\"", "\"name\":\"standard\""), "names the set standard");
        refuses(root, seeds, good.replaceFirst("\"heroClass\":\"WARRIOR\"", "\"heroClass\":\"PALADIN\""), "PALADIN");
        refuses(root, seeds, good.replaceFirst("\"seedCode\":\"[A-Z-]+\"", "\"seedCode\":\"AAA-AAA-AAA\""), "means the seed 0");
        refuses(root, seeds, good.replaceFirst("\"challengeFlags\":0", "\"challengeFlags\":512"), "MAX_VALUE");
        refuses(root, seeds, good.substring(0, good.length() - 2), "expected the end of the object");
        refuses(root, seeds, good + "{}\n", "text after the set");
        refuses(root, seeds, good.replace("\n", "\r\n"), "carriage return");
        refuses(root, seeds, good.substring(0, good.length() - 1), "does not end in a line feed");
        refuses(root, seeds, good.replace("  {\"challengeFlags\"", "{\"challengeFlags\""), "expected a triple");
        // A trailing comma after the last triple: the reader requires another one and refuses.
        refuses(root, seeds, good.replaceFirst("\\}\n\\],", "},\n],"), "expected a triple");
        // A set of the wrong size is refused even though every triple in it is well formed.
        refuses(root, seeds, good.replaceFirst("(?m)^  \\{\"challengeFlags\":0[^\n]*\\},\n", ""), "24 triples");
        // And a file that is well formed in every one of those ways and is still not the
        // derivation: the right name, the right version, the right size, a seed in range and its
        // own true code beside it -- just not the seed the formula gives. Nothing above rejects
        // it, and a tampered working tree could otherwise publish a number citing a set that did
        // not produce it.
        SeedSet.Entry first = SeedSets.set("smoke").entries().get(0);
        String honest = "\"seed\":" + first.seed() + ",\"seedCode\":\"" + first.seedCode() + "\"";
        assertTrue(good.contains(honest), "the writer no longer writes " + honest);
        refuses(root, seeds, good.replace(honest, "\"seed\":1,\"seedCode\":\"" + SeedSet.code(1) + "\""),
                "is not the derivation it names");
    }

    private static void refuses(Path root, Path seeds, String text, String said) throws IOException {
        Files.writeString(seeds.resolve("smoke.json"), text, StandardCharsets.UTF_8);
        RuntimeException caught = assertThrows(RuntimeException.class, () -> SeedSets.load(root, "smoke"), said);
        assertTrue(caught.getMessage().contains(said),
                "expected a refusal naming \"" + said + "\", got: " + caught.getMessage());
        assertTrue(caught.getMessage().contains("smoke.json"), caught.getMessage());
    }

    @Test
    @DisplayName("a set that is not committed is refused naming the file and the command")
    void a_missing_set_is_refused(@TempDir Path root) {
        UncheckedIOException missing = assertThrows(UncheckedIOException.class, () -> SeedSets.load(root, "smoke"));
        assertTrue(missing.getMessage().contains("smoke.json"), missing.getMessage());
        assertTrue(missing.getMessage().contains(SeedSets.COMMAND), missing.getMessage());
    }

}
