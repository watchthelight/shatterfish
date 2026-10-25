package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.input.PointerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.overlay.MapHighlightContent.Cells;
import org.shatterfish.overlay.MapHighlightContent.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Map highlights (story 5.10, FR-41, UX-DR10): the cells drawn on the dungeon are the Decision record's,
 * outlined in their documented colours and never filled, behind every sprite, drawn while the Decision is
 * the pending one, cleared when the hero acts or the plan changes, and never drawn in HUMAN mode.
 */
class MapHighlightTest {

    private static final long SEED = 12345;
    private int width;
    private int height;
    private int interfaceSize;

    @AfterEach
    void restore() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        boot.game().destroy();
        PointerEvent.clearListeners();
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(interfaceSize);
        }
    }

    private GameScene scene() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(1);
        Game.width = 1600;
        Game.height = 900;
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    /** A real Observation of the Run's first Input wait, whose driver is closed before the scene is built. */
    private static Observation observation() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, 1L)) {
            driver.stepToInputWait();
            return new Observer().observe();
        }
    }

    private static RunLog.Decision decision(Action chosen, Action... alternatives) {
        List<RunLog.Choice> alts = new java.util.ArrayList<>();
        for (Action alternative : alternatives) {
            alts.add(new RunLog.Choice(alternative, 1_000, "weighed"));
        }
        return new RunLog.Decision("explore: floor", new RunLog.Choice(chosen, 9_000, "frontier"), alts, List.of(),
                "explore");
    }

    private static EmbeddedRun.Snapshot pending(RunLog.Decision decision, Observation observation,
                                                List<Integer> highlights) {
        return new EmbeddedRun.Snapshot(decision, 14, 1, observation, EmbeddedRun.State.PLAYING, null, List.of(),
                null, true, null, highlights);
    }

    private static List<Integer> sorted(List<Integer> cells) {
        return cells.stream().sorted().toList();
    }

    @Test
    @DisplayName("the target is the record's last highlight, the path the cells before it, the considered cells the alternatives'")
    void roles_come_from_the_record() {
        RunLog.Decision decision = decision(new Action.Step(101), new Action.Step(100), new Action.Attack(103),
                new Action.Search());
        Cells cells = MapHighlightContent.of(decision, List.of(99, 100, 101));
        assertEquals(List.of(101), cells.target());
        assertEquals(List.of(99, 100), cells.path());
        assertEquals(List.of(103), cells.considered(), "100 is already on the path, Search points at no cell");
        assertEquals(Cells.NONE, MapHighlightContent.of(null, List.of(1, 2)), "no Decision, nothing to outline");
        assertEquals(List.of(), MapHighlightContent.of(decision, List.of()).target());
    }

    @Test
    @DisplayName("the drawn cells equal the Decision's highlight cells, role by role")
    void drawn_cells_equal_the_decisions() {
        Observation observation = observation();
        GameScene scene = scene();
        int w = observation.map().width();
        int hero = observation.hero().cell();
        RunLog.Decision decision = decision(new Action.Step(hero + 1), new Action.Step(hero - 1),
                new Action.Step(hero + w));
        List<Integer> highlights = List.of(hero + 1);

        PanelDock dock = new PanelDock();
        dock.frame(scene, pending(decision, observation, highlights), false);
        MapHighlights layer = dock.highlights();
        assertNotNull(layer);

        Cells expected = MapHighlightContent.of(decision, highlights);
        for (Role role : Role.values()) {
            assertEquals(sorted(expected.of(role)), sorted(layer.drawn(role)), role + " cells");
        }
        assertEquals(List.of(hero + 1), layer.drawn(Role.TARGET));
        assertEquals(List.of(hero - 1, hero + w), sorted(layer.drawn(Role.CONSIDERED)));
    }

    @Test
    @DisplayName("none is drawn in HUMAN mode, even for a pending shadow Decision")
    void nothing_in_human_mode() {
        Observation observation = observation();
        GameScene scene = scene();
        int hero = observation.hero().cell();
        RunLog.Decision decision = decision(new Action.Step(hero + 1), new Action.Step(hero - 1));
        PanelDock dock = new PanelDock();
        dock.frame(scene, pending(decision, observation, List.of(hero + 1)), false);
        assertFalse(dock.highlights().drawn(Role.TARGET).isEmpty(), "a Brain's Run draws it");

        EmbeddedRun.Snapshot human = new EmbeddedRun.Snapshot(decision, 14, 1, observation,
                EmbeddedRun.State.PLAYING, null, List.of(), new EmbeddedRun.Human(true, 1, true, 0, "", 0), true,
                null, List.of(hero + 1));
        dock.frame(scene, human, false);
        for (Role role : Role.values()) {
            assertEquals(List.of(), dock.highlights().drawn(role), role + " in HUMAN mode");
        }
        assertEquals(List.of(), dock.highlights().blocks());

        // A Run handed back to the Brain draws again, for its next pending Decision.
        dock.frame(scene, pending(decision, observation, List.of(hero + 1)), false);
        assertFalse(dock.highlights().drawn(Role.TARGET).isEmpty());
    }

    @Test
    @DisplayName("cleared when the hero acts, replaced when the plan changes, and not redrawn for an executed Decision")
    void cleared_when_the_hero_acts_or_the_plan_changes() {
        Observation observation = observation();
        GameScene scene = scene();
        int hero = observation.hero().cell();
        RunLog.Decision first = decision(new Action.Step(hero + 1), new Action.Step(hero - 1));
        PanelDock dock = new PanelDock();
        dock.frame(scene, pending(first, observation, List.of(hero + 1)), false);
        assertEquals(List.of(hero + 1), dock.highlights().drawn(Role.TARGET));

        RunLog.Decision second = decision(new Action.Step(hero - 1), new Action.Step(hero + 1));
        dock.frame(scene, pending(second, observation, List.of(hero - 1)), false);
        assertEquals(List.of(hero - 1), dock.highlights().drawn(Role.TARGET), "the plan changed");
        assertEquals(List.of(hero + 1), dock.highlights().drawn(Role.CONSIDERED));

        EmbeddedRun.Snapshot acted = new EmbeddedRun.Snapshot(second, 15, 1, observation, EmbeddedRun.State.PLAYING,
                null, List.of(), null, false, null, List.of(hero - 1));
        dock.frame(scene, acted, false);
        for (Role role : Role.values()) {
            assertEquals(List.of(), dock.highlights().drawn(role), role + " once the hero has acted");
        }
        dock.frame(scene, null, false);
        assertEquals(List.of(), dock.highlights().blocks(), "no Run, nothing");
    }

    @Test
    @DisplayName("outlines only: no block fills a cell, and each role has its documented colour and its own shape")
    void outlines_never_fills() {
        Observation observation = observation();
        GameScene scene = scene();
        int hero = observation.hero().cell();
        RunLog.Decision decision = decision(new Action.Step(hero + 1), new Action.Step(hero - 1));
        PanelDock dock = new PanelDock();
        dock.frame(scene, pending(decision, observation, List.of(hero + 2, hero + 1)), false);
        MapHighlights layer = dock.highlights();

        assertFalse(layer.blocks().isEmpty());
        for (ColorBlock block : layer.blocks()) {
            assertTrue(Math.min(block.width(), block.height()) <= MapHighlights.TARGET_EDGE,
                    "a block is an edge, thin on one side: " + block.width() + "x" + block.height());
            assertTrue(Math.max(block.width(), block.height()) <= MapHighlights.CELL, "and no longer than a cell");
        }
        assertEquals(4, blocksOf(layer, Role.TARGET));
        assertEquals(4, blocksOf(layer, Role.PATH));
        assertEquals(8, blocksOf(layer, Role.CONSIDERED), "corner brackets, two blocks to a corner");
        assertEquals(List.of(0xFFB347), colours(layer, Role.PATH));
        assertEquals(List.of(0xFF5555), colours(layer, Role.TARGET));
        assertEquals(List.of(0x7FB8FF), colours(layer, Role.CONSIDERED));
    }

    private static int blocksOf(MapHighlights layer, Role role) {
        int count = 0;
        for (Gizmo member : layer.shatterfishMembers()) {
            if (member instanceof MapHighlights.Outline outline && outline.role() == role) {
                count += outline.shatterfishMembers().size();
            }
        }
        return count;
    }

    /** The colour each outline of {@code role} was built in; a ColorBlock keeps its colour in its texture. */
    private static List<Integer> colours(MapHighlights layer, Role role) {
        java.util.Set<Integer> found = new java.util.TreeSet<>();
        for (Gizmo member : layer.shatterfishMembers()) {
            if (member instanceof MapHighlights.Outline outline && outline.role() == role) {
                found.add(outline.rgb());
            }
        }
        return List.copyOf(found);
    }

    @Test
    @DisplayName("the layer is behind every sprite: after the terrain, before the characters, items and the Panel")
    void layer_is_behind_the_sprites() {
        Observation observation = observation();
        GameScene scene = scene();
        int hero = observation.hero().cell();
        PanelDock dock = new PanelDock();
        dock.frame(scene, pending(decision(new Action.Step(hero + 1)), observation, List.of(hero + 1)), false);

        List<Gizmo> members = scene.shatterfishMembers();
        int layer = members.indexOf(dock.highlights());
        int panel = members.indexOf(dock.panel());
        assertTrue(layer > 0, "in the scene");
        assertTrue(layer < panel, "behind the Panel");
        int characters = -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) instanceof Group group && holds(group, CharSprite.class)) {
                characters = i;
            }
        }
        assertTrue(characters > layer, "behind the group the characters' sprites are drawn in");
        assertTrue(holds((Group) members.get(layer - 1), com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap.class),
                "directly after the terrain");
    }

    private static boolean holds(Group group, Class<?> type) {
        for (Gizmo member : group.shatterfishMembers()) {
            if (type.isInstance(member)) {
                return true;
            }
        }
        return false;
    }
}
