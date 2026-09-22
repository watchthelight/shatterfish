package org.shatterfish.codex;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The citation checker (story 2.10, FR-17): every {@code path:line} in {@code docs/} resolved
 * against the tree at the tag the citation itself names.
 *
 * <p>Non-negotiable 8 says a claim about the game is settled by reading the pinned code and citing
 * {@code path:line}. A citation that has quietly stopped resolving is that rule failing silently:
 * the sentence still carries an authority it no longer has, and nothing in the build says so. This
 * class is the instrument the upgrade procedure's re-verification step promised.
 *
 * <p>Four decisions make it a check rather than a nuisance, and each of them was a way of being
 * wrong that the first sweep over the committed documentation demonstrated:
 *
 * <ul>
 *   <li><b>A citation is resolved at the tag it names, not at the pin.</b> A rules row deliberately
 *       left at {@code v3.3.8} is still true of {@code v3.3.8}; resolving it against the pin would
 *       report ninety rows the project has already decided about, and a report nobody reads is not
 *       a check. What the checker adds is that the decision is now visible in both directions: a
 *       rules row at an older tag has to say {@code needs-review}, and a row that says so has to be
 *       at an older tag. A citation that names no tag — an unlinked span in prose — is resolved at
 *       the pin, because the pin is the tag the document is written against.
 *   <li><b>The tag-age rule is a rules-table rule.</b> {@code docs/UPSTREAM.md} already says which
 *       documents move with the pin and which stay: a decision record's citations are the evidence
 *       for a decision taken at that tag, and they stay there. So an older tag is a finding only
 *       inside a {@code docs/rules/} table row, which is where the {@code needs-review} convention
 *       lives. Everywhere else a citation is simply resolved at the tag it names.
 *   <li><b>A name without a path resolves by search, and refuses to guess.</b> The documentation
 *       writes {@code Random.java:202-229} and {@code core/…/actors/mobs/Snake.java:35} on purpose,
 *       several hundred times. Refusing them all would report prose the project means; taking the
 *       first match would be folklore. So a bare name, or a path whose middle is elided by
 *       {@code …} or {@code ...}, resolves when exactly one file in the tree at that tag carries
 *       it, is reported as ambiguous when more than one does, and as missing when none does — the
 *       same rule {@link Stated} uses for a superclass.
 *   <li><b>A tag is resolved the way the hook ledger resolves the pin: by commit.</b> The tag lives
 *       in upstream's repository and this fork carries it only if someone pushed it, so a lookup by
 *       tag name passes on a developer's machine and fails in continuous integration for a reason
 *       that has nothing to do with the documentation. The pinned tag is therefore resolved through
 *       the {@code Commit} row of {@code docs/UPSTREAM.md}, exactly as {@code Ledger} does, and any
 *       other ref is looked for as a tag, then as anything git will resolve, then on
 *       {@code origin}, because a pull-request checkout has {@code origin/main} and no local
 *       {@code main}.
 * </ul>
 *
 * <p>What it does not do is read prose. It resolves line ranges: that the cited lines exist in the
 * cited file at the cited tag, and that a code span and the link around it say the same thing. That
 * those lines still <em>mean</em> what the row says is what a person does when a row is flipped to
 * {@code needs-review}, and no check replaces it.
 *
 * <p>It lives in the module's test sources on purpose. The Codex's leak gate
 * ({@code CodexLeakTest}) denies the generator {@code Process}, {@code ProcessBuilder} and
 * {@code System}, so a class that runs git and prints a report cannot be one of the generator's
 * without a hole in that gate, and the gate is worth more than the placement.
 */
public final class DocsCitations {

    /** Where the documentation lives, relative to the repository root. */
    static final String DOCS = "docs";

    /** The rules tables: the only place the {@code needs-review} convention has force. */
    static final String RULES = "docs/rules/";

    /** The ledger, which names the pinned tag and the commit it stands for. */
    static final String LEDGER = "docs/UPSTREAM.md";

    /** The prefix of a link into this repository at a ref. */
    static final String BLOB = "https://github.com/watchthelight/shatterfish/blob/";

    /** The command that prints this report, named in the upgrade procedure. */
    static final String COMMAND = "./gradlew :codex:citations";

    /** The pinned-commit row of the ledger, read the way {@code Ledger} reads it. */
    private static final String COMMIT_ROW = "^\\|\\s*Commit\\s*\\|\\s*`([0-9a-f]{7,40})`";

    /**
     * A citation in the text: a path, or a name, and the lines of it. The token may not be preceded
     * by a path character, so the path inside a link's URL is not read a second time, and it must
     * end in an extension, so that a clock time or a version is not read as a file.
     */
    private static final Pattern CITATION = Pattern.compile(
            "(?<![A-Za-z0-9_./…-])([A-Za-z0-9_…][A-Za-z0-9_./…-]*\\.[A-Za-z][A-Za-z0-9]*)"
                    + ":(\\d+)(?:-(\\d+))?");

    /** A code span linked into this repository, with the ref, the path and the anchor it opens. */
    private static final Pattern LINK = Pattern.compile(
            "\\[`([^`\\]]*)`\\]\\(" + Pattern.quote(BLOB) + "([^/]+)/([^)#]+)(?:#L(\\d+)(?:-L(\\d+))?)?\\)");

    /** What a ref has to look like before the tag-age rule has an opinion about it. */
    private static final Pattern TAG = Pattern.compile("^v[0-9]+([.][0-9]+)*(-[A-Za-z0-9.]+)?$");

    /** The flag a rules row carries while its citation is waiting to be re-read. */
    static final String FLAG = "needs-review";

    /** What an elided middle is replaced by while a path is being matched. Never in a path. */
    private static final String ELISION = " ";

    private DocsCitations() {
    }

    /** The ways a citation fails, each as the report says it. */
    enum Rule {
        NO_SUCH_REF("the tag is not a ref this checkout has"),
        NOT_IN_THE_TREE("the file is not in the tree at that tag"),
        AMBIGUOUS("the name is more than one file in the tree at that tag"),
        PAST_THE_END("the line is past the end of the file at that tag"),
        SPAN_AND_LINK("the code span and the link around it state different things"),
        A_TAG_NOBODY_FLAGGED("the row cites another tag and does not say " + FLAG),
        A_FLAG_THAT_OUTLIVED_ITS_RE_CITATION("the row says " + FLAG + " and its link is at the pin");

        final String said;

        Rule(String said) {
            this.said = said;
        }
    }

    /** One citation that does not resolve: the page it is on, the line, and which rule it broke. */
    record Finding(String page, int line, Rule rule, String detail) {

        @Override
        public String toString() {
            return page + ":" + line + "  " + rule.said + "  -- " + detail;
        }
    }

    /**
     * One citation as the text writes it: the page and the line it is on, what it says, the path it
     * names and the lines it claims of that path, the ref it is to be resolved at, and whether it
     * sits in a rules row that carries the flag.
     */
    record Citation(String page, int line, String text, String path, int from, int to,
                    String ref, boolean row, boolean flagged) {
    }

    /** One link into this repository: where its label sits on the line, and what it opens. */
    private record Anchor(int labelStart, int labelEnd, String ref, String path, int from, int to) {

        boolean hasLines() {
            return from > 0;
        }
    }

    /**
     * Prints the report. Exits non-zero when anything failed to resolve, so that the upgrade
     * procedure has one command whose exit status is the answer.
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("usage: DocsCitations [<repository root>]");
        }
        Path root = args.length == 1 ? Generate.checkout(args[0]) : root();
        List<Finding> findings = sweep(root);
        System.out.println(report(root, findings));
        if (!findings.isEmpty()) {
            System.exit(1);
        }
    }

    /** The report, whether or not anything failed, so that a green run says what it checked. */
    static String report(Path root, List<Finding> findings) {
        StringBuilder text = new StringBuilder();
        text.append("citations under ").append(DOCS).append('/')
                .append(", each resolved at the tag it names (the pin is ").append(Upstream.tag(root))
                .append(")\n");
        if (findings.isEmpty()) {
            return text.append("no findings").toString();
        }
        text.append(findings.size()).append(findings.size() == 1 ? " finding" : " findings").append("\n\n");
        for (Finding finding : findings) {
            text.append(finding).append('\n');
        }
        return text.toString();
    }

    /** Every citation in {@code docs/} that does not resolve, in page then line order. */
    static List<Finding> sweep(Path root) {
        List<Finding> findings = new ArrayList<>();
        resolve(root, read(root, findings), findings);
        findings.sort(Comparator.comparing(Finding::page).thenComparingInt(Finding::line)
                .thenComparing(f -> f.rule().name()).thenComparing(Finding::detail));
        return List.copyOf(findings);
    }

    // --------------------------------------------------------------- reading the documentation

    /**
     * Every citation written under {@code docs/}. The disagreements between a code span and the
     * link around it, and the tag-age findings, are properties of the text rather than of any tree
     * and are settled here.
     */
    static List<Citation> read(Path root, List<Finding> findings) {
        String pin = Upstream.tag(root);
        List<Citation> citations = new ArrayList<>();
        for (Path file : pages(root)) {
            String page = root.relativize(file).toString().replace('\\', '/');
            boolean rulesPage = page.startsWith(RULES);
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(page + " could not be read as UTF-8", e);
            }
            for (int i = 0; i < lines.size(); i++) {
                readLine(page, i + 1, lines.get(i), rulesPage, pin, citations, findings);
            }
        }
        return citations;
    }

    private static void readLine(String page, int number, String line, boolean rulesPage, String pin,
                                 List<Citation> citations, List<Finding> findings) {
        boolean row = rulesPage && line.startsWith("|");
        boolean flagged = row && flagged(line);
        List<Anchor> anchors = anchors(line);
        List<Anchor> unread = new ArrayList<>(anchors);

        Matcher span = CITATION.matcher(line);
        while (span.find()) {
            String path = span.group(1);
            int from = Integer.parseInt(span.group(2));
            int to = span.group(3) == null ? from : Integer.parseInt(span.group(3));
            Anchor around = null;
            for (Anchor anchor : anchors) {
                if (anchor.labelStart() <= span.start() && span.end() <= anchor.labelEnd()) {
                    around = anchor;
                }
            }
            if (around == null) {
                // A citation that names no tag is written against the pin, which is the tag the
                // documentation as a whole is written against.
                citations.add(new Citation(page, number, span.group(), path, from, to, pin, row, flagged));
                continue;
            }
            unread.remove(around);
            disagreement(page, number, span.group(), path, from, to, around, findings);
            tagAge(page, number, span.group(), around.ref(), pin, row, flagged, findings);
            // The link is the citation once there is one: it names the ref and the whole path, and
            // the span beside it is the abbreviation a reader reads. A number the two disagree
            // about is already a finding above, so it is not resolved twice here.
            citations.add(new Citation(page, number, span.group(), around.path(),
                    around.hasLines() ? around.from() : from, around.hasLines() ? around.to() : to,
                    around.ref(), row, flagged));
        }

        // A link that carries line numbers and whose label is not a citation is a citation all the
        // same: the anchor is the claim, and nothing else in this class would look at it.
        for (Anchor orphan : unread) {
            if (!orphan.hasLines()) {
                continue;
            }
            String text = orphan.path() + "#L" + orphan.from()
                    + (orphan.to() == orphan.from() ? "" : "-L" + orphan.to());
            tagAge(page, number, text, orphan.ref(), pin, row, flagged, findings);
            citations.add(new Citation(page, number, text, orphan.path(), orphan.from(), orphan.to(),
                    orphan.ref(), row, flagged));
        }
    }

    /** Every link into this repository on one line, with where its label sits. */
    private static List<Anchor> anchors(String line) {
        List<Anchor> anchors = new ArrayList<>();
        Matcher link = LINK.matcher(line);
        while (link.find()) {
            int from = link.group(4) == null ? 0 : Integer.parseInt(link.group(4));
            int to = link.group(5) == null ? from : Integer.parseInt(link.group(5));
            anchors.add(new Anchor(link.start(1), link.end(1), link.group(2), link.group(3), from, to));
        }
        return anchors;
    }

    /** Whether a rules row's cells carry the flag: a cell that says it, not a sentence about it. */
    private static boolean flagged(String row) {
        for (String cell : row.split("\\|", -1)) {
            if (cell.trim().equals(FLAG)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The code span and the link around it say the same file and the same lines, or the pair is a
     * finding. A span whose middle is elided has to be a suffix of the link's path rather than
     * equal to it, which is what the abbreviation means.
     */
    private static void disagreement(String page, int number, String text, String path, int from, int to,
                                     Anchor around, List<Finding> findings) {
        if (!suffixOf(path, around.path())) {
            findings.add(new Finding(page, number, Rule.SPAN_AND_LINK,
                    "the span says " + path + " and the link opens " + around.path()));
        }
        if (around.hasLines() && (around.from() != from || around.to() != to)) {
            findings.add(new Finding(page, number, Rule.SPAN_AND_LINK,
                    "the span says " + text + " and the link opens #L" + around.from()
                            + (around.to() == around.from() ? "" : "-L" + around.to())));
        }
    }

    /** Whether a span's path, elided or not, names the path the link opens. */
    private static boolean suffixOf(String written, String linked) {
        String path = elided(written);
        if (!path.contains(ELISION)) {
            return linked.equals(path) || linked.endsWith("/" + path);
        }
        int elision = path.lastIndexOf(ELISION);
        String prefix = head(path.substring(0, elision));
        String suffix = tail(path.substring(elision + ELISION.length()));
        return (prefix.isEmpty() || linked.startsWith(prefix)) && linked.endsWith("/" + suffix);
    }

    /**
     * The two halves of the {@code needs-review} convention, which hold of a rules row and of
     * nothing else: a row at another tag has to say it, and a row that says it has to be at
     * another tag.
     */
    private static void tagAge(String page, int number, String text, String ref, String pin,
                               boolean row, boolean flagged, List<Finding> findings) {
        if (!row || !TAG.matcher(ref).matches()) {
            return;
        }
        if (!ref.equals(pin) && !flagged) {
            findings.add(new Finding(page, number, Rule.A_TAG_NOBODY_FLAGGED,
                    text + " is cited at " + ref + " and the pin is " + pin));
        }
        if (ref.equals(pin) && flagged) {
            findings.add(new Finding(page, number, Rule.A_FLAG_THAT_OUTLIVED_ITS_RE_CITATION,
                    text + " is cited at the pin " + pin));
        }
    }

    /** Every Markdown page under {@code docs/}, in path order. */
    static List<Path> pages(Path root) {
        Path docs = root.resolve(DOCS);
        if (!Files.isDirectory(docs)) {
            throw new IllegalStateException("no " + DOCS + "/ under " + root);
        }
        try (Stream<Path> tree = Files.walk(docs)) {
            return tree.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("the documentation under " + docs + " could not be walked", e);
        }
    }

    // ------------------------------------------------------------------- resolving them at git

    /** Resolves every citation against the tree of the ref it names, adding what does not resolve. */
    private static void resolve(Path root, List<Citation> citations, List<Finding> findings) {
        String pin = Upstream.tag(root);
        Map<String, Optional<String>> revisions = new TreeMap<>();
        Map<String, Tree> trees = new TreeMap<>();
        Map<String, List<Citation>> located = new LinkedHashMap<>();

        for (Citation citation : citations) {
            Optional<String> revision =
                    revisions.computeIfAbsent(citation.ref(), ref -> revision(root, ref, pin));
            if (revision.isEmpty()) {
                findings.add(new Finding(citation.page(), citation.line(), Rule.NO_SUCH_REF,
                        citation.text() + " names " + citation.ref()));
                continue;
            }
            Tree tree = trees.computeIfAbsent(revision.get(), rev -> new Tree(root, rev));
            List<String> candidates = tree.locate(citation.path());
            if (candidates.isEmpty()) {
                findings.add(new Finding(citation.page(), citation.line(), Rule.NOT_IN_THE_TREE,
                        citation.text() + " at " + citation.ref()));
            } else if (candidates.size() > 1) {
                findings.add(new Finding(citation.page(), citation.line(), Rule.AMBIGUOUS,
                        citation.text() + " at " + citation.ref() + " is " + candidates.size() + " files: "
                                + String.join(", ", candidates.subList(0, Math.min(3, candidates.size())))
                                + (candidates.size() > 3 ? ", ..." : "")));
            } else {
                located.computeIfAbsent(revision.get() + ":" + candidates.get(0), k -> new ArrayList<>())
                        .add(citation);
            }
        }

        Map<String, Integer> lengths = lineCounts(root, List.copyOf(located.keySet()));
        for (Map.Entry<String, List<Citation>> entry : located.entrySet()) {
            String object = entry.getKey();
            String path = object.substring(object.indexOf(':') + 1);
            int length = lengths.getOrDefault(object, -1);
            for (Citation citation : entry.getValue()) {
                if (length < 0) {
                    findings.add(new Finding(citation.page(), citation.line(), Rule.NOT_IN_THE_TREE,
                            citation.text() + " at " + citation.ref() + ": " + path + " could not be read"));
                } else if (citation.to() > length) {
                    findings.add(new Finding(citation.page(), citation.line(), Rule.PAST_THE_END,
                            citation.text() + " at " + citation.ref() + ": " + path + " has " + length
                                    + (length == 1 ? " line" : " lines")));
                }
            }
        }
    }

    /**
     * The commit a ref stands for, or empty.
     *
     * <p>The pinned tag is resolved through the ledger's {@code Commit} row rather than by name,
     * for the reason {@code docs/UPSTREAM.md} gives: the tag is upstream's, this fork carries it
     * only if someone pushed it, and continuous integration clones the fork. Any other ref is
     * looked for as a tag, then as anything git will resolve, then on {@code origin}, because a
     * pull-request checkout has {@code origin/main} and no local {@code main}.
     */
    static Optional<String> revision(Path root, String ref, String pin) {
        List<String> candidates = ref.equals(pin)
                ? List.of(pinnedRevision(root), "refs/tags/" + ref)
                : List.of("refs/tags/" + ref, ref, "refs/remotes/origin/" + ref);
        for (String candidate : candidates) {
            Run run = run(root, "rev-parse", "--verify", "--quiet", candidate + "^{commit}");
            if (run.status() == 0 && !run.out().isEmpty() && !run.out().get(0).isBlank()) {
                return Optional.of(run.out().get(0).trim());
            }
        }
        return Optional.empty();
    }

    /**
     * The commit the ledger pins, which is the revision every other check in the repository uses.
     *
     * <p>The first {@code Commit} row, in document order, exactly as {@code Ledger} reads it: the
     * ledger's second pinned source (vanilla Pixel Dungeon, story 2.8) carries a {@code Commit} row
     * of its own further down, and the pinned release is the table at the top.
     */
    static String pinnedRevision(Path root) {
        Pattern commit = Pattern.compile(COMMIT_ROW);
        for (String line : ledger(root)) {
            Matcher m = commit.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("no pinned-release Commit row in " + root.resolve(LEDGER)
                + "; every citation is resolved against the commit that row names");
    }

    private static List<String> ledger(Path root) {
        try {
            return Files.readAllLines(root.resolve(LEDGER), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(LEDGER + " could not be read under " + root, e);
        }
    }

    /** One pinned tree, listed once, with a name index for the citations that carry no path. */
    private static final class Tree {

        private final List<String> paths;
        private final Set<String> all;
        private final Map<String, List<String>> byName = new TreeMap<>();

        Tree(Path root, String revision) {
            paths = List.copyOf(git(root, "ls-tree", "-r", "--name-only", revision));
            all = new TreeSet<>(paths);
            for (String path : paths) {
                byName.computeIfAbsent(path.substring(path.lastIndexOf('/') + 1), k -> new ArrayList<>())
                        .add(path);
            }
        }

        /**
         * Every file of this tree the citation could mean: the path itself when it is one, and
         * otherwise every file whose path ends the way the citation does — and, when the citation
         * elides its middle, begins the way it does too. The caller reports none and more than one;
         * choosing between them here is what would turn a citation into a guess.
         */
        List<String> locate(String written) {
            String path = elided(written);
            if (!path.contains(ELISION)) {
                if (all.contains(path)) {
                    return List.of(path);
                }
                if (!path.contains("/")) {
                    return byName.getOrDefault(path, List.of());
                }
                return paths.stream().filter(p -> p.endsWith("/" + path)).toList();
            }
            int elision = path.lastIndexOf(ELISION);
            String prefix = head(path.substring(0, elision));
            String suffix = "/" + tail(path.substring(elision + ELISION.length()));
            return paths.stream().filter(p -> p.startsWith(prefix) && p.endsWith(suffix)).toList();
        }
    }

    /** A written path with either spelling of an elided middle marked, so one rule reads both. */
    private static String elided(String written) {
        return written.replace("…", ELISION).replace("...", ELISION);
    }

    /** What an elided path says before the elision, as a prefix a tree path starts with. */
    private static String head(String before) {
        String prefix = before.endsWith("/") ? before.substring(0, before.length() - 1) : before;
        return prefix.isEmpty() ? "" : prefix + "/";
    }

    /** What an elided path says after the elision, without the separator that led into it. */
    private static String tail(String after) {
        return after.startsWith("/") ? after.substring(1) : after;
    }

    /**
     * How many lines each of the named objects has, keyed by the {@code <revision>:<path>} that
     * named it. One {@code git cat-file --batch} reads them all: a process per cited file was most
     * of a minute of the build, and the answer is the same.
     */
    static Map<String, Integer> lineCounts(Path root, List<String> objects) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (objects.isEmpty()) {
            return counts;
        }
        ProcessBuilder builder = new ProcessBuilder("git", "--no-pager", "cat-file", "--batch")
                .directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            // The names go down one thread while the answers come back up this one. git answers
            // while it reads, and a pipe holds a few kilobytes: writing every name first and
            // reading afterwards stops both processes against a full buffer, which is a hang with
            // no message, on Windows at four kilobytes of names.
            Thread names = new Thread(() -> {
                try (OutputStream to = process.getOutputStream()) {
                    to.write(String.join("\n", objects).getBytes(StandardCharsets.UTF_8));
                    to.write('\n');
                } catch (IOException closed) {
                    // git stopped reading; the read side below reports what is missing.
                    process.destroy();
                }
            }, "git-cat-file-names");
            names.setDaemon(true);
            names.start();
            try (InputStream from = process.getInputStream()) {
                for (String object : objects) {
                    String header = header(from);
                    if (header == null) {
                        break;
                    }
                    String[] fields = header.split(" ");
                    if (fields.length < 3) {
                        // "<object> missing": no body follows, and the caller reports the object.
                        continue;
                    }
                    byte[] body = read(from, Integer.parseInt(fields[2]));
                    // git writes one newline after each object's bytes, whatever the object held.
                    if (from.read() < 0) {
                        throw new IOException("git cat-file --batch ended after " + object);
                    }
                    counts.put(object, lines(body));
                }
            }
            names.join();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("git cat-file --batch exited non-zero in " + root);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("git cat-file --batch could not be run in " + root, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted running git cat-file --batch in " + root, e);
        }
        return counts;
    }

    /** How many lines a file has: a last line without a terminator is a line all the same. */
    private static int lines(byte[] body) {
        if (body.length == 0) {
            return 0;
        }
        int count = 0;
        for (byte b : body) {
            if (b == '\n') {
                count++;
            }
        }
        return body[body.length - 1] == '\n' ? count : count + 1;
    }

    private static String header(InputStream from) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = from.read()) != -1 && b != '\n') {
            line.write(b);
        }
        return b == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
    }

    private static byte[] read(InputStream from, int size) throws IOException {
        byte[] body = new byte[size];
        int read = 0;
        while (read < size) {
            int got = from.read(body, read, size - read);
            if (got < 0) {
                throw new IOException("git cat-file --batch ended mid-object");
            }
            read += got;
        }
        return body;
    }

    /** What one git invocation said and how it exited. */
    record Run(int status, List<String> out, List<String> err) {
    }

    /** Runs git at the repository root, failing the check rather than skipping it. */
    static List<String> git(Path root, String... args) {
        Run run = run(root, args);
        if (run.status() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " exited " + run.status()
                    + " in " + root + ":\n" + String.join("\n", run.err())
                    + "\n(a checkout without full history cannot run this check; continuous"
                    + " integration checks out with fetch-depth: 0)");
        }
        return run.out();
    }

    /**
     * Runs git and hands back what it said. Every setting the parsing depends on is pinned as
     * configuration rather than as a flag, as {@code Ledger} does it, because a check whose answer
     * depends on the reviewer's git configuration is not a check.
     */
    static Run run(Path root, String... args) {
        List<String> command = new ArrayList<>(List.of("git",
                "-c", "core.quotepath=false",
                "-c", "color.ui=never",
                "--no-pager"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).start();
            List<String> out = drain(process.getInputStream());
            List<String> err = drain(process.getErrorStream());
            return new Run(process.waitFor(), out, err);
        } catch (IOException e) {
            throw new UncheckedIOException("git could not be run in " + root, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted running " + String.join(" ", command), e);
        }
    }

    private static List<String> drain(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The repository root, found from the working directory by what a checkout holds. */
    static Path root() {
        Path here = Path.of("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(LEDGER)) && Files.isDirectory(p.resolve("core/src/main/java"))) {
                return p;
            }
        }
        throw new IllegalStateException("no Shatterfish checkout above " + here);
    }
}
