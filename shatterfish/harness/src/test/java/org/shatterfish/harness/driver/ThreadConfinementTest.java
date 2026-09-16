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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ports are confined to the UI-role thread (FR-12; ADR-0013): the Observer and the executor
 * fail loudly when called from any other thread, naming the role, the thread that owns it and the
 * thread that called, before they read a game field; on the driver thread they work as they did;
 * the role is claimed by the driver that starts a Run and released when it closes, and a second
 * claim while a Run holds it is refused.
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
    @DisplayName("a foreign thread is refused by name, before any game state is read")
    void a_foreign_thread_is_refused_by_name() throws Exception {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        assertSame(Thread.currentThread(), UiRole.owner(), "the thread that started the Run holds the role");
        Observation observation = new Observer().observe();

        IllegalStateException observing = onAForeignThread("a brain worker", () -> new Observer().observe());
        names(observing, "Observer.observe()", Thread.currentThread().getName(), "a brain worker");

        Action wait = observation.actions().actions().stream()
                .filter(a -> a instanceof Action.Wait).findFirst().orElseThrow();
        IllegalStateException executing = onAForeignThread("a render thread", () -> new ActionExecutor().execute(observation, wait));
        names(executing, "ActionExecutor.execute()", Thread.currentThread().getName(), "a render thread");

        // A foreign thread with the owner's own name is still foreign: the identity is the thread,
        // never its name.
        IllegalStateException impostor = onAForeignThread(Thread.currentThread().getName(), () -> new Observer().observe());
        assertTrue(impostor.getMessage().contains("must run on the UI-role thread"), impostor.getMessage());

        // A foreign call touched nothing: the same wait reads the same on the right thread.
        assertEquals(observation.hash(), new Observer().observe().hash(), "the foreign calls changed nothing");
        assertTrue(new ActionExecutor().execute(observation, wait) instanceof Outcome.Applied, "the right thread executes");
    }

    @Test
    @DisplayName("the driver thread is the role: claimed at start, released at close, and not claimable twice")
    void the_driver_thread_is_the_role() throws Exception {
        assertNull(UiRole.owner(), "no Run, no role");
        IllegalStateException noRun = assertThrows(IllegalStateException.class, () -> new Observer().observe());
        assertTrue(noRun.getMessage().contains("no Run is in progress"), noRun.getMessage());

        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        assertSame(Thread.currentThread(), UiRole.owner());
        Thread other = new Thread(() -> { }, "another driver");
        IllegalStateException second = assertThrows(IllegalStateException.class, () -> UiRole.claim(other));
        assertTrue(second.getMessage().contains("another driver") && second.getMessage().contains(Thread.currentThread().getName()),
                second.getMessage());
        UiRole.claim(Thread.currentThread());
        driver.stepToInputWait();
        assertNotNull(new Observer().observe());

        driver.close();
        driver = null;
        assertNull(UiRole.owner(), "closing the Run releases the role");
    }

    private static void names(IllegalStateException failure, String port, String owner, String caller) {
        String message = failure.getMessage();
        assertTrue(message.contains(port), message);
        assertTrue(message.contains("UI-role thread"), message);
        assertTrue(message.contains("'" + owner + "'"), message);
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
        foreign.start();
        foreign.join();
        Throwable t = thrown.get();
        assertNotNull(t, "the call from '" + name + "' was accepted");
        assertTrue(t instanceof IllegalStateException, "an IllegalStateException, not " + t);
        return (IllegalStateException) t;
    }
}
