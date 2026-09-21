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
 * rather than in a bundle, and states a mob's health and defence beside it. Three shapes made the
 * difference between a table and a rumour, and each is read rather than skipped:
 *
 * <ul>
 *   <li><b>A name the game chooses at the moment it is shown.</b> Its bosses write
 *       {@code name = Dungeon.depth == Statistics.deepestFloor ? "Goo" : "spawn of Goo";}. Both
 *       are names a player sees, so the class contributes both, and a name statement that states
 *       no name at all is refused rather than passed over — a boss quietly missing from this
 *       table is the table asserting the other game does not have it.
 *   <li><b>A thing written inside another class.</b> Its Yog-Dzewa declares the rotting fist and
 *       the god's larva inside itself, and its wand of flock declares the sheep. This game does
 *       the same, so a reader that saw only files would compare one game's nested classes against
 *       nothing.
 *   <li><b>What a class is, rather than where its file sits.</b> The sheep is declared under the
 *       items folder and is a mob, so a class is a mob when it descends from the other game's
 *       {@code Mob}, not when its path says so.
 * </ul>
 *
 * <p>What it rolls for damage, accuracy and reduction are methods, carried as their text, because
 * the two games do not write those in one shape and the table's job is to say so rather than to
 * resolve it.
 */
final class Vanilla {

    static final String PIN = "vanilla.pin";
    static final String SOURCE = "core/src/main/java/com/watabou/pixeldungeon/";
    static final String MOBS = SOURCE + "actors/mobs/";
    static final String ITEMS = SOURCE + "items/";
    static final String MARKER = ".pinned";
    static final String FETCH = "tools/fetch-vanilla.sh";

    private static final Pattern NAME = Pattern.compile("^\\s*name\\s*=\\s*([^;]+);");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern HEALTH = Pattern.compile("\\bHT\\s*=\\s*([^;]+);");
    private static final Pattern DEFENCE = Pattern.compile("\\bdefenseSkill\\s*=\\s*([^;]+);");

    /** The rolls the other game writes as methods, carried as text on both sides of the diff. */
    static final List<String> ROLLS = List.of("damageRoll", "attackSkill", "dr");

    private Vanilla() {
    }

    /** Where the pinned tree sits. The pin names it; this constant is what the file gate allows. */
    static String folder(Path root) {
        String named = Citations.found(root, PIN, "^folder=(\\S+)$").value().trim() + "/";
        if (!named.equals(Sources.VANILLA_ROOT)) {
            throw new IllegalStateException(PIN + " puts the other game at " + named
                    + ", and the file gate only opens " + Sources.VANILLA_ROOT);
        }
        return named;
    }

    /** The tag the pin names, for a citation that says which game it points into. */
    static String tag(Path root) {
        return Citations.found(root, PIN, "^tag=(\\S+)$").value().trim();
    }

    /** The commit the pin names, which the fetched tree has to be at before a line of it is cited. */
    static String commit(Path root) {
        return Citations.found(root, PIN, "^commit=(\\S+)$").value().trim();
    }

    /**
     * Where a line of the other game is read, since the tree itself is never committed here: the
     * repository the pin names, at the commit it names, so a citation into it opens to the same
     * line the generator read (story 2.9). A pin naming a repository no browser can open is
     * refused rather than turned into a link that goes nowhere.
     */
    static String blob(Path root) {
        String repository = Citations.found(root, PIN, "^repository=(\\S+)$").value().trim();
        if (!repository.startsWith("https://") || !repository.endsWith(".git")) {
            throw new IllegalStateException(PIN + " names " + repository
                    + ", which is not an https repository a citation can be opened in");
        }
        return repository.substring(0, repository.length() - ".git".length()) + "/blob/" + commit(root) + "/";
    }

    /**
     * That the tree under the pin's folder is the commit the pin names, checked before anything is
     * read from it. A citation into a tree nobody verified is not a citation: the line numbers in
     * this table are only true of one commit, and the fetch script records which one it wrote.
     */
    static void pinned(Path root) {
        String folder = folder(root);
        if (!Sources.has(root, folder)) {
            throw new IllegalStateException(folder + " is not there: the other game is read, never committed."
                    + " Run " + FETCH + " to fetch it.");
        }
        String marker = folder + MARKER;
        if (!Sources.exists(root, marker)) {
            throw new IllegalStateException(folder + " carries no " + MARKER + " and so is at no known commit."
                    + " Run " + FETCH + " to fetch it.");
        }
        String at = Sources.contents(root, marker).trim();
        String wanted = commit(root);
        if (!at.equals(wanted)) {
            throw new IllegalStateException(folder + " is at " + at + " and " + PIN + " names " + wanted
                    + ". Run " + FETCH + " to fetch the pinned commit.");
        }
    }

    /** One thing the other game has: what it calls itself, and what it states about itself. */
    record Named(String className, String kind, String name, List<Codex.Rule> facts, Codex.Citation citation) {
    }

    /**
     * Every mob and item the other game names, with the health and defence each states and the
     * rolls each writes as methods. A class states its own name, so the walk is over the classes
     * the two folders declare rather than over the files, and a class that names itself twice
     * because the game shows two names contributes both.
     */
    static List<Named> named(Path root) {
        pinned(root);
        Stated tree = new Stated(root, folder(root) + SOURCE);
        List<Named> named = new ArrayList<>();
        List<Stated.Type> types = new ArrayList<>(tree.declared(folder(root) + MOBS));
        types.addAll(tree.declared(folder(root) + ITEMS));
        for (Stated.Type type : types) {
            Sources.Body body = tree.body(type.className());
            for (int line : names(body)) {
                boolean mob = tree.descendsFrom(type.className(), "Mob");
                List<Codex.Rule> facts = mob ? facts(tree, type.className()) : List.of();
                for (String name : literals(body, line)) {
                    named.add(new Named(type.className(), mob ? "mob" : "item", name, facts, body.citation(line)));
                }
            }
        }
        if (named.stream().noneMatch(n -> n.kind().equals("mob"))) {
            throw new IllegalStateException("the other game names no mob; the second pinned source is not where the reader looks");
        }
        if (named.stream().noneMatch(n -> n.kind().equals("item"))) {
            throw new IllegalStateException("the other game names no item; the second pinned source is not where the reader looks");
        }
        return named;
    }

    /** What a mob of the other game states about itself, its ancestors' statements included. */
    private static List<Codex.Rule> facts(Stated tree, String className) {
        List<Codex.Rule> facts = new ArrayList<>();
        add(facts, tree.stated(className, HEALTH, "health"));
        add(facts, tree.stated(className, DEFENCE, "defence"));
        for (String roll : ROLLS) {
            add(facts, tree.method(className, roll, roll));
        }
        return facts;
    }

    private static void add(List<Codex.Rule> facts, Codex.Rule rule) {
        if (rule != null) {
            facts.add(rule);
        }
    }

    /** The lines of a class's own members that name it: none where it names nothing, one for each name it states. */
    private static List<Integer> names(Sources.Body body) {
        List<Integer> found = new ArrayList<>();
        List<String> lines = Sources.stripped(body);
        List<Integer> where = new ArrayList<>(body.memberLines());
        where.addAll(body.constructorLines());
        java.util.Collections.sort(where);
        for (int i : where) {
            String line = lines.get(i - body.from());
            if (!NAME.matcher(line).find() || Stated.declaresField(line)) {
                continue;
            }
            found.add(i);
        }
        return found;
    }

    /**
     * The names one name statement states. A statement that states none is refused: a class that
     * names itself in a shape this reader cannot read must stop the generation, because the
     * alternative is a row saying the other game has no such thing.
     */
    private static List<String> literals(Sources.Body body, int line) {
        String text = Sources.stripped(body).get(line - body.from());
        Matcher statement = NAME.matcher(text);
        if (!statement.find()) {
            throw new IllegalStateException(body.path() + ":" + (line + 1) + ": no name where the reader found one");
        }
        List<String> names = new ArrayList<>();
        Matcher literal = LITERAL.matcher(statement.group(1));
        while (literal.find()) {
            if (!literal.group(1).isBlank()) {
                names.add(literal.group(1));
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException(body.path() + ":" + (line + 1)
                    + ": this class names itself in a shape the reader cannot read: " + statement.group(1).trim());
        }
        return names;
    }
}
