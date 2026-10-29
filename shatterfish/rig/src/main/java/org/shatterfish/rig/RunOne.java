package org.shatterfish.rig;

import org.shatterfish.harness.knowledge.PlaybookFile;

import org.shatterfish.harness.knowledge.CodexKnowledge;
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

    /** The Codex folder a Brain that reads one is built on (story 4.1); the parent passes it always. */
    public static final String CODEX = "--codex";

    /** The weight set a Brain that scores by one is built on (story 4.5); read here, never by the Brain. */
    public static final String WEIGHTS = "--weights";

    /**
     * The Playbook a Brain that plays by one is built on (story 7.2); read here, never by the Brain.
     * Absent only for a baseline checkout that predates Playbooks, which the direction check plays.
     */
    public static final String PLAYBOOK = "--playbook";

    /**
     * A parameter vector played over the Playbook (story 10.35): {@code name=value} pairs joined by commas,
     * each name a registered tunable ({@link org.shatterfish.api.Tunables}) and each value inside its bounds.
     * For the Rig's tuner and its checks only. The vector becomes part of the Playbook the Brain is built on,
     * so its canonical text, and the configuration hash in the Run log's header, name every number that moved:
     * a Run is still (tag, seed, actions) and the Playbook, and a Replay plays the same vector.
     */
    public static final String TUNABLES = "--tunables";

    /** The vector {@code text} states, as {@code name=value,name=value}; refuses a name or a value it cannot read. */
    static Map<String, Long> tunables(String text) {
        Map<String, Long> given = new java.util.TreeMap<>();
        if (text.isBlank()) {
            return given;
        }
        for (String pair : text.split(",", -1)) {
            int cut = pair.indexOf('=');
            if (cut <= 0) {
                throw new IllegalArgumentException(TUNABLES + " is name=value pairs joined by commas: " + pair);
            }
            String name = pair.substring(0, cut).strip();
            long value;
            try {
                value = Long.parseLong(pair.substring(cut + 1).strip());
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException(TUNABLES + " " + name + " is a whole number: " + pair, notANumber);
            }
            if (given.put(name, value) != null) {
                throw new IllegalArgumentException(TUNABLES + " names " + name + " twice");
            }
        }
        return given;
    }

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
        return play(arguments, decider -> decider);
    }

    /**
     * The same Run, with the Brain wrapped by {@code wrap} before it is played. The direction check
     * wraps it to watch the Observations the Brain is handed; every other part of a Run, its
     * arguments, its header and its log, stays this method's, so a Run cannot drift from this one.
     */
    static RunOutcome play(Map<String, String> arguments,
                           java.util.function.UnaryOperator<org.shatterfish.api.Decider> wrap) {
        long seed = number(arguments, SEED);
        long salt = number(arguments, SALT);
        String brain = required(arguments, BRAIN);
        HeroClass heroClass = SeedSets.heroClass(required(arguments, CLASS));
        int challenges = (int) number(arguments, CHALLENGES);
        SeedSet.Entry triple = new SeedSet.Entry(seed, heroClass, challenges, SeedSet.code(seed));
        Path out = Path.of(required(arguments, OUT));
        if (!out.isAbsolute()) {
            // A Run's working directory is its own, not the caller's, so a relative path here means
            // two different places to the parent and the child. Hand-running one Run is exactly when
            // that bites, and exactly when nobody is watching for it.
            throw new IllegalArgumentException(OUT + " is an absolute path: a Run's working directory"
                    + " is its own, so a relative one names somewhere the caller did not mean: " + out);
        }
        if (Brains.readsWeights(brain) && !arguments.containsKey(WEIGHTS)) {
            throw new IllegalArgumentException("the Brain " + brain + " scores by a weight set, and a Run of it"
                    + " states the file with " + WEIGHTS);
        }
        org.shatterfish.api.Weights weights = Brains.readsWeights(brain)
                ? WeightsFile.read(Path.of(arguments.get(WEIGHTS)), brain)
                : null;
        org.shatterfish.api.Playbook playbook = Brains.readsPlaybook(brain) && arguments.containsKey(PLAYBOOK)
                ? PlaybookFile.read(Path.of(arguments.get(PLAYBOOK)), brain, Path.of(required(arguments, CODEX)))
                : null;
        if (arguments.containsKey(TUNABLES)) {
            if (playbook == null) {
                throw new IllegalArgumentException(TUNABLES + " moves the numbers of a Playbook, and this Run has none");
            }
            playbook = org.shatterfish.api.Tunables.with(playbook, tunables(arguments.get(TUNABLES)));
        }
        String knowledge = arguments.containsKey(CODEX) && Brains.readsCodex(brain)
                ? CodexKnowledge.digest(Path.of(arguments.get(CODEX))) : null;
        RunLoop.Logging logging = new RunLoop.Logging(out,
                required(arguments, COMMIT),
                new RunLog.Brain(brain, required(arguments, BRAIN_COMMIT), Brains.configHash(brain, weights, playbook, knowledge)),
                arguments.getOrDefault(REGISTRATION, ""), arguments.getOrDefault(MACHINE, ""));
        int cap = arguments.containsKey(CAP) ? (int) number(arguments, CAP) : RunLoop.TURN_CAP;
        where();
        org.shatterfish.api.Codex.Knowledge codex = arguments.containsKey(CODEX) && Brains.readsCodex(brain)
                ? CodexKnowledge.read(Path.of(arguments.get(CODEX)), org.shatterfish.harness.boot.HeadlessBoot.pinnedTag())
                : null;
        RunOutcome outcome = new RunLoop().playTriple(triple, salt, wrap.apply(Brains.of(brain, triple, codex, weights, playbook)), cap, logging);
        saySteadyWakes();
        return outcome;
    }

    /** The file a Run leaves in its own working directory, with the stray wakes of its actor thread. */
    public static final String STRAY_WAKES = "stray-wakes.txt";

    /**
     * Writes down how often this Run's actor thread woke between frames with no notify of the stepper's
     * (issue #399). Zero on a quiet machine. The parent reads it into the index and the direction table,
     * so that a nonzero count in a ranked Run is seen and not silent.
     */
    private static void saySteadyWakes() {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("").toAbsolutePath().resolve(STRAY_WAKES),
                    org.shatterfish.harness.scene.SceneStepper.strayWakesInProcess() + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("a Run could not say how often its actor thread woke", e);
        }
    }

    /** The count a child left in {@code working}, or -1 when it left none (an older child, or a Run that failed). */
    static long strayWakesIn(java.nio.file.Path working) {
        try {
            return Long.parseLong(java.nio.file.Files.readString(working.resolve(STRAY_WAKES)).strip());
        } catch (java.io.IOException | NumberFormatException none) {
            return -1;
        }
    }

    /** The file a Run leaves in its own working directory, naming that directory. */
    public static final String WHERE = "where.txt";

    /**
     * Writes down where this Run is running.
     *
     * <p>A Run gets a working directory of its own so that a Run which writes beside itself writes
     * beside itself and not beside another Run (AD-6). Nothing observed that: the parent made the
     * directory and then handed it to `ProcessBuilder.directory`, and deleting that one call left
     * every child in the parent's own directory with every test still green. This is the mark that
     * makes the claim checkable -- the Run says, in its own hand, where it was.
     */
    private static void where() {
        java.nio.file.Path here = java.nio.file.Path.of("").toAbsolutePath();
        try {
            java.nio.file.Files.writeString(here.resolve(WHERE), here + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("a Run could not say where it is running: " + here, e);
        }
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

    /** Every flag a Run knows. Held by name, like the parent's (RigOracleGateTest). */
    static final java.util.List<String> KNOWN = java.util.List.of(SEED, CLASS, SALT, OUT,
            COMMIT, BRAIN, BRAIN_COMMIT, REGISTRATION, MACHINE, CAP, CHALLENGES, CODEX, WEIGHTS, PLAYBOOK, TUNABLES);

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
