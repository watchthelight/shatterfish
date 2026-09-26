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
 * placement these are drawn inside of; this holds what they say, and (the review after the first
 * launch) where the Decision card's own columns actually land in pixels.
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

    /**
     * A Decision whose rows deliberately differ in every measured width: a long action name with a
     * short score, a short action name with a long score, and an empty reason -- so a test that the
     * columns still line up is not passing by coincidence of equal-length text.
     */
    private static RunLog.Decision unevenRowsDecision() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Step(1), 10_000, "frontier");
        RunLog.Choice alt1 = new RunLog.Choice(new Action.UseItem(new org.shatterfish.api.ItemRef(0,
                "tome of dungeon mastery", 1), "read"), 1_111, "uniform 1/9");
        RunLog.Choice alt2 = new RunLog.Choice(new Action.Wait(), 100, "");
        return new RunLog.Decision("explore: floor", chosen, List.of(alt1, alt2), List.of(), "explore");
    }

    private static EmbeddedRun.Snapshot snapshot(RunLog.Decision decision) {
        return new EmbeddedRun.Snapshot(decision, 14, 2, null, EmbeddedRun.State.PLAYING);
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
        assertTrue(card.actionBlocks()[0].visible);
        // ActionText, not Action.toString(): "attack", not "Attack[cell=42]"; no name, since this
        // snapshot carries no Observation to find one in (ActionTextTest holds the named case).
        assertEquals("attack", card.actionBlocks()[0].text());
        assertEquals(Columns.score(7_800), card.scoreBlocks()[0].text());
        assertEquals("corridor, full hp", card.reasonBlocks()[0].text());
        assertTrue(card.actionBlocks()[1].visible);
        assertEquals("step 12", card.actionBlocks()[1].text(), "no Observation in this snapshot: the raw cell");
        assertEquals(Columns.score(5_100), card.scoreBlocks()[1].text());
        assertEquals("corridor", card.reasonBlocks()[1].text());
        assertFalse(card.actionBlocks()[2].visible, "only one alternative was given");
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
        assertFalse(card.actionBlocks()[0].visible);
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

    @Test
    @DisplayName("the score column is real pixel alignment (UX-DR5): every row's score ends at one right edge, and every reason starts at one left x, however the action names and scores differ in width")
    void scores_and_reasons_are_real_pixel_columns() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = unevenRowsDecision();
        dock.frame(scene, snapshot(decision));
        DecisionCard card = dock.panel().card();

        // Three rows shown (the chosen row, two alternatives), each with a different action-name
        // length ("step 1" vs "read tome of dungeon mastery" vs "wait") and a different score
        // magnitude ("1.0000" vs "0.1111" vs "0.0100"), which is exactly what would misalign under
        // one padded string in a proportional font. This snapshot carries no Observation, so the
        // Step falls back to its raw cell (ActionTextTest holds the real compass direction).
        assertEquals("step 1", card.actionBlocks()[0].text());
        assertEquals("read tome of dungeon mastery", card.actionBlocks()[1].text());
        assertEquals("wait", card.actionBlocks()[2].text());
        assertTrue(card.actionBlocks()[0].width() != card.actionBlocks()[1].width(),
                "the action blocks really do differ in measured width");

        // The expected column edges, computed independently of DecisionCard's own (private) column
        // fields: from its public x, its public COLUMN_GAP, and each block's own measured width --
        // the same inputs the review asked for ("computed from the widest cell in that column,
        // measured with the game's font"). A test that only compared the three rows to each other,
        // rather than to this independently derived edge, would not catch a right edge computed
        // without subtracting the score's own width (a left-aligned column that happens to still
        // line the rows up with each other when every score renders to the same width).
        float cardX = card.left();
        float maxActionWidth = 0;
        float maxScoreWidth = 0;
        for (int i = 0; i < 3; i++) {
            maxActionWidth = Math.max(maxActionWidth, card.actionBlocks()[i].width());
            maxScoreWidth = Math.max(maxScoreWidth, card.scoreBlocks()[i].width());
        }
        float expectedScoreRight = cardX + maxActionWidth + DecisionCard.COLUMN_GAP + maxScoreWidth;
        float expectedReasonLeft = expectedScoreRight + DecisionCard.COLUMN_GAP;
        for (int i = 0; i < 3; i++) {
            assertTrue(card.actionBlocks()[i].visible);
            assertEquals(cardX, card.actionBlocks()[i].left(), 0.05f, "row " + i + "'s action starts at the card's own left edge");
            assertEquals(expectedScoreRight, card.scoreBlocks()[i].right(), 0.05f,
                    "row " + i + "'s score block ends at the column's right edge, right-aligned by its own width");
            // The empty reason (Action.Wait's, row 2) is still positioned at the column even though
            // it draws nothing: the property is about where a reason would start, not about text.
            assertEquals(expectedReasonLeft, card.reasonBlocks()[i].left(), 0.05f,
                    "row " + i + "'s reason block starts at the column's left edge");
        }
    }
}
