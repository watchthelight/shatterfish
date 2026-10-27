package org.shatterfish.harness.knowledge;

import org.shatterfish.api.Playbook;
import org.shatterfish.harness.log.Json;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a Playbook file may say (story 7.2, FR-35): the check that runs in {@code ./gradlew build}
 * over every committed Playbook and in the Rig before a Registration is accepted.
 *
 * <p>It reads the file's text, not the {@link Playbook} value, because the things it refuses are
 * things the value cannot hold: an unknown key has no field to land in and a seed has none either.
 * Each problem is a message that names the field by its path in the file (for example
 * {@code identify.reach} or {@code classes.WIZARD}), so the author knows which line to change, and
 * every rule of the story has its own message so a test can tell the rules apart.
 *
 * <p>It refuses, in this order of asking:
 * <ul>
 *   <li>a seed or a seed string: a key that names a seed or a salt, or any string that is spelled as a
 *       seed code (non-negotiable 1: the seed is what the bot never reads, and a file the Brain is
 *       handed is a channel for it);</li>
 *   <li>a dungeon coordinate, cell index or floor layout: a key that names one, for the same reason;</li>
 *   <li>a hero class that {@code hero-classes.json} does not list;</li>
 *   <li>a key the format does not define;</li>
 *   <li>a number outside the range its field declares ({@link Playbook.Spec}), or not a whole number;</li>
 *   <li>an enum value the field does not allow.</li>
 * </ul>
 */
public final class PlaybookValidator {

    /** The words in a key that make it a seed: the dungeon seed and the salt the harness reseeds from. */
    private static final Pattern SEEDY = Pattern.compile("(^|_)(seed|seeds|salt|rng)($|_)");

    /** The words in a key that make it a place in one dungeon: a cell, a coordinate, a layout. */
    private static final Pattern PLACE = Pattern.compile(
            "(^|_)(cell|cells|x|y|pos|position|positions|coord|coords|coordinate|coordinates|layout|tiles|map|room|rooms)($|_)");

    /** A seed code is nine letters in three groups (DungeonSeed.java:51-62), with or without the dashes. */
    private static final Pattern SEED_CODE = Pattern.compile("[A-Z]{3}-?[A-Z]{3}-?[A-Z]{3}");

    /** The top-level keys, besides the sections. */
    private static final Set<String> TOP = Set.of("format", "name", "version", "identify", "upgrade", "bosses", "classes");

    private PlaybookValidator() {
    }

    /**
     * The problems in {@code text}, each naming its field; empty when the Playbook is acceptable.
     *
     * @param text        the file's text
     * @param heroClasses the hero classes {@code hero-classes.json} lists
     */
    public static List<String> problems(String text, Set<String> heroClasses) {
        List<String> problems = new ArrayList<>();
        Map<String, String> top;
        try {
            top = Json.object(text.strip());
        } catch (RuntimeException unreadable) {
            problems.add("the Playbook is not a JSON object: " + unreadable.getMessage());
            return problems;
        }
        keys("", top, problems);
        for (Map.Entry<String, String> member : top.entrySet()) {
            String key = member.getKey();
            if (seedy(key) || place(key) || !TOP.contains(key)) {
                continue;
            }
            switch (key) {
                case "format" -> {
                    if (!whole(member.getValue()) || Json.number(member.getValue()) != Playbook.FORMAT) {
                        problems.add("format is " + Playbook.FORMAT + ": " + member.getValue());
                    }
                }
                case "name" -> {
                    if (!text(member.getValue()) || !Json.string(member.getValue()).matches("[a-z][a-z0-9_]*")) {
                        problems.add("name is a Brain's name in lower snake case: " + member.getValue());
                    }
                }
                case "version" -> {
                    if (!whole(member.getValue()) || Json.number(member.getValue()) < 1) {
                        problems.add("version is a whole number from 1: " + member.getValue());
                    }
                }
                case "identify", "upgrade" -> section(key, member.getValue(), problems);
                case "bosses" -> section("bosses", member.getValue(), problems);
                default -> classes(member.getValue(), heroClasses, problems);
            }
        }
        for (String required : List.of("format", "name", "version")) {
            if (!top.containsKey(required)) {
                problems.add(required + " is missing");
            }
        }
        return problems;
    }

    /** Flags a seed-like or place-like key anywhere, and an unknown key at the top level only (sections check their own). */
    private static void keys(String path, Map<String, String> members, List<String> problems) {
        for (String key : members.keySet()) {
            if (seedy(key)) {
                problems.add(path + key + " names a seed; a Playbook never holds a seed, a salt or any of the Run's randomness");
            } else if (place(key)) {
                problems.add(path + key + " names a place in one dungeon (a coordinate, a cell index or a floor layout);"
                        + " a Playbook never holds one");
            } else if (path.isEmpty() && !TOP.contains(key)) {
                problems.add(key + " is not a key the Playbook format defines");
            }
        }
    }

    private static boolean seedy(String key) {
        return SEEDY.matcher(key).find();
    }

    private static boolean place(String key) {
        return PLACE.matcher(key).find();
    }

    private static void section(String name, String raw, List<String> problems) {
        Map<String, String> members = object(name, raw, problems);
        if (members == null) {
            return;
        }
        keys(name + ".", members, problems);
        List<Playbook.Spec> specs = name.equals("bosses") ? List.of() : Playbook.specs(name);
        for (Map.Entry<String, String> member : members.entrySet()) {
            String path = name + "." + member.getKey();
            if (seedy(member.getKey()) || place(member.getKey())) {
                continue;
            }
            Playbook.Spec spec = specs.stream().filter(candidate -> candidate.key().equals(member.getKey())).findFirst().orElse(null);
            if (spec == null) {
                problems.add(path + " is not a key the " + name + " section defines"
                        + (specs.isEmpty() ? " (the section has no field yet)" : "; it defines " + specs.stream().map(Playbook.Spec::key).toList()));
                continue;
            }
            value(path, spec, member.getValue(), problems);
        }
    }

    private static void value(String path, Playbook.Spec spec, String raw, List<String> problems) {
        if (text(raw) && SEED_CODE.matcher(Json.string(raw)).matches()) {
            problems.add(path + " holds a seed string (" + raw + "); a Playbook never holds one");
        } else if (spec.isChoice()) {
            if (!text(raw) || !spec.allowed().contains(Json.string(raw))) {
                problems.add(path + " is one of " + spec.allowed() + ": " + raw);
            }
        } else if (!whole(raw)) {
            problems.add(path + " is a whole number from " + spec.min() + " to " + spec.max() + ": " + raw);
        } else if (Json.number(raw) < spec.min() || Json.number(raw) > spec.max()) {
            problems.add(path + " is from " + spec.min() + " to " + spec.max() + ": " + raw);
        }
    }

    private static void classes(String raw, Set<String> heroClasses, List<String> problems) {
        Map<String, String> members = object("classes", raw, problems);
        if (members == null) {
            return;
        }
        for (Map.Entry<String, String> member : members.entrySet()) {
            String path = "classes." + member.getKey();
            if (!heroClasses.contains(member.getKey())) {
                problems.add(path + " is not a hero class; hero-classes.json lists " + new LinkedHashSet<>(heroClasses));
                continue;
            }
            Map<String, String> entry = object(path, member.getValue(), problems);
            if (entry == null) {
                continue;
            }
            for (Map.Entry<String, String> part : entry.entrySet()) {
                if (seedy(part.getKey()) || place(part.getKey())) {
                    keys(path + ".", Map.of(part.getKey(), part.getValue()), problems);
                } else if (Playbook.SECTIONS.contains(part.getKey())) {
                    Map<String, String> fields = object(path + "." + part.getKey(), part.getValue(), problems);
                    if (fields != null) {
                        keys(path + "." + part.getKey() + ".", fields, problems);
                        for (Map.Entry<String, String> field : fields.entrySet()) {
                            if (seedy(field.getKey()) || place(field.getKey())) {
                                continue;
                            }
                            String fieldPath = path + "." + part.getKey() + "." + field.getKey();
                            Playbook.Spec spec = Playbook.specs(part.getKey()).stream()
                                    .filter(candidate -> candidate.key().equals(field.getKey())).findFirst().orElse(null);
                            if (spec == null) {
                                problems.add(fieldPath + " is not a key the " + part.getKey() + " section defines");
                            } else {
                                value(fieldPath, spec, field.getValue(), problems);
                            }
                        }
                    }
                } else {
                    problems.add(path + "." + part.getKey() + " is not a key a class entry defines; it defines "
                            + Playbook.SECTIONS);
                }
            }
        }
    }

    private static Map<String, String> object(String path, String raw, List<String> problems) {
        if (raw == null || !raw.startsWith("{")) {
            problems.add(path + " is an object: " + raw);
            return null;
        }
        try {
            return Json.object(raw);
        } catch (RuntimeException unreadable) {
            problems.add(path + " is not a readable object: " + unreadable.getMessage());
            return null;
        }
    }

    private static boolean whole(String raw) {
        return raw.matches("-?\\d{1,18}");
    }

    private static boolean text(String raw) {
        return raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2;
    }
}
