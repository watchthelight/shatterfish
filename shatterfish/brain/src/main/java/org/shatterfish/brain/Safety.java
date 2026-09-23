package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Safety flags of a Decision (story 4.4, FR-32): what on the screen says the hero is in danger,
 * as labels the Panel and the strategy log print as they are.
 *
 * <p>Each is read off what the screen draws: the health the status pane shows, the enemies in view,
 * the hunger icon. None is a judgement about what to do; the Policies that later act on danger read
 * the same screen themselves.
 */
final class Safety {

    /** The hero shows at most a third of its health. */
    static final String HP_LOW = "hp-low";

    /** An enemy is in view. */
    static final String ENEMY_IN_VIEW = "enemy-in-view";

    /** The hunger icon shows hungry. */
    static final String HUNGRY = "hungry";

    /** The hunger icon shows starving. */
    static final String STARVING = "starving";

    private Safety() {
    }

    /** The flags that hold on {@code observation}, in a fixed order. */
    static List<String> flags(Observation observation) {
        List<String> flags = new ArrayList<>();
        HeroSection hero = observation.hero();
        if (hero.ht() > 0 && 3L * hero.hp() <= hero.ht()) {
            flags.add(HP_LOW);
        }
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                flags.add(ENEMY_IN_VIEW);
                break;
            }
        }
        if (hero.hunger() == Hunger.HUNGRY) {
            flags.add(HUNGRY);
        } else if (hero.hunger() == Hunger.STARVING) {
            flags.add(STARVING);
        }
        return List.copyOf(flags);
    }
}
