package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Playbook's {@code upgrade} section decides where a scroll of upgrade goes and when (story 7.4):
 * each choice is asserted on a constructed pack, and no choice names a piece the hero does not wear.
 */
class UpgradePlaybookTest {

    private static ItemView weapon(boolean levelKnown, int level) {
        return new ItemView(ItemKind.WEAPON, "worn shortsword", 1, levelKnown, level, levelKnown, false, "",
                EquipSlot.WEAPON, List.of(), "");
    }

    private static ItemView armour(int level) {
        return new ItemView(ItemKind.ARMOR, "cloth armor", 1, true, level, true, false, "", EquipSlot.ARMOR,
                List.of(), "");
    }

    /** Scale armour in the pack, heavier than the cloth worn, which no upgrade goes to. */
    private static final ItemView SPARE = new ItemView(ItemKind.ARMOR, "scale armor", 1, true, 0, true, false, "",
            EquipSlot.NONE, List.of("EQUIP"), "");

    private static final ItemView UPGRADE = new ItemView(ItemKind.SCROLL, TestItem.UPGRADE, 1, true, 0, true, false, "",
            EquipSlot.NONE, List.of(TestItem.READ), "");

    private static final ItemRef SWORD = new ItemRef(0, "worn shortsword", 1);
    private static final ItemRef CLOTH = new ItemRef(1, "cloth armor", 1);

    private static List<Tile> row() {
        return new ArrayList<>(Collections.nCopies(3, Tile.EMPTY));
    }

    /** A calm one-row floor at {@code depth}, the pack {@code items}, offering the Steps and the reads onto every piece. */
    private static Observation screen(int depth, List<ItemView> items) {
        List<Action> offered = new ArrayList<>(List.of(new Action.Step(0), new Action.Step(2)));
        for (int scroll = 0; scroll < items.size(); scroll++) {
            ItemView item = items.get(scroll);
            if (item.actions().contains(TestItem.READ)) {
                ItemRef ref = new ItemRef(scroll, item.name(), item.quantity());
                offered.add(new Action.UseItem(ref, TestItem.READ));
                for (int other = 0; other < items.size(); other++) {
                    if (other != scroll) {
                        offered.add(new Action.UseItemOn(ref, TestItem.READ,
                                new ItemRef(other, items.get(other).name(), items.get(other).quantity())));
                    }
                }
            }
        }
        return Screens.lab(depth, 3, row(), List.of(), Screens.heroAt(1, 20, 20, List.of()), items, List.of(),
                List.of(), offered.toArray(Action[]::new));
    }

    private static Playbook.Upgrade with(Object... pairs) {
        Map<String, Object> given = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            given.put((String) pairs[i], pairs[i + 1]);
        }
        return Playbook.Upgrade.REFERENCE.with(given);
    }

    private static Playbook playbook(Playbook.Upgrade upgrade) {
        return new Playbook("shatterfish", 1, Playbook.Identify.REFERENCE, upgrade, List.of());
    }

    @Test
    @DisplayName("the reference values keep the allocation of story 4.13: the weapon on a tie, no hold, an unknown-level weapon passed over")
    void reference() {
        Observation tied = screen(1, List.of(weapon(true, 1), armour(1)));
        assertEquals(SWORD, TestItem.target(tied, Playbook.Upgrade.REFERENCE));
        Observation unknown = screen(1, List.of(weapon(false, 0), armour(1)));
        assertEquals(CLOTH, TestItem.target(unknown, Playbook.Upgrade.REFERENCE), "a weapon whose level is not shown is not a target");
        Observation known = screen(1, List.of(weapon(true, 0), armour(0), UPGRADE));
        assertNotNull(TestItem.upgrade(known, known.actions().actions(), Playbook.Upgrade.REFERENCE), "read at depth 1");
    }

    private static void assertNotNull(Object value, String message) {
        assertNotEquals(null, value, message);
    }

    @Test
    @DisplayName("worn armour only: a lead of minus five sends every scroll to the armour, a lead of five to the weapon")
    void armour_or_weapon() {
        for (int[] levels : new int[][]{{0, 0}, {3, 0}, {0, 3}, {2, 5}}) {
            Observation screen = screen(1, List.of(weapon(true, levels[0]), armour(levels[1])));
            assertEquals(CLOTH, TestItem.target(screen, with("weapon_lead", -5L)),
                    "weapon +" + levels[0] + " armour +" + levels[1] + ": armour first");
            assertEquals(SWORD, TestItem.target(screen, with("weapon_lead", 5L)),
                    "weapon +" + levels[0] + " armour +" + levels[1] + ": weapon first");
        }
    }

    @Test
    @DisplayName("hold_until_depth keeps a known scroll in the pack above that depth and reads it from there")
    void hold() {
        Playbook.Upgrade held = with("hold_until_depth", 3L);
        for (int depth = 1; depth <= 4; depth++) {
            Observation screen = screen(depth, List.of(weapon(true, 0), armour(0), UPGRADE));
            Action read = TestItem.upgrade(screen, screen.actions().actions(), held);
            TestItem policy = new TestItem(Screens.CODEX, playbook(held));
            if (depth < 3) {
                assertNull(read, "depth " + depth + ": held");
                assertFalse(policy.enters(screen, Memory.START), "depth " + depth + ": nothing to do");
            } else {
                assertEquals(new Action.UseItemOn(new ItemRef(2, TestItem.UPGRADE, 1), TestItem.READ, SWORD), read,
                        "depth " + depth + ": read");
                assertTrue(policy.enters(screen, Memory.START));
                assertEquals("upgrade: worn shortsword",
                        policy.plan(screen, Memory.START, screen.actions().actions()).choice().why());
            }
        }
    }

    @Test
    @DisplayName("a weapon whose level is not shown is a target only when unknown_weapon says so; it counts as level 0")
    void unknown_weapon() {
        Observation screen = screen(1, List.of(weapon(false, 0), armour(1), UPGRADE));
        assertEquals(CLOTH, TestItem.target(screen, Playbook.Upgrade.REFERENCE));
        assertEquals(SWORD, TestItem.target(screen, with("unknown_weapon", "YES")), "allowed, and 0 is below the armour's 1");
        Observation level = screen(1, List.of(weapon(false, 0), armour(0), UPGRADE));
        assertEquals(SWORD, TestItem.target(level, with("unknown_weapon", "YES")), "on a tie the weapon first");
        assertEquals(CLOTH, TestItem.target(level, with("unknown_weapon", "YES", "tie", "ARMOR")));
        // The unknown scroll is read through the same target and the same safety verdict.
        ItemView kaunan = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);
        Observation unread = screen(1, List.of(weapon(false, 0), armour(0), kaunan));
        TestItem.Plan plan = new TestItem(Screens.CODEX, playbook(with("unknown_weapon", "YES")))
                .plan(unread, Memory.START, unread.actions().actions());
        assertEquals(new Action.UseItemOn(new ItemRef(2, "scroll of KAUNAN", 1), TestItem.READ, SWORD),
                plan.choice().action());
        TestItem.Plan reference = new TestItem(Screens.CODEX).plan(unread, Memory.START, unread.actions().actions());
        assertEquals(new Action.UseItemOn(new ItemRef(2, "scroll of KAUNAN", 1), TestItem.READ, CLOTH),
                reference.choice().action(), "by default the unknown weapon is passed over");
    }

    @Test
    @DisplayName("an armour in the pack is never a target: only the worn weapon and the worn armour take a scroll")
    void only_worn_pieces() {
        Observation screen = screen(1, List.of(weapon(true, 0), armour(0), SPARE, UPGRADE));
        for (Playbook.Upgrade upgrade : List.of(Playbook.Upgrade.REFERENCE, with("weapon_lead", -5L),
                with("weapon_lead", 5L), with("tie", "ARMOR", "unknown_weapon", "YES"))) {
            ItemRef target = TestItem.target(screen, upgrade);
            assertTrue(target.equals(SWORD) || target.equals(CLOTH), "worn pieces only: " + target);
            Action read = TestItem.upgrade(screen, screen.actions().actions(), upgrade);
            assertTrue(read instanceof Action.UseItemOn on && TestItem.worn(screen, on.target()),
                    "the read goes onto a worn piece: " + read);
        }
    }
}
