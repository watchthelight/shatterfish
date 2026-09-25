package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Drink a potion of healing before the enemies in view take the hero's last hit points (story 4.9,
 * FR-31).
 *
 * <p><b>The threshold is the visible threat, not a constant.</b> From story 4.7's threat estimate
 * ({@link Fight}): of the enemies in view, the {@code k} that can engage the hero's cell at once
 * ({@link Fight#engage}); {@code perTurn}, the damage those are expected to deal a turn; {@code
 * worst}, their largest rolls less the smallest roll of the hero's armour; and {@code H}, the turns
 * the hero expects to need to kill the enemy it kills soonest, between 1 and {@link #HORIZON}. The
 * danger is {@code max(worst, ceil(perTurn * H))}: what the hero is expected to lose before the
 * fight is over, and never less than one bad turn. The Policy drinks when the hit points are at or
 * under it. With no enemy in view the danger is 0.
 *
 * <p>The potion heals {@code (int)(0.8 * HT + 14)} over several turns, a quarter of what is left each
 * turn, after the hero acts (PotionOfHealing.java:58-69, Healing.java:51-84), and drinking takes a
 * turn (Potion.java:89, :288-292). So the Policy drinks only when the hero is missing at least the
 * first turn's quarter, and not again within {@link #HEALING} waits of a drink it handed over: the
 * heal buff draws no icon (Buff.java:94-96; Healing.java does not override it), so the screen does
 * not say one is running, and a second potion adds only what exceeds the first's remainder
 * (Healing.java:97-100).
 *
 * <p>A potion counts only when the screen shows it identified by name. An unidentified potion is
 * story 4.10's to test.
 *
 * <p>The Policy stands above {@link Fight}: when the hero is expected to die before the fight is
 * over, the fight's own best move is worse than the drink, and the fight Policy has no drink among its
 * moves.
 */
final class Heal implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "heal";

    /** The potion's name once identified (items.properties:759). */
    static final String POTION = "potion of healing";

    /** The most turns of fighting the danger counts: the estimate is a mean, and it drifts beyond. */
    static final int HORIZON = 10;

    /** Waits after a drink before another: the heal lands over these (Healing.java:76-79). */
    static final int HEALING = 5;

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
                && (memory.drank() < 0 || memory.waits() - memory.drank() >= HEALING)
                && !Fight.enemies(observation).isEmpty();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        HeroSection hero = observation.hero();
        int danger = danger(observation, knowledge);
        if (hero.hp() > danger || hero.ht() - hero.hp() < firstTurn(hero.ht())) {
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
     * The hit points the enemies in view are expected to take from the hero before the fight is
     * over, and never less than their worst single turn; 0 with none in view. See the class comment.
     */
    static int danger(Observation observation, Codex.Knowledge knowledge) {
        List<ActorView> enemies = Fight.enemies(observation);
        if (enemies.isEmpty()) {
            return 0;
        }
        int[] armour = Fight.heroArmour(observation, knowledge);
        double soonest = Double.POSITIVE_INFINITY;
        List<Double> expected = new ArrayList<>();
        List<Integer> worst = new ArrayList<>();
        for (ActorView enemy : enemies) {
            Codex.Threat threat = Fight.threat(observation, knowledge, enemy);
            soonest = Math.min(soonest, Fight.turnsToKill(observation, knowledge, enemy));
            expected.add(Fight.enemyDamage(observation, knowledge, threat));
            worst.add(Math.max(0, threat.damageMax() - armour[0]));
        }
        expected.sort((a, b) -> Double.compare(b, a));
        worst.sort((a, b) -> Integer.compare(b, a));
        int engaging = Math.min(enemies.size(), Math.max(1, Fight.engage(observation.map(), observation.hero().cell())));
        double perTurn = 0;
        int worstTurn = 0;
        for (int i = 0; i < engaging; i++) {
            perTurn += expected.get(i);
            worstTurn += worst.get(i);
        }
        double turns = Double.isInfinite(soonest) ? HORIZON : Math.max(1, Math.min(HORIZON, Math.ceil(soonest)));
        return Math.max(worstTurn, (int) Math.ceil(perTurn * turns));
    }
}
