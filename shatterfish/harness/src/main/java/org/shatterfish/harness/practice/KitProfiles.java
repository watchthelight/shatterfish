package org.shatterfish.harness.practice;

import org.shatterfish.api.HeroClass;
import org.shatterfish.harness.practice.KitSpec.Piece;
import org.shatterfish.harness.practice.KitSpec.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The kit profiles a practice Run can arrive with. A profile is a name for a function from (boss,
 * hero class) to a {@link KitSpec}; the header stamps the name and the hash of what it resolved to.
 *
 * <p>There are two profiles, both written by hand and neither a measurement of a Run. A sampler that
 * reads the arrival snapshots of real Runs (the rig writes them beside every direction Run,
 * {@code .arrivals.tsv}) produces a profile of the same shape, and the numbers published with a
 * profile say which one they were played with.
 *
 * <ul>
 *   <li>{@code standard} is what a hero that found everything the floors above guarantee arrives
 *       with: strength and upgrades from the drop tables, below.</li>
 *   <li>{@code arrived} is what the Brain's own Runs arrive with, read off the arrival records of a
 *       standard direction slice (docs/results/e10-practice-kit.md): less strength, fewer upgrades,
 *       a lower level. The second is the pessimistic bracket and the first the optimistic one.</li>
 * </ul>
 *
 * <p><b>Strength.</b> Two potions of strength drop on every set of five floors
 * ({@code core/.../Dungeon.java:529-543}, {@code posNeeded}: "2 POS each floor set", the set being
 * {@code depth / 5}), so a hero who found them all stands at ten plus two on the way to the sewers'
 * boss and ten plus four on the way to the prison's.
 *
 * <p><b>Upgrades.</b> Three scrolls of upgrade drop on every set of five floors
 * ({@code Dungeon.java:545-554}, {@code souNeeded}: "3 SOU each floor set"): three arriving at the
 * sewers' boss, six at the prison's. How a hero spends them is the Brain's, and the profile spends them
 * the way the Brain's own upgrade allocation does on the pieces it wears (docs/rules/upgrades.md,
 * story 7.4): armour first, then the weapon.
 *
 * <p><b>Level.</b> The experience to leave level {@code n} is {@code 5 + 5n}
 * ({@code core/.../actors/hero/Hero.java:2135-2137}), so level {@code L} costs {@code 5(L-1) + 5L(L-1)/2}
 * experience in all; the level is the one real Runs arrive with (docs/results/e10-practice-kit.md).
 */
public final class KitProfiles {

    /** The profile every practice Run uses unless it is told another. */
    public static final String STANDARD = "standard";

    /** The profile of what the Brain's own Runs arrive with, the pessimistic bracket. */
    public static final String ARRIVED = "arrived";

    private KitProfiles() {
    }

    /** The profiles there are. */
    public static List<String> names() {
        return List.of(STANDARD, ARRIVED);
    }

    /** The kit {@code profile} gives a hero of {@code heroClass} in front of {@code boss}. */
    public static KitSpec kit(String profile, PracticeBoss boss, HeroClass heroClass) {
        if (STANDARD.equals(profile)) {
            return standard(boss, heroClass);
        }
        if (ARRIVED.equals(profile)) {
            return arrived(boss, heroClass);
        }
        throw new IllegalArgumentException("there is no kit profile " + profile + "; the ones there are: " + names());
    }

    /** The floor sets completed before arriving on {@code depth}: floors 1-4 are the first, 5-9 the second. */
    static int sets(int depth) {
        return (depth - 1 + 4) / 5;
    }

    private static KitSpec standard(PracticeBoss boss, HeroClass heroClass) {
        int sets = sets(boss.depth());
        int upgradeScrolls = 3 * sets;
        boolean tengu = boss == PracticeBoss.TENGU;
        // Upgrades go to the armour first and then the weapon, two to one, for the classes whose
        // power is in melee; the staff, the bow and the cloak take them for the classes whose
        // power is in the kit's own piece.
        int armour = (upgradeScrolls * 2) / 3;
        return build(heroClass, tengu ? 9 : 6, 10 + 2 * sets, armour, upgradeScrolls - armour, 2,
                2 * sets, upgradeScrolls);
    }

    /**
     * What the Brain's Runs arrive with, rounded from the mean arrival of the first 24 Runs of each
     * class in the standard set: floor 5 at level 6 with strength 11, armour +2, weapon +1 and one
     * potion of healing; floor 10 extrapolated along the same line, level 8, strength 12, five
     * upgrades (docs/results/e10-practice-kit.md).
     */
    private static KitSpec arrived(PracticeBoss boss, HeroClass heroClass) {
        boolean tengu = boss == PracticeBoss.TENGU;
        return tengu ? build(heroClass, 8, 12, 3, 2, 1, 2, 5) : build(heroClass, 6, 11, 2, 1, 1, 1, 3);
    }

    private static KitSpec build(HeroClass heroClass, int level, int strength, int armour, int weapon, int healing,
                                 int strengthPotionsDropped, int upgradeScrollsDropped) {
        List<Piece> equipment = new ArrayList<>();
        List<Piece> pack = new ArrayList<>();
        switch (heroClass) {
            case WARRIOR -> {
                equipment.add(Piece.of(Slot.ARMOR, "ClothArmor", armour));
                equipment.add(Piece.of(Slot.WEAPON, "WornShortsword", weapon));
            }
            case MAGE -> {
                equipment.add(Piece.of(Slot.ARMOR, "ClothArmor", armour));
                equipment.add(Piece.of(Slot.WEAPON, "MagesStaff", weapon));
            }
            case ROGUE -> {
                equipment.add(Piece.of(Slot.ARMOR, "ClothArmor", armour));
                equipment.add(Piece.of(Slot.WEAPON, "Dagger", weapon));
            }
            case HUNTRESS -> {
                equipment.add(Piece.of(Slot.ARMOR, "ClothArmor", armour));
                equipment.add(Piece.of(Slot.WEAPON, "Gloves", 0));
                pack.add(Piece.pack("SpiritBow", weapon, 1));
            }
            case DUELIST -> {
                equipment.add(Piece.of(Slot.ARMOR, "ClothArmor", armour));
                equipment.add(Piece.of(Slot.WEAPON, "Rapier", weapon));
            }
            case CLERIC -> {
                equipment.add(Piece.of(Slot.ARMOR, "ClothArmor", armour));
                equipment.add(Piece.of(Slot.WEAPON, "Cudgel", weapon));
            }
        }
        pack.add(Piece.pack("Food", 0, 2));
        pack.add(Piece.pack("PotionOfHealing", 0, healing));
        return new KitSpec(level, strength, equipment, pack, strengthPotionsDropped, upgradeScrollsDropped);
    }
}
