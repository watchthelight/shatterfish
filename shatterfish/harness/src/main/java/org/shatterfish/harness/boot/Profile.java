package org.shatterfish.harness.boot;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a Run inherits from the player it is pretending to be: the settings, and an empty history.
 *
 * <p>ADR-0007 makes the Profile part of a Run's definition. A human's game depends on more than the
 * seed — which language the strings are in, whether the intro is on, which guide pages have been
 * read, what the badges and the rankings and the remains of earlier games hold — and a Run that
 * inherited a different one of those would be a different game while claiming the same tuple. So a
 * Run gets its own directory, this class decides what is in it, and the version below says which
 * decisions those were.
 *
 * <p>The version is the point of the file it writes. A Run replayed a year from now against a
 * directory some other version of Shatterfish prepared is not the Run that was recorded, and the
 * only thing worse than refusing it is comparing it. So the directory carries its version, and a
 * mismatch is an exception before the Run starts rather than a difference in the numbers after.
 */
public final class Profile {

    /**
     * What this build of the harness puts in a Run's directory. Raise it whenever a setting below
     * changes, whenever the game gains one that matters to a Run, or whenever the meaning of the
     * empty history changes — and say why in the ADR-0007 amendment, because a raised version
     * invalidates every recorded Run against the old one.
     */
    public static final int VERSION = 1;

    /** The file the version is written to, beside the game's own save files. */
    public static final String VERSION_FILE = "shatterfish-profile.txt";

    private final Path directory;

    private Profile(Path directory) {
        this.directory = directory;
    }

    /** Where this Profile's files are. */
    public Path directory() {
        return directory;
    }

    /**
     * Prepares {@code directory} as a Run's Profile and points the game at it: the settings a Run
     * declares, an empty history, and the version stamped on the directory.
     *
     * @throws IllegalStateException if the directory already carries another version's stamp
     */
    public static Profile prepare(HeadlessBoot boot, Path directory) {
        Path stamp = directory.resolve(VERSION_FILE);
        if (Files.exists(stamp)) {
            int found = readVersion(stamp);
            if (found != VERSION) {
                throw new IllegalStateException("this directory was prepared by Profile version "
                        + found + " and this harness is version " + VERSION + "; a Run against it would"
                        + " not be the Run that was recorded. Use a fresh directory, or an older build.");
            }
        }
        boot.profile(directory);
        declareSettings();
        emptyTheHistory();
        write(stamp);
        return new Profile(directory);
    }

    /**
     * The settings a Run declares, every Run, so that two Runs of one tuple see one game.
     *
     * <p>Each is a choice with a consequence, and the consequences are in the Codex and on every
     * results page (ADR-0007): English strings, no intro, the support prompt already answered, and
     * the compact interface. They are set per Run and not once per process, because a process that
     * played one Run and then another under different settings would be two games with one name.
     */
    private static void declareSettings() {
        SPDSettings.intro(false);
        SPDSettings.language(com.shatteredpixel.shatteredpixeldungeon.messages.Languages.ENGLISH);
        // Picking up the first boss's key asks the player to support the game unless this is set
        // (core/.../items/keys/WornKey.java:51-60); a window nobody headless can answer.
        SPDSettings.supportNagged(true);
        // The compact interface, which is what a phone player sees. The full one hands an item
        // selector to an inventory pane a headless Run draws nowhere and no Action can name
        // (core/.../scenes/GameScene.java:399, :547-551, :1668-1684), so a Run that tried to affix
        // the broken seal stopped there — story 1.14 found it.
        SPDSettings.interfaceSize(0);
    }

    /**
     * Empties what one Run would otherwise inherit from another in the same process.
     *
     * <p>This is not housekeeping. The game reads its own history while it builds a floor: the
     * guide pages it scatters are the ones the player has not found yet
     * ({@code core/.../levels/RegularLevel.java:561-589}), so a Run that inherited the journal of
     * the Run before it generates a different dungeon from the same seed. That is what issue #70
     * was, measured: two Runs of one tuple put the same item on two different cells.
     *
     * <p>The game's own loaders cannot be asked twice — {@code Journal.loadGlobal} and
     * {@code Badges.loadGlobal} return early once their statics are set
     * ({@code core/.../journal/Journal.java:34-55}; {@code core/.../Badges.java:315-325}), and
     * {@code Rankings.load} likewise ({@code core/.../Rankings.java:427-431}). So the state is
     * restored directly from an empty bundle, which is exactly what those loaders do with the file
     * a fresh directory does not have, through the same public calls they use.
     */
    private static void emptyTheHistory() {
        Badges.reset();
        Bundle empty = new Bundle();
        Catalog.restore(empty);
        Bestiary.restore(empty);
        Document.restore(empty);
        // Rankings holds its records for the life of the process once loaded; dropping them makes
        // the next load read this Run's own directory, which is empty.
        Rankings.INSTANCE.records = null;
    }

    private static void write(Path stamp) {
        try {
            Files.createDirectories(stamp.getParent());
            Files.writeString(stamp, "shatterfish-profile-version=" + VERSION + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not stamp the Profile at " + stamp, e);
        }
    }

    private static int readVersion(Path stamp) {
        String text;
        try {
            text = Files.readString(stamp, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the Profile stamp at " + stamp, e);
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("shatterfish-profile-version=")) {
                try {
                    return Integer.parseInt(trimmed.substring(trimmed.indexOf('=') + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("the Profile stamp at " + stamp + " says "
                            + trimmed + ", which is not a version", e);
                }
            }
        }
        throw new IllegalStateException("the Profile stamp at " + stamp + " names no version");
    }

    /** Whether the game's own file access currently points at this Profile's directory. */
    public boolean inUse() {
        return FileUtils.fileExists(VERSION_FILE);
    }
}
