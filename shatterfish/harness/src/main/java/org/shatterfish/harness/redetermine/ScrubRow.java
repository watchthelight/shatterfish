package org.shatterfish.harness.redetermine;

/**
 * One row of the scrub table of ADR-0009, named. {@code Scrub} applies the rows in this order and a caller may pick
 * which (the differential test removes one to see its own failure name the row), and every report counts, per row,
 * what the snapshot held and what the row changed.
 */
public enum ScrubRow {
    /** Unidentified potion, scroll and ring classes permuted among the unknown classes of their family. */
    POTION_SCROLL_RING_CLASSES,
    /** An unknown item level and curse re-rolled in range, by the game's own {@code random()}. */
    ITEM_LEVEL_AND_CURSE,
    /** Mobs outside the field of view removed, and the sample's remembered mobs re-added on visited cells. */
    MOBS_OUT_OF_VIEW,
    /**
     * A stealthy passive mimic on a visited cell. ADR-0009 keeps it; the audit of story 6.2 (blocker B2) found that
     * keeping the true mimic with the true chests teaches search which chest bites, so this row is the gate that
     * refuses a snapshot holding a hidden mimic or a chest-family heap in view, instead of keeping one.
     */
    STEALTHY_MIMIC_KEPT,
    /** Every mob in view: its AI state, its target and its exact HP re-sampled within what the screen shows. */
    MOB_AI_AND_HP,
    /** Unknown cells made solid, with heaps, traps, blobs, plants and transitions on them removed. */
    UNKNOWN_CELLS,
    /** Secret traps and doors on known cells turned into the floor and the wall the screen draws. */
    SECRET_TRAPS_AND_DOORS,
    /** Container contents, and everything under a heap's top item, redrawn from the Codex tables. */
    CONTAINER_CONTENTS,
    /** Heaps the player has never seen removed (the audit's addition to the table). */
    UNSEEN_ITEMS,
    /** The hero's buffs that carry state the screen does not show: the set tracker emptied, iconless buffs dropped. */
    HERO_HIDDEN_BUFFS,
    /** The dungeon seed replaced, and the floors the Run has not visited with it. */
    DUNGEON_SEED,
    /** Generator decks, limited drops, special and secret room queues, quest state and statistics reset. */
    GENERATOR_AND_QUEUES,
    /** The rollout's own salt taken from the sample. */
    ROLLOUT_SALT
}
