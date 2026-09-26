package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test-item Policy (story 4.10): an unidentified potion is drunk when the knowledge has a use --
 * the hero at half health or below, healing likely enough, a quarter of the hit points left after the
 * worst case -- at the cell that makes that worst case smallest; a scroll is read at full health when
 * its item-picker identities are unlikely; never in a fight or in harm, never twice on a floor that
 * refused it; the hero steps out of its own cloud, through the door it was credited with, and rests.
 */
class TestItemPolicyTest {

    /** Liquid flame or healing, even odds: at depth 1, 30 dry, 9 beside water, 6 on it. */
    private static final Codex.Knowledge FLAME = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("jade potion", "azure potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfLiquidFlame", "potion of liquid flame", 3),
                            new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6)))),
            List.of(), List.of());

    /** Toxic gas or healing: at depth 1, 10 in the open and 3 beside a door with a way through. */
    private static final Codex.Knowledge GAS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("jade potion", "azure potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfToxicGas", "potion of toxic gas", 3),
                            new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6)))),
            List.of(), List.of());

    /** A potion that is healing one time in ten: under {@link TestItem#HEALING_ODDS}. */
    private static final Codex.Knowledge RARE_HEALING = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("jade potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 1),
                            new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 9)))),
            List.of(), List.of());

    /**
     * Scrolls mostly without an item picker: identify one in seven (under {@link TestItem#INVENTORY_ODDS}),
     * magic mapping and teleportation the rest. {@link Screens#CODEX}'s scrolls are all item-picker
     * scrolls (upgrade and identify).
     */
    /** Crimson potions that are healing one time in two, and never strength or experience. */
    private static final Codex.Knowledge NO_GAINS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 1),
                            new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 1)))),
            List.of(), List.of());

    /** Crimson potions that are strength one time in four (story 4.13's gains). */
    private static final Codex.Knowledge GAINS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfStrength", "potion of strength", 1),
                            new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 3)))),
            List.of(), List.of());

    private static final Codex.Knowledge SCROLLS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.SCROLL, List.of("scroll of KAUNAN", "scroll of SOWILO"),
                    List.of(new Codex.Candidate("items.scrolls.ScrollOfIdentify", "scroll of identify", 1),
                            new Codex.Candidate("items.scrolls.ScrollOfMagicMapping", "scroll of magic mapping", 3),
                            new Codex.Candidate("items.scrolls.ScrollOfTeleportation", "scroll of teleportation", 3)))),
            List.of(), List.of());

    private static Brain brain(Codex.Knowledge knowledge) {
        return new Brain(knowledge, Screens.WEIGHTS, 7L);
    }

    private static List<Tile> row(int cells, int water, int door) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(cells, Tile.EMPTY));
        if (water >= 0) {
            tiles.set(water, Tile.WATER);
        }
        if (door >= 0) {
            tiles.set(door, Tile.DOOR);
        }
        return tiles;
    }

    /** The Steps a one-row floor offers from {@code hero}. */
    private static List<Action> steps(int hero, int cells) {
        List<Action> steps = new ArrayList<>();
        if (hero > 0) {
            steps.add(new Action.Step(hero - 1));
        }
        if (hero < cells - 1) {
            steps.add(new Action.Step(hero + 1));
        }
        return steps;
    }

    private static Action drink(int index, String name, int quantity) {
        return new Action.UseItem(new ItemRef(index, name, quantity), TestItem.DRINK);
    }

    private static Action read(int index, String name, int quantity) {
        return new Action.UseItem(new ItemRef(index, name, quantity), TestItem.READ);
    }

    /** A one-row floor, the hero on its cell, holding {@code items}, offering its Steps and {@code more}. */
    private static Observation screen(int depth, List<Tile> tiles, HeroSection hero, List<ItemView> items,
                                      List<ActorView> actors, List<BlobCell> blobs, List<KnownAppearance> known,
                                      Action... more) {
        List<Action> offered = new ArrayList<>(steps(hero.cell(), tiles.size()));
        offered.addAll(List.of(more));
        return Screens.lab(depth, tiles.size(), tiles, blobs, hero, items, actors, known, offered.toArray(Action[]::new));
    }

    private static Brain.Decided decide(Brain brain, Observation screen) {
        return brain.decide(screen, brain.update(screen, null));
    }

    private static HeroSection hungry(int cell, int hp, int ht) {
        return new HeroSection(cell, "", HeroSubclass.NONE, "", 1, 0, 1, hp, ht, 0, 10, 0, 0, 0, Hunger.HUNGRY,
                List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
    }

    /** A memory as it is {@code waits} waits after a test, with this refuge and these balks and clouds. */
    private static Memory after(long waits, long tested, int refuge, List<Memory.Balk> balked, List<Memory.Cloud> clouds) {
        return new Memory(waits, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, true, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, balked, 0, tested, clouds, refuge);
    }

    /** The screen with {@code cell} remembered but not in view. */
    private static Observation hide(Observation screen, int cell) {
        org.shatterfish.api.MapSection map = screen.map();
        List<org.shatterfish.api.Fog> fog = new ArrayList<>(map.fog());
        fog.set(cell, org.shatterfish.api.Fog.VISITED);
        org.shatterfish.api.MapSection hidden = new org.shatterfish.api.MapSection(map.width(), map.height(), map.tiles(),
                fog, map.traps(), map.heaps(), map.blobs(), map.feeling(), map.transitions());
        return new Observation(screen.header(), hidden, screen.actors(), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), screen.actions(), screen.prompt());
    }

    @Test
    @DisplayName("an unknown potion is drunk at half health, plainly, and the reason names it")
    void safe_potion_drunk() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation here = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
        Brain.Decided decided = decide(brain(Screens.CODEX), here);
        assertEquals("test-item", decided.decision().policy());
        assertEquals(drink(0, "crimson potion", 1), decided.action());
        assertEquals("test: crimson potion", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("a potion that can only heal is drunk only at half health or below, and only when healing is likely enough")
    void potion_needs_a_use() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        TestItem policy = new TestItem(NO_GAINS);
        for (int hp : new int[]{20, 11}) {
            Observation whole = screen(1, row(3, -1, -1), Screens.heroAt(1, hp, 20, List.of()), List.of(crimson),
                    List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
            assertFalse(policy.enters(whole, Memory.START), hp + " of 20");
        }
        Observation half = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
        assertTrue(policy.enters(half, Memory.START), "10 of 20");
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        Observation rare = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        assertFalse(new TestItem(RARE_HEALING).enters(rare, Memory.START), "healing one time in ten");
    }

    @Test
    @DisplayName("a potion likely enough to be strength or experience is drunk at any health, the reserve kept (story 4.13)")
    void potion_for_gains() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        TestItem policy = new TestItem(GAINS);
        for (int hp : new int[]{20, 15, 10}) {
            Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, hp, 20, List.of()), List.of(crimson),
                    List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
            assertTrue(policy.enters(screen, Memory.START), hp + " of 20: strength one time in four");
        }
        Observation none = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
        assertFalse(new TestItem(NO_GAINS).enters(none, Memory.START), "no strength or experience among the candidates");
        assertTrue(TestItem.GAIN_ODDS <= 0.25, "one time in four is enough");
    }

    @Test
    @DisplayName("a scroll is read only at full health, and only while its item-picker identities are unlikely")
    void scroll_needs_a_use() {
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        Observation whole = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
        assertTrue(new TestItem(SCROLLS).enters(whole, Memory.START), "identify one in seven, full health");
        Observation hurt = screen(1, row(3, -1, -1), Screens.heroAt(1, 19, 20, List.of()), List.of(kaunan),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
        assertFalse(new TestItem(SCROLLS).enters(hurt, Memory.START), "19 of 20: Lullaby would not be harmless");
        assertFalse(new TestItem(Screens.CODEX).enters(whole, Memory.START),
                "upgrade or identify: a read would be spent on the picker");
    }

    @Test
    @DisplayName("never in a fight: an enemy in view keeps the Policy out")
    void not_in_a_fight() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation fight = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(crimson),
                List.of(Screens.enemy("rat", 2)), List.of(), List.of(), drink(0, "crimson potion", 1));
        assertNotEquals("test-item", decide(brain(Screens.CODEX), fight).decision().policy());
        assertFalse(new TestItem(Screens.CODEX).enters(fight, Memory.START));
    }

    @Test
    @DisplayName("never a test while standing in fire or gas, escaping or not")
    void no_test_in_harm() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation inGas = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(crimson),
                List.of(), List.of(new BlobCell(1, List.of("ToxicGas"))), List.of(), drink(0, "crimson potion", 1));
        TestItem policy = new TestItem(Screens.CODEX);
        assertFalse(policy.enters(inGas, Memory.START), "another's cloud: no escape, and no test");
        assertNull(policy.plan(inGas, Memory.START, inGas.actions().actions()));
        Memory spent = after(TestItem.ESCAPE_WAITS + 5, 0, -1, List.of(), List.of());
        assertNull(policy.plan(inGas, spent, inGas.actions().actions()), "the escape's waits over: still no test");
    }

    @Test
    @DisplayName("it ranks below heal, fight and eat and above pick-up, equip and explore")
    void placement() {
        List<String> policies = brain(Screens.CODEX).policies();
        assertTrue(policies.indexOf("test-item") > policies.indexOf("eat"), policies.toString());
        assertTrue(policies.indexOf("test-item") > policies.indexOf("heal"), policies.toString());
        assertTrue(policies.indexOf("test-item") < policies.indexOf("pick-up"), policies.toString());
        assertTrue(policies.indexOf("test-item") < policies.indexOf("explore"), policies.toString());
    }

    @Test
    @DisplayName("lethal here, a reserve left on water five Steps away: it walks there, one Step a wait, and drinks on the water")
    void lethal_here_walks_to_water() {
        Brain brain = brain(FLAME);
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        List<Tile> tiles = row(7, 6, -1);
        Belief belief = null;
        List<Action> taken = new ArrayList<>();
        for (int hero = 1; hero <= 6; hero++) {
            Observation screen = screen(1, tiles, Screens.heroAt(hero, 20, 40, List.of()), List.of(jade), List.of(),
                    List.of(), List.of(), drink(0, "jade potion", 1));
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertEquals("test-item", decided.decision().policy(), "at " + hero);
            taken.add(decided.action());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(List.of(new Action.Step(2), new Action.Step(3), new Action.Step(4), new Action.Step(5),
                new Action.Step(6), drink(0, "jade potion", 1)), taken);
    }

    @Test
    @DisplayName("survivable is not enough for a potion: a quarter of the hit points must be left after the worst case")
    void reserve_margin() {
        assertTrue(TestItem.reserved(20, 40, 10));
        assertFalse(TestItem.reserved(20, 40, 11));
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        Observation onWater = screen(1, row(3, 1, -1), Screens.heroAt(1, 8, 20, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        TestItem policy = new TestItem(FLAME);
        assertTrue(SafeTest.of(SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of liquid flame", 0.5), new Beliefs.Odds("potion of healing", 0.5))),
                FLAME), onWater).safe(), "6 against 8 is survivable");
        assertNull(policy.plan(onWater, Memory.START, onWater.actions().actions()), "but leaves 2 of 20: no drink");
    }

    @Test
    @DisplayName("lethal everywhere in reach: not tried, and the Policy does not take the wait")
    void lethal_everywhere_untested() {
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        Observation weak = screen(1, row(7, 6, -1), Screens.heroAt(1, 5, 40, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        assertNull(new TestItem(FLAME).plan(weak, Memory.START, weak.actions().actions()));
        assertNotEquals("test-item", decide(brain(FLAME), weak).decision().policy());
        Observation farWater = screen(1, row(12, 11, -1), Screens.heroAt(1, 20, 40, List.of()), List.of(jade),
                List.of(), List.of(), List.of(), drink(0, "jade potion", 1));
        assertNull(new TestItem(FLAME).plan(farWater, Memory.START, farWater.actions().actions()),
                "water ten Steps away is beyond reach");
    }

    @Test
    @DisplayName("a door with a way through beside the cell cuts toxic gas's worst case; the Policy steps beside it to drink")
    void door_shortens_gas() {
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        List<Tile> tiles = row(4, -1, 2);
        Observation screen = screen(1, tiles, Screens.heroAt(0, 14, 40, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        List<SafeTest.Candidate> candidates = SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of toxic gas", 0.5), new Beliefs.Odds("potion of healing", 0.5))), GAS);
        assertEquals(10, SafeTest.of(candidates, screen, 0).worst().damage(), "ten turns in the open");
        assertEquals(3, SafeTest.of(candidates, screen, 1).worst().damage(), "three beside the door");
        assertEquals(3, SafeTest.refuge(screen, 1), "the refuge is the cell beyond the door");
        Brain.Decided decided = decide(brain(GAS), screen);
        assertEquals(new Action.Step(1), decided.action());
        assertEquals("cell: jade potion", decided.decision().chosen().why());
        Observation beside = screen(1, tiles, Screens.heroAt(1, 14, 40, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        Brain brain = brain(GAS);
        Belief belief = brain.update(beside, null);
        Brain.Decided drunk = brain.decide(beside, belief);
        assertEquals(drink(0, "jade potion", 1), drunk.action());
        assertEquals(3, Memory.of(brain.handed(beside, belief, drunk)).refuge(), "the drink records the refuge");
    }

    @Test
    @DisplayName("a door with no way through is no credit: the escape could not leave the gas behind it")
    void door_without_a_way_through() {
        List<Tile> tiles = row(4, -1, 2);
        tiles.set(3, Tile.WALL);
        Observation walled = screen(1, tiles, Screens.heroAt(0, 20, 40, List.of()), List.of(), List.of(), List.of(),
                List.of());
        List<SafeTest.Candidate> candidates = SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of toxic gas", 0.5), new Beliefs.Odds("potion of healing", 0.5))), GAS);
        assertEquals(-1, SafeTest.refuge(walled, 1));
        assertEquals(10, SafeTest.of(candidates, walled, 1).worst().damage());
    }

    @Test
    @DisplayName("the door the hero stands in counts: it shuts behind the hero; a door held open does not")
    void doorway_counts() {
        List<SafeTest.Candidate> candidates = SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of toxic gas", 0.5), new Beliefs.Odds("potion of healing", 0.5))), GAS);
        List<Tile> open = row(4, -1, -1);
        open.set(2, Tile.OPEN_DOOR);
        Observation inTheDoor = screen(1, open, Screens.heroAt(2, 20, 20, List.of()), List.of(), List.of(), List.of(),
                List.of());
        assertEquals(3, SafeTest.of(candidates, inTheDoor, 1).worst().damage(), "the hero's own doorway shuts behind it");
        Observation shutBeside = screen(1, row(4, -1, 2), Screens.heroAt(0, 20, 20, List.of()), List.of(), List.of(),
                List.of(), List.of());
        assertEquals(3, SafeTest.of(candidates, shutBeside, 1).worst().damage(), "the same cell, the door shut");
        Observation openBeside = screen(1, open, Screens.heroAt(0, 20, 20, List.of()), List.of(), List.of(), List.of(),
                List.of());
        assertEquals(10, SafeTest.of(candidates, openBeside, 1).worst().damage(),
                "a door open with nobody the screen shows in it is held open: no refuge");
    }

    @Test
    @DisplayName("worth most first: the appearance with more copies held goes before one with fewer")
    void most_worth_first() {
        ItemView amber = Screens.unknown(ItemKind.POTION, "amber potion", 1);
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 2);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(amber, crimson),
                List.of(), List.of(), List.of(), drink(0, "amber potion", 1), drink(1, "crimson potion", 2));
        assertEquals(drink(1, "crimson potion", 2), decide(brain(Screens.CODEX), screen).action());
    }

    @Test
    @DisplayName("what a test identifies narrows every other appearance's candidates through the journal")
    void candidates_narrow() {
        ItemView amber = Screens.unknown(ItemKind.POTION, "amber potion", 1);
        Observation before = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(amber),
                List.of(), List.of(), List.of());
        Observation after = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(amber),
                List.of(), List.of(), List.of(new KnownAppearance(ItemKind.POTION, "potion of healing")));
        List<String> was = Beliefs.identities(before, Screens.CODEX).get(0).odds().stream().map(Beliefs.Odds::name).toList();
        List<String> is = Beliefs.identities(after, Screens.CODEX).get(0).odds().stream().map(Beliefs.Odds::name).toList();
        assertTrue(was.contains("potion of healing"), was.toString());
        assertFalse(is.contains("potion of healing"), is.toString());
        assertEquals(was.size() - 1, is.size());
    }

    @Test
    @DisplayName("a test the game did not carry out is balked at: not tried again on that floor, tried again on the next")
    void refused_test_balks() {
        Brain brain = brain(SCROLLS);
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
        assertEquals(read(0, "scroll of KAUNAN", 1), decide(brain, screen).action(), "the first time, it reads");
        List<String> policies = new ArrayList<>();
        Belief belief = null;
        for (int wait = 0; wait < 6; wait++) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            policies.add(decided.decision().policy());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals("test-item", policies.get(0));
        assertTrue(policies.subList(1, 6).stream().noneMatch("test-item"::equals), policies.toString());
        Observation below = screen(2, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
        belief = brain.update(below, belief);
        assertEquals("test-item", brain.decide(below, belief).decision().policy(), "a new floor tries it again");
    }

    @Test
    @DisplayName("a test that happened is no balk: the appearance left the pack")
    void carried_out_is_no_balk() {
        Brain brain = brain(SCROLLS);
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 2);
        ItemView sowilo = Screens.unknown(ItemKind.SCROLL, "scroll of SOWILO", 1);
        Observation before = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan, sowilo),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 2), read(1, "scroll of SOWILO", 1));
        assertEquals(read(0, "scroll of KAUNAN", 2), decide(brain, before).action());
        Observation after = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()),
                List.of(Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1), sowilo), List.of(), List.of(),
                List.of(), read(0, "scroll of KAUNAN", 1), read(1, "scroll of SOWILO", 1));
        Belief belief = Screens.drive(brain, before, after);
        assertEquals(List.of(), Memory.of(belief).balked(), "one of two was read: carried out");
    }

    @Test
    @DisplayName("an appearance with one candidate left is known by elimination and not tried, even healing at half health")
    void single_candidate_skipped() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 10, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(new KnownAppearance(ItemKind.POTION, "potion of strength"),
                        new KnownAppearance(ItemKind.POTION, "potion of mind vision"),
                        new KnownAppearance(ItemKind.POTION, "potion of frost")),
                drink(0, "crimson potion", 1));
        assertEquals("potion of healing", Beliefs.identities(screen, Screens.CODEX).get(0).odds().get(0).name());
        assertEquals(1, Beliefs.identities(screen, Screens.CODEX).get(0).odds().size());
        assertFalse(new TestItem(Screens.CODEX).enters(screen, Memory.START));
    }

    @Test
    @DisplayName("a scroll is read plainly, never onto another item: an unknown upgrade read onto one opens a window no Action answers")
    void scroll_read_plainly() {
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        ItemView tome = Screens.using("holy tome", "DROP");
        ItemRef kaunanRef = new ItemRef(0, "scroll of KAUNAN", 1);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan, tome),
                List.of(), List.of(), List.of(), new Action.UseItemOn(kaunanRef, TestItem.READ,
                        new ItemRef(1, "holy tome", 1)), read(0, "scroll of KAUNAN", 1));
        assertEquals(read(0, "scroll of KAUNAN", 1), decide(brain(SCROLLS), screen).action());
    }

    @Test
    @DisplayName("not read while blinded or immune to magic, which the game refuses without a turn")
    void no_read_blind() {
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        for (String buff : List.of(TestItem.BLINDED, TestItem.MAGIC_IMMUNE)) {
            Observation screen = screen(1, row(3, -1, -1),
                    Screens.heroAt(1, 20, 20, List.of(new BuffView(buff, false, 0))), List.of(kaunan), List.of(),
                    List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
            assertFalse(new TestItem(SCROLLS).enters(screen, Memory.START), buff);
        }
    }

    @Test
    @DisplayName("the scroll's cancel confirmation is answered yes: no would reopen the picker")
    void cancel_confirmation_answered_yes() {
        // InventoryScroll.WndConfirmCancel (InventoryScroll.java:56-80, items.properties:1155-1157).
        Brain.Decided decided = brain(Screens.CODEX).decide(Screens.prompted(PromptKind.ITEM, "Scroll Of Upgrade",
                Policies.SCROLL_CANCEL, List.of("Yes, I'm positive", "No, I changed my mind"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), null);
        assertEquals(new Action.AnswerPrompt(0), decided.action());
    }

    @Test
    @DisplayName("every other item confirmation is declined: a known harmful potion is not drunk, a beneficial one not thrown")
    void item_confirmations_declined() {
        // Potion.java:239-252 (items.properties:738, :740-742) and :265-281.
        Brain.Decided harmful = brain(Screens.CODEX).decide(Screens.prompted(PromptKind.HARMFUL_POTION,
                "Harmful potion!", "Are you sure you want to drink it? In most cases you should throw such potions at"
                        + " your enemies.", List.of("Yes, I know what I'm doing", "No, I changed my mind"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), null);
        assertEquals(new Action.AnswerPrompt(1), harmful.action());
        assertEquals("decline: No, I changed my mind", harmful.decision().chosen().why());
        Brain.Decided beneficial = brain(Screens.CODEX).decide(Screens.prompted(PromptKind.ITEM, "Beneficial potion",
                "Are you sure you want to throw it?", List.of("Yes, I know what I'm doing", "No, I changed my mind"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), null);
        assertEquals(new Action.AnswerPrompt(1), beneficial.action());
        Brain.Decided other = brain(Screens.CODEX).decide(Screens.prompted(PromptKind.OTHER, "Warp beacon",
                "Where to?", List.of("Yes, I know what I'm doing", "No, I changed my mind"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), null);
        assertEquals(new Action.AnswerPrompt(0), other.action(), "only item Prompts: story 4.11 owns the rest");
    }

    @Test
    @DisplayName("out of its own gas: after its test, one Step a wait toward the nearest clear cell, then nothing to escape")
    void escapes_its_gas() {
        Brain brain = brain(Screens.CODEX);
        List<Tile> tiles = row(5, -1, -1);
        List<BlobCell> gas = List.of(new BlobCell(0, List.of("ToxicGas")), new BlobCell(1, List.of("ToxicGas")),
                new BlobCell(2, List.of("ToxicGas")));
        Observation drinking = screen(1, tiles, Screens.heroAt(1, 10, 20, List.of()),
                List.of(Screens.unknown(ItemKind.POTION, "crimson potion", 1)), List.of(), List.of(), List.of(),
                drink(0, "crimson potion", 1));
        Belief belief = brain.update(drinking, null);
        Brain.Decided drunk = brain.decide(drinking, belief);
        assertEquals(drink(0, "crimson potion", 1), drunk.action());
        belief = brain.handed(drinking, belief, drunk);
        List<Action> taken = new ArrayList<>();
        List<String> why = new ArrayList<>();
        for (int hero = 1; hero <= 4; hero++) {
            Observation screen = screen(1, tiles, Screens.heroAt(hero, 20, 20, List.of()), List.of(), List.of(), gas,
                    List.of());
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            if (hero < 4) {
                assertEquals("test-item", decided.decision().policy(), "in the gas or at its edge at " + hero);
                why.add(decided.decision().chosen().why());
            } else {
                assertNotEquals("test-item", decided.decision().policy(), "clear of it");
            }
            taken.add(decided.action());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(List.of(new Action.Step(2), new Action.Step(3), new Action.Step(4)), taken.subList(0, 3));
        assertEquals(List.of("escape: ToxicGas", "escape: ToxicGas", "escape: edge"), why);
    }

    @Test
    @DisplayName("after its test the hero leaves the cloud's edge before it rests or drinks again, over several screens")
    void no_rest_or_test_at_the_edge() {
        Brain brain = brain(Screens.CODEX);
        List<Tile> tiles = row(8, -1, -1);
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        ItemView amber = Screens.unknown(ItemKind.POTION, "amber potion", 1);
        Observation drinking = screen(1, tiles, Screens.heroAt(1, 10, 20, List.of()), List.of(crimson, amber),
                List.of(), List.of(), List.of(), drink(0, "crimson potion", 1), drink(1, "amber potion", 1));
        Belief belief = brain.update(drinking, null);
        Brain.Decided drunk = brain.decide(drinking, belief);
        assertEquals(drink(0, "crimson potion", 1), drunk.action());
        belief = brain.handed(drinking, belief, drunk);
        // The cloud grows from 0-2 to 0-3 and stays; the hero, hurt and holding amber, is at 1, 2, 3, 4, 5.
        List<String> why = new ArrayList<>();
        int[] reach = {2, 3, 3, 3, 3};
        for (int wait = 0; wait < reach.length; wait++) {
            int hero = 1 + wait;
            List<BlobCell> gas = new ArrayList<>();
            for (int cell = 0; cell <= reach[wait]; cell++) {
                gas.add(new BlobCell(cell, List.of("ToxicGas")));
            }
            Observation screen = screen(1, tiles, Screens.heroAt(hero, 10, 20, List.of()), List.of(amber), List.of(),
                    gas, List.of(), drink(0, "amber potion", 1), new Action.Rest(true));
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            why.add(decided.decision().policy() + " " + decided.decision().chosen().why());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(List.of("test-item escape: ToxicGas", "test-item escape: ToxicGas", "test-item escape: ToxicGas",
                "test-item escape: edge", "test-item test: amber potion"), why,
                "no drink and no rest until the cell and its neighbours are clear");
    }

    @Test
    @DisplayName("while a cloud is about, the escape carries on to the refuge beyond the credited door")
    void escape_carries_on_to_the_refuge() {
        List<Tile> tiles = row(7, -1, -1);
        Memory tested = Memory.START.trying(new Memory.Trial("jade potion", 1, -1), 6);
        Observation onTheWay = screen(1, tiles, Screens.heroAt(4, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(0, List.of("ToxicGas"))), List.of());
        assertTrue(TestItem.escaping(onTheWay, tested), "clear here, but not yet through the door");
        assertEquals(new Action.Step(5), TestItem.escape(onTheWay, tested, onTheWay.actions().actions()));
        Observation there = screen(1, tiles, Screens.heroAt(6, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(0, List.of("ToxicGas"))), List.of());
        assertFalse(TestItem.escaping(there, tested), "on the refuge");
        Observation noCloud = screen(1, tiles, Screens.heroAt(4, 20, 20, List.of()), List.of(), List.of(), List.of(),
                List.of());
        assertFalse(TestItem.escaping(noCloud, tested), "no cloud anywhere: a harmless potion, nothing to flee");
    }

    @Test
    @DisplayName("not in a doorway: a hero standing in one holds the door open")
    void not_in_a_doorway() {
        List<Tile> tiles = row(3, -1, -1);
        tiles.set(1, Tile.OPEN_DOOR);
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation inDoor = screen(1, tiles, Screens.heroAt(1, 10, 20, List.of()), List.of(crimson), List.of(),
                List.of(), List.of(), drink(0, "crimson potion", 1), new Action.Rest(true));
        TestItem policy = new TestItem(Screens.CODEX);
        assertFalse(policy.enters(inDoor, Memory.START), "no test in the doorway");
        assertFalse(policy.enters(inDoor, after(3, 0, -1, List.of(), List.of())), "no rest in the doorway");
    }

    @Test
    @DisplayName("no rest on a locked boss floor, where nothing would end it, nor inside a region the fight Policy retreated from")
    void rest_where_it_ends() {
        TestItem policy = new TestItem(Screens.CODEX);
        Memory justTested = after(3, 0, -1, List.of(), List.of());
        Observation locked = screen(1, row(3, -1, -1),
                Screens.heroAt(1, 12, 20, List.of(new BuffView(TestItem.LOCKED, false, 0))), List.of(), List.of(),
                List.of(), List.of(), new Action.Rest(true));
        assertFalse(policy.enters(locked, justTested), "floor is locked");
        Observation open = screen(1, row(3, -1, -1), Screens.heroAt(1, 12, 20, List.of()), List.of(), List.of(),
                List.of(), List.of(), new Action.Rest(true));
        assertTrue(policy.enters(open, justTested));
        Memory retreated = justTested.avoiding(new Memory.Avoid(1, 0, 2, 2, 50));
        assertFalse(policy.enters(open, retreated), "inside the region the fight Policy left");
    }

    @Test
    @DisplayName("no door credit while an ally is in view: it could step into the doorway and hold it open")
    void no_credit_with_an_ally() {
        ActorView ally = new ActorView(0, "sheep", org.shatterfish.api.Alignment.ALLY, 1, false,
                org.shatterfish.api.Emote.NONE, List.of());
        Observation withAlly = screen(1, row(4, -1, 2), Screens.heroAt(1, 20, 40, List.of()), List.of(), List.of(ally),
                List.of(), List.of());
        assertEquals(-1, SafeTest.refuge(withAlly, 1));
        Observation alone = screen(1, row(4, -1, 2), Screens.heroAt(1, 20, 40, List.of()), List.of(), List.of(),
                List.of(), List.of());
        assertEquals(3, SafeTest.refuge(alone, 1));
    }

    @Test
    @DisplayName("the escape makes for the refuge beyond the credited door, not the nearest clear cell deeper in the room")
    void escape_to_the_refuge() {
        // Row of 7: the hero in its gas on 3; cells 1 and 5 are the nearest clear cells (2 Steps), and
        // the lower index would win; the refuge is 6.
        Observation inGas = screen(1, row(7, -1, -1), Screens.heroAt(3, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(3, List.of("ToxicGas"))), List.of());
        Memory without = Memory.START.trying(new Memory.Trial("jade potion", 1, -1), -1);
        assertEquals(new Action.Step(2), TestItem.escape(inGas, without, inGas.actions().actions()));
        Memory with = Memory.START.trying(new Memory.Trial("jade potion", 1, -1), 6);
        assertEquals(6, with.refuge());
        assertEquals(new Action.Step(4), TestItem.escape(inGas, with, inGas.actions().actions()));
    }

    @Test
    @DisplayName("the escape aims past the cloud's edge, not to the next cell the gas will reach")
    void escape_past_the_edge() {
        // Row: 0 clean, 1-4 gas (the hero on 2), 5 clean, 6 clean. The nearest clean cell, 0, borders
        // the gas and is where it spreads next; 6 is the nearest with no gas beside it.
        Observation inGas = screen(1, row(7, -1, -1), Screens.heroAt(2, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(1, List.of("ToxicGas")), new BlobCell(2, List.of("ToxicGas")),
                        new BlobCell(3, List.of("ToxicGas")), new BlobCell(4, List.of("ToxicGas"))), List.of());
        Memory justTested = Memory.START.trying(new Memory.Trial("jade potion", 1, -1));
        assertEquals(new Action.Step(3), TestItem.escape(inGas, justTested, inGas.actions().actions()));
    }

    @Test
    @DisplayName("a cloud it did not make, or made more than the escape's waits ago, is not its to leave")
    void other_clouds_are_not_its_to_leave() {
        Observation inGas = screen(1, row(5, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(1, List.of("ToxicGas"))), List.of());
        TestItem policy = new TestItem(Screens.CODEX);
        assertFalse(policy.enters(inGas, Memory.START), "no test handed over: another Policy's cloud");
        Memory justTested = Memory.START.trying(new Memory.Trial("crimson potion", 1, -1));
        assertEquals(0, justTested.tested());
        assertTrue(policy.enters(inGas, justTested));
        assertFalse(policy.enters(inGas, after(TestItem.ESCAPE_WAITS + 1, 0, -1, List.of(), List.of())),
                "the escape's waits are over");
        assertEquals(-1, Memory.START.trying(new Memory.Trial("crimson potion", 1, 3)).tested(),
                "a Step toward a testing cell is not a test");
    }

    @Test
    @DisplayName("burning off water: toward the nearest water; on water, nothing to escape")
    void burning_to_water() {
        List<BuffView> burning = List.of(new BuffView(TestItem.BURNING, true, 800));
        List<Tile> tiles = row(5, 4, -1);
        Observation dry = screen(1, tiles, Screens.heroAt(1, 20, 20, burning), List.of(), List.of(), List.of(),
                List.of());
        Memory justTested = Memory.START.trying(new Memory.Trial("jade potion", 1, -1));
        TestItem.Plan plan = new TestItem(Screens.CODEX).plan(dry, justTested, dry.actions().actions());
        assertEquals(new Action.Step(2), plan.choice().action());
        assertEquals("escape: burning", plan.choice().why());
        Observation wet = screen(1, tiles, Screens.heroAt(4, 20, 20, burning), List.of(), List.of(), List.of(),
                List.of());
        assertFalse(TestItem.inHarm(wet));
    }

    @Test
    @DisplayName("after a test, short of full health on a calm screen: rest, for a bounded while, and not when hungry")
    void rest_after_a_test() {
        TestItem policy = new TestItem(Screens.CODEX);
        Observation hurt = screen(1, row(3, -1, -1), Screens.heroAt(1, 12, 20, List.of()), List.of(), List.of(),
                List.of(), List.of(), new Action.Rest(true), new Action.Search());
        Memory justTested = after(3, 0, -1, List.of(), List.of());
        TestItem.Plan plan = policy.plan(hurt, justTested, hurt.actions().actions());
        assertEquals(new Action.Rest(true), plan.choice().action());
        assertEquals("rest: after-test", plan.choice().why());
        assertTrue(policy.enters(hurt, justTested));
        assertFalse(policy.enters(hurt, Memory.START), "no test handed over: nothing owed");
        assertFalse(policy.enters(hurt, after(TestItem.REST_WAITS + 1, 0, -1, List.of(), List.of())), "bounded");
        Observation whole = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(),
                List.of(), List.of(), new Action.Rest(true));
        assertFalse(policy.enters(whole, justTested), "at full health");
        Observation hungry = screen(1, row(3, -1, -1), hungry(1, 12, 20), List.of(), List.of(), List.of(), List.of(),
                new Action.Rest(true));
        assertFalse(policy.enters(hungry, justTested), "a hungry hero does not rest for it");
    }

    @Test
    @DisplayName("the trial, the balks, the clouds and the refuge survive the Belief's bytes")
    void memory_round_trip() {
        Memory memory = Memory.START.trying(new Memory.Trial("scroll of KAUNAN", 2, 5));
        memory = new Memory(memory.waits(), memory.deepest(), memory.facts(), memory.found(), memory.held(),
                memory.known(), memory.labels(), memory.pending(), memory.monsters(), memory.at(), memory.streak(),
                memory.calm(), memory.dwelt(), memory.blocked(), memory.last(), memory.holds(), memory.near(),
                memory.before(), memory.flights(), memory.avoid(), memory.underfoot(), memory.refused(), memory.pack(),
                memory.aim(), memory.drank(), memory.trial(),
                List.of(new Memory.Balk(3, 0, "jade potion", false), new Memory.Balk(3, 0, "azure potion", true)), 4, 9,
                List.of(new Memory.Cloud(3, 0, 12, 40)), 17);
        assertEquals(memory, Memory.of(memory.belief()));
        assertTrue(memory.balks(3, 0, "jade potion"));
        assertFalse(memory.balks(4, 0, "jade potion"));
        assertFalse(memory.balks(3, 0, "azure potion"), "a walk balk does not stop the test");
        assertTrue(memory.balksWalking(3, 0, "azure potion"));
    }

    @Test
    @DisplayName("a cell seen clouded is kept out of until seen clear or its waits pass; a shut door's cell never keeps one")
    void clouds_are_remembered() {
        Brain brain = brain(Screens.CODEX);
        List<Tile> tiles = row(6, -1, -1);
        tiles.set(4, Tile.OPEN_DOOR);
        Observation gassed = screen(1, tiles, Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(2, List.of("ToxicGas")), new BlobCell(4, List.of("ToxicGas"))), List.of());
        Memory seen = Memory.of(brain.update(gassed, null));
        assertTrue(seen.clouded(1, 0, 2, seen.waits()));
        assertTrue(seen.clouded(1, 0, 4, seen.waits()), "an open door holds gas");
        assertFalse(Explore.walkable(gassed, seen)[2], "no Policy walks onto it");
        assertTrue(Explore.walkable(gassed, seen, true, true)[2], "the way out goes through");
        Observation shut = screen(1, row(6, -1, 4), Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(2, List.of("ToxicGas"))), List.of());
        Memory later = Memory.of(brain.update(shut, seen.belief()));
        assertTrue(later.clouded(1, 0, 2, later.waits()), "still showing gas");
        assertFalse(later.clouded(1, 0, 4, later.waits()), "the door shut: solid, it holds none");
        assertTrue(Explore.walkable(shut, later)[4], "so the room behind it is not blocked");
        // Out of view (remembered, not seen), a cloud on a cell that is now a shut door is still dropped.
        Observation unseen = hide(shut, 4);
        Memory reopened = Memory.of(brain.update(unseen, seen.belief()));
        assertFalse(reopened.clouded(1, 0, 4, reopened.waits()), "a shut door out of view holds none either");
        Observation clear = screen(1, row(6, -1, 4), Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(),
                List.of(), List.of());
        Memory cleared = Memory.of(brain.update(clear, later.belief()));
        assertFalse(cleared.clouded(1, 0, 2, cleared.waits()), "seen clear, cleared");
        Memory remembered = after(10, -1, -1, List.of(), List.of(new Memory.Cloud(1, 0, 2, 10 + Memory.CLOUD_WAITS)));
        assertFalse(Explore.walkable(clear, remembered)[2], "unseen, kept out of");
        Memory lapsed = after(11 + Memory.CLOUD_WAITS, -1, -1, List.of(), remembered.clouds());
        assertTrue(Explore.walkable(clear, lapsed)[2], "until the cloud's waits pass");
    }

    @Test
    @DisplayName("a walk toward testing cells that goes on past the bound balks the walking only: the test on the spot is still allowed")
    void long_walk_balks() {
        Observation screen = screen(1, row(7, -1, -1), Screens.heroAt(5, 20, 20, List.of()),
                List.of(Screens.unknown(ItemKind.POTION, "jade potion", 1)), List.of(), List.of(), List.of());
        Memory walked = new Memory(4, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(1, 0, 4), 0, true, List.of(), List.of(), "Step", 0, -1, -1, List.of(), List.of(), "",
                List.of(), Memory.Pack.NONE, Memory.Aim.NONE, -1, new Memory.Trial("jade potion", 1, 5), List.of(),
                TestItem.WALKS, -1, List.of(), -1);
        Memory after = Beliefs.fold(walked, screen, FLAME);
        assertTrue(after.balksWalking(1, 0, "jade potion"), "one more Step than the bound");
        assertFalse(after.balks(1, 0, "jade potion"), "the test itself is not balked");
        Memory shorter = new Memory(4, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(1, 0, 4), 0, true, List.of(), List.of(), "Step", 0, -1, -1, List.of(), List.of(), "",
                List.of(), Memory.Pack.NONE, Memory.Aim.NONE, -1, new Memory.Trial("jade potion", 1, 5), List.of(), 3, -1,
                List.of(), -1);
        Memory fine = Beliefs.fold(shorter, screen, FLAME);
        assertFalse(fine.balksWalking(1, 0, "jade potion"));
        assertEquals(4, fine.walking(), "the Step carried out counts");
        // Walking balked, with water two Steps away: the drink where the hero stands, if it fits.
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        Observation besideWater = screen(1, row(7, 6, -1), Screens.heroAt(5, 20, 40, List.of()), List.of(jade),
                List.of(), List.of(), List.of(), drink(0, "jade potion", 1));
        Memory balked = after(4, -1, -1, List.of(new Memory.Balk(1, 0, "jade potion", true)), List.of());
        assertEquals(drink(0, "jade potion", 1),
                new TestItem(FLAME).plan(besideWater, balked, besideWater.actions().actions()).choice().action(),
                "beside the water, nine against twenty of forty: drunk there, not walked on");
    }
}
