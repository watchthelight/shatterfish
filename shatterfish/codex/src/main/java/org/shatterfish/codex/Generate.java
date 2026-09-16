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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Codex generator (story 2.1; FR-14): {@code ./gradlew :codex:generate} writes
 * {@code codex/<tag>/} from the pinned upstream classes, in one process with no Run, no seed and
 * no Profile. Every value is a type's or a table's, read from the class or its declaration and
 * cited to the line it was found on; nothing is drawn, nothing is read from a save, and the
 * generator's classes cannot reach the Run's statics, the game's RNG or the harness, which
 * {@code CodexLeakTest} holds. Later stories add tables to {@link #generate(Path)} and files to
 * the manifest; none adds a task.
 *
 * <p>The files are UTF-8 with line feeds only and lists in a stated order, so that a generation
 * on any machine is the committed bytes; {@code CodexSeedFreeTest} holds it.
 */
public final class Generate {

    /** The folder every Codex lives under, relative to the repository root. */
    public static final String FOLDER = "codex";

    static final String HERO_CLASS_SOURCE = "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/HeroClass.java";
    static final String CHALLENGES_SOURCE = "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Challenges.java";

    private Generate() {
    }

    /**
     * Writes the Codex under {@code <root>/codex/<tag>/}; the root is the first argument or the
     * working directory. Prints each file written.
     */
    public static void main(String[] args) {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path folder = root.resolve(FOLDER).resolve(Upstream.tag());
        Map<String, String> files = generate(root);
        try {
            Files.createDirectories(folder);
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path target = folder.resolve(file.getKey());
                Files.write(target, file.getValue().getBytes(StandardCharsets.UTF_8));
                System.out.println("codex: wrote " + root.relativize(target).toString().replace('\\', '/'));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the Codex could not be written under " + folder, e);
        }
    }

    /**
     * Every Codex file's text, by file name, in the order they are written: the manifest first,
     * then the tables in alphabetical order.
     */
    public static Map<String, String> generate(Path root) {
        List<Codex.HeroClassEntry> heroClasses = heroClasses(root);
        List<Codex.ChallengeEntry> challenges = challenges(root);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.json", CodexJson.manifest(new Codex.Manifest(Codex.VERSION, Upstream.tag(),
                List.of("challenges.json", "hero-classes.json"))));
        files.put("challenges.json", CodexJson.challenges(challenges));
        files.put("hero-classes.json", CodexJson.heroClasses(heroClasses));
        return files;
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
                subclasses.add(HeroSubclass.valueOf(subclass.name()));
            }
            entries.add(new Codex.HeroClassEntry(org.shatterfish.api.HeroClass.valueOf(heroClass.name()), subclasses,
                    Citations.at(root, HERO_CLASS_SOURCE, "^\\s*" + heroClass.name() + "\\s*\\(")));
        }
        return entries;
    }

    /**
     * The nine challenge flags in the api's order, which is the game's, each with the bit the
     * game stores it under ({@code Challenges.java:30-38}); cited to the constant's own line. The
     * masks are named one by one rather than read by reflection, so that a renamed constant fails
     * to compile here rather than to resolve.
     */
    static List<Codex.ChallengeEntry> challenges(Path root) {
        List<Codex.ChallengeEntry> entries = new ArrayList<>();
        for (Challenge challenge : Challenge.values()) {
            int mask = switch (challenge) {
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
            entries.add(new Codex.ChallengeEntry(challenge, mask,
                    Citations.at(root, CHALLENGES_SOURCE, "^\\s*public static final int\\s+" + challenge.name() + "\\s*=")));
        }
        return entries;
    }
}
