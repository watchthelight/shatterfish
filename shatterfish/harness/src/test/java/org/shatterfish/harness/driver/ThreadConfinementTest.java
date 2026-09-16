package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.observer.OracleObserver;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ports are confined to the UI-role thread (FR-12; ADR-0013): the Observer, the oracle, the
 * executor and the driver's own stepping fail loudly when called from any other thread, naming the
 * role, the thread that owns it and the thread that called, by name and id, before they read a
 * game field; on the driver thread they work as they did; the role is claimed by the driver that
 * starts a Run and released when it closes, a second claim while a Run holds it is refused, a
 * release by another thread is refused, and another thread can hold it for the next Run.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ThreadConfinementTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 14_142_135L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    @DisplayName("a foreign thread is refused by name and id, before any game state is read")
    void a_foreign_thread_is_refused_by_name() throws Exception {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        Thread owner = Thread.currentThread();
        assertSame(owner, UiRole.owner(), "the thread that started the Run holds the role");
        Observation observation = new Observer().observe();

        IllegalStateException observing = onAForeignThread("a brain worker", () -> new Observer().observe());
        names(observing, "the Observer's read", owner, "a brain worker");

        IllegalStateException oracle = onAForeignThread("a labelling tool", () -> new OracleObserver().observe());
        names(oracle, "OracleObserver.observe()", owner, "a labelling tool");

        Action wait = observation.actions().actions().stream()
                .filter(a -> a instanceof Action.Wait).findFirst().orElseThrow();
        IllegalStateException executing = onAForeignThread("a render thread", () -> new ActionExecutor().execute(observation, wait));
        names(executing, "ActionExecutor.execute()", owner, "a render thread");

        IllegalStateException stepping = onAForeignThread("another loop", () -> driver.stepToInputWait());
        names(stepping, "HeadlessDriver.stepToInputWait()", owner, "another loop");
        IllegalStateException framing = onAForeignThread("another loop", () -> driver.step());
        names(framing, "HeadlessDriver.step()", owner, "another loop");

        // A foreign thread with the owner's own name is still foreign: the identity is the thread,
        // never its name, and the message tells the two apart by id.
        IllegalStateException impostor = onAForeignThread(owner.getName(), () -> new Observer().observe());
        assertTrue(impostor.getMessage().contains("must run on the UI-role thread"), impostor.getMessage());
        assertTrue(impostor.getMessage().contains("(#" + owner.threadId() + ")"), impostor.getMessage());
        assertFalse(impostor.getMessage().replace("(#" + owner.threadId() + ")", "").contains("(#" + owner.threadId() + ")"),
                "the caller's id is another");

        // A foreign call touched nothing: the same wait reads the same on the right thread.
        assertEquals(observation.hash(), new Observer().observe().hash(), "the foreign calls changed nothing");
        assertTrue(new ActionExecutor().execute(observation, wait) instanceof Outcome.Applied, "the right thread executes");
    }

    @Test
    @DisplayName("the check comes before any read: with no Run, the role's own refusal is what fails, on every port")
    void the_check_comes_first() {
        assertNull(UiRole.owner(), "no Run, no role");
        // The Observer's own "no Run is in progress" would read Dungeon.level first; the role's
        // refusal, which names the role, is the one that fires.
        IllegalStateException observing = assertThrows(IllegalStateException.class, () -> new Observer().observe());
        assertTrue(observing.getMessage().contains("no Run is in progress") && observing.getMessage().contains("UI-role"),
                observing.getMessage());
        // The executor would refuse "not at an Input wait" after reading the hero; with no role
        // claimed it throws instead, before reading.
        IllegalStateException executing = assertThrows(IllegalStateException.class,
                () -> new ActionExecutor().execute(null, null));
        assertTrue(executing.getMessage().contains("UI-role"), executing.getMessage());
        IllegalStateException oracle = assertThrows(IllegalStateException.class, () -> new OracleObserver().observe());
        assertTrue(oracle.getMessage().contains("OracleObserver.observe()") && oracle.getMessage().contains("UI-role"),
                oracle.getMessage());
    }

    @Test
    @DisplayName("the driver thread is the role: claimed at start, released at close, not claimable or releasable by another, and free for the next Run")
    void the_driver_thread_is_the_role() throws Exception {
        assertNull(UiRole.owner(), "no Run, no role");
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        Thread owner = Thread.currentThread();
        assertSame(owner, UiRole.owner());

        Thread other = new Thread(() -> { }, "another driver");
        other.start();
        other.join();
        IllegalArgumentException dead = assertThrows(IllegalArgumentException.class, () -> UiRole.claim(other));
        assertTrue(dead.getMessage().contains("not alive"), dead.getMessage());
        IllegalStateException second = onAForeignThread("another driver", () -> UiRole.claim(Thread.currentThread()));
        assertTrue(second.getMessage().contains("another driver") && second.getMessage().contains("(#" + owner.threadId() + ")"),
                second.getMessage());
        IllegalStateException releasing = onAForeignThread("a stranger", UiRole::release);
        assertTrue(releasing.getMessage().contains("only the UI-role thread") && releasing.getMessage().contains("a stranger"),
                releasing.getMessage());
        assertSame(owner, UiRole.owner(), "the stranger released nothing");
        UiRole.claim(owner);
        driver.stepToInputWait();
        assertNotNull(new Observer().observe());

        driver.close();
        driver = null;
        assertNull(UiRole.owner(), "closing the Run releases the role");

        // The next Run may belong to another thread, and the ports follow it there.
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<String> hash = new AtomicReference<>();
        Thread next = new Thread(() -> {
            try (HeadlessDriver theirs = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT)) {
                assertSame(Thread.currentThread(), UiRole.owner());
                theirs.stepToInputWait();
                hash.set(new Observer().observe().hash());
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "the next Run's thread");
        next.setDaemon(true);
        next.start();
        next.join(120_000);
        assertFalse(next.isAlive(), "the next Run's thread hung");
        assertNull(thrown.get(), String.valueOf(thrown.get()));
        assertNotNull(hash.get(), "the next Run observed on its own thread");
        assertNull(UiRole.owner(), "and released on close");
    }

    private static void names(IllegalStateException failure, String port, Thread owner, String caller) {
        String message = failure.getMessage();
        assertTrue(message.contains(port), message);
        assertTrue(message.contains("UI-role thread"), message);
        assertTrue(message.contains("'" + owner.getName() + "' (#" + owner.threadId() + ")"), message);
        assertTrue(message.contains("'" + caller + "'"), message);
    }

    private static IllegalStateException onAForeignThread(String name, Runnable call) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            try {
                call.run();
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, name);
        foreign.setDaemon(true);
        foreign.start();
        foreign.join(30_000);
        assertFalse(foreign.isAlive(), "the call from '" + name + "' hung instead of failing");
        Throwable t = thrown.get();
        assertNotNull(t, "the call from '" + name + "' was accepted");
        assertTrue(t instanceof IllegalStateException, "an IllegalStateException, not " + t);
        return (IllegalStateException) t;
    }
}
