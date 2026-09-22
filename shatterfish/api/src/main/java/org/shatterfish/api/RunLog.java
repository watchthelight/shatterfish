package org.shatterfish.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Run leaves behind (ADR-0011, story 3.2): one record per line of {@code <run-id>.jsonl},
 * hash chained, so that a published number is a claim a stranger can check rather than one they
 * have to take.
 *
 * <p><b>The chain is the point.</b> Each record's line carries the chain value over itself and
 * everything before it, so an edited byte anywhere in the file breaks every chain from that record
 * on. The chain is computed over the record's canonical JSON with three kinds of field left out --
 * the two the envelope adds ({@code prev}, {@code chain}) and the ones that say when and where
 * rather than what ({@code think_ms}, and the header's {@code machine} and {@code started}) -- so
 * the same Run recorded on a slow machine and a fast one chains identically and a skeptic with a
 * hex dump and a SHA-256 can recompute the whole file. {@link RunLogJson} names that excluded set
 * once and both the writer and the checks read it from there.
 *
 * <p><b>What the chain does not prove.</b> The header's {@code tag}, {@code commit}, {@code brain}
 * and {@code registration} are supplied by whoever started the Run: the driver has no checkout and
 * no Registration to read them from. They are attested, not verified -- the chain shows nobody
 * changed them after the Run, not that they were true when it began. What makes them worth
 * anything is the Registration committed before the first Run (story 3.5) and the Replay that
 * plays the log back (story 3.4).
 *
 * <p><b>Which kinds a headless Run writes.</b> {@link Header}, {@link Wait}, {@link Prompt} and
 * {@link End}. {@link Mode}, {@link Shadow} and {@link Boundary} belong to the Overlay (ADR-0013)
 * and {@link Unsupported} to a human input the executor cannot express, so nothing in the rig
 * produces them yet. They are defined, rendered and chained here all the same, so that the story
 * which starts writing them adds a caller and not a rule about the format.
 *
 * <p>No float reaches a chained field. A turn is thousandths of a turn and a score is
 * ten-thousandths, both as whole numbers, because two machines agreeing on a float's text is a
 * thing to hope for rather than a thing to rely on.
 */
public sealed interface RunLog
        permits RunLog.Header, RunLog.Wait, RunLog.Prompt, RunLog.Mode, RunLog.Shadow,
                RunLog.Boundary, RunLog.Unsupported, RunLog.End {

    /** The log schema version, which a reader refuses to guess at across (ADR-0011). */
    int VERSION = 1;

    /** The characters a run id's parts may hold, so that an id is one file name and not a path. */
    String PART_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*";

    /** The kind's own name, which every line writes first as {@code t}. */
    String t();

    /**
     * The file one Run is written to: {@code <tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>},
     * as ADR-0011 fixes it.
     *
     * <p>The Brain is the last part and never optional. A comparison plays both Brains on the same
     * triple under the same salt, so without it the two Runs of a pair would agree on every other
     * part and write to one file -- one of them silently winning (AD-14).
     */
    static String runId(String tag, HeroClass heroClass, int challenges, String seedCode, long salt,
                        String brain) {
        part(tag, "the upstream tag");
        Canon.require(heroClass != null, "a run id names the hero class");
        Canon.require(challenges >= 0 && challenges <= SeedSet.MAX_CHALLENGE_VALUE,
                "challenge flags are 0 through " + SeedSet.MAX_CHALLENGE_VALUE + ": " + challenges);
        Canon.text(seedCode, "a run id's seed code");
        Canon.require(seedCode.matches(SeedSet.CODE_PATTERN),
                "a run id's seed code is " + SeedSet.CODE_PATTERN + ": " + seedCode);
        part(brain, "the brain");
        return tag + "-" + heroClass.name() + "-" + challenges + "-" + seedCode + "-"
                + Long.toUnsignedString(salt, 16) + "-" + brain;
    }

    /** The file name {@link #runId} names. */
    static String fileName(String runId) {
        Canon.text(runId, "a run id");
        Canon.require(!runId.isEmpty(), "a run id is not empty");
        return runId + ".jsonl";
    }

    private static String part(String value, String what) {
        Canon.text(value, what);
        Canon.require(value.matches(PART_PATTERN),
                what + " is part of a file name, so it is " + PART_PATTERN + ": " + value);
        return value;
    }

    /** A 64-character lower-case hex digest, which is what every hash in a log is. */
    private static String digest(String value, String what) {
        Canon.text(value, what);
        Canon.require(value.matches("[0-9a-f]{64}"),
                what + " is a SHA-256 in lower-case hex: " + value);
        return value;
    }

    // ------------------------------------------------------------------------------ the header

    /** Which Brain played, and what identifies the build of it that did. */
    record Brain(String name, String commit, String configHash) {

        public Brain {
            part(name, "a brain's name");
            Canon.text(commit, "a brain's commit");
            Canon.require(!commit.isEmpty(), "a brain's commit is stated, or the Run cannot be rebuilt");
            digest(configHash, "a brain's configuration hash");
        }
    }

    /**
     * The first line of every log: the whole tuple a Run is determined by, and the versions of
     * everything that decided what it saw.
     *
     * @param registration the Registration this Run was played under, or empty when there is none
     *                     (story 3.5); a ranked Run has one
     * @param machine      what the Run ran on -- not chained, because it says nothing about the Run
     * @param started      when it began, ISO-8601 -- not chained, for the same reason
     */
    record Header(int v, String tag, String commit, HeroClass heroClass, int challenges, long seed,
                  String seedCode, long salt, int profile, int obsv, int codex, Brain brain,
                  String registration, boolean oracle, String machine, String started)
            implements RunLog {

        public Header {
            Canon.require(v == VERSION, "this build writes log schema version " + VERSION + ", not " + v);
            part(tag, "the upstream tag");
            Canon.text(commit, "the Shatterfish commit");
            Canon.require(!commit.isEmpty(),
                    "the Shatterfish commit is stated, or nothing says which build played this Run");
            Canon.require(heroClass != null, "a Run names its hero class");
            Canon.require(challenges >= 0 && challenges <= SeedSet.MAX_CHALLENGE_VALUE,
                    "challenge flags are 0 through " + SeedSet.MAX_CHALLENGE_VALUE + ": " + challenges);
            Canon.require(seed >= 0 && seed < SeedSet.TOTAL_SEEDS,
                    "a seed is at least 0 and under " + SeedSet.TOTAL_SEEDS + ": " + seed);
            Canon.text(seedCode, "a Run's seed code");
            Canon.require(seedCode.equals(SeedSet.code(seed)),
                    "the code for seed " + seed + " is " + SeedSet.code(seed) + ", not " + seedCode);
            Canon.require(profile >= 1, "a Profile version is positive: " + profile);
            Canon.require(obsv >= 1, "an Observation schema version is positive: " + obsv);
            Canon.require(codex >= 1, "a Codex version is positive: " + codex);
            Canon.require(brain != null, "a Run names the Brain that played it");
            Canon.text(registration, "a Run's registration");
            Canon.text(machine, "the machine a Run ran on");
            Canon.text(started, "when a Run started");
        }

        @Override
        public String t() {
            return "header";
        }

        /** The file this Run is written to. */
        public String runId() {
            return RunLog.runId(tag, heroClass, challenges, seedCode, salt, brain.name());
        }
    }

    // -------------------------------------------------------------------------- what a Brain did

    /** One Action a decider weighed, and what it thought of it. Scores are ten-thousandths. */
    record Choice(Action action, long score, String why) {

        public Choice {
            Canon.require(action != null, "a choice names an Action");
            Canon.text(why, "why a choice was weighed as it was");
        }
    }

    /**
     * Why a Brain took the Action it took (ADR-0011). A decider that states none carries none: the
     * random agent has no goal and no alternatives, and a decision half filled in would read as a
     * Brain that had thought about it.
     */
    record Decision(String goal, Choice chosen, List<Choice> alternatives, List<String> flags,
                    String policy) {

        /** At most three alternatives are recorded, so the log stays a log (ADR-0011). */
        public static final int ALTERNATIVES = 3;

        public Decision {
            Canon.text(goal, "a decision's goal");
            Canon.require(!goal.isEmpty(), "a decision states the goal it served");
            Canon.require(chosen != null, "a decision names the choice it took");
            alternatives = Canon.positional(alternatives, "a decision's alternatives");
            Canon.require(alternatives.size() <= ALTERNATIVES,
                    "at most " + ALTERNATIVES + " alternatives are recorded: " + alternatives.size());
            flags = Canon.positional(flags, "a decision's flags");
            Canon.text(policy, "a decision's policy");
            Canon.require(!policy.isEmpty(), "a decision names the policy that took it");
        }
    }

    /**
     * One Input wait: what the screen was, what was done about it, and how long that took.
     *
     * @param turn      thousandths of a turn, as the game counts the duration it shows a player
     * @param obs       the Observation's hash, which a Replay recomputes and compares (story 3.4)
     * @param sections  the section hashes, so a mismatch names the section rather than the wait
     * @param decision  why, when the decider says; null when it does not
     * @param belief    the Belief's hash, or empty when the decider carries none
     * @param thinkMs   how long the decider took -- the one field the chain leaves out
     */
    record Wait(long k, long turn, int depth, int branch, String obs, Map<String, String> sections,
                Action action, Decision decision, String belief, List<Integer> highlights,
                long thinkMs) implements RunLog {

        public Wait {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            Canon.require(turn >= 0, "a turn is at least 0: " + turn);
            Canon.require(depth >= 0, "a depth is at least 0: " + depth);
            Canon.require(branch >= 0, "a branch is at least 0: " + branch);
            digest(obs, "an Observation hash");
            sections = sections(sections);
            Canon.require(action != null, "a wait records the Action taken at it");
            Canon.text(belief, "a Belief hash");
            Canon.require(belief.isEmpty() || belief.matches("[0-9a-f]{64}"),
                    "a Belief hash is a SHA-256 in lower-case hex, or empty: " + belief);
            highlights = Canon.positional(highlights, "a wait's highlights");
            for (int cell : highlights) {
                Canon.cell(cell, "a highlighted cell");
            }
            Canon.require(thinkMs >= 0, "thinking took at least no time: " + thinkMs);
        }

        private static Map<String, String> sections(Map<String, String> given) {
            Canon.require(given != null, "a wait records the section hashes");
            Canon.require(!given.isEmpty(), "a wait records every section's hash, and there are sections");
            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> section : given.entrySet()) {
                Canon.text(section.getKey(), "a section's name");
                copy.put(section.getKey(), digest(section.getValue(), "the section " + section.getKey()));
            }
            return Map.copyOf(copy);
        }

        @Override
        public String t() {
            return "wait";
        }
    }

    /**
     * A Prompt the game put in front of the Run, and the option taken. It rides beside the wait
     * rather than inside it because a Prompt is a thing the game did, and the wait record says what
     * was done about it.
     */
    record Prompt(long k, PromptKind kind, Action answer) implements RunLog {

        public Prompt {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            Canon.require(kind != null, "a prompt record names the Prompt's kind");
            Canon.require(answer != null, "a prompt record names the option taken");
        }

        @Override
        public String t() {
            return "prompt";
        }
    }

    // --------------------------------------------------------------- the Overlay's kinds, unwritten

    /** A change of Mode at the Overlay (ADR-0013). Nothing in a headless Run writes one. */
    record Mode(long k, String mode, String speed) implements RunLog {

        /** The three Modes ADR-0013 names. */
        public static final List<String> MODES = List.of("PAUSED", "RUNNING", "HUMAN");

        public Mode {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            Canon.text(mode, "a Mode");
            Canon.require(MODES.contains(mode), "a Mode is one of " + MODES + ": " + mode);
            Canon.text(speed, "a speed mode");
            Canon.require(!speed.isEmpty(), "a Mode change names the speed it ran at");
        }

        @Override
        public String t() {
            return "mode";
        }
    }

    /**
     * What the Brain would have done at a wait a human took (ADR-0013). It was never executed, and
     * the record exists so that a human's Run still says what the Brain thought.
     */
    record Shadow(long k, Decision decision) implements RunLog {

        public Shadow {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            Canon.require(decision != null, "a shadow record is the decision that was not taken");
        }

        @Override
        public String t() {
            return "shadow";
        }
    }

    /**
     * A save and quit: the salt and the chain as they stood, so a resumed Run goes on with the same
     * log rather than beginning a second one (ADR-0013).
     *
     * @param chainAt the chain value at the boundary. It is a field of this record and not the
     *                envelope's own {@code chain}, which is why it is spelt differently; the
     *                envelope's belongs to this line, and this one belongs to the Run.
     */
    record Boundary(long k, long salt, String chainAt) implements RunLog {

        public Boundary {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            digest(chainAt, "the chain at a boundary");
        }

        @Override
        public String t() {
            return "boundary";
        }
    }

    /**
     * A human input the executor could not express. From here a Run is no longer verifiable, which
     * the {@link End} record then says (ADR-0011).
     */
    record Unsupported(long k, String input) implements RunLog {

        public Unsupported {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            Canon.text(input, "the input that could not be expressed");
            Canon.require(!input.isEmpty(), "an unsupported record names the input it could not express");
        }

        @Override
        public String t() {
            return "unsupported";
        }
    }

    // --------------------------------------------------------------------------------- the ending

    /**
     * How a Run ended and what it did. Scores are ten-thousandths, turns are thousandths, and both
     * are whole numbers for the reason nothing in a chained field is a float.
     */
    record Outcome(boolean win, boolean ascended, long score, int depth, long turns, String cause,
                   int bosses) {

        public Outcome {
            Canon.require(score >= 0, "a score is at least 0: " + score);
            Canon.require(depth >= 0, "a depth is at least 0: " + depth);
            Canon.require(turns >= 0, "a turn count is at least 0: " + turns);
            Canon.text(cause, "how a Run ended");
            Canon.require(!cause.isEmpty(), "a Run that ended says why");
            Canon.require(bosses >= 0, "a boss count is at least 0: " + bosses);
            Canon.require(!ascended || win, "a Run that ascended won");
        }
    }

    /**
     * The last line of a Run that finished. A log without one is *incomplete*: the Run was killed
     * or crashed, and the Rig counts it as such rather than repairing it (ADR-0011, ADR-0012).
     *
     * @param verifiable whether a Replay can reproduce this Run, which an {@link Unsupported}
     *                   record makes false from the wait it names
     */
    record End(long k, Outcome outcome, boolean verifiable) implements RunLog {

        public End {
            Canon.require(k >= 0, "a wait index is at least 0: " + k);
            Canon.require(outcome != null, "a Run that ended says how");
        }

        @Override
        public String t() {
            return "end";
        }
    }
}
