package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Tunables;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the Brain's numbers through the registry changed no play (story 10.35, the E7 lesson).
 *
 * <p>The {@code smoke} set, every class, is played three ways: by the committed Playbook, by the same
 * Playbook stating every Policy default in its {@code tunables} section, and by the same Playbook with
 * those numbers moved to the far ends of their ranges. The first two must play equal Run logs wait for
 * wait (the decider's wall-clock time apart), and carry one configuration hash, since a stated default
 * is no vector at all. The third is the control: it must play other Runs under another hash, or the
 * equality above holds for a Brain that ignores its vector.
 *
 * <p>The comparison against the code before the story needs two checkouts, as
 * {@link PlaybookEquivalenceTest} says of its own: it is made by hand, over {@code smoke} and a 40-Run
 * Warrior slice of {@code standard}, and the pull request and the story file record the figures.
 */
class TunableNoChangeTest {

    private static String committed() throws IOException {
        return Files.readString(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), StandardCharsets.UTF_8);
    }

    private static String withTunables(String playbook, Map<String, Long> stated) {
        String section = stated.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "\"tunables\":{", "},"));
        String anchor = "\"name\":\"shatterfish\",\"upgrade\"";
        assertTrue(playbook.contains(anchor), "the committed Playbook states name and upgrade side by side");
        return playbook.replace(anchor, "\"name\":\"shatterfish\"," + section + "\"upgrade\"");
    }

    @Test
    @DisplayName("a Playbook that states every Policy default plays the smoke set as the committed one does, and one that moves them does not")
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void the_registry_changes_no_play_at_the_defaults(@TempDir Path scratch) throws IOException {
        String committed = committed();
        Map<String, Long> defaults = new java.util.TreeMap<>();
        Map<String, Long> moved = new java.util.TreeMap<>();
        for (Tunables.Tunable tunable : Tunables.ALL) {
            if (tunable.owner() == Tunables.Owner.POLICY) {
                defaults.put(tunable.name(), tunable.def());
                moved.put(tunable.name(), tunable.def() - tunable.min() >= tunable.max() - tunable.def() ? tunable.min() : tunable.max());
            }
        }

        Map<String, Path> plain = PlaybookEquivalenceTest.play(PlaybookEquivalenceTest.root(scratch.resolve("plain"), committed),
                scratch.resolve("plain-out"), java.util.EnumSet.allOf(HeroClass.class));
        Map<String, Path> stated = PlaybookEquivalenceTest.play(
                PlaybookEquivalenceTest.root(scratch.resolve("stated"), withTunables(committed, defaults)),
                scratch.resolve("stated-out"), java.util.EnumSet.allOf(HeroClass.class));
        assertEquals(plain.keySet(), stated.keySet());
        assertTrue(plain.size() >= 6, "the smoke set holds every class: " + plain.size());
        for (Map.Entry<String, Path> log : plain.entrySet()) {
            assertEquals(PlaybookEquivalenceTest.waits(log.getValue()), PlaybookEquivalenceTest.waits(stated.get(log.getKey())),
                    log.getKey() + ": the same waits at the defaults");
            assertEquals(RunLogReader.of(log.getValue()).header().brain().configHash(),
                    RunLogReader.of(stated.get(log.getKey())).header().brain().configHash(),
                    log.getKey() + ": a stated default is the same configuration");
        }

        Map<String, Path> control = PlaybookEquivalenceTest.play(
                PlaybookEquivalenceTest.root(scratch.resolve("moved"), withTunables(committed, moved)),
                scratch.resolve("moved-out"), java.util.EnumSet.allOf(HeroClass.class));
        assertEquals(plain.keySet(), control.keySet());
        int differing = 0;
        for (Map.Entry<String, Path> log : plain.entrySet()) {
            if (!PlaybookEquivalenceTest.waits(log.getValue()).equals(PlaybookEquivalenceTest.waits(control.get(log.getKey())))) {
                differing++;
            }
            assertNotEquals(RunLogReader.of(log.getValue()).header().brain().configHash(),
                    RunLogReader.of(control.get(log.getKey())).header().brain().configHash(),
                    "the header carries the vector it was played with");
        }
        assertTrue(differing > 0, "a moved vector plays other Runs, or the equality above holds for any Brain");
    }
}
