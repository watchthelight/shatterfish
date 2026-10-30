package org.shatterfish.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The registry of the Brain's tunable numbers (story 10.35, ADR-0026).
 *
 * <p>Each entry names one number the Brain decides by, says where it lives, and declares the default,
 * the range a tuner may move it in, the step a tuner perturbs it by and one line of why it is a
 * parameter at all. The Rig reads the list ({@link #ALL}) and nothing else: it never edits a Policy,
 * it moves numbers, and the rules that use them stay readable code. Only whole numbers are
 * registered, so a value has one spelling in a Playbook file and in the hash of its canonical text.
 *
 * <p>An entry is owned by one of two places. A {@link Owner#PLAYBOOK} entry is a field of the
 * Playbook's sections ({@code identify.reach}); its value is read from the Playbook as it always was.
 * A {@link Owner#POLICY} entry was a {@code static final} constant of a Policy; the constant stays,
 * as the documented default a test holds equal to the entry, and the Policy reads the value through a
 * {@link Vector} the Playbook carries. A Vector that states nothing is the defaults, so a Brain
 * built without one plays as it did before this registry existed.
 *
 * <p>Information parity is untouched: a number here is a threshold, never a seed, a cell or a
 * hidden fact, and the vector reaches the Brain at construction as an {@code api} value, as the
 * Playbook does.
 */
public final class Tunables {

    private Tunables() {
    }

    /** Where a registered number lives. */
    public enum Owner {
        /** A field of a Playbook section, named {@code section.field} (bosses as {@code bosses.goo.field}). */
        PLAYBOOK,
        /** A Policy's constant, carried by the Playbook's {@link Vector}. */
        POLICY
    }

    /**
     * One tunable number.
     *
     * @param name   the entry's name: for a Playbook field its path in the file, for a Policy constant
     *               {@code policy.snake_case_name}
     * @param owner  where the number lives
     * @param origin a Policy constant as {@code Class.CONSTANT}, or empty for a Playbook field
     * @param def    the default: what the code decided by before the registry
     * @param min    the least a tuner may set (the default is inside the range)
     * @param max    the most a tuner may set
     * @param step   the size of one SPSA perturbation, from 1 to the width of the range
     * @param reason one line on why the number is a parameter and which way it pulls
     */
    public record Tunable(String name, Owner owner, String origin, long def, long min, long max, long step, String reason) {

        public Tunable {
            Canon.require(name.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"), "a tunable is named in lower snake case with a dot: " + name);
            java.util.Objects.requireNonNull(owner, "owner");
            Canon.require(owner == Owner.PLAYBOOK ? origin.isEmpty() : origin.matches("[A-Z][A-Za-z]*\\.[A-Z][A-Z_]*"),
                    "a Policy tunable names its constant as Class.CONSTANT, a Playbook one names none: " + name);
            Canon.require(min <= def && def <= max, "the bounds of " + name + " contain its default: " + min + " <= " + def + " <= " + max);
            Canon.require(step >= 1 && step <= max - min, "the step of " + name + " is from 1 to the width of its range: " + step);
            Canon.require(!reason.isBlank() && reason.length() <= 160 && !reason.contains("\n"), "a tunable carries a one-line reason: " + name);
        }

        /** The default as an {@code int}, for the Policy constants that are one. */
        public int defInt() {
            return Math.toIntExact(def);
        }
    }

    private static Tunable playbook(String name, long def, long min, long max, long step, String reason) {
        return new Tunable(name, Owner.PLAYBOOK, "", def, min, max, step, reason);
    }

    private static Tunable policy(String name, String origin, long def, long min, long max, long step, String reason) {
        return new Tunable(name, Owner.POLICY, origin, def, min, max, step, reason);
    }

    // ---- Playbook fields ---------------------------------------------------------------------------------

    public static final Tunable IDENTIFY_ESCAPE_WAITS = playbook("identify.escape_waits", 12, 0, 100, 4,
            "waits spent stepping out of fire or gas after a test: too few burns, too many wastes turns");
    public static final Tunable IDENTIFY_GAIN_ODDS = playbook("identify.gain_odds_per_mille", 200, 1, 1000, 50,
            "least odds of strength or experience at which an unknown potion is drunk at any health");
    public static final Tunable IDENTIFY_HEALING_ODDS = playbook("identify.healing_odds_per_mille", 200, 1, 1000, 50,
            "least odds of healing at which an unknown potion is drunk when hurt");
    public static final Tunable IDENTIFY_INVENTORY_ODDS = playbook("identify.inventory_odds_per_mille", 250, 1, 1000, 50,
            "most odds the item-picker scrolls may hold for an unknown scroll to be read");
    public static final Tunable IDENTIFY_POTION_HP = playbook("identify.potion_hp_per_mille", 500, 1, 1000, 50,
            "most share of health at which an unknown potion is drunk for its odds of healing");
    public static final Tunable IDENTIFY_REACH = playbook("identify.reach", 8, 0, 40, 4,
            "most steps walked to a better testing cell: a safer spot against the time it costs");
    public static final Tunable IDENTIFY_REST_WAITS = playbook("identify.rest_waits", 50, 0, 500, 25,
            "most waits resting toward full health after a test, before the next one");
    public static final Tunable IDENTIFY_SCROLL_HP = playbook("identify.scroll_hp_per_mille", 1000, 1, 1000, 50,
            "least share of health at which an unknown scroll is read");
    public static final Tunable UPGRADE_HOLD_UNTIL_DEPTH = playbook("upgrade.hold_until_depth", 0, 0, 26, 2,
            "depth from which a known scroll of upgrade is read: holding trades early power for a better target");
    public static final Tunable UPGRADE_WEAPON_LEAD = playbook("upgrade.weapon_lead", 0, -5, 5, 1,
            "levels the weapon may lead the armour before the armour takes the next scroll");
    public static final Tunable GOO_DESCENT_REST = playbook("bosses.goo.descent_rest_per_mille", 1000, 1, 1000, 100,
            "share of health the hero rests up to before the stairs down to Goo's floor");
    public static final Tunable GOO_DRINK = playbook("bosses.goo.drink_per_mille", 400, 1, 1000, 50,
            "share of health at or under which a healing potion is drunk against Goo alone");
    public static final Tunable GOO_MEET_RANGE = playbook("bosses.goo.meet_range", 6, 0, 12, 1,
            "most steps walked to ground where a pump could be dodged, before Goo arrives");
    public static final Tunable GOO_WASH_RANGE = playbook("bosses.goo.wash_range", 4, 0, 12, 1,
            "most steps walked to water to wash off caustic ooze");

    // ---- Policy constants --------------------------------------------------------------------------------

    public static final Tunable FIGHT_MARGIN = policy("fight.margin_per_mille", "Fight.MARGIN_PER_MILLE", 500, 100, 1500, 50,
            "share of health the damage expected while killing the target may take for a fight to be favourable");
    public static final Tunable FIGHT_FLIGHTS = policy("fight.flights", "Fight.FLIGHTS", 2, 0, 6, 1,
            "most times the hero leaves one floor by the stairs to get away from a fight");
    public static final Tunable FIGHT_OUTPACED_RANGE = policy("fight.outpaced_range", "Fight.OUTPACED_RANGE", 6, 2, 12, 1,
            "how near an awake faster enemy must be for a walk away to count as pointless");
    public static final Tunable FIGHT_OUTPACED_STAIRS = policy("fight.outpaced_stairs", "Fight.OUTPACED_STAIRS", 2, 0, 8, 1,
            "longest walk to the stairs a retreat makes with a faster enemy near");
    public static final Tunable FIGHT_CHASE_WAITS = policy("fight.chase_waits", "Fight.CHASE_WAITS", 10, 0, 40, 4,
            "waits the hero walks on toward an enemy that dropped out of view");
    public static final Tunable FIGHT_RECALL_WAITS = policy("fight.recall_waits", "Fight.RECALL_WAITS", 20, 0, 80, 8,
            "waits a sighting of an enemy now out of view still counts toward whether a fight is favourable");
    public static final Tunable DESCEND_ALLOWANCE = policy("descend.allowance", "Descend.ALLOWANCE", 500, 100, 1500, 100,
            "waits a floor is worth staying on with no guaranteed drop expected on it");
    public static final Tunable DESCEND_PER_DROP = policy("descend.per_drop", "Descend.PER_DROP", 250, 0, 800, 50,
            "waits more a floor is worth for each guaranteed drop expected still on it");
    public static final Tunable DESCEND_RESTS = policy("descend.rests", "Descend.RESTS", 20, 1, 100, 5,
            "most rests handed over on a floor before going down");
    public static final Tunable EXPLORE_RESTS = policy("explore.rests", "Explore.RESTS", 50, 1, 200, 10,
            "most waits rested for a floor fled from before going back down to it");
    public static final Tunable HEAL_REDRINK_SHARE = policy("heal.redrink_share_per_mille", "Heal.REDRINK_SHARE_PER_MILLE", 250, 50, 900, 50,
            "share of a potion's heal that must be left before the last drink counts as spent");
    public static final Tunable HEAL_HORIZON = policy("heal.horizon", "Heal.HORIZON", 10, 2, 30, 2,
            "most turns ahead the danger to the hero is added up over");
    public static final Tunable PLUNGE_SAFE_HP = policy("plunge.safe_hp", "Plunge.SAFE_HP", 15, 1, 60, 3,
            "hit points under which the hero will not jump into a chasm, whatever its share of health");
    public static final Tunable PLUNGE_FIT_PERCENT = policy("plunge.fit_percent", "Plunge.FIT_PERCENT", 80, 20, 100, 5,
            "percent of health the hero needs before it jumps into a chasm");
    public static final Tunable BRAIN_AVOID_WAITS = policy("brain.avoid_waits", "Brain.AVOID_WAITS", 40, 0, 200, 10,
            "waits a region the fight Policy retreated from stays avoided");
    public static final Tunable BRAIN_AVOID_RADIUS = policy("brain.avoid_radius", "Brain.AVOID_RADIUS", 5, 1, 12, 1,
            "widest a region the fight Policy retreated from reaches, in cells");
    public static final Tunable CHESTS_MIMIC_MARGIN = policy("chests.mimic_margin_per_mille", "Chests.MIMIC_MARGIN_PER_MILLE", 1250, 500, 3000, 150,
            "how far over the worst opening's cost the hero's health must stand before a chest is opened");

    public static final Tunable PICKUP_MISSILE_REACH = policy("pickup.missile_reach", "Pickup.MISSILE_REACH", 0, 0, 40, 4,
            "most steps walked, on a calm screen, to a missile the hero threw: 0 leaves them where they fell");
    public static final Tunable PICKUP_DEW_SKIN_BELOW = policy("pickup.dew_skin_below", "Pickup.DEW_SKIN_BELOW", 0, 0, 20, 4,
            "a dewdrop is taken at full health while the waterskin shows less than this of its 20: 0 refuses it as before");
    public static final Tunable RANGED_FETCH_STEPS = policy("ranged.fetch_steps", "Ranged.FETCH_STEPS", 0, 0, 4, 1,
            "most steps walked in a fight, no enemy beside the hero, to a missile it threw: 0 never leaves a fight for one");
    public static final Tunable RANGED_MIN_DAMAGE = policy("ranged.min_damage_per_mille", "Ranged.MIN_DAMAGE_PER_MILLE", 0, 0, 6000, 250,
            "least damage a throw is expected to do, in thousandths of a hit point, for it to be worth the missile: 0 throws as before");
    public static final Tunable DESCEND_TRAMPLE_REACH = policy("descend.trample_reach", "Descend.TRAMPLE_REACH", 0, 0, 30, 2,
            "most steps walked on a spent floor to tall grass that may drop dew while the waterskin is not full: 0 never");
    public static final Tunable EXPLORE_GRASS_SKIN_BELOW = policy("explore.grass_skin_below", "Explore.GRASS_SKIN_BELOW", 0, 0, 20, 4,
            "equally short ways break toward tall grass while the waterskin shows less than this of its 20: 0 never");

    /** Every registered tunable, once, sorted by name (the order a tuner and a Playbook file list them). */
    public static final List<Tunable> ALL = all();

    private static List<Tunable> all() {
        List<Tunable> found = new ArrayList<>(List.of(
                IDENTIFY_ESCAPE_WAITS,
                IDENTIFY_GAIN_ODDS,
                IDENTIFY_HEALING_ODDS,
                IDENTIFY_INVENTORY_ODDS,
                IDENTIFY_POTION_HP,
                IDENTIFY_REACH,
                IDENTIFY_REST_WAITS,
                IDENTIFY_SCROLL_HP,
                UPGRADE_HOLD_UNTIL_DEPTH,
                UPGRADE_WEAPON_LEAD,
                GOO_DESCENT_REST,
                GOO_DRINK,
                GOO_MEET_RANGE,
                GOO_WASH_RANGE,
                FIGHT_MARGIN,
                FIGHT_FLIGHTS,
                FIGHT_OUTPACED_RANGE,
                FIGHT_OUTPACED_STAIRS,
                FIGHT_CHASE_WAITS,
                FIGHT_RECALL_WAITS,
                DESCEND_ALLOWANCE,
                DESCEND_PER_DROP,
                DESCEND_RESTS,
                EXPLORE_RESTS,
                HEAL_REDRINK_SHARE,
                HEAL_HORIZON,
                PLUNGE_SAFE_HP,
                PLUNGE_FIT_PERCENT,
                BRAIN_AVOID_WAITS,
                BRAIN_AVOID_RADIUS,
                CHESTS_MIMIC_MARGIN,
                PICKUP_MISSILE_REACH,
                PICKUP_DEW_SKIN_BELOW,
                RANGED_FETCH_STEPS,
                RANGED_MIN_DAMAGE,
                DESCEND_TRAMPLE_REACH,
                EXPLORE_GRASS_SKIN_BELOW));
        found.sort(java.util.Comparator.comparing(Tunable::name));
        for (int i = 1; i < found.size(); i++) {
            Canon.require(!found.get(i).name().equals(found.get(i - 1).name()), "a tunable is registered once: " + found.get(i).name());
        }
        return List.copyOf(found);
    }

    /** The tunable named {@code name}, or null when none is registered. */
    public static Tunable named(String name) {
        for (Tunable tunable : ALL) {
            if (tunable.name().equals(name)) {
                return tunable;
            }
        }
        return null;
    }

    /**
     * The values of the {@link Owner#POLICY} tunables a Playbook states. Absent means the default, and a
     * value equal to the default is dropped, so a Playbook that states a default and one that does not
     * are one Playbook with one hash.
     *
     * @param values the stated values by name, each registered, owned by a Policy and inside its bounds
     */
    public record Vector(Map<String, Long> values) {

        /** The vector that states nothing: every Policy decides by its default. */
        public static final Vector REFERENCE = new Vector(Map.of());

        public Vector {
            TreeMap<String, Long> kept = new TreeMap<>();
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                Tunable tunable = named(entry.getKey());
                Canon.require(tunable != null, "the tunable " + entry.getKey() + " is not registered");
                Canon.require(tunable.owner() == Owner.POLICY,
                        entry.getKey() + " is a Playbook field; state it in its section, not in tunables");
                long value = entry.getValue();
                Canon.require(value >= tunable.min() && value <= tunable.max(),
                        entry.getKey() + " is from " + tunable.min() + " to " + tunable.max() + ": " + value);
                if (value != tunable.def()) {
                    kept.put(entry.getKey(), value);
                }
            }
            values = Collections.unmodifiableMap(kept);
        }

        /** The value of {@code tunable}: the one stated, else its default. */
        public long get(Tunable tunable) {
            Canon.require(tunable.owner() == Owner.POLICY, tunable.name() + " is read from the Playbook's section");
            Long stated = values.get(tunable.name());
            return stated == null ? tunable.def() : stated;
        }

        /** {@link #get} as an {@code int}. */
        public int getInt(Tunable tunable) {
            return Math.toIntExact(get(tunable));
        }

        /** This vector with {@code given} stated over it. */
        public Vector with(Map<String, Long> given) {
            TreeMap<String, Long> merged = new TreeMap<>(values);
            merged.putAll(given);
            return new Vector(merged);
        }

        /** Whether the vector states nothing. */
        public boolean isReference() {
            return values.isEmpty();
        }
    }

    /**
     * The value of {@code tunable} in {@code playbook}, as the base Playbook states it (a hero class's
     * own section may state another; {@link #with} moves every class together).
     */
    public static long value(Playbook playbook, Tunable tunable) {
        if (tunable.owner() == Owner.POLICY) {
            return playbook.tunables().get(tunable);
        }
        return ((Number) section(playbook, tunable)).longValue();
    }

    private static Object section(Playbook playbook, Tunable tunable) {
        String name = tunable.name();
        int cut = name.lastIndexOf('.');
        String key = name.substring(cut + 1);
        return switch (name.substring(0, cut)) {
            case "identify" -> playbook.identify().values().get(key);
            case "upgrade" -> playbook.upgrade().values().get(key);
            case "bosses.goo" -> playbook.bosses().goo().values().get(key);
            default -> throw new IllegalArgumentException("no section holds " + name);
        };
    }

    /**
     * {@code playbook} with the vector {@code given} played over it, for the Rig: a Playbook field is
     * moved in the base section and in every class entry that states the section (a class entry is
     * stored resolved, so it would otherwise keep the old number), and a Policy constant goes into the
     * Playbook's {@link Vector}. Every name must be registered and every value inside its bounds. The
     * result's canonical text, and so the Brain's configuration hash, names every number that moved.
     */
    public static Playbook with(Playbook playbook, Map<String, Long> given) {
        Map<String, Long> policies = new TreeMap<>();
        Map<String, Object> identifyGiven = new TreeMap<>();
        Map<String, Object> upgradeGiven = new TreeMap<>();
        Map<String, Object> gooGiven = new TreeMap<>();
        for (Map.Entry<String, Long> entry : given.entrySet()) {
            Tunable tunable = named(entry.getKey());
            Canon.require(tunable != null, "the tunable " + entry.getKey() + " is not registered");
            long value = entry.getValue();
            Canon.require(value >= tunable.min() && value <= tunable.max(),
                    entry.getKey() + " is from " + tunable.min() + " to " + tunable.max() + ": " + value);
            if (tunable.owner() == Owner.POLICY) {
                policies.put(tunable.name(), value);
                continue;
            }
            String name = tunable.name();
            int cut = name.lastIndexOf('.');
            Map<String, Object> into = switch (name.substring(0, cut)) {
                case "identify" -> identifyGiven;
                case "upgrade" -> upgradeGiven;
                default -> gooGiven;
            };
            into.put(name.substring(cut + 1), value);
        }
        List<Playbook.ClassEntry> classes = new ArrayList<>();
        for (Playbook.ClassEntry entry : playbook.classes()) {
            classes.add(new Playbook.ClassEntry(entry.heroClass(), entry.identify().with(identifyGiven), entry.kit(),
                    entry.upgrade().with(upgradeGiven)));
        }
        Playbook.Bosses bosses = new Playbook.Bosses(playbook.bosses().goo().with(gooGiven), playbook.bosses().tengu(),
                playbook.bosses().dm300());
        return new Playbook(playbook.name(), playbook.version(), playbook.identify().with(identifyGiven),
                playbook.upgrade().with(upgradeGiven), playbook.kit(), classes, bosses, playbook.tunables().with(policies));
    }
}
