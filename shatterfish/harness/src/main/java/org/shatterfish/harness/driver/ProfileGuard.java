package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.watabou.utils.Bundle;
import org.shatterfish.harness.boot.Profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a rollout from writing to the Run's profile (story 6.2).
 *
 * <p>The profile is a player's history: the badges, the journal's catalogs, bestiary and pages, the rankings, the remains
 * a dead hero leaves. A rollout plays the game in the live Run's process, and the game writes that history as it goes:
 * a monster killed fills the bestiary, an item used fills the catalog, a death submits a ranking, a win or a badge saves
 * the badges. All of it would stay behind when the rollout ends, and some of it is read by the game while it builds
 * a floor ({@code core/.../levels/RegularLevel.java:561-589} scatters the guide pages the journal has not found yet), so
 * a rollout that found a page would change the floors the live Run generates later. Worse, what a scrubbed copy
 * identifies by use is the sample's guess and not the Run's fact, and the journal is part of what an Observation shows.
 *
 * <p>The guard takes what the profile holds before the rollout and puts it back after: the four files the game writes
 * (rankings, badges, journal, bones), and the statics those files are loaded into. The scrubbed copy is a daily Run,
 * which the game itself keeps out of the bones file ({@code core/.../Bones.java:64-66}, {@code :155-158}), so no rollout
 * leaves a grave, and the guard restores the file all the same.
 *
 * <p>It restores only through the game's public doors: {@code restore} on the catalogs, {@code deletePage} and
 * {@code disown}, and plain assignment of the rankings' public fields. A rollout only ever adds to this history, so
 * putting back is removing what it added.
 */
public final class ProfileGuard {

    private static final List<String> FILES = List.of(Rankings.RANKINGS_FILE, Badges.BADGES_FILE, "journal.dat", "bones.dat");

    private final Path directory;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Bundle journal = new Bundle();
    private final Set<Badges.Badge> badges;
    private final Map<Document, Set<String>> pages = new LinkedHashMap<>();

    private final ArrayList<Rankings.Record> records;
    private final int lastRecord;
    private final int totalNumber;
    private final int wonNumber;
    private final int localTotal;
    private final int localWon;
    private final Rankings.Record latestDaily;
    private final Rankings.Record latestDailyReplay;
    private final LinkedHashMap<Long, Integer> dailyScoreHistory;

    private ProfileGuard(Path directory) {
        this.directory = directory;
        for (String name : FILES) {
            Path file = directory.resolve(name);
            try {
                files.put(name, Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the profile's " + name, e);
            }
        }
        Catalog.store(journal);
        Bestiary.store(journal);
        Document.store(journal);
        for (Document document : Document.values()) {
            Set<String> found = new HashSet<>();
            for (String page : document.pageNames()) {
                if (document.isPageFound(page)) {
                    found.add(page);
                }
            }
            pages.put(document, found);
        }
        badges = new HashSet<>(Badges.allUnlocked());
        Rankings rankings = Rankings.INSTANCE;
        records = rankings.records == null ? null : new ArrayList<>(rankings.records);
        lastRecord = rankings.lastRecord;
        totalNumber = rankings.totalNumber;
        wonNumber = rankings.wonNumber;
        localTotal = rankings.localTotal;
        localWon = rankings.localWon;
        latestDaily = rankings.latestDaily;
        latestDailyReplay = rankings.latestDailyReplay;
        dailyScoreHistory = new LinkedHashMap<>(rankings.dailyScoreHistory);
    }

    /** Takes what {@code driver}'s profile holds now. */
    public static ProfileGuard hold(HeadlessDriver driver) {
        return new ProfileGuard(driver.profileDirectory());
    }

    /** Puts the profile back to what it held when this was taken: the files, the journal, the badges and the rankings. */
    public void restore() {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path path = directory.resolve(file.getKey());
            try {
                if (file.getValue() == null) {
                    Files.deleteIfExists(path);
                } else {
                    Files.write(path, file.getValue());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("could not put back the profile's " + file.getKey(), e);
            }
        }
        for (Map.Entry<Document, Set<String>> held : pages.entrySet()) {
            for (String page : held.getKey().pageNames()) {
                if (!held.getValue().contains(page)) {
                    held.getKey().deletePage(page);
                }
            }
        }
        // restore() overwrites only the entries a bundle names, and the held bundle names only what was seen or used:
        // the catalogs are emptied first, as the Profile empties them, so an entry the rollout saw goes back unseen.
        Profile.emptyTheCatalogs();
        Catalog.restore(journal);
        Bestiary.restore(journal);
        Document.restore(journal);
        for (Badges.Badge badge : Badges.allUnlocked()) {
            if (!badges.contains(badge)) {
                Badges.disown(badge);
            }
        }
        Rankings rankings = Rankings.INSTANCE;
        rankings.records = records == null ? null : new ArrayList<>(records);
        rankings.lastRecord = lastRecord;
        rankings.totalNumber = totalNumber;
        rankings.wonNumber = wonNumber;
        rankings.localTotal = localTotal;
        rankings.localWon = localWon;
        rankings.latestDaily = latestDaily;
        rankings.latestDailyReplay = latestDailyReplay;
        rankings.dailyScoreHistory.clear();
        rankings.dailyScoreHistory.putAll(dailyScoreHistory);
    }
}
