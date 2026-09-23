package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Run log's canonical lines and its chain (story 3.2, ADR-0011).
 *
 * <p>The expected lines below are written out by hand, character for character, rather than taken
 * from the renderer and pasted back. That is the difference between a test of the format and a
 * test that the renderer agrees with itself — the second one passes whatever the format turns out
 * to be, which is how epic 2 shipped three wrong tables.
 */
class RunLogJsonTest {

    private static final String ZERO = "0".repeat(64);

    private static final String ONE = "1".repeat(64);

    private static final long SEED = 12_345L;

    private static final int CAP = 20_000;

    private static RunLog.Header header() {
        return new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED,
                SeedSet.code(SEED), 7L, CAP, 3, 2, 8, new RunLog.Brain("random", "def5678", ZERO),
                "", false, "a laptop", "2026-09-22T12:00:00Z");
    }

    private static RunLog.Wait served(long thinkMs) {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("map", ZERO);
        sections.put("hero", ONE);
        return new RunLog.Wait(4, 1_500, 2, 0, ZERO, sections, new Action.Step(17), true, RunLog.BOT,
                null, "", List.of(), thinkMs);
    }

    // --------------------------------------------------------------------- the lines, character by character

    @Test
    @DisplayName("each kind renders to the one canonical line, keys sorted and no whitespace anywhere")
    void the_lines_are_what_the_format_says() {
        assertEquals("{\"k\":3,\"mode\":\"PAUSED\",\"speed\":\"fast\",\"t\":\"mode\"}",
                RunLogJson.canonical(new RunLog.Mode(3, "PAUSED", "fast")));

        assertEquals("{\"input\":\"a two-finger swipe\",\"k\":5,\"t\":\"unsupported\"}",
                RunLogJson.canonical(new RunLog.Unsupported(5, "a two-finger swipe")));

        assertEquals("{\"chainAt\":\"" + ONE + "\",\"k\":7,\"salt\":\"000000000000002a\",\"t\":\"boundary\"}",
                RunLogJson.canonical(new RunLog.Boundary(7, 42, ONE)));

        assertEquals("{\"answer\":{\"kind\":\"AnswerPrompt\",\"option\":1},\"k\":2,"
                        + "\"prompt\":\"SUBCLASS\",\"t\":\"prompt\"}",
                RunLogJson.canonical(new RunLog.Prompt(2, PromptKind.SUBCLASS, new Action.AnswerPrompt(1))));

        assertEquals("{\"k\":9,\"outcome\":{\"ascended\":false,\"bosses\":1,\"cause\":\"DEATH\","
                        + "\"depth\":5,\"score\":1234,\"turns\":6000,\"win\":false},\"t\":\"end\","
                        + "\"verifiable\":true}",
                RunLogJson.canonical(new RunLog.End(9,
                        new RunLog.Outcome(false, false, 1234, 5, 6000, "DEATH", 1), true)));

        assertEquals("{\"action\":{\"cell\":17,\"kind\":\"Step\"},\"actor\":\"bot\",\"applied\":true,"
                        + "\"branch\":0,\"depth\":2,\"k\":4,"
                        + "\"obs\":\"" + ZERO + "\",\"sections\":{\"hero\":\"" + ONE + "\",\"map\":\""
                        + ZERO + "\"},\"t\":\"wait\",\"think_ms\":12,\"turn\":1500}",
                RunLogJson.canonical(served(12)));
    }

    @Test
    @DisplayName("schema version 2 is these header keys, so a new field is a version decision")
    void the_header_key_set_belongs_to_a_version() {
        // The page's test vector already fails when a header field is added, and it fails as "the
        // page is out of date" -- which is fixed by updating the page and leaves the schema still
        // calling itself the version it was. This is the other direction: the key set is labelled
        // with the version it belongs to, so adding a field means editing a line that says 2, and
        // a reader of a version-2 log can be told exactly what to expect in it.
        assertEquals(2, RunLog.VERSION, "this list is version 2's; a bump rewrites it");
        assertEquals(List.of("brain", "cap", "challenges", "class", "codex", "commit", "machine",
                        "obsv", "oracle", "profile", "registration", "salt", "seed", "seedcode",
                        "started", "t", "tag", "v"),
                keysOf(RunLogJson.canonical(header())),
                "the members of a version 2 header, in the order the writer sorts them");
    }

    /** The top-level keys of one canonical object, in the order they are written. */
    private static List<String> keysOf(String line) {
        List<String> keys = new ArrayList<>();
        int at = 1;
        while (at < line.length() - 1) {
            int quote = line.indexOf('"', at);
            int end = quote + 1;
            while (line.charAt(end) != '"') {
                end += line.charAt(end) == '\\' ? 2 : 1;
            }
            keys.add(line.substring(quote + 1, end));
            // Past this member's value, whatever shape it is, to the comma that follows it.
            int depth = 0;
            int i = end + 2;
            while (i < line.length() - 1) {
                char c = line.charAt(i);
                if (c == '"') {
                    i++;
                    while (line.charAt(i) != '"') {
                        i += line.charAt(i) == '\\' ? 2 : 1;
                    }
                } else if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    break;
                }
                i++;
            }
            at = i + 1;
        }
        return keys;
    }

    @Test
    @DisplayName("the header carries the tuple, the versions and who played, and nothing else")
    void the_header_line_is_the_tuple_and_the_versions() {
        assertEquals("{\"brain\":{\"commit\":\"def5678\",\"config\":\"" + ZERO + "\",\"name\":\"random\"},"
                        + "\"cap\":20000,\"challenges\":0,\"class\":\"WARRIOR\",\"codex\":8,"
                        + "\"commit\":\"abc1234\",\"machine\":\"a laptop\",\"obsv\":2,\"oracle\":false,"
                        + "\"profile\":3,\"registration\":\"\",\"salt\":\"0000000000000007\","
                        + "\"seed\":12345,\"seedcode\":\"" + SeedSet.code(SEED) + "\","
                        + "\"started\":\"2026-09-22T12:00:00Z\",\"t\":\"header\",\"tag\":\"v4.0.0\","
                        + "\"v\":2}",
                RunLogJson.canonical(header()));
    }

    // ------------------------------------------------------------------------------- the chain

    @Test
    @DisplayName("the chained text is the line with exactly the unchained keys removed")
    void the_chain_covers_the_line_minus_the_named_keys() {
        assertEquals(java.util.Set.of("prev", "chain", "think_ms", "machine", "started"),
                RunLogJson.UNCHAINED, "the excluded set is these five and no others");

        // The property a checker relies on: strip the named keys from the rendered line, textually,
        // and what is left is the text the chain was taken over. Nothing here calls the renderer
        // twice for the same answer -- the line and the chained text come from one code path.
        for (RunLog record : List.of(header(), served(12), new RunLog.Mode(3, "PAUSED", "fast"))) {
            String whole = RunLogJson.canonical(record);
            String chained = RunLogJson.chained(record);
            for (String key : RunLogJson.UNCHAINED) {
                assertFalse(chained.contains("\"" + key + "\":"), key + " is not chained: " + chained);
            }
            // What the chained text must be is a text transformation of the line, and the test of
            // that lives where a reader that shares no code with this one can perform it
            // (`RunLogKindsTest`, in `harness`). Here: the whole record still holds the keys the
            // chained one drops, so `chained` is dropping them rather than the renderer dropping
            // them everywhere.
            for (String key : RunLogJson.UNCHAINED) {
                assertEquals(whole.contains("\"" + key + "\":"), !chained.contains("\"" + key + "\":")
                                && whole.contains("\"" + key + "\":"),
                        key + " is in the record and out of the chained text, or in neither");
            }
        }
    }

    @Test
    @DisplayName("two waits differing only in how long the decider took chain identically")
    void the_clock_is_not_chained() {
        assertEquals(RunLogJson.chain("", served(0)), RunLogJson.chain("", served(9_999)),
                "a slow machine and a fast one record the same Run");
        assertNotEquals(RunLogJson.canonical(served(0)), RunLogJson.canonical(served(9_999)),
                "and the log still says how long it took");
    }

    @Test
    @DisplayName("the same header on two machines at two times chains identically")
    void the_machine_and_the_hour_are_not_chained() {
        RunLog.Header elsewhere = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234",
                HeroClass.WARRIOR, 0, SEED, SeedSet.code(SEED), 7L, CAP, 3, 2, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", false,
                "a server in another country", "2027-01-01T00:00:00Z");

        assertEquals(RunLogJson.chain("", header()), RunLogJson.chain("", elsewhere));
    }

    @Test
    @DisplayName("anything the chain does cover changes it")
    void everything_else_is_chained() {
        String was = RunLogJson.chain("", header());
        RunLog.Header later = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.MAGE,
                0, SEED, SeedSet.code(SEED), 7L, CAP, 3, 2, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", false, "a laptop",
                "2026-09-22T12:00:00Z");
        assertNotEquals(was, RunLogJson.chain("", later), "a different hero is a different Run");

        // The cap decides whether a Run ended or was stopped, so two Runs under different caps are
        // two Runs. Story 3.4 found this by replaying one: everything matched and the endings did
        // not, because the Replay did not know what had stopped the original.
        RunLog.Header shorter = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234",
                HeroClass.WARRIOR, 0, SEED, SeedSet.code(SEED), 7L, CAP / 2, 3, 2, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", false, "a laptop",
                "2026-09-22T12:00:00Z");
        assertNotEquals(was, RunLogJson.chain("", shorter), "a different cap is a different Run");

        RunLog.Wait moved = new RunLog.Wait(4, 1_500, 2, 0, ONE, served(12).sections(),
                new Action.Step(17), true, RunLog.BOT, null, "", List.of(), 12);
        assertNotEquals(RunLogJson.chain("", served(12)), RunLogJson.chain("", moved),
                "a different Observation is a different wait");
    }

    @Test
    @DisplayName("a chain is over everything before it, so the same record in two places chains differently")
    void the_chain_carries_the_history() {
        String first = RunLogJson.chain("", header());
        assertTrue(first.matches("[0-9a-f]{64}"), first);

        String second = RunLogJson.chain(first, served(12));
        String alone = RunLogJson.chain("", served(12));
        assertNotEquals(alone, second, "a record after a header is not the same as one before nothing");

        // And the line carries both, which is what lets a reader find where a file stops agreeing.
        String line = RunLogJson.line(first, served(12));
        assertTrue(line.contains("\"prev\":\"" + first + "\""), line);
        assertTrue(line.contains("\"chain\":\"" + second + "\""), line);
        assertFalse(RunLogJson.line("", header()).contains("\"prev\""), "nothing comes before the header");
    }

    @Test
    @DisplayName("a line is one object, keys sorted, no whitespace, and it ends where it ends")
    void a_line_is_canonical() {
        String line = RunLogJson.line(RunLogJson.chain("", header()), served(12));

        assertTrue(line.startsWith("{") && line.endsWith("}"), line);
        assertFalse(line.contains("\n"), "a record is one line");
        assertFalse(line.contains(" \""), "no whitespace between members");
        assertEquals("action", line.substring(2, 8), "the keys are sorted, so `action` comes first");
    }

    // ------------------------------------------------------------------------------ the refusals

    @Test
    @DisplayName("a record refuses a value the log could not mean")
    void the_records_refuse_what_they_cannot_mean() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Header(RunLog.VERSION + 1, "v4.0.0", "abc", HeroClass.WARRIOR, 0,
                        SEED, SeedSet.code(SEED), 7L, CAP, 3, 2, 8,
                        new RunLog.Brain("random", "def", ZERO), "", false, "", ""),
                "a schema version this build does not write");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc", HeroClass.WARRIOR, 0, SEED,
                        SeedSet.code(SEED + 1), 7L, CAP, 3, 2, 8,
                        new RunLog.Brain("random", "def", ZERO), "", false, "", ""),
                "a seed code that means another seed");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Header(RunLog.VERSION, "v4.0.0", "", HeroClass.WARRIOR, 0, SEED,
                        SeedSet.code(SEED), 7L, CAP, 3, 2, 8,
                        new RunLog.Brain("random", "def", ZERO), "", false, "", ""),
                "no commit, so nothing says which build played it");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc", HeroClass.WARRIOR, 0, SEED,
                        SeedSet.code(SEED), 7L, 0, 3, 2, 8,
                        new RunLog.Brain("random", "def", ZERO), "", false, "", ""),
                "a turn cap of nothing, which is not a Run anybody played");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc", HeroClass.WARRIOR, 512, SEED,
                        SeedSet.code(SEED), 7L, CAP, 3, 2, 8,
                        new RunLog.Brain("random", "def", ZERO), "", false, "", ""),
                "challenge flags past the game's own mask");

        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Wait(-1, 0, 0, 0, ZERO, Map.of("map", ZERO), new Action.PickUp(), true,
                        RunLog.BOT, null, "", List.of(), 0), "a negative wait index");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Wait(0, 0, 0, 0, "not a hash", Map.of("map", ZERO), new Action.PickUp(),
                        true, RunLog.BOT, null, "", List.of(), 0), "an Observation hash that is not one");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Wait(0, 0, 0, 0, ZERO, Map.of(), new Action.PickUp(), true, RunLog.BOT,
                        null, "", List.of(), 0), "no section hashes at all");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Wait(0, 0, 0, 0, ZERO, Map.of("map", ZERO), new Action.PickUp(), true,
                        RunLog.BOT, null, "", List.of(), -1), "thinking that took less than no time");

        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Outcome(false, true, 0, 0, 0, "DEATH", 0),
                "a Run that ascended without winning");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Outcome(false, false, 0, 0, 0, "", 0), "a Run that ended for no reason");

        assertThrows(IllegalArgumentException.class, () -> new RunLog.Mode(0, "SPRINTING", "fast"),
                "a Mode ADR-0013 does not name");
        // ADR-0011: a prompt record carries "the option chosen (an Action of kind answer)". A
        // decider that answered a Prompt with a step had that step refused by the executor, and the
        // wait record beside it is where a refused Action belongs.
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Prompt(0, PromptKind.SUBCLASS, new Action.Step(3)),
                "an answer that does not answer");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Prompt(0, PromptKind.NONE, new Action.AnswerPrompt(0)),
                "a prompt record for a Prompt that was not there");
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Boundary(0, 0, "short"),
                "a chain value that is not one");
    }

    @Test
    @DisplayName("a decision is whole or it is absent, so nothing half-fills one")
    void a_decision_is_not_half_stated() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.PickUp(), 10_000, "it is there");

        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Decision("", chosen, List.of(), List.of(), "greedy"), "no goal");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Decision("survive", chosen, List.of(), List.of(), ""), "no policy");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Decision("survive", null, List.of(), List.of(), "greedy"), "nothing chosen");
        assertThrows(IllegalArgumentException.class,
                () -> new RunLog.Decision("survive", chosen, List.of(chosen, chosen, chosen, chosen),
                        List.of(), "greedy"),
                "more alternatives than ADR-0011 records");

        // A wait without one renders without the key, rather than with a null or an empty object.
        assertFalse(RunLogJson.canonical(served(0)).contains("decision"),
                "a decider that states no reason states none");
    }

    // --------------------------------------------------------------------------------- the run id

    @Test
    @DisplayName("a run id is the tuple and the Brain, so a pair's two Runs never write one file")
    void the_run_id_ends_with_the_brain() {
        String first = RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, "random");
        String second = RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, "greedy");

        assertEquals("v4.0.0-WARRIOR-0-AAA-AAA-AAB-0000000000000007-random", first);
        assertNotEquals(first, second, "one triple, one salt, two Brains, two files (AD-14)");
        assertEquals("v4.0.0-WARRIOR-0-AAA-AAA-AAB-0000000000000007-random.jsonl", RunLog.fileName(first));
        assertEquals("v4.0.0-WARRIOR-0-" + SeedSet.code(SEED) + "-0000000000000007-random", header().runId(),
                "the header's own id is built the same way, from its own fields");

        assertThrows(IllegalArgumentException.class,
                () -> RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, ""),
                "a Run with no Brain named would collide with its own pair");
        assertThrows(IllegalArgumentException.class,
                () -> RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, "../escape"),
                "a run id is a file name, not a path");
        assertThrows(IllegalArgumentException.class,
                () -> RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "not a code", 7L, "random"),
                "a seed code the game does not write");

        // The parts are joined with `-`, so no part may hold one: a reviewer built two different
        // tuples that produce one id, and a Brain named `greedy-v2` is the everyday version of it.
        assertThrows(IllegalArgumentException.class,
                () -> RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, "greedy-v2"),
                "a Brain whose name holds the separator");
        assertThrows(IllegalArgumentException.class,
                () -> RunLog.runId("v4.0.0-beta", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, "random"),
                "a tag that holds the separator");
        assertThrows(IllegalArgumentException.class,
                () -> RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0, "AAA-AAA-AAB", 7L, "Random"),
                "a Brain whose name differs from another only in case, which is one file on Windows");

        // fileName is the method that turns text into a path, so it checks what it is handed rather
        // than trusting a caller to have built it above.
        for (String notAnId : new String[] {"", "../../evidence", "v4.0.0-WARRIOR-0-AAA-AAA-AAB-7-random",
                "v4.0.0-WARRIOR-0-AAA-AAA-AAB-0000000000000007-Random"}) {
            assertThrows(IllegalArgumentException.class, () -> RunLog.fileName(notAnId),
                    "a path is made from a run id, and " + notAnId + " is not one");
        }
    }
}
