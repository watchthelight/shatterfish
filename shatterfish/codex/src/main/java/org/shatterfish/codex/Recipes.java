package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.items.Recipe;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The recipes (story 2.6): what the alchemy pot can do, read from the game's own three registries
 * of constructed recipes. The registries are private and a recipe's fields are protected, so both
 * are read from source, which is how every table of the Codex reads what the game does not
 * publish; a recipe is named by the registry entry that constructs it and resolved through the
 * file's imports, so a registry that names a class the reader cannot find fails the generation.
 *
 * <p>A recipe that states its inputs and its output as fixed lists carries them, with the
 * quantities and the energy cost its own initialiser sets. One that does not (a scroll turned to
 * the stone of its kind, a seed turned to its potion) carries the text of the three methods it
 * answers with instead, and says that its inputs are not a list, so that a reader is told the
 * shape of the thing rather than handed a guess.
 */
final class Recipes {

    static final String RECIPE = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/items/Recipe.java";

    /**
     * The recipe classes the game declares and never registers, each with its reason: the pot
     * cannot make them, so the table does not carry them, and the completeness test holds this
     * list against the difference rather than leaving it unexplained. Empty at this tag, because
     * the game registers every recipe class it declares.
     */
    static final List<java.util.Map.Entry<String, String>> UNREGISTERED = List.of();

    /**
     * The registries the pot tries, read from the method that tries them rather than named here,
     * so a registry the game adds cannot be missed. The game keeps one it tries for every count of
     * ingredients and one per count; the reader takes them in the order that method walks them.
     */
    static List<String> registries(Sources.Body registry) {
        int find = registry.find("public static ArrayList<Recipe> findRecipes\\(");
        if (find < 0) {
            throw new IllegalStateException("Recipe declares no findRecipes(); the reader cannot say which registries the pot tries");
        }
        List<String> names = new ArrayList<>();
        Matcher tried = Pattern.compile("for \\(Recipe recipe : (\\w+)\\)").matcher(Sources.text(registry.block(find)));
        while (tried.find()) {
            if (!names.contains(tried.group(1))) {
                names.add(tried.group(1));
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("findRecipes walks no registry; the pot no longer reads as the generator knows");
        }
        return names;
    }

    private static final Pattern MADE = Pattern.compile("new\\s+([\\w.]+)\\s*\\(([^)]*)\\)");
    private static final Pattern CLASS_TOKEN = Pattern.compile("\\b([\\w.]+)\\.class\\b");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private static final Pattern NAME = Pattern.compile("\\b([A-Z][A-Z0-9_]{2,})\\b");

    private Recipes() {
    }

    /** Every recipe the game registers, in the order of the registries and of each registry's own list. */
    static List<Codex.RecipeEntry> entries(Path root) {
        Sources.Body registry = Sources.file(root, RECIPE);
        List<Codex.RecipeEntry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (String name : registries(registry)) {
            int line = registry.find("private static Recipe\\[\\] " + name + "\\s*=");
            if (line < 0) {
                throw new IllegalStateException("Recipe declares no " + name + "; the pot's registries no longer read as the generator knows");
            }
            String ingredients = name.endsWith("IngredientRecipes") ? name.substring(0, name.indexOf("Ingredient")) : name;
            for (String made : made(registry, line)) {
                if (!seen.add(made)) {
                    throw new IllegalStateException(made + " is registered twice; a recipe is listed once");
                }
                entries.add(entry(root, registry, made, ingredients, registry.citation(line)));
            }
        }
        return entries;
    }

    /** The classes a registry's literal constructs, resolved through the file's imports. */
    private static List<String> made(Sources.Body registry, int line) {
        List<String> made = new ArrayList<>();
        List<String> lines = Sources.stripped(registry);
        for (int i = line; i < registry.to(); i++) {
            String text = lines.get(i - registry.from());
            Matcher entry = MADE.matcher(text);
            while (entry.find()) {
                if (!entry.group(2).isBlank()) {
                    throw new IllegalStateException(registry.path() + ":" + (i + 1) + ": a registry builds " + entry.group(1)
                            + " with arguments; the reader does not know what a recipe built with arguments is");
                }
                made.add(entry.group(1));
            }
            if (text.contains("};")) {
                return made;
            }
        }
        throw new IllegalStateException(registry.path() + ":" + (line + 1) + ": a registry does not end inside the file");
    }

    /**
     * One recipe: its fields where it states them, its methods' text where it does not. The class
     * is named as the registry writes it ({@code Blandfruit.CookFruit}), so the file it lives in
     * is the outer class's and the declaration is the nested one's.
     */
    static Codex.RecipeEntry entry(Path root, Sources.Body registry, String written, String ingredients, Codex.Citation registryCitation) {
        String outer = written.contains(".") ? written.substring(0, written.indexOf('.')) : written;
        String nested = written.contains(".") ? written.substring(written.indexOf('.') + 1) : written;
        String path = resolved(root, registry, outer);
        Sources.Body file = Sources.file(root, path);
        Sources.Body body = written.equals(outer) ? file : file.block(file.declaration(nested));
        int declaration = written.equals(outer) ? file.declaration(outer) : file.declaration(nested);
        String className = path.substring(Sources.SOURCE_ROOT.length() + Sources.ROOT_PACKAGE_PREFIX.length(), path.length() - ".java".length())
                .replace('/', '.') + (written.equals(outer) ? "" : "." + nested);
        int inputs = body.find("\\binputs\\s*=\\s*new Class");
        if (inputs < 0) {
            List<Codex.Rule> answers = new ArrayList<>();
            for (String method : List.of("testIngredients", "cost", "sampleOutput")) {
                int line = body.find("public .*\\b" + method + "\\s*\\(");
                if (line < 0) {
                    throw new IllegalStateException(className + " answers no " + method + "; a recipe that states no inputs must state its methods");
                }
                answers.add(new Codex.Rule(method, Sources.text(body.block(line)), body.citation(line)));
            }
            return new Codex.RecipeEntry(className, ingredients, false, List.of(), "", 0, -1, answers, registryCitation, body.citation(declaration));
        }
        List<String> classes = tokens(root, body, inputs, CLASS_TOKEN, file);
        List<Integer> quantities = numbers(body, file, body.find("\\binQuantity\\s*=\\s*new int"));
        if (classes.size() != quantities.size()) {
            throw new IllegalStateException(className + " states " + classes.size() + " inputs and " + quantities.size() + " quantities");
        }
        List<Codex.Ingredient> ingredientList = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            ingredientList.add(new Codex.Ingredient(classes.get(i), quantities.get(i)));
        }
        int outputLine = body.find("\\boutput\\s*=\\s*[\\w.]+\\.class");
        if (outputLine < 0) {
            throw new IllegalStateException(className + " states inputs and no output");
        }
        String output = tokens(root, body, outputLine, CLASS_TOKEN, file).get(0);
        int outQuantity = scalar(className, "outQuantity", numbers(body, file, body.find("\\boutQuantity\\s*=")));
        int cost = scalar(className, "cost", numbers(body, file, body.find("\\bcost\\s*=")));
        return new Codex.RecipeEntry(className, ingredients, true, ingredientList, output,
                outQuantity, cost, List.of(), registryCitation, body.citation(declaration));
    }

    /**
     * The one number a recipe states for a quantity or a cost. A statement that yields no number,
     * or more than one, is one the reader cannot read: taking the first would publish the first
     * integer that happens to appear in an expression the game computes, which is the defect the
     * named-constant reading was written to end.
     */
    private static int scalar(String className, String field, List<Integer> numbers) {
        if (numbers.size() != 1) {
            throw new IllegalStateException(className + " states " + numbers.size() + " numbers for " + field
                    + "; the reader will not pick one of them");
        }
        return numbers.get(0);
    }

    /** The class tokens of one statement, each resolved through the file's imports. */
    private static List<String> tokens(Path root, Sources.Body body, int line, Pattern pattern, Sources.Body file) {
        if (line < 0) {
            throw new IllegalStateException(body.path() + ": a statement the reader expected is not there");
        }
        List<String> names = new ArrayList<>();
        Matcher token = pattern.matcher(statement(body, line));
        while (token.find()) {
            String written = token.group(1);
            String outer = written.contains(".") ? written.substring(0, written.indexOf('.')) : written;
            String nested = written.substring(outer.length());
            String path = resolved(root, file, outer);
            names.add(path.substring(Sources.SOURCE_ROOT.length() + Sources.ROOT_PACKAGE_PREFIX.length(), path.length() - ".java".length())
                    .replace('/', '.') + nested);
        }
        return names;
    }

    /**
     * The numbers of one statement, in order. A recipe may set a quantity from a constant it names
     * rather than from a literal (the aqua brew makes {@code OUT_QUANTITY} potions), so a name is
     * resolved to the constant's own declaration in the same file; a name the reader cannot
     * resolve fails the generation rather than being read as nothing, which once left a recipe
     * carrying a default the game never stated.
     */
    private static List<Integer> numbers(Sources.Body body, Sources.Body file, int line) {
        List<Integer> numbers = new ArrayList<>();
        if (line < 0) {
            return numbers;
        }
        String text = statement(body, line);
        String assigned = text.substring(text.indexOf('=') + 1);
        Matcher number = NUMBER.matcher(assigned);
        while (number.find()) {
            numbers.add(Integer.parseInt(number.group()));
        }
        if (!numbers.isEmpty()) {
            return numbers;
        }
        Matcher named = NAME.matcher(assigned);
        while (named.find()) {
            numbers.add(constant(file, named.group(1)));
        }
        return numbers;
    }

    /** The value of a constant the file declares, which a recipe may name instead of a literal. */
    private static int constant(Sources.Body file, String name) {
        Pattern declared = Pattern.compile("\\bfinal int " + name + "\\s*=\\s*(-?\\d+)\\s*;");
        Integer value = null;
        for (int i = file.from(); i < file.to(); i++) {
            Matcher m = declared.matcher(Sources.stripComment(file.lines().get(i)));
            if (m.find()) {
                if (value != null) {
                    throw new IllegalStateException(file.path() + " declares " + name + " twice; the reader cannot say which the recipe means");
                }
                value = Integer.parseInt(m.group(1));
            }
        }
        if (value == null) {
            throw new IllegalStateException(file.path() + " names " + name + " in a recipe's quantity and declares no constant of that name;"
                    + " the reader will not guess what the pot makes");
        }
        return value;
    }

    /** The whole statement at {@code line}, joined to its semicolon. */
    private static String statement(Sources.Body body, int line) {
        StringBuilder text = new StringBuilder();
        for (int i = line; i < body.to(); i++) {
            String part = Sources.stripComment(body.lines().get(i)).trim();
            text.append(text.length() == 0 ? "" : " ").append(part);
            if (part.endsWith(";")) {
                return text.toString();
            }
        }
        throw new IllegalStateException(body.path() + ":" + (line + 1) + ": a statement does not end inside the class");
    }

    /** The source path of the class {@code simpleName} the file imports, or the file's own class. */
    private static String resolve(Sources.Body body, String simpleName) {
        Pattern imported = Pattern.compile("^import\\s+" + Pattern.quote(Sources.ROOT_PACKAGE_PREFIX) + "([\\w.]+)\\." + Pattern.quote(simpleName) + "\\s*;");
        for (String line : body.lines()) {
            Matcher m = imported.matcher(line);
            if (m.find()) {
                return Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/" + m.group(1).replace('.', '/') + "/" + simpleName + ".java";
            }
        }
        if (body.path().endsWith("/" + simpleName + ".java")) {
            return body.path();
        }
        // A class of the same package needs no import; the game keeps the recipe base beside it.
        String folder = body.path().substring(0, body.path().lastIndexOf('/') + 1);
        return folder + simpleName + ".java";
    }

    /**
     * A resolved path, checked to be a file of the pinned core. Without this a name reached
     * without an import resolves to a path nothing backs, and the table publishes a class name a
     * reader cannot follow, which is a guess wearing the shape of a reading.
     */
    private static String resolved(Path root, Sources.Body body, String simpleName) {
        String path = resolve(body, simpleName);
        Sources.file(root, path);
        return path;
    }

    /** The registry class the pot reads, for a citation that names it. */
    static Class<?> base() {
        return Recipe.class;
    }
}
