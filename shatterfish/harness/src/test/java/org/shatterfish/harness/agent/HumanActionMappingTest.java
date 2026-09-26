package org.shatterfish.harness.agent;

import com.badlogic.gdx.Input;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.input.KeyEvent;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Point;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each kind of human input the recorder covers becomes the Action the executor would issue for it
 * (story 5.9), offered at its wait, with no {@code unsupported} record: the rest and wait buttons,
 * the search button, a talent point, an item's action with no target and with a cell target, a
 * window's own button tapped, and the back key on a message window.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HumanActionMappingTest {

    /** Plays one human turn from the first wait and returns the log's records. */
    private static List<RunLog> oneTurn(Path folder, Consumer<EmbeddedHost> before, Consumer<EmbeddedRun> turn)
            throws IOException {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 2_000);
            before.accept(host);
            assertTrue(host.untilOpen(20_000), "a wait opened");
            turn.accept(run);
            host.untilOpen(200_000);
        }
        return RunLogReader.of(HumanTurnReplayTest.only(folder)).records();
    }

    private static RunLog.Wait first(List<RunLog> records) {
        assertTrue(records.stream().noneMatch(RunLog.Unsupported.class::isInstance),
                "nothing unsupported: " + records.stream().filter(RunLog.Unsupported.class::isInstance).toList());
        return records.stream().filter(RunLog.Wait.class::isInstance).map(RunLog.Wait.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("no wait recorded"));
    }

    private static int index(Object item) {
        int i = 0;
        for (Object held : Dungeon.hero.belongings) {
            if (held == item) {
                return i;
            }
            i++;
        }
        throw new AssertionError("not held: " + item);
    }

    @Test
    @DisplayName("a tap on an adjacent heap, which the game reads as a pick-up there, is the executor's Step onto it")
    void adjacent_heap(@TempDir Path folder) throws IOException {
        // Seed 2000's Warrior begins beside the tome of dungeon mastery (the first real HUMAN launch,
        // where this tap was first recorded wrongly as a walk the executor could not make).
        List<RunLog> records;
        int[] heap = new int[1];
        try (EmbeddedHost host = new EmbeddedHost(2000L, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()), HumanTurnReplayTest.logging(folder), 2_000);
            assertTrue(host.untilOpen(20_000));
            for (int step : com.watabou.utils.PathFinder.NEIGHBOURS8) {
                if (Dungeon.level.heaps.get(Dungeon.hero.pos + step) != null) {
                    heap[0] = Dungeon.hero.pos + step;
                }
            }
            assertTrue(heap[0] > 0, "a heap beside the hero");
            ScriptedHuman.tapCell(heap[0]);
            host.untilOpen(20_000);
            records = RunLogReader.of(HumanTurnReplayTest.only(folder)).records();
        }
        assertEquals(new Action.Step(heap[0]), first(records).action());
    }

    @Test
    @DisplayName("the rest button is Rest(full)")
    void rest(@TempDir Path folder) throws IOException {
        assertEquals(new Action.Rest(true), first(oneTurn(folder, host -> { }, run -> ScriptedHuman.pressRest())).action());
    }

    @Test
    @DisplayName("the wait button is Wait, and the search button is Search")
    void wait_and_search(@TempDir Path folder) throws IOException {
        assertEquals(new Action.Wait(), first(oneTurn(folder.resolve("a"), host -> { }, run -> ScriptedHuman.pressWait())).action());
        assertEquals(new Action.Search(), first(oneTurn(folder.resolve("b"), host -> { }, run -> ScriptedHuman.pressSearch())).action());
    }

    @Test
    @DisplayName("a talent point is Talent by the name the hero section gives it")
    void talent(@TempDir Path folder) throws IOException {
        Talent[] chosen = new Talent[1];
        RunLog.Wait wait = first(oneTurn(folder,
                host -> Dungeon.hero.earnExp(Dungeon.hero.maxExp(), HumanActionMappingTest.class),
                run -> {
                    chosen[0] = Dungeon.hero.talents.get(0).keySet().iterator().next();
                    ScriptedHuman.upgrade(chosen[0]);
                }));
        assertEquals(new Action.Talent(chosen[0].title()), wait.action());
    }

    @Test
    @DisplayName("eating is UseItem on the pack's own reference to the food")
    void use_item(@TempDir Path folder) throws IOException {
        int[] at = new int[1];
        RunLog.Wait wait = first(oneTurn(folder, host -> { }, run -> {
            Food food = Dungeon.hero.belongings.getItem(Food.class);
            at[0] = index(food);
            ScriptedHuman.useItem(food, Food.AC_EAT);
        }));
        Action.UseItem use = (Action.UseItem) wait.action();
        assertEquals(at[0], use.item().index());
        assertEquals(Food.AC_EAT, use.action());
    }

    @Test
    @DisplayName("a throw answered with a cell is UseItemAt that cell")
    void use_item_at(@TempDir Path folder) throws IOException {
        int[] target = new int[1];
        RunLog.Wait wait = first(oneTurn(folder, host -> { }, run -> {
            ThrowingStone stones = Dungeon.hero.belongings.getItem(ThrowingStone.class);
            target[0] = Dungeon.hero.pos;
            ScriptedHuman.useItem(stones, "THROW");
            ScriptedHuman.tapCell(target[0]);
        }));
        Action.UseItemAt use = (Action.UseItemAt) wait.action();
        assertEquals("THROW", use.action());
        assertEquals(target[0], use.cell());
    }

    @Test
    @DisplayName("a tap on a window's own button is AnswerPrompt with the executor's index for it")
    void answer_prompt(@TempDir Path folder) throws IOException {
        RunLog.Wait wait = first(oneTurn(folder,
                host -> GameScene.show(new WndOptions("A question", "Which?", "first", "second")),
                run -> {
                    Component second = ActionExecutor.optionButtons(Windows.front()).get(1);
                    Camera camera = second.camera();
                    Point screen = camera.cameraToScreen(second.left() + second.width() / 2,
                            second.top() + second.height() / 2);
                    // The input lock's view of the tap first, then the game's, as the input handler posts it.
                    run.pointerUp(screen.x, screen.y);
                    PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
                    PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
                }));
        assertEquals(new Action.AnswerPrompt(1), wait.action());
    }

    @Test
    @DisplayName("a button of a window the person opened at the wait (the journal, say) is not a Prompt answer")
    void a_window_of_the_persons_own(@TempDir Path folder) throws IOException {
        List<RunLog> records = oneTurn(folder, host -> { }, run -> {
            // Opened by the person at an open wait, as the journal button opens the journal.
            GameScene.show(new WndOptions("Journal", "Pages", "first", "second"));
            Component first = ActionExecutor.optionButtons(Windows.front()).get(0);
            Point screen = first.camera().cameraToScreen(first.left() + first.width() / 2, first.top() + first.height() / 2);
            run.pointerUp(screen.x, screen.y);
            PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
            PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
            ScriptedHuman.pressWait();
        });
        assertEquals(new Action.Wait(), first(records).action(), "the Action is the wait button, not the window's");
    }

    @Test
    @DisplayName("the back key on the message the surface stairs show is DismissPrompt")
    void dismiss_prompt(@TempDir Path folder) throws IOException {
        List<RunLog> records;
        // The desktop game loads its key bindings when it is created (core/.../ShatteredPixelDungeon.java:63);
        // the headless boot does not, so the defaults are put in for this test and taken out after it.
        java.util.LinkedHashMap<Integer, com.watabou.input.GameAction> saved = com.watabou.input.KeyBindings.getAllBindings();
        com.watabou.input.KeyBindings.setAllBindings(com.shatteredpixel.shatteredpixeldungeon.SPDAction.getDefaults());
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 2_000);
            assertTrue(host.untilOpen(20_000));
            Observation first = run.snapshot().observation();
            // The hero begins on the surface stairs; a tap there climbs, and the sewers answer with a message.
            ScriptedHuman.tapCell(first.hero().cell());
            assertTrue(host.untilOpen(20_000), "the message's wait opened");
            assertNotNull(Windows.front(), "a window is in front");
            run.keyDown(Input.Keys.ESCAPE);
            KeyEvent.addKeyEvent(new KeyEvent(Input.Keys.ESCAPE, true));
            KeyEvent.addKeyEvent(new KeyEvent(Input.Keys.ESCAPE, false));
            host.untilOpen(20_000);
            records = RunLogReader.of(HumanTurnReplayTest.only(folder)).records();
        } finally {
            com.watabou.input.KeyBindings.setAllBindings(saved);
        }
        List<Action> taken = records.stream().filter(RunLog.Wait.class::isInstance)
                .map(r -> ((RunLog.Wait) r).action()).toList();
        assertTrue(taken.size() >= 2, "two waits taken: " + records);
        assertEquals(List.of(new Action.Ascend(), new Action.DismissPrompt()), taken.subList(0, 2));
        assertTrue(records.stream().anyMatch(r -> r instanceof RunLog.Prompt prompt
                && prompt.answer().equals(new Action.DismissPrompt())), "the Prompt record beside it");
        assertTrue(records.stream().noneMatch(RunLog.Unsupported.class::isInstance));
    }
}
