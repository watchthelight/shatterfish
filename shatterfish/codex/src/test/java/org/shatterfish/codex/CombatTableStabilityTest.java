package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat tables are measurements, and a measurement is only worth what its reproducibility
 * and its bounds are (story 2.5; FR-14). This holds: that two measurements of the same table in
 * one process are identical, whatever the Codex's seed is moved to between them, since every cell
 * seeds its own generator from its own coordinates; that every measured spread lies inside the
 * bounds the engine's own methods state for that item at that level, which is what makes the
 * numbers checkable without writing the arithmetic out; that the rolls cover every weapon, armour
 * and mob, the four mobs the game equips at spawn named with their reasons; and that the rig the
 * hit table needs works, by running the engine's own {@code Char.hit} here, where the process has
 * booted and the toolkit the generator may not have is present.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class CombatTableStabilityTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;

    @Test
    @DisplayName("two measurements of the table in one process are identical, whatever the Codex's seed is moved to between them")
    void the_measurement_is_reproducible() {
        long before = GameContext.generatorSeed;
        try {
            Codex.Combat first = Combat.read(ROOT);
            GameContext.generatorSeed = before + 977L;
            Codex.Combat moved = Combat.read(ROOT);
            GameContext.generatorSeed = before;
            Codex.Combat again = Combat.read(ROOT);
            assertEquals(org.shatterfish.api.CodexJson.combat(first), org.shatterfish.api.CodexJson.combat(again),
                    "a measurement repeated is the same measurement");
            assertEquals(org.shatterfish.api.CodexJson.combat(first), org.shatterfish.api.CodexJson.combat(moved),
                    "moving the Codex's construction seed moves no measured number: a measurement draws under its own");
            assertEquals(GameContext.MEASUREMENT_SEED, first.seed(), "the table says which seed it was measured under");
        } finally {
            GameContext.generatorSeed = before;
        }
    }

    @Test
    @DisplayName("every measured spread lies inside the bounds the engine's own methods state for that item at that level")
    void the_spreads_are_inside_the_engines_bounds() {
        Codex.Combat combat = Combat.read(ROOT);
        Map<String, Supplier<Item>> items = new TreeMap<>();
        for (Supplier<Item> make : Items.CONSTRUCTED) {
            items.put(Sources.name(GameContext.under(1, 0, make).getClass()), make);
        }
        int weapons = 0;
        for (Codex.RollEntry entry : combat.weapons()) {
            Supplier<Item> make = items.get(entry.className());
            assertTrue(make != null, entry.className() + " is a weapon of the item table");
            Weapon weapon = (Weapon) GameContext.under(1, 0, make);
            weapon.level(entry.level());
            // The bounds a weapon states for itself, where it states them by level: the two
            // families do, and a weapon that is neither (the spirit bow) is held by the
            // re-measurement below instead.
            if (weapon instanceof MeleeWeapon || weapon instanceof MissileWeapon) {
                int min = weapon instanceof MeleeWeapon melee ? melee.min(entry.level()) : ((MissileWeapon) weapon).min(entry.level());
                int max = weapon instanceof MeleeWeapon melee ? melee.max(entry.level()) : ((MissileWeapon) weapon).max(entry.level());
                assertTrue(entry.spread().min() >= min,
                        entry.className() + " +" + entry.level() + " rolled " + entry.spread().min() + ", under its own min " + min);
                assertTrue(entry.spread().max() <= max,
                        entry.className() + " +" + entry.level() + " rolled " + entry.spread().max() + ", over its own max " + max);
            }
            assertEquals(Combat.ROLL_SAMPLES, entry.spread().samples());
            weapons++;
        }
        assertTrue(weapons >= 100, "the weapons are measured: " + weapons);
        for (Codex.RollEntry entry : combat.armours()) {
            Armor armour = (Armor) GameContext.under(1, 0, items.get(entry.className()));
            armour.level(entry.level());
            assertTrue(entry.spread().min() >= armour.DRMin(), entry.className() + " +" + entry.level() + " absorbed under its own minimum");
            assertTrue(entry.spread().max() <= armour.DRMax(), entry.className() + " +" + entry.level() + " absorbed over its own maximum");
        }
        for (Codex.RollEntry entry : combat.mobs()) {
            assertEquals(0, entry.tier(), "a mob's reduction has no tier");
            assertEquals(0, entry.level(), "a mob's reduction has no level");
            assertEquals("drRoll", entry.method());
            assertTrue(entry.spread().min() >= 0,
                    entry.className() + " reduced a negative amount, which no mob the game spawns does: " + entry.spread());
        }
        // The mean is the measurement's, not the midpoint of the bounds a reader could have
        // guessed: every weapon's row at level zero is re-measured here against the engine, and
        // some measured mean is not that midpoint, so a table that guessed would be seen.
        int asymmetric = 0;
        for (Codex.RollEntry entry : combat.weapons()) {
            if (entry.level() != 0) {
                continue;
            }
            Weapon weapon = (Weapon) GameContext.under(1, 0, items.get(entry.className()));
            Codex.Spread again = Combat.spread(entry.className().hashCode() * 31L + entry.level(),
                    hero -> weapon.damageRoll(Sparring.attacker(0)));
            assertEquals(again.meanPerMille(), entry.spread().meanPerMille(), entry.className() + " measures the same mean twice");
            assertEquals(again.min(), entry.spread().min(), entry.className() + " measures the same minimum twice");
            assertEquals(again.max(), entry.spread().max(), entry.className() + " measures the same maximum twice");
            if (entry.spread().meanPerMille() != (entry.spread().min() + entry.spread().max()) * 500) {
                asymmetric++;
            }
        }
        assertTrue(asymmetric > 0, "a measured mean is not the midpoint of its bounds, so the table is a measurement and not a guess");
    }

    @Test
    @DisplayName("the rolls cover every weapon, armour and mob, and the mobs the game equips at spawn are named with their reasons")
    void the_rolls_cover_the_tables() {
        Codex.Combat combat = Combat.read(ROOT);
        TreeSet<String> measuredWeapons = new TreeSet<>();
        combat.weapons().forEach(e -> measuredWeapons.add(e.className()));
        TreeSet<String> measuredArmours = new TreeSet<>();
        combat.armours().forEach(e -> measuredArmours.add(e.className()));
        TreeSet<String> constructed = new TreeSet<>();
        for (Supplier<Item> make : Items.CONSTRUCTED) {
            constructed.add(Sources.name(GameContext.under(1, 0, make).getClass()));
        }
        // The expectation is the game's own hierarchy, read from the compiled classes, so that
        // the reader's predicate and the test's cannot be wrong together: a weapon is whatever
        // the game calls a Weapon. The spirit bow was missing from the table while a test built
        // from the reader's own instanceof reported full coverage.
        TreeSet<String> weapons = new TreeSet<>();
        TreeSet<String> armours = new TreeSet<>();
        for (JavaClass c : new ClassFileImporter().importPackages(Sources.GAME)) {
            if (c.getModifiers().contains(JavaModifier.ABSTRACT) || c.isInterface() || c.isAnonymousClass() || c.isLocalClass()) {
                continue;
            }
            String name = c.getName().substring(Sources.ROOT_PACKAGE_PREFIX.length()).replace('$', '.');
            if (!constructed.contains(name)) {
                continue;
            }
            if (c.isAssignableTo(Weapon.class)) {
                weapons.add(name);
            } else if (c.isAssignableTo(Armor.class)) {
                armours.add(name);
            }
        }
        assertEquals(weapons, measuredWeapons, "every weapon the item table constructs is measured");
        assertEquals(armours, measuredArmours, "every armour the item table constructs is measured");
        assertTrue(measuredWeapons.contains("items.weapon.SpiritBow"),
                "the spirit bow is a weapon that is neither melee nor missile, and it is measured");
        for (Codex.RollEntry entry : combat.weapons()) {
            assertTrue(Combat.LEVELS.contains(entry.level()), entry.className() + " was measured at a level the grid names");
        }
        TreeSet<String> mobs = new TreeSet<>();
        for (Supplier<Mob> make : Mobs.ALL) {
            mobs.add(Sources.name(GameContext.under(1, 0, make).getClass()));
        }
        TreeSet<String> covered = new TreeSet<>();
        combat.mobs().forEach(e -> covered.add(e.className()));
        TreeSet<String> named = new TreeSet<>();
        for (Map.Entry<Class<? extends Mob>, String> skipped : Combat.NOT_ROLLED) {
            String name = Sources.name(skipped.getKey());
            assertFalse(covered.contains(name), name + " is named as unrollable and measured anyway");
            assertFalse(skipped.getValue().isBlank(), name + " is named with a reason");
            assertTrue(named.add(name), name + " is named twice");
            assertTrue(covered.add(name));
        }
        assertEquals(mobs, covered, "every mob is measured or named with the reason it cannot be");
        // The named list is story 2.2's own: a mob whose stats the game sets when it spawns it
        // cannot be rolled bare, so one added to that list cannot be quietly measured here.
        TreeSet<String> later = new TreeSet<>();
        for (Class<?> type : Mobs.STATS_SET_LATER) {
            later.add(Sources.name(type));
        }
        TreeSet<String> missing = new TreeSet<>(later);
        missing.removeAll(named);
        assertEquals(new TreeSet<>(), missing, "every mob whose stats the game sets later is named here");
        TreeSet<String> beyond = new TreeSet<>(named);
        beyond.removeAll(later);
        assertEquals(java.util.Set.of("actors.mobs.Statue", "actors.mobs.ArmoredStatue"), beyond,
                "the two statues are named beyond that list, since the level arms them rather than setting their stats");
        assertTrue(named.contains("items.wands.WandOfLivingEarth.EarthGuardian"),
                "the earth guardian rolls from a wand level of minus one until the game sets it, so it is named, not measured");
    }

    @Test
    @DisplayName("the hit table names the method it could not run and why, and the rig measures it here, where the process has booted")
    void the_hit_rig_measures_the_engine() {
        Codex.Combat combat = Combat.read(ROOT);
        assertFalse(combat.hit().measured(), "a generator that may not boot cannot run Char.hit");
        assertTrue(combat.hit().cells().isEmpty());
        assertTrue(combat.hit().reason().contains("FloatingText"), combat.hit().reason());
        assertEquals("Char.hit", combat.hit().method());
        assertEquals(0, combat.hit().accuracy().from());
        assertEquals(40, combat.hit().accuracy().to());
        assertEquals(21, combat.hit().accuracy().size());
        // The rig itself, run against the engine in this process, which has the toolkit.
        HeadlessBoot.ensure();
        Codex.HitCell certain = Combat.cell(20, 0);
        assertEquals(1000, certain.hitPerMille(), "a defender with no evasion is always hit");
        Codex.HitCell hopeless = Combat.cell(0, 20);
        assertEquals(0, hopeless.hitPerMille(), "an attacker with no accuracy never lands");
        Codex.HitCell even = Combat.cell(20, 20);
        assertTrue(Math.abs(even.hitPerMille() - 500) < 30, "even stats land about half the time: " + even.hitPerMille());
        Codex.HitCell favoured = Combat.cell(40, 20);
        assertTrue(favoured.hitPerMille() > even.hitPerMille(), "more accuracy lands more often");
        Codex.HitCell pressed = Combat.cell(20, 40);
        assertTrue(pressed.hitPerMille() < even.hitPerMille(), "more evasion lands less often");
        assertEquals(Combat.HIT_SAMPLES, even.samples());
        assertEquals(even.hitPerMille(), Combat.cell(20, 20).hitPerMille(), "a cell measures the same twice, under its own seed");
    }

    @Test
    @DisplayName("a share is thousandths rounded half up, and no float reaches a table")
    void shares_are_whole() {
        assertEquals(500, Combat.perMille(1, 2));
        assertEquals(1000, Combat.perMille(7, 7));
        assertEquals(0, Combat.perMille(0, 7));
        assertEquals(333, Combat.perMille(1, 3));
        assertEquals(667, Combat.perMille(2, 3));
        assertEquals(143, Combat.perMille(1, 7));
        String text = org.shatterfish.api.CodexJson.combat(Combat.read(ROOT));
        // Class names and paths carry dots; a number must not.
        assertFalse(java.util.regex.Pattern.compile(":\\s*-?\\d+\\.\\d").matcher(text).find(), "no float reaches the table");
        assertTrue(text.endsWith("\n") && !text.contains("\r"));
        assertEquals(List.of(0, 1, 2, 3, 5, 10), Combat.LEVELS);
    }
}
