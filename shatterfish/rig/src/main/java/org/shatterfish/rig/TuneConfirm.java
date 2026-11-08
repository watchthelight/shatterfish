package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The confirmation of a tuned candidate (story 10.37): two folders of Run logs played on the same triples under the
 * same salts, one with the candidate vector and one with the current numbers, paired and put through the E3
 * Sequential test ({@link EProcess}), pooled and per class.
 *
 * <pre>
 * ./gradlew :rig:confirm --args="--candidate &lt;direction folder&gt;/current --current &lt;folder&gt; --out &lt;folder&gt;"
 * </pre>
 *
 * <p>It plays nothing. It reads the logs, writes {@code runs.tsv} (one line per Run: side, class, salt, seed code,
 * ending, depth, turns, bosses killed, Decisions and the milliseconds they took), the committed summary a results page
 * is checked against, and prints the verdict. The pairing key is the header's class, seed code and salt; a triple with
 * a Run missing or unfinished on either side is a missing pair and scores a tie, as in a ranked comparison. The pairs
 * are fed to the test in salt order, classes interleaved in the pooled test, so no class's pairs come all at once.
 *
 * <p>The hypotheses are fixed here and not by flag, so a result cannot be re-run under kinder ones: H0 is a pair-score
 * mean of 0.50 (no better), H1 is 0.55, alpha and beta are 0.05, the pairs may be 25% missing, and the test is
 * undecided after the pairs it was given. A candidate is kept only if, on each slice, the pooled test accepts, no
 * class kills fewer bosses than the current numbers, and no class's worse pairs outnumber its better pairs by
 * {@link #VETO} or more ({@link #kept}).
 */
public final class TuneConfirm {

    public static final String CANDIDATE = "--candidate";
    public static final String CURRENT = "--current";
    public static final String OUT = "--out";

    /** H0's pair-score mean. */
    public static final double P0 = 0.50;
    /** H1's pair-score mean. */
    public static final double P1 = 0.55;
    /** Both error rates. */
    public static final double ERROR = 0.05;
    /** The share of pairs, in thousandths, that may be missing. */
    public static final int MISSING_PER_MIL = 250;

    static final List<String> CLASSES = List.of("WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC");

    /** One Run read back: the figures of its ending and the time its Decisions took. */
    record Run(String heroClass, long salt, String seedCode, boolean win, String cause, int depth,
               long turns, int bosses, int decisions, long thinkMs, RunLog.Outcome outcome) {
    }

    /** What the test said of one group of pairs, with the figures a page quotes. */
    record Verdict(String group, int pairs, int missing, int worse, int equal, int better, Gsprt.Verdict verdict,
                   int consumed, int bossesCandidate, int bossesCurrent) {
    }

    private TuneConfirm() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> a = new TreeMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            a.put(args[i], args[i + 1]);
        }
        if (!a.containsKey(CANDIDATE) || !a.containsKey(CURRENT) || !a.containsKey(OUT) || a.size() != 3) {
            throw new IllegalArgumentException("usage: " + CANDIDATE + " <folder> " + CURRENT + " <folder> " + OUT + " <folder>");
        }
        Path out = Path.of(a.get(OUT));
        Files.createDirectories(out);
        List<Run> candidate = read(Path.of(a.get(CANDIDATE)));
        List<Run> current = read(Path.of(a.get(CURRENT)));
        Files.writeString(out.resolve("runs.tsv"), table(candidate, current), StandardCharsets.UTF_8);
        String text = text(verdicts(candidate, current));
        Files.writeString(out.resolve("confirm.txt"), text, StandardCharsets.UTF_8);
        System.out.print(text);
    }

    // ------------------------------------------------------------------------------------------------ reading

    static List<Run> read(Path folder) {
        List<Run> runs = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .filter(p -> !p.getFileName().toString().equals(RunIndex.RUNS)).sorted().toList()) {
                RunLogReader.Log log = RunLogReader.of(file);
                OverlayLogs.refuse(log, file);
                if (!log.readable() || log.records().isEmpty() || !(log.records().get(0) instanceof RunLog.Header header)) {
                    continue;
                }
                if (header.oracle() || !header.registration().isEmpty()) {
                    throw new IllegalStateException(file + " is an oracle or a ranked Run");
                }
                RunLog.Outcome outcome = log.complete() && log.end() != null ? log.end().outcome() : null;
                int decisions = 0;
                long think = 0;
                for (RunLog.Wait wait : log.waits()) {
                    if (wait.decision() != null) {
                        decisions++;
                        think += wait.thinkMs();
                    }
                }
                runs.add(new Run(header.heroClass().name(), header.salt(), header.seedCode(),
                        outcome != null && outcome.win(), outcome == null ? "NONE" : outcome.cause(),
                        outcome == null ? 0 : outcome.depth(), outcome == null ? 0 : outcome.turns(),
                        outcome == null ? 0 : outcome.bosses(), decisions, think, outcome));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + folder, e);
        }
        runs.sort(Comparator.comparing((Run r) -> CLASSES.indexOf(r.heroClass())).thenComparingLong(Run::salt));
        return runs;
    }

    static String table(List<Run> candidate, List<Run> current) {
        StringBuilder sb = new StringBuilder("side\tclass\tsalt\tseedcode\tcause\twin\tscore\tdepth\tturns\tbosses\tdecisions\tthink_ms\n");
        append(sb, "current", current);
        append(sb, "candidate", candidate);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String side, List<Run> runs) {
        for (Run r : runs) {
            sb.append(side).append('\t').append(r.heroClass()).append('\t').append(r.salt()).append('\t')
                    .append(r.seedCode()).append('\t').append(r.cause()).append('\t').append(r.win() ? 1 : 0).append('\t')
                    .append(r.outcome() == null ? 0 : r.outcome().score()).append('\t').append(r.depth()).append('\t').append(r.turns()).append('\t').append(r.bosses()).append('\t')
                    .append(r.decisions()).append('\t').append(r.thinkMs()).append('\n');
        }
    }

    /**
     * The Runs of one side of a committed {@code runs.tsv}, so a page's figures can be recomputed without the logs.
     * A line whose cause is {@code NONE} is a Run with no ending.
     */
    static List<Run> fromTable(Path table, String side) {
        List<Run> runs = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
            for (String line : lines.subList(1, lines.size())) {
                String[] p = line.split("\t", -1);
                if (line.isEmpty() || !p[0].equals(side)) {
                    continue;
                }
                boolean ended = !p[4].equals("NONE");
                boolean win = p[5].equals("1");
                RunLog.Outcome outcome = ended ? new RunLog.Outcome(win, false, Long.parseLong(p[6]),
                        Integer.parseInt(p[7]), Long.parseLong(p[8]), p[4], Integer.parseInt(p[9])) : null;
                runs.add(new Run(p[1], Long.parseLong(p[2]), p[3], win, p[4], Integer.parseInt(p[7]),
                        Long.parseLong(p[8]), Integer.parseInt(p[9]), Integer.parseInt(p[10]), Long.parseLong(p[11]), outcome));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + table, e);
        }
        return runs;
    }

    // ------------------------------------------------------------------------------------------------ the test

    /** The test of the hypotheses above over {@code maximum} pairs. */
    static EProcess test(int maximum) {
        return new EProcess(P0, P1, ERROR, ERROR, Math.max(1, maximum)).allowingMissing(MISSING_PER_MIL);
    }

    private static String key(Run r) {
        return r.heroClass() + "/" + r.salt() + "/" + r.seedCode();
    }

    /** Pooled first, then each class that has Runs. */
    static List<Verdict> verdicts(List<Run> candidate, List<Run> current) {
        Map<String, Run> mine = new LinkedHashMap<>();
        Map<String, Run> theirs = new LinkedHashMap<>();
        candidate.forEach(r -> mine.put(key(r), r));
        current.forEach(r -> theirs.put(key(r), r));
        List<String> keys = new ArrayList<>(mine.keySet());
        theirs.keySet().stream().filter(k -> !mine.containsKey(k)).forEach(keys::add);
        // Salt order, classes interleaved: the pooled test never sees one class's pairs all at once.
        keys.sort(Comparator.comparingLong((String k) -> Long.parseLong(k.split("/")[1]))
                .thenComparingInt(k -> CLASSES.indexOf(k.split("/")[0])).thenComparing(k -> k));
        List<Verdict> out = new ArrayList<>();
        out.add(one("POOLED", keys, mine, theirs));
        for (String hero : CLASSES) {
            List<String> own = keys.stream().filter(k -> k.startsWith(hero + "/")).toList();
            if (!own.isEmpty()) {
                out.add(one(hero, own, mine, theirs));
            }
        }
        return out;
    }

    private static Verdict one(String group, List<String> keys, Map<String, Run> mine, Map<String, Run> theirs) {
        List<PairScore> scores = new ArrayList<>();
        List<Boolean> missing = new ArrayList<>();
        int bossesMine = 0;
        int bossesTheirs = 0;
        for (String key : keys) {
            RunLog.Outcome m = mine.containsKey(key) ? mine.get(key).outcome() : null;
            RunLog.Outcome t = theirs.containsKey(key) ? theirs.get(key).outcome() : null;
            scores.add(PairScore.of(m, t));
            missing.add(!PairScore.reached(m) || !PairScore.reached(t));
            bossesMine += m == null ? 0 : m.bosses();
            bossesTheirs += t == null ? 0 : t.bosses();
        }
        Gsprt.Result result = test(scores.size()).run(scores, missing);
        // Counted over every pair played, not over the pairs the test consumed before it stopped: the veto and the page
        // are about the whole slice, and "consumed" says where the test stopped.
        int[] c = new int[3];
        scores.forEach(s -> c[s.halves()]++);
        return new Verdict(group, keys.size(), (int) missing.stream().filter(b -> b).count(), c[0], c[1], c[2],
                result.verdict(), result.pairs(), bossesMine, bossesTheirs);
    }

    /** A class whose better pairs trail its worse pairs by this many or more on a slice vetoes the candidate. */
    public static final int VETO = 8;

    /** Whether a class's pairs trail by {@link #VETO} or more. */
    static boolean vetoed(Verdict v) {
        return v.worse() - v.better() >= VETO;
    }

    /**
     * Whether the candidate holds on one slice: the pooled test accepts, no class kills fewer bosses than the current
     * numbers, and no class is vetoed.
     */
    static boolean kept(List<Verdict> slice) {
        boolean accepted = slice.get(0).verdict() == Gsprt.Verdict.ACCEPT;
        boolean noFall = slice.stream().skip(1).allMatch(v -> v.bossesCandidate() >= v.bossesCurrent());
        boolean noVeto = slice.stream().skip(1).noneMatch(TuneConfirm::vetoed);
        return accepted && noFall && noVeto;
    }

    static String text(List<Verdict> verdicts) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "confirm: H0 %.2f, H1 %.2f, alpha = beta = %.2f, missing allowed %d per mille, e-process%n", P0, P1, ERROR, MISSING_PER_MIL));
        sb.append("group\tpairs\tmissing\tworse\tequal\tbetter\tverdict\tconsumed\tbosses_candidate\tbosses_current\tvetoed\n");
        for (Verdict v : verdicts) {
            sb.append(v.group()).append('\t').append(v.pairs()).append('\t').append(v.missing()).append('\t')
                    .append(v.worse()).append('\t').append(v.equal()).append('\t').append(v.better()).append('\t')
                    .append(v.verdict()).append('\t').append(v.consumed()).append('\t').append(v.bossesCandidate())
                    .append('\t').append(v.bossesCurrent()).append('\t')
                    .append(!v.group().equals("POOLED") && vetoed(v)).append('\n');
        }
        sb.append("kept on this slice: ").append(kept(verdicts)).append('\n');
        return sb.toString();
    }
}
