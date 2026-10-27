package org.shatterfish.api;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Brain's strategy thresholds and choices, as data (story 7.2, FR-35).
 *
 * <p>A committed file under {@code playbooks/} holds them; the Rig reads and validates it and hands
 * this value to the Brain at construction, because a Brain cannot open a file. It sits beside the
 * {@link Weights} and not inside them (ADR-0023): the weights are one number per feature of a sum
 * the Evaluation takes, and a Playbook is a set of named thresholds, odds, budgets and choices that
 * Policies decide by. The Rig hashes the Playbook into the Brain's configuration, so a Run log's header
 * carries its name and its content, and a Replay plays the same one.
 *
 * <p>A Playbook holds typed numbers and enum values and nothing else: no free text the Brain would
 * interpret, no seed, no cell, no layout. Each field declares the range it may take
 * ({@link Spec}); the record refuses a value outside it, and the Rig's {@code PlaybookValidator}
 * reads the same declarations to refuse a file with a message that names the field.
 *
 * <p>A section a file leaves out means the section's {@code REFERENCE}, the values the first Playbook
 * (story 7.2) was written to hold, which are what the Policies decided by before the values moved
 * here. A hero class may override fields of the sections; a field it does not state is the base
 * Playbook's. The class entries are stored resolved, so reading a value never needs the base.
 *
 * <p>The sections are {@code identify} (which unknown potions and scrolls are tried, and where),
 * {@code upgrade} (where a scroll of upgrade goes), {@code bosses} (one entry per boss: how its
 * fight is approached and how fit the hero is sent down to it, story 7.5) and {@code classes}.
 *
 * @param name     the Playbook's name, which is the Brain's name
 * @param version  the Playbook's version, bumped when a value changes meaning or value
 * @param identify the base {@code identify} section
 * @param upgrade  the base {@code upgrade} section
 * @param classes  per hero class, the sections as that class plays them, sorted by class, each once
 * @param bosses   the {@code bosses} section, one entry per boss (story 7.5)
 */
public record Playbook(String name, int version, Identify identify, Upgrade upgrade, List<ClassEntry> classes, Bosses bosses) {

    /** The version of the file's shape, which the reader checks. */
    public static final int FORMAT = 1;

    public Playbook {
        name = Canon.text(name, "playbook name");
        Canon.require(name.matches("[a-z][a-z0-9_]*"), "a Playbook is named as a Brain is: " + name);
        Canon.require(version >= 1, "a Playbook's version starts at 1: " + version);
        java.util.Objects.requireNonNull(identify, "identify");
        java.util.Objects.requireNonNull(upgrade, "upgrade");
        java.util.Objects.requireNonNull(bosses, "bosses");
        classes = Canon.sorted(classes, Comparator.comparing(ClassEntry::heroClass), "classes");
        for (int i = 1; i < classes.size(); i++) {
            Canon.require(classes.get(i).heroClass() != classes.get(i - 1).heroClass(),
                    "a hero class is stated once: " + classes.get(i).heroClass());
        }
    }

    /** A Playbook whose {@code bosses} section is the reference one: the shape the file had before story 7.5. */
    public Playbook(String name, int version, Identify identify, Upgrade upgrade, List<ClassEntry> classes) {
        this(name, version, identify, upgrade, classes, Bosses.REFERENCE);
    }

    /** The Playbook of the values the Policies decided by before they were data: the file's defaults. */
    public static Playbook reference(String name) {
        return new Playbook(name, 1, Identify.REFERENCE, Upgrade.REFERENCE, List.of(), Bosses.REFERENCE);
    }

    /** The {@code identify} section as {@code heroClass} plays it. */
    public Identify identify(HeroClass heroClass) {
        for (ClassEntry entry : classes) {
            if (entry.heroClass() == heroClass) {
                return entry.identify();
            }
        }
        return identify;
    }

    /** The {@code upgrade} section as {@code heroClass} plays it. */
    public Upgrade upgrade(HeroClass heroClass) {
        for (ClassEntry entry : classes) {
            if (entry.heroClass() == heroClass) {
                return entry.upgrade();
            }
        }
        return upgrade;
    }

    /** The canonical text, which the Rig hashes into the Brain's configuration: sorted keys, every section whole. */
    public String canonical() {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("bosses").beginObject();
        section(out, "dm300", bosses.dm300().values());
        section(out, "goo", bosses.goo().values());
        section(out, "tengu", bosses.tengu().values());
        out.endObject();
        out.key("classes").beginObject();
        for (ClassEntry entry : classes) {
            out.key(entry.heroClass().name()).beginObject();
            section(out, "identify", entry.identify().values());
            section(out, "upgrade", entry.upgrade().values());
            out.endObject();
        }
        out.endObject();
        out.key("format").value(FORMAT);
        section(out, "identify", identify.values());
        out.key("name").value(name);
        section(out, "upgrade", upgrade.values());
        out.key("version").value(version);
        out.endObject();
        return out.toJson();
    }

    private static void section(JsonWriter out, String key, Map<String, Object> values) {
        out.key(key).beginObject();
        for (Map.Entry<String, Object> value : values.entrySet()) {
            out.key(value.getKey());
            if (value.getValue() instanceof Long number) {
                out.value(number.longValue());
            } else {
                out.value(value.getValue().toString());
            }
        }
        out.endObject();
    }

    /**
     * What a field may hold: a whole number from {@code min} to {@code max}, or one of the names in
     * {@code allowed}.
     *
     * @param key     the field's name in the file, lower snake case
     * @param min     the least number, for a numeric field
     * @param max     the most number, for a numeric field
     * @param allowed the enum names a field may hold, empty for a numeric field
     */
    public record Spec(String key, long min, long max, List<String> allowed) {

        public Spec {
            Canon.require(key.matches("[a-z][a-z0-9_]*"), "a field is named in lower snake case: " + key);
            allowed = List.copyOf(allowed);
            Canon.require(!allowed.isEmpty() || min <= max, "a field's range is not empty: " + key);
        }

        static Spec number(String key, long min, long max) {
            return new Spec(key, min, max, List.of());
        }

        static Spec choice(String key, List<String> allowed) {
            return new Spec(key, 0, 0, allowed);
        }

        /** Whether this field holds one of a set of names and not a number. */
        public boolean isChoice() {
            return !allowed.isEmpty();
        }
    }

    /** The boss entries, by name, sorted as the file states them. */
    public static final List<String> BOSSES = List.of("dm300", "goo", "tengu");

    /** The two section kinds a class may override, by section name. */
    public static final List<String> SECTIONS = List.of("identify", "upgrade");

    /** The fields of the section named {@code section}, in file order; refuses a section the format lacks. */
    public static List<Spec> specs(String section) {
        return switch (section) {
            case "identify" -> Identify.SPECS;
            case "upgrade" -> Upgrade.SPECS;
            case "bosses.goo" -> Goo.SPECS;
            case "bosses.tengu", "bosses.dm300" -> Later.SPECS;
            default -> throw new IllegalArgumentException("the Playbook has no section " + section);
        };
    }

    /**
     * Which unknown potions and scrolls the Brain tries, how far it walks to try them, and how long it
     * stays clear of the cloud a test can seed. Odds are in thousandths, so the file holds integers and
     * has one spelling for each value; {@link #healingOdds()} turns them back into the double a
     * comparison against a computed probability uses.
     *
     * @param healingOddsPerMille   the least odds of healing an unknown potion is drunk at when the hero is hurt
     * @param gainOddsPerMille      the least odds of strength or experience an unknown potion is drunk at, at any health
     * @param inventoryOddsPerMille the most odds the item-picker scrolls may hold of an unknown scroll that is read
     * @param restWaits             the most waits after a test the hero rests toward full health
     * @param reach                 the most Steps walked to a better testing cell
     * @param escapeWaits           the waits after a test the hero steps out of fire or gas
     */
    public record Identify(
            @Heuristic("unknown-potion-heal-half") int healingOddsPerMille,
            @Heuristic("strength-experience-potions") int gainOddsPerMille,
            @Heuristic(Heuristic.UNLINKED) int inventoryOddsPerMille,
            @Heuristic("regen-rest-test") int restWaits,
            @Heuristic("test-spot-door-water") int reach,
            @Heuristic("test-spot-door-water") int escapeWaits) {

        /** The fields, in the order the file states them (sorted, as the canonical text is). */
        public static final List<Spec> SPECS = List.of(
                Spec.number("escape_waits", 0, 100),
                Spec.number("gain_odds_per_mille", 1, 1000),
                Spec.number("healing_odds_per_mille", 1, 1000),
                Spec.number("inventory_odds_per_mille", 1, 1000),
                Spec.number("reach", 0, 40),
                Spec.number("rest_waits", 0, 500));

        /** The values the Policies decided by before they were data (stories 4.10, 4.13); the first Playbook states them. */
        public static final Identify REFERENCE = new Identify(200, 200, 250, 50, 8, 12);

        public Identify {
            check(SPECS.get(0), escapeWaits);
            check(SPECS.get(1), gainOddsPerMille);
            check(SPECS.get(2), healingOddsPerMille);
            check(SPECS.get(3), inventoryOddsPerMille);
            check(SPECS.get(4), reach);
            check(SPECS.get(5), restWaits);
        }

        /** The least odds of healing an unknown potion is drunk at when hurt. */
        public double healingOdds() {
            return healingOddsPerMille / 1000.0;
        }

        /** The least odds of strength or experience an unknown potion is drunk at. */
        public double gainOdds() {
            return gainOddsPerMille / 1000.0;
        }

        /** The odds the item-picker scrolls must stay under for an unknown scroll to be read. */
        public double inventoryOdds() {
            return inventoryOddsPerMille / 1000.0;
        }

        /** The most Steps in a row walked toward testing cells before the appearance is balked at: twice the reach. */
        public int walks() {
            return 2 * reach;
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("escape_waits", (long) escapeWaits);
            values.put("gain_odds_per_mille", (long) gainOddsPerMille);
            values.put("healing_odds_per_mille", (long) healingOddsPerMille);
            values.put("inventory_odds_per_mille", (long) inventoryOddsPerMille);
            values.put("reach", (long) reach);
            values.put("rest_waits", (long) restWaits);
            return values;
        }

        /** This section with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Identify with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Identify(num(values, "healing_odds_per_mille"), num(values, "gain_odds_per_mille"),
                    num(values, "inventory_odds_per_mille"), num(values, "rest_waits"), num(values, "reach"),
                    num(values, "escape_waits"));
        }
    }

    /**
     * Where a scroll of upgrade goes: the worn weapon while its level is below the worn armour's plus
     * {@code weaponLead}, the armour above it, and on the line itself the {@code tie} slot.
     *
     * @param weaponLead how many levels the weapon may be ahead of the armour and still be upgraded first
     * @param tie        the worn slot that takes the scroll when the levels sit on the line, WEAPON or ARMOR
     */
    public record Upgrade(@Heuristic("scroll-picker") int weaponLead, @Heuristic("scroll-picker") EquipSlot tie) {

        /** The fields, in the file's order. */
        public static final List<Spec> SPECS = List.of(
                Spec.choice("tie", List.of("ARMOR", "WEAPON")),
                Spec.number("weapon_lead", -5, 5));

        /** The values story 4.13 chose: the weapon first, so the two climb together. */
        public static final Upgrade REFERENCE = new Upgrade(0, EquipSlot.WEAPON);

        public Upgrade {
            java.util.Objects.requireNonNull(tie, "tie");
            Canon.require(SPECS.get(0).allowed().contains(tie.name()),
                    "upgrade.tie is one of " + SPECS.get(0).allowed() + ": " + tie);
            check(SPECS.get(1), weaponLead);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("tie", tie.name());
            values.put("weapon_lead", (long) weaponLead);
            return values;
        }

        /** This section with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Upgrade with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Upgrade(num(values, "weapon_lead"), EquipSlot.valueOf((String) values.get("tie")));
        }
    }

    /**
     * How each boss is approached (story 7.5, FR-35). A boss entry holds typed fields and no layout, no
     * cell and no coordinate: the Brain reads the boss from the screen. {@code goo} is the one entry
     * whose fields change a fight; the later bosses carry the descent rest and are marked
     * {@code UNTESTED} until the {@code bosses} set reaches them in numbers worth tuning on.
     *
     * @param goo   the first boss, depth 5
     * @param tengu the second boss, depth 10
     * @param dm300 the third boss, depth 15
     */
    public record Bosses(Goo goo, Later tengu, Later dm300) {

        /** The values the Policies decided by before the entries existed. */
        public static final Bosses REFERENCE = new Bosses(Goo.REFERENCE, Later.REFERENCE, Later.REFERENCE);

        public Bosses {
            java.util.Objects.requireNonNull(goo, "goo");
            java.util.Objects.requireNonNull(tengu, "tengu");
            java.util.Objects.requireNonNull(dm300, "dm300");
        }

        /**
         * The share of the hero's hit points, in thousandths, the descent rest reaches before the stairs
         * down to the floor at {@code depth}; a floor with no boss entry is rested to full.
         */
        public int descentRest(int depth) {
            return switch (depth) {
                case 5 -> goo.descentRestPerMille();
                case 10 -> tengu.descentRestPerMille();
                case 15 -> dm300.descentRestPerMille();
                default -> 1000;
            };
        }
    }

    /**
     * The Goo entry: the boss of depth 5, the one the Brain fights on purpose.
     *
     * @param descentRestPerMille the share of the hit points, in thousandths, the hero rests up to beside the stairs down to Goo's floor
     * @param drinkPerMille       the share of the hit points, in thousandths, at or under which a healing potion is drunk against Goo alone
     * @param meetRange           the most Steps walked to ground where a pump could be dodged, before Goo arrives
     * @param washRange           the most Steps walked to water to wash off caustic ooze
     */
    public record Goo(
            @Heuristic("rest-before-descend") int descentRestPerMille,
            @Heuristic(Heuristic.UNLINKED) int drinkPerMille,
            @Heuristic("goo-position-and-wash") int meetRange,
            @Heuristic("goo-position-and-wash") int washRange) {

        /** The fields, sorted as the file states them. */
        public static final List<Spec> SPECS = List.of(
                Spec.number("descent_rest_per_mille", 1, 1000),
                Spec.number("drink_per_mille", 1, 1000),
                Spec.number("meet_range", 0, 12),
                Spec.number("wash_range", 0, 12));

        /** The values the Policies held before they were data (issue #219; story 4.12). */
        public static final Goo REFERENCE = new Goo(1000, 400, 6, 4);

        public Goo {
            check(SPECS.get(0), descentRestPerMille);
            check(SPECS.get(1), drinkPerMille);
            check(SPECS.get(2), meetRange);
            check(SPECS.get(3), washRange);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("descent_rest_per_mille", (long) descentRestPerMille);
            values.put("drink_per_mille", (long) drinkPerMille);
            values.put("meet_range", (long) meetRange);
            values.put("wash_range", (long) washRange);
            return values;
        }

        /** This entry with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Goo with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Goo(num(values, "descent_rest_per_mille"), num(values, "drink_per_mille"),
                    num(values, "meet_range"), num(values, "wash_range"));
        }

        /** The hit points at or under which the hero drinks against Goo alone, for a hero of {@code ht} hit points. */
        public int danger(int ht) {
            return (int) (((long) ht * drinkPerMille + 999) / 1000);
        }
    }

    /**
     * A later boss's entry (Tengu, DM-300): the descent rest, and whether the entry was ever tuned.
     * An {@code UNTESTED} entry holds the reference values and the claims behind it in the Lore: fewer
     * than ten Runs of the {@code bosses} set reach these bosses, so nothing here has been measured.
     *
     * @param descentRestPerMille the share of the hit points, in thousandths, the hero rests up to beside the stairs down to the boss floor
     * @param status              {@code UNTESTED} or {@code TESTED}
     */
    public record Later(@Heuristic("rest-before-descend") int descentRestPerMille, String status) {

        /** The fields, sorted as the file states them. */
        public static final List<Spec> SPECS = List.of(
                Spec.number("descent_rest_per_mille", 1, 1000),
                Spec.choice("status", List.of("TESTED", "UNTESTED")));

        /** Rest to full, as the descend Policy did, and untested. */
        public static final Later REFERENCE = new Later(1000, "UNTESTED");

        public Later {
            check(SPECS.get(0), descentRestPerMille);
            java.util.Objects.requireNonNull(status, "status");
            Canon.require(SPECS.get(1).allowed().contains(status),
                    "status is one of " + SPECS.get(1).allowed() + ": " + status);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("descent_rest_per_mille", (long) descentRestPerMille);
            values.put("status", status);
            return values;
        }

        /** This entry with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Later with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Later(num(values, "descent_rest_per_mille"), (String) values.get("status"));
        }
    }

    /**
     * One hero class's sections, resolved over the base.
     *
     * @param heroClass the class
     * @param identify  the {@code identify} section as that class plays it
     * @param upgrade   the {@code upgrade} section as that class plays it
     */
    public record ClassEntry(HeroClass heroClass, Identify identify, Upgrade upgrade) {

        public ClassEntry {
            java.util.Objects.requireNonNull(heroClass, "heroClass");
            java.util.Objects.requireNonNull(identify, "identify");
            java.util.Objects.requireNonNull(upgrade, "upgrade");
        }
    }

    private static void check(Spec spec, long value) {
        Canon.require(value >= spec.min() && value <= spec.max(),
                spec.key() + " is from " + spec.min() + " to " + spec.max() + ": " + value);
    }

    private static int num(Map<String, Object> values, String key) {
        return Math.toIntExact((Long) values.get(key));
    }
}
