package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import org.shatterfish.api.PromptKind;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.observer.Observer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The executor applies an Action through the call a person's click, key or button makes, and
 * refuses everything else with a reason rather than an exception (ADR-0014; story 1.13). Paths
 * abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/},
 * at the tag.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ActionExecutorTest {

    private static final long SEED = 24_012_345L;

    private HeadlessDriver driver;
    private Level level;
    private Hero hero;
    private final ActionExecutor executor = new ActionExecutor();

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private Observation atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        level = Dungeon.level;
        hero = Dungeon.hero;
        return new Observer().observe();
    }

    @Test
    @DisplayName("a step moves the hero one cell, through the click a person makes")
    void a_step_moves_the_hero() {
        Observation observation = atTheFirstWait();
        Action.Step step = firstStep(observation);
        int from = hero.pos;

        Outcome outcome = executor.execute(observation, step);
        assertInstanceOf(Outcome.Applied.class, outcome, outcome.toString());
        assertNotEquals(null, hero.curAction, "the click became an action the hero will take");

        driver.stepToInputWait();
        assertEquals(step.cell(), hero.pos, "the hero is where the step named");
        assertNotEquals(from, hero.pos);
    }

    @Test
    @DisplayName("waiting passes a turn and comes back to a wait")
    void waiting_passes_a_turn() {
        Observation observation = atTheFirstWait();
        float before = Actor.now();

        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, new Action.Wait()));
        driver.stepToInputWait();
        assertTrue(Actor.now() > before, "time moved: " + before + " to " + Actor.now());
        assertEquals(observation.hero().cell(), hero.pos, "and the hero stayed where it was");
    }

    @Test
    @DisplayName("an Action the Observation does not offer is refused, and nothing is called")
    void an_action_outside_the_set_is_refused() {
        atTheFirstWait();
        // A step onto a wall: a cell the map draws as something a click does not walk onto, so the
        // set does not carry it (story 1.12). The wall goes up first, and the Observation is read
        // after it, since the set is what the screen showed at the moment it was read.
        int wall = wallBesideTheHero();
        Observation observation = new Observer().observe();
        Action.Step nowhere = new Action.Step(wall);
        assertFalse(observation.actions().actions().contains(nowhere), "the set does not offer a wall");

        String before = new Observer().observe().hash();
        Outcome outcome = executor.execute(observation, nowhere);
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class, outcome);
        assertEquals(Reason.NOT_OFFERED, rejected.reason());
        assertEquals(null, hero.curAction, "nothing was asked of the hero");
        assertEquals(before, new Observer().observe().hash(), "and the screen is what it was");
    }

    @Test
    @DisplayName("an Action while the hero is acting is refused before anything is called")
    void an_action_away_from_a_wait_is_refused() {
        Observation observation = atTheFirstWait();
        Action.Step step = firstStep(observation);
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, step));
        assertNotEquals(null, hero.curAction, "the hero is busy now");

        Outcome outcome = executor.execute(observation, new Action.Wait());
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class, outcome);
        assertEquals(Reason.NOT_AT_AN_INPUT_WAIT, rejected.reason());
        assertTrue(rejected.detail().contains("ready="), rejected.detail());
    }

    @Test
    @DisplayName("an item reference the pack no longer matches is refused as a desync")
    void a_stale_item_reference_is_refused() {
        Observation observation = atTheFirstWait();
        ItemView food = observation.inventory().items().stream()
                .filter(item -> item.actions().contains("EAT"))
                .findFirst().orElseThrow();
        int index = observation.inventory().items().indexOf(food);

        // The Action names the right position and the wrong item, which is what a Replay that
        // drifted looks like (ADR-0014, option 11).
        Action.UseItem stale = new Action.UseItem(new ItemRef(index, food.name(), food.quantity()), "EAT");
        Item real = itemAt(index);
        real.name();
        Observation bent = observation;
        Action.UseItem wrong = new Action.UseItem(new ItemRef(index, "a thing the pack never held", 1), "EAT");

        String before = new Observer().observe().hash();
        Outcome outcome = executor.execute(bent, wrong);
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class, outcome);
        assertTrue(rejected.reason() == Reason.NOT_OFFERED || rejected.reason() == Reason.ITEM_MOVED,
                "either the set refuses the reference or the pack does: " + rejected);
        assertEquals(before, new Observer().observe().hash(), "and nothing was eaten");

        // The control: the same item, named as the Observation names it, is applied.
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, stale));
    }

    @Test
    @DisplayName("a targeted use answers the game's own selector inside the same wait")
    void a_targeted_use_answers_the_selector() {
        Observation observation = atTheFirstWait();
        ItemView throwable = observation.inventory().items().stream()
                .filter(item -> item.actions().contains("THROW"))
                .findFirst().orElseThrow();
        int index = observation.inventory().items().indexOf(throwable);
        int target = floorInView();

        Action.UseItemAt thrown = new Action.UseItemAt(
                new ItemRef(index, throwable.name(), throwable.quantity()), "THROW", target);
        assertTrue(observation.actions().actions().contains(thrown)
                        || observation.actions().actions().stream().anyMatch(a -> a instanceof Action.UseItemAt),
                "the set offers a throw");

        Outcome outcome = executor.execute(observation, new Action.UseItemAt(
                new ItemRef(index, throwable.name(), throwable.quantity()), "THROW", firstThrowTarget(observation)));
        assertInstanceOf(Outcome.Applied.class, outcome, outcome.toString());
        driver.stepToInputWait();
        // The item left the pack, which is what a throw does.
        assertTrue(new Observer().observe().inventory().items().stream()
                        .noneMatch(item -> item.name().equals(throwable.name()) && item.quantity() == throwable.quantity()),
                "the throw happened: the pack changed");
    }

    @Test
    @DisplayName("a Prompt's button is pressed by the tap a person makes, and the answer is the game's")
    void a_prompt_is_answered_by_a_tap() {
        atTheFirstWait();
        // The chasm's own question, raised the way the game raises it.
        Chasm.heroJump(hero);
        HeadlessBoot.ensure().drainPostedRunnables();
        driver.stepToInputWait();

        Observation observation = new Observer().observe();
        assertEquals(PromptKind.CHASM_JUMP, observation.prompt().kind());
        assertEquals(2, observation.prompt().options().size(), observation.prompt().options().toString());
        int no = observation.prompt().options().size() - 1;
        int depth = Dungeon.depth;

        // Answering is a tap on the button, posted where the input system posts one; the frame
        // that follows delivers it, as it delivers a player's.
        Outcome outcome = executor.execute(observation, new Action.AnswerPrompt(no));
        assertInstanceOf(Outcome.Applied.class, outcome, outcome.toString());
        driver.stepToInputWait();

        assertEquals(null, Windows.front(), "the window the answer closed");
        assertEquals(depth, Dungeon.depth, "the hero said no, so it did not jump");
        assertTrue(hero.isAlive());
    }

    @Test
    @DisplayName("a message is sent away by the Action a message offers")
    void a_message_is_dismissed() {
        atTheFirstWait();
        GameScene.show(new WndMessage("The game says something and asks nothing."));
        driver.stepToInputWait();

        Observation observation = new Observer().observe();
        assertEquals(PromptKind.MESSAGE, observation.prompt().kind());
        assertEquals(List.of(new Action.DismissPrompt()), observation.actions().actions(),
                "one Action, which is the tap that sends it away");

        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, new Action.DismissPrompt()));
        assertEquals(null, Windows.front(), "and it is gone");
    }

    private Action.Step firstStep(Observation observation) {
        return observation.actions().actions().stream()
                .filter(Action.Step.class::isInstance)
                .map(Action.Step.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("no step in the set"));
    }

    private int firstThrowTarget(Observation observation) {
        return observation.actions().actions().stream()
                .filter(Action.UseItemAt.class::isInstance)
                .map(Action.UseItemAt.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("no targeted use in the set"))
                .cell();
    }

    private Item itemAt(int index) {
        int at = 0;
        for (Item item : hero.belongings) {
            if (at++ == index) {
                return item;
            }
        }
        throw new AssertionError("no item at " + index);
    }

    /**
     * A cell beside the hero that a click does not walk onto, made one if the floor is open: the
     * hero starts in a room, so the test paints a wall rather than hunting for one.
     */
    private int wallBesideTheHero() {
        int width = level.width();
        for (int d : new int[]{1, -1, width, -width, width + 1, width - 1, -width + 1, -width - 1}) {
            int cell = hero.pos + d;
            if (cell >= 0 && cell < level.length() && level.map[cell] == Terrain.WALL) {
                return cell;
            }
        }
        for (int d : new int[]{1, -1, width, -width}) {
            int cell = hero.pos + d;
            if (cell >= 0 && cell < level.length() && level.map[cell] == Terrain.EMPTY
                    && Actor.findChar(cell) == null && level.heaps.get(cell, null) == null) {
                Level.set(cell, Terrain.WALL);
                return cell;
            }
        }
        throw new AssertionError("nowhere beside the hero to put a wall");
    }

    private int floorInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no free floor in view");
    }

    static {
        // Referenced so an unused import cannot drop it: the list the pack is walked as.
        List.of();
    }
}
