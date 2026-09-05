package org.shatterfish.harness.driver;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver.Halt;
import org.shatterfish.harness.driver.HeadlessDriver.Reason;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 1.4's acceptance test: the boot, a seeded Warrior game driven to its first Input wait, a
 * Prompt window the game opens and the driver closes through the window's own button, the
 * driver's step count as the scene's update count, and a Run that never reaches a wait failing
 * with a diagnostic rather than hanging.
 *
 * <p>Paths abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} and
 * every line number is at the pinned tag {@code v3.3.8}.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HeadlessBootTest {

    /** A seed in the range a player can type. */
    private static final long SEED = 31_415_926L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    @DisplayName("the process boots the headless backend for the pinned upstream, and the backend's loop is not running")
    void boots_the_headless_backend_for_the_pinned_upstream() {
        HeadlessDriver.Boot boot = HeadlessDriver.boot();

        assertEquals("HeadlessDesktop", boot.applicationType());
        assertEquals("3.3.8", boot.upstreamVersion(),
                "the harness runs the release docs/UPSTREAM.md pins; if the pin moved, this and the"
                        + " ledger move together");
        assertFalse(HeadlessBoot.ensure().backendLoopThreadAlive(),
                "the backend's own loop thread ends at boot: nothing but the driver can advance a frame");
        assertEquals(-1, Gdx.graphics.getFrameId(), "the backend has rendered no frame, and never will");
    }

    @Test
    @DisplayName("a seeded Warrior game reaches the hero's first Input wait")
    void a_seeded_warrior_game_reaches_its_first_input_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);

        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        Hero hero = Dungeon.hero;
        assertEquals(HeroClass.WARRIOR, hero.heroClass);
        assertEquals(SEED, Dungeon.seed, "the seed typed into the seed window is the game's seed (Dungeon.java:224-226)");
        assertEquals(1, Dungeon.depth);
        assertTrue(hero.isAlive());
        assertTrue(hero.ready, "the hero waits for input");
        assertNull(hero.curAction);
        assertFalse(hero.resting);
        assertNull(halt.window(), "nothing is shown over the first floor");
        assertFalse(GameScene.showingWindow());
        assertEquals(1, halt.framesStepped(), "the hero's first turn ran before the first frame (the stepper starts"
                + " the actor thread), so the first frame is the first wait");
        assertEquals(1, driver.frames());
    }

    @Test
    @DisplayName("a Prompt window opened by game code appears headlessly and closes through its own button")
    void a_prompt_window_opened_by_game_code_appears_and_closes_through_its_own_button() throws Exception {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        int from = hero.pos;

        // Walking onto a chasm asks first: Hero.getCloser reaches Chasm.heroJump, which posts the
        // window to the render thread and interrupts the move, and the hero is ready again in the
        // same act (Hero.java:1836-1845, :989-992; Chasm.java:57-96).
        int chasm = placeAChasmBeside(hero);
        GameScene.handleCell(chasm);
        assertNotNull(hero.curAction, "the click became a move");
        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.INPUT_WAIT, halt.reason());
        assertInstanceOf(WndOptions.class, halt.window(), "the chasm prompt is a WndOptions");
        assertTrue(GameScene.showingWindow());
        assertTrue(GameScene.interfaceBlockingHero(), "a click on the map is refused while it is open");
        assertEquals(2, halt.framesStepped(), "after the first frame the hero was ready with the window still"
                + " queued; the second frame showed it, and only then is the wait confirmed");
        assertEquals(from, hero.pos);

        // The prompt ignores its buttons until it has been shown for 0.2 s of frame time
        // (Chasm.java:73-92), so one more frame; then "no" is the second button.
        driver.step();
        List<RedButton> buttons = buttons(halt.window());
        assertEquals(2, buttons.size(), "yes and no");
        click(buttons.get(1));
        driver.step();

        assertFalse(GameScene.showingWindow(), "the window closed through its own button");
        assertNull(driver.scene().openWindow());
        assertEquals(from, hero.pos, "and the hero did not jump");
        Halt again = driver.stepToInputWait();
        assertEquals(Reason.INPUT_WAIT, again.reason());
        assertNull(again.window());
        assertEquals(1, again.framesStepped());
    }

    @Test
    @DisplayName("a scene change requested by the game stops the driver rather than being stepped through")
    void a_requested_scene_change_stops_the_driver() throws Exception {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        int chasm = placeAChasmBeside(hero);
        GameScene.handleCell(chasm);
        Halt prompt = driver.stepToInputWait();
        assertInstanceOf(WndOptions.class, prompt.window());
        driver.step();
        // "yes": Chasm.jumpConfirmed and hero.resume() (Chasm.java:85-92), then the fall (:100-120).
        click(buttons(prompt.window()).get(0));

        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.SCENE_SWITCH, halt.reason());
        assertEquals(InterlevelScene.class, halt.requestedScene());
        assertEquals(InterlevelScene.Mode.FALL, InterlevelScene.mode);
        assertEquals(1, halt.framesStepped());
        assertTrue(hero.isAlive());
        IllegalStateException refused = assertThrows(IllegalStateException.class, driver::stepToInputWait);
        assertTrue(refused.getMessage().contains("does not serve"), refused.getMessage());
    }

    @Test
    @DisplayName("the hero dying stops the driver")
    void the_hero_dying_stops_the_driver() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        // Enough poison to kill on its first tick through any shielding: the damage is a third of
        // what is left, plus one (Poison.java:97-101).
        Buff.affect(hero, Poison.class).set(100f * hero.HT);
        GameScene.handleCell(freeCellBeside(hero.pos));

        Halt halt = driver.stepToInputWait();

        assertEquals(Reason.HERO_DEAD, halt.reason());
        assertFalse(hero.isAlive());
        assertFalse(driver.headlessBoot().game().sceneSwitchRequested(),
                "death shows the game-over banner in the scene; a scene change comes only from its buttons"
                        + " (GameScene.java:1482-1494)");
        assertTrue(driver.headlessBoot().pendingRunnables() > 0,
                "the banner was posted to the render thread by the dying hero (Hero.java:2256)");

        // The queue is process-wide: what this Run posted must not appear in the next Run's scene.
        driver.close();
        assertEquals(0, driver.headlessBoot().pendingRunnables(), "closing the Run ran what it had posted");
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        Halt next = driver.stepToInputWait();
        assertEquals(Reason.INPUT_WAIT, next.reason());
        assertNull(next.window());
        assertTrue(Dungeon.hero.isAlive());
    }

    @Test
    @DisplayName("no library-owned loop thread drives the scene: the driver's step count is the scene's update count")
    void the_driver_is_the_only_thing_that_updates_the_scene() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        Halt halt = driver.stepToInputWait();
        for (int wait = 0; wait < 5 && halt.reason() == Reason.INPUT_WAIT; wait++) {
            GameScene.handleCell(freeCellBeside(Dungeon.hero.pos));
            halt = driver.stepToInputWait();
        }

        assertTrue(driver.frames() >= 6, "six waits take at least six frames: " + driver.frames());
        assertEquals(driver.frames(), driver.scene().updates(),
                "every update of the scene was one step of this driver, and nothing else updated it");
        assertEquals(driver.frames(), driver.stepper().frames());
        assertFalse(driver.headlessBoot().backendLoopThreadAlive());
        assertEquals(-1, Gdx.graphics.getFrameId());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("a Run that never reaches a wait fails with a diagnostic naming the last actor, rather than hanging")
    void a_run_that_never_reaches_a_wait_fails_naming_the_last_actor() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Hero hero = Dungeon.hero;
        Actor.add(new Stuck());
        GameScene.handleCell(freeCellBeside(hero.pos));
        long before = driver.frames();

        HeadlessDriver.Stalled stalled = assertThrows(HeadlessDriver.Stalled.class, () -> driver.stepToInputWait(300));

        String message = stalled.getMessage();
        System.out.println("Stalled: " + message);
        assertTrue(message.contains("The last actor processed was Stuck#"), message);
        assertTrue(message.contains("act() returned false"), message);
        assertTrue(message.contains("300 frames"), message);
        assertEquals(300, driver.frames() - before, "the budget was spent exactly, then the Run was declared stalled");
        assertFalse(hero.ready, "the hero never got its turn back: " + message);
    }

    /**
     * An actor whose turn never ends: {@code act()} returns false and nothing calls {@code next()},
     * which is the shape of a turn waiting for an animation callback that never fires, the failure
     * a headless driver is most likely to meet.
     */
    static final class Stuck extends Actor {
        @Override
        protected boolean act() {
            return false;
        }
    }

    /**
     * Makes a cell beside the hero a chasm, the way the game changes terrain during play
     * ({@code Level.set}, {@code GameScene.updateMap}), and returns it. The hero can walk onto it:
     * a chasm is {@code AVOID | PIT} ({@code Terrain.java:83}), and an adjacent avoid cell is a
     * valid step ({@code Hero.java:1780-1784}).
     */
    private static int placeAChasmBeside(Hero hero) {
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

    /** A cell the hero can walk to next to it, not a chasm and not the stairs. */
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

    /**
     * The window's buttons in the order they were added, which for a {@code WndOptions} is the
     * order of its options ({@code WndOptions.java:86-97}). The member list is protected in
     * {@code Group}; a test reads it by reflection, which {@code docs/UPSTREAM.md} lists.
     */
    @SuppressWarnings("unchecked")
    private static List<RedButton> buttons(Window window) throws ReflectiveOperationException {
        Field members = Group.class.getDeclaredField("members");
        members.setAccessible(true);
        List<RedButton> buttons = new ArrayList<>();
        for (Gizmo member : (List<Gizmo>) members.get(window)) {
            if (member instanceof RedButton button) {
                buttons.add(button);
            }
        }
        return buttons;
    }

    /**
     * A left click at the button's centre, as a mouse delivers one: a DOWN and an UP at one screen
     * point, queued for the next frame. {@code PointerEvent} dispatches to every pointer area, most
     * recently registered first, and the button's hot area was registered after the window's
     * blocker, so it gets the click ({@code PointerEvent.java:144-190}, {@code PointerArea.java:57-105},
     * {@code Button.java:50-115}, {@code Window.java:68-80}).
     */
    private static void click(Component button) {
        Point at = button.camera().cameraToScreen(button.centerX(), button.centerY());
        PointerEvent.addPointerEvent(new PointerEvent(at.x, at.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
        PointerEvent.addPointerEvent(new PointerEvent(at.x, at.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
    }
}
