package org.shatterfish.codex;

import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Says which game a lore claim describes (story 7.8, FR-16, FR-18, ADR-0022): {@code spd}, {@code pd},
 * {@code mod:<name>} or {@code unknown}, and why.
 *
 * <p>The evidence is the vocabulary diff in {@code codex/<tag>/vocabulary.json}: a name only this
 * game gives is {@code spd}, a name only vanilla gives is {@code pd}, and a name both give says
 * nothing unless the claim states a figure the two games state differently and the figure matches
 * one side. Around that sit three weaker signals that a claim can carry on its face: a mod named in
 * the text, a text that declares its game, and a {@code developer} source (a post by the game's
 * developer about this game is {@code spd} by construction). When the signals disagree, or there
 * are none, the answer is {@code unknown} and the result lists everything it looked at. It never
 * guesses.
 *
 * <p>Mechanics (a rule of the game with no name in the table) classify by what the text declares and
 * by the source type, and are otherwise {@code unknown}; ADR-0022 says why the table has no
 * mechanics kind.
 *
 * <p>It lives in the Codex module's test sources beside {@link Lore}, for the same reason, reads the
 * committed Codex and the text it is given, opens no connection and names no game class.
 */
final class VariantClassifier {

    /** One reason for the answer: which rule fired and what it saw. */
    record Evidence(String rule, String detail) {

        @Override
        public String toString() {
            return rule + ": " + detail;
        }
    }

    /** The answer and everything it rests on. {@code evidence} is never empty. */
    record Result(String variant, List<Evidence> evidence) {

        Result {
            evidence = List.copyOf(evidence);
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException("a variant comes with its evidence");
            }
        }

        String why() {
            StringBuilder out = new StringBuilder();
            for (Evidence e : evidence) {
                out.append(out.isEmpty() ? "" : "; ").append(e);
            }
            return out.toString();
        }
    }

    /** One side of a name: what that game calls it and the classes that carry the name. */
    private record Side(String name, List<String> classNames) {
    }

    /** One row of the diff, as the classifier needs it. */
    record Row(String kind, String name, boolean here, boolean there, List<Diff> figures, Side hereSide, Side thereSide) {

        private Row(String kind, String name, Side hereSide, Side thereSide, List<Diff> figures) {
            this(kind, name, hereSide != null, thereSide != null, figures, hereSide, thereSide);
        }

        /** Which side of the diff the name sits on. */
        String side() {
            return here && there ? "shared" : here ? "spd" : "pd";
        }
    }

    /** A comparable figure the two games state differently. */
    record Diff(String what, String here, String there) {
    }

    /** The source types the checker allows; {@code developer} is the one that decides. */
    static final String DEVELOPER = "developer";

    private final Map<String, List<Row>> byName = new LinkedHashMap<>();
    private final Map<String, List<Row>> byClass = new LinkedHashMap<>();

    /** The tag of the table, for messages. */
    final String tag;

    private final List<String> namesLongestFirst;

    /** Reads {@code codex/<tag>/vocabulary.json} of a checkout. */
    static VariantClassifier of(Path root, String tag) {
        Path file = root.resolve("codex").resolve(tag).resolve("vocabulary.json");
        try {
            return new VariantClassifier(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(file + " could not be read", e);
        }
    }

    /** Reads the file's text: one entry per line, as the Codex writes it. */
    VariantClassifier(String vocabularyJson) {
        String tagFound = null;
        for (String raw : vocabularyJson.split("\n")) {
            String line = raw.strip();
            if (line.startsWith("\"tag\":")) {
                tagFound = Json.string(line.substring(6).replaceAll(",$", ""));
            }
            if (!line.startsWith("{\"differences\"")) {
                continue;
            }
            Row row = row(Json.object(line.replaceAll(",$", "")));
            byName.computeIfAbsent(row.name(), n -> new ArrayList<>()).add(row);
            for (Side side : new Side[] {row.hereSide(), row.thereSide()}) {
                if (side != null) {
                    for (String className : side.classNames()) {
                        byClass.computeIfAbsent(className, n -> new ArrayList<>()).add(row);
                    }
                }
            }
        }
        if (byName.isEmpty() || tagFound == null) {
            throw new IllegalArgumentException("not a vocabulary file: no entries or no tag");
        }
        this.tag = tagFound;
        List<String> names = new ArrayList<>(byName.keySet());
        names.sort((a, b) -> b.length() != a.length() ? b.length() - a.length() : a.compareTo(b));
        this.namesLongestFirst = names;
    }

    private static Row row(Map<String, String> entry) {
        List<Diff> figures = new ArrayList<>();
        for (String raw : Json.array(entry.get("differences"))) {
            Map<String, String> d = Json.object(raw);
            if (Json.bool(d.get("comparable"))) {
                figures.add(new Diff(Json.string(d.get("what")), Json.string(d.get("here")), Json.string(d.get("there"))));
            }
        }
        return new Row(Json.string(entry.get("kind")), Json.string(entry.get("name")),
                side(entry.get("here")), side(entry.get("there")), figures);
    }

    private static Side side(String raw) {
        if (raw == null) {
            return null;
        }
        Map<String, String> side = Json.object(raw);
        List<String> classes = new ArrayList<>();
        for (String c : Json.array(side.get("classNames"))) {
            classes.add(Json.string(c));
        }
        return new Side(Json.string(side.get("name")), classes);
    }

    /** Whether a subject is a name or class of the table. */
    boolean knows(String subject) {
        return !lookup(subject).isEmpty();
    }

    /** How many rows the table has. */
    int rows() {
        return byName.values().stream().mapToInt(List::size).sum();
    }

    // ---- the text patterns ----

    /** Words that may stand before "Pixel Dungeon" without naming a mod. */
    private static final Set<String> NOT_A_MOD = Set.of("shattered", "original", "vanilla", "the", "a", "an", "in", "on", "of",
            "this", "that", "classic", "old", "new", "from", "and", "or", "to", "for", "with", "play", "playing", "at",
            "is", "its", "my", "your", "our", "his", "her");

    private static final Pattern MOD = Pattern.compile("(?<![A-Za-z'’-])([A-Z][A-Za-z0-9-]{2,})\\s+(?:Pixel\\s+Dungeon|PD)\\b");

    private static final Pattern SPD_TEXT = Pattern.compile("\\bShattered\\s+(?:Pixel\\s+Dungeon|PD)\\b|\\bSPD\\b");

    private static final Pattern PD_TEXT = Pattern.compile(
            "\\boriginal\\b[\\s*_]*Pixel\\s+Dungeon\\b|\\bvanilla\\b(?!\\s+(?:Shattered|SPD))|\\bWatabou['’]s\\s+(?:game|original|Pixel\\s+Dungeon)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HEALTH_AFTER = Pattern.compile("(\\d{1,5})\\s*(?:hp|health|hit\\s*points?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALTH_BEFORE = Pattern.compile("\\b(?:hp|health)\\b[^\\d.]{0,12}(\\d{1,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFENCE_AFTER = Pattern.compile("(\\d{1,4})\\s*(?:defen[cs]e|evasion)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFENCE_BEFORE = Pattern.compile("\\b(?:defen[cs]e|evasion)\\b[^\\d.]{0,12}(\\d{1,4})", Pattern.CASE_INSENSITIVE);

    /** A name for a subject: lower case, quotes and parentheses gone. */
    static String normalize(String subject) {
        return subject.replaceAll("\\(.*?\\)", " ").replace('’', '\'').replaceAll("[\"“”*_]", "")
                .replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Classifies a claim.
     *
     * @param text the claim text, and the quote if there is one
     * @param subjects the names or class names the claim is about; empty when the writer gave none
     * @param sourceType {@code wiki}, {@code forum}, {@code guide}, {@code video}, {@code developer}, {@code other}
     */
    Result classify(String text, List<String> subjects, String sourceType) {
        List<Evidence> notes = new ArrayList<>();
        // A mod outranks everything: a mod carries this game's names, so the table would call it spd.
        Set<String> mods = new TreeSet<>();
        Matcher mod = MOD.matcher(text);
        while (mod.find()) {
            String word = mod.group(1);
            if (!NOT_A_MOD.contains(word.toLowerCase(Locale.ROOT)) && !word.endsWith("'s") && !word.endsWith("’s")) {
                mods.add(word.toLowerCase(Locale.ROOT));
                notes.add(new Evidence("mod-name", "\"" + mod.group().strip() + "\" names a mod, not this game or vanilla"));
            }
        }
        if (mods.size() == 1) {
            return new Result("mod:" + mods.iterator().next(), notes);
        }
        if (mods.size() > 1) {
            notes.add(new Evidence("conflict", "the text names more than one mod: " + mods));
            return new Result("unknown", notes);
        }

        // Votes, with what cast each.
        Set<String> votes = new LinkedHashSet<>();
        if (SPD_TEXT.matcher(text).find()) {
            votes.add("spd");
            notes.add(new Evidence("text-declares", "the text says it is about Shattered Pixel Dungeon"));
        }
        if (PD_TEXT.matcher(text).find()) {
            votes.add("pd");
            notes.add(new Evidence("text-declares", "the text says it is about the original (vanilla) Pixel Dungeon"));
        }
        if (DEVELOPER.equals(sourceType)) {
            votes.add("spd");
            notes.add(new Evidence("source-type", "a post by the game's developer about this game is spd by construction"));
        }

        // Subjects: the names the writer gave, else the names found in the text.
        List<Row> resolved = new ArrayList<>();
        boolean fromText = subjects.isEmpty();
        List<String> wanted = new ArrayList<>(subjects);
        if (fromText) {
            wanted.addAll(namesIn(text));
        }
        for (String subject : wanted) {
            List<Row> rows = lookup(subject);
            if (rows.isEmpty()) {
                notes.add(new Evidence("name-unlisted", "\"" + subject + "\" is not in the vocabulary of " + tag + " (items and mobs only)"));
                continue;
            }
            for (Row row : rows) {
                if (!resolved.contains(row)) {
                    resolved.add(row);
                }
            }
        }
        for (Row row : resolved) {
            String where = (fromText ? "found in the text: " : "") + row.kind() + " \"" + row.name() + "\"";
            switch (row.side()) {
                case "spd" -> {
                    votes.add("spd");
                    notes.add(new Evidence("name-only-here", where + " is only in this game's table"));
                }
                case "pd" -> {
                    votes.add("pd");
                    notes.add(new Evidence("name-only-vanilla", where + " is only in vanilla's table"));
                }
                default -> shared(row, where, resolved.size() == 1, text, votes, notes);
            }
        }
        if (votes.size() == 1) {
            return new Result(votes.iterator().next(), notes);
        }
        if (votes.size() > 1) {
            notes.add(new Evidence("conflict", "the evidence points to both games"));
        } else if (notes.isEmpty()) {
            notes.add(new Evidence("no-signal", "no subject, no declaration and no developer source"));
        } else {
            notes.add(new Evidence("no-vote", "nothing above points to one game"));
        }
        return new Result("unknown", notes);
    }

    private void shared(Row row, String where, boolean alone, String text, Set<String> votes, List<Evidence> notes) {
        if (row.figures().isEmpty()) {
            notes.add(new Evidence("shared-no-figure", where + " is in both games and no comparable figure of it differs"));
            return;
        }
        if (!alone) {
            notes.add(new Evidence("shared-figure-untied", where + " is in both games, but with several subjects a figure cannot be tied to it"));
            return;
        }
        boolean decided = false;
        for (Diff figure : row.figures()) {
            Set<Integer> stated = figuresOf(figure.what(), text);
            if (stated.isEmpty()) {
                continue;
            }
            int here = Integer.parseInt(figure.here());
            int there = Integer.parseInt(figure.there());
            boolean matchesHere = stated.contains(here);
            boolean matchesThere = stated.contains(there);
            String pair = figure.what() + " " + figure.here() + " here, " + figure.there() + " in vanilla; the claim states " + stated;
            if (matchesHere && !matchesThere) {
                votes.add("spd");
                decided = true;
                notes.add(new Evidence("shared-figure-here", where + " is in both games and its " + pair + ", which is this game's"));
            } else if (matchesThere && !matchesHere) {
                votes.add("pd");
                decided = true;
                notes.add(new Evidence("shared-figure-there", where + " is in both games and its " + pair + ", which is vanilla's"));
            } else if (matchesHere) {
                notes.add(new Evidence("shared-figure-both", where + ": the claim states both games' " + figure.what() + " (" + pair + ")"));
            } else {
                notes.add(new Evidence("shared-figure-neither", where + ": the claim's figure matches neither game (" + pair + ")"));
            }
        }
        if (!decided && notes.stream().noneMatch(e -> e.rule().startsWith("shared-figure") && e.detail().startsWith(where))) {
            notes.add(new Evidence("shared-no-figure", where + " is in both games; its differing figures ("
                    + row.figures().stream().map(Diff::what).toList() + ") are not stated in the claim"));
        }
    }

    /** The whole numbers a text states for a comparable figure of a mob. */
    private static Set<Integer> figuresOf(String what, String text) {
        Set<Integer> out = new TreeSet<>();
        List<Pattern> patterns = switch (what) {
            case "health" -> List.of(HEALTH_AFTER, HEALTH_BEFORE);
            case "defence" -> List.of(DEFENCE_AFTER, DEFENCE_BEFORE);
            default -> List.of();
        };
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                out.add(Integer.parseInt(m.group(1)));
            }
        }
        return out;
    }

    /** The rows a subject names: by display name first, then by class name. */
    private List<Row> lookup(String subject) {
        String name = normalize(subject);
        List<Row> rows = byName.get(name);
        if (rows == null && name.endsWith("s")) {
            rows = byName.get(name.substring(0, name.length() - 1));
        }
        if (rows == null) {
            rows = byClass.get(subject.strip());
        }
        return rows == null ? List.of() : rows;
    }

    /** The names of the table found in a text, longest first, each text span used once. */
    private List<String> namesIn(String text) {
        StringBuilder masked = new StringBuilder(text.toLowerCase(Locale.ROOT).replace('’', '\''));
        List<String> found = new ArrayList<>();
        for (String name : namesLongestFirst) {
            Matcher m = Pattern.compile("(?<![a-z])" + Pattern.quote(name) + "s?(?![a-z])").matcher(masked);
            if (m.find()) {
                found.add(name);
                m.reset();
                while (m.find()) {
                    for (int i = m.start(); i < m.end(); i++) {
                        masked.setCharAt(i, ' ');
                    }
                }
            }
        }
        return found;
    }
}
