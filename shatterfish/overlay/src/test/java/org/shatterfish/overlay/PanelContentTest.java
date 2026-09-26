package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.scene.HeadlessScene;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Panel's content on a real play scene (story 5.3): the Mode strip's text, the Goal line and the
 * Decision card, filled in by {@link PanelDock#frame} from an {@link EmbeddedRun.Snapshot}, and the
 * Explain control toggling the card's expansion in place. {@code PanelHudTest} (story 5.2) holds the
 * placement these are drawn inside of; this holds what they say.
 */
class PanelContentTest {

    private static final long SEED = 12345;
    private int width;
    private int height;
    private int interfaceSize;

    @AfterEach
    void restore() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        boot.game().destroy();
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(interfaceSize);
        }
    }

    /** A play scene at interface size 1 (the Overlay's own), in a window story 5.2 measured as FULL. */
    private GameScene fullScene() {
        return scene(1600, 900);
    }

    private GameScene scene(int w, int h) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(1);
        Game.width = w;
        Game.height = h;
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    private static RunLog.Decision decision(List<String> flags) {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Attack(42), 7_800, "corridor, full hp");
        RunLog.Choice alt = new RunLog.Choice(new Action.Step(12), 5_100, "corridor");
        return new RunLog.Decision("fight: gnoll scout", chosen, List.of(alt), flags, "fight-in-corridors");
    }

    private static EmbeddedRun.Snapshot snapshot(RunLog.Decision decision) {
        return new EmbeddedRun.Snapshot(decision, 14, 2, EmbeddedRun.State.PLAYING);
    }

    @Test
    @DisplayName("full, the strip shows the Mode line, the Goal line shows the goal, and the card shows the chosen Action and its alternatives")
    void full_panel_shows_everything() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of());
        dock.frame(scene, snapshot(decision));
        Panel panel = dock.panel();
        assertEquals(PanelLayout.Form.FULL, panel.placed().form(), "1600x900 at interface size 1 is FULL (story 5.2)");

        ModeState mode = ModeState.of(snapshot(decision));
        assertEquals(ModeStripContent.text(mode), panel.stripText().text());

        assertTrue(panel.goal().visible);
        assertEquals(decision.goal(), panel.goal().text().text());

        DecisionCard card = panel.card();
        assertFalse(card.emptyRow().visible);
        assertTrue(card.chosenRow().visible);
        assertEquals("Attack[cell=42]  " + Columns.score(7_800) + "  corridor, full hp", card.chosenRow().text());
        assertTrue(card.alternativeRows()[0].visible);
        assertEquals("Step[cell=12]  " + Columns.score(5_100) + "  corridor", card.alternativeRows()[0].text());
        assertFalse(card.alternativeRows()[1].visible, "only one alternative was given");
        assertTrue(card.explainButton().visible);
        assertFalse(card.policyRow().visible, "not expanded yet");
    }

    @Test
    @DisplayName("collapsed, the strip still shows the Mode line, but the Goal line and the card are hidden")
    void collapsed_hides_the_goal_and_the_card() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.collapsed(true);
        RunLog.Decision decision = decision(List.of());
        dock.frame(scene, snapshot(decision));
        Panel panel = dock.panel();
        assertEquals(PanelLayout.Form.STRIP, panel.placed().form());
        assertFalse(panel.stripText().text().isEmpty(), "the strip is the collapsed Panel and still says the Mode");
        assertFalse(panel.goal().visible);
        assertFalse(panel.card().visible);
    }

    @Test
    @DisplayName("no Decision yet: the card says so in words and the Goal line is hidden, even when full")
    void no_decision_yet_on_a_full_panel() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(null));
        Panel panel = dock.panel();
        assertEquals(PanelLayout.Form.FULL, panel.placed().form());
        assertFalse(panel.goal().visible);
        DecisionCard card = panel.card();
        assertTrue(card.emptyRow().visible);
        assertEquals(DecisionCardContent.NO_DECISION_YET, card.emptyRow().text());
        assertFalse(card.chosenRow().visible);
        assertFalse(card.explainButton().visible, "nothing to explain yet");
    }

    @Test
    @DisplayName("Explain expands the card in place to the Policy and the Safety flags; a second press collapses it")
    void explain_toggles_in_place() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of("hp-low"));
        dock.frame(scene, snapshot(decision));
        DecisionCard card = dock.panel().card();
        assertFalse(card.policyRow().visible);
        assertFalse(card.flagsRow().visible);

        card.toggleExplain();
        assertTrue(card.explaining());
        assertTrue(card.policyRow().visible);
        assertEquals("policy: fight-in-corridors", card.policyRow().text());
        assertTrue(card.flagsRow().visible);
        assertEquals("flags: hp-low", card.flagsRow().text());

        card.toggleExplain();
        assertFalse(card.explaining());
        assertFalse(card.policyRow().visible);
        assertFalse(card.flagsRow().visible);
    }
}
