package org.shatterfish.harness.scene;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Signal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 1.3's acceptance test: a scripted sequence consumes the same number of random draws under
 * {@link HeadlessScene} as under the game's own {@code GameScene}, with neither needing a
 * graphics context.
 *
 * <p>Both scenes are created and stepped headlessly, so the comparison is live rather than against
 * a number recorded from a desktop run. That is possible because the real scene constructs under
 * the no-op binding, which is also why {@code HeadlessScene} is a {@code GameScene}; the test then
 * holds the two to each other, and stays meaningful for as long as anyone is tempted to make the
 * headless one cheaper by leaving something out. A draw count alone would let two runs that
 * diverge and happen to re-converge pass, so the comparison is a fingerprint: the draws during
 * creation and during the script, every Input wait reached, every frame stepped, the hero, every
 * mob, the gold, the heaps, the whole game log, and the next value the generator would give.
 *
 * <p>The second test runs the same scene twice and asks for the same fingerprint. That is the
 * determinism the stepper promises: with the actor thread fenced to run only between frames, the
 * same seed and the same script produce the same game to the frame, in a process where the actor
 * thread is real and the driver never sleeps.
 *
 * <p>The script is what a player who clicks would do, decided from what the player can see: attack
 * the nearest visible enemy (ties by position on screen), otherwise walk to a cell already seen.
 * It never takes the stairs, so the scene it started in is the scene it ends in; scene lifetime is
 * story 1.6's.
 */
class SceneDrawParityTest {

    /** A seed in the range a player can type ({@code DungeonSeed.TOTAL_SEEDS}). */
    private static final long SEED = 31_415_926L;
    private static final long GENERATOR_SEED = 0x5EEDL;
    private static final long SCRIPT_SEED = 7L;
    private static final int WAITS = 60;
    private static final int FRAME_BUDGET = 20_000;

    /** Everything about a run that two runs of the same game must agree on. */
    record Fingerprint(long createDraws, long scriptDraws, int waits, long frames, String endedBy,
                       float actorTime, int heroPos, int heroHP, int heroExp, int heroLvl, int gold,
                       List<String> mobs, int heaps, List<String> log, int nextDraw) {
    }

    /**
     * The game orders actors, mobs and weighted choices by {@code HashSet} iteration, which
     * follows identity hash codes, which HotSpot draws from a random state. The harness's test
     * task pins them ({@code shatterfish/harness/build.gradle}) so that two runs of one seed are
     * one game; without the pin these comparisons are usually true, which is the same as false.
     * Story 1.16 removes the dependence from the game and this check with it.
     */
    @BeforeAll
    static void identity_hashes_are_pinned_until_story_1_16() {
        assertEquals(System.identityHashCode(new Object()), System.identityHashCode(new Object()),
                "the harness tests run with -XX:hashCode=2, which pins every identity hash;"
                        + " without it HashSet order, and so the game, differs between runs");
    }

    private static FreshRun.Snapshot floor;

    /**
     * One floor for every comparison. Generating it twice from one seed does not give the same
     * floor, because the entrance room places the guidebook from an unseeded generator (see
     * {@link FreshRun}), so it is generated once and every Run resumes the saved game.
     *
     * <p>The first Run in a process also draws once more than every later one: static
     * initializers that draw, such as {@code WindParticle}'s wind angle
     * ({@code core/.../effects/particles/WindParticle.java:41}), run when their class is first
     * used and never again. A Rig Run is a process of its own and pays that draw every time, so
     * it is not a reproducibility problem there; in one process it shifts the stream of every
     * comparison by one, so the comparisons start after a Run that is thrown away.
     */
    @BeforeAll
    static void one_floor_and_a_run_that_is_thrown_away() throws Exception {
        HeadlessBoot boot = HeadlessBoot.ensure();
        FreshRun.start(boot, SEED, () -> {
        });
        floor = FreshRun.snapshot(boot);
        play(HeadlessScene::new);
    }

    @Test
    void the_headless_scene_draws_exactly_what_the_real_scene_draws() throws Exception {
        Fingerprint real = play(GameScene::new);
        Fingerprint headless = play(HeadlessScene::new);

        assertEquals(real, headless);
        assertTrue(real.waits() >= WAITS / 2,
                "the script must actually play for the comparison to mean anything: " + real);
        assertTrue(real.scriptDraws() > 0, "the script drew nothing, so nothing was compared: " + real);
    }

    @Test
    void the_same_seed_and_script_replay_to_the_frame() throws Exception {
        Fingerprint first = play(HeadlessScene::new);
        Fingerprint second = play(HeadlessScene::new);

        assertEquals(first, second);
    }

    private static Fingerprint play(Supplier<GameScene> scenes) throws Exception {
        HeadlessBoot boot = HeadlessBoot.ensure();
        DrawCounter[] installed = new DrawCounter[1];
        FreshRun.resume(boot, floor, () -> installed[0] = DrawCounter.install(GENERATOR_SEED));
        DrawCounter draws = installed[0];

        GameScene scene = scenes.get();
        SceneStepper stepper = scene instanceof HeadlessScene headless
                ? headless.stepper() : new SceneStepper(scene);
        List<String> log = new ArrayList<>();
        Signal.Listener<String> listener = text -> {
            log.add(text);
            return false;
        };
        try {
            long before = draws.draws();
            boot.game().switchTo(scene);
            long createDraws = draws.draws() - before;
            // After create: the scene's GameLog replaced every listener (GameLog.java:47).
            GLog.update.add(listener);

            Script script = new Script(SCRIPT_SEED);
            int waits = 0;
            String endedBy = "the frame budget";
            while (stepper.frames() < FRAME_BUDGET) {
                stepper.step();
                Hero hero = Dungeon.hero;
                if (!hero.isAlive()) {
                    endedBy = "the hero's death";
                    break;
                }
                if (atInputWait(hero)) {
                    if (waits == WAITS) {
                        endedBy = "the script";
                        break;
                    }
                    waits++;
                    if (!script.act(hero)) {
                        endedBy = GameScene.showingWindow() ? "a window the hero cannot click through"
                                : "a wait where no cell could be handled";
                        break;
                    }
                }
            }
            long scriptDraws = draws.draws() - before - createDraws;

            Hero hero = Dungeon.hero;
            Fingerprint fingerprint = new Fingerprint(createDraws, scriptDraws, waits, stepper.frames(),
                    endedBy, Actor.now(), hero.pos, hero.HP, hero.exp, hero.lvl, Dungeon.gold,
                    mobs(), Dungeon.level.heaps.valueList().size(), List.copyOf(log), draws.nextInt());
            System.out.println(scene.getClass().getSimpleName() + ": " + fingerprint);
            return fingerprint;
        } finally {
            GLog.update.remove(listener);
            stepper.endActorThread();
            boot.game().destroy();
            draws.remove();
        }
    }

    /**
     * The hero is waiting for input: ready, with no action pending. {@code ready} is set only by
     * {@code Hero.ready()} at the wait itself ({@code Hero.java:935-946}); {@code curAction} is
     * set by {@code handle()} and cleared when the action finishes, so a wait with an action
     * already handed over is not counted twice.
     */
    private static boolean atInputWait(Hero hero) {
        return hero.ready && hero.curAction == null && !hero.resting;
    }

    /**
     * Every mob on the floor, sorted by what it is and where it stands rather than by id: ids are
     * handed out in the iteration order of a {@code HashSet}, which is not part of the game.
     */
    private static List<String> mobs() {
        List<String> out = new ArrayList<>();
        for (Mob mob : Dungeon.level.mobs) {
            out.add(mob.getClass().getSimpleName() + "@" + mob.pos + " hp=" + mob.HP + " "
                    + (mob.state == null ? "-" : mob.state.getClass().getSimpleName()));
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /** What a clicking player does, decided from what the player can see. */
    private static final class Script {

        private final java.util.Random choice;

        Script(long seed) {
            choice = new java.util.Random(seed);
        }

        boolean act(Hero hero) {
            Level level = Dungeon.level;

            Mob target = null;
            int nearest = Integer.MAX_VALUE;
            for (Mob mob : hero.getVisibleEnemies()) {
                int distance = level.distance(hero.pos, mob.pos);
                if (distance < nearest || (distance == nearest && mob.pos < target.pos)) {
                    target = mob;
                    nearest = distance;
                }
            }
            if (target != null && click(hero, target.pos)) {
                return true;
            }

            List<Integer> candidates = new ArrayList<>();
            for (int cell = 0; cell < level.length(); cell++) {
                if (walkable(level, hero, cell)) {
                    candidates.add(cell);
                }
            }
            for (int attempt = 0; attempt < 8 && !candidates.isEmpty(); attempt++) {
                int cell = candidates.remove(choice.nextInt(candidates.size()));
                if (click(hero, cell)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * A left click on a cell: {@code GameScene.handleCell} is what the pointer handler calls
         * ({@code GameScene.java:1547-1549}), and it goes through the real {@code CellSelector} to
         * {@code Hero.handle} and {@code Hero.next} ({@code CellSelector.java:152-166}, {@code :415-416}).
         * An action was taken if the hero now has one.
         */
        private static boolean click(Hero hero, int cell) {
            GameScene.handleCell(cell);
            return hero.curAction != null;
        }

        /**
         * A cell the player has seen and would click: not their own, walkable, not the stairs, not
         * an alchemy pot, and, if it is in view right now, not occupied. What stands on a cell out
         * of view is not the player's to know, so it is not consulted.
         */
        private static boolean walkable(Level level, Hero hero, int cell) {
            return cell != hero.pos
                    && level.passable[cell]
                    && level.visited[cell]
                    && level.getTransition(cell) == null
                    && level.map[cell] != Terrain.ALCHEMY
                    && (!level.heroFOV[cell] || Actor.findChar(cell) == null);
        }
    }
}
