package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.LockedFloor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.watabou.utils.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Skeleton.Serialized;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facts the floor itself shows (ADR-0006, Floor feeling, Transitions, Boss lock): the feeling
 * the depth button draws, the stairs and exits the player has seen at the cells the game
 * designates for them, and the lock a boss fight puts on the floor. Paths abbreviate
 * {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}, at the tag.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class FloorSectionTest {

    /** The rows of ADR-0006's whitelist this suite holds ({@link VisibilityChecklistTest}). */
    static final List<String> ADR_0006_ROWS = List.of("Floor feeling", "Transitions", "Boss lock");

    private static final long SEED = 57_721_566L;

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
    @DisplayName("the feeling is the floor's own, which the depth button draws as an icon")
    void the_feeling_is_the_depth_button_s() {
        atTheFirstWait();
        assertEquals(Feeling.valueOf(level.feeling.name()), new Observer().map().feeling(),
                "the icon the depth button draws (…/ui/MenuPane.java:88-89; …/ui/Icons.java:478-497)");

        // Every feeling the game has reaches the section under its own name: the button draws an
        // icon for each, its hover text the description and its window the title
        // (MenuPane.java:98-116), and the floor's arrival line names it (…/scenes/GameScene.java:670-689).
        for (Level.Feeling feeling : Level.Feeling.values()) {
            level.feeling = feeling;
            assertEquals(Feeling.valueOf(feeling.name()), new Observer().map().feeling(), feeling.name());
        }
        level.feeling = Level.Feeling.WATER;
        Serialized.of(new Observer().observe()).assertPresent("WATER");
    }

    @Test
    @DisplayName("a transition is carried at the cell the game designates, once the player has seen it")
    void transitions_are_the_ones_seen() {
        atTheFirstWait();
        LevelTransition surface = transitionOfType(LevelTransition.Type.SURFACE);
        LevelTransition exit = transitionOfType(LevelTransition.Type.REGULAR_EXIT);
        assertNotNull(surface, "depth 1 leaves for the surface (…/levels/rooms/standard/entrance/EntranceRoom.java:91-95)");
        assertNotNull(exit, "every floor has a way down");

        MapSection map = new Observer().map();
        assertEquals(Fog.VISIBLE, map.fog().get(surface.cell()), "the hero starts on the stairs it came by");
        assertEquals(TransitionKind.SURFACE, transitionAt(map, surface.cell()).orElseThrow().kind());
        assertEquals(Fog.UNKNOWN, map.fog().get(exit.cell()), "the way down is somewhere unseen");
        assertTrue(transitionAt(map, exit.cell()).isEmpty(), "a transition the player has not seen is not carried");
        assertEquals(seen(map), cells(map), "exactly the transitions whose cell the player has seen, by cell");
        Serialized.of(new Observer().observe()).assertAbsent(TransitionKind.REGULAR_EXIT.name());

        // Mapped, as a scroll of magic mapping leaves it: the tile is drawn, so the transition is.
        level.mapped[exit.cell()] = true;
        MapSection mapped = new Observer().map();
        assertEquals(Fog.MAPPED, mapped.fog().get(exit.cell()));
        assertEquals(TransitionKind.REGULAR_EXIT, transitionAt(mapped, exit.cell()).orElseThrow().kind());
        assertEquals(seen(mapped), cells(mapped), "the way down joins them");
        assertEquals(2, mapped.transitions().size(), "the floor's two transitions, both seen now");

        // A transition is a rectangle, and the boss floors make theirs wider than the stairs they
        // paint (…/levels/CavesBossLevel.java:158-163); the cell carried is the one the game
        // designates, not a corner of the rectangle.
        Point at = level.cellToPoint(surface.cell());
        surface.set(at.x - 1, at.y - 1, at.x + 1, at.y + 1);
        assertTrue(surface.inside(surface.cell()) && surface.width() == 3, "a region of nine cells now");
        MapSection wide = new Observer().map();
        assertEquals(TransitionKind.SURFACE, transitionAt(wide, surface.cell()).orElseThrow().kind());
        assertTrue(transitionAt(wide, level.pointToCell(new Point(at.x - 1, at.y - 1))).isEmpty(),
                "the corner of the region is not the transition's cell");
        assertEquals(seen(wide), cells(wide));
    }

    /** The cells of the transitions the player has seen, in the order the record fixes. */
    private List<Integer> seen(MapSection map) {
        return level.transitions.stream()
                .filter(transition -> map.fog().get(transition.cell()) != Fog.UNKNOWN)
                .map(LevelTransition::cell)
                .sorted()
                .toList();
    }

    private static List<Integer> cells(MapSection map) {
        return map.transitions().stream().map(TransitionView::cell).toList();
    }

    @Test
    @DisplayName("the sealed flag is the boss lock the floor is under")
    void the_sealed_flag_is_the_boss_lock() {
        atTheFirstWait();
        assertFalse(new Observer().header().sealed(), "the sewers' first floor locks nobody in");

        // seal() is what every boss level calls when its fight begins: the flag and the buff whose
        // icon the HUD shows (…/levels/Level.java:617-630; …/actors/buffs/LockedFloor.java:76-78).
        level.seal();
        assertTrue(level.locked);
        assertTrue(new Observer().header().sealed(), "the flag the descent is refused by");
        assertTrue(new Observer().hero().buffs().stream().anyMatch(buff -> buff.name().equals(lockedName())),
                "the buff the lock brings with it is on the HUD");

        level.unseal();
        assertFalse(new Observer().header().sealed());
        assertTrue(new Observer().hero().buffs().stream().noneMatch(buff -> buff.name().equals(lockedName())));
    }

    private String lockedName() {
        LockedFloor buff = hero.buff(LockedFloor.class);
        return buff != null ? buff.name() : new LockedFloor().name();
    }

    private static Optional<TransitionView> transitionAt(MapSection map, int cell) {
        return map.transitions().stream().filter(transition -> transition.cell() == cell).findFirst();
    }

    private LevelTransition transitionOfType(LevelTransition.Type type) {
        for (LevelTransition transition : level.transitions) {
            if (transition.type == type) {
                return transition;
            }
        }
        return null;
    }
}
