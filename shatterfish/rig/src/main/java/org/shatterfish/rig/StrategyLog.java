package org.shatterfish.rig;

import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The strategy log (story 4.4, FR-36, NFR-9): a Run log rendered as plain text, one line per Input
 * wait, saying which Policy fired, for what goal, what it chose with its score and reason, the
 * alternatives it weighed, the Safety flags and the highlighted cells.
 *
 * <p>It is rendered from the Run log rather than written beside it, so there is one source of truth
 * and nothing to keep in step: the JSONL is chained and replayable, and this is how a person reads
 * it. A wait whose decider said nothing -- the random agent's, or a human's -- prints its Action alone.
 *
 * <p>Scores print as fractions of one, from the ten-thousandths the log stores: {@code 0.1667} is
 * one Action in six (the fallback rounds 10000/6 to 1667).
 *
 * <p>One wait is always one line. Text the log carries from a decider -- goals, reasons, flags,
 * policy names -- is printed with control characters as spaces and {@code |} as {@code /}, so a
 * reason cannot break a line or fake a column. A wait at which a Prompt was answered names the
 * Prompt's kind, from the log's Prompt record.
 */
public final class StrategyLog {

    /** The file a log's strategy log is written to, beside it: {@code <run-id>.strategy.txt}. */
    public static final String SUFFIX = ".strategy.txt";

    private StrategyLog() {
    }

    /** The strategy log of {@code log}, one line per wait, ending with a newline. */
    public static String render(RunLogReader.Log log) {
        if (!log.readable()) {
            throw new IllegalArgumentException("an unreadable Run log has no strategy to show: " + log.unreadable());
        }
        StringBuilder text = new StringBuilder();
        RunLog.Header header = log.header();
        text.append("# ").append(header.runId()).append('\n');
        text.append("# brain ").append(header.brain().name()).append(", ").append(header.heroClass().name().toLowerCase(java.util.Locale.ROOT))
                .append(", tag ").append(header.tag()).append('\n');
        Map<Long, PromptKind> prompts = new HashMap<>();
        for (RunLog record : log.records()) {
            if (record instanceof RunLog.Prompt prompt) {
                prompts.put(prompt.k(), prompt.kind());
            }
        }
        for (RunLog.Wait wait : log.waits()) {
            text.append(line(wait, prompts.get(wait.k()))).append('\n');
        }
        RunLog.End end = log.end();
        text.append(end == null ? "# no end: the Run was cut short"
                : "# end " + end.outcome().cause() + ", depth " + end.outcome().depth()
                        + (end.detail().isEmpty() ? "" : ": " + end.detail())).append('\n');
        return text.toString();
    }

    /** One wait, as a line, naming the Prompt answered at it when there was one (null when none). */
    static String line(RunLog.Wait wait, PromptKind prompt) {
        StringBuilder line = new StringBuilder();
        line.append("k=").append(wait.k()).append(" d=").append(wait.depth());
        if (!RunLog.BOT.equals(wait.actor())) {
            line.append(' ').append(clean(wait.actor()));
        }
        if (prompt != null) {
            line.append(" prompt ").append(prompt.name());
        }
        RunLog.Decision decision = wait.decision();
        if (decision == null) {
            line.append(" | ").append(clean(wait.action().toString()));
        } else {
            line.append(" | ").append(clean(decision.policy())).append(" | ").append(clean(decision.goal()))
                    .append(" | ").append(choice(decision.chosen()));
            for (RunLog.Choice alternative : decision.alternatives()) {
                line.append(" | alt ").append(choice(alternative));
            }
            line.append(" | flags ").append(decision.flags().isEmpty() ? "-" : clean(String.join(",", decision.flags())));
        }
        if (!wait.highlights().isEmpty()) {
            line.append(" | cells ").append(wait.highlights().stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        if (!wait.applied()) {
            line.append(" | refused");
        }
        return line.toString();
    }

    /** A Choice: the Action, its score as a fraction of one, and its reason. */
    static String choice(RunLog.Choice choice) {
        return clean(choice.action().toString()) + " " + java.math.BigDecimal.valueOf(choice.score(), 4).toPlainString()
                + (choice.why().isEmpty() ? "" : " " + clean(choice.why()));
    }

    /** Text from the log made safe for one column of one line: control characters become spaces, '|' becomes '/'. */
    static String clean(String text) {
        StringBuilder safe = new StringBuilder(text.length());
        text.codePoints().forEach(c -> safe.appendCodePoint(c == '|' ? '/' : Character.isISOControl(c) ? ' ' : c));
        return safe.toString();
    }

    /**
     * {@code ./gradlew :rig:strategy --args="<log.jsonl> [...]"} writes {@code <run-id>.strategy.txt}
     * beside each log named, or beside every log in a folder named; for a log with a human's waits
     * (story 5.9) it also writes and prints {@code <run-id>.human.txt} ({@link HumanPlay}).
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: StrategyLog <log.jsonl or folder> [...]");
        }
        for (String arg : args) {
            Path path = Path.of(arg).toAbsolutePath().normalize();
            List<Path> logs = Files.isDirectory(path) ? Verify.logs(path) : List.of(path);
            for (Path log : logs) {
                // One unreadable log is reported and passed over: the rest of the folder is still
                // worth reading.
                try {
                    System.out.println("the strategy log wrote " + written(log));
                    // A human's Run (story 5.9): the person's play beside the Brain's shadow, one line per
                    // turn, written beside the log and printed, since that is what its reader came for.
                    RunLogReader.Log read = RunLogReader.of(log);
                    if (HumanPlay.human(read)) {
                        String rendered = HumanPlay.render(read);
                        System.out.println("the human play log wrote " + HumanPlay.written(log, rendered));
                        System.out.print(rendered);
                    }
                } catch (RuntimeException unreadable) {
                    System.out.println("the strategy log skipped " + log + ": " + unreadable.getMessage());
                }
            }
        }
    }

    /** Writes the strategy log of {@code log} beside it and returns where. */
    static Path written(Path log) {
        String name = log.getFileName().toString();
        if (!name.endsWith(".jsonl")) {
            throw new IllegalArgumentException("a Run log is a .jsonl file: " + log);
        }
        Path out = log.resolveSibling(name.substring(0, name.length() - ".jsonl".length()) + SUFFIX);
        try {
            Files.writeString(out, render(RunLogReader.of(log)), StandardCharsets.UTF_8);
        } catch (IOException cannot) {
            throw new UncheckedIOException(cannot);
        }
        return out;
    }
}
