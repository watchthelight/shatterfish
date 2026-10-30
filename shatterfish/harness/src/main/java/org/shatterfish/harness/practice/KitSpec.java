package org.shatterfish.harness.practice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * What a hero carries onto a boss floor in a practice Run, as plain data: no game class, so the
 * rig can name it, hash it, and a later sampler can build one from the arrival snapshots of real
 * Runs and hand it to the same door (ADR-0024).
 *
 * <p>A kit is a statement about the hero at the top of the stairs and nothing else. It says the
 * level, the strength, the equipped pieces and what is in the pack, and how many of the two limited
 * drops the floors above are taken to have given out ({@code Dungeon.java:529-562}), so that
 * anything the boss floor asks the game about its own history gets the answer a real descent would.
 * It never says what is on the floor, where anything stands, or what any enemy will do.
 *
 * @param level      the hero's level; the game's own experience code raises the hero to it
 * @param strength   the hero's strength, ten at the start and one more for each potion of strength
 * @param equipment  what is worn and wielded, by slot
 * @param pack       what is carried
 * @param strengthPotionsDropped the potions of strength the floors above are taken to have dropped
 * @param upgradeScrollsDropped  the scrolls of upgrade the floors above are taken to have dropped
 */
public record KitSpec(int level, int strength, List<Piece> equipment, List<Piece> pack,
                      int strengthPotionsDropped, int upgradeScrollsDropped) {

    /**
     * The item names a kit may hold: the simple names of the game's classes, each built by
     * {@code PracticeStart}. Held as names here so that this record stays free of game code; a
     * harness test holds the two lists equal.
     */
    public static final List<String> ITEMS = List.of(
            "ClothArmor", "LeatherArmor", "MailArmor", "ScaleArmor", "PlateArmor",
            "WornShortsword", "Dagger", "Gloves", "Rapier", "Cudgel", "MagesStaff",
            "Shortsword", "Sword", "Mace", "Spear", "Longsword", "Quarterstaff", "Whip",
            "SpiritBow", "CloakOfShadows", "HolyTome",
            "Food", "PotionOfHealing", "ThrowingStone", "ThrowingKnife", "ThrowingSpike");

    /** Where a piece goes. */
    public enum Slot {
        WEAPON, ARMOR, ARTIFACT, PACK
    }

    /**
     * One piece. {@code item} is the class's simple name from {@link #ITEMS}; a piece the class
     * starts with and this names again is upgraded in place, so "the Warrior's armour at +4" keeps
     * its seal. {@code level} is the number of upgrades, applied one at a time through the game's own
     * {@code Item.upgrade}, and {@code quantity} is for stackable things. Everything in a kit is
     * identified, which is what a hero who has played the floors above would know about their gear.
     */
    public record Piece(Slot slot, String item, int level, int quantity) {

        public Piece {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(item, "item");
            if (!ITEMS.contains(item)) {
                throw new IllegalArgumentException("a kit holds " + ITEMS + ", not " + item);
            }
            if (level < 0 || level > 15) {
                throw new IllegalArgumentException("a piece is upgraded 0 to 15 times: " + level);
            }
            if (quantity < 1 || quantity > 20) {
                throw new IllegalArgumentException("a piece is 1 to 20 of an item: " + quantity);
            }
        }

        public static Piece of(Slot slot, String item, int level) {
            return new Piece(slot, item, level, 1);
        }

        public static Piece pack(String item, int level, int quantity) {
            return new Piece(Slot.PACK, item, level, quantity);
        }
    }

    public KitSpec {
        if (level < 1 || level > 30) {
            throw new IllegalArgumentException("a hero is level 1 to 30: " + level);
        }
        if (strength < 10 || strength > 30) {
            throw new IllegalArgumentException("a hero's strength is 10 to 30: " + strength);
        }
        equipment = List.copyOf(equipment);
        pack = List.copyOf(pack);
        for (Piece piece : equipment) {
            if (piece.slot() == Slot.PACK) {
                throw new IllegalArgumentException("equipment goes in a slot, not the pack: " + piece);
            }
        }
        for (Piece piece : pack) {
            if (piece.slot() != Slot.PACK) {
                throw new IllegalArgumentException("the pack holds pack pieces: " + piece);
            }
        }
        if (strengthPotionsDropped < 0 || upgradeScrollsDropped < 0) {
            throw new IllegalArgumentException("a drop count is not negative");
        }
    }

    /**
     * The kit as one canonical text: every field in a fixed order, so equal kits have equal text and
     * the hash below names a kit and not a way of writing one.
     */
    public String canonical() {
        StringBuilder text = new StringBuilder();
        text.append("level=").append(level).append(";strength=").append(strength)
                .append(";strength_potions=").append(strengthPotionsDropped)
                .append(";upgrade_scrolls=").append(upgradeScrollsDropped);
        for (Piece piece : equipment) {
            text.append(";equip=").append(piece.slot()).append(':').append(piece.item()).append('+')
                    .append(piece.level()).append('x').append(piece.quantity());
        }
        for (Piece piece : pack) {
            text.append(";pack=").append(piece.item()).append('+').append(piece.level()).append('x')
                    .append(piece.quantity());
        }
        return text.toString();
    }

    /** The first sixteen hex digits of the SHA-256 of {@link #canonical()}, which a log header carries. */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
