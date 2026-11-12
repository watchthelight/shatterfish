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
    @org.shatterfish.api.Heuristic("dew-skin")
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

    /**
     * What a drink of the skin heals now, in hit points: it takes the drops that bring the hero to full health, at least
     * one and no more than the skin holds, and each drop heals 5% of the maximum, the whole rounded
     * (Waterskin.java:87-134, Dewdrop.java:83-84). 0 with no skin, an empty one, or a hero at full health. The Vial of Blood
     * and the Shielding Dew talent change the drops taken and are not modelled; they only make the real drink larger.
     */
    static int gain(Observation observation) {
        int volume = volume(observation);
        int ht = observation.hero().ht();
        int missing = ht - observation.hero().hp();
        if (volume <= 0 || missing <= 0) {
            return 0;
        }
        int drops = (int) Math.max(1, Math.min(volume, Math.ceil(missing / (0.05f * ht) - 0.01f)));
        return Math.min(missing, Math.round(ht * 0.05f * drops));
    }

    /** The action that drinks the skin, when the screen offers it, or null. */
    static org.shatterfish.api.Action drink(Observation observation, java.util.List<org.shatterfish.api.Action> offered) {
        java.util.List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.name().equals(WATERSKIN)) {
                org.shatterfish.api.Action drink = new org.shatterfish.api.Action.UseItem(
                        new org.shatterfish.api.ItemRef(index, item.name(), item.quantity()), "DRINK");
                return offered.contains(drink) ? drink : null;
            }
        }
        return null;
    }

    /** Whether {@code tile} is tall grass, which a step onto tramples (HighGrass.java:59-77). */
    static boolean tall(Tile tile) {
        return tile == Tile.HIGH_GRASS;
    }
}
