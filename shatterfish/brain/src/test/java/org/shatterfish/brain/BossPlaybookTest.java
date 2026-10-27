package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Playbook's boss entries steer the fight and the descent to it (story 7.5): each field is read
 * where the value used to be a constant, so a changed field changes the Decision on a constructed
 * screen, and the reference entry decides as the constants did.
 */
class BossPlaybookTest {

    private static Brain playing(Playbook.Goo goo) {
        Playbook playbook = new Playbook("shatterfish", 1, Playbook.Identify.REFERENCE, Playbook.Upgrade.REFERENCE, Playbook.Kit.REFERENCE, List.of(),
                new Playbook.Bosses(goo, Playbook.Later.REFERENCE, Playbook.Later.REFERENCE));
        return new Brain(Screens.CODEX, Screens.WEIGHTS, playbook, 5L, Brain.Model.BASELINE);
    }

    private static Brain.Decided decide(Brain brain, Observation screen) {
        return brain.decide(screen, Beliefs.fold(Memory.START, screen, Screens.CODEX).belief());
    }

    /** Water six Steps west of the hero, in the open, with Goo three cells east. */
    private static final String[] WATER_SIX = {
            "##############",
            "#............#",
            "#............#",
            "#............#",
            "#..~.........#",
            "#........@..r#",
            "#............#",
            "#............#",
            "##############"};

    @Test
    @DisplayName("wash_range: water five Steps off is walked to for the ooze when the range allows it, and not when it does not")
    void wash_range() {
        Observation soaked = GooTest.oozed(true, false, WATER_SIX);
        assertEquals("goo: wait", decide(playing(Playbook.Goo.REFERENCE), soaked).decision().chosen().why(),
                "the reference range of four does not reach it");
        Brain.Decided far = decide(playing(Playbook.Goo.REFERENCE.with(Map.of("wash_range", 6L))), soaked);
        assertEquals("goo: wash", far.decision().chosen().why(), far.decision().toString());
        Observation near = GooTest.oozed(true, false, GooTest.WATER_WEST);
        assertEquals("goo: wait", decide(playing(Playbook.Goo.REFERENCE.with(Map.of("wash_range", 0L))), near).decision().chosen().why(),
                "a range of none never walks to water");
    }

    @Test
    @DisplayName("meet_range: a hero pinned to a wall walks to ground to dodge on only when the walk is within the range")
    void meet_range() {
        Observation pinned = GooTest.screen(List.of("You see Goo."), GooTest.BY_THE_WALL);
        Brain.Decided reference = decide(playing(Playbook.Goo.REFERENCE), pinned);
        assertEquals("goo: position", reference.decision().chosen().why(), reference.decision().toString());
        Brain.Decided none = decide(playing(Playbook.Goo.REFERENCE.with(Map.of("meet_range", 0L))), pinned);
        assertEquals("goo: wait", none.decision().chosen().why(), none.decision().toString());
    }

    @Test
    @DisplayName("drink_per_mille: the hit points the hero drinks at against Goo alone follow the entry")
    void drink_share() {
        Observation goo = GooTest.screen(List.of("You see Goo."), GooTest.BY_THE_WALL);
        int ht = goo.hero().ht();
        assertEquals(Playbook.Goo.REFERENCE.danger(ht), Heal.danger(goo, FightPolicyTest.KNOWLEDGE, Memory.START));
        Playbook.Goo eager = Playbook.Goo.REFERENCE.with(Map.of("drink_per_mille", 700L));
        int reference = Heal.danger(goo, FightPolicyTest.KNOWLEDGE, Memory.START, Playbook.Goo.REFERENCE);
        int raised = Heal.danger(goo, FightPolicyTest.KNOWLEDGE, Memory.START, eager);
        assertTrue(raised > reference, "a higher share drinks at more hit points: " + raised + " over " + reference);
        assertEquals((int) (((long) ht * 700 + 999) / 1000), raised);
    }

    /** A spent floor-4 corridor, the exit beside the hero at column 6, the hero at {@code hp} of 20. */
    private static Observation beside(int depth, int hp) {
        return DescendPolicyTest.hero(ExplorePolicyTest.screen(depth, "#########", "#.....@>#", "#########"), hp, 20, Hunger.NONE);
    }

    private static Playbook resting(int goo, Playbook.Later tengu) {
        return new Playbook("shatterfish", 1, Playbook.Identify.REFERENCE, Playbook.Upgrade.REFERENCE, Playbook.Kit.REFERENCE, List.of(),
                new Playbook.Bosses(Playbook.Goo.REFERENCE.with(Map.of("descent_rest_per_mille", (long) goo)), tengu,
                        Playbook.Later.REFERENCE));
    }

    private static Brain.Decided at(Playbook playbook, Observation screen) {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, playbook, 5L, Brain.Model.BASELINE)
                .decide(screen, DescendPolicyTest.spent(screen.header().depth(), 10, 1, 0).belief());
    }

    @Test
    @DisplayName("descent_rest_per_mille: beside the stairs to the boss floor the hero rests to the entry's share, and no further")
    void rests_to_the_share_before_the_boss_floor() {
        Playbook seven = resting(700, Playbook.Later.REFERENCE);
        // 15 of 20 is 75%: at or over 70%, so down; 13 of 20 is 65%: rest.
        assertEquals("exit: spent 1", at(seven, beside(4, 15)).decision().chosen().why(), "75% is enough for a share of 70%");
        Brain.Decided hurt = at(seven, beside(4, 13));
        assertEquals(new Action.Rest(true), hurt.action(), hurt.decision().toString());
        assertEquals("rest: descent", hurt.decision().chosen().why());
        // The reference rests to full, as the Policy did before the entry existed.
        assertEquals(new Action.Rest(true), at(resting(1000, Playbook.Later.REFERENCE), beside(4, 19)).action());
        // The share is for the boss floor only: the floor above it (depth 2, going to 3) still rests to full.
        assertEquals(new Action.Rest(true), at(seven, beside(2, 15)).action(), "no boss floor below: rested to full");
    }

    @Test
    @DisplayName("descent_rest_per_mille: Tengu's entry is read on the way to depth 10, and Goo's is not")
    void later_entries_are_read_for_their_floor() {
        Playbook.Later hasty = Playbook.Later.REFERENCE.with(Map.of("descent_rest_per_mille", 500L));
        Playbook playbook = resting(1000, hasty);
        assertEquals("exit: spent 1", at(playbook, beside(9, 11)).decision().chosen().why(), "11 of 20 is over 50%");
        assertEquals(new Action.Rest(true), at(playbook, beside(4, 11)).action(), "Goo's entry rests to full");
    }
}
