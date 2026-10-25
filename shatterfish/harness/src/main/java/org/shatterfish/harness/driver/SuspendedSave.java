package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * What a Profile keeps so that a Run saved and quit can be resumed through the launcher (story 5.14,
 * ADR-0013): the Run log it continues, the wait index it stopped at, and a seal over the game's save.
 *
 * <p><b>The marker.</b> {@value #FILE}, in the Profile's directory, is written after the game's save and
 * the log's {@code boundary} record, in that order, so a Profile that has the marker has both. A Profile
 * without it is not resumable: the Run was killed, or ended, or the process died between the save and
 * the marker. Resuming claims the marker by renaming it ({@link #claim}), which the file system decides
 * for one process at a time, so two launchers given one Profile cannot both continue the one log.
 *
 * <p><b>The seal.</b> A digest over every file of the save's folder, taken when the Run was suspended. A
 * resume refuses a save whose digest has changed: someone opened it without the launcher and played it,
 * so the state is no longer the one the log's boundary describes. That save is then not an Overlay Run,
 * and its log ends at the boundary for good (ADR-0013). The digest covers the save only, never the
 * log: the log's own chain says whether the log is whole ({@link RunLogResume}).
 *
 * @param log       the Run log the resume continues, as an absolute path
 * @param k         the wait index the boundary names, which the log's boundary record carries again
 * @param slot      the save slot the game used
 * @param digest    the seal over the save's folder
 * @param agent     who played: {@code random}, {@code brain} or {@code human}, so the resume plays the same
 * @param temporary whether the launcher made the Profile for this Run alone and deletes it when the Run ends
 */
public record SuspendedSave(Path log, long k, int slot, String digest, String agent, boolean temporary) {

    /** The marker's file name. */
    public static final String FILE = "shatterfish-resume.txt";

    /** What a claimed marker is renamed to. */
    public static final String CLAIMED = FILE + ".claimed";

    private static final String VERSION_KEY = "shatterfish-resume-version";
    private static final int VERSION = 1;

    public SuspendedSave {
        if (log == null || digest == null || digest.isEmpty() || agent == null || k < 0 || slot < 1) {
            throw new IllegalArgumentException("a suspended save names its log, wait, slot, seal and agent");
        }
    }

    /** Whether {@code profile} holds a marker that has not been claimed. */
    public static boolean present(Path profile) {
        return Files.isRegularFile(profile.resolve(FILE));
    }

    /** The seal over the save folder of {@code slot} in {@code profile}, or empty if it holds no save. */
    public static String digest(Path profile, int slot) {
        Path folder = profile.resolve(GamesInProgress.gameFolder(slot));
        if (!Files.isDirectory(folder)) {
            return "";
        }
        try (Stream<Path> files = Files.list(folder)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            List<Path> sorted = files.filter(Files::isRegularFile).sorted().toList();
            if (sorted.isEmpty()) {
                return "";
            }
            for (Path file : sorted) {
                sha.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                byte[] bytes = Files.readAllBytes(file);
                sha.update(Long.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                sha.update(bytes);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("the save in " + folder + " could not be sealed", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("the JDK has no SHA-256", e);
        }
    }

    /** Writes the marker into {@code profile}, whole or not at all. */
    public void write(Path profile) {
        String text = VERSION_KEY + "=" + VERSION + "\n"
                + "log=" + log.toAbsolutePath() + "\n"
                + "k=" + k + "\n"
                + "slot=" + slot + "\n"
                + "digest=" + digest + "\n"
                + "agent=" + agent + "\n"
                + "temporary=" + temporary + "\n";
        Path part = profile.resolve(FILE + ".part");
        try {
            Files.writeString(part, text, StandardCharsets.UTF_8);
            try {
                Files.move(part, profile.resolve(FILE), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException plain) {
                Files.move(part, profile.resolve(FILE), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(profile.resolve(CLAIMED));
        } catch (IOException e) {
            throw new UncheckedIOException("the resume marker could not be written into " + profile, e);
        }
    }

    /**
     * Reads the marker of {@code profile} without claiming it.
     *
     * @throws IllegalStateException if the Profile holds none, or one this build cannot read
     */
    public static SuspendedSave read(Path profile) {
        Path marker = profile.resolve(FILE);
        if (!Files.isRegularFile(marker)) {
            String why = Files.isRegularFile(profile.resolve(CLAIMED))
                    ? "its marker was claimed by a Run that never saved again: it is playing, or it died without saving"
                    : "it holds no marker, so no Overlay Run was saved and quit there";
            throw new IllegalStateException("the Profile " + profile + " cannot be resumed: " + why);
        }
        return parse(marker);
    }

    /**
     * Claims the marker by renaming it, so no second launcher resumes the same Profile, and returns what
     * it held. {@link #unclaim} puts it back if the resume is then refused.
     *
     * @throws IllegalStateException if there is no marker to claim, or another launcher claimed it first
     */
    public static SuspendedSave claim(Path profile) {
        SuspendedSave held = read(profile);
        try {
            Files.move(profile.resolve(FILE), profile.resolve(CLAIMED), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException taken) {
            throw new IllegalStateException("the Profile " + profile + " was claimed by another launcher while this"
                    + " one read it; two Overlay Runs never share a Profile (FR-37)", taken);
        } catch (IOException e) {
            throw new UncheckedIOException("the resume marker in " + profile + " could not be claimed", e);
        }
        return held;
    }

    /** Puts a claimed marker back, for a resume that was refused after it claimed. */
    public static void unclaim(Path profile) {
        try {
            if (Files.isRegularFile(profile.resolve(CLAIMED))) {
                Files.move(profile.resolve(CLAIMED), profile.resolve(FILE), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the resume marker in " + profile + " could not be put back", e);
        }
    }

    private static SuspendedSave parse(Path marker) {
        String text;
        try {
            text = Files.readString(marker, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the resume marker " + marker + " could not be read", e);
        }
        java.util.Map<String, String> held = new java.util.HashMap<>();
        for (String line : text.split("\\R")) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                held.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
            }
        }
        if (!String.valueOf(VERSION).equals(held.get(VERSION_KEY))) {
            throw new IllegalStateException("the resume marker " + marker + " is version " + held.get(VERSION_KEY)
                    + ", and this build reads version " + VERSION);
        }
        try {
            return new SuspendedSave(Path.of(required(held, "log")), Long.parseLong(required(held, "k")),
                    Integer.parseInt(required(held, "slot")), required(held, "digest"), required(held, "agent"),
                    Boolean.parseBoolean(required(held, "temporary")));
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException("the resume marker " + marker + " is not readable: " + unreadable.getMessage(),
                    unreadable);
        }
    }

    private static String required(java.util.Map<String, String> held, String key) {
        String value = held.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("it names no " + key);
        }
        return value;
    }
}
