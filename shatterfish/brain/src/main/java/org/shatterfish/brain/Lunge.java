package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Heuristic;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * The Duelist's lunge (issue #375). The Duelist starts wearing a rapier, and its weapon ability costs a charge
 * the weapon shows on its slot as {@code cur/max}: two at the start, one more at every third level, and one
 * regained about every 60 turns while the weapon is not full ({@code MeleeWeapon.java:423}, {@code :428-433},
 * {@code :482-488}). No Policy ever used it, so the Duelist played as a fighter with a rapier.
 *
 * <p><b>Why it pays.</b> The rapier's ability jumps the hero to a free cell beside an enemy exactly two cells off and
 * strikes it at once, in the turn the jump takes ({@code Rapier.java:92-177}); the strike cannot miss
 * ({@code Char.INFINITE_ACCURACY}, {@code Rapier.java:151}) and does 5 more than a blow at level 0
 * ({@code Rapier.java:71-74}): 6 to 13, against the rapier's own 1 to 8 ({@code Rapier.java:55-58}) that can miss.
 * The enemy that walks up to the hero would otherwise take the first blow of the exchange from the hero's wait; the
 * lunge makes that blow a strong one and spends the turn the enemy needed to close. A charge comes back about every
 * 60 turns, so the bank is full again by the next fight on most floors.
 *
 * <p><b>What it takes.</b> No Prompt is open; the worn weapon is a rapier that offers {@code ABILITY} and shows a
 * charge on its status; the nearest awake, mobile enemy in view is exactly two cells away (the game refuses a lunge
 * at any other distance, {@code Rapier.java:106-111}); some Step the screen offers ends beside that enemy
 * ({@code Rapier.java:113-127}); and, against Goo, no pump is pending, which the fight Policy dodges. A lunge the game
 * refuses passes no turn and leaves the screen as it was, so the same lunge again is a Run that never moves on: the
 * Policy then leaves the next wait to the others, as the zap Policy does. The Action is the one a person makes, the
 * weapon's ability button and then the enemy's cell ({@code MeleeWeapon.java:103-146}).
 */
final class Lunge implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "lunge";

    /** The item action that uses a weapon's ability (MeleeWeapon.java:65). */
    static final String ABILITY = "ABILITY";

    /** How far off the enemy stands for a lunge: the rapier reaches one cell, and the jump covers the rest (Rapier.java:106-111). */
    @Heuristic("rapier-lunge")
    static final int REACH = 2;

    private final Codex.Knowledge knowledge;

    Lunge(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "lunge: rapier";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && rapier(observation) >= 0
                && !Fight.enemies(observation, knowledge).isEmpty();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        int index = rapier(observation);
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        if (index < 0 || enemies.isEmpty() || Zap.zappedWithoutEffect(memory)) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        ActorView target = null;
        int best = Integer.MAX_VALUE;
        for (ActorView enemy : enemies) {
            if (enemy.emote() == Emote.SLEEP || Bestiary.immobile(knowledge, observation.header().depth(),
                    observation.header().branch(), enemy.name())) {
                continue;
            }
            int distance = Fight.chebyshev(map, hero, enemy.cell());
            if (distance < best || (distance == best && enemy.cell() < target.cell())) {
                best = distance;
                target = enemy;
            }
        }
        if (target == null || best != REACH || (Goo.NAME.equals(target.name()) && memory.pump() == target.cell())
                || !landing(map, offered, target.cell())) {
            return null;
        }
        ItemView item = observation.inventory().items().get(index);
        Action lunge = new Action.UseItemAt(new ItemRef(index, item.name(), item.quantity()), ABILITY, target.cell());
        return offered.contains(lunge) ? new RunLog.Choice(lunge, Policies.CERTAIN, "lunge: " + item.name()) : null;
    }

    /** Whether a Step the screen offers ends beside {@code target} (Rapier.java:113-127: a free, passable neighbour of the hero). */
    private static boolean landing(MapSection map, List<Action> offered, int target) {
        for (Action action : offered) {
            if (action instanceof Action.Step step && Fight.chebyshev(map, step.cell(), target) <= 1) {
                return true;
            }
        }
        return false;
    }

    /** The index in the pack of the worn rapier that shows a charge and offers its ability, or -1. */
    static int rapier(Observation observation) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.slot() == EquipSlot.WEAPON && item.name().contains("rapier")
                    && item.actions().contains(ABILITY) && Zap.charged(item.status())) {
                return index;
            }
        }
        return -1;
    }
}
