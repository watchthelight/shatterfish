package org.shatterfish.overlay;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.driver.RunLogResume;
import org.shatterfish.harness.driver.SuspendedSave;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A Run that was saved and quit, checked and claimed for its resume (story 5.14, ADR-0013).
 *
 * <p>The launcher resumes through this class or not at all. It refuses, naming why and before it claims
 * anything, a Profile that:
 * <ul>
 * <li>holds no marker, or a log that is not suspended and verifiable ({@link RunLogResume});</li>
 * <li>holds a save whose seal is not the marker's: the save was opened without the launcher and played, so
 * it is no longer the state the log's boundary describes and is not an Overlay Run. Its log ends at the
 * boundary for good;</li>
 * <li>was asked to resume under another agent than the one that played (the header's name and
 * configuration hash), because a log is one agent's account.</li>
 * </ul>
 * Only then is the marker claimed, by an atomic rename, so two launchers never continue one log.
 *
 * @param marker the marker as it was claimed
 * @param log    the suspended log
 */
record Resumption(SuspendedSave marker, RunLogResume.Suspended log) {

    /** Checks the Profile named by {@code options} and claims its marker. */
    static Resumption claim(LaunchOptions options) {
        Path profile = options.resume();
        Check checked = check(options);
        SuspendedSave claimed = SuspendedSave.claim(profile);
        if (!claimed.equals(checked.marker())) {
            SuspendedSave.unclaim(profile);
            throw new IllegalStateException("the marker in " + profile + " changed while it was being checked");
        }
        return new Resumption(claimed, checked.log());
    }

    /** Everything {@link #claim} checks, without claiming. */
    static Check check(LaunchOptions options) {
        Path profile = options.resume();
        if (profile == null) {
            throw new IllegalArgumentException("not a resume");
        }
        SuspendedSave marker = SuspendedSave.read(profile);
        RunLogResume.Suspended log = RunLogResume.read(marker.log());
        if (log.boundary().k() != marker.k()) {
            throw new IllegalStateException("the marker names wait " + marker.k() + " and the log's boundary names wait "
                    + log.boundary().k() + ": they are not one save and quit");
        }
        String seal = SuspendedSave.digest(profile, marker.slot());
        if (!seal.equals(marker.digest())) {
            throw new IllegalStateException("the save in " + profile + " is not the one that was sealed when the Run was saved"
                    + " and quit: it was opened without the launcher. That is not an Overlay Run, and its log ends at the"
                    + " boundary for good");
        }
        RunLog.Brain brain = log.header().brain();
        if (!brain.name().equals(OverlayAgents.name(options))) {
            throw new IllegalStateException("the Run was played by " + brain.name() + ", not " + OverlayAgents.name(options));
        }
        String hash = OverlayAgents.configHash(options);
        if (!brain.configHash().equals(hash)) {
            throw new IllegalStateException("the agent's configuration is not the one that played the Run (weights or Codex"
                    + " changed): " + brain.configHash() + " against " + hash);
        }
        if (!Files.isRegularFile(marker.log())) {
            throw new IllegalStateException("the Run log " + marker.log() + " is gone");
        }
        return new Check(marker, log);
    }

    /** What was checked. */
    record Check(SuspendedSave marker, RunLogResume.Suspended log) {
    }
}
