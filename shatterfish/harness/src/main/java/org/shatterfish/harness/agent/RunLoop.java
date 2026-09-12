package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
import com.watabou.noosa.Scene;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Plays one Run from its first Input wait to its ending. This is the loop above the driver: the
 * driver reports what happened and stops, and this decides what the report means for the Run
 * (ADR-0015). The rig will grow its own loop here later, and a Replay will grow another; what they
 * share is this shape, so the shape is worth being plain about.
 *
 * <p>Each wait is the same five steps in the same order: observe, choose, execute, count, step. A
 * refusal is counted and the wait is served again, because a refusal changes nothing and the screen
 * is still asking. Anything that is not a wait ends the Run with a named cause, including the things
 * that would otherwise be a hang.
 */
public final class RunLoop {

    /**
     * The turn cap, in the game's own turns. A Run still alive here is stopped and counted as a
     * loss with {@link RunOutcome.Cause#TURN_CAP} (epic 1's own number).
     */
    public static final int TURN_CAP = 20_000;

    /**
     * How many refusals in a row mean the Run is going nowhere. One or two are ordinary — the set
     * is a wait old in places — but a screen that refuses everything offered is a disagreement
     * between {@code ValidActions} and the game, and that is worth a story rather than a loop.
     */
    private static final int REFUSALS_IN_A_ROW = 64;

    /** Frames the driver may spend on a single wait before the Run is called stuck. */
    private static final int FRAME_BUDGET = 20_000;

    private final ActionExecutor executor = new ActionExecutor();

    /**
     * Plays a Run of {@code heroClass} on {@code seed}, choosing at random from {@code agentSeed},
     * and says how it ended.
     */
    public RunOutcome play(long seed, HeroClass heroClass, long agentSeed) {
        return play(seed, heroClass, new RandomAgent(agentSeed), TURN_CAP);
    }

    /**
     * Plays a Run with a chooser of the caller's own. The Run's resources go with it: the driver is
     * closed whatever happens.
     */
    public RunOutcome play(long seed, HeroClass heroClass, Decider agent) {
        return play(seed, heroClass, agent, TURN_CAP);
    }

    /**
     * Plays a Run that stops at {@code turnCap} turns rather than the story's own cap. The cap is a
     * number and the behaviour at it is a rule; a test that wants the rule should not have to spend
     * twenty thousand turns reaching it, and a Run that wants the rule gets {@link #TURN_CAP}.
     */
    public RunOutcome play(long seed, HeroClass heroClass, Decider agent, int turnCap) {
        HeadlessDriver driver = HeadlessDriver.start(seed, heroClass);
        long waits = 0;
        long applied = 0;
        long refused = 0;
        int refusalsInARow = 0;
        String lastRefusal = "";
        // What the Run last did, which is the first thing anyone asks when a Run stops moving.
        Action lastAction = null;
        try {
            while (true) {
                HeadlessDriver.Halt halt;
                try {
                    halt = driver.stepToInputWait(FRAME_BUDGET);
                } catch (HeadlessDriver.Stalled stalled) {
                    return outcome(RunOutcome.Cause.UNKNOWN_WINDOW, waits, applied, refused,
                            describeWindow() + ", after " + lastAction
                                    + (lastRefusal.isEmpty() ? "" : ", last refusal " + lastRefusal));
                }
                switch (halt.reason()) {
                    case HERO_DEAD -> {
                        return outcome(RunOutcome.Cause.DEATH, waits, applied, refused, "");
                    }
                    case SCENE_SWITCH -> {
                        RunOutcome ending = serve(driver, halt, waits, applied, refused);
                        if (ending != null) {
                            return ending;
                        }
                        continue;
                    }
                    default -> {
                        // An Input wait: the only case with an Action in it.
                    }
                }
                if (turns() >= turnCap) {
                    return outcome(RunOutcome.Cause.TURN_CAP, waits, applied, refused, "");
                }

                Observation observation = new Observer().observe();
                Action chosen = agent.decide(observation);
                if (chosen == null) {
                    return outcome(RunOutcome.Cause.NOTHING_OFFERED, waits, applied, refused,
                            "at wait " + halt.waitIndex());
                }
                waits++;
                lastAction = chosen;
                Outcome outcome = executor.execute(observation, chosen);
                if (outcome instanceof Outcome.Rejected rejected) {
                    refused++;
                    refusalsInARow++;
                    lastRefusal = rejected.reason() + ": " + rejected.detail();
                    if (refusalsInARow >= REFUSALS_IN_A_ROW) {
                        return outcome(RunOutcome.Cause.REFUSED, waits, applied, refused, lastRefusal);
                    }
                } else {
                    applied++;
                    refusalsInARow = 0;
                }
            }
        } finally {
            driver.close();
        }
    }

    /**
     * Serves a scene the game asked for, or ends the Run. A Run ends here when the game asks for
     * the surface, which is the win ({@code core/.../levels/SewerLevel.java:157-165}); it goes on
     * when the game asks for the scene between two floors, which this does the work of rather than
     * running, the way {@code HeadlessDriver.newGame} does the work of the scene that starts a game.
     *
     * @return the Run's ending, or null when the Run goes on
     */
    private RunOutcome serve(HeadlessDriver driver, HeadlessDriver.Halt halt, long waits,
                             long applied, long refused) {
        Class<? extends Scene> asked = halt.requestedScene();
        if (asked == SurfaceScene.class) {
            return outcome(RunOutcome.Cause.WIN, waits, applied, refused, "");
        }
        if (asked != InterlevelScene.class) {
            return outcome(RunOutcome.Cause.UNSERVED_SCENE, waits, applied, refused,
                    asked == null ? "no scene named" : asked.getSimpleName());
        }
        InterlevelScene.Mode mode = InterlevelScene.mode;
        switch (mode) {
            case DESCEND, ASCEND, FALL -> driver.serveSceneSwitch(() -> crossFloor(mode));
            default -> {
                return outcome(RunOutcome.Cause.UNSERVED_SCENE, waits, applied, refused,
                        "the interlevel scene in mode " + mode);
            }
        }
        return null;
    }

    /**
     * The body of the interlevel scene for one floor change, with no scene and no actor thread.
     *
     * <p>Each branch is the game's own, read at {@code v4.0.0} and reproduced rather than called,
     * because the scene's copies are private and the scene itself is a fade, a thread and a
     * progress bar — none of which a headless Run has. A descent is
     * {@code core/.../scenes/InterlevelScene.java:649-670}, an ascent {@code :693-716} and a fall
     * {@code :676-691}; the level is loaded when it has been generated before, so climbing back
     * down finds the floor the hero left rather than a new one.
     */
    private static void crossFloor(InterlevelScene.Mode mode) {
        try {
            LevelTransition transition = InterlevelScene.curTransition;
            if (mode == InterlevelScene.Mode.FALL) {
                Mob.holdAllies(Dungeon.level);
                Buff.affect(Dungeon.hero, Chasm.Falling.class);
                Dungeon.saveAll();
                Dungeon.depth++;
                Level level = generated() ? Dungeon.loadLevel(GamesInProgress.curSlot) : Dungeon.newLevel();
                Dungeon.switchLevel(level, level.fallCell(InterlevelScene.fallIntoPit));
                return;
            }
            if (transition == null) {
                throw new IllegalStateException("the game asked for " + mode + " with no transition to take");
            }
            // The guard the game carries on both sides: allies are not held into the city's quest
            // area (…/scenes/InterlevelScene.java:650-655, :694-699), which upstream writes as an
            // empty branch under a FIXME. Holding them there would be a different game, which is
            // the one thing a mirrored body must not become; the review of story 1.14 found it
            // missing here.
            boolean intoTheCityQuestArea = transition.destBranch != Dungeon.branch
                    && Dungeon.depth >= 16 && Dungeon.depth <= 20;
            if (!intoTheCityQuestArea) {
                Mob.holdAllies(Dungeon.level);
            }
            Dungeon.saveAll();
            Dungeon.depth = transition.destDepth;
            Dungeon.branch = transition.destBranch;
            Level level = generated() ? Dungeon.loadLevel(GamesInProgress.curSlot) : Dungeon.newLevel();
            LevelTransition arrival = level.getTransition(transition.destType);
            InterlevelScene.curTransition = null;
            if (arrival == null) {
                // Upstream dereferences this without asking (…/scenes/InterlevelScene.java:670,
                // :714), so a floor with no way in is a case the game does not have and this must
                // not invent one for: dropping the hero wherever the no-position path puts them
                // would turn a loud failure into a quiet wrong arrival.
                throw new IllegalStateException("floor " + Dungeon.depth + " has no "
                        + transition.destType + " for the hero to arrive at");
            }
            Dungeon.switchLevel(level, arrival.cell());
        } catch (IOException e) {
            throw new UncheckedIOException("the floor the game asked for could not be loaded", e);
        }
    }

    private static boolean generated() {
        return Dungeon.levelHasBeenGenerated(Dungeon.depth, Dungeon.branch);
    }

    /**
     * The turns this Run has passed, as the game itself counts them for the duration it shows a
     * player: the time actors have spent plus the time of the turn in progress
     * ({@code core/.../actors/Actor.java:196}; {@code core/.../ui/QuickSlotButton.java:278}).
     */
    public static int turns() {
        return (int) (Statistics.duration + Actor.now());
    }

    private static String describeWindow() {
        return Windows.front() == null
                ? "no window in front, and no wait either"
                : Windows.front().getClass().getSimpleName();
    }

    private static RunOutcome outcome(RunOutcome.Cause cause, long waits, long applied, long refused,
                                      String detail) {
        return new RunOutcome(cause, Statistics.deepestFloor, turns(), waits, applied, refused, detail);
    }
}
