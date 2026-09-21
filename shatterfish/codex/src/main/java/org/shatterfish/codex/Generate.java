package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Codex;
import org.shatterfish.api.CodexJson;
import org.shatterfish.api.HeroSubclass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The Codex generator (story 2.1; FR-14): {@code ./gradlew :codex:generate} writes
 * {@code codex/<tag>/} from the pinned upstream classes, in one process with no Run, no seed and
 * no Profile. Every value is a type's or a table's, read from the class or its declaration and
 * cited to the line it was found on; nothing is drawn, nothing is read from a save, and the
 * generator's classes cannot reach the Run's statics, the game's toolkit, the RNG, the clock or
 * the harness, which {@code CodexLeakTest} holds by class and by package; {@link GameContext} is
 * the one named door to the depth and the challenges a table is parameterised by (story 2.2).
 * Later stories add tables to {@link #generate(Path)}; the manifest lists whatever files there
 * are; none adds a task.
 *
 * <p>The one task writes the site's Codex pages too (story 2.9): {@link Pages#pages(Path, Map)}
 * renders {@code docs/codex/} from the text just written, so that a page cannot describe a table
 * that is not there, and the drift check covers both folders.
 *
 * <p>The files are UTF-8 with line feeds only and lists in a stated order, so that a generation
 * on any machine is the committed bytes; {@code CodexSeedFreeTest} holds it. A file in either
 * folder that the generator no longer writes is deleted, so that the one command returns the tree
 * to what the tests expect.
 */
public final class Generate {

    /** The folder every Codex lives under, relative to the repository root. */
    public static final String FOLDER = "codex";

    /** The manifest's file name; every other file is a table. */
    public static final String MANIFEST = "manifest.json";

    static final String HERO_CLASS_SOURCE = "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/HeroClass.java";
    static final String CHALLENGES_SOURCE = "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Challenges.java";

    private Generate() {
    }

    /**
     * Writes the Codex: the first argument is the repository root; the tables go to
     * {@code <root>/codex/<tag>/} and the pages to {@code <root>/docs/codex/} unless both folders
     * are named, which only a test does. Either both are named or neither, so that a caller naming
     * one folder cannot write the other over the repository's. Silent; the folders are the output.
     */
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 3) {
            throw new IllegalArgumentException("usage: Generate <repository root> [<table folder> <page folder>]");
        }
        Path root = checkout(args[0]);
        Path tables = args.length == 3 ? Path.of(args[1]).toAbsolutePath().normalize()
                : root.resolve(FOLDER).resolve(Upstream.tag(root));
        Path pages = args.length == 3 ? Path.of(args[2]).toAbsolutePath().normalize() : root.resolve(Pages.FOLDER);
        if (tables.equals(pages)) {
            throw new IllegalArgumentException("the tables and the pages cannot share a folder: " + tables
                    + "; each is written whole and what it does not write there is deleted");
        }
        // Both are rendered before either is written, so that a table the renderer refuses cannot
        // leave one folder regenerated and the other standing at what it said before.
        Map<String, String> generated = generate(root);
        Map<String, String> rendered = Pages.pages(root, generated);
        write(generated, tables);
        write(rendered, pages);
    }

    /** The repository root the argument names, real and checked to be a Shatterfish checkout. */
    static Path checkout(String argument) {
        Path root = Path.of(argument).toAbsolutePath().normalize();
        try {
            root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("not a directory: " + argument, e);
        }
        if (!Files.isDirectory(root.resolve("core/src/main/java")) || !Files.isRegularFile(root.resolve("docs/UPSTREAM.md"))) {
            throw new IllegalArgumentException("not a Shatterfish checkout (no core/src/main/java or docs/UPSTREAM.md): " + root);
        }
        return root;
    }

    /**
     * Writes every file of {@code files} under {@code folder} as UTF-8 bytes, and deletes any
     * regular file there that is not one of them.
     */
    public static void write(Map<String, String> files, Path folder) {
        try {
            Files.createDirectories(folder);
            try (Stream<Path> present = Files.list(folder)) {
                for (Path stale : present.filter(p -> !files.containsKey(p.getFileName().toString())).toList()) {
                    // A folder is refused rather than removed: the generator owns the files it
                    // writes, and deleting a tree it never wrote is not a thing one command should
                    // do quietly. It is also what made a directory invisible to the drift check.
                    if (Files.isDirectory(stale)) {
                        throw new IllegalStateException(stale + " is not the generator's; "
                                + folder + " holds only what the Codex writes");
                    }
                    Files.delete(stale);
                }
            }
            for (Map.Entry<String, String> file : files.entrySet()) {
                Files.write(folder.resolve(file.getKey()), file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the Codex could not be written under " + folder, e);
        }
    }

    /**
     * Every Codex file's text, by file name, in the order they are written: the manifest first,
     * then the tables in alphabetical order. The manifest lists the tables from the same map.
     */
    public static Map<String, String> generate(Path root) {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("challenges.json", CodexJson.challenges(challenges(root)));
        tables.put("combat.json", CodexJson.combat(Combat.read(root)));
        tables.put("levels.json", CodexJson.structure(Structure.read(root)));
        tables.put("recipes.json", CodexJson.recipes(Recipes.entries(root)));
        tables.put("traps.json", CodexJson.traps(Traps.entries(root), Traps.pools(root, List.<Class<?>>copyOf(Guarantees.LEVELS))));
        tables.put("hero-classes.json", CodexJson.heroClasses(heroClasses(root)));
        // The mobs and the items are read once each for the same reason as the strings below:
        // three tables need them, and three readings are three lists that must agree.
        List<Codex.MobEntry> mobs = Mobs.entries(root);
        List<Codex.ItemEntry> items = Items.entries(root);
        tables.put("mobs.json", CodexJson.mobs(mobs));
        tables.put("spawn-rotation.json", CodexJson.spawnRotation(Rotation.read(root, Mobs.canonicalNames())));
        tables.put("decks.json", CodexJson.decks(Decks.read(root)));
        tables.put("guarantees.json", CodexJson.guarantees(Guarantees.read(root)));
        tables.put("items.json", CodexJson.items(items));
        tables.put("rooms.json", CodexJson.rooms(Rooms.read(root)));
        tables.put("tiers.json", CodexJson.tiers(Tiers.read(root)));
        // The strings are read once and handed to both tables that need them: reading them twice
        // walks every source file of the game twice and makes two lists that must agree.
        List<Codex.StringEntry> strings = Text.entries(root);
        tables.put("strings.json", CodexJson.strings(strings));
        tables.put("assets.json", CodexJson.assets(AssetIndex.entries(root)));
        tables.put("changelog.json", CodexJson.changelog(Changelog.version(root), Changelog.entries(root, strings)));
        tables.put("documents.json", CodexJson.documents(Documents.entries(root)));
        tables.put("vocabulary.json", CodexJson.vocabulary(Vocabulary.read(root, strings, mobs, items)));
        Map<String, String> files = new LinkedHashMap<>();
        files.put(MANIFEST, CodexJson.manifest(manifest(Upstream.tag(root), tables.keySet())));
        files.putAll(tables);
        return files;
    }

    /** The manifest for {@code tables}, the file names of every table written beside it. */
    static Codex.Manifest manifest(String tag, Iterable<String> tables) {
        List<String> names = new ArrayList<>();
        tables.forEach(names::add);
        return new Codex.Manifest(Codex.VERSION, tag, names);
    }

    /**
     * The six hero classes in the game's declaration order, each with its subclasses in the order
     * the constructor lists them ({@code HeroClass.java:87-92}, {@code :275}); cited to the
     * constant's own line.
     */
    static List<Codex.HeroClassEntry> heroClasses(Path root) {
        List<Codex.HeroClassEntry> entries = new ArrayList<>();
        for (HeroClass heroClass : HeroClass.values()) {
            List<HeroSubclass> subclasses = new ArrayList<>();
            for (HeroSubClass subclass : heroClass.subClasses()) {
                subclasses.add(api(HeroSubclass::valueOf, subclass.name(), "hero subclass", "org.shatterfish.api.HeroSubclass"));
            }
            entries.add(new Codex.HeroClassEntry(api(org.shatterfish.api.HeroClass::valueOf, heroClass.name(), "hero class",
                    "org.shatterfish.api.HeroClass"), subclasses,
                    Citations.at(root, HERO_CLASS_SOURCE, "^\\s*" + Pattern.quote(heroClass.name()) + "\\s*\\(")));
        }
        return entries;
    }

    /** The game's bit for an api challenge, constant by constant. */
    static int mask(Challenge challenge) {
        return switch (challenge) {
            case NO_FOOD -> Challenges.NO_FOOD;
            case NO_ARMOR -> Challenges.NO_ARMOR;
            case NO_HEALING -> Challenges.NO_HEALING;
            case NO_HERBALISM -> Challenges.NO_HERBALISM;
            case SWARM_INTELLIGENCE -> Challenges.SWARM_INTELLIGENCE;
            case DARKNESS -> Challenges.DARKNESS;
            case NO_SCROLLS -> Challenges.NO_SCROLLS;
            case CHAMPION_ENEMIES -> Challenges.CHAMPION_ENEMIES;
            case STRONGER_BOSSES -> Challenges.STRONGER_BOSSES;
        };
    }

    /**
     * The nine challenge flags in the api's order, which is the declaration order of
     * {@code Challenges.java:30-38} (the player-facing order is {@code NAME_IDS}, {@code :43}),
     * each with the bit the game stores it under; cited to the constant's own line. The masks are
     * named one by one rather than read by reflection, so that a renamed constant fails to compile
     * here rather than to resolve; the count, the union and the set are checked against the
     * game's own {@code MAX_CHALS}, {@code MAX_VALUE} and {@code MASKS}, so that a flag the game
     * adds is missing loudly.
     */
    static List<Codex.ChallengeEntry> challenges(Path root) {
        List<Codex.ChallengeEntry> entries = new ArrayList<>();
        int union = 0;
        TreeSet<Integer> masks = new TreeSet<>();
        for (Challenge challenge : Challenge.values()) {
            int mask = mask(challenge);
            union |= mask;
            masks.add(mask);
            entries.add(new Codex.ChallengeEntry(challenge, mask,
                    Citations.at(root, CHALLENGES_SOURCE, "^\\s*public static final int\\s+" + Pattern.quote(challenge.name()) + "\\s*=")));
        }
        TreeSet<Integer> game = new TreeSet<>();
        Arrays.stream(Challenges.MASKS).forEach(game::add);
        if (entries.size() != Challenges.MAX_CHALS || union != Challenges.MAX_VALUE || !masks.equals(game)) {
            throw new IllegalStateException("the challenge table does not cover the game's flags: " + entries.size() + " of "
                    + Challenges.MAX_CHALS + ", union " + union + " of " + Challenges.MAX_VALUE + ", masks " + masks + " against " + game
                    + "; extend org.shatterfish.api.Challenge and bump Codex.VERSION");
        }
        return entries;
    }

    /** The api constant named as the game names it, or an instruction naming the enum to extend. */
    static <E extends Enum<E>> E api(Function<String, E> valueOf, String name, String what, String type) {
        try {
            return valueOf.apply(name);
        } catch (IllegalArgumentException missing) {
            throw new IllegalStateException("the game declares a " + what + " the api does not know: " + name
                    + "; extend " + type + " and bump Codex.VERSION", missing);
        }
    }
}
