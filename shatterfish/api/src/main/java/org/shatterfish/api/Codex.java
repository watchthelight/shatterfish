package org.shatterfish.api;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Codex's shape (E2): what a Codex file carries, as records a Brain's caller can hand it
 * and a generator can write. The Codex is static, seed-free knowledge about types and tables,
 * generated from the pinned upstream tag and never about a Run (FR-14); every entry cites the
 * {@code path:line} it was read from (non-negotiable 8), and the citation is computed at
 * generation by finding the declaration in the pinned source, never typed from memory.
 *
 * <p>The version is the Codex's own, distinct from the Observation schema's and from the tag: it
 * changes when a table's meaning or shape changes, and it is what the Run-log header will record
 * (ADR-0011, E3) so that a Brain's behaviour can be tied to the knowledge it had. A Codex value
 * never reaches the Brain as a game class; these records and {@link CodexJson} are the whole
 * contract.
 */
public final class Codex {

    /**
     * The Codex version: 1 was the skeleton with the hero classes and the challenge flags; 2 adds
     * the mobs and the spawn rotation (story 2.2).
     */
    public static final int VERSION = 2;

    /** The shape of an upstream tag: {@code v}, a dotted version, and an optional pre-release suffix. */
    public static final String TAG_PATTERN = "v[0-9]+([.][0-9]+)*(-[A-Za-z0-9.]+)?";

    private Codex() {
    }

    /**
     * Where an entry was read: a path relative to the repository root, forward-slashed, with no
     * drive, no {@code .} or {@code ..} segment and no empty segment, and a one-based line.
     */
    public record Citation(String path, int line) {

        public Citation {
            path = Canon.text(path, "citation path");
            Canon.require(!path.isEmpty() && !path.startsWith("/") && !path.contains("\\") && !path.contains(":"),
                    "a citation path is relative, with forward slashes and no drive: " + path);
            for (String segment : path.split("/", -1)) {
                Canon.require(!segment.isEmpty() && !segment.equals(".") && !segment.equals(".."),
                        "a citation path has no empty, . or .. segment: " + path);
            }
            Canon.require(line >= 1, "a citation line is one-based: " + line);
        }

        /** The {@code path:line} form the documents use. */
        public String reference() {
            return path + ":" + line;
        }
    }

    /** The manifest: the Codex version, the upstream tag the folder is named by, and the table files. */
    public record Manifest(int version, String upstreamTag, List<String> tables) {

        public Manifest {
            Canon.require(version >= 1, "a Codex version is positive: " + version);
            upstreamTag = Canon.text(upstreamTag, "upstream tag");
            Canon.require(upstreamTag.matches(TAG_PATTERN), "an upstream tag is v and a version: " + upstreamTag);
            tables = Canon.sorted(tables, Comparator.naturalOrder(), "tables");
            Canon.require(new HashSet<>(tables).size() == tables.size(), "a table is listed once: " + tables);
        }
    }

    /** One hero class and its subclasses, in the game's declaration order. */
    public record HeroClassEntry(HeroClass heroClass, List<HeroSubclass> subclasses, Citation citation) {

        public HeroClassEntry {
            Canon.require(heroClass != null, "an entry names its hero class");
            subclasses = Canon.positional(subclasses, "subclasses");
            Canon.require(!subclasses.isEmpty(), "a hero class has subclasses");
            Canon.require(!subclasses.contains(HeroSubclass.NONE), "NONE is the absence of a subclass, not one");
            Canon.require(new HashSet<>(subclasses).size() == subclasses.size(), "a subclass is listed once: " + subclasses);
            Canon.require(citation != null, "an entry carries its citation");
        }
    }

    /** One challenge flag and the bit mask the game stores it under. */
    public record ChallengeEntry(Challenge challenge, int mask, Citation citation) {

        public ChallengeEntry {
            Canon.require(challenge != null, "an entry names its challenge");
            Canon.require(mask >= 1 && Integer.bitCount(mask) == 1, "a challenge mask is one bit: " + mask);
            Canon.require(citation != null, "an entry carries its citation");
        }
    }

    // --- the mobs table (story 2.2)

    /** How a roll's expression was read: a plain call the generator parsed, a constant, or anything else. */
    public enum RollKind {
        /** {@code Random.NormalIntRange(min, max)}: a normal-ish roll between the two, inclusive. */
        NORMAL,
        /** {@code Random.IntRange(min, max)}, inclusive, or {@code Random.Int(min, max)}, whose exclusive max is written as inclusive. */
        UNIFORM,
        /** {@code return N}: the same every time; {@code min} and {@code max} are N. */
        CONSTANT,
        /** A formula, a weapon's roll, several returns, a parent's roll added: the expression is the text, and 2.5 measures it. */
        OTHER
    }

    /**
     * A mob's damage roll, attack skill or damage reduction as its declaring class's source
     * states it: the kind, the parsed bounds where the kind is not OTHER (zero otherwise), the
     * expression's text, and the citation of the method's declaration.
     */
    public record Roll(RollKind kind, int min, int max, String expression, Citation citation) {

        public Roll {
            Canon.require(kind != null, "a roll has a kind");
            expression = Canon.text(expression, "roll expression");
            Canon.require(!expression.isEmpty(), "a roll has its expression");
            Canon.require(kind == RollKind.OTHER || min <= max, "a roll's min is at most its max: " + min + ".." + max);
            Canon.require(kind != RollKind.CONSTANT || min == max, "a constant roll has one value: " + min + ".." + max);
            Canon.require(kind != RollKind.OTHER || (min == 0 && max == 0), "an OTHER roll carries no bounds");
            Canon.require(citation != null, "a roll carries its citation");
        }
    }

    /** What the loot field names: nothing, an item class, a generator category, an item instance, or a draw. */
    public enum LootKind {
        NONE, CLASS, CATEGORY, ITEM, RANDOM
    }

    /**
     * A mob's loot as declared: the kind and the name it names (empty for NONE), the declaration's
     * text, the chance as thousandths when the declaration is a literal or a simple fraction and
     * -1 otherwise, the chance's expression, whether the loot itself is drawn at construction,
     * and whether the class overrides {@code createLoot()} or {@code lootChance()} so that the
     * declared values are not the whole story.
     */
    public record Loot(LootKind kind, String name, String declaration, int chanceThousandths, String chanceExpression,
                       boolean random, boolean customLoot, boolean customChance, Citation citation) {

        public Loot {
            Canon.require(kind != null, "loot has a kind");
            name = Canon.text(name, "loot name");
            Canon.require((kind == LootKind.NONE) == name.isEmpty(), "loot names what it is, or nothing: " + kind + " " + name);
            declaration = Canon.text(declaration, "loot declaration");
            Canon.require(!declaration.isEmpty(), "loot carries its declaration");
            Canon.require(chanceThousandths >= -1 && chanceThousandths <= 1000, "a chance is thousandths, or -1: " + chanceThousandths);
            chanceExpression = Canon.text(chanceExpression, "loot chance expression");
            Canon.require(kind == LootKind.RANDOM == random, "a drawn loot is the RANDOM kind: " + kind);
            Canon.require(citation != null, "loot carries its citation");
        }
    }

    /** One field of a mob that differs from the base under a depth or a challenge. */
    public record Field(String name, String value) {

        public Field {
            name = Canon.text(name, "field name");
            value = Canon.text(value, "field value");
            Canon.require(MOB_FIELDS.contains(name), "a field is one of " + MOB_FIELDS + ": " + name);
        }
    }

    /**
     * A mob's stats under a depth or a challenge where they differ from the base at depth 1 with
     * no challenge: the depth (1 for a challenge variant), the challenge (empty for a depth
     * variant), and only the fields that differ, by name. A variant is a depth's or a
     * challenge's, never both: the generator holds that the two compose, so a depth's variant
     * and a challenge's variant together are the mob under both. A depth with no variant is the
     * base.
     */
    public record Variant(int depth, String challenge, List<Field> fields) {

        public Variant {
            Canon.require(depth >= 1, "a variant's depth is one-based: " + depth);
            challenge = Canon.text(challenge, "variant challenge");
            Canon.require(depth > 1 != !challenge.isEmpty(), "a variant is a depth's or a challenge's: " + depth + " " + challenge);
            if (!challenge.isEmpty()) {
                Challenge.valueOf(challenge);
            }
            fields = Canon.sorted(fields, Comparator.comparing(Field::name), "fields");
            Canon.require(!fields.isEmpty(), "a variant differs in at least one field");
        }
    }

    /** The names of a mob entry's fields a variant may carry. */
    public static final List<String> MOB_FIELDS = List.of("ht", "defenseSkill", "exp", "maxLvl", "alignment", "properties");

    /** The numeric fields a constructor may take from the hero of a Run; the run-dependent ones are among these. */
    public static final List<String> MOB_NUMBERS = List.of("ht", "defenseSkill", "exp", "maxLvl");

    /**
     * One concrete mob class of the game, constructed at depth 1 with no challenge: its class name
     * (canonical, without the game's root package), alignment, properties (sorted; empty and
     * {@code propertiesRandom} when a draw at construction decides them), the numeric fields
     * whose value the constructor reads from the hero of a Run ({@code runDependent}, dumped as
     * zero and not compared), whether the game sets the stats after construction
     * ({@code statsSetLater}: a level, a spawn or a real constructor decides them, and the values
     * here are the placeholders), whether the class overrides {@code defenseSkill(Char)} so that
     * the field is not the whole story ({@code customDefense}), the lines where the constructor
     * draws ({@code draws}), hit points, defense skill, experience, maximum level, the three
     * rolls, the loot, the variants, and the citation of the class's declaration.
     */
    public record MobEntry(String className, Alignment alignment, List<String> properties, boolean propertiesRandom,
                           List<String> runDependent, boolean statsSetLater, boolean customDefense, List<Citation> draws,
                           int ht, int defenseSkill, int exp, int maxLvl, Roll damage, Roll attack, Roll dr, Loot loot,
                           List<Variant> variants, Citation citation) {

        public MobEntry {
            className = Canon.text(className, "mob class name");
            Canon.require(!className.isEmpty(), "a mob names its class");
            Canon.require(alignment != null, "a mob has an alignment");
            properties = Canon.sorted(properties, Comparator.naturalOrder(), "properties");
            Canon.require(!propertiesRandom || properties.isEmpty(), "random properties are not listed");
            runDependent = Canon.sorted(runDependent, Comparator.naturalOrder(), "run-dependent fields");
            Canon.require(MOB_NUMBERS.containsAll(runDependent), "a run-dependent field is one of " + MOB_NUMBERS + ": " + runDependent);
            Canon.require(!runDependent.contains("ht") || ht == 0, "a run-dependent field is dumped as zero: ht " + ht);
            Canon.require(!runDependent.contains("defenseSkill") || defenseSkill == 0, "a run-dependent field is dumped as zero: defenseSkill " + defenseSkill);
            Canon.require(!runDependent.contains("exp") || exp == 0, "a run-dependent field is dumped as zero: exp " + exp);
            Canon.require(!runDependent.contains("maxLvl") || maxLvl == 0, "a run-dependent field is dumped as zero: maxLvl " + maxLvl);
            draws = Canon.sorted(draws, Comparator.comparing(Citation::path).thenComparingInt(Citation::line), "draws");
            Canon.require(!propertiesRandom || !draws.isEmpty(), "random properties cite the draw");
            Canon.require(ht >= 0, "hit points are not negative: " + ht);
            Canon.require(damage != null && attack != null && dr != null, "a mob has its three rolls");
            Canon.require(loot != null, "a mob has its loot");
            Canon.require(!loot.random() || !draws.isEmpty(), "random loot cites the draw");
            variants = Canon.positional(variants, "variants");
            Canon.require(citation != null, "a mob carries its citation");
        }
    }

    // --- the spawn rotation (story 2.2)

    /** One member of a random family with its odds in thousandths and the expression the odds were read from. */
    public record Odds(String className, int perMille, String expression) {

        public Odds {
            className = Canon.text(className, "odds class name");
            Canon.require(perMille >= 1 && perMille <= 1000, "odds are thousandths above zero: " + perMille);
            expression = Canon.text(expression, "odds expression");
            Canon.require(!expression.isEmpty(), "odds carry their expression");
        }
    }

    /**
     * A random family the spawner draws from ({@code Shaman.random()}, {@code Elemental.random()}):
     * its class, its members with odds summing to a thousand, each named once, and the citation
     * of the method the odds were read from.
     */
    public record Family(String className, List<Odds> odds, Citation citation) {

        public Family {
            className = Canon.text(className, "family class name");
            odds = Canon.positional(odds, "odds");
            Canon.require(!odds.isEmpty(), "a family has members");
            int sum = 0;
            Set<String> names = new HashSet<>();
            for (Odds member : odds) {
                sum += member.perMille();
                Canon.require(names.add(member.className()), "a family member is listed once: " + member.className());
            }
            Canon.require(sum == 1000, "a family's odds sum to a thousand: " + sum);
            Canon.require(citation != null, "a family carries its citation");
        }
    }

    /**
     * One line of a depth's rotation: a class, how many times the spawner lists it, and whether
     * the class is a family the spawner draws from, listed under the rotation's families.
     */
    public record RotationEntry(String className, int count, boolean family) {

        public RotationEntry {
            className = Canon.text(className, "rotation class name");
            Canon.require(count >= 1, "a rotation entry is listed at least once: " + count);
        }
    }

    /** A depth's standard rotation, in the spawner's order, each class once, cited to its case. */
    public record RotationDepth(int depth, List<RotationEntry> entries, Citation citation) {

        public RotationDepth {
            Canon.require(depth >= 1, "a depth is one-based: " + depth);
            entries = Canon.positional(entries, "entries");
            Canon.require(!entries.isEmpty(), "a depth spawns something");
            Set<String> names = new HashSet<>();
            for (RotationEntry entry : entries) {
                Canon.require(names.add(entry.className()), "a depth lists a class once: " + entry.className());
            }
            Canon.require(citation != null, "a depth carries its citation");
        }
    }

    /** A rare mob the spawner adds to a depth's rotation with a chance, in thousandths. */
    public record RareMob(int depth, String className, int perMille, Citation citation) {

        public RareMob {
            Canon.require(depth >= 1, "a depth is one-based: " + depth);
            className = Canon.text(className, "rare class name");
            Canon.require(perMille >= 1 && perMille <= 1000, "a chance is thousandths above zero: " + perMille);
            Canon.require(citation != null, "a rare mob carries its citation");
        }
    }

    /**
     * A rare alternate the spawner may swap a rotation class for; {@code reachable} when the
     * class is listed as itself in some depth's rotation, which is where the swap looks.
     */
    public record RareAlt(String className, String alternate, boolean reachable, Citation citation) {

        public RareAlt {
            className = Canon.text(className, "alternate's class name");
            alternate = Canon.text(alternate, "alternate");
            Canon.require(citation != null, "an alternate carries its citation");
        }
    }

    /** A mob the champion roll excludes up to a depth. */
    public record Exclusion(String className, int maxDepth) {

        public Exclusion {
            className = Canon.text(className, "excluded class name");
            Canon.require(maxDepth >= 1, "an exclusion has a depth: " + maxDepth);
        }
    }

    /**
     * The champion roll as the game states it: the challenge that enables it, the buffs drawn at
     * equal odds (the generator holds the draw's range against their count), the exclusions by
     * depth, and the counter's expression.
     */
    public record ChampionRule(Challenge challenge, List<String> buffs, List<Exclusion> exclusions, String counterExpression,
                               Citation citation) {

        public ChampionRule {
            Canon.require(challenge != null, "the champion rule names its challenge");
            buffs = Canon.positional(buffs, "buffs");
            Canon.require(!buffs.isEmpty(), "the champion rule names its buffs");
            exclusions = Canon.sorted(exclusions, Comparator.comparing(Exclusion::className), "exclusions");
            counterExpression = Canon.text(counterExpression, "counter expression");
            Canon.require(citation != null, "the champion rule carries its citation");
        }
    }

    /**
     * The spawn rotation: every depth's standard rotation and the depth the spawner falls back
     * to for any other, the families, the rare additions, the alternate swap chance
     * (thousandths, with its expression, which a trinket multiplies) and the alternates, and the
     * champion rule.
     */
    public record SpawnRotation(List<RotationDepth> depths, int defaultDepth, List<Family> families, List<RareMob> rareMobs,
                                int alternateChancePerMille, String alternateChanceExpression, List<RareAlt> alternates,
                                ChampionRule champion) {

        public SpawnRotation {
            depths = Canon.sorted(depths, Comparator.comparingInt(RotationDepth::depth), "depths");
            Canon.distinctBy(depths, RotationDepth::depth, "depths");
            Canon.require(depths.stream().anyMatch(d -> d.depth() == defaultDepth), "the default depth is one of the depths: " + defaultDepth);
            families = Canon.sorted(families, Comparator.comparing(Family::className), "families");
            Set<String> familyNames = new HashSet<>();
            for (Family family : families) {
                Canon.require(familyNames.add(family.className()), "a family is listed once: " + family.className());
            }
            for (RotationDepth depth : depths) {
                for (RotationEntry entry : depth.entries()) {
                    Canon.require(!entry.family() || familyNames.contains(entry.className()),
                            "depth " + depth.depth() + " draws from a family the rotation does not list: " + entry.className());
                }
            }
            rareMobs = Canon.sorted(rareMobs, Comparator.comparingInt(RareMob::depth), "rare mobs");
            Canon.require(alternateChancePerMille >= 1 && alternateChancePerMille <= 1000, "a chance is thousandths above zero");
            alternateChanceExpression = Canon.text(alternateChanceExpression, "alternate chance expression");
            alternates = Canon.sorted(alternates, Comparator.comparing(RareAlt::className), "alternates");
            Canon.distinctBy(alternates, a -> a.className().hashCode(), "alternates");
            Canon.require(champion != null, "the rotation carries the champion rule");
        }
    }

    /** Refuses a table naming a key twice. */
    static <T> void distinct(Set<T> seen, T key, String what) {
        Canon.require(seen.add(key), what + " is listed twice: " + key);
    }
}
