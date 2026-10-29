package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.HostBridge;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Autoexplore over real Runs (story 8.10, FR-47, UJ-4): a scripted learner plays Warriors of the {@code standard}
 * set on {@code EmbeddedHost} at the desktop's sixtieth of a second a frame, with the Overlay's own
 * {@link ControlState} at the controls. The learner autoexplores whenever it can; when control comes back with an
 * enemy in view it hands the fight to the bot (Hand back, Resume) and takes the game again when the screen is
 * quiet. Nothing here moves the hero by hand, so every wait of the log is the bot's, and what the test shows is
 * where the stretches stop and what they cover.
 *
 * <p>It asserts the rules held over real play, not the rate: every credit a stretch spent was a Decision of a
 * Policy on the list, no stretch stopped for a reason it had not named, the logs verify, and the Mode records
 * are the existing kinds. It prints the two numbers the story reports: the share of turns the stretches
 * covered, the number of times control came back with an enemy adjacent, and how many of those had the enemy in
 * view a wait earlier (the failure: the stretch went on past it). An enemy first seen when adjacent, round a corner
 * or out of the dark, is not a stop that came late: no rule over what the screen shows could have stopped earlier.
 *
 * <p>How many seeds: {@code AUTOEXPLORE_RUNS} (default 2) first Warriors of the set, each to
 * {@code AUTOEXPLORE_TURNS} (default 1500) turns; the numbers in the story file are from 20 Runs of 3000.
 * Never {@code goo} or {@code holdout}.
 */
@Timeout(value = 40, unit = TimeUnit.MINUTES)
class AutoexploreRunTest {

    private static final long FRAMES = 400_000L;
    private static final Pattern ENTRY = Pattern.compile("\"heroClass\":\"WARRIOR\",\"seed\":(\\d+)");

    /** What one Run showed. */
    record Played(Path log, long turns, long stretchTurns, int stretches, int adjacentReturns, int lateReturns, int returns,
                  TreeMap<String, Integer> reasons, TreeSet<String> policiesSpent) {
    }

    private static int env(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    /** The repository root: the nearest folder above the test's working directory that holds {@code vanilla.pin}. */
    private static Path root() {
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            if (Files.exists(at.resolve("vanilla.pin"))) {
                return at;
            }
        }
        throw new IllegalStateException("no repository root above " + Path.of("").toAbsolutePath());
    }

    /** The {@code standard} set's Warriors, in order: salt 1000 + the Warrior's place, as the Rig's {@code --salt-base 1000}. */
    private static List<long[]> warriors() throws IOException {
        Matcher entry = ENTRY.matcher(Files.readString(root().resolve("seeds").resolve("standard.json")));
        List<long[]> seeds = new ArrayList<>();
        while (entry.find()) {
            seeds.add(new long[]{Long.parseLong(entry.group(1)), 1000 + seeds.size()});
        }
        return seeds;
    }

    private static boolean hostile(Observation observation) {
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                return true;
            }
        }
        return false;
    }

    private static boolean adjacent(Observation observation) {
        int width = observation.map().width();
        int hero = observation.hero().cell();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() != Alignment.ENEMY) {
                continue;
            }
            int dx = Math.abs(actor.cell() % width - hero % width);
            int dy = Math.abs(actor.cell() / width - hero / width);
            if (Math.max(dx, dy) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static Played play(Path folder, long seed, long salt, int turnCap) throws IOException {
        Path logs = Files.createDirectories(folder);
        TreeMap<String, Integer> reasons = new TreeMap<>();
        TreeSet<String> spent = new TreeSet<>();
        long stretchTurns = 0;
        int stretches = 0;
        int adjacentReturns = 0;
        int lateReturns = 0;
        int returns = 0;
        try (HostBridge host = new HostBridge(seed, HeroClass.WARRIOR, salt)) {
            EmbeddedRun run = host.attachBrain(logs, turnCap);
            ControlState controls = new ControlState(new OverlayGame.RunSeam(run), Capabilities.NOW,
                    OverlaySettings.DEFAULTS, BossFloors.read(root().resolve("codex")
                            .resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag())));
            // Fast as it can, then the person's turn first: the stretch is the thing under test, not the pace.
            assertTrue(controls.cycleSpeed(), "Human play speed");
            assertTrue(controls.cycleSpeed(), "Fast as it can");
            assertTrue(controls.takeOverHandBack(), "the learner takes the game");
            Learner learner = new Learner();
            Observation lastObservation = null;
            boolean hostileBefore = false;
            boolean was = false;
            int lastTurn = 0;
            long frames = 0;
            while (true) {
                boolean waiting = host.waitingOnTheBrain();
                if (host.frame() == EmbeddedRun.State.ENDED) {
                    break;
                }
                if (waiting) {
                    Thread.onSpinWait();
                    continue;
                }
                if (++frames > FRAMES) {
                    EmbeddedRun.Snapshot at = run.snapshot();
                    throw new AssertionError("the Run did not end: mode " + controls.mode() + ", autoexploring "
                            + controls.autoexploring() + ", switching " + controls.switching() + ", turn " + at.turn()
                            + ", state " + at.state() + ", human " + at.human() + ", stopped " + controls.stoppedFor()
                            + ", stretches " + stretches + ", returns " + returns + " " + reasons);
                }
                controls.frame(1.0 / 60.0);
                EmbeddedRun.Snapshot shown = run.snapshot();
                boolean now = controls.autoexploring();
                if (was || now) {
                    stretchTurns += Math.max(0, shown.turn() - lastTurn);
                }
                lastTurn = shown.turn();
                if (now && !was) {
                    stretches++;
                }
                if (now && shown.observation() != null && !shown.observation().equals(lastObservation)) {
                    hostileBefore = lastObservation != null && hostile(lastObservation);
                    lastObservation = shown.observation();
                }
                if (now && run.credits() > 0 && shown.decision() != null) {
                    spent.add(shown.decision().policy());
                }
                if (was && !now) {
                    returns++;
                    learner.handToBot = true;
                    reasons.merge(reasonKind(controls.stoppedFor()), 1, Integer::sum);
                    if (shown.observation() != null && adjacent(shown.observation())) {
                        adjacentReturns++;
                        if (hostileBefore) {
                            lateReturns++;
                        }
                    }
                }
                was = now;
                learner(learner, controls, shown);
            }
        }
        Path log;
        try (Stream<Path> files = Files.list(logs)) {
            log = files.filter(file -> file.toString().endsWith(".jsonl")).findFirst().orElseThrow();
        }
        RunLogVerifier.Verified verified = RunLogVerifier.of(log);
        assertTrue(verified.ok() && verified.complete(), verified.why());
        long turns = RunLogReader.of(log).waits().stream().mapToLong(RunLog.Wait::turn).max().orElse(0) / 1000;
        return new Played(log, turns, stretchTurns, stretches, adjacentReturns, lateReturns, returns, reasons, spent);
    }

    /** The first words of a reason, so a table of them stays short ("descend is next, ..." is "descend is next"). */
    private static String reasonKind(String why) {
        if (why == null) {
            return "(none)";
        }
        int comma = why.indexOf(',');
        return comma < 0 ? why : why.substring(0, comma);
    }

    /** What the learner remembers between frames. */
    private static final class Learner {
        /** A stretch just gave the game back, so the next thing is the bot's, not another stretch at the same wait. */
        boolean handToBot;
        /** The turn the bot was resumed at, so it plays at least one turn before the learner takes the game again. */
        int resumedAt = -1;
    }

    /**
     * The scripted learner: autoexplore whenever it can. Where a stretch stopped at an enemy or at a Decision it
     * would not take (a descend, a potion), or an enemy is in view, the learner hands the wait to the bot (Hand
     * back, Resume) and takes the game again when the screen is quiet and the bot has played a turn. It moves
     * nothing by hand, since a person's hands are not under test.
     */
    private static void learner(Learner learner, ControlState controls, EmbeddedRun.Snapshot shown) {
        if (controls.switching() || shown.observation() == null) {
            return;
        }
        switch (controls.mode()) {
            case HUMAN -> {
                if (shown.human() == null || !shown.human().inputOpen()) {
                    return;
                }
                if (learner.handToBot || hostile(shown.observation())) {
                    learner.handToBot = false;
                    controls.takeOverHandBack();
                } else {
                    controls.autoexplore();
                }
            }
            case PAUSED -> {
                learner.resumedAt = shown.turn();
                controls.pauseResume();
            }
            case RUNNING -> {
                if (!controls.autoexploring() && !hostile(shown.observation()) && shown.turn() > learner.resumedAt) {
                    controls.takeOverHandBack();
                }
            }
        }
    }

    @Test
    @DisplayName("scripted learner on standard Warriors: stretches stay on the list, stop for named reasons, and the logs verify")
    void stretches_over_real_runs(@TempDir Path folder) throws IOException {
        int runs = env("AUTOEXPLORE_RUNS", 2);
        int cap = env("AUTOEXPLORE_TURNS", 1500);
        List<long[]> seeds = warriors();
        assertTrue(seeds.size() >= runs, "the set has " + seeds.size() + " Warriors");
        long turns = 0;
        long covered = 0;
        int stretches = 0;
        int returns = 0;
        int adjacentReturns = 0;
        int lateReturns = 0;
        TreeMap<String, Integer> reasons = new TreeMap<>();
        for (int i = 0; i < runs; i++) {
            Played played = play(folder.resolve("run-" + i), seeds.get(i)[0], seeds.get(i)[1], cap);
            turns += played.turns();
            covered += played.stretchTurns();
            stretches += played.stretches();
            returns += played.returns();
            adjacentReturns += played.adjacentReturns();
            lateReturns += played.lateReturns();
            played.reasons().forEach((reason, count) -> reasons.merge(reason, count, Integer::sum));

            assertTrue(Autoexplore.POLICIES.containsAll(played.policiesSpent()),
                    "every credit a stretch spent was a Decision of a Policy on the list: " + played.policiesSpent());
            List<RunLog.Mode> modes = RunLogReader.of(played.log()).records().stream()
                    .filter(RunLog.Mode.class::isInstance).map(RunLog.Mode.class::cast).toList();
            assertTrue(modes.stream().allMatch(mode -> mode.mode().equals("HUMAN") || mode.mode().equals("PAUSED")),
                    "the Mode records are the existing kinds: " + modes);
            assertTrue(RunLogReader.of(played.log()).waits().stream().allMatch(wait -> wait.actor().equals(RunLog.BOT)),
                    "the learner moved nothing by hand: every wait is the bot's");
        }
        assertTrue(stretches > 0, "the learner started at least one stretch");
        assertTrue(covered > 0, "a stretch covered turns");
        assertFalse(reasons.isEmpty());
        assertEquals(returns, reasons.values().stream().mapToInt(Integer::intValue).sum());
        assertFalse(reasons.containsKey("(none)"), "every return named its reason: " + reasons);
        assertEquals(0, lateReturns, "a stretch that went on past an enemy already in view and came back with it adjacent");
        System.out.println("autoexplore: " + runs + " Runs, " + turns + " turns, " + stretches + " stretches covering "
                + covered + " turns (" + String.format(java.util.Locale.ROOT, "%.1f", 100.0 * covered / Math.max(1, turns))
                + "%), " + returns + " returns, " + adjacentReturns + " with an enemy adjacent (" + lateReturns
                + " of them with the enemy in view a wait earlier); reasons " + reasons);
    }
}
