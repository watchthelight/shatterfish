package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MindVision;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTileSheet;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.TrapView;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same world with and without blindness, mind vision and magic mapping changes the Observation
 * exactly as it changes the screen (FR-10; ADR-0006, Vision buffs). Each effect is applied through
 * the game's own buff or scroll and the game's own observe, and the Observation's difference from
 * the untoggled read is held to an exact set of parts ({@link ObservationDiff}), with the rest
 * still; then the effect is taken off, and what the screen remembers is held apart from what it
 * forgets.
 *
 * <p>The expectation for the field of view is the game's own {@code heroFOV} after
 * {@code Dungeon.observe()}, never a recomputation; where the story's acceptance names a shape,
 * the three-by-three block a blinded hero sees, the game's array is held to that shape as a check on
 * the citation and the Observation is held to the array. {@code FogParityTest} holds the fog to the
 * painted texture cell by cell under the same three effects; this suite holds the whole Observation.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class VisionToggleTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    /** The rows of ADR-0006's whitelist this suite holds ({@link VisibilityChecklistTest}). */
    static final List<String> ADR_0006_ROWS = List.of("Vision buffs", "Cell visibility");

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
    @DisplayName("blinded, the field of view is the three-by-three block the game computes, and only the fog, the actors, the blobs and the buff move")
    void blindness() {
        atTheFirstWait();
        // A mob and a gas in view but outside the block, so that losing them is exercised: the mob
        // moved there the way the actor suite moves one, the gas seeded through the game's own
        // method and given its emitter by the scene (GameScene.java:1131-1136).
        Set<Integer> block = block(hero.pos);
        List<Integer> outside = floorsInViewOutside(block, 2);
        Mob far = mobOutOfView();
        far.pos = outside.get(0);
        far.sprite.place(far.pos);
        GameScene.add(Blob.seed(outside.get(1), 20, ToxicGas.class));
        Observation before = observe();
        boolean[] fovBefore = level.heroFOV.clone();
        assertTrue(before.actors().actors().stream().anyMatch(a -> a.cell() == outside.get(0)), "the mob is drawn before");
        assertTrue(before.map().blobs().stream().anyMatch(b -> b.cell() == outside.get(1)), "the gas is drawn before");

        Buff.affect(hero, Blindness.class, 10f);
        Dungeon.observe();

        // The game's own array is the block: an unsighted char's view is cleared and the
        // discoverable cells within sense radius 1 are copied back (Level.java:1318-1319,
        // :1370-1372, :1374, :1386-1406), which with rounding[1][1] == 1 (ShadowCaster.java:35-45)
        // is the nine cells around the hero, clipped to the map.
        for (int cell = 0; cell < level.length(); cell++) {
            assertEquals(block.contains(cell) && level.discoverable[cell], level.heroFOV[cell],
                    "cell " + cell + " (Level.java:1386-1406)");
        }

        Observation blind = observe();
        Set<String> diff = ObservationDiff.of(before, blind);
        assertTrue(Set.of("map.fog", "actors", "map.blobs", "hero.buffs", "log").containsAll(diff), "only what the screen changes: " + diff);
        assertEquals(Set.of("map.fog", "actors", "map.blobs", "hero.buffs", "log"), diff, "and all of it");

        // The log: the hero logs a buff's message when the buff is added (Hero.java:2130-2136;
        // Buff.java:118-125; actors.properties, blindness.heromsg), so it is on the screen, and
        // nothing else is. The buff's announced flag is the floating text over the sprite
        // (Char.java:1234-1246), not the log.
        assertEquals(List.of(hero.buff(Blindness.class).heroMessage()), said(before.log().lines(), blind.log().lines()));

        // The fog: in view only inside the block; outside it, what was in view is now remembered,
        // since the observe at the wait before ORed the view into visited (Dungeon.java:930-938),
        // and nothing else moves.
        for (int cell = 0; cell < level.length(); cell++) {
            Fog was = before.map().fog().get(cell);
            Fog now = blind.map().fog().get(cell);
            if (!block.contains(cell)) {
                assertEquals(was == Fog.VISIBLE ? Fog.VISITED : was, now, "cell " + cell + " outside the block");
            } else if (level.discoverable[cell] && !DungeonTileSheet.wallStitcheable(level.map[cell])) {
                assertEquals(Fog.VISIBLE, now, "a floor cell of the block is in view: " + cell);
            } else {
                // A wall paints by its neighbours (FogOfWar.java:210-267), which FogParityTest holds.
                assertTrue(now != Fog.UNKNOWN || was == Fog.UNKNOWN, "cell " + cell);
            }
        }
        assertTrue(block.containsAll(ObservationDiff.cellsAt(blind, Fog.VISIBLE)), "nothing is in view outside the block");

        // The actors: the ones drawn before whose cell is still in view, no one else.
        List<ActorView> expected = new ArrayList<>();
        for (ActorView actor : before.actors().actors()) {
            if (level.heroFOV[actor.cell()]) {
                expected.add(actor);
            }
        }
        assertEquals(expected, blind.actors().actors(), "a mob is drawn exactly in the field of view");
        assertTrue(blind.actors().actors().stream().noneMatch(a -> a.cell() == outside.get(0)), "the mob outside the block is gone");
        assertTrue(blind.map().blobs().stream().noneMatch(b -> b.cell() == outside.get(1)),
                "the gas outside the block is gone: the emitter draws in view only (BlobEmitter.java:47-70)");

        // The buff: the blindness icon, and nothing else.
        assertEquals(List.of(hero.buff(Blindness.class).name()), added(before.hero().buffs(), blind.hero().buffs()));

        // Taken off (Blindness.detach observes, Blindness.java:37-40), the screen is what it was
        // but for the log, which remembers the announcement: the block was already remembered, so
        // nothing new was learnt of the floor.
        hero.buff(Blindness.class).detach();
        assertArrayEquals(level.heroFOV, fovBefore, "the field of view returns");
        Observation after = observe();
        assertEquals(Set.of("log"), ObservationDiff.of(before, after), "the original screen, and the log");
        assertEquals(blind.log(), after.log(), "taking the buff off says nothing");
    }

    @Test
    @DisplayName("with mind vision every mob is drawn and the fog opens around each, and taken off the screen keeps what it learnt")
    void mind_vision() {
        atTheFirstWait();
        Observation before = observe();
        boolean[] fovBefore = level.heroFOV.clone();
        assertTrue(before.actors().actors().size() < drawableMobs(), "the first floor has a mob the hero cannot see");

        Buff.affect(hero, MindVision.class, 5f);
        Dungeon.observe();

        // The citation, held on the game's own array both ways, as the blind block is: a cell newly
        // in view is within the rounded radius of 2 the buff gives (Level.java:1377-1379, :1386-1406;
        // MindVision.java:32) or within one cell of a mob that is neither a hidden mimic nor an
        // object (Level.java:1433-1434, :1457-1468); and every discoverable cell within that radius,
        // and every cell around such a mob the sighted view did not reach, is in view.
        for (int cell = 0; cell < level.length(); cell++) {
            assertTrue(!fovBefore[cell] || level.heroFOV[cell], "the view only grows: " + cell);
            if (level.heroFOV[cell] && !fovBefore[cell]) {
                assertTrue(withinRoundedTwo(hero.pos, cell) || nextToAMob(cell), "cell " + cell + " is in view by no rule");
            }
            if (withinRoundedTwo(hero.pos, cell) && level.discoverable[cell]) {
                assertTrue(level.heroFOV[cell], "a discoverable cell within the radius is in view: " + cell);
            }
        }
        int mobsOpened = 0;
        for (Mob mob : level.mobs) {
            if (stealthyNeutralMimic(mob) || Char.hasProp(mob, Char.Property.OBJECT) || fovBefore[mob.pos]) {
                continue;
            }
            mobsOpened++;
            for (int offset : PathFinder.NEIGHBOURS9) {
                assertTrue(level.heroFOV[mob.pos + offset], "the cells around " + mob.name() + " at " + mob.pos + " are in view");
            }
        }
        assertTrue(mobsOpened > 0, "a mob the sighted view did not reach");

        Observation seeing = observe();
        Set<String> diff = ObservationDiff.of(before, seeing);
        assertTrue(Set.of("map.fog", "map.tiles", "map.heaps", "map.traps", "map.transitions", "actors", "hero.buffs")
                .containsAll(diff), "only what the screen changes: " + diff);
        assertTrue(diff.containsAll(Set.of("map.fog", "actors", "hero.buffs")), diff.toString());

        // The fog only opens: a floor cell is in view exactly where the game's array says.
        for (int cell = 0; cell < level.length(); cell++) {
            Fog was = before.map().fog().get(cell);
            Fog now = seeing.map().fog().get(cell);
            assertTrue(now.ordinal() <= was.ordinal(), "cell " + cell + " got darker");
            if (level.discoverable[cell] && !DungeonTileSheet.wallStitcheable(level.map[cell])) {
                assertEquals(level.heroFOV[cell], now == Fog.VISIBLE, "cell " + cell);
            }
        }
        // A tile appears exactly where a cell stopped being unknown.
        for (int cell : ObservationDiff.tileCells(before, seeing)) {
            assertEquals(Fog.UNKNOWN, before.map().fog().get(cell), "a tile moved on a known cell: " + cell);
            assertTrue(seeing.map().tiles().get(cell) != Tile.NONE);
        }
        // Every mob whose cell is in view is drawn, and someone new is.
        assertEquals(drawnMobs(), cells(seeing.actors().actors()), "a mob is drawn exactly in the field of view");
        assertTrue(seeing.actors().actors().size() > before.actors().actors().size());
        // A heap in view is seen and stays seen (Level.java:1521-1525): the heaps drawn are exactly
        // the seen ones on cells that are not unknown, and a trap or a way down newly drawn sits on
        // a cell that was unknown before.
        assertEquals(seenHeaps(seeing), seeing.map().heaps());
        assertTrue(seeing.map().heaps().containsAll(before.map().heaps()));
        newlyKnown(before, seeing);
        assertEquals(List.of(hero.buff(MindVision.class).name()), added(before.hero().buffs(), seeing.hero().buffs()));

        // Taken off (MindVision.detach observes, MindVision.java:48-53): the view is what it was,
        // and the screen remembers the cells it saw and the heaps on them. A test that expected
        // the original bytes back would be wrong about the screen.
        hero.buff(MindVision.class).detach();
        assertArrayEquals(level.heroFOV, fovBefore, "the field of view returns");
        Observation after = observe();
        Set<String> memory = ObservationDiff.of(before, after);
        assertTrue(Set.of("map.fog", "map.tiles", "map.heaps", "map.traps", "map.transitions").containsAll(memory),
                "only memory remains: " + memory);
        assertEquals(seenHeaps(after), after.map().heaps());
        newlyKnown(before, after);
        assertEquals(ObservationDiff.cellsAt(before, Fog.VISIBLE), ObservationDiff.cellsAt(after, Fog.VISIBLE));
        for (int cell : ObservationDiff.fogCells(before, after)) {
            assertEquals(Fog.UNKNOWN, before.map().fog().get(cell), "cell " + cell);
            assertEquals(Fog.VISITED, after.map().fog().get(cell), "a cell seen once is remembered: " + cell);
        }
        assertEquals(before.actors(), after.actors());
        assertEquals(before.hero(), after.hero());
    }

    @Test
    @DisplayName("a scroll of magic mapping read the game's way maps the floor and discovers its secrets, and reveals no mob")
    void magic_mapping() {
        atTheFirstWait();
        // Two secrets in view, so that the scroll's discovery shows on the screen (ScrollOfMagicMapping.java:44-73).
        int door = wallInView();
        int trapCell = floorInView();
        Level.set(door, Terrain.SECRET_DOOR);
        Trap trap = new FrostTrap();
        level.setTrap(trap.hide(), trapCell);
        Level.set(trapCell, Terrain.SECRET_TRAP);
        ScrollOfMagicMapping scroll = new ScrollOfMagicMapping();
        assertTrue(scroll.collect(hero.belongings.backpack));
        assertFalse(scroll.isIdentified());
        String rune = scroll.name();
        Observation before = observe();
        assertEquals(Tile.WALL, before.map().tiles().get(door));
        assertTrue(trapAt(before, trapCell).isEmpty());
        assertTrue(before.map().transitions().stream().noneMatch(t -> t.kind() == TransitionKind.REGULAR_EXIT),
                "the way down is not in view at the first wait: " + before.map().transitions());

        // Reading takes a turn, so the world after is the next Input wait's: the effect is held
        // exactly, and the actors, whose turn also passed, to the drawing rule. The turn could move
        // other parts too, the hero's numbers, a heap a mob eats, a gas; on this seed at this wait
        // it moves none, which the set below pins, and a part that moves elsewhere names itself.
        scroll.execute(hero, Scroll.AC_READ);
        driver.stepToInputWait();
        Observation mapped = observe();
        Set<String> diff = ObservationDiff.of(before, mapped);
        assertTrue(Set.of("map.fog", "map.tiles", "map.traps", "map.transitions", "actors", "inventory", "journal.known", "log")
                .containsAll(diff), "only what the screen changes: " + diff);
        assertTrue(diff.containsAll(Set.of("map.fog", "map.tiles", "map.traps", "map.transitions", "inventory", "journal.known", "log")),
                diff.toString());

        // The fog: every discoverable cell is mapped; what changed was unknown and is now mapped.
        for (int cell = 0; cell < level.length(); cell++) {
            assertTrue(!level.discoverable[cell] || mapped.map().fog().get(cell) != Fog.UNKNOWN, "cell " + cell);
        }
        // What changed was unknown and is now mapped, with one exception the screen makes: a wall
        // is painted by the cells its face belongs to (FogOfWar.java:210-267), so a wall in view
        // whose far side was unknown is painted opaque, emitted at the examine level, and once the
        // far side is mapped is painted mapped, blue over a wall the hero stands beside.
        for (int cell : ObservationDiff.fogCells(before, mapped)) {
            assertEquals(Fog.MAPPED, mapped.map().fog().get(cell), "mapped, not seen (FogOfWar.java:288-298): " + cell);
            Fog was = before.map().fog().get(cell);
            assertTrue(was == Fog.UNKNOWN || (was == Fog.VISITED && DungeonTileSheet.wallStitcheable(level.map[cell])),
                    "cell " + cell + " was " + was);
        }
        // The tiles: a tile appears where a cell stopped being unknown, and the secrets are drawn
        // as what they are (Level.java:1108-1114).
        for (int cell : ObservationDiff.tileCells(before, mapped)) {
            assertTrue(before.map().fog().get(cell) == Fog.UNKNOWN || cell == door, "a tile moved on a known cell: " + cell);
        }
        assertEquals(Tile.DOOR, mapped.map().tiles().get(door), "the secret door is a door");
        TrapView revealed = trapAt(mapped, trapCell).orElseThrow();
        assertEquals(trap.name(), revealed.kind());
        assertTrue(revealed.active());
        // Every trap now drawn is visible on a cell that is not unknown, and the floor's own hidden
        // traps are among them.
        List<TrapView> traps = new ArrayList<>();
        for (Trap t : level.traps.valueList()) {
            if (t.visible && mapped.map().fog().get(t.pos) != Fog.UNKNOWN) {
                traps.add(new TrapView(t.pos, t.name(), t.active));
            }
        }
        traps.sort((x, y) -> Integer.compare(x.cell(), y.cell()));
        assertEquals(traps, mapped.map().traps());
        assertTrue(traps.size() > before.map().traps().size() + 1, "the floor's own traps are discovered");
        // The way down is drawn now that its cell is mapped.
        assertTrue(mapped.map().transitions().stream().anyMatch(t -> t.kind() == TransitionKind.REGULAR_EXIT),
                "the exit: " + mapped.map().transitions());
        // The actors: drawn exactly in the field of view, and no mob on a cell that is merely
        // mapped (updateFieldOfView never reads mapped, Level.java:1313-1406).
        assertEquals(drawnMobs(), cells(mapped.actors().actors()));
        for (ActorView actor : mapped.actors().actors()) {
            assertEquals(Fog.VISIBLE, mapped.map().fog().get(actor.cell()));
        }
        int onMappedCells = 0;
        for (Mob mob : level.mobs) {
            onMappedCells += mapped.map().fog().get(mob.pos) == Fog.MAPPED ? 1 : 0;
        }
        assertTrue(onMappedCells > 0, "a mob stands on a mapped cell the screen does not show it on");
        // Nor does mapping show a heap: a heap is seen only in view (Level.java:1521-1525), so one
        // on a merely mapped cell exists and is not drawn.
        int heapsOnMappedCells = 0;
        for (Heap heap : level.heaps.valueList()) {
            if (!heap.seen && mapped.map().fog().get(heap.pos) == Fog.MAPPED) {
                heapsOnMappedCells++;
                assertTrue(mapped.map().heaps().stream().noneMatch(h -> h.cell() == heap.pos), "an unseen heap is drawn at " + heap.pos);
            }
        }
        assertTrue(heapsOnMappedCells > 0, "a heap lies on a mapped cell the screen does not show it on");
        assertEquals(before.map().heaps(), mapped.map().heaps());
        // The scroll is gone from the bag and known in the journal; the log says the layout appeared.
        List<ItemView> left = new ArrayList<>(before.inventory().items());
        left.removeIf(item -> item.kind() == ItemKind.SCROLL && item.name().equals(rune));
        assertEquals(left, mapped.inventory().items());
        assertEquals(List.of(new KnownAppearance(ItemKind.SCROLL, Messages.get(ScrollOfMagicMapping.class, "name"))),
                learnt(before.journal().known(), mapped.journal().known()));
        assertTrue(mapped.log().lines().stream().anyMatch(line -> line.text().equals(Messages.get(ScrollOfMagicMapping.class, "layout"))));
    }

    // --- the reads and the shapes

    private static Observation observe() {
        return new Observer().observe();
    }

    /** The nine cells around {@code pos} that are on the map: NEIGHBOURS9, clipped. */
    private Set<Integer> block(int pos) {
        int width = level.width();
        int x = pos % width;
        int y = pos / width;
        Set<Integer> block = new TreeSet<>();
        for (int offset : PathFinder.NEIGHBOURS9) {
            int cell = pos + offset;
            if (cell >= 0 && cell < level.length() && Math.abs(cell % width - x) <= 1 && Math.abs(cell / width - y) <= 1) {
                block.add(cell);
            }
        }
        return block;
    }

    /**
     * Within the rounded circle of radius 2, which is the shape the copy loop yields from
     * {@code ShadowCaster.rounding[2]} — half-widths 2, 2 and 1 for rows 0, 1 and 2 away
     * (ShadowCaster.java:35-45; Level.java:1386-1406): five by five without the corners. A shape
     * the game's array is held to, as the blind block is, never the expectation itself.
     */
    private boolean withinRoundedTwo(int from, int cell) {
        int width = level.width();
        int dx = Math.abs(cell % width - from % width);
        int dy = Math.abs(cell / width - from / width);
        return dx <= 2 && dy <= 2 && dx + dy <= 3;
    }

    /** The mimic the mind-vision loop skips, in the game's own words (Level.java:1458). */
    private static boolean stealthyNeutralMimic(Mob mob) {
        return mob instanceof Mimic mimic && mimic.alignment == Char.Alignment.NEUTRAL && mimic.stealthy();
    }

    /** The heaps the map draws: seen, on a cell that is not unknown (Level.java:1521-1525; ItemSprite.java:323-326). */
    private List<HeapView> seenHeaps(Observation observation) {
        List<HeapView> heaps = new ArrayList<>();
        for (HeapView heap : observation.map().heaps()) {
            heaps.add(heap);
        }
        List<Integer> expected = new ArrayList<>();
        for (Heap heap : level.heaps.valueList()) {
            if (heap.seen && observation.map().fog().get(heap.pos) != Fog.UNKNOWN && heap.size() > 0) {
                expected.add(heap.pos);
            }
        }
        expected.sort(Integer::compare);
        List<Integer> drawn = new ArrayList<>();
        for (HeapView heap : heaps) {
            if (Observer.hiddenMimic(mobAt(heap.cell()))) {
                continue;
            }
            drawn.add(heap.cell());
        }
        drawn.sort(Integer::compare);
        assertEquals(expected, drawn, "the heaps drawn are the seen ones on known cells");
        return heaps;
    }

    private Mob mobAt(int cell) {
        for (Mob mob : level.mobs) {
            if (mob.pos == cell) {
                return mob;
            }
        }
        return null;
    }

    /** A trap or a way down drawn in {@code after} and not in {@code before} sits on a cell unknown before. */
    private static void newlyKnown(Observation before, Observation after) {
        for (TrapView trap : after.map().traps()) {
            if (!before.map().traps().contains(trap)) {
                assertEquals(Fog.UNKNOWN, before.map().fog().get(trap.cell()), "a trap appeared on a known cell: " + trap);
            }
        }
        for (TransitionView transition : after.map().transitions()) {
            if (!before.map().transitions().contains(transition)) {
                assertEquals(Fog.UNKNOWN, before.map().fog().get(transition.cell()), "a way appeared on a known cell: " + transition);
            }
        }
    }

    private boolean nextToAMob(int cell) {
        for (Mob mob : level.mobs) {
            if (stealthyNeutralMimic(mob) || Char.hasProp(mob, Char.Property.OBJECT)) {
                continue;
            }
            if (level.distance(mob.pos, cell) <= 1) {
                return true;
            }
        }
        return false;
    }

    /** The cells of every mob the scene draws: in view and not a hidden mimic, ascending. */
    private List<Integer> drawnMobs() {
        List<Integer> cells = new ArrayList<>();
        for (Mob mob : level.mobs) {
            if (level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob)) {
                cells.add(mob.pos);
            }
        }
        cells.sort(Integer::compare);
        return cells;
    }

    private int drawableMobs() {
        int count = 0;
        for (Mob mob : level.mobs) {
            count += Observer.hiddenMimic(mob) ? 0 : 1;
        }
        return count;
    }

    private static List<Integer> cells(List<ActorView> actors) {
        List<Integer> cells = new ArrayList<>();
        for (ActorView actor : actors) {
            cells.add(actor.cell());
        }
        cells.sort(Integer::compare);
        return cells;
    }

    private static List<String> added(List<BuffView> before, List<BuffView> after) {
        List<String> names = new ArrayList<>();
        for (BuffView buff : after) {
            if (!before.contains(buff)) {
                names.add(buff.name());
            }
        }
        assertTrue(after.containsAll(before), "a buff went missing");
        return names;
    }

    private static List<String> said(List<LogLine> before, List<LogLine> after) {
        assertTrue(before.size() < LogSection.MAX_LINES, "the log has room for the announcement");
        assertEquals(before, after.subList(0, before.size()), "the log keeps its lines");
        List<String> texts = new ArrayList<>();
        for (LogLine line : after.subList(before.size(), after.size())) {
            texts.add(line.text());
        }
        return texts;
    }

    private static List<KnownAppearance> learnt(List<KnownAppearance> before, List<KnownAppearance> after) {
        List<KnownAppearance> added = new ArrayList<>(after);
        added.removeAll(before);
        assertTrue(after.containsAll(before), "a known appearance went missing");
        return added;
    }

    private static java.util.Optional<TrapView> trapAt(Observation observation, int cell) {
        return observation.map().traps().stream().filter(t -> t.cell() == cell).findFirst();
    }

    private Mob mobOutOfView() {
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob) && mob.sprite != null) {
                return mob;
            }
        }
        throw new AssertionError("no mob out of view");
    }

    /** Free floor in view and outside {@code block}, {@code count} cells, not adjacent to each other. */
    private List<Integer> floorsInViewOutside(Set<Integer> block, int count) {
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < level.length() && cells.size() < count; cell++) {
            if (level.heroFOV[cell] && !block.contains(cell) && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null && apart(cells, cell)) {
                cells.add(cell);
            }
        }
        assertEquals(count, cells.size(), "enough free floor in view outside the block");
        return cells;
    }

    private boolean apart(List<Integer> cells, int cell) {
        for (int other : cells) {
            if (level.distance(other, cell) <= 1) {
                return false;
            }
        }
        return true;
    }

    private int wallInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                return cell;
            }
        }
        throw new AssertionError("no wall in view");
    }

    private int floorInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no free floor in view");
    }
}
