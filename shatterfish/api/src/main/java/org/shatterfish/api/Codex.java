package org.shatterfish.api;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Codex's shape (E2, story 2.1): what a Codex file carries, as records a Brain's caller can
 * hand it and a generator can write. The Codex is static, seed-free knowledge about types and
 * tables, generated from the pinned upstream tag and never about a Run (FR-14); every entry cites
 * the {@code path:line} it was read from (non-negotiable 8), and the citation is computed at
 * generation by finding the declaration in the pinned source, never typed from memory.
 *
 * <p>The version is the Codex's own, distinct from the Observation schema's and from the tag: it
 * changes when a table's meaning or shape changes, and it is what the Run-log header will record
 * (ADR-0011, E3) so that a Brain's behaviour can be tied to the knowledge it had. A Codex value
 * never reaches the Brain as a game class; these records and {@link CodexJson} are the whole
 * contract.
 */
public final class Codex {

    /** The Codex version: 1 is the skeleton with the hero classes and the challenge flags. */
    public static final int VERSION = 1;

    /** The shape of an upstream tag: {@code v}, a dotted version, and an optional pre-release suffix. */
    public static final String TAG_PATTERN = "v[0-9]+([.][0-9]+)*(-[A-Za-z0-9.]+)?";

    private Codex() {
    }

    /**
     * Where an entry was read: a path relative to the repository root, forward-slashed, with no
     * drive, no {@code .} or {@code ..} segment and no empty segment, and a one-based line.
     */
    public record Citation(String path, int line) {

        public Citation {
            path = Canon.text(path, "citation path");
            Canon.require(!path.isEmpty() && !path.startsWith("/") && !path.contains("\\") && !path.contains(":"),
                    "a citation path is relative, with forward slashes and no drive: " + path);
            for (String segment : path.split("/", -1)) {
                Canon.require(!segment.isEmpty() && !segment.equals(".") && !segment.equals(".."),
                        "a citation path has no empty, . or .. segment: " + path);
            }
            Canon.require(line >= 1, "a citation line is one-based: " + line);
        }

        /** The {@code path:line} form the documents use. */
        public String reference() {
            return path + ":" + line;
        }
    }

    /** The manifest: the Codex version, the upstream tag the folder is named by, and the table files. */
    public record Manifest(int version, String upstreamTag, List<String> tables) {

        public Manifest {
            Canon.require(version >= 1, "a Codex version is positive: " + version);
            upstreamTag = Canon.text(upstreamTag, "upstream tag");
            Canon.require(upstreamTag.matches(TAG_PATTERN), "an upstream tag is v and a version: " + upstreamTag);
            tables = Canon.sorted(tables, Comparator.naturalOrder(), "tables");
            Canon.require(new HashSet<>(tables).size() == tables.size(), "a table is listed once: " + tables);
        }
    }

    /** One hero class and its subclasses, in the game's declaration order. */
    public record HeroClassEntry(HeroClass heroClass, List<HeroSubclass> subclasses, Citation citation) {

        public HeroClassEntry {
            Canon.require(heroClass != null, "an entry names its hero class");
            subclasses = Canon.positional(subclasses, "subclasses");
            Canon.require(!subclasses.isEmpty(), "a hero class has subclasses");
            Canon.require(!subclasses.contains(HeroSubclass.NONE), "NONE is the absence of a subclass, not one");
            Canon.require(new HashSet<>(subclasses).size() == subclasses.size(), "a subclass is listed once: " + subclasses);
            Canon.require(citation != null, "an entry carries its citation");
        }
    }

    /** One challenge flag and the bit mask the game stores it under. */
    public record ChallengeEntry(Challenge challenge, int mask, Citation citation) {

        public ChallengeEntry {
            Canon.require(challenge != null, "an entry names its challenge");
            Canon.require(mask >= 1 && Integer.bitCount(mask) == 1, "a challenge mask is one bit: " + mask);
            Canon.require(citation != null, "an entry carries its citation");
        }
    }

    /** Refuses a table naming a key twice. */
    static <T> void distinct(Set<T> seen, T key, String what) {
        Canon.require(seen.add(key), what + " is listed twice: " + key);
    }
}
