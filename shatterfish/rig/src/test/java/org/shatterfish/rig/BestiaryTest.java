package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.log.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bestiary's tags ({@code tactics/bestiary.json}) against the Codex, their vocabulary and the
 * pinned code.
 *
 * <p>The file is what a Brain will read about an enemy it sees, so three things are held here. It
 * covers the game: every {@code ENEMY} class of the Codex's mob table has exactly one entry, and no
 * entry names a class the table does not have. It speaks the closed vocabulary that
 * {@code docs/bestiary/index.md} documents, and the page documents every value this test allows,
 * so the two cannot drift apart. And every citation behind a tag resolves at the pinned tag: the
 * file exists in the tree the ledger's {@code Commit} row names and the lines are inside it. That is
 * the rule {@code DocsCitations} applies to {@code docs/} (story 2.10), resolved the same way, by
 * the ledger's commit and {@code git cat-file}; the checker itself lives in the Codex module's test
 * sources, out of this module's reach. The bestiary pages carry the same citations and are swept by
 * {@code DocsCitationTest} with the rest of {@code docs/}.
 */
class BestiaryTest {

    private static final Path ROOT = SeedSetsTest.ROOT;
    private static final String TAG = "v4.0.0";
    private static final Path FILE = ROOT.resolve("tactics/bestiary.json");
    private static final Path MOBS = ROOT.resolve("codex").resolve(TAG).resolve("mobs.json");
    private static final Path INDEX = ROOT.resolve("docs/bestiary/index.md");
    private static final Path PAGES = ROOT.resolve("docs/bestiary");
    private static final Path LEDGER = ROOT.resolve("docs/UPSTREAM.md");

    /** The closed vocabulary, one set per enumerated tag. */
    static final Map<String, Set<String>> VOCABULARY = vocabulary();

    /** The tags every entry carries, and nothing else. */
    static final Set<String> KEYS = Set.of("className", "displayName", "speed", "attack", "reach", "flying",
            "amphibious", "immune", "inflicts", "ai", "evasive", "splits", "telegraph", "approach", "rangedFirst",
            "outrunnable", "breakContact", "citations");

    private static final Set<String> BOOLEANS = Set.of("flying", "amphibious", "evasive", "splits", "telegraph",
            "rangedFirst", "outrunnable");

    private static final Set<String> LISTS = Set.of("immune", "inflicts", "breakContact");

    private static final Pattern CITATION = Pattern.compile("([A-Za-z0-9_./-]+\\.[A-Za-z][A-Za-z0-9]*):(\\d+)(?:-(\\d+))?");

    private static final Pattern COMMIT_ROW = Pattern.compile("^\\|\\s*Commit\\s*\\|\\s*`([0-9a-f]{7,40})`");

    private static Map<String, Set<String>> vocabulary() {
        Map<String, Set<String>> v = new LinkedHashMap<>();
        v.put("speed", Set.of("slow", "normal", "fast", "immobile"));
        v.put("attack", Set.of("melee", "ranged", "bolt", "none"));
        v.put("ai", Set.of("sleeping", "wandering", "hunting", "passive"));
        v.put("approach", Set.of("close", "keep-away", "chokepoint", "surprise", "avoid"));
        v.put("breakContact", Set.of("door", "stairs", "out-of-sight", "corridor", "invisibility", "distance",
                "leave-water", "escape-crystal", "none"));
        v.put("immune", Set.of("AllyBuff", "Amok", "Bleeding", "Blazing", "Blindness", "Blizzard", "Burning", "Charm",
                "Chill", "ConfusionGas", "CorrosiveGas", "Dread", "Fire", "Freezing", "Frost", "Inferno", "Ooze",
                "Paralysis", "ParalyticGas", "Poison", "Regrowth", "Roots", "Sleep", "Slow", "SmokeScreen", "Speed",
                "StenchGas", "StormCloud", "Terror", "ToxicGas", "Vertigo", "Web"));
        v.put("inflicts", Set.of("Bleeding", "Blindness", "Burning", "Charm", "Chill", "Corrosion", "Cripple",
                "Degrade", "Electricity", "Frost", "Hex", "Ooze", "Paralysis", "Poison", "Roots", "ToxicGas",
                "Vulnerable", "Weakness", "cursed-wand", "darkness", "displace", "enchantment", "knockback", "pull",
                "shock", "steal"));
        return v;
    }

    /** One entry of the file, by tag, each as the raw text of its value. */
    private static List<Map<String, String>> entries() {
        Map<String, String> file = Json.object(CodexKnowledge.compact(read(FILE)));
        assertEquals(1, Json.integer(Json.required(file, "version", "bestiary")), "the file's format version");
        assertEquals(TAG, Json.string(Json.required(file, "tag", "bestiary")), "the tag the file was read at");
        assertEquals(Set.of("entries", "tag", "version"), file.keySet(), "the file's members");
        List<Map<String, String>> entries = new ArrayList<>();
        for (String raw : Json.array(Json.required(file, "entries", "bestiary"))) {
            entries.add(Json.object(raw));
        }
        return entries;
    }

    private static List<String> strings(String raw) {
        List<String> out = new ArrayList<>();
        for (String element : Json.array(raw)) {
            out.add(Json.string(element));
        }
        return out;
    }

    @Test
    @DisplayName("every ENEMY class of the Codex has exactly one entry, and every entry is a Codex class")
    void covers_the_codex() {
        Map<String, String> alignment = new TreeMap<>();
        for (String raw : Json.array(CodexKnowledge.compact(read(MOBS)))) {
            Map<String, String> mob = Json.object(raw);
            alignment.put(Json.string(Json.required(mob, "className", "mob")),
                    Json.string(Json.required(mob, "alignment", "mob")));
        }
        List<String> named = new ArrayList<>();
        for (Map<String, String> entry : entries()) {
            named.add(Json.string(Json.required(entry, "className", "entry")));
        }
        assertEquals(named.stream().sorted().toList(), named, "entries are sorted by class name");
        assertEquals(new LinkedHashSet<>(named).size(), named.size(), "no class has two entries");
        TreeSet<String> unknown = new TreeSet<>(named);
        unknown.removeAll(alignment.keySet());
        assertEquals(new TreeSet<String>(), unknown, "every entry names a class of the Codex's mob table");
        TreeSet<String> missing = new TreeSet<>();
        for (Map.Entry<String, String> mob : alignment.entrySet()) {
            if (mob.getValue().equals("ENEMY") && !named.contains(mob.getKey())) {
                missing.add(mob.getKey());
            }
        }
        assertEquals(new TreeSet<String>(), missing, "every ENEMY class of the Codex has an entry");
    }

    @Test
    @DisplayName("every tag is in the closed vocabulary, every entry carries every tag, and nothing else")
    void speaks_the_vocabulary() {
        List<String> wrong = new ArrayList<>();
        for (Map<String, String> entry : entries()) {
            String name = Json.string(Json.required(entry, "className", "entry"));
            if (!entry.keySet().equals(KEYS)) {
                wrong.add(name + " carries " + new TreeSet<>(entry.keySet()));
                continue;
            }
            if (Json.string(entry.get("displayName")).isBlank()) {
                wrong.add(name + " has no name");
            }
            if (Json.integer(entry.get("reach")) < 0) {
                wrong.add(name + " reach " + entry.get("reach"));
            }
            for (String flag : BOOLEANS) {
                Json.bool(entry.get(flag));
            }
            for (Map.Entry<String, Set<String>> tag : VOCABULARY.entrySet()) {
                List<String> values = LISTS.contains(tag.getKey())
                        ? strings(entry.get(tag.getKey())) : List.of(Json.string(entry.get(tag.getKey())));
                if (LISTS.contains(tag.getKey()) && new LinkedHashSet<>(values).size() != values.size()) {
                    wrong.add(name + " repeats a value of " + tag.getKey() + ": " + values);
                }
                for (String value : values) {
                    if (!tag.getValue().contains(value)) {
                        wrong.add(name + " " + tag.getKey() + "=" + value);
                    }
                }
            }
            if (strings(entry.get("breakContact")).isEmpty()) {
                wrong.add(name + " says nothing about breaking contact; 'none' is a value");
            }
            if (strings(entry.get("citations")).isEmpty()) {
                wrong.add(name + " cites nothing");
            }
        }
        assertEquals(List.of(), wrong, "tags outside the vocabulary of docs/bestiary/index.md");
    }

    @Test
    @DisplayName("the index page documents every tag and every value the vocabulary allows")
    void the_page_documents_the_vocabulary() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : read(INDEX).split("\n")) {
            Matcher row = Pattern.compile("^\\| `([A-Za-z]+)` \\|(.*)$").matcher(line);
            if (row.find()) {
                rows.put(row.group(1), row.group(2));
            }
        }
        assertTrue(rows.keySet().containsAll(KEYS), "every tag has a row in the vocabulary table: " + rows.keySet());
        List<String> undocumented = new ArrayList<>();
        for (Map.Entry<String, Set<String>> tag : VOCABULARY.entrySet()) {
            for (String value : tag.getValue()) {
                if (!Pattern.compile("(?<![A-Za-z-])" + Pattern.quote(value) + "(?![A-Za-z-])")
                        .matcher(rows.get(tag.getKey())).find()) {
                    undocumented.add(tag.getKey() + "=" + value);
                }
            }
        }
        assertEquals(List.of(), undocumented, "values the test allows and the page does not name");
    }

    @Test
    @DisplayName("every entry has its card on a bestiary page")
    void every_entry_has_a_card() throws IOException {
        StringBuilder pages = new StringBuilder();
        try (Stream<Path> tree = Files.list(PAGES)) {
            for (Path page : tree.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList()) {
                pages.append(read(page)).append('\n');
            }
        }
        List<String> missing = new ArrayList<>();
        for (Map<String, String> entry : entries()) {
            String name = Json.string(entry.get("className"));
            if (!pages.toString().contains("`" + name + "` · depths")) {
                missing.add(name);
            }
        }
        assertEquals(List.of(), missing, "entries without a card");
    }

    @Test
    @DisplayName("every citation behind a tag resolves at the pinned tag: the file is in its tree, the lines inside it")
    void every_citation_resolves_at_the_pin() {
        Map<String, List<int[]>> cited = new TreeMap<>();
        List<String> malformed = new ArrayList<>();
        int count = 0;
        for (Map<String, String> entry : entries()) {
            for (String citation : strings(entry.get("citations"))) {
                count++;
                Matcher m = CITATION.matcher(citation);
                if (!m.matches()) {
                    malformed.add(citation);
                    continue;
                }
                int from = Integer.parseInt(m.group(2));
                int to = m.group(3) == null ? from : Integer.parseInt(m.group(3));
                cited.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(new int[] {from, to});
            }
        }
        assertEquals(List.of(), malformed, "a citation is a full path and a line or a range");
        assertTrue(count > 1000, "the file cites the code behind its tags: " + count);

        String commit = pinnedCommit();
        Map<String, Integer> lengths = lineCounts(commit, List.copyOf(cited.keySet()));
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> file : cited.entrySet()) {
            Integer length = lengths.get(file.getKey());
            if (length == null) {
                broken.add(file.getKey() + " is not in the tree at " + TAG);
                continue;
            }
            for (int[] lines : file.getValue()) {
                if (lines[0] < 1 || lines[1] < lines[0] || lines[1] > length) {
                    broken.add(file.getKey() + ":" + lines[0] + "-" + lines[1] + " (the file has " + length + " lines)");
                }
            }
        }
        assertEquals(List.of(), broken, "citations that do not resolve at " + TAG + " (" + commit + ")");
    }

    /** The pinned commit, read from the ledger's first Commit row, as {@code DocsCitations} reads it. */
    private static String pinnedCommit() {
        for (String line : read(LEDGER).split("\n")) {
            Matcher m = COMMIT_ROW.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("no Commit row in " + LEDGER);
    }

    /**
     * The line count of each path at {@code commit}, by one {@code git cat-file --batch}; a path
     * the tree does not hold is left out. The names go down a thread of their own while the answers
     * come back on this one: git answers while it reads, and a pipe holds a few kilobytes.
     */
    private static Map<String, Integer> lineCounts(String commit, List<String> paths) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ProcessBuilder builder = new ProcessBuilder("git", "--no-pager", "cat-file", "--batch")
                .directory(ROOT.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = null;
        try {
            process = builder.start();
            Process running = process;
            Thread names = new Thread(() -> {
                try (OutputStream to = running.getOutputStream()) {
                    for (String path : paths) {
                        to.write((commit + ":" + path + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException closed) {
                    running.destroy();
                }
            }, "bestiary-git-names");
            names.setDaemon(true);
            names.start();
            try (InputStream from = process.getInputStream()) {
                for (String path : paths) {
                    String header = header(from);
                    assertFalse(header == null, "git cat-file ended before " + path);
                    String[] fields = header.split(" ");
                    if (fields.length < 3 || !fields[1].equals("blob")) {
                        continue;
                    }
                    byte[] body = from.readNBytes(Integer.parseInt(fields[2]));
                    if (from.read() < 0) {
                        throw new IOException("git cat-file ended after " + path);
                    }
                    counts.put(path, lines(body));
                }
            }
            names.join();
            assertEquals(0, process.waitFor(), "git cat-file --batch exits cleanly");
        } catch (IOException e) {
            throw new UncheckedIOException("git cat-file --batch could not be run in " + ROOT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return counts;
    }

    private static String header(InputStream from) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = from.read()) != -1 && b != '\n') {
            line.write(b);
        }
        return b == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
    }

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

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(file + " could not be read", e);
        }
    }
}
