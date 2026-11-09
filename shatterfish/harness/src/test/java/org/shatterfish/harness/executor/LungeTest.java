package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Duelist's rapier lunges through the calls a person's clicks make: the weapon's ABILITY action, which is its
 * default action for a Duelist and opens the cell selector ({@code core/.../items/weapon/melee/MeleeWeapon.java:75-85},
 * {@code :133-147}), and the second click on the enemy. The lunge jumps the hero to a cell beside an enemy two cells
 * off and strikes it without a miss for 5 more damage ({@code Rapier.java:71-74}, {@code :92-177}).
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LungeTest {

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
    @DisplayName("the rapier lunges at an enemy two cells off: a charge goes, the hero stands beside it, the blow lands")
    void the_rapier_lunges() {
        driver = HeadlessDriver.start(SEED, HeroClass.DUELIST, RUN_SALT);
        driver.stepToInputWait();
        Level level = Dungeon.level;
        Hero hero = Dungeon.hero;

        Rat rat = new Rat();
        rat.pos = clearCellAtDistance(level, hero, 2);
        rat.HT = 80;
        rat.HP = 80;
        rat.state = rat.SLEEPING;
        level.mobs.add(rat);
        GameScene.add(rat);
        Dungeon.observe();

        Observation observation = new Observer().observe();
        int index = -1;
        ItemView rapier = null;
        for (int i = 0; i < observation.inventory().items().size(); i++) {
            ItemView item = observation.inventory().items().get(i);
            if (item.actions().contains("ABILITY")) {
                rapier = item;
                index = i;
            }
        }
        assertTrue(rapier != null, "the Duelist carries a weapon with an ability: " + observation.inventory().items());
        assertEquals("2/2", rapier.status());
        Action.UseItemAt lunge = new Action.UseItemAt(new ItemRef(index, rapier.name(), rapier.quantity()), "ABILITY", rat.pos);
        assertTrue(observation.actions().actions().contains(lunge), "ABILITY is offered at the rat's cell");

        Outcome outcome = new ActionExecutor().execute(observation, lunge);
        assertInstanceOf(Outcome.Applied.class, outcome, outcome.toString());
        driver.stepToInputWait();
        MeleeWeapon.Charger charger = Buff.affect(hero, MeleeWeapon.Charger.class);
        assertEquals(1, charger.charges, "the lunge costs one charge (MeleeWeapon.java:243-245)");
        assertEquals(1, level.distance(hero.pos, rat.pos), "the hero stands beside the rat");
        assertTrue(rat.HP <= rat.HT - 6, "a lunge never misses and does at least 1+5 (Rapier.java:71-74), and the rat has " + rat.HP);
    }

    private static int clearCellAtDistance(Level level, Hero hero, int distance) {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY && Actor.findChar(cell) == null
                    && level.distance(hero.pos, cell) == distance) {
                return cell;
            }
        }
        throw new AssertionError("no clear cell in view");
    }
}
