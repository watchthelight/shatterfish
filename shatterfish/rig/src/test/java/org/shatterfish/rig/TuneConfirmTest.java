package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 10.37: the confirmation reads pairs the way a ranked comparison does and keeps a candidate only by its rule. */
class TuneConfirmTest {

    private static TuneConfirm.Run run(String hero, long salt, int depth, int bosses) {
        RunLog.Outcome outcome = new RunLog.Outcome(false, false, 0, depth, 1000, "DEATH", bosses);
        return new TuneConfirm.Run(hero, salt, "AAA-AAA-AAA", false, "DEATH", depth, 1000, bosses, 10, 100, outcome);
    }

    private static List<TuneConfirm.Run> side(int pairs, int depth, int bosses) {
        List<TuneConfirm.Run> runs = new ArrayList<>();
        for (int hero = 0; hero < TuneConfirm.CLASSES.size(); hero++) {
            for (int i = 0; i < pairs; i++) {
                runs.add(run(TuneConfirm.CLASSES.get(hero), 1000 + i, depth, bosses));
            }
        }
        return runs;
    }

    @Test
    @DisplayName("a candidate that is better on every pair is accepted pooled and kept")
    void a_clear_win_is_kept() {
        List<TuneConfirm.Verdict> slice = TuneConfirm.verdicts(side(40, 6, 1), side(40, 5, 0));
        assertEquals(Gsprt.Verdict.ACCEPT, slice.get(0).verdict());
        assertEquals(240, slice.get(0).better());
        assertEquals(1 + 6, slice.size());
        assertTrue(TuneConfirm.kept(slice));
    }

    @Test
    @DisplayName("identical sides are all ties: undecided, not kept (the control that the test does not accept by default)")
    void ties_are_not_kept() {
        List<TuneConfirm.Verdict> slice = TuneConfirm.verdicts(side(40, 5, 0), side(40, 5, 0));
        assertEquals(240, slice.get(0).equal());
        assertFalse(TuneConfirm.kept(slice));
    }

    @Test
    @DisplayName("one class with fewer bosses, or 8 more worse pairs than better, is a veto even when the pooled test accepts")
    void a_class_can_veto() {
        List<TuneConfirm.Run> candidate = side(40, 6, 1);
        List<TuneConfirm.Run> current = side(40, 5, 0);
        // The Mage's candidate Runs end a floor above the current ones, with fewer bosses: worse on all of its 40 pairs.
        candidate.replaceAll(r -> r.heroClass().equals("MAGE") ? run("MAGE", r.salt(), 4, 0) : r);
        current.replaceAll(r -> r.heroClass().equals("MAGE") ? run("MAGE", r.salt(), 5, 1) : r);
        List<TuneConfirm.Verdict> slice = TuneConfirm.verdicts(candidate, current);
        TuneConfirm.Verdict mage = slice.stream().filter(v -> v.group().equals("MAGE")).findFirst().orElseThrow();
        assertEquals(40, mage.worse());
        assertTrue(TuneConfirm.vetoed(mage));
        assertFalse(TuneConfirm.kept(slice));
    }

    @Test
    @DisplayName("a Run with no ending is a missing pair that scores a tie")
    void a_missing_run_is_a_tie() {
        List<TuneConfirm.Run> candidate = side(40, 6, 1);
        List<TuneConfirm.Run> current = side(40, 5, 0);
        candidate.set(0, new TuneConfirm.Run("WARRIOR", 1000, "AAA-AAA-AAA", false, "NONE", 0, 0, 0, 0, 0, null));
        TuneConfirm.Verdict pooled = TuneConfirm.verdicts(candidate, current).get(0);
        assertEquals(1, pooled.missing());
        assertEquals(1, pooled.equal());
    }
}
