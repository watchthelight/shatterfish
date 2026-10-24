package org.shatterfish.harness.agent;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Says what the threads of an embedded Run are doing when the Run stops serving waits (issue #175).
 *
 * <p>The owner once saw the Overlay "freeze" and the log could not say why. This is for the next time.
 * A daemon thread of its own looks once a second: when the Run still plays and no Input wait has been
 * served for {@link #SECONDS} seconds of wall-clock time, it writes the state and stack of the Run's
 * threads -- the render thread, which is the Run's UI-role thread, the Brain's worker and the game's actor
 * thread when one is alive -- to the Run's side log and to standard error, once per stall, and again
 * only after a wait has been served since. The clock is the wall's and not the game's, because a frozen
 * render thread advances no game time.
 *
 * <p>It never kills, interrupts or waits on anything, and reads nothing the Brain sees. The side log is a
 * plain text file beside the Run log ({@code <run-id>.watchdog.txt}), written only by this thread, so it
 * never touches the Run log's chain.
 */
final class Watchdog implements AutoCloseable {

    /** Seconds without a served wait, while the Run plays, after which the threads are written down. */
    static final long SECONDS = 30;

    /** The file name a side log takes beside its Run log. */
    static final String SUFFIX = ".watchdog.txt";

    /** The name the game gives its actor thread ({@code core/.../scenes/GameScene.java}). */
    static final String ACTOR_THREAD = "SHPD Actor Thread";

    private final Path sideLog;
    private final PrintStream console;
    private final Thread render;
    private final Supplier<Thread> brain;
    private final LongSupplier waitIndex;
    private final BooleanSupplier playing;
    private final long limitNanos;
    private volatile long lastServed;
    private volatile boolean reported;
    private volatile boolean closed;
    private volatile long reports;
    private Thread thread;

    /**
     * @param sideLog   the side log to append to, or null to write to {@code console} only
     * @param console   where every report is also printed
     * @param render    the Run's UI-role thread
     * @param brain     the Brain's worker thread, or null before it has one
     * @param waitIndex the Run's wait index, for the report
     * @param playing   whether the Run still plays: not ended, not closed
     * @param seconds   the stall, in seconds, after which the threads are written down
     */
    Watchdog(Path sideLog, PrintStream console, Thread render, Supplier<Thread> brain, LongSupplier waitIndex,
             BooleanSupplier playing, long seconds) {
        this.sideLog = sideLog;
        this.console = console;
        this.render = render;
        this.brain = brain;
        this.waitIndex = waitIndex;
        this.playing = playing;
        this.limitNanos = seconds * 1_000_000_000L;
        this.lastServed = System.nanoTime();
    }

    /** The side log's path beside {@code runLog}, or null for a Run with no log. */
    static Path sideLogOf(Path runLog) {
        if (runLog == null) {
            return null;
        }
        String name = runLog.getFileName().toString();
        String stem = name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
        return runLog.resolveSibling(stem + SUFFIX);
    }

    /** Starts the daemon that looks once a second. */
    void start() {
        Thread looking = new Thread(() -> {
            while (!closed) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException stop) {
                    return;
                }
                check(System.nanoTime());
            }
        }, "shatterfish-watchdog");
        looking.setDaemon(true);
        thread = looking;
        looking.start();
    }

    /** A wait was served: the stall clock starts again, and the next stall is reported. */
    void served() {
        lastServed = System.nanoTime();
        reported = false;
    }

    /**
     * One look at {@code now} (a {@link System#nanoTime()} reading): writes the threads down and returns
     * true when the Run plays, has served no wait for the limit, and this stall has not been reported.
     */
    boolean check(long now) {
        if (closed || reported || !playing.getAsBoolean() || now - lastServed < limitNanos) {
            return false;
        }
        reported = true;
        reports++;
        String report = report((now - lastServed) / 1_000_000_000L);
        console.print(report);
        console.flush();
        if (sideLog != null) {
            try {
                Files.writeString(sideLog, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException unwritten) {
                console.println("[shatterfish] the watchdog could not write " + sideLog + ": " + unwritten);
            }
        }
        return true;
    }

    /** Stalls reported so far. */
    long reports() {
        return reports;
    }

    private String report(long seconds) {
        StringBuilder out = new StringBuilder();
        out.append("[shatterfish watchdog] ").append(Instant.now()).append(": no Input wait served for ")
                .append(seconds).append(" s while the Run plays; last wait confirmed ")
                .append(waitIndex.getAsLong()).append('\n');
        List<Thread> threads = new ArrayList<>();
        threads.add(render);
        Thread worker = brain.get();
        if (worker != null) {
            threads.add(worker);
        }
        for (Thread alive : Thread.getAllStackTraces().keySet()) {
            if (ACTOR_THREAD.equals(alive.getName()) && !threads.contains(alive)) {
                threads.add(alive);
            }
        }
        for (Thread one : threads) {
            if (one == null) {
                continue;
            }
            String role = one == render ? "render" : one == worker ? "brain" : "actor";
            out.append("  ").append(role).append(" \"").append(one.getName()).append("\" ")
                    .append(one.getState()).append('\n');
            for (StackTraceElement frame : one.getStackTrace()) {
                out.append("      at ").append(frame).append('\n');
            }
        }
        return out.toString();
    }

    @Override
    public void close() {
        closed = true;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
