package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;

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
 * @param agentSeed     the random agent's seed
 * @param oracle        whether this Run may see what a player could not; a debugging mode
 * @param exitWhenOver  whether the window closes when the Run ends, for an unattended check
 */
public record LaunchOptions(long seed, HeroClass heroClass, Long salt, Path profile, Path out, int turnCap,
                            long agentSeed, boolean oracle, boolean exitWhenOver) {

    /** The flags the launcher knows. A flag it does not know is refused by name, never ignored. */
    public static final List<String> KNOWN = List.of("--seed", "--class", "--salt", "--profile", "--out",
            "--turn-cap", "--agent-seed", "--oracle", "--exit-when-over");

    /** The flags that take no value. */
    private static final List<String> SWITCHES = List.of("--oracle", "--exit-when-over");

    public LaunchOptions {
        if (heroClass == null || out == null) {
            throw new IllegalArgumentException("a Run names its hero and where its log goes");
        }
        if (seed < 0 || seed >= DungeonSeed.TOTAL_SEEDS) {
            throw new IllegalArgumentException("a seed is one a player could type: " + seed);
        }
        if (turnCap < 1) {
            throw new IllegalArgumentException("the turn cap is at least one turn: " + turnCap);
        }
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
                Long.parseLong(given.getOrDefault("--agent-seed", "1")),
                given.containsKey("--oracle"),
                given.containsKey("--exit-when-over"));
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
