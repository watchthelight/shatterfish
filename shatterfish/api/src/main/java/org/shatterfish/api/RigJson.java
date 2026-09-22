package org.shatterfish.api;

import java.util.List;
import java.util.Objects;

/**
 * The Rig's canonical JSON (story 3.1): the one writer every committed Rig value is written by,
 * the way {@link CodexJson} is the Codex's. It uses the same {@link JsonWriter}, so the same
 * records give the same bytes on every machine: every object's keys sorted, no whitespace inside a
 * value, integers only, nothing that names a machine or a time, line feeds only and one at the
 * end.
 *
 * <p>One entry per line, like a Codex table, for one reason: a committed file that drifts has to
 * name the triple that changed, and a single line holding five hundred triples names the file and
 * nothing else. It writes; nothing in {@code api} reads JSON back (story 2.1), so the reader of a
 * committed Seed set lives in {@code rig}.
 */
public final class RigJson {

    private RigJson() {
    }

    /** A Seed set's file text: its name, its schema version, and one triple per line. */
    public static String seedSet(SeedSet set) {
        Objects.requireNonNull(set, "set");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"entries\":[\n");
        List<SeedSet.Entry> entries = set.entries();
        for (int i = 0; i < entries.size(); i++) {
            SeedSet.Entry entry = entries.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("seed").value(entry.seed());
            out.key("seedCode").value(entry.seedCode());
            out.key("heroClass").value(entry.heroClass().name());
            out.key("challengeFlags").value(entry.challengeFlags());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == entries.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"name\":").append(JsonWriter.quote(set.name())).append(",\n");
        text.append("\"version\":").append(set.version()).append("\n");
        text.append("}\n");
        return text.toString();
    }
}
