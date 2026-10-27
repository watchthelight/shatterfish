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
 * Its worth is not known, and zero would make anything look better. Nor a worn weapon whose level
 * shows an upgrade: the Codex's means are level 0's, and a +2 worn sword is worth more than that.
 * An armour is rated at the level the screen shows (#305, {@link Piece#atLevel}), worn or not, so
 * an upgraded spare is put on when it is worth more and an upgraded worn one is kept.
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
        return Calm.quiet(observation, knowledge) && observation.inventory().items().stream()
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
            Piece gear = piece(item);
            // A shown negative level is worth less than the level-0 figures say: not put on.
            if (item.slot() != EquipSlot.NONE || gear == null || (item.cursedKnown() && item.visiblyCursed())
                    || (item.levelKnown() && item.visiblyUpgraded() < 0)) {
                continue;
            }
            ItemView worn = worn(items, gear.kind());
            if (worn != null && ((worn.cursedKnown() && worn.visiblyCursed()) || piece(worn) == null
                    || (worn.visiblyUpgraded() > 0 && gear.kind() != ItemKind.ARMOR))) {
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
    long gain(Observation observation, ItemView item, Piece gear, ItemView worn) {
        int strength = observation.hero().strength() + observation.hero().strengthBonus();
        long now = worn == null || piece(worn) == null ? 0 : worth(piece(worn), strength);
        long gain = Math.subtractExact(worth(gear, strength), now);
        if (!item.cursedKnown()) {
            gain = Math.addExact(gain, Math.round(SafeTest.curseChance(gear.kind()) * evaluation.cursed()));
        }
        return Math.addExact(gain, evaluation.turns(worn == null ? 1 : 2));
    }

    /** What {@code gear} is worth worn by a hero of {@code strength}. */
    long worth(Piece gear, int strength) {
        return evaluation.gear(gear.kind(), effective(gear, strength));
    }

    /**
     * The mean roll of {@code gear} in thousandths after the strength it asks beyond {@code strength}: cut
     * by a third for each point short.
     */
    static long effective(Piece gear, int strength) {
        long mean = gear.meanPerMille();
        for (int short_ = gear.strength() - strength; short_ > 0; short_--) {
            mean = mean * 2 / 3;
        }
        return mean;
    }

    /**
     * An instance that only names pieces, for the upgrade allocation (#305), which needs {@link #piece} and
     * {@link #effective} and no Evaluation.
     */
    static Equip namer(Codex.Knowledge knowledge) {
        return new Equip(null, knowledge);
    }

    /**
     * The least a spare armour must outdo the worn one by, in thousandths of a point absorbed, for this
     * Policy to swap to it: the swap's two turns and the curse a hidden state may hold, over the armour
     * weight, rounded up (2 x 150 / 4 plus 0.255 x 10000 / 4 with the shipped weights is 712), so a scroll
     * is not spent on a piece that would stay in the pack. A spare shown not cursed asks the turns only.
     */
    static long swapMargin(boolean cursedKnown) {
        return cursedKnown ? 75 : 750;
    }

    /**
     * A piece of melee gear as this Policy weighs it: its level-0 figures from the Codex and whether it
     * is a weapon or armour, by the list the Codex measured it in.
     */
    record Piece(String name, ItemKind kind, int tier, int strength, int meanPerMille) {

        /**
         * This armour at a shown upgrade {@code level} (#305). One level adds the tier plus one to the mean
         * roll, thousandths of a point: the least absorbed rises by one and the most by the tier
         * (Armor.java:379-406), and it lowers the strength asked by {@code floor((sqrt(8*level+1)-1)/2)}
         * (Armor.java:698-703). A weapon, or level 0, is the piece itself: a weapon's worn upgrade is not
         * rated, and no unworn weapon is told from level 0.
         */
        Piece atLevel(int level) {
            if (kind != ItemKind.ARMOR || level <= 0) {
                return this;
            }
            return new Piece(name, kind, tier, strength - (int) ((Math.sqrt(8 * level + 1) - 1) / 2),
                    meanPerMille + 500 * level * (tier + 1));
        }
    }

    /**
     * The piece an inventory item is, at the level the screen shows when it is an armour whose level is
     * shown (#305), or null. A level that is not shown counts as 0, as it always has.
     */
    Piece piece(ItemView item) {
        Piece gear = gear(item.name());
        return gear == null || !item.levelKnown() ? gear : gear.atLevel(item.visiblyUpgraded());
    }

    /**
     * The piece a shown name is, or null. The name is resolved by the fight Policy's matcher
     * ({@link Fight#measured}), and only a plain name counts: a piece whose shown name is its measured
     * name and nothing more, with a tier the item table states. An enchantment or glyph in the name,
     * the mage's staff (which shows its wand's name), a holy weapon's name, or a level the screen
     * shows are all a worth the level-0 figures do not give, so such a piece is neither put on nor
     * replaced.
     */
    Piece gear(String name) {
        for (ItemKind kind : List.of(ItemKind.WEAPON, ItemKind.ARMOR)) {
            List<Codex.Gear> measured = kind == ItemKind.WEAPON ? knowledge.weapons() : knowledge.armours();
            if (!name.equals(Fight.measured(name, measured))) {
                continue;
            }
            for (Codex.Gear gear : measured) {
                if (gear.name().equals(name) && gear.level() == 0 && gear.tier() > 0) {
                    return new Piece(gear.name(), kind, gear.tier(), gear.strength(), gear.meanPerMille());
                }
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
