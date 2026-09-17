package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

/**
 * The measurement rig for the combat tables (story 2.5): two characters whose only purpose is to
 * hand the engine's own {@code Char.hit} the accuracy and the evasion of a grid cell. They
 * override the two stat methods and nothing else, carry no buff, hold no item and are never put
 * on a level, so what a cell measures is the engine's comparison and its multipliers, and the
 * generator writes no combat arithmetic of its own (FR-14). {@code Char} implements
 * {@code Actor.act}, so a subclass owes nothing else.
 *
 * <p>A pair is also what a weapon's and an armour's rolls are measured through: a weapon asked to
 * roll for a wielder that is not a hero takes the bare roll, without the strength bonus a hero
 * would add ({@code MeleeWeapon.java:297-302}), which is the number this table is for.
 */
final class Sparring {

    private Sparring() {
    }

    /** A character with a fixed accuracy and evasion, alive and unencumbered, on no level. */
    static final class Fighter extends Char {

        private final int accuracy;
        private final int evasion;

        Fighter(int accuracy, int evasion) {
            this.accuracy = accuracy;
            this.evasion = evasion;
            this.alignment = Alignment.NEUTRAL;
            this.HT = 100;
            this.HP = 100;
        }

        @Override
        public int attackSkill(Char target) {
            return accuracy;
        }

        @Override
        public int defenseSkill(Char enemy) {
            return evasion;
        }
    }

    /** A fighter with the given accuracy and no evasion. */
    static Fighter attacker(int accuracy) {
        return new Fighter(accuracy, 0);
    }

    /** A fighter with the given evasion and no accuracy. */
    static Fighter defender(int evasion) {
        return new Fighter(0, evasion);
    }
}
