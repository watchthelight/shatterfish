package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Point;
import com.watabou.utils.Random;
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
import java.util.Map;

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
        // Every PointerArea (every button's hot area, including Explain's) registers itself on one
        // static, process-wide signal (PointerEvent.java) and is never unregistered on its own; a test
        // that dispatches a real tap (explain_does_not_steal_a_synthetic_tap_while_locked) must not see
        // another test's stale button still sitting in that list.
        PointerEvent.clearListeners();
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

    private static EmbeddedRun.Snapshot snapshot(RunLog.Decision decision, org.shatterfish.api.BeliefSummary beliefSummary,
                                                 List<RunLog> history) {
        return new EmbeddedRun.Snapshot(decision, 14, 2, null, EmbeddedRun.State.PLAYING, beliefSummary, history);
    }

    @Test
    @DisplayName("full, the strip shows the Mode line, the Goal line shows the goal, and the card shows the chosen Action and its alternatives")
    void full_panel_shows_everything() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of());
        dock.frame(scene, snapshot(decision), false);
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
        assertTrue(card.explainButton().active, "input is not locked in this snapshot, so Explain can be pressed");
        assertFalse(card.policyRow().visible, "not expanded yet");
    }

    /** Story 5.4: Safety flags as chips, the Belief summary's lines, and the Decision log's lines. */
    @Test
    @DisplayName("full, the Safety flags row shows chips, the Belief summary shows its lines, and the Decision log shows its lines")
    void full_panel_shows_flags_belief_and_log() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of("hp-low", "enemy-in-view"));
        org.shatterfish.api.BeliefSummary beliefSummary = new org.shatterfish.api.BeliefSummary(
                List.of(new org.shatterfish.api.BeliefSummary.Item("crimson potion", "potion of frost", 0.2)),
                List.of("potion of invisibility (pool room)"), List.of("potion of strength 1/2"));
        RunLog.Wait waitRecord = new RunLog.Wait(1, 14_000, 2, 0, "a".repeat(64), Map.of("hero", "a".repeat(64)),
                decision.chosen().action(), true, RunLog.BOT, decision, "", List.of(), 0);
        List<RunLog> history = List.of(waitRecord);
        dock.frame(scene, snapshot(decision, beliefSummary, history), false);
        Panel panel = dock.panel();

        SafetyFlagsRow flags = panel.flags();
        assertTrue(flags.visible);
        assertEquals(2, flags.chips().size());
        assertEquals("hp-low", flags.chips().get(0).text());
        assertEquals(SafetyFlagVerdict.DANGER, flags.chips().get(0).verdict());
        assertEquals("enemy-in-view", flags.chips().get(1).text());
        assertEquals(SafetyFlagVerdict.WARN, flags.chips().get(1).verdict());

        BeliefSummarySection belief = panel.belief();
        assertTrue(belief.shown().present());
        assertEquals(List.of("crimson potion: potion of frost 0.20"), belief.shown().items());
        assertEquals(List.of("potion of invisibility (pool room)"), belief.shown().floor());
        assertEquals(List.of("potion of strength 1/2"), belief.shown().chapters());

        DecisionLog log = panel.log();
        List<DecisionLogContent.Line> expected = DecisionLogContent.of(history);
        assertEquals(expected, log.lines());

        // The section order DESIGN.md names: strip, Goal line, Decision card, Safety flags, Belief
        // summary, Decision log -- each above the log positioned below the one before it, with no
        // overlap and one section gap between, so the log's own room is genuinely what is left below
        // every section above it, not a fixed offset that skips one of them.
        assertTrue(panel.card().top() >= panel.goal().bottom(), "the card is below the goal line");
        assertEquals(panel.card().bottom() + Panel.SECTION_GAP, flags.top(), 0.5f, "the flags row is below the card");
        assertEquals(flags.bottom() + Panel.SECTION_GAP, belief.top(), 0.5f, "the belief summary is below the flags row");
        assertEquals(belief.bottom() + Panel.SECTION_GAP, log.top(), 0.5f, "the log is below the belief summary");
    }

    @Test
    @DisplayName("no flags: the Safety flags row is absent")
    void no_flags_hides_the_row() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of());
        dock.frame(scene, snapshot(decision), false);
        assertFalse(dock.panel().flags().visible, "absent when there are none (the epic's own acceptance criterion)");
    }

    @Test
    @DisplayName("collapsed, the strip still shows the Mode line, but the Goal line and the card are hidden")
    void collapsed_hides_the_goal_and_the_card() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.collapsed(true);
        RunLog.Decision decision = decision(List.of());
        dock.frame(scene, snapshot(decision), false);
        Panel panel = dock.panel();
        assertEquals(PanelLayout.Form.STRIP, panel.placed().form());
        assertFalse(panel.stripText().text().isEmpty(), "the strip is the collapsed Panel and still says the Mode");
        assertFalse(panel.goal().visible);
        assertFalse(panel.card().visible);
        assertFalse(panel.flags().visible, "story 5.4: the Safety flags row is hidden collapsed too");
        assertFalse(panel.belief().visible, "story 5.4: the Belief summary is hidden collapsed too");
        assertFalse(panel.log().visible, "story 5.4: the Decision log is hidden collapsed too");
    }

    @Test
    @DisplayName("no Decision yet: the card says so in words and the Goal line is hidden, even when full")
    void no_decision_yet_on_a_full_panel() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(null), false);
        Panel panel = dock.panel();
        assertEquals(PanelLayout.Form.FULL, panel.placed().form());
        assertFalse(panel.goal().visible);
        DecisionCard card = panel.card();
        assertTrue(card.emptyRow().visible);
        assertEquals(DecisionCardContent.NO_DECISION_YET, card.emptyRow().text());
        assertFalse(card.actionBlocks()[0].visible);
        assertFalse(card.explainButton().visible, "nothing to explain yet");
        assertFalse(card.explainButton().active, "nor active, with nothing to explain");
    }

    @Test
    @DisplayName("Explain expands the card in place to the Policy and the Safety flags; a second press collapses it")
    void explain_toggles_in_place() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of("hp-low"));
        dock.frame(scene, snapshot(decision), false);
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
        dock.frame(scene, snapshot(decision), false);
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

    /**
     * The fairness review of story 5.3's Explain control: {@code ActionExecutor.press} queues a
     * synthetic tap as a {@code PointerEvent} directly, which bypasses {@code InputLock} entirely and
     * is dispatched to every {@code PointerArea} in view order (stack mode, newest first). If the
     * Panel (and Explain's hot area) was rebuilt after a window's own button already registered --
     * exactly the order a window opened in {@code create()} leaves things in -- Explain's listener
     * sits in front and would consume a tap meant for the button under it, and the executor would
     * report the tap {@code applied} though the game never saw it. Reproduced here at the worst case,
     * the window's button placed exactly where Explain is.
     */
    @Test
    @DisplayName("Explain's hot area is inactive while a Run plays, so a synthetic tap meant for a window button underneath still reaches it (the fairness review)")
    void explain_does_not_steal_a_synthetic_tap_while_locked() {
        PointerEvent.clearListeners();
        GameScene scene = fullScene();

        // The window's own button, registered first -- behind Explain's listener once the Panel below
        // is built, the order the review named as the vulnerable one.
        boolean[] clicked = {false};
        RedButton underlying = new RedButton("Continue") {
            @Override
            protected void onClick() {
                clicked[0] = true;
            }
        };
        underlying.camera = PixelScene.uiCamera;
        scene.add(underlying);

        PanelDock dock = new PanelDock();
        RunLog.Decision decision = decision(List.of());
        dock.frame(scene, snapshot(decision), true);   // a Run is playing: input is locked
        DecisionCard card = dock.panel().card();
        assertTrue(card.explainButton().visible, "Explain is shown whenever a Decision is present");
        assertFalse(card.explainButton().active, "but not active while input is locked");

        // The worst case: the window's button drawn exactly where Explain is.
        underlying.setRect(card.explainButton().left(), card.explainButton().top(),
                card.explainButton().width(), card.explainButton().height());

        press(underlying);

        assertFalse(card.explaining(), "Explain never toggled: its hot area let the tap through");
        assertTrue(clicked[0], "the tap reached the window's own button underneath");
    }

    /** {@code ActionExecutor.press}, reproduced: a synthetic DOWN and UP queued directly as {@code PointerEvent}s, bypassing {@code InputLock}. */
    private static void press(Component button) {
        Camera camera = button.camera();
        float x = button.left() + button.width() / 2;
        float y = button.top() + button.height() / 2;
        Point screen = camera.cameraToScreen(x, y);
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
        PointerEvent.processPointerEvents();
    }

    /**
     * Rendering is read-only (non-negotiable 5, reproducibility): the Panel's content is drawn from
     * the Decision and the Observation the Brain already produced, and drawing it again -- as every
     * frame between two Input waits does -- must never itself draw from the Run's own generator.
     * {@code RngControl} keeps no count of what is drawn from it (its own Javadoc: "there is no
     * counting of draws"), so this seeds a generator directly, draws one number as a reference,
     * reseeds to the identical state, calls {@code Panel.content} (and toggles Explain) many times in
     * between, and holds that the next number drawn is the same one: nothing in between consumed any.
     */
    @Test
    @DisplayName("calling Panel.content repeatedly draws nothing from the Run's generator")
    void content_does_not_draw_from_the_generator() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        RunLog.Decision withGoal = decision(List.of("hp-low"));
        dock.frame(scene, snapshot(withGoal), false);
        Panel panel = dock.panel();
        DecisionCard card = panel.card();

        long seed = 0xC0FFEEL;
        long reference;
        Random.pushGenerator(seed);
        try {
            reference = Random.Long(Long.MAX_VALUE);
        } finally {
            Random.popGenerator();
        }

        long after;
        Random.pushGenerator(seed);
        try {
            for (int i = 0; i < 50; i++) {
                RunLog.Decision decision = i % 2 == 0 ? withGoal : null;
                ModeState mode = ModeState.of(new EmbeddedRun.Snapshot(decision, i, 1, null, EmbeddedRun.State.PLAYING));
                panel.content(mode, decision, null, i % 3 == 0, null, List.of());
                if (decision != null) {
                    card.toggleExplain();
                    card.toggleExplain();
                }
            }
            after = Random.Long(Long.MAX_VALUE);
        } finally {
            Random.popGenerator();
        }

        assertEquals(reference, after, "the same generator, reseeded identically, drew the same next number: "
                + "fifty calls to Panel.content and a hundred Explain toggles drew nothing from it");
    }
}
