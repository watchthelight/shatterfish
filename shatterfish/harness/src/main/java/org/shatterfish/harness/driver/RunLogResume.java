package org.shatterfish.harness.driver;

import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where a suspended Run's log is picked up again (story 5.14, ADR-0013, ADR-0011's {@code boundary}
 * record): the one place a Run log is read in order to be written to.
 *
 * <p>A Run that was saved and quit left its log ending at a {@code boundary} record, carrying the wait
 * index, the salt and the chain as they stood. A resume through the launcher goes on with <em>that</em>
 * log: it does not begin a second one, so the file is one Run's whole account. This class reads the file
 * and refuses anything it cannot vouch for, because a log continued from a wrong place is a log that
 * lies. In order, it refuses a file that:
 * <ul>
 * <li>does not verify: a line whose chain is not the one its bytes give, or an unreadable line;</li>
 * <li>ends in a half-written line, or does not end at a boundary (a finished Run, a killed one);</li>
 * <li>has a boundary whose {@code chainAt} is not the chain of the line before it, which is how a
 * boundary pasted onto another log shows.</li>
 * </ul>
 * What the next record chains onto is the boundary line's own chain, so the continued file verifies end
 * to end as one chain ({@code RunLogVerifier}).
 *
 * <p>{@link #read} only reads, for the launcher to learn the tuple before it claims anything;
 * {@link #open} also reopens the file for appending.
 */
public final class RunLogResume {

    /** A suspended log as read: its header, its closing boundary, and the chain the next record follows. */
    public record Suspended(Path file, RunLog.Header header, RunLog.Boundary boundary, String chain, long records) {
    }

    /** A suspended log reopened for appending. */
    public record Resumed(RunLogWriter writer, Suspended was) {
    }

    private RunLogResume() {
    }

    /**
     * Reads {@code file} and checks it is a log that was suspended.
     *
     * @throws IllegalStateException saying which of the checks above failed
     */
    public static Suspended read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalStateException("the Run log " + file + " is not there, so there is nothing to continue");
        }
        RunLogVerifier.Verified verified = RunLogVerifier.of(file);
        if (!verified.ok() || !verified.complete()) {
            throw new IllegalStateException("the Run log " + file + " does not verify (" + verified.why() + "), so it is"
                    + " not continued");
        }
        RunLogReader.Log log = RunLogReader.of(file);
        if (!log.readable() || !log.complete() || log.records().isEmpty()) {
            throw new IllegalStateException("the Run log " + file + " cannot be read to its end: " + log.unreadable()
                    + (log.complete() ? "" : " (a half-written last line)"));
        }
        List<RunLog> records = log.records();
        if (!(records.get(records.size() - 1) instanceof RunLog.Boundary boundary)) {
            throw new IllegalStateException("the Run log " + file + " does not end at a boundary: it ended, or was killed,"
                    + " and a Run is resumed only from a save and quit");
        }
        String before = "";
        for (int i = 0; i < records.size() - 1; i++) {
            before = RunLogJson.chain(before, records.get(i));
        }
        if (!before.equals(boundary.chainAt())) {
            throw new IllegalStateException("the boundary at the end of " + file + " names a chain that is not the log's own");
        }
        String after = RunLogJson.chain(before, boundary);
        if (!after.equals(verified.chain())) {
            throw new IllegalStateException("the chain of " + file + " does not end where its boundary says");
        }
        return new Suspended(file, log.header(), boundary, after, records.size());
    }

    /** Reads {@code file}, as {@link #read}, and reopens it so the next record chains onto the boundary. */
    public static Resumed open(Path file) {
        Suspended was = read(file);
        return new Resumed(RunLogWriter.continuing(file, was.chain(), was.records()), was);
    }
}
