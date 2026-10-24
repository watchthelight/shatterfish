package org.shatterfish.rig;

import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RunLoop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One Run for a direction check, in this process and nothing else: {@link RunOne}'s arguments, and
 * beside the Run's log a {@code .heaps.tsv} that records every plain heap from the wait it was
 * first on screen to the wait it was picked up.
 *
 * <p>The record is made by a {@link Deliberator} that wraps the Brain and reads only the
 * Observation the Brain is handed, so it knows what a player at the screen knows and changes no
 * decision. It is a rig tool: nothing it writes is read back by a Brain.
 */
public final class DirectionPlay {

    /** The suffix of the heap record written beside a Run's log. */
    public static final String HEAPS = ".heaps.tsv";

    private DirectionPlay() {
    }

    public static void main(String[] args) {
        try {
            play(RunOne.arguments(args));
        } catch (IOException | RuntimeException | Error failed) {
            System.err.println("the Run failed: " + failed);
            failed.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void play(Map<String, String> a) throws IOException {
        long seed = Long.parseLong(required(a, RunOne.SEED));
        long salt = Long.parseLong(required(a, RunOne.SALT));
        String brain = required(a, RunOne.BRAIN);
        HeroClass heroClass = SeedSets.heroClass(required(a, RunOne.CLASS));
        int challenges = Integer.parseInt(required(a, RunOne.CHALLENGES));
        SeedSet.Entry triple = new SeedSet.Entry(seed, heroClass, challenges, SeedSet.code(seed));
        Path out = Path.of(required(a, RunOne.OUT));
        if (!out.isAbsolute()) {
            throw new IllegalArgumentException(RunOne.OUT + " is an absolute path: " + out);
        }
        org.shatterfish.api.Weights weights = Brains.readsWeights(brain)
                ? WeightsFile.read(Path.of(required(a, RunOne.WEIGHTS)), brain) : null;
        RunLoop.Logging logging = new RunLoop.Logging(out, required(a, RunOne.COMMIT),
                new RunLog.Brain(brain, required(a, RunOne.BRAIN_COMMIT), Brains.configHash(brain, weights)),
                "", "");
        org.shatterfish.api.Codex.Knowledge codex = Brains.readsCodex(brain)
                ? CodexKnowledge.read(Path.of(required(a, RunOne.CODEX)),
                        org.shatterfish.harness.boot.HeadlessBoot.pinnedTag())
                : null;
        Deliberator inner = (Deliberator) Brains.of(brain, triple, codex, weights);
        Watch watch = new Watch(inner);
        new RunLoop().playTriple(triple, salt, watch, RunLoop.TURN_CAP, logging);
        Files.writeString(out.resolve(heapsFile(heroClass, SeedSet.code(seed), salt)), watch.table(),
                StandardCharsets.UTF_8);
    }

    /** The name of the heap record of the Run of this triple under this salt. */
    static String heapsFile(HeroClass heroClass, String seedCode, long salt) {
        return heroClass + "-" + seedCode + "-" + String.format("%016x", salt) + HEAPS;
    }

    private static String required(Map<String, String> a, String flag) {
        String value = a.get(flag);
        if (value == null) {
            throw new IllegalArgumentException("a Run states " + flag);
        }
        return value;
    }

    private static final class Seen {
        int depth;
        int branch;
        int cell;
        String item;
        int seen;
        int taken = -1;
    }

    private static final class Watch implements Deliberator {

        private final Deliberator inner;
        private final Map<String, Seen> open = new HashMap<>();
        private final List<Seen> all = new ArrayList<>();
        private int k;
        private int lastHero = -1;

        Watch(Deliberator inner) {
            this.inner = inner;
        }

        String table() {
            StringBuilder sb = new StringBuilder("depth\tbranch\tcell\titem\tseen\ttaken\n");
            for (Seen s : all) {
                sb.append(s.depth).append('\t').append(s.branch).append('\t').append(s.cell).append('\t')
                        .append(s.item).append('\t').append(s.seen).append('\t').append(s.taken).append('\n');
            }
            return sb.toString();
        }

        @Override
        public Action decide(Observation o) {
            k++;
            int d = o.header().depth();
            int b = o.header().branch();
            int hero = o.hero().cell();
            for (HeapView h : o.map().heaps()) {
                if (h.kind() == HeapKind.HEAP && !h.item().isEmpty()) {
                    String key = d + ":" + b + ":" + h.cell();
                    if (!open.containsKey(key)) {
                        Seen s = new Seen();
                        s.depth = d;
                        s.branch = b;
                        s.cell = h.cell();
                        s.item = h.item();
                        s.seen = k;
                        all.add(s);
                        open.put(key, s);
                    }
                }
            }
            for (Seen s : all) {
                if (s.taken < 0 && s.depth == d && s.branch == b
                        && o.map().fog().get(s.cell) == Fog.VISIBLE
                        && o.map().heaps().stream().noneMatch(h -> h.cell() == s.cell && h.kind() == HeapKind.HEAP)
                        && (hero == s.cell || lastHero == s.cell)) {
                    s.taken = k;
                    open.remove(s.depth + ":" + s.branch + ":" + s.cell);
                }
            }
            lastHero = hero;
            return inner.decide(o);
        }

        @Override
        public RunLog.Decision lastDecision() {
            return inner.lastDecision();
        }

        @Override
        public Belief belief() {
            return inner.belief();
        }

        @Override
        public String beliefHash() {
            return inner.beliefHash();
        }

        @Override
        public List<Integer> lastHighlights() {
            return inner.lastHighlights();
        }

        @Override
        public BeliefSummary beliefSummary() {
            return inner.beliefSummary();
        }
    }
}
