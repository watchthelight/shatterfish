package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.TalentView;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a search is credited as coverage (issues #205 and #229). The search is judged by the screen it was issued on,
 * since {@code Hero.search} runs at the start of the Action, on the hero's field of view then
 * ({@code core/.../actors/hero/Hero.java:2562}); the screen after it shows the buffs of a turn later, and a
 * blindness that wears off in that turn is gone from it.
 */
class SearchSightTest {

    private static final BuffView LAST_TURN_BLIND = new BuffView("blinded", true, 100);
    private static final BuffView LAST_TURN_MELDED = new BuffView("shadowmelded", true, 100);

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 5L);
    }

    /** The cells searched after a search handed over on {@code issued}, with {@code after} the screen that followed. */
    private static List<Memory.Spot> searchedAfter(Observation issued, Observation after) {
        Brain brain = brain();
        Belief belief = brain.update(issued, null);
        belief = brain.handed(issued, belief, new Brain.Decided(new Action.Search(), null, List.of(), ""));
        belief = brain.update(after, belief);
        return Memory.of(belief).dwelt();
    }

    private static Observation sighted(HeroClass heroClass, BuffView... buffs) {
        return BlindSearchTest.seen(BlindSearchTest.room(heroClass, false, null), false, List.of(buffs));
    }

    private static Observation blind(HeroClass heroClass, BuffView... buffs) {
        return BlindSearchTest.seen(BlindSearchTest.room(heroClass, false, null), true, List.of(buffs));
    }

    @Test
    @DisplayName("a Rogue blinded for its last turn when it searched has not covered the two cells around it, though the "
            + "buff is gone from the screen after")
    void blindness_that_wore_off_in_the_search() {
        assertEquals(List.of(), searchedAfter(blind(HeroClass.ROGUE, LAST_TURN_BLIND), sighted(HeroClass.ROGUE)));
    }

    @Test
    @DisplayName("a Rogue shadowmelded for its last turn when it searched has not covered the two cells around it")
    void shadowmeld_that_wore_off_in_the_search() {
        assertEquals(List.of(), searchedAfter(blind(HeroClass.ROGUE, LAST_TURN_MELDED), sighted(HeroClass.ROGUE)));
    }

    @Test
    @DisplayName("a Rogue who searched sighted has covered its spot, though blindness shows on the screen after")
    void blindness_that_began_after_the_search() {
        Observation issued = sighted(HeroClass.ROGUE);
        Observation after = blind(HeroClass.ROGUE, new BuffView("blinded", true, 1000));
        assertEquals(List.of(new Memory.Spot(2, 0, issued.hero().cell())), searchedAfter(issued, after));
    }

    @Test
    @DisplayName("a search on branch 1 is credited to branch 1 and to no other")
    void branch_one() {
        Observation base = sighted(HeroClass.ROGUE);
        Observation branch = onBranch(base, 1);
        assertEquals(List.of(new Memory.Spot(2, 1, base.hero().cell())), searchedAfter(branch, branch));
    }

    @Test
    @DisplayName("a blind search on branch 1 is not credited")
    void blind_on_branch_one() {
        Observation branch = onBranch(blind(HeroClass.ROGUE, new BuffView("blinded", true, 500)), 1);
        assertEquals(List.of(), searchedAfter(branch, branch));
    }

    private static Observation onBranch(Observation base, int branch) {
        HeaderSection h = base.header();
        HeaderSection header = new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(),
                h.challenges(), h.depth(), branch, h.sealed(), h.oracle(), h.prompt());
        return new Observation(header, base.map(), base.actors(), base.hero(), base.inventory(), base.journal(),
                base.log(), base.actions(), base.prompt());
    }

    /**
     * Magical sight's sense of twelve ({@code MagicalSight.java:34}) is copied into the field of view of a hero who
     * cannot see ({@code Level.java:1391-1417}), and {@code Hero.search} reads that field ({@code Hero.java:2562}):
     * a blind Rogue with it sees the two cells its search reaches.
     */
    @Test
    @DisplayName("a blind Rogue with magical sight searches the two cells around it")
    void magical_sight_fills_the_view() {
        Observation issued = blind(HeroClass.ROGUE, new BuffView("blinded", true, 500),
                new BuffView("magical sight", true, 1000));
        assertEquals(List.of(new Memory.Spot(2, 0, issued.hero().cell())), searchedAfter(issued, issued));
    }

    /**
     * Mind vision's sense of two leaves the corners of the Rogue's square out of view ({@code Level.java:1400-1419},
     * {@code ShadowCaster.java:41-43}), and the Talisman of Foresight's buff is named like the potion's but does not
     * scan ({@code Hero.java:2525}, {@code TalismanOfForesight.java:363-367}): neither makes a blind search whole,
     * so neither is credited.
     */
    @Test
    @DisplayName("a blind Rogue with mind vision, or a buff named foresight, is not credited with the two cells")
    void mind_vision_and_foresight_are_not_read() {
        for (String name : new String[] {"mind vision", "foresight"}) {
            Observation issued = blind(HeroClass.ROGUE, new BuffView("blinded", true, 500), new BuffView(name, true, 1000));
            assertEquals(List.of(), searchedAfter(issued, issued), name);
        }
    }

    @Test
    @DisplayName("a blind Rogue with some other buff is not credited")
    void other_buffs_do_not_help() {
        Observation issued = blind(HeroClass.ROGUE, new BuffView("blinded", true, 500), new BuffView("haste", true, 1000));
        assertEquals(List.of(), searchedAfter(issued, issued));
    }

    /**
     * {@code Hero.java:2522-2523}: two for the Rogue, one more with Wide Search. The Brain still counts two:
     * a one-point Wide Search is circular ({@code :2521}), so a square of three would credit corners the search
     * does not reach, and under-crediting only makes the Brain search again.
     */
    @Test
    @DisplayName("Wide Search is not counted in the search radius: a Rogue's is two with or without it")
    void wide_search_is_not_counted() {
        Observation plain = sighted(HeroClass.ROGUE);
        HeroSection was = plain.hero();
        List<TalentView> talents = new ArrayList<>(was.talents());
        talents.add(new TalentView(2, "wide search", 1));
        HeroSection wide = new HeroSection(was.cell(), was.name(), was.subclass(), was.ability(), was.level(), was.exp(),
                was.expToLevel(), was.hp(), was.ht(), was.shield(), was.strength(), was.strengthBonus(), was.gold(),
                was.energy(), was.hunger(), was.buffs(), talents, was.talentPointsAvailable(), was.quickslots());
        Observation talented = new Observation(plain.header(), plain.map(), plain.actors(), wide, plain.inventory(),
                plain.journal(), plain.log(), plain.actions(), plain.prompt());
        assertEquals(2, Explore.radius(plain));
        assertEquals(2, Explore.radius(talented));
        assertEquals(1, Explore.radius(sighted(HeroClass.WARRIOR)));
    }

    private static final String[] CORRIDOR = {
        "##############",
        "#.@..^.......#",
        "#............ ",
        "##############",
    };

    @Test
    @DisplayName("a sighted Warrior searches ahead of a Step into a shown trap's reach, and the cell it searched from "
            + "is then covered")
    void search_ahead_of_a_shown_trap() {
        Observation screen = ExplorePolicyTest.screen(1, CORRIDOR);
        Brain brain = brain();
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(decided.action() instanceof Action.Search, String.valueOf(decided.action()));
        assertTrue(decided.decision().chosen().why().startsWith(Traps.AHEAD), decided.decision().chosen().why());
        int ahead = screen.hero().cell() + 1;
        assertTrue(Traps.unsearched(screen, Memory.of(belief), ahead));
        belief = brain.handed(screen, belief, decided);
        belief = brain.update(screen, belief);
        assertFalse(Traps.unsearched(screen, Memory.of(belief), ahead), "the search covered the cell ahead");
    }

    @Test
    @DisplayName("a blind Rogue does not search ahead of a shown trap: the search would reach only the cell beside it")
    void no_search_ahead_when_blind() {
        Observation screen = BlindSearchTest.seen(ExplorePolicyTest.screen(1, false, HeroClass.ROGUE, CORRIDOR), true,
                List.of(new BuffView("blinded", true, 500)));
        Brain brain = brain();
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        String why = decided.decision() == null ? "" : decided.decision().chosen().why();
        assertFalse(why.startsWith(Traps.AHEAD), why);
    }
}
