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
 * {@code upgrade} (where a scroll of upgrade goes), {@code kit} (what the hero does with the items its class starts with), {@code bosses} (one entry per boss: how its
 * fight is approached and how fit the hero is sent down to it, story 7.5) and {@code classes}.
 *
 * @param name     the Playbook's name, which is the Brain's name
 * @param version  the Playbook's version, bumped when a value changes meaning or value
 * @param identify the base {@code identify} section
 * @param upgrade  the base {@code upgrade} section
 * @param kit      the base {@code kit} section
 * @param classes  per hero class, the sections as that class plays them, sorted by class name (the order the file reader requires), each once
 * @param bosses   the {@code bosses} section, one entry per boss (story 7.5)
 * @param tunables the values of the Policy constants the registry {@link Tunables} lists, as far as they differ from the defaults (story 10.35)
 */
public record Playbook(String name, int version, Identify identify, Upgrade upgrade, Kit kit, List<ClassEntry> classes, Bosses bosses,
                       Tunables.Vector tunables) {

    /** The version of the file's shape, which the reader checks. */
    public static final int FORMAT = 1;

    public Playbook {
        name = Canon.text(name, "playbook name");
        Canon.require(name.matches("[a-z][a-z0-9_]*"), "a Playbook is named as a Brain is: " + name);
        Canon.require(version >= 1, "a Playbook's version starts at 1: " + version);
        java.util.Objects.requireNonNull(identify, "identify");
        java.util.Objects.requireNonNull(upgrade, "upgrade");
        java.util.Objects.requireNonNull(kit, "kit");
        java.util.Objects.requireNonNull(bosses, "bosses");
        java.util.Objects.requireNonNull(tunables, "tunables");
        classes = Canon.sorted(classes, Comparator.comparing((ClassEntry entry) -> entry.heroClass().name()), "classes");
        for (int i = 1; i < classes.size(); i++) {
            Canon.require(classes.get(i).heroClass() != classes.get(i - 1).heroClass(),
                    "a hero class is stated once: " + classes.get(i).heroClass());
        }
    }

    /** A Playbook that states no Policy tunables, the shape before story 10.35. */
    public Playbook(String name, int version, Identify identify, Upgrade upgrade, Kit kit, List<ClassEntry> classes, Bosses bosses) {
        this(name, version, identify, upgrade, kit, classes, bosses, Tunables.Vector.REFERENCE);
    }

    /** A Playbook with the reference {@code bosses}, the shape story 7.6 built. */
    public Playbook(String name, int version, Identify identify, Upgrade upgrade, Kit kit, List<ClassEntry> classes) {
        this(name, version, identify, upgrade, kit, classes, Bosses.REFERENCE);
    }

    /** A Playbook with the reference {@code kit} and {@code bosses}, the shape the stories before 7.6 built. */
    public Playbook(String name, int version, Identify identify, Upgrade upgrade, List<ClassEntry> classes) {
        this(name, version, identify, upgrade, Kit.REFERENCE, classes, Bosses.REFERENCE);
    }

    /** The Playbook of the values the Policies decided by before they were data: the file's defaults. */
    public static Playbook reference(String name) {
        return new Playbook(name, 1, Identify.REFERENCE, Upgrade.REFERENCE, Kit.REFERENCE, List.of(), Bosses.REFERENCE);
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
        out.key("bosses").beginObject();
        section(out, "dm300", bosses.dm300().values());
        section(out, "goo", bosses.goo().values());
        section(out, "tengu", bosses.tengu().values());
        out.endObject();
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
        // Only a Playbook that moves a Policy constant has the section, so every earlier hash holds (story 10.35).
        if (!tunables.isReference()) {
            Map<String, Object> stated = new LinkedHashMap<>(tunables.values());
            section(out, "tunables", stated);
        }
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
    public static final List<String> SECTIONS = List.of("identify", "kit", "upgrade");

    /** The fields of the section named {@code section}, in file order; refuses a section the format lacks. */
    public static List<Spec> specs(String section) {
        return switch (section) {
            case "identify" -> Identify.SPECS;
            case "upgrade" -> Upgrade.SPECS;
            case "kit" -> Kit.SPECS;
            case "bosses.goo" -> Goo.SPECS;
            case "bosses.tengu" -> Tengu.SPECS;
            case "bosses.dm300" -> Later.SPECS;
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
     * @param restHpPerMille        the most hit points, in thousandths of the hero's most, at which a rest after a
     *                              test is owed (it ends above that); food is one a floor and a rest costs ten turns of it a hit point, so a
     *                              hero scratched but not hurt goes on (story 10.15)
     * @param reach                 the most Steps walked to a better testing cell
     * @param escapeWaits           the waits after a test the hero steps out of fire or gas
     * @param potionHpPerMille      the most hit points, in thousandths of the hero's most, at which an unknown potion is
     *                              drunk for its odds of healing (story 7.3)
     * @param scrollHpPerMille      the least hit points, in thousandths of the hero's most, at which an unknown scroll
     *                              is read (story 7.3)
     * @param knownGains            1 to drink a known potion of strength or experience at the first calm moment, 0 to hold
     *                              it (story 7.3)
     */
    public record Identify(
            @Heuristic("unknown-potion-heal-half") int healingOddsPerMille,
            @Heuristic("strength-experience-potions") int gainOddsPerMille,
            @Heuristic(Heuristic.UNLINKED) int inventoryOddsPerMille,
            @Heuristic("regen-rest-test") int restWaits,
            @Heuristic("regen-rest-test") int restHpPerMille,
            @Heuristic("test-spot-door-water") int reach,
            @Heuristic("test-spot-door-water") int escapeWaits,
            @Heuristic("unknown-potion-heal-half") int potionHpPerMille,
            @Heuristic("unknown-scroll-full-health") int scrollHpPerMille,
            @Heuristic("strength-experience-potions") int knownGains) {

        /** The fields, in the order the file states them (sorted, as the canonical text is). */
        public static final List<Spec> SPECS = List.of(
                Spec.number("escape_waits", 0, 100),
                Spec.number("gain_odds_per_mille", 1, 1000),
                Spec.number("healing_odds_per_mille", 1, 1000),
                Spec.number("inventory_odds_per_mille", 1, 1000),
                Spec.number("known_gains", 0, 1),
                Spec.number("potion_hp_per_mille", 1, 1000),
                Spec.number("reach", 0, 40),
                Spec.number("rest_hp_per_mille", 1, 1000),
                Spec.number("rest_waits", 0, 500),
                Spec.number("scroll_hp_per_mille", 1, 1000));

        /** The values the Policies decided by before they were data (stories 4.10, 4.13); the first Playbook states them. */
        public static final Identify REFERENCE = new Identify(200, 200, 250, 50, 1000, 8, 12, 500, 1000, 0);

        public Identify {
            check(SPECS.get(0), escapeWaits);
            check(SPECS.get(1), gainOddsPerMille);
            check(SPECS.get(2), healingOddsPerMille);
            check(SPECS.get(3), inventoryOddsPerMille);
            check(SPECS.get(4), knownGains);
            check(SPECS.get(5), potionHpPerMille);
            check(SPECS.get(6), reach);
            check(SPECS.get(7), restHpPerMille);
            check(SPECS.get(8), restWaits);
            check(SPECS.get(9), scrollHpPerMille);
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
            values.put("known_gains", (long) knownGains);
            values.put("potion_hp_per_mille", (long) potionHpPerMille);
            values.put("reach", (long) reach);
            values.put("rest_hp_per_mille", (long) restHpPerMille);
            values.put("rest_waits", (long) restWaits);
            values.put("scroll_hp_per_mille", (long) scrollHpPerMille);
            return values;
        }

        /** This section with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Identify with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Identify(num(values, "healing_odds_per_mille"), num(values, "gain_odds_per_mille"),
                    num(values, "inventory_odds_per_mille"), num(values, "rest_waits"), num(values, "rest_hp_per_mille"),
                    num(values, "reach"),
                    num(values, "escape_waits"), num(values, "potion_hp_per_mille"), num(values, "scroll_hp_per_mille"),
                    num(values, "known_gains"));
        }

        /** Whether {@code hp} of {@code ht} is low enough for a rest after a test to begin. */
        public boolean restHp(int hp, int ht) {
            return 1000L * hp <= (long) restHpPerMille * ht;
        }

        /** Whether {@code hp} of {@code ht} is low enough for an unknown potion to be drunk for its odds of healing. */
        public boolean potionHp(int hp, int ht) {
            return 1000L * hp <= (long) potionHpPerMille * ht;
        }

        /** Whether {@code hp} of {@code ht} is high enough for an unknown scroll to be read. */
        public boolean scrollHp(int hp, int ht) {
            return 1000L * hp >= (long) scrollHpPerMille * ht;
        }
    }

    /**
     * Where a scroll of upgrade goes, and when: the worn weapon while its level is below the worn armour's
     * plus {@code weaponLead}, the armour above it, on the line itself the {@code tie} slot, and nothing
     * before the hero reaches depth {@code holdUntilDepth}. The armour is the one the hero wears
     * (story 7.4), or, with {@code armour_pick} {@code HEAVIEST}, the one in the pack worth most worn
     * after the scroll, which the equip Policy then puts on (#305, docs/rules/upgrades.md).
     *
     * @param heaviestArmour whether the armour is the one in the pack worth most worn one level higher
     *                       ({@code armour_pick} {@code HEAVIEST}) rather than the one worn ({@code WORN})
     * @param weaponLead     how many levels the weapon may be ahead of the armour and still be upgraded first
     * @param tie            the worn slot that takes the scroll when the levels sit on the line, WEAPON or ARMOR
     * @param holdUntilDepth the depth from which a known scroll of upgrade is read; 0 reads at once, and a
     *                       deeper number keeps the scrolls in the pack until the hero is that deep
     * @param unknownWeapon  whether the worn weapon may take a scroll while its level is not shown
     */
    public record Upgrade(@Heuristic("scroll-picker") int weaponLead, @Heuristic("scroll-picker") EquipSlot tie,
                          @Heuristic("upgrade-allocation") int holdUntilDepth,
                          @Heuristic("upgrade-allocation") boolean unknownWeapon,
                          @Heuristic("upgrade-allocation") boolean heaviestArmour) {

        /** The fields, in the file's order. */
        public static final List<Spec> SPECS = List.of(
                Spec.choice("armour_pick", List.of("WORN", "HEAVIEST")),
                Spec.number("hold_until_depth", 0, 26),
                Spec.choice("tie", List.of("ARMOR", "WEAPON")),
                Spec.choice("unknown_weapon", List.of("NO", "YES")),
                Spec.number("weapon_lead", -5, 5));

        /** The values stories 4.13 and 7.4 settled on: the weapon first so the two climb together, no hold. */
        public static final Upgrade REFERENCE = new Upgrade(0, EquipSlot.WEAPON, 0, false, false);

        public Upgrade {
            java.util.Objects.requireNonNull(tie, "tie");
            check(SPECS.get(1), holdUntilDepth);
            Canon.require(SPECS.get(2).allowed().contains(tie.name()),
                    "upgrade.tie is one of " + SPECS.get(2).allowed() + ": " + tie);
            check(SPECS.get(4), weaponLead);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("armour_pick", heaviestArmour ? "HEAVIEST" : "WORN");
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
            Canon.require(SPECS.get(3).allowed().contains(unknown),
                    "upgrade.unknown_weapon is one of " + SPECS.get(3).allowed() + ": " + unknown);
            String pick = String.valueOf(values.get("armour_pick"));
            Canon.require(SPECS.get(0).allowed().contains(pick),
                    "upgrade.armour_pick is one of " + SPECS.get(0).allowed() + ": " + pick);
            return new Upgrade(num(values, "weapon_lead"), EquipSlot.valueOf((String) values.get("tie")),
                    num(values, "hold_until_depth"), unknown.equals("YES"), pick.equals("HEAVIEST"));
        }
    }

    /**
     * How each boss is approached (story 7.5, FR-35). A boss entry holds typed fields and no layout, no
     * cell and no coordinate: the Brain reads the boss from the screen. {@code goo} is the one entry
     * whose fields change a fight and {@code tengu} the one whose fields change the walk to it (story
     * 10.18); the last boss carries the descent rest and is marked {@code UNTESTED} until the
     * {@code bosses} set reaches it in numbers worth tuning on.
     *
     * @param goo   the first boss, depth 5
     * @param tengu the second boss, depth 10
     * @param dm300 the third boss, depth 15
     */
    public record Bosses(Goo goo, Tengu tengu, Later dm300) {

        /** The values the Policies decided by before the entries existed. */
        public static final Bosses REFERENCE = new Bosses(Goo.REFERENCE, Tengu.REFERENCE, Later.REFERENCE);

        /**
         * Bosses whose Tengu entry is the reference entry with the descent rest and status of {@code tengu}: the
         * shape before story 10.18, which held Tengu's entry as a later boss's.
         */
        public Bosses(Goo goo, Later tengu, Later dm300) {
            this(goo, Tengu.REFERENCE.with(Map.of("descent_rest_per_mille", (long) tengu.descentRestPerMille(),
                    "status", tengu.status())), dm300);
        }

        public Bosses {
            java.util.Objects.requireNonNull(goo, "goo");
            java.util.Objects.requireNonNull(tengu, "tengu");
            java.util.Objects.requireNonNull(dm300, "dm300");
        }

        /**
         * The share of the hero's hit points, in thousandths, the descent rest reaches before the stairs
         * down to the floor at {@code depth}; a floor with no boss entry is rested to full. The boss floors are depths 5
         * (Goo), 10 (Tengu) and 15 (DM-300), from {@code Dungeon.bossLevel} at
         * {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java:441-443} (Rule in docs/rules/combat.md).
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
     * The Tengu entry: the boss of depth 10, the floor the Brain walks through to reach him (story 10.18) and
     * the costs the second half of the fight is weighed by (story 10.20). The costs are in hit points, the
     * currency the threat map adds up in: what standing on a cell that the screen shows about to be struck
     * is expected to take, and what a step off the cells beside Tengu is worth.
     *
     * @param descentRestPerMille the share of the hit points, in thousandths, the hero rests up to beside the stairs down to Tengu's floor
     * @param enterPerMille       the share of the hit points, in thousandths, the hero rests up to before it opens the cell door, which seals the floor
     * @param restLimit           the most rests handed over on Tengu's floor before the door is opened regardless
     * @param status              {@code UNTESTED} or {@code TESTED}
     * @param bombCost            the hit points a cell within two steps of a smoke bomb is expected to cost (15 to 30 before armour)
     * @param fireCost            the hit points a cell the fire is shown on is expected to cost (it sets the hero burning)
     * @param shockCost           the hit points a cell beside a shocker is expected to cost (12 at depth 10, every other turn)
     * @param adjacentCost        the hit points a turn away from the cells beside Tengu is worth: the strike not made
     */
    public record Tengu(
            @Heuristic("rest-before-descend") int descentRestPerMille,
            @Heuristic("tengu-entry") int enterPerMille,
            @Heuristic("tengu-entry") int restLimit,
            String status,
            @Heuristic("tengu-threat") int bombCost,
            @Heuristic("tengu-threat") int fireCost,
            @Heuristic("tengu-threat") int shockCost,
            @Heuristic("tengu-threat") int adjacentCost) {

        /** The fields, sorted as the file states them. */
        public static final List<Spec> SPECS = List.of(
                Spec.number("adjacent_cost", 0, 100),
                Spec.number("bomb_cost", 0, 100),
                Spec.number("descent_rest_per_mille", 1, 1000),
                Spec.number("enter_per_mille", 1, 1000),
                Spec.number("fire_cost", 0, 100),
                Spec.number("rest_limit", 0, 100),
                Spec.number("shock_cost", 0, 100),
                Spec.choice("status", List.of("TESTED", "UNTESTED")));

        /** Rest to full on both sides of the stairs, a rest limit as the descend Policy's, untested; the costs of {@code docs/rules/levels.md}. */
        public static final Tengu REFERENCE = new Tengu(1000, 1000, 20, "UNTESTED", 22, 18, 12, 6);

        public Tengu {
            check(SPECS.get(0), adjacentCost);
            check(SPECS.get(1), bombCost);
            check(SPECS.get(2), descentRestPerMille);
            check(SPECS.get(3), enterPerMille);
            check(SPECS.get(4), fireCost);
            check(SPECS.get(5), restLimit);
            check(SPECS.get(6), shockCost);
            java.util.Objects.requireNonNull(status, "status");
            Canon.require(SPECS.get(7).allowed().contains(status),
                    "status is one of " + SPECS.get(7).allowed() + ": " + status);
        }

        /** The entry before story 10.20 gave it costs: the fields of story 10.18 and the reference costs. */
        public Tengu(int descentRestPerMille, int enterPerMille, int restLimit, String status) {
            this(descentRestPerMille, enterPerMille, restLimit, status, REFERENCE.bombCost, REFERENCE.fireCost,
                    REFERENCE.shockCost, REFERENCE.adjacentCost);
        }

        /** The fields by name, in {@link #SPECS} order. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("adjacent_cost", (long) adjacentCost);
            values.put("bomb_cost", (long) bombCost);
            values.put("descent_rest_per_mille", (long) descentRestPerMille);
            values.put("enter_per_mille", (long) enterPerMille);
            values.put("fire_cost", (long) fireCost);
            values.put("rest_limit", (long) restLimit);
            values.put("shock_cost", (long) shockCost);
            values.put("status", status);
            return values;
        }

        /** This entry with the fields {@code given} states replaced; every key of {@code given} is a field. */
        public Tengu with(Map<String, Object> given) {
            Map<String, Object> values = values();
            values.putAll(given);
            return new Tengu(num(values, "descent_rest_per_mille"), num(values, "enter_per_mille"),
                    num(values, "rest_limit"), (String) values.get("status"), num(values, "bomb_cost"),
                    num(values, "fire_cost"), num(values, "shock_cost"), num(values, "adjacent_cost"));
        }
    }

    /**
     * A later boss's entry (DM-300): the descent rest, and whether the entry was ever tuned.
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
