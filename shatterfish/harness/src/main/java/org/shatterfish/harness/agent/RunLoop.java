package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
import com.watabou.noosa.Scene;
import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.RunLogWriter;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.rng.Salt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.time.Instant;

/**
 * Plays one Run from its first Input wait to its ending. This is the loop above the driver: the
 * driver reports what happened and stops, and this decides what the report means for the Run
 * (ADR-0015). The rig will grow its own loop here later, and a Replay will grow another; what they
 * share is this shape, so the shape is worth being plain about.
 *
 * <p>Each wait is the same five steps in the same order: observe, choose, execute, count, step. A
 * refusal is counted and the wait is served again, because a refusal changes nothing and the screen
 * is still asking. Anything that is not a wait ends the Run with a named cause, including the things
 * that would otherwise be a hang.
 */
public final class RunLoop {

    /**
     * The turn cap, in the game's own turns. A Run still alive here is stopped and counted as a
     * loss with {@link RunOutcome.Cause#TURN_CAP} (epic 1's own number).
     */
    public static final int TURN_CAP = 20_000;

    /**
     * How many refusals in a row mean the Run is going nowhere. One or two are ordinary — the set
     * is a wait old in places — but a screen that refuses everything offered is a disagreement
     * between {@code ValidActions} and the game, and that is worth a story rather than a loop.
     */
    private static final int REFUSALS_IN_A_ROW = 64;

    /** Frames the driver may spend on a single wait before the Run is called stuck. */
    private static final int FRAME_BUDGET = 20_000;

    private final ActionExecutor executor = new ActionExecutor();

    /**
     * Plays a Run of {@code heroClass} on {@code seed}, choosing at random from {@code agentSeed},
     * and says how it ended.
     */
    /**
     * Plays a Run of {@code heroClass} on {@code seed}, choosing at random from {@code agentSeed},
     * with a salt drawn for it. The salt is in the outcome, because a Run whose salt is not written
     * down cannot be replayed and its numbers cannot be checked.
     */
    public RunOutcome play(long seed, HeroClass heroClass, long agentSeed) {
        return play(seed, heroClass, Salt.draw(), new RandomAgent(agentSeed), TURN_CAP);
    }

    /** Plays a Run with a salt the caller chose, which is what a Replay and a rig pair do. */
    public RunOutcome play(long seed, HeroClass heroClass, long salt, long agentSeed) {
        return play(seed, heroClass, salt, new RandomAgent(agentSeed), TURN_CAP);
    }

    /**
     * Plays a Run with a chooser of the caller's own. The Run's resources go with it: the driver is
     * closed whatever happens.
     */
    public RunOutcome play(long seed, HeroClass heroClass, Decider agent) {
        return play(seed, heroClass, Salt.draw(), agent, TURN_CAP);
    }

    /**
     * Plays a Run that stops at {@code turnCap} turns rather than the story's own cap. The cap is a
     * number and the behaviour at it is a rule; a test that wants the rule should not have to spend
     * twenty thousand turns reaching it, and a Run that wants the rule gets {@link #TURN_CAP}.
     */
    /**
     * What only the caller can say about a Run (story 3.2). The driver has no checkout to read a
     * commit from and no Registration to read an id from, so the header's provenance arrives here
     * or not at all. It is attested rather than verified: the chain shows nobody changed these
     * after the Run, not that they were true when it started, which is what the Registration
     * committed before the first Run is for (story 3.5).
     *
     * @param folder       where the log file goes; the file's name is the run id
     * @param commit       the Shatterfish commit this build was made from
     * @param brain        which Brain played, and which build of it
     * @param registration the Registration this Run was played under, or empty
     * @param machine      what it ran on -- recorded, and left out of the chain
     * @param oracle       whether this Run may see what a player could not. The caller states it
     *                     rather than the loop assuming it: a field that is always {@code false}
     *                     because nothing can set it is not a flag, and the Rig refuses a Run whose
     *                     header carries it (non-negotiable 1). Every wait then checks the
     *                     Observation's own oracle bit against this one, so a Run that says it is
     *                     fair and is not stops at its first wait.
     */
    public record Logging(Path folder, String commit, RunLog.Brain brain, String registration,
                          String machine, boolean oracle) {

        public Logging {
            if (folder == null || commit == null || brain == null || registration == null || machine == null) {
                throw new IllegalArgumentException("a logged Run states where it is written and who played it");
            }
        }

        /** A fair Run, which is every Run the Rig ranks. */
        public Logging(Path folder, String commit, RunLog.Brain brain, String registration, String machine) {
            this(folder, commit, brain, registration, machine, false);
        }
    }

    /**
     * Plays a Run and writes its log (story 3.2, ADR-0011). The Run is the same Run either way:
     * logging observes and records, and changes nothing about what is played -- the only value it
     * reads that the unlogged loop does not is the wall clock, and that one goes in the field the
     * chain leaves out.
     */
    public RunOutcome play(long seed, HeroClass heroClass, long salt, Decider agent, int turnCap,
                           Logging logging) {
        if (logging == null) {
            throw new IllegalArgumentException("a logged Run says where its log goes");
        }
        // Booted before the tag is read: Game.version is null until the boot sets it, and the
        // header names the release this Run was played on.
        HeadlessBoot.ensure();
        // The Run is started before its header is written, because the header is a statement about
        // this Run and half of it does not exist until the game has been initialised. The
        // challenges are the case that bit: `Dungeon.challenges` is assigned in `Dungeon.init`
        // (core/.../Dungeon.java:236), which runs inside the start below, so a header built first
        // recorded whatever the previous Run in this process had left in the static -- and the
        // challenges are part of the run id, so it named the file after a Run nobody played.
        HeadlessDriver driver = HeadlessDriver.start(seed, heroClass, salt);
        RunLogWriter opened;
        try {
            opened = RunLogWriter.open(logging.folder(), new RunLog.Header(RunLog.VERSION,
                    Observer.upstreamTag(), logging.commit(),
                    org.shatterfish.api.HeroClass.valueOf(heroClass.name()), Dungeon.challenges, seed,
                    SeedSet.code(seed), salt, turnCap, Profile.VERSION,
                    ObservationCodec.SCHEMA_VERSION, Codex.VERSION, logging.brain(),
                    logging.registration(), logging.oracle(), logging.machine(),
                    Instant.now().toString()));
        } catch (RuntimeException | Error opening) {
            // The Run was started and nothing will play it, so the driver is closed here rather
            // than left holding the process's one UI role.
            driver.close();
            throw opening;
        }
        try (RunLogWriter log = opened) {
            return play(driver, seed, heroClass, salt, agent, turnCap, log, logging.oracle());
        }
    }

    public RunOutcome play(long seed, HeroClass heroClass, long salt, Decider agent, int turnCap) {
        return play(HeadlessDriver.start(seed, heroClass, salt), seed, heroClass, salt, agent,
                turnCap, null, false);
    }

    /**
     * One triple of a Seed set, played and logged (story 3.3).
     *
     * <p>The Rig cannot see the game: `harness` depends on `core` with `implementation` and
     * ADR-0003's edges do not give `rig` the game, so the game's own {@code HeroClass} is not a type
     * the Rig can name — it cannot even take part in resolving an overload. What crosses a module
     * edge is an {@code api} value, which is the architecture's rule rather than a convenience, and
     * a triple is the value the Rig has in its hand. So this is where the two spellings of a hero
     * class meet, once, rather than the Rig growing a copy of the mapping.
     *
     * <p>The challenge flags travel with the triple and are not applied here: version 1 of every
     * Seed set carries none (ADR-0018), and the story that gives a Run challenges is the one that
     * adds a set with them.
     */
    public RunOutcome playTriple(org.shatterfish.api.SeedSet.Entry triple, long salt, Decider agent,
                                 int turnCap, Logging logging) {
        if (triple == null) {
            throw new IllegalArgumentException("a Run is a triple from a Seed set");
        }
        return play(triple.seed(), HeroClass.valueOf(triple.heroClass().name()), salt, agent, turnCap,
                logging);
    }

    private RunOutcome play(HeadlessDriver driver, long seed, HeroClass heroClass, long salt,
                            Decider agent, int turnCap, RunLogWriter log, boolean oracle) {
        // Every ending goes through here and nowhere else. Wrapping each of the loop's seven
        // returns was the first shape of this, and one of the seven was missed -- the Run that the
        // executor gives up on wrote no end record at all, which made its log byte-identical to a
        // killed Run's and its pair a tie under ADR-0012. A return added to the loop next year
        // cannot make that mistake now, because the loop does not write the record.
        try {
            // The end record is written while the Run's own state is still standing: the score is
            // the game's own scorer reading the hero and the statistics, and the driver's close
            // tears that down. So the close moved out here, after the ending, rather than staying
            // in the loop where it used to sit.
            return ending(log, driver, playing(driver, seed, heroClass, salt, agent, turnCap, log, oracle));
        } finally {
            driver.close();
        }
    }

    private RunOutcome playing(HeadlessDriver driver, long seed, HeroClass heroClass, long salt,
                               Decider agent, int turnCap, RunLogWriter log, boolean oracle) {
        long waits = 0;
        long applied = 0;
        long refused = 0;
        int refusalsInARow = 0;
        String lastRefusal = "";
        // What the Run last did, which is the first thing anyone asks when a Run stops moving.
        Action lastAction = null;
        while (true) {
            HeadlessDriver.Halt halt;
            try {
                halt = driver.stepToInputWait(FRAME_BUDGET);
            } catch (HeadlessDriver.Stalled stalled) {
                return outcome(RunOutcome.Cause.UNKNOWN_WINDOW, salt, waits, applied, refused,
                        describeWindow() + ", after " + lastAction
                                + (lastRefusal.isEmpty() ? "" : ", last refusal " + lastRefusal));
            }
            switch (halt.reason()) {
                case HERO_DEAD -> {
                    return outcome(RunOutcome.Cause.DEATH, salt, waits, applied, refused, "");
                }
                case SCENE_SWITCH -> {
                    RunOutcome served = serve(driver, halt, salt, waits, applied, refused);
                    if (served != null) {
                        return served;
                    }
                    continue;
                }
                default -> {
                    // An Input wait: the only case with an Action in it.
                }
            }
            if (turns() >= turnCap) {
                return outcome(RunOutcome.Cause.TURN_CAP, salt, waits, applied, refused, "");
            }

            Observation observation = new Observer().observe();
            // The clock is read around the decision and nowhere else, and what it measures goes
            // in the one field the chain leaves out, so a slow machine and a fast one write the
            // same chain for the same Run.
            long before = System.nanoTime();
            Action chosen;
            try {
                chosen = agent.decide(observation);
            } catch (RuntimeException error) {
                // A Brain that cannot decide says so by throwing: a Prompt it has no rule for is the
                // case story 4.11 names, and it is a result to count, not a stall and not a crash
                // that loses the Run's log.
                return outcome(RunOutcome.Cause.BRAIN_ERROR, salt, waits, applied, refused,
                        "at wait " + halt.waitIndex() + ": " + error);
            }
            long thinkMs = (System.nanoTime() - before) / 1_000_000L;
            if (chosen == null) {
                return outcome(RunOutcome.Cause.NOTHING_OFFERED, salt, waits, applied, refused,
                        "at wait " + halt.waitIndex());
            }
            waits++;
            lastAction = chosen;
            Outcome outcome = executor.execute(observation, chosen);
            // Recorded after the executor has answered, not before: a wait record used to say
            // an Action was taken at it when the executor had refused it and the game had not
            // moved, and a Replay applying that Action would have reproduced a different Run
            // with nothing in the file to explain the divergence.
            record(log, halt.waitIndex(), observation, chosen,
                    !(outcome instanceof Outcome.Rejected), thinkMs, oracle, agent);
            if (outcome instanceof Outcome.Rejected rejected) {
                refused++;
                refusalsInARow++;
                lastRefusal = rejected.reason() + ": " + rejected.detail();
                if (refusalsInARow >= REFUSALS_IN_A_ROW) {
                    return outcome(RunOutcome.Cause.REFUSED, salt, waits, applied, refused, lastRefusal);
                }
            } else {
                applied++;
                refusalsInARow = 0;
            }
        }
    }

    /**
     * Serves a scene the game asked for, or ends the Run. A Run ends here when the game asks for
     * the surface, which is the win ({@code core/.../levels/SewerLevel.java:157-165}); it goes on
     * when the game asks for the scene between two floors, which this does the work of rather than
     * running, the way {@code HeadlessDriver.newGame} does the work of the scene that starts a game.
     *
     * @return the Run's ending, or null when the Run goes on
     */
    private RunOutcome serve(HeadlessDriver driver, HeadlessDriver.Halt halt, long salt, long waits,
                             long applied, long refused) {
        Class<? extends Scene> asked = halt.requestedScene();
        if (asked == SurfaceScene.class) {
            return outcome(RunOutcome.Cause.WIN, salt, waits, applied, refused, "");
        }
        if (asked != InterlevelScene.class) {
            return outcome(RunOutcome.Cause.UNSERVED_SCENE, salt, waits, applied, refused,
                    asked == null ? "no scene named" : asked.getSimpleName());
        }
        InterlevelScene.Mode mode = InterlevelScene.mode;
        switch (mode) {
            case DESCEND, ASCEND, FALL -> driver.serveSceneSwitch(() -> crossFloor(mode));
            default -> {
                return outcome(RunOutcome.Cause.UNSERVED_SCENE, salt, waits, applied, refused,
                        "the interlevel scene in mode " + mode);
            }
        }
        return null;
    }

    /**
     * The body of the interlevel scene for one floor change, with no scene and no actor thread.
     *
     * <p>Each branch is the game's own, read at {@code v4.0.0} and reproduced rather than called,
     * because the scene's copies are private and the scene itself is a fade, a thread and a
     * progress bar — none of which a headless Run has. A descent is
     * {@code core/.../scenes/InterlevelScene.java:649-670}, an ascent {@code :693-716} and a fall
     * {@code :676-691}; the level is loaded when it has been generated before, so climbing back
     * down finds the floor the hero left rather than a new one.
     */
    private static void crossFloor(InterlevelScene.Mode mode) {
        try {
            LevelTransition transition = InterlevelScene.curTransition;
            if (mode == InterlevelScene.Mode.FALL) {
                Mob.holdAllies(Dungeon.level);
                Buff.affect(Dungeon.hero, Chasm.Falling.class);
                Dungeon.saveAll();
                Dungeon.depth++;
                Level level = generated() ? Dungeon.loadLevel(GamesInProgress.curSlot) : Dungeon.newLevel();
                Dungeon.switchLevel(level, level.fallCell(InterlevelScene.fallIntoPit));
                return;
            }
            if (transition == null) {
                throw new IllegalStateException("the game asked for " + mode + " with no transition to take");
            }
            // The guard the game carries on both sides: allies are not held into the city's quest
            // area (…/scenes/InterlevelScene.java:650-655, :694-699), which upstream writes as an
            // empty branch under a FIXME. Holding them there would be a different game, which is
            // the one thing a mirrored body must not become; the review of story 1.14 found it
            // missing here.
            boolean intoTheCityQuestArea = transition.destBranch != Dungeon.branch
                    && Dungeon.depth >= 16 && Dungeon.depth <= 20;
            if (!intoTheCityQuestArea) {
                Mob.holdAllies(Dungeon.level);
            }
            Dungeon.saveAll();
            Dungeon.depth = transition.destDepth;
            Dungeon.branch = transition.destBranch;
            Level level = generated() ? Dungeon.loadLevel(GamesInProgress.curSlot) : Dungeon.newLevel();
            LevelTransition arrival = level.getTransition(transition.destType);
            InterlevelScene.curTransition = null;
            if (arrival == null) {
                // Upstream dereferences this without asking (…/scenes/InterlevelScene.java:670,
                // :714), so a floor with no way in is a case the game does not have and this must
                // not invent one for: dropping the hero wherever the no-position path puts them
                // would turn a loud failure into a quiet wrong arrival.
                throw new IllegalStateException("floor " + Dungeon.depth + " has no "
                        + transition.destType + " for the hero to arrive at");
            }
            Dungeon.switchLevel(level, arrival.cell());
        } catch (IOException e) {
            throw new UncheckedIOException("the floor the game asked for could not be loaded", e);
        }
    }

    private static boolean generated() {
        return Dungeon.levelHasBeenGenerated(Dungeon.depth, Dungeon.branch);
    }

    /**
     * The turns this Run has passed, as the game itself counts them for the duration it shows a
     * player: the time actors have spent plus the time of the turn in progress
     * ({@code core/.../actors/Actor.java:196}; {@code core/.../ui/QuickSlotButton.java:278}).
     */
    public static int turns() {
        return (int) (Statistics.duration + Actor.now());
    }

    /**
     * Writes the record of one served wait: the wait as the decider saw it, and what was done about
     * it. A Prompt gets its own record beside the wait, because a Prompt is a thing the game did and
     * the wait says what was done about it (ADR-0011).
     *
     * <p>Nearly everything here is read from the Observation the decider was given rather than
     * from the game a second time, because a log that said what the game held while the decider
     * held something else would be a record of a Run nobody played. The turn is the exception and
     * has to be: the Observation carries no turn counter, deliberately -- a player reads the clock
     * off the screen and the bot may not -- so it is read from the game, at an instant when nothing
     * has stepped it since the Observation was made.
     */
    private static void record(RunLogWriter log, long k, Observation observation, Action chosen,
                               boolean applied, long thinkMs, boolean oracle, Decider agent) {
        if (log == null) {
            return;
        }
        // The header said once, before the Run, whether this Run may see what a player could not.
        // Every wait says it again, from the Observation the decider was actually handed, so a Run
        // whose header claims to be fair and whose Observations are an Oracle's stops here rather
        // than being published with a chain that lends the claim credibility.
        if (observation.header().oracle() != oracle) {
            throw new IllegalStateException("the header says oracle=" + oracle + " and the Observation at"
                    + " wait " + k + " says " + observation.header().oracle()
                    + "; a Run does not change which of the two it is halfway through");
        }
        // A Prompt record carries the option taken, so it is written when an option was taken. A
        // decider that answered a Prompt with something that is not an answer had its Action
        // refused by the executor, and the wait record below says so.
        if (observation.header().prompt() != PromptKind.NONE && answers(chosen)) {
            log.write(new RunLog.Prompt(k, observation.header().prompt(), chosen));
        }
        // A Deliberator says why and what it now believes (story 4.1); a plain Decider says
        // neither, and its record carries neither rather than a reason nobody gave.
        // The cells its Decision points at ride on the wait record (story 4.4, ADR-0011).
        RunLog.Decision decision = null;
        String belief = "";
        List<Integer> highlights = List.of();
        if (agent instanceof org.shatterfish.api.Deliberator deliberator) {
            decision = deliberator.lastDecision();
            belief = deliberator.beliefHash();
            highlights = deliberator.lastHighlights();
        }
        log.write(new RunLog.Wait(k, thousandths(), observation.header().depth(), observation.header().branch(),
                observation.hash(), observation.sectionHashes(), chosen, applied, RunLog.BOT, decision, belief,
                highlights, thinkMs));
    }

    /** Whether an Action answers a Prompt, which is what a Prompt record records (ADR-0011). */
    private static boolean answers(Action chosen) {
        return chosen instanceof Action.AnswerPrompt || chosen instanceof Action.DismissPrompt;
    }

    /**
     * Writes the record that says how a Run ended, and returns the outcome unchanged. A log without
     * one is a Run that was killed, which the Rig counts rather than repairs (ADR-0012).
     */
    private static RunOutcome ending(RunLogWriter log, HeadlessDriver driver, RunOutcome outcome) {
        if (log == null) {
            return outcome;
        }
        // The driver's own wait index, which every wait record is keyed by and which the spec makes
        // the driver's to assign. The loop's count of served waits is a different number -- they
        // part company the first time a wait is confirmed and not served -- and one file had been
        // using both under one key name.
        long k = driver.waitIndex();
        // The cause is the authority on whether this Run was won, not `Statistics.gameWon`. The
        // game sets that flag in `Dungeon.win` (core/.../Dungeon.java:883), which runs from the
        // surface scene's own callback -- and this loop ends the Run when the game *asks* for that
        // scene, without serving it, so the flag is never set here. It sets `Statistics.ascended`
        // earlier, at the stairs (core/.../levels/SewerLevel.java:157). So every logged Run that
        // won used to build (win=false, ascended=true) and die on the record's own refusal: the one
        // ending the rig exists to measure was the one ending that crashed.
        boolean win = outcome.cause() == RunOutcome.Cause.WIN;
        log.write(new RunLog.End(k, new RunLog.Outcome(win, win && Statistics.ascended, score(),
                outcome.depth(), thousandths(), outcome.cause().name(), bosses()),
                // A Replay reproduces a Run by applying its Actions and comparing Observations. It
                // can do that for a Run that died, won or hit the cap; it cannot for one that
                // stopped because the harness could not follow the game, which is what the other
                // four causes say. Claiming otherwise under a valid chain is the shape of lie this
                // format exists to prevent.
                outcome.ordinary()));
        return outcome;
    }

    /**
     * The Run's score, as the game's own scorer computes it
     * ({@code core/.../Rankings.java:187-257}) -- never a second implementation of the game's rule
     * (non-negotiable 4). It needs a hero to read a level from, so a Run that ended without one
     * scores nothing rather than guessing.
     *
     * <p>It is the game's own whole number of points, not ten-thousandths of anything. ADR-0011's
     * "scores are integers in ten-thousandths" is about a <em>Decision's</em> score -- a Brain's own
     * evaluation of an Action, which needs a fraction -- and this field had inherited that sentence
     * in its documentation while carrying the game's points, so a reader following the published
     * unit would have divided a published score by ten thousand.
     */
    private static int score() {
        if (Dungeon.hero == null) {
            return 0;
        }
        try {
            return Rankings.INSTANCE.calculateScore();
        } catch (RuntimeException torn) {
            // The scorer walks the hero's belongings (core/.../Rankings.java:203-206), which a Run
            // that ended badly may have torn down. A Run that ended is a result to count and not a
            // crash to debug (`RunOutcome`), so the ending is still recorded and the score it could
            // not compute is 0.
            return 0;
        }
    }

    /**
     * The bosses this Run killed, counted the way the game's own score does: an entry of
     * {@code Statistics.bossScores} above zero is a boss whose fight scored
     * ({@code core/.../Rankings.java:221-224}; {@code core/.../Statistics.java:51}).
     */
    private static int bosses() {
        int killed = 0;
        for (int score : Statistics.bossScores) {
            if (score > 0) {
                killed++;
            }
        }
        return killed;
    }

    /**
     * The turns passed, in thousandths. {@link #turns()} rounds two of the game's floats down to
     * an int for a person to read; a chained field holds no float, so the log carries the same
     * quantity as a whole number instead.
     *
     * <p>A thousandth is the unit, not the precision. The game counts turns in single precision
     * ({@code Statistics.duration} and {@code Actor.now()} are both {@code float}), so past about
     * eight thousand turns its own resolution is coarser than a thousandth and the trailing digits
     * are whatever the float could hold. Two Runs of one tuple quantise identically, which is what
     * a Replay compares; nobody should read the third digit as a measurement.
     */
    private static long thousandths() {
        return Math.round((double) (Statistics.duration + Actor.now()) * 1000.0);
    }

    private static String describeWindow() {
        return Windows.front() == null
                ? "no window in front, and no wait either"
                : Windows.front().getClass().getSimpleName();
    }

    private static RunOutcome outcome(RunOutcome.Cause cause, long salt, long waits, long applied,
                                      long refused, String detail) {
        return new RunOutcome(cause, salt, Statistics.deepestFloor, turns(), waits, applied, refused, detail);
    }
}
