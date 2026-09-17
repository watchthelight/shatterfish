package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pinned source of a class, read as a citation is (story 2.2): a class's file and the line
 * range its body occupies (the whole file for a top-level class, the braced block for a nested
 * one), the lines that are the class's own (not a nested or anonymous type's), the lines that
 * are its members' (not a method's body), a method's block, the declaring class of a method
 * found by walking up the hierarchy, and the statements of a block that return. Nothing here is
 * a parser of Java; it is the shape a declaration has in this code base, braces counted outside
 * comments and literals, and an anchor that stops matching fails loudly.
 */
final class Sources {

    static final String GAME = "com.shatteredpixel.shatteredpixeldungeon";
    static final String ROOT_PACKAGE_PREFIX = GAME + ".";
    static final String SOURCE_ROOT = "core/src/main/java/";

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|abstract|final|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|enum|interface|record)\\s+(\\w+)");
    private static final Pattern ANONYMOUS = Pattern.compile("\\bnew\\s+[\\w.<>]+\\s*\\([^;{]*\\)\\s*\\{\\s*$");
    private static final Pattern METHOD_HEADER = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|abstract|final|synchronized|native)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w.<>\\[\\], ?]+?\\s*\\b(\\w+)\\s*\\([^;{]*\\)\\s*(?:throws\\s+[\\w., ]+)?\\s*\\{\\s*$");
    private static final Pattern CONTROL = Pattern.compile("^\\s*(?:if|for|while|switch|catch|synchronized|else|do|try|return)\\b");

    private Sources() {
    }

    /** A class's body in its file: the file's path, its lines, the body's line range, and the class's simple name. */
    record Body(String path, List<String> lines, int from, int to, String ownName) {

        /**
         * The body's own lines: every line of the range except those inside another type's block
         * (a nested class, enum, interface or record, or an anonymous class), since a nested
         * type's method is not the enclosing class's.
         */
        List<Integer> ownLines() {
            List<Integer> own = new ArrayList<>();
            int i = from;
            while (i < to) {
                String line = lines.get(i);
                Matcher type = TYPE_DECLARATION.matcher(line);
                boolean otherType = type.find() && !type.group(1).equals(ownName);
                if (otherType || ANONYMOUS.matcher(line).find()) {
                    i = block(i).to();
                    continue;
                }
                own.add(i);
                i++;
            }
            return own;
        }

        /**
         * The body's member lines: its own lines outside any method's or constructor's body, so
         * that a field's declaration or an initialiser's assignment is found and a method's
         * local of the same name is not.
         */
        List<Integer> memberLines() {
            List<Integer> members = new ArrayList<>();
            List<Integer> own = ownLines();
            int skipTo = -1;
            for (int i : own) {
                if (i < skipTo) {
                    continue;
                }
                String line = lines.get(i);
                if (i != from && METHOD_HEADER.matcher(line).find() && !CONTROL.matcher(line).find()) {
                    skipTo = block(i).to();
                    continue;
                }
                members.add(i);
            }
            return members;
        }

        /** The body's lines inside its constructors' bodies, where a draw at construction is written. */
        List<Integer> constructorLines() {
            List<Integer> inside = new ArrayList<>();
            for (int i : ownLines()) {
                Matcher header = METHOD_HEADER.matcher(lines.get(i));
                if (i != from && header.find() && header.group(1).equals(ownName) && !CONTROL.matcher(lines.get(i)).find()) {
                    Body constructor = block(i);
                    for (int j = constructor.from() + 1; j < constructor.to() - 1; j++) {
                        inside.add(j);
                    }
                }
            }
            return inside;
        }

        /** The one own line of the body matching {@code anchor}, or -1; more than one fails. */
        int find(String anchor) {
            Pattern pattern = Pattern.compile(anchor);
            int hit = -1;
            for (int i : ownLines()) {
                if (pattern.matcher(lines.get(i)).find()) {
                    if (hit >= 0) {
                        throw new IllegalStateException(path + " lines " + (hit + 1) + " and " + (i + 1) + " both match " + anchor);
                    }
                    hit = i;
                }
            }
            return hit;
        }

        /** The one line of the whole range declaring a type named {@code simpleName}; none or more than one fails. */
        int declaration(String simpleName) {
            int hit = -1;
            for (int i = from; i < to; i++) {
                Matcher type = TYPE_DECLARATION.matcher(lines.get(i));
                if (type.find() && type.group(1).equals(simpleName)) {
                    if (hit >= 0) {
                        throw new IllegalStateException(path + " lines " + (hit + 1) + " and " + (i + 1) + " both declare " + simpleName);
                    }
                    hit = i;
                }
            }
            if (hit < 0) {
                throw new IllegalStateException(simpleName + " is not declared in " + path);
            }
            return hit;
        }

        /** The first member line of the body matching {@code pattern} as a whole, or -1. */
        int firstMember(Pattern pattern) {
            for (int i : memberLines()) {
                if (pattern.matcher(lines.get(i)).matches()) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * The block starting at the first {@code {} at or after {@code line}, as a body named by
         * the type or method declared on that line (or the enclosing name for a bare block);
         * braces inside comments, strings and character literals do not count.
         */
        Body block(int line) {
            int depth = 0;
            boolean opened = false;
            boolean inBlockComment = false;
            Matcher type = TYPE_DECLARATION.matcher(lines.get(line));
            String name = type.find() ? type.group(1) : ownName;
            for (int i = line; i < to; i++) {
                String text = lines.get(i);
                boolean inString = false;
                boolean inChar = false;
                for (int c = 0; c < text.length(); c++) {
                    char ch = text.charAt(c);
                    char next = c + 1 < text.length() ? text.charAt(c + 1) : '\0';
                    if (inBlockComment) {
                        if (ch == '*' && next == '/') {
                            inBlockComment = false;
                            c++;
                        }
                        continue;
                    }
                    if (inString) {
                        if (ch == '\\') {
                            c++;
                        } else if (ch == '"') {
                            inString = false;
                        }
                        continue;
                    }
                    if (inChar) {
                        if (ch == '\\') {
                            c++;
                        } else if (ch == '\'') {
                            inChar = false;
                        }
                        continue;
                    }
                    if (ch == '/' && next == '/') {
                        break;
                    }
                    if (ch == '/' && next == '*') {
                        inBlockComment = true;
                        c++;
                        continue;
                    }
                    if (ch == '"') {
                        inString = true;
                    } else if (ch == '\'') {
                        inChar = true;
                    } else if (ch == '{') {
                        depth++;
                        opened = true;
                    } else if (ch == '}') {
                        depth--;
                        if (opened && depth == 0) {
                            return new Body(path, lines, line, i + 1, name);
                        }
                    }
                }
            }
            throw new IllegalStateException(path + ":" + (line + 1) + ": the block does not close inside the body");
        }

        Codex.Citation citation(int line) {
            return new Codex.Citation(path, line + 1);
        }
    }

    /** The class's name without the game's root package, with dots between an outer and a nested class. */
    static String name(Class<?> type) {
        String canonical = type.getCanonicalName();
        if (canonical == null || !canonical.startsWith(ROOT_PACKAGE_PREFIX)) {
            throw new IllegalStateException("not a game class: " + type.getName());
        }
        return canonical.substring(ROOT_PACKAGE_PREFIX.length());
    }

    /** The body of {@code type} in the pinned source; a class outside the game's core is refused by name. */
    static Body body(Path root, Class<?> type) {
        if (!type.getName().startsWith(ROOT_PACKAGE_PREFIX)) {
            throw new IllegalStateException("not a class of the pinned core: " + type.getName());
        }
        Class<?> outer = type;
        while (outer.getEnclosingClass() != null) {
            outer = outer.getEnclosingClass();
        }
        String path = SOURCE_ROOT + outer.getName().replace('.', '/') + ".java";
        List<String> lines;
        try {
            lines = Files.readAllLines(root.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the source of " + type.getName() + " could not be read at " + path, e);
        }
        if (!lines.isEmpty() && lines.get(0).startsWith("﻿")) {
            lines.set(0, lines.get(0).substring(1));
        }
        Body body = new Body(path, lines, 0, lines.size(), outer.getSimpleName());
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c.getEnclosingClass() != null; c = c.getEnclosingClass()) {
            chain.add(0, c);
        }
        for (Class<?> nested : chain) {
            body = body.block(body.declaration(nested.getSimpleName()));
        }
        return body;
    }

    /**
     * The body of the file at {@code path} under the root (story 2.4), for a class the generator
     * may not name ({@code Dungeon}): the whole file, named by the file's simple name.
     */
    static Body file(Path root, String path) {
        if (!path.startsWith(SOURCE_ROOT) || !path.endsWith(".java")) {
            throw new IllegalStateException("not a source of the pinned core: " + path);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(root.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the source could not be read at " + path, e);
        }
        if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
            lines.set(0, lines.get(0).substring(1));
        }
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
        return new Body(path, lines, 0, lines.size(), name);
    }

    /**
     * A block's body as one line: its statements between the braces, comments stripped (a
     * {@code //} to the end of its line and a {@code /* *}{@code /} wherever it runs, so that a
     * comment a later tag adds inside a pinned method is not mistaken for the method moving),
     * blank lines dropped, whitespace collapsed.
     */
    static String text(Body block) {
        List<String> parts = new ArrayList<>();
        boolean inBlockComment = false;
        for (int i = block.from() + 1; i < block.to() - 1; i++) {
            StringBuilder line = new StringBuilder();
            String source = block.lines().get(i);
            for (int c = 0; c < source.length(); c++) {
                if (inBlockComment) {
                    if (source.startsWith("*/", c)) {
                        inBlockComment = false;
                        c++;
                    }
                } else if (source.startsWith("/*", c)) {
                    inBlockComment = true;
                    c++;
                } else if (source.startsWith("//", c)) {
                    break;
                } else {
                    line.append(source.charAt(c));
                }
            }
            String text = line.toString().trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }

    /** A method's block and declaration line in the class that declares it. */
    record Declared(Class<?> owner, Body block, int line) {
    }

    /**
     * The nearest class in {@code type}'s hierarchy, up to and including {@code stopAt}, whose
     * own lines declare a method matching {@code anchor}; null when none does. {@code stopAt}
     * is an ancestor of {@code type} or the walk is refused.
     */
    static Declared declared(Path root, Class<?> type, Class<?> stopAt, String anchor) {
        if (!stopAt.isAssignableFrom(type)) {
            throw new IllegalArgumentException(stopAt.getName() + " is not an ancestor of " + type.getName());
        }
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            Body body = body(root, c);
            int line = body.find(anchor);
            if (line >= 0) {
                return new Declared(c, body.block(line), line);
            }
            if (c == stopAt) {
                return null;
            }
        }
        return null;
    }

    /**
     * Every return statement of {@code block}, each as one trimmed text ending in its semicolon,
     * joined across lines, comments stripped: a return split over lines is one statement, and a
     * return after an {@code if} on the same line is one too.
     */
    static List<String> returns(Body block) {
        List<String> out = new ArrayList<>();
        StringBuilder pending = null;
        for (int i = block.from(); i < block.to(); i++) {
            String line = stripComment(block.lines().get(i)).trim();
            if (pending == null) {
                int at = returnAt(line);
                if (at < 0) {
                    continue;
                }
                pending = new StringBuilder(line.substring(at));
            } else {
                pending.append(' ').append(line);
            }
            if (pending.toString().endsWith(";")) {
                out.add(pending.toString());
                pending = null;
            }
        }
        if (pending != null) {
            throw new IllegalStateException(block.path() + ":" + (block.from() + 1) + ": a return does not end inside the block");
        }
        return out;
    }

    private static int returnAt(String line) {
        Matcher m = Pattern.compile("(?:^|[\\s;{}])return\\b").matcher(line);
        return m.find() ? m.end() - "return".length() : -1;
    }

    /**
     * The net braces of one line, opened less closed, counted outside string and character
     * literals and outside a {@code //} comment; a block comment on one line is skipped, one
     * spanning lines is not (the readers are given a method's block, which {@link Body#block}
     * already closed with the same care).
     */
    static int braces(String line) {
        int net = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int c = 0; c < line.length(); c++) {
            char ch = line.charAt(c);
            char next = c + 1 < line.length() ? line.charAt(c + 1) : '\0';
            if (inString) {
                if (ch == '\\') {
                    c++;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (inChar) {
                if (ch == '\\') {
                    c++;
                } else if (ch == '\'') {
                    inChar = false;
                }
            } else if (ch == '/' && next == '/') {
                break;
            } else if (ch == '/' && next == '*') {
                int end = line.indexOf("*/", c + 2);
                if (end < 0) {
                    break;
                }
                c = end + 1;
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '\'') {
                inChar = true;
            } else if (ch == '{') {
                net++;
            } else if (ch == '}') {
                net--;
            }
        }
        return net;
    }

    /** {@code line} without a trailing {@code //} comment (a {@code //} inside a string is not one here). */
    static String stripComment(String line) {
        int at = line.indexOf("//");
        return at < 0 ? line : line.substring(0, at);
    }

    /**
     * A literal float, an integer, or a simple fraction as thousandths, rounded half up, or -1
     * when the text is not one of those; a chance that is not zero but rounds to zero is refused,
     * since "never" would be a lie.
     */
    static int thousandths(String expression) {
        String text = expression.trim();
        BigDecimal value;
        Matcher literal = Pattern.compile("^(\\d*\\.\\d+|\\d+)[fF]?$").matcher(text);
        Matcher fraction = Pattern.compile("^(\\d+)[fF]?\\s*/\\s*(\\d+)[fF]?$").matcher(text);
        if (literal.matches()) {
            value = new BigDecimal(literal.group(1));
        } else if (fraction.matches()) {
            value = new BigDecimal(fraction.group(1)).divide(new BigDecimal(fraction.group(2)), 12, RoundingMode.HALF_UP);
        } else {
            return -1;
        }
        int thousandths = value.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValueExact();
        if (thousandths == 0 && value.signum() != 0) {
            throw new IllegalStateException("a chance of " + expression + " is not zero and rounds to zero thousandths");
        }
        return thousandths;
    }
}
