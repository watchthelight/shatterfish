package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.brain.Brain;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.observer.Observer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first fairness evidence for the one-ply model (story 6.1), ahead of the search leak test of story
 * 6.3: two worlds that differ only in hidden state are one Observation, and the model, which is a
 * function of the Observation and the Codex, decides identically in both.
 *
 * <p>Each pair is one Run at its first wait, with an enemy standing in view beside the hero so that the
 * model has a fight to weigh, mutated in place as {@code HiddenStateDifferentialTest} does: an unseen
 * mob moved, hurt and enraged and hasted; a hidden trap and a secret door planted; the generator
 * stack's position and the seed changed. The Observation bytes must be equal (the precondition, held
 * by that suite too) and the Decision, the Policy, the Action, the reason and the ranked alternatives
 * must be equal. The control changes what the screen does show, and the bytes move.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class OnePlyLeakTest {

    private static final long SEED = 14_142_135L;
    private static final long RUN_SALT = 0x5A17_5A17L;

    private HeadlessDriver driver;
    private Level level;
    private Hero hero;
    private Brain brain;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    /** A Run at its first wait, an enemy drawn beside the hero, and the one-ply Brain on the real Codex. */
    private Mob atAFight() {
        HeadlessBoot.ensure();
        Path root = RealBrain.root();
        String tag = HeadlessBoot.pinnedTag();
        Codex.Knowledge codex = CodexKnowledge.read(root.resolve("codex").resolve(tag), tag);
        brain = new Brain(codex, RealBrain.weights(root), 9L, Brain.Model.ONE_PLY);
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        level = Dungeon.level;
        hero = Dungeon.hero;
        Mob foe = null;
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && mob.sprite != null) {
                foe = mob;
                break;
            }
        }
        assertNotNull(foe, "a mob out of view to draw in");
        int beside = -1;
        for (int offset : com.watabou.utils.PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY && Actor.findChar(cell) == null) {
                beside = cell;
                break;
            }
        }
        assertTrue(beside >= 0, "free floor beside the hero");
        move(foe, beside);
        foe.aggro(hero);
        assertTrue(level.heroFOV[foe.pos]);
        return foe;
    }

    private static void move(Mob mob, int cell) {
        mob.pos = cell;
        if (mob.sprite != null) {
            mob.sprite.place(cell);
        }
    }

    private Observation observe() {
        return new Observer().observe();
    }

    private Brain.Decided decide(Observation observation) {
        return brain.decide(observation, brain.update(observation, null));
    }

    private Mob otherUnseen(Mob not) {
        for (Mob mob : level.mobs) {
            if (mob != not && !level.heroFOV[mob.pos] && mob.sprite != null) {
                return mob;
            }
        }
        throw new AssertionError("a second mob out of view");
    }

    private int floorOutOfView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.discoverable[cell] && !level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY
                    && level.heaps.get(cell, null) == null && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no floor out of view");
    }

    /** Both worlds read, their bytes held equal, and the model's Decision in each held equal. */
    private void assertOneDecision(Observation a, Observation b, String what) {
        assertArrayEquals(ObservationCodec.encode(a), ObservationCodec.encode(b), what + ": one Observation");
        Brain.Decided first = decide(a);
        Brain.Decided second = decide(b);
        assertEquals(first.action(), second.action(), what);
        assertEquals(first.decision(), second.decision(), what + ": the whole Decision, alternatives and reasons");
    }

    @Test
    @DisplayName("worlds that differ only in hidden state: the one-ply model decides identically in each")
    void the_model_cannot_tell_the_worlds_apart() {
        Mob foe = atAFight();
        Observation before = observe();
        Brain.Decided baseline = decide(before);
        assertEquals("one-ply", baseline.decision().policy(), "the fight is the model's, so the pair is not vacuous: "
                + baseline.decision());

        // An unseen mob, elsewhere, hurt, enraged and hasted.
        Mob far = otherUnseen(foe);
        move(far, floorOutOfView());
        assertFalse(level.heroFOV[far.pos]);
        far.HP = Math.max(1, far.HP / 2);
        far.aggro(hero);
        Buff.affect(far, Haste.class, 5f);
        assertOneDecision(before, observe(), "an unseen mob's position, health, state and buffs");

        // A hidden trap and a secret door, in view.
        int trapCell = -1;
        int wall = -1;
        for (int cell = 0; cell < level.length(); cell++) {
            if (trapCell < 0 && level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null) {
                trapCell = cell;
            }
            if (wall < 0 && level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                wall = cell;
            }
        }
        Trap hidden = new FrostTrap();
        level.setTrap(hidden.hide(), trapCell);
        Level.set(trapCell, Terrain.SECRET_TRAP);
        Level.set(wall, Terrain.SECRET_DOOR);
        assertTrue(level.secret[trapCell] && level.secret[wall]);
        assertOneDecision(before, observe(), "a hidden trap and a secret door");

        // The generator stack's position and the seed.
        long seed = Dungeon.seed;
        Random.pushGenerator(0xC0FFEEL);
        try {
            for (int draw = 0; draw < 10; draw++) {
                Random.Int(1000);
            }
            Dungeon.seed = seed ^ 0x5EEDL;
            assertOneDecision(before, observe(), "the generator's state and the seed");
        } finally {
            Dungeon.seed = seed;
            Random.popGenerator();
        }
    }

    @Test
    @DisplayName("the control: what the screen does show moves the Observation, and the model's input with it")
    void what_the_screen_shows_moves_the_bytes() {
        Mob foe = atAFight();
        byte[] before = ObservationCodec.encode(observe());
        foe.HP = Math.max(1, foe.HP / 3);
        assertFalse(Arrays.equals(before, ObservationCodec.encode(observe())),
                "a drawn enemy's health bar is on the screen, so it is the model's to read");
    }
}
