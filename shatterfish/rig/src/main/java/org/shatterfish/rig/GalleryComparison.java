package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The per-Brain comparison view of the death gallery (story 4.13, the E4 half of FR-26): how two
 * Brains' Runs of the same triples ended, side by side, and which triples changed.
 *
 * <p><b>What it compares.</b> Each side is a folder of Run logs: a Rig folder, whose run index names
 * its logs, or any folder of logs, whose every {@code *.jsonl} file is one Run. A Run is keyed by its
 * triple and salt (seed, hero class, challenge flags, salt), which is what makes a Run of one Brain
 * the counterpart of a Run of another.
 *
 * <p><b>The situation.</b> A log's ending has no killer ({@link Gallery#NO_KILLER}), but a Brain's
 * last wait records the Decision it took on the screen that ended the Run: the Policy that acted
 * and the Safety flags that screen raised ({@code hp-low}, {@code enemy-in-view}, {@code starving},
 * and so on). That pair is what this view groups deaths by, under the name <i>situation</i>: it says
 * what the Brain was doing and what was wrong when the Run ended, which is the question a failure
 * cause answers first. A last wait with no Decision (a Brain that states none, or a human's wait) is
 * the situation {@link #NO_DECISION}.
 *
 * <p><b>Three tables.</b> Endings by cause and depth, both sides; the deaths by situation, both
 * sides; and every triple both sides played, worst change first -- shallower before deeper, then
 * shorter survival before longer -- so what the candidate broke is at the top. Triples only one side
 * played are counted, not compared. Plain Markdown, every cell escaped (NFR-9); the same two folders
 * give the same page.
 *
 * <p><b>A log it cannot key.</b> A log the index names that is not there, or one with no header to
 * read a triple from, is still a Run of that side: it is counted under {@link Gallery#NO_LOG} or
 * {@link Gallery#UNREADABLE}, keyed by its file name ({@link #UNKEYED}), and never compared, so a
 * side that lost logs shows it rather than looking smaller.
 */
public final class GalleryComparison {

    /** The page {@code main} writes into the comparison folder it is given. */
    public static final String FILE = "gallery-comparison.md";

    /** The start of the key of a log with no triple to key it by: its file name follows. */
    public static final String UNKEYED = "log ";

    /** The situation of a Run whose last wait carries no Decision. */
    public static final String NO_DECISION = "no decision";

    /**
     * One Run, keyed by its triple and salt.
     *
     * @param situation the last wait's Policy and flags, or {@link #NO_DECISION}
     */
    public record Ending(String key, String seedCode, String heroClass, String cause, int depth, long turns,
                         String situation, String log) {

        boolean decided() {
            return PairScore.ENDINGS.contains(cause);
        }
    }

    private GalleryComparison() {
    }

    /**
     * The Runs in {@code folder}, keyed: through its run index when it has one, else every
     * {@code *.jsonl} file in it. A key seen twice is refused, since one of the two would be counted
     * twice.
     */
    public static Map<String, Ending> of(Path folder) {
        List<Path> files = new ArrayList<>();
        Path home = folder.toAbsolutePath().normalize();
        if (Files.isRegularFile(home.resolve(RunIndex.RUNS))) {
            for (String line : lines(home.resolve(RunIndex.RUNS))) {
                if (line.isBlank()) {
                    continue;
                }
                String log = LogHeader.string(line, "log");
                if (log == null || log.isEmpty()) {
                    throw new IllegalArgumentException("the index in " + folder + " has a line without a log: " + line);
                }
                Path file = home.resolve(log).normalize();
                if (!home.equals(file.getParent())) {
                    throw new IllegalArgumentException("the index in " + folder + " names a log outside it: " + log);
                }
                files.add(file);
            }
        } else {
            try (var listed = Files.list(home)) {
                listed.filter(p -> p.getFileName().toString().endsWith(".jsonl") && Files.isRegularFile(p))
                        .sorted().forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException("could not list " + folder, e);
            }
        }
        Map<String, Ending> endings = new TreeMap<>();
        for (Path file : files) {
            Ending ending = ending(file);
            if (endings.put(ending.key(), ending) != null) {
                throw new IllegalArgumentException(folder + " holds two Runs of " + ending.key());
            }
        }
        return endings;
    }

    /**
     * A log's ending and situation; for a log that is not there, or has no header to key it by, an
     * ending under {@link Gallery#NO_LOG} or {@link Gallery#UNREADABLE} keyed by its file name.
     */
    static Ending ending(Path file) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file)) {
            return new Ending(UNKEYED + name, "", "", Gallery.NO_LOG, -1, -1, NO_DECISION, name);
        }
        RunLogReader.Log read;
        try {
            read = RunLogReader.of(file);
        } catch (RuntimeException unreadable) {
            return new Ending(UNKEYED + name, "", "", Gallery.UNREADABLE, -1, -1, NO_DECISION, name);
        }
        // It counts endings across two Rig folders, so an Overlay log is refused rather than counted
        // (story 5.1), as the gallery it compares refuses one.
        OverlayLogs.refuse(read, file);
        if (read.records().isEmpty() || !(read.records().get(0) instanceof RunLog.Header header)) {
            return new Ending(UNKEYED + name, "", "", Gallery.UNREADABLE, -1, -1, NO_DECISION, name);
        }
        String key = header.seedCode() + " " + header.heroClass().name() + " " + header.challenges()
                + " " + Long.toHexString(header.salt());
        List<RunLog.Wait> waits = read.waits();
        RunLog.Wait last = waits.isEmpty() ? null : waits.get(waits.size() - 1);
        String situation = situation(last);
        RunLog.End end = read.end();
        if (!read.readable()) {
            return new Ending(key, header.seedCode(), header.heroClass().name(), Gallery.UNREADABLE,
                    last == null ? -1 : last.depth(), -1, situation, name);
        }
        if (end == null) {
            return new Ending(key, header.seedCode(), header.heroClass().name(), Gallery.NO_ENDING,
                    last == null ? -1 : last.depth(), -1, situation, name);
        }
        return new Ending(key, header.seedCode(), header.heroClass().name(), end.outcome().cause(),
                end.outcome().depth(), end.outcome().turns(), situation, name);
    }

    /** The Policy of a wait's Decision and its flags, sorted, or {@link #NO_DECISION}. */
    static String situation(RunLog.Wait wait) {
        if (wait == null || wait.decision() == null) {
            return NO_DECISION;
        }
        List<String> flags = new ArrayList<>(new TreeSet<>(wait.decision().flags()));
        return wait.decision().policy() + (flags.isEmpty() ? "" : ": " + String.join(", ", flags));
    }

    /** The comparison page for a baseline folder and a candidate folder. */
    public static String page(String baselineName, Map<String, Ending> baseline, String candidateName,
                              Map<String, Ending> candidate) {
        StringBuilder out = new StringBuilder();
        out.append("<!-- Written by ").append(Gallery.COMMAND).append(" --compare. Each ending is read from")
                .append(" the Run's own log. -->\n\n");
        out.append("# How two Brains' Runs ended: ").append(Gallery.cell(candidateName)).append(" against ")
                .append(Gallery.cell(baselineName)).append("\n\n");
        List<String> both = baseline.keySet().stream().filter(k -> !k.startsWith(UNKEYED))
                .filter(candidate::containsKey).sorted().toList();
        long onlyBaseline = baseline.keySet().stream().filter(k -> !k.startsWith(UNKEYED))
                .filter(k -> !candidate.containsKey(k)).count();
        long onlyCandidate = candidate.keySet().stream().filter(k -> !k.startsWith(UNKEYED))
                .filter(k -> !baseline.containsKey(k)).count();
        long unkeyedBaseline = baseline.keySet().stream().filter(k -> k.startsWith(UNKEYED)).count();
        long unkeyedCandidate = candidate.keySet().stream().filter(k -> k.startsWith(UNKEYED)).count();
        out.append("The baseline has ").append(baseline.size()).append(" Runs and the candidate ")
                .append(candidate.size()).append("; ").append(both.size())
                .append(" triples were played by both and are compared, ").append(onlyBaseline)
                .append(" only by the baseline and ").append(onlyCandidate)
                .append(" only by the candidate. ");
        if (unkeyedBaseline + unkeyedCandidate > 0) {
            out.append(unkeyedBaseline).append(" baseline and ").append(unkeyedCandidate)
                    .append(" candidate logs are missing or have no header: they are counted under ")
                    .append(Gallery.NO_LOG).append(" or ").append(Gallery.UNREADABLE)
                    .append(" and never compared. ");
        }
        out.append(Gallery.NO_KILLER)
                .append(" The situation is the last wait's Policy and Safety flags: what the Brain was"
                        + " doing, and what was wrong, when the Run ended.\n\n");

        out.append("## Endings\n\n| Ending | Depth | Baseline | Candidate | Change |\n|---|---|---|---|---|\n");
        Map<String, int[]> endings = new TreeMap<>(Comparator.comparing((String k) -> !decided(k))
                .thenComparing(Comparator.naturalOrder()));
        count(endings, baseline, 0, e -> e.cause() + "|" + depthKey(e.depth()));
        count(endings, candidate, 1, e -> e.cause() + "|" + depthKey(e.depth()));
        for (Map.Entry<String, int[]> row : endings.entrySet()) {
            String[] parts = row.getKey().split("\\|", 2);
            out.append("| ").append(Gallery.cell(parts[0])).append(" | ").append(depthCell(parts[1]))
                    .append(" | ").append(row.getValue()[0]).append(" | ").append(row.getValue()[1])
                    .append(" | ").append(signed(row.getValue()[1] - row.getValue()[0])).append(" |\n");
        }

        out.append("\n## Deaths by situation\n\nThe endings the game decided, by the Decision on the"
                + " screen that ended them. Largest in the candidate first.\n\n"
                + "| Situation | Baseline | Candidate | Change |\n|---|---|---|---|\n");
        Map<String, int[]> situations = new LinkedHashMap<>();
        count(situations, baseline, 0, e -> e.decided() ? e.situation() : null);
        count(situations, candidate, 1, e -> e.decided() ? e.situation() : null);
        List<Map.Entry<String, int[]>> rows = new ArrayList<>(situations.entrySet());
        rows.sort(Comparator.comparingInt((Map.Entry<String, int[]> r) -> -r.getValue()[1])
                .thenComparingInt(r -> -r.getValue()[0]).thenComparing(Map.Entry::getKey));
        if (rows.isEmpty()) {
            out.append("| none | 0 | 0 | 0 |\n");
        }
        for (Map.Entry<String, int[]> row : rows) {
            out.append("| ").append(Gallery.cell(row.getKey())).append(" | ").append(row.getValue()[0])
                    .append(" | ").append(row.getValue()[1]).append(" | ")
                    .append(signed(row.getValue()[1] - row.getValue()[0])).append(" |\n");
        }

        int deeper = 0;
        int shallower = 0;
        int longer = 0;
        int shorter = 0;
        List<String[]> changes = new ArrayList<>();
        List<Integer> depthChange = new ArrayList<>();
        List<Long> turnChange = new ArrayList<>();
        for (String key : both) {
            Ending b = baseline.get(key);
            Ending c = candidate.get(key);
            int dd = c.depth() - b.depth();
            long dt = (c.turns() < 0 || b.turns() < 0) ? 0 : (c.turns() - b.turns()) / 1000;
            deeper += dd > 0 ? 1 : 0;
            shallower += dd < 0 ? 1 : 0;
            longer += dt > 0 ? 1 : 0;
            shorter += dt < 0 ? 1 : 0;
            changes.add(new String[]{key, c.seedCode(), c.heroClass(), ending(b), ending(c), b.situation(),
                    c.situation()});
            depthChange.add(dd);
            turnChange.add(dt);
        }
        Integer[] order = new Integer[changes.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> depthChange.get(i))
                .thenComparingLong(i -> turnChange.get(i)).thenComparing(i -> changes.get(i)[0]));
        out.append("\n## Every triple both played\n\n").append(deeper).append(" ended deeper in the candidate, ")
                .append(shallower).append(" shallower; ").append(longer).append(" survived longer, ")
                .append(shorter).append(" shorter. Worst change first.\n\n")
                .append("| Seed | Class | Baseline | Candidate | Depth | Turns | Baseline situation"
                        + " | Candidate situation |\n|---|---|---|---|---|---|---|---|\n");
        for (Integer i : order) {
            String[] row = changes.get(i);
            out.append("| ").append(Gallery.cell(row[1])).append(" | ").append(Gallery.cell(row[2]))
                    .append(" | ").append(Gallery.cell(row[3])).append(" | ").append(Gallery.cell(row[4]))
                    .append(" | ").append(signed(depthChange.get(i))).append(" | ")
                    .append(signed(turnChange.get(i))).append(" | ").append(Gallery.cell(row[5]))
                    .append(" | ").append(Gallery.cell(row[6])).append(" |\n");
        }
        return out.toString();
    }

    private static boolean decided(String key) {
        return PairScore.ENDINGS.contains(key.split("\\|", 2)[0]);
    }

    private static void count(Map<String, int[]> into, Map<String, Ending> side, int column,
                              java.util.function.Function<Ending, String> by) {
        for (Ending e : side.values()) {
            String k = by.apply(e);
            if (k != null) {
                into.computeIfAbsent(k, x -> new int[2])[column]++;
            }
        }
    }

    /** A depth as a key that sorts as a number: -1 (none) first, then 0 to 99. */
    private static String depthKey(int depth) {
        return depth < 0 ? "-" : String.format(Locale.ROOT, "%02d", depth);
    }

    private static String depthCell(String key) {
        return key.equals("-") ? "—" : String.valueOf(Integer.parseInt(key));
    }

    private static String ending(Ending e) {
        return e.cause() + " at " + (e.depth() < 0 ? "—" : e.depth())
                + (e.turns() < 0 ? "" : ", " + e.turns() / 1000 + " turns");
    }

    private static String signed(long n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    /**
     * Writes the comparison page to {@code into}, through a temporary file moved into place, so a
     * reader never sees half a page.
     */
    public static void write(Path baseline, Path candidate, Path into) {
        String text = page(baseline.getFileName().toString(), of(baseline), candidate.getFileName().toString(),
                of(candidate));
        try {
            Path temporary = into.resolveSibling(into.getFileName() + ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, into, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("the comparison could not be written to " + into, e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
