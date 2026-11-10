package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Heuristic;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tunables;

import java.util.List;

/**
 * Drink the waterskin (story 10.51).
 *
 * <p>A death audit of 240 Runs found 106 of 114 losing heroes holding dew in the waterskin and none ever drinking it.
 * A drink takes the drops that bring the hero to full health, at least one and at most the skin holds, each healing 5%
 * of the maximum, and costs one turn (Waterskin.java:87-134, Dewdrop.java:83-84). The skin is a renewable heal the
 * hero walks into, so it is spent before a potion and before a gamble on an unknown one. The skin's fill is on the
 * screen ({@link Dew#volume}); nothing here reads more than a person at the screen sees.
 *
 * <p>Two cases, by whether a fight has begun ({@link Calm#unengaged}).
 * <ul>
 *   <li><b>In a fight</b>, with the skin's gain at least {@link #MIN_GAIN_PER_MILLE} of the maximum and more than the
 *   damage the enemies within two cells deal in the turn the drink takes: when the hit points are at the heal
 *   Policy's danger, or under {@link #FIGHT_BELOW_PER_MILLE} of the maximum. Not when the heal Policy would rather
 *   retreat (the same test it makes before a potion), and not against Goo while a pump can be dodged.</li>
 *   <li><b>On a calm screen</b>, hurt under {@link #CALM_BELOW_PER_MILLE} of the maximum with the same least gain: a
 *   drink is one turn, a rest is many turns of hunger (Regeneration.java:44).</li>
 * </ul>
 * The descent rest drinks the skin before it rests ({@link Descend}), so the hero arrives on the next floor, the Goo
 * floor above all, at full health without the hunger a long rest costs.
 */
final class Drink implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "drink";

    /**
     * The least a drink must heal, in thousandths of the maximum hit points, to be worth the turn and the dew. An
     * assumption, tuned by the rig.
     */
    @Heuristic("drink-waterskin")
    static final int MIN_GAIN_PER_MILLE = 150;

    /** In a fight the skin is drunk once the hit points are under this share of the maximum, thousandths. */
    @Heuristic("drink-waterskin")
    static final int FIGHT_BELOW_PER_MILLE = 400;

    /** On a calm screen the skin is drunk once the hit points are under this share of the maximum, thousandths. */
    @Heuristic("drink-waterskin")
    static final int CALM_BELOW_PER_MILLE = 600;

    private final Codex.Knowledge knowledge;
    private final org.shatterfish.api.Playbook.Goo goo;
    private final org.shatterfish.api.Playbook.Tengu tengu;
    private final Tunables.Vector tunables;

    Drink(Codex.Knowledge knowledge, org.shatterfish.api.Playbook.Goo goo, org.shatterfish.api.Playbook.Tengu tengu,
          Tunables.Vector tunables) {
        this.knowledge = knowledge;
        this.goo = goo;
        this.tengu = tengu;
        this.tunables = tunables;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "drink: waterskin";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        HeroSection hero = observation.hero();
        return observation.header().prompt() == PromptKind.NONE && hero.hp() < hero.ht() && Dew.volume(observation) > 0;
    }

    /** Whether a drink that heals {@code gain} is worth its turn, by the vector's least share. */
    static boolean worth(HeroSection hero, int gain, Tunables.Vector tunables) {
        return gain > 0 && 1000L * gain >= (long) tunables.getInt(Tunables.DRINK_MIN_GAIN) * hero.ht();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        HeroSection hero = observation.hero();
        int gain = Dew.gain(observation);
        if (!worth(hero, gain, tunables)) {
            return null;
        }
        Action drink = Dew.drink(observation, offered);
        if (drink == null) {
            return null;
        }
        String why = "drink " + hero.hp() + "/" + hero.ht() + " +" + gain;
        if (Calm.unengaged(observation, knowledge)) {
            return 1000L * hero.hp() < (long) tunables.getInt(Tunables.DRINK_CALM_BELOW) * hero.ht()
                    ? new RunLog.Choice(drink, Policies.CERTAIN, why + " calm") : null;
        }
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        double turn = 0;
        for (ActorView enemy : enemies) {
            if (enemy.emote() != Emote.SLEEP && Fight.chebyshev(observation.map(), hero.cell(), enemy.cell()) <= 2) {
                turn += Fight.enemyDamage(observation, knowledge, Fight.threat(observation, knowledge, enemy));
            }
        }
        if (gain <= turn) {
            return null;
        }
        int danger = Heal.danger(observation, knowledge, memory, goo, tengu, tunables);
        if (hero.hp() > danger && 1000L * hero.hp() >= (long) tunables.getInt(Tunables.DRINK_FIGHT_BELOW) * hero.ht()) {
            return null;
        }
        boolean low = 1000L * hero.hp() < 334L * hero.ht();
        boolean gooAlone = Goo.alone(enemies) != null;
        boolean tenguAlone = Tengu.alone(observation, enemies) != null;
        if (gooAlone && Goo.dodge(observation, memory, offered) != null) {
            return null;
        }
        if (!gooAlone && !tenguAlone && !low && !Fight.favourable(observation, knowledge, enemies, tunables)
                && Fight.retreat(observation, memory, offered, enemies, knowledge, tunables) != null) {
            return null;
        }
        return new RunLog.Choice(drink, Policies.CERTAIN, why + " fight");
    }
}
