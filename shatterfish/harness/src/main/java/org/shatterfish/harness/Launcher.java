package org.shatterfish.harness;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.OracleObserver;
import org.shatterfish.harness.observer.OracleView;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.rng.Salt;

import java.io.PrintStream;

/**
 * The harness's command line: a seed, and the one flag that exists only here, {@code --oracle}
 * (FR-11). Without the flag a Run is read by the fair {@link Observer}; with it, by the
 * {@link OracleObserver}, whose Observation is marked in its header and whose sidecar is printed
 * for the person who asked. This is the only class in the harness allowed to construct an oracle
 * observer, which {@code OracleGateTest} holds, so the flag is a property of a launch and never of
 * a Run, a Brain or the measured loop.
 */
public final class Launcher {

    /** What a command line asked for. */
    public record Launch(long seed, boolean oracle) {

        /** The seed a launch plays when none is given. */
        public static final long DEFAULT_SEED = 31_415_926L;

        /**
         * Parses one seed and {@code --oracle} in any order. A second seed, a seed outside the
         * game's range ({@code …/utils/DungeonSeed.java}, {@code TOTAL_SEEDS}) and anything else
         * are refused by name.
         */
        public static Launch parse(String[] args) {
            long seed = DEFAULT_SEED;
            boolean seedGiven = false;
            boolean oracle = false;
            for (String arg : args) {
                if (arg.equals("--oracle")) {
                    oracle = true;
                } else if (arg.matches("-?[0-9]+")) {
                    if (seedGiven) {
                        throw new IllegalArgumentException("two seeds: " + seed + " and " + arg);
                    }
                    try {
                        seed = Long.parseLong(arg);
                    } catch (NumberFormatException tooLong) {
                        throw new IllegalArgumentException("seed out of range: " + arg);
                    }
                    if (seed < 0 || seed >= DungeonSeed.TOTAL_SEEDS) {
                        throw new IllegalArgumentException("seed out of range: " + arg + " (0 to " + (DungeonSeed.TOTAL_SEEDS - 1) + ")");
                    }
                    seedGiven = true;
                } else {
                    throw new IllegalArgumentException("unknown argument " + arg + "; a seed and --oracle are the arguments");
                }
            }
            return new Launch(seed, oracle);
        }
    }

    private Launcher() {
    }

    public static void main(String[] args) {
        Launch launch = Launch.parse(args);
        HeadlessDriver.Boot boot = HeadlessDriver.boot();
        System.out.println("Launcher: booted libGDX " + boot.applicationType()
                + " backend for Shattered Pixel Dungeon " + boot.upstreamVersion());
        try {
            // The runner draws the salt and says what it drew (ADR-0007): anyone with the seed,
            // the salt and the Actions can play the Run again.
            long salt = Salt.draw();
            try (HeadlessDriver driver = HeadlessDriver.start(launch.seed(), HeroClass.WARRIOR, salt)) {
                HeadlessDriver.Halt halt = driver.stepToInputWait();
                System.out.println("Launcher: salt " + Long.toHexString(salt));
                System.out.println("Launcher: seed " + launch.seed() + " (" + DungeonSeed.convertToCode(launch.seed()) + "), "
                        + HeroClass.WARRIOR.name() + ": " + halt.reason() + " after " + halt.framesStepped()
                        + " frame(s); hero at cell " + Dungeon.hero.pos + " on depth " + Dungeon.depth);
                read(launch, System.out);
            }
        } finally {
            Gdx.app.exit();
        }
    }

    /**
     * The read a launch asks for, at the Input wait the game is at: the oracle's, marked and with
     * the sidecar printed to {@code out}, when the flag was given; the fair Observer's otherwise.
     * Returns the Observation read, so a test can hold the branch to the flag.
     */
    public static Observation read(Launch launch, PrintStream out) {
        if (launch.oracle()) {
            OracleObserver.Read read = new OracleObserver().observe();
            out.println("Launcher: ORACLE, observation " + read.observation().hash());
            describe(read.view(), out);
            return read.observation();
        }
        Observation observation = new Observer().observe();
        out.println("Launcher: fair, observation " + observation.hash());
        return observation;
    }

    /** The sidecar, one line per thing, for a person and for nobody else. */
    private static void describe(OracleView view, PrintStream out) {
        out.println("Launcher: ORACLE seed " + view.seed());
        for (OracleView.Identity identity : view.identities()) {
            out.println("Launcher: ORACLE " + identity.family() + " " + identity.appearance() + " = " + identity.trueName()
                    + " (" + identity.type() + ")");
        }
        for (OracleView.Presence mob : view.mobs()) {
            out.println("Launcher: ORACLE mob " + mob.name() + " (" + mob.type() + ") at " + mob.cell() + " " + mob.hp() + "/"
                    + mob.ht() + " " + mob.state() + (mob.seen() ? " drawn" : " not drawn"));
        }
        out.println("Launcher: ORACLE hidden mimics " + view.hiddenMimics() + ", secret doors " + view.secretDoors()
                + ", hidden traps " + view.hiddenTraps());
    }
}
