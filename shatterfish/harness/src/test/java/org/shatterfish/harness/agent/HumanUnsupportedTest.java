package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the executor cannot express is marked, never passed off as a Run that replays (story 5.9): a
 * click on a distant cell, which the game walks over several turns and the executor has no Action for,
 * and a zero-time change nobody's hook hears, which leaves a screen the next wait's Replay would not
 * reach. Each writes an {@code unsupported} record at its wait, the Panel's snapshot says so, the end
 * record says the Run is not verifiable, and a Replay stops there.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HumanUnsupportedTest {

    /** A cell the hero has seen, can stand on, and is not next to. */
    private static int distantCell() {
        int hero = Dungeon.hero.pos;
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (Dungeon.level.passable[cell] && Dungeon.level.visited[cell] && Dungeon.level.distance(hero, cell) >= 3
                    && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell) == null
                    && Dungeon.level.heaps.get(cell) == null && Dungeon.level.getTransition(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no distant open cell in view");
    }

    @Test
    @DisplayName("a click on a distant cell is recorded as MoveTo, marked unsupported, and a Replay stops there")
    void a_distant_click(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        int cell;
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(played), 12);
            assertTrue(host.untilOpen(20_000));
            ScriptedHuman.pressWait();
            assertTrue(host.untilOpen(20_000));
            cell = distantCell();
            ScriptedHuman.tapCell(cell);
            assertTrue(host.untilOpen(200_000), "the walk ended at a wait");
            EmbeddedRun.Human human = run.snapshot().human();
            assertEquals(2, human.unverifiableFrom(), "the Panel is told from which wait");
            assertTrue(human.unverifiableWhy().contains("cell " + cell), human.unverifiableWhy());
            // Waits until the cap, so the Run writes its ending.
            while (host.untilOpen(200_000)) {
                ScriptedHuman.pressWait();
            }
            assertEquals(RunOutcome.Cause.TURN_CAP, run.outcome().cause());
        }
        Path log = HumanTurnReplayTest.only(played);
        RunLogReader.Log read = RunLogReader.of(log);
        List<RunLog.Wait> waits = read.waits();
        assertEquals(new Action.MoveTo(cell), waits.get(1).action(), "the click, as the person made it");
        assertEquals(List.of(2L), read.records().stream().filter(RunLog.Unsupported.class::isInstance)
                .map(r -> ((RunLog.Unsupported) r).k()).toList(), "one mark, at the click's wait");
        assertFalse(read.end().verifiable(), "the ending says the Run cannot be reproduced");

        Replay.Waits replayed = Replay.waitsOf(log, Files.createDirectories(folder.resolve("replay")), "test");
        assertEquals(2, replayed.unverifiableFrom());
        assertEquals(1, replayed.verified(), "the wait before it reproduced");
    }

    @Test
    @DisplayName("a zero-time change no hook hears is marked unsupported at its wait")
    void a_change_nobody_heard(@TempDir Path folder) throws IOException {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 2_000);
            assertTrue(host.untilOpen(20_000));
            // The quickslot selector's own call: no Action names it, and the Observation shows it.
            Dungeon.quickslot.setSlot(3, Dungeon.hero.belongings.getItem(Food.class));
            run.inputEvent();
            host.frame();
            assertEquals(1, run.snapshot().human().unverifiableFrom());
            ScriptedHuman.pressWait();
            host.untilOpen(20_000);
        }
        RunLogReader.Log read = RunLogReader.of(HumanTurnReplayTest.only(folder));
        assertEquals(List.of(1L), read.records().stream().filter(RunLog.Unsupported.class::isInstance)
                .map(r -> ((RunLog.Unsupported) r).k()).toList());
        assertEquals(new Action.Wait(), read.waits().get(0).action(), "what the person then did is still recorded");
    }

    @Test
    @DisplayName("a wait that ends with nothing heard is marked, and an input heard between waits marks the next")
    void nothing_heard(@TempDir Path folder) throws IOException {
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()), HumanTurnReplayTest.logging(folder), 2_000);
            assertTrue(host.untilOpen(20_000));
            // The wait button's call with its hook unheard: the recorder is not told, as if a hook site were lost.
            com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.HeroInput heard =
                    com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.heroInput;
            com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.heroInput = null;
            ScriptedHuman.pressWait();
            com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.heroInput = heard;
            assertTrue(host.untilOpen(20_000));
        }
        RunLogReader.Log read = RunLogReader.of(HumanTurnReplayTest.only(folder));
        assertTrue(read.waits().isEmpty(), "no Action is invented for it");
        assertEquals(List.of(1L), read.records().stream().filter(RunLog.Unsupported.class::isInstance)
                .map(r -> ((RunLog.Unsupported) r).k()).toList());
    }
}
