package org.shatterfish.rig;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The cloud columns of a direction check (issue #408): reach of depths 4 and 6, the waits the hero stood in
 * fire or gas, and how the Runs that died last stood. The first two are read from the Run logs; the rest
 * from a {@code .harm.tsv} that {@link DirectionPlay} writes beside each log from the Observations the Brain
 * is handed, so a baseline that does not raise the {@code in-harm} flag is measured by the same rule.
 *
 * <p>The ending is a guess from the last screen the Brain saw, not the game's own account of the death
 * (the Run log records neither): "gas" when the hero stood in fire or gas, else the enemy within one cell of
 * it, else "hunger" when starving, else "unseen".
 */
final class DirectionHarm {

    /** The suffix of the harm record written beside a Run's log. */
    static final String HARM = ".harm.tsv";

    /** The blobs that hurt where the hero stands: the Brain's own list of the fire and the harmful gases. */
    private static final Set<String> HARMFUL = Set.of("Fire", "ToxicGas", "CorrosiveGas", "ParalyticGas", "EternalFire");

    private DirectionHarm() {
    }

    /** Whether the hero's own cell shows a harmful blob on {@code observation}. */
    static boolean inHarm(Observation observation) {
        for (BlobCell blob : observation.map().blobs()) {
            if (blob.cell() == observation.hero().cell() && blob.kinds().stream().anyMatch(HARMFUL::contains)) {
                return true;
            }
        }
        return false;
    }

    /** The guess at what ended a Run, from the last screen its Brain saw. */
    static String ending(Observation last) {
        if (inHarm(last)) {
            return "gas";
        }
        int width = last.map().width();
        int hero = last.hero().cell();
        String nearest = "";
        int best = Integer.MAX_VALUE;
        for (ActorView actor : last.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                int distance = Math.max(Math.abs(actor.cell() % width - hero % width),
                        Math.abs(actor.cell() / width - hero / width));
                if (distance < best) {
                    best = distance;
                    nearest = actor.name();
                }
            }
        }
        if (best <= 1) {
            return nearest;
        }
        return last.hero().hunger() == Hunger.STARVING ? "hunger" : "unseen";
    }

    /** One side's cloud numbers. */
    record Side(int runs, int reached4, int reached6, int harmWaits, int runsInHarm, int diedInHarm,
                TreeMap<String, Integer> endings) {
    }

    static Side read(Path folder) {
        int runs = 0;
        int r4 = 0;
        int r6 = 0;
        int waits = 0;
        int inHarm = 0;
        int died = 0;
        TreeMap<String, Integer> endings = new TreeMap<>();
        for (Path log : list(folder, ".jsonl")) {
            RunLogReader.Log read = RunLogReader.of(log);
            runs++;
            int max = 0;
            for (RunLog.Wait wait : read.waits()) {
                max = Math.max(max, wait.depth());
            }
            if (max >= 4) {
                r4++;
            }
            if (max >= 6) {
                r6++;
            }
            RunLog.End end = read.end();
            boolean dead = end != null && "DEATH".equals(end.outcome().cause());
            Path record = find(folder, log);
            if (record != null) {
                String[] cells = lines(record).get(0).split("\t");
                int n = Integer.parseInt(cells[0]);
                waits += n;
                if (n > 0) {
                    inHarm++;
                }
                if (dead) {
                    if (cells[1].equals("1")) {
                        died++;
                    }
                    endings.merge(cells[2], 1, Integer::sum);
                }
            }
        }
        return new Side(runs, r4, r6, waits, inHarm, died, endings);
    }

    /** The harm record of the Run whose log is {@code log}: the log's class, seed code and salt name it. */
    private static Path find(Path folder, Path log) {
        // <tag>-<class>-<challenges>-<seed code of three words>-<salt>-<brain>.jsonl
        String[] parts = log.getFileName().toString().split("-");
        if (parts.length < 8) {
            return null;
        }
        Path path = folder.resolve(parts[1] + "-" + parts[3] + "-" + parts[4] + "-" + parts[5] + "-" + parts[6] + HARM);
        return Files.exists(path) ? path : null;
    }

    /** The markdown rows for the cloud columns, to follow the direction table. */
    static String rows(Side baseline, Side current) {
        StringBuilder out = new StringBuilder();
        out.append(row("reach depth 4", baseline == null ? null : baseline.reached4() + "/" + baseline.runs(),
                current.reached4() + "/" + current.runs(), baseline == null ? null : current.reached4() - baseline.reached4()));
        out.append(row("reach depth 6", baseline == null ? null : baseline.reached6() + "/" + baseline.runs(),
                current.reached6() + "/" + current.runs(), baseline == null ? null : current.reached6() - baseline.reached6()));
        out.append(row("waits in fire or gas", baseline == null ? null : String.valueOf(baseline.harmWaits()),
                String.valueOf(current.harmWaits()), baseline == null ? null : current.harmWaits() - baseline.harmWaits()));
        out.append(row("Runs that stood in fire or gas", baseline == null ? null : String.valueOf(baseline.runsInHarm()),
                String.valueOf(current.runsInHarm()), baseline == null ? null : current.runsInHarm() - baseline.runsInHarm()));
        out.append(row("deaths with the last screen in fire or gas", baseline == null ? null : String.valueOf(baseline.diedInHarm()),
                String.valueOf(current.diedInHarm()), baseline == null ? null : current.diedInHarm() - baseline.diedInHarm()));
        out.append(row("deaths by last screen's enemy", baseline == null ? null : baseline.endings().toString(),
                current.endings().toString(), null));
        return out.toString();
    }

    private static String row(String label, String baseline, String current, Integer delta) {
        return "| " + label + " | " + (baseline == null ? "" : baseline) + " | " + current + " | "
                + (delta == null ? "" : (delta > 0 ? "+" : "") + delta) + " |\n";
    }

    private static List<Path> list(Path folder, String suffix) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of("0\t0\tunseen");
        }
    }
}
