package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The other game (story 2.8): vanilla Pixel Dungeon, read from the second pinned source and never
 * loaded, built or imported. It is a different game whose classes this build must not have on a
 * path, so every fact here is read from its source text, the way this fork's own tables read what
 * they cannot construct.
 *
 * <p>Vanilla names a thing in the class's own initialiser — {@code name = "marsupial rat";} —
 * rather than in a bundle, and states a mob's health and defence beside it. What it rolls for
 * damage, accuracy and reduction are methods, carried as their text, because the two games do not
 * write those in one shape and the table's job is to say so rather than to resolve it.
 */
final class Vanilla {

    static final String PIN = "vanilla.pin";
    static final String SOURCE = "core/src/main/java/com/watabou/pixeldungeon/";
    static final String MOBS = SOURCE + "actors/mobs/";
    static final String ITEMS = SOURCE + "items/";

    private static final Pattern NAME = Pattern.compile("^\\s*name\\s*=\\s*\"([^\"]+)\"\\s*;");
    private static final Pattern HEALTH = Pattern.compile("\\bHT\\s*=\\s*(\\d+)\\s*;");
    private static final Pattern DEFENCE = Pattern.compile("\\bdefenseSkill\\s*=\\s*(\\d+)\\s*;");
    private static final Pattern EXTENDS = Pattern.compile("\\bclass\\s+\\w+\\s+extends\\s+(\\w+)");
    private static final Pattern DECLARED = Pattern.compile(
            "^\\s*(?:@\\w+\\s+)*(?:(?:public|private|protected|static|abstract|final)\\s+)*"
                    + "(?:class|enum|interface)\\s+(\\w+)");

    private Vanilla() {
    }

    /** Where the pinned tree sits, read from the pin rather than named here. */
    static String folder(Path root) {
        return Citations.found(root, PIN, "^folder=(\\S+)$").value() + "/";
    }

    /** The tag the pin names, for a citation that says which game it points into. */
    static String tag(Path root) {
        return Citations.found(root, PIN, "^tag=(\\S+)$").value();
    }

    /** One thing the other game has: what it calls itself, and what it states about itself. */
    record Named(String className, String name, List<Codex.Rule> facts, Codex.Citation citation) {
    }

    /** Every mob of the other game, with the health and defence it states and the rolls it writes as methods. */
    static List<Named> mobs(Path root) {
        List<Named> named = new ArrayList<>();
        for (String path : Sources.under(root, folder(root) + MOBS)) {
            Sources.Body file = Sources.file(root, path);
            String className = className(root, path);
            int line = nameLine(file);
            if (line < 0) {
                continue;
            }
            named.add(new Named(className, name(file, line), facts(root, file), file.citation(line)));
        }
        if (named.isEmpty()) {
            throw new IllegalStateException("the other game names no mob; the second pinned source is not where the reader looks");
        }
        return named;
    }

    /**
     * Every item of the other game. It states a name and little else in a shape this game also
     * states: a weapon's numbers are constructor arguments and a potion's are its own method, so
     * the diff carries what each says and does not pretend the two are one measurement.
     */
    static List<Named> items(Path root) {
        List<Named> named = new ArrayList<>();
        for (String path : Sources.under(root, folder(root) + ITEMS)) {
            Sources.Body file = Sources.file(root, path);
            int line = nameLine(file);
            if (line < 0) {
                continue;
            }
            named.add(new Named(className(root, path), name(file, line), List.of(), file.citation(line)));
        }
        if (named.isEmpty()) {
            throw new IllegalStateException("the other game names no item; the second pinned source is not where the reader looks");
        }
        return named;
    }

    /**
     * What a mob of the other game states about itself, walking up what it extends for anything its
     * own class does not state. A subclass there changes one number and inherits the rest, so a
     * reader that looked only at the class's own lines would call an albino rat a mob that states
     * nothing, and the diff would have nothing to set against this game's.
     */
    private static List<Codex.Rule> facts(Path root, Sources.Body file) {
        List<Codex.Rule> facts = new ArrayList<>();
        for (Sources.Body body = file; body != null; body = extended(root, body)) {
            number(body, HEALTH, "health", facts);
            number(body, DEFENCE, "defence", facts);
            for (String method : List.of("damageRoll", "attackSkill", "dr")) {
                if (facts.stream().anyMatch(f -> f.what().equals(method))) {
                    continue;
                }
                int at = body.find("public int " + method + "\\s*\\(");
                if (at >= 0) {
                    facts.add(new Codex.Rule(method, Sources.text(body.block(at)), body.citation(at)));
                }
            }
        }
        return facts;
    }

    /** The class one class of the other game extends, when that class is a mob of the same folder. */
    private static Sources.Body extended(Path root, Sources.Body body) {
        List<String> lines = Sources.stripped(body);
        for (int i : body.ownLines()) {
            Matcher declared = EXTENDS.matcher(lines.get(i - body.from()));
            if (!declared.find()) {
                continue;
            }
            String path = body.path().substring(0, body.path().lastIndexOf('/') + 1) + declared.group(1) + ".java";
            return Sources.exists(root, path) ? Sources.file(root, path) : null;
        }
        return null;
    }

    /** The one line of a class's own members that names it, or none when the class names nothing. */
    private static int nameLine(Sources.Body file) {
        int found = -1;
        List<String> lines = Sources.stripped(file);
        for (int i : file.ownLines()) {
            if (!NAME.matcher(lines.get(i - file.from())).find()) {
                continue;
            }
            if (found >= 0) {
                throw new IllegalStateException(file.path() + ":" + (i + 1) + ": two lines name this class");
            }
            found = i;
        }
        return found;
    }

    /** The name a line states. */
    private static String name(Sources.Body file, int line) {
        Matcher named = NAME.matcher(Sources.stripped(file).get(line - file.from()));
        if (!named.find()) {
            throw new IllegalStateException(file.path() + ":" + (line + 1) + ": no name where the reader found one");
        }
        return named.group(1);
    }

    /** One number a class states plainly about itself, where it states one. */
    private static void number(Sources.Body file, Pattern pattern, String what, List<Codex.Rule> facts) {
        if (facts.stream().anyMatch(f -> f.what().equals(what))) {
            return;
        }
        List<String> lines = Sources.stripped(file);
        for (int i : file.ownLines()) {
            Matcher found = pattern.matcher(lines.get(i - file.from()));
            if (found.find()) {
                facts.add(new Codex.Rule(what, found.group(1), file.citation(i)));
                return;
            }
        }
    }

    /** A class of the other game as the table names it: its path under that game's own source. */
    private static String className(Path root, String path) {
        String prefix = folder(root) + SOURCE;
        return path.substring(prefix.length(), path.length() - ".java".length()).replace('/', '.');
    }
}
