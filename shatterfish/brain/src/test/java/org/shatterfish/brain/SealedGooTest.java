package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fight Policy on the sealed boss floor (issue #180): a retreat cannot break contact there, since the
 * stairs refuse every transition (Level.java:657-670) and Goo follows, so the Policy retreats only to a cell
 * that gains something, and never repeats a retreat without end.
 */
class SealedGooTest {

    private static final int WIDTH = 16;
    private static final int HEIGHT = 9;

    /** The arena's walls: its border, and a block in the middle the hero can run round, as Goo's pillar rooms allow. */
    private static boolean wall(int x, int y) {
        return x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1 || (x >= 4 && x <= 11 && y >= 3 && y <= 5);
    }

    /** An arena, the hero at {@code hero} and Goo at {@code goo} (cells), the hero at {@code hp} of 20. */
    private static Observation arena(int hero, int goo, int hp, boolean sealed) {
        String[] rows = new String[HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < WIDTH; x++) {
                int cell = x + y * WIDTH;
                row.append(wall(x, y) ? '#' : cell == hero ? '@' : cell == goo ? 'r' : '.');
            }
            rows[y] = row.toString();
        }
        Observation base = FightPolicyTest.screen(5, hp, sealed, "worn shortsword", rows);
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : base.actors().actors()) {
            actors.add(new ActorView(actor.cell(), Goo.NAME, actor.alignment(), actor.healthPips(), actor.invisible(),
                    actor.emote(), actor.buffs()));
        }
        Observation bare = new Observation(base.header(), base.map(), new ActorsSection(actors), base.hero(),
                base.inventory(), base.journal(), new LogSection(List.of()), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    /** Goo's step along the shortest way round to {@code hero}, one cell, never onto it. */
    private static int follow(int goo, int hero) {
        int[] from = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(from, -1);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        from[goo] = goo;
        queue.add(goo);
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            if (cell == hero) {
                break;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = cell % WIDTH + dx;
                    int y = cell / WIDTH + dy;
                    if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT || wall(x, y) || from[x + y * WIDTH] >= 0) {
                        continue;
                    }
                    from[x + y * WIDTH] = cell;
                    queue.add(x + y * WIDTH);
                }
            }
        }
        if (from[hero] < 0) {
            return goo;
        }
        int cell = hero;
        while (from[cell] != goo) {
            cell = from[cell];
        }
        return cell == hero ? goo : cell;
    }

    /** The reasons of the Decisions a Run makes on the arena for {@code waits} waits, Goo following the hero. */
    private static List<String> play(boolean sealed, boolean follows, int hp, int waits) {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        int hero = 7 + 1 * WIDTH;
        int goo = 9 + 1 * WIDTH;
        Belief belief = null;
        List<String> whys = new ArrayList<>();
        for (int i = 0; i < waits; i++) {
            Observation screen = arena(hero, goo, hp, sealed);
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
            whys.add(decided.decision().chosen().why());
            belief = brain.handed(screen, belief, decided);
            if (decided.action() instanceof Action.Step step) {
                hero = step.cell();
            }
            goo = follows ? follow(goo, hero) : goo;
        }
        return whys;
    }

    private static long retreats(List<String> whys) {
        return whys.stream().filter(why -> why.startsWith("retreat")).count();
    }
    private static boolean fights(List<String> whys) {
        return whys.stream().anyMatch(why -> why.startsWith("attack") || why.startsWith("cornered"));
    }

    @Test
    @DisplayName("a Goo that stays where it is: the walk away and back stops after SEALED_RETREATS, then the Policy goes to it and fights")
    void commits_to_a_standing_goo() {
        List<String> whys = play(true, false, 5, 300);
        assertTrue(retreats(whys) <= Fight.SEALED_RETREATS, "retreats on the sealed floor: " + retreats(whys));
        assertTrue(retreats(whys) > 0, "the retreat itself is kept: " + whys.subList(0, 30));
        assertTrue(fights(whys), "it fights: " + whys.subList(0, 30));
    }

    @Test
    @DisplayName("a Goo that follows: the same bound")
    void commits_to_a_following_goo() {
        List<String> whys = play(true, true, 5, 300);
        assertTrue(retreats(whys) <= Fight.SEALED_RETREATS, "retreats: " + retreats(whys));
        assertTrue(fights(whys), whys.subList(0, 30).toString());
    }

    @Test
    @DisplayName("a floor that is not sealed keeps its retreat without the bound")
    void unsealed_retreats() {
        List<String> whys = play(false, false, 5, 300);
        assertTrue(retreats(whys) > Fight.SEALED_RETREATS, "retreats: " + retreats(whys));
    }
}
