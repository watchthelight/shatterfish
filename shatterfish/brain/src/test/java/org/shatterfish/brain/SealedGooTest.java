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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("a Goo that stays where it is: the Policy does not walk away and back for ever, it goes to it and fights")
    void commits_to_a_standing_goo() {
        List<String> whys = play(true, false, 5, 300);
        assertEquals(0, retreats(whys), "no retreat gains anything on the open sealed floor: " + whys.subList(0, 30));
        assertTrue(fights(whys), "it fights: " + whys.subList(0, 30));
    }

    @Test
    @DisplayName("a Goo that follows: the same, the Policy fights from the first wait")
    void commits_to_a_following_goo() {
        List<String> whys = play(true, true, 5, 300);
        assertEquals(0, retreats(whys), whys.subList(0, 30).toString());
        assertTrue(fights(whys), whys.subList(0, 30).toString());
    }

    @Test
    @DisplayName("a floor that is not sealed keeps its retreat: the stairs or the walk away can still end the fight")
    void unsealed_retreats() {
        List<String> whys = play(false, false, 5, 300);
        assertTrue(retreats(whys) > Fight.SEALED_RETREATS, "retreats: " + retreats(whys));
    }

    /** The arena with the cell {@code water} drawn as water and the hero showing {@code buff}, if any. */
    private static Observation oozed(int hero, int goo, int water, String buff) {
        Observation base = arena(hero, goo, 5, true);
        List<org.shatterfish.api.Tile> tiles = new ArrayList<>(base.map().tiles());
        tiles.set(water, org.shatterfish.api.Tile.WATER);
        org.shatterfish.api.MapSection map = base.map();
        org.shatterfish.api.MapSection watered = new org.shatterfish.api.MapSection(map.width(), map.height(), tiles,
                map.fog(), map.traps(), map.heaps(), map.blobs(), map.feeling(), map.transitions());
        org.shatterfish.api.HeroSection h = base.hero();
        List<org.shatterfish.api.BuffView> buffs = buff == null ? List.of()
                : List.of(new org.shatterfish.api.BuffView(buff, true, 1000));
        org.shatterfish.api.HeroSection oozy = new org.shatterfish.api.HeroSection(h.cell(), h.name(), h.subclass(),
                h.ability(), h.level(), h.exp(), h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(),
                h.strengthBonus(), h.gold(), h.energy(), h.hunger(), buffs, h.talents(), h.talentPointsAvailable(),
                h.quickslots());
        Observation bare = new Observation(base.header(), watered, base.actors(), oozy, base.inventory(), base.journal(),
                base.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    @Test
    @DisplayName("an oozed hero beside Goo retreats to water, which washes the ooze off; one not oozed fights")
    void water_for_ooze() {
        int hero = 7 + 1 * WIDTH;
        int goo = 8 + 1 * WIDTH;
        int water = 6 + 1 * WIDTH;
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        Observation wet = oozed(hero, goo, water, Fight.OOZE);
        Brain.Decided decided = brain.decide(wet, brain.update(wet, null));
        assertEquals(new Action.Step(water), decided.action(), decided.decision().toString());
        assertEquals("retreat water", decided.decision().chosen().why());
        Observation dry = oozed(hero, goo, water, null);
        Brain.Decided fought = brain.decide(dry, brain.update(dry, null));
        assertTrue(fought.decision().chosen().why().startsWith("cornered"), fought.decision().toString());
    }

    @Test
    @DisplayName("a Goo standing in water heals there: the hero steps away to pull it onto dry ground, and fights once it is dry")
    void goo_off_its_water() {
        int hero = 7 + 1 * WIDTH;
        int goo = 8 + 1 * WIDTH;
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        Observation wet = oozed(hero, goo, goo, null);
        Brain.Decided decided = brain.decide(wet, brain.update(wet, null));
        assertEquals("retreat dry", decided.decision().chosen().why(), decided.decision().toString());
        Observation dry = oozed(hero, goo, 2 + 1 * WIDTH, null);
        Brain.Decided fought = brain.decide(dry, brain.update(dry, null));
        assertTrue(fought.decision().chosen().why().startsWith("cornered"), fought.decision().toString());
    }

    @Test
    @DisplayName("a gainful retreat is not repeated without end either: after SEALED_RETREATS of them the Policy fights")
    void budget() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        Belief belief = null;
        int retreats = 0;
        for (int i = 0; i < 4 * Fight.SEALED_RETREATS; i++) {
            // Each wait a fresh cell along the arena's top rows, Goo behind and water ahead, oozed again each
            // time: a retreat to water every wait, and no two cells alike for the bounce guard to catch.
            int hero = (2 + i % 12) + (1 + i / 12 % 2) * WIDTH;
            Observation screen = oozed(hero, hero - 1, hero + 1, Fight.OOZE);
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            if (decided.decision().chosen().why().startsWith("retreat")) {
                retreats++;
            }
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(Fight.SEALED_RETREATS, retreats);
    }
}
