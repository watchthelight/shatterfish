package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;

import java.util.List;

/**
 * The two meanings of "calm" the Brain uses, named once so they cannot drift apart (issue #206). Both read only
 * the Observation and the Codex's general knowledge. They differ on a screen with an enemy in view but far off:
 * that screen is not {@link #quiet}, since the enemy is still there to be fought or fled, yet it is
 * {@link #unengaged}, since no fight has begun.
 */
final class Calm {

    private Calm() {
    }

    /**
     * No Prompt open and no enemy in view, less those the game keeps passive until provoked (story 4.7,
     * {@link Fight#passive}). The screen the non-fighting Policies act on, and the screen
     * {@code Memory.calm} records for a wait.
     */
    static boolean quiet(Observation observation, Codex.Knowledge knowledge) {
        if (observation.header().prompt() != PromptKind.NONE) {
            return false;
        }
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY && !Fight.passive(knowledge, observation.header(), actor)) {
                return false;
            }
        }
        return true;
    }

    /**
     * No enemy in view, or the nearest farther than {@link Traps#ENGAGED} cells (issues #184, #203): a fight has
     * not begun, so the hero may search ahead of a Step near a shown trap. It says nothing of a Prompt; a caller
     * that cares checks it.
     */
    static boolean unengaged(Observation observation, Codex.Knowledge knowledge) {
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        return enemies.isEmpty()
                || Fight.nearest(observation.map(), observation.hero().cell(), enemies) > Traps.ENGAGED;
    }
}
