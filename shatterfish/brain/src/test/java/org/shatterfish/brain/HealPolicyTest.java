package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heal Policy (story 4.9): it drinks an identified potion of healing when the hero's hit points
 * are at or under the damage the enemies threatening it are expected to deal before the fight is over,
 * a threshold that follows the threat rather than a constant; not while the fight Policy can retreat
 * from a fight it would lose; and one potion at a time.
 */
class HealPolicyTest {

    /** A potion as the inventory lists it, identified or by its appearance (Potion.java:222-226). */
    static ItemView potion(String name, int quantity) {
        return new ItemView(ItemKind.POTION, name, quantity, true, 0, true, false, "", EquipSlot.NONE,
                List.of("DRINK", "DROP", "THROW"), "DRINK");
    }

    /** The hero at {@code hp} of 20 in a corridor drawn by {@code row}, holding {@code items}. */
    private static Observation fight(int hp, String row, ItemView... items) {
        return EatPolicyTest.holding(FightPolicyTest.screen(1, hp, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length())), items);
    }

    /** {@code screen} with every enemy asleep. */
    static Observation asleep(Observation screen) {
        return recast(screen, null, Emote.SLEEP);
    }

    /** {@code screen} with every enemy named {@code name}. */
    static Observation named(Observation screen, String name) {
        return recast(screen, name, null);
    }

    private static Observation recast(Observation screen, String name, Emote emote) {
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : screen.actors().actors()) {
            actors.add(new ActorView(actor.cell(), name == null ? actor.name() : name, actor.alignment(),
                    actor.healthPips(), actor.invisible(), emote == null ? actor.emote() : emote, actor.buffs()));
        }
        Observation bare = new Observation(screen.header(), screen.map(), new ActorsSection(actors), screen.hero(),
                screen.inventory(), screen.journal(), screen.log(), ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static int danger(Observation screen) {
        return Heal.danger(screen, FightPolicyTest.KNOWLEDGE, Memory.START);
    }

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = brain();
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static boolean drinks(Brain.Decided decided) {
        return decided.action() instanceof Action.UseItem use && use.action().equals("DRINK");
    }

    @Test
    @DisplayName("hurt and cornered by a rat, with a potion of healing, the hero drinks before it fights")
    void drinks_under_threat() {
        Brain.Decided decided = decide(fight(3, "#@r...#", potion("potion of healing", 1)));
        assertEquals("heal", decided.decision().policy());
        assertTrue(drinks(decided), decided.action().toString());
        assertEquals("potion of healing", ((Action.UseItem) decided.action()).item().name());
        assertEquals("heal 3/" + danger(fight(3, "#@r...#")), decided.decision().chosen().why());
        assertEquals("heal: threat", decided.decision().goal());
        assertEquals("fight", decide(fight(3, "#@r...#")).decision().policy(), "without the potion, the fight Policy acts");
    }

    @Test
    @DisplayName("hurt beside a rat with room behind, the fight Policy retreats and the potion is kept")
    void retreat_before_drinking() {
        Brain.Decided decided = decide(fight(3, "#..@r.#", potion("potion of healing", 1)));
        assertEquals("fight", decided.decision().policy());
        assertTrue(decided.decision().chosen().why().startsWith("retreat"), decided.decision().chosen().why());
    }

    @Test
    @DisplayName("with more hit points than a rat is expected to take, the hero fights and keeps the potion")
    void holds_when_threat_is_small() {
        Brain.Decided decided = decide(fight(12, "#@r...#", potion("potion of healing", 1)));
        assertEquals("fight", decided.decision().policy());
    }

    @Test
    @DisplayName("the same hit points drink beside a brute and fight beside a rat: the threshold is the threat")
    void threshold_follows_the_threat() {
        int rat = danger(fight(12, "#@r...#"));
        int brute = danger(fight(12, "#@B...#"));
        assertTrue(rat < 12 && brute >= 12, "rat " + rat + ", brute " + brute);
        assertTrue(drinks(decide(fight(12, "#@B...#", potion("potion of healing", 1)))));
        assertEquals(0, danger(fight(12, "#.@...#")), "no enemy in view, no danger");
        // Two rats, one on each side of the hero, threaten more than one.
        int two = danger(fight(12, "#r@r..#"));
        assertTrue(two > rat, "two rats " + two + ", one " + rat);
    }

    @Test
    @DisplayName("a sleeping enemy, one the hero cannot reach, or one too far to arrive before the fight ends, threatens nothing")
    void only_what_threatens() {
        assertTrue(danger(fight(12, "#@B...#")) > 0);
        assertEquals(0, danger(asleep(fight(12, "#@B...#"))), "asleep beside the hero");
        assertEquals(0, danger(fight(12, "#@.#r.#")), "behind a wall");
        assertTrue(danger(fight(12, "#@.r......#")) > 0, "two steps off");
        assertEquals(0, danger(fight(12, "#@........r#")), "ten steps off, and a rat dies in two turns");
        Brain.Decided sleeping = decide(asleep(fight(3, "#@B...#", potion("potion of healing", 1))));
        assertTrue(!drinks(sleeping), "no potion spent on a sleeping brute: " + sleeping.decision());
    }

    @Test
    @DisplayName("an enemy that shoots or flies threatens from where the hero cannot walk, and a cornered hero drinks")
    void shooters_threaten_from_afar() {
        Observation behind = fight(3, "#@.#B.#", potion("potion of healing", 1));
        assertEquals(0, danger(behind), "a brute behind a wall cannot reach the hero");
        for (String name : List.of("evil eye", "gnoll shaman", "dwarf warlock", "DM-100", "scorpio", "acidic scorpio",
                "vampire bat")) {
            assertTrue(danger(named(behind, name)) > 0, name);
        }
        assertTrue(drinks(decide(named(behind, "evil eye"))), "shot at, cornered and hurt");
        assertEquals(0, danger(asleep(named(behind, "evil eye"))), "an eye asleep shoots nothing");
    }

    @Test
    @DisplayName("with no enemy in view the hero never drinks, however hurt")
    void no_enemy_no_drink() {
        Brain.Decided decided = decide(fight(2, "#..@..#", potion("potion of healing", 1)));
        assertNotEquals("heal", decided.decision().policy());
        assertTrue(!drinks(decided));
    }

    @Test
    @DisplayName("an unidentified potion is never drunk to heal")
    void unknown_potion() {
        Brain.Decided decided = decide(fight(3, "#@r...#", potion("crimson potion", 1)));
        assertEquals("fight", decided.decision().policy());
    }

    @Test
    @DisplayName("a hero missing less than the first turn's heal does not drink, whatever the threat")
    void not_while_nearly_full() {
        assertEquals(8, Heal.firstTurn(20), "(int)(0.8 * 20 + 14) = 30, a quarter rounded");
        assertEquals(30, Heal.heals(20));
        Brain.Decided decided = decide(fight(13, "#@B...#", potion("potion of healing", 1)));
        assertTrue(!drinks(decided), "missing 7 of 20: " + decided.decision());
    }

    @Test
    @DisplayName("the heal of 30 is spent in 11 turns, a quarter of what is left each, at least 1")
    void healing_turns() {
        assertEquals(11, Heal.healingTurns(20));
        assertTrue(Heal.healingTurns(100) > Heal.healingTurns(20));
        assertEquals(30, Heal.remaining(20, 0));
        assertEquals(22, Heal.remaining(20, 1));
        assertEquals(7, Heal.remaining(20, 5));
        assertEquals(0, Heal.remaining(20, Heal.healingTurns(20)));
        // A quarter of 30 is 7.5: after four turns 9 is left, after five 7.
        assertTrue(!Heal.spent(20, 4) && Heal.spent(20, 5));
    }

    @Test
    @DisplayName("over several waits: one potion, none while most of its heal is left, then another if still in danger")
    void one_potion_at_a_time() {
        int turns = 5;
        assertTrue(Heal.spent(20, turns) && !Heal.spent(20, turns - 1), "the redrink comes at wait " + turns);
        Observation hurt = fight(3, "#@r...#", potion("potion of healing", 2));
        Observation still = fight(3, "#@r...#", potion("potion of healing", 1));
        Brain brain = brain();
        Belief belief = null;
        List<Boolean> drank = new ArrayList<>();
        List<Observation> screens = new ArrayList<>();
        screens.add(hurt);
        for (int i = 0; i < turns; i++) {
            screens.add(still);
        }
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            drank.add(drinks(decided));
            belief = brain.handed(screen, belief, decided);
        }
        List<Boolean> expected = new ArrayList<>(List.of(true));
        for (int i = 1; i < turns; i++) {
            expected.add(false);
        }
        expected.add(true);
        assertEquals(expected, drank, "a drink, " + (turns - 1) + " waits of fighting, then the next");
    }

    @Test
    @DisplayName("over several waits: a retreat is taken each wait and no potion is spent while one is open")
    void retreats_rather_than_drinks() {
        Brain brain = brain();
        Belief belief = null;
        for (String row : List.of("#....@r#", "#...@r.#", "#..@r..#")) {
            Observation screen = fight(3, row, potion("potion of healing", 1));
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertTrue(!drinks(decided), row + ": " + decided.decision());
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(-1, Memory.of(belief).drank());
    }

    @Test
    @DisplayName("the memory keeps the wait of the last drink, and round-trips it")
    void the_memory() {
        Brain brain = brain();
        Observation hurt = fight(3, "#@r...#", potion("potion of healing", 1));
        Belief belief = brain.update(hurt, null);
        assertEquals(-1, Memory.of(belief).drank());
        belief = brain.handed(hurt, belief, brain.decide(hurt, belief));
        Memory memory = Memory.of(belief);
        assertEquals(memory.waits(), memory.drank());
        assertEquals(memory, Memory.of(memory.belief()));
        belief = brain.update(fight(3, "#@r...#"), belief);
        assertEquals(1, Memory.of(belief).drank(), "the fold keeps it");
    }
}
