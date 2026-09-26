package org.shatterfish.brain;

import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;

import java.util.List;
import java.util.Map;

/**
 * What the Brain reads of the bestiary's tags ({@link Codex.Tactics}, {@code tactics/bestiary.json}),
 * by the name the screen shows an enemy under and the depth it is on ({@link Codex.Knowledge#tactics}
 * chooses among the classes sharing a name). The tags are general game knowledge derived from the
 * pinned code and cited on each bestiary card, which non-negotiable 1 allows.
 *
 * <p>An enemy the bestiary has no entry for (a Knowledge the caller built without one) answers false
 * to every question here, which is the Brain's behaviour before the bestiary: nothing passive,
 * nothing at range, nothing immobile, nothing it cannot walk away from.
 */
final class Bestiary {

    static final String FAST = "fast";
    static final String NORMAL = "normal";
    static final String IMMOBILE = "immobile";
    static final String PASSIVE = "passive";
    static final String RANGED = "ranged";
    static final String BOLT = "bolt";

    /**
     * Every tag value the Brain compares against, by tag. A test holds each to the api's closed
     * vocabulary, which the rig's {@code BestiaryTest} holds to {@code docs/bestiary/index.md}, so a
     * value renamed there cannot leave a comparison here silently false.
     */
    static final Map<String, List<String>> READS = Map.of(
            "speed", List.of(FAST, NORMAL, IMMOBILE),
            "ai", List.of(PASSIVE),
            "attack", List.of(RANGED, BOLT));

    private Bestiary() {
    }

    /** The entry for the enemy named {@code name} on {@code depth}, or null. */
    static Codex.Tactics of(Codex.Knowledge knowledge, int depth, String name) {
        return knowledge.tactics(name, depth);
    }

    /**
     * Whether the game creates this enemy passive and keeps it so until provoked, while the screen
     * draws it as an enemy: tagged {@code ai: passive}, created as an enemy, and not immobile. That is
     * the animated statue (Statue.java:46-47, woken only by damage, :131-136), the armored statue,
     * which is one, and the gnoll exile, which wanders passive until debuffed (GnollExile.java:49-51,
     * :137-146). A mimic is tagged passive too, but it is created neutral and drawn as its chest while
     * it hides, and once the screen draws it as an enemy it is hunting (docs/rules/visibility.md); the
     * immobile passives (a rot heart, a demon spawner, Yog-Dzewa) are fought where they stand.
     */
    static boolean passive(Codex.Knowledge knowledge, int depth, String name) {
        Codex.Tactics tactics = of(knowledge, depth, name);
        return tactics != null && tactics.ai().equals(PASSIVE) && tactics.alignment() == Alignment.ENEMY
                && !tactics.speed().equals(IMMOBILE);
    }

    /**
     * Whether this enemy can hurt the hero without walking a path the hero could walk: it attacks
     * from where it stands (tagged {@code attack: ranged} or {@code bolt}), or it flies over what the
     * hero cannot cross (tagged {@code flying}).
     */
    static boolean atRange(Codex.Knowledge knowledge, int depth, String name) {
        Codex.Tactics tactics = of(knowledge, depth, name);
        return tactics != null && (tactics.attack().equals(RANGED) || tactics.attack().equals(BOLT) || tactics.flying());
    }

    /** Whether this enemy never walks: tagged {@code speed: immobile}. */
    static boolean immobile(Codex.Knowledge knowledge, int depth, String name) {
        Codex.Tactics tactics = of(knowledge, depth, name);
        return tactics != null && tactics.speed().equals(IMMOBILE);
    }

    /**
     * Whether the hero cannot walk away from this enemy because it is faster: tagged
     * {@code speed: fast} and not {@code outrunnable} (the sewer crab, the vampire bat, the ripper
     * demon; a piranha is fast but bound to its water, so it is outrunnable).
     */
    static boolean faster(Codex.Knowledge knowledge, int depth, String name) {
        Codex.Tactics tactics = of(knowledge, depth, name);
        return tactics != null && tactics.speed().equals(FAST) && !tactics.outrunnable();
    }

    /**
     * Whether the hero cannot gain ground on this enemy because it walks as fast: tagged
     * {@code speed: normal} and not {@code outrunnable}.
     */
    static boolean abreast(Codex.Knowledge knowledge, int depth, String name) {
        Codex.Tactics tactics = of(knowledge, depth, name);
        return tactics != null && tactics.speed().equals(NORMAL) && !tactics.outrunnable();
    }
}
