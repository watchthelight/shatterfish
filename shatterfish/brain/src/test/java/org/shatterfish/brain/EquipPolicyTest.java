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
        return pack(depth, strength, 40, backpack, worn);
    }

    /** As above, with {@code hp} of forty. */
    private static Observation pack(int depth, int strength, int hp, List<ItemView> backpack, ItemView worn) {
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
        return Screens.floor(depth, Screens.heroAt(1, strength, hp, 40), Collections.nCopies(4, Tile.EMPTY), List.of(), items,
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
        assertEquals(3333L, clean - hidden, "a third of the cursed weight: 0.3 of the 0.9 that shows no enchantment");
        ItemView leather = unknown(ItemKind.ARMOR, "leather armor");
        ItemView cleanLeather = Screens.gear(ItemKind.ARMOR, "leather armor", EquipSlot.NONE, true, false);
        Codex_ codex = new Codex_();
        assertEquals(3529L, EQUIP.gain(screen, cleanLeather, codex.leather, null) - EQUIP.gain(screen, leather, codex.leather, null),
                "0.3 of the 0.85 of armour that shows no glyph");
        assertNull(choose(pack(1, 12, List.of(Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, true)),
                WORN_SWORD)), "a piece shown cursed never goes on");
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "shortsword")),
                Screens.gear(ItemKind.WEAPON, "worn shortsword", EquipSlot.WEAPON, true, true))),
                "nothing replaces a worn piece shown cursed, which would not come off");
    }

    @Test
    @DisplayName("never a piece whose worst case SafeTest calls unsurvivable: a cursed weapon's wand effect at low health")
    void safe_test_refuses() {
        // A cursed weapon is scored as a cursed wand's zap: at depth 1 the worst is a dry burn, 10
        // turns of 3, which forty hit points survive and twenty do not.
        assertTrue(choose(pack(1, 12, 40, List.of(unknown(ItemKind.WEAPON, "shortsword")), WORN_SWORD)) != null);
        assertNull(choose(pack(1, 12, 20, List.of(unknown(ItemKind.WEAPON, "shortsword")), WORN_SWORD)));
        assertTrue(choose(pack(1, 12, 20, List.of(Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, false)),
                WORN_SWORD)) != null, "the same piece known uncursed goes on");
    }

    @Test
    @DisplayName("nothing replaces a worn piece the Codex does not name, or one whose level shows an upgrade")
    void unknown_or_upgraded_worn() {
        ItemView staff = Screens.gear(ItemKind.WEAPON, "staff of magic missile", EquipSlot.WEAPON, true, false);
        assertNull(choose(pack(1, 12, List.of(Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, false)),
                staff)), "the Mage's staff is worth what the Codex cannot say, not zero");
        ItemView upgraded = new ItemView(ItemKind.WEAPON, "worn shortsword", 1, true, 2, true, false, "",
                EquipSlot.WEAPON, List.of("UNEQUIP"), "");
        assertNull(choose(pack(1, 12, List.of(Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, false)),
                upgraded)), "a +2 worn piece is worth more than its level 0");
    }

    @Test
    @DisplayName("a piece whose level shows negative is not put on")
    void negative_level() {
        ItemView degraded = new ItemView(ItemKind.WEAPON, "shortsword", 1, true, -1, true, false, "",
                EquipSlot.NONE, List.of("EQUIP"), "");
        assertNull(choose(pack(1, 12, List.of(degraded), WORN_SWORD)), "a -1 sword is not the +0 the Codex measured");
        ItemView plain = new ItemView(ItemKind.WEAPON, "shortsword", 1, true, 0, true, false, "",
                EquipSlot.NONE, List.of("EQUIP"), "");
        assertEquals("wear: shortsword", choose(pack(1, 12, List.of(plain), WORN_SWORD)).why());
    }

    @Test
    @DisplayName("a swap costs two turns, putting on into an empty slot one")
    void swap_costs_two() {
        Observation screen = pack(1, 12, List.of(), null);
        Codex_ codex = new Codex_();
        ItemView sword = Screens.gear(ItemKind.WEAPON, "shortsword", EquipSlot.NONE, true, false);
        ItemView worn = Screens.gear(ItemKind.WEAPON, "worn shortsword", EquipSlot.WEAPON, true, false);
        long intoEmpty = EQUIP.gain(screen, sword, codex.sword, null);
        long swap = EQUIP.gain(screen, sword, codex.sword, worn);
        assertEquals(2 * 8485 - 150, intoEmpty);
        assertEquals(2 * (8485 - 5485) - 300, swap);
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

    private static ItemView armourAt(String name, EquipSlot slot, int level) {
        return new ItemView(ItemKind.ARMOR, name, 1, true, level, true, false, "", slot,
                List.of(slot == EquipSlot.NONE ? "EQUIP" : "UNEQUIP"), "");
    }

    @Test
    @DisplayName("an upgraded armour is rated at its level: a +3 cloth spare beats a plain leather worn (#305)")
    void upgraded_spare_armour_is_worn() {
        // One level adds the tier plus one to the mean (Armor.java:379-406): cloth +3 absorbs 4004 per
        // mille against leather +0's 2013.
        RunLog.Choice choice = choose(pack(1, 12, List.of(armourAt("cloth armor", EquipSlot.NONE, 3)),
                armourAt("leather armor", EquipSlot.ARMOR, 0)));
        assertEquals("wear: cloth armor", choice.why());
        assertNull(choose(pack(1, 12, List.of(armourAt("cloth armor", EquipSlot.NONE, 1)),
                armourAt("leather armor", EquipSlot.ARMOR, 0))), "cloth +1 is 2004, under leather's 2013");
    }

    @Test
    @DisplayName("a worn upgraded armour is replaced only by a spare worth more at its own level (#305)")
    void upgraded_worn_armour() {
        assertNull(choose(pack(1, 12, List.of(armourAt("leather armor", EquipSlot.NONE, 0)),
                armourAt("cloth armor", EquipSlot.ARMOR, 2))), "cloth +2 (3004) stays over leather +0 (2013)");
        assertEquals("wear: leather armor", choose(pack(1, 12, List.of(armourAt("leather armor", EquipSlot.NONE, 2)),
                armourAt("cloth armor", EquipSlot.ARMOR, 2))).why(), "leather +2 (5013) over cloth +2 (3004)");
    }

    @Test
    @DisplayName("an upgraded armour asks less strength: leather +3 fits a hero of 10 (Armor.java:698-703) (#305)")
    void upgrade_lowers_the_strength_asked() {
        assertNull(choose(pack(1, 10, List.of(armourAt("leather armor", EquipSlot.NONE, 0)),
                armourAt("cloth armor", EquipSlot.ARMOR, 0))), "two short at level 0: no");
        assertEquals("wear: leather armor", choose(pack(1, 10, List.of(armourAt("leather armor", EquipSlot.NONE, 3)),
                armourAt("cloth armor", EquipSlot.ARMOR, 0))).why(), "the +3 asks 10, and has the higher mean");
    }

    @Test
    @DisplayName("it does not enter with an enemy in view, and ignores what the Codex does not know as gear")
    void entering() {
        Observation fight = Screens.world(1, Collections.nCopies(4, Tile.EMPTY), List.of(), List.of(Screens.enemy("rat", 3)),
                List.of(unknown(ItemKind.WEAPON, "shortsword")), List.of());
        assertFalse(EQUIP.enters(fight, Memory.START));
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "a mystery blade")), WORN_SWORD)));
        // The fight Policy's matcher resolves the names (story 4.8's merge with 4.7): an enchanted
        // name finds its item, but only a plain name is worth its level-0 figures.
        assertEquals("shortsword", Fight.measured("blazing shortsword", Screens.CODEX.weapons()));
        assertNull(EQUIP.gear("blazing shortsword"));
        assertNull(EQUIP.gear("staff of magic missile"), "the staff resolves to the mage's staff, never a plain piece");
        assertEquals(ItemKind.ARMOR, EQUIP.gear("leather armor").kind(), "the list it is measured in says its kind");
        assertNull(choose(pack(1, 12, List.of(unknown(ItemKind.WEAPON, "blazing shortsword")), WORN_SWORD)));
    }

    /** The test Codex's gear, by name. */
    private static final class Codex_ {
        final Equip.Piece sword = EQUIP.gear("shortsword");
        final Equip.Piece leather = EQUIP.gear("leather armor");
    }

    @Test
    @DisplayName("equip.armour_short_cost_per_mille prices a missing strength point as the game does (2 armour a point, Hero.java:652-656), and 0 keeps the cut by a third")
    void short_cost() {
        Equip.Piece leather = EQUIP.gear("leather armor");
        assertEquals(Equip.effective(leather, 12), Equip.effective(leather, 12, 2000), "at the strength it asks, the cost is not charged");
        int asks = leather.strength();
        assertEquals(Math.max(0, leather.meanPerMille() - 4000), Equip.effective(leather, asks - 2, 2000), "two points short cost four armour");
        assertTrue(Equip.effective(leather, asks - 3, 2000) <= Equip.effective(leather, asks - 2, 2000), "each point short costs more");
        Equip priced = new Equip(new Evaluation(Screens.WEIGHTS), Screens.CODEX,
                org.shatterfish.api.Tunables.Vector.REFERENCE.with(java.util.Map.of("equip.armour_short_cost_per_mille", 2000L)));
        ItemView cloth = Screens.gear(ItemKind.ARMOR, "cloth armor", EquipSlot.ARMOR, true, false);
        Observation shortOfIt = pack(1, asks - 2, List.of(unknown(ItemKind.ARMOR, "leather armor")), cloth);
        assertNull(priced.choose(shortOfIt, Memory.START, shortOfIt.actions().actions(), Stream.at(1, 1)),
                "two points short of leather, priced as the game does, the cloth stays on");
    }
}
