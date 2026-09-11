package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("the whole read is the sections read one by one, with no Actions before story 1.12")
    void the_whole_is_its_sections() {
        Observer observer = atTheFirstWait();
        Observation whole = observer.observe();
        Observation assembled = new Observation(observer.header(), observer.map(), observer.actors(), observer.hero(),
                observer.inventory(), observer.journal(), observer.log(), ActionsSection.NONE, observer.prompt());
        assertEquals(assembled, whole, "the same sections, so the same record");
        assertEquals(assembled.hash(), whole.hash());
        assertEquals(ActionsSection.NONE, whole.actions(), "the valid Actions are story 1.12's");
        assertEquals(whole.header().prompt(), whole.prompt().kind(), "the record holds the two to each other");
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
