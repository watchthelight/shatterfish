package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.log.DebugRecord;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.practice.KitProfiles;
import org.shatterfish.harness.practice.Practice;
import org.shatterfish.harness.practice.PracticeBoss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The boss practice harness (E10, ADR-0024):
 *
 * <pre>
 * ./gradlew :rig:practice --args="--boss tengu --class WARRIOR --kit standard --n 40 --salt-base 1000 --jobs 4"
 * </pre>
 *
 * <p>Every Run starts on the boss's floor (depth 5 for Goo, depth 10 for Tengu) with a hero whose
 * level, strength, equipment upgrades and pack follow a kit profile, built through the harness with
 * the game's own classes and no upstream edit ({@code PracticeStart}). The Brain plays from there
 * exactly as it plays anywhere: it is handed its Observation and nothing of the setup. A Run ends
 * when the boss is killed ({@code BOSS_DOWN}), the hero dies, or the turn cap is reached.
 *
 * <p><b>It measures a fight and nothing else.</b> The triples are the first {@code n} of the
 * {@code standard} set for the class in file order, each under salt {@code saltBase + index}, so the
 * boss floor is the one a real Run of that seed reaches, and a rerun of the same flags is the same
 * measurement. A practice Run is never ranked (ADR-0024): its header carries a {@code practice}
 * stamp, and the places that count, compare or publish refuse a log that has one
 * ({@link OverlayLogs}). This command accepts no Registration and reads no set but {@code standard}
 * and {@code smoke}; {@code goo}, {@code holdout} and any set added for registered use are not
 * development sets and are refused by name.
 */
public final class PracticeRuns {

    public static final String BOSS = "--boss";
    public static final String CLASS = "--class";
    public static final String KIT = "--kit";
    public static final String N = "--n";
    public static final String SALT_BASE = "--salt-base";
    public static final String JOBS = "--jobs";
    public static final String OUT = "--out";
    public static final String ROOT = "--root";
    public static final String SET = "--set";
    public static final String CAP = "--cap";
    public static final String BRAIN_FLAG = "--brain";

    static final List<String> KNOWN = List.of(BOSS, CLASS, KIT, N, SALT_BASE, JOBS, OUT, ROOT, SET, CAP, BRAIN_FLAG);

    /** The sets a practice Run may take its triples from: the development sets, by name. */
    static final List<String> SETS = List.of(SeedSets.STANDARD, SeedSets.SMOKE);

    /** The turn cap of a practice Run: a fight is a few hundred turns, and the Run starts at the boss. */
    static final int DEFAULT_CAP = 5_000;

    private static final int DEADLINE_SECONDS = 1800;

    private PracticeRuns() {
    }

    public static void main(String[] args) {
        try {
            System.out.println(run(arguments(args)));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            System.err.println("practice: " + refused.getMessage());
            System.exit(2);
        }
    }

    static Map<String, String> arguments(String[] args) {
        Map<String, String> given = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String flag = args[i];
            if (!KNOWN.contains(flag)) {
                throw new IllegalArgumentException("unknown flag " + flag + "; the flags are " + KNOWN
                        + ". A practice Run takes no Registration and no baseline: it is never ranked (ADR-0024)");
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(flag + " takes a value");
            }
            if (given.put(flag, args[i + 1]) != null) {
                throw new IllegalArgumentException(flag + " is given twice");
            }
        }
        return given;
    }

    /** Refuses a set a practice Run may not take triples from, saying why. */
    static void allow(String set) {
        if (!SETS.contains(set)) {
            throw new IllegalArgumentException("a practice Run takes its triples from a development set, " + SETS
                    + ", and not from " + set + ": goo, holdout and the sets kept for registered use are played only"
                    + " under a Registration, and a practice Run is never played under one (ADR-0024)");
        }
    }

    /** One class's practice Runs, read back. */
    record Side(HeroClass heroClass, int runs, int killed, int died, int capped, int other, int unfinished,
                List<Double> killTurns, List<Double> deathTurns, Map<String, Integer> killers, List<Integer> lastHp) {

        int finished() {
            return killed + died + capped + other;
        }
    }

    static String run(Map<String, String> a) {
        Path root = Path.of(a.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        if (!a.containsKey(BOSS)) {
            throw new IllegalArgumentException(BOSS + " is goo or tengu");
        }
        PracticeBoss boss = PracticeBoss.of(a.get(BOSS));
        String kit = a.getOrDefault(KIT, KitProfiles.STANDARD);
        Practice practice = new Practice(boss, kit);
        String set = a.getOrDefault(SET, SeedSets.STANDARD);
        allow(set);
        String classes = a.getOrDefault(CLASS, "ALL");
        List<HeroClass> heroClasses = new ArrayList<>();
        if (classes.equalsIgnoreCase("ALL")) {
            heroClasses.addAll(List.of(HeroClass.values()));
        } else {
            heroClasses.add(DirectionSelect.heroClass(classes));
        }
        int n = integer(a, N, 1, 40);
        long saltBase = a.containsKey(SALT_BASE) ? Long.parseLong(a.get(SALT_BASE)) : 0L;
        int jobs = a.containsKey(JOBS) ? integer(a, JOBS, 1, 0) : Direction.DEFAULT_JOBS;
        int cap = a.containsKey(CAP) ? integer(a, CAP, 100, 0) : DEFAULT_CAP;
        String brain = a.containsKey(BRAIN_FLAG) ? Brains.named(a.get(BRAIN_FLAG)) : Brains.SHATTERFISH;
        if (!Brains.readsWeights(brain)) {
            throw new IllegalArgumentException(BRAIN_FLAG + " names a Brain built on the Codex and weights; " + brain + " is not");
        }
        Path out = a.containsKey(OUT) ? Path.of(a.get(OUT)).toAbsolutePath().normalize()
                : root.resolve("build").resolve("practice")
                        .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        if (Files.exists(out) && !isEmpty(out)) {
            throw new IllegalArgumentException(out + " is not empty; a practice check writes a folder of its own");
        }

        String commit = Runner.commitOf(root);
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        StringBuilder text = new StringBuilder();
        text.append("practice: ").append(boss.id()).append(" (depth ").append(boss.depth()).append("), kit ")
                .append(kit).append(", ").append(set).append(" first ").append(n).append(" per class, salts ")
                .append(saltBase).append("..").append(saltBase + n - 1).append(", cap ").append(cap)
                .append(" turns, commit ").append(commit, 0, Math.min(9, commit.length())).append("\n\n");
        System.out.println(text.toString().strip());
        List<Side> sides = new ArrayList<>();
        int failed = 0;
        for (HeroClass heroClass : heroClasses) {
            List<DirectionSelect.Pick> picks = DirectionSelect.select(root, set, heroClass, n, saltBase);
            if (picks.isEmpty()) {
                throw new IllegalArgumentException("no triple of " + set + " is left for " + heroClass);
            }
            Path folder = out.resolve(heroClass.name().toLowerCase(Locale.ROOT));
            System.out.println("practice: " + heroClass + ", " + picks.size() + " Runs, " + jobs + " at a time, into " + folder);
            failed += play(picks, folder, root, tag, commit, jobs, brain, practice, cap);
            sides.add(read(heroClass, folder, practice));
        }
        text.append(table(boss, sides));
        text.append("\nKit per class (profile ").append(kit).append("):\n\n");
        for (HeroClass heroClass : heroClasses) {
            text.append("- ").append(heroClass).append(": `").append(practice.kit(heroClass).canonical()).append("`\n");
        }
        if (failed > 0) {
            text.append("\nRuns that did not finish: ").append(failed).append("; see the .err files beside the logs.\n");
        }
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("practice.md"), text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the table into " + out, e);
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ playing

    private static int play(List<DirectionSelect.Pick> picks, Path folder, Path source, String tag, String commit,
                            int jobs, String brain, Practice practice, int cap) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("could not create " + folder, e);
        }
        Path codex = source.resolve(CodexManifest.FOLDER).resolve(tag);
        Path weights = source.resolve(WeightsFile.FOLDER).resolve(brain + ".json");
        Path playbook = Files.exists(PlaybookFile.of(source, brain)) ? PlaybookFile.of(source, brain) : null;
        String classpath = Runner.classpath();
        ExecutorService pool = Executors.newFixedThreadPool(jobs);
        try {
            List<Future<Boolean>> started = new ArrayList<>();
            for (int i = 0; i < picks.size(); i++) {
                DirectionSelect.Pick pick = picks.get(i);
                Path working = folder.resolve("work").resolve(String.valueOf(i));
                started.add(pool.submit(() -> one(pick, folder, working, classpath, codex, weights, playbook, commit,
                        brain, practice, cap)));
            }
            int failed = 0;
            for (Future<Boolean> f : started) {
                try {
                    if (!f.get()) {
                        failed++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException("a Run could not be started: " + e.getCause(), e.getCause());
                }
            }
            return failed;
        } finally {
            pool.shutdownNow();
            org.shatterfish.harness.boot.TempDirs.deleteTree(folder.resolve("work"));
        }
    }

    private static boolean one(DirectionSelect.Pick pick, Path folder, Path working, String classpath, Path codex,
                               Path weights, Path playbook, String commit, String brain, Practice practice, int cap)
            throws IOException, InterruptedException {
        Files.createDirectories(working);
        var triple = pick.entry();
        List<String> command = new ArrayList<>(List.of(
                Runner.javaExecutable().toString(),
                "-cp", classpath, RunOne.class.getName(),
                RunOne.SEED, Long.toString(triple.seed()),
                RunOne.CLASS, triple.heroClass().name(),
                RunOne.SALT, Long.toString(pick.salt()),
                RunOne.OUT, folder.toAbsolutePath().toString(),
                RunOne.COMMIT, commit,
                RunOne.BRAIN, brain,
                RunOne.BRAIN_COMMIT, commit,
                RunOne.CAP, Integer.toString(cap),
                RunOne.CHALLENGES, Integer.toString(triple.challengeFlags()),
                RunOne.CODEX, codex.toAbsolutePath().toString(),
                RunOne.WEIGHTS, weights.toAbsolutePath().toString(),
                RunOne.PRACTICE, practice.boss().id(),
                RunOne.KIT, practice.profile()));
        if (playbook != null && Brains.readsPlaybook(brain)) {
            command.add(RunOne.PLAYBOOK);
            command.add(playbook.toAbsolutePath().toString());
        }
        ProcessBuilder builder = new ProcessBuilder(command).directory(working.toFile()).redirectErrorStream(true);
        // The debug record names the killer, which the table prints; it is written after each wait and
        // read by nobody while the Run is played (issue #219).
        builder.environment().put(DebugRecord.ENVIRONMENT, "1");
        Process child = builder.start();
        StringBuffer said = new StringBuffer();
        Thread reader = new Thread(() -> drain(child, said), "shatterfish-practice-" + pick.salt());
        reader.setDaemon(true);
        reader.start();
        boolean ok;
        if (!child.waitFor(DEADLINE_SECONDS, TimeUnit.SECONDS)) {
            child.descendants().forEach(ProcessHandle::destroyForcibly);
            child.destroyForcibly();
            said.append("passed its deadline of ").append(DEADLINE_SECONDS).append("s\n");
            ok = false;
        } else {
            ok = child.exitValue() == 0;
        }
        reader.join(30_000L);
        if (!ok) {
            Files.writeString(folder.resolve(triple.heroClass() + "-" + triple.seedCode() + "-" + pick.salt()
                    + ".err"), said.toString(), StandardCharsets.UTF_8);
        }
        return ok;
    }

    private static void drain(Process child, StringBuffer said) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (said.length() < (1 << 20)) {
                    said.append(line).append('\n');
                }
            }
        } catch (IOException closed) {
            // The child is gone.
        }
    }

    // ------------------------------------------------------------------ reading

    /**
     * The numbers of every practice log in {@code folder}. A log that is not a practice log of this
     * start is a refusal: a folder the command wrote holds nothing else.
     */
    static Side read(HeroClass heroClass, Path folder, Practice practice) {
        int runs = 0;
        int killed = 0;
        int died = 0;
        int capped = 0;
        int other = 0;
        int unfinished = 0;
        List<Double> killTurns = new ArrayList<>();
        List<Double> deathTurns = new ArrayList<>();
        Map<String, Integer> killers = new TreeMap<>();
        List<Integer> lastHp = new ArrayList<>();
        for (Path log : list(folder, ".jsonl")) {
            RunLogReader.Log read = RunLogReader.of(log);
            if (!read.readable() || read.records().isEmpty() || !(read.records().get(0) instanceof RunLog.Header header)) {
                unfinished++;
                runs++;
                continue;
            }
            if (!header.practiceRun() || !header.practice().equals(practice.stamp(header.heroClass()))) {
                throw new IllegalStateException(log + " is not a practice Run of " + practice.boss().id() + "/"
                        + practice.profile() + ": " + (header.practiceRun() ? header.practice() : "no practice stamp"));
            }
            runs++;
            RunLog.End end = read.end();
            if (end == null) {
                unfinished++;
                continue;
            }
            RunLog.Outcome outcome = end.outcome();
            double turns = outcome.turns() / 1000.0;
            switch (outcome.cause()) {
                case "BOSS_DOWN" -> {
                    killed++;
                    killTurns.add(turns);
                }
                case "DEATH" -> {
                    died++;
                    deathTurns.add(turns);
                    String[] killer = killerOf(log);
                    killers.merge(killer[0], 1, Integer::sum);
                    if (!killer[1].isEmpty()) {
                        lastHp.add(Integer.parseInt(killer[1]));
                    }
                }
                case "TURN_CAP" -> capped++;
                default -> other++;
            }
        }
        return new Side(heroClass, runs, killed, died, capped, other, unfinished, killTurns, deathTurns, killers, lastHp);
    }

    /** The killer and the last health the Run's debug record names for its end, or "unknown" and "". */
    private static String[] killerOf(Path log) {
        Path debug = DebugRecord.beside(log);
        if (!Files.isRegularFile(debug)) {
            return new String[] {"unknown", ""};
        }
        try {
            List<String> lines = Files.readAllLines(debug, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (line.contains("\"t\":\"end\"")) {
                    Map<String, String> held = Json.object(line);
                    String killer = held.containsKey("killer") ? Json.string(held.get("killer")) : "unknown";
                    String hp = held.containsKey("last_hp") ? held.get("last_hp") : "";
                    return new String[] {killer.isEmpty() ? "unknown" : killer, hp};
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // A killer the record cannot give is "unknown", which the table says.
        }
        return new String[] {"unknown", ""};
    }

    // ------------------------------------------------------------------ the table

    static String table(PracticeBoss boss, List<Side> sides) {
        StringBuilder out = new StringBuilder();
        out.append("| class | Runs | ").append(boss.id()).append(" killed | rate | 95% (Wilson) | died | cap | left floor / other | not finished |"
                + " median turns (kills) | median turns (deaths) |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        int runs = 0;
        int killed = 0;
        int finishedRuns = 0;
        for (Side s : sides) {
            int base = s.finished();
            double[] w = wilson(s.killed(), base);
            out.append("| ").append(s.heroClass()).append(" | ").append(s.runs()).append(" | ").append(s.killed())
                    .append(" | ").append(percent(s.killed(), base)).append(" | ").append(interval(w)).append(" | ")
                    .append(s.died()).append(" | ").append(s.capped()).append(" | ").append(s.other()).append(" | ")
                    .append(s.unfinished()).append(" | ").append(median(s.killTurns())).append(" | ")
                    .append(median(s.deathTurns())).append(" |\n");
            runs += s.runs();
            killed += s.killed();
            finishedRuns += base;
        }
        if (sides.size() > 1) {
            double[] w = wilson(killed, finishedRuns);
            out.append("| all | ").append(runs).append(" | ").append(killed).append(" | ")
                    .append(percent(killed, finishedRuns)).append(" | ").append(interval(w)).append(" | | | | | | |\n");
        }
        out.append("\nThe rate is kills over Runs that finished. Killers of the Runs that died:\n\n");
        for (Side s : sides) {
            out.append("- ").append(s.heroClass()).append(": ");
            if (s.killers().isEmpty()) {
                out.append("none died");
            } else {
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(s.killers().entrySet());
                sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
                StringBuilder killers = new StringBuilder();
                for (Map.Entry<String, Integer> e : sorted) {
                    if (killers.length() > 0) {
                        killers.append(", ");
                    }
                    killers.append(e.getKey()).append(' ').append(e.getValue());
                }
                out.append(killers);
            }
            out.append("\n");
        }
        return out.toString();
    }

    private static String percent(int part, int whole) {
        return whole == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }

    private static String interval(double[] w) {
        return Double.isNaN(w[0]) ? "n/a" : String.format(Locale.ROOT, "%.0f-%.0f%%", 100 * w[0], 100 * w[1]);
    }

    private static String median(List<Double> values) {
        return values.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.1f", DirectionMetrics.median(values));
    }

    /** The 95% Wilson score interval of {@code k} successes in {@code n}, or NaN when there are no trials. */
    static double[] wilson(int k, int n) {
        if (n == 0) {
            return new double[] {Double.NaN, Double.NaN};
        }
        double z = 1.959964;
        double p = (double) k / n;
        double denominator = 1 + z * z / n;
        double centre = (p + z * z / (2 * n)) / denominator;
        double margin = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / denominator;
        return new double[] {Math.max(0, centre - margin), Math.min(1, centre + margin)};
    }

    // ------------------------------------------------------------------ small things

    private static List<Path> list(Path folder, String suffix) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted(Comparator.naturalOrder()).toList();
        } catch (IOException e) {
            throw new IllegalStateException("could not list " + folder, e);
        }
    }

    private static int integer(Map<String, String> a, String flag, int least, int fallback) {
        if (!a.containsKey(flag)) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(a.get(flag));
            if (value < least) {
                throw new IllegalArgumentException(flag + " is at least " + least + ": " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " is a number: " + a.get(flag));
        }
    }

    private static boolean isEmpty(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.findAny().isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException("could not list " + folder, e);
        }
    }
}
