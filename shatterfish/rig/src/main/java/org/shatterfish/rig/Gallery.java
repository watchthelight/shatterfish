package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The death gallery (story 3.12, FR-26): how the Runs of one Rig invocation ended, grouped by cause
 * and depth, with the seeds, so that the next thing to fix is the biggest group.
 *
 * <p>Every ending is read from the Run's own log, through the harness's reader, never from the run
 * index: the index is the parent's account of a child, and the gallery is about what each child
 * wrote. A Run whose log is absent is grouped as {@code NO_LOG}; one whose log holds no ending, as
 * {@code NO_ENDING}; one whose log cannot be read or holds no header, as {@code UNREADABLE}. None
 * is dropped, so the counts add up to the index's.
 *
 * <p>The Runner writes {@code gallery.md} beside each side's summary when an invocation completes.
 * {@code ./gradlew :rig:gallery --args="<folder> [--snapshots N]"} rewrites it for any folder the
 * Rig wrote, and with {@code --snapshots} also writes {@code snapshots/<run id>.md}, the last N waits
 * of every Run, which the gallery then links to.
 *
 * <p>Plain Markdown, readable with a text editor (NFR-9). The per-Brain comparison view -- the same
 * gallery side by side for two Brains -- is E4's half of FR-26 and is not here.
 */
public final class Gallery {

    /** The file the gallery is written to, beside the run index. */
    public static final String FILE = "gallery.md";

    /** The folder the snapshots are written to, beside the gallery. */
    public static final String SNAPSHOTS = "snapshots";

    public static final String COMMAND = "./gradlew :rig:gallery";

    /** A Run whose log is not there. */
    public static final String NO_LOG = "NO_LOG";

    /** A Run whose log was read and ends before an ending. */
    public static final String NO_ENDING = "NO_ENDING";

    /** A Run whose log could not be read, or has no header. */
    public static final String UNREADABLE = "UNREADABLE";

    /**
     * One Run, as its log tells it.
     *
     * @param depth the depth its ending records, or -1 when there is no ending to say
     * @param turns turns survived in thousandths, or -1 with it
     */
    public record Run(String runId, String log, String seedCode, String heroClass, String cause,
                      int depth, long turns) {
    }

    /** The Runs that ended one way at one depth. */
    public record Group(String cause, int depth, List<Run> runs) {

        public Group {
            runs = List.copyOf(runs);
        }
    }

    private Gallery() {
    }

    /**
     * The groups for the Runs {@code folder}'s index lists, largest first; ties by cause, then
     * depth, and the Runs in each by seed code, then run id, so the same folder gives the same page.
     */
    public static List<Group> of(Path folder) {
        List<Run> runs = new ArrayList<>();
        for (String line : lines(folder.resolve(RunIndex.RUNS))) {
            if (line.isBlank()) {
                continue;
            }
            String runId = LogHeader.string(line, "runId");
            String log = LogHeader.string(line, "log");
            if (runId == null || log == null) {
                throw new IllegalArgumentException("the index in " + folder
                        + " has a line without a run id or a log: " + line);
            }
            Path file = folder.resolve(log).normalize();
            if (!folder.toAbsolutePath().normalize().equals(file.toAbsolutePath().getParent())) {
                throw new IllegalArgumentException("the index in " + folder + " names a log outside"
                        + " it: " + log);
            }
            runs.add(run(runId, log, file, LogHeader.string(line, "class")));
        }
        Map<String, List<Run>> grouped = new LinkedHashMap<>();
        for (Run run : runs) {
            grouped.computeIfAbsent(run.cause() + "|" + run.depth(), k -> new ArrayList<>()).add(run);
        }
        List<Group> groups = new ArrayList<>();
        for (List<Run> members : grouped.values()) {
            List<Run> sorted = new ArrayList<>(members);
            sorted.sort(Comparator.comparing(Run::seedCode).thenComparing(Run::runId));
            groups.add(new Group(sorted.get(0).cause(), sorted.get(0).depth(), sorted));
        }
        groups.sort(Comparator.comparingInt((Group g) -> -g.runs().size())
                .thenComparing(Group::cause).thenComparingInt(Group::depth));
        return List.copyOf(groups);
    }

    private static Run run(String runId, String log, Path file, String indexClass) {
        String heroClass = indexClass == null ? "" : indexClass;
        if (!Files.isRegularFile(file)) {
            return new Run(runId, log, "", heroClass, NO_LOG, -1, -1);
        }
        RunLogReader.Log read = RunLogReader.of(file);
        if (!read.readable() || read.records().isEmpty()
                || !(read.records().get(0) instanceof RunLog.Header header)) {
            return new Run(runId, log, "", heroClass, UNREADABLE, -1, -1);
        }
        RunLog.End end = read.end();
        if (end == null) {
            return new Run(runId, log, header.seedCode(), header.heroClass().name(), NO_ENDING, -1, -1);
        }
        return new Run(runId, log, header.seedCode(), header.heroClass().name(),
                end.outcome().cause(), end.outcome().depth(), end.outcome().turns());
    }

    /** The gallery page for {@code folder}; {@code snapshots} says whether to link each Run's. */
    public static String page(Path folder, List<Group> groups, boolean snapshots) {
        String summary = Files.isRegularFile(folder.resolve(RunIndex.SUMMARY))
                ? read(folder.resolve(RunIndex.SUMMARY)).strip() : "";
        String brain = summary.isEmpty() ? null : LogHeader.string(summary, "brain");
        String set = summary.isEmpty() ? null : LogHeader.string(summary, "seedSet");
        int total = groups.stream().mapToInt(g -> g.runs().size()).sum();
        StringBuilder out = new StringBuilder();
        out.append("<!-- Written by the Rig (and by ").append(COMMAND).append("). Each ending is read")
                .append(" from the Run's own log. -->\n\n");
        out.append("# How the Runs ended");
        if (brain != null && set != null) {
            out.append(": `").append(brain).append("` on `").append(set).append('`');
        }
        out.append("\n\n");
        out.append(total).append(" Runs in ").append(groups.size())
                .append(groups.size() == 1 ? " group" : " groups")
                .append(", largest first. Turns are turns survived, from the log's ending.\n\n");
        out.append("| Ending | Depth | Runs | Share |\n|---|---|---|---|\n");
        for (Group group : groups) {
            out.append("| `").append(group.cause()).append("` | ").append(depth(group.depth()))
                    .append(" | ").append(group.runs().size()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f%%", 100.0 * group.runs().size() / total))
                    .append(" |\n");
        }
        for (Group group : groups) {
            out.append("\n## `").append(group.cause()).append("` at depth ").append(depth(group.depth()))
                    .append(" — ").append(group.runs().size())
                    .append(group.runs().size() == 1 ? " Run" : " Runs").append("\n\n");
            out.append("| Seed | Class | Turns | Log |").append(snapshots ? " Last waits |" : "")
                    .append("\n|---|---|---|---|").append(snapshots ? "---|" : "").append('\n');
            for (Run run : group.runs()) {
                out.append("| ").append(run.seedCode().isEmpty() ? "—" : "`" + run.seedCode() + "`")
                        .append(" | ").append(run.heroClass().isEmpty() ? "—" : run.heroClass())
                        .append(" | ").append(run.turns() < 0 ? "—"
                                : String.format(Locale.ROOT, "%.1f", run.turns() / 1000.0))
                        .append(" | [").append(run.runId()).append("](").append(run.log()).append(')')
                        .append(" |");
                if (snapshots) {
                    out.append(NO_LOG.equals(run.cause()) ? " —" : " [snapshot](" + SNAPSHOTS + "/"
                            + snapshotName(run) + ")").append(" |");
                }
                out.append('\n');
            }
        }
        out.append("\nThe per-Brain comparison view, this gallery for two Brains side by side, is E4's"
                + " half of FR-26 and is not written here.\n");
        return out.toString();
    }

    /**
     * A snapshot's file name: the log's, which the index check has already confined to the folder,
     * with {@code .md} for {@code .jsonl}. Named from the run id, an index line saying
     * {@code "runId":"../x"} wrote outside {@code snapshots/}.
     */
    static String snapshotName(Run run) {
        String log = Path.of(run.log()).getFileName().toString();
        return (log.endsWith(".jsonl") ? log.substring(0, log.length() - ".jsonl".length()) : log) + ".md";
    }

    private static String depth(int depth) {
        return depth < 0 ? "—" : String.valueOf(depth);
    }

    /** The last {@code count} waits of a Run's log, as a page: what it did before it ended. */
    public static String snapshot(Path file, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("a snapshot of at least one wait: " + count);
        }
        RunLogReader.Log read = RunLogReader.of(file);
        List<RunLog.Wait> waits = read.waits();
        List<RunLog.Wait> last = waits.subList(Math.max(0, waits.size() - count), waits.size());
        RunLog.End end = read.end();
        StringBuilder out = new StringBuilder("# The last waits of `")
                .append(file.getFileName()).append("`\n\n");
        out.append("The last ").append(last.size()).append(" of ").append(waits.size())
                .append(" waits, from the Run's own log");
        if (end != null) {
            out.append(String.format(Locale.ROOT, "; it ended `%s` at depth %d after %.1f turns",
                    end.outcome().cause(), end.outcome().depth(), end.outcome().turns() / 1000.0));
        } else {
            out.append("; it has no ending");
        }
        out.append(".\n\n| Wait | Turn | Depth | Actor | Action |\n|---|---|---|---|---|\n");
        for (RunLog.Wait wait : last) {
            out.append("| ").append(wait.k()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f", wait.turn() / 1000.0)).append(" | ")
                    .append(wait.depth()).append(" | ").append(wait.actor()).append(" | `")
                    .append(wait.action()).append("` |\n");
        }
        return out.toString();
    }

    /**
     * Writes {@code gallery.md} in {@code folder}, and with {@code snapshots} above zero the last
     * that many waits of every Run with a log into {@code snapshots/}.
     */
    public static void write(Path folder, int snapshots) {
        List<Group> groups = of(folder);
        try {
            if (snapshots > 0) {
                Path into = Files.createDirectories(folder.resolve(SNAPSHOTS));
                // The folder is the gallery's own: last time's snapshots of Runs no longer in the
                // index would otherwise sit beside this time's as though they were part of it.
                try (var stale = Files.list(into)) {
                    for (Path old : stale.filter(f -> f.getFileName().toString().endsWith(".md")).toList()) {
                        Files.delete(old);
                    }
                }
                for (Group group : groups) {
                    for (Run run : group.runs()) {
                        if (!NO_LOG.equals(run.cause())) {
                            Files.writeString(into.resolve(snapshotName(run)),
                                    snapshot(folder.resolve(run.log()), snapshots), StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            Files.writeString(folder.resolve(FILE), page(folder, groups, snapshots > 0),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the gallery could not be written in " + folder, e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    /**
     * {@code <folder> [--snapshots N]}: writes the gallery for a folder the Rig wrote -- for a
     * comparison folder, one per side.
     */
    public static void main(String[] args) {
        if (args.length != 1 && !(args.length == 3 && args[1].equals("--snapshots"))) {
            throw new IllegalArgumentException("usage: Gallery <folder> [--snapshots N], not "
                    + Arrays.toString(args));
        }
        Path folder = Path.of(args[0]).toAbsolutePath().normalize();
        int snapshots = args.length == 3 ? Integer.parseInt(args[2]) : 0;
        if (args.length == 3 && snapshots < 1) {
            throw new IllegalArgumentException("--snapshots takes a count of at least one: " + args[2]);
        }
        long began = System.nanoTime();
        List<Path> folders = Files.isRegularFile(folder.resolve(Comparison.FILE))
                ? List.of(folder.resolve(Comparison.CANDIDATE), folder.resolve(Comparison.BASELINE))
                : List.of(folder);
        for (Path one : folders) {
            write(one, snapshots);
            System.out.println("the gallery wrote " + one.resolve(FILE));
        }
        System.out.println("in " + (System.nanoTime() - began) / 1_000_000L + " ms");
    }
}
