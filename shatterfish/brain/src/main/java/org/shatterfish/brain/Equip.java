package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * The equip Policy (story 4.8, FR-31): put on a melee weapon or a suit of armour from the pack when
 * the Evaluation prefers it to what is worn.
 *
 * <p>What a piece is worth worn is the Evaluation's {@code weapon} or {@code armor} weight times
 * its mean level-0 roll from the Codex's combat table (damage for a weapon, damage absorbed for
 * armour), cut for the strength it asks beyond the hero's: a weapon's accuracy and an armour's
 * evasion are divided by 1.5 for each point short (Weapon.java:291-303, Armor.java:420-422), which
 * the worth is divided by too. A level the screen shows is not counted: the Codex measured level 0.
 *
 * <p>A piece whose curse is hidden and whose name shows no enchantment or glyph is cursed with
 * {@link SafeTest#curseChance} (a third for a weapon, 0.3/0.85 for armour), and a cursed piece
 * cannot be taken off once worn (EquipableItem.java:126-129, shown on equipping at
 * KindOfWeapon.java:135-139 and Armor.java:250-254): its expected worth adds that chance times the
 * {@code cursed} weight. A piece shown cursed is never put on, and nothing replaces a worn piece
 * shown cursed, which would not come off. Putting on takes a turn (EquipableItem.java:118-120), and
 * a swap two: the worn piece comes off first (KindOfWeapon.java:127, EquipableItem.java:132-136,
 * then :141; Armor.java:246, :259), which the {@code turn} weight costs.
 *
 * <p>Nothing replaces a worn piece the Codex does not name: the Mage's staff, a piece whose name
 * shows an enchantment or a glyph (Weapon.java:411-419, Armor.java:578), a renamed holy weapon.
 * Its worth is not known, and zero would make anything look better. Nor a worn piece whose level
 * shows an upgrade: the Codex's means are level 0's, and a +2 worn sword is worth more than that.
 *
 * <p>Never a piece whose worst case under {@link SafeTest} is unsurvivable: a cursed weapon's or
 * armour's worst proc is scored there, and the verdict must be safe.
 *
 * <p>It enters only on a calm screen, no Prompt and no enemy in view, and takes the
 * {@code UseItem(EQUIP)} Action the screen offers for the piece.
 */
final class Equip implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "equip";

    /** The item action that puts a piece on (EquipableItem.java:41). */
    static final String EQUIP = "EQUIP";

    private final Evaluation evaluation;
    private final Codex.Knowledge knowledge;

    Equip(Evaluation evaluation, Codex.Knowledge knowledge) {
        this.evaluation = evaluation;
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "gear: wear the better";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Explore.calm(observation) && observation.inventory().items().stream()
                .anyMatch(item -> item.slot() == EquipSlot.NONE && item.actions().contains(EQUIP));
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<ItemView> items = observation.inventory().items();
        Action best = null;
        String bestName = "";
        long bestGain = 0;
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            Codex.Gear gear = gear(item.name());
            if (item.slot() != EquipSlot.NONE || gear == null || (item.cursedKnown() && item.visiblyCursed())) {
                continue;
            }
            ItemView worn = worn(items, gear.kind());
            if (worn != null && ((worn.cursedKnown() && worn.visiblyCursed()) || gear(worn.name()) == null
                    || worn.visiblyUpgraded() > 0)) {
                continue;
            }
            long gain = gain(observation, item, gear, worn);
            if (gain <= bestGain) {
                continue;
            }
            SafeTest.Verdict verdict = SafeTest.of(SafeTest.gear(item.name(), gear.kind(), item.cursedKnown(),
                    item.visiblyCursed()), observation);
            if (!verdict.safe()) {
                continue;
            }
            Action equip = new Action.UseItem(new ItemRef(index, item.name(), item.quantity()), EQUIP);
            if (offered.contains(equip)) {
                best = equip;
                bestName = item.name();
                bestGain = gain;
            }
        }
        return best == null ? null : new RunLog.Choice(best, 10000, "wear: " + bestName);
    }

    /**
     * What putting {@code item} on gains over wearing {@code worn} (null when nothing is worn), after
     * the curse risk and the turn it takes.
     */
    long gain(Observation observation, ItemView item, Codex.Gear gear, ItemView worn) {
        int strength = observation.hero().strength() + observation.hero().strengthBonus();
        long now = worn == null || gear(worn.name()) == null ? 0 : worth(gear(worn.name()), strength);
        long gain = Math.subtractExact(worth(gear, strength), now);
        if (!item.cursedKnown()) {
            gain = Math.addExact(gain, Math.round(SafeTest.curseChance(gear.kind()) * evaluation.cursed()));
        }
        return Math.addExact(gain, evaluation.turns(worn == null ? 1 : 2));
    }

    /** What {@code gear} is worth worn by a hero of {@code strength}. */
    long worth(Codex.Gear gear, int strength) {
        long mean = gear.meanPerMille();
        for (int short_ = gear.strength() - strength; short_ > 0; short_--) {
            mean = mean * 2 / 3;
        }
        return evaluation.gear(gear.kind(), mean);
    }

    /** The piece the Codex names {@code name}, or null for anything that is not melee gear. */
    Codex.Gear gear(String name) {
        for (Codex.Gear gear : knowledge.gear()) {
            if (gear.name().equals(name)) {
                return gear;
            }
        }
        return null;
    }

    private static ItemView worn(List<ItemView> items, ItemKind kind) {
        EquipSlot slot = kind == ItemKind.WEAPON ? EquipSlot.WEAPON : EquipSlot.ARMOR;
        for (ItemView item : items) {
            if (item.slot() == slot) {
                return item;
            }
        }
        return null;
    }
}
