package org.shatterfish.rig;

import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Every log in a Rig folder, checked against its own bytes and against the index (story 3.4).
 *
 * <p><b>It plays nothing.</b> A chain is recomputed by hashing text, so a folder of five hundred
 * Runs is checked in the time it takes to read five hundred files — which is what makes this worth
 * running on every folder rather than on a sample. Replaying is the other half and costs what the
 * Runs cost: it is {@code --replay}, which takes one log and not a folder, because a Replay is a
 * Run and AD-6 gives a Run its own process.
 *
 * <p><b>It checks three things a folder can be wrong about,</b> and they fail differently:
 *
 * <ul>
 *   <li>the <em>chain</em>, recomputed from each file's own bytes. A break means the file was
 *       edited after it was written, and it names the line;</li>
 *   <li>the <em>index</em>, whose stated chain for each Run must be the one the file gives. The
 *       index is what a Results page cites, and a folder where the two disagree is one where the
 *       published number is not about the log beside it;</li>
 *   <li>the <em>shape</em>: a log begins with a header, holds one, and a finished Run ends with an
 *       {@code end} record. Every prefix of a valid log is a valid log, so this is the question
 *       the chain cannot answer.</li>
 * </ul>
 *
 * <p>It reports rather than throws. A folder with one bad log in five hundred is a folder with one
 * bad log, and a checker that stopped at the first would make the other four hundred and ninety
 * nine unknown rather than good.
 */
public final class Verify {

    /** What one log was found to be. */
    public record Checked(String runId, String log, boolean verified, int lines, int brokenLine,
                          boolean complete, String chain, String why) {

        /** Whether this log is intact, whole, and the one the index says it is. */
        public boolean ok() {
            return verified && why.isEmpty();
        }
    }

    /** What a folder was found to be. */
    public record Report(Path folder, List<Checked> checked, String why) {

        public int ok() {
            return (int) checked.stream().filter(Checked::ok).count();
        }

        public int broken() {
            return checked.size() - ok();
        }

        /** Whether every log in the folder verified, and the folder itself could be read. */
        public boolean ok(boolean incompleteIsFine) {
            return why.isEmpty() && checked.stream()
                    .allMatch(one -> one.ok() && (incompleteIsFine || one.complete()));
        }

        /** The lines a person reads, one per log that is wrong, and a count of the rest. */
        public String text() {
            StringBuilder out = new StringBuilder();
            for (Checked one : checked) {
                if (!one.ok()) {
                    out.append("  ").append(one.log()).append(": ").append(one.why()).append('\n');
                }
            }
            out.append(ok()).append(" of ").append(checked.size()).append(" logs in ")
                    .append(folder).append(" verify against their own bytes");
            long incomplete = checked.stream().filter(one -> one.ok() && !one.complete()).count();
            if (incomplete > 0) {
                out.append(" (").append(incomplete).append(" of them incomplete, which is a Run"
                        + " that was killed rather than a file that was changed)");
            }
            if (!why.isEmpty()) {
                out.append("\n").append(why);
            }
            return out.toString();
        }
    }

    private Verify() {
    }

    /** Checks every log the folder's index names, and every log it holds that the index does not. */
    public static Report of(Path folder) {
        if (!Files.isDirectory(folder)) {
            return new Report(folder, List.of(), "there is no folder at " + folder);
        }
        Map<String, String> stated = indexChains(folder);
        List<Checked> checked = new ArrayList<>();
        for (Path file : logs(folder)) {
            checked.add(check(folder, file, stated));
        }
        String why = "";
        if (checked.isEmpty()) {
            why = "the folder holds no log, so there is nothing to verify";
        }
        // A Run the index names and no file answers for. The index is written as Runs are
        // dispatched, so this is a child that died before its first line reached the disk -- which
        // the index already records as incomplete, and which this says out loud rather than
        // silently checking nothing.
        for (String runId : stated.keySet()) {
            if (checked.stream().noneMatch(one -> runId.equals(one.runId()))) {
                checked.add(new Checked(runId, runId + ".jsonl", false, 0, 0, false, "",
                        "the index names this Run and the folder holds no log for it"));
            }
        }
        return new Report(folder, List.copyOf(checked), why);
    }

    private static Checked check(Path folder, Path file, Map<String, String> stated) {
        String name = folder.relativize(file).toString().replace('\\', '/');
        RunLogVerifier.Verified verified;
        try {
            verified = RunLogVerifier.of(file);
        } catch (RuntimeException unreadable) {
            return new Checked("", name, false, 0, 0, false, "", "unreadable: " + unreadable.getMessage());
        }
        LogHeader.Read read = LogHeader.of(file);
        String runId = read.runId();
        if (!verified.ok()) {
            return new Checked(runId, name, false, verified.lines(), verified.brokenLine(),
                    read.complete(), "", verified.why());
        }
        if (!read.readable()) {
            return new Checked(runId, name, true, verified.lines(), 0, false, verified.chain(),
                    "the chain holds but the records do not read: " + read.unreadable());
        }
        // The chain the index published for this Run, against the chain its file gives. This is the
        // check that makes the index evidence rather than a copy: a folder can be handed on, and
        // the two halves have to still be about each other.
        String said = stated.get(runId);
        String why = "";
        if (said != null && !said.isEmpty() && !said.equals(verified.chain())) {
            why = "the index says this Run chained to " + said + " and its log gives "
                    + verified.chain();
        }
        return new Checked(runId, name, true, verified.lines(), 0, read.complete(), verified.chain(),
                why);
    }

    /** The chain the run index states for each Run, or an empty map when there is no index. */
    private static Map<String, String> indexChains(Path folder) {
        Map<String, String> chains = new TreeMap<>();
        Path index = folder.resolve(RunIndex.RUNS);
        if (!Files.isRegularFile(index)) {
            return chains;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(index, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the run index " + index + " could not be read", e);
        }
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                chains.put(LogHeader.string(line, "runId"), LogHeader.string(line, "chain"));
            } catch (RuntimeException unreadable) {
                // One unreadable index line does not make the logs unreadable. It shows up as a
                // Run whose stated chain is simply not there to compare, which is weaker than a
                // mismatch and is reported as nothing rather than as agreement.
                continue;
            }
        }
        return chains;
    }

    /** Every {@code .jsonl} in the folder, sorted, which is every Run log and nothing else. */
    static List<Path> logs(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".jsonl"))
                    .filter(file -> !file.getFileName().toString().equals(RunIndex.RUNS))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("the folder " + folder + " could not be listed", e);
        }
    }
}
