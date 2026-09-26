package org.shatterfish.harness.boot;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Bones;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.watabou.utils.FileUtils;

import java.util.List;
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
    public static final int VERSION = 3;

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
        return prepare(new Target() {
            @Override
            public void pointAt(Path where) {
                boot.profile(where);
            }

            @Override
            public void clearPreferences() {
                boot.preferences().clear();
            }
        }, directory);
    }

    /**
     * What a Profile is prepared into: where the game's file access points, and the settings it
     * reads. The headless boot is one ({@link #prepare(HeadlessBoot, Path)}); the Overlay's launcher
     * is the other (story 5.1), which owns a Run's directory and settings in a desktop game so that a
     * Run never reads or writes the player's own saves, badges or preferences.
     */
    public interface Target {
        /** Points the game's file access at {@code directory}, which exists. */
        void pointAt(Path directory);

        /** Forgets every setting, so that the Run begins from the game's defaults. */
        void clearPreferences();
    }

    /**
     * A target for a game this process did not boot headlessly: the game's files at the directory,
     * as an absolute path, the save slots it remembers forgotten, and {@code preferences} as the
     * settings, which the caller has installed with {@code GameSettings.set} before the game reads any.
     */
    public static Target owned(MemoryPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("a Run's settings are its own preferences");
        }
        return new Target() {
            @Override
            public void pointAt(Path directory) {
                FileUtils.setDefaultFileProperties(com.badlogic.gdx.Files.FileType.Absolute,
                        directory.toAbsolutePath() + "/");
                // What HeadlessBoot.profile forgets, for the same reason: the game remembers which
                // slots it has seen occupied for the life of the process (GamesInProgress.java:40-41).
                for (int slot = 1; slot <= GamesInProgress.MAX_SLOTS; slot++) {
                    GamesInProgress.setUnknown(slot);
                }
            }

            @Override
            public void clearPreferences() {
                preferences.clear();
            }
        };
    }

    /**
     * Prepares {@code directory} as a Run's Profile through {@code target}: the settings a Run
     * declares, an empty history, and the version stamped on the directory.
     *
     * @throws IllegalStateException if the directory already carries another version's stamp
     */
    public static Profile prepare(Target target, Path directory) {
        Path stamp = directory.resolve(VERSION_FILE);
        if (Files.exists(stamp)) {
            int found = readVersion(stamp);
            if (found != VERSION) {
                throw new IllegalStateException("this directory was prepared by Profile version "
                        + found + " and this harness is version " + VERSION + "; a Run against it would"
                        + " not be the Run that was recorded. Use a fresh directory, or an older build.");
            }
        }
        target.pointAt(directory);
        // Fresh preferences, not inherited ones. The game writes its own preferences during play:
        // dragging the waterskin out of a quickslot turns off the setting that slots it for every
        // game after (core/.../ui/QuickSlotButton.java:390; :283 turns it back on), and the hero
        // records the vault's warning (core/.../actors/hero/Hero.java:953-954). A process that has played many Runs
        // therefore starts the next one with a different hero screen from a fresh process's, which
        // is exactly what the two-JVM determinism test found on its first full build. Every Run
        // begins from the game's defaults, and then declares its own.
        target.clearPreferences();
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
        // The waterskin in a quickslot, which is the game's default and the one the game turns off
        // by itself when a player drags it out (core/.../ui/QuickSlotButton.java:390); declared so
        // that the Profile says it rather than relying on the cleared preferences' default.
        SPDSettings.quickslotWaterskin(true);
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
        // The seed text the last Run left behind. It is inherited state like any other -- the game
        // sets it in Dungeon.initSeed (core/.../Dungeon.java:223-228), which runs after this -- and
        // leaving it standing does more than untidiness: Badges.unlock refuses a LOCAL-type badge
        // while it is non-empty (core/.../Badges.java:1209-1214), so the grant below silently did
        // nothing from the second Run of a process onward.
        Dungeon.customSeedText = "";
        // The remains of a hero who died in an earlier Run of this process (story 5.1). Bones keeps
        // what it last left or read in statics and reads its file only while its depth is -1
        // (core/.../Bones.java:50-54, :154-160), so a Run's fresh directory, which has no bones file,
        // is never read, and the Run's floors get the earlier hero's remains: two Runs of one tuple,
        // one of them with a grave (the determinism test of story 5.1 found it). Bones has no reset;
        // its own daily branch is the public door that puts the depth back to -1 and writes nothing
        // (Bones.java:62-68). A Run in a process of its own, which every Rig Run is, starts at -1
        // anyway, so this changes no Run the Rig has recorded.
        boolean daily = Dungeon.daily;
        Dungeon.daily = true;
        try {
            Bones.leave();
        } finally {
            Dungeon.daily = daily;
        }
        // Badges.reset clears the local badges and calls loadGlobal, which returns early once the
        // static is set (core/.../Badges.java:244-247, :315-325), so the global badges an earlier
        // Run in this process earned survive it. Emptying them here through disown -- the only
        // public door -- was tried and put back: a Run then earns them again during play, and
        // earning one posts a badge window to the render thread, which the next Run refuses to
        // start on top of. That is ADR-0007's leak rather than this story's, and it is issue #117.
        Badges.reset();
        unlockTheClasses();
        // Every page of every document, deleted one by one, because that is the only public way to
        // put one back: restore() adds what a bundle holds and never takes anything away
        // (core/.../journal/Document.java:357-375), so restoring from an empty bundle leaves a
        // process's pages exactly where they were.
        for (Document document : Document.values()) {
            for (String page : document.pageNames()) {
                document.deletePage(page);
            }
        }
        // Rankings holds its records for the life of the process once loaded; dropping them makes
        // the next load read this Run's own directory, which is empty.
        Rankings.INSTANCE.records = null;
    }

    /**
     * The five hero classes a new player has to earn, granted deliberately (story 3.1).
     *
     * <p>{@code Badges.reset()} above leaves a profile that has played nothing, and
     * {@code HeroClass.isUnlocked()} reads exactly these badges
     * ({@code core/.../actors/hero/HeroClass.java:330-347}) -- it returns true unconditionally
     * only on a build whose version says {@code INDEV}
     * ({@code SPD-classes/.../DeviceCompat.java:54-56}), which a pinned release never does. So a
     * profile that has forgotten everything can select the Warrior and nothing else.
     *
     * <p>The seed sets name all six classes, and a set whose Runs cannot be started is a set that
     * lies. Granting the badges is not an advantage at play: it is the menu a player reaches by
     * having played, and the rig skips the earning rather than the playing. Nothing the bot reads
     * changes, so information parity is untouched -- what changes is which hero the Run may start
     * as, which is a Run's own tuple and not something the bot may know.
     *
     * <p>It has to run after the global badges are emptied and after the seed text is cleared, and
     * both for the same reason: these are {@code LOCAL}-type badges (core/.../Badges.java:77-81),
     * and {@code Badges.unlock} refuses one while a custom seed is set
     * (core/.../Badges.java:1209-1214), which is how a seeded game avoids handing out unlocks.
     */
    private static void unlockTheClasses() {
        for (Badges.Badge badge : List.of(Badges.Badge.UNLOCK_MAGE, Badges.Badge.UNLOCK_ROGUE,
                Badges.Badge.UNLOCK_HUNTRESS, Badges.Badge.UNLOCK_DUELIST, Badges.Badge.UNLOCK_CLERIC)) {
            Badges.unlock(badge);
        }
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
