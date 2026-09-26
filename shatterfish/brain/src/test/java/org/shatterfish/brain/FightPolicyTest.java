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
import org.shatterfish.api.RunLog;
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
 * Codex's figures, take the cell fewest enemies can engage when several are closing in, retreat when
 * the fight is not favourable -- by the regular stairs only while the floor is not sealed -- and
 * never target a character the screen does not show. The multi-wait cases drive several screens
 * through the Brain as its driver does, because the loops a single screen cannot show are the ones
 * that end Runs.
 *
 * <p>Screens are drawn as text: {@code #} wall, {@code .} floor, a space a cell never seen,
 * {@code @} the hero on floor, {@code >} the stairs down, {@code <} the stairs up, {@code H} the hero
 * on the stairs down, {@code U} the hero on the stairs up, {@code S} the hero on the surface stairs,
 * {@code r} a rat, {@code i} a rat drawn faint (invisible), {@code B} a brute, {@code ?} an enemy the
 * Codex has no figures for, {@code L} a lasher that cannot move, {@code T} an animated statue.
 */
class FightPolicyTest {

    /**
     * A rat as the Codex has it (actors.mobs.Rat: ht 8, accuracy 8, evasion 2, damage 1-4, armour
     * 0-1), a brute no level-one hero should stand next to, a lasher that cannot move, and the
     * shortsword and cloth armour as the Codex measured them.
     */
    static final Codex.Knowledge KNOWLEDGE = new Codex.Knowledge(Screens.CODEX.manifest(), Screens.CODEX.families(),
            Screens.CODEX.rooms(), Screens.CODEX.guarantees(),
            List.of(new Codex.Threat("marsupial rat", 8, 8, 2, 1, 4, 0, 1),
                    new Codex.Threat("brute", 80, 30, 20, 10, 20, 0, 8),
                    new Codex.Threat("lasher", 40, 25, 0, 8, 12, 0, 8)),
            List.of(new Codex.Gear("worn shortsword", 0, 1, 10, 5485), new Codex.Gear("worn shortsword", 1, 2, 12, 7009),
                    new Codex.Gear("dagger", 0, 1, 8, 4500), new Codex.Gear(Fight.MAGES_STAFF, 0, 1, 6, 3500)),
            List.of(new Codex.Gear("cloth armor", 0, 0, 2, 1004)),
            List.of("lasher"));

    static Observation screen(int hp, boolean sealed, String... rows) {
        return screen(1, hp, sealed, "worn shortsword", rows);
    }

    /** A screen at {@code depth} from rows of text, the hero at {@code hp} of 20 wielding {@code weapon}. */
    static Observation screen(int depth, int hp, boolean sealed, String weapon, String... rows) {
        return screen(depth, hp, sealed, weapon, Hunger.NONE, rows);
    }

    /** As above, the hero {@code hunger}. */
    static Observation screen(int depth, int hp, boolean sealed, String weapon, Hunger hunger, String... rows) {
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
                    case '<', 'U', 'S' -> Tile.ENTRANCE;
                    case 'c' -> Tile.CHASM;
                    default -> Tile.EMPTY;
                });
                fog.add(c == ' ' ? Fog.UNKNOWN : Fog.VISIBLE);
                switch (c) {
                    case '@' -> hero = cell;
                    case 'H' -> {
                        hero = cell;
                        transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    }
                    case 'U' -> {
                        hero = cell;
                        transitions.add(new TransitionView(cell, TransitionKind.REGULAR_ENTRANCE));
                    }
                    case 'S' -> {
                        hero = cell;
                        transitions.add(new TransitionView(cell, TransitionKind.SURFACE));
                    }
                    case '>' -> transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    case '<' -> transitions.add(new TransitionView(cell, TransitionKind.REGULAR_ENTRANCE));
                    case 'r' -> actors.add(enemy(cell, "marsupial rat", false));
                    case 'i' -> actors.add(enemy(cell, "marsupial rat", true));
                    case 'B' -> actors.add(enemy(cell, "brute", false));
                    case '?' -> actors.add(enemy(cell, "stranger", false));
                    case 'L' -> actors.add(enemy(cell, "lasher", false));
                    case 'T' -> actors.add(enemy(cell, "animated statue", false));
                    case 'X' -> actors.add(new ActorView(cell, "animated statue", Alignment.ENEMY, 5, false, Emote.NONE,
                            List.of()));
                    case 'A' -> actors.add(new ActorView(cell, "animated statue", Alignment.ENEMY,
                            ObservationCodec.MAX_HEALTH_PIPS, false, Emote.ALERT, List.of()));
                    default -> {
                    }
                }
            }
        }
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, sealed, false, PromptKind.NONE);
        MapSection map = new MapSection(width, rows.length, tiles, fog, List.of(), List.of(), List.of(), Feeling.NONE,
                transitions);
        HeroSection section = new HeroSection(hero, "", HeroSubclass.NONE, "", 1, 0, 1, hp, 20, 0, 10, 0, 0, 0,
                hunger, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        List<ItemView> worn = weapon == null
                ? List.of(new ItemView(ItemKind.ARMOR, "cloth armor", 1, true, 0, true, false, "", EquipSlot.ARMOR, List.of(), ""))
                : List.of(new ItemView(ItemKind.WEAPON, weapon, 1, true, 0, true, false, "", EquipSlot.WEAPON, List.of(), ""),
                        new ItemView(ItemKind.ARMOR, "cloth armor", 1, true, 0, true, false, "", EquipSlot.ARMOR, List.of(), ""));
        Observation bare = new Observation(header, map, new ActorsSection(actors), section, new InventorySection(worn),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), ActionsSection.NONE,
                PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static ActorView enemy(int cell, String name, boolean invisible) {
        return new ActorView(cell, name, Alignment.ENEMY, ObservationCodec.MAX_HEALTH_PIPS, invisible, Emote.NONE, List.of());
    }

    private static Brain brain() {
        return new Brain(KNOWLEDGE, Screens.WEIGHTS, 9L);
    }

    /** The Decision at the last of {@code screens}, the earlier ones driven through the Brain. */
    private static Brain.Decided after(Observation... screens) {
        Brain brain = brain();
        Belief belief = Screens.drive(brain, screens);
        Brain.Decided decided = brain.decide(screens[screens.length - 1], belief);
        assertTrue(screens[screens.length - 1].actions().actions().contains(decided.action()),
                "only an Action the screen offers: " + decided.action());
        return decided;
    }

    /** The Decisions at each of {@code screens} in turn, driven as the Brain's driver drives them. */
    private static List<Brain.Decided> each(Observation... screens) {
        Brain brain = brain();
        Belief belief = null;
        List<Brain.Decided> all = new ArrayList<>();
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
            all.add(decided);
            belief = brain.handed(screen, belief, decided);
        }
        return all;
    }

    private static int cell(Observation screen, int x, int y) {
        return x + y * screen.map().width();
    }

    private static String why(Brain.Decided decided) {
        return decided.decision().chosen().why();
    }

    // --------------------------------------------------------------------------------- one screen

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
        assertEquals("chokepoint 2", why(decided));

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
        assertEquals("attack: marsupial rat", why(decided));
        assertTrue(Fight.favourable(rat, KNOWLEDGE, Fight.enemies(rat)));
    }

    @Test
    @DisplayName("a rat drawn faint is still on the screen, and is fought like any other")
    void faint_enemy() {
        Observation faint = screen(20, false,
                "#######",
                "#.@i..#",
                "#######");
        assertEquals(new Action.Attack(cell(faint, 3, 1)), after(faint).action());
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
        assertTrue(why(decided).startsWith("approach "), decided.decision().toString());
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
        assertTrue(why(decided).startsWith("retreat "), decided.decision().toString());
        assertTrue(decided.decision().alternatives().stream().anyMatch(alt -> alt.action() instanceof Action.Attack),
                "the attack it passed over is recorded");

        Observation cornered = screen(20, false,
                "#####",
                "#@B.#",
                "#####");
        Brain.Decided last = after(cornered);
        assertEquals(new Action.Attack(cell(cornered, 2, 1)), last.action());
        assertEquals("cornered: brute", why(last));
    }

    @Test
    @DisplayName("the stairs are a way out while the floor is open, never the plan on a sealed floor, and never the surface")
    void stairs() {
        String[] rows = {
                "##########",
                "#>...@B..#",
                "#........#",
                "##########"};
        assertEquals("retreat: stairs", why(after(screen(20, false, rows))));
        Brain.Decided sealed = after(screen(20, true, rows));
        assertNotEquals("retreat: stairs", why(sealed), sealed.decision().toString());
        assertTrue(why(sealed).startsWith("retreat "), sealed.decision().toString());

        String[] onStairs = {
                "######",
                "#HB..#",
                "#....#",
                "######"};
        assertEquals(new Action.Descend(), after(screen(20, false, onStairs)).action(), "on the stairs, it leaves");
        Brain.Decided locked = after(screen(20, true, onStairs));
        assertNotEquals(new Action.Descend(), locked.action());
        assertTrue(locked.decision().alternatives().stream().noneMatch(alt -> alt.action() instanceof Action.Descend),
                "a sealed floor offers no descent, and the Policy plans none");

        // The surface stairs are offered as an ascent (ValidActions), but at depth 1 they open a
        // window and the hero stays (SewerLevel.java:146-156): not a way out of a fight.
        Observation surface = screen(1, 20, false, "worn shortsword",
                "######",
                "#SB..#",
                "#....#",
                "######");
        assertTrue(surface.actions().actions().contains(new Action.Ascend()));
        Brain.Decided up = after(surface);
        assertNotEquals(new Action.Ascend(), up.action(), up.decision().toString());
        assertTrue(new Fight(KNOWLEDGE).ranked(surface, Memory.START, surface.actions().actions(), Stream.at(1, 1))
                .stream().noneMatch(choice -> choice.action() instanceof Action.Ascend), "the fight Policy never ranks it");
    }

    @Test
    @DisplayName("the way to the stairs does not pass beside the enemy it flees")
    void stairs_path_keeps_clear() {
        // The stairs lie past the brute: the only way there runs beside it, so the Policy steps away.
        Observation past = screen(20, false,
                "##########",
                "#...@B..>#",
                "##########");
        Brain.Decided decided = after(past);
        assertNotEquals("retreat: stairs", why(decided), decided.decision().toString());
        assertEquals(new Action.Step(cell(past, 3, 1)), decided.action());
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
    @DisplayName("an Attack is only ever on a cell a shown enemy stands on")
    void attack_only_shown() {
        Observation rat = screen(20, false,
                "#######",
                "#.@r..#",
                "#######");
        // Ranked with an Attack on an empty cell in the offered set: the Policy never takes it.
        List<Action> offered = new ArrayList<>(rat.actions().actions());
        offered.add(new Action.Attack(cell(rat, 1, 1)));
        List<RunLog.Choice> ranked = new Fight(KNOWLEDGE).ranked(rat, Memory.START, offered, Stream.at(1, 1));
        assertTrue(ranked.stream().noneMatch(choice -> choice.action().equals(new Action.Attack(cell(rat, 1, 1)))), ranked.toString());
        assertEquals(new Action.Attack(cell(rat, 3, 1)), ranked.get(0).action());
    }

    @Test
    @DisplayName("an enemy the Codex has no figures for is taken at a pessimistic figure: fought when healthy, fled when hurt")
    void unknown_enemy() {
        String[] rows = {
                "#######",
                "#.@?..#",
                "#.....#",
                "#######"};
        assertEquals(new Action.Attack(cell(screen(20, false, rows), 3, 1)), after(screen(20, false, rows)).action());
        Brain.Decided hurt = after(screen(9, false, rows));
        assertTrue(hurt.action() instanceof Action.Step, "an unknown enemy is no reason to stay: " + hurt.decision());
        assertTrue(Fight.turnsToKill(screen(20, false, rows), KNOWLEDGE, Fight.enemies(screen(20, false, rows)).get(0)) > 0,
                "an unknown enemy is not assumed dead already");
    }

    @Test
    @DisplayName("hurt badly enough, even a rat is not worth the risk: never favourable while the status pane warns")
    void hurt_retreats() {
        Observation rat = screen(6, false,
                "#########",
                "#...@r..#",
                "#.......#",
                "#########");
        assertFalse(Fight.favourable(rat, KNOWLEDGE, Fight.enemies(rat)));
        assertTrue(after(rat).action() instanceof Action.Step);
    }

    @Test
    @DisplayName("an animated statue is scenery: neither fought nor fled, and the floor is explored past it")
    void passive_statue() {
        Observation statue = screen(20, false,
                "#######",
                "#.@T.  ",
                "#......",
                "#######");
        Brain.Decided decided = after(statue);
        assertNotEquals(Fight.NAME, decided.decision().policy());
        assertFalse(decided.action() instanceof Action.Attack);
        assertEquals(Explore.NAME, decided.decision().policy());
    }

    @Test
    @DisplayName("the hero's weapon is found by its name inside an enchanted name, and a mage's staff by the wand it holds")
    void gear_names() {
        String[] rows = {"#####", "#@r.#", "#####"};
        assertEquals(new Codex.Gear("worn shortsword", 0, 1, 10, 5485),
                Fight.worn(screen(1, 20, false, "blazing worn shortsword", rows), KNOWLEDGE.weapons(), EquipSlot.WEAPON));
        assertEquals(new Codex.Gear(Fight.MAGES_STAFF, 0, 1, 6, 3500),
                Fight.worn(screen(1, 20, false, "staff of magic missile", rows), KNOWLEDGE.weapons(), EquipSlot.WEAPON));
        assertEquals(new Codex.Gear("cloth armor", 0, 0, 2, 1004),
                Fight.worn(screen(1, 20, false, "dagger", rows), KNOWLEDGE.armours(), EquipSlot.ARMOR));
        // A weapon worn that the Codex does not know is the weakest one measured, never bare hands.
        assertEquals(new Codex.Gear(Fight.MAGES_STAFF, 0, 1, 6, 3500),
                Fight.worn(screen(1, 20, false, "mystery blade", rows), KNOWLEDGE.weapons(), EquipSlot.WEAPON));
        assertEquals(null, Fight.worn(screen(1, 20, false, null, rows), KNOWLEDGE.weapons(), EquipSlot.WEAPON));
        assertEquals(1, Fight.heroDamage(screen(1, 20, false, null, rows), KNOWLEDGE)[0], "bare hands: 1 to STR - 8");
        assertEquals(2, Fight.heroDamage(screen(1, 20, false, null, rows), KNOWLEDGE)[1]);
    }

    @Test
    @DisplayName("the hit rule, and the exact expectation of a roll less a reduction roll")
    void arithmetic() {
        assertEquals(0.9, Fight.hitChance(10, 2), 1e-12);
        assertEquals(0.1, Fight.hitChance(2, 10), 1e-12);
        assertEquals(0.5, Fight.hitChance(7, 7), 1e-12);
        assertEquals(0.0, Fight.hitChance(0, 5), 1e-12);
        assertEquals(1.0, Fight.hitChance(5, 0), 1e-12);
        // NormalIntRange(1, 4): the floor of a triangular variable, 1/8, 3/8, 3/8, 1/8.
        double[] p = Fight.normal(1, 4);
        assertEquals(0.125, p[0], 1e-12);
        assertEquals(0.375, p[1], 1e-12);
        assertEquals(0.375, p[2], 1e-12);
        assertEquals(0.125, p[3], 1e-12);
        assertEquals(2.0, Fight.landed(1, 4, 0, 1), 1e-12, "no overlap: the difference of the means");
        // Overlapping ranges: 0-2 less 0-2, each 2/9, 5/9, 2/9. E[max(0, X - Y)] = 28/81, where the
        // difference of the means says 0.
        assertEquals(28.0 / 81, Fight.landed(0, 2, 0, 2), 1e-12);
    }

    // ------------------------------------------------------------------------------ several waits

    @Test
    @DisplayName("two attacks from one cell are not a refused step: when the rat is dead, explore goes on")
    void attacks_are_not_refusals() {
        Observation rat = screen(20, false,
                "#######",
                "#.@r.  ",
                "#......",
                "#######");
        Observation calm = screen(20, false,
                "#######",
                "#.@..  ",
                "#......",
                "#######");
        List<Brain.Decided> all = each(rat, rat, calm, calm);
        assertTrue(all.get(0).action() instanceof Action.Attack);
        assertTrue(all.get(1).action() instanceof Action.Attack);
        assertEquals(Explore.NAME, all.get(2).decision().policy(), all.get(2).decision().toString());
        assertEquals(Explore.NAME, all.get(3).decision().policy(), "and the step it took was not blocked");
        Brain brain = brain();
        Memory memory = Memory.of(Screens.drive(brain, rat, rat, calm));
        assertEquals(0, memory.streak());
        assertTrue(memory.blocked().isEmpty(), memory.blocked().toString());
    }

    @Test
    @DisplayName("a floor fled by the stairs is not walked back into until the hero is healed")
    void no_stairs_ping_pong() {
        Observation fleeing = screen(2, 20, false, "worn shortsword",
                "######",
                "#UB..#",
                "#....#",
                "######");
        Observation above = screen(1, 12, false, "worn shortsword",
                "######",
                "#<.H.#",
                "######");
        Observation healed = screen(1, 20, false, "worn shortsword",
                "######",
                "#<.H.#",
                "######");
        List<Brain.Decided> all = each(fleeing, above, above, healed);
        assertEquals(new Action.Ascend(), all.get(0).action(), "fled up");
        assertEquals("rest: before-descent", why(all.get(1)), all.get(1).decision().toString());
        assertEquals("rest: before-descent", why(all.get(2)));
        assertFalse(why(all.get(3)).startsWith("rest"), "healed, it goes on: " + all.get(3).decision());
    }

    @Test
    @DisplayName("enemies that do not come are not waited for: the hero goes to them rather than step in and out of a corridor")
    void no_chokepoint_oscillation() {
        Observation doorway = screen(20, false,
                "############",
                "#####......#",
                "....@..r...#",
                "#####...r..#",
                "############");
        Observation corridor = screen(20, false,
                "############",
                "#####......#",
                "...@...r...#",
                "#####...r..#",
                "############");
        List<Brain.Decided> all = each(doorway, corridor, doorway, doorway);
        assertEquals("chokepoint 2", why(all.get(0)), "first sight: they may be coming");
        assertTrue(why(all.get(1)).startsWith("approach "), "they stood still: " + all.get(1).decision());
        assertTrue(why(all.get(2)).startsWith("approach "), "and it keeps going: " + all.get(2).decision());
        assertTrue(why(all.get(3)).startsWith("approach "), all.get(3).decision().toString());
    }

    @Test
    @DisplayName("a chokepoint is held while the enemies close in, and at most four waits")
    void bounded_hold() {
        List<Observation> screens = new ArrayList<>();
        for (int step = 0; step < 6; step++) {
            char[] row = "#.@..........#".toCharArray();
            char[] other = "##############".toCharArray();
            row[12 - step] = 'r';
            screens.add(screen(20, false, new String(other), new String(row), "#.#########.##".substring(0, 14),
                    new String(other)));
        }
        List<Brain.Decided> all = each(screens.toArray(new Observation[0]));
        assertTrue(all.stream().allMatch(decided -> Fight.NAME.equals(decided.decision().policy())));
        // One rat is not a crowd: a lone enemy is approached, never held for.
        assertTrue(why(all.get(0)).startsWith("approach "), all.get(0).decision().toString());

        List<Observation> two = new ArrayList<>();
        for (int step = 0; step < 7; step++) {
            char[] row = "...@..............#".toCharArray();
            char[] low = "#####.............#".toCharArray();
            row[16 - step] = 'r';
            low[17 - step] = 'r';
            two.add(screen(20, false, "###################", "#####.............#", new String(row), new String(low),
                    "###################"));
        }
        List<Brain.Decided> held = each(two.toArray(new Observation[0]));
        for (int i = 0; i < Fight.HOLDS; i++) {
            assertEquals("hold: chokepoint", why(held.get(i)), i + ": " + held.get(i).decision());
        }
        assertTrue(why(held.get(Fight.HOLDS)).startsWith("approach "), "the holds ran out: " + held.get(Fight.HOLDS).decision());
    }

    @Test
    @DisplayName("after a retreat from a brute out of sight, explore keeps out of the region instead of walking back into view")
    void retreat_is_remembered() {
        Observation brute = screen(20, false,
                "###########",
                "#....@.B.  ",
                "#........  ",
                "###########");
        Observation away = screen(20, false,
                "###########",
                "#...@.     ",
                "#.....     ",
                "###########");
        List<Brain.Decided> all = each(brute, away, away);
        assertTrue(why(all.get(0)).startsWith("retreat "), all.get(0).decision().toString());
        Brain.Decided next = all.get(1);
        assertEquals(Explore.NAME, next.decision().policy());
        if (next.action() instanceof Action.Step step) {
            assertTrue(step.cell() % away.map().width() <= 4, "not back toward the brute: " + next.decision());
        }
    }

    @Test
    @DisplayName("a lasher that cannot move is no crowd: the hero does not hold a chokepoint waiting for it")
    void immovable_is_not_waited_for() {
        Observation lasher = screen(20, false,
                "############",
                "#####......#",
                "...@...r.L.#",
                "#####......#",
                "############");
        Brain.Decided decided = after(lasher);
        assertNotEquals("hold: chokepoint", why(decided), decided.decision().toString());
    }

    @Test
    @DisplayName("whenever the fight Policy enters, it returns an Action")
    void always_acts() {
        // A brute beside the hero in a dead end with nowhere farther to go and the fight unfavourable:
        // cornered, it attacks; and with no enemy adjacent and nowhere to retreat, it holds.
        Observation boxed = screen(3, false,
                "#####",
                "#@.B#",
                "#####");
        Brain.Decided decided = after(boxed);
        assertEquals(Fight.NAME, decided.decision().policy());
        assertTrue(decided.action() != null);
    }

    // ------------------------------------------------------------------ the verification pass (4.7)

    @Test
    @DisplayName("a retreat, and explore's way out of an avoided region, never step onto a chasm")
    void never_onto_a_chasm() {
        // Beside a brute with only a chasm behind: nothing farther is walkable, so cornered, it attacks.
        Observation brink = screen(20, false,
                "######",
                "#c@B.#",
                "######");
        assertTrue(brink.actions().actions().contains(new Action.Step(cell(brink, 1, 1))), "the chasm is offered as a step");
        Brain.Decided decided = after(brink);
        assertTrue(decided.action() instanceof Action.Attack, decided.decision().toString());
        assertEquals("cornered: brute", why(decided));
    }

    @Test
    @DisplayName("an avoided region over the only way on is walked through rather than left to chance")
    void region_over_the_only_corridor() {
        // The retreat leaves a region around the brute's cell; the only corridor to the unexplored
        // part runs through it. Once the hero is out of the region, explore still plans a way on.
        Observation brute = screen(20, false,
                "###########",
                "#.....@.B..",
                "###########");
        Observation later = screen(20, false,
                "#############",
                "#@.........  ",
                "#############");
        List<Brain.Decided> all = each(brute, later, later);
        assertTrue(why(all.get(0)).startsWith("retreat "), all.get(0).decision().toString());
        assertEquals(Explore.NAME, all.get(2).decision().policy(), "explore plans on: " + all.get(2).decision());
        assertTrue(all.get(2).action() instanceof Action.Step, all.get(2).decision().toString());
    }

    @Test
    @DisplayName("the rest before going back down stops for hunger, is capped, and is owed again only after another flight")
    void rest_is_bounded() {
        Observation fleeing = screen(2, 20, false, "worn shortsword",
                "######",
                "#UB..#",
                "#....#",
                "######");
        Observation hungry = screen(1, 12, false, "worn shortsword", Hunger.HUNGRY,
                "######",
                "#<.H.#",
                "######");
        List<Brain.Decided> fed = each(fleeing, hungry);
        // The explore Policy's rest before going back down stops for hunger. (A hungry hero with no food
        // and nothing left to uncover is going down for food, and the descend Policy rests it beside the
        // exit first: a hungry hero regenerates, only a starving one does not; story 4.12.)
        assertFalse(why(fed.get(1)).equals("rest: before-descent"), "explore does not wait to heal a hungry hero: "
                + fed.get(1).decision());

        Observation above = screen(1, 12, false, "worn shortsword",
                "######",
                "#<.H.#",
                "######");
        List<Observation> screens = new ArrayList<>();
        screens.add(fleeing);
        for (int i = 0; i <= Explore.RESTS; i++) {
            screens.add(above);
        }
        List<Brain.Decided> all = each(screens.toArray(new Observation[0]));
        assertEquals("rest: before-descent", why(all.get(1)));
        assertEquals("rest: before-descent", why(all.get(Explore.RESTS)));
        assertFalse(why(all.get(Explore.RESTS + 1)).startsWith("rest"), "the rests ran out: " + all.get(Explore.RESTS + 1).decision());

        Observation healed = screen(1, 20, false, "worn shortsword",
                "######",
                "#<.H.#",
                "######");
        Brain brain = brain();
        Memory memory = Memory.of(Screens.drive(brain, fleeing, above, healed, above));
        assertFalse(Explore.restOwed(above, memory), "healed once, the flight is settled");
    }

    @Test
    @DisplayName("a mage's staff is found inside an enchanted or holy name")
    void wrapped_staff() {
        String[] rows = {"#####", "#@r.#", "#####"};
        for (String shown : List.of("blazing staff of magic missile", "holy staff of frost", "staff of lightning")) {
            assertEquals(Fight.MAGES_STAFF,
                    Fight.worn(screen(1, 20, false, shown, rows), KNOWLEDGE.weapons(), EquipSlot.WEAPON).name(), shown);
        }
    }

    @Test
    @DisplayName("an enemy's known figures stand and only the unknown ones are guessed: Goo keeps its hundred hit points")
    void partial_figures() {
        Codex.Knowledge goo = new Codex.Knowledge(KNOWLEDGE.manifest(), List.of(), List.of(), List.of(),
                List.of(new Codex.Threat("Goo", 100, 0, 8, 0, 0, 0, 2, List.of("attack", "damage", "defense"))),
                KNOWLEDGE.weapons(), KNOWLEDGE.armours(), List.of());
        Observation boss = screen(5, 20, true, "worn shortsword",
                "#######",
                "#.@G..#",
                "#######".replace('G', '.'));
        ActorView enemy = new ActorView(cell(boss, 3, 1), "Goo", Alignment.ENEMY, ObservationCodec.MAX_HEALTH_PIPS, false,
                Emote.NONE, List.of());
        Codex.Threat merged = Fight.threat(boss, goo, enemy);
        Codex.Threat guess = Fight.assumed("Goo", 5);
        assertEquals(100, merged.ht(), "the Codex's hit points, not the guess's " + guess.ht());
        assertEquals(guess.attack(), merged.attack());
        assertEquals(guess.damageMax(), merged.damageMax());
        assertEquals(Math.max(8, guess.defense()), merged.defense(), "an evasion by its own rule takes the higher");
        assertEquals(2, merged.drMax(), "the damage reduction the Codex read stands");
    }

    @Test
    @DisplayName("an enemy it can neither reach, flee nor wait out is held at most four waits, then left to the Policies below")
    void unreachable_enemy() {
        Observation across = screen(20, false,
                "#########",
                "##@ccc.r#",
                "#########");
        List<Observation> screens = new ArrayList<>();
        for (int i = 0; i < Fight.HOLDS + 2; i++) {
            screens.add(across);
        }
        List<Brain.Decided> all = each(screens.toArray(new Observation[0]));
        for (int i = 0; i < Fight.HOLDS; i++) {
            assertEquals("hold: no-way", why(all.get(i)), i + ": " + all.get(i).decision());
        }
        assertNotEquals(Fight.NAME, all.get(Fight.HOLDS).decision().policy(), all.get(Fight.HOLDS).decision().toString());
    }

    @Test
    @DisplayName("a statue that is hurt or alert is no longer scenery")
    void provoked_statue() {
        Observation hurt = screen(20, false,
                "#######",
                "#.@X..#",
                "#######");
        Observation alert = screen(20, false,
                "#######",
                "#.@A..#",
                "#######");
        assertEquals(1, Fight.enemies(hurt).size());
        assertEquals(1, Fight.enemies(alert).size());
        assertFalse(Explore.calm(hurt));
        assertEquals(Fight.NAME, after(hurt).decision().policy());
        assertEquals(Fight.NAME, after(alert).decision().policy());
    }

    // ------------------------------------------------------------------------------- story 4.12

    @Test
    @DisplayName("a retreat never flees down onto a boss floor, nor down while the hero is hurt and the descend Policy is taking it down")
    void never_down_hurt_or_onto_a_boss() {
        String[] rows = {
                "###########",
                "#....>@..B#",
                "#.........#",
                "#<........#",
                "###########"};
        Observation two = screen(2, 20, false, "worn shortsword", rows);
        int down = cell(two, 5, 1);
        Brain.Decided flees = after(two);
        assertEquals("retreat: stairs", why(flees));
        assertEquals(new Action.Step(down), flees.action(), "on floor 2 the stairs down are nearest");
        assertTrue(Fight.down(two, Memory.START, KNOWLEDGE));

        Observation four = screen(4, 20, false, "worn shortsword", rows);
        assertFalse(Fight.down(four, Memory.START, KNOWLEDGE), "floor 5 is Goo's");
        Brain.Decided up = after(four);
        assertEquals("retreat: stairs", why(up), up.decision().toString());
        assertNotEquals(new Action.Step(down), up.action(), "toward the stairs up instead");

        // Hurt, on a floor the descend Policy is leaving (every search spent): not down.
        Observation hurt = screen(2, 10, false, "worn shortsword", rows);
        List<Memory.Spot> spent = new ArrayList<>();
        for (int i = 0; i < Explore.SEARCHES; i++) {
            spent.add(new Memory.Spot(2, 0, 100 + i));
        }
        Memory leaving = new Memory(1, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, spent, List.of(), "", 0, -1, -1, List.of(), List.of());
        Observation calm = screen(2, 10, false, "worn shortsword",
                "###########",
                "#....>@...#",
                "#.........#",
                "#<........#",
                "###########");
        assertEquals("spent", Descend.leaving(calm, leaving, KNOWLEDGE));
        assertFalse(Fight.down(hurt, leaving, KNOWLEDGE));
        assertTrue(Fight.down(hurt, Memory.START, KNOWLEDGE), "hurt, but nothing is taking it down yet");
    }

    @Test
    @DisplayName("a Step the game refuses in a fight is not retried forever: after two, its cell is blocked and the next plan goes round it")
    void refused_step_in_a_fight() {
        Observation brute = screen(20, false,
                "##########",
                "#....@B..#",
                "#........#",
                "##########");
        List<Brain.Decided> all = each(brute, brute, brute);
        assertTrue(all.get(0).action() instanceof Action.Step, all.get(0).decision().toString());
        assertEquals(all.get(0).action(), all.get(1).action(), "the same Step, refused once");
        Action.Step refused = (Action.Step) all.get(0).action();
        Memory memory = Memory.of(Screens.drive(brain(), brute, brute, brute));
        assertTrue(memory.blocked().contains(new Memory.Spot(1, 0, refused.cell())), memory.blocked().toString());
        assertNotEquals(refused, all.get(2).action(), "the third wait goes round the refused cell");
    }
}
