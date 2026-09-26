package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Belief;
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
}
