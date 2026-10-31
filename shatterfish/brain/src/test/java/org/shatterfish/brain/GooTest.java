package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Tile;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.LogTone;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Goo's pump-up (story 4.13): announced in the log, dodged by stepping out of its two-cell reach, which
 * makes Goo step and drop the pump (Goo.java:141-152, :244-258).
 */
class GooTest {

    /** {@code rows} with the rat drawn as Goo, and the log ending in {@code lines}. */
    static Observation screen(List<String> lines, String... rows) {
        Observation base = FightPolicyTest.screen(4, 20, true, "worn shortsword", rows);
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : base.actors().actors()) {
            actors.add(new ActorView(actor.cell(), Goo.NAME, actor.alignment(), actor.healthPips(), actor.invisible(),
                    actor.emote(), actor.buffs()));
        }
        List<LogLine> log = lines.stream().map(text -> new LogLine(LogTone.NEGATIVE, text)).toList();
        Observation bare = new Observation(base.header(), base.map(), new ActorsSection(actors), base.hero(),
                base.inventory(), base.journal(), new LogSection(log), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static final String[] NEAR = {
            "##########",
            "#...@.r..#",
            "#........#",
            "##########"};

    @Test
    @DisplayName("an announced pump-up with Goo in reach: the fold remembers Goo's cell, and the fight Policy steps out of reach")
    void dodge() {
        Observation pumped = screen(List.of("You see Goo.", Goo.PUMP), NEAR);
        Memory memory = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        assertEquals(Goo.cell(pumped), memory.pump(), "the pump is remembered at Goo's cell");
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
        Brain.Decided decided = brain.decide(pumped, memory.belief());
        assertEquals("dodge: pump", decided.decision().chosen().why(), decided.decision().toString());
        Action.Step step = (Action.Step) decided.action();
        int width = pumped.map().width();
        int goo = Goo.cell(pumped);
        assertTrue(Math.max(Math.abs(step.cell() % width - goo % width), Math.abs(step.cell() / width - goo / width))
                > Goo.REACH, "out of the pump's reach");
    }

    @Test
    @DisplayName("beside Goo at the announcement: a Step away, though one is not out of reach; the next wait's finishes it")
    void dodge_from_beside() {
        Observation pumped = screen(List.of("You see Goo.", Goo.PUMP),
                "##########",
                "#...@r...#",
                "#........#",
                "##########");
        Memory memory = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(pumped, memory.belief());
        assertEquals("dodge: pump", decided.decision().chosen().why(), decided.decision().toString());
        Action.Step step = (Action.Step) decided.action();
        int width = pumped.map().width();
        int goo = Goo.cell(pumped);
        assertEquals(2, Math.max(Math.abs(step.cell() % width - goo % width), Math.abs(step.cell() / width - goo / width)),
                "one Step from beside Goo is two away");
    }

    @Test
    @DisplayName("a full log: the fifth announcement in a row, the oldest line dropping, is still new")
    void full_log() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < org.shatterfish.api.LogSection.MAX_LINES - 4; i++) {
            lines.add("line " + i);
        }
        for (int i = 0; i < 4; i++) {
            lines.add(Goo.PUMP);
        }
        Observation first = screen(lines, NEAR);
        Memory seen = Beliefs.fold(Memory.START, first, Screens.CODEX);
        assertEquals(Goo.cell(first), seen.pump());
        List<String> next = new ArrayList<>(lines.subList(1, lines.size()));
        next.add(Goo.PUMP);
        Observation moved = screen(next,
                "##########",
                "#...@..r.#",
                "#........#",
                "##########");
        assertEquals(Goo.cell(moved), Beliefs.fold(seen, moved, Screens.CODEX).pump(),
                "the same size and the same last lines, but the window moved: a new announcement at Goo's new cell");
    }

    @Test
    @DisplayName("an announcement followed by another line the same turn is still read; one while Goo is not drawn is not")
    void announcement_not_last() {
        Observation followed = screen(List.of("You see Goo.", Goo.PUMP, "Goo: GLURP-GLURP!"), NEAR);
        assertEquals(Goo.cell(followed), Beliefs.fold(Memory.START, followed, Screens.CODEX).pump());
        Observation unseen = screen(List.of("You see Goo.", Goo.PUMP),
                "##########",
                "#...@....#",
                "#........#",
                "##########");
        assertEquals(-1, Goo.cell(unseen), "no Goo drawn");
        assertEquals(-1, Beliefs.fold(Memory.START, unseen, Screens.CODEX).pump(), "no pump without Goo in view");
    }

    @Test
    @DisplayName("an announcement is answered once: the same log tail again is no new pump, and Goo moving drops it")
    void once() {
        Observation pumped = screen(List.of("You see Goo.", Goo.PUMP), NEAR);
        Memory seen = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        Observation moved = screen(List.of("You see Goo.", Goo.PUMP),
                "##########",
                "#.@..r...#",
                "#........#",
                "##########");
        Memory after = Beliefs.fold(seen, moved, Screens.CODEX);
        assertEquals(-1, after.pump(), "Goo stepped: the pump is dropped, and the same announcement is not new");
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(moved, after.belief());
        assertNotEquals("dodge: pump", decided.decision().chosen().why());
        Observation calm = screen(List.of("You see Goo."), NEAR);
        assertEquals(-1, Beliefs.fold(Memory.START, calm, Screens.CODEX).pump(), "no announcement, no pump");
        assertEquals(seen, Memory.of(seen.belief()), "the pump survives the Belief's bytes");
    }

    @Test
    @DisplayName("a pump lapses after PUMP_WAITS waits with Goo still")
    void lapses() {
        Observation pumped = screen(List.of("You see Goo.", Goo.PUMP), NEAR);
        Memory memory = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        for (int i = 0; i < Goo.PUMP_WAITS; i++) {
            memory = Beliefs.fold(memory, pumped, Screens.CODEX);
            assertEquals(Goo.cell(pumped), memory.pump(), "wait " + i);
        }
        memory = Beliefs.fold(memory, pumped, Screens.CODEX);
        assertEquals(-1, memory.pump(), "lapsed");
        Belief belief = memory.belief();
        assertEquals(memory, Memory.of(belief));
    }

    @Test
    @DisplayName("an awake Goo far off on a sealed floor: the hero holds its ground and lets it come, where it used to retreat (issue #219)")
    void holds_for_goo() {
        Observation far = screen(List.of("You see Goo."),
                "##########",
                "#...@..r.#",
                "#........#",
                "##########");
        Observation sealed = far;
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(sealed, Beliefs.fold(Memory.START, sealed, Screens.CODEX).belief());
        assertEquals("goo: wait", decided.decision().chosen().why(), decided.decision().toString());
    }

    static final String[] BY_THE_WALL = {
            "##############",
            "#............#",
            "#.....@......#",
            "#............#",
            "#............#",
            "#............#",
            "#.......r....#",
            "##############"};

    @Test
    @DisplayName("Goo coming and the hero with its back to a wall, so that a pump could not be dodged where Goo will stand: the hero walks to ground where it could (issue #219)")
    void positions_before_contact() {
        Observation pinned = screen(List.of("You see Goo."), BY_THE_WALL);
        Memory memory = Beliefs.fold(Memory.START, pinned, Screens.CODEX);
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(pinned, memory.belief());
        assertEquals("goo: position", decided.decision().chosen().why(), decided.decision().toString());
        int width = pinned.map().width();
        int hero = pinned.hero().cell();
        int to = ((Action.Step) decided.action()).cell();
        assertTrue(to / width > hero / width, "a Step away from the wall");
        int goo = Goo.cell(pinned);
        assertTrue(Math.max(Math.abs(to % width - goo % width), Math.abs(to / width - goo / width)) >= 2,
                "and not into Goo's reach");
    }

    /**
     * {@code rows} as {@link #screen} with the cells drawn {@code ~} water, and the hero soaked in caustic
     * ooze when {@code oozed} (its turns are not shown), standing in water when {@code inWater}.
     */
    static Observation oozed(boolean oozed, boolean inWater, String... rows) {
        String[] plain = java.util.Arrays.stream(rows).map(row -> row.replace('~', '.')).toArray(String[]::new);
        Observation base = screen(List.of("You see Goo."), plain);
        MapSection map = base.map();
        List<Tile> tiles = new ArrayList<>(map.tiles());
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '~') {
                    tiles.set(x + y * map.width(), Tile.WATER);
                }
            }
        }
        if (inWater) {
            tiles.set(base.hero().cell(), Tile.WATER);
        }
        MapSection wet = new MapSection(map.width(), map.height(), tiles, map.fog(), map.traps(), map.heaps(), map.blobs(),
                map.feeling(), map.transitions());
        HeroSection h = base.hero();
        List<BuffView> buffs = oozed ? List.of(new BuffView(Goo.OOZE, false, 0)) : List.of();
        HeroSection hero = new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(), h.level(), h.exp(), h.expToLevel(),
                h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(), h.gold(), h.energy(), h.hunger(), buffs,
                h.talents(), h.talentPointsAvailable(), h.quickslots());
        Observation bare = new Observation(base.header(), wet, base.actors(), hero, base.inventory(), base.journal(),
                base.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    static final String[] WATER_WEST = {
            "##############",
            "#............#",
            "#............#",
            "#............#",
            "#..~~@.....r.#",
            "#............#",
            "#............#",
            "#............#",
            "##############"};

    static final String[] WATER_FAR = {
            "##############",
            "#............#",
            "#............#",
            "#............#",
            "#~...........#",
            "#.....@....r.#",
            "#............#",
            "#............#",
            "##############"};

    @Test
    @DisplayName("caustic ooze on the hero and water in reach: the hero walks to the water, which washes it away (issue #219)")
    void washes_the_ooze_off() {
        Observation soaked = oozed(true, false, WATER_WEST);
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(soaked, Beliefs.fold(Memory.START, soaked, Screens.CODEX).belief());
        assertEquals("goo: wash", decided.decision().chosen().why(), decided.decision().toString());
        assertTrue(((Action.Step) decided.action()).cell() < soaked.hero().cell(), "a Step west, toward the water");
    }

    @Test
    @DisplayName("ooze but the water is too far to walk to, or the hero already stands in water, or there is no ooze: no walk to the water")
    void does_not_wash_for_nothing() {
        for (Observation screen : List.of(oozed(true, false, WATER_FAR), oozed(true, true, WATER_WEST), oozed(false, false, WATER_WEST))) {
            Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(screen, Beliefs.fold(Memory.START, screen, Screens.CODEX).belief());
            assertEquals("goo: wait", decided.decision().chosen().why(), decided.decision().toString());
        }
    }

    @Test
    @DisplayName("Goo coming and ground to dodge on where the hero stands: the hero holds, as before (issue #219)")
    void holds_in_the_open() {
        Observation open = screen(List.of("You see Goo."),
                "##############",
                "#............#",
                "#............#",
                "#............#",
                "#.....@......#",
                "#............#",
                "#............#",
                "#.......r....#",
                "##############");
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(open, Beliefs.fold(Memory.START, open, Screens.CODEX).belief());
        assertEquals("goo: wait", decided.decision().chosen().why(), decided.decision().toString());
    }

    @Test
    @DisplayName("Goo beside the hero in a corner with no cell out of its reach to walk to: the hero strikes (issue #219)")
    void strikes_when_cornered() {
        Observation cornered = screen(List.of("You see Goo."),
                "######",
                "#@r..#",
                "#....#",
                "######");
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(cornered, Beliefs.fold(Memory.START, cornered, Screens.CODEX).belief());
        assertEquals("goo: attack", decided.decision().chosen().why(), decided.decision().toString());
    }

    @Test
    @DisplayName("a dodge takes the Step with a way on: of the two cells two from Goo, the one that is a pocket is not taken (issue #219)")
    void dodge_leaves_room() {
        Observation pumped = screen(List.of("You see Goo.", Goo.PUMP),
                "########",
                "#..r...#",
                "#..@...#",
                "#......#",
                "####..##",
                "########");
        Memory memory = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(pumped, memory.belief());
        assertEquals("dodge: pump", decided.decision().chosen().why(), decided.decision().toString());
        int width = pumped.map().width();
        int pocket = 2 + 3 * width;
        assertNotEquals(pocket, ((Action.Step) decided.action()).cell(), "the pocket under the hero's left has no cell three from Goo");
    }

    private static final String[] OPEN_ROOM = {
            "##############",
            "#............#",
            "#............#",
            "#............#",
            "#.....@......#",
            "#............#",
            "#............#",
            "#.......r....#",
            "##############"};

    private static org.shatterfish.api.ItemView stones() {
        return new org.shatterfish.api.ItemView(org.shatterfish.api.ItemKind.MISSILE, "throwing stone", 3, false, 0, false, false, "",
                org.shatterfish.api.EquipSlot.NONE, List.of("DROP", "THROW"), "THROW");
    }

    private static org.shatterfish.api.ItemView bow() {
        return new org.shatterfish.api.ItemView(org.shatterfish.api.ItemKind.WEAPON, "spirit bow", 1, true, 0, true, false, "",
                org.shatterfish.api.EquipSlot.NONE, List.of("DROP", "THROW", "SHOOT"), "SHOOT");
    }

    private static org.shatterfish.api.ItemView staff(String status) {
        return new org.shatterfish.api.ItemView(org.shatterfish.api.ItemKind.WEAPON, "staff of magic missile", 1, true, 0, true,
                false, status, org.shatterfish.api.EquipSlot.WEAPON, List.of("IMBUE", "ZAP"), "ZAP");
    }

    /** {@code screen} with exactly {@code items} in the pack (the backpack first, the worn ones after), its Actions worked out again. */
    private static Observation kitted(Observation screen, org.shatterfish.api.ItemView... items) {
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), screen.hero(),
                new org.shatterfish.api.InventorySection(List.of(items)), screen.journal(), screen.log(), ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static String chosen(Observation screen) {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(screen, Beliefs.fold(Memory.START, screen, Screens.CODEX).belief())
                .decision().chosen().why();
    }

    @Test
    @DisplayName("Goo coming across open ground and a stack of missiles in the pack: the hero throws for each cell Goo spends walking, where it used to wait (story 10.38)")
    void volley_throws() {
        Observation far = kitted(screen(List.of("You see Goo."), OPEN_ROOM), stones());
        assertEquals("goo: volley throw: throwing stone", chosen(far));
    }

    @Test
    @DisplayName("the staff first, then the bow, then the missiles; the bolt, the arrow and the throw are the same Choices the Policies of their names make (story 10.38)")
    void volley_order() {
        Observation all = kitted(screen(List.of("You see Goo."), OPEN_ROOM), staff("3/3"), stones(), bow());
        assertEquals("goo: volley zap: staff of magic missile", chosen(all));
        Observation spent = kitted(screen(List.of("You see Goo."), OPEN_ROOM), staff("0/3"), stones(), bow());
        assertEquals("goo: volley shoot: spirit bow", chosen(spent));
        Observation arrow = kitted(screen(List.of("You see Goo."), OPEN_ROOM), bow());
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(arrow, Beliefs.fold(Memory.START, arrow, Screens.CODEX).belief());
        Action.UseItemAt shot = (Action.UseItemAt) decided.action();
        assertEquals("SHOOT", shot.action());
        assertEquals(Goo.cell(arrow), shot.cell());
    }

    @Test
    @DisplayName("beside Goo the blow is the melee one, with nothing to throw the hero waits as before, and a wall on the line holds the volley (story 10.38)")
    void volley_holds_its_fire() {
        Observation beside = kitted(screen(List.of("You see Goo."),
                "##########",
                "#...@r...#",
                "#........#",
                "##########"), staff("3/3"), stones(), bow());
        assertEquals("goo: attack", chosen(beside));
        assertEquals("goo: wait", chosen(screen(List.of("You see Goo."), OPEN_ROOM)));
        Observation blocked = kitted(screen(List.of("You see Goo."),
                "##############",
                "#............#",
                "#............#",
                "#............#",
                "#.....@......#",
                "#......#.....#",
                "#......#.....#",
                "#.......r....#",
                "##############"), stones());
        assertNotEquals("goo: volley throw: throwing stone", chosen(blocked));
    }

    @Test
    @DisplayName("an announced pump in reach is still dodged before anything is thrown (story 10.38)")
    void volley_yields_to_the_dodge() {
        Observation pumped = kitted(screen(List.of("You see Goo.", Goo.PUMP), NEAR), stones());
        Memory memory = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        assertEquals("dodge: pump", new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(pumped, memory.belief()).decision().chosen().why());
    }

    /** {@code screen} with Goo's cell water, and Goo at {@code pips} health pips. */
    private static Observation gooInWater(Observation screen, int pips) {
        MapSection map = screen.map();
        List<Tile> tiles = new ArrayList<>(map.tiles());
        tiles.set(Goo.cell(screen), Tile.WATER);
        MapSection wet = new MapSection(map.width(), map.height(), tiles, map.fog(), map.traps(), map.heaps(), map.blobs(),
                map.feeling(), map.transitions());
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : screen.actors().actors()) {
            actors.add(new ActorView(actor.cell(), actor.name(), actor.alignment(), pips, actor.invisible(), actor.emote(),
                    actor.buffs()));
        }
        Observation bare = new Observation(screen.header(), wet, new ActorsSection(actors), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static final String[] WET_EAST = {
            "##############",
            "#............#",
            "#............#",
            "#......~~~...#",
            "#.....@~~~..r#",
            "#......~~~...#",
            "#............#",
            "#............#",
            "##############"};

    @Test
    @DisplayName("Goo coming across water to the hero's side: the hero walks to ground where Goo will stand on dry land, and not toward the water (issue #429)")
    void keeps_dry_ground_before_contact() {
        Observation wet = oozed(false, false, WET_EAST);
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(wet, Beliefs.fold(Memory.START, wet, Screens.CODEX).belief());
        assertEquals("goo: dry", decided.decision().chosen().why(), decided.decision().toString());
        int width = wet.map().width();
        assertTrue(((Action.Step) decided.action()).cell() % width < wet.hero().cell() % width, "a Step west, away from the water");
    }

    @Test
    @DisplayName("a hurt Goo beside the hero standing in water: the hero steps back to dry ground so Goo follows off the water; a Goo at full health is struck (issue #429)")
    void lures_a_hurt_goo_off_the_water() {
        String[] rows = {
                "##############",
                "#............#",
                "#............#",
                "#............#",
                "#.....@r.....#",
                "#............#",
                "#............#",
                "##############"};
        Observation hurt = gooInWater(screen(List.of("You see Goo."), rows), 4);
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(hurt, Beliefs.fold(Memory.START, hurt, Screens.CODEX).belief());
        assertEquals("goo: dry", decided.decision().chosen().why(), decided.decision().toString());
        int width = hurt.map().width();
        assertTrue(((Action.Step) decided.action()).cell() % width < hurt.hero().cell() % width, "a Step away from Goo");
        Observation fresh = gooInWater(screen(List.of("You see Goo."), rows), org.shatterfish.api.ObservationCodec.MAX_HEALTH_PIPS);
        assertEquals("goo: attack", chosen(fresh));
    }

    @Test
    @DisplayName("a pump pending is dodged, never walked away from for dry ground (issue #429)")
    void dry_yields_to_the_dodge() {
        Observation pumped = gooInWater(screen(List.of("You see Goo.", Goo.PUMP), NEAR), 4);
        Memory memory = Beliefs.fold(Memory.START, pumped, Screens.CODEX);
        assertEquals("dodge: pump", new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(pumped, memory.belief()).decision().chosen().why());
    }
}
