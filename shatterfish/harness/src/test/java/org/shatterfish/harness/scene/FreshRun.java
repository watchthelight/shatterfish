package org.shatterfish.harness.scene;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Starts or resumes a game the way the game does, up to the point where a scene is created, for
 * tests that bring their own scene or need the same floor twice.
 *
 * <p>{@link #start} is the driver's own start ({@code HeadlessDriver.newGame}), preceded by
 * {@link #forget}: the game loads global badges and the journal once per process, and level
 * generation reads the journal, so a Run that inherits the last Run's journal generates a
 * different floor from the same seed. Story 1.15 owns what a Profile is; until then this is the
 * minimum that makes two Runs in one process start from the same state, and it stays in the
 * tests because it reaches two private statics.
 *
 * <p>{@link #resume} is the Continue button: load the saved game and its current floor
 * ({@code core/.../scenes/InterlevelScene.java:733-747}) from a copy of a profile that
 * {@link #snapshot} took. Two Runs that must start from the same floor start here rather than
 * from {@link #start}, because generating a floor twice from one seed does not give the same
 * floor: the entrance room places the first guidebook page from a generator pushed <em>without</em>
 * a seed, deliberately, so that meta-progression does not perturb the rest of the layout
 * ({@code core/.../levels/rooms/standard/entrance/EntranceRoom.java:103-118}). That is the
 * determinism story's to settle; a saved floor sidesteps it.
 */
final class FreshRun {

    static final HeroClass HERO = HeroClass.WARRIOR;

    private FreshRun() {
    }

    /** Starts a Warrior Run with {@code seed} in a fresh profile, with the last profile forgotten first. */
    static void start(long seed) {
        forget();
        HeadlessDriver.newGame(seed, HERO);
    }

    /** A copy of a profile with a game saved in it, and the slot the game is in. */
    record Snapshot(Path directory, int slot) {
    }

    /**
     * Copies the current profile, with everything the game holds in memory written out first, so
     * that {@link #resume} can start from it as many times as needed. The slot is recorded because
     * the continue button names one.
     */
    static Snapshot snapshot(HeadlessBoot boot) throws IOException {
        Badges.saveGlobal(true);
        Journal.saveGlobal(true);
        Path snapshot = Files.createTempDirectory("shatterfish-snapshot");
        copyTree(boot.profile(), snapshot);
        return new Snapshot(snapshot, GamesInProgress.curSlot);
    }

    /**
     * Resumes the game saved in {@code snapshot}, in a fresh copy of it; {@code afterLoad} runs
     * once the game is loaded and before its floor is, which is where a generator to be counted
     * goes.
     */
    static void resume(HeadlessBoot boot, Snapshot snapshot, Runnable afterLoad) throws IOException {
        Path profile = Files.createTempDirectory("shatterfish-run");
        copyTree(snapshot.directory(), profile);
        boot.profile(profile);
        forget();
        // What the hero-select screen loads (HeroSelectScene.java:106-107), from this profile.
        Badges.loadGlobal();
        Journal.loadGlobal();

        // StartScene: the slot's continue button.
        GamesInProgress.curSlot = snapshot.slot();
        InterlevelScene.mode = InterlevelScene.Mode.CONTINUE;

        // InterlevelScene.java:733-747, restore().
        Mob.clearHeldAllies();
        GameLog.wipe();
        Dungeon.loadGame(GamesInProgress.curSlot);
        afterLoad.run();
        Level level = Dungeon.loadLevel(GamesInProgress.curSlot);
        Dungeon.switchLevel(level, Dungeon.hero.pos);
    }

    /**
     * Forgets the last profile's global badges and journal, so that the next {@code loadGlobal}
     * of each reads the current profile. Both loaders are once-per-process
     * ({@code Badges.java:315-323}, {@code Journal.java:36-56}), and level generation reads the
     * journal: which guide pages are still missing decides what the first floors drop
     * ({@code core/.../levels/RegularLevel.java:561-575}).
     */
    static void forget() {
        set(Badges.class, "global", null);
        set(Journal.class, "loaded", false);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            paths.forEach(source -> {
                Path target = to.resolve(from.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(source, target);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void set(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name
                    + " is not where the pinned upstream had it", e);
        }
    }
}
