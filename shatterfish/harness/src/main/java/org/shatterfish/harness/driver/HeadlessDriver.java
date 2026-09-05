package org.shatterfish.harness.driver;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.watabou.noosa.Scene;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.HeadlessGame;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.harness.scene.SceneStepper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * The headless driver: owns one Run's game and the loop that advances it, on the calling thread.
 *
 * <p>ADR-0015 decided that the driver thread owns the main loop headlessly, as the render thread
 * owns it in the game, and that the driver drains what the game posts to the render thread. The
 * boot ({@link HeadlessBoot}) gives the process a backend whose own loop never runs; the scene
 * ({@link HeadlessScene}) is the game's own; the stepper ({@link SceneStepper}) advances it one
 * fenced frame at a time and returns with the actor thread parked. What this class adds is the
 * Run: it starts a seeded game the way a player does, and it steps the scene until the hero waits
 * for input, the hero is dead, or the game asks for another scene. Nothing else advances the game.
 *
 * <p><b>The Input wait.</b> Between two frames the actor thread is parked and everything it wrote
 * is published, so the hero's flags can be read plainly: an Input wait is the hero {@code ready}
 * with no action pending and not resting ({@code core/.../actors/hero/Hero.java:935-946},
 * {@code :870-876}). One more thing must hold, which the flags do not show: nothing may be waiting
 * in the render thread's queue. The hero's own act can post the window that makes the wait a
 * Prompt, as the chasm jump does ({@code .../levels/features/Chasm.java:57-96}), and the game
 * shows that window at the start of the next frame, before anything could observe or click. So a
 * wait with a runnable pending is stepped one frame further, which is the order the render thread
 * gives it. Story 1.5 replaces reading the flags with the game's own notification at the observe
 * site; the confirmation stays.
 *
 * <p><b>Stopping.</b> A dead hero stops the loop, because the scene stops waking the actor thread
 * then ({@code .../scenes/GameScene.java:865}); the game-over banner it posted is run when the
 * scene is destroyed, not in the next Run. A requested scene change stops it, because the
 * actor loop picks nobody while one is pending ({@code .../actors/Actor.java:252}) and this driver
 * does not serve it: the stairs, a fall and the end of a game are scene changes, and serving them
 * is the scene-lifetime work of the Run stories. A Run that reaches none of these within its
 * frame budget fails with {@link Stalled}, whose message names the actor the game is waiting on,
 * rather than looping forever; the stepper's own timeout covers a thread that never parks.
 */
public final class HeadlessDriver implements AutoCloseable {

    /**
     * Frames a single {@link #stepToInputWait()} may take before the Run is declared stalled. A
     * turn is a frame or two and a full rest a few hundred; ten thousand is two thousand seconds
     * of frame time and a few seconds of wall clock.
     */
    public static final int DEFAULT_FRAME_BUDGET = 10_000;

    /** What the backend reported about itself after booting. */
    public record Boot(String applicationType, String upstreamVersion) {
    }

    /** Why {@link #stepToInputWait()} returned. */
    public enum Reason {
        /** The hero waits for input, under {@link Halt#window()} when that is not null. */
        INPUT_WAIT,
        /** The hero is dead; the scene will not wake the actor thread again. */
        HERO_DEAD,
        /** Game code asked for {@link Halt#requestedScene()}; the actor loop picks nobody until it is served. */
        SCENE_SWITCH
    }

    /**
     * Where a call to {@link #stepToInputWait()} stopped: why, how many frames it stepped, the
     * window in front if any, and the scene the game asked for if it asked.
     */
    public record Halt(Reason reason, long framesStepped, Window window, Class<? extends Scene> requestedScene) {
    }

    /** A Run that reached no wait within its frame budget; the message says what it was waiting on. */
    public static final class Stalled extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        Stalled(String message) {
            super(message);
        }
    }

    private final HeadlessBoot boot;
    private final HeadlessScene scene;
    private long frames;
    private boolean closed;

    private HeadlessDriver(HeadlessBoot boot, HeadlessScene scene) {
        this.boot = boot;
        this.scene = scene;
    }

    /** Boots the process, or returns the boot that already happened. */
    public static Boot boot() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        return new Boot(Gdx.app.getType().name(), boot.upstreamVersionName());
    }

    /**
     * Starts a Run of {@code heroClass} with {@code seed} and creates its scene; the hero has not
     * acted yet. {@code seed} is what a player could type: {@code [0, DungeonSeed.TOTAL_SEEDS)}.
     */
    public static HeadlessDriver start(long seed, HeroClass heroClass) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        newGame(seed, heroClass);
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return new HeadlessDriver(boot, scene);
    }

    /**
     * Starts a new game in a fresh profile directory, up to and including its first floor, and
     * creates no scene: the path a player takes from typing a seed into the custom-seed window to
     * pressing start ({@code core/.../scenes/HeroSelectScene.java:157-162}), and then what the
     * loading scene does for a game with no hero yet
     * ({@code .../scenes/InterlevelScene.java:622-649}). Nothing is hand-placed and nothing is
     * skipped. {@link #start} is this plus the scene; a test that brings its own scene calls this.
     */
    public static void newGame(long seed, HeroClass heroClass) {
        if (heroClass == null) {
            throw new IllegalArgumentException("a Run needs a hero class");
        }
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (SceneStepper.theSceneHasALiveActorThread()) {
            throw new IllegalStateException("a Run is in progress in this process; close it before starting another");
        }
        if (boot.pendingRunnables() != 0) {
            throw new IllegalStateException("the last Run left " + boot.pendingRunnables() + " runnable(s) queued for"
                    + " the render thread, which would run in this Run's scene; HeadlessGame drains the queue when"
                    + " a scene is destroyed, so the last scene was not destroyed");
        }
        try {
            boot.profile(Files.createTempDirectory("shatterfish-run"));
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a profile directory for the Run", e);
        }

        // What the hero-select screen loads before any game starts (HeroSelectScene.java:106-107).
        // Both load once per process; story 1.15 owns what a Profile is and when it is reloaded.
        Badges.loadGlobal();
        Journal.loadGlobal();

        // HeroSelectScene.java:157-162, the start button, with the seed typed into the seed window
        // and the class and the slot chosen on the screens before it.
        SPDSettings.customSeed(DungeonSeed.convertToCode(seed));
        GamesInProgress.selectedClass = heroClass;
        GamesInProgress.curSlot = GamesInProgress.firstEmpty();
        if (GamesInProgress.curSlot < 1) {
            throw new IllegalStateException("no free save slot: every slot this process has seen is occupied");
        }
        Dungeon.hero = null;
        Dungeon.daily = Dungeon.dailyReplay = false;
        Dungeon.initSeed();
        ActionIndicator.clearAction();
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;

        // InterlevelScene.java:622-649, descend() with no hero: a new game and its first floor.
        Mob.clearHeldAllies();
        Dungeon.init();
        GameLog.wipe();
        Level level = Dungeon.newLevel();
        Dungeon.switchLevel(level, -1);
    }

    /** Steps until the hero waits for input, the hero is dead or a scene change is requested. */
    public Halt stepToInputWait() {
        return stepToInputWait(DEFAULT_FRAME_BUDGET);
    }

    /**
     * Steps at most {@code frameBudget} frames until the hero waits for input, the hero is dead or
     * a scene change is requested, and says which; always steps at least one frame.
     *
     * @throws Stalled if none of the three happened within the budget
     */
    public Halt stepToInputWait(int frameBudget) {
        requireOpen();
        if (frameBudget < 1) {
            throw new IllegalArgumentException("the frame budget must be at least one frame: " + frameBudget);
        }
        HeadlessGame game = boot.game();
        if (game.sceneSwitchRequested()) {
            throw new IllegalStateException("the game has asked for a change to "
                    + game.requestedSceneClass().getSimpleName() + ", which this driver does not serve; the actor"
                    + " loop picks nobody until it is served, so stepping on would only spend the budget");
        }
        long before = frames;
        while (true) {
            step();
            long stepped = frames - before;
            Hero hero = Dungeon.hero;
            if (hero == null || !hero.isAlive()) {
                return new Halt(Reason.HERO_DEAD, stepped, scene.openWindow(), null);
            }
            if (game.sceneSwitchRequested()) {
                return new Halt(Reason.SCENE_SWITCH, stepped, scene.openWindow(), game.requestedSceneClass());
            }
            if (hero.ready && hero.curAction == null && !hero.resting && boot.pendingRunnables() == 0) {
                return new Halt(Reason.INPUT_WAIT, stepped, scene.openWindow(), null);
            }
            if (stepped >= frameBudget) {
                throw new Stalled(diagnose(frameBudget));
            }
        }
    }

    /** One fenced frame; see {@link SceneStepper#step()}. */
    public void step() {
        requireOpen();
        scene.stepper().step();
        frames++;
    }

    /** Frames this driver has stepped, counted here and not by the scene or the stepper. */
    public long frames() {
        return frames;
    }

    public HeadlessScene scene() {
        return scene;
    }

    public SceneStepper stepper() {
        return scene.stepper();
    }

    public HeadlessBoot headlessBoot() {
        return boot;
    }

    public boolean closed() {
        return closed;
    }

    /** Ends the actor thread and destroys the scene; the process stays booted for the next Run. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            scene.stepper().endActorThread();
        } finally {
            boot.game().destroy();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this Run has been closed");
        }
    }

    /**
     * What a stalled Run was waiting on. The game records the actor whose turn it is in
     * {@code Actor.current}, which the stepper reads: non-null between frames means an
     * {@code act()} returned false and nothing has called {@code next()} since, which is a turn
     * waiting for an animation callback ({@code Actor.java:293-321}); null means turns were
     * ending and being picked normally and the hero simply never became ready.
     */
    private String diagnose(int frameBudget) {
        SceneStepper stepper = scene.stepper();
        Actor current = stepper.currentActor();
        StringBuilder out = new StringBuilder("no Input wait within ").append(frameBudget)
                .append(" frames of ").append(SceneStepper.FRAME).append(" s. ");
        if (current == null) {
            out.append("No actor is mid-turn: the last actor processed ended its turn with next(), the loop kept"
                    + " picking actors, and the hero never became ready. Next due: ").append(nextDue()).append('.');
        } else if (stepper.parkedOnItsOwnMonitor()) {
            out.append("The last actor processed was ").append(SceneStepper.name(current))
                    .append(": its act() returned false and nothing has called next() since (Actor.java:293-321"
                            + " at the pinned tag), so the actor thread is parked between turns waiting for a"
                            + " callback that has not come.");
        } else {
            out.append("The last actor processed was ").append(SceneStepper.name(current))
                    .append(": the actor thread is waiting for a sprite to stop moving before its turn"
                            + " (Actor.java:274-282), and the frames stepped have not ended the movement.");
        }
        return out.append(' ').append(heroLine()).append(" Actor thread: ").append(stepper.describeActorThread())
                .toString();
    }

    /** The actor the loop would pick next: least cooldown, ties by id, which is the order they were added in. */
    private static String nextDue() {
        Actor next = null;
        for (Actor actor : Actor.all()) {
            if (next == null || actor.cooldown() < next.cooldown()
                    || (actor.cooldown() == next.cooldown() && actor.id() < next.id())) {
                next = actor;
            }
        }
        return next == null ? "no actor at all" : SceneStepper.name(next) + " in " + next.cooldown() + " turn(s)";
    }

    private static String heroLine() {
        Hero hero = Dungeon.hero;
        if (hero == null) {
            return "No hero.";
        }
        StringBuilder out = new StringBuilder("Hero: ").append(SceneStepper.name(hero))
                .append(", ready=").append(hero.ready)
                .append(", curAction=").append(hero.curAction == null ? "none"
                        : hero.curAction.getClass().getSimpleName() + " to " + hero.curAction.dst)
                .append(", resting=").append(hero.resting)
                .append(", paralysed=").append(hero.paralysed)
                .append(", buffs=[");
        String separator = "";
        for (Buff buff : hero.buffs()) {
            out.append(separator).append(buff.getClass().getSimpleName());
            separator = ", ";
        }
        return out.append("].").toString();
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 31_415_926L;
        Boot boot = boot();
        System.out.println("HeadlessDriver: booted libGDX " + boot.applicationType()
                + " backend for Shattered Pixel Dungeon " + boot.upstreamVersion());
        try (HeadlessDriver driver = start(seed, HeroClass.WARRIOR)) {
            Halt halt = driver.stepToInputWait();
            System.out.println("HeadlessDriver: seed " + seed + " (" + DungeonSeed.convertToCode(seed) + "), "
                    + HeroClass.WARRIOR.name() + ": " + halt.reason() + " after " + halt.framesStepped()
                    + " frame(s); hero at cell " + Dungeon.hero.pos + " on depth " + Dungeon.depth);
        }
        Gdx.app.exit();
    }
}
