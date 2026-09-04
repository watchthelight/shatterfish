package org.shatterfish.harness.spike;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.backends.headless.HeadlessNativesLoader;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.AttackIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.TargetHealthIndicator;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.Scene;
import com.watabou.utils.FileUtils;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Story 1.1, the touchpoint audit: boot the game with no window and no graphics context, and drive
 * one hero melee attack from the hero's own action to the damage landing and the hero becoming
 * ready for the next turn.
 *
 * <p>The point is not the attack. Turn resolution in this game runs through sprite animation
 * callbacks: {@code Hero.actAttack} plays an animation and returns false, and the damage, the time
 * spent and the next turn all happen in {@code onAttackComplete}, which only the animation's own
 * update can fire. If that path cannot complete without a renderer, the headless harness is
 * impossible and E1 needs a different plan. This test is the evidence that it is possible.
 *
 * <p>The shape here is the game's own: the actor loop runs on its own thread through
 * {@code Actor.process()}, and this thread plays the part the render thread plays in the real game,
 * updating sprites, draining the posted-runnable queue and notifying the actor thread. Stories 1.3
 * and 1.4 inherit that shape, the no-op graphics binding and the boot sequence. They must not
 * inherit the hand-built level or the hand-placed mob; the real driver starts a Run through the
 * game's own path.
 *
 * <p>What this test does <em>not</em> prove: the Input-wait detection of ADR-0015. It polls the
 * hero's readiness, which is that ADR's rejected option 9. The decided option, a notification at
 * the observe site inside {@code Hero.act()}, is story 1.5's to build and prove.
 */
class HeadlessTurnSpikeTest {

    /** Every game static this spike had to set up or work around, for the audit report. */
    private final List<String> touchpoints = new ArrayList<>();
    private volatile Throwable actorThreadFailure;

    private void note(String what) {
        touchpoints.add(what);
    }

    @Test
    void oneMeleeAttackResolvesAndTheHeroBecomesReadyAgain() throws Exception {
        Path profile = java.nio.file.Files.createTempDirectory("shatterfish-spike");
        bootHeadlessApplication();
        installNoOpGraphics();
        prepareGameStatics(profile);

        Dungeon.seed = 12345L;
        GamesInProgress.selectedClass = HeroClass.WARRIOR;
        note("GamesInProgress.selectedClass must be set before Dungeon.init(), which reads it to "
                + "build the hero (Dungeon.java, initHero at the end of init())");
        Dungeon.init();

        Level level = new SewerLevel();
        Random.pushGenerator(Dungeon.seedCurDepth());
        level.create();
        Random.popGenerator();

        PathFinder.setMapSize(level.width(), level.height());
        Dungeon.level = level;
        Actor.clear();

        Hero hero = Dungeon.hero;
        hero.pos = level.entrance();
        Actor.add(hero);

        Rat rat = new Rat();
        rat.pos = adjacentPassable(level, hero.pos);
        level.mobs.add(rat);
        Actor.add(rat);
        note("The rat is left asleep, as a real Run's mobs are, so the emote path is exercised "
                + "rather than avoided");

        Group sprites = new Group();
        // HeroSprite links itself in its constructor (HeroSprite.java:58). Linking again would draw
        // a second Random.Int (CharSprite.java:152) that the real game never draws, which is
        // exactly the headless-versus-Overlay stream divergence this project must avoid.
        hero.sprite = new HeroSprite();
        sprites.add(hero.sprite);
        note("Hero has no sprite() factory: the game constructs HeroSprite directly "
                + "(GameScene.java:311) and HeroSprite self-links (HeroSprite.java:58 in its constructor), while "
                + "Mob.sprite() is a factory (Mob.java:220) whose result the caller must link. "
                + "CharSprite.link draws Random.Int (CharSprite.java:152), so a driver that links "
                + "the hero a second time consumes a draw the real game does not");
        rat.sprite = rat.sprite();
        rat.sprite.link(rat);
        sprites.add(rat.sprite);
        note("Char.sprite must be assigned and, for mobs, linked by the driver; the game does it "
                + "in GameScene.addMobSprite (GameScene.java:1054-1066)");

        assertNotNull(hero.sprite, "hero sprite");
        assertSame(hero, hero.sprite.ch, "the hero sprite linked itself to the hero");

        Outcome outcome = runOneAttack(sprites, rat);

        if (actorThreadFailure != null) {
            fail("the actor thread died: " + actorThreadFailure, actorThreadFailure);
        }
        if (!outcome.damaged()) {
            fail("no melee attack resolved within the update budget: rat HP still " + rat.HP
                    + " of " + outcome.ratHpBefore() + ", hero.curAction=" + hero.curAction
                    + ", hero.ready=" + hero.ready);
        }
        assertTrue(outcome.heroReadyAgain(),
                "the hero became ready again after the attack, so the turn completed rather than "
                        + "stalling inside the animation callback");
        assertTrue(outcome.actorTimeAdvanced(),
                "actor time advanced, so the attack cost the hero a turn");

        report(level, hero, rat, outcome);
    }

    private record Outcome(int ratHpBefore, int ratHpAfter, boolean damaged, boolean heroReadyAgain,
                           boolean actorTimeAdvanced, float actorTimeAfter, int framesToDamage) {
    }

    /**
     * Plays the part {@code GameScene.update()} plays: start the actor thread, then each frame
     * advance the sprites (which fires the animation callbacks that resolve the turn), drain
     * anything the game posted to the render thread, and notify the actor thread so it can take the
     * next turn.
     *
     * <p>The step is large on purpose. Animations are timed in seconds of frame time, so a big step
     * fast-forwards them; this is the fast-forward ADR-0015 assumed.
     */
    private Outcome runOneAttack(Group sprites, Mob rat) throws Exception {
        Thread actorThread = new Thread(Actor::process, "SHPD Actor Thread");
        Actor.keepActorThreadAlive = true;
        actorThread.setDaemon(true);
        actorThread.setUncaughtExceptionHandler((t, e) -> actorThreadFailure = e);
        note("An exception on the actor thread is invisible without an uncaught-exception handler: "
                + "the thread dies, the loop simply stops, and the driver sees a Run that stalls "
                + "rather than a failure. The real driver must install one and treat it as fatal");
        note("Actor.act() is protected, so no driver can call it; the only supported entry is "
                + "Actor.process() on its own thread (Actor.java:244), parked and woken with "
                + "wait/notify on the thread object, exactly as GameScene.update does "
                + "(GameScene.java:865-888)");
        actorThread.start();

        final int ratHpBefore = rat.HP;
        try {
            final float step = 0.2f;
            boolean noted = false;
            boolean damaged = false;
            int framesToDamage = -1;

            for (int frame = 0; frame < 600; frame++) {
                Game.elapsed = step;
                Game.timeTotal += step;

                // An Action is given at an Input wait, never before one: the first turn interrupts
                // the hero as the rat becomes visible, and interrupt() clears curAction
                // (Hero.java:1719 -> :948-957). Re-order whenever the hero is idle and the rat is
                // still unhurt, so a missed swing does not end the test.
                if (!damaged && Dungeon.hero.ready && Dungeon.hero.curAction == null) {
                    Dungeon.hero.curAction = new HeroAction.Attack(rat);
                    Dungeon.hero.ready = false;
                    if (!noted) {
                        noted = true;
                        note("An Action must be given at an Input wait, not before it: the first "
                                + "turn interrupts the hero as the rat becomes visible and "
                                + "interrupt() clears curAction. This is why AD-5 orders the "
                                + "per-wait sequence observe, decide, execute");
                        note("Actor.processing() is not an idle signal: it is current != null and "
                                + "current stays set while the actor thread is parked "
                                + "(Actor.java:234, :244-322). A driver that waits for it to go "
                                + "false waits forever; the hero's readiness is the signal");
                    }
                }

                sprites.update();
                drainPostedRunnables();

                synchronized (actorThread) {
                    actorThread.notify();
                }
                Thread.sleep(2);

                if (!damaged && rat.HP < ratHpBefore) {
                    damaged = true;
                    framesToDamage = frame;
                }
                // Keep pumping after the damage lands: the acceptance criterion is that the hero
                // becomes ready again, which happens after onAttackComplete calls next().
                if (damaged && Dungeon.hero.ready) {
                    return new Outcome(ratHpBefore, rat.HP, true, true, Actor.now() > 0f,
                            Actor.now(), framesToDamage);
                }
            }
            return new Outcome(ratHpBefore, rat.HP, damaged, Dungeon.hero.ready,
                    Actor.now() > 0f, Actor.now(), framesToDamage);
        } finally {
            Actor.keepActorThreadAlive = false;
            synchronized (actorThread) {
                actorThread.notify();
            }
            actorThread.join(2000);
            Actor.keepActorThreadAlive = true; // leave the static as the game expects to find it
        }
    }

    /**
     * The game posts work to the render thread through {@code Game.runOnRenderThread}, which is
     * {@code Gdx.app.postRunnable}. With the headless application's own loop body disabled, nobody
     * drains that queue unless the driver does, and any path that defers to it would never
     * complete. ADR-0015 made this the driver's job; this is where it is exercised.
     */
    private void drainPostedRunnables() {
        ((HeadlessApplication) Gdx.app).executeRunnables();
    }

    private static int adjacentPassable(Level level, int from) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = from + offset;
            if (cell >= 0 && cell < level.length() && level.passable[cell]
                    && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new IllegalStateException("no passable cell beside the hero at " + from);
    }

    /** A Game whose only jobs are to be the static instance and to not look like a scene switch. */
    private static final class SpikeGame extends Game {
        SpikeGame() {
            super(Scene.class, null);
            // Cleared by Game.step() in the real game when it creates the first scene; a headless
            // driver never calls step(), and Actor.process() will not act while this is true.
            this.requestedReset = false;
        }
    }

    private void bootHeadlessApplication() throws InterruptedException {
        if (Gdx.app != null) {
            return;
        }
        HeadlessNativesLoader.load();
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1; // the backend's own loop body never runs; the driver owns it
        CountDownLatch created = new CountDownLatch(1);
        new HeadlessApplication(new ApplicationAdapter() {
            @Override
            public void create() {
                created.countDown();
            }
        }, config);
        if (!created.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("headless backend did not start");
        }
        note("HeadlessApplication with updatesPerSecond = -1 provides Gdx.app and Gdx.files while "
                + "its own loop body never runs, so the driver owns the loop as ADR-0015 decided. "
                + "The consequence is that the driver must drain Gdx.app.postRunnable itself: "
                + "Game.runOnRenderThread posts there (Game.java:306-313) and the game uses it to "
                + "show windows from the actor thread");
    }

    private void installNoOpGraphics() {
        Gdx.gl20 = NoOpGL.gl20();
        Gdx.gl30 = NoOpGL.gl30();
        Gdx.gl = Gdx.gl20;
        note("Gdx.gl, gl20 and gl30 must be installed before any Texture is constructed; "
                + "Texture's constructor calls glGenTexture (SPD-classes/.../glwrap/Texture.java)");
    }

    private void prepareGameStatics(Path profile) {
        Game.versionCode = 1;
        Game.version = "spike";
        Game.density = 1f;
        Game.width = 800;
        Game.height = 600;
        note("Game.versionCode, version, density, width and height must all be set: versionCode is "
                + "stored as the save version by Dungeon.init(), and width and height are read by "
                + "Camera.reset (Camera.java:71-72)");

        new SpikeGame();
        note("Game.instance must exist before any sprite is placed and before the actor loop runs: "
                + "Game.scene() and Game.switchingScene() dereference it with no null check "
                + "(Game.java:222-228), and HeroSprite.place calls Game.scene() "
                + "(HeroSprite.java:107). Constructing a Game sets the static and needs no graphics");
        note("SILENT STALL: Game.requestedReset starts true (Game.java:64) and is cleared only by "
                + "Game.step() when it creates the first scene (Game.java:232-233). "
                + "Actor.process() skips picking any actor while Game.switchingScene() is true "
                + "(Actor.java:251), so a driver that never calls step() gets an actor loop that "
                + "parks forever with no error at all. The field is protected, so the driver clears "
                + "it by subclassing Game rather than by an upstream edit");

        Camera.reset();
        note("Camera.main must exist before any CharSprite is constructed: "
                + "CharSprite.worldToCamera reads Camera.main directly rather than the gizmo's own "
                + "camera (CharSprite.java:183-184)");

        new TargetHealthIndicator();
        note("Hero.act() -> checkVisibleMobs() -> QuickSlotButton.target() dereferences "
                + "TargetHealthIndicator.instance with no guard (QuickSlotButton.java:398). No hook "
                + "is needed: the indicator sets the static in its own constructor "
                + "(TargetHealthIndicator.java:33-36) and builds without a graphics context");
        new AttackIndicator();
        note("Hero.ready() calls AttackIndicator.updateState(), which dereferences "
                + "AttackIndicator.instance with no guard (AttackIndicator.java:206). It also sets "
                + "its own static in its constructor (AttackIndicator.java:54-58), so no hook");

        FileUtils.setDefaultFileProperties(Files.FileType.Absolute, profile + "/");
        note("FileUtils.setDefaultFileProperties must point at a per-Run directory, which is the "
                + "Profile of ADR-0007 (FileUtils.java:42)");

        Messages.setup(Languages.ENGLISH);
        note("Messages.setup pins the language; without it the bundle follows the host locale and "
                + "every display string in the Observation would be locale-dependent");

        note("PathFinder.setMapSize and Actor.clear() are also part of the driver's setup, done "
                + "beside the level below rather than here");
    }

    private void report(Level level, Hero hero, Mob rat, Outcome outcome) {
        StringBuilder out = new StringBuilder("\n=== story 1.1 touchpoint audit ===\n");
        out.append("level: ").append(level.getClass().getSimpleName()).append(' ')
                .append(level.width()).append('x').append(level.height()).append('\n');
        out.append("hero: ").append(hero.name()).append(" at ").append(hero.pos)
                .append(", HP ").append(hero.HP).append('/').append(hero.HT).append('\n');
        out.append("rat: HP ").append(outcome.ratHpAfter()).append(" of ")
                .append(outcome.ratHpBefore()).append(rat.isAlive() ? " (alive)" : " (dead)")
                .append(", damage landed on frame ").append(outcome.framesToDamage()).append('\n');
        out.append("hero ready again: ").append(outcome.heroReadyAgain())
                .append(", actor time now ").append(outcome.actorTimeAfter()).append('\n');
        out.append("touchpoints:\n");
        for (String t : touchpoints) {
            out.append("  - ").append(t).append('\n');
        }
        System.out.println(out);
    }
}
