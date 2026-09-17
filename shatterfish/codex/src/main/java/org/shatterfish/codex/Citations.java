package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A citation is read, not remembered (story 2.1; non-negotiable 8): the generator names the file
 * and an anchor that matches the declaration's line, and the line number comes from the pinned
 * source at generation. An anchor that matches no line, or more than one, fails the generation
 * naming both, so that a declaration that moved or was renamed cannot keep a stale citation.
 */
final class Citations {

    private Citations() {
    }

    /**
     * The one line of {@code root/path} matching {@code anchor}, as a citation with {@code path}
     * kept relative and forward-slashed. The file is read as UTF-8; a byte-order mark on the
     * first line is not part of it.
     */
    static Codex.Citation at(Path root, String path, String anchor) {
        return found(root, path, anchor).citation();
    }

    /** What one line said, with the citation of that line. */
    record Found(String value, Codex.Citation citation) {
    }

    /**
     * The one line of {@code root/path} matching {@code anchor}, with what its first capture group
     * held. A caller that needs the value as well as the line reads both here rather than opening
     * the file a second time.
     */
    static Found found(Path root, String path, String anchor) {
        Path file = root.resolve(path);
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            throw new UncheckedIOException("citation " + path + " for anchor " + anchor + ": the file is not UTF-8", e);
        } catch (IOException e) {
            throw new UncheckedIOException("citation " + path + " for anchor " + anchor + ": the file could not be read under " + root, e);
        }
        if (!lines.isEmpty() && lines.get(0).startsWith("﻿")) {
            lines.set(0, lines.get(0).substring(1));
        }
        Pattern pattern = Pattern.compile(anchor);
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                hits.add(i + 1);
            }
        }
        if (hits.size() != 1) {
            throw new IllegalStateException("citation " + path + " for anchor " + anchor + ": "
                    + (hits.isEmpty() ? "no line matches" : "lines " + hits + " all match; one declaration is one line"));
        }
        java.util.regex.Matcher found = pattern.matcher(lines.get(hits.get(0) - 1));
        String value = found.find() && found.groupCount() >= 1 ? found.group(1) : "";
        return new Found(value, new Codex.Citation(path, hits.get(0)));
    }
}
