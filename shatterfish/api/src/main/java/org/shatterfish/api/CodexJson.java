package org.shatterfish.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The Codex's canonical JSON (story 2.1): one text per file, written with the same
 * {@link JsonWriter} as an Observation's rendering, so that the same records give the same bytes
 * on every machine. The writer sorts every object's keys; lists keep the order the records hold;
 * a table is an array with one entry per line, so that a drift diff names the entry; no floats,
 * nothing that names a machine or a time; line feeds only, and one at the end. A table naming a
 * key twice is refused here, since the records validate entries and this is where a table is.
 */
public final class CodexJson {

    private CodexJson() {
    }

    /** The manifest file's text. */
    public static String manifest(Codex.Manifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
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

    /** The hero classes file's text; every class and every subclass once. */
    public static String heroClasses(List<Codex.HeroClassEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<HeroClass> classes = new HashSet<>();
        Set<HeroSubclass> subclasses = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < entries.size(); i++) {
            Codex.HeroClassEntry entry = entries.get(i);
            Codex.distinct(classes, entry.heroClass(), "a hero class");
            for (HeroSubclass subclass : entry.subclasses()) {
                Codex.distinct(subclasses, subclass, "a subclass");
            }
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("heroClass").value(entry.heroClass().name());
            out.key("subclasses").beginArray();
            for (HeroSubclass subclass : entry.subclasses()) {
                out.value(subclass.name());
            }
            out.endArray();
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == entries.size());
        }
        return close(table);
    }

    /** The challenges file's text; every challenge and every mask once. */
    public static String challenges(List<Codex.ChallengeEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<Challenge> challenges = new HashSet<>();
        Set<Integer> masks = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < entries.size(); i++) {
            Codex.ChallengeEntry entry = entries.get(i);
            Codex.distinct(challenges, entry.challenge(), "a challenge");
            Codex.distinct(masks, entry.mask(), "a mask");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("challenge").value(entry.challenge().name());
            out.key("mask").value(entry.mask());
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == entries.size());
        }
        return close(table);
    }

    private static void citation(JsonWriter out, Codex.Citation citation) {
        out.key("citation").beginObject();
        out.key("path").value(citation.path());
        out.key("line").value(citation.line());
        out.endObject();
    }

    private static StringBuilder table() {
        return new StringBuilder("[\n");
    }

    private static void row(StringBuilder table, JsonWriter entry, boolean last) {
        table.append("  ").append(entry.toJson()).append(last ? "\n" : ",\n");
    }

    private static String close(StringBuilder table) {
        return table.append("]\n").toString();
    }
}
