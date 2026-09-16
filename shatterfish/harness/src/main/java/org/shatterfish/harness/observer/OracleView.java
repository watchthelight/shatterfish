package org.shatterfish.harness.observer;

import java.util.List;

/**
 * What the screen hides, for a person debugging a belief or a tool writing labels (FR-11;
 * ADR-0005, ADR-0006): the seed, every unknown appearance with the identity behind it, every mob
 * with its cell, health and state whether the fair read draws it or not, the hidden mimics, the
 * secret doors and the hidden traps. It is a {@code harness} type on purpose and must never become
 * an {@code api} one: a Brain reaches {@code api} only ({@code BrainBoundaryTest}), the
 * {@code Decider} takes an Observation alone, and {@code OracleGateTest} holds that nothing an
 * Observation can reach lives here. Only {@link OracleObserver} builds one, and inside the harness
 * only the launcher asks it to, in its {@code --oracle} branch and in the benchmark's tactics half
 * that runs under the same flag (story 1.21); a module built on the harness (the rig, the overlay)
 * carries its own rule when it arrives.
 *
 * <p>Every name here is the game's display string, which the game localises; the class names ride
 * beside them so a label written from this view does not depend on the language setting.
 *
 * @param seed the Run's seed ({@code …/Dungeon.java:213}), which no Observation carries (ADR-0006, Seed and turn)
 * @param identities every unknown potion, scroll and ring class of this Run, by the appearance it draws under
 * @param mobs every mob of the floor ({@code …/levels/Level.java:183}), drawn or not, by cell
 * @param hiddenMimics the cells of the mimics the map draws as chests ({@code …/actors/mobs/Mimic.java:62-64}, {@code :112-118})
 * @param secretDoors the cells the map draws as walls that are doors ({@code …/levels/Terrain.java:47}, {@code :106})
 * @param hiddenTraps the traps hidden by {@code Trap.hide()} ({@code …/levels/traps/Trap.java:63}, {@code :83-91}), which the map draws as floor
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

    /**
     * An unknown item's family, the appearance the player sees, the name behind it and its class
     * ({@code …/items/Item.java:499-505}; {@code …/items/potions/Potion.java:378-380}).
     */
    public record Identity(String family, String appearance, String trueName, String type) {
    }

    /**
     * A mob wherever it stands: its class and name, cell, health ({@code …/actors/Char.java:168-173}),
     * AI state ({@code …/actors/mobs/Mob.java:118-124}), and whether the fair read draws it as an
     * actor, which is in view and not a hidden mimic ({@code Observer.actors()}).
     */
    public record Presence(int cell, String type, String name, int hp, int ht, String state, boolean seen) {
    }

    /** A hidden trap: its cell, class and kind, and whether it is still armed ({@code Trap.java:64}). */
    public record Secret(int cell, String type, String kind, boolean active) {
    }
}
