package org.shatterfish.overlay;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.noosa.Scene;
import com.watabou.utils.PlatformSupport;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.boot.MemoryPreferences;
import org.shatterfish.harness.boot.Profile;
import org.shatterfish.harness.driver.NewGame;
import org.shatterfish.harness.rng.RngControl;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * The desktop game with a Run attached: upstream's own {@code ShatteredPixelDungeon}, and three
 * things added at the edges of its life (story 5.1, FR-37, ADR-0013).
 *
 * <ul>
 * <li>{@link #create()}: the Run's Profile is prepared before the game reads a setting or a file, so
 * a Run never touches the player's own saves, badges or preferences (ADR-0007); the game creates
 * itself as it always does; then, on this render thread, the Run's game begins through the headless
 * driver's own body ({@code NewGame.begin}), the Run is attached, and the game is asked for its play
 * scene, which is created at the first frame and attaches through the scene seam.</li>
 * <li>{@link #render()}: the game's frame, exactly as upstream draws and steps it, and then the Run's
 * frame, which returns at once whatever the Brain is doing.</li>
 * <li>{@link #dispose()}: the Run is detached before the game is torn down.</li>
 * </ul>
 *
 * <p>Nothing here edits upstream: the class extends the game, and the three methods call the game's
 * own first or last. The render thread is the Run's UI-role thread, claimed when the Run attaches.
 */
public final class OverlayGame extends ShatteredPixelDungeon implements EmbeddedRun.Host {

    private final LaunchOptions options;
    private final MemoryPreferences preferences;
    private final Path profileDirectory;
    private final long salt;
    private final Supplier<Decider> brain;
    private final Supplier<Observation> observer;
    private final RunLoop.Logging logging;
    private RngControl rng;
    private EmbeddedRun run;
    private boolean reported;

    /**
     * @param platform  upstream's desktop platform support
     * @param options   the Run's tuple and where it goes
     * @param preferences the Run's own settings, installed as the game's before this is constructed
     * @param profileDirectory the directory the Run's Profile is prepared in
     * @param salt      the Run's salt
     * @param brain     what decides, built when the Run begins
     * @param observer  the door the Brain sees through: fair, or the launcher's oracle
     * @param logging   where the Run's log goes and who played it
     */
    OverlayGame(PlatformSupport platform, LaunchOptions options, MemoryPreferences preferences, Path profileDirectory,
                long salt, Supplier<Decider> brain, Supplier<Observation> observer, RunLoop.Logging logging) {
        super(platform);
        this.options = options;
        this.preferences = preferences;
        this.profileDirectory = profileDirectory;
        this.salt = salt;
        this.brain = brain;
        this.observer = observer;
        this.logging = logging;
    }

    @Override
    public void create() {
        // Before the game reads anything: its files at the Run's directory, its settings the Run's.
        Profile.prepare(Profile.owned(preferences), profileDirectory);
        // The strings in the language the Profile declares, as the headless boot sets them up, whatever
        // the machine's own locale would have given the game's first read of them (Messages.java:79-82).
        Messages.setup(Languages.ENGLISH);
        super.create();
        rng = new RngControl(salt);
        NewGame.begin(options.seed(), options.heroClass(), rng);
        run = EmbeddedRun.attach(this, options.seed(), options.heroClass(), rng, brain.get(), observer, logging,
                options.turnCap());
        // The play scene, created at the first frame the way the loading scene asks for it after a new
        // game (core/.../scenes/InterlevelScene.java), with the Run already listening at its seam.
        switchScene(GameScene.class);
    }

    @Override
    public void render() {
        super.render();
        if (run == null) {
            return;
        }
        EmbeddedRun.State state = run.frame();
        if (state == EmbeddedRun.State.ENDED && !reported) {
            reported = true;
            RunOutcome outcome = run.outcome();
            Gdx.app.log("shatterfish", "the Run ended: " + outcome.cause() + " on floor " + outcome.depth() + " after "
                    + outcome.turns() + " turns and " + run.waitIndex() + " waits; log " + run.logFile());
            if (options.exitWhenOver()) {
                Gdx.app.exit();
            }
        }
    }

    @Override
    public void dispose() {
        try {
            if (run != null) {
                run.close();
            }
            if (rng != null) {
                rng.release();
            }
        } finally {
            super.dispose();
        }
    }

    @Override
    public int pendingRunnables() {
        return OverlayApplication.pending();
    }

    @Override
    public Class<? extends Scene> requestedScene() {
        return sceneClass;
    }

    /** The Run attached to this game, or null before {@link #create()}. */
    EmbeddedRun run() {
        return run;
    }
}
