package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
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
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

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
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
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
        // Which button was pressed is the whole question, and the depth does not answer it: a jump
        // asks for the scene that carries the hero down and this driver stops at a scene change
        // (issue #68), so the floor is the same either way. What the yes button sets is the jump
        // itself (…/levels/features/Chasm.java:88, :101), and that is what says no was pressed.
        assertFalse(Chasm.jumpConfirmed, "the hero said no, so no jump was confirmed");
        assertEquals(depth, Dungeon.depth, "and it is on the floor it was on");
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

    @Test
    @DisplayName("an Action that leaves no trace on the hero still ends its wait")
    void an_action_with_no_trace_ends_its_wait() {
        Observation observation = atTheFirstWait();
        // Detaching the broken seal plays the hero's operate animation and returns
        // (…/items/armor/Armor.java:190-197): no action held, no rest, nothing for the driver to
        // infer from. The executor says so itself, and the next wait arrives (story 1.13).
        ItemView armor = observation.inventory().items().stream()
                .filter(item -> item.actions().contains("DETACH"))
                .findFirst().orElseThrow(() -> new AssertionError("the Warrior starts with a sealed armor"));
        int index = observation.inventory().items().indexOf(armor);

        assertInstanceOf(Outcome.Applied.class, executor.execute(observation,
                new Action.UseItem(new ItemRef(index, armor.name(), armor.quantity()), "DETACH")));
        assertEquals(null, hero.curAction, "the hero holds nothing and is not resting");
        assertFalse(hero.resting);

        // Without the executor's word, the driver would wait for a wait that had already been
        // served, and this is the call that would never return.
        driver.stepToInputWait();
        assertTrue(new Observer().observe().inventory().items().stream()
                        .anyMatch(item -> item.name().toLowerCase(java.util.Locale.ROOT).contains("seal")),
                "the seal is in the pack now");
    }

    @Test
    @DisplayName("a heap under the hero is picked up by the click on its own cell")
    void a_heap_underfoot_is_picked_up() {
        Observation observation = atTheFirstWait();
        ItemView stones = observation.inventory().items().stream()
                .filter(item -> item.actions().contains("DROP"))
                .findFirst().orElseThrow();
        int index = observation.inventory().items().indexOf(stones);
        int packed = observation.inventory().items().size();

        // Dropped, it becomes a heap on the hero's own cell, which the set then offers to pick up.
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation,
                new Action.UseItem(new ItemRef(index, stones.name(), stones.quantity()), "DROP")));
        driver.stepToInputWait();
        Observation dropped = new Observer().observe();
        assertTrue(dropped.map().heaps().stream().anyMatch(heap -> heap.cell() == hero.pos),
                "the heap is underfoot");
        assertTrue(dropped.actions().actions().contains(new Action.PickUp()), "and the set offers to take it");

        assertInstanceOf(Outcome.Applied.class, executor.execute(dropped, new Action.PickUp()));
        driver.stepToInputWait();
        Observation again = new Observer().observe();
        assertEquals(packed, again.inventory().items().size(), "the pack holds what it held");
        assertTrue(again.map().heaps().stream().noneMatch(heap -> heap.cell() == hero.pos),
                "and the cell is bare");
    }

    @Test
    @DisplayName("an item that moved between the read and the Action is a desync, not a wrong item used")
    void an_item_that_moved_is_refused() {
        Observation observation = atTheFirstWait();
        ItemView food = observation.inventory().items().stream()
                .filter(item -> item.actions().contains("EAT"))
                .findFirst().orElseThrow();
        int index = observation.inventory().items().indexOf(food);
        Action.UseItem eat = new Action.UseItem(new ItemRef(index, food.name(), food.quantity()), "EAT");
        assertTrue(observation.actions().actions().contains(eat), "the set offered it when the screen was read");

        // The pack changes under the Action, as it would in a Replay that drifted: the item at that
        // position is no longer the one the Observation named.
        Item moved = itemAt(index);
        moved.detachAll(hero.belongings.backpack);
        String before = new Observer().observe().hash();

        Outcome outcome = executor.execute(observation, eat);
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class, outcome, outcome.toString());
        assertEquals(Reason.ITEM_MOVED, rejected.reason(), rejected.detail());
        assertTrue(rejected.detail().contains(food.name()), rejected.detail());
        assertEquals(before, new Observer().observe().hash(), "and nothing was eaten");
    }

    @Test
    @DisplayName("a descent is the click on the stairs, and a heap on them takes the click first")
    void a_descent_and_what_stands_in_front_of_it() {
        atTheFirstWait();
        LevelTransition down = null;
        for (LevelTransition transition : Dungeon.level.transitions) {
            if (transition.type == LevelTransition.Type.REGULAR_EXIT) {
                down = transition;
            }
        }
        assertNotNull(down, "the floor has a way down");

        // An item on the stairs takes the click: the hero decides by cell in one chain and the
        // heap branch stands above the transition branch (…/actors/hero/Hero.java:1974, :2000), so
        // the set offers the pick-up and not the descent, and a hand-built descent is refused.
        int stairs = down.cell();
        Dungeon.level.drop(new Gold(10), stairs);
        stand(stairs);
        Observation onTheHeap = new Observer().observe();
        assertTrue(onTheHeap.actions().actions().contains(new Action.PickUp()), "the item is offered");
        assertFalse(onTheHeap.actions().actions().contains(new Action.Descend()),
                "and the descent is not, because the click would pick the item up");
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class,
                executor.execute(onTheHeap, new Action.Descend()));
        assertEquals(Reason.NOT_OFFERED, rejected.reason());

        // With the item gone the stairs are offered, and the descent is the click. The heap is
        // taken off the floor rather than picked up, so the assertion is about the descent and not
        // about what a pick-up costs in turns.
        Dungeon.level.heaps.remove(stairs);
        GameScene.updateMap(stairs);
        Observation onTheStairs = new Observer().observe();
        assertTrue(onTheStairs.actions().actions().contains(new Action.Descend()), "the way down");
        assertInstanceOf(Outcome.Applied.class, executor.execute(onTheStairs, new Action.Descend()));
        // What the executor owes is the click, and the click is taken: the hero's own action
        // becomes the transition, which is what a person's tap on the stairs makes it
        // (…/actors/hero/Hero.java:2000-2006). Carrying the hero to the next floor is the game's
        // part and runs in the interlevel scene, which this driver reports and does not cross
        // (ADR-0015, issue #68).
        //
        // The transition is not activated here, and that is this test's own doing: stand() writes
        // hero.pos from this thread, and the guard that decides a transition reads it on the actor
        // thread (…/actors/hero/Hero.java:1447). A hero that walks to the stairs itself and clicks
        // the cell it is standing on activates it and halts with the scene change, which a probe
        // for issue #68 shows. So what is held here is the click, which is the executor's part; the
        // walked case belongs to the story that serves the scene change.
        assertInstanceOf(HeroAction.LvlTransition.class, hero.curAction, "the click is the descent");
        assertEquals(stairs, ((HeroAction.LvlTransition) hero.curAction).dst, "at the stairs");
    }

    /** Puts the hero on a cell without a walk, so a test can reach a feature the walk would not. */
    private void stand(int cell) {
        hero.pos = cell;
        hero.sprite.place(cell);
        Dungeon.level.occupyCell(hero);
        Dungeon.observe();
        GameScene.updateFog();
    }

    @Test
    @DisplayName("an action that opens no bag is applied, target or no target")
    void an_action_that_asks_nothing() {
        Observation observation = atTheFirstWait();
        // A scroll is the case: READ opens the bag for five scrolls and for no other
        // (…/items/scrolls/InventoryScroll.java, …/items/scrolls/exotic/ScrollOfEnchantment.java:45),
        // so for the rest the press is the whole input and the scroll is read and gone. The set
        // offers both shapes because it cannot tell which scroll this is, and the review of story
        // 1.14 found the executor calling the plain one a refusal after reading the scroll.
        // A Warrior starts with no scroll, so one is picked up the way anything is picked up.
        // Collecting is not an Action and hands the game nothing, so no wait follows it; the next
        // Observation is simply read.
        new ScrollOfMagicMapping().identify().collect();
        Observation withScroll = new Observer().observe();
        int before = withScroll.inventory().items().size();
        Action.UseItemOn readAtSomething = withScroll.actions().actions().stream()
                .filter(Action.UseItemOn.class::isInstance).map(Action.UseItemOn.class::cast)
                .filter(use -> use.action().equals("READ"))
                .findFirst().orElseThrow(() -> new AssertionError("the set offers the scroll a target,"
                        + " because READ can open the bag: " + withScroll.actions().actions()));

        assertInstanceOf(Outcome.Applied.class, executor.execute(withScroll, readAtSomething),
                "the scroll was read, which is what pressing READ does, so the Action was applied");
        driver.stepToInputWait();
        assertNotEquals(before, new Observer().observe().inventory().items().size(),
                "and the scroll is gone from the pack");
    }

    @Test
    @DisplayName("an action that opens a bag nothing can answer is cancelled, not left open")
    void an_action_that_opens_a_bag_with_no_answer() {
        Observation observation = atTheFirstWait();
        Action.UseItem detach = observation.actions().actions().stream()
                .filter(Action.UseItem.class::isInstance).map(Action.UseItem.class::cast)
                .filter(use -> use.action().equals("DETACH"))
                .findFirst().orElseThrow(() -> new AssertionError("the armour offers its seal"));
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, detach));
        driver.stepToInputWait();

        // The plain shape of an action that does open the bag: the window asks which item, and this
        // Action carries no answer. A person would choose or press back; the executor presses back,
        // and the Run goes on rather than waiting for a choice nobody will make.
        Observation withSeal = new Observer().observe();
        Action.UseItem affix = withSeal.actions().actions().stream()
                .filter(Action.UseItem.class::isInstance).map(Action.UseItem.class::cast)
                .filter(use -> use.action().equals("AFFIX"))
                .findFirst().orElseThrow(() -> new AssertionError("the seal offers to be affixed: "
                        + withSeal.actions().actions()));

        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class,
                executor.execute(withSeal, affix), "half an input is not an input");
        assertEquals(Reason.NO_SELECTOR, rejected.reason(), rejected.toString());
        assertEquals(null, Windows.front(), "and the window it opened is gone");
        driver.stepToInputWait();
        assertTrue(Dungeon.hero.isAlive(), "and the Run goes on");
    }

    @Test
    @DisplayName("an item the selector would not take is refused, and the Run goes on")
    void an_item_the_selector_refuses() {
        Observation observation = atTheFirstWait();
        // The seal starts on the armour, so a person detaches it first; both are the item window's
        // own buttons. After this the seal is in the pack and offers AFFIX, which opens a window
        // asking which armour — and the window draws a button only for an item it accepts
        // (…/windows/WndBag.java:478-487).
        Action.UseItem detach = observation.actions().actions().stream()
                .filter(Action.UseItem.class::isInstance).map(Action.UseItem.class::cast)
                .filter(use -> use.action().equals("DETACH"))
                .findFirst().orElseThrow(() -> new AssertionError("the armour offers its seal"));
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, detach));
        driver.stepToInputWait();

        Observation withSeal = new Observer().observe();
        Action.UseItemOn onNothingItTakes = withSeal.actions().actions().stream()
                .filter(Action.UseItemOn.class::isInstance).map(Action.UseItemOn.class::cast)
                .filter(use -> use.action().equals("AFFIX") && !use.target().name().contains("armor"))
                .findFirst().orElseThrow(() -> new AssertionError("the set offers the seal an item it"
                        + " cannot take: " + withSeal.actions().actions()));

        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class,
                executor.execute(withSeal, onNothingItTakes),
                "a target the window draws no button for is not a tap a person could make");
        assertEquals(Reason.NO_SELECTOR, rejected.reason(), rejected.toString());
        assertEquals(null, Windows.front(), "and the window it opened is gone");

        // The refusal followed a change, so the driver has to hear that something was handed over;
        // without that the Run stops here, which is how story 1.14 found this.
        driver.stepToInputWait();
        assertTrue(Dungeon.hero.isAlive(), "and the Run goes on");
    }

    @Test
    @DisplayName("a talent takes a point only while the pane would give it one")
    void a_talent_stops_at_its_ceiling() {
        atTheFirstWait();
        // The hero levels up until a tier-one point is there to spend.
        while (hero.talentPointsAvailable(1) <= 0 && hero.lvl < 5) {
            hero.earnExp(hero.maxExp(), Hero.class);
        }
        assertTrue(hero.talentPointsAvailable(1) > 0, "a point to spend");
        Observation observation = new Observer().observe();
        Action.Talent talent = observation.actions().actions().stream()
                .filter(Action.Talent.class::isInstance)
                .map(Action.Talent.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("the set offers a talent"));
        Talent upgrading = talentNamed(talent.talent());
        int ceiling = upgrading.maxPoints();

        // Up to the ceiling the pane would give a point, and so does the executor; the hero levels
        // up again whenever the tier has none left to give.
        int given = 0;
        while (hero.pointsInTalent(upgrading) < ceiling) {
            if (hero.talentPointsAvailable(1) <= 0) {
                hero.earnExp(hero.maxExp(), Hero.class);
                continue;
            }
            assertInstanceOf(Outcome.Applied.class, executor.execute(new Observer().observe(), talent));
            given++;
        }
        assertEquals(ceiling, hero.pointsInTalent(upgrading), "the talent is full after " + given);

        // Past it the pane offers no upgrade at all (…/ui/TalentButton.java:114-119), and neither
        // does the executor: Hero.upgradeTalent would take the point, which is why this is checked
        // here and not left to the game.
        while (hero.talentPointsAvailable(1) <= 0 && hero.lvl < 12) {
            hero.earnExp(hero.maxExp(), Hero.class);
        }
        assertTrue(hero.talentPointsAvailable(1) > 0, "another point, and nowhere for it to go in this talent");
        Outcome outcome = executor.execute(new Observer().observe(), talent);
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class, outcome,
                "a talent at its ceiling takes no more: " + outcome);
        assertEquals(Reason.NOT_OFFERED, rejected.reason());
        assertTrue(rejected.detail().contains(String.valueOf(ceiling)), rejected.detail());
        assertEquals(ceiling, hero.pointsInTalent(upgrading), "and it is still what it was");
    }

    @Test
    @DisplayName("resting and searching are the buttons they are")
    void resting_and_searching() {
        Observation observation = atTheFirstWait();
        float before = Actor.now();
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, new Action.Search()));
        driver.stepToInputWait();
        assertTrue(Actor.now() > before, "searching takes a turn");

        Observation next = new Observer().observe();
        assertTrue(next.actions().actions().contains(new Action.Rest(true)), "the rest button is offered");
        assertFalse(next.actions().actions().contains(new Action.Rest(false)),
                "and the wait button is Wait, not a second Rest (story 1.13)");
        float resting = Actor.now();
        assertInstanceOf(Outcome.Applied.class, executor.execute(next, new Action.Rest(true)));
        driver.stepToInputWait();
        assertTrue(Actor.now() > resting, "resting takes at least a turn");
    }

    private Talent talentNamed(String name) {
        for (java.util.Map<Talent, Integer> tier : hero.talents) {
            for (Talent talent : tier.keySet()) {
                if (talent.title().equals(name)) {
                    return talent;
                }
            }
        }
        throw new AssertionError("no talent called " + name);
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
