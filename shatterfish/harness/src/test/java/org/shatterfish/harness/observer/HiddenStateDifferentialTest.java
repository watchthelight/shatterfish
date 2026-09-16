package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.ItemStatusHandler;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two worlds a player could not tell apart are one Observation, byte for byte (FR-9; non-negotiable
 * 1). Each test takes one Run at its first wait, reads it, changes one kind of hidden state to
 * something the screen does not show, reads again, and holds the bytes identical. Each carries a
 * control: the raw state must really differ, and the same change made where the player can see it
 * must change the bytes, or the pair holds nothing.
 *
 * <p>The worlds are one Run mutated in place rather than two Runs, since two seeds give two floors
 * and the screen would differ before the hidden state did; everything else on the screen is shared
 * by construction, which is what {@code MimicDifferentialTest} relies on too. Hidden state is set
 * through the game's own methods where it has one, and by reflection only where it has none: the
 * labels an unidentified item draws under.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HiddenStateDifferentialTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    /** The rows of ADR-0006's whitelist this suite holds ({@link VisibilityChecklistTest}). */
    static final List<String> ADR_0006_ROWS = List.of("Items", "Known appearances", "Mobs", "Traps", "Terrain",
            "Seed and turn");

    private static final long SEED = 14_142_135L;

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
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        level = Dungeon.level;
        hero = Dungeon.hero;
    }

    @Test
    @DisplayName("the same appearance over a different identity, in the bag and on the floor, is one Observation")
    void unidentified_item_identities() throws Exception {
        atTheFirstWait();
        // Two unknown classes of each family, so that the label the first draws under can be
        // handed to the second: the player sees one colour, rune or gem, and the game knows which
        // potion, scroll or ring it is (ItemStatusHandler.java:38, :179-184; Potion.java:199-207).
        List<Class<? extends Item>> potions = twoUnknown(Generator.Category.POTION.classes, Potion.getKnown());
        List<Class<? extends Item>> scrolls = twoUnknown(Generator.Category.SCROLL.classes, Scroll.getKnown());
        List<Class<? extends Item>> rings = twoUnknown(Generator.Category.RING.classes, Ring.getKnown());
        int cell = floorsInView(1).get(0);

        // World A: the first class of each family, in the backpack and on a seen heap.
        Item potion = Reflection.newInstance(potions.get(0));
        Item scroll = Reflection.newInstance(scrolls.get(0));
        Item ring = Reflection.newInstance(rings.get(0));
        assertTrue(potion.collect() && scroll.collect() && ring.collect());
        Heap heap = level.drop(Reflection.newInstance(potions.get(0)), cell);
        assertTrue(heap.seen, "a heap dropped in view is seen (Level.java:1019)");
        String potionLook = potion.name();
        String scrollLook = scroll.name();
        String ringLook = ring.name();
        int potionImage = potion.image;
        byte[] a = bytes();

        // World B: the labels swapped, and the second class of each family under the first's look.
        swapLabels(Potion.class, potions.get(0), potions.get(1));
        swapLabels(Scroll.class, scrolls.get(0), scrolls.get(1));
        swapLabels(Ring.class, rings.get(0), rings.get(1));
        potion.detach(hero.belongings.backpack);
        scroll.detach(hero.belongings.backpack);
        ring.detach(hero.belongings.backpack);
        heap.destroy();
        Item otherPotion = Reflection.newInstance(potions.get(1));
        Item otherScroll = Reflection.newInstance(scrolls.get(1));
        Item otherRing = Reflection.newInstance(rings.get(1));
        assertTrue(otherPotion.collect() && otherScroll.collect() && otherRing.collect());
        Heap otherHeap = level.drop(Reflection.newInstance(potions.get(1)), cell);
        assertTrue(otherHeap.seen);
        assertEquals(potionLook, otherPotion.name(), "the second potion draws under the first's colour");
        assertEquals(scrollLook, otherScroll.name(), "the second scroll draws under the first's rune");
        assertEquals(ringLook, otherRing.name(), "the second ring draws under the first's gem");
        assertEquals(potionImage, otherPotion.image, "and under its sprite");
        assertNotEquals(potion.trueName(), otherPotion.trueName(), "the identities differ");
        assertNotEquals(scroll.trueName(), otherScroll.trueName());
        assertNotEquals(ring.trueName(), otherRing.trueName());
        assertFalse(otherPotion.isIdentified() || otherScroll.isIdentified() || otherRing.isIdentified());
        byte[] b = bytes();
        assertArrayEquals(a, b, "a different identity under the same appearance is one Observation");

        // The control: identified, the identity is on the screen, and the bytes move.
        otherPotion.identify();
        assertFalse(Arrays.equals(a, bytes()), "an identified potion is a different screen");
    }

    @Test
    @DisplayName("a mob the hero cannot see is one Observation wherever it stands")
    void unseen_mob_positions() {
        atTheFirstWait();
        Mob far = mobOutOfView();
        int elsewhere = floorOutOfView(far.pos);
        byte[] a = bytes();
        move(far, elsewhere);
        assertFalse(level.heroFOV[far.pos]);
        byte[] b = bytes();
        assertArrayEquals(a, b, "an unseen mob's position is not in the Observation");

        // The control: in view, the same mob is an actor, and the bytes move.
        int inView = floorsInView(1).get(0);
        move(far, inView);
        assertTrue(level.heroFOV[far.pos]);
        assertTrue(new Observer().actors().actors().stream().anyMatch(actor -> actor.cell() == inView),
                "drawn in view (GameScene.java:1447; Char.java:1272-1274)");
        assertFalse(Arrays.equals(a, bytes()));
    }

    @Test
    @DisplayName("a hidden trap is one Observation wherever it lies, and so is a revealed trap on an unknown cell")
    void hidden_trap_placement() {
        atTheFirstWait();
        List<Integer> floors = floorsInView(2);
        byte[] none = bytes();

        // A hidden trap under secret-trap terrain (Trap.java:83-91), in view, at one cell and then
        // at another. A frost trap can be hidden and is not in the sewers' pool, so its name is on
        // this floor only if the Observer leaks it.
        plantHidden(floors.get(0));
        byte[] a = bytes();
        assertArrayEquals(none, a, "a hidden trap is a world identical to no trap");
        Level.set(floors.get(0), Terrain.EMPTY);
        assertTrue(level.traps.get(floors.get(0), null) == null, "Level.set removes the trap (Level.java:966-972)");
        plantHidden(floors.get(1));
        byte[] b = bytes();
        assertArrayEquals(a, b, "a hidden trap's cell is not in the Observation");

        // The control: discovered (Level.java:1108-1114), the trap is drawn, and the bytes move.
        level.discover(floors.get(1));
        Optional<TrapView> drawn = trapAt(new Observer().map(), floors.get(1));
        assertTrue(drawn.isPresent() && drawn.get().active(), "a revealed trap is a feature tile");
        assertFalse(Arrays.equals(a, bytes()));
        Level.set(floors.get(1), Terrain.EMPTY);

        // A revealed trap on a cell the fog paints opaque, at one unknown cell and then another.
        List<Integer> unknown = unknownFloors(2);
        byte[] bare = bytes();
        plantRevealed(unknown.get(0));
        byte[] c = bytes();
        assertArrayEquals(bare, c, "a revealed trap under opaque fog is a world identical to no trap");
        Level.set(unknown.get(0), Terrain.EMPTY);
        plantRevealed(unknown.get(1));
        byte[] d = bytes();
        assertArrayEquals(c, d, "its cell is not in the Observation");

        // The control: the cell mapped, the trap is drawn (FogOfWar.java:288-298), and the bytes move.
        level.mapped[unknown.get(1)] = true;
        assertTrue(trapAt(new Observer().map(), unknown.get(1)).isPresent());
        assertFalse(Arrays.equals(c, bytes()));
    }

    @Test
    @DisplayName("a secret door is one Observation whichever wall hides it")
    void secret_door_placement() {
        atTheFirstWait();
        List<Integer> walls = wallsInView(2);
        byte[] none = bytes();
        Level.set(walls.get(0), Terrain.SECRET_DOOR);
        assertTrue(level.secret[walls.get(0)]);
        byte[] a = bytes();
        assertArrayEquals(none, a, "a secret door is a world identical to a wall (DungeonTileSheet.java:464)");
        Level.set(walls.get(0), Terrain.WALL);
        Level.set(walls.get(1), Terrain.SECRET_DOOR);
        assertTrue(level.secret[walls.get(1)]);
        byte[] b = bytes();
        assertArrayEquals(a, b, "which wall hides the door is not in the Observation");

        // The control: discovered, the door is drawn, and the bytes move.
        level.discover(walls.get(1));
        assertEquals(Tile.DOOR, new Observer().map().tiles().get(walls.get(1)));
        assertFalse(Arrays.equals(a, bytes()));
    }

    @Test
    @DisplayName("the generator's state and the seed are one Observation, and a read draws nothing")
    void generator_state() {
        atTheFirstWait();
        byte[] a = bytes();
        long seed = Dungeon.seed;
        Random.pushGenerator(0xC0FFEEL);
        try {
            for (int draw = 0; draw < 10; draw++) {
                Random.Int(1000);
            }
            Dungeon.seed = seed ^ 0x5EEDL;
            byte[] b = bytes();
            assertArrayEquals(a, b, "the generator stack, its position and the seed are not in the Observation");
        } finally {
            Dungeon.seed = seed;
            Random.popGenerator();
        }

        // The control on the other side: the draw after a read is the draw predicted without one,
        // so the Observer consumes no randomness and a Run observed is the Run unobserved.
        Random.pushGenerator(7L);
        long predicted = Random.Long();
        Random.popGenerator();
        Random.pushGenerator(7L);
        try {
            bytes();
            assertEquals(predicted, Random.Long(), "a read consumed a draw");
        } finally {
            Random.popGenerator();
        }
    }

    // --- the worlds

    private static byte[] bytes() {
        Observation observation = new Observer().observe();
        return ObservationCodec.encode(observation);
    }

    /** The first two classes, by name, that the hero does not know. */
    private static List<Class<? extends Item>> twoUnknown(Class<?>[] classes, Set<?> known) {
        List<Class<? extends Item>> unknown = new ArrayList<>();
        for (Class<?> type : classes) {
            if (!known.contains(type)) {
                @SuppressWarnings("unchecked")
                Class<? extends Item> item = (Class<? extends Item>) type;
                unknown.add(item);
            }
        }
        unknown.sort(Comparator.comparing(Class::getSimpleName));
        assertTrue(unknown.size() >= 2, "two unknown classes to swap: " + unknown);
        return unknown.subList(0, 2);
    }

    /**
     * Hands {@code x}'s label to {@code y} and {@code y}'s to {@code x} in the family's handler.
     * The handler's map is private and the game has no method for this, since it never needs one:
     * the labels are drawn once per Run (ItemStatusHandler.java:42-61). The known set is untouched,
     * so both stay unknown, and the label set is untouched, so every image still resolves.
     */
    private static void swapLabels(Class<?> family, Class<?> x, Class<?> y) throws Exception {
        Field handlerField = family.getDeclaredField("handler");
        handlerField.setAccessible(true);
        ItemStatusHandler<?> handler = (ItemStatusHandler<?>) handlerField.get(null);
        Field labelsField = ItemStatusHandler.class.getDeclaredField("itemLabels");
        labelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedHashMap<Class<?>, String> labels = (LinkedHashMap<Class<?>, String>) labelsField.get(handler);
        String ofX = labels.get(x);
        String ofY = labels.get(y);
        assertTrue(ofX != null && ofY != null && !ofX.equals(ofY), x + " and " + y + " draw under different labels");
        labels.put(x, ofY);
        labels.put(y, ofX);
    }

    private void plantHidden(int cell) {
        Trap trap = new FrostTrap();
        level.setTrap(trap.hide(), cell);
        Level.set(cell, Terrain.SECRET_TRAP);
        assertTrue(level.secret[cell] && !trap.visible, "the level holds a hidden trap");
    }

    private void plantRevealed(int cell) {
        Trap trap = new FrostTrap();
        level.setTrap(trap.reveal(), cell);
        Level.set(cell, Terrain.TRAP);
        assertTrue(trap.visible && !level.heroFOV[cell] && !level.visited[cell] && !level.mapped[cell]);
    }

    private static void move(Mob mob, int cell) {
        mob.pos = cell;
        if (mob.sprite != null) {
            mob.sprite.place(cell);
        }
    }

    // --- finding things on the floor

    private Mob mobOutOfView() {
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob) && mob.sprite != null) {
                return mob;
            }
        }
        throw new AssertionError("no mob out of view");
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

    private int floorOutOfView(int not) {
        for (int cell = 0; cell < level.length(); cell++) {
            if (cell != not && level.discoverable[cell] && !level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY
                    && level.heaps.get(cell, null) == null && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no floor out of view");
    }

    private List<Integer> unknownFloors(int count) {
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < level.length() && cells.size() < count; cell++) {
            if (level.discoverable[cell] && !level.heroFOV[cell] && !level.visited[cell] && !level.mapped[cell]
                    && level.map[cell] == Terrain.EMPTY && level.traps.get(cell, null) == null
                    && level.heaps.get(cell, null) == null) {
                cells.add(cell);
            }
        }
        assertEquals(count, cells.size(), "enough unknown floor");
        return cells;
    }

    private List<Integer> wallsInView(int count) {
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < level.length() && cells.size() < count; cell++) {
            if (level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                cells.add(cell);
            }
        }
        assertEquals(count, cells.size(), "enough wall in view");
        return cells;
    }

    private static Optional<TrapView> trapAt(MapSection map, int cell) {
        return map.traps().stream().filter(t -> t.cell() == cell).findFirst();
    }
}
