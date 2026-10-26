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
    private static Observation screen(List<String> lines, String... rows) {
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

    private static final String[] BY_THE_WALL = {
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
    private static Observation oozed(boolean oozed, boolean inWater, String... rows) {
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

    private static final String[] WATER_WEST = {
            "##############",
            "#............#",
            "#............#",
            "#............#",
            "#..~~@.....r.#",
            "#............#",
            "#............#",
            "#............#",
            "##############"};

    private static final String[] WATER_FAR = {
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
}
