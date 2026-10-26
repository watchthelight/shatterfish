package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lore intake (story 7.7): a draft claim is entered, or refused with a note, and the five kinds of
 * claim FR-18 refuses are refused one by one. A draft is written here as the research skill would
 * write it: frontmatter without an id, a status or a tag it was graded at.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LoreIntakeTest {

    private static final Path ROOT = DocsCitations.root();
    private static final Lore.Registry REGISTRY = new Lore.Registry(Set.of("fight"), Set.of("chasm-step"), Set.of("hp"));

    @TempDir
    Path temp;

    // --------------------------------------------------------------- the five kinds, one case each

    @Test
    @DisplayName("a seed code is refused, and the message says it found a seed string")
    void a_seed_string_is_refused() throws IOException {
        Outcome out = intake("The Goo room is always left of the start on ABC-DEF-GHI.");
        assertRefused(out, "seed string (ABC-DEF-GHI)");
    }

    @Test
    @DisplayName("a seed named, or pointed at, is refused as a seed")
    void a_seed_is_refused() throws IOException {
        assertRefused(intake("On seed 4821 the first chest is a mimic."), "names a seed (seed 4821)");
        assertRefused(intake("Use this seed to get an early armor."), "refers to one seed");
        assertRefused(intake("The seed is XQZWVBNMK and gives a ring."), "names a seed");
    }

    @Test
    @DisplayName("a specific dungeon layout is refused")
    void a_layout_is_refused() throws IOException {
        assertRefused(intake("The sewers layout always puts the stairs in the north-east corner."), "specific dungeon layout");
        assertRefused(intake("Go to the top-left corner room first."), "specific dungeon layout");
    }

    @Test
    @DisplayName("a map coordinate is refused")
    void a_coordinate_is_refused() throws IOException {
        assertRefused(intake("The hidden door is at (12, 30) on depth 3."), "map coordinate");
        assertRefused(intake("Stand on cell 1234 and wait."), "map coordinate");
        assertRefused(intake("The key is at x=14 y=9."), "map coordinate");
    }

    @Test
    @DisplayName("a level feeling tied to one Run is refused, and a general statement about feelings is not")
    void a_level_feeling_tied_to_a_run_is_refused() throws IOException {
        assertRefused(intake("My run had a level feeling of chasm on depth 4, so I jumped."), "level feeling to one Run");
        assertEquals(1, enteredCount(intake("A level feeling that mentions a chasm means the level has one to jump into.")));
    }

    @Test
    @DisplayName("a claim that is none of the five is entered, with an id, at Tier 3, graded at the pin")
    void a_good_claim_is_entered() throws IOException {
        Outcome out = intake("A lone marsupial rat is not worth a consumable.");
        assertTrue(out.outcomes().get(0).accepted(), out.toString());
        Path written = out.outcomes().get(0).written();
        assertEquals(out.lore.resolve("bestiary/sewers-1.md"), written);
        String text = Files.readString(written, StandardCharsets.UTF_8);
        assertTrue(text.contains("id: sewers-1\n"), text);
        assertTrue(text.contains("tier: 3\n"), text);
        assertTrue(text.contains("graded_against: " + Upstream.tag(ROOT) + "\n"), text);
        assertTrue(text.contains("status: current\n"), text);
        assertTrue(text.contains("## Verdict against `" + Upstream.tag(ROOT) + "`"), text);
        // The entered file is a file the checker accepts.
        assertEquals(List.of(), LoreCheck.check(ROOT, out.lore, REGISTRY));
    }

    // -------------------------------------------------------------------------- never promotes

    @Test
    @DisplayName("a draft that asks for Tier 1 with no citation is refused, not lowered and not promoted")
    void tier_one_needs_a_citation() throws IOException {
        Outcome out = intake("A lone marsupial rat is not worth a consumable.", "tier: 1\n");
        assertRefused(out, "tier 1 is asked for with no citation");
        Outcome f = intake("Wand of Slowness is a strong tool against Goo.", "tier: F\n");
        assertRefused(f, "tier F is asked for with no citation");
    }

    @Test
    @DisplayName("a draft with a citation that resolves at the pin may enter at Tier 1, and one that does not may not")
    void a_resolving_citation_carries_the_tier() throws IOException {
        Outcome ok = intake("A rat has 8 HP.", "tier: 1\ncites:\n  - core/…/mobs/Rat.java:36-62\n");
        assertTrue(ok.outcomes().get(0).accepted(), ok.toString());
        assertTrue(Files.readString(ok.outcomes().get(0).written(), StandardCharsets.UTF_8).contains("tier: 1\n"));
        Outcome bad = intake("A rat has 8 HP.", "tier: 1\ncites:\n  - core/…/mobs/Rat.java:900000\n");
        assertRefused(bad, "past the end");
        // A citation on a draft that did not ask for a tier does not promote it either.
        Outcome quiet = intake("A rat has 8 HP.", "cites:\n  - core/…/mobs/Rat.java:36-62\n");
        assertTrue(Files.readString(quiet.outcomes().get(0).written(), StandardCharsets.UTF_8).contains("tier: 3\n"));
    }

    @Test
    @DisplayName("a draft cannot carry what only intake assigns")
    void assigned_fields_are_refused() throws IOException {
        assertRefused(intake("A rat is weak.", "id: sewers-99\n"), "assigned by intake");
        assertRefused(intake("A rat is weak.", "status: needs-review\n"), "assigned by intake");
        assertRefused(intake("A rat is weak.", "graded_against: v1.0.0\n"), "assigned by intake");
    }

    // ----------------------------------------------------------------------------- the command

    @Test
    @DisplayName("ids are assigned in order within a group, an accepted draft is not entered twice, a rejected one gets a note beside it")
    void ids_notes_and_reruns() throws IOException {
        Path drafts = Files.createDirectories(temp.resolve("drafts"));
        Path lore = temp.resolve("lore");
        Files.writeString(drafts.resolve("a.md"), draft("First claim.", ""), StandardCharsets.UTF_8);
        Files.writeString(drafts.resolve("b.md"), draft("Second claim.", ""), StandardCharsets.UTF_8);
        Files.writeString(drafts.resolve("c.md"), draft("Seed 77 is lucky.", ""), StandardCharsets.UTF_8);
        List<LoreIntake.Outcome> first = LoreIntake.run(ROOT, lore, drafts, REGISTRY);
        assertEquals(List.of("sewers-1", "sewers-2", "null"), first.stream().map(o -> String.valueOf(o.id())).toList());
        assertTrue(Files.exists(lore.resolve("bestiary/sewers-1.md")) && Files.exists(lore.resolve("bestiary/sewers-2.md")));
        assertFalse(Files.exists(drafts.resolve("a.md")), "an accepted draft is renamed");
        assertTrue(Files.exists(drafts.resolve("a.md.accepted")));
        Path note = drafts.resolve("c.md.rejected.txt");
        assertTrue(Files.readString(note, StandardCharsets.UTF_8).contains("names a seed"), "the note says why");
        assertTrue(Files.exists(drafts.resolve("c.md")), "a rejected draft stays where it was");
        // Run again: the accepted ones are gone from the folder, the rejected one is refused again.
        List<LoreIntake.Outcome> second = LoreIntake.run(ROOT, lore, drafts, REGISTRY);
        assertEquals(1, second.size());
        assertFalse(second.get(0).accepted());
        assertEquals(2L, Files.list(lore.resolve("bestiary")).count());
        // A third claim in the group takes the next number, not a reused one.
        Files.writeString(drafts.resolve("d.md"), draft("Third claim.", ""), StandardCharsets.UTF_8);
        Files.delete(drafts.resolve("c.md"));
        assertEquals("sewers-3", LoreIntake.run(ROOT, lore, drafts, REGISTRY).get(0).id());
    }

    @Test
    @DisplayName("a draft that is missing a field or is not frontmatter is refused with a note naming what is missing")
    void incomplete_drafts_are_refused() throws IOException {
        Path drafts = Files.createDirectories(temp.resolve("drafts"));
        Files.writeString(drafts.resolve("a.md"), "---\ntopic: bestiary\nclaim: \"x\"\n---\n", StandardCharsets.UTF_8);
        Files.writeString(drafts.resolve("b.md"), "just text\n", StandardCharsets.UTF_8);
        List<LoreIntake.Outcome> out = LoreIntake.run(ROOT, temp.resolve("lore"), drafts, REGISTRY);
        assertTrue(out.get(0).reasons().stream().anyMatch(r -> r.contains("required field `source_url` is missing")), out.toString());
        assertTrue(out.get(1).reasons().stream().anyMatch(r -> r.contains("does not open with")), out.toString());
        assertFalse(Files.exists(temp.resolve("lore")), "nothing was written under lore/");
    }

    @Test
    @DisplayName("intake and the checker open no connection: no network class appears in their sources")
    void no_network() throws IOException {
        Path sources = ROOT.resolve("shatterfish/codex/src/test/java/org/shatterfish/codex");
        for (String name : List.of("Lore", "LoreCheck", "LoreIntake", "LoreDocs", "LoreTool")) {
            String text = Files.readString(sources.resolve(name + ".java"), StandardCharsets.UTF_8);
            for (String banned : List.of("java.net.http", "HttpClient", "HttpURLConnection", "openConnection", "openStream",
                    "java.net.Socket", "javax.net")) {
                assertFalse(text.contains(banned), name + " mentions " + banned + ": lore intake never reaches the network (NFR-8)");
            }
            assertFalse(text.contains("com.shatteredpixel") || text.contains("com.watabou") || text.contains("com.badlogic"),
                    name + " touches game code");
        }
    }

    // ------------------------------------------------------------------------------- the helpers

    /** What one draft did: its outcomes and the lore folder it wrote under. */
    private record Outcome(List<LoreIntake.Outcome> outcomes, Path lore) {
        LoreIntake.Outcome only() {
            return outcomes.get(0);
        }
    }

    private Outcome intake(String claim) throws IOException {
        return intake(claim, "");
    }

    private int enteredCount(Outcome out) {
        return (int) out.outcomes().stream().filter(LoreIntake.Outcome::accepted).count();
    }

    private Outcome intake(String claim, String extra) throws IOException {
        Path drafts = Files.createDirectories(temp.resolve("drafts-" + System.nanoTime()));
        Path lore = Files.createDirectories(temp.resolve("lore-" + System.nanoTime()));
        Files.writeString(drafts.resolve("draft.md"), draft(claim, extra), StandardCharsets.UTF_8);
        return new Outcome(LoreIntake.run(ROOT, lore, drafts, REGISTRY), lore);
    }

    private static void assertRefused(Outcome out, String fragment) {
        LoreIntake.Outcome one = out.only();
        assertFalse(one.accepted(), "should have been refused: " + one);
        assertTrue(one.reasons().stream().anyMatch(r -> r.contains(fragment)), "expected '" + fragment + "' in " + one.reasons());
        assertTrue(Files.exists(one.draft().resolveSibling("draft.md.rejected.txt")), "a note sits beside the draft");
        assertFalse(Files.exists(out.lore().resolve("bestiary")), "nothing was written under lore/");
    }

    /** A draft as the research skill writes it. {@code extra} is more frontmatter lines. */
    private static String draft(String claim, String extra) {
        return "---\n"
                + "topic: bestiary\n"
                + "group: sewers\n"
                + "subject: \"Marsupial rat\"\n"
                + "source_url: https://pixeldungeon.fandom.com/wiki/Marsupial_rat\n"
                + "source_type: wiki\n"
                + "date: unknown\n"
                + "retrieved: 2026-10-26\n"
                + "variant: unknown\n"
                + "version_claimed: \"unspecified\"\n"
                + "claim: " + Lore.quote(claim) + "\n"
                + extra
                + "---\n";
    }
}
