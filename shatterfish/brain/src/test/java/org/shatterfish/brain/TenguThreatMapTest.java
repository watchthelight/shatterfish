package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tengu's second half on a constructed arena (story 10.20, issue #366): one screen per effect on which the
 * Policy of story 10.19 stands on a damage cell and strikes, and the threat map ({@link Threats}) takes the
 * hero off it, to a cell still beside him where one is free; a screen where every cell beside him is a threat
 * and the least bad is accepted; and one where the cells beside him are under a blast that passes and the hero
 * holds clear of it. The arena is {@link TenguFightTest}'s: ten columns by four rows, Tengu at column 6, row 2.
 *
 * <p>Every test here fails on the Brain before the story (docs/results/e10-practice-kit.md): the fight Policy
 * struck whenever it stood beside him, on a bomb, in the fire or in the shocker's reach, and had no reason
 * {@code tengu: dodge} or {@code tengu: hold}.
 */
class TenguThreatMapTest {

    private static final int BOSS = TenguFightTest.cell(6, 2);

    private static int cell(int x, int y) {
        return TenguFightTest.cell(x, y);
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

    static HeapView bomb(int cell) {
        return new HeapView(cell, HeapKind.HEAP, false, Threats.BOMB, 0, "");
    }

    static HeapView shocker(int cell) {
        return new HeapView(cell, HeapKind.HEAP, false, Threats.SHOCKER, 0, "");
    }

    static BlobCell blob(int cell, String kind) {
        return new BlobCell(cell, List.of(kind));
    }

    /** The arena with Tengu on his cell, the hero on {@code hero}, and these heaps and blobs drawn. */
    static Observation screen(int hero, List<HeapView> heaps, List<BlobCell> blobs) {
        Observation plain = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS), hero);
        MapSection map = plain.map();
        MapSection drawing = new MapSection(map.width(), map.height(), map.tiles(), map.fog(), map.traps(), heaps,
                blobs, map.feeling(), map.transitions());
        Observation bare = new Observation(plain.header(), drawing, plain.actors(), plain.hero(), plain.inventory(),
                plain.journal(), plain.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static boolean beside(int cell) {
        return Math.abs(cell % 12 - BOSS % 12) <= 1 && Math.abs(cell / 12 - BOSS / 12) <= 1 && cell != BOSS;
    }

    /** The cell a Step Decision lands on. */
    private static int landing(Brain.Decided decided) {
        assertTrue(decided.action() instanceof Action.Step, decided.decision().toString());
        return ((Action.Step) decided.action()).cell();
    }

    @Test
    @DisplayName("a smoke bomb lands beside the hero: it leaves the blast, and comes back to strike once the bomb is gone")
    void leaves_the_bomb_and_returns() {
        int hero = cell(5, 2);
        Observation armed = screen(hero, List.of(bomb(cell(4, 3))), List.of());
        Brain.Decided leaving = last(armed);
        assertTrue(leaving.decision().chosen().why().startsWith("tengu: dodge"), leaving.decision().toString());
        // The step goes toward the east, out of the two steps the blast reaches, and it is not a strike.
        assertTrue(landing(leaving) % 12 >= 5, leaving.decision().toString());

        // One step on, still in the blast: the walk goes on, to the free cells beside him on the east.
        Observation second = screen(landing(leaving), List.of(bomb(cell(4, 3))), List.of());
        Brain.Decided onward = last(armed, second);
        assertTrue(onward.decision().chosen().why().startsWith("tengu: dodge")
                || onward.decision().chosen().why().startsWith("tengu: approach"), onward.decision().toString());

        // Out of the blast and beside him: strike.
        Observation clear = screen(cell(7, 1), List.of(bomb(cell(4, 3))), List.of());
        assertEquals(new Action.Attack(BOSS), last(clear).action(), last(clear).decision().toString());

        // The bomb gone, the hero in the cell it left: strike, as before the story.
        Observation gone = screen(hero, List.of(), List.of());
        assertEquals(new Action.Attack(BOSS), last(gone).action(), last(gone).decision().toString());
        assertEquals("tengu: attack", last(gone).decision().chosen().why());
    }

    @Test
    @DisplayName("the fire is drawn on the hero's cell: it steps to a cell beside him that is not burning")
    void leaves_the_fire() {
        int hero = cell(5, 2);
        Observation burning = screen(hero, List.of(), List.of(blob(cell(5, 2), Threats.FIRE),
                blob(cell(5, 1), Threats.FIRE), blob(cell(5, 3), Threats.FIRE)));
        Brain.Decided decided = last(burning);
        assertTrue(decided.decision().chosen().why().startsWith("tengu: dodge"), decided.decision().toString());
        int to = landing(decided);
        assertTrue(beside(to), "a cell still beside him: " + decided.decision());
        assertFalse(List.of(cell(5, 1), cell(5, 2), cell(5, 3)).contains(to), "not onto the fire: " + decided.decision());
    }

    @Test
    @DisplayName("the shocker's cells are drawn about to strike: it steps to a cell beside him outside them")
    void leaves_the_shocker() {
        int hero = cell(5, 2);
        // The shocker at column 4, row 3, and the blobs on its nine cells.
        List<BlobCell> blobs = new ArrayList<>();
        for (int y = 2; y <= 4; y++) {
            for (int x = 3; x <= 5; x++) {
                blobs.add(blob(cell(x, y), Threats.SHOCK));
            }
        }
        Observation shocked = screen(hero, List.of(shocker(cell(4, 3))), blobs);
        Brain.Decided decided = last(shocked);
        assertTrue(decided.decision().chosen().why().startsWith("tengu: dodge"), decided.decision().toString());
        int to = landing(decided);
        assertTrue(beside(to), "still beside him: " + decided.decision());
        assertTrue(to % 12 >= 6 || to / 12 == 1, "outside the shocker's nine cells: " + decided.decision());

        // The machine stays: with the blobs not drawn this turn the cells around it are still its cells.
        Observation quiet = screen(hero, List.of(shocker(cell(4, 3))), List.of());
        assertTrue(last(quiet).decision().chosen().why().startsWith("tengu: dodge"), last(quiet).decision().toString());
    }

    @Test
    @DisplayName("every cell beside him is a threat: the hero stands on the cheapest and strikes there, and leaves a dearer one for it")
    void accepts_the_least_bad() {
        // Two bombs between them blast every cell of the room (22 each); the shocker's blobs add 12 to the west column beside him.
        List<HeapView> heaps = List.of(bomb(cell(3, 2)), bomb(cell(8, 2)));
        List<BlobCell> shock = new ArrayList<>();
        for (int cell : new int[] {cell(5, 1), cell(5, 2), cell(5, 3)}) {
            shock.add(blob(cell, Threats.SHOCK));
        }
        // Costs beside him: the west column 34, every other cell beside him 22.
        Observation dear = screen(cell(5, 2), heaps, shock);
        Threats.Map map = Threats.map(dear, Playbook.Tengu.REFERENCE);
        assertEquals(34, map.cost(cell(5, 2)));
        assertEquals(22, map.cost(cell(7, 2)));
        for (int cell = 0; cell < map.bomb().length; cell++) {
            if (dear.map().tiles().get(cell) == org.shatterfish.api.Tile.EMPTY) {
                assertTrue(map.cost(cell) >= 22, "every floor cell of the room is a threat: " + cell);
            }
        }
        Brain.Decided leaving = last(dear);
        int to = landing(leaving);
        assertEquals(22, map.cost(to), "the cheapest cell beside him: " + leaving.decision());
        assertTrue(beside(to), leaving.decision().toString());

        // On the cheapest cell it strikes where it is: no cell is better, and the walk costs a strike.
        Observation cheapest = screen(cell(7, 2), heaps, shock);
        Brain.Decided striking = last(cheapest);
        assertEquals(new Action.Attack(BOSS), striking.action(), striking.decision().toString());
        assertEquals("tengu: attack", striking.decision().chosen().why());
    }

    @Test
    @DisplayName("the cells beside him are all under a bomb about to go off and the hero is clear: it waits for it to pass")
    void holds_clear_of_a_passing_blast() {
        // The bomb at column 7, row 2 blasts columns 5 to 9: every cell beside Tengu.
        Observation clear = screen(cell(2, 2), List.of(bomb(cell(7, 2))), List.of());
        Brain.Decided decided = last(clear);
        assertEquals("tengu: hold", decided.decision().chosen().why(), decided.decision().toString());
        assertTrue(decided.action() instanceof Action.Wait, decided.decision().toString());

        // The control: with no bomb drawn the same hero walks in.
        Brain.Decided walking = last(screen(cell(2, 2), List.of(), List.of()));
        assertTrue(walking.action() instanceof Action.Step, walking.decision().toString());
        assertTrue(walking.decision().chosen().why().startsWith("tengu: approach"), walking.decision().toString());
    }

    @Test
    @DisplayName("the shocker is no blast that passes: the hero never holds for it, it walks in and strikes through the least bad cell")
    void never_holds_for_the_shocker() {
        List<BlobCell> blobs = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            for (int x = 5; x <= 7; x++) {
                if (cell(x, y) != BOSS) {
                    blobs.add(blob(cell(x, y), Threats.SHOCK));
                }
            }
        }
        Observation field = screen(cell(2, 2), List.of(), blobs);
        Brain.Decided decided = last(field);
        assertNotEquals("tengu: hold", decided.decision().chosen().why(), decided.decision().toString());
        assertTrue(decided.action() instanceof Action.Step, decided.decision().toString());
    }

    @Test
    @DisplayName("off the sealed floor, and with Tengu gone, the map is empty")
    void the_map_is_the_boss_floor_only() {
        Observation unsealed = ExplorePolicyTest.screen(Tengu.DEPTH, false, org.shatterfish.api.HeroClass.WARRIOR,
                "############", "#@.........#", "############");
        assertFalse(Threats.map(unsealed, Playbook.Tengu.REFERENCE).any());
        Observation upstairs = ExplorePolicyTest.screen(Tengu.DEPTH - 1, true, org.shatterfish.api.HeroClass.WARRIOR,
                "############", "#@.........#", "############");
        assertFalse(Threats.map(upstairs, Playbook.Tengu.REFERENCE).any());
    }
}
