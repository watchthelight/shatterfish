package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The upstream tag the Codex folder is named by: {@code v} and the version the root build script
 * declares ({@code build.gradle: appVersionName}), read from that script at generation the way a
 * citation is read, so that nothing is stamped and nothing remembered. {@code docs/UPSTREAM.md}
 * pins the tag, and {@code CodexSeedFreeTest} holds that the two agree.
 */
final class Upstream {

    static final String BUILD_SCRIPT = "build.gradle";
    private static final String ANCHOR = "^\\s*appVersionName\\s*=\\s*'([^']*)'";
    private static final Pattern DECLARATION = Pattern.compile(ANCHOR);

    private Upstream() {
    }

    /** The tag, {@code v4.0.0} at the pin this story was written against. */
    static String tag(Path root) {
        Codex.Citation where = Citations.at(root, BUILD_SCRIPT, ANCHOR);
        String line;
        try {
            line = java.nio.file.Files.readAllLines(root.resolve(BUILD_SCRIPT), java.nio.charset.StandardCharsets.UTF_8).get(where.line() - 1);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(BUILD_SCRIPT + " could not be read under " + root, e);
        }
        Matcher m = DECLARATION.matcher(line);
        if (!m.find()) {
            throw new IllegalStateException(where.reference() + " does not declare appVersionName as the anchor found it");
        }
        String tag = "v" + m.group(1).trim();
        if (!tag.matches(Codex.TAG_PATTERN)) {
            throw new IllegalStateException(BUILD_SCRIPT + " appVersionName is not a version a tag is named by: '" + m.group(1)
                    + "' (" + where.reference() + "); a tag is " + Codex.TAG_PATTERN);
        }
        return tag;
    }
}
