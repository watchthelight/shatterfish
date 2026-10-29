package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Heuristic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every heuristic the Brain decides by is accounted for (story 7.1, FR-18, FR-17): a named threshold,
 * odds, budget or ordering choice is declared in code with {@link Heuristic}, found here by
 * reflection over the {@code brain} classes, and its slug must be a Rule row of
 * {@code docs/brain-rules.md}, a measured-heuristic row on the same page, or the visible
 * {@code unlinked} allowlist below, whose count may fall and may not rise. The table on the index
 * page is rendered by {@link HeuristicIndex} and compared with the page.
 */
class HeuristicLinkTest {

    /**
     * The heuristics nobody can yet account for: no Rule states the value or the mechanic that sets
     * it, and no Results page or idea record played it. Each entry carries its reason and the issue that
     * owns it (story 7.10 closed the check with these left over: the Results page lists them). Linking one
     * means annotating it with a slug and deleting it here; the test fails on a stale name, on a missing
     * one, and on a reason that does not end in an issue number.
     */
    static final Map<String, String> UNLINKED = Map.ofEntries(
            Map.entry("Brain.AVOID_RADIUS", "a backstop radius for a region the fight Policy retreated from; no Codex fact sets it (#314)"),
            Map.entry("Brain.AVOID_WAITS", "a backstop duration for a region the fight Policy retreated from; no Codex fact sets it (#314)"),
            Map.entry("Descend.ALLOWANCE", "the waits a floor is worth staying on is an assumption; the food Rules state costs, not this value (#313)"),
            Map.entry("Descend.PER_DROP", "the waits a guaranteed drop is worth is an assumption beside floor-food-guarantees, which states the drops and not the value (#313)"),
            Map.entry("Douse.FLEE", "the steps to bare ground are a tuned reach; burning-douse states the burn and the water, not six (#315)"),
            Map.entry("Eat.WATER_REACH", "the walk to water before mystery meat is a tuned reach; the hunger Rules state the cost per turn, not thirty (#315)"),
            Map.entry("Explore.FRUGAL_SEARCHES", "the frugal search count is a tuned budget; time-and-hunger-costs states the costs and quotes the figure without setting it (#313)"),
            Map.entry("Explore.SEARCHES", "the searches per floor are a tuned budget; search-radius-cost states what a search costs, not how many to make (#313)"),
            Map.entry("Explore.STUCK", "the waits on a cell before the Policy yields are a loop guard; no Codex fact sets them (#313)"),
            Map.entry("Fight.CHASE_WAITS", "an assumption about how far an awake enemy that walked off is followed; sleeping-mob-out-of-view states the sleeper, not this value (#312)"),
            Map.entry("Fight.FLIGHTS", "the flights per floor are a tuned cap; no Codex fact sets them (#312)"),
            Map.entry("Fight.HOLDS", "the chokepoint holds are a tuned cap; no Codex fact sets them (#312)"),
            Map.entry("Fight.MARGIN_PER_MILLE", "the share of hit points a fight may cost is a stated assumption, not a Codex fact (#312)"),
            Map.entry("Fight.RECALL_WAITS", "an assumption about how long a sighting counts; sleeping-mob-out-of-view states the sleeper, not this value (#312)"),
            Map.entry("Playbook.Goo.drinkPerMille", "the health at which the hero drinks against Goo is a tuned value; goo-engage states the fight, and the measuring page covers other Goo figures (#315)"),
            Map.entry("Heal.HORIZON", "the turns of fighting the danger counts is a tuned horizon; the estimate is a mean and no Codex fact sets the cut (#312)"),
            Map.entry("Heal.REDRINK_SHARE_PER_MILLE", "the share of a heal under which a second drink is worth it is a stated assumption (#312)"),
            Map.entry("Larder.FRUGAL", "the food budget below which the Brain turns frugal is a tuned value; time-and-hunger-costs quotes it without setting it (#313)"),
            Map.entry("Memory.BOUNCES", "a backstop count for two Policies undoing each other (issue #174); no Codex fact sets it (#314)"),
            Map.entry("Memory.BOUNCE_WAITS", "a backstop duration for a blocked cell (issue #174); no Codex fact sets it (#314)"),
            Map.entry("Memory.FLEETING_WAITS", "tied to Brain.AVOID_WAITS, itself a backstop (story 4.12); no Codex fact sets it (#314)"),
            Map.entry("OnePly.DEATH_PER_MILLE", "the cost of dying in the one-ply evaluation is a stated assumption (#312)"),
            Map.entry("OnePly.ENGAGE", "how near an awake walker must be for the one-ply Policy to take the wait is a tuned value (#312)"),
            Map.entry("OnePly.ENUMERATED", "the repliers enumerated before the expectation is used is a compute cap, not a game fact (#312)"),
            Map.entry("Playbook.Identify.inventoryOddsPerMille", "the odds an item-picker scroll may hold are a tuned value; scroll-picker states the picker, not the odds (#315)"),
            Map.entry("Plunge.FIT_PERCENT", "the health share before a jump is a tuned value; chasm-plunge states the fall's cost and quotes the share without setting it (#315)"),
            Map.entry("Plunge.STARVING_PERCENT", "the health share before a starving jump is a tuned value; chasm-plunge states the fall's cost and quotes the share without setting it (#315)"),
            Map.entry("Ranged.REACH", "the farthest throw is a tuned reach; missile-throw states the odds from a distance, not six (#315)"),
            Map.entry("SafeTest.GAS_TURNS", "the turns a hero stays in its own cloud are an assumption beside PotionOfToxicGas.java:49, which seeds the gas and sets no duration (#315)"),
            Map.entry("Traps.ENGAGED", "how near an enemy must be to skip a search is a tuned value (issue #184) (#315)"),
            Map.entry("Traps.REACH", "how far a hidden trap lies from a shown one is an assumption; the burned room's clustering is a fill rule that sets no distance (#315)"));

    /** The most heuristics that may be unlinked. Lower it when one is linked; raising it is a decision for the product owner. */
    static final int UNLINKED_CEILING = 31;

    /**
     * Numeric constants of the brain classes that are not heuristics, with the reason. A constant
     * that is neither annotated nor listed here fails {@link #every_numeric_constant_is_declared_or_exempt()},
     * so a new threshold cannot slip past the check by not being annotated.
     */
    static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("BrainModule.API_SCHEMA_VERSION", "a schema version"),
            Map.entry("Memory.VERSION", "a schema version of the Memory"),
            Map.entry("Policies.CERTAIN", "the fixed-point scale of a probability, not a choice"),
            Map.entry("Beliefs.EXACT", "a cap on the enumeration, past which the answer is the same"),
            Map.entry("Memory.CLOUDS", "a capacity of the Memory"),
            Map.entry("Memory.AVOIDED", "a capacity of the Memory"),
            Map.entry("Memory.DWELT", "a capacity of the Memory"),
            Map.entry("Memory.MONSTERS", "a capacity of the Memory"),
            Map.entry("Memory.SEALED_RETREATS", "an index into the Memory's flight counters"),
            Map.entry("Memory.WATER_LEAVES", "an index into the Memory's flight counters"),
            Map.entry("Sentry.MAX_ZONE", "a capacity guard on a flood fill"),
            Map.entry("Tengu.DEPTH", "the floor of the dungeon where the prison boss lives (Dungeon.java:441-443), a fact of the map and not a choice"));

    /** Constants of nested classes are listed by their nested name; the capacity of the shunned set is one. */
    static final Map<String, String> EXEMPT_NESTED = Map.of("Memory.Windows.SHUNNED", "a capacity of the Memory");

    static List<String> problems(List<HeuristicIndex.Found> found, Set<String> ruleSlugs, Map<String, HeuristicIndex.Measured> measured,
                                 Map<String, HeuristicIndex.LoreClaim> lore) {
        List<String> problems = new ArrayList<>();
        for (HeuristicIndex.Found heuristic : found) {
            String slug = heuristic.slug();
            if (slug.equals(Heuristic.UNLINKED)) {
                if (!UNLINKED.containsKey(heuristic.constant())) {
                    problems.add(heuristic.constant() + " is annotated unlinked but is not on the unlinked allowlist; link it to a Rule or a measurement, or add it knowingly");
                }
            } else if (!ruleSlugs.contains(slug) && !measured.containsKey(slug) && !lore.containsKey(slug)) {
                problems.add(heuristic.constant() + " carries slug '" + slug + "', which is neither a Rule row, a measured-heuristic row of docs/brain-rules.md nor a Lore claim id");
            } else if (lore.containsKey(slug) && !ruleSlugs.contains(slug) && !measured.containsKey(slug)
                    && !names(lore.get(slug), heuristic)) {
                problems.add(heuristic.constant() + " is linked to Lore claim '" + slug + "', whose implemented_in does not name "
                        + "policy:" + heuristic.policy() + " (the claim and the heuristic have to agree)");
            } else if (UNLINKED.containsKey(heuristic.constant())) {
                problems.add(heuristic.constant() + " is linked to '" + slug + "' but is still on the unlinked allowlist; delete it there");
            }
        }
        return problems;
    }

    /** Whether the claim names the heuristic's Policy, or any target when the heuristic's class is not a Policy. */
    private static boolean names(HeuristicIndex.LoreClaim claim, HeuristicIndex.Found heuristic) {
        if (claim.implementedIn().contains("policy:" + heuristic.policy())) {
            return true;
        }
        return !org.shatterfish.brain.Brain.policyNames().contains(heuristic.policy()) && !claim.implementedIn().isEmpty();
    }

    @Test
    @DisplayName("every heuristic's slug names a Rule row or a measured-heuristic row, or the constant is on the unlinked allowlist")
    void every_heuristic_is_linked_or_listed() throws IOException {
        List<HeuristicIndex.Found> found = HeuristicIndex.annotated();
        assertFalse(found.isEmpty(), "the brain declares heuristics");
        List<String> problems = problems(found, HeuristicIndex.ruleTiers().keySet(), HeuristicIndex.measured(), HeuristicIndex.loreClaims());
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    @DisplayName("a slug that names neither a Rule nor a measurement fails, naming the constant (the check bites)")
    void a_dangling_slug_is_refused() throws IOException {
        List<HeuristicIndex.Found> dangling = List.of(
                new HeuristicIndex.Found("Descend", "RESTS", "no-such-row", "descend"),
                new HeuristicIndex.Found("Eat", "HUNGRY", "hunger-thresholds-food", "eat"),
                new HeuristicIndex.Found("Fight", "NEW_BUDGET", Heuristic.UNLINKED, "fight"));
        Map<String, HeuristicIndex.LoreClaim> lore = Map.of(
                "bosses-1", new HeuristicIndex.LoreClaim("bosses-1", "1", List.of("policy:fight")),
                "bosses-2", new HeuristicIndex.LoreClaim("bosses-2", "1", List.of()));
        List<String> problems = problems(dangling, HeuristicIndex.ruleTiers().keySet(), HeuristicIndex.measured(), lore);
        assertEquals(2, problems.size(), String.join("\n", problems));
        // The Lore half: a claim id is a slug, and the claim has to name the heuristic's Policy back.
        List<HeuristicIndex.Found> viaLore = List.of(
                new HeuristicIndex.Found("Goo", "REACH", "bosses-1", "fight"),
                new HeuristicIndex.Found("Goo", "RECENT", "bosses-2", "fight"),
                new HeuristicIndex.Found("Goo", "PUMP_WAITS", "bosses-9", "fight"));
        List<String> loreProblems = problems(viaLore, HeuristicIndex.ruleTiers().keySet(), HeuristicIndex.measured(), lore);
        assertEquals(2, loreProblems.size(), String.join("\n", loreProblems));
        assertTrue(loreProblems.stream().anyMatch(p -> p.startsWith("Goo.RECENT") && p.contains("policy:fight")), loreProblems.toString());
        assertTrue(loreProblems.stream().anyMatch(p -> p.startsWith("Goo.PUMP_WAITS")), loreProblems.toString());
        assertTrue(problems.get(0).startsWith("Descend.RESTS"), problems.get(0));
        assertTrue(problems.get(1).startsWith("Fight.NEW_BUDGET"), problems.get(1));
    }

    @Test
    @DisplayName("the unlinked allowlist is exactly the unlinked heuristics, and its count does not rise")
    void unlinked_count_does_not_rise() {
        Set<String> unlinked = HeuristicIndex.annotated().stream().filter(found -> found.slug().equals(Heuristic.UNLINKED))
                .map(HeuristicIndex.Found::constant).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(UNLINKED.keySet()), unlinked, "the unlinked allowlist and the unlinked annotations agree");
        assertTrue(unlinked.size() <= UNLINKED_CEILING, "the unlinked count rose to " + unlinked.size() + " from " + UNLINKED_CEILING);
        assertEquals(UNLINKED_CEILING, unlinked.size(), "lower UNLINKED_CEILING when a heuristic is linked");
    }

    @Test
    @DisplayName("every unlinked entry names its reason and an owning issue that is open work, so the allowlist cannot be a silent list")
    void every_unlinked_entry_names_a_reason_and_an_owning_issue() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> entry : UNLINKED.entrySet()) {
            String reason = entry.getValue();
            if (!reason.matches("(?s).{20,}\\(#\\d+\\)")) {
                bad.add(entry.getKey() + ": '" + reason + "' needs a reason of some length ending in the owning issue, like (#312)");
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
        // The check bites: an entry with no issue, and one with no reason, are both refused.
        assertFalse("a tuned value, no more to say about it".matches("(?s).{20,}\\(#\\d+\\)"));
        assertFalse("(#312)".matches("(?s).{20,}\\(#\\d+\\)"));
    }

    @Test
    @DisplayName("every numeric constant of the brain classes is a declared heuristic or an exempt one with a reason")
    void every_numeric_constant_is_declared_or_exempt() {
        Set<String> declared = HeuristicIndex.annotated().stream().map(HeuristicIndex.Found::constant).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        Set<String> exempt = new TreeSet<>(EXEMPT.keySet());
        exempt.addAll(EXEMPT_NESTED.keySet());
        Set<String> seen = new TreeSet<>();
        for (String constant : HeuristicIndex.numericConstants()) {
            seen.add(constant);
            if (!declared.contains(constant) && !exempt.contains(constant)) {
                missing.add(constant);
            }
            assertFalse(declared.contains(constant) && exempt.contains(constant), constant + " is both a heuristic and exempt");
        }
        assertTrue(missing.isEmpty(), "numeric constants that are neither @Heuristic nor exempt (annotate them, or exempt them with a reason): " + missing);
        Set<String> stale = new TreeSet<>(exempt);
        stale.removeAll(seen);
        assertTrue(stale.isEmpty(), "exempt names that are no constant of the brain: " + stale);
    }

    @Test
    @DisplayName("the annotation carries a slug and nothing else")
    void the_annotation_carries_only_a_slug() {
        List<String> members = new ArrayList<>();
        for (Method method : Heuristic.class.getDeclaredMethods()) {
            members.add(method.getName());
        }
        assertEquals(List.of("value"), members);
        assertEquals(String.class, Heuristic.class.getDeclaredMethods()[0].getReturnType());
    }

    @Test
    @DisplayName("every measured-heuristic row names a page that exists, is used by a heuristic, and is Tier 2 only with a test that exists")
    void measured_rows_are_sound() throws IOException {
        Map<String, HeuristicIndex.Measured> measured = HeuristicIndex.measured();
        assertFalse(measured.isEmpty(), "the index lists measured heuristics");
        Set<String> used = HeuristicIndex.annotated().stream().map(HeuristicIndex.Found::slug).collect(Collectors.toSet());
        Set<String> ruleSlugs = HeuristicIndex.ruleTiers().keySet();
        for (HeuristicIndex.Measured row : measured.values()) {
            assertFalse(ruleSlugs.contains(row.slug()), "a measured slug is not also a Rule slug: " + row.slug());
            Path page = SeedSetsTest.ROOT.resolve("docs").resolve(row.evidence()).normalize();
            assertTrue(Files.isRegularFile(page), row.slug() + " points at a page that does not exist: " + row.evidence());
            String folder = SeedSetsTest.ROOT.resolve("docs").relativize(page).toString().replace('\\', '/');
            assertTrue(folder.startsWith("results/") || folder.startsWith("ideas/"),
                    row.slug() + " must point at a Results page or an idea record: " + row.evidence());
            assertTrue(used.contains(row.slug()), "no heuristic carries the measured slug " + row.slug());
            if (row.tier().equals("2")) {
                assertTrue(testExists(row.test()), row.slug() + " is Tier 2 and needs a test in the repository that reproduces the figure: " + row.test());
            } else {
                assertEquals("none", row.test(), row.slug() + " is Tier 3 and names no test");
            }
        }
    }

    private static boolean testExists(String name) throws IOException {
        try (Stream<Path> walk = Files.walk(SeedSetsTest.ROOT.resolve("shatterfish"))) {
            return walk.anyMatch(file -> file.getFileName().toString().equals(name + ".java")
                    && !file.toString().replace('\\', '/').contains("/build/"));
        }
    }

    @Test
    @DisplayName("the heuristics table on the index page is the one rendered from the code (no drift)")
    void the_table_has_not_drifted() throws IOException {
        String rendered = HeuristicIndex.render(HeuristicIndex.annotated(), HeuristicIndex.ruleTiers(), HeuristicIndex.measured(), HeuristicIndex.loreClaims());
        if ("1".equals(System.getenv(HeuristicIndex.WRITE))) {
            HeuristicIndex.write(rendered);
        }
        String onPage = HeuristicIndex.block().orElseThrow(() -> new AssertionError("docs/brain-rules.md has no heuristics markers"));
        assertEquals(rendered, onPage, "docs/brain-rules.md is stale: run this test with " + HeuristicIndex.WRITE + "=1 to rewrite the table");
    }
}
