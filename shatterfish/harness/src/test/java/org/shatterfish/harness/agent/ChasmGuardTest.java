package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.utils.PathFinder;
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

    /** Plays a Run whose Brain jumps, at {@code frameTime} a frame (0: the headless fifth), until it falls. */
    private static void jumps(float frameTime, boolean held) {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            int chasm = placeAChasmBeside(Dungeon.hero);
            List<Action> decided = Collections.synchronizedList(new ArrayList<>());
            Decider brain = observation -> {
                Action action = observation.prompt().kind() == PromptKind.CHASM_JUMP
                        ? new Action.AnswerPrompt(0) : new Action.Step(chasm);
                decided.add(action);
                return action;
            };
            host.frameTime = frameTime;
            // The desktop draws its frames on while the Brain thinks and while an answer is held; the
            // headless loop's timing is the decision within its frame.
            host.stepWhileThinking = held;
            EmbeddedRun run = host.attach(brain, null, 5_000);
            for (int frame = 0; frame < 20_000 && run.state() != EmbeddedRun.State.ENDED && Dungeon.depth == 1; frame++) {
                host.frame();
            }
            assertEquals(2, Dungeon.depth, "the hero jumped: " + run.outcome() + " after " + decided);
            assertEquals(List.of(new Action.Step(chasm), new Action.AnswerPrompt(0)), decided.subList(0, 2),
                    "a Step onto the chasm, then the answer");
            assertEquals(1, decided.stream().filter(action -> action instanceof Action.AnswerPrompt).count(),
                    "one answer, taken the first time: no tap was lost to the window's guard " + decided);
            if (held) {
                assertTrue(run.heldFrames() > 0, "the answer was held until the window took input");
            } else {
                assertEquals(0, run.heldFrames(), "at the headless fifth of a second nothing is held");
            }
        }
    }

    @Test
    @DisplayName("at the desktop's frame time, the answer to the chasm is held until the window takes it, and taken once")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_answer_waits_for_the_guard() {
        jumps(1 / 60f, true);
    }

    @Test
    @DisplayName("at the headless frame time nothing is held, so the embedded Run is still the headless loop's")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void nothing_is_held_headlessly() {
        jumps(0f, false);
    }
}
