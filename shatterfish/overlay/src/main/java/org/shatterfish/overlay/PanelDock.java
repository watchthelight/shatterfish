package org.shatterfish.overlay;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.effects.BadgeBanner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Toast;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Scene;
import org.shatterfish.harness.agent.EmbeddedRun;

/**
 * Keeps the Panel on the play scene and the camera offset the Panel needs (story 5.2).
 *
 * <p>{@link #step(Scene, Runnable)} is the end of the Overlay's frame update: the scene's own update, in
 * which the play scene lays out its tags and resets the camera's offset
 * ({@code core/.../scenes/GameScene.java:931-958}, {@code :993-999}), then the Panel placed and the
 * offset put back, then {@code Camera.updateAll()}, which builds the matrices the next frame is drawn
 * with ({@code SPD-classes/.../noosa/Game.java:269-283}, {@code Camera.java:225}). Each play scene gets
 * its own Panel, added in front of the HUD the scene built and behind the scene's fade from black
 * ({@code GameScene.java:784}, {@code PixelScene.java:366-376}); windows the game shows later are added
 * in front of it ({@code GameScene.java:1420-1441}).
 *
 * <p>Two of the game's own transient elements are drawn over the Panel and take precedence: the prompt
 * of a cell selection (a {@code Toast} near the bottom centre, {@code GameScene.java:1126-1149}) and the
 * badge banners ({@code PixelScene.java:378-395}). While either shows, the Panel dims, so what it says
 * stays readable around them and nothing of the game's is hidden.
 */
final class PanelDock {

    /** The fade from black each play scene begins with, found by its class's name (it is not visible here). */
    static final String FADER = "com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene$Fader";

    private Panel panel;
    private boolean collapsed;
    private boolean oracle;

    /**
     * The end of the Overlay's frame update: {@code sceneUpdate}, then the Panel and its offset, then the
     * cameras' matrices, so the frame drawn next shows the offset the Panel set rather than the one the
     * scene's layout pass left.
     *
     * @param snapshot     the Run's Decision, turn, floor and live state (story 5.3), or null before a Run
     *                     is attached; {@link ModeState#of} reads either.
     * @param inputLocked  whether the game's own input is closed ({@code InputLock}): while a Run plays,
     *                     the Decision card's Explain control must not be clickable either (the fairness
     *                     review of story 5.3: {@code ActionExecutor.press} queues synthetic taps that
     *                     bypass the lock, and Explain's hot area would otherwise be live to steal one
     *                     meant for a window button drawn under it).
     */
    void step(Scene scene, Runnable sceneUpdate, EmbeddedRun.Snapshot snapshot, boolean inputLocked) {
        sceneUpdate.run();
        frame(scene, snapshot, inputLocked);
        Camera.updateAll();
    }

    /** The Panel on {@code scene} if it is a play scene, placed and filled in for this frame, and the offset set. */
    void frame(Scene scene, EmbeddedRun.Snapshot snapshot, boolean inputLocked) {
        if (!(scene instanceof GameScene)) {
            return;
        }
        if (panel == null || panel.parent != scene) {
            panel = new Panel();
            panel.camera = PixelScene.uiCamera;
            panel.oracle(oracle);
            scene.add(panel);
            keepFaderInFront(scene);
        }
        PanelLayout.Layout layout = PanelLayout.of(PanelLayout.current(), collapsed);
        if (!layout.equals(panel.placed()) && Gdx.app != null) {
            // Where the Panel went, once per change: the record of a launch nobody watched.
            Gdx.app.log("shatterfish", "the Panel: " + layout.form() + " at " + layout.rect() + " on a UI view of "
                    + PixelScene.uiCamera.width + "x" + PixelScene.uiCamera.height + " (interface size "
                    + SPDSettings.interfaceSize() + ", camera offset " + layout.offsetUi() + " UI px)");
        }
        panel.place(layout);
        panel.content(ModeState.of(snapshot), snapshot == null ? null : snapshot.decision(),
                snapshot == null ? null : snapshot.observation(), inputLocked,
                snapshot == null ? null : snapshot.beliefSummary(),
                snapshot == null ? java.util.List.of() : snapshot.history());
        panel.dim(covered(scene));
        Camera world = Camera.main;
        PanelCamera.apply(world, PanelCamera.world(layout.offsetUi(), PixelScene.uiCamera.zoom, world.zoom));
    }

    /** The scene's fade from black, if it is still fading, brought back in front of the Panel. */
    private static void keepFaderInFront(Scene scene) {
        for (Gizmo member : scene.shatterfishMembers()) {
            if (member != null && member.getClass().getName().equals(FADER)) {
                scene.bringToFront(member);
            }
        }
    }

    /** Whether one of the game's transient elements that take precedence over the Panel is showing. */
    static boolean covered(Scene scene) {
        for (Gizmo member : scene.shatterfishMembers()) {
            if (member != null && member.visible && (member instanceof Toast || member instanceof BadgeBanner)) {
                return true;
            }
        }
        return false;
    }

    /** Collapses the Panel to the Mode strip, or restores it (UX-DR2; the hotkey is story 5.11's). */
    void collapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    boolean collapsed() {
        return collapsed;
    }

    /**
     * Marks the Panel for an oracle Run: the ORACLE label in the Mode strip, in the Overlay itself and not
     * only in the window's title, which fullscreen hides (non-negotiable 1: visibly flagged). The border
     * around the game view is story 5.12's.
     */
    void oracle(boolean oracle) {
        this.oracle = oracle;
        if (panel != null) {
            panel.oracle(oracle);
        }
    }

    /** The Panel on the current play scene, or null before the first one. */
    Panel panel() {
        return panel;
    }
}
