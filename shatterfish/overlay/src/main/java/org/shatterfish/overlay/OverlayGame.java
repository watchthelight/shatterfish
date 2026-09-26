package org.shatterfish.overlay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.PlatformSupport;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.boot.MemoryPreferences;
import org.shatterfish.harness.boot.Profile;
import org.shatterfish.harness.driver.NewGame;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.rng.RngControl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The desktop game with a Run attached: upstream's own {@code ShatteredPixelDungeon}, and a few
 * things added at the edges of its life (story 5.1, FR-37, ADR-0013).
 *
 * <ul>
 * <li>{@link #create()}: the Run's Profile is prepared before the game reads a setting or a file, so
 * a Run never touches the player's own saves, badges or preferences (ADR-0007); the game creates
 * itself as it always does; the player's input is closed ({@link InputLock}); then, on this render
 * thread, the Run's game begins through the headless driver's own body ({@code NewGame.begin}), the Run
 * is attached, and the game is asked for its play scene, which is created at the first frame and
 * attaches through the scene seam.</li>
 * <li>{@link #render()}: the game's frame, exactly as upstream draws and steps it, and then the Run's
 * frame, which returns at once whatever the Brain is doing. When the Run ends the input opens again.</li>
 * <li>{@link #update()}: the game's update, in the game's order, with the Panel docked beside the
 * dungeon and the camera offset that keeps the hero in the uncovered map set between the scene's
 * update and the cameras' matrices ({@link PanelDock}, story 5.2).</li>
 * <li>{@link #dispose()}: the Run is detached before the game is torn down, and a Profile the launcher
 * made for this Run alone is deleted.</li>
 * </ul>
 *
 * <p>Nothing here edits upstream: the class extends the game, and the methods call the game's own
 * first or last, except {@link #update()}, which is the game's own update written out here so the
 * Panel's offset can go in before the matrices are built. The render thread is the Run's UI-role thread, claimed when the Run attaches.
 */
public final class OverlayGame extends ShatteredPixelDungeon implements EmbeddedRun.Host {

    private final LaunchOptions options;
    private final MemoryPreferences preferences;
    private final Path profileDirectory;
    private final boolean deleteProfile;
    private final long salt;
    private final Supplier<Decider> brain;
    private final Supplier<Observation> observer;
    private final RunLoop.Logging logging;
    /** The interface the Overlay plays on: the mixed one, desktop layout with no inventory pane (story 5.2). */
    static final int INTERFACE_SIZE = 1;

    private final InputLock lock = new InputLock();
    private final PanelDock dock = new PanelDock();
    private RngControl rng;
    private EmbeddedRun run;
    private boolean reported;
    private long frames;

    /**
     * @param platform  upstream's desktop platform support
     * @param options   the Run's tuple and where it goes
     * @param preferences the Run's own settings, installed as the game's before this is constructed
     * @param profileDirectory the directory the Run's Profile is prepared in, claimed for this Run
     * @param deleteProfile whether the launcher made that directory for this Run and deletes it at the end
     * @param salt      the Run's salt
     * @param brain     what decides, built when the Run begins
     * @param observer  the door the Brain sees through: fair, or the launcher's oracle
     * @param logging   where the Run's log goes and who played it
     */
    OverlayGame(PlatformSupport platform, LaunchOptions options, MemoryPreferences preferences, Path profileDirectory,
                boolean deleteProfile, long salt, Supplier<Decider> brain, Supplier<Observation> observer,
                RunLoop.Logging logging) {
        super(platform);
        this.options = options;
        this.preferences = preferences;
        this.profileDirectory = profileDirectory;
        this.deleteProfile = deleteProfile;
        this.salt = salt;
        this.brain = brain;
        this.observer = observer;
        this.logging = logging;
    }

    @Override
    public void create() {
        // Before the game reads anything: its files at the Run's directory, its settings the Run's.
        Profile.prepare(Profile.owned(preferences), profileDirectory);
        // The desktop layout without the inventory pane (story 5.2): the Run Profile declares the compact
        // interface, the phone's, where the Panel only ever shows its Mode strip (UX-DR2). With interface
        // size 1 there is no inventory pane (GameScene.java:547-556), so an item selector is still a
        // window the executor answers (:1673-1674); the rest of the setting is layout and tutorial text.
        SPDSettings.interfaceSize(INTERFACE_SIZE);
        if (windowed(options)) {
            // The desktop game goes fullscreen by default (SPDSettings.java:66-68), which would replace
            // --window's size at its first frame and hide the title bar, the oracle's first marker
            // (non-negotiable 1: visibly flagged). Written to the Run's own settings directly, since the
            // setter also reaches for the system UI.
            preferences.putBoolean(SPDSettings.KEY_FULLSCREEN, false);
        }
        dock.oracle(options.oracle());
        // The strings in the language the Profile declares, as the headless boot sets them up, whatever
        // the machine's own locale would have given the game's first read of them (Messages.java:79-82).
        Messages.setup(Languages.ENGLISH);
        super.create();
        // First in the game's input multiplexer, which super.create() has just built (Game.java:108).
        lock.lock();
        inputHandler.addInputProcessor(lock);
        rng = new RngControl(salt);
        NewGame.begin(options.seed(), options.heroClass(), rng);
        if (options.human()) {
            // The person plays every wait; the Brain shadows them (story 5.9). The lock then holds only
            // presses between waits, and tells the Run what it passes.
            run = EmbeddedRun.attachHuman(this, options.seed(), options.heroClass(), rng, brain.get(), observer,
                    logging, options.turnCap());
            lock.human(new HumanInput());
        } else {
            run = EmbeddedRun.attach(this, options.seed(), options.heroClass(), rng, brain.get(), observer, logging,
                    options.turnCap());
        }
        // The play scene, created at the first frame the way the loading scene asks for it after a new
        // game (core/.../scenes/InterlevelScene.java), with the Run already listening at its seam.
        switchScene(GameScene.class);
    }

    /**
     * The game's update ({@code SPD-classes/.../noosa/Game.java:269-283}, line for line), with the Panel
     * placed between the scene's update and {@code Camera.updateAll()} (story 5.2).
     *
     * <p>A frame is drawn with each camera's matrix, which only {@code Camera.update} rebuilds
     * ({@code Camera.java:225}, {@code :300-313}), inside {@code Camera.updateAll()} at the end of the
     * game's update. The scene's update is where {@code GameScene.layoutTags} resets the camera's
     * offset to {@code (0, y)}. Placing the Panel after the whole update would put the offset back after
     * the matrix was built, and the next frame would draw the map shifted by the offset for one frame
     * every time the tags are laid out. So the update is this class's own, in the game's order, and the
     * Panel's offset goes in before the matrices are made. Nothing upstream is edited.
     */
    @Override
    protected void update() {
        float frameDelta = Math.min(0.2f, Gdx.graphics.getDeltaTime());
        Game.elapsed = Game.timeScale * frameDelta;
        Game.timeTotal += Game.elapsed;
        Game.realTime = TimeUtils.millis();
        inputHandler.processAllEvents();
        if (run != null) {
            // A HUMAN Run's wait reached late in the last frame is confirmed here, before a held key
            // moves the hero from inside the scene's update (story 5.9); a Brain's Run does nothing.
            run.beforeUpdate();
        }
        Music.INSTANCE.update();
        Sample.INSTANCE.update();
        dock.step(scene, scene::update, run == null ? null : run.snapshot(), panelLocked());
    }

    /**
     * Whether the Panel's own hot areas (Explain, the Decision log's scroll) and its tap blocker are off.
     * While a Brain plays, whenever the game's input is locked, since the executor's synthetic taps pass
     * the lock (story 5.3's fairness review). While a person plays, only while a window is in front
     * (story 5.9): then the window's own blocker holds the dungeon, and the Panel must not take a tap
     * meant for the window's buttons; otherwise the Panel takes every tap on itself, so a tap on the
     * Panel never reaches the dungeon under it and is never a hero's Action.
     */
    private boolean panelLocked() {
        if (run != null && run.human()) {
            return Windows.front() != null;
        }
        return lock.locked();
    }

    /** What the lock tells a HUMAN Run (story 5.9), on the render thread as the input is polled. */
    private final class HumanInput implements InputLock.Human {

        @Override
        public void pointerUp(int screenX, int screenY) {
            run.pointerUp(screenX, screenY);
        }

        @Override
        public void keyDown(int keycode) {
            run.keyDown(keycode);
        }

        @Override
        public void input() {
            run.inputEvent();
        }

        @Override
        public boolean overlayKey(int keycode) {
            if (keycode != NOTE_KEY || noteOpen()) {
                return false;
            }
            // Opened at the end of this frame, after the key's own typed character has gone by, so the
            // note does not begin with the key that asked for it.
            noteAsked = true;
            return true;
        }
    }

    /** The notes key (story 5.9): N, which the game binds to nothing (core/.../SPDAction.java:95-149). */
    static final int NOTE_KEY = com.badlogic.gdx.Input.Keys.N;

    private boolean noteAsked;

    private static boolean noteOpen() {
        return Windows.front() instanceof NoteWindow;
    }

    /** Opens the note window if the notes key asked for one, over the play scene. */
    private void openNote() {
        if (!noteAsked) {
            return;
        }
        noteAsked = false;
        if (run == null || !(scene instanceof GameScene) || noteOpen()) {
            return;
        }
        GameScene.show(new NoteWindow(run));
    }

    @Override
    public void render() {
        super.render();
        frames++;
        if (options.screenshot() != null && frames == Screenshot.FRAME) {
            // The frame this render drew, before it is shown: what a person at the window sees.
            Screenshot.write(options.screenshot());
        }
        if (run == null) {
            return;
        }
        EmbeddedRun.State state = run.frame();
        if (run.human() && state != EmbeddedRun.State.ENDED) {
            // A press reaches the game only while a wait is open for it, so every input the record holds
            // was made on the screen its wait observed; off the play scene (a loading scene's region
            // intro) the person clicks through as the game asks (story 5.9).
            if (run.inputOpen() || !(scene instanceof GameScene)) {
                lock.unlock();
            } else {
                lock.lock();
            }
            openNote();
        }
        if (lock.locked() && !noteOpen()) {
            // A window this frame opened (a text input) may have put its own processor in front.
            lock.keepFirst(inputHandler);
        }
        if (state == EmbeddedRun.State.ENDED && !reported) {
            reported = true;
            lock.unlock();
            RunOutcome outcome = run.outcome();
            Gdx.app.log("shatterfish", "the Run ended: " + outcome.cause() + " with deepest floor " + outcome.depth() + " after "
                    + outcome.turns() + " turns and " + run.waitIndex() + " waits"
                    + (outcome.detail().isEmpty() ? "" : " (" + outcome.detail() + ")")
                    + "; " + run.attachments() + " play scenes; " + run.staleAnswers() + " stale answers ("
                    + run.unrewoundAnswers() + " not rewound); log "
                    + run.logFile());
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
            releaseProfile(profileDirectory, deleteProfile);
        }
    }

    /**
     * The end of a Run's Profile: deleted if the launcher made it for this Run alone, left as it is if
     * the player named it ({@code --profile}), claim file and all, so that directory is not taken by a
     * second Run (FR-37).
     */
    static void releaseProfile(Path directory, boolean madeForThisRun) {
        if (madeForThisRun) {
            deleteQuietly(directory);
        }
    }

    /**
     * Deletes a Profile directory the launcher made for this Run; one already gone is fine, and a file
     * left open is left behind. Called at the game's close and again by the launcher's shutdown hook,
     * which is what runs when the game dies without closing.
     */
    static void deleteQuietly(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException leftBehind) {
            String line = "the Run's Profile at " + directory + " was not deleted: " + leftBehind;
            if (Gdx.app != null) {
                Gdx.app.log("shatterfish", line);
            } else {
                System.err.println("[shatterfish] " + line);
            }
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

    /**
     * The interface size the Overlay declares, which its log header states (story 5.2): the setting reads
     * 0 until the game knows its window. On a window below the game's full-UI minimum the game plays the
     * compact interface instead ({@code SPDSettings.java:140-146}), which the Panel's placement line in the
     * console records; that fallback is not in the header (ADR-0013's story 5.2 amendment).
     */
    @Override
    public int interfaceSize() {
        return INTERFACE_SIZE;
    }

    /** Whether the game opens windowed: a size was asked for, or the Run is an oracle Run. */
    static boolean windowed(LaunchOptions options) {
        return options.windowWidth() > 0 || options.oracle();
    }

    /** The Panel's dock. */
    PanelDock dock() {
        return dock;
    }

    /** The Run attached to this game, or null before {@link #create()}. */
    EmbeddedRun run() {
        return run;
    }
}
