package org.shatterfish.harness.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.observer.OracleView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two definitions of story 6.5 on hand-built cases whose answers a reader can do on paper (AC 2). No game runs here:
 * the arithmetic is {@link Properties}, and a later reader who doubts a figure on the Results page checks it against
 * these cases first.
 */
class PropertiesDefinitionTest {

    private static Properties.Sample sample(long seed, int hiddenFloor, int hiddenRun, Properties.Leaf... leaves) {
        return new Properties.Sample(seed, 1, "toy", hiddenFloor, hiddenRun, Map.of(), List.of(leaves));
    }

    private static final int LAST = Properties.HORIZONS.length - 1;

    @Test
    @DisplayName("a toy snapshot where every sibling agrees: agreement 1, and kappa has nothing left to explain when all survive")
    void every_sibling_agrees() {
        Properties.Sample allLive = sample(1, 0, 0, Properties.Leaf.same(true, 0, 0), Properties.Leaf.same(true, 0, 0),
                Properties.Leaf.same(true, 0, 0), Properties.Leaf.same(true, 0, 0));
        assertEquals(1.0, Properties.leafCorrelation(List.of(allLive), LAST), 1e-12, "four leaves, six pairs, all agree");
        assertEquals(1.0, Properties.survival(List.of(allLive), LAST), 1e-12);
        assertEquals(1.0, Properties.chance(1.0), 1e-12, "if everyone lives, agreement is certain by chance");
        assertTrue(Double.isNaN(Properties.kappa(List.of(allLive), LAST)), "kappa is 0/0: a payoff that never varies explains nothing");

        // Half the snapshots all-live and half all-dead: siblings always agree, yet survival is 1/2, so chance is 1/2
        // and kappa is (1 - 0.5) / (1 - 0.5) = 1. This is the case where the agreement is the position's and not the survival rate's.
        Properties.Sample allDead = sample(2, 0, 0, Properties.Leaf.same(false, 0, 0), Properties.Leaf.same(false, 0, 0));
        List<Properties.Sample> both = List.of(allLive, allDead);
        assertEquals(1.0, Properties.leafCorrelation(both, LAST), 1e-12);
        assertEquals(4.0 / 6.0, Properties.survival(both, LAST), 1e-12, "4 live leaves of 6");
        double chance = (4.0 / 6.0) * (4.0 / 6.0) + (2.0 / 6.0) * (2.0 / 6.0);
        assertEquals(5.0 / 9.0, chance, 1e-12);
        assertEquals((1.0 - chance) / (1.0 - chance), Properties.kappa(both, LAST), 1e-12);
        assertEquals(1.0, Properties.kappa(both, LAST), 1e-12);
    }

    @Test
    @DisplayName("agreement is the share of agreeing pairs: three leaves, two alive and one dead, agree in one pair of three")
    void agreement_counts_pairs() {
        assertEquals(1.0 / 3.0, Properties.agreement(List.of(true, true, false)), 1e-12);
        assertEquals(0.0, Properties.agreement(List.of(true, false)), 1e-12);
        assertTrue(Double.isNaN(Properties.agreement(List.of(true))), "a lone leaf has no pair to agree with");
        // A sample with one leaf does not enter the mean at all, rather than counting as zero or one.
        Properties.Sample lone = sample(1, 0, 0, Properties.Leaf.same(true, 0, 0));
        Properties.Sample split = sample(2, 0, 0, Properties.Leaf.same(true, 0, 0), Properties.Leaf.same(false, 0, 0));
        assertEquals(0.0, Properties.leafCorrelation(List.of(lone, split), LAST), 1e-12);
    }

    @Test
    @DisplayName("the second payoff separates leaves the first cannot: all alive, half of them sound")
    void the_second_payoff_can_disagree_where_survival_cannot() {
        Properties.At sound = new Properties.At(true, true, 0, 0);
        Properties.At hurt = new Properties.At(true, false, 0, 0);
        Properties.Leaf a = new Properties.Leaf(Properties.End.HORIZON, 20, new Properties.At[] {sound, sound});
        Properties.Leaf b = new Properties.Leaf(Properties.End.HORIZON, 20, new Properties.At[] {hurt, hurt});
        Properties.Sample s = sample(1, 0, 0, a, b);
        assertEquals(1.0, Properties.leafCorrelation(List.of(s), LAST), 1e-12, "both alive");
        assertEquals(0.0, Properties.leafCorrelation(List.of(s), LAST, Properties.Payoff.SOUND), 1e-12, "one sound, one not");
        assertEquals(0.5, Properties.survival(List.of(s), LAST, Properties.Payoff.SOUND), 1e-12);
        assertEquals(-1.0, Properties.kappa(List.of(s), LAST, Properties.Payoff.SOUND), 1e-12, "(0 - 0.5) / (1 - 0.5)");
        assertTrue(Double.isNaN(Properties.kappa(List.of(s), LAST)), "alive for both: nothing to explain");
    }

    @Test
    @DisplayName("a snapshot whose revealed share is known: 4 facts on the floor, leaves reveal 1, 2 and 0, mean share one quarter")
    void the_revealed_share_is_known() {
        Properties.Sample s = sample(1, 4, 4, Properties.Leaf.same(true, 1, 1), Properties.Leaf.same(true, 2, 2), Properties.Leaf.same(true, 0, 0));
        double expected = (0.25 + 0.5 + 0.0) / 3.0;
        assertEquals(0.25, expected, 1e-12);
        assertEquals(0.25, Properties.disambiguation(List.of(s), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL), 1e-12);
        assertEquals(1, Properties.disambiguated(List.of(s), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL));
    }

    @Test
    @DisplayName("the first change: the denominator is the floor's facts, not the Run's whole set of unknown appearances")
    void the_denominator_is_the_floor() {
        // Ten unknown appearances in the Run, four of them reachable on this floor; the leaves reveal two of the four.
        Properties.Sample s = sample(1, 4, 10, Properties.Leaf.same(true, 2, 2), Properties.Leaf.same(true, 2, 2));
        assertEquals(0.5, Properties.disambiguation(List.of(s), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL), 1e-12, "2 of the floor's 4");
        assertEquals(0.2, Properties.disambiguation(List.of(s), LAST, Properties.Basis.RUN, Properties.Counted.ALL), 1e-12,
                "2 of the Run's 10: story 1.21's denominator dilutes the same reveal to a fifth");
    }

    @Test
    @DisplayName("the second change: a death counts where it happens, and the old count leaves it out")
    void a_death_counts_at_the_death() {
        // One leaf survives having revealed nothing; one dies after a trap sprung and a potion drunk revealed 3 of 4.
        Properties.Leaf survivor = Properties.Leaf.same(true, 0, 0);
        Properties.Leaf dies = Properties.Leaf.same(false, 3, 3);
        Properties.Sample s = sample(1, 4, 4, survivor, dies);
        assertEquals((0.0 + 0.75) / 2.0, Properties.disambiguation(List.of(s), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL), 1e-12);
        assertEquals(0.0, Properties.disambiguation(List.of(s), LAST, Properties.Basis.FLOOR, Properties.Counted.SURVIVORS), 1e-12,
                "story 1.21 averaged over surviving leaves only, so the event that revealed the facts and killed the hero was not seen");
        // A snapshot whose every leaf died has no survivor to count under the old rule, and is still a sample under the new.
        Properties.Sample doomed = sample(2, 4, 4, Properties.Leaf.same(false, 2, 2), Properties.Leaf.same(false, 4, 4));
        assertTrue(Double.isNaN(Properties.disambiguation(List.of(doomed), LAST, Properties.Basis.FLOOR, Properties.Counted.SURVIVORS)));
        assertEquals(0.75, Properties.disambiguation(List.of(doomed), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL), 1e-12);
    }

    @Test
    @DisplayName("a snapshot with nothing hidden on the floor is left out of the factor and not counted as zero")
    void nothing_hidden_is_not_zero() {
        Properties.Sample bare = sample(1, 0, 3, Properties.Leaf.same(true, 0, 0));
        Properties.Sample held = sample(2, 2, 2, Properties.Leaf.same(true, 2, 2));
        assertEquals(1.0, Properties.disambiguation(List.of(bare, held), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL), 1e-12);
        assertTrue(Double.isNaN(Properties.disambiguation(List.of(bare), LAST, Properties.Basis.FLOOR, Properties.Counted.ALL)));
    }

    @Test
    @DisplayName("the horizons: a leaf that died at wait 10 is alive at the short horizon and dead at the long one")
    void the_horizons_read_apart() {
        Properties.Leaf diesLate = new Properties.Leaf(Properties.End.DEATH, 10, new Properties.At[] {
                new Properties.At(true, 0, 0), new Properties.At(false, 1, 1)});
        Properties.Sample s = sample(1, 2, 2, diesLate, Properties.Leaf.same(true, 0, 0));
        assertEquals(1.0, Properties.leafCorrelation(List.of(s), 0), 1e-12, "both alive after four waits");
        assertEquals(0.0, Properties.leafCorrelation(List.of(s), 1), 1e-12, "one dead after twenty");
    }

    @Test
    @DisplayName("the facts of a sidecar are told apart: an appearance an item of the floor shows, the Run's others, and the floor's secrets")
    void the_facts_are_told_apart() {
        OracleView.Identity onFloor = new OracleView.Identity("potion", "crimson", "Healing", "PotionOfHealing");
        OracleView.Identity elsewhere = new OracleView.Identity("scroll", "KAUNAN", "Identify", "ScrollOfIdentify");
        OracleView view = new OracleView(1L, List.of(onFloor, elsewhere), List.of(), List.of(77), List.of(12, 13),
                List.of(new OracleView.Secret(40, "FrostTrap", "frost trap", true)), List.of(onFloor));
        Set<String> facts = Launcher.facts(view);
        assertEquals(Set.of("identity potion crimson", "elsewhere scroll KAUNAN", "door 12", "door 13", "trap 40", "mimic 77"), facts);
        assertEquals(Map.of("identity", 1, "elsewhere", 1, "door", 2, "trap", 1, "mimic", 1), PropertiesMeasurement.kinds(facts));
        assertEquals(5, facts.stream().filter(PropertiesMeasurement::isFloor).count(), "the Run's other appearance is not the floor's");
        // Revealed: the potion was drunk (no longer unknown), a door found, the scroll still unknown.
        Set<String> later = Set.of("elsewhere scroll KAUNAN", "door 13", "trap 40", "mimic 77");
        assertEquals(2, PropertiesMeasurement.revealed(facts, later, true), "the potion and the door, of the floor's five");
        assertEquals(2, PropertiesMeasurement.revealed(facts, later, false), "the scroll was not revealed, so the Run-wide count is the same two");
    }

    @Test
    @DisplayName("percentiles interpolate; a constant statistic has a point interval; the same samples give the same interval")
    void the_interval_is_a_function_of_the_samples() {
        assertEquals(3.0, Properties.percentile(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 0.5), 1e-12);
        assertEquals(1.1, Properties.percentile(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 0.025), 1e-12, "0.025 of the way along four steps");
        assertEquals(4.9, Properties.percentile(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 0.975), 1e-12);

        List<Properties.Sample> samples = new ArrayList<>();
        for (int seed = 0; seed < 12; seed++) {
            samples.add(sample(seed, 2, 2, Properties.Leaf.same(seed % 3 != 0, seed % 2, seed % 2), Properties.Leaf.same(seed % 4 != 0, 1, 1)));
        }
        Properties.Interval constant = Properties.bootstrap(samples, x -> 0.5, 200, 7L);
        assertEquals(0.5, constant.low(), 1e-12);
        assertEquals(0.5, constant.high(), 1e-12);
        Properties.Interval a = Properties.bootstrap(samples, x -> Properties.survival(x, LAST), 400, 11L);
        Properties.Interval b = Properties.bootstrap(samples, x -> Properties.survival(x, LAST), 400, 11L);
        assertEquals(a, b, "the draws are a function of the seed");
        assertTrue(a.low() <= a.point() && a.point() <= a.high(), "the point lies in its interval: " + a);
        assertTrue(a.low() < a.high(), "twelve seeds that differ give an interval with a width: " + a);
        assertEquals(400, a.valid());
    }

    @Test
    @DisplayName("the page's tables print for a toy row and hold no NaN")
    void the_tables_print() {
        List<Properties.Sample> samples = new ArrayList<>();
        for (int seed = 0; seed < 6; seed++) {
            samples.add(new Properties.Sample(seed, 5, "toy", 3, 5, Map.of("identity", 2, "trap", 1),
                    List.of(Properties.Leaf.same(true, seed % 2, seed % 2), Properties.Leaf.same(seed % 3 != 0, 1, 1))));
        }
        String page = Properties.tables(List.of(new Properties.Row("toy", samples, 12, 0, Map.of())), 50, 1L);
        for (String heading : List.of("### The two properties, per driving Brain", "Horizon 20 waits", "Horizon 4 waits",
                "### Story 1.21's reading and the two changes, one at a time", "### What the leaves did")) {
            assertTrue(page.contains(heading), "the page has " + heading);
        }
        assertFalse(page.contains("NaN"), "an undefined figure is printed as n/a");
    }
}
