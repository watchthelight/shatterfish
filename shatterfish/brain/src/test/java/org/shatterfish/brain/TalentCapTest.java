package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.TalentView;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A talent already at its most points is no choice (story 4.13, issue #162): the valid set offers it,
 * the executor refuses it, and the refusal ends the Run.
 */
class TalentCapTest {

    private static Observation screen(List<TalentView> talents, Action... actions) {
        HeroSection hero = new HeroSection(1, "", HeroSubclass.NONE, "", 3, 0, 1, 20, 20, 0, 10, 0, 0, 0,
                Hunger.NONE, List.of(), talents, List.of(1, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        return Screens.showing(1, hero, List.of(), actions);
    }

    @Test
    @DisplayName("a tier-1 talent holding two points is withheld; one holding one is not")
    void capped() {
        Action full = new Action.Talent("veteran's intuition");
        Action open = new Action.Talent("iron will");
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
        Observation both = screen(List.of(new TalentView(1, "veteran's intuition", 2), new TalentView(1, "iron will", 1)),
                full, open);
        for (long seed = 0; seed < 20; seed++) {
            Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, seed).decide(both, Memory.START.belief());
            assertEquals(open, decided.action(), "seed " + seed + ": " + decided.decision());
        }
        Observation only = screen(List.of(new TalentView(1, "veteran's intuition", 2)), full);
        assertEquals(null, brain.decide(only, Memory.START.belief()).action(), "nothing left to choose");
    }
}
