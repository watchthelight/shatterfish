package org.shatterfish.harness.driver;

import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Where a Run's log is written (story 3.2, ADR-0011): {@code <run-id>.jsonl}, one record per line,
 * each line complete on disk before the next is built.
 *
 * <p><b>Why a line at a time.</b> A Run that is killed -- a crash, a timeout, a machine going away
 * mid-comparison -- has to leave something a reader can still use, because the Rig counts those
 * Runs rather than losing them (ADR-0012). So the bytes of one record go out and are flushed
 * before the next record exists, and the worst a kill can do is leave a partial last line, which a
 * reader drops and says so. Nothing is buffered across records and nothing is rewritten.
 *
 * <p>The driver owns this file, the way it owns the wait index and the Profile: the Rig owns the
 * Registration, the salts and the Results, and one of them writing the other's is how two accounts
 * of a Run come to disagree.
 *
 * <p>It is a writer and it never reads. A log is a record a Run leaves behind, never an input to
 * one, and the thing that checks a chain is deliberately not this class.
 */
public final class RunLogWriter implements AutoCloseable {

    private final Path file;
    private final OutputStream out;

    /** The chain of the last record written, and the value the next one chains onto. */
    private String chain = "";

    private long records;

    private RunLogWriter(Path file, OutputStream out) {
        this.file = file;
        this.out = out;
    }

    /**
     * Opens the log for the Run {@code header} describes, under {@code folder}.
     *
     * <p>The file must not exist. A comparison plays two Brains on one triple under one salt, and
     * the only part of the run id that then differs is the Brain (AD-14); if two Runs ever agree on
     * every part, the right answer is to stop rather than to let one of them overwrite the other's
     * evidence.
     */
    public static RunLogWriter open(Path folder, RunLog.Header header) {
        if (folder == null || header == null) {
            throw new IllegalArgumentException("a log is written to a folder, for a Run that has a header");
        }
        Path file = folder.resolve(RunLog.fileName(header.runId()));
        try {
            Files.createDirectories(folder);
            OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            RunLogWriter writer = new RunLogWriter(file, out);
            writer.write(header);
            return writer;
        } catch (java.nio.file.FileAlreadyExistsException taken) {
            throw new IllegalStateException(file + " is already written: two Runs of one comparison"
                    + " share every part of a run id but the Brain, so a collision means one Run was"
                    + " about to overwrite the other's evidence", taken);
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be opened", e);
        }
    }

    /** Where this log is, for a Results page and for anyone reading it afterwards. */
    public Path file() {
        return file;
    }

    /** The chain as it stands, which is the Run's Hash chain value once the end record is written. */
    public String chain() {
        return chain;
    }

    /** How many records have been written, the header included. */
    public long records() {
        return records;
    }

    /**
     * Appends one record and flushes it. The chain it carries is over everything written before it,
     * so a record written out of order is a different log rather than a repaired one -- which is
     * why the order is the caller's to get right and this class does not reorder anything.
     */
    public void write(RunLog record) {
        if (record == null) {
            throw new IllegalArgumentException("a log line is a record");
        }
        if (records == 0 && !(record instanceof RunLog.Header)) {
            throw new IllegalStateException("a log begins with its header, not a " + record.t());
        }
        if (records > 0 && record instanceof RunLog.Header) {
            throw new IllegalStateException("a log has one header, and " + file + " already has it");
        }
        String line = RunLogJson.line(chain, record);
        try {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be written", e);
        }
        chain = RunLogJson.chain(chain, record);
        records++;
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be closed", e);
        }
    }
}
