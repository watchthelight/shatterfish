package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Fog;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Simulator;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.api.Tile;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubRow;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Function;

/**
 * The probe search (story 6.3): a test fixture that exists so the rollout path is exercised before any real candidate
 * does. It is a search in the full sense and a bad player on purpose: for each of a few candidate Actions the screen
 * offers it scrubs the world it stands in with several samples, rolls the candidate and two rests out of every scrubbed
 * copy through a {@link Simulator}, scores the screen each rollout ends on, and ranks the candidates by the scores' sum.
 *
 * <p>Everything it decides by is the Observation it is given, its own seed and the wait index (the samples are drawn
 * from those three and from the Observation's map), and what comes back from the {@link Simulator}. That is what a
 * fair search may decide by, and it is what {@code SearchLeakTest} holds it to. The planted leaks of the test's
 * negative controls are the {@link Config} fields that bend it: a different host, a scrubber missing a row, a rollout
 * from the live handle, a peek at the game.
 *
 * <p>A scrub that refuses a snapshot is no sample: the copy is skipped, and the number skipped is in the Decision's
 * reasons, so that a refusal that tracks the hidden state shows up as a different Decision (ADR-0009's amendment: a
 * refusal is never evidence, and this search does not take one as evidence, but it does not hide one either).
 */
final class ProbeSearch {

    /** How many candidates, samples and rolled-out turns the probe spends. */
    static final int CANDIDATES = 3;
    static final int SAMPLES = 2;
    static final int HORIZON = 3;

    /** The pieces a search stands in: the world it is in and the machinery the harness gives it. */
    record Context(HeadlessDriver driver, SnapshotStore store, Observation observation, long waitIndex, long searchSeed) {
    }

    /**
     * What a planted leak bends. The honest probe is {@link #honest()}; each field is one way the test's controls
     * make it dishonest, and each is a thing a real search must never do.
     */
    record Config(Function<Context, Simulator> host, Set<ScrubRow> rows, boolean forgeLiveHandle,
                  java.util.function.ToLongFunction<Context> peek, java.util.function.Predicate<Context> refuseWhen) {

        static Config honest() {
            return new Config(c -> new RolloutHost(c.store(), c.driver()), EnumSet.allOf(ScrubRow.class), false, c -> 0L,
                    c -> false);
        }

        Config withHost(Function<Context, Simulator> value) {
            return new Config(value, rows, forgeLiveHandle, peek, refuseWhen);
        }

        Config withRows(Set<ScrubRow> value) {
            return new Config(host, EnumSet.copyOf(value), forgeLiveHandle, peek, refuseWhen);
        }

        Config forgingTheLiveHandle() {
            return new Config(host, rows, true, peek, refuseWhen);
        }

        Config peeking(java.util.function.ToLongFunction<Context> value) {
            return new Config(host, rows, forgeLiveHandle, value, refuseWhen);
        }

        /** A planted refusal that tracks the hidden state: the scrub is declined whenever {@code value} holds. */
        Config refusingWhen(java.util.function.Predicate<Context> value) {
            return new Config(host, rows, forgeLiveHandle, peek, value);
        }
    }

    /** What one run of the probe found, apart from the Decision: how many scrubs refused, for the refusal case. */
    record Found(RunLog.Decision decision, int refused, int rolled) {
    }

    private ProbeSearch() {
    }

    /** The probe's Decision for the world {@code c} stands in. */
    static Found decide(Context c, Config config) {
        List<Action> candidates = candidates(c.observation());
        SnapshotHandle live = c.store().take(c.driver());
        List<SnapshotHandle> copies = new ArrayList<>();
        int refused = 0;
        try {
            SnapshotScrubber scrubber = new SnapshotScrubber(c.store(), config.rows());
            for (int j = 0; j < SAMPLES; j++) {
                try {
                    if (config.refuseWhen().test(c)) {
                        throw new Scrub.Refused("planted: a refusal that tracks the hidden state");
                    }
                    copies.add(scrubber.scrub(live, sample(c.searchSeed(), c.waitIndex(), j, c.observation())));
                } catch (Scrub.Refused refusal) {
                    // No sample: not a signal, and not replaced by another draw (the next draw would be a function of the refusal).
                    refused++;
                }
            }
            Simulator host = config.host().apply(c);
            SnapshotHandle forged = new SnapshotHandle(live.id(), live.k(), true);
            List<RunLog.Choice> scored = new ArrayList<>();
            int rolled = 0;
            for (Action candidate : candidates) {
                long total = 0;
                int deaths = 0;
                int refusedActions = 0;
                List<Action> actions = new ArrayList<>();
                actions.add(candidate);
                for (int turn = 1; turn < HORIZON; turn++) {
                    actions.add(new Action.Rest(false));
                }
                List<SnapshotHandle> starts = config.forgeLiveHandle() ? List.of(forged) : copies;
                for (SnapshotHandle start : starts) {
                    RolloutResult result = host.simulate(start, actions);
                    rolled++;
                    total += value(result);
                    deaths += result.end() == RolloutEnd.DEATH ? 1 : 0;
                    refusedActions += result.end() == RolloutEnd.REFUSED ? 1 : 0;
                }
                total += config.peek().applyAsLong(c);
                scored.add(new RunLog.Choice(candidate, total, "probe: " + starts.size() + " rollouts of " + HORIZON
                        + " turns, " + deaths + " deaths, " + refusedActions + " refused Actions, " + refused + " refused scrubs"));
            }
            if (scored.isEmpty()) {
                throw new IllegalStateException("the screen offers the probe nothing to weigh");
            }
            // Stable by candidate order: a tie is the Observation's, never the hidden state's.
            List<RunLog.Choice> ranked = new ArrayList<>(scored);
            ranked.sort(Comparator.comparingLong(RunLog.Choice::score).reversed());
            RunLog.Choice chosen = ranked.get(0);
            List<RunLog.Choice> alternatives = ranked.subList(1, Math.min(ranked.size(), 1 + RunLog.Decision.ALTERNATIVES));
            return new Found(new RunLog.Decision("probe the rollout path", chosen, alternatives, List.of(), "probe"),
                    refused, rolled);
        } finally {
            for (SnapshotHandle copy : copies) {
                c.store().drop(copy);
            }
            c.store().drop(live);
        }
    }

    /** The candidates, from the Observation alone: attacks, then a rest, then a search, then steps, in cell order. */
    static List<Action> candidates(Observation observation) {
        List<Action> offered = new ArrayList<>(observation.actions().actions());
        offered.sort(Comparator.comparingInt(ProbeSearch::rank).thenComparing(Action::toString));
        List<Action> picked = new ArrayList<>();
        for (Action action : offered) {
            if (rank(action) < 4 && picked.size() < CANDIDATES) {
                picked.add(action);
            }
        }
        return picked;
    }

    private static int rank(Action action) {
        return switch (action) {
            case Action.Attack attack -> 0;
            case Action.Rest rest -> 1;
            case Action.Search search -> 2;
            case Action.Step step -> 3;
            default -> 9;
        };
    }

    /** What a rollout is worth: the hero's hit points kept, the enemies' bars taken down, a death a disaster. */
    static long value(RolloutResult result) {
        if (result.end() == RolloutEnd.DEATH) {
            return -10_000;
        }
        if (result.observations().isEmpty()) {
            return 0;
        }
        Observation last = result.observations().get(result.observations().size() - 1);
        long value = last.hero().hp() * 100L;
        for (var actor : last.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                value -= actor.healthPips() * 10L;
            }
        }
        return value;
    }

    /**
     * Sample {@code j} of the search at {@code wait}: drawn from the search's seed, the wait and the index, and placing
     * remembered mobs on cells the map shows as visited and not in view. Nothing in the Run's hidden state is read.
     */
    static BeliefSample sample(long searchSeed, long waitIndex, int j, Observation observation) {
        SplittableRandom random = new SplittableRandom(searchSeed * 0x9E37_79B9_7F4A_7C15L + waitIndex * 0x632B_E59B_D9B4_E019L + j);
        return sampleFrom(random, observation);
    }

    /** A sample whose draws come from {@code random}, with up to two remembered mobs placed from the Observation's map. */
    static BeliefSample sampleFrom(SplittableRandom random, Observation observation) {
        ScrubSample sample = ScrubSample.of(random.nextLong(), random.nextLong(), random.nextLong());
        List<Integer> remembered = new ArrayList<>();
        var map = observation.map();
        for (int cell = 0; cell < map.fog().size(); cell++) {
            if (map.fog().get(cell) == Fog.VISITED && map.tiles().get(cell) == Tile.EMPTY) {
                remembered.add(cell);
            }
        }
        List<ScrubSample.Remembered> mobs = new ArrayList<>();
        for (int i = 0; i < 2 && !remembered.isEmpty(); i++) {
            mobs.add(new ScrubSample.Remembered("com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat",
                    remembered.get(random.nextInt(remembered.size()))));
        }
        return sample.withRemembered(mobs).toBelief();
    }
}
