package org.shatterfish.codex;

import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date gating for lore claims (story 7.9, FR-18, ADR-0021 amendment): a claim's date and the
 * version it names, checked against when the thing it describes changed.
 *
 * <p>The evidence is the game's own changelog in {@code codex/<tag>/changelog.json} and the text of
 * the changelist files it cites, and a committed table of release dates, {@link #TABLE}, that is
 * derived from that same changelog with a {@code path:line} source on every row. The gate never
 * changes a Tier. It flags, and it says what a flagged claim cannot become until a person or a
 * citation says so.
 *
 * <p>It lives in the Codex's test sources beside {@link Lore}, for the reason ADR-0021 gives: the
 * leak gate denies the generator a process and a print stream, and the gate needs neither a game
 * class nor the network. It reads files and nothing else.
 *
 * <p>The verdicts, in the order they are decided:
 *
 * <ul>
 *   <li>{@code undated}: the claim names no parseable version and carries no date. Nothing can be
 *       compared, so it is flagged and, without a code citation, capped at Tier 3.
 *   <li>{@code changed-since}: the claim has an anchor (a version, a bound such as {@code pre-v4},
 *       or a date turned into a release through the table) and a changelog heading that names its
 *       subject sits in a later release.
 *   <li>{@code clean}: anchored, the subject has changelog headings, none of them later.
 *   <li>{@code no-entry}: anchored, and no changelog heading names the subject at all. It passes
 *       unflagged and is counted, so that a gate that finds nothing cannot look like a gate that
 *       found nothing wrong.
 * </ul>
 */
final class DateGate {

    private DateGate() {
    }

    /** The release table, relative to the repository root. */
    static final String TABLE = "lore/release-dates.json";

    /** The verdicts. */
    static final String UNDATED = "undated";
    static final String CHANGED_SINCE = "changed-since";
    static final String CLEAN = "clean";
    static final String NO_ENTRY = "no-entry";

    // ------------------------------------------------------------------------------ versions

    /** A release number: {@code v3.3} is (3, 3, 0). */
    record Version(int major, int minor, int patch) implements Comparable<Version> {

        private static final Pattern LABEL = Pattern.compile("v(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

        /** The version a label such as {@code v3.3} or {@code v0.4.3} names, or null for any other text. */
        static Version label(String text) {
            Matcher m = LABEL.matcher(text.strip());
            return m.matches() ? of(m) : null;
        }

        private static Version of(Matcher m) {
            return new Version(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    m.group(3) == null ? 0 : Integer.parseInt(m.group(3)));
        }

        @Override
        public int compareTo(Version other) {
            return Comparator.comparingInt(Version::major).thenComparingInt(Version::minor)
                    .thenComparingInt(Version::patch).compare(this, other);
        }

        @Override
        public String toString() {
            return "v" + major + "." + minor + "." + patch;
        }
    }

    /**
     * One release the changelog labels. A release with no date in the changelog (the two patch
     * releases of v4.0) carries null for the date fields and is not in the table.
     */
    record Release(String label, Version version, LocalDate released, String asWritten, String source) {

        boolean dated() {
            return released != null;
        }
    }

    /**
     * One heading of the changelog: the entry it sits in (by position), the release it belongs to
     * (null before any version, and in the vanilla history), what it says, and the lowercase text a
     * subject is looked for in (the heading's title and its body).
     */
    record Change(int entry, Release release, String entryClass, String entryTitle, String heading, String citation,
                  String text) {
    }

    // ------------------------------------------------------------------------------ the changelog

    /** What the changelog holds, counted for the finding. */
    record Stats(int entries, int classes, int entryDates, int headings, int datedHeadings, int entriesWithDatedHeading,
                 int versionClassEntries, int labelledEntries, int releases, int datedReleases) {
    }

    /** The changelog read: releases in order, every heading (with its release where it has one), and the counts. */
    record Log(List<Release> releases, List<Change> changes, Stats stats) {
    }

    /**
     * The objects of a Codex or table file that writes one object per line: the harness's reader
     * takes compact JSON in sorted order, and these files put each row on a line of its own.
     */
    static List<String> objectLines(String text) {
        List<String> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("  {")) {
                rows.add(line.strip().replaceAll(",$", ""));
            }
        }
        return rows;
    }

    private static final Pattern VERSION_CLASS = Pattern.compile("v\\d+_(?:\\d+_)?X_Changes");
    private static final Pattern DATE = Pattern.compile("([A-Za-z]+) (\\d{1,2})(?:st|nd|rd|th)?, (\\d{4})");
    private static final List<String> MONTHS =
            List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

    /** The date a changelog phrase such as {@code Mar 18th, 2019} states. */
    static LocalDate date(String asWritten) {
        Matcher m = DATE.matcher(asWritten);
        if (!m.find()) {
            throw new IllegalArgumentException("not a date as the game writes one: " + asWritten);
        }
        int month = MONTHS.indexOf(m.group(1).substring(0, Math.min(3, m.group(1).length())).toLowerCase(Locale.ROOT));
        if (month < 0) {
            throw new IllegalArgumentException("not a month: " + m.group(1) + " in " + asWritten);
        }
        return LocalDate.of(Integer.parseInt(m.group(3)), month + 1, Integer.parseInt(m.group(2)));
    }

    /**
     * Reads {@code codex/<tag>/changelog.json} and the changelist sources it cites. A release is a
     * title the game writes as a version ({@code v3.3}, {@code v0.4.3}); a title that extends the
     * release above it ({@code v4.0.2} under {@code v4.0}) is a patch release whose headings are
     * its own, and the entries that follow it belong to the release above again. An entry before
     * any version, or in the vanilla history, belongs to no release and is counted, not gated.
     */
    static Log read(Path root, String tag) {
        String text = Lore.read(root.resolve("codex").resolve(tag).resolve("changelog.json")).strip();
        List<String> entries = objectLines(text);
        List<Release> releases = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        Map<String, List<String>> sources = new HashMap<>();
        int classes = 0;
        int entryIndex = -1;
        int entryDates = 0;
        int headings = 0;
        int datedHeadings = 0;
        int entriesWithDatedHeading = 0;
        int versionClassEntries = 0;
        int labelledEntries = 0;
        String lastClass = null;
        Release base = null;
        for (String raw : entries) {
            Map<String, String> entry = Json.object(raw);
            entryIndex++;
            String className = Json.string(entry.get("className"));
            String simple = className.substring(className.lastIndexOf('.') + 1);
            if (!className.equals(lastClass)) {
                classes++;
                lastClass = className;
                base = null;
            }
            String title = Json.string(entry.get("title"));
            if (!Json.array(entry.get("dates")).isEmpty()) {
                entryDates++;
            }
            if (VERSION_CLASS.matcher(simple).matches()) {
                versionClassEntries++;
            }
            Version labelled = Version.label(title);
            Release release = base;
            if (labelled != null) {
                if (base != null && title.startsWith(base.label() + ".")) {
                    release = patch(releases, title, labelled);
                } else {
                    base = new Release(title, labelled, null, null, null);
                    release = base;
                    releases.add(release);
                }
            }
            if (release != null) {
                labelledEntries++;
            }
            boolean anyDated = false;
            for (String rawHeading : Json.array(entry.get("headings"))) {
                headings++;
                Map<String, String> heading = Json.object(rawHeading);
                Map<String, String> where = Json.object(heading.get("citation"));
                String path = Json.string(where.get("path"));
                int line = Json.integer(where.get("line"));
                String headingTitle = Json.string(heading.get("title"));
                List<String> dates = Json.array(heading.get("dates"));
                Body body = body(root, sources, path, line);
                if (!dates.isEmpty()) {
                    datedHeadings++;
                    anyDated = true;
                    if (release != null && !release.dated()) {
                        String asWritten = Json.string(dates.get(0));
                        Release dated = new Release(release.label(), release.version(), date(asWritten), asWritten,
                                path + ":" + body.lineOf(asWritten, line));
                        releases.set(releases.indexOf(release), dated);
                        if (base == release) {
                            base = dated;
                        }
                        release = dated;
                    }
                }
                changes.add(new Change(entryIndex, release, className, title, headingTitle, path + ":" + line,
                        (headingTitle + " " + body.text()).toLowerCase(Locale.ROOT)));
            }
            if (anyDated) {
                entriesWithDatedHeading++;
            }
        }
        // A change made before its release's date was read still points at the undated copy.
        Map<String, Release> final_ = new LinkedHashMap<>();
        for (Release r : releases) {
            final_.put(r.label(), r);
        }
        List<Change> settled = new ArrayList<>();
        for (Change c : changes) {
            settled.add(new Change(c.entry(), c.release() == null ? null : final_.get(c.release().label()), c.entryClass(),
                    c.entryTitle(), c.heading(), c.citation(), c.text()));
        }
        int datedReleases = (int) final_.values().stream().filter(Release::dated).count();
        return new Log(List.copyOf(final_.values()), settled, new Stats(entries.size(), classes, entryDates, headings,
                datedHeadings, entriesWithDatedHeading, versionClassEntries, labelledEntries, final_.size(), datedReleases));
    }

    private static Release patch(List<Release> releases, String title, Version version) {
        Release patch = new Release(title, version, null, null, null);
        releases.add(patch);
        return patch;
    }

    /** The string literals of one {@code new ChangeButton(...)}, joined, and the line each starts on. */
    record Body(String text, List<String> literals, List<Integer> lines) {

        /** The line of the first literal that holds {@code phrase}, or {@code fallback}. */
        int lineOf(String phrase, int fallback) {
            for (int i = 0; i < literals.size(); i++) {
                if (literals.get(i).contains(phrase)) {
                    return lines.get(i);
                }
            }
            return fallback;
        }
    }

    private static Body body(Path root, Map<String, List<String>> cache, String path, int line) {
        List<String> file = cache.computeIfAbsent(path, p -> {
            try {
                return Files.readAllLines(root.resolve(p), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(p + " could not be read; the changelog cites it", e);
            }
        });
        StringBuilder joined = new StringBuilder();
        for (int i = line - 1; i < file.size(); i++) {
            joined.append(file.get(i)).append('\n');
        }
        String text = joined.toString();
        int open = text.indexOf("new ChangeButton(");
        if (open < 0) {
            throw new IllegalStateException(path + ":" + line + " is cited as a heading and holds no ChangeButton");
        }
        List<String> literals = new ArrayList<>();
        List<Integer> lines = new ArrayList<>();
        int depth = 1;
        int row = line;
        for (int i = open + "new ChangeButton(".length(); depth > 0; i++) {
            if (i >= text.length()) {
                throw new IllegalStateException(path + ":" + line + ": a ChangeButton that does not close");
            }
            char c = text.charAt(i);
            if (c == '\n') {
                row++;
            } else if (c == '"') {
                StringBuilder lit = new StringBuilder();
                int startRow = row;
                int j = i + 1;
                while (text.charAt(j) != '"') {
                    char d = text.charAt(j);
                    if (d == '\\') {
                        char e = text.charAt(++j);
                        lit.append(e == 'n' || e == 't' ? ' ' : e);
                    } else {
                        lit.append(d);
                    }
                    j++;
                }
                literals.add(lit.toString());
                lines.add(startRow);
                i = j;
            } else if (c == '\'') {
                i = text.indexOf('\'', text.charAt(i + 1) == '\\' ? i + 3 : i + 2);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return new Body(String.join(" ", literals), literals, lines);
    }

    // ------------------------------------------------------------------------------ the table

    /** One row of {@link #TABLE}. */
    record Row(String version, String released, String asWritten, String source) {
    }

    /** The dated releases of a changelog, oldest first: what the table must say. */
    static List<Row> derive(Log log) {
        List<Release> dated = new ArrayList<>(log.releases().stream().filter(Release::dated).toList());
        dated.sort(Comparator.comparing(Release::version));
        List<Row> rows = new ArrayList<>();
        for (Release r : dated) {
            rows.add(new Row(r.label(), r.released().toString(), r.asWritten(), r.source()));
        }
        return rows;
    }

    private static final String CONSUMER = "the date gate of story 7.9 (DateGate): a claim's date becomes a release through this table, and "
            + "a release is later than another by its number; every row cites the line of the game's own changelog that states the date";

    /** The file for these rows, in the one form the writer produces. */
    static String render(String tag, List<Row> rows) {
        StringBuilder out = new StringBuilder("{\n");
        out.append("\"consumer\":").append(Lore.quote(CONSUMER)).append(",\n");
        out.append("\"releases\":[\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out.append("  {\"as_written\":").append(Lore.quote(r.asWritten()))
                    .append(",\"released\":").append(Lore.quote(r.released()))
                    .append(",\"source\":").append(Lore.quote(r.source()))
                    .append(",\"version\":").append(Lore.quote(r.version())).append('}')
                    .append(i + 1 < rows.size() ? ",\n" : "\n");
        }
        return out.append("],\n\"tag\":").append(Lore.quote(tag)).append("\n}\n").toString();
    }

    /** The table as committed, or a reason it cannot be read. */
    record Table(String tag, List<Row> rows) {
    }

    static Table readTable(String text) {
        List<Row> rows = new ArrayList<>();
        String tag = "";
        for (String line : text.split("\n")) {
            if (line.startsWith("\"tag\":")) {
                tag = Json.string(line.substring(6).strip());
            }
        }
        for (String raw : objectLines(text)) {
            Map<String, String> m = Json.object(raw);
            rows.add(new Row(field(m, "version"), field(m, "released"), field(m, "as_written"), field(m, "source")));
        }
        return new Table(tag, rows);
    }

    private static String field(Map<String, String> row, String key) {
        String raw = row.get(key);
        return raw == null ? "" : Json.string(raw);
    }

    private static final Pattern SOURCE = Pattern.compile("([A-Za-z0-9_][A-Za-z0-9_./-]*\\.java):(\\d+)");

    /**
     * What is wrong with the rows, as sentences. A row with no source is not accepted: the table is
     * data whose provenance is the point. A source must be a {@code path:line} that resolves at the
     * tag and whose line states the date the row carries.
     */
    static List<String> problems(Path root, String tag, List<Row> rows) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Version last = null;
        LocalDate lastDate = null;
        List<DocsCitations.Citation> cites = new ArrayList<>();
        for (Row r : rows) {
            String who = r.version().isEmpty() ? "a row with no version" : r.version();
            Version v = Version.label(r.version());
            if (v == null) {
                problems.add(who + ": the version is not v<major>.<minor>[.<patch>]");
                continue;
            }
            if (!seen.add(r.version())) {
                problems.add(who + ": listed twice");
            }
            if (r.source().isBlank()) {
                problems.add(who + ": has no source, and a row with no source is not accepted");
                continue;
            }
            LocalDate released;
            try {
                released = LocalDate.parse(r.released());
            } catch (java.time.format.DateTimeParseException bad) {
                problems.add(who + ": released `" + r.released() + "` is not a yyyy-mm-dd date");
                continue;
            }
            if (r.released().length() != 10) {
                problems.add(who + ": released `" + r.released() + "` is not yyyy-mm-dd");
            }
            if (r.asWritten().isBlank()) {
                problems.add(who + ": says nothing about how the source words the date");
            } else {
                try {
                    if (!date(r.asWritten()).equals(released)) {
                        problems.add(who + ": released " + r.released() + " is not the date `" + r.asWritten() + "` says");
                    }
                } catch (IllegalArgumentException bad) {
                    problems.add(who + ": " + bad.getMessage());
                }
            }
            if (last != null && v.compareTo(last) <= 0) {
                problems.add(who + ": not after " + last + "; the table is oldest first");
            }
            if (lastDate != null && released.isBefore(lastDate)) {
                problems.add(who + ": released before the release above it");
            }
            last = v;
            lastDate = released;
            Matcher m = SOURCE.matcher(r.source());
            if (!m.matches()) {
                problems.add(who + ": the source `" + r.source() + "` is not path:line of a changelist file");
                continue;
            }
            int line = Integer.parseInt(m.group(2));
            cites.add(new DocsCitations.Citation(who, 1, r.source(), m.group(1), line, line, tag, false, false));
            Path file = root.resolve(m.group(1));
            if (!Files.isRegularFile(file)) {
                continue;
            }
            List<String> lines = readLines(file);
            if (line < 1 || line > lines.size() || !lines.get(line - 1).contains(r.asWritten())) {
                problems.add(who + ": line " + line + " of " + m.group(1) + " does not say `" + r.asWritten() + "`");
            }
        }
        List<DocsCitations.Finding> unresolved = new ArrayList<>();
        DocsCitations.resolve(root, cites, unresolved);
        for (DocsCitations.Finding f : unresolved) {
            problems.add(f.page() + ": source does not resolve: " + f.rule().said + " -- " + f.detail());
        }
        return problems;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(file + " could not be read", e);
        }
    }

    // ------------------------------------------------------------------------------ the vocabulary

    /** The names the Codex's vocabulary gives a class, the one way a claim's subject is tied to the words the changelog uses. */
    static final class Names {

        private final Map<String, Set<String>> byClass = new HashMap<>();
        private final Set<String> all = new TreeSet<>();

        static Names empty() {
            return new Names("");
        }

        static Names of(Path root, String tag) {
            return new Names(Lore.read(root.resolve("codex").resolve(tag).resolve("vocabulary.json")).strip());
        }

        Names(String vocabularyJson) {
            for (String raw : objectLines(vocabularyJson)) {
                Map<String, String> entry = Json.object(raw);
                String name = Json.string(entry.get("name")).toLowerCase(Locale.ROOT);
                all.add(name);
                String here = entry.get("here");
                if (here == null) {
                    continue;
                }
                for (String cls : Json.array(Json.object(here).get("classNames"))) {
                    byClass.computeIfAbsent(Json.string(cls), k -> new TreeSet<>()).add(name);
                }
            }
        }

        /** Every name the table holds, for counting what a heading names. */
        Set<String> all() {
            return all;
        }

        /**
         * The words a claim's subject goes by: the subject without a trailing parenthesis, the
         * vocabulary's names for the subject's class (and its outer class), and the class's own name
         * split at its capitals. The nested class's name is used, not the outer one, so a Fire
         * Elemental is not looked for as "elemental".
         */
        Set<String> terms(String subject, String subjectClass) {
            Set<String> terms = new TreeSet<>();
            if (subject != null) {
                String bare = subject.replaceAll("\\s*\\(.*\\)", "").strip().toLowerCase(Locale.ROOT);
                if (!bare.isEmpty()) {
                    terms.add(bare);
                }
            }
            if (subjectClass != null) {
                String[] parts = subjectClass.split("\\.");
                StringBuilder outer = new StringBuilder();
                String lastCapital = null;
                String previousCapital = null;
                boolean seenCapital = false;
                for (String p : parts) {
                    if (!seenCapital) {
                        outer.append(outer.length() == 0 ? "" : ".").append(p);
                    }
                    if (!p.isEmpty() && Character.isUpperCase(p.charAt(0))) {
                        seenCapital = true;
                        previousCapital = lastCapital;
                        lastCapital = p;
                    }
                }
                // A nested class named for one word (Fire, Frost, Red) is a variant, not a name: the class around it is.
                if (lastCapital != null && previousCapital != null && lastCapital.matches("[A-Z][a-z0-9]*")) {
                    lastCapital = previousCapital;
                }
                terms.addAll(byClass.getOrDefault(subjectClass, Set.of()));
                terms.addAll(byClass.getOrDefault(outer.toString(), Set.of()));
                if (lastCapital != null) {
                    terms.add(lastCapital.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").toLowerCase(Locale.ROOT));
                }
            }
            terms.removeIf(t -> t.length() < 3);
            return terms;
        }
    }

    /** The pattern that finds a term as a word, with an optional plural. */
    static Pattern word(String term) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?:e?s)?(?![a-z0-9])");
    }

    /** Whether any of the terms is a word of the text. */
    static boolean names(Set<String> terms, String text) {
        for (String term : terms) {
            if (word(term).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------ the claim and the gate

    /** The fields of a claim the gate reads. */
    record Claim(String id, String subject, String subjectClass, String date, String versionClaimed, String tier,
                 List<String> cites, String text) {

        static Claim of(Lore.Doc doc) {
            return new Claim(doc.get("id"), doc.get("subject"), doc.get("subject_class"), doc.get("date"),
                    doc.get("version_claimed"), doc.get("tier"), doc.list("cites"), doc.get("claim"));
        }

        private static final Pattern JAVA_CITE = Pattern.compile(".*\\.java:\\d+(?:-\\d+)?");

        /** Whether the claim cites a line of game code, the one thing that lets an undated claim stand above Tier 3. */
        boolean codeCited() {
            return cites.stream().anyMatch(c -> JAVA_CITE.matcher(c).matches());
        }
    }

    /**
     * Where a claim sits in the release order: exactly at a version, or before one (a claim that
     * says {@code pre-v4} holds at some release below v4.0, so every release from v4.0 on is later).
     */
    record Anchor(boolean before, Version version, String how) {

        boolean later(Version change) {
            return before ? change.compareTo(version) >= 0 : change.compareTo(version) > 0;
        }
    }

    private static final Pattern BOUND = Pattern.compile("(?i)\\b(?:pre|before|prior to)[- ]?v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
    private static final Pattern NAMED = Pattern.compile("(?i)(?<![\\w.])v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?![\\w.])");

    /**
     * The anchor a claim gives, or null. A version the claim names wins over its date (a claim
     * posted in 2026 about {@code v3.0} is about v3.0). A bound ({@code pre-v4}) is a bound. Where
     * several versions are named the highest is taken: it is the latest release the claim is said
     * to hold in, so a change after it is a change after the claim. A date becomes the newest
     * release of the table at or before it.
     */
    static Anchor anchor(Claim claim, List<Row> table) {
        String said = claim.versionClaimed() == null ? "" : claim.versionClaimed();
        Matcher bound = BOUND.matcher(said);
        if (bound.find()) {
            Version v = new Version(Integer.parseInt(bound.group(1)),
                    bound.group(2) == null ? 0 : Integer.parseInt(bound.group(2)),
                    bound.group(3) == null ? 0 : Integer.parseInt(bound.group(3)));
            return new Anchor(true, v, "version_claimed says `" + said + "`: some release before " + v);
        }
        Version top = null;
        Matcher named = NAMED.matcher(said);
        while (named.find()) {
            Version v = new Version(Integer.parseInt(named.group(1)), Integer.parseInt(named.group(2)),
                    named.group(3) == null ? 0 : Integer.parseInt(named.group(3)));
            if (top == null || v.compareTo(top) > 0) {
                top = v;
            }
        }
        if (top != null) {
            return new Anchor(false, top, "version_claimed says `" + said + "`");
        }
        if (claim.date() != null && !claim.date().equals("unknown") && LoreCheck.date(claim.date())) {
            LocalDate posted = LocalDate.parse(claim.date());
            Row current = null;
            for (Row r : table) {
                if (!LocalDate.parse(r.released()).isAfter(posted)) {
                    current = r;
                }
            }
            Version v = current == null ? new Version(0, 0, 0) : Version.label(current.version());
            return new Anchor(false, v, "date " + claim.date() + " falls in " + (current == null ? "no release" : current.version())
                    + " (release-dates.json)");
        }
        return null;
    }

    /** What the gate says about one claim. */
    record Verdict(String id, String status, Anchor anchor, List<Change> later, int history, Set<String> terms,
                   int ceiling, boolean codeCited) {

        boolean flagged() {
            return status.equals(UNDATED) || status.equals(CHANGED_SINCE);
        }
    }

    /**
     * The gate: the table, every change that belongs to a release, and the vocabulary. It holds no
     * state a claim could change, and it has no way to write a Tier.
     */
    static final class Gate {

        private final List<Row> table;
        private final List<Change> changes;
        private final Names names;

        Gate(List<Row> table, List<Change> changes, Names names) {
            this.table = List.copyOf(table);
            this.changes = List.copyOf(changes);
            this.names = names;
        }

        static Gate of(Path root, String tag) {
            Log log = read(root, tag);
            Table committed = readTable(Lore.read(root.resolve(TABLE)));
            return new Gate(committed.rows(), log.changes(), Names.of(root, tag));
        }

        Verdict check(Claim claim) {
            Set<String> terms = names.terms(claim.subject(), claim.subjectClass());
            List<Change> mine = new ArrayList<>();
            for (Change c : changes) {
                if (c.release() != null && names(terms, c.text())) {
                    mine.add(c);
                }
            }
            mine.sort(Comparator.comparing((Change c) -> c.release().version()));
            Anchor anchor = anchor(claim, table);
            if (anchor == null) {
                return verdict(claim, UNDATED, null, List.of(), mine.size(), terms);
            }
            List<Change> later = mine.stream().filter(c -> anchor.later(c.release().version())).toList();
            String status = !later.isEmpty() ? CHANGED_SINCE : mine.isEmpty() ? NO_ENTRY : CLEAN;
            return verdict(claim, status, anchor, later, mine.size(), terms);
        }

        /** A date alone never lifts a Tier, and a flag without a code citation caps it at 3. */
        private static Verdict verdict(Claim claim, String status, Anchor anchor, List<Change> later, int history, Set<String> terms) {
            boolean flagged = status.equals(UNDATED) || status.equals(CHANGED_SINCE);
            int ceiling = flagged && !claim.codeCited() ? 3 : 1;
            return new Verdict(claim.id(), status, anchor, later, history, terms, ceiling, claim.codeCited());
        }

        /** Whether the claim's Tier is above what the gate lets a date support: a Tier 1 or 2 that is flagged and cites no code. */
        static boolean exceeds(Claim claim, Verdict verdict) {
            return verdict.ceiling() >= 3 && (claim.tier().equals("1") || claim.tier().equals("2"));
        }
    }

    /** Every claim under {@code loreDir}, in id order within group. */
    static List<Claim> claims(Path loreDir) {
        List<Claim> out = new ArrayList<>();
        for (Path file : Lore.files(loreDir)) {
            if (!LoreCheck.isClaimPath(loreDir, file)) {
                continue;
            }
            try {
                out.add(Claim.of(Lore.parse(file.getFileName().toString(), Lore.read(file))));
            } catch (Lore.Unreadable bad) {
                throw new IllegalStateException(file + ": " + bad.getMessage());
            }
        }
        out.sort(Comparator.comparing((Claim c) -> c.id().replaceAll("-\\d+$", "")).thenComparingInt(c -> Lore.number(c.id())));
        return out;
    }
}
