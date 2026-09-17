package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.Ratmogrify;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredStatue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GuardianTrap;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The measured combat tables (story 2.5; FR-14): every number here was produced by running the
 * engine's own method, never by writing its arithmetic out. The weapon table runs each weapon's
 * own {@code damageRoll} by level, the armour table runs the engine's own {@code Hero.drRoll}
 * with the armour worn, and the mob table runs each mob's own {@code drRoll}. Each names the
 * method it ran and cites its declaration, and each spread carries the count of samples behind
 * it, so a consumer can weigh it.
 *
 * <p>The hit table is the one this generator cannot fill. {@code Char.hit} writes the icon of the
 * reason an attack landed or missed, and reaching that code initialises {@code FloatingText},
 * whose own initialiser builds a texture film ({@code effects/FloatingText.java:75}) and so needs
 * the toolkit a generator may not have (ADR-0017). It is the same wall story 2.3 met at
 * {@code ItemSpriteSheet.Icons}, and the answer is the same: the table names the method, cites
 * it, states the grid a measurement would sweep and says plainly that it was not measured and
 * why, rather than carrying a number nobody ran or a formula written out by hand. {@link Sparring}
 * is the rig that measurement needs, kept here and exercised by the tests, so that the story
 * which runs it inside the harness (where booting is ordinary) has nothing left to invent.
 *
 * <p>Every measurement runs under {@link GameContext}: a generator seeded from the measurement
 * seed and the cell's own coordinates, a bare hero for the rolls the engine makes through a
 * wearer, and the Run's statics restored. That seed is a constant of its own and not the Codex's
 * construction seed, so a cell does not depend on the order the table was filled in, two
 * measurements agree byte for byte, and moving the construction seed moves no measured number.
 */
final class Combat {

    /** The accuracy the grid sweeps: a hero of the first chapters and a mob of the last both lie inside it. */
    static final Codex.Grid ACCURACY = new Codex.Grid("accuracy", 0, 40, 2);

    /** The evasion the grid sweeps, on the same scale. */
    static final Codex.Grid EVASION = new Codex.Grid("evasion", 0, 40, 2);

    /** How many attacks a hit cell is measured over. */
    static final int HIT_SAMPLES = 20_000;

    /** How many rolls a damage or absorption spread is measured over. */
    static final int ROLL_SAMPLES = 20_000;

    /** The levels a weapon and an armour are measured at: what a Run's first chapters hand a player. */
    static final List<Integer> LEVELS = List.of(0, 1, 2, 3, 5, 10);

    static final String CHAR = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/actors/Char.java";

    private Combat() {
    }

    /** Why the hit table holds no cell: the same wall story 2.3 met, one class further down. */
    static final String NEEDS_THE_TOOLKIT = "Char.hit writes the icon of the reason an attack landed, which initialises FloatingText, "
            + "whose initialiser builds a texture film; a generator that may not boot cannot run it";

    /** The whole table: the rolls measured, the hit table named and cited. */
    static Codex.Combat read(Path root) {
        Sources.Body chars = Sources.file(root, CHAR);
        int hit = chars.find("public static boolean hit\\( Char attacker, Char defender, float accMulti, boolean magic \\)");
        if (hit < 0) {
            throw new IllegalStateException("Char declares no hit(attacker, defender, accMulti, magic); the table no longer names what it means");
        }
        Codex.HitTable table = new Codex.HitTable("Char.hit", chars.citation(hit), ACCURACY, EVASION, false, NEEDS_THE_TOOLKIT, List.of());
        return new Codex.Combat(GameContext.MEASUREMENT_SEED, table, weapons(root), armours(root), mobs(root));
    }

    /**
     * One cell of the hit table, measured by running the engine's own {@code Char.hit} over a
     * grid cell. The generator cannot call it ({@link #NEEDS_THE_TOOLKIT}); the rig is kept and
     * exercised by {@code CombatTableStabilityTest}, which runs in a process that has booted, so
     * that the story which fills the table has the measurement written already.
     */
    static Codex.HitCell cell(int accuracy, int evasion) {
        Sparring.Fighter attacker = Sparring.attacker(accuracy);
        Sparring.Fighter defender = Sparring.defender(evasion);
        int landed = GameContext.measure(accuracy * 1_000L + evasion, hero -> {
            int hits = 0;
            for (int i = 0; i < HIT_SAMPLES; i++) {
                if (Char.hit(attacker, defender, 1f, false)) {
                    hits++;
                }
            }
            return hits;
        });
        return new Codex.HitCell(accuracy, evasion, HIT_SAMPLES, perMille(landed, HIT_SAMPLES));
    }

    /** Every melee and missile weapon of the item table, by level: the spread of its own damage roll. */
    static List<Codex.RollEntry> weapons(Path root) {
        List<Codex.RollEntry> entries = new ArrayList<>();
        Sparring.Fighter wielder = Sparring.attacker(0);
        for (Supplier<com.shatteredpixel.shatteredpixeldungeon.items.Item> make : Items.CONSTRUCTED) {
            com.shatteredpixel.shatteredpixeldungeon.items.Item item = GameContext.under(1, 0, make);
            if (!(item instanceof Weapon)) {
                continue;
            }
            // A weapon that is neither melee nor missile carries no tier field of its own (the
            // spirit bow's own formula names the tier it uses); the entry says zero rather than
            // inventing one, as the item table's strength does.
            int tier = item instanceof MeleeWeapon melee ? melee.tier : item instanceof MissileWeapon missile ? missile.tier : 0;
            Sources.Declared method = Sources.declared(root, item.getClass(),
                    Weapon.class, "public int damageRoll\\s*\\(\\s*Char owner\\s*\\)");
            if (method == null) {
                throw new IllegalStateException(item.getClass().getName() + " declares no damageRoll(Char) up to Weapon");
            }
            for (int level : LEVELS) {
                com.shatteredpixel.shatteredpixeldungeon.items.Item at = GameContext.under(1, 0, make);
                at.level(level);
                entries.add(new Codex.RollEntry(Sources.name(item.getClass()), tier, level,
                        spread(Sources.name(item.getClass()).hashCode() * 31L + level,
                                hero -> ((Weapon) at).damageRoll(wielder)),
                        "damageRoll", method.block().citation(method.line())));
            }
        }
        return entries;
    }

    /**
     * Every armour of the item table, by level: the spread of what the engine's own
     * {@code Hero.drRoll} takes off an attack while it is worn. The game has no roll of the
     * armour's own; the wearer rolls it ({@code Hero.java:644-653}), so the measurement equips a
     * bare hero with the armour and gives that hero exactly the strength the armour asks, which
     * is the number a player who can wear it sees; the encumbrance penalty below that strength is
     * the same method's other branch and is a later table's. With no weapon, no buff and no ring,
     * nothing else of that method contributes.
     */
    static List<Codex.RollEntry> armours(Path root) {
        List<Codex.RollEntry> entries = new ArrayList<>();
        Sources.Declared method = Sources.declared(root, Hero.class, Char.class, "public int drRoll\\s*\\(\\s*\\)");
        if (method == null) {
            throw new IllegalStateException("Hero declares no drRoll() up to Char");
        }
        for (Supplier<com.shatteredpixel.shatteredpixeldungeon.items.Item> make : Items.CONSTRUCTED) {
            com.shatteredpixel.shatteredpixeldungeon.items.Item item = GameContext.under(1, 0, make);
            if (!(item instanceof Armor)) {
                continue;
            }
            for (int level : LEVELS) {
                Armor at = (Armor) GameContext.under(1, 0, make);
                at.level(level);
                entries.add(new Codex.RollEntry(Sources.name(item.getClass()), at.tier, level,
                        spread(Sources.name(item.getClass()).hashCode() * 17L + level, hero -> worn(hero, at)),
                        "Hero.drRoll", method.block().citation(method.line())));
            }
        }
        return entries;
    }

    /** What the engine takes off an attack for a bare hero wearing {@code armour} with the strength it asks. */
    private static int worn(Hero hero, Armor armour) {
        Armor armourBefore = hero.belongings.armor;
        int strengthBefore = hero.STR;
        hero.belongings.armor = armour;
        hero.STR = armour.STRReq();
        try {
            return hero.drRoll();
        } finally {
            hero.belongings.armor = armourBefore;
            hero.STR = strengthBefore;
        }
    }

    /**
     * Why a mob's reduction is not measured: the game gives it what it rolls with when it spawns
     * it, so a bare instance would roll a placeholder. Four throw outright (they dereference the
     * weapon the level hands them); the rest return a number that is not the game's, which is
     * worse, because it looks like a measurement. Story 2.2 already names the same classes as the
     * ones whose stats the game sets later, and this list is held against that one, so a mob that
     * joins it there cannot be quietly measured here.
     */
    static final String EQUIPPED_AT_SPAWN = "the game gives it what it rolls with when it spawns it; a bare instance rolls a placeholder";

    /**
     * The mobs whose damage reduction a bare instance cannot roll as the game would, each with its
     * reason. The four that throw are named for what they dereference; the others are exactly
     * story 2.2's {@code STATS_SET_LATER}, and {@code CombatTableStabilityTest} holds that this
     * list is that one plus those four.
     */
    static final List<Map.Entry<Class<? extends Mob>, String>> NOT_ROLLED = notRolled();

    private static List<Map.Entry<Class<? extends Mob>, String>> notRolled() {
        List<Map.Entry<Class<? extends Mob>, String>> named = new ArrayList<>();
        named.add(Map.entry(Statue.class, "rolls with the weapon the level gives it when it spawns (Statue.java:114)"));
        named.add(Map.entry(ArmoredStatue.class, "rolls with the weapon and the armour the level gives it when it spawns"));
        named.add(Map.entry(GuardianTrap.Guardian.class, "rolls with the weapon the trap gives it when it spawns"));
        named.add(Map.entry(Ratmogrify.TransmogRat.class, "rolls as the mob it was made from, which a bare one does not have"));
        java.util.TreeMap<String, Class<?>> later = new java.util.TreeMap<>();
        for (Class<?> type : Mobs.STATS_SET_LATER) {
            later.put(Sources.name(type), type);
        }
        java.util.TreeSet<String> already = new java.util.TreeSet<>();
        named.forEach(e -> already.add(Sources.name(e.getKey())));
        for (Map.Entry<String, Class<?>> type : later.entrySet()) {
            if (already.add(type.getKey())) {
                @SuppressWarnings("unchecked")
                Class<? extends Mob> mob = (Class<? extends Mob>) type.getValue();
                named.add(Map.entry(mob, EQUIPPED_AT_SPAWN));
            }
        }
        return List.copyOf(named);
    }

    /**
     * Every mob whose reduction a bare instance rolls as the game would: the spread of its own
     * {@code drRoll} at the depth the door sets, which story 2.2 carries as the expression its
     * source states. The ones that cannot are named in {@link #NOT_ROLLED} with their reasons.
     */
    static List<Codex.RollEntry> mobs(Path root) {
        java.util.Set<String> named = new java.util.TreeSet<>();
        for (Map.Entry<Class<? extends Mob>, String> skipped : NOT_ROLLED) {
            named.add(Sources.name(skipped.getKey()));
        }
        List<Codex.RollEntry> entries = new ArrayList<>();
        for (Supplier<Mob> make : Mobs.ALL) {
            Mob mob = GameContext.under(1, 0, make);
            if (named.contains(Sources.name(mob.getClass()))) {
                continue;
            }
            Sources.Declared method = Sources.declared(root, mob.getClass(), Char.class, "public int drRoll\\s*\\(\\s*\\)");
            if (method == null) {
                throw new IllegalStateException(mob.getClass().getName() + " declares no drRoll() up to Char");
            }
            entries.add(new Codex.RollEntry(Sources.name(mob.getClass()), 0, 0,
                    spread(Sources.name(mob.getClass()).hashCode() * 13L, hero -> mob.drRoll()), "drRoll", method.block().citation(method.line())));
        }
        return entries;
    }

    /**
     * The spread of a roll over {@link #ROLL_SAMPLES} samples, under a generator of the roll's
     * own; the roll is handed the bare hero the door put in place, for the rolls the engine makes
     * through a wearer.
     */
    static Codex.Spread spread(long key, java.util.function.ToIntFunction<Hero> roll) {
        return GameContext.measure(key, hero -> {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            long total = 0;
            for (int i = 0; i < ROLL_SAMPLES; i++) {
                int value = roll.applyAsInt(hero);
                min = Math.min(min, value);
                max = Math.max(max, value);
                total += value;
            }
            return new Codex.Spread(min, max, (int) Math.round(1000.0 * total / ROLL_SAMPLES), ROLL_SAMPLES);
        });
    }

    /** {@code hits} of {@code samples} in thousandths, rounded half up, without a float reaching a table. */
    static int perMille(int hits, int samples) {
        return (int) ((2000L * hits + samples) / (2L * samples));
    }
}
