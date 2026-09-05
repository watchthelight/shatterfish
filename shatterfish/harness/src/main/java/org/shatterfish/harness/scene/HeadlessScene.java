package org.shatterfish.harness.scene;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Gizmo;

/**
 * The game's own scene, constructed without a graphics context and stepped by the driver.
 *
 * <p>ADR-0015 decided that the harness owns a scene which creates the same groups, sprites and
 * emote icons as {@code GameScene}, because their constructors draw random numbers on the actor
 * thread and a scene that skipped them would consume a different number of draws than an Overlay
 * Run. Story 1.3 found that the same-groups property is not enough on its own: the statics game
 * code calls are gated on {@code GameScene.scene}, which is package-private and assigned only
 * inside {@code GameScene.create()} ({@code core/.../scenes/GameScene.java:159}, {@code :242}),
 * and several of them carry game logic rather than drawing. A mob spawned during play is added to
 * the actor list only when a scene exists ({@code :1153-1161}), a heap dropped during play counts
 * for the exploration bonus only then ({@code :1131-1136}), a window the game opens is shown only
 * then ({@code :1352}), and {@code effectOverFog} and {@code addSprite} dereference the scene with
 * no guard at all ({@code :1149}, {@code :1185-1186}). A scene that is not {@code GameScene.scene}
 * therefore plays a different game, not just a quieter one.
 *
 * <p>So this scene <em>is</em> a {@code GameScene}, and {@link #create()} is
 * {@code GameScene.create()}: the same construction, the same groups, the same sprites, the same
 * emote icons, the same level-entry logic (dropped items landing, journal landmarks, the first
 * log lines), and the same static. The whole of it runs under the no-op binding installed by
 * {@code HeadlessBoot}, with atlases loaded as {@code Pixmap}s and text rasterized by FreeType.
 * {@code SceneDrawParityTest} holds this class to the real one by counting draws over a scripted
 * sequence under both; the two are the same code today, and the test is what keeps them so when
 * one of them changes.
 *
 * <p>What this class adds is the harness's grip: it refuses to construct unless the no-op binding
 * is installed, which is how "before any texture" is enforced; it counts its updates so a driver
 * can prove its own step count matches; it names the window in front, which the game's statics
 * answer only yes or no to; and it carries a {@link SceneStepper}, which advances it one fenced
 * frame at a time. What it deliberately does not do is override anything the game does inside a
 * frame.
 */
public final class HeadlessScene extends GameScene {

    private final SceneStepper stepper = new SceneStepper(this);
    private long updates;

    public HeadlessScene() {
        if (!NoOpGL.isNoOp(Gdx.gl)) {
            throw new IllegalStateException("the no-op graphics binding is not installed: every texture the"
                    + " scene builds calls glGenTexture in its constructor, so the binding must exist first."
                    + " HeadlessBoot.ensure() installs it");
        }
    }

    /** {@code GameScene.create()}, and nothing else; see the class comment for why. */
    @Override
    public void create() {
        super.create();
    }

    @Override
    public synchronized void update() {
        updates++;
        super.update();
    }

    /**
     * The window in front of the play area, or null. Windows are scene members, added in front
     * by {@code GameScene.show} ({@code GameScene.java:1352-1373}); {@code showingWindow()} says
     * whether there is one and this says which, for a driver confirming an Input wait, where a
     * window in front makes the wait one to answer a Prompt, and later for an Observer reading it.
     * The member list is the scene's own, read here because this class is the scene.
     */
    public synchronized Window openWindow() {
        Window front = null;
        for (int i = 0; i < length; i++) {
            Gizmo member = members.get(i);
            if (member instanceof Window window) {
                front = window;
            }
        }
        return front;
    }

    /** Times {@link #update()} has run, for a driver to check against its own frame count. */
    public long updates() {
        return updates;
    }

    /** The stepper that advances this scene; frames stepped through it equal {@link #updates()}. */
    public SceneStepper stepper() {
        return stepper;
    }

    /** One fenced frame; see {@link SceneStepper#step()}. */
    public void step() {
        stepper.step();
    }
}
