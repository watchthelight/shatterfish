package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drink a potion of healing before the enemies in view take the hero's last hit points (story 4.9,
 * FR-31).
 *
 * <p><b>The threshold is the visible threat, not a constant.</b> From story 4.7's threat estimate
 * ({@link Fight}), over the enemies that <em>threaten</em> the hero: awake (no sleep icon over them,
 * docs/rules/combat.md) and able to reach it, by the cells the hero may walk on, within {@code 1 + H}
 * steps. {@code H} is the turns the hero expects to need to kill the enemy it kills soonest, between 1
 * and {@link #HORIZON}. Of those, the {@code k} that can engage the hero's cell at once
 * ({@link Fight#engage}); {@code perTurn}, the damage they are expected to deal a turn; {@code worst},
 * their largest rolls less the smallest roll of the hero's armour. The danger is
 * {@code max(worst, ceil(perTurn * H))}: what the hero is expected to lose before the fight is over,
 * and never less than one bad turn. The Policy drinks when the hit points are at or under it. With no
 * enemy threatening, the danger is 0.
 *
 * <p><b>Only when drinking is better than the fight's own move.</b> When the fight is not favourable
 * and the fight Policy has a retreat, the retreat takes the wait: a drink would buy a few turns of the
 * same unwinnable fight, and the potion is gone. The Policy drinks when the hero is cornered (no
 * retreat), or when the fight is favourable and the hit points are still at the danger.
 *
 * <p>The potion heals {@code (int)(0.8 * HT + 14)} over several turns, a quarter of what is left each
 * turn and at least 1, after the hero acts (PotionOfHealing.java:58-69, Healing.java:51-84), and
 * drinking takes a turn (Potion.java:89, :288-292). So the Policy drinks only when the hero is missing
 * at least the first turn's quarter, and not again until the first potion's heal would be spent
 * ({@link #healingTurns}): a second heal replaces what is left of the first rather than adding to it
 * (Healing.java:97-98). The heal draws no buff icon (Buff.java:94-96; Healing.java does not override
 * it); the game does show a floating heal number each turn and the sprite's healing state
 * (Healing.java:61, :107-111), but neither is in the Observation, so the Memory counts the waits since
 * the drink the Brain handed over.
 *
 * <p>A potion counts only when the screen shows it identified by name. An unidentified potion is
 * story 4.10's to test.
 *
 * <p>The Policy stands above {@link Fight}: the fight Policy has no drink among its moves.
 */
final class Heal implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "heal";

    /** The potion's name once identified (items.properties:759). */
    static final String POTION = "potion of healing";

    /** The most turns of fighting the danger counts: the estimate is a mean, and it drifts beyond. */
    static final int HORIZON = 10;

    private final Codex.Knowledge knowledge;

    Heal(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "heal: threat";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        HeroSection hero = observation.hero();
        return observation.header().prompt() == PromptKind.NONE && hero.hp() < hero.ht()
                && (memory.drank() < 0 || memory.waits() - memory.drank() >= healingTurns(hero.ht()))
                && !Fight.enemies(observation).isEmpty();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        HeroSection hero = observation.hero();
        int danger = danger(observation, knowledge, memory);
        if (hero.hp() > danger || hero.ht() - hero.hp() < firstTurn(hero.ht())) {
            return null;
        }
        List<ActorView> enemies = Fight.enemies(observation);
        if (!Fight.favourable(observation, knowledge, enemies)
                && Fight.retreat(observation, memory, offered, enemies) != null) {
            return null;
        }
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (!item.name().equals(POTION)) {
                continue;
            }
            Action drink = new Action.UseItem(new ItemRef(index, item.name(), item.quantity()), "DRINK");
            if (offered.contains(drink)) {
                return new RunLog.Choice(drink, Policies.CERTAIN, "heal " + hero.hp() + "/" + danger);
            }
        }
        return null;
    }

    /** What the potion heals in all: {@code (int)(0.8 * HT + 14)} (PotionOfHealing.java:64). */
    static int heals(int ht) {
        return (int) (0.8f * ht + 14);
    }

    /** What it heals on its first turn: a quarter of it, rounded, at least 1 (Healing.java:76-79). */
    static int firstTurn(int ht) {
        return Math.max(1, Math.round(heals(ht) * 0.25f));
    }

    /**
     * The turns the potion's heal takes to be spent: each turn heals a quarter of what is left,
     * rounded, at least 1 (Healing.java:62, :76-79). Eleven for a hero of 20 hit points.
     */
    static int healingTurns(int ht) {
        int left = heals(ht);
        int turns = 0;
        while (left > 0) {
            left -= Math.max(1, Math.min(left, Math.round(left * 0.25f)));
            turns++;
        }
        return turns;
    }

    /**
     * The hit points the enemies threatening the hero are expected to take before the fight is over,
     * and never less than their worst single turn; 0 with none. See the class comment.
     */
    static int danger(Observation observation, Codex.Knowledge knowledge, Memory memory) {
        List<ActorView> awake = new ArrayList<>();
        for (ActorView enemy : Fight.enemies(observation)) {
            if (enemy.emote() != Emote.SLEEP) {
                awake.add(enemy);
            }
        }
        if (awake.isEmpty()) {
            return 0;
        }
        double soonest = Double.POSITIVE_INFINITY;
        for (ActorView enemy : awake) {
            soonest = Math.min(soonest, Fight.turnsToKill(observation, knowledge, enemy));
        }
        int turns = Double.isInfinite(soonest) ? HORIZON : (int) Math.max(1, Math.min(HORIZON, Math.ceil(soonest)));
        int[] steps = steps(observation, memory);
        int[] armour = Fight.heroArmour(observation, knowledge);
        List<Double> expected = new ArrayList<>();
        List<Integer> worst = new ArrayList<>();
        for (ActorView enemy : awake) {
            int reach = steps[enemy.cell()];
            if (reach < 0 || reach > turns + 1) {
                continue;
            }
            Codex.Threat threat = Fight.threat(observation, knowledge, enemy);
            expected.add(Fight.enemyDamage(observation, knowledge, threat));
            worst.add(Math.max(0, threat.damageMax() - armour[0]));
        }
        if (expected.isEmpty()) {
            return 0;
        }
        expected.sort((a, b) -> Double.compare(b, a));
        worst.sort((a, b) -> Integer.compare(b, a));
        int engaging = Math.min(expected.size(), Math.max(1, Fight.engage(observation.map(), observation.hero().cell())));
        double perTurn = 0;
        int worstTurn = 0;
        for (int i = 0; i < engaging; i++) {
            perTurn += expected.get(i);
            worstTurn += worst.get(i);
        }
        return Math.max(worstTurn, (int) Math.ceil(perTurn * turns));
    }

    /**
     * Steps from the hero to every cell, over the cells the hero may walk on and the cells enemies
     * stand on, eight ways; -1 where no such path reaches. An enemy {@code d} steps away reaches the
     * hero's side in {@code d - 1}.
     */
    static int[] steps(Observation observation, Memory memory) {
        MapSection map = observation.map();
        boolean[] walk = Explore.walkable(observation, memory, false);
        for (ActorView actor : observation.actors().actors()) {
            walk[actor.cell()] = true;
        }
        int[] distance = new int[walk.length];
        Arrays.fill(distance, -1);
        int hero = observation.hero().cell();
        distance[hero] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(hero);
        int width = map.width();
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = cell % width + dx;
                    int ny = cell / width + dy;
                    if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= width || ny >= map.height()) {
                        continue;
                    }
                    int next = nx + ny * width;
                    if (walk[next] && distance[next] < 0) {
                        distance[next] = distance[cell] + 1;
                        queue.add(next);
                    }
                }
            }
        }
        return distance;
    }
}
