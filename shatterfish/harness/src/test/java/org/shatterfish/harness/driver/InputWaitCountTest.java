package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.harness.driver.HeadlessDriver.Halt;
import org.shatterfish.harness.driver.HeadlessDriver.Reason;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 1.5's acceptance test: the notification from hook row 5's site in {@code Hero.act()},
 * consumed and confirmed by the driver, gives exactly one Input wait per hero turn.
 *
 * <p>Paths abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} and
 * every line number is at the pinned tag {@code v3.3.8}.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class InputWaitCountTest {

    private static final long SEED = 31_415_926L;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
        assertNull(Hooks.inputWait, "closing the Run unregistered its listener");
    }

    @Test
    @DisplayName("sixty actor-thread wake-ups with the hero parked are one wait, not sixty")
    void sixty_wake_ups_with_the_hero_parked_are_one_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        Halt first = driver.stepToInputWait();
        assertEquals(1, first.waitIndex());
        assertEquals(1, driver.hookNotifications(), "the hero's first act began unready and notified once (Hero.java:840)");

        // A parked hero is left parked: the scene wakes the thread only while no actor is current
        // (GameScene.java:865), and the waiting hero stays current. What wakes it is the UI ending
        // the hero's turn after handling a click (GameScene.java:1750-1756, Actor.java:228-232), as
        // a click on a cell the hero cannot reach does; sixty of those, with nothing handed over.
        long parksBefore = waitedCount(driver.stepper().actorThread());
        for (int frame = 0; frame < 60; frame++) {
            Dungeon.hero.next();
            driver.step();
        }
        long wakeUps = waitedCount(driver.stepper().actorThread()) - parksBefore;

        assertEquals(60, wakeUps, "each frame woke the parked thread, which acted and parked again");
        assertEquals(1, driver.hookNotifications(),
                "a hero that is already ready skips the branch on every wake-up (Hero.java:840, :862-870)");
        assertEquals(1, driver.waitIndex());
        assertEquals(0, driver.droppedNotifications());

        HeadlessDriver.Stalled stalled = assertThrows(HeadlessDriver.Stalled.class, () -> driver.stepToInputWait(30));
        assertTrue(stalled.getMessage().contains("nothing has happened since wait 1"), stalled.getMessage());
        assertEquals(1, driver.waitIndex(), "waiting longer did not make a second wait");
    }

    @Test
    @DisplayName("a move of several steps is one wait: the notification of every step but the last is dropped")
    void a_move_of_several_steps_is_one_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        int from = hero.pos;
        int target = aCellStepsAway(hero, 2);
        long before = driver.hookNotifications();
        long droppedBefore = driver.droppedNotifications();

        GameScene.handleCell(target);
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(2, halt.waitIndex(), "one wait for the whole move");
        assertNotEquals(from, hero.pos, "the hero moved");
        assertNull(hero.curAction);
        long steps = driver.hookNotifications() - before;
        assertTrue(steps >= 2, "each step's act began unready and notified (Hero.java:840, :885-887): " + steps);
        assertEquals(steps - 1, driver.droppedNotifications() - droppedBefore, "every notification but the last was dropped");
    }

    @Test
    @DisplayName("resting turns are dropped, and the end of the rest is the wait")
    void resting_turns_are_dropped_and_the_end_of_the_rest_is_the_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        long before = driver.hookNotifications();
        long droppedBefore = driver.droppedNotifications();
        hero.HP = hero.HT - 3;

        // A full rest: each turn begins unready and ends with next(), never ready() (Hero.java:862-870),
        // until regeneration fills the hero and clears resting (Regeneration.java:90-93).
        hero.rest(true);
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(2, halt.waitIndex(), "one wait at the end of the rest");
        assertEquals(hero.HT, hero.HP);
        assertFalse(hero.resting);
        long turns = driver.hookNotifications() - before;
        assertTrue(turns >= 3, "each resting turn notified: " + turns);
        assertEquals(turns - 1, driver.droppedNotifications() - droppedBefore, "every resting turn was dropped");
    }

    @Test
    @DisplayName("an Action the game refuses still gets its wait: the act began and ended ready and announced nothing")
    void a_refused_action_is_still_a_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        int from = hero.pos;
        long before = driver.hookNotifications();
        int unknown = anUnknownCell(hero);

        // Hero.handle makes it a move (Hero.java:1960); getCloser finds no path through cells the
        // hero has neither visited nor mapped and calls ready() in the same act (:1801-1825,
        // :989-992): the act began ready, so the site said nothing.
        GameScene.handleCell(unknown);
        assertNotNull(hero.curAction);
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(2, halt.waitIndex(), "AD-5: the Action was executed, so the next wait is a new one");
        assertEquals(from, hero.pos);
        assertEquals(before, driver.hookNotifications(), "no act of the hero began unready");
        assertEquals(1, halt.framesStepped());
    }

    @Test
    @DisplayName("a message the game shows after an Action holds the wait until the message is dismissed")
    void a_message_after_an_action_holds_the_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        Level level = Dungeon.level;
        LevelTransition entrance = level.getTransition(hero.pos);
        assertNotNull(entrance, "the hero starts on the entrance");
        assertEquals(LevelTransition.Type.SURFACE, entrance.type, "on the first floor the entrance leads out");

        // Leaving without the amulet: the game posts a message and the hero is ready under it
        // (SewerLevel.java:146-155, Hero.java:1391-1395), a window nobody but the player closes.
        GameScene.handleCell(hero.pos);
        assertNotNull(hero.curAction);
        HeadlessDriver.Stalled stalled = assertThrows(HeadlessDriver.Stalled.class, () -> driver.stepToInputWait(20));

        assertTrue(stalled.getMessage().contains("WndMessage, not a Prompt"), stalled.getMessage());
        assertTrue(stalled.getMessage().contains("An Action is waiting for its wait"), stalled.getMessage());
        assertEquals(1, driver.waitIndex());
        assertTrue(hero.ready);

        driver.scene().openWindow().onBackPressed();
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(2, halt.waitIndex(), "the wait the notification announced, confirmed once the message was gone");
        assertNull(halt.window());
        assertEquals(1, halt.framesStepped());
    }

    @Test
    @DisplayName("an interruption mid-move is a wait of its own, with no Action before it")
    void an_interruption_is_a_wait_with_no_action() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        int target = aCellStepsAway(hero, 2);
        GameScene.handleCell(target);
        driver.step();
        assertNotNull(hero.curAction, "the hero is mid-move");
        assertNotEquals(target, hero.pos);

        // What damage, or an enemy coming into view, does (Hero.java:948-957), from between frames.
        hero.interrupt();
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(2, halt.waitIndex(), "the wait after the interruption is the second, though no Action preceded it");
        assertNotEquals(target, hero.pos, "the move was cut short");
        assertNull(hero.curAction);
        assertTrue(hero.ready);
    }

    @Test
    @DisplayName("the per-wait sequence runs in order: the index, reseed, observe, decide, execute, record")
    void the_per_wait_sequence_runs_in_order() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        List<String> calls = new ArrayList<>();
        WaitSequence<Integer, Integer> sequence = new WaitSequence<>() {
            @Override
            public void reseed(long k) {
                calls.add("reseed " + k);
            }

            @Override
            public Integer observe(long k) {
                calls.add("observe " + k);
                return Dungeon.hero.pos;
            }

            @Override
            public Integer decide(long k, Integer heroCell) {
                calls.add("decide " + k);
                return freeCellBeside(heroCell);
            }

            @Override
            public void execute(long k, Integer cell) {
                calls.add("execute " + k);
                GameScene.handleCell(cell);
            }

            @Override
            public void record(long k, Integer heroCell, Integer cell) {
                calls.add("record " + k);
            }
        };

        Halt halt = driver.run(sequence, 3);

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(3, halt.waitIndex());
        assertEquals(3, driver.waitIndex());
        List<String> expected = new ArrayList<>();
        for (int k = 1; k <= 3; k++) {
            for (String step : new String[]{"reseed", "observe", "decide", "execute", "record"}) {
                expected.add(step + " " + k);
            }
        }
        assertEquals(expected, calls);
    }

    @Test
    @DisplayName("a window that is not a Prompt is not a wait: the Run stalls naming it, and only an Action moves it on")
    void a_wait_under_a_window_that_is_not_a_prompt_is_not_confirmed() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        // Shown the way game code shows a window from the render thread.
        GameScene.show(new WndMessage("not a prompt"));

        HeadlessDriver.Stalled stalled = assertThrows(HeadlessDriver.Stalled.class, () -> driver.stepToInputWait(20));

        assertTrue(stalled.getMessage().contains("WndMessage, not a Prompt"), stalled.getMessage());
        assertEquals(1, driver.waitIndex(), "the hero was ready under it, and that is not a wait (AD-5)");
        assertTrue(GameScene.showingWindow());

        // Closed the way a tap outside it closes it (Window.java:223-225), the game is back where
        // wait 1 left it, so nothing is new for the brain until an Action changes something.
        driver.scene().openWindow().onBackPressed();
        assertThrows(HeadlessDriver.Stalled.class, () -> driver.stepToInputWait(20));
        assertEquals(1, driver.waitIndex());
        GameScene.handleCell(freeCellBeside(Dungeon.hero.pos));
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertEquals(2, halt.waitIndex());
        assertNull(halt.window());
    }

    private static long waitedCount(Thread thread) {
        ThreadInfo info = THREADS.getThreadInfo(thread.threadId());
        return info == null ? -1 : info.getWaitedCount();
    }

    /**
     * A visible cell the hero can walk to at least {@code steps} cells away, with a path of at
     * least that many steps, so that the move takes several acts.
     */
    private static int aCellStepsAway(Hero hero, int steps) {
        Level level = Dungeon.level;
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.distance(hero.pos, cell) < steps || !level.heroFOV[cell] || !level.passable[cell]
                    || level.pit[cell] || level.getTransition(cell) != null || Actor.findChar(cell) != null) {
                continue;
            }
            PathFinder.Path path = Dungeon.findPath(hero, cell, level.passable, level.heroFOV, true);
            if (path != null && path.size() >= steps) {
                return cell;
            }
        }
        throw new IllegalStateException("no visible cell " + steps + " steps from the hero on this floor");
    }

    /** A walkable cell the hero has never seen, which a click makes a move the hero cannot path to. */
    private static int anUnknownCell(Hero hero) {
        Level level = Dungeon.level;
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.passable[cell] && !level.visited[cell] && !level.mapped[cell] && !level.heroFOV[cell]
                    && !level.pit[cell] && level.traps.get(cell) == null && level.getTransition(cell) == null
                    && level.heaps.get(cell) == null && !level.adjacent(hero.pos, cell)) {
                return cell;
            }
        }
        throw new IllegalStateException("no unknown walkable cell on this floor");
    }

    private static int freeCellBeside(int cell) {
        Level level = Dungeon.level;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int candidate = cell + offset;
            if (candidate >= 0 && candidate < level.length() && level.passable[candidate] && !level.pit[candidate]
                    && level.getTransition(candidate) == null && Actor.findChar(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("no free cell beside " + cell);
    }
}
