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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The death gallery (story 3.12, FR-26): how the Runs of one Rig invocation ended, grouped by
 * ending and depth, with the seeds, so that the next thing to fix is in front of you.
 *
 * <p><b>What it groups by.</b> A Run log's ending records how the Run ended ({@code DEATH},
 * {@code WIN}, or a stop the game did not decide) and at what depth, and nothing about what killed
 * the hero: {@link RunLog.Outcome} has no killer -- no mob, trap or hunger. So the gallery groups by
 * ending and depth, and says so on every page; the killer is an idea for later (docs/ideas.md).
 *
 * <p><b>Two sections.</b> Endings the game decided ({@link PairScore#ENDINGS}) come first, largest
 * group first: those are the deaths, and the largest is the place to look. Runs the game did not
 * end -- a window the Harness does not know, the turn cap, no log, a log with no ending, a log that
 * cannot be read -- come after, because they say what the Rig or the Harness failed at, not how the
 * hero died.
 *
 * <p><b>Nothing is dropped.</b> Every ending is read from the Run's own log, through the harness's
 * reader, never from the run index. A Run whose log is absent is {@code NO_LOG}; one whose log stops
 * before an ending, {@code NO_ENDING} at the depth of its last wait; one whose log cannot be read,
 * or throws while being read, {@code UNREADABLE}. The counts add up to the index's.
 *
 * <p>The Runner writes {@code gallery.md} beside each side's summary when an invocation completes.
 * {@code ./gradlew :rig:gallery --args="<folder> [--snapshots N]"} rewrites it for any folder the
 * Rig wrote -- for a comparison, one per side -- and with {@code --snapshots} also writes the last N
 * waits of every Run into {@code snapshots/}, which the gallery then links. Plain Markdown, every
 * cell escaped, readable with a text editor (NFR-9). The per-Brain comparison view, E4's half of
 * FR-26, is {@link GalleryComparison}.
 */
public final class Gallery {

    /** The file the gallery is written to, beside the run index. */
    public static final String FILE = "gallery.md";

    /** The folder the snapshots are written to, beside the gallery. */
    public static final String SNAPSHOTS = "snapshots";

    public static final String COMMAND = "./gradlew :rig:gallery";

    /** A Run whose log is not there. */
    public static final String NO_LOG = "NO_LOG";

    /** A Run whose log was read and stops before an ending. */
    public static final String NO_ENDING = "NO_ENDING";

    /** A Run whose log could not be read, has no header, or failed while being read. */
    public static final String UNREADABLE = "UNREADABLE";

    /** What every page says about what it groups by. */
    static final String NO_KILLER = "A Run log's ending records how the Run ended and at what depth,"
            + " not what killed the hero: the log's Outcome has no killer (a mob, a trap, hunger), so"
            + " this gallery groups by ending and depth.";

    /**
     * One Run, as its log tells it.
     *
     * @param depth the depth its ending records -- or, with no ending, its last wait's -- or -1
     * @param turns turns survived in thousandths, or -1 when there is no ending to say
     */
    public record Run(String runId, String log, String seedCode, String heroClass, String cause,
                      int depth, long turns) {

        /** Whether the game decided this ending, which is what makes it a death (or a win). */
        public boolean decided() {
            return PairScore.ENDINGS.contains(cause);
        }
    }

    /** The Runs that ended one way at one depth. */
    public record Group(String cause, int depth, List<Run> runs) {

        public Group {
            runs = List.copyOf(runs);
        }

        public boolean decided() {
            return PairScore.ENDINGS.contains(cause);
        }
    }

    private Gallery() {
    }

    /**
     * The groups for the Runs {@code folder}'s index lists: decided endings first, then the rest,
     * each largest first, ties by cause then depth, and the Runs in each by seed code then run id,
     * so the same folder gives the same page. An index that names one run id or one log twice is
     * refused: one of the two lines would be a Run counted twice.
     */
    public static List<Group> of(Path folder) {
        Path home = folder.toAbsolutePath().normalize();
        List<Run> runs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Path> logs = new HashSet<>();
        for (String line : lines(folder.resolve(RunIndex.RUNS))) {
            if (line.isBlank()) {
                continue;
            }
            String runId = LogHeader.string(line, "runId");
            String log = LogHeader.string(line, "log");
            if (runId == null || runId.isEmpty() || log == null || log.isEmpty()) {
                throw new IllegalArgumentException("the index in " + folder
                        + " has a line without a run id or a log: " + line);
            }
            Path file = home.resolve(log).normalize();
            if (!home.equals(file.getParent())) {
                throw new IllegalArgumentException("the index in " + folder + " names a log outside"
                        + " it: " + log);
            }
            if (!ids.add(runId) || !logs.add(file)) {
                throw new IllegalArgumentException("the index in " + folder + " names the Run " + runId
                        + " or the log " + log + " twice");
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
        groups.sort(Comparator.comparing((Group g) -> !g.decided())
                .thenComparingInt(g -> -g.runs().size())
                .thenComparing(Group::cause).thenComparingInt(Group::depth));
        return List.copyOf(groups);
    }

    private static Run run(String runId, String log, Path file, String indexClass) {
        String heroClass = indexClass == null ? "" : indexClass;
        if (!Files.isRegularFile(file)) {
            return new Run(runId, log, "", heroClass, NO_LOG, -1, -1);
        }
        try {
            RunLogReader.Log read = RunLogReader.of(file);
            OverlayLogs.refuse(read, file);
            if (!read.readable() || read.records().isEmpty()
                    || !(read.records().get(0) instanceof RunLog.Header header)) {
                return new Run(runId, log, "", heroClass, UNREADABLE, -1, -1);
            }
            RunLog.End end = read.end();
            if (end == null) {
                List<RunLog.Wait> waits = read.waits();
                int depth = waits.isEmpty() ? -1 : waits.get(waits.size() - 1).depth();
                return new Run(runId, log, header.seedCode(), header.heroClass().name(), NO_ENDING,
                        depth, -1);
            }
            return new Run(runId, log, header.seedCode(), header.heroClass().name(),
                    end.outcome().cause(), end.outcome().depth(), end.outcome().turns());
        } catch (OverlayLogs.Refused overlay) {
            throw overlay;
        } catch (RuntimeException unreadable) {
            // One bad log must not cost the gallery every other Run: it is a Run the gallery
            // could not read, and it is counted as one.
            return new Run(runId, log, "", heroClass, UNREADABLE, -1, -1);
        }
    }

    /** The gallery page for {@code folder}; {@code snapshots} says whether to link each Run's. */
    public static String page(Path folder, List<Group> groups, boolean snapshots) {
        String summary = Files.isRegularFile(folder.resolve(RunIndex.SUMMARY))
                ? read(folder.resolve(RunIndex.SUMMARY)).strip() : "";
        String brain = summary.isEmpty() ? null : LogHeader.string(summary, "brain");
        String set = summary.isEmpty() ? null : LogHeader.string(summary, "seedSet");
        int total = groups.stream().mapToInt(g -> g.runs().size()).sum();
        int decided = groups.stream().filter(Group::decided).mapToInt(g -> g.runs().size()).sum();
        StringBuilder out = new StringBuilder();
        out.append("<!-- Written by the Rig (and by ").append(COMMAND).append("). Each ending is read")
                .append(" from the Run's own log. -->\n\n");
        out.append("# How the Runs ended");
        if (brain != null && set != null) {
            out.append(": ").append(cell(brain)).append(" on ").append(cell(set));
        }
        out.append("\n\n");
        out.append(total).append(" Runs: ").append(decided).append(" ended by the game, ")
                .append(total - decided).append(" not. ").append(NO_KILLER)
                .append(" Turns are whole turns survived, from the log's ending.\n\n");
        out.append("Each Run links its log by file name, in the folder this page sits in. A copy of"
                + " this page published without its logs -- as `results/` publishes them -- links"
                + " logs that are not committed.\n\n");
        section(out, "Endings the game decided",
                "Largest group first: the largest is the place to look.",
                groups.stream().filter(Group::decided).toList(), total, snapshots);
        section(out, "Runs the game did not end",
                "A window the Harness does not know, the turn cap, a missing or unreadable log: what"
                        + " the Rig or the Harness did not finish, not how the hero died.",
                groups.stream().filter(g -> !g.decided()).toList(), total, snapshots);
        out.append("\nFor two Brains side by side -- endings, deaths by the situation that ended them,"
                + " and every triple both played -- see the comparison view (`")
                .append(GalleryComparison.FILE).append("`, written beside a comparison's two sides).\n");
        return out.toString();
    }

    private static void section(StringBuilder out, String title, String note, List<Group> groups,
                                int total, boolean snapshots) {
        out.append("## ").append(title).append("\n\n");
        if (groups.isEmpty()) {
            out.append("None.\n\n");
            return;
        }
        out.append(note).append("\n\n");
        out.append("| Ending | Depth | Runs | Share |\n|---|---|---|---|\n");
        for (Group group : groups) {
            out.append("| ").append(cell(group.cause())).append(" | ").append(depth(group.depth()))
                    .append(" | ").append(group.runs().size()).append(" | ")
                    .append(String.format(java.util.Locale.ROOT, "%.1f%%",
                            100.0 * group.runs().size() / total))
                    .append(" |\n");
        }
        for (Group group : groups) {
            out.append("\n### ").append(cell(group.cause())).append(" at depth ")
                    .append(depth(group.depth())).append(" — ").append(group.runs().size())
                    .append(group.runs().size() == 1 ? " Run" : " Runs").append("\n\n");
            out.append("| Seed | Class | Turns | Log |").append(snapshots ? " Last waits |" : "")
                    .append("\n|---|---|---|---|").append(snapshots ? "---|" : "").append('\n');
            for (Run run : group.runs()) {
                out.append("| ").append(run.seedCode().isEmpty() ? "—" : cell(run.seedCode()))
                        .append(" | ").append(run.heroClass().isEmpty() ? "—" : cell(run.heroClass()))
                        .append(" | ").append(run.turns() < 0 ? "—" : String.valueOf(run.turns() / 1000))
                        .append(" | [").append(cell(run.runId())).append("](").append(href(run.log()))
                        .append(") |");
                if (snapshots) {
                    out.append(NO_LOG.equals(run.cause()) ? " —" : " [snapshot](" + SNAPSHOTS + "/"
                            + href(snapshotName(run)) + ")").append(" |");
                }
                out.append('\n');
            }
        }
        out.append('\n');
    }

    /**
     * Text safe in a Markdown table cell or a line: the characters that end a cell, open a code
     * span, a link, emphasis or HTML are backslash-escaped, and a line break becomes a space.
     */
    static String cell(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '\r') {
                out.append(' ');
            } else {
                if ("\\`|[]*_<>#".indexOf(c) >= 0) {
                    out.append('\\');
                }
                out.append(c);
            }
        }
        return out.toString();
    }

    /** A link target: anything outside a plain path's characters percent-encoded, as UTF-8. */
    static String href(String path) {
        StringBuilder out = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "-._~/".indexOf(c) >= 0) {
                out.append((char) c);
            } else {
                out.append('%').append(String.format(java.util.Locale.ROOT, "%02X", c));
            }
        }
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

    /**
     * The last {@code count} waits of a Run's log, as a page: what it did before it ended. A log
     * that cannot be read, or has no ending, says so rather than failing.
     */
    public static String snapshot(Path file, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("a snapshot of at least one wait: " + count);
        }
        StringBuilder out = new StringBuilder("# The last waits of ")
                .append(cell(file.getFileName().toString())).append("\n\n");
        RunLogReader.Log read;
        try {
            read = RunLogReader.of(file);
        } catch (RuntimeException unreadable) {
            return out.append("This log could not be read: ").append(cell(String.valueOf(unreadable.getMessage())))
                    .append(".\n").toString();
        }
        if (!read.readable()) {
            out.append("This log could not be read to its end: ").append(cell(read.unreadable()))
                    .append(". The waits before that line follow.\n\n");
        }
        List<RunLog.Wait> waits = read.waits();
        List<RunLog.Wait> last = waits.subList(Math.max(0, waits.size() - count), waits.size());
        RunLog.End end = read.end();
        out.append("The last ").append(last.size()).append(" of ").append(waits.size())
                .append(" waits, from the Run's own log");
        if (end != null) {
            out.append("; it ended ").append(cell(end.outcome().cause())).append(" at depth ")
                    .append(end.outcome().depth()).append(" after ").append(end.outcome().turns() / 1000)
                    .append(" turns");
            if (!end.detail().isEmpty()) {
                out.append(" (").append(cell(end.detail())).append(')');
            }
        } else {
            out.append("; it has no ending");
        }
        out.append(".\n\n| Wait | Turn | Depth | Actor | Action |\n|---|---|---|---|---|\n");
        for (RunLog.Wait wait : last) {
            out.append("| ").append(wait.k()).append(" | ").append(wait.turn() / 1000).append(" | ")
                    .append(wait.depth()).append(" | ").append(cell(wait.actor())).append(" | ")
                    .append(cell(String.valueOf(wait.action()))).append(" |\n");
        }
        return out.toString();
    }

    /**
     * Writes {@code gallery.md} in {@code folder}, and with {@code snapshots} above zero the last
     * that many waits of every Run with a log into {@code snapshots/}.
     *
     * <p>In an order that cannot leave the page linking a snapshot that is not there: the new
     * snapshots are written first, then the page (to a temporary file, moved into place), and only
     * then are the snapshots this page does not link deleted -- the regular {@code .md} files in
     * {@code snapshots/}, which are the gallery's own. Written with no snapshots, every one goes.
     */
    public static void write(Path folder, int snapshots) {
        if (snapshots < 0) {
            throw new IllegalArgumentException("a snapshot count is not negative: " + snapshots);
        }
        List<Group> groups = of(folder);
        Path into = folder.resolve(SNAPSHOTS);
        Set<String> wrote = new HashSet<>();
        try {
            if (snapshots > 0) {
                Files.createDirectories(into);
                for (Group group : groups) {
                    for (Run run : group.runs()) {
                        if (!NO_LOG.equals(run.cause())) {
                            String name = snapshotName(run);
                            Files.writeString(into.resolve(name),
                                    snapshot(folder.resolve(run.log()), snapshots), StandardCharsets.UTF_8);
                            wrote.add(name);
                        }
                    }
                }
            }
            Path page = folder.resolve(FILE);
            Path temporary = folder.resolve(FILE + ".tmp");
            Files.writeString(temporary, page(folder, groups, snapshots > 0), StandardCharsets.UTF_8);
            Files.move(temporary, page, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (Files.isDirectory(into)) {
                try (var present = Files.list(into)) {
                    for (Path old : present.toList()) {
                        String name = old.getFileName().toString();
                        if (Files.isRegularFile(old) && name.endsWith(".md") && !wrote.contains(name)) {
                            Files.delete(old);
                        }
                    }
                }
            }
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
     * comparison folder, one per side, and the per-Brain comparison view
     * ({@link GalleryComparison}) beside them. {@code --compare <baseline> <candidate> <page>}
     * writes the comparison view for any two folders of Run logs.
     */
    public static void main(String[] args) {
        if (args.length == 4 && args[0].equals("--compare")) {
            Path into = Path.of(args[3]).toAbsolutePath().normalize();
            GalleryComparison.write(Path.of(args[1]).toAbsolutePath().normalize(),
                    Path.of(args[2]).toAbsolutePath().normalize(), into);
            System.out.println("the comparison view wrote " + into);
            return;
        }
        if (args.length != 1 && !(args.length == 3 && args[1].equals("--snapshots"))) {
            throw new IllegalArgumentException("usage: Gallery <folder> [--snapshots N], or Gallery --compare"
                    + " <baseline> <candidate> <page>, not " + Arrays.toString(args));
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
        if (folders.size() == 2) {
            Path into = folder.resolve(GalleryComparison.FILE);
            GalleryComparison.write(folders.get(1), folders.get(0), into);
            System.out.println("the comparison view wrote " + into);
        }
        System.out.println("in " + (System.nanoTime() - began) / 1_000_000L + " ms");
    }
}
