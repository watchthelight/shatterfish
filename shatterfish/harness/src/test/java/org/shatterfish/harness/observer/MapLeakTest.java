package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisintegrationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Skeleton.Serialized;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The map section carries what the screen draws and nothing the player could not see (ADR-0006,
 * Cell visibility, Terrain, Traps, Heaps): a secret door reads as a wall and a secret trap as
 * floor, a revealed trap on a cell the fog paints opaque is absent, a container shows only its
 * container, and a plain heap shows its top item as the sprite and the title show it. Each rule is
 * held both by the section's records and by a search of the serialized Observation for the
 * hidden name.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MapLeakTest {

    private static final long SEED = 27_182_818L;

    private HeadlessDriver driver;
    private Level level;
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
        level = Dungeon.level;
        hero = Dungeon.hero;
    }

    @Test
    @DisplayName("a secret door reads as a wall and a secret trap as floor, and neither is identifiable")
    void secrets_are_drawn_as_their_cover() {
        atTheFirstWait();
        int door = wallInView();
        int trap = floorsInView(1).get(0);
        Level.set(door, Terrain.SECRET_DOOR);
        // A frost trap can be hidden (five kinds cannot) and is not in the sewers' pool, so its
        // name is on this floor only if the Observer leaks it.
        FrostTrap hidden = new FrostTrap();
        level.setTrap(hidden.hide(), trap);
        Level.set(trap, Terrain.SECRET_TRAP);
        assertTrue(level.secret[door] && level.secret[trap], "the level holds both secrets");
        assertFalse(hidden.visible);

        Observer observer = new Observer();
        MapSection map = observer.map();
        assertEquals(Fog.VISIBLE, map.fog().get(door));
        assertEquals(Tile.WALL, map.tiles().get(door), "a secret door is drawn as a wall (DungeonTileSheet.java:464)");
        assertEquals(Tile.EMPTY, map.tiles().get(trap), "a secret trap is drawn as floor (DungeonTileSheet.java:427)");
        assertTrue(trapAt(map, trap).isEmpty(), "a hidden trap has no feature tile (TerrainFeaturesTilemap.java:59-60)");
        Serialized serialized = Serialized.of(Skeleton.around(observer.header(), map));
        serialized.assertAbsent("SECRET");
        serialized.assertAbsent(hidden.name());

        // The control: once discovered, the same cells read as what they are.
        level.discover(door);
        level.discover(trap);
        MapSection after = new Observer().map();
        assertEquals(Tile.DOOR, after.tiles().get(door));
        assertEquals(Tile.EMPTY, after.tiles().get(trap), "a revealed trap's floor is still floor; the trap is a layer");
        TrapView revealed = trapAt(after, trap).orElseThrow();
        assertEquals(hidden.name(), revealed.kind());
        assertTrue(revealed.active());
        Serialized.of(Skeleton.around(new Observer().header(), after)).assertPresent(hidden.name());
    }

    @Test
    @DisplayName("a trap that is visible on a cell the fog paints opaque does not appear, and appears once the cell is mapped")
    void a_revealed_trap_on_an_unknown_cell_is_absent() {
        atTheFirstWait();
        int far = unknownFloor();
        level.setTrap(new DisintegrationTrap().reveal(), far);
        Level.set(far, Terrain.TRAP);
        assertTrue(level.traps.get(far, null).visible, "the painter and mobs reveal traps out of sight (RegularPainter.java:483-493; Trap.java:92-102)");

        Observer observer = new Observer();
        MapSection map = observer.map();
        assertEquals(Fog.UNKNOWN, map.fog().get(far));
        assertEquals(Tile.NONE, map.tiles().get(far));
        assertTrue(trapAt(map, far).isEmpty(), "the fog paints the cell opaque over the feature tile (FogOfWar.java:200-205)");
        Serialized.of(Skeleton.around(observer.header(), map)).assertAbsent(new DisintegrationTrap().name());

        // Mapped by a scroll, the cell shows its terrain under blue fog, and the trap's tile with it.
        level.mapped[far] = true;
        MapSection mapped = new Observer().map();
        assertEquals(Fog.MAPPED, mapped.fog().get(far));
        assertEquals(Tile.EMPTY, mapped.tiles().get(far));
        assertEquals(new DisintegrationTrap().name(), trapAt(mapped, far).orElseThrow().kind());

        // Disarmed, the trap is drawn black and reads inactive.
        level.traps.get(far, null).disarm();
        assertFalse(trapAt(new Observer().map(), far).orElseThrow().active());
    }

    @Test
    @DisplayName("a container heap exposes only its container, a crystal chest names its category, a plain heap its top item")
    void containers_show_only_themselves() {
        atTheFirstWait();
        List<Integer> cells = floorsInView(9);
        PotionOfStrength strength = new PotionOfStrength();
        Heap locked = level.drop(strength, cells.get(0));
        locked.type = Heap.Type.LOCKED_CHEST;
        WandOfMagicMissile wand = new WandOfMagicMissile();
        Heap crystal = level.drop(wand, cells.get(1));
        crystal.type = Heap.Type.CRYSTAL_CHEST;
        Heap plain = level.drop(new Torch(), cells.get(2));
        PotionOfInvisibility unknown = new PotionOfInvisibility();
        Heap sale = level.drop(unknown, cells.get(3));
        sale.type = Heap.Type.FOR_SALE;
        Heap stack = level.drop(new PotionOfInvisibility().quantity(3), cells.get(4));
        stack.type = Heap.Type.FOR_SALE;
        Heap hidden = level.drop(new Torch(), cells.get(5));
        hidden.hidden = true;
        Heap chest = level.drop(new Torch(), cells.get(6));
        chest.type = Heap.Type.CHEST;
        PotionOfHealing known = new PotionOfHealing();
        level.drop(known, cells.get(7));
        level.drop(new Torch(), cells.get(8));
        Heap several = level.drop(new PotionOfInvisibility(), cells.get(8));
        several.type = Heap.Type.FOR_SALE;
        assertEquals(2, several.size(), "two items in one heap");
        assertTrue(locked.seen && crystal.seen && plain.seen && sale.seen, "dropped in view, a heap is seen (Level.java:991)");
        assertFalse(unknown.isKnown() || strength.isKnown(), "these potions are unidentified at the start");
        assertTrue(known.isKnown(), "the Warrior starts knowing the potion of healing (HeroClass.java:186)");

        Observer observer = new Observer();
        MapSection map = observer.map();
        assertEquals(new HeapView(cells.get(0), HeapKind.LOCKED_CHEST, false, "", 0, ""), heapAt(map, cells.get(0)));
        assertEquals(new HeapView(cells.get(1), HeapKind.CRYSTAL_CHEST, false, "", 0, Messages.get(Heap.class, "wand")),
                heapAt(map, cells.get(1)));
        assertEquals(new HeapView(cells.get(2), HeapKind.HEAP, false, new Torch().title(), 0, ""), heapAt(map, cells.get(2)));
        assertEquals(new HeapView(cells.get(3), HeapKind.FOR_SALE, false, unknown.title(), Shopkeeper.sellPrice(unknown), ""),
                heapAt(map, cells.get(3)), "a single for-sale item shows its price in the heap's title (Heap.java:371-374)");
        assertEquals(new HeapView(cells.get(4), HeapKind.FOR_SALE, false, stack.peek().title(),
                        Shopkeeper.sellPrice(stack.peek()), ""), heapAt(map, cells.get(4)),
                "a stacked item is one entry, so its title prints the stack's price (Heap.java:371-374)");
        assertEquals(new HeapView(cells.get(8), HeapKind.FOR_SALE, false, several.peek().title(), 0, ""),
                heapAt(map, cells.get(8)), "a heap of several items prints no price (Heap.java:375-376)");
        assertTrue(heapAt(map, cells.get(5)).hidden(), "a hidden heap is drawn faint and flagged (ItemSprite.java:236)");
        assertEquals(new HeapView(cells.get(6), HeapKind.CHEST, false, "", 0, ""), heapAt(map, cells.get(6)));
        assertEquals(known.title(), heapAt(map, cells.get(7)).item(), "a known potion shows its name");

        Serialized serialized = Serialized.of(Skeleton.around(observer.header(), map));
        serialized.assertAbsent(strength.title());
        serialized.assertAbsent("Strength");
        serialized.assertAbsent(wand.title());
        serialized.assertAbsent("Magic Missile");
        serialized.assertAbsent("Invisibility");
        serialized.assertPresent(unknown.title());
        serialized.assertPresent(known.title());
        serialized.assertPresent(new Torch().title());
        serialized.assertPresent(Messages.get(Heap.class, "wand"));
    }

    @Test
    @DisplayName("a heap never seen is absent, an empty heap is absent, and the map is the floor's size")
    void unseen_and_empty_heaps() {
        atTheFirstWait();
        int far = unknownFloor();
        Heap unseen = level.drop(new Torch(), far);
        assertFalse(unseen.seen);
        int emptied = floorsInView(1).get(0);
        Heap empty = level.drop(new Torch(), emptied);
        empty.items.clear();

        Observer observer = new Observer();
        MapSection map = observer.map();
        assertTrue(map.heaps().stream().noneMatch(h -> h.cell() == far), "a heap out of sight and never seen has no sprite");
        assertTrue(map.heaps().stream().noneMatch(h -> h.cell() == emptied), "an empty heap's sprite is blank (ItemSprite.java:213-215)");
        assertEquals(level.width(), map.width());
        assertEquals(level.height(), map.height());
        assertEquals(Fog.VISIBLE, map.fog().get(hero.pos));
        assertEquals(Fog.UNKNOWN, map.fog().get(far));
        // The fog itself, cell for cell against the painted texture, is FogParityTest's.
    }

    private int wallInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                return cell;
            }
        }
        throw new AssertionError("no wall in view");
    }

    private List<Integer> floorsInView(int count) {
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < level.length() && cells.size() < count; cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null) {
                cells.add(cell);
            }
        }
        assertEquals(count, cells.size(), "enough free floor in view");
        return cells;
    }

    private int unknownFloor() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.discoverable[cell] && !level.heroFOV[cell] && !level.visited[cell] && !level.mapped[cell]
                    && level.map[cell] == Terrain.EMPTY && level.traps.get(cell, null) == null
                    && level.heaps.get(cell, null) == null) {
                return cell;
            }
        }
        throw new AssertionError("no unknown floor cell");
    }

    private static Optional<TrapView> trapAt(MapSection map, int cell) {
        return map.traps().stream().filter(t -> t.cell() == cell).findFirst();
    }

    private static HeapView heapAt(MapSection map, int cell) {
        return map.heaps().stream().filter(h -> h.cell() == cell).findFirst().orElseThrow();
    }
}
