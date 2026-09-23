package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One Run, in this process and nothing else (story 3.3, AD-6).
 *
 * <p>This is what the runner starts a JVM for. The game's state is static and process-wide — the
 * dungeon, the statistics, the badges, the scene — so two Runs in one process are two Runs sharing
 * everything that defines them, which is what AD-6 forbids and what story 3.1 found the Profile
 * doing even in a single thread. A child per Run is the only arrangement in which "the same tuple
 * gives the same Run" is a property of the tuple rather than of the order the Runs happened to go
 * in.
 *
 * <p>It takes its tuple from the command line, plays it, and exits. It prints nothing a caller
 * parses: the Run's log is the record, and the parent reads that. What it does say, on the way out,
 * is why it failed, because a child that dies silently is the one thing the parent cannot tell from
 * a child that hung.
 *
 * <p>There is no oracle flag here, in any spelling. The Rig cannot start an oracle Run because
 * there is no way to ask for one, and the parent also reads back what every finished log says about
 * it (FR-11, non-negotiable 1).
 */
public final class RunOne {

    /** What the parent names each value with, so a mistyped argument is a refusal and not a guess. */
    public static final String SEED = "--seed";

    public static final String CLASS = "--class";

    public static final String SALT = "--salt";

    public static final String OUT = "--out";

    public static final String COMMIT = "--commit";

    public static final String BRAIN = "--brain";

    public static final String BRAIN_COMMIT = "--brain-commit";

    public static final String REGISTRATION = "--registration";

    public static final String MACHINE = "--machine";

    public static final String CAP = "--cap";

    public static final String CHALLENGES = "--challenges";

    private RunOne() {
    }

    public static void main(String[] args) {
        try {
            play(arguments(args));
        } catch (RuntimeException | Error failed) {
            // The parent counts this Run incomplete and keeps whatever log it left. The message is
            // the only thing that distinguishes a Run that could not start from one that hung, so
            // it goes to the error stream where the parent collects it.
            System.err.println("the Run failed: " + failed);
            failed.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /** The outcome, for a caller in this process; the log is the record the Rig reads. */
    public static RunOutcome play(Map<String, String> arguments) {
        long seed = number(arguments, SEED);
        long salt = number(arguments, SALT);
        String brain = required(arguments, BRAIN);
        HeroClass heroClass = SeedSets.heroClass(required(arguments, CLASS));
        int challenges = (int) number(arguments, CHALLENGES);
        SeedSet.Entry triple = new SeedSet.Entry(seed, heroClass, challenges, SeedSet.code(seed));
        RunLoop.Logging logging = new RunLoop.Logging(Path.of(required(arguments, OUT)),
                required(arguments, COMMIT),
                new RunLog.Brain(brain, required(arguments, BRAIN_COMMIT), Brains.configHash(brain)),
                arguments.getOrDefault(REGISTRATION, ""), arguments.getOrDefault(MACHINE, ""));
        int cap = arguments.containsKey(CAP) ? (int) number(arguments, CAP) : RunLoop.TURN_CAP;
        return new RunLoop().playTriple(triple, salt, Brains.of(brain, salt), cap, logging);
    }

    /** The command line as a map, refusing a flag this child does not know and a value-less one. */
    static Map<String, String> arguments(String[] args) {
        Map<String, String> given = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String flag = args[i];
            if (!flag.startsWith("--")) {
                throw new IllegalArgumentException("expected a flag at argument " + i + ", found " + flag);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(flag + " takes a value");
            }
            if (given.put(flag, args[i + 1]) != null) {
                throw new IllegalArgumentException(flag + " is given twice");
            }
        }
        for (String flag : given.keySet()) {
            if (!KNOWN.contains(flag)) {
                throw new IllegalArgumentException("this Run does not know " + flag + "; it knows " + KNOWN);
            }
        }
        return given;
    }

    private static final java.util.List<String> KNOWN = java.util.List.of(SEED, CLASS, SALT, OUT,
            COMMIT, BRAIN, BRAIN_COMMIT, REGISTRATION, MACHINE, CAP, CHALLENGES);

    private static String required(Map<String, String> arguments, String flag) {
        String value = arguments.get(flag);
        if (value == null) {
            throw new IllegalArgumentException("a Run states " + flag);
        }
        return value;
    }

    private static long number(Map<String, String> arguments, String flag) {
        String value = required(arguments, flag);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(flag + " is a number: " + value, notANumber);
        }
    }
}
