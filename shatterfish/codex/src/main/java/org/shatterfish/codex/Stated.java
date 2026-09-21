package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a class of a pinned tree states about itself in its own source (story 2.8).
 *
 * <p>Both games write a mob's numbers the same way — an initialiser block that assigns {@code HT}
 * and {@code defenseSkill} — and both let a subclass restate one and inherit the rest. So one
 * reader serves both trees, and the vocabulary diff sets like against like: the text each game
 * writes, read from the line that writes it, cited there.
 *
 * <p>Three rules make the difference between a fact and a guess, and every one of them was a
 * defect this reader was written to end:
 *
 * <ul>
 *   <li><b>A field's declaration is not a statement.</b> {@code protected int defenseSkill = 0;}
 *       in an abstract base declares a field; it does not say a mob has no defence. Only an
 *       assignment — in an initialiser or a constructor — states anything.
 *   <li><b>The walk stops at the first class that states the field at all.</b> A class that
 *       writes {@code defenseSkill = 4 + Dungeon.depth;} has stated its defence, even though the
 *       value is not a number the table can compare. Walking past it to an ancestor would publish
 *       the ancestor's number as this class's, which is a citation pointing at a line that says
 *       something else.
 *   <li><b>What is stated is carried as text.</b> A bare integer compares; an expression does
 *       not. The table says which, rather than resolving an expression it cannot evaluate.
 * </ul>
 */
final class Stated {

    /** A type declared in a pinned tree: where it is written, and what the file around it says. */
    record Type(String className, String path, int line) {
    }

    private static final Pattern DECLARED = Pattern.compile(
            "^\\s*(?:@\\w+\\s+)*(?:(?:public|private|protected|static|abstract|final|sealed|non-sealed)\\s+)*"
                    + "(?:class|enum|interface|record)\\s+(\\w+)");
    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+([\\w.]+)");
    private static final Pattern IMPORTED = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");
    private static final Pattern METHOD = Pattern.compile("^\\s*(?:@\\w+\\s+)*(?:public|protected|private)\\s");

    /**
     * A line that declares a field rather than assigning one: a type and a name before the
     * {@code =}. {@code protected int defenseSkill = 0;} matches; {@code defenseSkill = 2;} and
     * {@code HP = HT = 8;} do not.
     */
    private static final Pattern FIELD_DECLARATION = Pattern.compile(
            "^\\s*(?:@\\w+\\s+)*(?:(?:public|private|protected|static|final|transient|volatile)\\s+)*"
                    + "(?:\\w+\\.)*\\w+(?:<[^>]*>)?(?:\\[\\s*\\])?\\s+\\w+\\s*=");

    /** How far the reader will follow {@code extends} before it decides the tree is not a tree. */
    private static final int DEPTH = 32;

    private final Path root;
    private final String source;
    private final Map<String, List<String>> byName = new TreeMap<>();
    private final Map<String, Sources.Body> bodies = new TreeMap<>();

    /** A pinned tree, indexed by simple name once so a superclass can be resolved across folders. */
    Stated(Path root, String source) {
        this.root = root;
        this.source = source;
        for (String path : Sources.under(root, source)) {
            String simple = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            byName.computeIfAbsent(simple, k -> new ArrayList<>()).add(path);
        }
    }

    /** The path prefix every class of this tree is named under. */
    String source() {
        return source;
    }

    /**
     * Every type declared under one folder of this tree, the nested ones included, named the way
     * the Codex names a class: the file's path under the tree's source root, dotted, with each
     * enclosing type's name before the nested one.
     *
     * <p>Nested types are read because both games put real things in them — one game's rotting
     * fist and god's larva are nested inside its Yog-Dzewa, the other's shocker is nested inside
     * its Tengu — and a diff that read one game's nested classes and not the other's would report
     * the difference between two readers rather than between two games.
     */
    List<Type> declared(String folder) {
        List<Type> types = new ArrayList<>();
        for (String path : Sources.under(root, folder)) {
            Sources.Body file = Sources.file(root, path);
            String outer = className(path);
            types.add(new Type(outer, path, file.declaration(simpleName(outer))));
            for (Type nested : nestedOf(file, outer)) {
                types.add(nested);
            }
        }
        return types;
    }

    /** The types declared inside one type's body, to any depth. */
    private List<Type> nestedOf(Sources.Body body, String enclosing) {
        List<Type> types = new ArrayList<>();
        List<String> lines = Sources.stripped(body);
        // The range is walked rather than the body's own lines: a nested type's declaration is
        // exactly the line its own lines leave out, so a reader over those would find none.
        int i = body.from();
        while (i < body.to()) {
            Matcher declared = DECLARED.matcher(lines.get(i - body.from()));
            if (declared.find() && !declared.group(1).equals(simpleName(enclosing))) {
                String name = enclosing + "." + declared.group(1);
                Sources.Body nested = body.block(i);
                types.add(new Type(name, body.path(), i));
                types.addAll(nestedOf(nested, name));
                i = Math.max(nested.to(), i + 1);
                continue;
            }
            i++;
        }
        return types;
    }

    /** The body of one class of this tree, nested or not. */
    Sources.Body body(String className) {
        Sources.Body held = bodies.get(className);
        if (held != null) {
            return held;
        }
        int dot = className.lastIndexOf('.');
        Sources.Body found;
        String path = source + className.replace('.', '/') + ".java";
        if (Sources.exists(root, path)) {
            found = Sources.file(root, path);
        } else if (dot < 0) {
            throw new IllegalStateException(className + " is not a class of " + source);
        } else {
            Sources.Body enclosing = body(className.substring(0, dot));
            found = enclosing.block(enclosing.declaration(className.substring(dot + 1)));
        }
        bodies.put(className, found);
        return found;
    }

    /**
     * What a class states for one field, as the text it writes, from the class itself or from the
     * nearest ancestor that states it at all. A class that states nothing about the field, and
     * whose ancestors state nothing either, has no fact to publish and none is invented.
     */
    Codex.Rule stated(String className, Pattern pattern, String what) {
        Set<String> seen = new TreeSet<>();
        String at = className;
        for (int depth = 0; at != null && depth < DEPTH; depth++) {
            if (!seen.add(at)) {
                throw new IllegalStateException(className + " extends itself through " + at);
            }
            Sources.Body body = body(at);
            Codex.Rule rule = assigned(body, pattern, what);
            if (rule != null) {
                return rule;
            }
            at = extended(at);
        }
        if (at != null) {
            throw new IllegalStateException(className + " extends more than " + DEPTH + " classes");
        }
        return null;
    }

    /** The assignment one class writes for a field, in an initialiser or a constructor, if any. */
    private Codex.Rule assigned(Sources.Body body, Pattern pattern, String what) {
        List<String> lines = Sources.stripped(body);
        List<Integer> where = new ArrayList<>(body.memberLines());
        where.addAll(body.constructorLines());
        java.util.Collections.sort(where);
        for (int i : where) {
            String line = lines.get(i - body.from());
            if (FIELD_DECLARATION.matcher(line).find()) {
                continue;
            }
            Matcher found = pattern.matcher(line);
            if (found.find()) {
                return new Codex.Rule(what, found.group(1).trim(), body.citation(i));
            }
        }
        return null;
    }

    /**
     * The method one class writes, as its text, from the class itself or the nearest ancestor
     * that writes it. The two games write a roll as a method and the table carries what each
     * writes rather than pretending the two are one measurement.
     *
     * <p>The mechanic and the method are named separately because the two games do not call the
     * same mechanic by the same name: what one writes as {@code dr()} the other writes as
     * {@code drRoll()}, and a row that keyed on the method name would set neither against the
     * other and quietly report no difference where it had simply failed to look.
     */
    Codex.Rule method(String className, String what, String name) {
        Set<String> seen = new TreeSet<>();
        String at = className;
        for (int depth = 0; at != null && depth < DEPTH; depth++) {
            if (!seen.add(at)) {
                throw new IllegalStateException(className + " extends itself through " + at);
            }
            Sources.Body body = body(at);
            List<String> lines = Sources.stripped(body);
            Pattern header = Pattern.compile("\\b(?:int|float|long|double)\\s+" + name + "\\s*\\(");
            for (int i : body.ownLines()) {
                String line = lines.get(i - body.from());
                if (METHOD.matcher(line).find() && header.matcher(line).find()) {
                    return new Codex.Rule(what, Sources.text(body.block(i)), body.citation(i));
                }
            }
            at = extended(at);
        }
        return null;
    }

    /** Whether a class is, or descends from, a class of the given simple name. */
    boolean descendsFrom(String className, String ancestor) {
        Set<String> seen = new TreeSet<>();
        String at = className;
        for (int depth = 0; at != null && depth < DEPTH; depth++) {
            if (!seen.add(at)) {
                return false;
            }
            if (simpleName(at).equals(ancestor)) {
                return true;
            }
            at = extended(at);
        }
        return false;
    }

    /**
     * The class one class extends, named the way this tree names a class, or none where the class
     * extends nothing of this tree. The declaration is read as a whole rather than one line, so a
     * wrapped {@code extends} does not silently end a walk, and the name is resolved through the
     * file's own imports before its own folder, because a mob's base class is not always beside
     * it — one game keeps its shopkeepers in a folder of their own and they extend a class one
     * level up.
     */
    private String extended(String className) {
        Sources.Body body = body(className);
        List<String> lines = Sources.stripped(body);
        StringBuilder header = new StringBuilder();
        for (int i = body.from(); i < body.to(); i++) {
            header.append(' ').append(lines.get(i - body.from()));
            if (lines.get(i - body.from()).contains("{")) {
                break;
            }
        }
        Matcher found = EXTENDS.matcher(header.toString());
        if (!found.find()) {
            return null;
        }
        String named = found.group(1);
        String simple = named.substring(named.lastIndexOf('.') + 1);
        String file = body.path();
        String within = packageOf();
        for (String line : Sources.stripped(Sources.file(root, file))) {
            Matcher imported = IMPORTED.matcher(line);
            if (!imported.find() || !imported.group(1).endsWith("." + simple) || !imported.group(1).startsWith(within)) {
                continue;
            }
            String path = source + imported.group(1).substring(within.length()).replace('.', '/') + ".java";
            if (Sources.exists(root, path)) {
                return className(path);
            }
        }
        String beside = file.substring(0, file.lastIndexOf('/') + 1) + simple + ".java";
        if (Sources.exists(root, beside)) {
            return className(beside);
        }
        List<String> paths = byName.get(simple);
        if (paths != null && paths.size() == 1) {
            return className(paths.get(0));
        }
        int dot = className.lastIndexOf('.');
        if (dot >= 0) {
            String sibling = className.substring(0, dot + 1) + simple;
            if (declaredIn(body(className.substring(0, dot)), simple)) {
                return sibling;
            }
        }
        return null;
    }

    /** Whether a type of this simple name is declared directly inside a body. */
    private boolean declaredIn(Sources.Body body, String simple) {
        List<String> lines = Sources.stripped(body);
        for (int i : body.ownLines()) {
            Matcher declared = DECLARED.matcher(lines.get(i - body.from()));
            if (declared.find() && declared.group(1).equals(simple)) {
                return true;
            }
        }
        return false;
    }

    /** The package every class of this tree sits under, as the dotted tail of its source root. */
    private String packageOf() {
        String trimmed = source.substring(0, source.length() - 1);
        int java = trimmed.indexOf("/java/");
        return java < 0 ? "" : trimmed.substring(java + "/java/".length()).replace('/', '.') + ".";
    }

    /** A path of this tree as the Codex names the class in it. */
    String className(String path) {
        if (!path.startsWith(source) || !path.endsWith(".java")) {
            throw new IllegalStateException(path + " is not a source of " + source);
        }
        return path.substring(source.length(), path.length() - ".java".length()).replace('/', '.');
    }

    /** The last segment of a dotted class name. */
    static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** Whether a line declares a field rather than assigning one. */
    static boolean declaresField(String line) {
        return FIELD_DECLARATION.matcher(line).find();
    }
}
