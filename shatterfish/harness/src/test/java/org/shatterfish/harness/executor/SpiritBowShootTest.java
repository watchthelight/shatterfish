package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Huntress's Spirit Bow shoots through the same two calls a person's clicks make: the item
 * window's SHOOT, which opens the cell selector ({@code core/.../items/weapon/SpiritBow.java:88-93}),
 * and the second click on the enemy ({@code core/.../scenes/GameScene.java:1622-1624}). Issue #309:
 * the Observation offered SHOOT as a plain use, which opened a selector nothing answered.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class SpiritBowShootTest {

    private static final long RUN_SALT = 0x5A17_5A17L;
    private static final long SEED = 24_012_345L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    @DisplayName("the bow is offered at an enemy in view, and shooting it answers the selector and spends the turn")
    void the_bow_shoots_an_enemy_in_view() {
        driver = HeadlessDriver.start(SEED, HeroClass.HUNTRESS, RUN_SALT);
        driver.stepToInputWait();
        Level level = Dungeon.level;
        Hero hero = Dungeon.hero;

        Rat rat = new Rat();
        rat.pos = clearCellAtDistance(level, hero, 3);
        rat.HT = 8;
        rat.HP = 8;
        rat.state = rat.SLEEPING;
        level.mobs.add(rat);
        GameScene.add(rat);
        Dungeon.observe();

        Observation observation = new Observer().observe();
        int index = -1;
        ItemView bow = null;
        for (int i = 0; i < observation.inventory().items().size(); i++) {
            ItemView item = observation.inventory().items().get(i);
            if (item.actions().contains("SHOOT")) {
                bow = item;
                index = i;
            }
        }
        assertTrue(bow != null, "the Huntress carries the bow: " + observation.inventory().items());
        ItemRef ref = new ItemRef(index, bow.name(), bow.quantity());
        Action.UseItemAt shot = new Action.UseItemAt(ref, "SHOOT", rat.pos);
        assertTrue(observation.actions().actions().contains(shot), "SHOOT is offered at the rat's cell");
        assertTrue(observation.actions().actions().stream()
                        .noneMatch(a -> a instanceof Action.UseItem use && use.action().equals("SHOOT")),
                "and never as the plain use, which would open a selector nobody answers");

        Outcome outcome = new ActionExecutor().execute(observation, shot);
        assertInstanceOf(Outcome.Applied.class, outcome, outcome.toString());
        driver.stepToInputWait();
        assertTrue(!rat.isAlive() || rat.HP < rat.HT || rat.state != rat.SLEEPING,
                "the arrow flew: the rat was hit, killed or woken");
    }

    private static int clearCellAtDistance(Level level, Hero hero, int distance) {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY && Actor.findChar(cell) == null
                    && level.distance(hero.pos, cell) == distance
                    && new Ballistica(hero.pos, cell, Ballistica.PROJECTILE).collisionPos == cell) {
                return cell;
            }
        }
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY && Actor.findChar(cell) == null
                    && level.distance(hero.pos, cell) >= 2
                    && new Ballistica(hero.pos, cell, Ballistica.PROJECTILE).collisionPos == cell) {
                return cell;
            }
        }
        throw new AssertionError("no clear cell in view");
    }
}
