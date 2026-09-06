package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Pickaxe;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ChaoticCenser;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.curses.Annoying;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inventory and the known appearances carry exactly the identification the player has
 * (ADR-0006, Items, Known appearances): an unknown potion, scroll or ring under its appearance
 * with its class unrecoverable, a level and a curse only once learned, a wand's charges only once
 * known, the identification counters never, in the belongings' order.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ItemLeakTest {

    private static final long SEED = 27_182_818L;

    private HeadlessDriver driver;
    private Hero hero;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        hero = Dungeon.hero;
    }

    private static ItemView view(InventorySection inventory, String name) {
        for (ItemView item : inventory.items()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        throw new AssertionError("no item named " + name + " in " + inventory.items());
    }

    @Test
    @DisplayName("an unknown potion, scroll and ring appear under their appearance, and their class is not recoverable")
    void unknown_items_are_their_appearance() {
        atTheFirstWait();
        PotionOfInvisibility potion = new PotionOfInvisibility();
        ScrollOfTeleportation scroll = new ScrollOfTeleportation();
        RingOfHaste ring = new RingOfHaste();
        assertTrue(potion.collect() && scroll.collect() && ring.collect());
        assertFalse(potion.isKnown() || scroll.isKnown() || ring.isKnown(), "the Warrior starts knowing neither");

        Observer observer = new Observer();
        InventorySection inventory = observer.inventory();
        // The appearance name is what the slot and the item window draw (Potion.java:377-379;
        // Scroll.java:240-242; Ring.java:172-174), and it is not the true name.
        for (Item item : List.of(potion, scroll, ring)) {
            ItemView view = view(inventory, item.name());
            assertNotEquals(item.trueName(), view.name(), "the true name is what the player does not know");
            assertFalse(view.levelKnown() && item instanceof RingOfHaste, "a fresh ring's level is unknown");
        }
        assertEquals(ItemKind.POTION, view(inventory, potion.name()).kind());
        assertEquals(ItemKind.SCROLL, view(inventory, scroll.name()).kind());
        assertEquals(ItemKind.RING, view(inventory, ring.name()).kind());

        Skeleton.Serialized serialized = Skeleton.Serialized.of(Skeleton.everything(observer));
        for (String secret : List.of("PotionOfInvisibility", "Invisibility", "ScrollOfTeleportation",
                "Teleportation", "RingOfHaste", potion.trueName(), scroll.trueName(), ring.trueName())) {
            serialized.assertAbsent(secret);
        }
        serialized.assertPresent(potion.name());
        serialized.assertPresent(scroll.name());
        serialized.assertPresent(ring.name());

        // Identified, the true name is drawn everywhere and the class joins the known appearances.
        potion.identify();
        scroll.identify();
        ring.identify();
        Observer again = new Observer();
        Skeleton.Serialized known = Skeleton.Serialized.of(Skeleton.everything(again));
        known.assertPresent(potion.trueName());
        known.assertPresent(scroll.trueName());
        known.assertPresent(ring.trueName());
        JournalSection journal = again.journal();
        assertTrue(journal.known().contains(new KnownAppearance(ItemKind.POTION, Messages.get(PotionOfInvisibility.class, "name"))));
        assertTrue(journal.known().contains(new KnownAppearance(ItemKind.SCROLL, Messages.get(ScrollOfTeleportation.class, "name"))));
        assertTrue(journal.known().contains(new KnownAppearance(ItemKind.RING, Messages.get(RingOfHaste.class, "name"))));
        assertTrue(view(again.inventory(), ring.name()).levelKnown(), "an identified ring shows its level");
    }

    @Test
    @DisplayName("an unknown level, an unknown curse, unknown charges and the identification counters change no byte")
    void hidden_item_state_is_absent_by_differential() throws Exception {
        atTheFirstWait();
        Dagger dagger = new Dagger();
        WandOfMagicMissile wand = new WandOfMagicMissile();
        RingOfHaste ring = new RingOfHaste();
        assertTrue(dagger.collect() && wand.collect() && ring.collect());
        assertFalse(dagger.levelKnown || dagger.cursedKnown || wand.levelKnown || wand.curChargeKnown,
                "a fresh item is unidentified");

        // The hidden state, one way.
        dagger.level(2);
        dagger.cursed = true;
        dagger.enchantment = new Annoying();
        wand.curCharges = 0;
        set(wand, "usesLeftToID", 3f);
        set(ring, "levelsToID", 0.25f);
        Observer observer = new Observer();
        Observation hidden = Skeleton.everything(observer);
        ItemView daggerView = view(hidden.inventory(), dagger.name());
        assertFalse(daggerView.levelKnown());
        assertEquals(0, daggerView.visiblyUpgraded());
        assertFalse(daggerView.cursedKnown());
        assertFalse(daggerView.visiblyCursed());
        assertFalse(dagger.name().toLowerCase().contains("annoying"),
                "a curse enchantment names itself only once the curse is known (Weapon.java:413)");
        assertEquals("", view(hidden.inventory(), wand.name()).status(), "no status until the level is known (Wand.java:337-342)");

        // The hidden state, the other way: nothing drawn changes, so no byte changes.
        dagger.level(0);
        dagger.cursed = false;
        dagger.enchantment = null;
        wand.curCharges = 2;
        set(wand, "usesLeftToID", 1f);
        set(ring, "levelsToID", 1f);
        Observation plain = Skeleton.everything(new Observer());
        assertEquals(hidden, plain, "an unknown level, curse, charge count and counter are not in the Observation");
        assertArrayEquals(ObservationCodec.encode(hidden), ObservationCodec.encode(plain));
        Skeleton.Serialized.of(hidden).assertAbsent("Annoying");

        // Known, they are drawn: the wand's status shows "?" for the charges and then the count.
        wand.levelKnown = true;
        assertEquals("?/" + wand.maxCharges, view(new Observer().inventory(), wand.name()).status());
        wand.curChargeKnown = true;
        assertEquals(wand.curCharges + "/" + wand.maxCharges, view(new Observer().inventory(), wand.name()).status());
        dagger.level(2);
        dagger.cursed = true;
        dagger.enchantment = new Annoying();
        dagger.identify();
        ItemView identified = view(new Observer().inventory(), dagger.name());
        assertEquals(2, identified.visiblyUpgraded());
        assertTrue(identified.visiblyCursed());
        assertTrue(dagger.name().toLowerCase().contains("annoying"));
    }

    @Test
    @DisplayName("the inventory is the belongings' order: the six slots, then the backpack with a bag before its contents")
    void the_belongings_order() {
        atTheFirstWait();
        RingOfHaste ring = new RingOfHaste();
        hero.belongings.ring = ring;
        // Every hero starts with a velvet pouch (HeroClass.java:111); a stone goes into it.
        VelvetPouch pouch = hero.belongings.getItem(VelvetPouch.class);
        assertNotNull(pouch, "the hero has a velvet pouch: " + hero.belongings.backpack.items);
        StoneOfIntuition stone = new StoneOfIntuition();
        assertTrue(stone.collect(), "a stone goes into the pouch (Bag.canHold)");
        assertTrue(pouch.contains(stone), "the stone is in the pouch: " + pouch.items + " / " + hero.belongings.backpack.items);

        List<ItemView> items = new Observer().inventory().items();
        assertEquals(hero.belongings.weapon.name(), items.get(0).name());
        assertEquals(EquipSlot.WEAPON, items.get(0).slot());
        assertEquals(hero.belongings.armor.name(), items.get(1).name());
        assertEquals(EquipSlot.ARMOR, items.get(1).slot());
        assertEquals(ring.name(), items.get(2).name());
        assertEquals(EquipSlot.RING, items.get(2).slot());
        int atPouch = -1;
        int atStone = -1;
        for (int i = 3; i < items.size(); i++) {
            assertEquals(EquipSlot.NONE, items.get(i).slot(), "the backpack follows the slots");
            if (items.get(i).name().equals(pouch.name())) {
                atPouch = i;
            }
            if (items.get(i).name().equals(stone.name())) {
                atStone = i;
            }
        }
        assertTrue(atPouch >= 3 && atStone == atPouch + 1, "a bag is followed by its contents (Bag.java:231-250)");
        assertEquals(ItemKind.BAG, items.get(atPouch).kind());
        assertEquals(ItemKind.STONE, items.get(atStone).kind());
        assertEquals(ItemKind.WEAPON, items.get(0).kind());
        assertEquals(ItemKind.ARMOR, items.get(1).kind());
        assertEquals(ItemKind.RING, items.get(2).kind());

        // Every item's actions are the item window's buttons; the default is the identifier a quickslot
        // executes, which an item keeps even while it is not offered: an empty waterskin offers no drink
        // and its default stays drink (Waterskin.java:74-78, :52).
        for (ItemView item : items) {
            assertFalse(item.actions().isEmpty(), item.name() + " offers drop and throw at least (Item.java:110-115)");
            assertTrue(item.actions().contains(Item.AC_DROP) && item.actions().contains(Item.AC_THROW), item.actions().toString());
        }
        ItemView waterskin = view(new Observer().inventory(), hero.belongings.getItem(Waterskin.class).name());
        assertEquals("DRINK", waterskin.defaultAction(), "Waterskin.java:43, :52");
        assertFalse(waterskin.actions().contains("DRINK"), "the Warrior's skin starts empty: " + waterskin);
    }

    @Test
    @DisplayName("the known appearances are this Run's identified potions, scrolls and rings by their true names")
    void the_known_appearances() {
        atTheFirstWait();
        JournalSection journal = new Observer().journal();
        // Every hero starts knowing the scroll of identify (HeroClass.java:117), and the Warrior the
        // potion of healing and the scroll of rage besides (HeroClass.java:183-184).
        assertTrue(journal.known().contains(new KnownAppearance(ItemKind.POTION, Messages.get(PotionOfHealing.class, "name"))));
        assertTrue(journal.known().contains(new KnownAppearance(ItemKind.SCROLL, Messages.get(ScrollOfIdentify.class, "name"))));
        assertTrue(journal.known().contains(new KnownAppearance(ItemKind.SCROLL, Messages.get(ScrollOfRage.class, "name"))));
        assertEquals(3, journal.known().size(), "and nothing else: " + journal.known());
        for (KnownAppearance known : journal.known()) {
            assertNotEquals(ItemKind.RING, known.kind());
        }
        // The Catalog is cross-Run state (ADR-0006, Known appearances): seen is not known.
        Catalog.setSeen(PotionOfInvisibility.class);
        assertEquals(journal, new Observer().journal(), "a Catalog entry changes nothing");
    }

    @Test
    @DisplayName("a potion of a known type is one item whether a scroll identified it or it was picked up known")
    void identification_history_is_not_drawn() {
        atTheFirstWait();
        PotionOfInvisibility byScroll = new PotionOfInvisibility();
        byScroll.identify();
        assertTrue(byScroll.levelKnown && byScroll.cursedKnown, "identify() sets both fields (Item.java:468-469)");
        assertTrue(byScroll.collect());
        Observation identified = Skeleton.everything(new Observer());
        ItemView view = view(identified.inventory(), byScroll.name());
        assertTrue(view.levelKnown() && view.cursedKnown());

        byScroll.detach(hero.belongings.backpack);
        PotionOfInvisibility pickedUp = new PotionOfInvisibility();
        assertTrue(pickedUp.isKnown(), "the type is known this Run");
        assertFalse(pickedUp.levelKnown || pickedUp.cursedKnown, "the fields of a potion picked up later are not set");
        assertTrue(pickedUp.collect());
        Observation pickedUpKnown = Skeleton.everything(new Observer());
        assertEquals(identified, pickedUpKnown, "the screen draws both the same: the name, the icon, no level, no curse");
        assertArrayEquals(ObservationCodec.encode(identified), ObservationCodec.encode(pickedUpKnown));
    }

    @Test
    @DisplayName("an artifact shows its charge only once identified and uncursed")
    void the_artifact_status() throws Exception {
        atTheFirstWait();
        CloakOfShadows cloak = new CloakOfShadows();
        assertTrue(cloak.collect());
        assertFalse(cloak.isIdentified());
        set(cloak, "charge", 1);
        Observation one = Skeleton.everything(new Observer());
        assertEquals("", view(one.inventory(), cloak.name()).status(), "nothing until identified (Artifact.java:189-193)");
        set(cloak, "charge", 3);
        Observation three = Skeleton.everything(new Observer());
        assertEquals(one, three, "an unidentified artifact's charge is not drawn");
        cloak.identify();
        assertEquals(cloak.status(), view(new Observer().inventory(), cloak.name()).status());
        assertFalse(cloak.status().isEmpty());
    }

    @Test
    @DisplayName("every family is its package, and every slot its field")
    void families_and_slots() {
        atTheFirstWait();
        CloakOfShadows cloak = new CloakOfShadows();
        RingOfHaste misc = new RingOfHaste();
        Dagger second = new Dagger();
        hero.belongings.artifact = cloak;
        hero.belongings.misc = misc;
        hero.belongings.secondWep = second;
        Map<Item, ItemKind> families = Map.of(new WandOfMagicMissile(), ItemKind.WAND, new ChaoticCenser(), ItemKind.TRINKET,
                new Alchemize(), ItemKind.SPELL, new Bomb(), ItemKind.BOMB, new Pickaxe(), ItemKind.QUEST,
                new Sungrass.Seed(), ItemKind.SEED, new Ankh(), ItemKind.OTHER);
        for (Item item : families.keySet()) {
            assertTrue(item.collect(), item.name());
        }
        List<ItemView> items = new Observer().inventory().items();
        assertEquals(EquipSlot.ARTIFACT, view(new Observer().inventory(), cloak.name()).slot());
        assertEquals(EquipSlot.MISC, view(new Observer().inventory(), misc.name()).slot());
        assertEquals(EquipSlot.SECOND_WEAPON, view(new Observer().inventory(), second.name()).slot());
        assertEquals(ItemKind.ARTIFACT, view(new Observer().inventory(), cloak.name()).kind());
        for (Map.Entry<Item, ItemKind> family : families.entrySet()) {
            assertEquals(family.getValue(), view(new Observer().inventory(), family.getKey().name()).kind(),
                    family.getKey().getClass().getName());
        }
        // The pickaxe is a melee weapon in the quest package, and the family is the package.
        assertEquals(ItemKind.MISSILE, view(new Observer().inventory(), hero.belongings.getItem(
                com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone.class).name()).kind());
        assertEquals(ItemKind.FOOD, view(new Observer().inventory(), hero.belongings.getItem(
                com.shatteredpixel.shatteredpixeldungeon.items.food.Food.class).name()).kind());
        assertTrue(items.size() >= 12, items.toString());
    }

    @Test
    @DisplayName("two readings of one wait are one section, records and bytes")
    void determinism() {
        atTheFirstWait();
        assertTrue(new PotionOfInvisibility().collect() && new WandOfMagicMissile().collect());
        Observer observer = new Observer();
        InventorySection first = observer.inventory();
        JournalSection firstJournal = observer.journal();
        assertEquals(first, observer.inventory());
        assertEquals(firstJournal, observer.journal());
        assertArrayEquals(ObservationCodec.encode(Skeleton.everything(observer)),
                ObservationCodec.encode(Skeleton.everything(new Observer())));
    }

    private static void set(Object owner, String field, float value) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    f.setInt(owner, (int) value);
                } else {
                    f.setFloat(owner, value);
                }
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(field);
    }
}
