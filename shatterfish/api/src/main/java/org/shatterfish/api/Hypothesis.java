package org.shatterfish.api;

import java.util.List;

/**
 * One determinization a search asks for (story 10.24, ADR-0009, non-negotiable 4 option b): what a Brain says about the
 * hidden state of the wait it decides at, as plain draws and plain claims, never as game state. The Brain draws the
 * three numbers from its own stream and picks the mobs from its own memory of the floor; the harness's scrubber turns
 * them into a {@link BeliefSample} and writes them into a copy of the Run in which everything the player cannot know has
 * been replaced.
 *
 * @param dungeonSeed the seed the copy generates the floors the player has not visited from
 * @param rolloutSalt the salt the copy's waits draw their randomness from (a hit and a miss, a damage roll)
 * @param draws the seed of the stream every other scrubbed element is drawn from
 * @param remembered mobs the Brain believes stand on cells the player has visited and left
 */
public record Hypothesis(long dungeonSeed, long rolloutSalt, long draws, List<Remembered> remembered) {

    /**
     * A mob the Brain remembers: the class the Codex names it by (the Codex's {@code className}, without the game's root
     * package), and the cell it was last seen on.
     */
    public record Remembered(String mobClass, int cell) {

        public Remembered {
            Canon.require(mobClass != null && !mobClass.isEmpty(), "a remembered mob names its class");
            Canon.require(cell >= 0, "a remembered mob stands on a cell: " + cell);
        }
    }

    public Hypothesis {
        remembered = List.copyOf(remembered);
    }
}
