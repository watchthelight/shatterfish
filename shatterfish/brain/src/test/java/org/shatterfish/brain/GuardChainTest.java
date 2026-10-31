package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Prison Guard's chain (story 10.10, issue #356, Rule {@code guard-chain}). A screen is rows of text:
 * {@code @} the hero, {@code B} the Guard, {@code r} another awake enemy, {@code #} a wall.
 *
 * <p>The first test fails on {@code main}: there no Policy knows the Guard's line, and the hero with a rat in view
 * and the Guard three cells away on a clear row stands where the chain will set it down.
 */
class GuardChainTest {

    private static Observation screen(String... rows) {
        Observation plain = FightPolicyTest.screen(7, 20, false, "worn shortsword", rows);
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : plain.actors().actors()) {
            actors.add(actor.name().equals("brute")
                    ? new ActorView(actor.cell(), Guard.NAME, actor.alignment(), actor.healthPips(), actor.invisible(), actor.emote(), actor.buffs())
                    : actor);
        }
        Observation bare = new Observation(plain.header(), plain.map(), new ActorsSection(actors), plain.hero(), plain.inventory(),
                plain.journal(), plain.log(), ActionsSection.NONE, plain.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        return brain.decide(screen, belief);
    }

    private static final String[] LINE = {
            "##########",
            "#...#...r#",
            "#..@..B..#",
            "#...#....#",
            "##########"};

    @Test
    @DisplayName("in the Guard's line with another enemy in view, the hero steps out of the line and not away from the Guard")
    void steps_out_of_the_line() {
        Observation screen = screen(LINE);
        int guard = 6 + 2 * 10;
        assertTrue(Guard.pulls(screen, guard, screen.hero().cell()), "the hero is in the line");
        Brain.Decided decided = decide(screen);
        assertTrue(decided.decision().chosen().why().startsWith("guard: dodge"), decided.decision().toString());
        int cell = ((Action.Step) decided.action()).cell();
        assertFalse(Guard.pulls(screen, guard, cell), "the step leaves the line");
        assertEquals(3, Math.max(Math.abs(cell % 10 - guard % 10), Math.abs(cell / 10 - guard / 10)));
    }

    @Test
    @DisplayName("where the pull cannot happen the hero is not moved: out of reach, in a blocked line, beside the Guard, or alone with it")
    void no_dodge_where_the_pull_cannot_happen() {
        String[] far = {
                "############",
                "#...#.....r#",
                "#@........B#",
                "#...#......#",
                "############"};
        Observation outOfReach = screen(far);
        assertFalse(Guard.pulls(outOfReach, 10 + 2 * 12, outOfReach.hero().cell()), "nine cells away");
        assertFalse(decide(outOfReach).decision().chosen().why().startsWith("guard:"));

        String[] blocked = {
                "##########",
                "#...#...r#",
                "#..@#.B..#",
                "#...#....#",
                "##########"};
        Observation behindWall = screen(blocked);
        assertFalse(Guard.pulls(behindWall, 6 + 2 * 10, behindWall.hero().cell()), "a wall between");
        assertFalse(decide(behindWall).decision().chosen().why().startsWith("guard:"));

        String[] beside = {
                "##########",
                "#...#...r#",
                "#...@B...#",
                "#...#....#",
                "##########"};
        assertFalse(decide(screen(beside)).decision().chosen().why().startsWith("guard:"));

        String[] alone = {
                "##########",
                "#...#....#",
                "#..@..B..#",
                "#...#....#",
                "##########"};
        Observation withNoCompany = screen(alone);
        assertTrue(Guard.pulls(withNoCompany, 6 + 2 * 10, withNoCompany.hero().cell()), "in the line, but no one else is in view");
        assertFalse(decide(withNoCompany).decision().chosen().why().startsWith("guard:"));
    }
}
