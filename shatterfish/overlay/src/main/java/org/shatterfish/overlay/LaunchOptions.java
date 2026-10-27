package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the launcher is told: the Run's tuple, where its Profile and log go, and whether it may see
 * what a player could not (story 5.1, FR-37).
 *
 * <p>The oracle flag lives here and nowhere else a Run can be started from. The Rig's command lines
 * refuse it by name ({@code Runner.arguments}, {@code RunOne}), and this is the one place an Overlay
 * Run can ask for it: for debugging, off by default, and marked in the window's title and in the
 * Run's log header (non-negotiable 1, FR-11).
 *
 * @param seed          the dungeon seed, as a number or as the code a player types
 * @param heroClass     the hero
 * @param salt          the Run's salt, or null to draw one (ADR-0007)
 * @param profile       the directory the Run's Profile is prepared in, or null for a fresh one
 * @param out           where the Run's log goes
 * @param turnCap       the Run's turn cap
 * @param agentSeed     the random agent's seed: the one the Rig gives a random agent playing the same
 *                      triple ({@code DeciderSeeds.agent}; an Overlay Run has no challenges)
 * @param oracle        whether this Run may see what a player could not; a debugging mode
 * @param exitWhenOver  whether the window closes when the Run ends, for an unattended check
 * @param agent         who plays: {@code random}, the Rig's Baseline, {@code brain} (OverlayAgents), or
 *                      {@code human}: the person at the window plays the whole Run with the game's own
 *                      input, recorded, and the Brain decides every wait as a shadow that is never
 *                      executed (story 5.9)
 * @param weights       the Brain's weight set, for {@code --agent brain} and the shadow of {@code human}
 * @param playbook      the Brain's Playbook (story 7.2), read as the Rig reads it, for the same agents as the weights
 * @param codex         the Codex folder the Brain's knowledge is read from ({@code codex/<tag>} under the
 *                      repository root by default, where {@code :overlay:launch} runs); the bestiary is read
 *                      beside it, from {@code tactics/bestiary.json}
 * @param windowWidth   the window's width in pixels, or 0 for the game's own setting ({@code --window WxH})
 * @param windowHeight  the window's height in pixels, or 0 for the game's own setting
 * @param screenshot    a PNG of one frame to write once the Run is under way, or null ({@code --screenshot})
 * @param resume        the Profile of a Run that was saved and quit, to continue, or null for a new Run
 *                      ({@code --resume}, story 5.14); then seed, hero, salt, turn cap, oracle flag, agent and
 *                      log folder are the suspended Run's own, read from its marker and its log's header
 * @param replay        a Run log to play back in the game, or null ({@code --replay}, story 8.5); then seed, hero,
 *                      salt and turn cap are the log header's, nobody plays, and no log is written
 * @param refused       why a log named by {@code --replay} cannot be played, or null; the Panel says it and no Run
 *                      starts (story 8.5)
 */
public record LaunchOptions(long seed, HeroClass heroClass, Long salt, Path profile, Path out, int turnCap,
                            long agentSeed, boolean oracle, boolean exitWhenOver, String agent, Path weights,
                            Path playbook, Path codex, int windowWidth, int windowHeight, Path screenshot, Path resume,
                            Path replay, String refused) {

    /** The flags the launcher knows. A flag it does not know is refused by name, never ignored. */
    public static final List<String> KNOWN = List.of("--seed", "--class", "--salt", "--profile", "--out",
            "--turn-cap", "--oracle", "--exit-when-over", "--agent", "--weights", "--playbook", "--codex", "--window",
            "--screenshot", "--resume", "--replay");

    /** The flags that take no value. */
    private static final List<String> SWITCHES = List.of("--oracle", "--exit-when-over");

    public LaunchOptions {
        if (heroClass == null || out == null || weights == null || playbook == null || codex == null) {
            throw new IllegalArgumentException("a Run names its hero, where its log goes, and the weights and"
                    + " Codex a Brain would be built on");
        }
        if (seed < 0 || seed >= DungeonSeed.TOTAL_SEEDS) {
            throw new IllegalArgumentException("a seed is one a player could type: " + seed);
        }
        if (turnCap < 1) {
            throw new IllegalArgumentException("the turn cap is at least one turn: " + turnCap);
        }
        if (windowWidth < 0 || windowHeight < 0 || (windowWidth == 0) != (windowHeight == 0)) {
            throw new IllegalArgumentException("--window is WxH, both positive: " + windowWidth + "x" + windowHeight);
        }
        if (!agent.equals("random") && !agent.equals("brain") && !agent.equals("human") && !agent.equals(REPLAY_AGENT)) {
            throw new IllegalArgumentException("--agent is random, brain or human: " + agent);
        }
        if (refused != null && replay == null) {
            throw new IllegalArgumentException("only a replay is refused");
        }
    }

    /** The name a replay's options give to who plays: the log, which nobody chose from the command line. */
    static final String REPLAY_AGENT = "replay";

    /** Whether this launch plays a Run log back (story 8.5). */
    public boolean replaying() {
        return replay != null;
    }

    /** Whether the person at the window plays this Run (story 5.9). */
    public boolean human() {
        return agent.equals("human");
    }

    /** The flags a resume takes from the suspended Run, and so refuses to be given (story 5.14). */
    private static final List<String> FROM_THE_RUN = List.of("--seed", "--class", "--salt", "--profile", "--out",
            "--turn-cap", "--oracle", "--agent");

    /** Whether this launch continues a Run that was saved and quit. */
    public boolean resuming() {
        return resume != null;
    }

    /** Reads the command line; see {@link #KNOWN}. */
    public static LaunchOptions parse(String[] args) {
        Map<String, String> given = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (!KNOWN.contains(flag)) {
                throw new IllegalArgumentException("the launcher does not know " + flag + "; it knows " + KNOWN);
            }
            if (given.containsKey(flag)) {
                throw new IllegalArgumentException(flag + " is given twice");
            }
            if (SWITCHES.contains(flag)) {
                given.put(flag, "yes");
                continue;
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException(flag + " takes a value");
            }
            given.put(flag, args[++i]);
        }
        if (given.containsKey("--resume") && given.containsKey("--replay")) {
            throw new IllegalArgumentException("--resume and --replay are two ways to start from a log; give one");
        }
        if (given.containsKey("--replay")) {
            return replayed(given);
        }
        if (given.containsKey("--resume")) {
            return resumed(given);
        }
        if (!given.containsKey("--seed") || !given.containsKey("--class")) {
            throw new IllegalArgumentException("a Run states --seed and --class");
        }
        return new LaunchOptions(
                seed(given.get("--seed")),
                HeroClass.valueOf(given.get("--class").toUpperCase(Locale.ROOT)),
                given.containsKey("--salt") ? Long.parseUnsignedLong(strip(given.get("--salt")), 16) : null,
                given.containsKey("--profile") ? Path.of(given.get("--profile")) : null,
                Path.of(given.getOrDefault("--out", "overlay-runs")),
                Integer.parseInt(given.getOrDefault("--turn-cap", "20000")),
                DeciderSeeds.agent(seed(given.get("--seed")),
                        org.shatterfish.api.HeroClass.valueOf(given.get("--class").toUpperCase(Locale.ROOT)), 0),
                given.containsKey("--oracle"),
                given.containsKey("--exit-when-over"),
                given.getOrDefault("--agent", "random"),
                Path.of(given.getOrDefault("--weights", "weights/shatterfish.json")),
                Path.of(given.getOrDefault("--playbook", "playbooks/shatterfish.json")),
                given.containsKey("--codex") ? Path.of(given.get("--codex"))
                        : Path.of("codex", org.shatterfish.harness.boot.HeadlessBoot.pinnedTag()),
                window(given.get("--window"), 0), window(given.get("--window"), 1),
                given.containsKey("--screenshot") ? Path.of(given.get("--screenshot")) : null, null, null, null);
    }

    /** The flags a replay takes from the log, and so refuses to be given (story 8.5). */
    private static final List<String> FROM_THE_LOG = List.of("--seed", "--class", "--salt", "--out", "--turn-cap",
            "--oracle", "--agent");

    /**
     * The options of a replay (story 8.5): the tuple and the cap are the log header's, so they cannot be changed on
     * the way in, and nothing is written. A log that cannot be played still starts the game, with the reason for the
     * Panel to say and no Run: the tuple is a placeholder nobody plays.
     */
    private static LaunchOptions replayed(Map<String, String> given) {
        for (String flag : FROM_THE_LOG) {
            if (given.containsKey(flag)) {
                throw new IllegalArgumentException(flag + " cannot be given with --replay: the log's own "
                        + flag.substring(2) + " is the one its header states");
            }
        }
        if (given.containsKey("--resume")) {
            throw new IllegalArgumentException("--resume and --replay are two ways to start from a log; give one");
        }
        Path log = Path.of(given.get("--replay"));
        Path profile = given.containsKey("--profile") ? Path.of(given.get("--profile")) : null;
        org.shatterfish.api.RunLog.Header header = null;
        String refused = null;
        try {
            header = org.shatterfish.harness.log.Replay.open(log).header();
        } catch (IllegalArgumentException | java.io.UncheckedIOException cannot) {
            refused = cannot.getMessage();
        }
        return new LaunchOptions(
                header == null ? 0 : header.seed(),
                header == null ? HeroClass.WARRIOR : HeroClass.valueOf(header.heroClass().name()),
                header == null ? null : header.salt(),
                profile,
                Path.of("overlay-runs"),
                header == null ? 20000 : header.cap(),
                0L,
                false,
                given.containsKey("--exit-when-over"),
                REPLAY_AGENT,
                Path.of(given.getOrDefault("--weights", "weights/shatterfish.json")),
                Path.of(given.getOrDefault("--playbook", "playbooks/shatterfish.json")),
                given.containsKey("--codex") ? Path.of(given.get("--codex"))
                        : Path.of("codex", org.shatterfish.harness.boot.HeadlessBoot.pinnedTag()),
                window(given.get("--window"), 0), window(given.get("--window"), 1),
                given.containsKey("--screenshot") ? Path.of(given.get("--screenshot")) : null, null, log, refused);
    }

    /**
     * The options of a resume: everything about the Run comes from its marker and the header of its log, so
     * the tuple cannot be changed on the way back in. The marker is read, not claimed; the launcher claims it.
     */
    private static LaunchOptions resumed(Map<String, String> given) {
        for (String flag : FROM_THE_RUN) {
            if (given.containsKey(flag)) {
                throw new IllegalArgumentException(flag + " cannot be given with --resume: the Run's own " + flag.substring(2)
                        + " is the one its log's header states");
            }
        }
        Path profile = Path.of(given.get("--resume"));
        org.shatterfish.harness.driver.SuspendedSave marker = org.shatterfish.harness.driver.SuspendedSave.read(profile);
        org.shatterfish.api.RunLog.Header header = org.shatterfish.harness.driver.RunLogResume.read(marker.log()).header();
        Path folder = marker.log().toAbsolutePath().getParent();
        return new LaunchOptions(
                header.seed(),
                HeroClass.valueOf(header.heroClass().name()),
                header.salt(),
                profile,
                folder,
                header.cap(),
                DeciderSeeds.agent(header.seed(), header.heroClass(), 0),
                header.oracle(),
                given.containsKey("--exit-when-over"),
                marker.agent(),
                Path.of(given.getOrDefault("--weights", "weights/shatterfish.json")),
                Path.of(given.getOrDefault("--playbook", "playbooks/shatterfish.json")),
                given.containsKey("--codex") ? Path.of(given.get("--codex"))
                        : Path.of("codex", org.shatterfish.harness.boot.HeadlessBoot.pinnedTag()),
                window(given.get("--window"), 0), window(given.get("--window"), 1),
                given.containsKey("--screenshot") ? Path.of(given.get("--screenshot")) : null, profile, null, null);
    }

    /**
     * One side of {@code --window WxH}, or 0 when the flag is absent. The window size decides the UI
     * camera's size, and so where the Panel goes and whether it is collapsed (story 5.2).
     */
    private static int window(String text, int side) {
        if (text == null) {
            return 0;
        }
        String[] sides = text.toLowerCase(Locale.ROOT).split("x");
        if (sides.length != 2) {
            throw new IllegalArgumentException("--window is WxH, e.g. 1600x900: " + text);
        }
        return Integer.parseInt(sides[side].trim());
    }

    /** A seed as a number, or as the code a player types into the seed window. */
    private static long seed(String text) {
        if (DungeonSeed.formatText(text).matches("[A-Z]{3}-[A-Z]{3}-[A-Z]{3}")) {
            return DungeonSeed.convertFromCode(DungeonSeed.formatText(text));
        }
        return Long.parseLong(text);
    }

    private static String strip(String hex) {
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
    }
}
