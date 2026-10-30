package org.shatterfish.rig;

import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.BeliefView;
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

    /**
     * The suffix of the arrival record: one line per depth, from the hero's first wait on it. It is the
     * raw material a kit profile is read from (ADR-0024): what a real hero held on the stairs.
     */
    public static final String ARRIVALS = ".arrivals.tsv";

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
        HeroClass heroClass = SeedSets.heroClass(required(a, RunOne.CLASS));
        Path out = Path.of(required(a, RunOne.OUT));
        java.util.concurrent.atomic.AtomicReference<Watch> watching = new java.util.concurrent.atomic.AtomicReference<>();
        RunOne.play(a, decider -> {
            Watch watch = new Watch((Deliberator) decider);
            watching.set(watch);
            return watch;
        });
        Files.writeString(out.resolve(heapsFile(heroClass, SeedSet.code(seed), salt)), watching.get().table(),
                StandardCharsets.UTF_8);
        Files.writeString(out.resolve(heapsFile(heroClass, SeedSet.code(seed), salt)
                .replace(HEAPS, ARRIVALS)), watching.get().arrivals(), StandardCharsets.UTF_8);
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

    private static final class Watch implements Deliberator, org.shatterfish.api.Searching {

        private final Deliberator inner;
        private final Map<String, Seen> open = new HashMap<>();
        private final List<Seen> all = new ArrayList<>();
        private int k;
        private int lastHero = -1;
        private final StringBuilder arrived = new StringBuilder(
                "depth\twait\tlevel\texp\thp\tht\tstr\titems\n");
        private final java.util.Set<Integer> arrivedAt = new java.util.HashSet<>();

        Watch(Deliberator inner) {
            this.inner = inner;
        }

        @Override
        public boolean searches() {
            return inner instanceof org.shatterfish.api.Searching searching && searching.searches();
        }

        @Override
        public void lookahead(org.shatterfish.api.Lookahead lookahead) {
            if (inner instanceof org.shatterfish.api.Searching searching) {
                searching.lookahead(lookahead);
            }
        }

        String arrivals() {
            return arrived.toString();
        }

        /** The first wait on a depth (branch 0): the hero as a player at the screen sees it. */
        private void arrive(Observation o) {
            if (o.header().branch() != 0 || !arrivedAt.add(o.header().depth())) {
                return;
            }
            var hero = o.hero();
            StringBuilder items = new StringBuilder();
            for (var item : o.inventory().items()) {
                if (items.length() > 0) {
                    items.append('|');
                }
                items.append(item.slot()).append(':').append(item.name());
                if (item.levelKnown() || item.visiblyUpgraded() != 0) {
                    items.append('+').append(item.visiblyUpgraded());
                }
                items.append('x').append(item.quantity());
            }
            arrived.append(o.header().depth()).append('\t').append(k).append('\t').append(hero.level()).append('\t')
                    .append(hero.exp()).append('\t').append(hero.hp()).append('\t').append(hero.ht()).append('\t')
                    .append(hero.strength()).append('\t').append(items).append('\n');
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
            arrive(o);
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

        @Override
        public BeliefView beliefView() {
            return inner.beliefView();
        }
    }
}
