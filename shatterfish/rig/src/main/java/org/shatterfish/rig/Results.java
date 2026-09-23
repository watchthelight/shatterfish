package org.shatterfish.rig;

import org.shatterfish.api.JsonWriter;
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
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Results pages (story 3.10, FR-25): a published number carries everything needed to check it.
 *
 * <p>Two steps, like the calibration's. {@link #extract} reads a folder the Rig wrote -- a
 * Baseline's, or a comparison's with its two sides -- through the Harness's reader, and writes a
 * small data folder to commit: each side's run index and summary, the comparison's own file, one
 * outcome line per Run taken from that Run's log, and a {@code results.json} holding what the logs'
 * headers, the Registration and the ledger say about the invocation. {@link #page} renders the
 * Markdown from that data folder alone, so a page is regenerated from what is committed and
 * {@code ResultsTest} fails when the two drift.
 *
 * <p>The Run logs themselves are not in the data folder: a side of {@code standard} is some 37 MB.
 * {@code results.json} has a {@code logs} field for where they are published, and the page says
 * plainly when that is nowhere yet.
 */
public final class Results {

    /** Where the data folders live, under the repository root. */
    public static final String FOLDER = "results";

    /** Where the generated pages go, under the repository root. */
    public static final String PAGES = "docs/results";

    public static final String COMMAND = "./gradlew :rig:results";

    /** The file in a data folder that describes the invocation. */
    public static final String DESCRIPTION = "results.json";

    /** One Run's outcome per line, per side. */
    public static final String OUTCOMES = "outcomes.jsonl";

    /** The turns at which the survival curve is read, in whole turns. */
    static final int[] SURVIVAL = {100, 250, 500, 750, 1000, 1250, 1500, 2000, 5000, 10000, 20000};

    private Results() {
    }

    // ------------------------------------------------------------------------------- extract

    /** What a slug may be: a file name, never a path. */
    static final String SLUG = "[A-Za-z0-9][A-Za-z0-9._-]*";

    /**
     * Writes the data folder for {@code runs} into {@code into}. A comparison is recognised by its
     * {@code comparison.json}; its two sides are read from their own folders.
     *
     * <p>Everything is read and checked before anything is written, so a refusal leaves an existing
     * data folder as it was. Refused: an index line without a log or a seed, a log outside its
     * folder, Runs that disagree on the tag, the invoking commit, the Registration or the cap, a Run
     * with the Oracle on, a comparison whose sides name different Seed sets or whose
     * {@code comparison.json} names another Registration than its Runs, a malformed stamp, and a
     * Registration git cannot say was committed.
     */
    public static void extract(Path runs, Path root, Path into) {
        boolean comparison = Files.isRegularFile(runs.resolve(Comparison.FILE));
        List<String> sides = comparison ? List.of(Comparison.CANDIDATE, Comparison.BASELINE) : List.of("");
        List<RunLog.Header> headers = new ArrayList<>();
        int[] unreadable = new int[1];
        java.util.Map<String, String> outcomes = new java.util.LinkedHashMap<>();
        for (String side : sides) {
            Path from = side.isEmpty() ? runs : runs.resolve(side);
            outcomes.put(side, outcomes(from, headers, unreadable));
        }
        String description = description(runs, root, comparison, headers, unreadable[0]);
        try {
            Files.createDirectories(into);
            for (String side : sides) {
                Path from = side.isEmpty() ? runs : runs.resolve(side);
                Path to = side.isEmpty() ? into : into.resolve(side);
                Files.createDirectories(to);
                Files.copy(from.resolve(RunIndex.RUNS), to.resolve(RunIndex.RUNS), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(from.resolve(RunIndex.SUMMARY), to.resolve(RunIndex.SUMMARY), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(to.resolve(OUTCOMES), outcomes.get(side), StandardCharsets.UTF_8);
            }
            if (comparison) {
                Files.copy(runs.resolve(Comparison.FILE), into.resolve(Comparison.FILE),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(into.resolve(DESCRIPTION), description + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the data folder " + into + " could not be written", e);
        }
    }

    /**
     * One outcome line per Run the index lists, read from the Run's own log. A Run whose log is
     * missing or unreadable is written as INCOMPLETE and counted, so the page can say how many of
     * the Runs its checks could not see.
     */
    private static String outcomes(Path folder, List<RunLog.Header> headers, int[] unreadable) {
        Path home = folder.toAbsolutePath().normalize();
        StringBuilder out = new StringBuilder();
        List<String> lines;
        try {
            lines = Files.readAllLines(folder.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the run index in " + folder + " could not be read", e);
        }
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String log = named(line, "log", folder);
            String seed = LogHeader.value(line, "seed");
            if (seed == null || !seed.matches("-?[0-9]+")) {
                throw new IllegalArgumentException("the index in " + folder + " has a line without a seed: " + line);
            }
            Path file = home.resolve(log).normalize();
            if (!home.equals(file.getParent())) {
                throw new IllegalArgumentException("the index in " + folder + " names a log outside it: " + log);
            }
            RunLog.Outcome outcome = Comparison.outcome(file);
            RunLogReader.Log read = Files.isRegularFile(file) ? RunLogReader.of(file) : null;
            if (read != null && read.readable() && read.header() != null) {
                headers.add(read.header());
            } else {
                unreadable[0]++;
            }
            JsonWriter row = new JsonWriter().beginObject();
            row.key("bosses").value(outcome == null ? 0 : outcome.bosses());
            row.key("cause").value(outcome == null ? "INCOMPLETE" : outcome.cause());
            row.key("chain").value(named(line, "chain", folder));
            row.key("class").value(named(line, "class", folder));
            row.key("depth").value(outcome == null ? 0 : outcome.depth());
            row.key("run").value(named(line, "runId", folder));
            row.key("score").value(outcome == null ? 0 : outcome.score());
            row.key("seed").value(Long.parseLong(seed));
            row.key("turns").value(outcome == null ? 0 : outcome.turns());
            row.key("win").value(outcome != null && outcome.win());
            out.append(row.endObject().toJson()).append('\n');
        }
        return out.toString();
    }

    private static String named(String line, String key, Path folder) {
        String value = LogHeader.string(line, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("the index in " + folder + " has a line without \"" + key
                    + "\": " + line);
        }
        return value;
    }

    private static String description(Path runs, Path root, boolean comparison, List<RunLog.Header> headers,
                                      int unreadable) {
        if (headers.isEmpty()) {
            throw new IllegalArgumentException(runs + " holds no readable Run log to describe");
        }
        RunLog.Header first = headers.get(0);
        java.time.Instant earliest = java.time.Instant.parse(first.started());
        int oracle = 0;
        for (RunLog.Header h : headers) {
            if (!h.tag().equals(first.tag()) || !h.commit().equals(first.commit())
                    || !h.registration().equals(first.registration()) || h.cap() != first.cap()) {
                throw new IllegalArgumentException(runs + " mixes invocations: " + h.tag() + " at "
                        + h.commit() + " under '" + h.registration() + "', cap " + h.cap() + ", and "
                        + first.tag() + " at " + first.commit() + " under '" + first.registration()
                        + "', cap " + first.cap());
            }
            oracle += h.oracle() ? 1 : 0;
            java.time.Instant started = java.time.Instant.parse(h.started());
            earliest = started.isBefore(earliest) ? started : earliest;
        }
        if (oracle > 0) {
            throw new IllegalArgumentException(oracle + " Run(s) in " + runs + " had the Oracle on;"
                    + " nothing they produced is published (FR-11)");
        }
        String set = seedSet(comparison ? runs.resolve(Comparison.CANDIDATE) : runs);
        if (comparison && !set.equals(seedSet(runs.resolve(Comparison.BASELINE)))) {
            throw new IllegalArgumentException("the two sides of " + runs + " name different Seed sets");
        }
        String stamp = first.registration();
        if (!stamp.isEmpty() && !stamp.matches(org.shatterfish.api.Registration.ID_PATTERN + "@[0-9a-f]{16}")) {
            throw new IllegalArgumentException("a Registration stamp that is not one: " + stamp);
        }
        String id = stamp.isEmpty() ? "" : stamp.substring(0, stamp.indexOf('@'));
        String cmp = comparison ? read(runs.resolve(Comparison.FILE)) : "";
        if (comparison && !stamp.equals(LogHeader.string(cmp, "registration"))) {
            throw new IllegalArgumentException("comparison.json in " + runs + " names the Registration '"
                    + LogHeader.string(cmp, "registration") + "' and its Runs '" + stamp + "'");
        }
        String registered = "";
        String claim = "";
        List<String> declared = new ArrayList<>();
        int prior = 0;
        int siblings = 0;
        int seedVersion = SeedSets.load(root, set).set().version();
        int alpha = 0;
        int beta = 0;
        String candidate = comparison ? LogHeader.string(cmp, "candidate") : first.brain().name();
        String against = comparison ? LogHeader.string(cmp, "baseline") : "";
        if (!id.isEmpty()) {
            // The commit that added the Registration, not the last one to touch it: a later
            // reformatting would otherwise move the date a hypothesis was fixed.
            String added = git(root, "log", "--diff-filter=A", "--format=%H", "--",
                    Registrations.FOLDER + "/" + id + ".json");
            String[] commits = added.isEmpty() ? new String[0] : added.split("\\s+");
            if (commits.length == 0) {
                throw new IllegalArgumentException("git cannot say when " + id + " was committed; a"
                        + " Registration that was never committed is not one (FR-22)");
            }
            registered = commits[commits.length - 1];
            org.shatterfish.api.Registration registration = Registrations.read(root, id).registration();
            claim = registration.claim();
            seedVersion = registration.seedVersion();
            alpha = registration.alphaPerMil();
            beta = registration.betaPerMil();
            if (registration.brainA() != null) {
                declared.add(brain(registration.brainA()));
            }
            declared.add(brain(registration.brainB()));
            for (Ledger.Entry entry : new Ledger(root.resolve(Registrations.FOLDER)).entries()) {
                if (!java.time.Instant.parse(entry.when()).isBefore(earliest)) {
                    continue;
                }
                if (entry.registration().equals(id)) {
                    prior++;
                } else if (entry.brain().equals(candidate) && entry.seedSet().equals(set)) {
                    siblings++;
                }
            }
        }
        List<String> invoked = new ArrayList<>();
        for (RunLog.Header h : headers) {
            String brain = h.brain().name() + "@" + h.brain().commit() + "/" + h.brain().configHash();
            if (!invoked.contains(brain)) {
                invoked.add(brain);
            }
        }
        JsonWriter out = new JsonWriter().beginObject();
        out.key("alpha_per_mil").value(alpha);
        out.key("beta_per_mil").value(beta);
        out.key("brains").beginArray();
        for (String brain : invoked) {
            out.value(brain);
        }
        out.endArray();
        out.key("cap").value(first.cap());
        out.key("claim").value(claim);
        out.key("command").value("./gradlew :rig:run --args=\"--brain " + candidate
                + (comparison ? " --against " + against : "") + " --seeds " + set + " --cap " + first.cap()
                + " --out <dir>" + (id.isEmpty() ? "" : " --registration " + id) + "\"");
        out.key("commit").value(first.commit());
        out.key("kind").value(comparison ? "comparison" : "baseline");
        out.key("logs").value("");
        out.key("machine").value(first.machine());
        out.key("oracle_runs").value(oracle);
        out.key("prior_attempts").value(prior);
        out.key("prior_sibling_attempts").value(siblings);
        out.key("registered_brains").beginArray();
        for (String brain : declared) {
            out.value(brain);
        }
        out.endArray();
        out.key("registration").value(stamp);
        out.key("registration_commit").value(registered);
        out.key("runs").value(headers.size() + unreadable);
        out.key("seed_set").value(set);
        out.key("seed_version").value(seedVersion);
        out.key("started").value(earliest.toString());
        out.key("tag").value(first.tag());
        out.key("unreadable_runs").value(unreadable);
        return out.endObject().toJson();
    }

    private static String brain(org.shatterfish.api.Registration.Brain brain) {
        return brain.name() + "@" + brain.commit() + "/" + brain.configHash();
    }

    private static String seedSet(Path side) {
        String set = LogHeader.string(read(side.resolve(RunIndex.SUMMARY)), "seedSet");
        if (set == null) {
            throw new IllegalArgumentException("the summary in " + side + " names no Seed set");
        }
        return set;
    }

    /** What git answers, stripped, or empty when it cannot say. */
    private static String git(Path root, String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).start();
            String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            return process.waitFor() == 0 ? said.strip() : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    // ---------------------------------------------------------------------------------- page

    /** One side's outcomes, as the data folder holds them. */
    record Side(String brain, List<String> rows) {

        int count(java.util.function.Predicate<String> test) {
            return (int) rows.stream().filter(test).count();
        }

        List<Long> reachedTurns() {
            List<Long> turns = new ArrayList<>();
            for (String row : rows) {
                if (PairScore.ENDINGS.contains(LogHeader.string(row, "cause"))) {
                    turns.add(Long.parseLong(LogHeader.value(row, "turns")));
                }
            }
            turns.sort(null);
            return turns;
        }
    }

    private static Side side(Path folder, String brain) {
        List<String> rows = new ArrayList<>();
        for (String line : read(folder.resolve(OUTCOMES)).split("\n")) {
            if (!line.isBlank()) {
                rows.add(line);
            }
        }
        return new Side(brain, rows);
    }

    /** The page for the data folder {@code data}, titled {@code title}. */
    public static String page(Path data, String title) {
        String d = read(data.resolve(DESCRIPTION));
        boolean comparison = "comparison".equals(LogHeader.string(d, "kind"));
        String json = comparison ? read(data.resolve(Comparison.FILE)) : null;
        StringBuilder out = new StringBuilder();
        out.append("<!-- Generated by ").append(COMMAND).append(" from ").append(FOLDER).append('/')
                .append(data.getFileName()).append(". Do not edit by hand: ResultsTest regenerates it. -->\n\n");
        out.append("# ").append(title).append("\n\n");
        String commit = LogHeader.string(d, "commit");
        String stamp = LogHeader.string(d, "registration");
        out.append("| | |\n|---|---|\n");
        out.append("| Kind | ").append(comparison ? "comparison" : "baseline (one Brain, no test)").append(" |\n");
        out.append("| Upstream tag | `").append(LogHeader.string(d, "tag")).append("` |\n");
        out.append("| Shatterfish commit | `").append(commit).append("` |\n");
        out.append("| Seed set | `").append(LogHeader.string(d, "seed_set")).append("` version ")
                .append(LogHeader.value(d, "seed_version")).append(" |\n");
        List<String> registered = strings(need(d, "registered_brains"));
        List<String> brains = strings(need(d, "brains"));
        if (brains.isEmpty() || brains.get(0).indexOf('@') < 0) {
            throw new IllegalArgumentException("the data folder " + data + " names no Brain");
        }
        out.append("| Brains, as registered (name@commit/configuration) | ")
                .append(registered.isEmpty() ? "no Registration names them"
                        : String.join(", ", registered.stream().map(b -> "`" + b + "`").toList()))
                .append(" |\n");
        out.append("| Brains, as the Runs' headers name them | ")
                .append(String.join(", ", brains.stream().map(b -> "`" + b + "`").toList()))
                .append(" (the commit a header carries is the invocation's) |\n");
        String registrationCommit = need(d, "registration_commit");
        out.append("| Registration | ").append(stamp.isEmpty() ? "none: not a measurement (FR-22)"
                : "`" + stamp + "`, [committed](https://github.com/watchthelight/shatterfish/blob/"
                        + registrationCommit + "/registrations/" + stamp.substring(0, stamp.indexOf('@'))
                        + ".json) at `" + registrationCommit + "`").append(" |\n");
        if (!stamp.isEmpty()) {
            out.append("| Claim | ").append(cell(need(d, "claim"))).append(" |\n");
            out.append(String.format(Locale.ROOT, "| Error rates | α = %.3f, β = %.3f |\n",
                    Integer.parseInt(need(d, "alpha_per_mil")) / 1000.0,
                    Integer.parseInt(need(d, "beta_per_mil")) / 1000.0));
        }
        out.append("| Prior registered attempts | ").append(need(d, "prior_attempts"))
                .append(" of this Registration, and ").append(need(d, "prior_sibling_attempts"))
                .append(" of other Registrations with the same candidate Brain on the same Seed set,"
                        + " in the ledger before this invocation began |\n");
        out.append("| Turn cap | ").append(need(d, "cap")).append(" |\n");
        out.append("| Oracle | off: ").append(need(d, "oracle_runs")).append(" of ")
                .append(need(d, "runs")).append(" Runs had it on");
        if (!"0".equals(need(d, "unreadable_runs"))) {
            out.append("; ").append(need(d, "unreadable_runs"))
                    .append(" Run(s) had no readable log, so no header to check");
        }
        out.append(" |\n");
        out.append("| Fairness suite | runs in CI on every pull request and every push to `main`;")
                .append(" the checks recorded for this commit, if any, are")
                .append(" [here](https://github.com/watchthelight/shatterfish/commit/").append(commit)
                .append("/checks) |\n");
        out.append("| Machine | ").append(LogHeader.string(d, "machine")).append(" |\n");
        String logs = LogHeader.string(d, "logs");
        out.append("| Run logs | ").append(logs.isEmpty()
                ? "not published; each Run's chain is in the committed index, so a log that is published later can be checked against it"
                : logs).append(" |\n");
        out.append("| Data | [`").append(FOLDER).append('/').append(data.getFileName())
                .append("`](https://github.com/watchthelight/shatterfish/tree/main/").append(FOLDER).append('/')
                .append(data.getFileName()).append(") |\n\n");
        out.append("**Command**, from the repository root at the commit above:\n\n```sh\n")
                .append(need(d, "command")).append("\n```\n\n")
                .append("Salts are drawn when each Run executes (ADR-0007), so the command reproduces the"
                        + " distribution, not these Runs; a Run itself is replayed from its log with"
                        + " `--replay`, and its salt and chain are in the committed index.\n\n");
        if (comparison) {
            out.append(verdict(json));
        }
        List<Side> sides = comparison
                ? List.of(side(data.resolve(Comparison.CANDIDATE), "candidate `" + LogHeader.string(json, "candidate") + "`"),
                        side(data.resolve(Comparison.BASELINE), "baseline `" + LogHeader.string(json, "baseline") + "`"))
                : List.of(side(data, "`" + brains.get(0).substring(0, brains.get(0).indexOf('@')) + "`"));
        out.append("Pages this one depends on: [the statistic](../methodology.md#comparing-two-brains),"
                + " [the Run log and its chain](../methodology.md#the-run-log-and-its-chain),"
                + " [the mix](../methodology.md#the-mix), and [how to read this page](../methodology.md#results-pages).\n\n");
        out.append("## Per-Run aggregates\n\n");
        out.append(aggregates(sides));
        out.append("\n## Survival curve\n\nThe share of the Runs the game ended that were still alive at each turn."
                + " A Run with no ending the game decided is left out (it is censored, not dead) and"
                + " counted above; \"alive at T\" is \"survived at least T turns\".\n\n");
        out.append(survival(sides));
        out.append("\n## Boss staircase\n\n");
        out.append(staircase(sides));
        out.append("\n## By hero class\n\n");
        out.append(byClass(sides));
        return out.toString();
    }

    private static String verdict(String json) {
        StringBuilder out = new StringBuilder("## The test\n\n");
        if (!"true".equals(LogHeader.value(json, "tested"))) {
            return out.append("Not tested: no Registration stated bounds.\n\n").toString();
        }
        String statistic = LogHeader.string(json, "statistic");
        out.append(String.format(Locale.ROOT, "**%s**%s after %s pairs. %s, H0: p0 = %s, H1: p1 = %s,"
                        + " burn-in %s, maximum %s, missing cap %s per mil. Bounds %s and %s, in natural"
                        + " log units (%s). Consumed pairs worse / equal / better: %s / %s / %s; missing"
                        + " pairs over all %d: %s.\n\n",
                LogHeader.string(json, "verdict"),
                "true".equals(LogHeader.value(json, "direction_check")) ? " (a direction check: this set cannot accept)" : "",
                LogHeader.value(json, "stopped_at"), statistic,
                micros(LogHeader.value(json, "p0_micros")), micros(LogHeader.value(json, "p1_micros")),
                LogHeader.value(json, "burn_in"), LogHeader.value(json, "maximum"),
                LogHeader.value(json, "missing_per_mil"),
                micros(LogHeader.value(json, "lower_micros")), micros(LogHeader.value(json, "upper_micros")),
                "GSPRT".equals(statistic) ? "a log-likelihood ratio" : "a log wealth",
                LogHeader.value(json, "consumed_worse"), LogHeader.value(json, "consumed_equal"),
                LogHeader.value(json, "consumed_better"),
                strings(LogHeader.value(json, "pairs")).size(), LogHeader.value(json, "missing")));
        out.append("A pair with a Run the game did not end scores ½ and is counted among the equal"
                + " ones; the result is void if more of the consumed pairs are missing than the cap allows.\n\n");
        if ("0".equals(LogHeader.value(json, "consumed_worse")) && "0".equals(LogHeader.value(json, "consumed_better"))) {
            out.append("**Every consumed pair tied.** A one-sided test rejects two Brains that played alike"
                    + " as readily as a worse one, so a REJECT here says only that; it is no evidence of"
                    + " \"worse\".\n\n");
        }
        out.append("Reported statistic ").append(micros(LogHeader.value(json, "llr_micros")))
                .append("true".equals(LogHeader.value(json, "clamped")) ? ", clamped at the bound" : "")
                .append(". The trace, one value per consumed pair; the first ")
                .append(LogHeader.value(json, "burn_in"))
                .append(" are the burn-in, which cannot stop the test, and their size at the start is the"
                        + " regularization dividing by almost no variance:\n\n```\n");
        List<String> trace = strings(LogHeader.value(json, "trace_micros"));
        for (int i = 0; i < trace.size(); i++) {
            out.append(micros(trace.get(i))).append(i % 10 == 9 || i == trace.size() - 1 ? "\n" : "  ");
        }
        out.append("```\n\n");
        String r = LogHeader.value(json, "turns_correlation_micros");
        out.append("**Pair correlation**: turns survived, over the ").append(LogHeader.value(json, "correlated_pairs"))
                .append(" pairs both Runs of which ended: ").append(r == null ? "not computable" : "r = " + micros(r))
                .append("; pairs that ended alike in every part of the Composite outcome: ")
                .append(LogHeader.value(json, "identical_pairs")).append(".\n\n");
        return out.toString();
    }

    private static String aggregates(List<Side> sides) {
        StringBuilder out = new StringBuilder("| | ");
        sides.forEach(s -> out.append(s.brain()).append(" | "));
        out.append("\n|---|").append("---|".repeat(sides.size())).append('\n');
        row(out, "Runs", sides, s -> String.valueOf(s.rows().size()));
        TreeMap<String, Integer> causes = new TreeMap<>();
        TreeMap<Integer, Integer> depths = new TreeMap<>();
        for (Side s : sides) {
            for (String row : s.rows()) {
                causes.put(LogHeader.string(row, "cause"), 0);
                depths.put(Integer.parseInt(LogHeader.value(row, "depth")), 0);
            }
        }
        for (String cause : causes.keySet()) {
            row(out, "Ended `" + cause + "`", sides,
                    s -> String.valueOf(s.count(r -> cause.equals(LogHeader.string(r, "cause")))));
        }
        for (int depth : depths.keySet()) {
            row(out, "Deepest floor " + depth, sides,
                    s -> String.valueOf(s.count(r -> Integer.parseInt(LogHeader.value(r, "depth")) == depth)));
        }
        row(out, "Won", sides, s -> String.valueOf(s.count(r -> "true".equals(LogHeader.value(r, "win")))));
        row(out, "Turns survived, quartiles (ended Runs)", sides, s -> quartiles(s.reachedTurns()));
        row(out, "Turns survived, mean (ended Runs)", sides, s -> mean(s.reachedTurns()));
        return out.toString();
    }

    private static void row(StringBuilder out, String name, List<Side> sides,
                            java.util.function.Function<Side, String> cell) {
        out.append("| ").append(name).append(" | ");
        sides.forEach(s -> out.append(cell.apply(s)).append(" | "));
        out.append('\n');
    }

    private static String survival(List<Side> sides) {
        StringBuilder out = new StringBuilder("| Turn | ");
        sides.forEach(s -> out.append(s.brain()).append(" | "));
        out.append("\n|---|").append("---|".repeat(sides.size())).append('\n');
        for (int turn : SURVIVAL) {
            out.append("| ").append(turn).append(" | ");
            for (Side s : sides) {
                List<Long> turns = s.reachedTurns();
                long alive = turns.stream().filter(t -> t >= turn * 1000L).count();
                out.append(turns.isEmpty() ? "—" : String.format(Locale.ROOT, "%.1f%%", 100.0 * alive / turns.size()))
                        .append(" | ");
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String staircase(List<Side> sides) {
        int most = 0;
        for (Side s : sides) {
            for (String row : s.rows()) {
                most = Math.max(most, Integer.parseInt(LogHeader.value(row, "bosses")));
            }
        }
        StringBuilder out = new StringBuilder("| Bosses killed, at least | ");
        sides.forEach(s -> out.append(s.brain()).append(" | "));
        out.append("\n|---|").append("---|".repeat(sides.size())).append('\n');
        for (int bosses = 0; bosses <= Math.max(1, most); bosses++) {
            int at = bosses;
            row(out, String.valueOf(at), sides,
                    s -> String.valueOf(s.count(r -> Integer.parseInt(LogHeader.value(r, "bosses")) >= at)));
        }
        return out.toString();
    }

    private static String byClass(List<Side> sides) {
        TreeMap<String, Integer> classes = new TreeMap<>();
        sides.forEach(s -> s.rows().forEach(r -> classes.put(LogHeader.string(r, "class"), 0)));
        StringBuilder out = new StringBuilder("| Class | ");
        sides.forEach(s -> out.append(s.brain()).append(": Runs, median turns, deepest | "));
        out.append("\n|---|").append("---|".repeat(sides.size())).append('\n');
        for (String heroClass : classes.keySet()) {
            out.append("| ").append(heroClass).append(" | ");
            for (Side s : sides) {
                List<String> mine = s.rows().stream().filter(r -> heroClass.equals(LogHeader.string(r, "class"))).toList();
                Side only = new Side(s.brain(), mine);
                List<Long> turns = only.reachedTurns();
                int deepest = mine.stream().mapToInt(r -> Integer.parseInt(LogHeader.value(r, "depth"))).max().orElse(0);
                out.append(mine.size()).append(", ").append(turns.isEmpty() ? "—" : whole(turns.get(turns.size() / 2)))
                        .append(", ").append(deepest).append(" | ");
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String quartiles(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return "—";
        }
        return whole(sorted.get(sorted.size() / 4)) + " / " + whole(sorted.get(sorted.size() / 2)) + " / "
                + whole(sorted.get(sorted.size() * 3 / 4));
    }

    private static String mean(List<Long> turns) {
        return turns.isEmpty() ? "—" : String.format(Locale.ROOT, "%.1f",
                turns.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0);
    }

    /** Thousandths of a turn, as whole turns. */
    private static String whole(long thousandths) {
        return String.valueOf(thousandths / 1000);
    }

    private static String micros(String value) {
        return String.format(Locale.ROOT, "%.3f", Long.parseLong(value) / 1e6);
    }

    /** The elements of a JSON array of scalars, as their raw text (strings unquoted). */
    static List<String> strings(String array) {
        String inner = array.strip();
        inner = inner.substring(1, inner.length() - 1).strip();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (quoted && c == '\\') {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '{' || c == '[')) {
                depth++;
            } else if (!quoted && (c == '}' || c == ']')) {
                depth--;
            } else if (!quoted && depth == 0 && c == ',') {
                out.add(unquote(inner.substring(start, i).strip()));
                start = i + 1;
            }
        }
        out.add(unquote(inner.substring(start).strip()));
        return out;
    }

    private static String unquote(String raw) {
        return raw.startsWith("\"") && raw.endsWith("\"") ? org.shatterfish.harness.log.Json.string(raw) : raw;
    }

    /** A required key of a data folder's description, refused rather than printed as "null". */
    private static String need(String description, String key) {
        String value = LogHeader.value(description, key);
        if (value == null) {
            throw new IllegalArgumentException("a Results description without \"" + key + "\": " + description);
        }
        return value.startsWith("\"") ? LogHeader.string(description, key) : value;
    }

    /** Text for one Markdown table cell. */
    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }

    // ---------------------------------------------------------------------------------- task

    /**
     * {@code <root> extract <runs folder> <slug>} writes {@code results/<slug>/}; {@code <root> page
     * <slug> <title>} writes {@code docs/results/<slug>.md} from it; {@code <root>} alone regenerates
     * every page from its committed data folder.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: Results <root> [extract <runs> <slug> | page <slug> <title>]");
        }
        Path root = Seeds.checkout(args[0]);
        long began = System.nanoTime();
        if (args.length >= 3 && !args[args[1].equals("extract") ? 3 : 2].matches(SLUG)) {
            throw new IllegalArgumentException("a slug is a file name: " + Arrays.toString(args));
        }
        if (args.length == 4 && args[1].equals("extract")) {
            extract(Path.of(args[2]).toAbsolutePath().normalize(), root, root.resolve(FOLDER).resolve(args[3]));
        } else if (args.length >= 4 && args[1].equals("page")) {
            String title = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).strip();
            Path data = root.resolve(FOLDER).resolve(args[2]);
            String rendered = page(data, title);
            Files.writeString(data.resolve("title.txt"), title + "\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve(PAGES).resolve(args[2] + ".md"), rendered, StandardCharsets.UTF_8);
        } else if (args.length == 1) {
            for (Path data : generated(root)) {
                Files.writeString(root.resolve(PAGES).resolve(data.getFileName() + ".md"),
                        page(data, read(data.resolve("title.txt"))), StandardCharsets.UTF_8);
            }
        } else {
            throw new IllegalArgumentException("usage: Results <root> [extract <runs> <slug> | page <slug> <title>], not "
                    + Arrays.toString(args));
        }
        System.out.println("the results task finished in " + (System.nanoTime() - began) / 1_000_000L + " ms");
    }

    /** Every data folder that has a page: those with a description and a title. */
    static List<Path> generated(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        Path folder = root.resolve(FOLDER);
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (var list = Files.list(folder)) {
            for (Path data : list.sorted().toList()) {
                if (Files.isRegularFile(data.resolve(DESCRIPTION)) && !Files.isRegularFile(data.resolve("title.txt"))) {
                    // An untitled data folder would drop out of the drift check without a word.
                    throw new IllegalStateException("the data folder " + data + " has no title.txt;"
                            + " give it a page with " + COMMAND + " --args=\"<root> page <slug> <title>\"");
                }
                if (Files.isRegularFile(data.resolve(DESCRIPTION))) {
                    out.add(data);
                }
            }
        }
        return out;
    }
}
