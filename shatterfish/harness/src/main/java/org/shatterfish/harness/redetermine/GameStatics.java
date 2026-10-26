package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Keeps a scrub from touching the live Run through the game's statics. The scrubber asks the game's own code for fresh
 * values (a generator deck, a room queue, an item, a mob), and that code reads and writes statics: the generator's
 * decks, the room queues, the next actor id. {@link #guarded} saves them through the game's own save methods before the
 * body runs and puts them back after, with a throwaway generator on the stack while it does, so that the put-back's own
 * draws do not move the generator the live Run draws from at its next wait. A test holds the result: the saved bytes
 * before and after a scrub are equal, and the live Run's next draws are those of a twin that was never scrubbed.
 */
final class GameStatics {

    private GameStatics() {
    }

    private static final long THROWAWAY = 0x5C2AB5EED5C2AB5EL;

    static <T> T guarded(Supplier<T> body) {
        String saved = save();
        Random.pushGenerator(THROWAWAY);
        try {
            return body.get();
        } finally {
            try {
                restore(saved);
            } finally {
                Random.popGenerator();
            }
        }
    }

    private static String save() {
        Bundle bundle = new Bundle();
        Generator.storeInBundle(bundle);
        SpecialRoom.storeRoomsInBundle(bundle);
        SecretRoom.storeRoomsInBundle(bundle);
        Actor.storeNextID(bundle);
        return bundle.toString();
    }

    private static void restore(String saved) {
        try {
            Bundle bundle = Bundle.read(new ByteArrayInputStream(saved.getBytes(StandardCharsets.UTF_8)));
            Generator.restoreFromBundle(bundle);
            SpecialRoom.restoreRoomsFromBundle(bundle);
            SecretRoom.restoreRoomsFromBundle(bundle);
            Actor.restoreNextID(bundle);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The static state these calls guard, as the game would save it, for a test to compare. */
    static String fingerprint() {
        return save();
    }

    /**
     * A fresh generator deck and fresh room queues, as the game makes them at the start of a Run
     * ({@code Dungeon.init}), drawn from the generator {@code seed} and returned as the keys the game saves them under.
     */
    static JSONObject freshRunState(long seed) {
        Random.pushGenerator(seed);
        try {
            Generator.fullReset();
            SpecialRoom.initForRun();
            SecretRoom.initForRun();
            Bundle bundle = new Bundle();
            Generator.storeInBundle(bundle);
            SpecialRoom.storeRoomsInBundle(bundle);
            SecretRoom.storeRoomsInBundle(bundle);
            return new JSONObject(bundle.toString());
        } finally {
            Random.popGenerator();
        }
    }
}
