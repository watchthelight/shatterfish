package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.overlay.PauseOn.Condition;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six Pause-on conditions of FR-45 as a pure function (story 8.3): each has a case where it fires and a
 * case where it does not, two holding at once report the first in the documented order, and a condition that
 * is not enabled never fires. The Observation is a real one from the headless game, at the first Input wait of
 * a seeded Warrior, with the parts a case is about replaced; no game runs during a case.
 *
 * <p>The seed is from the {@code standard} set (the one {@code SewersRunSmokeTest} plays); only the first
 * wait's screen is used, and every part the conditions read is set by the case.
 */
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PauseOnTest {

    /** Entry of the {@code standard} set's Warriors that {@code SewersRunSmokeTest} plays, under its salt. */
    private static final long SEED = 2713922911738L;
    private static final long SALT = 1014L;
    private static final Set<Integer> BOSSES = Set.of(5, 10, 15, 20, 25);
    private static final ItemRef POTION = new ItemRef(0, "pink potion", 1);
    private static final ItemRef SCROLL = new ItemRef(1, "scroll of identify", 1);

    private static Observation base;

    @BeforeAll
    static void boot() {
        base = firstWait();
    }

    /** The seeded Warrior's first Input wait, with a backpack of an unidentified potion and an identified scroll. */
    static Observation firstWait() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT)) {
            driver.stepToInputWait();
            Observation first = new Observer().observe();
            // A backpack of an unidentified potion and an identified scroll, and no Action to check them against.
            ItemView pink = new ItemView(ItemKind.POTION, "pink potion", 1, false, 0, false, false, "", EquipSlot.NONE,
                    List.of("drink"), "drink");
            ItemView identify = new ItemView(ItemKind.SCROLL, "scroll of identify", 1, false, 0, false, false, "",
                    EquipSlot.NONE, List.of("read"), "read");
            JournalSection journal = new JournalSection(first.journal().notes(),
                    List.of(new KnownAppearance(ItemKind.SCROLL, "scroll of identify")));
            return new Observation(first.header(), first.map(), new ActorsSection(List.of()), first.hero(),
                    new InventorySection(List.of(pink, identify)), journal, first.log(), ActionsSection.NONE,
                    first.prompt());
        }
    }

    @AfterAll
    static void forget() {
        base = null;
    }

    private static OverlaySettings only(Condition... conditions) {
        Set<Condition> set = EnumSet.noneOf(Condition.class);
        set.addAll(List.of(conditions));
        return OverlaySettings.DEFAULTS.withPauseOn(set);
    }

    static RunLog.Decision deciding(Action action) {
        return new RunLog.Decision("goal", new RunLog.Choice(action, 1, "why"), List.of(), List.of(), "Policy");
    }

    private static RunLog.Decision resting() {
        return deciding(new Action.Rest(false));
    }

    private static Optional<Condition> first(OverlaySettings settings, Observation observation, RunLog.Decision decision,
                                             PauseOn.Previous previous) {
        return PauseOn.first(settings, observation, decision, previous, BOSSES);
    }

    static Observation onFloor(Observation from, int depth, int branch) {
        HeaderSection h = from.header();
        return new Observation(new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(),
                h.challenges(), depth, branch, h.sealed(), h.oracle(), h.prompt()), from.map(), from.actors(),
                from.hero(), from.inventory(), from.journal(), from.log(), from.actions(), from.prompt());
    }

    static Observation withHp(Observation from, int hp, int ht) {
        HeroSection o = from.hero();
        HeroSection hero = new HeroSection(o.cell(), o.name(), o.subclass(), o.ability(), o.level(), o.exp(),
                o.expToLevel(), hp, ht, o.shield(), o.strength(), o.strengthBonus(), o.gold(), o.energy(), o.hunger(),
                o.buffs(), o.talents(), o.talentPointsAvailable(), o.quickslots());
        return new Observation(from.header(), from.map(), from.actors(), hero, from.inventory(), from.journal(),
                from.log(), from.actions(), from.prompt());
    }

    static Observation withEnemies(Observation from, String... names) {
        int hero = from.hero().cell();
        java.util.ArrayList<ActorView> actors = new java.util.ArrayList<>();
        int cell = 0;
        for (String name : names) {
            while (cell == hero || from.map().fog().get(cell) != Fog.VISIBLE) {
                cell++;
            }
            actors.add(new ActorView(cell++, name, Alignment.ENEMY, 10, false, Emote.NONE, List.of()));
        }
        return new Observation(from.header(), from.map(), new ActorsSection(actors), from.hero(), from.inventory(),
                from.journal(), from.log(), from.actions(), from.prompt());
    }

    @Test
    @DisplayName("before any item use: fires for a use of any shape, not for a step or a rest")
    void before_item_use() {
        OverlaySettings on = only(Condition.ITEM_USE);
        assertEquals(Optional.of(Condition.ITEM_USE), first(on, base, deciding(new Action.UseItem(SCROLL, "read")), null));
        assertEquals(Optional.of(Condition.ITEM_USE),
                first(on, base, deciding(new Action.UseItemAt(SCROLL, "read", base.hero().cell())), null));
        assertEquals(Optional.of(Condition.ITEM_USE),
                first(on, base, deciding(new Action.UseItemOn(SCROLL, "read", POTION)), null));
        assertEquals(Optional.empty(), first(on, base, resting(), null), "a rest uses no item");
        assertEquals(Optional.empty(), first(on, base, null, null), "a Run with no Decision has no use to be before");
    }

    @Test
    @DisplayName("before testing an unknown item: an unidentified potion fires, an identified scroll does not")
    void before_an_unknown_item() {
        OverlaySettings on = only(Condition.UNKNOWN_ITEM);
        assertEquals(Optional.of(Condition.UNKNOWN_ITEM), first(on, base, deciding(new Action.UseItem(POTION, "drink")), null));
        assertEquals(Optional.of(Condition.UNKNOWN_ITEM),
                first(on, base, deciding(new Action.UseItemOn(SCROLL, "read", POTION)), null), "an unknown target counts");
        assertEquals(Optional.empty(), first(on, base, deciding(new Action.UseItem(SCROLL, "read")), null),
                "the journal lists the scroll as identified");
        assertEquals(Optional.empty(), first(on, base, resting(), null));
    }

    @Test
    @DisplayName("before stairs: a Descend or an Ascend fires, a step does not")
    void before_stairs() {
        OverlaySettings on = only(Condition.STAIRS);
        assertEquals(Optional.of(Condition.STAIRS), first(on, base, deciding(new Action.Descend()), null));
        assertEquals(Optional.of(Condition.STAIRS), first(on, base, deciding(new Action.Ascend()), null));
        assertEquals(Optional.empty(), first(on, base, deciding(new Action.Step(base.hero().cell() + 1)), null));
    }

    @Test
    @DisplayName("on boss floors: the first wait on a boss depth fires, the rest of the floor and a plain floor do not")
    void on_boss_floors() {
        OverlaySettings on = only(Condition.BOSS_FLOOR);
        Observation boss = onFloor(base, 5, 0);
        assertEquals(Optional.of(Condition.BOSS_FLOOR),
                first(on, boss, resting(), PauseOn.Previous.of(onFloor(base, 4, 0))), "arriving on the Goo floor");
        assertEquals(Optional.empty(), first(on, boss, resting(), PauseOn.Previous.of(boss)), "already there");
        assertEquals(Optional.empty(), first(on, onFloor(base, 4, 0), resting(), PauseOn.Previous.of(onFloor(base, 3, 0))));
        assertEquals(Optional.empty(), first(on, onFloor(base, 5, 1), resting(), null), "a branch is not the boss floor");
    }

    @Test
    @DisplayName("when HP drops below the threshold: crossing it fires, staying under it or being above it does not")
    void when_hp_drops_below_the_threshold() {
        OverlaySettings on = only(Condition.HP_BELOW).withHpThreshold(30);
        Observation healthy = withHp(base, 20, 20);
        Observation hurt = withHp(base, 5, 20);
        assertEquals(Optional.of(Condition.HP_BELOW), first(on, hurt, resting(), PauseOn.Previous.of(healthy)));
        assertEquals(Optional.empty(), first(on, hurt, resting(), PauseOn.Previous.of(hurt)), "already under it");
        assertEquals(Optional.empty(), first(on, withHp(base, 6, 20), resting(), PauseOn.Previous.of(healthy)),
                "6 of 20 is exactly 30%, which is not below");
        assertEquals(Optional.empty(), first(on, healthy, resting(), PauseOn.Previous.of(healthy)));
        assertEquals("HP below 30%", PauseOn.label(Condition.HP_BELOW, on));
    }

    @Test
    @DisplayName("when a new enemy appears: one more of a name than the wait before fires, the same ones do not")
    void when_a_new_enemy_appears() {
        OverlaySettings on = only(Condition.NEW_ENEMY);
        Observation none = withEnemies(base);
        Observation one = withEnemies(base, "rat");
        Observation two = withEnemies(base, "rat", "rat");
        assertEquals(Optional.of(Condition.NEW_ENEMY), first(on, one, resting(), PauseOn.Previous.of(none)));
        assertEquals(Optional.of(Condition.NEW_ENEMY), first(on, two, resting(), PauseOn.Previous.of(one)), "a second rat");
        assertEquals(Optional.of(Condition.NEW_ENEMY),
                first(on, withEnemies(base, "crab"), resting(), PauseOn.Previous.of(one)), "a crab for a rat");
        assertEquals(Optional.empty(), first(on, one, resting(), PauseOn.Previous.of(one)), "the same rat");
        assertEquals(Optional.empty(), first(on, none, resting(), PauseOn.Previous.of(one)), "an enemy leaving");
        assertEquals(Optional.empty(), first(on, one, resting(), null), "no wait before, nothing is new against it");
    }

    @Test
    @DisplayName("two conditions holding at once report the first in FR-45's order; one not enabled never fires")
    void two_at_once_report_the_first() {
        Observation boss = withEnemies(onFloor(base, 5, 0), "rat");
        PauseOn.Previous before = PauseOn.Previous.of(onFloor(withEnemies(base), 4, 0));
        RunLog.Decision down = deciding(new Action.Descend());
        assertEquals(Optional.of(Condition.STAIRS),
                first(OverlaySettings.all(), boss, down, before), "stairs come before the boss floor, the enemy");
        assertEquals(Optional.of(Condition.BOSS_FLOOR),
                first(only(Condition.NEW_ENEMY, Condition.BOSS_FLOOR), boss, down, before));
        assertEquals(Optional.of(Condition.NEW_ENEMY), first(only(Condition.NEW_ENEMY), boss, down, before));
        assertEquals(Optional.of(Condition.ITEM_USE),
                first(OverlaySettings.all(), base, deciding(new Action.UseItem(POTION, "drink")), null),
                "an item use is reported before the unknown item it also is");
        assertEquals(Optional.empty(), first(OverlaySettings.DEFAULTS, boss, down, before), "all off by default");
    }

    @Test
    @DisplayName("the settings default to every condition off and 30%, and refuse a threshold outside 1 to 99")
    void settings_defaults() {
        assertTrue(OverlaySettings.DEFAULTS.pauseOn().isEmpty());
        assertEquals(30, OverlaySettings.DEFAULTS.hpThresholdPercent());
        assertThrows(IllegalArgumentException.class, () -> OverlaySettings.DEFAULTS.withHpThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> OverlaySettings.DEFAULTS.withHpThreshold(100));
        assertEquals(EnumSet.allOf(Condition.class), OverlaySettings.all().pauseOn());
    }
}
