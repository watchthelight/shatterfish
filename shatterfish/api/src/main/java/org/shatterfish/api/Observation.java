package org.shatterfish.api;

import java.util.Map;
import java.util.Objects;

/**
 * What the brain sees: an immutable record tree, section by section, of what the screen, the
 * HUD, the log and the journal show at one Input wait (ADR-0005, AD-2). Story 1.6 gives it the
 * header, the map and the actors; story 1.7 adds the hero, the inventory, the journal, the log,
 * the valid Actions and the prompt.
 *
 * <p>Equality is structural, as for every record, and every list is in the one order its record
 * fixes, so two Observations of the same screen are equal and have equal bytes and hashes
 * whatever order their parts were collected in; a test holds {@code equals} and {@link #hash()}
 * to each other over a corpus. Every actor stands on a cell the player sees.
 */
public record Observation(HeaderSection header, MapSection map, ActorsSection actors) {

    public Observation {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(actors, "actors");
        for (ActorView actor : actors.actors()) {
            Canon.require(actor.cell() < map.cells(), "an actor is off the map at cell " + actor.cell());
            Canon.require(map.fog().get(actor.cell()) == Fog.VISIBLE,
                    "an actor stands on cell " + actor.cell() + ", which is " + map.fog().get(actor.cell())
                            + ": a character is drawn only in view (ADR-0006)");
        }
    }

    /** The Observation's hash: SHA-256 over the schema version and the section hashes, in hex. */
    public String hash() {
        return ObservationCodec.hash(this);
    }

    /** The hash of each section's canonical bytes, in section order, for the differential test. */
    public Map<String, String> sectionHashes() {
        return ObservationCodec.sectionHashes(this);
    }
}
