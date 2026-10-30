package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.TrapView;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tengu's fight, the first half (story 10.19, issue #365), on a constructed arena: the hero walks straight to
 * Tengu and strikes, never turning back, the dart traps the screen draws as they are laid are walked round,
 * and the cells stay walked round for as long as Tengu is in view and no longer. The same fight played in the
 * game, with a real Brain, is {@code TenguFightRunTest} in the rig.
 *
 * <p>The arena is a room of ten columns by five rows, as wide as the cell Tengu fights in is and shorter. The
 * hero stands at the west end and Tengu at the east. Every test here fails on the Brain before the story: it
 * had no Tengu fight (its fight Policy approached from three cells and retreated from two, in every Run of
 * the practice table in docs/results/e10-practice-kit.md), so none of the {@code tengu: ...} reasons exist.
 */
class TenguFightTest {

    private static final String[] ROOM = {
            "############",
            "#@.........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "############"};

    /** The cell at column {@code x}, row {@code y} of the arena. */
    static int cell(int x, int y) {
        return x + y * 12;
    }

    static ActorView tengu(int cell) {
        return new ActorView(cell, Tengu.BOSS, Alignment.ENEMY, ObservationCodec.MAX_HEALTH_PIPS, false, Emote.NONE, List.of());
    }

    /** The arena with the floor sealed, Tengu on {@code boss} (-1 for him gone) and the dart traps on {@code drawn} drawn. */
    static Observation arena(boolean sealed, int boss, int... drawn) {
        Observation plain = ExplorePolicyTest.screen(Tengu.DEPTH, sealed, org.shatterfish.api.HeroClass.WARRIOR, ROOM);
        MapSection map = plain.map();
        List<TrapView> traps = new ArrayList<>();
        for (int cell : drawn) {
            traps.add(new TrapView(cell, Darts.KIND, true));
        }
        MapSection drawing = new MapSection(map.width(), map.height(), map.tiles(), map.fog(), traps, map.heaps(),
                map.blobs(), map.feeling(), map.transitions());
        List<ActorView> actors = boss < 0 ? List.of() : List.of(tengu(boss));
        Observation bare = new Observation(plain.header(), drawing, new ActorsSection(actors), plain.hero(),
                plain.inventory(), plain.journal(), plain.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    /** The Decision at the last of {@code screens}, the earlier ones handed through the Brain as a Run would. */
    private static Brain.Decided last(Observation... screens) {
        Brain brain = brain();
        Belief belief = null;
        Brain.Decided decided = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            decided = brain.decide(screen, belief);
            belief = brain.handed(screen, belief, decided);
        }
        return decided;
    }

    /** The memory's dart cells after the screens, as plain cell numbers. */
    private static List<Integer> remembered(Observation... screens) {
        Brain brain = brain();
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            belief = brain.handed(screen, belief, brain.decide(screen, belief));
        }
        return Memory.of(belief).darts().stream().map(Memory.Spot::cell).toList();
    }

    @Test
    @DisplayName("Tengu three cells off, the hero steps toward him; two off, it steps again and does not turn back; beside him it strikes")
    void goes_straight_in_and_strikes() {
        int boss = cell(5, 1);
        Brain.Decided far = last(arena(true, boss));
        assertEquals(new Action.Step(cell(2, 1)), far.action(), far.decision().toString());
        assertTrue(far.decision().chosen().why().startsWith("tengu: approach"), far.decision().chosen().why());

        // Two cells off, the dither of the old fight Policy was a Step back; here every wait is a Step in.
        Observation[] walk = {
                withHero(arena(true, boss), cell(1, 1)), withHero(arena(true, boss), cell(2, 1)),
                withHero(arena(true, boss), cell(3, 1))};
        Brain brain = brain();
        Belief belief = null;
        int previous = Integer.MAX_VALUE;
        for (Observation screen : walk) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            Action.Step step = (Action.Step) decided.action();
            int distance = Math.abs(step.cell() % 12 - boss % 12) + Math.abs(step.cell() / 12 - boss / 12);
            assertTrue(distance < previous, "each Step brings the hero nearer: " + decided.decision());
            previous = distance;
            belief = brain.handed(screen, belief, decided);
        }

        Brain.Decided beside = last(withHero(arena(true, boss), cell(4, 1)));
        assertEquals(new Action.Attack(boss), beside.action(), beside.decision().toString());
        assertEquals("tengu: attack", beside.decision().chosen().why());
    }

    @Test
    @DisplayName("hurt, with Tengu in reach, it still strikes: there is nowhere to retreat to on a sealed floor and no regeneration to win by waiting")
    void does_not_retreat_or_stall() {
        Observation hurt = DescendPolicyTest.hero(withHero(arena(true, cell(3, 1)), cell(2, 1)), 4, 20,
                org.shatterfish.api.Hunger.NONE, ExplorePolicyTest.PACK.toArray(new org.shatterfish.api.ItemView[0]));
        Brain.Decided decided = last(hurt);
        assertEquals(new Action.Attack(cell(3, 1)), decided.action(), decided.decision().toString());
        for (Action action : List.of(new Action.Wait(), new Action.Search(), new Action.Rest(true))) {
            assertNotEquals(action, decided.action(), "no stall");
        }
    }

    @Test
    @DisplayName("the trap cells drawn between the hero and Tengu are walked round, and still walked round on the screen after the drawing ends")
    void walks_round_the_drawn_cells_and_remembers_them() {
        // A wall of traps across columns 4 and 5, rows 2 to 4, leaves only the top row open.
        int boss = cell(10, 2);
        int[] patch = {cell(4, 2), cell(4, 3), cell(4, 4), cell(5, 2), cell(5, 3), cell(5, 4)};
        Observation hero = withHero(arena(true, boss, patch), cell(3, 4));
        Brain.Decided drawn = last(hero);
        assertTrue(drawn.action() instanceof Action.Step, drawn.decision().toString());
        assertTrue(((Action.Step) drawn.action()).cell() % 12 < 4, "toward the open row, not into the patch: " + drawn.decision());

        // The drawing is over: the screen shows no trap. The cells are still walked round, from the memory.
        Observation gone = withHero(arena(true, boss), cell(3, 4));
        assertTrue(gone.map().traps().isEmpty());
        Brain.Decided remembered = last(hero, gone);
        assertEquals(drawn.action(), remembered.action(), remembered.decision().toString());
        assertEquals(List.of(cell(4, 2), cell(5, 2), cell(4, 3), cell(5, 3), cell(4, 4), cell(5, 4)),
                remembered(hero, gone));

        // The control: a hero that never saw the patch drawn walks straight on, into the column the patch holds.
        Brain.Decided never = last(gone);
        assertEquals(4, ((Action.Step) never.action()).cell() % 12, never.decision().toString());
        assertEquals(List.of(), remembered(gone));
    }

    @Test
    @DisplayName("a new drawing replaces the old one, and a hero with no way round crosses the patch rather than wait")
    void a_new_patch_replaces_the_old_and_a_shut_door_is_crossed() {
        int boss = cell(10, 2);
        Observation first = withHero(arena(true, boss, cell(4, 1), cell(4, 2)), cell(3, 2));
        Observation second = withHero(arena(true, boss, cell(6, 3), cell(6, 4)), cell(3, 2));
        assertEquals(List.of(cell(6, 3), cell(6, 4)), remembered(first, second));

        // A whole column of traps: no cell is open. The Policy crosses at the nearest cell rather than stalls.
        int[] column = {cell(5, 1), cell(5, 2), cell(5, 3), cell(5, 4)};
        Observation shut = withHero(arena(true, boss, column), cell(4, 2));
        Brain.Decided crossing = last(shut);
        assertTrue(crossing.action() instanceof Action.Step, crossing.decision().toString());
        assertTrue(crossing.decision().chosen().why().startsWith("tengu: across"), crossing.decision().chosen().why());
    }

    @Test
    @DisplayName("the memory is dropped when Tengu is out of view, when the floor is unsealed and on another floor; it is never filled by a trap that is not the patch's kind")
    void the_memory_lasts_only_while_the_fight_does() {
        int boss = cell(10, 2);
        Observation drawn = withHero(arena(true, boss, cell(4, 2)), cell(3, 2));
        assertEquals(List.of(cell(4, 2)), remembered(drawn));
        assertEquals(List.of(cell(4, 2)), remembered(drawn, withHero(arena(true, boss), cell(3, 2))));
        // The pause between the halves removes him: the next wait forgets the patch, as the arena is another room.
        assertEquals(List.of(), remembered(drawn, withHero(arena(true, -1), cell(3, 2))));
        assertEquals(List.of(), remembered(drawn, withHero(arena(false, boss), cell(3, 2))), "unsealed");
        // A trap of any other kind, drawn on this floor, is not the patch.
        Observation other = withTrap(withHero(arena(true, boss), cell(3, 2)), new TrapView(cell(4, 2), "worn dart trap", true));
        assertEquals(List.of(), remembered(other));
        // The same trap on another floor.
        Observation upstairs = ExplorePolicyTest.screen(Tengu.DEPTH - 1, true, org.shatterfish.api.HeroClass.WARRIOR, ROOM);
        assertEquals(List.of(), remembered(withTrap(upstairs, new TrapView(cell(4, 2), Darts.KIND, true))));
    }

    @Test
    @DisplayName("the patch the screen draws is no sign of hidden traps beside it: the search-ahead rule leaves the hero's Steps alone")
    void the_patch_asks_for_no_search() {
        int boss = cell(10, 2);
        Observation drawn = withHero(arena(true, boss, cell(5, 2)), cell(3, 2));
        assertFalse(Traps.near(drawn, cell(4, 1)), "Tengu's patch is drawn whole (Darts)");
        Observation burned = withTrap(withHero(arena(false, boss), cell(3, 2)), new TrapView(cell(5, 2), Darts.KIND, true));
        assertTrue(Traps.near(burned, cell(4, 1)), "off the boss floor a shown trap still hides others beside it (Traps)");
        assertNull(Tengu.engage(burned, Memory.START, burned.actions().actions(), List.of(tengu(boss)), org.shatterfish.api.Playbook.Tengu.REFERENCE), "unsealed: no Tengu fight");
    }

    static Observation withHero(Observation screen, int cell) {
        org.shatterfish.api.HeroSection h = screen.hero();
        org.shatterfish.api.HeroSection hero = new org.shatterfish.api.HeroSection(cell, h.name(), h.subclass(), h.ability(),
                h.level(), h.exp(), h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(), h.gold(),
                h.energy(), h.hunger(), h.buffs(), h.talents(), h.talentPointsAvailable(), h.quickslots());
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), hero, screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    static Observation withTrap(Observation screen, TrapView trap) {
        MapSection map = screen.map();
        List<TrapView> traps = new ArrayList<>(map.traps());
        traps.add(trap);
        MapSection drawing = new MapSection(map.width(), map.height(), map.tiles(), map.fog(), traps, map.heaps(),
                map.blobs(), map.feeling(), map.transitions());
        Observation bare = new Observation(screen.header(), drawing, screen.actors(), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }
}
