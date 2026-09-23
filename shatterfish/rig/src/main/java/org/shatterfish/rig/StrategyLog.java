package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * one Action in six.
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
        for (RunLog.Wait wait : log.waits()) {
            text.append(line(wait)).append('\n');
        }
        RunLog.End end = log.end();
        text.append(end == null ? "# no end: the Run was cut short"
                : "# end " + end.outcome().cause() + ", depth " + end.outcome().depth()).append('\n');
        return text.toString();
    }

    /** One wait, as a line. */
    static String line(RunLog.Wait wait) {
        StringBuilder line = new StringBuilder();
        line.append("k=").append(wait.k()).append(" d=").append(wait.depth());
        if (!RunLog.BOT.equals(wait.actor())) {
            line.append(' ').append(wait.actor());
        }
        RunLog.Decision decision = wait.decision();
        if (decision == null) {
            line.append(" | ").append(wait.action());
        } else {
            line.append(" | ").append(decision.policy()).append(" | ").append(decision.goal())
                    .append(" | ").append(choice(decision.chosen()));
            for (RunLog.Choice alternative : decision.alternatives()) {
                line.append(" | alt ").append(choice(alternative));
            }
            line.append(" | flags ").append(decision.flags().isEmpty() ? "-" : String.join(",", decision.flags()));
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
        return choice.action() + " " + java.math.BigDecimal.valueOf(choice.score(), 4).toPlainString()
                + (choice.why().isEmpty() ? "" : " " + choice.why());
    }

    /**
     * {@code ./gradlew :rig:strategy --args="<log.jsonl> [...]"} writes {@code <run-id>.strategy.txt}
     * beside each log named, or beside every log in a folder named.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: StrategyLog <log.jsonl or folder> [...]");
        }
        for (String arg : args) {
            Path path = Path.of(arg).toAbsolutePath().normalize();
            List<Path> logs = Files.isDirectory(path) ? Verify.logs(path) : List.of(path);
            for (Path log : logs) {
                Path out = written(log);
                System.out.println("the strategy log wrote " + out);
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
