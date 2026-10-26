package org.shatterfish.codex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lore intake (story 7.7, FR-18, ADR-0021): the entry for the product owner's research skill.
 *
 * <p>The skill writes a draft claim file into a folder. {@code ./gradlew :codex:lore --args="intake
 * <folder>"} reads each draft, validates it with the checker's own rules, assigns the next id and
 * writes it under {@code lore/<topic>/}, or writes a rejection note beside the draft and writes
 * nothing. It never reaches the network: a draft names its source, and no step here opens it.
 *
 * <p>Intake never promotes. A draft enters at Tier 3 unless it carries the citation that makes it
 * Tier 1, and that citation has to resolve at the pinned tag, by the checker's resolver. A draft
 * that asks for Tier 1, 2 or F with no citation is refused, not quietly lowered, so the person who
 * wrote it learns which claim was not graded.
 *
 * <p>A claim about one seed or one layout is not knowledge about the game (FR-18, non-negotiable
 * 1): a Brain that read it would be reading a thing no human at the screen knows. {@link
 * #rejection} names which of the five kinds it found.
 */
final class LoreIntake {

    private LoreIntake() {
    }

    /** What happened to one draft. */
    record Outcome(Path draft, boolean accepted, String id, Path written, List<String> reasons) {
    }

    /** The keys a draft must carry. Everything else is assigned here or optional. */
    static final List<String> DRAFT_REQUIRED = List.of("topic", "source_url", "source_type", "date", "retrieved",
            "variant", "version_claimed", "claim");

    /** The keys only intake sets, so a draft that carries one is saying something it cannot. */
    static final List<String> ASSIGNED = List.of("id", "status", "graded_against");

    // ------------------------------------------------------------------------ the five kinds

    /** A seed code as the game shows it: nine letters in three groups (DungeonSeed.java:51-54, 77-84). */
    private static final Pattern SEED_CODE = Pattern.compile("\\b[A-Z]{3}-[A-Z]{3}-[A-Z]{3}\\b");

    /** The word seed followed by a value: seed 4821, seed: ABCDEFGHI, seed #12, seed "x1". */
    private static final Pattern SEED_VALUE = Pattern.compile(
            "(?i:\\bseed\\b\\s*(?:[:=#]|number|code|string|is|was)?)\\s*[`\"'“]?([A-Za-z0-9-]*\\d[A-Za-z0-9-]*|[A-Z]{9})\\b");

    /** A seed pointed at: this seed, my seed, the same seed, a certain seed. */
    private static final Pattern SEED_POINTED = Pattern.compile(
            "(?i)\\b(?:this|that|my|your|their|the same|same|a certain|certain|specific|particular)\\s+(?:dungeon\\s+|run\\s+)?seed\\b");

    /** The words for where things are in one dungeon. */
    private static final Pattern LAYOUT = Pattern.compile(
            "(?i)\\b(?:layouts?|floor ?plans?)\\b"
                    + "|\\b(?:north|south|east|west)(?:-?(?:east|west))?\\s+(?:corner|side|wall|end|part|half)\\b"
                    + "|\\b(?:top|bottom|upper|lower)[- ](?:left|right)\\s+(?:corner|room|of the (?:map|level|screen))"
                    + "|\\b(?:first|second|third|left|right)\\s+room\\s+(?:on|of|from)\\s+(?:the\\s+)?(?:left|right|start|entrance|stairs)\\b");

    /** A place on one map: (12, 30), x=14, cell 1234, coordinates. */
    private static final Pattern COORDINATE = Pattern.compile(
            "(?<![A-Za-z0-9_])\\(\\s*-?\\d{1,3}\\s*,\\s*-?\\d{1,3}\\s*\\)"
                    + "|(?i)\\b[xy]\\s*[:=]\\s*-?\\d+"
                    + "|(?i)\\bcoordinates?\\b"
                    + "|(?i)\\b(?:cell|tile|pos(?:ition)?)\\s*#?\\s*\\d{2,4}\\b");

    /** The words for one level's feeling tied to a run: the feeling alone is general knowledge. */
    private static final Pattern FEELING = Pattern.compile("(?i)\\blevel feelings?\\b");
    private static final Pattern THIS_RUN = Pattern.compile(
            "(?i)\\b(?:depth|floor|level)\\s*\\d+\\b|\\bthis (?:run|game|floor|level|seed)\\b|\\bmy (?:run|game)\\b"
                    + "|\\bon (?:that|the same) (?:run|floor|level)\\b");

    /** Which of the five kinds of claim FR-18 refuses the text is, if any, and what in it said so. */
    static Optional<String> rejection(String text) {
        Matcher m = SEED_CODE.matcher(text);
        if (m.find()) {
            return Optional.of("it names a seed string (" + m.group() + "): a seed is one Run's dungeon, not knowledge of the game");
        }
        m = SEED_VALUE.matcher(text);
        if (m.find()) {
            return Optional.of("it names a seed (" + m.group().strip() + "): a seed is one Run's dungeon, not knowledge of the game");
        }
        m = SEED_POINTED.matcher(text);
        if (m.find()) {
            return Optional.of("it refers to one seed (\"" + m.group() + "\"): a seed is one Run's dungeon, not knowledge of the game");
        }
        m = LAYOUT.matcher(text);
        if (m.find()) {
            return Optional.of("it describes a specific dungeon layout (\"" + m.group() + "\"): a layout belongs to one seed");
        }
        m = COORDINATE.matcher(text);
        if (m.find()) {
            return Optional.of("it names a map coordinate (\"" + m.group() + "\"): a position belongs to one level of one Run");
        }
        if (FEELING.matcher(text).find()) {
            m = THIS_RUN.matcher(text);
            if (m.find()) {
                return Optional.of("it ties a level feeling to one Run (\"" + m.group() + "\"): the feeling is general, which floor had it is not");
            }
        }
        return Optional.empty();
    }

    // ----------------------------------------------------------------------------- the intake

    /**
     * Processes every {@code *.md} draft in {@code drafts}, in name order. An accepted draft is
     * renamed to {@code <name>.accepted} so a second run does not enter it twice; a rejected one
     * stays, with {@code <name>.rejected.txt} beside it saying why.
     */
    static List<Outcome> run(Path root, Path loreDir, Path drafts, Lore.Registry registry) {
        List<Path> files;
        try (Stream<Path> list = Files.list(drafts)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(drafts + " could not be read", e);
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (Path draft : files) {
            outcomes.add(one(root, loreDir, draft, registry));
        }
        return outcomes;
    }

    /** One draft: entered under {@code loreDir}, or refused with a note beside it. */
    static Outcome one(Path root, Path loreDir, Path draft, Lore.Registry registry) {
        Path note = draft.resolveSibling(draft.getFileName() + ".rejected.txt");
        List<String> reasons = new ArrayList<>();
        Lore.Doc doc = null;
        try {
            doc = Lore.parse(draft.getFileName().toString(), Lore.read(draft));
        } catch (Lore.Unreadable bad) {
            reasons.add(bad.getMessage());
        }
        Lore.Doc entry = null;
        if (doc != null) {
            entry = assemble(doc, Upstream.tag(root), loreDir, reasons);
        }
        if (entry != null && reasons.isEmpty()) {
            List<LoreCheck.Finding> findings = new ArrayList<>();
            LoreCheck.validate(entry, registry, findings);
            // The seed, layout, coordinate and feeling kinds are the cheap ones and are said first;
            // the citations are resolved only for a draft that has passed them, since that is the
            // step that runs git.
            if (findings.isEmpty()) {
                LoreCheck.citations(root, List.of(entry), findings);
            }
            for (LoreCheck.Finding finding : findings) {
                reasons.add(finding.message());
            }
        }
        if (!reasons.isEmpty() || entry == null) {
            write(note, "rejected: " + draft.getFileName() + "\n\n" + String.join("\n", reasons.stream().map(r -> "- " + r).toList()) + "\n");
            return new Outcome(draft, false, null, note, List.copyOf(reasons));
        }
        Path topicDir = loreDir.resolve(entry.get("topic"));
        Path target = topicDir.resolve(entry.get("id") + ".md");
        try {
            Files.createDirectories(topicDir);
            if (Files.exists(target)) {
                throw new IllegalStateException(target + " exists: ids are assigned, never reused");
            }
            Files.writeString(target, Lore.render(entry), StandardCharsets.UTF_8);
            Files.deleteIfExists(note);
            Files.move(draft, draft.resolveSibling(draft.getFileName() + ".accepted"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("claim " + entry.get("id") + " could not be written to " + target, e);
        }
        return new Outcome(draft, true, entry.get("id"), target, List.of());
    }

    /**
     * The claim a draft becomes: the draft's keys, the id intake assigns, the pinned tag it is
     * graded against, and a tier that is the draft's only if the draft can carry it. Reasons it
     * cannot are added to {@code reasons}.
     */
    private static Lore.Doc assemble(Lore.Doc draft, String pin, Path loreDir, List<String> reasons) {
        for (String key : ASSIGNED) {
            if (draft.has(key)) {
                reasons.add("`" + key + "` is assigned by intake and a draft does not carry it");
            }
        }
        for (String key : draft.scalars().keySet()) {
            if (!Lore.KEYS.contains(key)) {
                reasons.add("unknown key `" + key + "`");
            }
        }
        for (String key : DRAFT_REQUIRED) {
            if (!draft.has(key)) {
                reasons.add("required field `" + key + "` is missing");
            }
        }
        String requested = draft.get("tier") == null ? "3" : draft.get("tier");
        if (!LoreCheck.TIERS.contains(requested)) {
            reasons.add("tier `" + requested + "` is not 1, 2, 3 or F");
        } else if (!requested.equals("3") && draft.list("cites").isEmpty()) {
            reasons.add("tier " + requested + " is asked for with no citation: a claim enters at Tier 3 unless the draft"
                    + " carries the path:line that settles it, and intake does not lower a tier for the writer");
        }
        String topic = draft.get("topic");
        if (topic == null || !topic.matches("[a-z][a-z0-9-]*")) {
            reasons.add("topic `" + topic + "` is not a lowercase slug");
            return null;
        }
        if (!reasons.isEmpty()) {
            return null;
        }
        String group = draft.get("group") == null ? topic : draft.get("group");
        if (!group.matches("[a-z][a-z0-9-]*")) {
            reasons.add("group `" + group + "` is not a lowercase slug");
            return null;
        }
        Map<String, String> scalars = new LinkedHashMap<>(draft.scalars());
        scalars.put("group", group);
        scalars.put("id", group + "-" + next(loreDir, group));
        scalars.put("tier", requested);
        scalars.put("graded_against", pin);
        scalars.put("status", "current");
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("cites", new ArrayList<>(draft.list("cites")));
        lists.put("implemented_in", new ArrayList<>(draft.list("implemented_in")));
        String heading = "## Verdict against `" + pin + "`";
        String text = draft.body().strip();
        if (text.isEmpty()) {
            text = requested.equals("3")
                    ? "Not graded against the pinned code. It is a hypothesis until a citation settles it."
                    : "Graded from the cited lines at " + pin + ".";
        }
        String body = text.startsWith("## Verdict against") ? text + "\n" : heading + "\n\n" + text + "\n";
        return new Lore.Doc(draft.name(), scalars, lists, "\n" + body);
    }

    /** The next number for a group: one past the highest id any topic holds for it. */
    static int next(Path loreDir, String group) {
        Pattern id = Pattern.compile(Pattern.quote(group) + "-(\\d+)\\.md");
        int highest = 0;
        for (Path file : Lore.files(loreDir)) {
            Matcher m = id.matcher(file.getFileName().toString());
            if (m.matches()) {
                highest = Math.max(highest, Integer.parseInt(m.group(1)));
            }
        }
        return highest + 1;
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(file + " could not be written", e);
        }
    }
}
