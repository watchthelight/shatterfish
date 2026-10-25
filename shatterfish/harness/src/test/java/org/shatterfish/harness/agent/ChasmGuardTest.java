package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import com.watabou.utils.PathFinder;
import org.shatterfish.harness.driver.Windows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.PromptKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chasm's confirmation ignores every tap until it has been open a fifth of a second of game time
 * ({@code core/.../levels/features/Chasm.java:69-87}), and the embedded Run holds a Prompt's answer until
 * then (issue #170). At the desktop's sixtieth of a second a frame, an answer tapped at once was lost:
 * the window stayed, and the Brain answered it again, each answer a wait with no turn passed. At the
 * headless loop's fifth of a second the window has been open long enough by the time its wait is
 * confirmed, and nothing is held, so the Run is still the headless loop's.
 *
 * <p>The Brain here steps onto the chasm and answers "Yes" on purpose: the test is of the tap, not of
 * the Shatterfish Brain, which neither steps onto a chasm nor jumps (issue #170's other two parts).
 */
class ChasmGuardTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;

    @AfterEach
    void theLoadingSceneModeBack() {
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
    }

    /** Makes a cell beside the hero a chasm, as the game changes terrain in play, and returns it. */
    static int placeAChasmBeside(Hero hero) {
        Level level = Dungeon.level;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (cell >= 0 && cell < level.length() && level.passable[cell] && !level.pit[cell]
                    && Actor.findChar(cell) == null && level.heaps.get(cell) == null
                    && level.plants.get(cell) == null && level.getTransition(cell) == null) {
                Level.set(cell, Terrain.CHASM);
                GameScene.updateMap(cell);
                return cell;
            }
        }
        throw new IllegalStateException("no cell beside the hero could become a chasm");
    }

    /**
     * Plays a Run whose Brain jumps, at {@code frameTime} a frame (0: the headless fifth), until it falls, and
     * returns the frames its answer was held; {@code stallMillis} is how long the worker takes to decide each
     * question, the scheduling of a loaded machine made explicit.
     */
    private static long jumps(float frameTime, boolean held, long stallMillis) {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            int chasm = placeAChasmBeside(Dungeon.hero);
            List<Action> decided = Collections.synchronizedList(new ArrayList<>());
            Decider brain = observation -> {
                stall(stallMillis);
                Action action = observation.prompt().kind() == PromptKind.CHASM_JUMP
                        ? new Action.AnswerPrompt(0) : new Action.Step(chasm);
                decided.add(action);
                return action;
            };
            host.frameTime = frameTime;
            // The desktop draws its frames on while an answer is held; the headless loop's timing is the
            // decision within its frame. The host waits for the worker before every frame that follows a
            // question, so the answer is ready on the window's first frame however slowly the worker is
            // scheduled (a worker slower than the window's fifth of a second let the guard open first and
            // left nothing held, #193), and the game takes the same single step before the Run serves or
            // holds it (a worker finishing between the host's look and the Run's own took that step or not
            // by chance, which moved the count of frames held).
            host.stepWhileThinking = held;
            host.waitForTheWorker = held;
            StringBuilder trace = new StringBuilder("frames ");
            EmbeddedRun run = host.attach(brain, null, 5_000);
            // Only frames that step the game count against the bound; waiting on the worker is the timeout's.
            for (int frame = 0; frame < 20_000 && run.state() != EmbeddedRun.State.ENDED && Dungeon.depth == 1; ) {
                boolean waiting = host.waitingOnTheBrain();
                EmbeddedRun.State state = host.frame();
                if (!waiting) {
                    trace.append(frame).append(':').append(state).append(" t=").append(Game.timeTotal)
                            .append(" held=").append(run.heldFrames()).append(" front=")
                            .append(Windows.front() == null ? "none" : Windows.front().getClass().getSimpleName())
                            .append("; ");
                }
                if (waiting) {
                    Thread.onSpinWait();
                } else {
                    frame++;
                }
            }
            // The worker may already be deciding the first wait of the floor below: a copy, taken under the list's lock.
            List<Action> seen;
            synchronized (decided) {
                seen = new ArrayList<>(decided);
            }
            assertEquals(2, Dungeon.depth, "the hero jumped: " + run.outcome() + " after " + seen);
            assertEquals(List.of(new Action.Step(chasm), new Action.AnswerPrompt(0)), seen.subList(0, 2),
                    "a Step onto the chasm, then the answer");
            assertEquals(1, seen.stream().filter(action -> action instanceof Action.AnswerPrompt).count(),
                    "one answer, taken the first time: no tap was lost to the window's guard " + seen);
            if (held) {
                assertTrue(run.heldFrames() > 0, "the answer was held until the window took input: " + trace);
            } else {
                assertEquals(0, run.heldFrames(), "at the headless fifth of a second nothing is held");
            }
            return run.heldFrames();
        }
    }

    private static void stall(long millis) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("at the desktop's frame time, the answer to the chasm is held until the window takes it, and taken once")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_answer_waits_for_the_guard() {
        jumps(1 / 60f, true, 0);
    }

    @Test
    @DisplayName("how long the answer is held does not depend on how slowly the worker decides")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_hold_does_not_depend_on_the_worker() {
        long quick = jumps(1 / 60f, true, 0);
        for (long stall : new long[] {1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8, 40, 250}) {
            assertEquals(quick, jumps(1 / 60f, true, stall), "frames held with a worker " + stall + " ms a question");
        }
    }

    @Test
    @DisplayName("at the headless frame time nothing is held, so the embedded Run is still the headless loop's")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void nothing_is_held_headlessly() {
        jumps(0f, false, 0);
    }
}
