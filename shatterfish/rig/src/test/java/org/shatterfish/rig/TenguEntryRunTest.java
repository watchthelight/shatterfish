package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.RunOutcome;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 10.18 (issue #364), in the game: a practice Tengu Run (ADR-0024) of the shipped Brain gets to the
 * seal. The Run begins on the prison boss floor with the hero on the entrance stairs, which lead up; the
 * Brain must fetch the key from a side cell, open the cell door at the end of the hallway and step in, which
 * seals the floor (PrisonBossLevel.java:580-583).
 *
 * <p>This fails on the Brain before the story: it took the stairs up (LEFT_BOSS_FLOOR) or, with nothing to
 * explore, rested and wandered until it starved (docs/results/e10-practice-kit.md). The seeds are the first
 * of the {@code standard} set for each class under the salts 1000 and up, never {@code goo}, {@code holdout}
 * or {@code tengu}.
 */
class TenguEntryRunTest {

    /** The waits within which the seal must be reached: the key, the hallway and a rest are some hundreds of waits at most. */
    private static final int WAITS_TO_THE_SEAL = 600;

    /** The Brain, watched: whether the floor sealed, at which wait, and whether it ever chose the stairs up. */
    private static final class Watched implements Deliberator {

        private final Deliberator brain;
        int waits;
        int sealedAt = -1;
        boolean keyHeld;
        boolean ascended;

        Watched(Deliberator brain) {
            this.brain = brain;
        }

        @Override
        public Action decide(Observation observation) {
            waits++;
            if (observation.header().sealed() && sealedAt < 0) {
                sealedAt = waits;
            }
            keyHeld |= observation.journal().notes().stream().anyMatch(note -> note.kind() == org.shatterfish.api.NoteKind.KEY);
            Action chosen = brain.decide(observation);
            ascended |= chosen instanceof Action.Ascend;
            return chosen;
        }

        @Override
        public RunLog.Decision lastDecision() {
            return brain.lastDecision();
        }

        @Override
        public Belief belief() {
            return brain.belief();
        }

        @Override
        public List<Integer> lastHighlights() {
            return brain.lastHighlights();
        }
    }

    private static Map<String, String> practice(HeroClass heroClass, Path out) {
        Path root = SeedSetsTest.ROOT;
        DirectionSelect.Pick pick = DirectionSelect.select(root, SeedSets.STANDARD, heroClass, 1, 1000L).get(0);
        Map<String, String> a = new LinkedHashMap<>();
        a.put(RunOne.SEED, Long.toString(pick.entry().seed()));
        a.put(RunOne.CLASS, heroClass.name());
        a.put(RunOne.SALT, Long.toString(pick.salt()));
        a.put(RunOne.OUT, out.toAbsolutePath().toString());
        a.put(RunOne.COMMIT, "test");
        a.put(RunOne.BRAIN, Brains.SHATTERFISH);
        a.put(RunOne.BRAIN_COMMIT, "test");
        a.put(RunOne.CHALLENGES, Integer.toString(pick.entry().challengeFlags()));
        a.put(RunOne.CODEX, root.resolve("codex/v4.0.2").toAbsolutePath().toString());
        a.put(RunOne.WEIGHTS, root.resolve("weights/shatterfish.json").toAbsolutePath().toString());
        a.put(RunOne.PLAYBOOK, root.resolve("playbooks/shatterfish.json").toAbsolutePath().toString());
        a.put(RunOne.PRACTICE, "tengu");
        a.put(RunOne.KIT, "standard");
        a.put(RunOne.CAP, "3000");
        return a;
    }

    @Test
    @DisplayName("a practice Tengu Run of every class gets the key, opens the cell door and reaches the seal, and never takes the stairs up")
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void every_class_reaches_the_seal(@TempDir Path out) {
        for (HeroClass heroClass : HeroClass.values()) {
            java.util.concurrent.atomic.AtomicReference<Watched> watching = new java.util.concurrent.atomic.AtomicReference<>();
            RunOutcome outcome = RunOne.play(practice(heroClass, out.resolve(heroClass.name())), decider -> {
                Watched watched = new Watched((Deliberator) decider);
                watching.set(watched);
                return watched;
            });
            Watched watched = watching.get();
            String who = heroClass + ": " + outcome;
            assertNotEquals(RunOutcome.Cause.LEFT_BOSS_FLOOR, outcome.cause(), who);
            assertFalse(watched.ascended, who + ": the stairs up were chosen");
            assertTrue(watched.keyHeld, who + ": the key was never held");
            assertTrue(watched.sealedAt > 0, who + ": the floor never sealed in " + watched.waits + " waits");
            assertTrue(watched.sealedAt <= WAITS_TO_THE_SEAL, who + ": sealed at wait " + watched.sealedAt);
        }
    }
}
