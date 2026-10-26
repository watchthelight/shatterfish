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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One lore claim as a file (story 7.7, FR-18, ADR-0021): the reader, the writer and the shapes the
 * checker and the intake share.
 *
 * <p>A claim file is a small frontmatter between two {@code ---} lines and a body. The frontmatter
 * is deliberately not YAML: a line is {@code key: value}, a value is a bare token, a JSON string
 * (the only way free text is written, so that a colon or a quote in a claim cannot change what the
 * line means), the empty list {@code []}, or a key followed by {@code - item} lines. A reader that
 * accepts only that is a reader whose answer a person can predict, and it needs no library.
 *
 * <p>It lives in the module's test sources beside {@link DocsCitations}, for the same reason: the
 * Codex's leak gate ({@code CodexLeakTest}) denies the generator {@code Process} and
 * {@code System}, and the checker has to run git to resolve a citation. There is no game code here
 * and no network call (NFR-8).
 */
final class Lore {

    private Lore() {
    }

    /** The folder, relative to the repository root, that holds the claims. */
    static final String DIR = "lore";

    /** The keys a claim file may carry, in the order the writer writes them. */
    static final List<String> KEYS = List.of("id", "topic", "group", "subject", "subject_class", "source_url",
            "source_type", "date", "retrieved", "variant", "version_claimed", "tier", "graded_against",
            "status", "claim", "quote", "cites", "implemented_in", "card");

    /** The keys whose value is a list. */
    static final List<String> LISTS = List.of("cites", "implemented_in");

    /** The keys whose value is free text, and so is always written as a JSON string. */
    static final List<String> TEXT = List.of("subject", "version_claimed", "claim", "quote");

    /** A claim file as read: its scalar keys, its list keys and its body, nothing interpreted yet. */
    record Doc(String name, Map<String, String> scalars, Map<String, List<String>> lists, String body) {

        String get(String key) {
            return scalars.get(key);
        }

        List<String> list(String key) {
            return lists.getOrDefault(key, List.of());
        }

        boolean has(String key) {
            return scalars.containsKey(key) || lists.containsKey(key);
        }
    }

    /** A claim file that could not be read as one, with why. */
    static final class Unreadable extends Exception {

        private static final long serialVersionUID = 1L;

        Unreadable(String message) {
            super(message);
        }
    }

    /** Reads one claim file's text. {@code name} is only for the message. */
    static Doc parse(String name, String text) throws Unreadable {
        if (text.indexOf('\r') >= 0) {
            throw new Unreadable("carriage return: the file is line feeds only (.gitattributes says so)");
        }
        if (!text.startsWith("---\n")) {
            throw new Unreadable("the file does not open with a --- line");
        }
        int close = text.indexOf("\n---\n", 3);
        if (close < 0) {
            throw new Unreadable("the frontmatter is not closed by a --- line");
        }
        String[] lines = text.substring(4, close + 1).split("\n", -1);
        Map<String, String> scalars = new LinkedHashMap<>();
        Map<String, List<String>> lists = new LinkedHashMap<>();
        String open = null;
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            if (line.startsWith("  - ")) {
                if (open == null) {
                    throw new Unreadable("line " + (i + 2) + ": a list item with no key above it");
                }
                lists.get(open).add(line.substring(4).strip());
                continue;
            }
            open = null;
            int colon = line.indexOf(':');
            if (colon <= 0 || line.startsWith(" ")) {
                throw new Unreadable("line " + (i + 2) + ": not `key: value`: " + line);
            }
            String key = line.substring(0, colon);
            String value = line.substring(colon + 1).strip();
            if (scalars.containsKey(key) || lists.containsKey(key)) {
                throw new Unreadable("line " + (i + 2) + ": " + key + " is written twice");
            }
            if (value.isEmpty()) {
                lists.put(key, new ArrayList<>());
                open = key;
            } else if (value.equals("[]")) {
                lists.put(key, new ArrayList<>());
            } else if (value.startsWith("\"")) {
                scalars.put(key, unquote(value, i + 2));
            } else {
                scalars.put(key, value);
            }
        }
        String body = text.substring(close + 5);
        return new Doc(name, scalars, lists, body);
    }

    /** A JSON string literal, read: the only escapes a writer here produces, and \\uXXXX. */
    static String unquote(String literal, int line) throws Unreadable {
        if (literal.length() < 2 || !literal.endsWith("\"")) {
            throw new Unreadable("line " + line + ": a string that does not end in a quote");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < literal.length() - 1; i++) {
            char c = literal.charAt(i);
            if (c == '"') {
                throw new Unreadable("line " + line + ": an unescaped quote inside a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (++i >= literal.length() - 1) {
                throw new Unreadable("line " + line + ": a backslash at the end of a string");
            }
            char e = literal.charAt(i);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'u' -> {
                    if (i + 4 >= literal.length()) {
                        throw new Unreadable("line " + line + ": a short \\u escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException bad) {
                        throw new Unreadable("line " + line + ": a bad \\u escape");
                    }
                    i += 4;
                }
                default -> throw new Unreadable("line " + line + ": an unknown escape \\" + e);
            }
        }
        return out.toString();
    }

    /** Text as a JSON string literal, non-ASCII left as it is. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** The text of a claim file for these values: keys in the canonical order, no key repeated. */
    static String render(Doc doc) {
        StringBuilder out = new StringBuilder("---\n");
        for (String key : KEYS) {
            if (LISTS.contains(key)) {
                if (!doc.lists().containsKey(key)) {
                    continue;
                }
                List<String> items = doc.list(key);
                out.append(key).append(items.isEmpty() ? ": []\n" : ":\n");
                for (String item : items) {
                    out.append("  - ").append(item).append('\n');
                }
            } else if (doc.scalars().containsKey(key)) {
                String value = doc.get(key);
                out.append(key).append(": ").append(TEXT.contains(key) ? quote(value) : value).append('\n');
            }
        }
        return out.append("---\n").append(doc.body()).toString();
    }

    /** Every claim file under {@code loreDir}, as {@code <topic>/<file>} in path order. */
    static List<Path> files(Path loreDir) {
        if (!Files.isDirectory(loreDir)) {
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(loreDir)) {
            return tree.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(loreDir + " could not be walked", e);
        }
    }

    /** The file's text, UTF-8. */
    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(file + " could not be read as UTF-8", e);
        }
    }

    /** The number at the end of an id, for sorting {@code sewers-10} after {@code sewers-9}. */
    static int number(String id) {
        Matcher m = Pattern.compile("-(\\d+)$").matcher(id);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * The names an {@code implemented_in} entry may use: a Policy of the Brain (policy:fight), a row
     * of the Brain's Rules index (rule:chasm-step) or a field of a Playbook (playbook:hp). Until
     * the Playbook exists as data (story 7.2) its fields are the terms of the weights files, which
     * is the data the Brain reads at construction today.
     */
    record Registry(java.util.Set<String> policies, java.util.Set<String> rules, java.util.Set<String> playbook) {

        static final String POLICY = "policy:";
        static final String RULE = "rule:";
        static final String PLAYBOOK = "playbook:";

        /** Whether an entry names something that exists, and if not, what it should have named. */
        String problem(String entry) {
            if (entry.startsWith(POLICY)) {
                return policies.contains(entry.substring(POLICY.length())) ? null
                        : "no Policy named " + entry.substring(POLICY.length()) + " (the Brain has "
                        + String.join(", ", policies) + ")";
            }
            if (entry.startsWith(RULE)) {
                return rules.contains(entry.substring(RULE.length())) ? null
                        : "no row of docs/brain-rules.md has the slug " + entry.substring(RULE.length());
            }
            if (entry.startsWith(PLAYBOOK)) {
                return playbook.contains(entry.substring(PLAYBOOK.length())) ? null
                        : "no Playbook field named " + entry.substring(PLAYBOOK.length());
            }
            return "not policy:<name>, rule:<slug> or playbook:<field>";
        }

        /** The registry of a checkout: the Brain's Rules index and the weights files, plus the Policies given. */
        static Registry of(Path root, java.util.Collection<String> policies) {
            java.util.Set<String> rules = new TreeSet<>();
            Path index = root.resolve("docs/brain-rules.md");
            if (Files.isRegularFile(index)) {
                Matcher m = Pattern.compile("<a id=\"([a-z0-9-]+)\"></a>").matcher(read(index));
                while (m.find()) {
                    rules.add(m.group(1));
                }
            }
            java.util.Set<String> fields = new TreeSet<>();
            Path weights = root.resolve("weights");
            for (Path file : files(weights)) {
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                Matcher terms = Pattern.compile("\"terms\"\\s*:\\s*\\{([^}]*)\\}").matcher(read(file));
                if (terms.find()) {
                    Matcher key = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:").matcher(terms.group(1));
                    while (key.find()) {
                        fields.add(key.group(1));
                    }
                }
            }
            return new Registry(new TreeSet<>(policies), rules, fields);
        }
    }
}
