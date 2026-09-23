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
     * The Codex version: 1 was the skeleton with the hero classes and the challenge flags; 2 added
     * the mobs and the spawn rotation (story 2.2); 3 added the items and the decks (story 2.3);
     * 4 added the guarantees, the tier tables and the rooms (story 2.4); 5 added the measured
     * combat tables (story 2.5); 6 added the traps, the recipes and the level structure (story 2.6);
     * 7 added the strings, the assets, the changelog and the journal's documents (story 2.7);
     * 8 adds the vanilla-versus-Shattered vocabulary diff (story 2.8).
     */
    public static final int VERSION = 8;

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

    // --- the decks and the items (story 2.3)

    /** One class of a category's deck with its weight in the first deck, the second deck (0 when the category has one deck) and their total. */
    public record Weighted(String className, int firstDeck, int secondDeck, int total) {

        public Weighted {
            className = Canon.text(className, "weighted class name");
            Canon.require(!className.isEmpty(), "a weighted class is named");
            Canon.require(firstDeck >= 0 && secondDeck >= 0, "a weight is not negative: " + firstDeck + ", " + secondDeck);
            Canon.require(total == firstDeck + secondDeck, "the total is the two decks' sum: " + total);
        }
    }

    /**
     * A generator category: its weight in each of the two category decks, the superclass it
     * draws, how many decks it draws its classes by (0 for a category the game draws another
     * way, whose classes are listed with zero weights; 1 for one deck; 2 for a deck with a
     * second weighting), its classes with their deck weights, the citation of the constant, of
     * the class list's assignment, and of each deck's weights (null where there is no such
     * deck).
     */
    public record CategoryEntry(String name, int firstProb, int secondProb, String superClass, int decks, List<Weighted> classes,
                                Citation citation, Citation classesCitation, Citation weightsCitation, Citation weights2Citation) {

        public CategoryEntry {
            name = Canon.text(name, "category name");
            Canon.require(!name.isEmpty(), "a category is named");
            Canon.require(firstProb >= 0 && secondProb >= 0, "a category weight is not negative");
            superClass = Canon.text(superClass, "category superclass");
            Canon.require(decks >= 0 && decks <= 2, "a category draws by no deck, one or two: " + decks);
            classes = Canon.positional(classes, "classes");
            Set<String> names = new HashSet<>();
            for (Weighted weighted : classes) {
                Canon.require(names.add(weighted.className()), "a class is listed once in a category: " + weighted.className());
                Canon.require(decks >= 1 || weighted.firstDeck() == 0, "a category with no deck weights nothing: " + weighted.className());
                Canon.require(decks == 2 || weighted.secondDeck() == 0, "a category with one deck has no second weight: " + weighted.className());
            }
            Canon.require(citation != null && classesCitation != null, "a category carries its citations");
            Canon.require((decks >= 1) == (weightsCitation != null), "a deck's weights are cited, and nothing else is");
            Canon.require((decks == 2) == (weights2Citation != null), "a second deck's weights are cited, and nothing else is");
        }
    }

    /**
     * One appearance label of an identifiable family: its key, the display name of the regular
     * family under it, the display name of the exotic family under it (empty for a family with
     * no exotics), the citation of the key and of each name.
     */
    public record Label(String key, String name, String exoticName, Citation citation, Citation nameCitation, Citation exoticCitation) {

        public Label {
            key = Canon.text(key, "label key");
            name = Canon.text(name, "label name");
            exoticName = Canon.text(exoticName, "exotic label name");
            Canon.require(!key.isEmpty() && !name.isEmpty(), "a label has a key and a name");
            Canon.require(citation != null && nameCitation != null, "a label carries its citations");
            Canon.require(exoticName.isEmpty() == (exoticCitation == null), "an exotic name is cited, and nothing else is");
        }
    }

    /** The appearance-label pool of one identifiable family, in the game's order, each key once. */
    public record LabelPool(String family, List<Label> labels, Citation citation) {

        public LabelPool {
            family = Canon.text(family, "label family");
            labels = Canon.positional(labels, "labels");
            Canon.require(!labels.isEmpty(), "a pool has labels");
            Set<String> keys = new HashSet<>();
            for (Label label : labels) {
                Canon.require(keys.add(label.key()), "a label is listed once: " + label.key());
            }
            Canon.require(citation != null, "a pool carries its citation");
        }
    }

    /** A regular consumable and the exotic the generator may swap it for. */
    public record ExoticPair(String regular, String exotic) {

        public ExoticPair {
            regular = Canon.text(regular, "regular class");
            exotic = Canon.text(exotic, "exotic class");
            Canon.require(!regular.isEmpty() && !exotic.isEmpty() && !regular.equals(exotic), "a pair is two classes");
        }
    }

    /** The exotic swap: the pairs, the chance's expression by trinket level, and the chance without the trinket in thousandths. */
    public record ExoticSwap(List<ExoticPair> pairs, String chanceExpression, int chanceWithoutTrinketPerMille, Citation citation) {

        public ExoticSwap {
            pairs = Canon.sorted(pairs, Comparator.comparing(ExoticPair::regular), "exotic pairs");
            chanceExpression = Canon.text(chanceExpression, "exotic chance expression");
            Canon.require(chanceWithoutTrinketPerMille >= 0 && chanceWithoutTrinketPerMille <= 1000, "a chance is thousandths");
            Canon.require(citation != null, "the swap carries its citation");
        }
    }

    /** The decks: every category, the label pools, the exotic swap. */
    public record Decks(List<CategoryEntry> categories, List<LabelPool> labelPools, ExoticSwap exotic) {

        public Decks {
            categories = Canon.positional(categories, "categories");
            Set<String> names = new HashSet<>();
            for (CategoryEntry category : categories) {
                Canon.require(names.add(category.name()), "a category is listed once: " + category.name());
            }
            labelPools = Canon.sorted(labelPools, Comparator.comparing(LabelPool::family), "label pools");
            Set<String> families = new HashSet<>();
            for (LabelPool pool : labelPools) {
                Canon.require(families.add(pool.family()), "a family has one pool: " + pool.family());
            }
            Canon.require(exotic != null, "the decks carry the exotic swap");
        }
    }

    /**
     * An item's strength requirement: whether it has one, its tier, the requirement at level 0,
     * the formula's text and the citation of the formula; a tier and a value of zero and an
     * empty formula when it has none.
     */
    public record Strength(boolean present, int tier, int atLevel0, String formula, Citation citation) {

        public Strength {
            formula = Canon.text(formula, "strength formula");
            Canon.require(present || (tier == 0 && atLevel0 == 0 && formula.isEmpty()), "an absent strength carries nothing");
            Canon.require(!present || (!formula.isEmpty() && citation != null), "a present strength carries its formula and citation");
        }

        /** No strength requirement. */
        public static Strength none() {
            return new Strength(false, 0, 0, "", null);
        }
    }

    /**
     * One concrete item class a player can meet: its class name, its display name from the
     * bundle with the line cited (the identified name; {@code customName} says the class or a
     * superclass overrides {@code name()}, so the screen may show another), the category whose
     * deck lists it (empty for none, the exotics among them, which are swapped in after a draw),
     * the quantity a fresh instance holds, its value at that quantity (-1 when read from a source
     * expression that is not a number), the value's expression (empty when the value was read
     * from an instance), its strength, the actions it offers a fresh instance, whether it was
     * constructed, the reason when it was not, and the citation of the class's declaration.
     */
    public record ItemEntry(String className, String name, boolean customName, Citation nameCitation, String category, int quantity, int value,
                            String valueExpression, Strength strength, List<String> actions, boolean constructed, String reason, Citation citation) {

        public ItemEntry {
            className = Canon.text(className, "item class name");
            Canon.require(!className.isEmpty(), "an item names its class");
            name = Canon.text(name, "item name");
            Canon.require(!name.isEmpty(), "an item has a display name");
            Canon.require(nameCitation != null, "an item's name is cited");
            category = Canon.text(category, "item category");
            Canon.require(quantity >= 1, "a fresh instance holds at least one: " + quantity);
            Canon.require(value >= -1, "a value is a number or -1: " + value);
            valueExpression = Canon.text(valueExpression, "value expression");
            Canon.require(constructed == valueExpression.isEmpty(), "a constructed item's value is its instance's; a read one carries the expression");
            Canon.require(strength != null, "an item says whether it has a strength requirement");
            actions = Canon.positional(actions, "actions");
            Set<String> offered = new HashSet<>();
            for (String action : actions) {
                Canon.require(offered.add(action), "an action is offered once: " + action);
            }
            reason = Canon.text(reason, "reason");
            Canon.require(constructed == reason.isEmpty(), "an unconstructed item says why");
            Canon.require(citation != null, "an item carries its citation");
        }
    }

    // --- the guarantees, the tiers and the rooms (story 2.4)

    /**
     * One state of a limited drop's schedule: at {@code depth} with the counter at {@code count},
     * the chance in thousandths that the game's method says the drop is needed, that the floor
     * places it (a boss floor places none), and that it places it under Forbidden Runes (every
     * second upgrade scroll is withheld; every other drop is unchanged).
     */
    public record ScheduleEntry(int depth, int count, int neededPerMille, int placedPerMille, int placedNoScrollsPerMille) {

        public ScheduleEntry {
            Canon.require(depth >= 1, "a depth is a floor: " + depth);
            Canon.require(count >= 0, "a counter is not negative: " + count);
            Canon.require(neededPerMille >= 0 && neededPerMille <= 1000, "a chance is thousandths: " + neededPerMille);
            Canon.require(placedPerMille == 0 || placedPerMille == neededPerMille,
                    "a floor either places what is needed or places nothing: " + placedPerMille + " of " + neededPerMille);
            Canon.require(placedNoScrollsPerMille == 0 || placedNoScrollsPerMille == placedPerMille,
                    "Forbidden Runes withholds the whole drop or none of it: " + placedNoScrollsPerMille + " of " + placedPerMille);
        }
    }

    /**
     * A limited drop the level's creation decides: its counter's name, the item, the method, whether
     * it drops once in a Run (else {@code perSet} per floor set), the method's text and citation,
     * the placement's citation, and the schedule over every depth and counter state.
     */
    public record DropSchedule(String name, String item, String method, boolean once, int perSet, String expression, Citation citation,
                               Citation placementCitation, List<ScheduleEntry> entries) {

        public DropSchedule {
            name = Canon.text(name, "drop name");
            item = Canon.text(item, "drop item");
            method = Canon.text(method, "drop method");
            expression = Canon.text(expression, "drop expression");
            Canon.require(!name.isEmpty() && !item.isEmpty() && !method.isEmpty() && !expression.isEmpty(), "a drop is named, with its item, method and text");
            Canon.require(perSet >= 1, "a drop comes at least once: " + perSet);
            Canon.require(!once || perSet == 1, "a once-only drop comes once");
            Canon.require(citation != null && placementCitation != null, "a drop carries its citations");
            entries = Canon.positional(entries, "schedule entries");
            Canon.require(!entries.isEmpty(), "a schedule has entries");
            int depths = 0;
            int counts = 0;
            for (ScheduleEntry entry : entries) {
                depths = Math.max(depths, entry.depth());
                counts = Math.max(counts, entry.count());
            }
            Canon.require(entries.size() == depths * (counts + 1),
                    "a schedule covers every depth and counter state it reaches: " + entries.size() + " of " + depths + " by " + (counts + 1));
            Set<Long> states = new HashSet<>();
            for (ScheduleEntry entry : entries) {
                Canon.require(states.add(((long) entry.depth() << 32) | entry.count()), "a state once: depth " + entry.depth() + " count " + entry.count());
            }
        }
    }

    /**
     * What the main branch builds at one depth: the level class the game names in its own
     * switch, and whether that class empties the floor's spawn list, which is what places a
     * guaranteed drop. A depth whose level does not (the amulet floor, a boss floor) places
     * none however the counter stands.
     */
    public record Placement(int depth, String levelClass, boolean placesSpawnList, Citation citation) {

        public Placement {
            Canon.require(depth >= 1, "a depth is a floor: " + depth);
            levelClass = Canon.text(levelClass, "level class");
            Canon.require(!levelClass.isEmpty() && citation != null, "a placement names its level class and is cited");
        }
    }

    /**
     * The guarantees: every limited-drop counter the game keeps, the boss depths, the gate the
     * level's creation applies (the main branch only, a boss floor never), the Forbidden Runes
     * rule, what each depth of the main branch builds, and the schedules.
     */
    public record Guarantees(List<String> counters, Citation countersCitation, List<Integer> bossDepths, Citation bossCitation,
                             String gateExpression, Citation placementCitation, String noScrollsExpression, Citation noScrollsCitation,
                             List<Placement> placements, List<DropSchedule> drops) {

        public Guarantees {
            gateExpression = Canon.text(gateExpression, "gate expression");
            Canon.require(!gateExpression.isEmpty(), "the placement gate has text");
            placements = Canon.positional(placements, "placements");
            Canon.require(!placements.isEmpty(), "the main branch builds floors");
            for (int i = 0; i < placements.size(); i++) {
                Canon.require(placements.get(i).depth() == i + 1, "the placements are the depths in order from one");
            }
            counters = Canon.positional(counters, "counters");
            Canon.require(!counters.isEmpty(), "the game keeps counters");
            Set<String> names = new HashSet<>();
            for (String counter : counters) {
                Canon.require(names.add(counter), "a counter once: " + counter);
            }
            bossDepths = Canon.sorted(bossDepths, Comparator.naturalOrder(), "boss depths");
            Canon.noRepeats(bossDepths, "boss depths");
            for (int depth : bossDepths) {
                Canon.require(depth >= 1, "a boss depth is a floor: " + depth);
            }
            Canon.require(countersCitation != null && bossCitation != null && placementCitation != null && noScrollsCitation != null,
                    "the guarantees carry their citations");
            noScrollsExpression = Canon.text(noScrollsExpression, "Forbidden Runes expression");
            Canon.require(!noScrollsExpression.isEmpty(), "the Forbidden Runes rule has text");
            drops = Canon.positional(drops, "drops");
            Set<String> dropNames = new HashSet<>();
            for (DropSchedule drop : drops) {
                Canon.require(dropNames.add(drop.name()), "a drop once: " + drop.name());
                Canon.require(names.contains(drop.name()), "a drop's counter is one the game keeps: " + drop.name());
            }
        }
    }

    /** One floor set's tier weights: the set, the depths it covers, the five weights by tier. */
    public record TierRow(int floorSet, int depthFrom, int depthTo, List<Integer> weights) {

        public TierRow {
            Canon.require(floorSet >= 0, "a floor set is not negative");
            Canon.require(depthFrom >= 1 && depthTo >= depthFrom, "a floor set covers depths: " + depthFrom + "-" + depthTo);
            weights = Canon.positional(weights, "tier weights");
            Canon.require(weights.size() == 5, "five tiers");
            int sum = 0;
            for (int weight : weights) {
                Canon.require(weight >= 0, "a weight is not negative");
                sum += weight;
            }
            Canon.require(sum > 0, "a floor set draws some tier");
        }
    }

    /** A rule read as text: what it decides, the text, the citation. */
    public record Rule(String what, String expression, Citation citation) {

        public Rule {
            what = Canon.text(what, "rule name");
            expression = Canon.text(expression, "rule expression");
            Canon.require(!what.isEmpty() && !expression.isEmpty() && citation != null, "a rule is named, with its text and citation");
        }
    }

    /**
     * The tier tables: the rows by floor set, the literal's citation, the gate every draw
     * applies, the armor, weapon and missile rules, and the tier arrays the weapon and missile
     * rules index, each cited to its own declaration.
     */
    public record Tiers(List<TierRow> rows, Citation citation, Rule gate, Rule armor, Rule weapon, Rule missile, List<Rule> arrays) {

        public Tiers {
            arrays = Canon.positional(arrays, "tier arrays");
            Set<String> named = new HashSet<>();
            for (Rule array : arrays) {
                Canon.require(named.add(array.what()), "a tier array once: " + array.what());
            }
            rows = Canon.positional(rows, "tier rows");
            Canon.require(!rows.isEmpty(), "the table has rows");
            for (int i = 0; i < rows.size(); i++) {
                Canon.require(rows.get(i).floorSet() == i, "the rows are the floor sets in order");
                Canon.require(i == 0 || rows.get(i).depthFrom() == rows.get(i - 1).depthTo() + 1, "the depth ranges abut");
            }
            Canon.require(citation != null && gate != null && armor != null && weapon != null && missile != null, "the tiers carry their citation and rules");
        }
    }

    /**
     * An item a room puts on the floor: the class, how many, whether the room drops it on its own
     * cells ({@code floorDrop}) rather than adding it to the level's spawn list, whether it does
     * so under a condition the reader does not evaluate ({@code conditional}, so the count is how
     * many statements put it there and not how many a floor gets), and the citation of the first
     * line that does so. A spawn-list item is placed anywhere on the floor; a floor drop is
     * placed in the room. An item added to the spawn list is never conditional: the reader
     * refuses one it cannot count, since a guaranteed drop that is not guaranteed is worse than
     * no entry.
     */
    public record Spawn(String className, int count, boolean floorDrop, boolean conditional, Citation citation) {

        public Spawn {
            className = Canon.text(className, "spawned class");
            Canon.require(!className.isEmpty() && count >= 1 && citation != null, "a spawn names its class, counts at least one and is cited");
        }
    }

    /** A draw a room makes, as cited text. */
    public record Draw(String expression, Citation citation) {

        public Draw {
            expression = Canon.text(expression, "draw expression");
            Canon.require(!expression.isEmpty() && citation != null, "a draw has text and a citation");
        }
    }

    /**
     * One special or secret room: its class, whether it is a secret room, the items it puts on
     * the floor, the draws its painting makes (every line that asks the generator, picks by
     * chances or makes an instance by class, as cited text), and the citation of its
     * declaration.
     */
    public record RoomEntry(String className, boolean secret, List<Spawn> spawns, List<Draw> draws, Citation citation) {

        public RoomEntry {
            className = Canon.text(className, "room class");
            Canon.require(!className.isEmpty() && citation != null, "a room names its class and is cited");
            spawns = Canon.sorted(spawns, Comparator.comparing(Spawn::className), "spawns");
            Set<String> classes = new HashSet<>();
            for (Spawn spawn : spawns) {
                Canon.require(classes.add(spawn.className() + (spawn.floorDrop() ? " dropped" : " spawned")),
                        "a spawned class once per placement: " + spawn.className());
                Canon.require(spawn.floorDrop() || !spawn.conditional(), "an item added to the spawn list is not conditional: " + spawn.className());
            }
            draws = Canon.positional(draws, "draws");
        }
    }

    /** One of the game's room lists: its name, its members in the game's order, the citation of the literal. */
    public record RoomList(String name, List<String> members, Citation citation) {

        public RoomList {
            name = Canon.text(name, "list name");
            members = Canon.positional(members, "members");
            Canon.require(!name.isEmpty() && !members.isEmpty() && citation != null, "a list is named, has members and is cited");
            Set<String> seen = new HashSet<>();
            for (String member : members) {
                Canon.require(seen.add(member), "a member once: " + member);
            }
        }
    }

    /**
     * The rooms: the specials, the secrets, the game's lists, the base count of secret rooms per
     * region times a thousand (the whole part is the count the region gets and the fraction is
     * the chance of one more, rolled once per Run), and the queue rule.
     */
    public record Rooms(List<RoomEntry> specials, List<RoomEntry> secrets, List<RoomList> lists, List<Integer> baseSecretsPerRegionThousandths,
                        Citation secretsCitation, Rule queue) {

        public Rooms {
            specials = Canon.sorted(specials, Comparator.comparing(RoomEntry::className), "specials");
            secrets = Canon.sorted(secrets, Comparator.comparing(RoomEntry::className), "secrets");
            Canon.noRepeats(specials.stream().map(RoomEntry::className).toList(), "specials");
            Canon.noRepeats(secrets.stream().map(RoomEntry::className).toList(), "secrets");
            for (RoomEntry room : specials) {
                Canon.require(!room.secret(), "a special is not a secret: " + room.className());
            }
            for (RoomEntry room : secrets) {
                Canon.require(room.secret(), "a secret is a secret: " + room.className());
            }
            lists = Canon.positional(lists, "lists");
            Set<String> names = new HashSet<>();
            Set<String> known = new HashSet<>();
            specials.forEach(r -> known.add(r.className()));
            secrets.forEach(r -> known.add(r.className()));
            for (RoomList list : lists) {
                Canon.require(names.add(list.name()), "a list once: " + list.name());
                for (String member : list.members()) {
                    Canon.require(known.contains(member), "a listed room is in the table: " + member);
                }
            }
            baseSecretsPerRegionThousandths = Canon.positional(baseSecretsPerRegionThousandths, "secrets per region");
            Canon.require(!baseSecretsPerRegionThousandths.isEmpty(), "the secrets per region are given");
            for (int perRegion : baseSecretsPerRegionThousandths) {
                Canon.require(perRegion >= 0, "secrets per region are not negative");
            }
            Canon.require(secretsCitation != null && queue != null, "the rooms carry their citation and the queue rule");
        }
    }

    // --- the measured combat tables (story 2.5)

    /** One axis a measurement swept: what it varied, from and to inclusive, and the step. */
    public record Grid(String what, int from, int to, int step) {

        public Grid {
            what = Canon.text(what, "grid axis");
            Canon.require(!what.isEmpty(), "an axis is named");
            Canon.require(step >= 1, "a step is positive: " + step);
            Canon.require(to >= from, "an axis runs forwards: " + from + " to " + to);
        }

        /** How many values the axis takes. */
        public int size() {
            return (to - from) / step + 1;
        }
    }

    /** One measured cell of the hit table: the two stats, how many attacks were made, and how many landed, in thousandths. */
    public record HitCell(int accuracy, int evasion, int samples, int hitPerMille) {

        public HitCell {
            Canon.require(accuracy >= 0 && evasion >= 0, "a stat is not negative");
            Canon.require(samples >= 1, "a cell was sampled: " + samples);
            Canon.require(hitPerMille >= 0 && hitPerMille <= 1000, "a share is thousandths: " + hitPerMille);
        }
    }

    /**
     * The hit table: the method that decides whether an attack lands, its citation, the two axes a
     * measurement sweeps, whether it was measured, and, when it was, every cell in the order the
     * grid gives them (accuracy outer, evasion inner). A table that was not measured carries no
     * cell and says why, as an item that could not be constructed does: a consumer is told what
     * the Codex does not know rather than handed a number nobody ran.
     */
    public record HitTable(String method, Citation citation, Grid accuracy, Grid evasion, boolean measured, String reason, List<HitCell> cells) {

        public HitTable {
            method = Canon.text(method, "hit method");
            Canon.require(!method.isEmpty() && citation != null, "the hit table names the method and cites it");
            Canon.require(accuracy != null && evasion != null, "the hit table carries its axes");
            reason = Canon.text(reason, "reason");
            Canon.require(measured == reason.isEmpty(), "an unmeasured table says why, a measured one does not");
            cells = Canon.positional(cells, "hit cells");
            Canon.require(!measured || cells.size() == accuracy.size() * evasion.size(),
                    "a measured table covers its grid: " + cells.size() + " of " + accuracy.size() + " by " + evasion.size());
            Canon.require(measured || cells.isEmpty(), "an unmeasured table carries no cell");
            Set<Long> seen = new HashSet<>();
            for (HitCell cell : cells) {
                Canon.require(seen.add(((long) cell.accuracy() << 32) | cell.evasion()),
                        "a cell once: accuracy " + cell.accuracy() + " evasion " + cell.evasion());
            }
        }
    }

    /** A measured distribution: the least and greatest value seen, the mean in thousandths, and the samples behind them. */
    public record Spread(int min, int max, int meanPerMille, int samples) {

        public Spread {
            Canon.require(max >= min, "a spread runs forwards: " + min + " to " + max);
            Canon.require(samples >= 1, "a spread was sampled: " + samples);
            Canon.require(meanPerMille >= min * 1000 && meanPerMille <= max * 1000,
                    "a mean lies inside its bounds: " + meanPerMille + " in " + (min * 1000) + " to " + (max * 1000));
        }
    }

    /**
     * One measured roll: the class rolled, its tier and level (zero where the roll has neither),
     * what was measured, the method that was run and its citation.
     */
    public record RollEntry(String className, int tier, int level, Spread spread, String method, Citation citation) {

        public RollEntry {
            className = Canon.text(className, "rolled class");
            method = Canon.text(method, "roll method");
            Canon.require(!className.isEmpty() && !method.isEmpty(), "a roll names its class and its method");
            Canon.require(tier >= 0 && level >= 0, "a tier and a level are not negative");
            Canon.require(spread != null && citation != null, "a roll carries its spread and its citation");
        }
    }

    /**
     * The measured combat tables: the generator seed every measurement drew under, the hit table,
     * and the rolls of the weapons, the armours and the mobs, each sorted by class, tier and
     * level.
     */
    public record Combat(long seed, HitTable hit, List<RollEntry> weapons, List<RollEntry> armours, List<RollEntry> mobs) {

        public Combat {
            Canon.require(hit != null, "the combat tables carry the hit table");
            weapons = rolls(weapons, "weapons");
            armours = rolls(armours, "armours");
            mobs = rolls(mobs, "mobs");
        }

        private static List<RollEntry> rolls(List<RollEntry> in, String what) {
            List<RollEntry> sorted = Canon.sorted(in, Comparator.comparing(RollEntry::className)
                    .thenComparingInt(RollEntry::tier).thenComparingInt(RollEntry::level), what);
            Set<String> seen = new HashSet<>();
            for (RollEntry entry : sorted) {
                Canon.require(seen.add(entry.className() + "@" + entry.level()), "a class and level once: " + entry.className() + " +" + entry.level());
            }
            return sorted;
        }
    }

    // --- the traps, the recipes and the level structure (story 2.6)

    /**
     * One trap: its class, its display name with the bundle line cited, whether the level may
     * hide it and whether a player may find it by searching, the text of its own effect (which
     * needs a level to run, so the table carries what it says and not what it does), and the
     * citation of its declaration.
     */
    public record TrapEntry(String className, String name, String nameFrom, Citation nameCitation, boolean canBeHidden,
                            boolean canBeSearched, boolean active, String activateFrom, String activateExpression,
                            Citation activateCitation, Citation citation, List<Rule> alsoOnTheCell) {

        public TrapEntry {
            className = Canon.text(className, "trap class");
            name = Canon.text(name, "trap name");
            nameFrom = Canon.text(nameFrom, "the class a name was read from");
            activateFrom = Canon.text(activateFrom, "the class an effect was read from");
            activateExpression = Canon.text(activateExpression, "trap effect");
            Canon.require(!className.isEmpty() && !name.isEmpty(), "a trap names its class and itself");
            Canon.require(nameCitation != null && citation != null && activateCitation != null, "a trap carries its citations");
            Canon.require(!nameFrom.equals(className), "a name read from the trap's own class says so by naming no other");
            Canon.require(!activateFrom.equals(className), "an effect read from the trap's own class says so by naming no other");
            alsoOnTheCell = Canon.positional(alsoOnTheCell, "what else stands on a trap's cell");
            // The same blob can be seeded from two places in one class, so a row is one place, not
            // one thing: the pair of what it is and where it was read is what must be distinct.
            Set<String> seen = new HashSet<>();
            for (Rule rule : alsoOnTheCell) {
                Canon.require(seen.add(rule.what() + " " + rule.citation().reference()),
                        "one row per place something reaches the cell: " + rule.what());
            }
        }
    }

    /**
     * One page of a journal document (story 2.7): the name the game keeps it under, and the title
     * and body the bundle gives it.
     */
    public record DocumentPage(String page, String title, String body, Citation titleCitation, Citation citation) {

        public DocumentPage {
            page = Canon.text(page, "page name");
            title = Canon.text(title, "page title");
            body = Canon.text(body, "page body");
            Canon.require(!page.isEmpty() && !title.isEmpty() && !body.isEmpty(), "a page names itself and says something");
            Canon.require(titleCitation != null && citation != null, "a page cites its title and its body");
        }
    }

    /**
     * One journal document (story 2.7): whether it is lore rather than a guide, its title, the hint
     * the game shows before it is found where the bundle gives one (a guide is handed over and has
     * none), and its pages in the order the game keeps them, which is the order a page index means.
     */
    public record DocumentEntry(String document, boolean lore, String title, Citation titleCitation, String hint,
                                Citation hintCitation, List<DocumentPage> pages, Citation citation) {

        public DocumentEntry {
            document = Canon.text(document, "document name");
            title = Canon.text(title, "document title");
            hint = Canon.text(hint, "document hint");
            Canon.require(!document.isEmpty() && !title.isEmpty(), "a document names itself and its title");
            Canon.require(citation != null && titleCitation != null, "a document cites itself and its title");
            Canon.require(hint.isEmpty() == (hintCitation == null), "a document that shows a hint cites it");
            // A guide is handed to the hero and a lore document must be found, so the game gives a
            // hint to exactly the ones that must be found. A hint on a guide, or a lore document
            // without one, means the bundle moved and the table would be guessing which.
            Canon.require(lore == !hint.isEmpty(), "a document a player must find says where to look, and a guide does not");
            pages = Canon.positional(pages, "pages");
            Canon.require(!pages.isEmpty(), "a document holds a page");
            Set<String> seen = new HashSet<>();
            for (DocumentPage page : pages) {
                Canon.require(seen.add(page.page()), "a page once per document: " + page.page());
            }
        }
    }

    /**
     * One heading under a changelog entry (story 2.7): its title, the bundle key the game named it
     * by where it named one rather than writing it, every date its text states, and the condition
     * the game shows it under where it shows it only under one. The texts themselves are the game's
     * release notes and are not carried; the dates in them are.
     */
    public record ChangeHeading(String title, String titleKey, String titleExpression, List<String> dates,
                                String conditionExpression, Citation citation) {

        public ChangeHeading {
            title = Canon.text(title, "heading title");
            titleKey = Canon.text(titleKey, "the bundle key a title came from");
            titleExpression = Canon.text(titleExpression, "the expression a title is computed by");
            conditionExpression = Canon.text(conditionExpression, "the condition a heading is shown under");
            Canon.require(citation != null, "a heading is cited");
            Canon.require(title.isEmpty() != titleExpression.isEmpty(),
                    "a heading carries its title or the expression that computes one, never both and never neither");
            Canon.require(titleKey.isEmpty() || !title.isBlank(), "a title named by a bundle key has that bundle's words");
            dates = Canon.positional(dates, "the dates a heading states");
            Set<String> seen = new HashSet<>();
            for (String date : dates) {
                Canon.require(!date.isBlank(), "a date is a phrase the game wrote");
                Canon.require(seen.add(date), "a date once per heading: " + date);
            }
        }
    }

    /**
     * One entry of the game's own changelog (story 2.7): the class that writes it, the tab the game
     * shows it under (0 is the newest), its title, whether the game shows it as a major heading, its
     * own text and every date that text states, the condition it is shown under where it has one,
     * and the headings under it. The title is the argument the game passes; the game title-cases it
     * before drawing it ({@code Messages.titleCase}), so what a player reads is that rule applied
     * to this text.
     */
    public record ChangeEntry(String className, int tab, String title, String titleKey, String titleExpression, boolean major,
                              String text, List<String> dates, String conditionExpression,
                              List<ChangeHeading> headings, Citation citation) {

        public ChangeEntry {
            className = Canon.text(className, "the class that writes an entry");
            title = Canon.text(title, "entry title");
            titleKey = Canon.text(titleKey, "the bundle key a title came from");
            titleExpression = Canon.text(titleExpression, "the expression a title is computed by");
            text = Canon.text(text, "entry text");
            conditionExpression = Canon.text(conditionExpression, "the condition an entry is shown under");
            Canon.require(!className.isEmpty(), "an entry names the class that writes it");
            Canon.require(tab >= 0, "an entry is shown under a tab of the game: " + tab);
            // The game writes an entry whose title is one space, as a spacer between two blocks of
            // changes. That is a title it passes, so the table carries it as read; what it may not
            // carry is a blank title that also claims to come from a bundle.
            Canon.require(title.isEmpty() != titleExpression.isEmpty(),
                    "an entry carries its title or the expression that computes one, never both and never neither");
            Canon.require(titleKey.isEmpty() || !title.isBlank(), "a title named by a bundle key has that bundle's words");
            Canon.require(citation != null, "an entry is cited");
            dates = Canon.positional(dates, "the dates an entry states");
            Set<String> seen = new HashSet<>();
            for (String date : dates) {
                Canon.require(text.contains(date), "an entry's date is its own text's: " + date);
                Canon.require(seen.add(date), "a date once per entry: " + date);
            }
            headings = Canon.positional(headings, "headings");
        }
    }

    /**
     * What version the pinned tree builds as (story 2.7), and the save codes the game still names:
     * a claim about a mechanic can be dated against the version that made it.
     */
    public record VersionRecord(String name, int code, List<Rule> saveCodes, Citation citation) {

        public VersionRecord {
            name = Canon.text(name, "version name");
            Canon.require(!name.isEmpty() && code > 0 && citation != null, "the version names itself, counts and is cited");
            saveCodes = Canon.positional(saveCodes, "save codes");
            Canon.require(!saveCodes.isEmpty(), "the game names the saves it reads");
            Set<String> seen = new HashSet<>();
            for (Rule saveCode : saveCodes) {
                Canon.require(seen.add(saveCode.what()), "a save code once: " + saveCode.what());
            }
        }
    }

    /**
     * One asset the game names (story 2.7): its path under the game's own asset folder, the group
     * of the asset class that holds it and the constant's name where a constant names it, which of
     * the game's asset folders holds the file (empty when none does), and the line that names it.
     */
    public record AssetEntry(String path, String group, String constant, String assetRoot, Citation citation) {

        public AssetEntry {
            path = Canon.text(path, "asset path");
            group = Canon.text(group, "asset group");
            constant = Canon.text(constant, "asset constant");
            assetRoot = Canon.text(assetRoot, "the folder that holds a file");
            Canon.require(!path.isEmpty() && citation != null, "an asset names its path and is cited");
            Canon.require(group.isEmpty() == constant.isEmpty(), "an asset named by a constant names the group that holds it");
        }

        /** Whether a file is actually behind the name, which is the folder being known. */
        public boolean present() {
            return !assetRoot.isEmpty();
        }
    }

    /**
     * One game's side of a name in the vocabulary diff (story 2.8): what that game spells it, the
     * classes that carry it there (more than one where a game reuses a name), what that game
     * states about it, and where each of those was read.
     */
    public record VocabularySide(String name, List<String> classNames, List<Rule> facts, List<Citation> citations) {

        public VocabularySide {
            name = Canon.text(name, "a name one game gives");
            Canon.require(!name.isEmpty(), "a side spells the name it gives");
            classNames = Canon.positional(classNames, "the classes that carry a name");
            citations = Canon.positional(citations, "where a name was read");
            facts = Canon.positional(facts, "what a game states about a name");
            Canon.require(!classNames.isEmpty(), "a side names the class that carries it");
            Canon.require(classNames.size() == citations.size(), "a side cites every class it names");
        }
    }

    /**
     * One mechanic two games both state about one name, and how they differ (story 2.8).
     * {@code comparable} is false where the two write the fact in shapes that cannot be set
     * against each other — a number on one side and a method's text on the other — in which case
     * the row carries what each says and judges nothing.
     */
    public record MechanicDifference(String what, String here, String there, boolean comparable) {

        public MechanicDifference {
            what = Canon.text(what, "the mechanic that differs");
            here = Canon.text(here, "what this game states");
            there = Canon.text(there, "what the other game states");
            Canon.require(!what.isEmpty(), "a difference names the mechanic");
            Canon.require(!here.isEmpty() && !there.isEmpty(), "a difference is between two statements");
            Canon.require(!comparable || !here.equals(there), "two statements that agree are not a difference");
        }
    }

    /**
     * One name in the vocabulary diff (story 2.8): the kind of thing it names, the name itself as
     * the join is made (lower-cased, since one game capitalises and the other does not), each
     * game's side where that game has it, and the mechanics the two state differently.
     */
    public record VocabularyEntry(String kind, String name, VocabularySide here, VocabularySide there,
                                  List<MechanicDifference> differences) {

        public VocabularyEntry {
            kind = Canon.text(kind, "the kind of thing a name names");
            name = Canon.text(name, "a name");
            Canon.require(!kind.isEmpty() && !name.isEmpty(), "a row names a kind and a name");
            Canon.require(here != null || there != null, "a row is a name at least one game gives");
            differences = Canon.positional(differences, "the mechanics two games state differently");
            Canon.require(differences.isEmpty() || (here != null && there != null),
                    "only a name both games give can have a difference between them");
            Set<String> seen = new HashSet<>();
            for (MechanicDifference difference : differences) {
                Canon.require(seen.add(difference.what()), "a mechanic once per row: " + difference.what());
            }
        }

        /** Whether both games give this name, which is what makes a difference meaningful. */
        public boolean shared() {
            return here != null && there != null;
        }
    }

    /**
     * The vocabulary diff (story 2.8): every name either game gives a mob or an item, each game's
     * tag, and what the table is for. Nothing reads it yet, which the table says of itself.
     */
    public record Vocabulary(List<VocabularyEntry> entries, String tag, String vanillaTag, String consumer) {

        public Vocabulary {
            tag = Canon.text(tag, "this game's tag");
            vanillaTag = Canon.text(vanillaTag, "the other game's tag");
            consumer = Canon.text(consumer, "what the diff is for");
            Canon.require(!tag.isEmpty() && !vanillaTag.isEmpty(), "the diff names both pinned sources");
            Canon.require(!consumer.isEmpty(), "the diff says what it is for");
            entries = Canon.positional(entries, "the names either game gives");
            Canon.require(!entries.isEmpty(), "the two games name things");
            Set<String> seen = new HashSet<>();
            for (VocabularyEntry entry : entries) {
                Canon.require(seen.add(entry.kind() + " " + entry.name()), "a name once per kind: " + entry.name());
            }
        }
    }

    /**
     * One line of the game's own text (story 2.7): the key, its value, the bundle it was found in,
     * the class the game's key rule names and the suffix the caller asks for, or an empty class
     * and the reason where the key names no class at all.
     */
    public record StringEntry(String key, String value, String bundle, String className, String suffix,
                              String reason, Citation citation) {

        public StringEntry {
            key = Canon.text(key, "text key");
            value = Canon.text(value, "text value");
            bundle = Canon.text(bundle, "bundle");
            className = Canon.text(className, "the class a key names");
            suffix = Canon.text(suffix, "the suffix a key asks for");
            reason = Canon.text(reason, "why a key names no class");
            Canon.require(!key.isEmpty() && !bundle.isEmpty() && !suffix.isEmpty(), "a line names its key, its bundle and its suffix");
            Canon.require(citation != null, "a line is cited");
            Canon.require(className.isEmpty() == !reason.isEmpty(), "a key names a class or says why it names none");
            Canon.require(key.endsWith(suffix), "a suffix is the end of its key: " + key + ", " + suffix);
        }
    }

    /**
     * The traps one level class draws, with the weight it gives each, and the condition that
     * chooses this pool where the level has more than one (the sewers draw one trap on the first
     * floor and eleven after it); empty where the level draws one pool always.
     */
    public record TrapPool(String levelClass, String declaredBy, String condition, List<Weighted> traps,
                           String nTrapsExpression, Citation nTrapsCitation, Citation citation) {

        public TrapPool {
            levelClass = Canon.text(levelClass, "level class");
            declaredBy = Canon.text(declaredBy, "the class a pool was read from");
            condition = Canon.text(condition, "pool condition");
            nTrapsExpression = Canon.text(nTrapsExpression, "how many traps a floor lays");
            Canon.require(!levelClass.isEmpty() && citation != null, "a pool names its level and is cited");
            Canon.require(!declaredBy.equals(levelClass), "a pool read from the level's own class says so by naming no other");
            Canon.require(!nTrapsExpression.isEmpty() && nTrapsCitation != null, "a pool says how many traps the floor lays, and cites it");
            traps = Canon.positional(traps, "traps");
            Canon.require(!traps.isEmpty(), "a pool draws something");
            Set<String> seen = new HashSet<>();
            for (Weighted trap : traps) {
                Canon.require(seen.add(trap.className()), "a trap once per pool: " + trap.className());
            }
        }
    }

    /** One input of a recipe: the class and how many of it. */
    public record Ingredient(String className, int quantity) {

        public Ingredient {
            className = Canon.text(className, "ingredient class");
            Canon.require(!className.isEmpty() && quantity >= 1, "an ingredient names its class and counts at least one");
        }
    }

    /**
     * One recipe the pot knows: its class, which registry holds it (by how many ingredients it
     * takes), whether it states its inputs and output as fixed lists, those lists with the
     * quantities and the energy cost where it does, the text of the methods it answers with where
     * it does not, the citation of the registry and of its own declaration.
     */
    public record RecipeEntry(String className, String ingredients, boolean simple, List<Ingredient> inputs, String output, int outQuantity,
                              int cost, List<Rule> answers, Citation registryCitation, Citation citation) {

        public RecipeEntry {
            className = Canon.text(className, "recipe class");
            ingredients = Canon.text(ingredients, "recipe registry");
            output = Canon.text(output, "recipe output");
            Canon.require(!className.isEmpty() && !ingredients.isEmpty(), "a recipe names its class and its registry");
            Canon.require(registryCitation != null && citation != null, "a recipe carries its citations");
            inputs = Canon.positional(inputs, "inputs");
            answers = Canon.positional(answers, "answers");
            Canon.require(simple == !inputs.isEmpty(), "a recipe that states its inputs has them, and one that does not has none");
            Canon.require(simple == !output.isEmpty(), "a recipe that states its inputs names its output, and one that does not names none");
            Canon.require(simple == answers.isEmpty(), "a recipe that states no inputs carries its methods, and one that does carries none");
            Canon.require(!simple || outQuantity >= 1, "a stated recipe makes at least one");
            Canon.require(cost >= -1, "a cost is stated or -1");
            Set<String> seen = new HashSet<>();
            for (Ingredient input : inputs) {
                Canon.require(seen.add(input.className()), "an input once per recipe: " + input.className());
            }
        }
    }

    /**
     * What one depth of one branch builds: the level class, and whether it holds a shop, is a boss
     * floor, or seals behind the hero.
     */
    public record LevelEntry(int depth, int branch, String levelClass, boolean shop, Citation shopCitation, boolean boss,
                            boolean sealed, String sealedBy, Citation sealCitation, Citation citation) {

        public LevelEntry {
            Canon.require(depth >= 1 && branch >= 0, "a floor is a depth of a branch: " + depth + ", " + branch);
            levelClass = Canon.text(levelClass, "level class");
            sealedBy = Canon.text(sealedBy, "how a floor seals");
            Canon.require(!levelClass.isEmpty() && citation != null, "a floor names its level class and is cited");
            Canon.require(shopCitation != null, "a floor cites where its shop was decided");
            Canon.require(sealed == !sealedBy.isEmpty(), "a floor that seals says how, and one that does not says nothing");
            Canon.require(sealed == (sealCitation != null), "a floor that seals cites the line that seals it");
        }
    }

    /**
     * One level feeling: the chance the game's own roll gives it in thousandths, the arm that sets
     * it as cited text, and every place the game reads it afterwards, since a feeling whose effect
     * is not in the arm that sets it has one somewhere else.
     */
    public record FeelingEntry(String what, int chancePerMille, String expression, Citation citation, List<Rule> effects) {

        public FeelingEntry {
            what = Canon.text(what, "feeling name");
            expression = Canon.text(expression, "feeling expression");
            Canon.require(!what.isEmpty() && !expression.isEmpty(), "a feeling names itself and carries its arm");
            Canon.require(citation != null, "a feeling is cited");
            Canon.require(chancePerMille >= 0 && chancePerMille <= 1000, "a chance is a share of one: " + chancePerMille);
            effects = Canon.positional(effects, "feeling effects");
            Set<String> seen = new HashSet<>();
            for (Rule effect : effects) {
                Canon.require(seen.add(effect.what()), "one row per place a feeling is read: " + effect.what());
            }
        }
    }

    /**
     * The shape of a Run: every floor of every branch the game builds, the rules that decide the
     * shops and the sealing as cited text, and the level feelings with what each changes.
     */
    public record Structure(List<LevelEntry> levels, List<FeelingEntry> feelings, String shopExpression, Citation shopCitation,
                           String bossExpression, Citation bossCitation, String feelingGate, Citation feelingCitation,
                           List<Rule> otherFeelingSources, String otherwiseClass, Citation otherwiseCitation,
                           Citation sealedCitation, Citation roomsCitation) {

        public Structure {
            levels = Canon.positional(levels, "levels");
            Canon.require(!levels.isEmpty(), "the game builds floors");
            Set<Long> seen = new HashSet<>();
            for (LevelEntry level : levels) {
                Canon.require(seen.add(((long) level.branch() << 32) | level.depth()),
                        "a floor once: depth " + level.depth() + " of branch " + level.branch());
            }
            feelings = Canon.positional(feelings, "feelings");
            Canon.require(!feelings.isEmpty(), "a floor can feel like something");
            Set<String> named = new HashSet<>();
            int total = 0;
            for (FeelingEntry feeling : feelings) {
                Canon.require(named.add(feeling.what()), "a feeling once: " + feeling.what());
                total += feeling.chancePerMille();
            }
            Canon.require(total <= 1000, "the feelings share one roll between them: " + total);
            shopExpression = Canon.text(shopExpression, "shop rule");
            bossExpression = Canon.text(bossExpression, "boss rule");
            feelingGate = Canon.text(feelingGate, "the gate on the feeling roll");
            otherFeelingSources = Canon.positional(otherFeelingSources, "the assignments that are not a literal feeling");
            Canon.require(!shopExpression.isEmpty() && shopCitation != null && sealedCitation != null, "the structure carries its rules and citations");
            Canon.require(!bossExpression.isEmpty() && bossCitation != null, "the structure carries the boss rule and cites it");
            Canon.require(!feelingGate.isEmpty() && feelingCitation != null, "the structure carries the gate on the feeling roll and cites it");
            Canon.require(roomsCitation != null, "the structure cites the table that carries the rooms a floor draws");
            otherwiseClass = Canon.text(otherwiseClass, "what an unnamed depth builds");
            Canon.require(!otherwiseClass.isEmpty() && otherwiseCitation != null,
                    "the structure says what a depth it does not name builds, and cites it");
        }
    }

    // --- what a Brain is built on (story 4.2)

    /**
     * One identity an unidentified item of a family may turn out to be: its class, its display name
     * once identified, and its weight in the generator's decks (the two decks' total; zero for a
     * class the decks never draw, such as the potion of strength, which arrives by a guarantee).
     */
    public record Candidate(String className, String name, int weight) {

        public Candidate {
            className = Canon.text(className, "candidate class");
            name = Canon.text(name, "candidate name");
            Canon.require(!className.isEmpty() && !name.isEmpty(), "a candidate is named");
            Canon.require(weight >= 0, "a weight is not negative: " + weight);
        }
    }

    /**
     * An identifiable family, as the screen meets it: the kind, the display names its unidentified
     * items wear (one per appearance, "crimson potion"), and the identities behind them.
     */
    public record Identities(ItemKind kind, List<String> labels, List<Candidate> candidates) {

        public Identities {
            Canon.require(kind != null, "a family has a kind");
            labels = Canon.positional(labels, "labels");
            candidates = Canon.positional(candidates, "candidates");
            Set<String> seen = new HashSet<>();
            for (String label : labels) {
                distinct(seen, Canon.text(label, "label"), "a label");
            }
            for (Candidate candidate : candidates) {
                distinct(seen, candidate.name(), "a candidate's name");
            }
            Canon.require(!labels.isEmpty() && !candidates.isEmpty(), "a family has appearances and identities");
        }
    }

    /** An item a special room places on its floor: the room's class, the item's class and display name. */
    public record RoomSpawn(String room, String className, String name) {

        public RoomSpawn {
            room = Canon.text(room, "room");
            className = Canon.text(className, "spawn class");
            name = Canon.text(name, "spawn name");
            Canon.require(!room.isEmpty() && !className.isEmpty() && !name.isEmpty(), "a room spawn is named");
        }
    }

    /**
     * A limited drop the game guarantees per set of floors: its counter, the item's class and display
     * name, how many per set, and how many floors a set is.
     */
    public record Guarantee(String counter, String className, String name, int perSet, int floorsPerSet) {

        public Guarantee {
            counter = Canon.text(counter, "counter");
            className = Canon.text(className, "guarantee class");
            name = Canon.text(name, "guarantee name");
            Canon.require(!counter.isEmpty() && !className.isEmpty() && !name.isEmpty(), "a guarantee is named");
            Canon.require(perSet > 0 && floorsPerSet > 0, "a guarantee places something per set of floors");
        }
    }

    /**
     * The general game knowledge a Brain is built on: the manifest it came from, the identifiable
     * families, the items special rooms place, and the guaranteed drops. The caller reads it from
     * the committed Codex folder; a Brain cannot open a file. Nothing in it is about a Run.
     */
    public record Knowledge(Manifest manifest, List<Identities> families, List<RoomSpawn> rooms, List<Guarantee> guarantees) {

        public Knowledge {
            Canon.require(manifest != null, "knowledge says which Codex it came from");
            families = Canon.positional(families, "families");
            rooms = Canon.positional(rooms, "rooms");
            guarantees = Canon.positional(guarantees, "guarantees");
            Set<ItemKind> kinds = new HashSet<>();
            for (Identities family : families) {
                distinct(kinds, family.kind(), "a family's kind");
            }
        }

        /** Knowledge with nothing in it but the manifest: a Brain that knows no mechanics. */
        public static Knowledge of(Manifest manifest) {
            return new Knowledge(manifest, List.of(), List.of(), List.of());
        }
    }

    /** Refuses a table naming a key twice. */
    static <T> void distinct(Set<T> seen, T key, String what) {
        Canon.require(seen.add(key), what + " is listed twice: " + key);
    }
}
