package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fight-in-corridors Policy (story 4.7, FR-31): attack when the fight is favourable by the
 * Codex's figures, take the cell fewest enemies can engage when several are coming, retreat when the
 * fight is not favourable -- toward stairs only while the floor is not sealed -- and never target a
 * character the screen does not show.
 *
 * <p>Screens are drawn as text: {@code #} wall, {@code .} floor, a space a cell never seen,
 * {@code @} the hero on floor, {@code >} the stairs down, {@code H} the hero on the stairs down,
 * {@code r} a rat, {@code B} a brute, {@code ?} an enemy the Codex has no figures for.
 */
class FightPolicyTest {

    /** A rat as the Codex has it (actors.mobs.Rat: ht 8, accuracy 8, evasion 2, damage 1-4, armour 0-1), and a brute no level-one hero should stand next to. */
    static final Codex.Knowledge KNOWLEDGE = new Codex.Knowledge(Screens.CODEX.manifest(), Screens.CODEX.families(),
            Screens.CODEX.rooms(), Screens.CODEX.guarantees(),
            List.of(new Codex.Threat("marsupial rat", 8, 8, 2, 1, 4, 0, 1),
                    new Codex.Threat("brute", 80, 30, 20, 10, 20, 0, 8)),
            List.of(new Codex.Gear("worn shortsword", 0, 5485), new Codex.Gear("worn shortsword", 1, 7009)),
            List.of(new Codex.Gear("cloth armor", 0, 1004)));

    /** A screen from rows of text, with the hero at {@code hp} of 20, the floor {@code sealed} or not. */
    static Observation screen(int hp, boolean sealed, String... rows) {
        int width = rows[0].length();
        List<Tile> tiles = new ArrayList<>();
        List<Fog> fog = new ArrayList<>();
        List<TransitionView> transitions = new ArrayList<>();
        List<ActorView> actors = new ArrayList<>();
        int hero = -1;
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                char c = rows[y].charAt(x);
                int cell = x + y * width;
                tiles.add(switch (c) {
                    case ' ' -> Tile.NONE;
                    case '#' -> Tile.WALL;
                    case '>', 'H' -> Tile.EXIT;
                    default -> Tile.EMPTY;
                });
                fog.add(c == ' ' ? Fog.UNKNOWN : Fog.VISIBLE);
                switch (c) {
                    case '@' -> hero = cell;
                    case 'H' -> {
                        hero = cell;
                        transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    }
                    case '>' -> transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    case 'r' -> actors.add(enemy(cell, "marsupial rat"));
                    case 'B' -> actors.add(enemy(cell, "brute"));
                    case '?' -> actors.add(enemy(cell, "stranger"));
                    default -> {
                    }
                }
            }
        }
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), 1, 0, sealed, false, PromptKind.NONE);
        MapSection map = new MapSection(width, rows.length, tiles, fog, List.of(), List.of(), List.of(), Feeling.NONE,
                transitions);
        HeroSection section = new HeroSection(hero, "", HeroSubclass.NONE, "", 1, 0, 1, hp, 20, 0, 10, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        List<ItemView> worn = List.of(
                new ItemView(ItemKind.WEAPON, "worn shortsword", 1, true, 0, true, false, "", EquipSlot.WEAPON, List.of(), ""),
                new ItemView(ItemKind.ARMOR, "cloth armor", 1, true, 0, true, false, "", EquipSlot.ARMOR, List.of(), ""));
        Observation bare = new Observation(header, map, new ActorsSection(actors), section, new InventorySection(worn),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), ActionsSection.NONE,
                PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static ActorView enemy(int cell, String name) {
        return new ActorView(cell, name, Alignment.ENEMY, ObservationCodec.MAX_HEALTH_PIPS, false, Emote.NONE, List.of());
    }

    private static Brain.Decided after(Observation... screens) {
        Brain brain = new Brain(KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
        }
        Brain.Decided decided = brain.decide(screens[screens.length - 1], belief);
        assertTrue(screens[screens.length - 1].actions().actions().contains(decided.action()),
                "only an Action the screen offers: " + decided.action());
        return decided;
    }

    private static int cell(Observation screen, int x, int y) {
        return x + y * screen.map().width();
    }

    @Test
    @DisplayName("two enemies coming across a room: the hero steps back into the corridor, where two cells can reach it, not four")
    void corridor_preference() {
        Observation doorway = screen(20, false,
                "############",
                "#####......#",
                "....@..r...#",
                "#####...r..#",
                "############");
        assertEquals(4, Fight.engage(doorway.map(), doorway.hero().cell()));
        Brain.Decided decided = after(doorway);
        assertEquals(Fight.NAME, decided.decision().policy());
        assertEquals(new Action.Step(cell(doorway, 3, 2)), decided.action(), decided.decision().toString());
        assertEquals("chokepoint 2", decided.decision().chosen().why());

        Observation corridor = screen(20, false,
                "############",
                "#####......#",
                "...@...r...#",
                "#####...r..#",
                "############");
        assertEquals(new Action.Wait(), after(corridor).action(), "in the corridor it holds, and lets them come one at a time");
    }

    @Test
    @DisplayName("an adjacent rat is attacked: a level-one hero with a shortsword kills it long before it does much harm")
    void attacks_when_favourable() {
        Observation rat = screen(20, false,
                "#######",
                "#.@r..#",
                "#.....#",
                "#######");
        Brain.Decided decided = after(rat);
        assertEquals(new Action.Attack(cell(rat, 3, 1)), decided.action());
        assertEquals("attack: marsupial rat", decided.decision().chosen().why());
        assertTrue(Fight.favourable(rat, KNOWLEDGE, Fight.enemies(rat)));
    }

    @Test
    @DisplayName("a lone rat across the room is approached when the fight is favourable")
    void approaches() {
        Observation rat = screen(20, false,
                "#########",
                "#.@....r#",
                "#.......#",
                "#########");
        Brain.Decided decided = after(rat);
        assertTrue(decided.decision().chosen().why().startsWith("approach "), decided.decision().toString());
        Action.Step step = (Action.Step) decided.action();
        assertTrue(Fight.nearest(rat.map(), step.cell(), Fight.enemies(rat)) < Fight.nearest(rat.map(), rat.hero().cell(),
                Fight.enemies(rat)), "each step closes on it");
    }

    @Test
    @DisplayName("against a brute the hero retreats, whatever it could hit it for, and fights only when cornered")
    void retreats_when_not() {
        Observation brute = screen(20, false,
                "#########",
                "#...@B..#",
                "#.......#",
                "#########");
        assertFalse(Fight.favourable(brute, KNOWLEDGE, Fight.enemies(brute)));
        Brain.Decided decided = after(brute);
        Action.Step step = (Action.Step) decided.action();
        assertTrue(Fight.nearest(brute.map(), step.cell(), Fight.enemies(brute)) > 1, decided.decision().toString());
        assertTrue(decided.decision().chosen().why().startsWith("retreat "), decided.decision().toString());
        assertTrue(decided.decision().alternatives().stream().anyMatch(alt -> alt.action() instanceof Action.Attack),
                "the attack it passed over is recorded");

        Observation cornered = screen(20, false,
                "#####",
                "#@B.#",
                "#####");
        Brain.Decided last = after(cornered);
        assertEquals(new Action.Attack(cell(cornered, 2, 1)), last.action());
        assertEquals("cornered: brute", last.decision().chosen().why());
    }

    @Test
    @DisplayName("the stairs are a way out while the floor is open, and never the plan on a sealed floor")
    void sealed_floor() {
        String[] rows = {
                "##########",
                "#>...@B..#",
                "#........#",
                "##########"};
        Brain.Decided open = after(screen(20, false, rows));
        assertEquals("retreat: stairs", open.decision().chosen().why());
        Brain.Decided sealed = after(screen(20, true, rows));
        assertNotEquals("retreat: stairs", sealed.decision().chosen().why(), sealed.decision().toString());
        assertTrue(sealed.decision().chosen().why().startsWith("retreat "), sealed.decision().toString());

        String[] onStairs = {
                "######",
                "#H B.#",
                "#....#",
                "######"};
        onStairs[1] = "#HB..#";
        assertEquals(new Action.Descend(), after(screen(20, false, onStairs)).action(), "on the stairs, it leaves");
        Brain.Decided locked = after(screen(20, true, onStairs));
        assertNotEquals(new Action.Descend(), locked.action());
        assertTrue(locked.decision().alternatives().stream().noneMatch(alt -> alt.action() instanceof Action.Descend),
                "a sealed floor offers no descent, and the Policy plans none");
    }

    @Test
    @DisplayName("an enemy seen and gone is not a target: with none in view the fight Policy stands aside")
    void never_an_absent_target() {
        Observation seen = screen(20, false,
                "#######",
                "#.@r..#",
                "#.....#",
                "#######");
        Observation gone = screen(20, false,
                "#######",
                "#.@...#",
                "#.....#",
                "#######");
        Brain.Decided decided = after(seen, gone);
        assertNotEquals(Fight.NAME, decided.decision().policy());
        assertFalse(decided.action() instanceof Action.Attack);
    }

    @Test
    @DisplayName("an enemy the Codex has no figures for is fought rather than fled")
    void unknown_enemy() {
        Observation stranger = screen(20, false,
                "#######",
                "#.@?..#",
                "#.....#",
                "#######");
        assertEquals(new Action.Attack(cell(stranger, 3, 1)), after(stranger).action());
    }

    @Test
    @DisplayName("the hit rule: a uniform roll under accuracy against a uniform roll under evasion")
    void hit_chance() {
        assertEquals(0.9, Fight.hitChance(10, 2), 1e-12);
        assertEquals(0.1, Fight.hitChance(2, 10), 1e-12);
        assertEquals(0.5, Fight.hitChance(7, 7), 1e-12);
        assertEquals(0.0, Fight.hitChance(0, 5), 1e-12);
        assertEquals(1.0, Fight.hitChance(5, 0), 1e-12);
        Observation rat = screen(20, false,
                "#######",
                "#.@r..#",
                "#######");
        // Shortsword 5.485 less the rat's half point of armour, landing 0.9 of the time: 8 hit
        // points in under two turns.
        assertEquals(8 / (0.9 * (5.485 - 0.5)), Fight.turnsToKill(rat, KNOWLEDGE, Fight.enemies(rat).get(0)), 1e-9);
        // The rat's 2.5 less cloth's 1.004, landing 1 - 5/16 of the time.
        assertEquals((1 - 5 / 16.0) * (2.5 - 1.004),
                Fight.enemyDamage(rat, KNOWLEDGE, KNOWLEDGE.threat("marsupial rat")), 1e-9);
    }

    @Test
    @DisplayName("hurt badly enough, even a rat is not worth the risk")
    void hurt_retreats() {
        Observation rat = screen(2, false,
                "#########",
                "#...@r..#",
                "#.......#",
                "#########");
        assertFalse(Fight.favourable(rat, KNOWLEDGE, Fight.enemies(rat)));
        assertTrue(after(rat).action() instanceof Action.Step);
    }
}
