package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A red sentry's room (issue #219, step 6). The sentry fires at a hero on the room's special floor
 * once it has charged, a turn at a time (SentryRoom.java:253-272, :292), and the walk to the chest
 * past it is on that floor. Three Runs on the standard Warrior set died on it at depths 2 to 4, in the
 * chest Policy's walk and the explore Policy's frontier. Everything goes through {@link Brain#decide}.
 *
 * <pre>
 *   y0:  SP  SP  [S] SP  SP        the sentry's pedestal at cell 2
 *   y1:  SP  SP  SP  SP  SP
 *   y2:  H   SP  SP  SP  C         the hero on plain floor at cell 10, the chest at cell 14
 * </pre>
 */
class SentryRoomTest {

    private static final int WIDTH = 5;

    private static final int SENTRY = 2;

    private static final int HERO = 10;

    private static final int CHEST = 14;

    /** The room, the hero on {@code hero}, the sentry drawn when {@code sentryInView}, offering what the game offers. */
    private static Observation room(int depth, int hero, boolean sentryInView) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(WIDTH * 3, Tile.EMPTY_SP));
        tiles.set(SENTRY, Tile.PEDESTAL);
        tiles.set(HERO, Tile.EMPTY);
        tiles.set(CHEST, Tile.EMPTY);
        Observation bare = Screens.grid(depth, WIDTH, tiles, List.of(new HeapView(CHEST, HeapKind.CHEST, false, "", 0, "")));
        List<ActorView> actors = sentryInView
                ? List.of(new ActorView(SENTRY, "red sentry", Alignment.NEUTRAL, 1, false, Emote.NONE, List.of()))
                : List.of();
        Observation placed = new Observation(bare.header(), bare.map(), new ActorsSection(actors),
                Screens.heroAt(hero, 10, 200, 200), bare.inventory(), bare.journal(), bare.log(), bare.actions(),
                bare.prompt());
        return placed.withActions(ValidActions.of(placed));
    }

    private static boolean onSpecialFloor(Action action) {
        return action instanceof Action.Step step && step.cell() != HERO && step.cell() != CHEST && step.cell() != SENTRY;
    }

    @Test
    @DisplayName("a chest across the sentry's special floor is not walked to, however healthy the hero")
    void the_chest_across_the_floor_is_left() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        Observation seen = room(1, HERO, true);
        Brain.Decided decided = brain.decide(seen, brain.update(seen, null));
        assertNotEquals("open-chest", decided.decision().policy(), decided.decision().toString());
        assertFalse("explore".equals(decided.decision().policy()) && onSpecialFloor(decided.action()),
                decided.decision().toString());
    }

    @Test
    @DisplayName("and stays left when the hero has stepped out of the sentry's sight: the Brain remembers it")
    void the_floor_stays_left_out_of_sight() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        Belief belief = brain.update(room(1, HERO, true), null);
        for (int wait = 0; wait < 3; wait++) {
            Observation unseen = room(1, HERO, false);
            Brain.Decided decided = brain.decide(unseen, belief);
            assertNotEquals("open-chest", decided.decision().policy(), "wait " + wait + ": " + decided.decision());
            belief = brain.update(unseen, belief);
        }
    }

    @Test
    @DisplayName("the same room with no sentry seen is crossed for the chest, as before")
    void without_a_sentry_the_chest_is_fetched() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        Observation unseen = room(1, HERO, false);
        Brain.Decided decided = brain.decide(unseen, brain.update(unseen, null));
        assertEquals("open-chest", decided.decision().policy(), decided.decision().toString());
        assertTrue(onSpecialFloor(decided.action()), decided.action().toString());
    }

    @Test
    @DisplayName("a sentry seen on another floor does not wall off this one")
    void another_floor_is_not_walled_off() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        Belief belief = brain.update(room(2, HERO, true), null);
        Observation elsewhere = room(1, HERO, false);
        Brain.Decided decided = brain.decide(elsewhere, belief);
        assertEquals("open-chest", decided.decision().policy(), decided.decision().toString());
    }
}
