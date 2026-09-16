package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * one), a method's block inside that range, the declaring class of a method found by walking up
 * the hierarchy, and the lines of a block that return. Nothing here is a parser of Java; it is
 * the shape a declaration has in this code base, and an anchor that stops matching fails loudly.
 */
final class Sources {

    static final String GAME = "com.shatteredpixel.shatteredpixeldungeon";
    static final String ROOT_PACKAGE_PREFIX = GAME + ".";
    static final String SOURCE_ROOT = "core/src/main/java/";

    private Sources() {
    }

    /** A class's body in its file: the file's path, its lines, and the body's line range. */
    record Body(String path, List<String> lines, int from, int to) {

        private static final Pattern TYPE_DECLARATION = Pattern.compile(
                "^\\s*(?:(?:public|private|protected|static|abstract|final)\\s+)*(?:class|enum|interface)\\s+\\w+");

        /**
         * The body's own lines: every line of the range except those inside a nested type's
         * block, since a nested class's method is not the enclosing class's.
         */
        List<Integer> ownLines() {
            List<Integer> own = new ArrayList<>();
            int i = from;
            boolean first = true;
            while (i < to) {
                String line = lines.get(i);
                if (TYPE_DECLARATION.matcher(line).find()) {
                    if (first) {
                        first = false;
                    } else {
                        i = block(i).to();
                        continue;
                    }
                }
                own.add(i);
                i++;
            }
            return own;
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

        /** The one line of the whole range declaring a type named {@code simpleName}, or -1; more than one fails. */
        int declaration(String simpleName) {
            Pattern pattern = Pattern.compile("\\b(?:class|enum|interface)\\s+" + Pattern.quote(simpleName) + "\\b");
            int hit = -1;
            for (int i = from; i < to; i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    if (hit >= 0) {
                        throw new IllegalStateException(path + " lines " + (hit + 1) + " and " + (i + 1) + " both declare " + simpleName);
                    }
                    hit = i;
                }
            }
            return hit;
        }

        /** The first own line of the body matching {@code pattern}, or -1. */
        int first(Pattern pattern) {
            for (int i : ownLines()) {
                if (pattern.matcher(lines.get(i)).matches()) {
                    return i;
                }
            }
            return -1;
        }

        /** The block starting at the first {@code {} at or after {@code line}, as a body of its own. */
        Body block(int line) {
            int depth = 0;
            boolean opened = false;
            for (int i = line; i < to; i++) {
                for (char c : lines.get(i).toCharArray()) {
                    if (c == '{') {
                        depth++;
                        opened = true;
                    } else if (c == '}') {
                        depth--;
                        if (opened && depth == 0) {
                            return new Body(path, lines, line, i + 1);
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

    /** The body of {@code type} in the pinned source. */
    static Body body(Path root, Class<?> type) {
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
        Body body = new Body(path, lines, 0, lines.size());
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c.getEnclosingClass() != null; c = c.getEnclosingClass()) {
            chain.add(0, c);
        }
        for (Class<?> nested : chain) {
            int line = body.declaration(nested.getSimpleName());
            if (line < 0) {
                throw new IllegalStateException(nested.getName() + " is not declared in " + path);
            }
            body = body.block(line);
        }
        return body;
    }

    /** A method's block and declaration line in the class that declares it, walking up to {@code stopAt} inclusive. */
    record Declared(Class<?> owner, Body block, int line) {
    }

    /**
     * The nearest class in {@code type}'s hierarchy, up to and including {@code stopAt}, whose
     * body declares a method matching {@code anchor}; null when none does.
     */
    static Declared declared(Path root, Class<?> type, Class<?> stopAt, String anchor) {
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

    /** The trimmed text of every line of {@code block} that returns something. */
    static List<String> returns(Body block) {
        List<String> out = new ArrayList<>();
        Pattern returning = Pattern.compile("^\\s*return\\b");
        for (int i = block.from(); i < block.to(); i++) {
            String line = block.lines().get(i);
            if (returning.matcher(line).find()) {
                out.add(line.trim());
            }
        }
        return out;
    }

    /** A literal float or a simple fraction as thousandths, rounded, or -1. */
    static int thousandths(String expression) {
        String text = expression.trim();
        Matcher literal = Pattern.compile("^(\\d*\\.\\d+|\\d+)f?$").matcher(text);
        if (literal.matches()) {
            return Math.round(Float.parseFloat(literal.group(1)) * 1000f);
        }
        Matcher fraction = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)f?$").matcher(text);
        if (fraction.matches()) {
            return Math.round(1000f * Integer.parseInt(fraction.group(1)) / Integer.parseInt(fraction.group(2)));
        }
        Matcher fractionF = Pattern.compile("^(\\d+)f\\s*/\\s*(\\d+)f?$").matcher(text);
        if (fractionF.matches()) {
            return Math.round(1000f * Integer.parseInt(fractionF.group(1)) / Integer.parseInt(fractionF.group(2)));
        }
        return -1;
    }
}
