package org.shatterfish.rig;

import org.shatterfish.api.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * The nightly smoke run's record and its one results page (story 3.11, ADR-0002).
 *
 * <p>Every night the workflow plays the {@code smoke} set under the standing Registration
 * {@code H-0001-nightly-smoke}, and this class turns what the Rig wrote into two things: one line
 * of {@code results/nightly/history.jsonl}, append-only like the ledger, and the page
 * {@code docs/results/nightly.md}, which is a pure function of that history. One page rather than
 * one per night, because the site refuses a page its navigation does not list, and a history
 * rather than a page edited in place, because a page a bot rewrites from itself drifts and a page
 * generated from lines it only appends to does not.
 *
 * <p><b>What passing means.</b> H-0001 is a baseline: it tests nothing between two Brains, so no
 * night is ever accepted, rejected or undecided -- every night is a direction check, and the page
 * says so on every line. A night passes when the Rig ran ranked under H-0001, played every triple
 * of the set, and every Run it dispatched finished: the Registration's claim is that the random
 * Brain finishes every Run of the smoke set, and the point of checking it nightly is that a change
 * which breaks the Harness is seen the night it lands. Anything else fails, with the reason in the
 * first column, so the failure is visible without opening a log.
 *
 * <p>Nothing here touches the network: it reads two files the Rig wrote and writes two files.
 */
public final class Nightly {

    /** The standing Registration the nightly job runs under. */
    public static final String REGISTRATION = "H-0001-nightly-smoke";

    /** The history, one line per night, under the repository root. */
    public static final String HISTORY = "results/nightly/history.jsonl";

    /** The page, under the repository root. */
    public static final String PAGE = "docs/results/nightly.md";

    /** The command the workflow plays, which is also the command that reproduces a night. */
    public static final String COMMAND = "./gradlew :rig:run --args=\"--brain random --seeds smoke"
            + " --parallel 4 --out build/nightly --registration " + REGISTRATION + "\"";

    /** The keys a history line carries. */
    static final List<String> KEYS = List.of("causes", "commit", "date", "finished", "incomplete",
            "ms", "pass", "registration", "run", "started", "unaccounted", "why");

    /**
     * One night.
     *
     * @param run    the Actions run that played it, as a URL, or empty when played by hand
     * @param causes how its Runs ended, {@code CAUSE=n} joined by spaces in name order
     * @param why    why it failed, or empty when it passed
     */
    public record Night(String date, String commit, String registration, int started, int finished,
                        int incomplete, int unaccounted, long ms, String causes, boolean pass,
                        String why, String run) {

        /** The night as one canonical history line. */
        public String line() {
            JsonWriter out = new JsonWriter().beginObject();
            out.key("causes").value(causes);
            out.key("commit").value(commit);
            out.key("date").value(date);
            out.key("finished").value(finished);
            out.key("incomplete").value(incomplete);
            out.key("ms").value(ms);
            out.key("pass").value(pass);
            out.key("registration").value(registration);
            out.key("run").value(run);
            out.key("started").value(started);
            out.key("unaccounted").value(unaccounted);
            out.key("why").value(why);
            return out.endObject().toJson();
        }

        static Night of(String line) {
            for (String key : KEYS) {
                if (LogHeader.value(line, key) == null) {
                    throw new IllegalArgumentException("a nightly history line without \"" + key
                            + "\": " + line);
                }
            }
            return new Night(LogHeader.string(line, "date"), LogHeader.string(line, "commit"),
                    LogHeader.string(line, "registration"),
                    Integer.parseInt(LogHeader.value(line, "started")),
                    Integer.parseInt(LogHeader.value(line, "finished")),
                    Integer.parseInt(LogHeader.value(line, "incomplete")),
                    Integer.parseInt(LogHeader.value(line, "unaccounted")),
                    Long.parseLong(LogHeader.value(line, "ms")), LogHeader.string(line, "causes"),
                    "true".equals(LogHeader.value(line, "pass")), LogHeader.string(line, "why"),
                    LogHeader.string(line, "run"));
        }
    }

    private Nightly() {
    }

    /**
     * The night the Rig wrote into {@code out}, judged against the smoke set's size.
     *
     * <p>A folder with no summary is a night the Rig did not finish -- refused, or stopped -- and
     * is a failure with that reason, not an exception: the page has to say that a night failed,
     * and a night that could not be recorded would say nothing.
     */
    public static Night night(Path out, int setSize, String date, String commit, String run) {
        return night(out, setSize, date, commit, run, "success");
    }

    /** The counts a summary must state; a summary without one is not read as zero. */
    static final List<String> COUNTS = List.of("runsStarted", "runsFinished", "runsIncomplete",
            "runsUnaccounted");

    /**
     * As {@link #night(Path, int, String, String, String)}, with the outcome of the step that
     * played it: a night whose play step did not succeed fails, whatever its summary says, because
     * a Rig that exited with an error after writing a clean summary is not a Rig that worked.
     */
    public static Night night(Path out, int setSize, String date, String commit, String run,
                              String played) {
        Path summaryFile = out.resolve(RunIndex.SUMMARY);
        if (!Files.isRegularFile(summaryFile)) {
            return new Night(date, commit, "", 0, 0, 0, 0, 0, "", false,
                    "the Rig wrote no summary: it was refused or stopped before the end", run);
        }
        String registration;
        String causes;
        int started;
        int finished;
        int incomplete;
        int unaccounted;
        long ms;
        String set;
        String brain;
        try {
            String summary = read(summaryFile).strip();
            for (String key : COUNTS) {
                if (LogHeader.value(summary, key) == null) {
                    throw new IllegalArgumentException("it states no " + key);
                }
            }
            registration = orEmpty(LogHeader.string(summary, "registration"));
            started = number(summary, "runsStarted");
            finished = number(summary, "runsFinished");
            incomplete = number(summary, "runsIncomplete");
            unaccounted = number(summary, "runsUnaccounted");
            ms = Long.parseLong(orZero(LogHeader.value(summary, "ms")));
            set = orEmpty(LogHeader.string(summary, "seedSet"));
            brain = orEmpty(LogHeader.string(summary, "brain"));
            causes = causes(out.resolve(RunIndex.RUNS));
        } catch (RuntimeException unreadable) {
            // A summary the Rig half wrote is a night that went wrong, and the page has to say so
            // rather than the recording step dying with a parse error nobody reads.
            return new Night(date, commit, "", 0, 0, 0, 0, 0, "", false,
                    "the Rig's summary could not be read: " + message(unreadable), run);
        }
        String why;
        if (!registration.startsWith(REGISTRATION + "@")) {
            why = "not ranked under " + REGISTRATION + " (registration: \"" + registration + "\")";
        } else if (!SeedSets.SMOKE.equals(set) || !Brains.RANDOM.equals(brain)) {
            why = "played " + brain + " on " + set + ", not random on smoke";
        } else if (started != setSize) {
            why = started + " Runs started of the " + setSize + " the smoke set holds";
        } else if (incomplete > 0 || unaccounted > 0 || finished != started) {
            why = finished + " of " + started + " Runs finished (" + incomplete + " incomplete, "
                    + unaccounted + " unaccounted)";
        } else if (!"success".equals(played)) {
            why = "the play step ended " + played + " although its summary looks whole";
        } else {
            why = "";
        }
        return new Night(date, commit, registration, started, finished, incomplete, unaccounted, ms,
                causes, why.isEmpty(), why, run);
    }

    /** How the Runs in an index ended, {@code CAUSE=n} in name order; empty when there is none. */
    static String causes(Path index) {
        if (!Files.isRegularFile(index)) {
            return "";
        }
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (String line : read(index).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String cause = LogHeader.string(line, "cause");
            counts.merge(cause == null || cause.isEmpty() ? "NONE" : cause, 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((cause, count) -> parts.add(cause + "=" + count));
        return String.join(" ", parts);
    }

    /** Every night in a history file, oldest first; empty when there is no file yet. */
    public static List<Night> history(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        return Arrays.stream(read(file).split("\n")).filter(line -> !line.isBlank())
                .map(Night::of).toList();
    }

    /** The one-line status of a night, for the job summary and the pull request. */
    public static String status(Night night) {
        return (night.pass() ? "PASS" : "FAIL") + " -- nightly smoke " + night.date() + " (a direction"
                + " check under " + REGISTRATION + ", never an acceptance): "
                + (night.pass() ? night.finished() + " of " + night.started() + " Runs finished"
                : oneLine(night.why()));
    }

    /** An exception's message, or its class when it has none. */
    static String message(Throwable failure) {
        String said = failure.getMessage();
        return said == null || said.isBlank() ? failure.getClass().getName() : said;
    }

    /**
     * Text on one line with no backticks: a status line is a job summary heading, a pull request
     * title and a table cell, and a newline or a backtick in an exception's message breaks all three.
     */
    static String oneLine(String text) {
        return text.replace("\r\n", " / ").replace('\n', ' ').replace('\r', ' ').replace('`', '\'');
    }

    /** The page for a history: the latest night first, then every night, newest first. */
    public static String page(List<Night> nights) {
        StringBuilder out = new StringBuilder();
        out.append("<!-- Generated by ./gradlew :rig:nightly from ").append(HISTORY)
                .append(". Do not edit by hand: NightlyTest regenerates it. -->\n\n");
        out.append("# Nightly smoke\n\n");
        out.append("Story 3.11, [#100](https://github.com/watchthelight/shatterfish/issues/100). Every"
                + " night the `nightly` workflow plays the `smoke` set under the standing Registration"
                + " `").append(REGISTRATION).append("` and updates one results pull request on the"
                + " branch `rig/nightly`. **Every night is a direction check, never an acceptance**:"
                + " H-0001 fixes a baseline and tests nothing between two Brains, so no night can"
                + " accept, reject or be undecided. A night passes when every one of the set's Runs"
                + " finished under the Registration; anything else fails, and says why below.\n\n");
        out.append("```sh\n").append(COMMAND).append("\n```\n\n");
        if (nights.isEmpty()) {
            out.append("No night has been recorded yet.\n");
            return out.toString();
        }
        Night latest = nights.get(nights.size() - 1);
        out.append("**Latest:** ").append(status(latest)).append(".\n\n");
        out.append("| Night | Status | Why | Runs finished | Endings | Time | Commit | Registration |"
                + " Run |\n");
        out.append("|---|---|---|---|---|---|---|---|---|\n");
        for (int i = nights.size() - 1; i >= 0; i--) {
            Night n = nights.get(i);
            out.append(String.format(Locale.ROOT, "| %s | %s | %s | %d of %d | %s | %,d ms | `%s` | `%s` | %s |%n",
                    n.date(), n.pass() ? "PASS" : "**FAIL**", n.why().isEmpty() ? "--" : cell(n.why()),
                    n.finished(), n.started(), n.causes().isEmpty() ? "--" : n.causes(), n.ms(),
                    n.commit().length() > 9 ? n.commit().substring(0, 9) : n.commit(),
                    n.registration().isEmpty() ? "none" : n.registration(),
                    n.run().isEmpty() ? "by hand" : "[log](" + n.run() + ")"));
        }
        return out.toString().replace("\r\n", "\n");
    }

    static String cell(String text) {
        return oneLine(text).replace("|", "\\|");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    private static int number(String object, String key) {
        return Integer.parseInt(orZero(LogHeader.value(object, key)));
    }

    private static String orZero(String value) {
        return value == null ? "0" : value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * {@code <root> night <out> <date> <commit> [<play outcome> [<run url>]]} judges the night in {@code out} and
     * writes its history line to {@code <out>/night.jsonl} and its status to
     * {@code <out>/status.md}, answering 1 when it failed. {@code <root> page} writes the page from
     * the history.
     */
    public static void main(String[] args) throws IOException {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * What {@link #main} does, answering its exit code instead of exiting, so that a test can run
     * the task end to end. {@code <root> night <out> <date> <commit> [<play outcome> [<run url>]]}.
     */
    static int run(String[] args) throws IOException {
        if (args.length == 2 && args[1].equals("page")) {
            Path root = Seeds.checkout(args[0]);
            Files.writeString(root.resolve(PAGE), page(history(root.resolve(HISTORY))),
                    StandardCharsets.UTF_8);
            return 0;
        }
        if (args.length < 5 || args.length > 7 || !args[1].equals("night")) {
            throw new IllegalArgumentException("usage: Nightly <root> page | <root> night <out> <date>"
                    + " <commit> [<play outcome> [<run url>]], not " + Arrays.toString(args));
        }
        Path root = Seeds.checkout(args[0]);
        Path out = root.resolve(args[2]);
        int size = SeedSets.load(root, SeedSets.SMOKE).set().entries().size();
        Night night = night(out, size, args[3], args[4], args.length == 7 ? args[6] : "",
                args.length >= 6 ? args[5] : "success");
        Files.createDirectories(out);
        Files.writeString(out.resolve("night.jsonl"), night.line() + "\n", StandardCharsets.UTF_8);
        Files.writeString(out.resolve("status.md"), status(night) + "\n", StandardCharsets.UTF_8);
        System.out.println(status(night));
        return night.pass() ? 0 : 1;
    }
}
