package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.observer.OracleView;
import org.shatterfish.harness.scene.HeadlessScene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle marking in the Overlay (story 5.12, FR-43, UX-DR11): a red border around the game view, oracle rows
 * in the Belief summary, the unseen enemies outlined on the map, all in the oracle colour or the oracle
 * prefix and nowhere else, none of it for a fair Run, and nothing that can turn the mode on or off once the
 * Run has begun. The ORACLE label in the Mode strip is story 5.2's ({@code PanelHudTest.the_oracle_label});
 * the log header's bit and the window title's marker are {@code OracleToggleTest}'s.
 */
class OracleMarkingTest {

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

    private static Observation observation() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, 1L)) {
            driver.stepToInputWait();
            return new Observer().observe();
        }
    }

    private static EmbeddedRun.Snapshot snapshot(Observation observation, BeliefSummary summary) {
        return new EmbeddedRun.Snapshot(null, 14, 1, observation, EmbeddedRun.State.PLAYING, summary, List.of(), null,
                false, null, List.of());
    }

    private static BeliefSummary summary() {
        return new BeliefSummary(List.of(new BeliefSummary.Item("turquoise potion", "healing", 0.35),
                new BeliefSummary.Item("scroll labeled ZYX", "identify", 0.2)), List.of(), List.of());
    }

    private static OracleView view(int seenCell, int... unseenCells) {
        List<OracleView.Presence> mobs = new ArrayList<>();
        mobs.add(new OracleView.Presence(seenCell, "Rat", "marsupial rat", 8, 8, "WANDERING", true));
        for (int cell : unseenCells) {
            mobs.add(new OracleView.Presence(cell, "Gnoll", "gnoll scout", 12, 12, "SLEEPING", false));
        }
        return new OracleView(SEED,
                List.of(new OracleView.Identity("potion", "turquoise potion", "potion of healing", "PotionOfHealing"),
                        new OracleView.Identity("potion", "crimson potion", "potion of frost", "PotionOfFrost")),
                mobs, List.of(), List.of(), List.of());
    }

    private static PanelDock oracleDock(OracleSidecar sidecar) {
        PanelDock dock = new PanelDock();
        dock.oracle(true);
        dock.oracleSidecar(sidecar);
        return dock;
    }

    @Test
    @DisplayName("the marks: the true identity behind each Belief item, then the unseen enemies, every row prefixed, the seen ones left out")
    void marks_are_prefixed_rows_and_unseen_cells() {
        Observation observation = observation();
        int w = observation.map().width();
        int hero = observation.hero().cell();
        OracleMarkContent.Marks marks = OracleMarkContent.of(view(hero + 1, hero + 2 * w + 3, hero + 5), observation, summary());

        assertEquals(List.of(
                "oracle: turquoise potion = potion of healing",
                "oracle: gnoll scout at " + ((hero + 5) % w) + "," + ((hero + 5) / w) + ", unseen",
                "oracle: gnoll scout at " + ((hero + 2 * w + 3) % w) + "," + ((hero + 2 * w + 3) / w) + ", unseen"),
                marks.rows().stream().sorted(java.util.Comparator.comparing((String r) -> !r.contains("potion"))
                        .thenComparing(r -> r.contains("," + ((hero + 5) / w) + ",") ? 0 : 1)).toList(),
                "the scroll has no identity in the view, so no row; the rat in view has none either");
        assertTrue(marks.rows().stream().allMatch(row -> row.startsWith(OracleMarkContent.PREFIX)));
        assertEquals(List.of(hero + 5, hero + 2 * w + 3).stream().sorted().toList(), marks.unseen());
        assertEquals(w, marks.mapWidth());
        assertEquals(OracleMarkContent.Marks.NONE, OracleMarkContent.of(null, observation, summary()));
        assertEquals(OracleMarkContent.Marks.NONE, OracleMarkContent.of(view(hero), null, summary()));
    }

    @Test
    @DisplayName("past four unseen enemies the rows count the rest, but every one is still outlined")
    void rows_are_capped_cells_are_not() {
        Observation observation = observation();
        int hero = observation.hero().cell();
        int[] cells = {hero + 10, hero + 11, hero + 12, hero + 13, hero + 14, hero + 15};
        OracleMarkContent.Marks marks = OracleMarkContent.of(view(hero, cells), observation, null);
        assertEquals(OracleMarkContent.MOB_ROWS + 1, marks.rows().size());
        assertEquals("oracle: +2 more unseen", marks.rows().get(marks.rows().size() - 1));
        assertEquals(6, marks.unseen().size());
    }

    @Test
    @DisplayName("a view answers only for the screen it was read at")
    void the_sidecar_answers_for_its_own_screen() {
        Observation observation = observation();
        int hero = observation.hero().cell();
        OracleSidecar sidecar = new OracleSidecar();
        assertEquals(OracleMarkContent.Marks.NONE, sidecar.marks(observation, summary()), "nothing read yet");
        sidecar.take(observation, view(hero, hero + 7));
        assertEquals(List.of(hero + 7), sidecar.marks(observation, summary()).unseen());
        org.shatterfish.api.HeaderSection h = observation.header();
        Observation later = new Observation(new org.shatterfish.api.HeaderSection(h.version(), h.upstreamTag(),
                h.codexVersion(), h.heroClass(), h.challenges(), h.depth() + 1, h.branch(), h.sealed(), h.oracle(),
                h.prompt()), observation.map(), observation.actors(), observation.hero(), observation.inventory(),
                observation.journal(), observation.log(), observation.actions(), observation.prompt());
        assertEquals(OracleMarkContent.Marks.NONE, sidecar.marks(later, summary()), "a screen on another floor is not marked");
    }

    @Test
    @DisplayName("an oracle Run has the border: four oracle-colour edges, 2 UI px, around the whole view, last in the scene")
    void the_border() {
        GameScene scene = scene();
        PanelDock dock = oracleDock(new OracleSidecar());
        dock.frame(scene, null, false);

        OracleBorder border = dock.border();
        assertNotNull(border);
        assertEquals(0xFF2020, border.rgb());
        assertEquals(4, border.edges().size());
        float w = PixelScene.uiCamera.width;
        float h = PixelScene.uiCamera.height;
        assertEquals(w, border.edges().get(0).width(), 1e-3f, "top");
        assertEquals(OracleBorder.EDGE, border.edges().get(0).height(), 1e-3f);
        assertEquals(h, border.edges().get(1).y + border.edges().get(1).height(), 1e-3f, "bottom ends at the view's");
        assertEquals(w - OracleBorder.EDGE, border.edges().get(3).x, 1e-3f, "right");
        assertEquals(2f, OracleBorder.EDGE, "2 UI px (DESIGN.md Oracle border)");
        assertSame(PixelScene.uiCamera, border.camera(), "on the UI camera, so the Panel's offset on the world does not move it");

        // The game adds a window after the Panel: the border is brought back in front of it on the next frame.
        scene.add(new com.watabou.noosa.Group());
        dock.frame(scene, null, false);
        List<Gizmo> members = scene.shatterfishMembers();
        assertSame(border, members.get(members.size() - 1), "drawn above everything");
        assertTrue(members.indexOf(border) > members.indexOf(dock.panel()));
    }

    private static void assertSame(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual, message);
    }

    @Test
    @DisplayName("a fair Run has no border, no oracle outline layer and no oracle row, whatever the Panel is shown")
    void a_fair_run_has_none() {
        Observation observation = observation();
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        dock.oracle(false);
        dock.frame(scene, snapshot(observation, summary()), false);

        assertNull(dock.border());
        assertNull(dock.oracleOutlines());
        assertFalse(dock.panel().oracleLabel().visible);
        assertEquals(2, dock.panel().belief().lines().size(), "only the Brain's own lines");
        assertTrue(dock.panel().belief().lines().stream().noneMatch(line -> line.startsWith(OracleMarkContent.PREFIX)));
        for (Gizmo member : scene.shatterfishMembers()) {
            assertFalse(member instanceof OracleBorder || member instanceof OracleOutlines, "no oracle layer in the scene");
        }
        assertThrows(IllegalStateException.class, () -> dock.oracleSidecar(new OracleSidecar()),
                "a fair Run cannot be given a sidecar");
        assertThrows(IllegalArgumentException.class, () -> ShatterfishLauncher.observer(false, new OracleSidecar()),
                "nor can a fair door");
    }

    @Test
    @DisplayName("an oracle Run's Belief summary carries the prefixed rows after the Brain's own, in the ordinary ink")
    void the_belief_summary_rows() {
        Observation observation = observation();
        int hero = observation.hero().cell();
        GameScene scene = scene();
        OracleSidecar sidecar = new OracleSidecar();
        sidecar.take(observation, view(hero, hero + 9));
        PanelDock dock = oracleDock(sidecar);
        dock.frame(scene, snapshot(observation, summary()), false);

        List<String> lines = dock.panel().belief().lines();
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("turquoise potion: healing"), "the Brain's own first: " + lines);
        assertTrue(lines.get(2).startsWith("oracle: turquoise potion = potion of healing"), lines.get(2));
        assertTrue(lines.get(3).startsWith("oracle: gnoll scout at "), lines.get(3));

        // The Brain's Run with no belief yet: the oracle rows still follow the plain statement of that.
        dock.frame(scene, snapshot(observation, null), false);
        List<String> none = dock.panel().belief().lines();
        assertEquals(BeliefSummaryContent.NOTHING_YET, none.get(0));
        assertTrue(none.get(1).startsWith("oracle: gnoll scout"), none.toString());
    }

    @Test
    @DisplayName("unseen enemies are outlined in the oracle colour, in front of the fog and behind the Panel, edges only")
    void the_unseen_enemies_are_outlined() {
        Observation observation = observation();
        int hero = observation.hero().cell();
        GameScene scene = scene();
        OracleSidecar sidecar = new OracleSidecar();
        sidecar.take(observation, view(hero, hero + 9, hero + 20));
        PanelDock dock = oracleDock(sidecar);
        dock.frame(scene, snapshot(observation, null), false);

        OracleOutlines layer = dock.oracleOutlines();
        assertNotNull(layer);
        assertEquals(List.of(hero + 9, hero + 20), layer.drawn().stream().sorted().toList(), "the unseen, and not the seen");
        assertEquals(0xFF2020, layer.rgb());
        assertEquals(2 * 4 * layer.drawn().size(), layer.blocks().size(), "a double frame, four edges each");
        for (ColorBlock block : layer.blocks()) {
            assertTrue(Math.min(block.width(), block.height()) <= OracleOutlines.EDGE, "an edge, never a fill");
        }

        List<Gizmo> members = scene.shatterfishMembers();
        int fog = -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getClass().getName().equals(OracleOutlines.FOG)) {
                fog = i;
            }
        }
        assertTrue(fog >= 0, "the scene has its fog");
        assertEquals(fog + 1, members.indexOf(layer), "directly in front of the fog");
        assertTrue(members.indexOf(layer) < members.indexOf(dock.panel()), "behind the Panel");

        // The screen moves on, and the outlines go with it.
        dock.frame(scene, null, false);
        assertEquals(List.of(), layer.drawn(), "no screen, nothing marked");
    }

    @Test
    @DisplayName("nothing in the Overlay can turn oracle mode on or off once it is said")
    void it_cannot_be_toggled() {
        PanelDock dock = new PanelDock();
        dock.oracle(true);
        dock.oracle(true);
        assertThrows(IllegalStateException.class, () -> dock.oracle(false));
        PanelDock fair = new PanelDock();
        fair.oracle(false);
        assertThrows(IllegalStateException.class, () -> fair.oracle(true));

        // Who may say it: the game's create(), and nobody else, apart from the dock's own tests.
        ArchRule onlyTheGame = noClasses().that().resideInAPackage("org.shatterfish.overlay..")
                .and().doNotHaveFullyQualifiedName(OverlayGame.class.getName())
                .and().doNotHaveFullyQualifiedName(PanelDock.class.getName())
                .should().callMethod(PanelDock.class, "oracle", boolean.class)
                .orShould().callMethod(PanelDock.class, "oracleSidecar", OracleSidecar.class)
                .because("the launcher's flag, said once in the game's create(), is the only source of oracle mode (FR-43)");
        onlyTheGame.check(mainClasses());
        // The rule bites: the game does say it.
        ArchRule theGame = noClasses().that().haveFullyQualifiedName(OverlayGame.class.getName())
                .should().callMethod(PanelDock.class, "oracle", boolean.class);
        assertTrue(theGame.evaluate(mainClasses()).hasViolation());
    }

    @Test
    @DisplayName("an oracle view is named by the sidecar, its content function and the launcher, and by no other class of the Overlay")
    void the_view_stays_in_three_classes() {
        ArchRule rule = noClasses().that().resideInAPackage("org.shatterfish.overlay..")
                .and().haveNameNotMatching("org\\.shatterfish\\.overlay\\.(OracleSidecar|OracleMarkContent|ShatterfishLauncher)(\\$.*)?")
                .should().dependOnClassesThat().haveFullyQualifiedName(OracleView.class.getName())
                .because("the Panel, the Snapshot and the Run log hold text and cell numbers, never a hidden fact's own type");
        rule.check(mainClasses());
        ArchRule bites = noClasses().that().haveFullyQualifiedName(OracleSidecar.class.getName())
                .should().dependOnClassesThat().haveFullyQualifiedName(OracleView.class.getName());
        assertTrue(bites.evaluate(mainClasses()).hasViolation());
    }

    private static JavaClasses mainClasses() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.overlay");
    }

    @Test
    @DisplayName("the oracle colour appears nowhere else in the instrument: one literal, and three users of the constant")
    void the_colour_is_confined() throws IOException {
        Path sources = Path.of("src/main/java/org/shatterfish/overlay");
        if (!Files.isDirectory(sources)) {
            sources = Path.of("shatterfish/overlay/src/main/java/org/shatterfish/overlay");
        }
        List<String> literal = new ArrayList<>();
        List<String> users = new ArrayList<>();
        try (Stream<Path> files = Files.list(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                String name = file.getFileName().toString();
                // The literal, in any spelling a colour constant could take.
                if (text.matches("(?s).*(?i:0xFF2020|0xFFFF2020|#FF2020|16722976|-14304).*")) {
                    literal.add(name);
                }
                if (text.contains("ORACLE_COLOR")) {
                    users.add(name);
                }
            }
        }
        assertEquals(List.of("Panel.java"), literal, "the colour is defined once");
        assertEquals(List.of("OracleBorder.java", "OracleOutlines.java", "Panel.java"),
                users.stream().sorted().toList(), "and used by the border, the outlines and the label only");

        // Every colour the Decision highlights can take is somebody else's.
        for (MapHighlightContent.Role role : MapHighlightContent.Role.values()) {
            assertTrue(role.rgb() != Panel.ORACLE_COLOR, role + " is not the oracle colour");
        }
        // The Panel's own ORACLE label is hidden for a fair Run, so its colour is on screen only in an oracle Run.
        assertEquals(0xFF2020, Panel.ORACLE_COLOR);
    }
}
