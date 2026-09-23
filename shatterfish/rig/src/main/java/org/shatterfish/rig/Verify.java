package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
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

    /**
     * What one log was found to be.
     *
     * @param indexed whether the run index published a chain for this Run to be held against. A log
     *                nothing published a chain for is not a log that failed the comparison; it is
     *                one the comparison never happened for, and the two must not read alike
     */
    public record Checked(String runId, String log, boolean verified, int lines, int brokenLine,
                          boolean complete, boolean indexed, String chain, String why) {

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

        /** How many logs had no published chain to be held against. */
        public int unindexed() {
            return (int) checked.stream().filter(one -> !one.indexed()).count();
        }

        /** Whether every log in the folder verified, and the folder itself could be read. */
        public boolean ok(boolean incompleteIsFine) {
            return why.isEmpty() && checked.stream()
                    .allMatch(one -> one.ok() && (incompleteIsFine || one.complete()));
        }

        /**
         * The lines a person reads: one per log that is wrong, then what was checked and what was
         * not.
         *
         * <p>The count of logs with no published chain is said out loud. The first draft skipped
         * that comparison in silence, so a folder whose index had been deleted printed the same
         * clean sentence as one whose index agreed with every log in it -- and deleting the index
         * is the cheapest way to pass a folder that had been edited.
         */
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
            if (unindexed() > 0) {
                out.append("; ").append(unindexed()).append(" of them have no published chain in ")
                        .append(RunIndex.RUNS).append(" to be held against, so for those this"
                        + " checked the file against itself and nothing more");
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
        // A comparison's folder holds no logs of its own: each side's are in its own folder, with its
        // own index. Both are verified, the reports are joined, and the comparison's record of each
        // index -- a SHA-256 written when the verdict was -- is held against the index on disk, so a
        // verdict cannot be kept while the Runs it rests on are swapped underneath it.
        if (Files.isRegularFile(folder.resolve(Comparison.FILE))) {
            return comparison(folder);
        }
        if (!Files.isDirectory(folder)) {
            return new Report(folder, List.of(), "there is no folder at " + folder);
        }
        Map<String, String> stated;
        List<Path> files;
        try {
            stated = indexChains(folder);
            files = logs(folder);
        } catch (RuntimeException cannot) {
            return new Report(folder, List.of(), "the folder could not be read: " + cannot);
        }
        List<Checked> checked = new ArrayList<>();
        for (Path file : files) {
            checked.add(check(folder, file, stated));
        }
        String why = "";
        if (checked.isEmpty()) {
            why = "the folder holds no log, so there is nothing to verify";
        } else if (stated.isEmpty()) {
            // No index at all. Every log in the folder still verifies against its own bytes, and
            // not one of them has been held against anything published -- which is the whole of
            // what the index check is for, and is trivially arranged by deleting one file. A folder
            // in that state has not been verified; it has been read.
            why = "the folder holds no " + RunIndex.RUNS + ", so no log in it could be held against"
                    + " a published chain; a folder with no index is one that cannot be verified,"
                    + " not one that verified";
        }
        // A Run the index names and no file answers for. The index is written as Runs are
        // dispatched, so this is a child that died before its first line reached the disk -- which
        // the index already records as incomplete, and which this says out loud rather than
        // silently checking nothing.
        for (String runId : stated.keySet()) {
            if (checked.stream().noneMatch(one -> runId.equals(one.runId()))) {
                checked.add(new Checked(runId, runId + ".jsonl", false, 0, 0, false, true, "",
                        "the index names this Run and the folder holds no log for it"));
            }
        }
        return new Report(folder, List.copyOf(checked), why);
    }

    private static Checked check(Path folder, Path file, Map<String, String> stated) {
        String name = folder.relativize(file).toString().replace('\\', '/');
        // Read once, and catch everything. Two reads let a log that was being written verify as one
        // file and be indexed as another; and a locked file, a malformed byte or a log too large
        // for the heap raises something that is not a RuntimeException, which used to escape this
        // method and end the report on all five hundred logs -- the exact case this class promises
        // in its own javadoc to survive.
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception | OutOfMemoryError cannot) {
            return new Checked("", name, false, 0, 0, false, false, "",
                    "could not be read: " + cannot);
        }
        RunLogVerifier.Verified verified;
        LogHeader.Read read;
        try {
            verified = RunLogVerifier.of(text);
            read = LogHeader.of(text, file);
        } catch (RuntimeException | OutOfMemoryError unreadable) {
            return new Checked("", name, false, 0, 0, false, false, "",
                    "unreadable: " + unreadable);
        }
        String runId = read.runId();
        String said = stated.get(runId);
        boolean indexed = said != null && !said.isEmpty();
        if (!verified.ok()) {
            return new Checked(runId, name, false, verified.lines(), verified.brokenLine(),
                    read.complete(), indexed, "", verified.why());
        }
        // The oracle, before anything else about a log that reads. FR-11 is enforced when a Run is
        // dispatched -- in the process whoever ran it controls -- and this is the only place it is
        // asked of the artifact, which is the half that survives being handed to someone else.
        if (read.oracle()) {
            return new Checked(runId, name, true, verified.lines(), 0, read.complete(), indexed,
                    verified.chain(), "this log's own header says the Run saw what a player could"
                            + " not; an oracle Run is not ranked (FR-11)");
        }
        if (!read.readable()) {
            return new Checked(runId, name, true, verified.lines(), 0, false, indexed,
                    verified.chain(), "the chain holds but the records do not read: " + read.unreadable());
        }
        // The file's name against the Run its own header says it is. `Runner.finish` makes exactly
        // this check for the Rig's own writes and said why: without it, two files claiming one Run
        // are both held against one index row and the one that is checked is whichever sorts first.
        if (!runId.isEmpty() && !name.equals(RunLog.fileName(runId))) {
            return new Checked(runId, name, true, verified.lines(), 0, read.complete(), indexed,
                    verified.chain(), "this file is named " + name + " and its header says it is the"
                            + " Run " + runId);
        }
        // The chain the index published for this Run, against the chain its file gives. This is the
        // check that makes the index evidence rather than a copy: a folder can be handed on, and
        // the two halves have to still be about each other.
        String why = "";
        if (indexed && !said.equals(verified.chain())) {
            why = "the index says this Run chained to " + said + " and its log gives "
                    + verified.chain();
        }
        return new Checked(runId, name, true, verified.lines(), 0, read.complete(), indexed,
                verified.chain(), why);
    }

    private static Report comparison(Path folder) {
        List<Checked> checked = new ArrayList<>();
        StringBuilder why = new StringBuilder();
        String json;
        try {
            json = Files.readString(folder.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + folder.resolve(Comparison.FILE), e);
        }
        for (String side : List.of(Comparison.CANDIDATE, Comparison.BASELINE)) {
            Report report = of(folder.resolve(side));
            checked.addAll(report.checked());
            if (!report.why().isEmpty()) {
                why.append(side).append(": ").append(report.why()).append('\n');
            }
            String stated;
            try {
                stated = LogHeader.string(json, side + "_index_sha256");
            } catch (RuntimeException unreadable) {
                stated = null;
            }
            String actual = sha256(folder.resolve(side).resolve(RunIndex.RUNS));
            if (stated == null || !stated.equals(actual)) {
                why.append("the comparison was written over a ").append(side).append(" index hashing to ")
                        .append(stated).append(" and the index there now hashes to ").append(actual)
                        .append('\n');
            }
        }
        return new Report(folder, List.copyOf(checked), why.toString().strip());
    }

    private static String sha256(Path file) {
        try {
            byte[] bytes = Files.isRegularFile(file) ? Files.readAllBytes(file) : new byte[0];
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JDK has SHA-256", impossible);
        }
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
                // One unreadable index line does not make the logs unreadable, and it is not
                // agreement either: the Run it was about ends up with no published chain, which
                // the report counts and prints rather than passing over.
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
