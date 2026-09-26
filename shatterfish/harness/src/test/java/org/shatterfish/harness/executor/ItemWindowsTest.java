package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndClericSpells;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTradeItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUpgrade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Prompts;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three windows an item opens that used to end a Run as unknown windows, and the shop's trade
 * window, as Prompts a Brain can answer (story 4.11): the Observer carries what each draws, the
 * valid set offers what a person can do with it, and the executor does it with the tap or the key a
 * person uses. Paths abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/}
 * as {@code …/}, at the tag.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ItemWindowsTest {

    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 24_012_345L;

    private HeadlessDriver driver;
    private Hero hero;
    private final ActionExecutor executor = new ActionExecutor();

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait(HeroClass heroClass) {
        driver = HeadlessDriver.start(SEED, heroClass, RUN_SALT);
        driver.stepToInputWait();
        hero = Dungeon.hero;
    }

    private static ItemRef ref(Observation observation, String name) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).name().equals(name)) {
                return new ItemRef(index, name, items.get(index).quantity());
            }
        }
        throw new AssertionError("the pack holds no " + name + ": " + items);
    }

    /** What the guess window should list: the unknown types of the category, named as its button names them, by name. */
    private static List<String> unknownPotionNames() {
        List<String> names = new ArrayList<>();
        for (Class<? extends Potion> type : Potion.getUnknown()) {
            names.add(Messages.titleCase(Messages.get(type, "name")));
        }
        names.sort(null);
        return names;
    }

    @Test
    @DisplayName("the upgrade window is an upgrade Prompt with its two buttons, and upgrade upgrades the chosen item")
    void the_upgrade_window() {
        atTheFirstWait(HeroClass.WARRIOR);
        ScrollOfUpgrade scroll = new ScrollOfUpgrade();
        scroll.identify();
        assertTrue(scroll.collect());
        Item weapon = hero.belongings.weapon();
        int level = weapon.level();

        // The scroll read onto the worn weapon, the whole input as a person makes it: the read, and the
        // item chosen in the selector it opens (…/items/scrolls/ScrollOfUpgrade.java:55-65).
        Observation before = new Observer().observe();
        Action read = new Action.UseItemOn(ref(before, scroll.name()), "READ", ref(before, weapon.name()));
        assertTrue(before.actions().actions().contains(read), "the read onto the weapon is offered");
        assertInstanceOf(Outcome.Applied.class, executor.execute(before, read));
        driver.stepToInputWait();

        assertInstanceOf(WndUpgrade.class, Windows.front(), "the upgrade window is in front");
        Observation asked = new Observer().observe();
        assertEquals(PromptKind.UPGRADE, asked.prompt().kind());
        assertEquals(PromptKind.UPGRADE, asked.header().prompt());
        assertEquals(Messages.get(WndUpgrade.class, "title"), asked.prompt().title());
        assertEquals(List.of(Messages.get(WndUpgrade.class, "upgrade"), Messages.get(WndUpgrade.class, "back")),
                asked.prompt().options(), "the two buttons (…/windows/WndUpgrade.java:431-480)");
        assertEquals(List.of(new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), asked.actions().actions(),
                "no dismissal: the back key reopens the selector (WndUpgrade.java:497-504)");

        assertInstanceOf(Outcome.Applied.class, executor.execute(asked, new Action.AnswerPrompt(0)));
        driver.stepToInputWait();
        assertNull(Windows.front(), "the window closed");
        assertEquals(level + 1, weapon.level(), "and the weapon was upgraded");
    }

    @Test
    @DisplayName("the guess window lists the unknown types by name, a tap chooses one and the guess button guesses it")
    void the_guess_window() {
        atTheFirstWait(HeroClass.WARRIOR);
        PotionOfStrength potion = new PotionOfStrength();
        assertFalse(potion.isKnown(), "the Warrior does not know strength (HeroClass.java:183)");
        assertTrue(potion.collect());
        StoneOfIntuition stone = new StoneOfIntuition();
        assertTrue(stone.collect());

        Observation before = new Observer().observe();
        Action use = new Action.UseItemOn(ref(before, stone.name()), "USE", ref(before, potion.name()));
        assertTrue(before.actions().actions().contains(use), "the stone used on the potion is offered");
        assertInstanceOf(Outcome.Applied.class, executor.execute(before, use));
        driver.stepToInputWait();

        assertEquals(PromptKind.GUESS, Prompts.kind(Windows.front()));
        Observation asked = new Observer().observe();
        assertEquals(PromptKind.GUESS, asked.prompt().kind());
        assertEquals(Messages.titleCase(potion.name()), asked.prompt().title(),
                "titled with the item (…/items/stones/StoneOfIntuition.java:101-104)");
        List<String> expected = unknownPotionNames();
        assertEquals(expected, asked.prompt().options(), "the unknown types, by name, and no guess button yet");
        assertFalse(asked.prompt().options().contains(Messages.titleCase(Messages.get(
                com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing.class, "name"))),
                "a type the hero knows is not a guess");
        assertTrue(asked.actions().actions().contains(new Action.DismissPrompt()), "the window can be left");

        String strength = Messages.titleCase(Messages.get(PotionOfStrength.class, "name"));
        int icon = asked.prompt().options().indexOf(strength);
        assertInstanceOf(Outcome.Applied.class, executor.execute(asked, new Action.AnswerPrompt(icon)));
        driver.stepToInputWait();

        Observation chosen = new Observer().observe();
        assertEquals(PromptKind.GUESS, chosen.prompt().kind(), "a tap on an icon leaves the window open");
        assertEquals(strength, chosen.prompt().options().get(0),
                "the guess button shows first, with the chosen type's name (StoneOfIntuition.java:196-199)");
        assertEquals(expected.size() + 1, chosen.prompt().options().size());

        assertInstanceOf(Outcome.Applied.class, executor.execute(chosen, new Action.AnswerPrompt(0)));
        driver.stepToInputWait();
        assertNull(Windows.front(), "the guess closed the window");
        assertTrue(potion.isKnown(), "and the right guess identified the type (StoneOfIntuition.java:118-126)");
    }

    @Test
    @DisplayName("the guess window's options do not depend on which unknown item it guesses, and come sorted")
    void the_guess_options_leak_nothing() {
        atTheFirstWait(HeroClass.WARRIOR);
        StoneOfIntuition stone = new StoneOfIntuition();
        PotionOfStrength strength = new PotionOfStrength();
        PotionOfToxicGas gas = new PotionOfToxicGas();

        GameScene.show(stone.new WndGuess(strength));
        List<String> forStrength = new Observer().prompt().options();
        Windows.front().hide();
        GameScene.show(stone.new WndGuess(gas));
        List<String> forGas = new Observer().prompt().options();
        Windows.front().hide();

        assertEquals(forStrength, forGas, "the same list whichever unknown potion is guessed: the true type is not in it");
        List<String> sorted = new ArrayList<>(forStrength);
        sorted.sort(null);
        assertEquals(sorted, forStrength, "listed by name, not in the HashSet's order (Potion.java:407-409)");
        assertEquals(unknownPotionNames(), forStrength);
    }

    @Test
    @DisplayName("the holy tome's spell window is a spell Prompt with no option, left by the back key")
    void the_spell_window() {
        atTheFirstWait(HeroClass.CLERIC);
        HolyTome tome = hero.belongings.getItem(HolyTome.class);
        tome.execute(hero, HolyTome.AC_CAST);
        assertInstanceOf(WndClericSpells.class, Windows.front(), "the tome opens its spell window (HolyTome.java:87-95)");

        Observation asked = new Observer().observe();
        assertEquals(PromptKind.SPELL, asked.prompt().kind());
        assertEquals(List.of(), asked.prompt().options(), "the spells are icons, which the section does not carry");
        assertEquals(List.of(new Action.DismissPrompt()), asked.actions().actions());

        String status = tome.status();
        assertInstanceOf(Outcome.Applied.class, executor.execute(asked, new Action.DismissPrompt()));
        assertNull(Windows.front(), "the back key closed it");
        assertEquals(status, tome.status(), "and cast nothing: the tome's charge as its slot shows it is unchanged");
        assertTrue(hero.ready, "the hero is still waiting for input");
    }

    @Test
    @DisplayName("the shop's trade window can be left, which buys nothing")
    void the_trade_window_can_be_left() {
        atTheFirstWait(HeroClass.WARRIOR);
        Heap heap = new Heap();
        heap.type = Heap.Type.FOR_SALE;
        Food food = new Food();
        heap.drop(food);
        GameScene.show(new WndTradeItem(heap));

        Observation asked = new Observer().observe();
        assertEquals(PromptKind.SHOP, asked.prompt().kind());
        assertTrue(asked.actions().actions().contains(new Action.DismissPrompt()),
                "a shop can be left as well as answered: " + asked.actions().actions());
        int gold = Dungeon.gold;
        assertInstanceOf(Outcome.Applied.class, executor.execute(asked, new Action.DismissPrompt()));
        assertNull(Windows.front());
        assertEquals(gold, Dungeon.gold, "nothing was bought");
        assertEquals(food, heap.peek(), "the item is still for sale");
    }
}
