package org.shatterfish.codex;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lore checker (story 7.7, FR-18, ADR-0021): every file under {@code lore/} validated, in
 * {@code ./gradlew build} through {@code LoreCheckTest} and on demand through
 * {@code ./gradlew :codex:lore --args=check}.
 *
 * <p>It checks what a person cannot be trusted to keep checking: the fields and their allowed
 * values, a URL that is a URL, a date that is a date, an {@code implemented_in} name that names
 * something, and the one rule that makes a Tier mean something: a Tier 1 or 2 claim carries a
 * {@code path:line} citation, and the citation resolves at the tag the claim was graded against,
 * through the same resolver the documentation uses ({@link DocsCitations#resolve}).
 *
 * <p>A verdict is graded against one tag and says so ({@code graded_against}). The checker does
 * not move it. What it holds is that the file says honestly whether it still stands at the pin:
 * a claim graded at an older tag is {@code needs-review} exactly when a line it cites reads
 * differently at the pin than it did where the verdict was written, and {@code current} when every
 * cited line reads the same. Both directions, as the rules tables hold theirs.
 */
final class LoreCheck {

    private LoreCheck() {
    }

    /** One thing wrong with one file. */
    record Finding(String file, String message) {

        @Override
        public String toString() {
            return file + "  " + message;
        }
    }

    static final Set<String> TIERS = Set.of("1", "2", "3", "F");
    static final Set<String> SOURCE_TYPES = Set.of("wiki", "forum", "guide", "video", "developer", "other");
    static final Set<String> STATUSES = Set.of("current", "needs-review");

    /** The keys every claim file carries. {@code subject}, {@code subject_class}, {@code quote} and {@code card} are optional. */
    static final List<String> REQUIRED = List.of("id", "topic", "group", "source_url", "source_type", "date",
            "retrieved", "variant", "version_claimed", "tier", "graded_against", "status", "claim", "cites",
            "implemented_in");

    /** The longest paraphrase a claim may be, in characters: the longest of the 144 first claims was 533. */
    static final int CLAIM_MAX = 600;

    /** The longest quotation a claim may carry (the body rule: a paraphrase and at most a short quote). */
    static final int QUOTE_MAX = 200;

    private static final Pattern SLUG = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern VARIANT = Pattern.compile("spd|pd|unknown|mod:[a-z0-9][a-z0-9-]*");
    private static final Pattern TAG = Pattern.compile("v[0-9]+(\\.[0-9]+)*");
    private static final Pattern CITE = Pattern.compile("([A-Za-z0-9_][A-Za-z0-9_./…-]*\\.[A-Za-z][A-Za-z0-9]*):(\\d+)(?:-(\\d+))?");

    /** Whether this claim file is a candidate to be checked, as {@code lore/<topic>/<id>.md}. */
    static boolean isClaimPath(Path loreDir, Path file) {
        Path relative = loreDir.relativize(file);
        return relative.getNameCount() == 2 && relative.getFileName().toString().endsWith(".md");
    }

    /**
     * Every finding over every claim under {@code loreDir}. {@code root} is the checkout that
     * citations are resolved in.
     */
    static List<Finding> check(Path root, Path loreDir, Lore.Registry registry) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Lore.Doc> docs = new LinkedHashMap<>();
        Map<String, Path> where = new HashMap<>();
        for (Path file : Lore.files(loreDir)) {
            String name = name(loreDir, file);
            if (!isClaimPath(loreDir, file)) {
                findings.add(new Finding(name, "not a claim file: claims are lore/<topic>/<id>.md and nothing else lives under lore/"));
                continue;
            }
            Lore.Doc doc;
            try {
                doc = Lore.parse(name, Lore.read(file));
            } catch (Lore.Unreadable bad) {
                findings.add(new Finding(name, bad.getMessage()));
                continue;
            }
            String topic = loreDir.relativize(file).getName(0).toString();
            String stem = file.getFileName().toString().replaceFirst("\\.md$", "");
            validate(doc, registry, findings);
            if (doc.has("id") && !stem.equals(doc.get("id"))) {
                findings.add(new Finding(name, "the file is named " + stem + " and the id says " + doc.get("id")));
            }
            if (doc.has("topic") && !topic.equals(doc.get("topic"))) {
                findings.add(new Finding(name, "the folder is " + topic + " and the topic says " + doc.get("topic")));
            }
            String id = doc.get("id");
            if (id != null) {
                if (docs.containsKey(id)) {
                    findings.add(new Finding(name, "the id " + id + " is also " + where.get(id)));
                } else {
                    docs.put(id, doc);
                    where.put(id, file);
                }
            }
        }
        citations(root, new ArrayList<>(docs.values()), findings);
        findings.sort(java.util.Comparator.comparing(Finding::file).thenComparing(Finding::message));
        return findings;
    }

    private static String name(Path loreDir, Path file) {
        return loreDir.getFileName() + "/" + loreDir.relativize(file).toString().replace('\\', '/');
    }

    /** The checks that need no git: fields, enums, URL, dates, lengths, the rejection scan, names. */
    static void validate(Lore.Doc doc, Lore.Registry registry, List<Finding> findings) {
        String name = doc.name();
        for (String key : doc.scalars().keySet()) {
            if (!Lore.KEYS.contains(key) || Lore.LISTS.contains(key)) {
                findings.add(new Finding(name, "unknown key `" + key + "`"));
            }
        }
        for (String key : doc.lists().keySet()) {
            if (!Lore.LISTS.contains(key)) {
                findings.add(new Finding(name, "`" + key + "` is not a list"));
            }
        }
        for (String key : REQUIRED) {
            if (!doc.has(key)) {
                findings.add(new Finding(name, "required field `" + key + "` is missing"));
            }
        }
        for (String key : Lore.LISTS) {
            if (doc.scalars().containsKey(key)) {
                findings.add(new Finding(name, "`" + key + "` is a list, written `" + key + ": []` or one `- item` per line"));
            }
        }
        enumerated(doc, "topic", SLUG, findings);
        enumerated(doc, "group", SLUG, findings);
        String id = doc.get("id");
        String group = doc.get("group");
        if (id != null && group != null && !id.matches(Pattern.quote(group) + "-[1-9][0-9]*")) {
            findings.add(new Finding(name, "id `" + id + "` is not `" + group + "-<n>`"));
        }
        if (doc.get("source_type") != null && !SOURCE_TYPES.contains(doc.get("source_type"))) {
            findings.add(new Finding(name, "source_type `" + doc.get("source_type") + "` is not one of "
                    + new TreeSet<>(SOURCE_TYPES)));
        }
        if (doc.get("variant") != null && !VARIANT.matcher(doc.get("variant")).matches()) {
            findings.add(new Finding(name, "variant `" + doc.get("variant")
                    + "` is not spd, pd, mod:<name> or unknown"));
        }
        if (doc.get("tier") != null && !TIERS.contains(doc.get("tier"))) {
            findings.add(new Finding(name, "tier `" + doc.get("tier") + "` is not 1, 2, 3 or F"));
        }
        if (doc.get("status") != null && !STATUSES.contains(doc.get("status"))) {
            findings.add(new Finding(name, "status `" + doc.get("status") + "` is not current or needs-review"));
        }
        if (doc.get("graded_against") != null && !TAG.matcher(doc.get("graded_against")).matches()) {
            findings.add(new Finding(name, "graded_against `" + doc.get("graded_against") + "` is not a release tag like v4.0.2"));
        }
        if (doc.get("source_url") != null && !url(doc.get("source_url"))) {
            findings.add(new Finding(name, "source_url `" + doc.get("source_url") + "` is not an http or https URL"));
        }
        if (doc.get("retrieved") != null && !date(doc.get("retrieved"))) {
            findings.add(new Finding(name, "retrieved `" + doc.get("retrieved") + "` is not a date (yyyy-mm-dd)"));
        }
        String posted = doc.get("date");
        if (posted != null && !posted.equals("unknown") && !date(posted)) {
            findings.add(new Finding(name, "date `" + posted + "` is not a date (yyyy-mm-dd) or unknown"));
        }
        String version = doc.get("version_claimed");
        if (version != null && version.isBlank()) {
            findings.add(new Finding(name, "version_claimed is empty: say `unspecified` when the source names none"));
        }
        String claim = doc.get("claim");
        if (claim != null) {
            if (claim.isBlank()) {
                findings.add(new Finding(name, "claim is empty"));
            } else if (claim.length() > CLAIM_MAX) {
                findings.add(new Finding(name, "claim is " + claim.length() + " characters; a paraphrase is at most " + CLAIM_MAX));
            }
        }
        String quote = doc.get("quote");
        if (quote != null && quote.length() > QUOTE_MAX) {
            findings.add(new Finding(name, "quote is " + quote.length() + " characters; at most " + QUOTE_MAX
                    + " are kept, the rest is paraphrase"));
        }
        if (doc.body().isBlank()) {
            findings.add(new Finding(name, "the body is empty: it holds the verdict"));
        } else if (doc.get("graded_against") != null
                && !doc.body().stripLeading().startsWith("## Verdict against `" + doc.get("graded_against") + "`")) {
            findings.add(new Finding(name, "the body does not open with `## Verdict against `"
                    + doc.get("graded_against") + "``: a verdict says which tag it was graded against"));
        }
        for (String text : new String[] {claim, quote}) {
            if (text != null) {
                LoreIntake.rejection(text).ifPresent(why -> findings.add(new Finding(name, "refused at intake: " + why)));
            }
        }
        List<String> cites = doc.list("cites");
        for (String cite : cites) {
            if (!CITE.matcher(cite).matches()) {
                findings.add(new Finding(name, "cite `" + cite + "` is not path:line or path:from-to"));
            }
        }
        String tier = doc.get("tier");
        if (("1".equals(tier) || "2".equals(tier)) && cites.isEmpty()) {
            findings.add(new Finding(name, "tier " + tier + " needs at least one path:line citation; without one it is tier 3"));
        }
        for (String entry : doc.list("implemented_in")) {
            String problem = registry.problem(entry);
            if (problem != null) {
                findings.add(new Finding(name, "implemented_in `" + entry + "`: " + problem));
            }
        }
    }

    private static void enumerated(Lore.Doc doc, String key, Pattern shape, List<Finding> findings) {
        String value = doc.get(key);
        if (value != null && !shape.matcher(value).matches()) {
            findings.add(new Finding(doc.name(), key + " `" + value + "` is not a lowercase slug"));
        }
    }

    static boolean url(String text) {
        try {
            URI uri = new URI(text);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException bad) {
            return false;
        }
    }

    static boolean date(String text) {
        try {
            LocalDate.parse(text);
            return text.length() == 10;
        } catch (DateTimeParseException bad) {
            return false;
        }
    }

    // ------------------------------------------------------------------------- the citations

    /**
     * Resolves every cite at the tag its claim was graded against, and holds the status of each
     * claim graded before the pin to what its cited lines say.
     */
    static void citations(Path root, List<Lore.Doc> docs, List<Finding> findings) {
        List<DocsCitations.Citation> all = new ArrayList<>();
        for (Lore.Doc doc : docs) {
            String ref = doc.get("graded_against");
            if (ref == null || !TAG.matcher(ref).matches()) {
                continue;
            }
            for (String cite : doc.list("cites")) {
                Matcher m = CITE.matcher(cite);
                if (m.matches()) {
                    int from = Integer.parseInt(m.group(2));
                    int to = m.group(3) == null ? from : Integer.parseInt(m.group(3));
                    all.add(new DocsCitations.Citation(doc.name(), 1, cite, m.group(1), from, to, ref, false, false));
                }
            }
        }
        List<DocsCitations.Finding> unresolved = new ArrayList<>();
        DocsCitations.resolve(root, all, unresolved);
        for (DocsCitations.Finding finding : unresolved) {
            findings.add(new Finding(finding.page(), "cite does not resolve: " + finding.rule().said + " -- " + finding.detail()));
        }
        String pin = Upstream.tag(root);
        for (Map.Entry<Lore.Doc, Expected> entry : expected(root, docs).entrySet()) {
            Lore.Doc doc = entry.getKey();
            Expected want = entry.getValue();
            String status = doc.get("status");
            if (status == null || status.equals(want.status())) {
                continue;
            }
            if (doc.get("graded_against").equals(pin)) {
                findings.add(new Finding(doc.name(), "graded at the pin " + pin + " and says " + status
                        + ": a verdict at the pin is current"));
            } else if ("needs-review".equals(want.status())) {
                findings.add(new Finding(doc.name(), "graded at " + doc.get("graded_against") + " and the pin is " + pin + "; "
                        + want.moved() + " read differently there, so the status is needs-review, not " + status));
            } else {
                findings.add(new Finding(doc.name(), "says needs-review, and every cited line reads the same at "
                        + pin + " as at " + doc.get("graded_against") + ": the status is current"));
            }
        }
    }

    /** The status a claim's citations say it has, and the cites that moved. */
    record Expected(String status, List<String> moved) {
    }

    /**
     * The status each claim should carry: {@code current} at the pin, and for a claim graded at an
     * older tag {@code needs-review} exactly when a line it cites reads differently at the pin.
     * Claims whose tag could not be read are left out; the field checks report them.
     */
    static Map<Lore.Doc, Expected> expected(Path root, List<Lore.Doc> docs) {
        String pin = Upstream.tag(root);
        Moves moves = new Moves(root, pin);
        Map<Lore.Doc, Expected> out = new LinkedHashMap<>();
        for (Lore.Doc doc : docs) {
            String graded = doc.get("graded_against");
            if (graded == null || !TAG.matcher(graded).matches()) {
                continue;
            }
            List<String> moved = new ArrayList<>();
            if (!graded.equals(pin)) {
                for (String cite : doc.list("cites")) {
                    Matcher m = CITE.matcher(cite);
                    if (m.matches() && moves.moved(graded, m.group(1), Integer.parseInt(m.group(2)),
                            m.group(3) == null ? Integer.parseInt(m.group(2)) : Integer.parseInt(m.group(3)))) {
                        moved.add(cite);
                    }
                }
            }
            out.put(doc, new Expected(moved.isEmpty() ? "current" : "needs-review", moved));
        }
        return out;
    }

    /**
     * Whether the lines a claim cites read differently at the pin than at the tag it was graded at.
     * Compared by their text, at the same line numbers: a citation is a place in a file, and a
     * place whose words changed or moved away is a verdict that has to be read again.
     */
    static final class Moves {

        private final Path root;
        private final String pin;
        private final Map<String, Optional<String>> revisions = new TreeMap<>();
        private final Map<String, DocsCitations.Tree> trees = new TreeMap<>();
        private final Map<String, List<String>> files = new HashMap<>();

        Moves(Path root, String pin) {
            this.root = root;
            this.pin = pin;
        }

        boolean moved(String graded, String path, int from, int to) {
            List<String> before = lines(graded, path);
            List<String> after = lines(pin, path);
            if (before == null || after == null) {
                return true;
            }
            if (to > before.size() || to > after.size()) {
                return true;
            }
            return !before.subList(from - 1, to).equals(after.subList(from - 1, to));
        }

        /** The file's lines at a tag, or null when the written path is not exactly one file there. */
        private List<String> lines(String ref, String path) {
            Optional<String> revision = revisions.computeIfAbsent(ref, r -> DocsCitations.revision(root, r, pin));
            if (revision.isEmpty()) {
                return null;
            }
            DocsCitations.Tree tree = trees.computeIfAbsent(revision.get(), r -> new DocsCitations.Tree(root, r));
            List<String> found = tree.locate(path);
            if (found.size() != 1) {
                return null;
            }
            String object = revision.get() + ":" + found.get(0);
            return files.computeIfAbsent(object, o -> DocsCitations.git(root, "show", o));
        }
    }

    // ------------------------------------------------------------------------------ the report

    /** The report: what was read, and the findings. */
    static String report(Path loreDir, int claims, List<Finding> findings) {
        StringBuilder text = new StringBuilder();
        text.append(claims).append(" claims under ").append(Lore.DIR).append("/ checked\n");
        if (findings.isEmpty()) {
            return text.append("no findings").toString();
        }
        text.append('\n').append(findings.size()).append(findings.size() == 1 ? " finding" : " findings").append("\n\n");
        for (Finding finding : findings.subList(0, Math.min(100, findings.size()))) {
            text.append(finding).append('\n');
        }
        if (findings.size() > 100) {
            text.append("... and ").append(findings.size() - 100).append(" more\n");
        }
        return text.toString();
    }
}
