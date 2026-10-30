package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #412: the hero takes back the missiles it threw, throws only what is expected to pay, takes a dewdrop
 * into a waterskin that has room, and values an unknown potion for the healing it may be. Each test here fails on
 * the Brain before the issue (ADR-0019).
 */
class MissilesAndDewTest {

    private static ItemView stones(int quantity) {
        return new ItemView(ItemKind.MISSILE, "throwing stone", quantity, false, 0, false, false, "", EquipSlot.NONE,
                List.of("DROP", "THROW"), "THROW");
    }

    private static ItemView waterskin(String status) {
        return new ItemView(ItemKind.OTHER, "waterskin", 1, false, 0, false, false, status, EquipSlot.NONE,
                List.of("DRINK", "DROP"), "DRINK");
    }

    /** {@code screen} showing these heaps, the offered Actions recomputed. */
    private static Observation withHeaps(Observation screen, HeapView... heaps) {
        MapSection map = screen.map();
        MapSection shown = new MapSection(map.width(), map.height(), map.tiles(), map.fog(), map.traps(), List.of(heaps),
                map.blobs(), map.feeling(), map.transitions());
        Observation bare = new Observation(screen.header(), shown, screen.actors(), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static HeapView heap(int cell, String item) {
        return new HeapView(cell, HeapKind.HEAP, false, item, 0, "");
    }

    private static Brain.Decided decide(Brain brain, Belief belief, Observation screen) {
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    /** A Brain with the #412 pick-up and fetch levers switched off, the control that shows each test below discriminates. */
    private static Brain off() {
        org.shatterfish.api.Playbook off = org.shatterfish.api.Tunables.with(org.shatterfish.api.Playbook.reference("off"),
                Map.of("pickup.missile_reach", 0L, "pickup.dew_skin_below", 0L, "ranged.fetch_steps", 0L));
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, off, 9L, Brain.Model.BASELINE);
    }

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
    }

    /** A corridor of {@code floor} cells, the hero at {@code hero}, a rat at {@code rat} (-1 for none). */
    private static String row(int floor, int hero, int rat) {
        StringBuilder row = new StringBuilder("#");
        for (int x = 1; x <= floor; x++) {
            row.append(x == hero ? '@' : x == rat ? 'r' : '.');
        }
        return row.append('#').toString();
    }

    /** The cell of column {@code x} in the corridor's one row of {@code floor} cells. */
    private static int at(int floor, int x) {
        return x + floor + 2;
    }

    private static Observation corridor(int floor, int hero, int rat, ItemView... items) {
        String walls = "#".repeat(floor + 2);
        return EatPolicyTest.holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE, walls,
                row(floor, hero, rat), walls), items);
    }

    @Test
    @DisplayName("a stone thrown at a rat that then dies is walked back to sixteen steps away; the same stone it did not throw is not")
    void stone_recovery_after_a_kill() {
        // The rat is four cells off, so it is thrown at; it dies; the stone lies on its cell, and the hero has walked on.
        Observation fight = corridor(40, 2, 6, stones(3));
        Brain brain = brain();
        Belief belief = brain.update(fight, null);
        Brain.Decided throwing = decide(brain, belief, fight);
        assertEquals("ranged", throwing.decision().policy());
        assertTrue(throwing.action() instanceof Action.UseItemAt);
        belief = brain.handed(fight, belief, throwing);

        int stone = at(40, 6);
        Observation after = withHeaps(corridor(40, 22, -1, stones(2)), heap(stone, "throwing stone"));
        belief = brain.update(after, belief);
        Brain.Decided fetch = decide(brain, belief, after);
        assertEquals("pick-up", fetch.decision().policy(), fetch.decision().toString());
        assertEquals(new Action.Step(at(40, 21)), fetch.action(), "toward the stone, sixteen steps off");
        assertEquals("item: throwing stone 16", fetch.decision().chosen().why());

        // Control: with the levers off the same walk is not made.
        Brain control = off();
        Belief seen = control.update(fight, null);
        seen = control.handed(fight, seen, decide(control, seen, fight));
        seen = control.update(after, seen);
        assertNotEquals("pick-up", decide(control, seen, after).decision().policy());

        // Control: a stone lying there that this Brain did not throw is an ordinary item, and sixteen steps is too far for it.
        Brain fresh = brain();
        Belief unknown = fresh.update(after, null);
        assertNotEquals("pick-up", decide(fresh, unknown, after).decision().policy());
    }

    @Test
    @DisplayName("a stone that is gone from the screen where it was thrown is forgotten, not walked to")
    void a_stone_that_broke_is_forgotten() {
        Observation fight = corridor(40, 2, 6, stones(3));
        Brain brain = brain();
        Belief belief = brain.update(fight, null);
        belief = brain.handed(fight, belief, decide(brain, belief, fight));
        Observation after = corridor(40, 22, -1, stones(2));
        belief = brain.update(after, belief);
        assertNotEquals("pick-up", decide(brain, belief, after).decision().policy());
        // And a heap drawn there later is an ordinary one.
        Observation later = withHeaps(after, heap(at(40, 6), "throwing stone"));
        belief = brain.update(later, belief);
        assertNotEquals("pick-up", decide(brain, belief, later).decision().policy());
    }

    @Test
    @DisplayName("in a fight, with a second rat far off and no enemy beside the hero, the stone two steps away is fetched first")
    void a_stone_two_steps_away_in_a_fight() {
        Observation first = corridor(40, 2, 6, stones(3));
        Brain brain = brain();
        Belief belief = brain.update(first, null);
        belief = brain.handed(first, belief, decide(brain, belief, first));
        // The first rat is dead; a second walks at 30; the hero has stepped to 4, the stone lies on 6.
        Observation second = withHeaps(corridor(40, 4, 30, stones(2)), heap(at(40, 6), "throwing stone"));
        belief = brain.update(second, belief);
        Brain.Decided fetch = decide(brain, belief, second);
        assertEquals("ranged", fetch.decision().policy(), fetch.decision().toString());
        assertEquals("fetch: throwing stone 2", fetch.decision().chosen().why());
        assertEquals(new Action.Step(at(40, 5)), fetch.action());

        // The same stone with the rat close behind it is left: the pick-up would not finish before the rat arrived.
        Observation near = withHeaps(corridor(40, 4, 9, stones(2)), heap(at(40, 6), "throwing stone"));
        Belief seen = brain.update(near, belief);
        assertNotEquals("fetch: throwing stone 2", decide(brain, seen, near).decision().chosen().why());
    }

    @Test
    @DisplayName("a stone is not thrown at an enemy it would hit three times in ten for a hit point or so; it is at one it would hit")
    void a_dodging_enemy_is_not_thrown_at() {
        Codex.Knowledge dodging = new Codex.Knowledge(FightPolicyTest.KNOWLEDGE.manifest(), FightPolicyTest.KNOWLEDGE.families(),
                FightPolicyTest.KNOWLEDGE.rooms(), FightPolicyTest.KNOWLEDGE.guarantees(),
                List.of(new Codex.Threat("marsupial rat", 4, 8, 25, 1, 4, 0, 0)), FightPolicyTest.KNOWLEDGE.weapons(),
                FightPolicyTest.KNOWLEDGE.armours(), List.of()).withBestiary(Screens.BESTIARY);
        Observation snake = corridor(8, 1, 3, stones(3));
        Brain brain = new Brain(dodging, Screens.WEIGHTS, 9L);
        Brain.Decided decided = brain.decide(snake, brain.update(snake, null));
        assertNotEquals("ranged", decided.decision().policy(), decided.decision().toString());

        Brain plain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        assertEquals("ranged", plain.decide(snake, plain.update(snake, null)).decision().policy());
    }

    @Test
    @DisplayName("a dewdrop three steps away is taken at full health when the waterskin shows 10/20, and not when it shows 20/20")
    void dew_at_full_health_with_a_half_full_skin() {
        Observation half = withHeaps(corridor(8, 1, -1, waterskin("10/20")), heap(at(8, 4), "dewdrop"));
        Brain brain = brain();
        Brain.Decided taken = decide(brain, brain.update(half, null), half);
        assertEquals("pick-up", taken.decision().policy(), taken.decision().toString());
        assertEquals(new Action.Step(at(8, 2)), taken.action());

        Brain control = off();
        assertNotEquals("pick-up", decide(control, control.update(half, null), half).decision().policy(), "lever off");

        Observation full = withHeaps(corridor(8, 1, -1, waterskin("20/20")), heap(at(8, 4), "dewdrop"));
        Brain again = brain();
        assertNotEquals("pick-up", decide(again, again.update(full, null), full).decision().policy());
        Observation none = withHeaps(corridor(8, 1, -1), heap(at(8, 4), "dewdrop"));
        assertNotEquals("pick-up", decide(again, again.update(none, null), none).decision().policy());
    }

    @Test
    @DisplayName("an unknown potion fifteen steps away is fetched for the healing it may be, once strength is not what it is worth")
    void an_unknown_potion_is_worth_a_longer_walk() {
        // Strength weighted nothing, so the potion is worth its place in the pack and its odds of healing and no more.
        Brain brain = new Brain(Screens.CODEX, Screens.weights(Map.of("strength", 0L)), 3L);
        List<org.shatterfish.api.Tile> tiles = new ArrayList<>(java.util.Collections.nCopies(30, Tile.EMPTY));
        Observation far = Screens.floor(1, Screens.heroAt(1, 10), tiles, List.of(heap(16, "crimson potion")), List.of(),
                new Action.Step(0), new Action.Step(2));
        Brain.Decided decided = brain.decide(far, brain.update(far, null));
        assertEquals("pick-up", decided.decision().policy(), decided.decision().toString());
        assertEquals(new Action.Step(2), decided.action());

        Brain without = new Brain(Screens.CODEX, Screens.weights(Map.of("strength", 0L, "heal", 0L)), 3L);
        assertNotEquals("pick-up", without.decide(far, without.update(far, null)).decision().policy(),
                "without the healing weight, fifteen steps is too far");
    }

    @Test
    @DisplayName("equally short ways break toward tall grass while the waterskin is not full, and not when it is")
    void ties_break_toward_tall_grass() {
        // A room two rows high: the hero on the left, the goal on the right, tall grass along the lower row.
        String walls = "##########";
        Observation open = FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE, walls, "#@.......#",
                "#........#", walls);
        List<Tile> tiles = new ArrayList<>(open.map().tiles());
        int width = open.map().width();
        for (int x = 2; x <= 7; x++) {
            tiles.set(x + 2 * width, Tile.HIGH_GRASS);
        }
        MapSection grassy = new MapSection(width, open.map().height(), tiles, open.map().fog(), open.map().traps(),
                open.map().heaps(), open.map().blobs(), open.map().feeling(), open.map().transitions());
        for (String status : List.of("10/20", "20/20")) {
            Observation bare = new Observation(open.header(), grassy, open.actors(), open.hero(),
                    EatPolicyTest.holding(open, waterskin(status)).inventory(), open.journal(), open.log(), ActionsSection.NONE,
                    open.prompt());
            Observation screen = bare.withActions(ValidActions.of(bare));
            Explore.Path path = Explore.nearest(screen.map(), Explore.walkable(screen, Memory.START, true),
                    screen.hero().cell(), screen.actions().actions(), cell -> cell == 8 + width,
                    Explore.grassWanted(screen, org.shatterfish.api.Tunables.Vector.REFERENCE));
            assertEquals(7, path.distance(), "the way is as short either way");
            boolean throughGrass = path.step() instanceof Action.Step step && Dew.tall(screen.map().tiles().get(step.cell()));
            assertEquals(status.startsWith("10"), throughGrass, status);
        }
    }
}
