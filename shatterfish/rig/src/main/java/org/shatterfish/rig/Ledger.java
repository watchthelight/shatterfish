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
        this.file = folder.resolve(FILE);
        this.entries = read(file);
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
            Files.writeString(file, entry.line() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("the ledger " + file + " could not be written", e);
        }
        entries.add(entry);
        return entry;
    }
}
