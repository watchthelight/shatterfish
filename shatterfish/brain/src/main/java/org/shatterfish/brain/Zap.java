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
 *
 * <p><b>The Cleric's tome (issue #376).</b> The Cleric starts wearing a holy tome with three charges, shown on
 * its slot as {@code cur/max}, and no other Policy ever opened it ({@code HeroClass.java:248-261},
 * {@code HolyTome.java:57-59}). Its Guiding Light is a bolt for 2 to 8 that costs one charge and one turn
 * ({@code GuidingLight.java:84-96}, {@code :111-118}), against the cudgel's swing, and it leaves the enemy
 * illuminated: the Cleric's next blow on it cannot miss, because an illuminated mob has no defence against the
 * Cleric ({@code Mob.java:822-832}, {@code Char.java:423-424}). A charge comes back about every forty turns while
 * the tome is not full ({@code HolyTome.java:289-296}), so a charge spent at the cap costs nothing. The same
 * conditions apply with {@code UseItemAt(tome, CAST, enemy cell)}, which the executor plays as a person does: the
 * spell button for Guiding Light, then the cell. The staff is looked for first, the tome second.
 *
 * <p><b>The Rogue's cloak (issue #373).</b> The Rogue starts wearing a cloak of shadows with three charges and a dagger
 * ({@code HeroClass.java:204-219}), and no Policy ever put the cloak on. Stealth takes one turn and one charge
 * ({@code CloakOfShadows.java:89-103}, {@code :320-325}) and makes the hero invisible: a mob cannot see an invisible
 * hero, so it cannot attack it ({@code Mob.java:289-291}, {@code :1365-1367}), and the hero's next blow hits whatever its
 * accuracy ({@code Char.java:627-629}) and, with the dagger, rolls from three quarters of the way to its top damage up
 * to the top ({@code Dagger.java:62-79}, {@code Mob.java:912-916}). The blow ends the invisibility without a further charge
 * while the cloak has not yet billed its next four turns ({@code Hero.java:2389}, {@code :486}, {@code Invisibility.java:90-98},
 * {@code CloakOfShadows.java:360-365}). So when the class has a reach and an awake enemy that one blow would not kill
 * stands within it, this Policy puts the cloak on, and the fight Policy strikes from the shadow. A charge returns about
 * every forty turns ({@code CloakOfShadows.java:230-244}). The cloak shows its charges as {@code cur/max} and the hero
 * shows the {@code cloaked} buff while it is on, which is what keeps the Policy from taking it off again
 * ({@code CloakOfShadows.java:111-116}).
 */
final class Zap implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "zap";

    /** The item action that zaps (MagesStaff.java:67). */
    static final String ZAP = "ZAP";

    /** The item action that casts the tome's Guiding Light at a cell (HolyTome.java:67; the executor casts Guiding Light at the cell). */
    static final String CAST = "CAST";

    /** The item action that puts the Rogue's cloak of shadows on (CloakOfShadows.java:68). */
    static final String STEALTH = "STEALTH";

    /** The buff the hero shows while the cloak is on (items.properties, cloakofshadows$cloakstealth.name). */
    static final String CLOAKED = "cloaked";

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
                && !Fight.enemies(observation, knowledge).isEmpty() && (caster(observation) >= 0 || cloak(observation) >= 0);
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        int reach = playbook.kit(observation.header().heroClass()).zapReach();
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        int index = caster(observation);
        int cloak = index < 0 ? cloak(observation) : -1;
        if (reach <= 0 || (index < 0 && cloak < 0) || enemies.isEmpty() || enemies.stream().anyMatch(enemy -> Goo.NAME.equals(enemy.name()))) {
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
        if (cloak >= 0) {
            return target == null || best > reach || Fight.dies(observation, knowledge, target) || "UseItem".equals(memory.last())
                    ? null : stealth(observation, offered, cloak);
        }
        if (target == null || best > reach || !Ranged.clear(observation, hero, target.cell())) {
            return null;
        }
        return zapAt(observation, memory, offered, target, "zap: ");
    }

    /**
     * The zap at {@code target} from the worn staff of magic missile, or null when it shows no charge, the last
     * zap had no effect, or the screen does not offer it; the reason is {@code why} and the staff's name. Shared
     * with the Goo fight ({@link Goo#volley}, story 10.38).
     */
    static RunLog.Choice zapAt(Observation observation, Memory memory, List<Action> offered, ActorView target, String why) {
        int index = caster(observation);
        if (index < 0 || zappedWithoutEffect(memory)) {
            return null;
        }
        ItemView item = observation.inventory().items().get(index);
        String action = item.slot() == EquipSlot.ARTIFACT ? CAST : ZAP;
        Action zap = new Action.UseItemAt(new ItemRef(index, item.name(), item.quantity()), action, target.cell());
        return offered.contains(zap) ? new RunLog.Choice(zap, Policies.CERTAIN, why + item.name()) : null;
    }

    /** The cloak put on, or null when the screen does not offer it. */
    private static RunLog.Choice stealth(Observation observation, List<Action> offered, int index) {
        ItemView item = observation.inventory().items().get(index);
        Action put = new Action.UseItem(new ItemRef(index, item.name(), item.quantity()), STEALTH);
        return offered.contains(put) ? new RunLog.Choice(put, Policies.CERTAIN, "cloak: " + item.name()) : null;
    }

    /**
     * The index in the pack of the worn cloak of shadows that shows a charge while the hero is not cloaked, or -1. The
     * action is a toggle ({@code CloakOfShadows.java:105-112}), so a cloak already on is never offered again here.
     */
    static int cloak(Observation observation) {
        for (org.shatterfish.api.BuffView buff : observation.hero().buffs()) {
            if (CLOAKED.equalsIgnoreCase(buff.name())) {
                return -1;
            }
        }
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.slot() == EquipSlot.ARTIFACT && item.name().contains("cloak of shadows")
                    && item.actions().contains(STEALTH) && charged(item.status())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Whether the last wait was a zap and the nearest enemy stands as far off as it did then. A bolt the
     * game refuses (its line blocked at the hero's own side, {@code Wand.java:682-709}) passes no turn and
     * leaves the screen as it was, so the same zap again is a Run that never moves on; the wait is left
     * to the other Policies, and the next zap is tried after them. A bolt that lands leaves the enemy
     * nearer, or dead, so this costs a working zap one wait only against an enemy that stands still.
     */
    static boolean zappedWithoutEffect(Memory memory) {
        return "UseItemAt".equals(memory.last()) && memory.near() >= 0 && memory.near() == memory.before();
    }

    /**
     * The index in the pack of what this Policy shoots with: the worn staff of magic missile when there is one, else
     * the Cleric's worn holy tome that shows a charge (issue #376).
     */
    static int caster(Observation observation) {
        int staff = staff(observation);
        return staff >= 0 ? staff : tome(observation);
    }

    /**
     * The index in the pack of the worn holy tome that shows a charge, or -1. The tome is the Cleric's artifact
     * ({@code HeroClass.java:253-257}); it offers CAST only while worn and uncursed ({@code HolyTome.java:70-78})
     * and shows {@code cur/max} charges ({@code Artifact.java:189-211}), three at the start
     * ({@code HolyTome.java:57-59}).
     */
    static int tome(Observation observation) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.slot() == EquipSlot.ARTIFACT && item.name().contains("holy tome")
                    && item.actions().contains(CAST) && charged(item.status()) && onlyGuidingLight(observation)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Whether the Cleric holds no spell but the first tier's three, so that a cast at a cell can only mean Guiding Light. A point
     * in Shield of Light (a bolt at a cell too, {@code ClericSpell.java:109-117}) or in any talent of the second tier or
     * above adds spells ({@code ClericSpell.java:118-206}), and the Action {@code UseItemAt(tome, CAST, cell)} does not
     * name which one it casts, so the Policy leaves the tome alone then and the executor refuses the cast.
     */
    static boolean onlyGuidingLight(Observation observation) {
        for (org.shatterfish.api.TalentView talent : observation.hero().talents()) {
            if (talent.points() > 0 && (talent.tier() >= 2 || talent.name().equalsIgnoreCase("shield of light"))) {
                return false;
            }
        }
        return true;
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
