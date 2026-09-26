package org.shatterfish.overlay;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.BoundedLog;
import org.shatterfish.harness.agent.EmbeddedRun;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a HUMAN Run (story 5.9) shows and lets through, without booting the game: the launcher's flag,
 * the Mode strip's word, the Decision card's shadow, the Decision log's lines, and the input lock's
 * rule that a press passes only while a wait is open and a release always does.
 */
class HumanModeContentTest {

    private static RunLog.Decision decision() {
        return new RunLog.Decision("explore: floor", new RunLog.Choice(new Action.Step(12), 10_000, "frontier"),
                List.of(new RunLog.Choice(new Action.Search(), 1_111, "uniform")), List.of(), "explore");
    }

    @Test
    @DisplayName("--agent human is the person's Run, logged as human, shadowed by the Brain")
    void the_flag() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--seed", "2000", "--class", "warrior", "--agent", "human"});
        assertTrue(options.human());
        assertEquals("human", OverlayAgents.name(options));
        assertFalse(LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--agent", "brain"}).human());
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--agent", "people"}));
    }

    @Test
    @DisplayName("the Mode strip says HUMAN in the human colour for a person's Run, and RUNNING for a Brain's")
    void the_strip() {
        EmbeddedRun.Snapshot human = new EmbeddedRun.Snapshot(decision(), 7, 1, null, EmbeddedRun.State.PLAYING, null,
                List.of(), new EmbeddedRun.Human(true, 2, true, 0, "", 0));
        ModeState mode = ModeState.of(human);
        assertEquals(ModeState.Mode.HUMAN, mode.mode());
        assertTrue(ModeStripContent.text(mode).startsWith("HUMAN  player  turn"), ModeStripContent.text(mode));
        assertEquals(ModeStripContent.HUMAN_COLOR, ModeStripContent.color(mode.mode()));
        assertEquals(ModeState.Mode.RUNNING,
                ModeState.of(new EmbeddedRun.Snapshot(decision(), 7, 1, null, EmbeddedRun.State.PLAYING)).mode());
    }

    @Test
    @DisplayName("the card: a shadow's headline says it was not executed, its rows are the Decision's, and the Replay notice names the wait")
    void the_card() {
        DecisionCardContent.Content now = DecisionCardContent.of(decision(), false, false,
                new DecisionCardContent.Shadow(true, 0, ""));
        assertTrue(now.shadow());
        assertEquals(DecisionCardContent.SHADOW_NOW, now.headline());
        assertEquals(new Action.Step(12), now.chosen().action());
        assertNull(now.notice());
        DecisionCardContent.Content late = DecisionCardContent.of(decision(), true, false,
                new DecisionCardContent.Shadow(false, 4, "a click on cell 9"));
        assertEquals(DecisionCardContent.SHADOW_LAST, late.headline(), "a shadow is never a next press");
        assertEquals(DecisionCardContent.UNVERIFIABLE + "4: a click on cell 9", late.notice());
        DecisionCardContent.Content brain = DecisionCardContent.of(decision(), false, false);
        assertFalse(brain.shadow());
        assertNull(brain.headline());
    }

    @Test
    @DisplayName("the Decision log: the person's wait says human, a shadow is greyed and says late when it was, a note and a Replay notice have lines")
    void the_log() {
        RunLog.Wait wait = new RunLog.Wait(1, 3_000, 1, 0, "0".repeat(64), java.util.Map.of("map", "1".repeat(64)),
                new Action.Search(), true, RunLog.HUMAN, null, "", List.of(), 0);
        List<BoundedLog.Entry> history = List.of(
                new BoundedLog.Entry(new RunLog.Mode(0, "HUMAN", EmbeddedRun.HUMAN_SPEED), null),
                new BoundedLog.Entry(new RunLog.Shadow(1, decision()), null),
                new BoundedLog.Entry(wait, null),
                new BoundedLog.Entry(new RunLog.Shadow(2, decision(), true), null),
                new BoundedLog.Entry(new RunLog.Note(2, "wanted the door"), null),
                new BoundedLog.Entry(new RunLog.Unsupported(3, "a click on cell 9"), null));
        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);
        List<String> text = new ArrayList<>();
        lines.forEach(line -> text.add(line.text()));
        assertEquals("mode: HUMAN player", text.get(0));
        assertTrue(lines.get(1).greyed() && text.get(1).startsWith("wait 1  shadow  "), text.get(1));
        assertTrue(text.get(2).contains("  human  "), text.get(2));
        assertFalse(lines.get(2).greyed());
        assertTrue(lines.get(3).greyed() && text.get(3).endsWith("late"), text.get(3));
        assertEquals("note: wanted the door", text.get(4));
        assertEquals(DecisionCardContent.UNVERIFIABLE + "3: a click on cell 9", text.get(5));
    }

    @Test
    @DisplayName("the lock of a person's Run: presses pass only while open, releases always, the notes key never reaches the game")
    void the_lock() {
        AtomicInteger reached = new AtomicInteger();
        List<String> heard = new ArrayList<>();
        InputAdapter game = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }
        };
        InputLock lock = new InputLock();
        lock.human(new InputLock.Human() {
            @Override
            public void pointerUp(int screenX, int screenY) {
                heard.add("up " + screenX + "," + screenY);
            }

            @Override
            public void keyDown(int keycode) {
                heard.add("key " + keycode);
            }

            @Override
            public void input() {
                heard.add("input");
            }

            @Override
            public boolean overlayKey(int keycode) {
                return keycode == OverlayGame.NOTE_KEY;
            }
        });
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(game);
        multiplexer.addProcessor(0, lock);

        lock.lock();
        multiplexer.keyDown(Input.Keys.W);
        multiplexer.touchDown(5, 5, 0, 0);
        multiplexer.touchUp(5, 5, 0, 0);
        assertEquals(0, reached.get(), "between waits no press reaches the game, nor the release of one it never had");
        multiplexer.keyUp(Input.Keys.W);
        assertEquals(1, reached.get(), "a release always does, so no key is left held in the game");
        assertTrue(heard.isEmpty(), "and nothing held back is told to the Run");

        lock.unlock();
        multiplexer.touchDown(8, 9, 0, 0);
        lock.lock();
        multiplexer.touchUp(8, 9, 0, 0);
        assertEquals(3, reached.get(), "a tap begun while open ends in the game, whatever the lock says since");
        assertTrue(heard.contains("up 8,9"), "and the Run reads the tap where it was released: " + heard);

        lock.unlock();
        multiplexer.keyDown(Input.Keys.S);
        assertTrue(heard.contains("key " + Input.Keys.S));
        int before = reached.get();
        multiplexer.keyDown(OverlayGame.NOTE_KEY);
        multiplexer.keyUp(OverlayGame.NOTE_KEY);
        assertEquals(before, reached.get(), "the notes key is the Overlay's, press and release");
    }
}
