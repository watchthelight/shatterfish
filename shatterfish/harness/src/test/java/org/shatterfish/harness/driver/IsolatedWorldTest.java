package org.shatterfish.harness.driver;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.isolate.IsolationLoader;
import org.shatterfish.harness.isolate.RolloutWorld;
import org.shatterfish.harness.isolate.WorldFactory;
import org.shatterfish.harness.observer.Observer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The isolated world is a second copy of the game that plays the same game (E10 spike, ADR-0024).
 *
 * <p>The class loader gives the copy its own statics and shares what must be shared. The equivalence case is the
 * "fully observed" one: the world is built from the live Run's own unscrubbed snapshot, which a test may do by reaching
 * the private constructor of {@link ScrubbedStart} and production code may not, so that nothing is hidden from the world
 * and nothing about the Run's draws differs, and the Actions the live Run took are played in the world. Every Observation
 * must be the live Run's.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class IsolatedWorldTest {

    private static final long SEED = 27_182_818L;
    private static final long SALT = 0x5A17_5A17L;

    private HeadlessDriver driver;
    private RolloutWorld world;

    @AfterEach
    void end() {
        if (world != null) {
            world.close();
            world = null;
        }
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static RolloutWorld open(String heroClass) {
        IsolationLoader loader = new IsolationLoader(IsolatedWorldTest.class.getClassLoader());
        return ServiceLoader.load(WorldFactory.class, loader).findFirst().orElseThrow().open(heroClass);
    }

    /** The test's way in, which production code does not have: a start made of the live Run's own, unscrubbed, snapshot. */
    private static ScrubbedStart unscrubbedStart(SnapshotStore store, org.shatterfish.api.SnapshotHandle handle) throws Exception {
        Constructor<ScrubbedStart> constructor = ScrubbedStart.class.getDeclaredConstructor(Snapshot.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store.find(handle.id()));
    }

    @Test
    @DisplayName("the loader gives the copy its own game and shares libGDX, the interfaces and the start")
    void the_loader_splits_the_game_from_the_shared_classes() throws Exception {
        IsolationLoader loader = new IsolationLoader(IsolatedWorldTest.class.getClassLoader());
        Class<?> statistics = Class.forName("com.shatteredpixel.shatteredpixeldungeon.Statistics", false, loader);
        Class<?> mine = Class.forName("com.shatteredpixel.shatteredpixeldungeon.Statistics");
        assertNotSame(mine, statistics, "the game's classes are the copy's own");
        assertSame(loader, statistics.getClassLoader());
        // The statics are two sets: writing one leaves the other.
        Field deepest = statistics.getField("deepestFloor");
        Field theirs = mine.getField("deepestFloor");
        int before = theirs.getInt(null);
        deepest.setInt(null, before + 41);
        assertEquals(before, theirs.getInt(null), "a static of the copy is not the live game's");
        assertEquals(before + 41, deepest.getInt(null));

        assertSame(Gdx.class, Class.forName("com.badlogic.gdx.Gdx", false, loader), "libGDX is the parent's");
        assertSame(ScrubbedStart.class, Class.forName(ScrubbedStart.class.getName(), false, loader));
        assertSame(RolloutWorld.class, Class.forName(RolloutWorld.class.getName(), false, loader));
        assertSame(org.shatterfish.harness.scene.NoOpGL.class,
                Class.forName("org.shatterfish.harness.scene.NoOpGL", false, loader));
        assertSame(Action.class, Class.forName(Action.class.getName(), false, loader), "api is the parent's");
        // libGDX's reflection helper must be the copy's, or the game's class names resolve to the live game's classes.
        assertNotSame(com.badlogic.gdx.utils.reflect.ClassReflection.class,
                Class.forName("com.badlogic.gdx.utils.reflect.ClassReflection", false, loader));
    }

    @Test
    @DisplayName("which classes a loader owns")
    void ownership_rules() {
        assertTrue(IsolationLoader.isOwn("com.shatteredpixel.shatteredpixeldungeon.Dungeon"));
        assertTrue(IsolationLoader.isOwn("com.watabou.utils.Bundle"));
        assertTrue(IsolationLoader.isOwn("org.shatterfish.harness.driver.HeadlessDriver"));
        assertTrue(!IsolationLoader.isOwn("org.shatterfish.harness.driver.ScrubbedStart"));
        assertTrue(!IsolationLoader.isOwn("org.shatterfish.harness.scene.NoOpGL$ZeroValues"));
        assertTrue(!IsolationLoader.isOwn("org.shatterfish.api.Observation"));
        assertTrue(!IsolationLoader.isOwn("com.badlogic.gdx.Gdx"));
        assertTrue(!IsolationLoader.isOwn("org.json.JSONObject"));
        assertTrue(!IsolationLoader.isOwn("java.lang.String"));
    }

    @Test
    @DisplayName("a position with nothing hidden: the isolated world's Observations are the live Run's, wait for wait")
    void the_isolated_step_is_the_live_step() throws Exception {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        HeadlessDriver.Halt first = driver.stepToInputWait();
        assertEquals(HeadlessDriver.Reason.INPUT_WAIT, first.reason());
        SnapshotStore store = new SnapshotStore();
        var handle = store.take(driver);
        ScrubbedStart start = unscrubbedStart(store, handle);

        // The live Run plays on: a seeded random agent at the live screens, recording what it did and what it saw.
        ActionExecutor executor = new ActionExecutor();
        RandomAgent agent = new RandomAgent(11L);
        List<Action> taken = new ArrayList<>();
        List<Observation> seen = new ArrayList<>();
        Observation at = new Observer().observe();
        seen.add(at);
        HeadlessDriver.Reason ended = HeadlessDriver.Reason.INPUT_WAIT;
        for (int i = 0; i < 80; i++) {
            Action action = agent.decide(at);
            taken.add(action);
            executor.execute(at, action);
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                ended = halt.reason();
                break;
            }
            at = new Observer().observe();
            seen.add(at);
        }
        assertTrue(taken.size() >= 40, "the live Run plays a line long enough to mean something: " + taken.size());

        world = open("WARRIOR");
        RolloutResult result = world.rollout(start, taken);
        assertEquals(seen.size(), result.observations().size(), "as many waits");
        for (int i = 0; i < seen.size(); i++) {
            assertEquals(seen.get(i).hash(), result.observations().get(i).hash(), "the same Observation at step " + i);
            assertEquals(seen.get(i), result.observations().get(i), "the same Observation, field for field, at step " + i);
        }
        assertEquals(taken.size(), result.applied());
        // The line ends where the live Run's did: at its horizon, or at the death both worlds reached.
        assertEquals(ended == HeadlessDriver.Reason.HERO_DEAD ? RolloutEnd.DEATH : RolloutEnd.HORIZON, result.end());
        System.out.println("IsolatedWorldTest: " + taken.size() + " Actions, " + seen.size() + " waits equal, ended " + ended);

        // The live Run was not saved, loaded or touched by the world: it stands at the wait it reached.
        if (ended == HeadlessDriver.Reason.INPUT_WAIT) {
            assertEquals(seen.get(seen.size() - 1), new Observer().observe());
        }
    }
}
