package org.shatterfish.rig;

import org.shatterfish.api.Registration;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Where a Registration is read from, and how "committed before the Run" is checked (story 3.5).
 *
 * <p><b>Git is the authority, and it has to be.</b> "This was decided before the numbers were seen"
 * is a claim about time, and a file cannot make it about itself: any bytes on disk could have been
 * written a second ago. So the Rig asks git two questions — is this path tracked, and does the
 * working copy differ from what is committed — and then hashes <em>the committed bytes</em> rather
 * than the ones on disk. An edit made after the Runs changes nothing the Rig ever used, and the
 * commit the Registration was read at goes into the ledger, which is what lets a stranger check the
 * order of events without taking anybody's word for it.
 *
 * <p><b>The Rig reads Registrations and never writes one.</b> A runner that could author its own
 * hypothesis is a runner that can choose the hypothesis after seeing the numbers, which is the one
 * thing this whole mechanism exists to prevent. Writing one is a person's job, and committing it is
 * the act that fixes it.
 *
 * <p><b>The holdout rule lives here and nowhere else.</b> FR-20 says the holdout set may be used
 * only for a release-level claim, at most once per Brain version, and that every use is recorded.
 * Story 3.1 built the first half — {@code SeedSets.publish} is the only door to the set and demands
 * a reason — and left the budget with nowhere durable to live (issue #116). The Registration is that
 * somewhere: it declares whether the claim is release-level, and the {@link Ledger} counts the uses.
 */
public final class Registrations {

    /** Where committed Registrations live, beside the seed sets they name. */
    public static final String FOLDER = "registrations";

    /**
     * The seed set FR-20 guards.
     *
     * <p>Taken from {@link SeedSets}, which owns the set and refuses it at the door story 3.1
     * built. A second constant spelling the same word would be a second place for the rule to
     * live, which is what the story's own acceptance criterion forbids.
     */
    public static final String HOLDOUT = SeedSets.HOLDOUT;

    /** Whether running {@code seedSet} spends a Brain version's held-out allowance (FR-20). */
    public static boolean spendsTheBudget(String seedSet) {
        return HOLDOUT.equals(seedSet);
    }

    /** What a Registration could not be used for, and why. Null when it can. */
    public record Refusal(String why) {
    }

    /**
     * A Registration, the commit it was read at, and the hash of the bytes that commit holds.
     *
     * @param at   the commit the file was read from, so the ledger can record what "before" meant
     * @param hash the SHA-256 of the committed canonical text, which is what a Run log stamps
     */
    public record Committed(Registration registration, String at, String hash) {

        /**
         * What a Run log's header records: the id, and the first sixteen digits of the hash.
         *
         * <p>Through {@link Registration#stamp()}, not beside it. Two implementations of the
         * truncation would let a log and a ledger line disagree about the same Registration the
         * day one of them changed.
         */
        public String stamp() {
            return registration.stamp();
        }
    }

    private Registrations() {
    }

    /**
     * Reads the Registration {@code id} names, refusing one git does not vouch for.
     *
     * @throws IllegalArgumentException when there is no such Registration, when git reports the
     *                                  file untracked or modified, or when the file is not one
     */
    public static Committed read(Path root, String id) {
        // Checked before it becomes a path and a git argument. `--registration ../../something` is
        // refused here rather than three subprocesses later with a message about the wrong thing.
        if (id == null || !id.matches(Registration.ID_PATTERN)) {
            throw new IllegalArgumentException("a hypothesis id looks like H-0001-a-short-name,"
                    + " and this is not one: " + id);
        }
        // Git has to be answerable before its silence can mean anything. Without this, a machine
        // with no git on it reports every hypothesis as uncommitted, which is a true-sounding
        // accusation about somebody's honesty rather than a true statement about the machine.
        if (git(root, "rev-parse", "--git-dir") == null) {
            throw new IllegalArgumentException("git could not be asked about " + root + ", so"
                    + " whether a hypothesis was committed before the Runs cannot be established"
                    + " here (FR-22)");
        }
        Path file = root.resolve(FOLDER).resolve(id + ".json");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("there is no Registration " + id + " in "
                    + root.resolve(FOLDER) + "; the ones that are committed are " + ids(root));
        }
        String relative = (FOLDER + "/" + id + ".json");
        if (!tracked(root, relative)) {
            throw new IllegalArgumentException("the Registration " + id + " is not committed, and a"
                    + " hypothesis that is not in the repository is one that can still be changed"
                    + " after the numbers are seen (FR-22): git does not track " + relative);
        }
        String dirty = modified(root, relative);
        if (!dirty.isEmpty()) {
            throw new IllegalArgumentException("the Registration " + id + " differs from what is"
                    + " committed, so the Rig cannot tell which version the Runs would be under:"
                    + " git says " + dirty + " for " + relative);
        }
        // The committed bytes, not the working copy. This is the difference between checking that a
        // decision was recorded and checking the decision that was recorded.
        String text = committedText(root, relative);
        Registration registration = of(text, id);
        // Hashed over the text that came out of HEAD, not over a re-rendering of what was read out
        // of it. The two agree, and they agree *by construction*: `of` refuses a file that is not
        // the canonical text of what it means, so the re-rendering is the committed text. The
        // battery confirmed that by surviving a mutation of this line -- there is no input on which
        // the two expressions differ while that check stands.
        //
        // It stays written this way regardless. The javadoc above, the methodology page and the
        // story all say the hash is over the committed bytes, and a line that computes it from
        // those bytes says so without asking a reader to hold an invariant from two lines away in
        // their head.
        return new Committed(registration, commit(root, relative), Registration.hashOf(text.strip()));
    }

    /**
     * Whether {@code committed} may be used for a Run over {@code seedSet}, and why not when it
     * may not.
     *
     * <p>Both halves of FR-20 in one place. The ledger is passed in rather than read here so that
     * the rule can be asked about a folder the caller chooses — the alternative is a method that
     * decides where the record lives, and then two of them.
     */
    public static Refusal refusal(Committed committed, String seedSet, String brainCommit,
                                  String brainConfig, Ledger ledger) {
        Registration registration = committed.registration();
        if (!registration.seedSet().equals(seedSet)) {
            return new Refusal("the Registration " + registration.id() + " is for the "
                    + registration.seedSet() + " set and this invocation runs " + seedSet
                    + "; a hypothesis names the Runs it is about");
        }
        if (!HOLDOUT.equals(seedSet)) {
            return null;
        }
        if (!registration.releaseLevel()) {
            return new Refusal("the " + HOLDOUT + " set may be used only to publish a release-level"
                    + " number, and the Registration " + registration.id() + " does not claim one"
                    + " (FR-20)");
        }
        // At most once per Brain version, where a Brain's version is the commit and configuration
        // of the Brain that is about to *run* -- not the one the Registration names. A Registration
        // records when the hypothesis was written; if the budget were counted against that, it
        // could be spent again by editing a document.
        String used = ledger.holdoutUse(brainCommit, brainConfig);
        if (used != null) {
            return new Refusal("the " + HOLDOUT + " set has already been used for this Brain"
                    + " version: " + used + ". FR-20 allows one use per Brain version, so that the"
                    + " count of attempts behind a published number is the count the Results page"
                    + " shows");
        }
        return null;
    }

    /** Every Registration id the folder holds, sorted, for a refusal that says what does exist. */
    public static List<String> ids(Path root) {
        Path folder = root.resolve(FOLDER);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            List<String> ids = new ArrayList<>();
            files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .forEach(ids::add);
            return List.copyOf(ids);
        } catch (IOException e) {
            throw new UncheckedIOException("the Registrations in " + folder + " could not be listed", e);
        }
    }

    // ------------------------------------------------------------------------ reading one file

    /**
     * One Registration out of its canonical text.
     *
     * <p>Read through {@link Json}, the one production reader of this project's canonical shape, so
     * a Registration is held to the same grammar as a Run log: sorted keys, no whitespace, whole
     * numbers, no key written twice. A hand-made Registration is a thing this project expects —
     * a person writes them — and a reader that quietly accepted a file the writer would not produce
     * would be accepting the drift the format exists to make visible.
     */
    static Registration of(String text, String id) {
        Map<String, String> held;
        try {
            held = Json.object(text.strip());
        } catch (RuntimeException notCanonical) {
            throw new IllegalArgumentException("the Registration " + id + " is not written in the"
                    + " shape this project reads: " + notCanonical.getMessage(), notCanonical);
        }
        Registration registration = new Registration(
                Json.string(Json.required(held, "hypothesis", "Registration")),
                Json.string(Json.required(held, "claim", "Registration")),
                held.containsKey("brain_a") ? brain(held, "brain_a") : null,
                brain(held, "brain_b"),
                Json.string(Json.required(held, "seed_set", "Registration")),
                Json.integer(Json.required(held, "seed_version", "Registration")),
                Json.integer(Json.required(held, "alpha_per_mil", "Registration")),
                Json.integer(Json.required(held, "beta_per_mil", "Registration")),
                Json.integer(Json.required(held, "burn_in", "Registration")),
                Json.integer(Json.required(held, "maximum", "Registration")),
                Json.integer(Json.required(held, "budget_ms", "Registration")),
                Json.string(Json.required(held, "machine_class", "Registration")),
                Json.bool(Json.required(held, "release_level", "Registration")),
                held.containsKey("p0_per_mil") ? Json.integer(held.get("p0_per_mil")) : 0,
                held.containsKey("p1_per_mil") ? Json.integer(held.get("p1_per_mil")) : 0,
                held.containsKey("missing_per_mil") ? Json.integer(held.get("missing_per_mil")) : 0,
                // Never defaulted for a comparison: an empty statistic on one is refused by the
                // record, so a committed comparison has to say which test its bounds are for.
                held.containsKey("statistic") ? Json.string(held.get("statistic")) : "");
        if (!registration.id().equals(id)) {
            throw new IllegalArgumentException("the file " + id + ".json holds the Registration "
                    + registration.id() + "; a hypothesis is named by the file it is in");
        }
        // Read, then written back, then compared. The file is what a person edits and the canonical
        // text is what is hashed, so a file carrying a member this reader ignores -- a salt, a note,
        // a field from a later schema -- would hash as though it were not there, and the stamp in
        // every Run log would be about a document nobody wrote.
        if (!registration.canonical().equals(text.strip())) {
            // Named, not merely refused. A file carrying a member this reader does not know -- a
            // salt, a note, a field from a later schema -- would otherwise be reported as "your
            // file is wrong somewhere", and the member is the whole of what the reader needs to
            // hear. The story's own matrix asks for the name.
            List<String> extra = new ArrayList<>(held.keySet());
            extra.removeAll(Json.object(registration.canonical()).keySet());
            throw new IllegalArgumentException("the Registration " + id + " on disk is not the"
                    + " canonical text of what it means, so its hash would be over bytes nobody"
                    + " reads"
                    + (extra.isEmpty() ? "" : "; it carries " + extra + ", which a Registration"
                            + " does not have")
                    + ". Write it as:\n" + registration.canonical());
        }
        return registration;
    }

    private static Registration.Brain brain(Map<String, String> held, String key) {
        Map<String, String> brain = Json.object(Json.required(held, key, "Registration"));
        return new Registration.Brain(
                Json.string(Json.required(brain, "name", "Brain")),
                Json.string(Json.required(brain, "commit", "Brain")),
                Json.string(Json.required(brain, "config", "Brain")));
    }

    // ------------------------------------------------------------------------------ asking git

    private static boolean tracked(Path root, String relative) {
        return git(root, "ls-files", "--error-unmatch", "--", relative) != null;
    }

    /** What git says is different about the working copy, or empty when nothing is. */
    private static String modified(Path root, String relative) {
        String said = git(root, "status", "--porcelain", "--", relative);
        return said == null ? "it could not be asked about" : said.strip();
    }

    /**
     * The path {@code git show HEAD:} needs, which is not always the path the other questions took.
     *
     * <p>`ls-files` and `status` resolve a pathspec against the directory git is run in; `show
     * HEAD:<path>` resolves against the repository top. With a {@code --root} inside a repository
     * rather than at its top, those are different files -- so the "is it committed, is it
     * unmodified" verdict was about one and the bytes hashed into every Run log's header were the
     * other's. Found by a review that built the two-file repository and ran it.
     */
    private static String fromTheTop(Path root, String relative) {
        String prefix = git(root, "rev-parse", "--show-prefix");
        return (prefix == null ? "" : prefix.strip()) + relative;
    }

    /** What HEAD holds at {@code relative}, or null when it holds nothing there. */
    static String committedOrNull(Path root, String relative) {
        return git(root, "show", "HEAD:" + fromTheTop(root, relative));
    }

    private static String committedText(Path root, String relative) {
        String text = git(root, "show", "HEAD:" + fromTheTop(root, relative));
        if (text == null) {
            throw new IllegalArgumentException("the Registration " + relative + " is tracked but is"
                    + " not in HEAD; a hypothesis is fixed by the commit that carries it");
        }
        return text;
    }

    /**
     * The commit that last changed this Registration, which is when the hypothesis was fixed.
     *
     * <p>Package-private so that its refusal can be reached. Through {@link #read} it cannot be:
     * the two checks above fire first for every file git cannot answer about, so the guard here is
     * the third lock on a door that is already shut — and a lock nothing can test is a lock nobody
     * knows works.
     */
    static String commit(Path root, String relative) {
        String said = git(root, "log", "-1", "--format=%H", "--", relative);
        if (said == null || !said.strip().matches("[0-9a-f]{40}")) {
            // Fails closed, like every other git question on this path. It used to return an empty
            // string, which was written into the ledger as the commit a hypothesis was fixed by --
            // the one field the design calls what lets a stranger check the order of events.
            throw new IllegalArgumentException("git could not say which commit fixed " + relative
                    + ", and a hypothesis whose commit is unknown cannot be shown to have come"
                    + " before the numbers (FR-22)");
        }
        return said.strip();
    }

    /**
     * Runs one git command in {@code root} and hands back its output, or null when it failed.
     *
     * <p>Null rather than an exception, because every caller here has its own sentence to say about
     * what git's silence means: untracked is a different refusal from unreadable, and both are
     * better messages than a stack trace about a process.
     */
    private static String git(Path root, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectErrorStream(false)
                    .start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            return process.waitFor() == 0 ? out : null;
        } catch (IOException | RuntimeException cannot) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
