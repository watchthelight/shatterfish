package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dart-trap Memory is fair (story 10.19, issue #365; Non-negotiable 1). Tengu lays his traps hidden and
 * the game draws them for two turns ({@link Darts}); the Brain may keep the cells it was shown and nothing
 * else. Three claims, each held on constructed screens, where the only input to the Brain is the screen:
 *
 * <ol>
 *   <li><b>No field reads the cells after the draw ends.</b> The Memory's dart cells are a function of the
 *       drawings the Brain was shown, replayed here by a model written apart from {@link Darts}, over random
 *       histories; and a screen after the draw has ended carries no trap, so there is nothing else for a
 *       field to have read.</li>
 *   <li><b>A trap never drawn is not avoided.</b> A hero shown no drawing walks the cells a hidden patch
 *       would hold, and its Memory is empty, whatever is hidden.</li>
 *   <li><b>The permutation control.</b> Two histories alike on every screen up to the draw, whose patches
 *       then differ, give the same Decisions up to the draw and different ones after it. Without the second
 *       half the first would hold for a Brain that never looked at traps.</li>
 * </ol>
 */
class TenguTrapMemoryLeakTest {

    private static final int BOSS = TenguFightTest.cell(10, 2);

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    /** The Decisions at each of {@code screens}, driven as a Run drives them. */
    private static List<Brain.Decided> decisions(List<Observation> screens) {
        Brain brain = brain();
        Belief belief = null;
        List<Brain.Decided> all = new ArrayList<>();
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            all.add(decided);
            belief = brain.handed(screen, belief, decided);
        }
        return all;
    }

    private static List<Integer> memory(List<Observation> screens) {
        Brain brain = brain();
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            belief = brain.handed(screen, belief, brain.decide(screen, belief));
        }
        return Memory.of(belief).darts().stream().map(Memory.Spot::cell).toList();
    }

    @Test
    @DisplayName("the Memory's dart cells are the last drawing of the fight in view and nothing else, over 200 random histories")
    void memory_is_the_last_drawing_and_nothing_else() {
        Random random = new Random(365);
        for (int history = 0; history < 200; history++) {
            Brain brain = brain();
            Belief belief = null;
            List<Integer> model = List.of();
            int hero = TenguFightTest.cell(2, 2);
            for (int wait = 0; wait < 12; wait++) {
                boolean sealed = random.nextInt(8) != 0;
                int boss = random.nextInt(6) == 0 ? -1 : BOSS;
                int[] drawn = random.nextInt(3) == 0 ? cells(random) : new int[0];
                hero = TenguFightTest.cell(1 + random.nextInt(4), 1 + random.nextInt(4));
                Observation screen = TenguFightTest.withHero(TenguFightTest.arena(sealed, boss, drawn), hero);
                // The model, from the rule in words (Darts): nothing off the sealed floor, the drawing when
                // there is one, the earlier cells while Tengu stays in view, else nothing.
                if (!sealed) {
                    model = List.of();
                } else if (drawn.length > 0) {
                    model = new TreeSet<>(java.util.Arrays.stream(drawn).boxed().toList()).stream().toList();
                } else if (boss < 0) {
                    model = List.of();
                }
                belief = brain.update(screen, belief);
                belief = brain.handed(screen, belief, brain.decide(screen, belief));
                List<Integer> held = Memory.of(belief).darts().stream().map(Memory.Spot::cell).sorted().toList();
                assertEquals(model, held, "history " + history + " wait " + wait);
            }
        }
    }

    private static int[] cells(Random random) {
        TreeSet<Integer> set = new TreeSet<>();
        int count = 1 + random.nextInt(8);
        for (int i = 0; i < count; i++) {
            set.add(TenguFightTest.cell(1 + random.nextInt(10), 1 + random.nextInt(4)));
        }
        return set.stream().mapToInt(Integer::intValue).toArray();
    }

    @Test
    @DisplayName("a patch the hero was never shown is not avoided: the walk goes through its cells and the Memory is empty")
    void an_unseen_trap_is_not_avoided() {
        // The screens a hero sees whether or not a hidden patch lies across the room are one screen.
        List<Observation> walk = new ArrayList<>();
        for (int x = 1; x <= 8; x++) {
            walk.add(TenguFightTest.withHero(TenguFightTest.arena(true, BOSS), TenguFightTest.cell(x, 2)));
        }
        List<Brain.Decided> decided = decisions(walk);
        for (int i = 0; i < decided.size(); i++) {
            Action.Step step = (Action.Step) decided.get(i).action();
            assertEquals(i + 2, step.cell() % 12, "one column nearer, never aside for a trap: " + decided.get(i).decision());
        }
        assertEquals(List.of(), memory(walk));
    }

    @Test
    @DisplayName("histories alike up to the draw and then different: the same Decisions before it, different ones after it")
    void the_permutation_control() {
        Observation before = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS), TenguFightTest.cell(3, 2));
        Observation next = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS), TenguFightTest.cell(3, 2));
        // Two patches the game might lay: across the straight walk, and far from it.
        int[] across = {TenguFightTest.cell(4, 1), TenguFightTest.cell(4, 2), TenguFightTest.cell(4, 3), TenguFightTest.cell(4, 4)};
        int[] aside = {TenguFightTest.cell(8, 4), TenguFightTest.cell(9, 4), TenguFightTest.cell(8, 3), TenguFightTest.cell(9, 3)};
        Observation drawnAcross = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS, across), TenguFightTest.cell(3, 2));
        Observation drawnAside = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS, aside), TenguFightTest.cell(3, 2));
        Observation gone = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS), TenguFightTest.cell(3, 2));

        List<Brain.Decided> a = decisions(List.of(before, next, drawnAcross, gone));
        List<Brain.Decided> b = decisions(List.of(before, next, drawnAside, gone));
        assertEquals(a.get(0).action(), b.get(0).action(), "before the draw the two histories are one");
        assertEquals(a.get(1).action(), b.get(1).action(), "still one");
        // After the draw the hero acts on what it was shown. The control: the two do differ, or the equality above says nothing.
        assertNotEquals(a.get(3).action(), b.get(3).action(), "the cells shown moved the Decision after the draw ended: "
                + a.get(3).decision().chosen().why() + " / " + b.get(3).decision().chosen().why());
        assertTrue(memory(List.of(before, next, drawnAcross, gone)).containsAll(
                java.util.Arrays.stream(across).boxed().toList()));
        assertEquals(List.of(), memory(List.of(before, next, gone)));
    }
}
