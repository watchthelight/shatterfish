package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The equip Policy (story 4.8): a weapon or armour goes on when the Evaluation prefers it, with the
 * strength it asks and the chance a hidden curse holds, and never when SafeTest calls its worst
 * case unsurvivable.
 */
class EquipPolicyTest {

    private static final Equip EQUIP = new Equip(new Evaluation(Screens.WEIGHTS), Screens.CODEX);

    private static final ItemView WORN_SWORD = Screens.gear(ItemKind.WEAPON, "worn shortsword", EquipSlot.WEAPON, true, false);

    /** A hero of {@code strength} at depth {@code depth} with {@code worn} on (or nothing) and {@code backpack}, offered every EQUIP. */
    private static Observation pack(int depth, int strength, List<ItemView> backpack, ItemView worn) {
        // The inventory lists what is worn first (InventorySection).
        List<ItemView> items = new ArrayList<>();
        if (worn != null) {
            items.add(worn);
        }
        items.addAll(backpack);
        List<Action> offered = new ArrayList<>(List.of(new Action.Wait()));
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).slot() == EquipSlot.NONE) {
                offered.add(new Action.UseItem(new ItemRef(i, items.get(i).name(), 1), "EQUIP"));
            }
        }
        return Screens.floor(depth, Screens.heroAt(1, strength), Collections.nCopies(4, Tile.EMPTY), List.of(), items,
                offered.toArray(new Action[0]));
    }

    private static RunLog.Choice choose(Observation observation) {
        return EQUIP.choose(observation, Memory.START, observation.actions().actions(), Stream.at(1, 1));
    }

    private static ItemView unknown(ItemKind kind, String name) {
        return Screens.gear(kind, name, EquipSlot.NONE, false, false);
    }

    @Test
    @DisplayName("a better weapon of hidden curse goes on at the strength it asks, and not two points short")
    void strength() {
        RunLog.Choice choice = choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "shortsword")), WORN_SWORD));
        assertEquals(new Action.UseItem(new ItemRef(1, "shortsword", 1), "EQUIP"), choice.action());
        assertEquals("wear: shortsword", choice.why());
        assertNull(choose(pack(1, 10, List.of(unknown(ItemKind.WEAPON, "shortsword")), WORN_SWORD)),
                "two points short, accuracy falls by 1.5 twice and the worn one is better");
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "greataxe")), WORN_SWORD)),
                "six points short, the greataxe is worse than what is worn");
    }

    @Test
    @DisplayName("the curse risk counts: known uncursed gains more than hidden, and shown cursed never goes on")
    void curse_risk() {
        Observation screen = pack(1, 12, List.of(), WORN_SWORD);
        Codex_ gear = new Codex_();
        long hidden = EQUIP.gain(screen, unknown(ItemKind.WEAPON, "shortsword"), gear.sword, WORN_SWORD);
        long clean = EQUIP.gain(screen, Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, false),
                gear.sword, WORN_SWORD);
        assertEquals(3000L, clean - hidden, "three in ten of the cursed weight");
        assertNull(choose(pack(1, 12, List.of(Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, true)),
                WORN_SWORD)), "a piece shown cursed never goes on");
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "shortsword")),
                Screens.gear(ItemKind.WEAPON, "worn shortsword", EquipSlot.WEAPON, true, true))),
                "nothing replaces a worn piece shown cursed, which would not come off");
    }

    @Test
    @DisplayName("never a piece whose worst case SafeTest calls unsurvivable: a cursed weapon's blast at depth three")
    void safe_test_refuses() {
        // At depth 1 the Explosive curse's blast is 12 + 3 = 15 < 20 hit points; at depth 3 it is 21.
        assertTrue(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "shortsword")), WORN_SWORD)) != null);
        assertNull(choose(pack(3, 12, List.of(unknown(ItemKind.WEAPON, "shortsword")), WORN_SWORD)));
        assertTrue(choose(pack(3, 12, List.of(Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, false)),
                WORN_SWORD)) != null, "the same piece known uncursed goes on");
    }

    @Test
    @DisplayName("armour against armour, and into an empty slot")
    void armour() {
        ItemView cloth = Screens.gear(ItemKind.ARMOR, "cloth armor", EquipSlot.ARMOR, true, false);
        assertEquals("wear: leather armor", choose(pack(1, 12, List.of(unknown(ItemKind.ARMOR, "leather armor")), cloth)).why());
        // Into an empty slot, even cloth armour of hidden curse is worth it: 4 x 1004 - 3000 - 150.
        assertEquals("wear: cloth armor", choose(pack(1, 12, List.of(unknown(ItemKind.ARMOR, "cloth armor")), null)).why());
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.ARMOR, "cloth armor")),
                Screens.gear(ItemKind.ARMOR, "leather armor", EquipSlot.ARMOR, true, false))),
                "cloth over leather is a loss");
    }

    @Test
    @DisplayName("it does not enter with an enemy in view, and ignores what the Codex does not know as gear")
    void entering() {
        Observation fight = Screens.world(1, Collections.nCopies(4, Tile.EMPTY), List.of(), List.of(Screens.enemy("rat", 3)),
                List.of(unknown(ItemKind.WEAPON, "shortsword")), List.of());
        assertFalse(EQUIP.enters(fight, Memory.START));
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "a mystery blade")), WORN_SWORD)));
    }

    /** The test Codex's gear, by name. */
    private static final class Codex_ {
        final org.shatterfish.api.Codex.Gear sword = EQUIP.gear("shortsword");
    }
}
