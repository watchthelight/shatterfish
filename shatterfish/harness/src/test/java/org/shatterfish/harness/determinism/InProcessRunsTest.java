package org.shatterfish.harness.determinism;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two Runs of one tuple in one process are one Run, even when the first one dies (story 5.1).
 *
 * <p>A hero who dies leaves remains ({@code core/.../actors/hero/Hero.java:2270}), and the game keeps
 * them in statics and reads its bones file only while it has none cached
 * ({@code core/.../Bones.java:50-54}, {@code :154-160}). So a Run begun in a fresh Profile after a
 * death in the same process found the dead hero's remains on its floor, and its Observations parted
 * from the first Run's at the first sight of them. The Rig plays each Run in a process of its own and
 * never saw it; the embedded driver's determinism test, which plays a tuple twice in one process, did.
 * The Profile now empties the cache as part of the empty history.
 */
class InProcessRunsTest {

    private static final long SEED = 27_182_818L;
    private static final long SALT = 0x5A17_5A17L;

    @Test
    @DisplayName("a Run after a death in the same process is the Run a fresh process would play")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_death_leaves_nothing_for_the_next_run() {
        List<Observation> first = new ArrayList<>();
        List<Observation> second = new ArrayList<>();
        RunOutcome died = play(first);
        assertEquals(RunOutcome.Cause.DEATH, died.cause(), "the first Run must die for this to test anything: " + died);
        play(second);
        assertEquals(first.size(), second.size(), "as many waits");
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).map().heaps(), second.get(i).map().heaps(),
                    "no remains of the first hero on the second Run's floor, at wait " + (i + 1));
            assertEquals(first.get(i).hash(), second.get(i).hash(), "the same Observation at wait " + (i + 1));
        }
    }

    private static RunOutcome play(List<Observation> into) {
        BrainDecider brain = new BrainDecider(brain());
        return new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, observation -> {
            into.add(observation);
            return brain.decide(observation);
        }, 1_500);
    }

    private static Weights weights() {
        Map<String, Long> terms = new TreeMap<>(Map.of(
                "act_attack", 0L, "act_descend", 0L, "act_rest_hurt", 0L, "act_search", 0L, "act_wait", 0L,
                "depth", 10000L, "enemies", -3000L, "hp", 10L, "hunger", -5000L, "level", 5000L));
        terms.put("strength", 2000L);
        terms.putAll(Map.of("item", 2000L, "gold", 10L, "turn", -150L, "weapon", 2L, "armor", 4L, "cursed", -10000L));
        return new Weights("shatterfish", 2,
                terms.entrySet().stream().map(term -> new Weights.Term(term.getKey(), term.getValue())).toList());
    }

    private static Brain brain() {
        return new Brain(new Codex.Knowledge(new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json")),
                List.of(), List.of(), List.of()), weights(), 19L);
    }
}
