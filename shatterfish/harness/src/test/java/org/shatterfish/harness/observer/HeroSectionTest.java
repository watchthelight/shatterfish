package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AdrenalineSurge;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Weakness;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.TalentView;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hero section is what the status pane, the hero window, the talents pane, the bag window and
 * the quickslots show (ADR-0005; ADR-0006, Hero buffs): the numbers the pane prints, the buffs
 * with an icon, the talents of the tiers the pane shows, the six quickslots, and hunger as the
 * icon's three states with the value behind it absent.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HeroSectionTest {

    private static final long SEED = 14_142_135L;

    private HeadlessDriver driver;
    private Hero hero;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        hero = Dungeon.hero;
    }

    @Test
    @DisplayName("the hero section is the HUD's numbers, the pane's talents and the toolbar's quickslots")
    void the_hero_as_the_hud_shows_it() {
        atTheFirstWait();
        HeroSection section = new Observer().hero();
        assertEquals(hero.pos, section.cell());
        assertEquals(hero.name(), section.name());
        assertEquals(HeroSubclass.NONE, section.subclass());
        assertEquals("", section.ability());
        assertEquals(hero.lvl, section.level());
        assertEquals(hero.exp, section.exp());
        assertEquals(hero.maxExp(), section.expToLevel());
        assertEquals(hero.HP, section.hp());
        assertEquals(hero.HT, section.ht());
        assertEquals(hero.shielding(), section.shield());
        assertEquals(hero.STR, section.strength());
        assertEquals(hero.STR() - hero.STR, section.strengthBonus());
        assertEquals(Dungeon.gold, section.gold());
        assertEquals(Dungeon.energy, section.energy());
        assertEquals(org.shatterfish.api.Hunger.NONE, section.hunger());

        // Level 1: the first tier alone is shown, every talent of it at no points (TalentsPane.java:75-86).
        Map<Talent, Integer> tierOne = hero.talents.get(0);
        assertEquals(tierOne.size(), section.talents().size());
        for (Map.Entry<Talent, Integer> entry : tierOne.entrySet()) {
            assertTrue(section.talents().contains(new TalentView(1, entry.getKey().title(), entry.getValue())),
                    entry.getKey().title());
        }
        assertEquals(List.of(hero.talentPointsAvailable(1), hero.talentPointsAvailable(2), hero.talentPointsAvailable(3),
                hero.talentPointsAvailable(4)), section.talentPointsAvailable());

        assertEquals(HeroSection.QUICKSLOTS, section.quickslots().size());
        Item first = Dungeon.quickslot.getItem(0);
        assertNotNull(first, "the Warrior starts with an item in the first quickslot");
        assertEquals(new QuickslotView(first.name(), false), section.quickslots().get(0));
        assertEquals(new QuickslotView("", false), section.quickslots().get(HeroSection.QUICKSLOTS - 1));
    }

    @Test
    @DisplayName("what changes on the screen changes the section: health and shield, gold and energy, a level, buffs, a placeholder")
    void changes_on_screen_change_the_section() {
        atTheFirstWait();
        hero.HP = Math.max(1, hero.HP - 4);
        Buff.affect(hero, Barrier.class).setShield(3);
        Dungeon.gold = 123;
        Dungeon.energy = 4;
        Buff.affect(hero, Weakness.class, 10f);
        Buff.affect(hero, Haste.class, 10f);
        Buff.affect(hero, AdrenalineSurge.class).reset(1, 100f);
        hero.earnExp(hero.maxExp(), Hero.class);
        Item placeholder = new Torch().virtual();
        Dungeon.quickslot.setSlot(1, placeholder);
        assertTrue(Dungeon.quickslot.isPlaceholder(1), "an item of no quantity is a placeholder (QuickSlot.java:72-74)");

        HeroSection section = new Observer().hero();
        assertEquals(hero.HP, section.hp());
        assertEquals(3, section.shield());
        assertEquals(123, section.gold());
        assertEquals(4, section.energy());
        assertEquals(hero.STR, section.strength());
        assertEquals(hero.STR() - hero.STR, section.strengthBonus());
        assertEquals(1, section.strengthBonus(), "a surge prints a bonus after the strength (Hero.java:271-285; WndHero.java:188-190)");
        assertEquals(2, section.level());
        assertEquals(hero.exp, section.exp());
        assertEquals(hero.maxExp(), section.expToLevel());
        assertEquals(List.of(hero.talentPointsAvailable(1), hero.talentPointsAvailable(2), hero.talentPointsAvailable(3),
                hero.talentPointsAvailable(4)), section.talentPointsAvailable());
        assertTrue(section.talentPointsAvailable().get(0) > 0, "a level-2 hero has a talent point to spend (Hero.java:387-396)");
        assertEquals(new QuickslotView(placeholder.name(), true), section.quickslots().get(1));

        Weakness weakness = hero.buff(Weakness.class);
        Haste haste = hero.buff(Haste.class);
        assertTrue(weakness.icon() != BuffIndicator.NONE && haste.icon() != BuffIndicator.NONE);
        assertTrue(section.buffs().contains(new BuffView(haste.name(), true, Math.round(haste.visualcooldown() * 100f))),
                "a flavour buff's turns as its description prints them: " + section.buffs());
        assertTrue(section.buffs().stream().anyMatch(b -> b.name().equals(weakness.name())));
        for (Buff buff : hero.buffs()) {
            assertEquals(buff.icon() != BuffIndicator.NONE, section.buffs().stream().anyMatch(b -> b.name().equals(buff.name())),
                    buff.name() + ": listed exactly when it has an icon (WndHero.java:301-314)");
        }
    }

    @Test
    @DisplayName("hunger is the icon's three states, and the value behind it is absent")
    void hunger_is_the_icon() {
        atTheFirstWait();
        Hunger hunger = hero.buff(Hunger.class);
        assertNotNull(hunger);
        assertEquals(org.shatterfish.api.Hunger.NONE, new Observer().hero().hunger());

        hunger.affectHunger(-Hunger.HUNGRY);
        assertEquals(BuffIndicator.HUNGER, hunger.icon());
        assertEquals(org.shatterfish.api.Hunger.HUNGRY, new Observer().hero().hunger());

        hunger.affectHunger(-(Hunger.STARVING - Hunger.HUNGRY));
        assertEquals(BuffIndicator.STARVATION, hunger.icon());
        Observer observer = new Observer();
        HeroSection section = observer.hero();
        assertEquals(org.shatterfish.api.Hunger.STARVING, section.hunger());
        String json = Skeleton.around(observer.header(), observer.map(), observer.actors(), section).json();
        assertTrue(json.contains("\"hunger\":\"STARVING\""));
        assertFalse(Pattern.compile("\"hunger\":\\d").matcher(json).find(), "no number stands behind the hunger icon");
        assertFalse(json.contains(":" + hunger.hunger() + ",") && hunger.hunger() > 100, "the hunger value is not a field");
    }
}
