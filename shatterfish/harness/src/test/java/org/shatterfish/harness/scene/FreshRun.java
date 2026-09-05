package org.shatterfish.harness.scene;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Starts or resumes a game the way the game does, up to the point where the scene is created.
 *
 * <p>{@link #start} is the path a player takes: type a seed into the custom-seed window, pick a
 * class, press start ({@code core/.../scenes/HeroSelectScene.java:157-162}), and let the loading
 * scene build the first floor ({@code core/.../scenes/InterlevelScene.java:622-649}, the branch
 * with no hero yet). Nothing is hand-placed and nothing is skipped; what story 1.1's spike built
 * by hand, this asks the game for. Story 1.4 lifts it into the driver.
 *
 * <p>{@link #resume} is the Continue button: load the saved game and its current floor
 * ({@code InterlevelScene.java:733-747}) from a copy of a profile that {@link #snapshot} took.
 * Two Runs that must start from the same floor start here rather than from {@link #start},
 * because generating a floor twice from one seed does not give the same floor: the entrance room
 * places the first guidebook page from a generator pushed <em>without</em> a seed, deliberately,
 * so that meta-progression does not perturb the rest of the layout
 * ({@code core/.../levels/rooms/standard/entrance/EntranceRoom.java:103-118}). That is the
 * determinism story's to settle; a saved floor sidesteps it.
 *
 * <p>Every start and resume gets a fresh profile directory, and forgets what the game cached
 * from the last one: global badges and the journal are loaded once per process and would
 * otherwise leak from one Run into the next. Story 1.15 owns what a Profile is; until then this
 * is the minimum that makes two Runs start from the same state.
 */
final class FreshRun {

    static final HeroClass HERO = HeroClass.WARRIOR;

    private FreshRun() {
    }

    /**
     * Starts a Run with {@code seed}; {@code afterInit} runs right after {@code Dungeon.init()}
     * has reset the generator stack, which is where a generator to be counted goes.
     */
    static void start(HeadlessBoot boot, long seed, Runnable afterInit) throws IOException {
        boot.profile(Files.createTempDirectory("shatterfish-run"));
        forgetTheLastProfile();

        // HeroSelectScene.java:157-162, the start button.
        SPDSettings.customSeed(DungeonSeed.convertToCode(seed));
        GamesInProgress.selectedClass = HERO;
        GamesInProgress.curSlot = GamesInProgress.firstEmpty();
        Dungeon.hero = null;
        Dungeon.daily = Dungeon.dailyReplay = false;
        Dungeon.initSeed();
        ActionIndicator.clearAction();
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;

        // InterlevelScene.java:622-649, descend() with no hero.
        Mob.clearHeldAllies();
        Dungeon.init();
        afterInit.run();
        GameLog.wipe();
        Level level = Dungeon.newLevel();
        Dungeon.switchLevel(level, -1);
    }

    /** A copy of a profile with a game saved in it, and the slot the game is in. */
    record Snapshot(Path directory, int slot) {
    }

    /**
     * Copies the current profile, with everything the game holds in memory written out first, so
     * that {@link #resume} can start from it as many times as needed. The slot is recorded because
     * {@code GamesInProgress} remembers which slots it has seen occupied for the life of the
     * process, so the slot a fresh profile's game lands in depends on what ran before.
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
        forgetTheLastProfile();

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
     * What the hero-select screen loads before a game starts
     * ({@code core/.../scenes/HeroSelectScene.java:106-107}), loaded again from the current
     * profile. Level generation reads the journal: which guide pages are still missing decides
     * what the first floors drop ({@code core/.../levels/RegularLevel.java:561-575}), so a Run
     * that inherits the last Run's journal generates a different floor from the same seed. Both
     * loaders are once-per-process, hence the two fields reset first.
     */
    private static void forgetTheLastProfile() {
        set(Badges.class, "global", null);
        set(Journal.class, "loaded", false);
        Badges.loadGlobal();
        Journal.loadGlobal();
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
