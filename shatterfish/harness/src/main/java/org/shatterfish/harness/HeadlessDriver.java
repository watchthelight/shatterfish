package org.shatterfish.harness;

import com.badlogic.gdx.Gdx;
import org.shatterfish.harness.boot.HeadlessBoot;

/**
 * The headless driver: boots the game with no window, and will own the loop.
 *
 * <p>Story 1.3 put the boot behind {@link HeadlessBoot} and the scene behind
 * {@code HeadlessScene}; story 1.4 adds the loop here (start a seeded Run, step the scene until
 * the hero's first Input wait, drain the posted runnables, fail with a diagnostic rather than
 * hang). Until then this class is the entry point that proves the boot works from the command
 * line and from CI.
 */
public final class HeadlessDriver {

    private HeadlessDriver() {
    }

    /** What the backend reported about itself after booting. */
    public record Boot(String applicationType, String upstreamVersion) {
    }

    /** Boots the process, or returns the boot that already happened. */
    public static Boot boot() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        return new Boot(Gdx.app.getType().name(), boot.upstreamVersionName());
    }

    public static void main(String[] args) {
        Boot boot = boot();
        System.out.println("HeadlessDriver: booted libGDX " + boot.applicationType()
                + " backend for Shattered Pixel Dungeon " + boot.upstreamVersion());
        Gdx.app.exit();
    }
}
