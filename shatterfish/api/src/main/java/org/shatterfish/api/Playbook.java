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
 * {@code upgrade} (where a scroll of upgrade goes), {@code kit} (what the hero does with the items its class starts with), {@code bosses} (reserved: the format has the
 * section and no field in it yet, so a file may state it empty) and {@code classes}.
 *
 * @param name     the Playbook's name, which is the Brain's name
 * @param version  the Playbook's version, bumped when a value changes meaning or value
 * @param identify the base {@code identify} section
 * @param upgrade  the base {@code upgrade} section
 * @param kit      the base {@code kit} section
 * @param classes  per hero class, the sections as that class plays them, sorted by class name (the order the file reader requires), each once
 */
public record Playbook(String name, int version, Identify identify, Upgrade upgrade, Kit kit, List<ClassEntry> classes) {

    /** The version of the file's shape, which the reader checks. */
    public static final int FORMAT = 1;

    public Playbook {
        name = Canon.text(name, "playbook name");
        Canon.require(name.matches("[a-z][a-z0-9_]*"), "a Playbook is named as a Brain is: " + name);
        Canon.require(version >= 1, "a Playbook's version starts at 1: " + version);
        java.util.Objects.requireNonNull(identify, "identify");
        java.util.Objects.requireNonNull(upgrade, "upgrade");
        java.util.Objects.requireNonNull(kit, "kit");
        classes = Canon.sorted(classes, Comparator.comparing((ClassEntry entry) -> entry.heroClass().name()), "classes");
        for (int i = 1; i < classes.size(); i++) {
            Canon.require(classes.get(i).heroClass() != classes.get(i - 1).heroClass(),
                    "a hero class is stated once: " + classes.get(i).heroClass());
        }
    }

    /** A Playbook with the reference {@code kit}, the shape the stories before 7.6 built. */
    public Playbook(String name, int version, Identify identify, Upgrade upgrade, List<ClassEntry> classes) {
        this(name, version, identify, upgrade, Kit.REFERENCE, classes);
    }

    /** The Playbook of the values the Policies decided by before they were data: the file's defaults. */
    public static Playbook reference(String name) {
        return new Playbook(name, 1, Identify.REFERENCE, Upgrade.REFERENCE, Kit.REFERENCE, List.of());
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

    /** The {@code kit} section as {@code heroClass} plays it. */
    public Kit kit(HeroClass heroClass) {
        for (ClassEntry entry : classes) {
            if (entry.heroClass() == heroClass) {
                return entry.kit();
            }
        }
        return kit;
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
        out.key("bosses").beginObject().endObject();
        out.key("classes").beginObject();
        for (ClassEntry entry : classes) {
            out.key(entry.heroClass().name()).beginObject();
            section(out, "identify", entry.identify().values());
            section(out, "kit", entry.kit().values());
            section(out, "upgrade", entry.upgrade().values());
            out.endObject();
        }
        out.endObject();
        out.key("format").value(FORMAT);
        section(out, "identify", identify.values());
        section(out, "kit", kit.values());
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

    /** The two section kinds a class may override, by section name. */
    public static final List<String> SECTIONS = List.of("identify", "kit", "upgrade");

    /** The fields of the section named {@code section}, in file order; refuses a section the format lacks. */
    public static List<Spec> specs(String section) {
        return switch (section) {
            case "identify" -> Identify.SPECS;
            case "upgrade" -> Upgrade.SPECS;
            case "kit" -> Kit.SPECS;
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
     * Where a scroll of upgrade goes, and when: the worn weapon while its level is below the worn armour's
     * plus {@code weaponLead}, the armour above it, on the line itself the {@code tie} slot, and nothing
     * before the hero reaches depth {@code holdUntilDepth}. Every target is a piece the hero wears
     * (story 7.4): the heaviest armour in the pack is not a target, because nothing the Brain does would
     * put the upgraded piece on (docs/rules/upgrades.md).
     *
     * @param weaponLead     how many levels the weapon may be ahead of the armour and still be upgraded first
     * @param tie            the worn slot that takes the scroll when the levels sit on the line, WEAPON or ARMOR
     * @param holdUntilDepth the depth from which a known scroll of upgrade is read; 0 reads at once, and a
     *                       deeper number keeps the scrolls in the pack until the hero is that deep
     * @param unknownWeapon  whether the worn weapon may take a scroll while its level is not shown
     */
    public record Upgrade(@Heuristic("scroll-picker") int weaponLead, @Heuristic("scroll-picker") EquipSlot tie,
                          @Heuristic("upgrade-allocation") int holdUntilDepth,
                          @Heuristic("upgrade-allocation") boolean unknownWeapon) {

        /** The fields, in the file's order. */
        public static final List<Spec> SPECS = List.of(
                Spec.number("hold_until_depth", 0, 26),
                Spec.choice("tie", List.of("ARMOR", "WEAPON")),
                Spec.choice("unknown_weapon", List.of("NO", "YES")),
                Spec.number("weapon_lead", -5, 5));

        /** The values stories 4.13 and 7.4 settled on: the weapon first so the two climb together, no hold. */
        public static final Upgrade REFERENCE = new Upgrade(0, EquipSlot.WEAPON, 0, false);

        public Upgrade {
            java.util.Objects.requireNonNull(tie, "tie");
            check(SPECS.get(0), holdUntilDepth);
            Canon.require(SPECS.get(1).allowed().contains(tie.name()),
                    "upgrade.tie is one of " + SPECS.get(1).allowed() + ": " + tie);
            check(SPECS.get(3), weaponLead);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("hold_until_depth", (long) holdUntilDepth);
            values.put("tie", tie.name());
            values.put("unknown_weapon", unknownWeapon ? "YES" : "NO");
            values.put("weapon_lead", (long) weaponLead);
            return values;
        }

        /** This section with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Upgrade with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            String unknown = String.valueOf(values.get("unknown_weapon"));
            Canon.require(SPECS.get(2).allowed().contains(unknown),
                    "upgrade.unknown_weapon is one of " + SPECS.get(2).allowed() + ": " + unknown);
            return new Upgrade(num(values, "weapon_lead"), EquipSlot.valueOf((String) values.get("tie")),
                    num(values, "hold_until_depth"), unknown.equals("YES"));
        }
    }

    /**
     * What the hero does with the items it starts with that only some classes hold (story 7.6): the
     * Mage's staff, whose wand the hero zaps at an enemy in view. The base states the reference, which
     * uses none of them, so a class without an entry plays as it did before the section existed.
     *
     * @param zapReach the farthest an enemy in view is zapped at with the worn staff of magic missile, in
     *                 cells; 0 never zaps
     */
    public record Kit(@Heuristic("staff-zap") int zapReach) {

        /** The fields, in the file's order. */
        public static final List<Spec> SPECS = List.of(Spec.number("zap_reach", 0, 10));

        /** No kit item is used: the Brain before story 7.6. */
        public static final Kit REFERENCE = new Kit(0);

        public Kit {
            check(SPECS.get(0), zapReach);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("zap_reach", (long) zapReach);
            return values;
        }

        /** This section with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Kit with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Kit(num(values, "zap_reach"));
        }
    }

    /**
     * One hero class's sections, resolved over the base.
     *
     * @param heroClass the class
     * @param identify  the {@code identify} section as that class plays it
     * @param kit       the {@code kit} section as that class plays it
     * @param upgrade   the {@code upgrade} section as that class plays it
     */
    public record ClassEntry(HeroClass heroClass, Identify identify, Kit kit, Upgrade upgrade) {

        public ClassEntry {
            java.util.Objects.requireNonNull(heroClass, "heroClass");
            java.util.Objects.requireNonNull(identify, "identify");
            java.util.Objects.requireNonNull(kit, "kit");
            java.util.Objects.requireNonNull(upgrade, "upgrade");
        }

        /** An entry that leaves the {@code kit} at the reference, the shape before story 7.6. */
        public ClassEntry(HeroClass heroClass, Identify identify, Upgrade upgrade) {
            this(heroClass, identify, Kit.REFERENCE, upgrade);
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
