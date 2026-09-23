package org.shatterfish.rig;

import org.shatterfish.api.JsonWriter;
import org.shatterfish.api.Registration;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every Registration the Rig has run under, and what came of it (story 3.5, FR-25).
 *
 * <p><b>What it is for.</b> A published number means something different depending on how many
 * times the question was asked before it gave that answer. Ten comparisons and one acceptance is a
 * different claim from one comparison and one acceptance, and nothing in a Results page or a Run
 * log distinguishes them — the runs that were not published leave no trace at all. So the Rig
 * appends a line every time it runs under a Registration, whatever the outcome, and the count of
 * prior attempts behind a claim becomes a thing a reader can add up rather than a thing they have
 * to trust.
 *
 * <p><b>Append-only, and committed.</b> Nothing here rewrites a line. The file is text, one record
 * per line, in the same canonical shape as everything else this project writes, so it reviews as a
 * diff and a deleted line is visible in git. That is the whole mechanism: the ledger is not
 * tamper-proof, it is tamper-<em>evident</em>, which is what a repository can actually offer.
 *
 * <p><b>It refuses a file it cannot read.</b> A ledger with a malformed line is not a ledger with
 * one bad entry — it is a record whose count nobody can be sure of, and appending to it would be
 * adding a true line to a document that is no longer evidence. The Rig stops instead.
 */
public final class Ledger {

    /** The file, beside the Registrations it records uses of. */
    public static final String FILE = "ledger.jsonl";

    /** How an invocation ended, as the ledger says it. */
    public enum Outcome {

        /**
         * A held-out set is about to be read, and is therefore already spent.
         *
         * <p>Written before the set is opened rather than after the Runs finish. `publish` is the
         * act that spends a held-out set: once those triples have been read they have been seen,
         * whatever happens next. Recording the use at the end meant that killing the process left
         * the set played and the ledger silent, and the next invocation's budget check passed.
         */
        CLAIMED,

        /** The invocation ran to the end and its folder holds what it produced. */
        FINISHED,

        /** The invocation was refused, and the folder it wrote is void (story 3.3). */
        REFUSED,

        /** The Rig was asked to do something FR-20 or FR-22 does not permit. */
        FORBIDDEN
    }

    /**
     * One use of one Registration.
     *
     * @param holdout whether this use consumed the Brain version's single holdout allowance, which
     *                is the number FR-20 caps and the reason this file exists rather than a count
     *                kept somewhere the Rig could rewrite
     */
    public record Entry(String registration, String hash, String at, String brain,
                        String brainCommit, String brainConfig, String seedSet, Outcome outcome,
                        boolean holdout, String note, String when) {

        String line() {
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("at").value(at);
            out.key("brain").value(brain);
            out.key("brain_commit").value(brainCommit);
            out.key("brain_config").value(brainConfig);
            out.key("hash").value(hash);
            out.key("holdout").value(holdout);
            out.key("note").value(note);
            out.key("outcome").value(outcome.name());
            out.key("registration").value(registration);
            out.key("seed_set").value(seedSet);
            out.key("when").value(when);
            out.endObject();
            return out.toJson();
        }

        static Entry of(String line) {
            Map<String, String> held = Json.object(line);
            return new Entry(
                    Json.string(Json.required(held, "registration", "ledger entry")),
                    Json.string(Json.required(held, "hash", "ledger entry")),
                    Json.string(Json.required(held, "at", "ledger entry")),
                    Json.string(Json.required(held, "brain", "ledger entry")),
                    Json.string(Json.required(held, "brain_commit", "ledger entry")),
                    Json.string(Json.required(held, "brain_config", "ledger entry")),
                    Json.string(Json.required(held, "seed_set", "ledger entry")),
                    Outcome.valueOf(Json.string(Json.required(held, "outcome", "ledger entry"))),
                    Json.bool(Json.required(held, "holdout", "ledger entry")),
                    Json.string(Json.required(held, "note", "ledger entry")),
                    Json.string(Json.required(held, "when", "ledger entry")));
        }
    }

    private final Path file;

    private final List<Entry> entries;

    /** Reads the ledger in {@code folder}, refusing one that cannot be read. */
    public Ledger(Path folder) {
        this(folder, null);
    }

    /**
     * Reads the ledger in {@code folder} and, when {@code root} is given, checks that no committed
     * line has gone missing.
     *
     * <p>The Registration is pinned by git and the count of attempts behind a published number was
     * pinned by nothing, so deleting this file restored every budget it records. The whole file
     * cannot be demanded clean -- appending to it is the Rig's job, so the working copy is dirty by
     * design -- but what HEAD holds must still be a <em>prefix</em> of what is on disk. That is
     * what append-only means, it is checkable, and it turns deleting the record from a silent act
     * into a refusal.
     */
    public Ledger(Path folder, Path root) {
        this.file = folder.resolve(FILE);
        if (root != null) {
            committedLinesAreStillHere(root, folder, file);
        }
        this.entries = read(file);
    }

    private static void committedLinesAreStillHere(Path root, Path folder, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        String committed = Registrations.committedOrNull(root, relative);
        if (committed == null || committed.isEmpty()) {
            // Never committed, which is the state of a repository that has not published a number
            // yet. There is nothing to have lost.
            return;
        }
        String now;
        try {
            now = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException("the ledger " + file + " could not be read", e);
        }
        if (!now.replace("\r\n", "\n").startsWith(committed.replace("\r\n", "\n"))) {
            throw new IllegalStateException("the ledger " + file + " no longer begins with what is"
                    + " committed, so lines recording prior attempts have been changed or removed."
                    + " The count of attempts behind a published number is what FR-25 publishes,"
                    + " and a ledger that can lose lines quietly is not that count");
        }
    }

    private static List<Entry> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the ledger " + file + " could not be read", e);
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                entries.add(Entry.of(lines.get(i)));
            } catch (RuntimeException unreadable) {
                // Not "skip the bad line". A ledger is a count, and a count with a hole in it is
                // not a smaller count -- it is an unknown one. Appending a true line to a document
                // whose other lines cannot be read would be adding to something that has stopped
                // being evidence.
                throw new IllegalStateException("the ledger " + file + " cannot be read at line "
                        + (i + 1) + ", so the number of prior attempts behind any published claim"
                        + " is unknown and the Rig will not add to it: " + unreadable.getMessage(),
                        unreadable);
            }
        }
        return entries;
    }

    private static boolean endsWithNewline(Path file) throws IOException {
        try (java.io.RandomAccessFile open = new java.io.RandomAccessFile(file.toFile(), "r")) {
            open.seek(open.length() - 1);
            return open.read() == 10;   // a line feed, which is the only ending this format writes
        }
    }

    /** Every use recorded, oldest first. */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * When the holdout set was already used for this exact Brain version, or null when it was not.
     *
     * <p>A Brain's version is its commit and its configuration hash together: the same Brain built
     * from the same source is the same Brain, and one whose weights changed is a different one even
     * under the same name. FR-20's allowance is per version for that reason — otherwise the budget
     * is spent by renaming.
     */
    public String holdoutUse(String brainCommit, String brainConfig) {
        for (Entry entry : entries) {
            // A CLAIMED entry counts. It says the set was opened, which is the moment the
            // allowance is spent -- what happened to the Runs afterwards does not give it back.
            if (entry.holdout() && entry.brainCommit().equals(brainCommit)
                    && entry.brainConfig().equals(brainConfig)) {
                return entry.registration() + " on " + entry.when();
            }
        }
        return null;
    }

    /** How many times this Registration has been run under, whatever came of it. */
    public int uses(String registration) {
        return (int) entries.stream().filter(e -> e.registration().equals(registration)).count();
    }

    /**
     * Appends one use and returns it.
     *
     * <p>Opened for append rather than rewritten, so a line already on disk is never touched by
     * this process even if it fails halfway through the next one.
     */
    public Entry record(Registrations.Committed committed, String brain, String brainCommit,
                        String brainConfig, String seedSet, Outcome outcome, boolean holdout,
                        String note) {
        // The Brain that ran, named by what it was built from. The Registration says which Brain
        // the hypothesis is about; this says which one was measured, and they are different
        // sentences -- FR-20's budget is spent by the second.
        Entry entry = new Entry(committed.registration().id(), committed.hash(), committed.at(),
                brain, brainCommit, brainConfig, seedSet, outcome, holdout,
                note, Instant.now().toString());
        try {
            Files.createDirectories(file.getParent());
            // A newline first when the file does not end in one. Appending onto a truncated last
            // line joins two records into one, and this file refuses to be read when a line cannot
            // be -- so that mistake is permanent rather than untidy.
            String first = Files.exists(file) && Files.size(file) > 0 && !endsWithNewline(file)
                    ? "\n" : "";
            Files.writeString(file, first + entry.line() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("the ledger " + file + " could not be written", e);
        }
        entries.add(entry);
        return entry;
    }
}
