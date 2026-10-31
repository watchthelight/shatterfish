package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * Shoot the Huntress's Spirit Bow at an enemy in view (issue #309). The Huntress starts with the bow in
 * her pack and gloves in hand ({@code HeroClass.java:221-227}); until this Policy nothing ever fired it,
 * because the Observation offered its {@code SHOOT} as a plain use that opened a cell selector nobody
 * answered, so she fought with her fists.
 *
 * <p><b>Why it pays.</b> A shot is a missile hit from where the hero stands: it takes one turn, rolls the
 * bow's own damage ({@code SpiritBow.java:189-203}, 1 to 6 at the first level), and from a cell that is
 * not adjacent it hits one and a half times as often as it would beside the enemy
 * ({@code MissileWeapon.java:215-233}). The arrow is not a pack item that runs out: the bow knocks a
 * fresh {@code SpiritArrow} for each shot ({@code SpiritBow.java:289-291}). An enemy walking up to the
 * hero takes a shot for each cell of the way.
 *
 * <p><b>What it takes.</b> No Prompt is open; the nearest hostile in view that is awake and mobile is at
 * least two cells away; none is adjacent; no Goo is among the enemies (the fight Policy dodges its pump);
 * the fight is {@link Fight#favourable} (otherwise the fight Policy retreats, which a shot would only
 * delay); and the line to it is clear of walls and of other characters. The bow is whatever pack item
 * offers {@code SHOOT}. It is a Choice of one: {@code UseItemAt(bow, SHOOT, enemy cell)}, which {@link
 * org.shatterfish.api.ValidActions} offers for every character in view. No reach is stated: the shot
 * flies as far as the hero sees, so there is no threshold to tune.
 */
final class Shoot implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "shoot";

    /** The item action that shoots (SpiritBow.java:60). */
    static final String SHOOT = "SHOOT";

    private final Codex.Knowledge knowledge;
    private final org.shatterfish.api.Tunables.Vector tunables;

    Shoot(Codex.Knowledge knowledge) {
        this(knowledge, org.shatterfish.api.Tunables.Vector.REFERENCE);
    }

    /** {@link #Shoot(Codex.Knowledge)} judging a fight by the vector's {@code fight.margin_per_mille} (story 10.35). */
    Shoot(Codex.Knowledge knowledge, org.shatterfish.api.Tunables.Vector tunables) {
        this.knowledge = knowledge;
        this.tunables = tunables;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "shoot: bow";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && bow(observation) >= 0
                && !Fight.enemies(observation, knowledge).isEmpty();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        int index = bow(observation);
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        if (index < 0 || enemies.isEmpty() || enemies.stream().anyMatch(enemy -> Goo.NAME.equals(enemy.name()))) {
            return null;
        }
        int hero = observation.hero().cell();
        ActorView target = null;
        int best = Integer.MAX_VALUE;
        for (ActorView enemy : enemies) {
            int distance = Fight.chebyshev(observation.map(), hero, enemy.cell());
            if (distance <= 1) {
                return null;
            }
            if (enemy.emote() == Emote.SLEEP || Bestiary.immobile(knowledge, observation.header().depth(),
                    observation.header().branch(), enemy.name())) {
                continue;
            }
            if (distance < best || (distance == best && enemy.cell() < target.cell())) {
                best = distance;
                target = enemy;
            }
        }
        if (target == null || !Fight.favourable(observation, knowledge, enemies, tunables)
                || !Ranged.clear(observation, hero, target.cell())) {
            return null;
        }
        return shotAt(observation, offered, target, "shoot: ");
    }

    /**
     * The shot at {@code target} from the pack's bow, or null when there is no bow or the screen does not offer
     * the shot; the reason is {@code why} and the bow's name. Shared with the Goo fight ({@link Goo#volley}), which
     * shoots an awake Goo that is coming and not yet beside the hero (story 10.38).
     */
    static RunLog.Choice shotAt(Observation observation, List<Action> offered, ActorView target, String why) {
        int index = bow(observation);
        if (index < 0) {
            return null;
        }
        ItemView bow = observation.inventory().items().get(index);
        Action shot = new Action.UseItemAt(new ItemRef(index, bow.name(), bow.quantity()), SHOOT, target.cell());
        return offered.contains(shot) ? new RunLog.Choice(shot, Policies.CERTAIN, why + bow.name()) : null;
    }

    /** The index in the pack of the item that offers {@code SHOOT}, or -1. */
    static int bow(Observation observation) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).actions().contains(SHOOT)) {
                return index;
            }
        }
        return -1;
    }
}
