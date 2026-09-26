package org.shatterfish.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The Run log's canonical JSON and its hash chain (ADR-0011, story 3.2): one record to one line,
 * written through the same {@link JsonWriter} every other committed value uses, so that the same
 * Run gives the same bytes on every machine and a chain recomputed by a stranger's own script
 * agrees with this one.
 *
 * <p><b>The chain.</b> {@code chain_k = SHA-256(chain_{k-1} || canonical(record_k minus the
 * unchained keys))}, where {@code chain_{k-1}} is the previous line's chain as its thirty-two
 * <em>bytes</em> and not its hex text, and the first record -- the header -- has nothing before it,
 * so its chain is taken over its own chained text alone. {@link #UNCHAINED} is the excluded set,
 * named here once: the two keys the envelope adds and the three that record when and where rather
 * than what. That is what makes the chain a statement about the Run instead of about the machine
 * that played it.
 *
 * <p>The excluded keys are excluded <em>by name</em>, and each name belongs to exactly one place in
 * the schema, so a checker reading the file can strip them from a line's text without knowing which
 * kind of record it holds. That is the whole design: {@link #line} and {@link #chained} are one
 * renderer with one flag, and the check that a line minus those keys <em>is</em> the chained text
 * is a thing an outsider can perform on the bytes.
 *
 * <p>It writes. Nothing in {@code api} reads JSON back (story 2.1), so a reader of a committed log
 * -- the Replay of story 3.4, the Rig's verification, and the checks that hold this class -- lives
 * outside it. That is not tidiness: a chain check that recomputed by calling this renderer again
 * would agree with any writer that agreed with itself, including a wrong one.
 */
public final class RunLogJson {

    /** The key naming the kind, which every line writes. */
    public static final String KIND = "t";

    /** The previous line's chain, repeated for convenience. Absent on the header. */
    public static final String PREVIOUS = "prev";

    /** This line's chain: the value the next line chains onto. */
    public static final String CHAIN = "chain";

    /** How long the decider took at a wait. The one field of a wait the chain leaves out. */
    public static final String THINK_MS = "think_ms";

    /**
     * The keys the chain does not cover: the envelope's own two, the time a decider took, and the
     * two header fields that say where and when. Nothing else is excluded, and nothing excluded is
     * needed to replay a Run -- which is the test of whether a field belongs on this list.
     */
    public static final Set<String> UNCHAINED = Set.of(PREVIOUS, CHAIN, THINK_MS, "machine", "started");

    private RunLogJson() {
    }

    /**
     * The whole record as canonical JSON, without the envelope: every field it has, including the
     * unchained ones.
     */
    public static String canonical(RunLog record) {
        return render(record, false, "", "");
    }

    /**
     * The record as the chain sees it: the same object with {@link #UNCHAINED} left out. A checker
     * that strips those keys from a line's text gets this, byte for byte, which is the property
     * that lets the chain be recomputed by something that is not this class.
     */
    public static String chained(RunLog record) {
        return render(record, true, "", "");
    }

    /**
     * The chain value after {@code record}, given the chain before it: the empty string for the
     * header, which has nothing before it, and the previous line's chain otherwise.
     */
    public static String chain(String previous, RunLog record) {
        Encoder out = new Encoder();
        if (!previous(previous).isEmpty()) {
            out.raw(unhex(previous));
        }
        out.raw(Utf8.encode(chained(record)));
        return Sha256.hex(Sha256.digest(out.toByteArray()));
    }

    /**
     * One line of the file: the record, the chain before it when there is one, and the chain after
     * it -- canonical, and without the line feed the writer adds.
     */
    public static String line(String previous, RunLog record) {
        return render(record, false, previous(previous), chain(previous, record));
    }

    private static String previous(String previous) {
        Objects.requireNonNull(previous, "the chain before this record");
        Canon.require(previous.isEmpty() || previous.matches("[0-9a-f]{64}"),
                "a chain is a SHA-256 in lower-case hex, or empty at the header: " + previous);
        return previous;
    }

    /**
     * The one renderer. {@code chainedOnly} skips exactly {@link #UNCHAINED}; the envelope is
     * written only when a chain is given. One code path, so a line and the text the chain covers
     * cannot drift apart into two spellings of one record.
     */
    private static String render(RunLog record, boolean chainedOnly, String previous, String chain) {
        Objects.requireNonNull(record, "record");
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key(KIND).value(record.t());
        switch (record) {
            case RunLog.Header header -> write(out, header, chainedOnly);
            case RunLog.Wait wait -> write(out, wait, chainedOnly);
            case RunLog.Prompt prompt -> write(out, prompt);
            case RunLog.Mode mode -> write(out, mode);
            case RunLog.Shadow shadow -> write(out, shadow);
            case RunLog.Boundary boundary -> write(out, boundary);
            case RunLog.Unsupported unsupported -> write(out, unsupported);
            case RunLog.End end -> write(out, end);
        }
        if (!chain.isEmpty()) {
            if (!previous.isEmpty()) {
                out.key(PREVIOUS).value(previous);
            }
            out.key(CHAIN).value(chain);
        }
        out.endObject();
        return out.toJson();
    }

    // ------------------------------------------------------------------ the records, one by one

    private static void write(JsonWriter out, RunLog.Header header, boolean chainedOnly) {
        out.key("v").value(header.v());
        out.key("tag").value(header.tag());
        out.key("commit").value(header.commit());
        out.key("class").value(header.heroClass().name());
        out.key("challenges").value(header.challenges());
        out.key("seed").value(header.seed());
        out.key("seedcode").value(header.seedCode());
        // The same sixteen lower-case hex digits the run id uses, and a string rather than a
        // number: a salt is drawn across the whole 64-bit range, and a JSON number above 2^53 is
        // silently rounded by every reader built on IEEE doubles -- which is most of the scripts
        // the methodology page invites a stranger to write. A file name that says
        // `ffffffffffffffff` and a header that said `-1` were two spellings of one value on one
        // line.
        out.key("salt").value(RunLog.salt(header.salt()));
        out.key("cap").value(header.cap());
        out.key("profile").value(header.profile());
        out.key("obsv").value(header.obsv());
        out.key("codex").value(header.codex());
        out.key("brain").beginObject();
        out.key("name").value(header.brain().name());
        out.key("commit").value(header.brain().commit());
        out.key("config").value(header.brain().configHash());
        out.endObject();
        out.key("registration").value(header.registration());
        out.key("oracle").value(header.oracle());
        // Written only for the Overlay's driver, so every headless log keeps its bytes (story 5.1).
        if (!header.driver().isEmpty()) {
            out.key("driver").value(header.driver());
        }
        if (!chainedOnly) {
            out.key("machine").value(header.machine());
            out.key("started").value(header.started());
        }
    }

    private static void write(JsonWriter out, RunLog.Wait wait, boolean chainedOnly) {
        out.key("k").value(wait.k());
        out.key("turn").value(wait.turn());
        out.key("depth").value(wait.depth());
        out.key("branch").value(wait.branch());
        out.key("obs").value(wait.obs());
        out.key("sections").beginObject();
        for (Map.Entry<String, String> section : wait.sections().entrySet()) {
            out.key(section.getKey()).value(section.getValue());
        }
        out.endObject();
        out.key("action");
        ObservationJson.write(out, wait.action());
        out.key("applied").value(wait.applied());
        out.key("actor").value(wait.actor());
        if (wait.decision() != null) {
            out.key("decision");
            write(out, wait.decision());
        }
        if (!wait.belief().isEmpty()) {
            out.key("belief").value(wait.belief());
        }
        if (!wait.highlights().isEmpty()) {
            out.key("highlights").beginArray();
            for (int cell : wait.highlights()) {
                out.value(cell);
            }
            out.endArray();
        }
        if (!chainedOnly) {
            out.key(THINK_MS).value(wait.thinkMs());
        }
    }

    private static void write(JsonWriter out, RunLog.Decision decision) {
        out.beginObject();
        out.key("goal").value(decision.goal());
        out.key("chosen");
        write(out, decision.chosen());
        out.key("alternatives").beginArray();
        for (RunLog.Choice choice : decision.alternatives()) {
            write(out, choice);
        }
        out.endArray();
        out.key("flags").beginArray();
        for (String flag : decision.flags()) {
            out.value(flag);
        }
        out.endArray();
        out.key("policy").value(decision.policy());
        out.endObject();
    }

    private static void write(JsonWriter out, RunLog.Choice choice) {
        out.beginObject();
        out.key("action");
        ObservationJson.write(out, choice.action());
        out.key("score").value(choice.score());
        out.key("why").value(choice.why());
        out.endObject();
    }

    private static void write(JsonWriter out, RunLog.Prompt prompt) {
        out.key("k").value(prompt.k());
        out.key("prompt").value(prompt.kind().name());
        out.key("answer");
        ObservationJson.write(out, prompt.answer());
    }

    private static void write(JsonWriter out, RunLog.Mode mode) {
        out.key("k").value(mode.k());
        out.key("mode").value(mode.mode());
        out.key("speed").value(mode.speed());
    }

    private static void write(JsonWriter out, RunLog.Shadow shadow) {
        out.key("k").value(shadow.k());
        out.key("decision");
        write(out, shadow.decision());
    }

    private static void write(JsonWriter out, RunLog.Boundary boundary) {
        out.key("k").value(boundary.k());
        out.key("salt").value(RunLog.salt(boundary.salt()));
        out.key("chainAt").value(boundary.chainAt());
    }

    private static void write(JsonWriter out, RunLog.Unsupported unsupported) {
        out.key("k").value(unsupported.k());
        out.key("input").value(unsupported.input());
    }

    private static void write(JsonWriter out, RunLog.End end) {
        out.key("k").value(end.k());
        out.key("outcome").beginObject();
        out.key("win").value(end.outcome().win());
        out.key("ascended").value(end.outcome().ascended());
        out.key("score").value(end.outcome().score());
        out.key("depth").value(end.outcome().depth());
        out.key("turns").value(end.outcome().turns());
        out.key("cause").value(end.outcome().cause());
        out.key("bosses").value(end.outcome().bosses());
        out.endObject();
        out.key("verifiable").value(end.verifiable());
        if (!end.detail().isEmpty()) {
            out.key("detail").value(end.detail());
        }
    }

    private static byte[] unhex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
