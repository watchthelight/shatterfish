package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.LogTone;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack Policy (story 10.48, issue #458): a known liquid flame or frost is thrown, and a known attack wand zapped,
 * at an awake enemy the hero will not outfight, from a distance, and the fire is kept from coming back over grass.
 */
class PackPolicyTest {

    private static final String FLAME = "potion of liquid flame";
    private static final String FROST = "potion of frost";

    private static ItemView potion(String name) {
        return HealPolicyTest.potion(name, 1);
    }

    private static ItemView wand(String name, String status) {
        return new ItemView(ItemKind.WAND, name, 1, false, 0, false, false, status, EquipSlot.NONE,
                List.of("DROP", "THROW", "ZAP"), "ZAP");
    }

    /** A depth 7 screen with the hero at 20 of 20 and the brute ("B") a fight it would not take, the pack holding {@code items}. */
    private static Observation screen(String row, ItemView... items) {
        return EatPolicyTest.holding(FightPolicyTest.screen(7, 20, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length())), items);
    }

    /** {@code screen} with the tiles at the cells of the first row changed, and then the actions offered again. */
    private static Observation tiled(Observation screen, Map<Integer, Tile> changed) {
        MapSection was = screen.map();
        List<Tile> tiles = new ArrayList<>(was.tiles());
        changed.forEach(tiles::set);
        MapSection map = new MapSection(was.width(), was.height(), tiles, was.fog(), was.traps(), was.heaps(),
                was.blobs(), was.feeling(), was.transitions());
        return rebuilt(screen, map, screen.actors(), screen.log(), screen.header());
    }

    private static Observation rebuilt(Observation screen, MapSection map, ActorsSection actors, LogSection log, HeaderSection header) {
        Observation bare = new Observation(header, map, actors, screen.hero(), screen.inventory(), screen.journal(), log,
                ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Observation buffed(Observation screen, String buff) {
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : screen.actors().actors()) {
            actors.add(new ActorView(actor.cell(), actor.name(), actor.alignment(), actor.healthPips(), actor.invisible(),
                    actor.emote(), List.of(new BuffView(buff, false, 0))));
        }
        return rebuilt(screen, screen.map(), new ActorsSection(actors), screen.log(), screen.header());
    }

    private static Observation logged(Observation screen, String... lines) {
        List<LogLine> log = new ArrayList<>();
        for (String line : lines) {
            log.add(new LogLine(LogTone.WARNING, line));
        }
        return rebuilt(screen, screen.map(), screen.actors(), new LogSection(log), screen.header());
    }

    private static Observation sealed(Observation screen) {
        HeaderSection was = screen.header();
        HeaderSection header = new HeaderSection(was.version(), was.upstreamTag(), was.codexVersion(), was.heroClass(),
                was.challenges(), was.depth(), was.branch(), true, was.oracle(), was.prompt());
        return rebuilt(screen, screen.map(), screen.actors(), screen.log(), header);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static int brute(Observation screen) {
        return screen.actors().actors().get(0).cell();
    }

    private static void assertPack(Brain.Decided decided, String why) {
        assertEquals("pack", decided.decision().policy(), decided.decision().toString());
        assertEquals("pack: use", decided.decision().goal());
        assertEquals(why, decided.decision().chosen().why());
    }

    @Test
    @DisplayName("a brute five cells off and a known liquid flame: the hero throws it, where it used to wait or back away")
    void throws_liquid_flame_at_a_brute() {
        Observation screen = screen("#@.....B..#", potion(FLAME));
        Brain.Decided decided = decide(screen);
        assertPack(decided, "hurl: " + FLAME);
        Action.UseItemAt thrown = (Action.UseItemAt) decided.action();
        assertEquals("THROW", thrown.action());
        assertEquals(brute(screen), thrown.cell());
        assertEquals(FLAME, thrown.item().name());
    }

    @Test
    @DisplayName("the flame is thrown from three cells or more, not from two or beside the enemy")
    void only_from_a_distance() {
        assertEquals("pack", decide(screen("#@..B.....#", potion(FLAME))).decision().policy());
        assertNotEquals("pack", decide(screen("#@.B......#", potion(FLAME))).decision().policy());
        assertNotEquals("pack", decide(screen("#@B.......#", potion(FLAME))).decision().policy());
        assertEquals("pack", decide(screen("#@......B.#", potion(FLAME))).decision().policy());
        assertNotEquals("pack", decide(screen("#@.......B.#", potion(FLAME))).decision().policy());
    }

    @Test
    @DisplayName("an enemy the hero would take, one it kills in a few blows, a sleeper, a wall on the line, a sealed floor and a Goo are left alone")
    void only_a_strong_awake_enemy_the_hero_will_not_outfight() {
        assertNotEquals("pack", decide(screen("#@.....r..#", potion(FLAME))).decision().policy(), "a rat is a fight it takes");
        assertNotEquals("pack", decide(HealPolicyTest.asleep(screen("#@.....B..#", potion(FLAME)))).decision().policy());
        assertNotEquals("pack", decide(screen("#@..#..B..#", potion(FLAME))).decision().policy());
        assertNotEquals("pack", decide(sealed(screen("#@.....B..#", potion(FLAME)))).decision().policy());
        assertNotEquals("pack", decide(HealPolicyTest.named(screen("#@.....B..#", potion(FLAME)), "Goo")).decision().policy());
        Observation hurt = EatPolicyTest.holding(FightPolicyTest.screen(3, 5, false, "worn shortsword", Hunger.NONE,
                "##########", "#@.....r.#", "##########"), potion(FLAME));
        assertFalse(Fight.favourable(hurt, FightPolicyTest.KNOWLEDGE, Fight.enemies(hurt, FightPolicyTest.KNOWLEDGE)));
        assertTrue(Fight.turnsToKill(hurt, FightPolicyTest.KNOWLEDGE, Fight.enemies(hurt, FightPolicyTest.KNOWLEDGE).get(0)) < Pack.STRONG_TURNS);
        assertNotEquals("pack", decide(hurt).decision().policy(), "a hurt hero keeps the pack from a rat it kills in a few blows");
    }

    @Test
    @DisplayName("the hero standing on grass, or grass joined to the fire reaching the hero's side, keeps the flame in the pack")
    void fire_must_not_come_back_over_grass() {
        // Cells of the first row (width 11): the hero is at 12, the brute at 18.
        Observation bare = screen("#@.....B..#", potion(FLAME));
        int hero = bare.hero().cell();
        assertTrue(Pack.fireSafe(bare, brute(bare)));
        Observation underfoot = tiled(bare, Map.of(hero, Tile.GRASS));
        assertFalse(Pack.fireSafe(underfoot, brute(bare)), "the hero's own cell burns");
        assertNotEquals("pack", decide(underfoot).decision().policy());
        // A strip of grass from the brute's cell to the hero's neighbour is one fire.
        Observation strip = tiled(bare, Map.of(hero + 1, Tile.HIGH_GRASS, hero + 2, Tile.GRASS, hero + 3, Tile.GRASS,
                hero + 4, Tile.GRASS, hero + 5, Tile.GRASS));
        assertFalse(Pack.fireSafe(strip, brute(bare)));
        assertNotEquals("pack", decide(strip).decision().policy());
        // Grass that fire from the landing cells cannot reach, behind the hero, is not on the way.
        Observation behind = tiled(bare, Map.of(hero - 1, Tile.WALL, hero + 3, Tile.GRASS));
        assertTrue(Pack.fireSafe(behind, brute(bare)), "grass two cells off the hero and not touching the landing cells");
        // Grass under the enemy alone is fire that stays at the enemy.
        Observation under = tiled(bare, Map.of(brute(bare), Tile.GRASS));
        assertTrue(Pack.fireSafe(under, brute(bare)));
        assertEquals("pack", decide(under).decision().policy());
        // An enemy standing in water puts the burning out at once.
        assertFalse(Pack.fireSafe(tiled(bare, Map.of(brute(bare), Tile.WATER)), brute(bare)));
    }

    @Test
    @DisplayName("an enemy already burning is not set alight again, and a wand is zapped at it instead")
    void not_twice() {
        Observation burning = buffed(screen("#@.....B..#", potion(FLAME), wand("wand of magic missile", "")), "burning");
        Brain.Decided decided = decide(burning);
        assertPack(decided, "zap: wand of magic missile");
        assertNotEquals("pack", decide(buffed(screen("#@.....B..#", potion(FLAME)), "burning")).decision().policy());
    }

    @Test
    @DisplayName("frost is thrown when there is no flame to throw, not at an enemy that is chilled or burning")
    void throws_frost() {
        assertPack(decide(screen("#@.....B..#", potion(FROST))), "hurl: " + FROST);
        assertPack(decide(screen("#@.....B..#", potion(FROST), potion(FLAME))), "hurl: " + FLAME);
        assertNotEquals("pack", decide(buffed(screen("#@.....B..#", potion(FROST)), "chilled")).decision().policy());
        assertNotEquals("pack", decide(buffed(screen("#@.....B..#", potion(FROST)), "burning")).decision().policy());
    }

    @Test
    @DisplayName("an unknown potion is never thrown, and a known harmless one is kept")
    void only_known_harmful_potions() {
        assertNotEquals("pack", decide(screen("#@.....B..#", HealPolicyTest.potion("crimson potion", 1))).decision().policy());
        assertNotEquals("pack", decide(screen("#@.....B..#", potion("potion of healing"))).decision().policy());
    }

    @Test
    @DisplayName("a known attack wand is zapped while the enemy comes, best bolt first, from the distance its bolt carries")
    void zaps_a_wand() {
        Observation screen = screen("#@....B...#", wand("wand of magic missile", ""), wand("wand of lightning", "2/2"));
        Brain.Decided decided = decide(screen);
        assertPack(decided, "zap: wand of lightning");
        Action.UseItemAt zap = (Action.UseItemAt) decided.action();
        assertEquals("ZAP", zap.action());
        assertEquals(brute(screen), zap.cell());
        // Lightning stays three cells off the hero; the next bolt serves at two.
        assertPack(decide(screen("#@.B.......#", wand("wand of magic missile", ""), wand("wand of lightning", "2/2"))),
                "zap: wand of magic missile");
        // Disintegration carries six cells and no more.
        assertPack(decide(screen("#@.....B...#", wand("wand of disintegration", ""))), "zap: wand of disintegration");
        assertNotEquals("pack", decide(screen("#@......B...#", wand("wand of disintegration", ""))).decision().policy());
    }

    @Test
    @DisplayName("a wand shown cursed or empty, one that fizzled in the log, a wand of another kind and the Mage's staff are left alone")
    void wands_left_alone() {
        ItemView cursed = new ItemView(ItemKind.WAND, "wand of magic missile", 1, true, 0, true, true, "2/2", EquipSlot.NONE,
                List.of("DROP", "THROW", "ZAP"), "ZAP");
        assertNotEquals("pack", decide(screen("#@....B...#", cursed)).decision().policy());
        assertNotEquals("pack", decide(screen("#@....B...#", wand("wand of magic missile", "0/3"))).decision().policy());
        assertNotEquals("pack", decide(screen("#@....B...#", wand("wand of regrowth", ""))).decision().policy());
        assertNotEquals("pack", decide(screen("#@....B...#", wand("wand of corruption", ""))).decision().policy());
        assertNotEquals("pack", decide(logged(screen("#@....B...#", wand("wand of magic missile", "")),
                "Your wand fizzles; it must not have enough charge.")).decision().policy());
        ItemView staff = new ItemView(ItemKind.WEAPON, "staff of magic missile", 1, true, 0, true, false, "2/2", EquipSlot.NONE,
                List.of("IMBUE", "ZAP"), "ZAP");
        assertNotEquals("pack", decide(screen("#@....B...#", staff)).decision().policy());
    }

    @Test
    @DisplayName("an enemy that is frozen is not zapped, since damage thaws it")
    void not_a_frozen_enemy() {
        assertNotEquals("pack", decide(buffed(screen("#@....B...#", wand("wand of magic missile", "")), "frozen")).decision().policy());
    }

    @Test
    @DisplayName("the walk goes round a frost cloud the pack threw")
    void walks_round_frost() {
        Observation bare = screen("#@....B...#", potion(FROST));
        MapSection was = bare.map();
        MapSection map = new MapSection(was.width(), was.height(), was.tiles(), was.fog(), was.traps(), was.heaps(),
                List.of(new BlobCell(bare.hero().cell() + 2, List.of("Freezing"))), was.feeling(), was.transitions());
        Observation clouded = rebuilt(bare, map, bare.actors(), bare.log(), bare.header());
        assertTrue(Explore.walkable(bare, Memory.START)[bare.hero().cell() + 2]);
        assertFalse(Explore.walkable(clouded, Memory.START)[bare.hero().cell() + 2]);
    }
}
