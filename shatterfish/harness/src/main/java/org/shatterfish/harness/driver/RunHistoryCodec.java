package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.watabou.utils.Bundle;
import org.shatterfish.harness.boot.Profile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the history of the game this loader holds into a {@link RunHistory}, and writes a {@link RunHistory} into it
 * (issue #390, ADR-0025). It is not shared between class loaders: each copy of the game has its own, and it touches that
 * copy's statics. The live side calls {@link #capture} (the host does, at a rollout, on the thread that plays the Run);
 * the world calls {@link #apply} before it loads the scrubbed copy, after it has taken the profile it will put back.
 *
 * <p>The journal goes through the same bundles {@link ProfileGuard} keeps and puts back ({@code Catalog.store},
 * {@code Bestiary.store}, {@code Document.store} and their {@code restore}), so what {@code apply} writes is exactly what
 * the guard's {@code restore} removes again. A badge is granted through {@link Badges#unlock}, which refuses the kinds
 * the game keeps out of seeded Runs while a seed text is set ({@code Badges.java:1209-1214}); the text is cleared around
 * the grant, as {@code Profile} clears it for the class badges, and put back.
 */
public final class RunHistoryCodec {

    private RunHistoryCodec() {
    }

    /** The history of the game in this loader, now. */
    public static RunHistory capture() {
        Bundle journal = new Bundle();
        Catalog.store(journal);
        Bestiary.store(journal);
        Document.store(journal);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Bundle.write(journal, bytes, false);

        List<String> badges = new ArrayList<>();
        for (Badges.Badge badge : Badges.allUnlocked()) {
            badges.add(badge.name());
        }
        badges.sort(null);

        boolean[] flags = new boolean[RunStatics.FLAGS];
        int[] counters = new int[RunStatics.counters()];
        RunStatics.exportTo(flags, counters);
        return new RunHistory(bytes.toByteArray(), badges, flags, counters);
    }

    /**
     * Puts {@code history} into the game in this loader, over the profile it holds. The caller has taken a
     * {@link ProfileGuard} and a {@link RunStatics.Held} first, and puts them back after the rollout.
     */
    public static void apply(RunHistory history) {
        if (history.isNone()) {
            return;
        }
        Bundle journal;
        try {
            journal = Bundle.read(new ByteArrayInputStream(history.journal()));
        } catch (IOException e) {
            throw new UncheckedIOException("the carried journal could not be read back", e);
        }
        // Over an empty journal, so that the world holds exactly the Run's and not the Run's and its own.
        Profile.emptyTheCatalogs();
        Catalog.restore(journal);
        Bestiary.restore(journal);
        Document.restore(journal);

        String seedText = Dungeon.customSeedText;
        Dungeon.customSeedText = "";
        try {
            for (String name : history.badges()) {
                Badges.unlock(Badges.Badge.valueOf(name));
            }
        } finally {
            Dungeon.customSeedText = seedText;
        }
        RunStatics.importFrom(history.flags(), history.counters());
    }
}
