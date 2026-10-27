package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.ExplainSession;
import org.shatterfish.overlay.ExplainContent.Kind;
import org.shatterfish.overlay.ExplainContent.Line;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explain at a real Decision (story 8.2, FR-44): a person played by {@code ScriptedHuman} on the harness's
 * {@code EmbeddedHost} follows the Brain's shadow through a Run of the {@code standard} set's first entry (a
 * WARRIOR, seed code DCS-SHA-XYL, {@code seeds/standard.json}; never {@code goo} or {@code holdout}), and at
 * the first Decision the Brain scored through the Evaluation the Explain key is pressed. The Panel's content
 * model is then read: the rows are what {@link ExplainContent} makes of that Decision, and the terms under the
 * chosen Action and under each alternative add up to the score printed on its row.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ExplainRealRunTest {

    /** {@code seeds/standard.json}, entry 0: WARRIOR, DCS-SHA-XYL. */
    private static final long SEED = 648_322_377_831L;
    /** The most waits the person plays before the test gives up looking for a scored Decision. */
    private static final int WAITS = 4_000;

    @AfterEach
    void restore() {
        PointerEvent.clearListeners();
    }

    /** One frame of the Panel for {@code snapshot}, as the dock gives it. */
    private static void frame(Panel panel, EmbeddedRun.Snapshot snapshot) {
        panel.content(ModeState.of(snapshot), snapshot.decision(), snapshot.observation(), false,
                snapshot.beliefSummary(), snapshot.history(), PanelDock.shadow(snapshot));
    }

    private static boolean scored(RunLog.Decision decision) {
        return decision != null && !decision.chosen().terms().isEmpty();
    }

    @Test
    @DisplayName("at a real scored Decision, Explain's content model is the Decision's, and its terms sum to the scores shown")
    void terms_sum_to_the_displayed_score(@TempDir Path folder) {
        // The Run plays on the compact interface, as the harness requires; the Panel is placed for a 1600 by 900
        // window at interface size 1, the Overlay's own, by the layout function the dock uses.
        try (ExplainSession person = new ExplainSession(SEED, HeroClass.WARRIOR, folder)) {
            Panel panel = new Panel();
            panel.camera = PixelScene.uiCamera;
            Game.scene().add(panel);
            panel.place(PanelLayout.of(PanelLayout.Screen.of(1600, 900, 1), false));
            assertEquals(PanelLayout.Form.FULL, panel.placed().form());
            RunLog.Decision found = null;
            int waits = 0;
            while (waits++ < WAITS && person.nextWait()) {
                EmbeddedRun.Snapshot snapshot = person.snapshot();
                if (scored(snapshot.decision())) {
                    found = snapshot.decision();
                    frame(panel, snapshot);
                    assertTrue(panel.controls().press(ControlEnablement.Control.EXPLAIN), "Explain acts at a real Decision");
                    frame(panel, snapshot);
                    break;
                }
                person.follow();
            }
            assertNotNull(found, "a Decision with terms came within " + WAITS + " waits");
            ExplainView view = panel.explain();
            assertTrue(view.shown());
            List<Line> lines = view.lines();
            assertEquals(ExplainContent.of(found, person.snapshot().observation()), lines,
                    "the Panel shows what the pure function says of this Decision");

            // The displayed score of each Choice is the sum of the term rows beneath it.
            BigDecimal shown = null;
            BigDecimal sum = BigDecimal.ZERO;
            int tables = 0;
            for (Line line : lines) {
                switch (line.kind()) {
                    case CHOSEN, ALTERNATIVE, FLAGS -> {
                        if (shown != null && sum.signum() != 0) {
                            assertEquals(0, shown.compareTo(sum), "the terms under " + shown + " add up to it");
                            tables++;
                        }
                        shown = line.kind() == Kind.FLAGS ? null : new BigDecimal(line.right());
                        sum = BigDecimal.ZERO;
                    }
                    case TERM -> sum = sum.add(new BigDecimal(line.right()));
                    default -> {
                    }
                }
            }
            assertTrue(tables >= 1, "at least the chosen Action's table was checked");
            assertEquals(Columns.score(found.chosen().score()), lines.get(1).right(), "the chosen row shows its score");
            assertEquals("policy: " + found.policy(), lines.get(0).left());
            assertFalse(lines.stream().anyMatch(line -> line.kind() == Kind.SEARCH),
                    "no Search ran: no Search block (Search never shipped, ADR-0010)");
        }
    }
}
