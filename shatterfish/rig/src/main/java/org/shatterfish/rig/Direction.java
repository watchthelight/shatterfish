package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.harness.knowledge.CodexManifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The direction check: play a subset of a development seed set with the current Brain, optionally
 * with a baseline too, and print the table every Brain PR carries.
 *
 * <pre>
 * ./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main"
 * </pre>
 *
 * <p>The tuning set is {@code --set standard --class WARRIOR --first 40 --salt-base 1000}: the
 * first 40 Warriors of the standard set in file order, entry {@code i} under salt {@code 1000 + i}.
 * It is defined by those flags and by nothing committed, because a seeds file is written only by
 * {@code ./gradlew :rig:seeds}.
 *
 * <p>This is a direction check, not an acceptance (ADR-0012): nothing it plays is ranked and nothing
 * it prints is a published measurement. It refuses {@code holdout} (never for development) and
 * {@code goo} (registered use only); see {@link DirectionSelect}.
 *
 * <p><b>Baseline.</b> {@code --baseline <folder>} reads an earlier direction folder (its
 * {@code current/} folder, or a folder of logs) and plays nothing. {@code --baseline <git ref>}
 * checks the ref out into a worktree under the temp folder, builds its {@code :brain:jar} there
 * with that checkout's own Gradle wrapper, and plays the same picks under the same salts with the
 * current build's classpath but the ref's Brain, Codex and weights. The api, harness and rig
 * stay the current ones, so a ref whose api differs from the current one cannot be a baseline.
 * Building the jar is a second Gradle job while this one runs; mind the machine's Gradle limit.
 */
public final class Direction {

    public static final String SET = "--set";
    public static final String CLASS = "--class";
    public static final String FIRST = "--first";
    public static final String SALT_BASE = "--salt-base";
    public static final String BASELINE = "--baseline";
    public static final String JOBS = "--jobs";
    public static final String OUT = "--out";
    public static final String ROOT = "--root";

    static final List<String> KNOWN = List.of(SET, CLASS, FIRST, SALT_BASE, BASELINE, JOBS, OUT, ROOT);

    /** Default number of Runs in flight: a quarter of the cores, from 2 to 8, so several measurements can share the machine. */
    static final int DEFAULT_JOBS = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 4));

    private static final int DEADLINE_SECONDS = 1800;

    private static final String BRAIN = Brains.SHATTERFISH;

    private Direction() {
    }

    public static void main(String[] args) {
        try {
            System.out.println(run(arguments(args)));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            System.err.println("direction: " + refused.getMessage());
            System.exit(2);
        }
    }

    /** The flags as a map, refusing an unknown flag, a flag with no value, or one given twice. */
    static Map<String, String> arguments(String[] args) {
        Map<String, String> given = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String flag = args[i];
            if (!KNOWN.contains(flag)) {
                throw new IllegalArgumentException("unknown flag " + flag + "; the flags are " + KNOWN);
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

    /** Plays and measures what the arguments ask for, and returns the text to print. */
    static String run(Map<String, String> a) {
        Path root = Path.of(a.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        String set = a.getOrDefault(SET, SeedSets.STANDARD);
        HeroClass heroClass = a.containsKey(CLASS) ? DirectionSelect.heroClass(a.get(CLASS)) : null;
        int first = a.containsKey(FIRST) ? integer(a, FIRST, 1) : -1;
        long saltBase = a.containsKey(SALT_BASE) ? Long.parseLong(a.get(SALT_BASE)) : 0L;
        int jobs = a.containsKey(JOBS) ? integer(a, JOBS, 1) : DEFAULT_JOBS;
        List<DirectionSelect.Pick> picks = DirectionSelect.select(root, set, heroClass, first, saltBase);
        if (picks.isEmpty()) {
            throw new IllegalArgumentException("no triple of " + set + " is left after the filter");
        }
        Path out = a.containsKey(OUT) ? Path.of(a.get(OUT)).toAbsolutePath().normalize()
                : root.resolve("build").resolve("direction")
                        .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        if (Files.exists(out) && !isEmpty(out)) {
            throw new IllegalArgumentException(out + " is not empty; a direction check writes a folder of its own");
        }
        String baseline = a.get(BASELINE);
        Path baselineFolder = baseline == null ? null : folderOf(baseline);
        if (baseline != null && baselineFolder == null && !gitRef(root, baseline)) {
            throw new IllegalArgumentException(baseline + " is neither a folder nor a git ref of " + root);
        }

        String describe = set + (heroClass == null ? "" : " " + heroClass) + ", " + picks.size()
                + " entries, salts " + saltBase + ".." + (saltBase + picks.size() - 1);
        System.out.println("direction: " + describe + ", " + jobs + " at a time, into " + out);

        String commit = Runner.commitOf(root);
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        Path currentFolder = out.resolve("current");
        int failedNow = play(picks, currentFolder, Runner.classpath(), root, tag, commit, jobs);

        Path baselineLogs = baselineFolder;
        String baselineName = baseline;
        int failedBase = 0;
        if (baseline != null && baselineFolder == null) {
            // Short, and under the temp folder: the game's source tree has paths long enough that a
            // checkout under a deep output folder fails on Windows ("Filename too long").
            Path source;
            try {
                source = Files.createTempDirectory("sfbase");
            } catch (IOException e) {
                throw new IllegalStateException("could not make a folder for the baseline checkout", e);
            }
            String sha = revParse(root, baseline);
            Path cached = cacheFolder(root, sha, tag, set, heroClass, first, saltBase, picks.size());
            if (cached != null && Files.isDirectory(cached)) {
                System.out.println("direction: the baseline " + sha.substring(0, 9) + " is cached in " + cached);
                org.shatterfish.harness.boot.TempDirs.deleteTree(source);
                baselineLogs = cached;
                baselineName = sha.startsWith(baseline) ? sha.substring(0, 9) : baseline + " " + sha.substring(0, 9);
            } else try {
                git(root, "worktree", "add", "--detach", source.toString(), sha);
                gradleJar(source);
                Path jar = brainJar(source);
                String cp = swapBrain(Runner.classpath(), jar);
                baselineLogs = out.resolve("baseline");
                failedBase = play(picks, baselineLogs, cp, source, tag, sha, jobs);
                baselineName = sha.startsWith(baseline) ? sha.substring(0, 9) : baseline + " " + sha.substring(0, 9);
                if (cached != null && failedBase == 0) {
                    store(baselineLogs, cached);
                }
            } finally {
                try {
                    git(root, "worktree", "remove", "--force", source.toString());
                } catch (RuntimeException leftover) {
                    System.err.println("direction: could not remove " + source + ": " + leftover.getMessage());
                }
                org.shatterfish.harness.boot.TempDirs.deleteTree(source);
                try {
                    git(root, "worktree", "prune");
                } catch (RuntimeException pruned) {
                    System.err.println("direction: could not prune worktrees: " + pruned.getMessage());
                }
            }
        }

        DirectionMetrics.Side now = DirectionMetrics.read(currentFolder);
        DirectionMetrics.Side then = baselineLogs == null ? null : DirectionMetrics.read(baselineLogs);
        String table = DirectionMetrics.table("current " + commit.substring(0, 9), now, baselineName, then);
        StringBuilder text = new StringBuilder();
        text.append(describe).append("\n\n").append(table);
        if (failedNow + failedBase > 0) {
            text.append("\nRuns that did not finish: ").append(failedNow).append(" current, ")
                    .append(failedBase).append(" baseline; see the .err files beside the logs.\n");
        }
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("direction.md"), text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the table into " + out, e);
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ playing

    /**
     * Plays every pick, {@code jobs} at a time, one child JVM per Run (AD-6), into {@code folder}.
     * Codex and weights come from {@code source}'s checkout. Returns how many Runs did not finish.
     */
    private static int play(List<DirectionSelect.Pick> picks, Path folder, String classpath, Path source,
                            String tag, String commit, int jobs) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("could not create " + folder, e);
        }
        Path codex = source.resolve(CodexManifest.FOLDER).resolve(tag);
        Path weights = source.resolve(WeightsFile.FOLDER).resolve(BRAIN + ".json");
        ExecutorService pool = Executors.newFixedThreadPool(jobs);
        try {
            List<Future<Boolean>> started = new ArrayList<>();
            for (int i = 0; i < picks.size(); i++) {
                DirectionSelect.Pick pick = picks.get(i);
                Path working = folder.resolve("work").resolve(String.valueOf(i));
                started.add(pool.submit(() -> one(pick, folder, working, classpath, codex, weights, commit)));
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

    private static boolean one(DirectionSelect.Pick pick, Path folder, Path working, String classpath,
                               Path codex, Path weights, String commit) throws IOException, InterruptedException {
        Files.createDirectories(working);
        var triple = pick.entry();
        List<String> command = new ArrayList<>(List.of(
                Runner.javaExecutable().toString(),
                "-cp", classpath, DirectionPlay.class.getName(),
                RunOne.SEED, Long.toString(triple.seed()),
                RunOne.CLASS, triple.heroClass().name(),
                RunOne.SALT, Long.toString(pick.salt()),
                RunOne.OUT, folder.toAbsolutePath().toString(),
                RunOne.COMMIT, commit,
                RunOne.BRAIN, BRAIN,
                RunOne.BRAIN_COMMIT, commit,
                RunOne.CHALLENGES, Integer.toString(triple.challengeFlags()),
                RunOne.CODEX, codex.toAbsolutePath().toString(),
                RunOne.WEIGHTS, weights.toAbsolutePath().toString()));
        Process child = new ProcessBuilder(command).directory(working.toFile()).redirectErrorStream(true).start();
        StringBuffer said = new StringBuffer();
        Thread reader = new Thread(() -> drain(child, said), "shatterfish-direction-" + pick.salt());
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

    // ------------------------------------------------------------------ the baseline

    /** The folder of logs {@code name} names, or null when it names no folder. */
    static Path folderOf(String name) {
        Path path = Path.of(name);
        if (!Files.isDirectory(path)) {
            return null;
        }
        Path current = path.resolve("current");
        return Files.isDirectory(current) ? current : path;
    }

    /**
     * {@code classpath} with its Brain replaced by {@code jar}. The Brain is the entry that is a
     * {@code brain-*.jar}, or a build output directory of the brain module; there must be exactly one.
     */
    static String swapBrain(String classpath, Path jar) {
        String[] parts = classpath.split(java.io.File.pathSeparator);
        List<String> kept = new ArrayList<>();
        boolean replaced = false;
        for (String part : parts) {
            String normal = part.replace('\\', '/');
            String file = normal.substring(normal.lastIndexOf('/') + 1);
            boolean brain = (file.startsWith("brain-") && file.endsWith(".jar"))
                    || normal.contains("/brain/build/classes/") || normal.contains("/brain/build/resources/");
            if (!brain) {
                kept.add(part);
            } else if (!replaced) {
                kept.add(jar.toAbsolutePath().toString());
                replaced = true;
            }
        }
        if (!replaced) {
            throw new IllegalStateException("the classpath holds no Brain to replace: " + classpath);
        }
        return String.join(java.io.File.pathSeparator, kept);
    }

    private static Path brainJar(Path source) {
        Path libs = source.resolve("shatterfish").resolve("brain").resolve("build").resolve("libs");
        try (Stream<Path> files = Files.list(libs)) {
            List<Path> jars = files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("brain-") && n.endsWith(".jar") && !n.contains("-sources")
                        && !n.contains("-javadoc");
            }).toList();
            if (jars.size() != 1) {
                throw new IllegalStateException("expected one brain jar in " + libs + ", found " + jars);
            }
            return jars.get(0);
        } catch (IOException e) {
            throw new IllegalStateException("no brain jar was built in " + libs, e);
        }
    }

    private static void gradleJar(Path source) {
        boolean windows = Runner.windows();
        String wrapper = windows ? "gradlew.bat" : "./gradlew";
        System.out.println("direction: building the baseline's brain jar in " + source);
        run(source, List.of(source.resolve(wrapper).toString(), "-q", ":brain:jar"), "building the baseline's brain");
    }

    // ------------------------------------------------------------------ the baseline cache

    /** The game and harness paths whose code a baseline Run uses from the current tree, not the baseline's. */
    static final List<String> HOST_PATHS = List.of("core", "SPD-classes", "shatterfish/api", "shatterfish/harness",
            "shatterfish/rig");

    /**
     * Where the baseline's logs for this selection are kept, shared by every worktree of the repository
     * (under the common git folder), or null when they cannot be reused. A baseline Run takes its Brain
     * and Codex from the baseline commit but the game, the harness and the Runner from the current tree,
     * so the key names both: the baseline commit and the tree ids of {@link #HOST_PATHS} at the current
     * commit. Uncommitted changes under those paths make the key a lie, so then nothing is cached.
     */
    static Path cacheFolder(Path root, String sha, String tag, String set, HeroClass heroClass, int first,
                            long saltBase, int picks) {
        try {
            List<String> status = new ArrayList<>(List.of("status", "--porcelain", "--"));
            status.addAll(HOST_PATHS);
            if (!git(root, status.toArray(String[]::new)).isBlank()) {
                System.out.println("direction: the harness or the game has uncommitted changes; the baseline is not cached");
                return null;
            }
            StringBuilder key = new StringBuilder();
            key.append(sha).append('|').append(tag).append('|').append(set).append('|')
                    .append(heroClass == null ? "all" : heroClass.name()).append('|').append(first).append('|')
                    .append(saltBase).append('|').append(picks);
            for (String path : HOST_PATHS) {
                key.append('|').append(git(root, "rev-parse", "HEAD:" + path).strip());
            }
            String hash = sha256(key.toString()).substring(0, 16);
            Path common = Path.of(git(root, "rev-parse", "--git-common-dir").strip());
            if (!common.isAbsolute()) {
                common = root.resolve(common).normalize();
            }
            return common.resolve("shatterfish-direction-cache").resolve(sha.substring(0, 9) + "-" + hash);
        } catch (RuntimeException unavailable) {
            System.out.println("direction: the baseline is not cached: " + unavailable.getMessage());
            return null;
        }
    }

    /** Copies a finished baseline's logs into the cache, through a temporary folder so a reader never sees half. */
    static void store(Path logs, Path cached) {
        Path partial = cached.resolveSibling(cached.getFileName() + ".partial-" + ProcessHandle.current().pid());
        try {
            Files.createDirectories(partial);
            try (Stream<Path> files = Files.list(logs)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (Files.isRegularFile(file)) {
                        Files.copy(file, partial.resolve(file.getFileName()));
                    }
                }
            }
            Files.move(partial, cached, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("direction: could not cache the baseline: " + e.getMessage());
            org.shatterfish.harness.boot.TempDirs.deleteTree(partial);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean gitRef(Path root, String ref) {
        try {
            revParse(root, ref);
            return true;
        } catch (RuntimeException no) {
            return false;
        }
    }

    private static String revParse(Path root, String ref) {
        return git(root, "rev-parse", "--verify", ref + "^{commit}").strip();
    }

    private static String git(Path root, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        return run(root, command, "git " + String.join(" ", args));
    }

    private static String run(Path in, List<String> command, String what) {
        try {
            Process p = new ProcessBuilder(command).directory(in.toFile()).redirectErrorStream(true).start();
            String said = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) {
                throw new IllegalStateException(what + " failed: " + said.strip());
            }
            return said;
        } catch (IOException e) {
            throw new IllegalStateException(what + " could not run: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(what + " was interrupted", e);
        }
    }

    // ------------------------------------------------------------------ small things

    private static int integer(Map<String, String> a, String flag, int least) {
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
