package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.shatterfish.harness.redetermine.ScrubRow;

import java.util.EnumSet;
import java.util.Set;

/**
 * The hidden elements the search leak test names (story 6.3, FR-13), each with the rows of the scrub table that replace
 * it and the one-line read of the live game a planted leak makes. A search that reads {@link #peek()} has used a thing
 * the player cannot know; nothing in the main source set can do what this does, because it reads game state and sits
 * in the test source set beside {@code ProbeSearch}'s controls.
 */
enum HiddenPeek {

    /** The dungeon's true seed ({@code Dungeon.seed}), which seeds every floor not yet generated. */
    TRUE_SEED("the true seed", EnumSet.of(ScrubRow.DUNGEON_SEED)) {
        @Override
        long peek() {
            return Math.floorMod(Dungeon.seed, 997L) + 1;
        }
    },

    /** Where the mobs the hero cannot see stand. */
    UNSEEN_MOB_POSITIONS("unseen mob positions", EnumSet.of(ScrubRow.MOBS_OUT_OF_VIEW)) {
        @Override
        long peek() {
            long sum = 1;
            Level level = Dungeon.level;
            for (Mob mob : level.mobs) {
                if (!level.heroFOV[mob.pos]) {
                    sum += mob.pos;
                }
            }
            return sum;
        }
    },

    /** Which potion, scroll and ring class wears which label: the images a class draws under. */
    UNIDENTIFIED_ITEM_CLASSES("unidentified item classes", EnumSet.of(ScrubRow.POTION_SCROLL_RING_CLASSES)) {
        @Override
        long peek() {
            long sum = 1;
            // Building an item may draw from the generator on top of the stack; the pushed one is thrown away.
            Random.pushGenerator(1L);
            try {
                int weight = 1;
                for (Generator.Category category : new Generator.Category[] {Generator.Category.POTION,
                        Generator.Category.SCROLL, Generator.Category.RING}) {
                    for (Class<?> type : category.classes) {
                        sum += weight++ * (long) ((Item) Reflection.newInstance(type)).image();
                    }
                }
            } finally {
                Random.popGenerator();
            }
            return sum;
        }
    },

    /** The traps the hero has not found. */
    HIDDEN_TRAPS("hidden traps", EnumSet.of(ScrubRow.SECRET_TRAPS_AND_DOORS)) {
        @Override
        long peek() {
            long count = 1;
            Level level = Dungeon.level;
            for (int cell = 0; cell < level.length(); cell++) {
                Trap trap = level.traps.get(cell);
                if (trap != null && !trap.visible || level.map[cell] == Terrain.SECRET_TRAP) {
                    count += 13;
                }
            }
            return count;
        }
    },

    /** The doors the hero has not found. */
    SECRET_DOORS("secret doors", EnumSet.of(ScrubRow.SECRET_TRAPS_AND_DOORS)) {
        @Override
        long peek() {
            long count = 1;
            Level level = Dungeon.level;
            for (int cell = 0; cell < level.length(); cell++) {
                if (level.map[cell] == Terrain.SECRET_DOOR) {
                    count += 11;
                }
            }
            return count;
        }
    };

    private final String what;
    private final Set<ScrubRow> rows;

    HiddenPeek(String what, Set<ScrubRow> rows) {
        this.what = what;
        this.rows = rows;
    }

    /** The element as the case names it in its failure. */
    String what() {
        return what;
    }

    /** The rows of the scrub table that replace the element, and no others. */
    Set<ScrubRow> rows() {
        return EnumSet.copyOf(rows);
    }

    /** A non-zero number that is a function of the element in the game as it stands. */
    abstract long peek();
}
