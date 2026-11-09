package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
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
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.observer.Observer;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Cleric's holy tome casts Guiding Light through the calls a person's clicks make: the item window's CAST,
 * which opens the spell window ({@code core/.../items/artifacts/HolyTome.java:81-96}), the spell's button, and the
 * second click on the enemy ({@code core/.../scenes/GameScene.java:1649-1651}). Issue #376: the Observation offered
 * CAST at a cell and the executor opened the spell window, which nothing answered.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class TomeCastTest {

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
    @DisplayName("the tome casts Guiding Light at an enemy in view: one charge goes, the enemy is hit, the window is shut")
    void the_tome_casts_at_an_enemy_in_view() {
        driver = HeadlessDriver.start(SEED, HeroClass.CLERIC, RUN_SALT);
        driver.stepToInputWait();
        Level level = Dungeon.level;
        Hero hero = Dungeon.hero;

        Rat rat = new Rat();
        rat.pos = clearCellAtDistance(level, hero, 3);
        rat.HT = 80;
        rat.HP = 80;
        rat.state = rat.SLEEPING;
        level.mobs.add(rat);
        GameScene.add(rat);
        Dungeon.observe();

        Observation observation = new Observer().observe();
        int index = -1;
        ItemView tome = null;
        for (int i = 0; i < observation.inventory().items().size(); i++) {
            ItemView item = observation.inventory().items().get(i);
            if (item.actions().contains("CAST")) {
                tome = item;
                index = i;
            }
        }
        assertTrue(tome != null, "the Cleric carries the tome: " + observation.inventory().items());
        assertEquals("3/3", tome.status());
        ItemRef ref = new ItemRef(index, tome.name(), tome.quantity());
        Action.UseItemAt cast = new Action.UseItemAt(ref, "CAST", rat.pos);
        assertTrue(observation.actions().actions().contains(cast), "CAST is offered at the rat's cell");

        Outcome outcome = new ActionExecutor().execute(observation, cast);
        assertInstanceOf(Outcome.Applied.class, outcome, outcome.toString());
        driver.stepToInputWait();
        assertEquals("2/3", hero.belongings.getItem(HolyTome.class).status(), "Guiding Light costs one charge (ClericSpell.java:41-43)");
        assertTrue(rat.HP < rat.HT, "the bolt hit the rat for 2 to 8 (GuidingLight.java:86)");
        assertNull(Windows.front(), "and the spell window is shut");
    }

    @Test
    @DisplayName("a tome with no charge is refused, and leaves no window open")
    void a_spent_tome_is_refused() {
        driver = HeadlessDriver.start(SEED, HeroClass.CLERIC, RUN_SALT);
        driver.stepToInputWait();
        Level level = Dungeon.level;
        Hero hero = Dungeon.hero;
        Rat rat = new Rat();
        rat.pos = clearCellAtDistance(level, hero, 3);
        rat.state = rat.SLEEPING;
        level.mobs.add(rat);
        GameScene.add(rat);
        Dungeon.observe();
        HolyTome held = hero.belongings.getItem(HolyTome.class);
        Observation observation = new Observer().observe();
        int index = 0;
        for (int i = 0; i < observation.inventory().items().size(); i++) {
            if (observation.inventory().items().get(i).actions().contains("CAST")) {
                index = i;
            }
        }
        ItemView tome = observation.inventory().items().get(index);
        Action.UseItemAt cast = new Action.UseItemAt(new ItemRef(index, tome.name(), tome.quantity()), "CAST", rat.pos);
        held.spendCharge(3);
        Outcome outcome = new ActionExecutor().execute(observation, cast);
        assertInstanceOf(Outcome.Rejected.class, outcome, outcome.toString());
        assertNull(Windows.front());
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
