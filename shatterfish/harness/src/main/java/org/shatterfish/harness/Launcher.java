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

/**
 * The harness's command line: a seed, and the one flag that exists only here, {@code --oracle}
 * (FR-11). Without the flag a Run is read by the fair {@link Observer}; with it, by the
 * {@link OracleObserver}, whose Observation is marked in its header and whose sidecar is printed
 * for the person who asked. This is the only class allowed to construct an oracle observer, which
 * {@code OracleGateTest} holds, so the flag is a property of a launch and never of a Run, a Brain
 * or the measured loop.
 */
public final class Launcher {

    /** What a command line asked for. */
    public record Launch(long seed, boolean oracle) {

        /** Parses a seed and {@code --oracle} in any order; anything else is refused by name. */
        public static Launch parse(String[] args) {
            long seed = 31_415_926L;
            boolean oracle = false;
            for (String arg : args) {
                if (arg.equals("--oracle")) {
                    oracle = true;
                } else if (arg.matches("-?\\d+")) {
                    seed = Long.parseLong(arg);
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
        // The runner draws the salt and says what it drew (ADR-0007): anyone with the seed, the
        // salt and the Actions can play the Run again.
        long salt = Salt.draw();
        try (HeadlessDriver driver = HeadlessDriver.start(launch.seed(), HeroClass.WARRIOR, salt)) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            System.out.println("Launcher: salt " + Long.toHexString(salt));
            System.out.println("Launcher: seed " + launch.seed() + " (" + DungeonSeed.convertToCode(launch.seed()) + "), "
                    + HeroClass.WARRIOR.name() + ": " + halt.reason() + " after " + halt.framesStepped()
                    + " frame(s); hero at cell " + Dungeon.hero.pos + " on depth " + Dungeon.depth);
            if (launch.oracle()) {
                OracleObserver.Read read = new OracleObserver().observe();
                System.out.println("Launcher: ORACLE, observation " + read.observation().hash());
                describe(read.view());
            } else {
                Observation observation = new Observer().observe();
                System.out.println("Launcher: fair, observation " + observation.hash());
            }
        }
        Gdx.app.exit();
    }

    /** The sidecar, one line per thing, for a person and for nobody else. */
    private static void describe(OracleView view) {
        System.out.println("Launcher: ORACLE seed " + view.seed());
        for (OracleView.Identity identity : view.identities()) {
            System.out.println("Launcher: ORACLE " + identity.family() + " " + identity.appearance() + " = " + identity.trueName());
        }
        for (OracleView.Presence mob : view.mobs()) {
            System.out.println("Launcher: ORACLE mob " + mob.name() + " at " + mob.cell() + " " + mob.hp() + "/" + mob.ht()
                    + " " + mob.state() + (mob.seen() ? " seen" : " unseen"));
        }
        System.out.println("Launcher: ORACLE hidden mimics " + view.hiddenMimics() + ", secret doors " + view.secretDoors()
                + ", hidden traps " + view.hiddenTraps());
    }
}
