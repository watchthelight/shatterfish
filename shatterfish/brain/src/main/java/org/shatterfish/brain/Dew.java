package org.shatterfish.brain;

import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

/**
 * What the screen shows of the waterskin, and the tall grass that may feed it (issue #412).
 *
 * <p>A dewdrop is taken into the waterskin whatever the hero's health, while the skin has room, and a
 * pick-up with no room and full health is refused and spends no turn (Dewdrop.java:59-72, :122-125); the
 * skin shows its fill as {@code n/20} on its sprite (Waterskin.java:41, :47, :191-194). A tall grass cell the
 * hero tramples drops a dewdrop one time in six (HighGrass.java:148-157), halved on a level whose feeling is
 * grass, which the screen does not show. What a cell of tall grass holds is not shown, so only its terrain
 * is used here.
 */
final class Dew {

    private Dew() {
    }

    /** The waterskin's name (items.properties: {@code items.waterskin.name}). */
    static final String WATERSKIN = "waterskin";

    /** The skin's capacity (Waterskin.java:41). */
    static final int CAPACITY = 20;

    /** The volume the waterskin shows, or -1 when the pack holds none or its status shows no volume. */
    static int volume(Observation observation) {
        for (ItemView item : observation.inventory().items()) {
            if (item.name().equals(WATERSKIN)) {
                String status = item.status();
                int slash = status.indexOf('/');
                if (slash <= 0) {
                    return -1;
                }
                try {
                    return Integer.parseInt(status.substring(0, slash).trim());
                } catch (NumberFormatException shown) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** Whether a waterskin is carried that shows less than {@code below} of its {@link #CAPACITY}. */
    static boolean wants(Observation observation, int below) {
        int volume = volume(observation);
        return volume >= 0 && volume < below && volume < CAPACITY;
    }

    /** Whether {@code tile} is tall grass, which a step onto tramples (HighGrass.java:59-77). */
    static boolean tall(Tile tile) {
        return tile == Tile.HIGH_GRASS;
    }
}
