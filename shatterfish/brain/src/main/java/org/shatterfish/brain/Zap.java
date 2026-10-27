package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * Zap the Mage's staff at an enemy in view (story 7.6, FR-35). The Mage starts with a staff holding a
 * wand of magic missile ({@code HeroClass.java:190-196}), and no other Policy ever uses it: until this
 * one the staff was only ever swung as a club.
 *
 * <p><b>Why it pays.</b> A zap is a bolt that hits the first character on its line for 2 to 8 at level 0
 * ({@code WandOfMagicMissile.java:47-53}), against the staff's own swing of 1 to 6 ({@code
 * MeleeWeapon.java:250-259}, {@code MagesStaff.java:89-93}); it takes one turn ({@code Wand.java:80}),
 * costs one charge ({@code Wand.java:437-439}, {@code :489}), a zap without one fizzles ({@code Wand.java:158-162}), and a wand of magic missile starts with three, full ({@code WandOfMagicMissile.java:91-93}), and
 * gains them back as the hero plays ({@code Wand.java:801-875}). An enemy walking up to the hero takes a
 * bolt for each cell of the way.
 *
 * <p><b>What it takes.</b> The class's {@code kit.zap_reach} in the Playbook is above zero; no Prompt is
 * open; the nearest hostile in view that is awake and mobile is at most that far away; no Goo is among
 * the enemies (the fight Policy dodges its pump); the line to it is clear of walls and of other
 * characters; and the worn staff names a wand of magic missile and shows a charge on its status
 * ({@code MagesStaff.java:332-335}, {@code Wand.java:337-344}: {@code cur/max}). It is a Choice of one:
 * {@code UseItemAt(staff, ZAP, enemy cell)}, which {@link org.shatterfish.api.ValidActions} offers for
 * every character in view. A staff holding another wand is left to the swing: the damage this Policy
 * stands on is magic missile's.
 */
final class Zap implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "zap";

    /** The item action that zaps (MagesStaff.java:67). */
    static final String ZAP = "ZAP";

    private final Codex.Knowledge knowledge;
    private final Playbook playbook;

    Zap(Codex.Knowledge knowledge, Playbook playbook) {
        this.knowledge = knowledge;
        this.playbook = playbook;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "zap: staff";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE
                && playbook.kit(observation.header().heroClass()).zapReach() > 0
                && !Fight.enemies(observation, knowledge).isEmpty() && staff(observation) >= 0;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        int reach = playbook.kit(observation.header().heroClass()).zapReach();
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        int index = staff(observation);
        if (reach <= 0 || index < 0 || enemies.isEmpty() || enemies.stream().anyMatch(enemy -> Goo.NAME.equals(enemy.name()))) {
            return null;
        }
        int hero = observation.hero().cell();
        ActorView target = null;
        int best = Integer.MAX_VALUE;
        for (ActorView enemy : enemies) {
            if (enemy.emote() == Emote.SLEEP || Bestiary.immobile(knowledge, observation.header().depth(),
                    observation.header().branch(), enemy.name())) {
                continue;
            }
            int distance = Fight.chebyshev(observation.map(), hero, enemy.cell());
            if (distance < best || (distance == best && enemy.cell() < target.cell())) {
                best = distance;
                target = enemy;
            }
        }
        if (target == null || best > reach || !Ranged.clear(observation, hero, target.cell())) {
            return null;
        }
        if (zappedWithoutEffect(memory)) {
            return null;
        }
        ItemView staff = observation.inventory().items().get(index);
        Action zap = new Action.UseItemAt(new ItemRef(index, staff.name(), staff.quantity()), ZAP, target.cell());
        return offered.contains(zap) ? new RunLog.Choice(zap, Policies.CERTAIN, "zap: " + staff.name()) : null;
    }

    /**
     * Whether the last wait was a zap and the nearest enemy stands as far off as it did then. A bolt the
     * game refuses (its line blocked at the hero's own side, {@code Wand.java:682-709}) passes no turn and
     * leaves the screen as it was, so the same zap again is a Run that never moves on; the wait is left
     * to the other Policies, and the next zap is tried after them. A bolt that lands leaves the enemy
     * nearer, or dead, so this costs a working zap one wait only against an enemy that stands still.
     */
    private static boolean zappedWithoutEffect(Memory memory) {
        return "UseItemAt".equals(memory.last()) && memory.near() >= 0 && memory.near() == memory.before();
    }

    /** The index in the pack of the worn staff of magic missile that shows a charge, or -1. */
    static int staff(Observation observation) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.slot() == EquipSlot.WEAPON && item.name().contains("staff of magic missile")
                    && item.actions().contains(ZAP) && charged(item.status())) {
                return index;
            }
        }
        return -1;
    }

    /** Whether a staff's status text, {@code cur/max} (Wand.java:340), shows at least one charge; {@code ?/max} shows none. */
    static boolean charged(String status) {
        int slash = status.indexOf('/');
        if (slash <= 0 || slash == status.length() - 1) {
            return false;
        }
        for (int i = 0; i < status.length(); i++) {
            if (i != slash && !Character.isDigit(status.charAt(i))) {
                return false;
            }
        }
        return Integer.parseInt(status.substring(0, slash)) > 0;
    }
}
