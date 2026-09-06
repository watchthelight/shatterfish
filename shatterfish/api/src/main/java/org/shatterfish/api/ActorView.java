package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A character the player can see, other than the hero: its cell, display name and side, its
 * health as the bar over its sprite shows it, quantised to
 * {@link ObservationCodec#healthPips(int, int)}, whether it is drawn faint for invisibility, the
 * emote its sprite shows, and every buff with an icon (ADR-0005, ADR-0006). Buffs are in name
 * order.
 */
public record ActorView(int cell, String name, Alignment alignment, int healthPips, boolean invisible, Emote emote,
                        List<BuffView> buffs) {

    public ActorView {
        Canon.cell(cell, "an actor");
        name = Canon.text(name, "name");
        Canon.require(!name.isEmpty(), "an actor has a name");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(emote, "emote");
        Canon.require(healthPips >= 0 && healthPips <= ObservationCodec.MAX_HEALTH_PIPS,
                "health is shown in 0 to " + ObservationCodec.MAX_HEALTH_PIPS + " pips: " + healthPips);
        buffs = Canon.sorted(buffs, Comparator.comparing(BuffView::name)
                .thenComparing(BuffView::timed).thenComparingInt(BuffView::turnsHundredths), "buffs");
    }
}
