package org.shatterfish.harness.observer;

import java.util.List;

/**
 * What the screen hides, for a person debugging a belief or a tool writing labels (FR-11;
 * ADR-0005, ADR-0006): the seed, every unknown appearance with the identity behind it, every mob
 * with its cell, health and state whether the hero sees it or not, the hidden mimics, the secret
 * doors and the hidden traps. It is a {@code harness} type on purpose and must never become an
 * {@code api} one: a Brain reaches {@code api} only ({@code BrainBoundaryTest}), the
 * {@code Decider} takes an Observation alone, and {@code OracleGateTest} holds that nothing an
 * Observation can reach lives here. Only {@link OracleObserver} builds one and only the launcher's
 * {@code --oracle} branch asks it to.
 *
 * @param seed the Run's seed, which no Observation carries (ADR-0006, Seed and turn)
 * @param identities every unknown potion, scroll and ring class of this Run, by the appearance it draws under
 * @param mobs every mob of the floor, seen or not, by cell
 * @param hiddenMimics the cells of the mimics the map draws as chests
 * @param secretDoors the cells the map draws as walls that are doors
 * @param hiddenTraps the traps the feature layer does not draw, by cell
 */
public record OracleView(long seed, List<Identity> identities, List<Presence> mobs, List<Integer> hiddenMimics,
                         List<Integer> secretDoors, List<Secret> hiddenTraps) {

    public OracleView {
        identities = List.copyOf(identities);
        mobs = List.copyOf(mobs);
        hiddenMimics = List.copyOf(hiddenMimics);
        secretDoors = List.copyOf(secretDoors);
        hiddenTraps = List.copyOf(hiddenTraps);
    }

    /** An unknown item's family, the appearance the player sees, and the name behind it. */
    public record Identity(String family, String appearance, String trueName) {
    }

    /** A mob wherever it stands: its cell, name, health, AI state, and whether the hero sees it. */
    public record Presence(int cell, String name, int hp, int ht, String state, boolean seen) {
    }

    /** A hidden trap: its cell and its kind. */
    public record Secret(int cell, String kind) {
    }
}
