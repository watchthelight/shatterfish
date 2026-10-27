package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Pause-on conditions as a pure function (story 8.3, FR-45): given the enabled conditions, the
 * Observation at the Input wait and the Decision about to be executed, which condition holds, if any.
 *
 * <p>Information parity (non-negotiable 1): the function reads the {@link Observation}, the
 * {@link RunLog.Decision} and {@link Previous}, which is itself drawn from the Observation of the wait before.
 * Nothing else reaches it. An unknown item is one the screen shows as unidentified (a potion, scroll or ring
 * whose name is not in the journal's identified list, or a weapon or armour whose level is not shown); a boss
 * floor is a depth of the main branch that the Codex names a boss depth ({@link BossFloors}); a new enemy is a
 * visible hostile actor of a name more of which are in view than at the previous wait.
 *
 * <p><b>The fixed order</b>, which is FR-45's: {@link Condition#ITEM_USE}, {@link Condition#UNKNOWN_ITEM},
 * {@link Condition#STAIRS}, {@link Condition#BOSS_FLOOR}, {@link Condition#HP_BELOW},
 * {@link Condition#NEW_ENEMY}. When two hold at once the first of these is reported.
 *
 * <p><b>Edges, not levels.</b> A condition about the state of the Run (a boss floor, low hit points) would
 * hold at every wait of that stretch and stop the Run at each, so those two fire when the state is entered:
 * the first wait on a boss floor, and the first wait at which the hit points are under the threshold after a
 * wait at which they were not. With no previous wait (the settings were changed mid-Run, or this is the first
 * wait) a state that holds counts as entered, and nothing counts as a new enemy, since there is nothing to be
 * new against.
 */
final class PauseOn {

    /** FR-45's six conditions, in the order they are reported. */
    enum Condition {
        ITEM_USE("before item use"),
        UNKNOWN_ITEM("before testing an unknown item"),
        STAIRS("before stairs"),
        BOSS_FLOOR("on a boss floor"),
        HP_BELOW("when HP is low"),
        NEW_ENEMY("when a new enemy appears");

        private final String label;

        Condition(String label) {
            this.label = label;
        }

        /** The condition in words, as the Mode strip and the settings screen name it. */
        String label() {
            return label;
        }
    }

    /**
     * What the wait before showed, for the conditions that compare: the floor, the hostile actors by name, and
     * the hit points. Made from an Observation and kept by the caller, one wait at a time.
     */
    record Previous(int depth, int branch, Map<String, Integer> hostiles, int hp, int ht) {

        Previous {
            hostiles = Map.copyOf(hostiles);
        }

        /** What {@code observation} shows, to compare the next wait against. */
        static Previous of(Observation observation) {
            Map<String, Integer> hostiles = new HashMap<>();
            for (ActorView actor : observation.actors().actors()) {
                if (actor.alignment() == Alignment.ENEMY) {
                    hostiles.merge(actor.name(), 1, Integer::sum);
                }
            }
            return new Previous(observation.header().depth(), observation.header().branch(), hostiles,
                    observation.hero().hp(), observation.hero().ht());
        }
    }

    private PauseOn() {
    }

    /**
     * The first condition that holds, or empty. {@code previous} is null when there is no wait before to compare
     * with; {@code decision} is null when the Run has none (a Run whose agent does not explain itself), and then
     * only the conditions about the screen can hold.
     */
    static Optional<Condition> first(OverlaySettings settings, Observation observation, RunLog.Decision decision,
                                     Previous previous, Set<Integer> bossDepths) {
        for (Condition condition : Condition.values()) {
            if (settings.pauseOn().contains(condition)
                    && holds(condition, settings, observation, decision, previous, bossDepths)) {
                return Optional.of(condition);
            }
        }
        return Optional.empty();
    }

    /** The condition named with the threshold it was set to, as the Mode strip prints it. */
    static String label(Condition condition, OverlaySettings settings) {
        return condition == Condition.HP_BELOW ? "HP below " + settings.hpThresholdPercent() + "%" : condition.label();
    }

    private static boolean holds(Condition condition, OverlaySettings settings, Observation observation,
                                 RunLog.Decision decision, Previous previous, Set<Integer> bossDepths) {
        Action action = decision == null ? null : decision.chosen().action();
        return switch (condition) {
            case ITEM_USE -> usesItem(action);
            case UNKNOWN_ITEM -> usesUnknownItem(action, observation);
            case STAIRS -> action instanceof Action.Descend || action instanceof Action.Ascend;
            case BOSS_FLOOR -> enteredBossFloor(observation, previous, bossDepths);
            case HP_BELOW -> droppedBelow(observation, previous, settings.hpThresholdPercent());
            case NEW_ENEMY -> newEnemy(observation, previous);
        };
    }

    private static boolean usesItem(Action action) {
        return action instanceof Action.UseItem || action instanceof Action.UseItemAt
                || action instanceof Action.UseItemOn;
    }

    private static boolean usesUnknownItem(Action action, Observation observation) {
        ItemRef item;
        ItemRef target = null;
        if (action instanceof Action.UseItem use) {
            item = use.item();
        } else if (action instanceof Action.UseItemAt use) {
            item = use.item();
        } else if (action instanceof Action.UseItemOn use) {
            item = use.item();
            target = use.target();
        } else {
            return false;
        }
        return unknown(item, observation) || (target != null && unknown(target, observation));
    }

    private static boolean unknown(ItemRef ref, Observation observation) {
        if (ref.index() >= observation.inventory().items().size()) {
            return false;
        }
        ItemView item = observation.inventory().items().get(ref.index());
        return switch (item.kind()) {
            case POTION, SCROLL, RING -> !identified(observation, item.kind(), item.name());
            case WEAPON, ARMOR -> !item.levelKnown();
            default -> false;
        };
    }

    private static boolean identified(Observation observation, ItemKind kind, String name) {
        for (KnownAppearance known : observation.journal().known()) {
            if (known.kind() == kind && known.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean enteredBossFloor(Observation observation, Previous previous, Set<Integer> bossDepths) {
        int depth = observation.header().depth();
        boolean boss = observation.header().branch() == 0 && bossDepths.contains(depth);
        boolean entered = previous == null || previous.depth() != depth
                || previous.branch() != observation.header().branch();
        return boss && entered;
    }

    private static boolean droppedBelow(Observation observation, Previous previous, int percent) {
        boolean low = below(observation.hero().hp(), observation.hero().ht(), percent);
        return low && (previous == null || !below(previous.hp(), previous.ht(), percent));
    }

    private static boolean below(int hp, int ht, int percent) {
        return (long) hp * 100 < (long) percent * ht;
    }

    private static boolean newEnemy(Observation observation, Previous previous) {
        if (previous == null) {
            return false;
        }
        Map<String, Integer> now = Previous.of(observation).hostiles();
        for (Map.Entry<String, Integer> seen : now.entrySet()) {
            if (seen.getValue() > previous.hostiles().getOrDefault(seen.getKey(), 0)) {
                return true;
            }
        }
        return false;
    }
}
