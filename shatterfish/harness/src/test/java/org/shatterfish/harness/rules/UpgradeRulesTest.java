package org.shatterfish.harness.rules;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ScaleArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strength-penalty and wand/ring rows of {@code docs/rules/upgrades.md} (#306), asserted on the
 * pinned game's own classes rather than on a copy of their arithmetic.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class UpgradeRulesTest {

    private static final long RUN_SALT = 0x5A17_5A17L;
    private static final long SEED = 24_012_345L;

    private HeadlessDriver driver;
    private Hero hero;

    @BeforeEach
    void startARun() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        hero = Dungeon.hero;
    }

    @AfterEach
    void endTheRun() {
        driver.close();
    }

    /** Whole steps gained from a level: 0 at +0, 1 at +1, 2 at +3, 3 at +6, 4 at +10. */
    private static int falls(int level) {
        return (int) Math.floor((Math.sqrt(8 * level + 1) - 1) / 2);
    }

    @Test
    @DisplayName("the strength requirement is 8 + 2*tier minus the triangular steps of the level, for armour and weapons alike")
    void strength_requirement_falls_with_level() {
        Armor[] armours = {new ClothArmor(), new LeatherArmor(), new MailArmor(), new ScaleArmor(), new PlateArmor()};
        for (int tier = 1; tier <= 5; tier++) {
            Armor armour = armours[tier - 1];
            assertEquals(tier, armour.tier);
            for (int level = -1; level <= 12; level++) {
                int expected = 8 + 2 * tier - falls(Math.max(0, level));
                assertEquals(expected, armour.STRReq(level), "armour tier " + tier + " at +" + level);
            }
        }
        MeleeWeapon[] weapons = {new Shortsword(), new Sword()};
        for (MeleeWeapon weapon : weapons) {
            for (int level = -1; level <= 12; level++) {
                int expected = 8 + 2 * weapon.tier - falls(Math.max(0, level));
                assertEquals(expected, weapon.STRReq(level), weapon.name() + " at +" + level);
            }
        }
        // The cited breakpoints: one off at +1, two at +3, three at +6.
        Sword sword = new Sword();
        assertEquals(14, sword.STRReq(0));
        assertEquals(13, sword.STRReq(1));
        assertEquals(13, sword.STRReq(2));
        assertEquals(12, sword.STRReq(3));
        assertEquals(11, sword.STRReq(6));
    }

    @Test
    @DisplayName("armour short of strength loses evasion by 1.5 and speed by 1.2 for each point short, and nothing when met")
    void armour_penalty_per_point_short() {
        Armor armour = new MailArmor();
        int required = armour.STRReq();
        hero.STR = required;
        float evasion = armour.evasionFactor(hero, 10f);
        float speed = armour.speedFactor(hero, 1f);
        assertEquals(10f, evasion, 1e-4, "met: no loss");
        assertEquals(1f, speed, 1e-4, "met: no loss");
        hero.STR = required + 3;
        assertEquals(evasion, armour.evasionFactor(hero, 10f), 1e-4, "excess strength is no bonus");
        assertEquals(speed, armour.speedFactor(hero, 1f), 1e-4);
        for (int missing = 1; missing <= 4; missing++) {
            hero.STR = required - missing;
            assertEquals(evasion / Math.pow(1.5, missing), armour.evasionFactor(hero, 10f), 1e-3,
                    missing + " short: evasion");
            assertEquals(speed / Math.pow(1.2, missing), armour.speedFactor(hero, 1f), 1e-4,
                    missing + " short: speed");
        }
    }

    @Test
    @DisplayName("a weapon short of strength loses accuracy by 1.5 and attack speed by 1.2 for each point short")
    void weapon_penalty_per_point_short() {
        Sword sword = new Sword();
        int required = sword.STRReq();
        hero.STR = required;
        float accuracy = sword.accuracyFactor(hero, hero);
        float delay = sword.delayFactor(hero);
        hero.STR = required + 2;
        assertEquals(accuracy, sword.accuracyFactor(hero, hero), 1e-4, "excess strength is no accuracy bonus");
        assertEquals(delay, sword.delayFactor(hero), 1e-4);
        for (int missing = 1; missing <= 4; missing++) {
            hero.STR = required - missing;
            assertEquals(accuracy / Math.pow(1.5, missing), sword.accuracyFactor(hero, hero), 1e-4,
                    missing + " short: accuracy");
            assertEquals(delay * Math.pow(1.2, missing), sword.delayFactor(hero), 1e-4,
                    missing + " short: delay");
        }
    }

    @Test
    @DisplayName("an upgrade lowers the requirement, so a point short at +0 is met at +1")
    void an_upgrade_can_close_the_gap() {
        Armor armour = new MailArmor();
        hero.STR = armour.STRReq() - 1;
        assertTrue(armour.evasionFactor(hero, 10f) < 10f);
        armour.upgrade();
        assertEquals(1, armour.level());
        assertEquals(10f, armour.evasionFactor(hero, 10f), 1e-4);
    }

    /** Reads the scroll onto {@code item} the way the player does and returns what it hands back. */
    private Item readOnto(Item item) {
        try {
            java.lang.reflect.Field user = Item.class.getDeclaredField("curUser");
            user.setAccessible(true);
            user.set(null, hero);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return new ScrollOfUpgrade().upgradeItem(item);
    }

    @Test
    @DisplayName("an upgrade on a wand adds one level and lifts the curse one time in three, not at once")
    void wand_upgrade() {
        assertCurseGoesOneInThree(WandOfMagicMissile::new);
    }

    @Test
    @DisplayName("an upgrade on a ring adds one level and lifts the curse one time in three, not at once")
    void ring_upgrade() {
        assertCurseGoesOneInThree(RingOfHaste::new);
    }

    private void assertCurseGoesOneInThree(Supplier<? extends Item> make) {
        int kept = 0;
        int lifted = 0;
        for (long seed = 1; seed <= 300; seed++) {
            Item item = make.get();
            item.cursed = true;
            int before = item.level();
            Random.pushGenerator(seed);
            try {
                assertSame(item, readOnto(item), "the same item comes back");
            } finally {
                Random.popGenerator();
            }
            assertEquals(before + 1, item.level(), "exactly one level");
            if (item.cursed) {
                kept++;
            } else {
                lifted++;
            }
        }
        assertTrue(kept > 0, "some upgrades leave the curse in place");
        assertTrue(lifted > 0, "some upgrades lift it");
        assertEquals(1 / 3.0, lifted / 300.0, 0.08, "about one in three");
        // A clean item stays clean.
        Item clean = make.get();
        for (long seed = 1; seed <= 20; seed++) {
            Random.pushGenerator(seed);
            try {
                readOnto(clean);
            } finally {
                Random.popGenerator();
            }
            assertFalse(clean.cursed);
        }
    }
}
