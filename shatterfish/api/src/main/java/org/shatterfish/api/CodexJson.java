package org.shatterfish.api;

import java.util.List;

/**
 * The Codex's canonical JSON (story 2.1): one text per file, written with the same
 * {@link JsonWriter} as an Observation's rendering, so that the same records give the same bytes
 * on every machine. The writer sorts every object's keys, lists keep the order the records hold,
 * no floats, nothing that names a machine or a time; the text ends with one line feed.
 */
public final class CodexJson {

    private CodexJson() {
    }

    /** The manifest file's text. */
    public static String manifest(Codex.Manifest manifest) {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("codexVersion").value(manifest.version());
        out.key("upstreamTag").value(manifest.upstreamTag());
        out.key("tables").beginArray();
        for (String table : manifest.tables()) {
            out.value(table);
        }
        out.endArray();
        out.endObject();
        return out.toJson() + "\n";
    }

    /** The hero classes file's text. */
    public static String heroClasses(List<Codex.HeroClassEntry> entries) {
        JsonWriter out = new JsonWriter();
        out.beginArray();
        for (Codex.HeroClassEntry entry : entries) {
            out.beginObject();
            out.key("heroClass").value(entry.heroClass().name());
            out.key("subclasses").beginArray();
            for (HeroSubclass subclass : entry.subclasses()) {
                out.value(subclass.name());
            }
            out.endArray();
            citation(out, entry.citation());
            out.endObject();
        }
        out.endArray();
        return out.toJson() + "\n";
    }

    /** The challenges file's text. */
    public static String challenges(List<Codex.ChallengeEntry> entries) {
        JsonWriter out = new JsonWriter();
        out.beginArray();
        for (Codex.ChallengeEntry entry : entries) {
            out.beginObject();
            out.key("challenge").value(entry.challenge().name());
            out.key("mask").value(entry.mask());
            citation(out, entry.citation());
            out.endObject();
        }
        out.endArray();
        return out.toJson() + "\n";
    }

    private static void citation(JsonWriter out, Codex.Citation citation) {
        out.key("citation").beginObject();
        out.key("path").value(citation.path());
        out.key("line").value(citation.line());
        out.endObject();
    }
}
