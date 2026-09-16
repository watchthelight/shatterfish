package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.io.IOException;
import java.io.UncheckedIOException;
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
     * kept relative and forward-slashed.
     */
    static Codex.Citation at(Path root, String path, String anchor) {
        Path file = root.resolve(path);
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("citation " + path + " for anchor " + anchor + ": the file could not be read", e);
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
        return new Codex.Citation(path, hits.get(0));
    }
}
