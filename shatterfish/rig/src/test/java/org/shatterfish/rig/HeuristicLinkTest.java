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
     * it, and no Results page or idea record played it. Linking one means annotating it with a slug
     * and deleting it here; the test fails on a stale name and on a missing one.
     */
    static final Set<String> UNLINKED = Set.of(
            "Brain.AVOID_RADIUS",
            "Brain.AVOID_WAITS",
            "Descend.ALLOWANCE",
            "Descend.PER_DROP",
            "Douse.FLEE",
            "Eat.WATER_REACH",
            "Explore.FRUGAL_SEARCHES",
            "Explore.SEARCHES",
            "Explore.STUCK",
            "Fight.CHASE_WAITS",
            "Fight.FLIGHTS",
            "Fight.HOLDS",
            "Fight.MARGIN_PER_MILLE",
            "Fight.RECALL_WAITS",
            "Goo.DRINK_PER_MILLE",
            "Heal.HORIZON",
            "Heal.REDRINK_SHARE_PER_MILLE",
            "Larder.FRUGAL",
            "Memory.BOUNCES",
            "Memory.BOUNCE_WAITS",
            "Memory.FLEETING_WAITS",
            "OnePly.DEATH_PER_MILLE",
            "OnePly.ENGAGE",
            "OnePly.ENUMERATED",
            "Playbook.Identify.inventoryOddsPerMille",
            "Plunge.FIT_PERCENT",
            "Plunge.STARVING_PERCENT",
            "Ranged.REACH",
            "SafeTest.GAS_TURNS",
            "Traps.ENGAGED",
            "Traps.REACH");

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
            Map.entry("Sentry.MAX_ZONE", "a capacity guard on a flood fill"));

    /** Constants of nested classes are listed by their nested name; the capacity of the shunned set is one. */
    static final Map<String, String> EXEMPT_NESTED = Map.of("Memory.Windows.SHUNNED", "a capacity of the Memory");

    static List<String> problems(List<HeuristicIndex.Found> found, Set<String> ruleSlugs, Map<String, HeuristicIndex.Measured> measured,
                                 Map<String, HeuristicIndex.LoreClaim> lore) {
        List<String> problems = new ArrayList<>();
        for (HeuristicIndex.Found heuristic : found) {
            String slug = heuristic.slug();
            if (slug.equals(Heuristic.UNLINKED)) {
                if (!UNLINKED.contains(heuristic.constant())) {
                    problems.add(heuristic.constant() + " is annotated unlinked but is not on the unlinked allowlist; link it to a Rule or a measurement, or add it knowingly");
                }
            } else if (!ruleSlugs.contains(slug) && !measured.containsKey(slug) && !lore.containsKey(slug)) {
                problems.add(heuristic.constant() + " carries slug '" + slug + "', which is neither a Rule row, a measured-heuristic row of docs/brain-rules.md nor a Lore claim id");
            } else if (lore.containsKey(slug) && !ruleSlugs.contains(slug) && !measured.containsKey(slug)
                    && !names(lore.get(slug), heuristic)) {
                problems.add(heuristic.constant() + " is linked to Lore claim '" + slug + "', whose implemented_in does not name "
                        + "policy:" + heuristic.policy() + " (the claim and the heuristic have to agree)");
            } else if (UNLINKED.contains(heuristic.constant())) {
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
        assertEquals(new TreeSet<>(UNLINKED), unlinked, "the unlinked allowlist and the unlinked annotations agree");
        assertTrue(unlinked.size() <= UNLINKED_CEILING, "the unlinked count rose to " + unlinked.size() + " from " + UNLINKED_CEILING);
        assertEquals(UNLINKED_CEILING, unlinked.size(), "lower UNLINKED_CEILING when a heuristic is linked");
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
