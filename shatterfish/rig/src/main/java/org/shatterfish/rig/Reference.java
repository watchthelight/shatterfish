package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes the committed reference Run: one log, and the index that publishes its chain (story 3.4).
 *
 * <p>The nightly cross-platform job replays this Run on Windows and on Linux. A Run is meant to be a
 * property of its tuple and nothing else, and the one difference no test on one machine can find is
 * a difference between machines — so one Run is fixed in every part and committed, and a platform
 * that reaches its chain has reproduced every Observation, every Action, every section hash, the
 * turn counts and the ending.
 *
 * <p><b>The index is committed with it, and that is not bookkeeping.</b> A chain a file states about
 * itself proves only that the file is internally consistent; it becomes evidence when its value is
 * recorded somewhere the file's author does not control. For a Rig invocation that somewhere is
 * {@code runs.jsonl}; for this one Run it is the same file, committed beside the log, so that
 * regenerating the reference is a two-file change a reviewer can see rather than a silent swap.
 * {@code --verify} refuses a folder with no index for exactly this reason.
 *
 * <p>The attested commit is forty zeros on purpose. A real commit would change every time this was
 * regenerated and would say nothing true about the build that replays it — a Replay attests what the
 * log attests, so the value here is a claim about nobody, which is the honest claim to make.
 */
public final class Reference {

    /** The tuple, fixed. Changing any of these makes a different reference Run. */
    public static final long SEED = 12_648_430L;

    public static final long SALT = 0x5A18_1417L;

    public static final int CAP = 400;

    public static final HeroClass HERO = HeroClass.WARRIOR;

    public static final int CHALLENGES = 0;

    public static final String BRAIN = Brains.RANDOM;

    /** Nobody's build, so that the value is a claim about nobody. */
    public static final String COMMIT = "0".repeat(40);

    /** Where this Run says it was played, which the chain does not cover. */
    public static final String MACHINE = "the reference";

    private Reference() {
    }

    /** The name of the file this writes, which is the Run's own id. */
    public static String fileName() {
        return RunLog.fileName(RunLog.runId(
                org.shatterfish.harness.boot.HeadlessBoot.pinnedTag(), HERO, CHALLENGES,
                SeedSet.code(SEED), SALT, BRAIN));
    }

    public static void main(String[] args) {
        Path out = Path.of(args.length > 0 ? args[0] : "reference").toAbsolutePath().normalize();
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(RunOne.SEED, Long.toString(SEED));
        arguments.put(RunOne.CLASS, HERO.name());
        arguments.put(RunOne.CHALLENGES, Integer.toString(CHALLENGES));
        arguments.put(RunOne.SALT, Long.toString(SALT));
        arguments.put(RunOne.CAP, Integer.toString(CAP));
        arguments.put(RunOne.BRAIN, BRAIN);
        arguments.put(RunOne.BRAIN_COMMIT, COMMIT);
        arguments.put(RunOne.COMMIT, COMMIT);
        arguments.put(RunOne.MACHINE, MACHINE);
        arguments.put(RunOne.OUT, out.toString());

        long began = System.nanoTime();
        RunOne.play(arguments);
        long millis = (System.nanoTime() - began) / 1_000_000L;

        // The index, written through the Rig's own path rather than by hand, so that the chain it
        // publishes is the one a real invocation would publish and `--verify` compares like with
        // like. `finish` reads the log back and takes the chain from the file.
        String runId = RunLog.runId(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag(), HERO,
                CHALLENGES, SeedSet.code(SEED), SALT, BRAIN);
        RunIndex index = new RunIndex(out);
        index.started(new RunIndex.Entry(runId, RunLog.fileName(runId), RunIndex.State.STARTED, "",
                SEED, HERO.name(), CHALLENGES, SALT, "", 0, ""));
        Runner.finish(index, out, runId, "", millis, new AtomicLong());

        System.out.println("the reference Run is " + out.resolve(RunLog.fileName(runId))
                + ", chaining to " + index.entries().get(0).chain());
    }
}
