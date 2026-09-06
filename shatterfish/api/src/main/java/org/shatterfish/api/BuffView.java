package org.shatterfish.api;

/**
 * A buff as its icon and description show it: the name, and the turns the description prints
 * when it prints any, to the two decimals the game formats them with
 * ({@code core/.../actors/buffs/Buff.java:136-138}) and so as hundredths of a turn.
 *
 * @param timed whether the description shows turns at all
 * @param turnsHundredths the turns shown, in hundredths; zero when not timed
 */
public record BuffView(String name, boolean timed, int turnsHundredths) {

    public BuffView {
        name = Canon.text(name, "name");
        Canon.require(!name.isEmpty(), "a buff has a name");
        Canon.require(turnsHundredths >= 0, "turns are not negative: " + turnsHundredths);
        Canon.require(timed || turnsHundredths == 0, "a buff that shows no turns has none");
    }
}
