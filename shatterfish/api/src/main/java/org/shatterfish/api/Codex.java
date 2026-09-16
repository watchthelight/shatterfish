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
        /** {@code Random.NormalIntRange(min, max)}: a normal-ish roll between the two. */
        NORMAL,
        /** {@code Random.IntRange(min, max)} or {@code Random.Int(min, max)}: a uniform roll. */
        UNIFORM,
        /** {@code return N}: the same every time; {@code min} and {@code max} are N. */
        CONSTANT,
        /** A formula, a weapon's roll, several returns: the expression is the text, and 2.5 measures it. */
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
            Canon.require(citation != null, "a roll carries its citation");
        }
    }

    /** What the loot field names: nothing, an item class, a generator category, an item instance, or a draw. */
    public enum LootKind {
        NONE, CLASS, CATEGORY, ITEM, RANDOM
    }

    /**
     * A mob's loot as declared: the kind and the name it names (empty for NONE), the chance as
     * thousandths when the declaration is a literal or a simple fraction and -1 otherwise, the
     * chance's expression, whether the loot itself is drawn at construction, and whether the
     * class overrides {@code createLoot()} or {@code lootChance()} so that the declared values are
     * not the whole story.
     */
    public record Loot(LootKind kind, String name, int chanceThousandths, String chanceExpression, boolean random,
                       boolean customLoot, boolean customChance, Citation citation) {

        public Loot {
            Canon.require(kind != null, "loot has a kind");
            name = Canon.text(name, "loot name");
            Canon.require((kind == LootKind.NONE) == name.isEmpty(), "loot names what it is, or nothing: " + kind + " " + name);
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
            Canon.require(!name.isEmpty(), "a field has a name");
        }
    }

    /**
     * A mob's stats under a depth or a challenge where they differ from the base at depth 1 with
     * no challenge: the depth (1 for a challenge variant), the challenge (NONE-less: empty for a
     * depth variant), and only the fields that differ, by name.
     */
    public record Variant(int depth, String challenge, List<Field> fields) {

        public Variant {
            Canon.require(depth >= 1, "a variant's depth is one-based: " + depth);
            challenge = Canon.text(challenge, "variant challenge");
            Canon.require(depth > 1 != !challenge.isEmpty(), "a variant is a depth's or a challenge's: " + depth + " " + challenge);
            fields = Canon.sorted(fields, Comparator.comparing(Field::name), "fields");
            Canon.require(!fields.isEmpty(), "a variant differs in at least one field");
        }
    }

    /** The names of a mob entry's numeric fields, the ones a variant or a Run dependency may name. */
    public static final List<String> MOB_FIELDS = List.of("ht", "defenseSkill", "exp", "maxLvl", "alignment", "properties");

    /**
     * One concrete mob class of the game, constructed at depth 1 with no challenge: its class name
     * (canonical, without the game's root package), alignment, properties (sorted; empty and
     * {@code propertiesRandom} when a draw at construction decides them), the fields whose value
     * the constructor reads from the hero of a Run ({@code runDependent}, dumped as zero and not
     * compared), hit points, defense skill, experience, maximum level, the three rolls, the
     * loot, the variants, and the citation of the class's declaration.
     */
    public record MobEntry(String className, Alignment alignment, List<String> properties, boolean propertiesRandom,
                           List<String> runDependent, int ht, int defenseSkill, int exp, int maxLvl, Roll damage, Roll attack,
                           Roll dr, Loot loot, List<Variant> variants, Citation citation) {

        public MobEntry {
            className = Canon.text(className, "mob class name");
            Canon.require(!className.isEmpty(), "a mob names its class");
            Canon.require(alignment != null, "a mob has an alignment");
            properties = Canon.sorted(properties, Comparator.naturalOrder(), "properties");
            Canon.require(!propertiesRandom || properties.isEmpty(), "random properties are not listed");
            runDependent = Canon.sorted(runDependent, Comparator.naturalOrder(), "run-dependent fields");
            Canon.require(MOB_FIELDS.containsAll(runDependent), "a run-dependent field is one of " + MOB_FIELDS + ": " + runDependent);
            Canon.require(ht >= 0, "hit points are not negative: " + ht);
            Canon.require(damage != null && attack != null && dr != null, "a mob has its three rolls");
            Canon.require(loot != null, "a mob has its loot");
            variants = Canon.positional(variants, "variants");
            Canon.require(citation != null, "a mob carries its citation");
        }
    }

    // --- the spawn rotation (story 2.2)

    /** One class of a random family with its odds in thousandths. */
    public record Odds(String className, int perMille) {

        public Odds {
            className = Canon.text(className, "odds class name");
            Canon.require(perMille >= 0 && perMille <= 1000, "odds are thousandths: " + perMille);
        }
    }

    /**
     * One line of a depth's rotation: a class, how many times the spawner lists it, and, for a
     * family the spawner draws from ({@code Shaman.random()}), the members with their odds.
     */
    public record RotationEntry(String className, int count, List<Odds> family) {

        public RotationEntry {
            className = Canon.text(className, "rotation class name");
            Canon.require(count >= 1, "a rotation entry is listed at least once: " + count);
            family = Canon.positional(family, "family");
        }
    }

    /** A depth's standard rotation, in the spawner's order, cited to its case. */
    public record RotationDepth(int depth, List<RotationEntry> entries, Citation citation) {

        public RotationDepth {
            Canon.require(depth >= 1, "a depth is one-based: " + depth);
            entries = Canon.positional(entries, "entries");
            Canon.require(!entries.isEmpty(), "a depth spawns something");
            Canon.require(citation != null, "a depth carries its citation");
        }
    }

    /** A rare mob the spawner adds to a depth's rotation with a chance, in thousandths. */
    public record RareMob(int depth, String className, int perMille, Citation citation) {

        public RareMob {
            Canon.require(depth >= 1, "a depth is one-based: " + depth);
            className = Canon.text(className, "rare class name");
            Canon.require(perMille >= 0 && perMille <= 1000, "a chance is thousandths: " + perMille);
            Canon.require(citation != null, "a rare mob carries its citation");
        }
    }

    /** A rare alternate the spawner may swap a rotation class for. */
    public record RareAlt(String className, String alternate, Citation citation) {

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
     * equal odds, the exclusions by depth, and the counter's expression.
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
     * The spawn rotation: every depth's standard rotation, the rare additions, the alternate swap
     * chance (thousandths, with its expression, which a trinket multiplies) and the alternates,
     * and the champion rule.
     */
    public record SpawnRotation(List<RotationDepth> depths, List<RareMob> rareMobs, int alternateChancePerMille,
                                String alternateChanceExpression, List<RareAlt> alternates, ChampionRule champion) {

        public SpawnRotation {
            depths = Canon.sorted(depths, Comparator.comparingInt(RotationDepth::depth), "depths");
            Canon.distinctBy(depths, RotationDepth::depth, "depths");
            rareMobs = Canon.sorted(rareMobs, Comparator.comparingInt(RareMob::depth), "rare mobs");
            Canon.require(alternateChancePerMille >= 0 && alternateChancePerMille <= 1000, "a chance is thousandths");
            alternateChanceExpression = Canon.text(alternateChanceExpression, "alternate chance expression");
            alternates = Canon.sorted(alternates, Comparator.comparing(RareAlt::className), "alternates");
            Canon.require(champion != null, "the rotation carries the champion rule");
        }
    }

    /** Refuses a table naming a key twice. */
    static <T> void distinct(Set<T> seen, T key, String what) {
        Canon.require(seen.add(key), what + " is listed twice: " + key);
    }
}
