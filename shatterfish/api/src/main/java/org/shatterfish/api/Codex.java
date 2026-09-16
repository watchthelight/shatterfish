package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;

/**
 * The Codex's shape (E2, story 2.1): what a Codex file carries, as records a Brain's caller can
 * hand it and a generator can write. The Codex is static, seed-free knowledge about types and
 * tables, generated from the pinned upstream tag and never about a Run (FR-14); every entry cites
 * the {@code path:line} it was read from (non-negotiable 8), and the citation is computed at
 * generation by finding the declaration in the pinned source, never typed from memory.
 *
 * <p>The version is the Codex's own, distinct from the Observation schema's and from the tag: it
 * changes when a table's meaning or shape changes, and it is what the Run-log header records
 * (ADR-0011) so that a Brain's behaviour can be tied to the knowledge it had. A Codex value never
 * reaches the Brain as a game class; these records and {@link CodexJson} are the whole contract.
 */
public final class Codex {

    /** The Codex version: 1 is the skeleton with the hero classes and the challenge flags. */
    public static final int VERSION = 1;

    private Codex() {
    }

    /** Where an entry was read: a path relative to the repository root and a one-based line. */
    public record Citation(String path, int line) {

        public Citation {
            path = Canon.text(path, "citation path");
            Canon.require(!path.isEmpty() && !path.startsWith("/") && !path.contains("\\"),
                    "a citation path is relative, with forward slashes: " + path);
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
            Canon.require(upstreamTag.matches("v[0-9]+([.][0-9]+)*"), "an upstream tag is v and a version: " + upstreamTag);
            tables = Canon.sorted(tables, Comparator.naturalOrder(), "tables");
        }
    }

    /** One hero class and its subclasses, in the game's declaration order. */
    public record HeroClassEntry(HeroClass heroClass, List<HeroSubclass> subclasses, Citation citation) {

        public HeroClassEntry {
            Canon.require(heroClass != null, "an entry names its hero class");
            subclasses = Canon.positional(subclasses, "subclasses");
            Canon.require(!subclasses.contains(HeroSubclass.NONE), "NONE is the absence of a subclass, not one");
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
}
