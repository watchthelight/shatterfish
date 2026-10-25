package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.input.GameAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.log.RunLogReader;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A person's click and held direction key do nothing while the input gate is shut (story 5.5, ADR-0013
 * option 9, hook row 9): the Run log gains no Action, and the hero has not moved, been made busy, or been
 * forced ready by a key. The same two inputs, with the gate open, do move the hero, which is what makes
 * the shut case a test of the gate and not of an input that never worked.
 *
 * <p>The quickslot, the toolbar and the inventory pane never reach the selector; a mouse or a key reaches
 * them only through the Overlay's {@code InputLock}, whose own test ({@code InputLockTest}) presses the
 * quickslot keys. The gate is shut for a HUMAN Run here by {@link EmbeddedRun#humanInput}, the switch
 * story 5.6's Pause will turn.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class PausedInputTest {

    /** A passable cell next to the hero, with the direction action that walks to it. */
    private record Neighbour(int cell, GameAction action) {
    }

    private static Neighbour neighbour() {
        int width = Dungeon.level.width();
        int hero = Dungeon.hero.pos;
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        GameAction[] actions = {SPDAction.N, SPDAction.E, SPDAction.S, SPDAction.W};
        for (int i = 0; i < steps.length; i++) {
            int cell = hero + steps[i][0] + steps[i][1] * width;
            if (Dungeon.level.passable[cell] && Actor.findChar(cell) == null
                    && Dungeon.level.heaps.get(cell) == null && Dungeon.level.getTransition(cell) == null) {
                return new Neighbour(cell, actions[i]);
            }
        }
        throw new AssertionError("no free neighbouring cell");
    }

    private static CellSelector selector() throws ReflectiveOperationException {
        Field field = GameScene.class.getDeclaredField("cellSelector");
        field.setAccessible(true);
        return (CellSelector) field.get(null);
    }

    private static void hold(CellSelector selector, GameAction action) throws ReflectiveOperationException {
        Field held = CellSelector.class.getDeclaredField("heldAction1");
        held.setAccessible(true);
        held.set(selector, action);
        Field delay = CellSelector.class.getDeclaredField("heldDelay");
        delay.setAccessible(true);
        delay.setFloat(selector, 0f);
    }

    private static GameAction held(CellSelector selector) throws ReflectiveOperationException {
        Field held = CellSelector.class.getDeclaredField("heldAction1");
        held.setAccessible(true);
        return (GameAction) held.get(selector);
    }

    @Test
    @DisplayName("shut: a click and a held direction key leave the log without an Action and the hero where it was")
    void paused(@TempDir Path folder) throws Exception {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 12);
            assertTrue(host.untilOpen(20_000));
            assertTrue(run.humanInput(), "a HUMAN Run opens the gate at attach");
            run.humanInput(false);

            int at = Dungeon.hero.pos;
            Neighbour next = neighbour();

            ScriptedHuman.tapCell(next.cell());
            assertEquals(at, Dungeon.hero.pos, "the click did not move the hero");
            assertNull(Dungeon.hero.curAction, "the click chose no action for the hero");
            assertTrue(Dungeon.hero.ready, "the hero is still waiting");

            CellSelector selector = selector();
            hold(selector, next.action());
            selector.processKeyHold();
            assertEquals(at, Dungeon.hero.pos, "the held key did not move the hero");
            assertNull(Dungeon.hero.curAction, "the held key chose no action for the hero");
            assertTrue(Dungeon.hero.ready, "the hero is still waiting");
            assertEquals(SPDAction.NONE, held(selector), "a key still down when the gate opens must not walk the hero");

            // A key released quickly, or a stick let go, moves through moveFromActions without processKeyHold.
            java.lang.reflect.Method move = CellSelector.class.getDeclaredMethod("moveFromActions", GameAction[].class);
            move.setAccessible(true);
            assertFalse((Boolean) move.invoke(selector, (Object) new GameAction[] {next.action()}),
                    "a key-release move does nothing while the gate is shut");
            assertEquals(at, Dungeon.hero.pos, "the released key did not move the hero");
            assertNull(Dungeon.hero.curAction);

            // The wait is still open and nothing was recorded for it: the next frames end it only by the
            // turn cap, never by an Action nobody made.
            assertTrue(run.inputOpen());
            host.frame();
            host.frame();
            assertEquals(at, Dungeon.hero.pos);
            assertTrue(run.inputOpen(), "the wait is still open: no input was recorded");
        }
        RunLogReader.Log read = RunLogReader.of(HumanTurnReplayTest.only(folder));
        assertTrue(read.waits().isEmpty(), "the Run log holds no Action");
    }

    @Test
    @DisplayName("open: the same click and the same held key do move the hero, and the log holds the Action")
    void open_again(@TempDir Path folder) throws Exception {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 12);
            assertTrue(host.untilOpen(20_000));
            int at = Dungeon.hero.pos;
            Neighbour next = neighbour();

            // Shut, then open: the gate is a switch and not a one-way door.
            run.humanInput(false);
            ScriptedHuman.tapCell(next.cell());
            assertEquals(at, Dungeon.hero.pos);
            run.humanInput(true);
            ScriptedHuman.tapCell(next.cell());
            assertTrue(host.untilOpen(20_000));
            assertEquals(next.cell(), Dungeon.hero.pos, "an open gate leaves the click as the game has it");

            // A held key, gate open: the game's own key-hold path moves the hero from where it now is.
            Neighbour onward = neighbour();
            CellSelector selector = selector();
            hold(selector, onward.action());
            selector.processKeyHold();
            assertNotEquals(SPDAction.NONE, held(selector), "an open gate leaves the held key held");
            assertFalse(Dungeon.hero.ready, "the key started a move");
            assertTrue(host.untilOpen(20_000));
            assertEquals(onward.cell(), Dungeon.hero.pos);
        }
        RunLogReader.Log read = RunLogReader.of(HumanTurnReplayTest.only(folder));
        assertEquals(2, read.waits().size(), "the click and the key are the log's two Actions");
    }

    @Test
    @DisplayName("a targeted item use (a throw at a cell) completes through a shut gate: the cell reaches select inside the executor's call")
    void a_targeted_use_completes(@TempDir Path folder) throws Exception {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 12);
            assertTrue(host.untilOpen(20_000));
            run.humanInput(false);
            org.shatterfish.api.Observation observation = new org.shatterfish.harness.observer.Observer().observe();
            org.shatterfish.api.Action.UseItemAt throwing = null;
            for (org.shatterfish.api.Action action : observation.actions().actions()) {
                if (action instanceof org.shatterfish.api.Action.UseItemAt use && use.action().equals("THROW")) {
                    throwing = use;
                    break;
                }
            }
            org.junit.jupiter.api.Assertions.assertNotNull(throwing, "the pack offers a throw at a cell");
            // The item opens the cell selector and the executor answers it in the same call, on this thread.
            org.shatterfish.harness.executor.Outcome outcome =
                    new org.shatterfish.harness.executor.ActionExecutor().execute(observation, throwing);
            assertTrue(outcome instanceof org.shatterfish.harness.executor.Outcome.Applied,
                    "the executor's own selection of the cell went through: " + outcome);
            assertFalse(Dungeon.hero.ready, "the throw took the hero's turn");
        }
    }

    @Test
    @DisplayName("a headless Run starts with no gate registered, whatever an earlier Run left behind")
    void a_headless_run_clears_a_leftover_gate(@TempDir Path folder) throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.inputGate = () -> false;
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            assertTrue(host.driver != null);
            assertNull(com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.inputGate,
                    "starting the driver cleared the leftover gate");
        }
        assertNull(com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.inputGate);
    }

    @Test
    @DisplayName("a Run that is not the person's has the gate shut, and the Run's own executor still gets through")
    void the_executor_passes(@TempDir Path folder) throws Exception {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attach(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 40);
            assertFalse(run.humanInput(), "RUNNING shuts a person's input out of the hero");
            while (host.frame() != EmbeddedRun.State.ENDED) {
                // The Brain plays through ActionExecutor.execute, which reaches CellSelector.select by
                // handleCell: with the gate shut it must be let through, or the Run never leaves its first wait.
            }
            assertTrue(run.waitIndex() > 1, "the Brain's own Actions went through a shut gate");
        }
    }
}
