package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The date gate (story 7.9). The rule cases run on a small changelog written here, where the
 * answer is known; the sweep cases run on the real changelog and the 144 claims, and hold the
 * thresholds the story file states before the first run.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DateGateTest {

    private static final Path ROOT = DocsCitations.root();

    private static final List<DateGate.Row> TABLE = List.of(
            new DateGate.Row("v1.0", "2021-08-17", "August 17th, 2021", "x/v1_X_Changes.java:814"),
            new DateGate.Row("v2.0", "2023-03-13", "March 13th, 2023", "x/v2_X_Changes.java:1183"),
            new DateGate.Row("v3.0", "2025-02-27", "February 27th, 2025", "x/v3_X_Changes.java:719"));

    private static DateGate.Release release(String label, String date) {
        return new DateGate.Release(label, DateGate.Version.label(label), java.time.LocalDate.parse(date), date, "x");
    }

    private static final DateGate.Release V1 = release("v1.0", "2021-08-17");
    private static final DateGate.Release V2 = release("v2.0", "2023-03-13");

    /** Goo changes in v2.0; the rat in v1.0; nothing names the crab. */
    private static final List<DateGate.Change> CHANGES = List.of(
            new DateGate.Change(0, V1, "ui.changelist.v1_X_Changes", "Buffs", "Rat", "core/x/v1_X_Changes.java:900",
                    "rat the rat now bites harder"),
            new DateGate.Change(1, V2, "ui.changelist.v2_X_Changes", "Changes", "Goo", "core/x/v2_X_Changes.java:1250",
                    "goo goo's pump-up attack is telegraphed longer"),
            new DateGate.Change(2, null, "ui.changelist.v4_X_Changes", "Coming Soon", "Goo", "core/x/v4_X_Changes.java:60",
                    "goo is mentioned in a preview that belongs to no release"));

    private static final DateGate.Gate GATE = new DateGate.Gate(TABLE, CHANGES, DateGate.Names.empty());

    private static DateGate.Claim claim(String subject, String date, String version, String tier, List<String> cites) {
        return new DateGate.Claim("t-1", subject, null, date, version, tier, cites, "a claim");
    }

    private static final List<String> CODE = List.of("core/…/mobs/Goo.java:90");

    // --------------------------------------------------------------------- the rules

    @Test
    @DisplayName("a claim dated before a change to its subject is flagged changed-since")
    void a_claim_dated_before_a_change_is_flagged() {
        DateGate.Verdict v = GATE.check(claim("Goo", "2022-01-01", "unspecified", "3", List.of()));
        assertEquals(DateGate.CHANGED_SINCE, v.status());
        assertTrue(v.flagged());
        assertEquals("v1.0.0", v.anchor().version().toString(), "2022-01-01 falls in v1.0");
        assertEquals(1, v.later().size());
    }

    @Test
    @DisplayName("a claim dated after the change is not flagged")
    void a_claim_dated_after_is_not() {
        DateGate.Verdict v = GATE.check(claim("Goo", "2024-01-01", "unspecified", "3", List.of()));
        assertEquals(DateGate.CLEAN, v.status());
        assertFalse(v.flagged());
        assertEquals(List.of(), v.later());
        DateGate.Verdict onTheDay = GATE.check(claim("Goo", "2023-03-13", "unspecified", "3", List.of()));
        assertEquals(DateGate.CLEAN, onTheDay.status(), "a claim dated on the release day is in that release");
    }

    @Test
    @DisplayName("the flag names the heading's entry class, release and path:line")
    void a_flag_carries_the_entry_class_and_citation() {
        DateGate.Change later = GATE.check(claim("Goo", "2022-01-01", "unspecified", "3", List.of())).later().get(0);
        assertEquals("ui.changelist.v2_X_Changes", later.entryClass());
        assertEquals("core/x/v2_X_Changes.java:1250", later.citation());
        assertEquals("v2.0", later.release().label());
    }

    @Test
    @DisplayName("a version the claim names beats its date")
    void a_version_beats_a_date() {
        DateGate.Verdict v = GATE.check(claim("Goo", "2024-01-01", "v1.0.2", "3", List.of()));
        assertEquals(DateGate.CHANGED_SINCE, v.status(), "written in 2024, but about v1.0");
        assertEquals(DateGate.CLEAN, GATE.check(claim("Goo", "2020-01-01", "v2.0", "3", List.of())).status());
        assertEquals(DateGate.Version.label("v2.3.1"), DateGate.anchor(claim("Goo", "unknown", "from v1.0 to v2.3.1", "3", List.of()), TABLE).version(),
                "several versions named: the highest");
    }

    @Test
    @DisplayName("pre-vN is a bound: every release from vN is later, an earlier one is not")
    void a_bound_flags_every_release_from_it() {
        assertEquals(DateGate.CHANGED_SINCE, GATE.check(claim("Goo", "unknown", "pre-v2", "3", List.of())).status());
        assertEquals(DateGate.CLEAN, GATE.check(claim("Goo", "unknown", "pre-v3", "3", List.of())).status());
        assertTrue(DateGate.anchor(claim("Goo", "unknown", "pre-v4 (source flagged as old version)", "3", List.of()), TABLE).before());
    }

    @Test
    @DisplayName("a heading that belongs to no release is not a change to gate on")
    void an_unreleased_heading_is_not_a_change() {
        DateGate.Verdict v = GATE.check(claim("Goo", "2024-01-01", "unspecified", "3", List.of()));
        assertEquals(1, v.history(), "the preview heading names Goo and is not counted");
    }

    @Test
    @DisplayName("no date and no version is undated, whatever the subject")
    void an_undated_claim_is_flagged() {
        for (String said : List.of("not stated", "unspecified", "old wiki page (flagged oldVersion)", "older version")) {
            DateGate.Verdict v = GATE.check(claim("Goo", "unknown", said, "3", List.of()));
            assertEquals(DateGate.UNDATED, v.status(), said);
            assertNull(v.anchor());
        }
        assertEquals(DateGate.UNDATED, GATE.check(claim("Crab", "unknown", "not stated", "3", List.of())).status());
    }

    @Test
    @DisplayName("a flagged claim with no code citation is capped at Tier 3; a code citation lifts the cap")
    void an_undated_claim_is_capped_at_tier_3() {
        DateGate.Claim bare = claim("Goo", "unknown", "not stated", "1", List.of("docs/rules/combat.md:3"));
        DateGate.Verdict v = GATE.check(bare);
        assertEquals(3, v.ceiling());
        assertTrue(DateGate.Gate.exceeds(bare, v), "a Tier 1 on no code citation is above the ceiling");
        assertFalse(DateGate.Gate.exceeds(claim("Goo", "unknown", "not stated", "3", List.of()),
                GATE.check(claim("Goo", "unknown", "not stated", "3", List.of()))), "Tier 3 is at the ceiling");
    }

    @Test
    @DisplayName("a code citation lets a flagged claim stand")
    void a_code_citation_lifts_the_cap() {
        DateGate.Claim cited = claim("Goo", "unknown", "not stated", "1", CODE);
        DateGate.Verdict v = GATE.check(cited);
        assertEquals(DateGate.UNDATED, v.status(), "still flagged");
        assertEquals(1, v.ceiling());
        assertFalse(DateGate.Gate.exceeds(cited, v));
        DateGate.Claim changed = claim("Goo", "2022-01-01", "unspecified", "1", CODE);
        assertFalse(DateGate.Gate.exceeds(changed, GATE.check(changed)));
        DateGate.Claim uncited = claim("Goo", "2022-01-01", "unspecified", "2", List.of());
        assertTrue(DateGate.Gate.exceeds(uncited, GATE.check(uncited)), "changed-since without a code citation is capped too");
    }

    @Test
    @DisplayName("a clean claim has no ceiling, so a date that finds nothing wrong cannot lower anything either")
    void a_date_alone_never_lifts_a_tier() {
        DateGate.Claim c = claim("Goo", "2024-01-01", "unspecified", "3", List.of());
        DateGate.Verdict v = GATE.check(c);
        assertEquals(1, v.ceiling());
        assertEquals("3", c.tier(), "the claim is a value the gate was given and has no way to set");
        assertFalse(DateGate.Gate.exceeds(c, v));
    }

    @Test
    @DisplayName("a subject with no changelog entry passes unflagged and is counted")
    void a_subject_with_no_entry_passes_and_is_counted() {
        DateGate.Verdict v = GATE.check(claim("Crab", "2022-01-01", "unspecified", "3", List.of()));
        assertEquals(DateGate.NO_ENTRY, v.status());
        assertFalse(v.flagged());
        List<DateGate.Verdict> all = List.of(v, GATE.check(claim("Goo", "2022-01-01", "x", "3", List.of())),
                GATE.check(claim("Goo", "2024-01-01", "x", "3", List.of())), GATE.check(claim("Goo", "unknown", "x", "3", List.of())));
        Map<String, Integer> counts = DateGateReport.counts(all);
        assertEquals(1, counts.get(DateGate.NO_ENTRY));
        assertEquals(1, counts.get(DateGate.CHANGED_SINCE));
        assertEquals(1, counts.get(DateGate.CLEAN));
        assertEquals(1, counts.get(DateGate.UNDATED));
    }

    @Test
    @DisplayName("a word is matched as a word: a rat is not a pirate, and a plural is the same word")
    void words_are_matched_as_words() {
        assertTrue(DateGate.names(java.util.Set.of("rat"), "the rats are faster"));
        assertFalse(DateGate.names(java.util.Set.of("rat"), "a pirate ship"));
        assertTrue(DateGate.names(java.util.Set.of("goo"), "goo's pump-up"));
        assertFalse(DateGate.names(java.util.Set.of("goo"), "good luck"));
    }

    // --------------------------------------------------------------------- the real changelog

    private static final String TAG = Upstream.tag(ROOT);

    @Test
    @DisplayName("the finding: what the changelog carries, counted from the file")
    void the_finding_counts_are_the_changelogs() {
        DateGate.Stats s = DateGate.read(ROOT, TAG).stats();
        assertEquals(195, s.entries());
        assertEquals(14, s.classes());
        assertEquals(0, s.entryDates(), "no entry carries a date in its own text");
        assertEquals(61, s.datedHeadings(), "the game states 61 dates, all inside headings");
        assertEquals(54, s.entriesWithDatedHeading());
        assertEquals(194, s.versionClassEntries(), "13 classes named for a version; the 14th is the vanilla history");
        assertEquals(53, s.datedReleases());
        assertEquals(55, s.releases(), "the two undated ones are the patch releases of v4.0");
    }

    @Test
    @DisplayName("every claim has a gate verdict, and no Tier 1 or 2 stands above the gate's ceiling")
    void the_144_claims_are_gated() {
        DateGate.Gate gate = DateGate.Gate.of(ROOT, TAG);
        List<DateGate.Claim> claims = DateGate.claims(ROOT.resolve(Lore.DIR));
        assertTrue(claims.size() >= 144, "the 144 converted claims and every claim entered since: " + claims.size());
        List<DateGate.Verdict> verdicts = new ArrayList<>();
        for (DateGate.Claim c : claims) {
            DateGate.Verdict v = gate.check(c);
            verdicts.add(v);
            assertFalse(DateGate.Gate.exceeds(c, v), c.id() + " is Tier " + c.tier() + " and flagged with no code citation");
            assertNotNull(v.status());
        }
        Map<String, Integer> counts = DateGateReport.counts(verdicts);
        assertEquals(claims.size(), counts.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(counts.get(DateGate.UNDATED) > 0, "the 144 were collected without dates, and the gate says so");
    }

    @Test
    @DisplayName("given the same anchor, the gate neither flags nothing nor everything (thresholds from the story file)")
    void the_gate_neither_flags_nothing_nor_everything() {
        DateGate.Gate gate = DateGate.Gate.of(ROOT, TAG);
        List<DateGate.Claim> claims = DateGate.claims(ROOT.resolve(Lore.DIR));
        Map<String, Integer> oldest = DateGateReport.probe(gate, claims, "v0.1.0");
        assertTrue(oldest.get(DateGate.CHANGED_SINCE) >= 100, "oldest anchor, changed-since: " + oldest);
        assertTrue(oldest.get(DateGate.NO_ENTRY) >= 5, "oldest anchor, no-entry: " + oldest);
        Map<String, Integer> middle = DateGateReport.probe(gate, claims, "v3.3");
        assertTrue(middle.get(DateGate.CHANGED_SINCE) >= 10 && middle.get(DateGate.CHANGED_SINCE) <= 120, "v3.3: " + middle);
        Map<String, Integer> newest = DateGateReport.probe(gate, claims, "v4.0.2");
        assertEquals(0, newest.get(DateGate.CHANGED_SINCE), "nothing is later than the newest release: " + newest);
        assertEquals(0, newest.get(DateGate.UNDATED));
    }

    @Test
    @DisplayName("the two pre-v4 claims are changed-since when their subject has a v4 heading, and cite it")
    void a_real_bound_is_gated() {
        DateGate.Gate gate = DateGate.Gate.of(ROOT, TAG);
        for (DateGate.Claim c : DateGate.claims(ROOT.resolve(Lore.DIR))) {
            DateGate.Verdict v = gate.check(c);
            if (v.anchor() != null) {
                assertTrue(v.anchor().before(), c.id() + " is anchored by `" + c.versionClaimed() + "`");
                for (DateGate.Change later : v.later()) {
                    assertTrue(later.citation().matches(".*\\.java:\\d+"), later.citation());
                    assertTrue(later.release().version().compareTo(DateGate.Version.label("v4.0")) >= 0);
                }
            }
        }
    }

    @Test
    @DisplayName("the gate reads Codex files and claim text only: no network, no game class, no write")
    void no_network_and_no_game_code() {
        for (String name : List.of("DateGate", "DateGateReport")) {
            String source = Lore.read(ROOT.resolve("shatterfish/codex/src/test/java/org/shatterfish/codex/" + name + ".java"));
            for (String banned : List.of("java.net", "HttpClient", "com.shatteredpixel", "com.watabou", "org.shatterfish.core",
                    "Files.write", "ProcessBuilder", "Runtime.getRuntime")) {
                assertFalse(source.contains(banned), name + " names " + banned);
            }
        }
    }

    @Test
    @DisplayName("the five to read first are flagged, ranked, and at most five")
    void the_five_are_flagged() {
        DateGate.Gate gate = DateGate.Gate.of(ROOT, TAG);
        Map<DateGate.Claim, DateGate.Verdict> verdicts = new java.util.LinkedHashMap<>();
        for (DateGate.Claim c : DateGate.claims(ROOT.resolve(Lore.DIR))) {
            verdicts.put(c, gate.check(c));
        }
        var five = DateGateReport.consequential(verdicts, 5);
        assertEquals(5, five.size());
        assertTrue(five.stream().allMatch(e -> e.getValue().flagged()));
        long changed = verdicts.values().stream().filter(v -> v.status().equals(DateGate.CHANGED_SINCE)).count();
        for (int i = 0; i < Math.min(changed, 5); i++) {
            assertEquals(DateGate.CHANGED_SINCE, five.get(i).getValue().status(), "a known later change ranks first");
        }
    }
}
