package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Observer runs only at an Input wait (ADR-0006), and its header is what the screens around
 * the play area show (ADR-0005): the release, the class, the challenges, the depth and branch,
 * and the boss lock.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ObserverGateTest {

    private static final long SEED = 16_180_339L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    @DisplayName("the header names the release, the class, the challenges, the depth, the branch and the lock")
    void the_header() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        HeaderSection header = new Observer().header();
        assertEquals(ObservationCodec.SCHEMA_VERSION, header.version());
        assertEquals("v3.3.8", header.upstreamTag(), "the release docs/UPSTREAM.md pins");
        assertEquals("", header.codexVersion(), "no Codex before E2");
        assertEquals(org.shatterfish.api.HeroClass.WARRIOR, header.heroClass());
        assertEquals(List.of(), header.challenges());
        assertEquals(1, header.depth());
        assertEquals(0, header.branch());
        assertFalse(header.sealed());
        assertFalse(header.oracle());
        assertEquals(PromptKind.NONE, header.prompt());

        Dungeon.challenges = Challenges.NO_FOOD | Challenges.DARKNESS | Challenges.STRONGER_BOSSES;
        Dungeon.level.locked = true;
        HeaderSection changed = new Observer().header();
        assertEquals(List.of(Challenge.DARKNESS, Challenge.NO_FOOD, Challenge.STRONGER_BOSSES), changed.challenges(),
                "the challenges by their names (Challenges.java:43-64), in name order");
        assertTrue(changed.sealed(), "the boss lock is the sealed flag (Level.java:180)");
        Dungeon.challenges = 0;
        Dungeon.level.locked = false;
    }

    @Test
    @DisplayName("the Observer refuses to run while the hero acts and while a window is open")
    void only_at_an_input_wait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Observer observer = new Observer();
        assertNotNull(observer.map());

        int far = farFloor();
        GameScene.handleCell(far);
        assertNotNull(Dungeon.hero.curAction, "the click became a move");
        IllegalStateException acting = assertThrows(IllegalStateException.class, observer::map);
        assertTrue(acting.getMessage().contains("not waiting for input"), acting.getMessage());
        assertThrows(IllegalStateException.class, observer::header);
        driver.stepToInputWait();
        assertNotNull(observer.map(), "ready again at the next wait");

        WndMessage window = new WndMessage("not a Prompt");
        driver.scene().add(window);
        assertTrue(GameScene.showingWindow());
        IllegalStateException shown = assertThrows(IllegalStateException.class, observer::map);
        assertTrue(shown.getMessage().contains("window"), shown.getMessage());
        window.remove();
        assertFalse(GameScene.showingWindow());
        assertNotNull(observer.map());
    }

    private static int farFloor() {
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (Dungeon.level.heroFOV[cell] && Dungeon.level.passable[cell] && cell != Dungeon.hero.pos
                    && Dungeon.level.distance(cell, Dungeon.hero.pos) >= 3) {
                return cell;
            }
        }
        throw new AssertionError("no floor three cells away in view");
    }
}
