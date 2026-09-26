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
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
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
 * The test-item Policy (story 4.10): an unidentified potion or scroll is tried where {@code SafeTest}
 * says its worst case is survivable, at the cell that makes that worst case smallest, never in a
 * fight, and never twice on a floor that refused it.
 */
class TestItemPolicyTest {

    /** Liquid flame or healing: at depth 1 on 20 HP, lethal dry (30), 9 beside water, 6 on it. */
    private static final Codex.Knowledge FLAME = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("jade potion", "azure potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfLiquidFlame", "potion of liquid flame", 3),
                            new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6)))),
            List.of(), List.of());

    /** Toxic gas or healing: at depth 1, 10 in the open and 3 beside a closed door. */
    private static final Codex.Knowledge GAS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("jade potion", "azure potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfToxicGas", "potion of toxic gas", 3),
                            new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6)))),
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

    /** A one-row floor of {@code cells}, the hero on {@code hero}, holding {@code items}, offering its Steps and {@code more}. */
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

    @Test
    @DisplayName("a potion whose worst case is survivable here is drunk, plainly, and the reason names it")
    void safe_potion_drunk() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation here = screen(1, row(3, -1, -1), Screens.heroAt(1, 16, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
        Brain.Decided decided = decide(brain(Screens.CODEX), here);
        assertEquals("test-item", decided.decision().policy());
        assertEquals(drink(0, "crimson potion", 1), decided.action());
        assertEquals("test: crimson potion", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("a potion is drunk to test it only at four fifths of the hit points or below, where a healing one heals; a scroll is read whole")
    void whole_does_not_drink() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        TestItem policy = new TestItem(Screens.CODEX);
        for (int hp : new int[]{20, 17}) {
            Observation whole = screen(1, row(3, -1, -1), Screens.heroAt(1, hp, 20, List.of()), List.of(crimson),
                    List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
            assertFalse(policy.enters(whole, Memory.START), hp + " of 20");
        }
        Observation fourFifths = screen(1, row(3, -1, -1), Screens.heroAt(1, 16, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(), drink(0, "crimson potion", 1));
        assertTrue(policy.enters(fourFifths, Memory.START), "16 of 20");
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        Observation reading = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
        assertTrue(policy.enters(reading, Memory.START), "a scroll at full health");
    }

    @Test
    @DisplayName("never in a fight: an enemy in view keeps the Policy out")
    void not_in_a_fight() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation fight = screen(1, row(3, -1, -1), Screens.heroAt(1, 16, 20, List.of()), List.of(crimson),
                List.of(Screens.enemy("rat", 2)), List.of(), List.of(), drink(0, "crimson potion", 1));
        assertNotEquals("test-item", decide(brain(Screens.CODEX), fight).decision().policy());
        assertFalse(new TestItem(Screens.CODEX).enters(fight, Memory.START));
    }

    @Test
    @DisplayName("it ranks first on a calm screen, above pick-up, equip and explore, so it tests at the first calm moment")
    void before_explore() {
        List<String> policies = brain(Screens.CODEX).policies();
        assertTrue(policies.indexOf("test-item") > policies.indexOf("fight"), policies.toString());
        assertTrue(policies.indexOf("test-item") < policies.indexOf("pick-up"), policies.toString());
        assertTrue(policies.indexOf("test-item") < policies.indexOf("explore"), policies.toString());
    }

    @Test
    @DisplayName("lethal here, survivable on water five Steps away: it walks there, one Step a wait, and drinks on the water")
    void lethal_here_walks_to_water() {
        Brain brain = brain(FLAME);
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        List<Tile> tiles = row(7, 6, -1);
        Belief belief = null;
        List<Action> taken = new ArrayList<>();
        for (int hero = 1; hero <= 6; hero++) {
            Observation screen = screen(1, tiles, Screens.heroAt(hero, 16, 20, List.of()), List.of(jade), List.of(),
                    List.of(), List.of(), drink(0, "jade potion", 1));
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertEquals("test-item", decided.decision().policy(), "at " + hero);
            taken.add(decided.action());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(List.of(new Action.Step(2), new Action.Step(3), new Action.Step(4), new Action.Step(5),
                new Action.Step(6), drink(0, "jade potion", 1)), taken);
        assertFalse(SafeTest.of(SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of liquid flame", 0.5), new Beliefs.Odds("potion of healing", 0.5))),
                FLAME), screen(1, tiles, Screens.heroAt(1, 16, 20, List.of()), List.of(jade), List.of(), List.of(),
                List.of())).safe(), "dry, it would have been lethal");
    }

    @Test
    @DisplayName("lethal everywhere in reach: not tried, and the Policy does not take the wait")
    void lethal_everywhere_untested() {
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        Observation weak = screen(1, row(7, 6, -1), Screens.heroAt(1, 5, 20, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        assertNull(new TestItem(FLAME).plan(weak, Memory.START, weak.actions().actions()));
        assertNotEquals("test-item", decide(brain(FLAME), weak).decision().policy());
        Observation farWater = screen(1, row(12, 11, -1), Screens.heroAt(1, 16, 20, List.of()), List.of(jade),
                List.of(), List.of(), List.of(), drink(0, "jade potion", 1));
        assertNull(new TestItem(FLAME).plan(farWater, Memory.START, farWater.actions().actions()),
                "water ten Steps away is beyond reach");
    }

    @Test
    @DisplayName("a closed door beside the cell cuts toxic gas's worst case, and the Policy steps beside the door to drink")
    void door_shortens_gas() {
        ItemView jade = Screens.unknown(ItemKind.POTION, "jade potion", 1);
        List<Tile> tiles = row(3, -1, 2);
        Observation screen = screen(1, tiles, Screens.heroAt(0, 8, 20, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        List<SafeTest.Candidate> candidates = SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of toxic gas", 0.5), new Beliefs.Odds("potion of healing", 0.5))), GAS);
        assertEquals(10, SafeTest.of(candidates, screen, 0).worst().damage(), "ten turns in the open");
        assertEquals(3, SafeTest.of(candidates, screen, 1).worst().damage(), "three beside the door");
        assertFalse(SafeTest.of(candidates, screen).safe(), "10 against 8 where the hero stands");
        Brain.Decided decided = decide(brain(GAS), screen);
        assertEquals(new Action.Step(1), decided.action());
        assertEquals("cell: jade potion", decided.decision().chosen().why());
        Observation beside = screen(1, tiles, Screens.heroAt(1, 8, 20, List.of()), List.of(jade), List.of(),
                List.of(), List.of(), drink(0, "jade potion", 1));
        assertEquals(drink(0, "jade potion", 1), decide(brain(GAS), beside).action());
    }

    @Test
    @DisplayName("worth most first: the appearance with more copies held goes before one with fewer")
    void most_worth_first() {
        ItemView amber = Screens.unknown(ItemKind.POTION, "amber potion", 1);
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 2);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 16, 20, List.of()), List.of(amber, crimson),
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
        Brain brain = brain(Screens.CODEX);
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
        Brain.Decided first = decide(brain, screen);
        assertEquals(read(0, "scroll of KAUNAN", 1), first.action(), "the first time, it reads");
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
        Brain brain = brain(Screens.CODEX);
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 2);
        ItemView sowilo = Screens.unknown(ItemKind.SCROLL, "scroll of SOWILO", 1);
        Observation before = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()), List.of(kaunan, sowilo),
                List.of(), List.of(), List.of(), read(0, "scroll of KAUNAN", 2), read(1, "scroll of SOWILO", 1));
        Observation after = screen(1, row(3, -1, -1), Screens.heroAt(1, 20, 20, List.of()),
                List.of(Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1), sowilo), List.of(), List.of(),
                List.of(), read(0, "scroll of KAUNAN", 1), read(1, "scroll of SOWILO", 1));
        Belief belief = Screens.drive(brain, before, after);
        assertEquals(List.of(), Memory.of(belief).balked(), "one of two was read: carried out");
    }

    @Test
    @DisplayName("an appearance with one candidate left is known by elimination and not tried")
    void single_candidate_skipped() {
        ItemView crimson = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        Observation screen = screen(1, row(3, -1, -1), Screens.heroAt(1, 16, 20, List.of()), List.of(crimson),
                List.of(), List.of(), List.of(new KnownAppearance(ItemKind.POTION, "potion of healing"),
                        new KnownAppearance(ItemKind.POTION, "potion of mind vision"),
                        new KnownAppearance(ItemKind.POTION, "potion of frost")),
                drink(0, "crimson potion", 1));
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
        assertEquals(read(0, "scroll of KAUNAN", 1), decide(brain(Screens.CODEX), screen).action());
    }

    @Test
    @DisplayName("not read while blinded or immune to magic, which the game refuses without a turn")
    void no_read_blind() {
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        for (String buff : List.of(TestItem.BLINDED, TestItem.MAGIC_IMMUNE)) {
            Observation screen = screen(1, row(3, -1, -1),
                    Screens.heroAt(1, 20, 20, List.of(new BuffView(buff, false, 0))), List.of(kaunan), List.of(),
                    List.of(), List.of(), read(0, "scroll of KAUNAN", 1));
            assertFalse(new TestItem(Screens.CODEX).enters(screen, Memory.START), buff);
        }
    }

    @Test
    @DisplayName("an unknown inventory scroll's cancel confirmation is answered yes: no would reopen the bag")
    void cancel_confirmation_answered_yes() {
        // InventoryScroll.WndConfirmCancel (InventoryScroll.java:56-80, items.properties:1155-1157).
        Brain.Decided decided = brain(Screens.CODEX).decide(Screens.titled("Scroll Of Upgrade",
                "Do you really want to cancel this scroll usage? The scroll wasn't previously identified, so it"
                        + " will be consumed anyway.", List.of("Yes, I'm positive", "No, I changed my mind"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), null);
        assertEquals(new Action.AnswerPrompt(0), decided.action());
    }

    @Test
    @DisplayName("out of its own gas: after its test, one Step a wait toward the nearest cell without it, then nothing to escape")
    void escapes_its_gas() {
        Brain brain = brain(Screens.CODEX);
        List<Tile> tiles = row(5, -1, -1);
        List<BlobCell> gas = List.of(new BlobCell(0, List.of("ToxicGas")), new BlobCell(1, List.of("ToxicGas")),
                new BlobCell(2, List.of("ToxicGas")));
        Observation drinking = screen(1, tiles, Screens.heroAt(1, 16, 20, List.of()),
                List.of(Screens.unknown(ItemKind.POTION, "crimson potion", 1)), List.of(), List.of(), List.of(),
                drink(0, "crimson potion", 1));
        Belief belief = brain.update(drinking, null);
        Brain.Decided drunk = brain.decide(drinking, belief);
        assertEquals(drink(0, "crimson potion", 1), drunk.action());
        belief = brain.handed(drinking, belief, drunk);
        List<Action> taken = new ArrayList<>();
        for (int hero = 1; hero <= 3; hero++) {
            Observation screen = screen(1, tiles, Screens.heroAt(hero, 20, 20, List.of()), List.of(), List.of(), gas,
                    List.of());
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            if (hero < 3) {
                assertEquals("test-item", decided.decision().policy(), "in the gas at " + hero);
                assertEquals("escape: ToxicGas", decided.decision().chosen().why());
            } else {
                assertNotEquals("test-item", decided.decision().policy(), "out of it");
            }
            taken.add(decided.action());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(List.of(new Action.Step(2), new Action.Step(3)), taken.subList(0, 2));
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
        Memory later = new Memory(TestItem.ESCAPE_WAITS + 1, 1, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), Memory.Spot.NOWHERE, 0, true, List.of(), List.of(), "", 0, -1, -1, List.of(),
                List.of(), "", List.of(), Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, 0, List.of());
        assertFalse(policy.enters(inGas, later), "the escape's waits are over");
        assertEquals(0, Memory.START.trying(new Memory.Trial("crimson potion", 1, 3)).walking());
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
    @DisplayName("the trial and the balks survive the Belief's bytes")
    void memory_round_trip() {
        Memory memory = Memory.START.trying(new Memory.Trial("scroll of KAUNAN", 2, 5));
        memory = new Memory(memory.waits(), memory.deepest(), memory.facts(), memory.found(), memory.held(),
                memory.known(), memory.labels(), memory.pending(), memory.monsters(), memory.at(), memory.streak(),
                memory.calm(), memory.dwelt(), memory.blocked(), memory.last(), memory.holds(), memory.near(),
                memory.before(), memory.flights(), memory.avoid(), memory.underfoot(), memory.refused(), memory.pack(),
                memory.aim(), memory.drank(), memory.trial(), List.of(new Memory.Balk(3, 0, "jade potion")), 4, 9, List.of(new Memory.Cloud(3, 0, 12, 40)));
        assertEquals(memory, Memory.of(memory.belief()));
        assertTrue(memory.balks(3, 0, "jade potion"));
        assertFalse(memory.balks(4, 0, "jade potion"));
    }

    @Test
    @DisplayName("the door the hero stands in counts: it shuts behind the hero, so the best cell does not flip as it walks")
    void doorway_counts() {
        List<SafeTest.Candidate> candidates = SafeTest.candidates(new Beliefs.Guess("jade potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of toxic gas", 0.5), new Beliefs.Odds("potion of healing", 0.5))), GAS);
        List<Tile> open = row(3, -1, -1);
        open.set(2, Tile.OPEN_DOOR);
        Observation inTheDoor = screen(1, open, Screens.heroAt(2, 20, 20, List.of()), List.of(), List.of(), List.of(),
                List.of());
        assertEquals(3, SafeTest.of(candidates, inTheDoor, 1).worst().damage(), "the hero's own doorway shuts behind it");
        Observation shutBeside = screen(1, row(3, -1, 2), Screens.heroAt(0, 20, 20, List.of()), List.of(), List.of(),
                List.of(), List.of());
        assertEquals(3, SafeTest.of(candidates, shutBeside, 1).worst().damage(), "the same cell, the door shut");
        Observation openBeside = screen(1, open, Screens.heroAt(0, 20, 20, List.of()), List.of(), List.of(), List.of(),
                List.of());
        assertEquals(10, SafeTest.of(candidates, openBeside, 1).worst().damage(),
                "a door open with nobody the screen shows in it is held open: no refuge");
    }

    @Test
    @DisplayName("a cell seen clouded is kept out of until seen clear, except a door's, which shut hides the gas behind it")
    void clouds_are_remembered() {
        Brain brain = brain(Screens.CODEX);
        List<Tile> tiles = row(6, -1, 4);
        Observation gassed = screen(1, tiles, Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(2, List.of("ToxicGas")), new BlobCell(4, List.of("ToxicGas"))), List.of());
        Observation clear = screen(1, tiles, Screens.heroAt(1, 20, 20, List.of()), List.of(), List.of(), List.of(),
                List.of());
        Memory seen = Memory.of(brain.update(gassed, null));
        assertTrue(seen.clouded(1, 0, 2, seen.waits()));
        assertTrue(seen.clouded(1, 0, 4, seen.waits()));
        assertFalse(Explore.walkable(gassed, seen)[2], "no Policy walks onto it");
        assertTrue(Explore.walkable(gassed, seen, true, true)[2], "the way out goes through");
        Memory later = Memory.of(brain.update(clear, seen.belief()));
        assertFalse(later.clouded(1, 0, 2, later.waits()), "seen clear, cleared");
        assertTrue(later.clouded(1, 0, 4, later.waits()), "a shut door is not seen clear");
        assertFalse(Explore.walkable(clear, later)[4], "so the doorway stays out of bounds");
        Memory lapsed = new Memory(later.waits() + Memory.CLOUD_WAITS + 1, 1, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Memory.Spot.NOWHERE, 0, true, List.of(), List.of(), "", 0,
                -1, -1, List.of(), List.of(), "", List.of(), Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE,
                List.of(), 0, -1, later.clouds());
        assertTrue(Explore.walkable(clear, lapsed)[4], "until the cloud's waits pass");
    }

    @Test
    @DisplayName("the escape aims past the cloud's edge: through a door, not to the next cell the gas will reach")
    void escape_through_the_door() {
        // Row: 0 clean, 1-4 gas (the hero on 2), 5 clean, 6 clean. The nearest clean cell, 0, borders
        // the gas and is where it spreads next; 6 is the nearest with no gas beside it.
        List<Tile> tiles = row(7, -1, -1);
        Observation inGas = screen(1, tiles, Screens.heroAt(2, 20, 20, List.of()), List.of(), List.of(),
                List.of(new BlobCell(1, List.of("ToxicGas")), new BlobCell(2, List.of("ToxicGas")),
                        new BlobCell(3, List.of("ToxicGas")), new BlobCell(4, List.of("ToxicGas"))), List.of());
        Memory justTested = Memory.START.trying(new Memory.Trial("jade potion", 1, -1));
        assertEquals(new Action.Step(3), TestItem.escape(inGas, justTested, inGas.actions().actions()));
    }

    @Test
    @DisplayName("a walk toward testing cells that goes on past the bound is balked at")
    void long_walk_balks() {
        Observation screen = screen(1, row(7, -1, -1), Screens.heroAt(5, 20, 20, List.of()),
                List.of(Screens.unknown(ItemKind.POTION, "jade potion", 1)), List.of(), List.of(), List.of());
        Memory walked = new Memory(4, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(1, 0, 4), 0, true, List.of(), List.of(), "Step", 0, -1, -1, List.of(), List.of(), "",
                List.of(), Memory.Pack.NONE, Memory.Aim.NONE, -1, new Memory.Trial("jade potion", 1, 5), List.of(),
                TestItem.WALKS, -1, List.of());
        Memory after = Beliefs.fold(walked, screen, FLAME);
        assertTrue(after.balks(1, 0, "jade potion"), "one more Step than the bound");
        Memory shorter = new Memory(4, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(1, 0, 4), 0, true, List.of(), List.of(), "Step", 0, -1, -1, List.of(), List.of(), "",
                List.of(), Memory.Pack.NONE, Memory.Aim.NONE, -1, new Memory.Trial("jade potion", 1, 5), List.of(), 3, -1, List.of());
        Memory fine = Beliefs.fold(shorter, screen, FLAME);
        assertFalse(fine.balks(1, 0, "jade potion"));
        assertEquals(4, fine.walking(), "the Step carried out counts");
    }
}
