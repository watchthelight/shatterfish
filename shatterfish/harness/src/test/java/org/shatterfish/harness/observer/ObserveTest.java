package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Fog;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.ValidActions;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Observer.observe()} is the whole read: every section the Observer builds, in one
 * Observation, at one Input wait (ADR-0005; ADR-0006). It is the only call a Brain makes, and the
 * section methods beside it exist for the leak tests that hold one rule each; this suite holds the
 * two to each other, the read to itself, and the gate over both.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ObserveTest {

    /**
     * The rows of ADR-0006's whitelist this suite holds ({@link VisibilityChecklistTest}). What
     * makes the valid-Action row true is structural and is held elsewhere: the set is computed in
     * {@code api}, which the build forbids from seeing the game ({@code ApiBoundaryTest}), and its
     * rules are held over the schema's own corpus by {@code ValidActionsTest}, in a module this
     * checklist cannot see. What this suite adds is the part that needs a game: that the set the
     * read carries is the one the Observation implies, and that state the screen does not show
     * cannot change it.
     */
    static final List<String> ADR_0006_ROWS = List.of("Valid Actions");

    private static final long SEED = 66_260_701L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private Observer atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        return new Observer();
    }

    @Test
    @DisplayName("the whole read is the sections read one by one, with the Actions they imply")
    void the_whole_is_its_sections() {
        Observer observer = atTheFirstWait();
        someoneInView();
        Observation whole = observer.observe();
        assertFalse(whole.actors().actors().isEmpty(), "an actor is in view, so the section is not empty");
        assertFalse(whole.inventory().items().isEmpty(), "the hero carries what it started with");
        assertFalse(whole.log().lines().isEmpty(), "the floor's own lines were captured");
        Observation sections = new Observation(observer.header(), observer.map(), observer.actors(), observer.hero(),
                observer.inventory(), observer.journal(), observer.log(), ActionsSection.NONE, observer.prompt());
        Observation assembled = sections.withActions(ValidActions.of(sections));
        assertEquals(assembled, whole, "the same sections, so the same record");
        assertEquals(assembled.hash(), whole.hash());
        assertNotEquals(ActionsSection.NONE, whole.actions(), "the valid Actions ride with the sections (story 1.12)");
        assertEquals(ValidActions.of(whole), whole.actions(), "and are the ones the Observation itself implies");
        assertEquals(whole.header().prompt(), whole.prompt().kind(), "the record holds the two to each other");
    }

    @Test
    @DisplayName("state the screen does not show cannot change the Actions the read carries")
    void hidden_state_does_not_reach_the_set() {
        Observer observer = atTheFirstWait();
        Observation before = observer.observe();

        // A mob the hero cannot see, moved to another cell the hero cannot see: the Observation
        // carries neither position (ADR-0006, the Mobs row), so the menu cannot move either.
        Mob hidden = mobOutOfView();
        int elsewhere = anotherCellOutOfView(hidden.pos);
        hidden.pos = elsewhere;
        if (hidden.sprite != null) {
            hidden.sprite.place(elsewhere);
        }
        Observation after = new Observer().observe();
        assertEquals(before.actions(), after.actions(), "the same screen, the same menu");
        assertEquals(before.hash(), after.hash(), "and the same bytes");

        // The control: brought into view, the same mob changes the menu, because now the screen
        // shows it — the step to its cell becomes an attack on it.
        int beside = besideTheHero();
        hidden.pos = beside;
        if (hidden.sprite != null) {
            hidden.sprite.place(beside);
        }
        Dungeon.hero.checkVisibleMobs();
        Observation seen = new Observer().observe();
        assertTrue(seen.actions().actions().contains(new Action.Attack(beside)),
                "a character the hero can see beside it is an attack");
        assertFalse(seen.actions().actions().contains(new Action.Step(beside)), "and no longer a step");
    }

    @Test
    @DisplayName("two reads of one wait are the same bytes")
    void two_reads_of_one_wait_are_equal() {
        Observer observer = atTheFirstWait();
        Observation first = observer.observe();
        Observation second = new Observer().observe();
        assertEquals(first, second);
        assertEquals(first.hash(), second.hash());
        assertEquals(HexFormat.of().formatHex(ObservationCodec.encode(first)),
                HexFormat.of().formatHex(ObservationCodec.encode(second)));
        assertEquals(first.sectionHashes(), second.sectionHashes());
        assertTrue(first.json().contains(first.hash()), "the readable form carries the hash");
    }

    @Test
    @DisplayName("the whole read refuses to run outside an Input wait, as every section does")
    void the_gate_holds_for_the_whole() {
        Observer observer = atTheFirstWait();
        assertNotNull(observer.observe());

        WndMessage window = new WndMessage("not a Prompt");
        driver.scene().add(window);
        assertTrue(GameScene.showingWindow());
        IllegalStateException shown = assertThrows(IllegalStateException.class, observer::observe);
        assertTrue(shown.getMessage().contains("window"), shown.getMessage());
        window.remove();

        int far = farFloor();
        GameScene.handleCell(far);
        assertNotNull(Dungeon.hero.curAction, "the click became a move");
        IllegalStateException acting = assertThrows(IllegalStateException.class, observer::observe);
        assertTrue(acting.getMessage().contains("not waiting for input"), acting.getMessage());
        driver.stepToInputWait();
        assertNotNull(observer.observe(), "ready again at the next wait");
    }

    /** A free floor cell beside the hero, which the map draws and the hero can step onto. */
    private static int besideTheHero() {
        int width = Dungeon.level.width();
        for (int d : new int[]{1, -1, width, -width, width + 1, width - 1, -width + 1, -width - 1}) {
            int cell = Dungeon.hero.pos + d;
            if (cell >= 0 && cell < Dungeon.level.length() && Dungeon.level.map[cell] == Terrain.EMPTY
                    && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no free floor beside the hero");
    }

    /** A cell out of the hero's view that is free floor, for a hidden mob to move to. */
    private static int anotherCellOutOfView(int from) {
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != from && !Dungeon.level.heroFOV[cell] && Dungeon.level.map[cell] == Terrain.EMPTY
                    && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no free floor out of view");
    }

    /** A mob the hero cannot see, for the test that hidden state stays out of the set. */
    private static Mob mobOutOfView() {
        for (Mob mob : Dungeon.level.mobs) {
            if (!Dungeon.level.heroFOV[mob.pos]) {
                return mob;
            }
        }
        throw new AssertionError("no mob out of view");
    }

    /** Moves a mob the hero cannot see into view, so that the actors section carries someone. */
    private static void someoneInView() {
        for (Mob mob : Dungeon.level.mobs) {
            if (!Dungeon.level.heroFOV[mob.pos] && mob.sprite != null) {
                mob.pos = farFloor();
                mob.sprite.place(mob.pos);
                Dungeon.hero.checkVisibleMobs();
                return;
            }
        }
        throw new AssertionError("no mob out of view to bring into it");
    }

    /** The cell in view furthest from the hero: a click there is a move of more than one step. */
    private static int farFloor() {
        int far = -1;
        int distance = 0;
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (Dungeon.level.heroFOV[cell] && Dungeon.level.passable[cell] && cell != Dungeon.hero.pos
                    && Dungeon.level.distance(cell, Dungeon.hero.pos) > distance) {
                far = cell;
                distance = Dungeon.level.distance(cell, Dungeon.hero.pos);
            }
        }
        if (far < 0) {
            throw new AssertionError("no floor in view but the hero's own");
        }
        return far;
    }
}
