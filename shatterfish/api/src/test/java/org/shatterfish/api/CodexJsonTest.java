package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex's records refuse what a Codex cannot carry, its tables refuse a key twice, and their
 * JSON is the golden text (stories 2.1 and 2.2): a change here is a change every consumer sees,
 * and the Codex version says so.
 */
class CodexJsonTest {

    private static final Codex.Citation AT = new Codex.Citation("core/src/main/java/X.java", 7);
    private static final String CITE = "\"citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";

    @Test
    @DisplayName("the manifest renders its version, its tag and its tables, keys and tables sorted")
    void the_manifest_renders() {
        Codex.Manifest manifest = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("hero-classes.json", "challenges.json"));
        assertEquals("{\"codexVersion\":5,\"tables\":[\"challenges.json\",\"hero-classes.json\"],\"upstreamTag\":\"v4.0.0\"}\n",
                CodexJson.manifest(manifest));
        assertEquals("v4.1.0-beta2", new Codex.Manifest(1, "v4.1.0-beta2", List.of()).upstreamTag(), "a pre-release tag is a tag");
    }

    @Test
    @DisplayName("a table renders one entry per line with its citation, and ends with one line feed")
    void the_entries_render() {
        Codex.HeroClassEntry warrior = new Codex.HeroClassEntry(HeroClass.WARRIOR, List.of(HeroSubclass.BERSERKER, HeroSubclass.GLADIATOR), AT);
        Codex.HeroClassEntry mage = new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.BATTLEMAGE, HeroSubclass.WARLOCK), AT);
        String heroes = CodexJson.heroClasses(List.of(warrior, mage));
        assertEquals("[\n"
                + "  {" + CITE + ",\"heroClass\":\"WARRIOR\",\"subclasses\":[\"BERSERKER\",\"GLADIATOR\"]},\n"
                + "  {" + CITE + ",\"heroClass\":\"MAGE\",\"subclasses\":[\"BATTLEMAGE\",\"WARLOCK\"]}\n"
                + "]\n", heroes);
        Codex.ChallengeEntry darkness = new Codex.ChallengeEntry(Challenge.DARKNESS, 32, AT);
        String challenges = CodexJson.challenges(List.of(darkness));
        assertEquals("[\n  {\"challenge\":\"DARKNESS\"," + CITE + ",\"mask\":32}\n]\n", challenges);
        assertEquals("[\n]\n", CodexJson.challenges(List.of()));
        for (String text : new String[] {heroes, challenges, CodexJson.manifest(new Codex.Manifest(1, "v4.0.0", List.of()))}) {
            assertFalse(text.contains("\r"), "line feeds only");
            assertEquals('\n', text.charAt(text.length() - 1));
        }
        assertEquals("core/src/main/java/X.java:7", AT.reference());
    }

    @Test
    @DisplayName("a mob entry renders its facets, its rolls, its loot, its variants and its citation on one line")
    void a_mob_renders() {
        Codex.Roll damage = new Codex.Roll(Codex.RollKind.NORMAL, 1, 4, "return Random.NormalIntRange( 1, 4 );", AT);
        Codex.Roll attack = new Codex.Roll(Codex.RollKind.CONSTANT, 8, 8, "return 8;", AT);
        Codex.Roll dr = new Codex.Roll(Codex.RollKind.OTHER, 0, 0, "return weapon.dr();", AT);
        Codex.Loot loot = new Codex.Loot(Codex.LootKind.CLASS, "Gold", "Gold.class", 500, "0.5f", false, true, false, AT);
        Codex.Variant deep = new Codex.Variant(5, "", List.of(new Codex.Field("ht", "35"), new Codex.Field("defenseSkill", "9")));
        Codex.Variant strong = new Codex.Variant(1, "STRONGER_BOSSES", List.of(new Codex.Field("ht", "120")));
        Codex.Citation draw = new Codex.Citation("core/src/main/java/X.java", 9);
        Codex.MobEntry mob = new Codex.MobEntry("actors.mobs.Rat", Alignment.ENEMY, List.of("UNDEAD", "INORGANIC"), false, List.of("ht"), true, true,
                List.of(draw), 0, 2, 1, 5, damage, attack, dr, loot, List.of(deep, strong), AT);
        String text = CodexJson.mobs(List.of(mob));
        assertEquals("[\n  {\"alignment\":\"ENEMY\",\"attack\":{" + CITE + ",\"expression\":\"return 8;\",\"kind\":\"CONSTANT\",\"max\":8,\"min\":8},"
                + CITE + ",\"className\":\"actors.mobs.Rat\",\"customDefense\":true,"
                + "\"damage\":{" + CITE + ",\"expression\":\"return Random.NormalIntRange( 1, 4 );\",\"kind\":\"NORMAL\",\"max\":4,\"min\":1},"
                + "\"defenseSkill\":2,\"dr\":{" + CITE + ",\"expression\":\"return weapon.dr();\",\"kind\":\"OTHER\",\"max\":0,\"min\":0},"
                + "\"draws\":[{\"line\":9,\"path\":\"core/src/main/java/X.java\"}],"
                + "\"exp\":1,\"ht\":0,\"loot\":{\"chanceExpression\":\"0.5f\",\"chanceThousandths\":500," + CITE + ",\"customChance\":false,\"customLoot\":true,"
                + "\"declaration\":\"Gold.class\",\"kind\":\"CLASS\",\"name\":\"Gold\",\"random\":false},\"maxLvl\":5,\"properties\":[\"INORGANIC\",\"UNDEAD\"],\"propertiesRandom\":false,"
                + "\"runDependent\":[\"ht\"],\"statsSetLater\":true,"
                + "\"variants\":[{\"challenge\":\"\",\"depth\":5,\"fields\":{\"defenseSkill\":\"9\",\"ht\":\"35\"}},{\"challenge\":\"STRONGER_BOSSES\",\"depth\":1,\"fields\":{\"ht\":\"120\"}}]}\n]\n",
                text);
        assertTrue(text.endsWith("\n") && !text.contains("\r"));
    }

    @Test
    @DisplayName("the spawn rotation renders its keys in order, its depths one per line, with the families, the rare mobs, the alternates and the champion rule")
    void the_rotation_renders() {
        Codex.RotationDepth one = new Codex.RotationDepth(1, List.of(new Codex.RotationEntry("actors.mobs.Rat", 3, false),
                new Codex.RotationEntry("Shaman", 1, true)), AT);
        Codex.RotationDepth two = new Codex.RotationDepth(2, List.of(new Codex.RotationEntry("actors.mobs.Snake", 1, false)), AT);
        Codex.Family shaman = new Codex.Family("Shaman", List.of(new Codex.Odds("actors.mobs.Shaman.RedShaman", 400, "roll < 0.4f"),
                new Codex.Odds("actors.mobs.Shaman.BlueShaman", 600, "else")), AT);
        Codex.SpawnRotation rotation = new Codex.SpawnRotation(List.of(two, one), 1, List.of(shaman), List.of(new Codex.RareMob(4, "actors.mobs.Thief", 25, AT)), 20,
                "1 / 50f * RatSkull.exoticChanceMultiplier()", List.of(new Codex.RareAlt("actors.mobs.Rat", "actors.mobs.Albino", true, AT)),
                new Codex.ChampionRule(Challenge.CHAMPION_ENEMIES, List.of("Blazing", "Giant"), List.of(new Codex.Exclusion("actors.mobs.Crab", 3)),
                        "Dungeon.mobsToChampion += 8;", AT));
        String text = CodexJson.spawnRotation(rotation);
        assertEquals("{\n"
                + "\"alternateChanceExpression\":\"1 / 50f * RatSkull.exoticChanceMultiplier()\",\n"
                + "\"alternateChancePerMille\":20,\n"
                + "\"alternates\":[{\"alternate\":\"actors.mobs.Albino\"," + CITE + ",\"className\":\"actors.mobs.Rat\",\"reachable\":true}],\n"
                + "\"champion\":{\"buffs\":[\"Blazing\",\"Giant\"],\"challenge\":\"CHAMPION_ENEMIES\"," + CITE
                + ",\"counterExpression\":\"Dungeon.mobsToChampion += 8;\",\"exclusions\":[{\"className\":\"actors.mobs.Crab\",\"maxDepth\":3}]},\n"
                + "\"defaultDepth\":1,\n"
                + "\"depths\":[\n"
                + "  {" + CITE + ",\"depth\":1,\"entries\":[{\"className\":\"actors.mobs.Rat\",\"count\":3,\"family\":false},"
                + "{\"className\":\"Shaman\",\"count\":1,\"family\":true}]},\n"
                + "  {" + CITE + ",\"depth\":2,\"entries\":[{\"className\":\"actors.mobs.Snake\",\"count\":1,\"family\":false}]}\n"
                + "],\n"
                + "\"families\":[{" + CITE + ",\"className\":\"Shaman\",\"odds\":[{\"className\":\"actors.mobs.Shaman.RedShaman\",\"expression\":\"roll < 0.4f\",\"perMille\":400},"
                + "{\"className\":\"actors.mobs.Shaman.BlueShaman\",\"expression\":\"else\",\"perMille\":600}]}],\n"
                + "\"rareMobs\":[{" + CITE + ",\"className\":\"actors.mobs.Thief\",\"depth\":4,\"perMille\":25}]\n"
                + "}\n", text);
        List<String> keys = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("\"")) {
                keys.add(line.substring(1, line.indexOf('"', 1)));
            }
        }
        assertEquals(keys.stream().sorted().toList(), keys, "the top-level keys are in order, as the writer would sort them");
    }

    @Test
    @DisplayName("the records refuse a bad path, a zero line, a tag that is not one, a two-bit mask, NONE or a repeated subclass, and a table refuses a key twice")
    void the_records_refuse() {
        for (String bad : new String[] {"/abs/X.java", "core\\X.java", "C:/X.java", "core/../X.java", "./X.java", "core//X.java", "core/X.java/", ""}) {
            assertThrows(IllegalArgumentException.class, () -> new Codex.Citation(bad, 1), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new Codex.Citation("core/X.java", 0));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(0, "v4.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(1, "4.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(1, "v4.0.0", List.of("a.json", "a.json")));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ChallengeEntry(Challenge.NO_FOOD, 3, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ChallengeEntry(Challenge.NO_FOOD, 0, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.NONE), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.WARLOCK, HeroSubclass.WARLOCK), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.WARLOCK), null));
        Codex.HeroClassEntry mage = new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.BATTLEMAGE), AT);
        Codex.HeroClassEntry mageAgain = new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.WARLOCK), AT);
        Codex.HeroClassEntry rogue = new Codex.HeroClassEntry(HeroClass.ROGUE, List.of(HeroSubclass.BATTLEMAGE), AT);
        assertThrows(IllegalArgumentException.class, () -> CodexJson.heroClasses(List.of(mage, mageAgain)), "a class twice");
        assertThrows(IllegalArgumentException.class, () -> CodexJson.heroClasses(List.of(mage, rogue)), "a subclass under two classes");
        Codex.ChallengeEntry food = new Codex.ChallengeEntry(Challenge.NO_FOOD, 1, AT);
        Codex.ChallengeEntry foodAgain = new Codex.ChallengeEntry(Challenge.NO_FOOD, 2, AT);
        Codex.ChallengeEntry armorSameMask = new Codex.ChallengeEntry(Challenge.NO_ARMOR, 1, AT);
        assertThrows(IllegalArgumentException.class, () -> CodexJson.challenges(List.of(food, foodAgain)), "a challenge twice");
        assertThrows(IllegalArgumentException.class, () -> CodexJson.challenges(List.of(food, armorSameMask)), "a mask twice");
        assertThrows(NullPointerException.class, () -> CodexJson.challenges(null));
    }

    @Test
    @DisplayName("an item entry renders its name, its category, its value or its expression, its strength, its actions and why it was not constructed, on one line")
    void an_item_renders() {
        Codex.Strength strength = new Codex.Strength(true, 3, 14, "return req;", AT);
        Codex.ItemEntry sword = new Codex.ItemEntry("items.weapon.melee.Sword", "sword", true, AT, "WEP_T3", 1, 60, "", strength,
                List.of("DROP", "THROW", "EQUIP"), true, "", AT);
        Codex.ItemEntry healing = new Codex.ItemEntry("items.potions.PotionOfHealing", "potion of healing", true, AT, "POTION", 1, -1,
                "return isKnown() ? 30 * quantity : super.value();", Codex.Strength.none(), List.of("DROP", "THROW", "DRINK"), false,
                "its icon needs the toolkit", AT);
        String text = CodexJson.items(List.of(sword, healing));
        String nameCite = "\"nameCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";
        assertEquals("[\n"
                + "  {\"actions\":[\"DROP\",\"THROW\",\"EQUIP\"],\"category\":\"WEP_T3\"," + CITE + ",\"className\":\"items.weapon.melee.Sword\","
                + "\"constructed\":true,\"customName\":true,\"name\":\"sword\"," + nameCite + ",\"quantity\":1,\"reason\":\"\","
                + "\"strength\":{\"atLevel0\":14," + CITE + ",\"formula\":\"return req;\",\"present\":true,\"tier\":3},\"value\":60,\"valueExpression\":\"\"},\n"
                + "  {\"actions\":[\"DROP\",\"THROW\",\"DRINK\"],\"category\":\"POTION\"," + CITE + ",\"className\":\"items.potions.PotionOfHealing\","
                + "\"constructed\":false,\"customName\":true,\"name\":\"potion of healing\"," + nameCite + ",\"quantity\":1,\"reason\":\"its icon needs the toolkit\","
                + "\"strength\":{\"atLevel0\":0,\"formula\":\"\",\"present\":false,\"tier\":0},\"value\":-1,"
                + "\"valueExpression\":\"return isKnown() ? 30 * quantity : super.value();\"}\n"
                + "]\n", text);
        assertTrue(text.endsWith("\n") && !text.contains("\r"));
        assertThrows(IllegalArgumentException.class, () -> CodexJson.items(List.of(sword, sword)), "an item twice");
    }

    @Test
    @DisplayName("the decks render their keys in order, the categories one per line with their weighted classes, then the exotic swap and the label pools")
    void the_decks_render() {
        Codex.CategoryEntry potion = new Codex.CategoryEntry("POTION", 8, 8, "items.potions.Potion", 2,
                List.of(new Codex.Weighted("items.potions.PotionOfHealing", 3, 3, 6)), AT, AT, AT, AT);
        Codex.CategoryEntry weapon = new Codex.CategoryEntry("WEAPON", 2, 2, "items.weapon.melee.MeleeWeapon", 0, List.of(), AT, AT, null, null);
        Codex.LabelPool colors = new Codex.LabelPool("Potion", List.of(new Codex.Label("crimson", "crimson potion", "exotic crimson potion", AT, AT, AT),
                new Codex.Label("amber", "amber potion", "", AT, AT, null)), AT);
        Codex.ExoticSwap exotic = new Codex.ExoticSwap(List.of(new Codex.ExoticPair("items.scrolls.ScrollOfRage", "items.scrolls.exotic.ScrollOfChallenge"),
                new Codex.ExoticPair("items.potions.PotionOfHealing", "items.potions.exotic.PotionOfShielding")), "return 0f; | return 0.2f + 0.2f*level;", 0, AT);
        String text = CodexJson.decks(new Codex.Decks(List.of(potion, weapon), List.of(colors), exotic));
        String classesCite = "\"classesCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";
        assertEquals("{\n"
                + "\"categories\":[\n"
                + "  {" + CITE + ",\"classes\":[{\"className\":\"items.potions.PotionOfHealing\",\"firstDeck\":3,\"secondDeck\":3,\"total\":6}],"
                + classesCite + ",\"decks\":2,\"firstProb\":8,\"name\":\"POTION\",\"secondProb\":8,\"superClass\":\"items.potions.Potion\","
                + "\"weights2Citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},\"weightsCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}},\n"
                + "  {" + CITE + ",\"classes\":[]," + classesCite + ",\"decks\":0,\"firstProb\":2,\"name\":\"WEAPON\",\"secondProb\":2,\"superClass\":\"items.weapon.melee.MeleeWeapon\"}\n"
                + "],\n"
                + "\"exotic\":{\"chanceExpression\":\"return 0f; | return 0.2f + 0.2f*level;\",\"chanceWithoutTrinketPerMille\":0," + CITE + ","
                + "\"pairs\":[{\"exotic\":\"items.potions.exotic.PotionOfShielding\",\"regular\":\"items.potions.PotionOfHealing\"},"
                + "{\"exotic\":\"items.scrolls.exotic.ScrollOfChallenge\",\"regular\":\"items.scrolls.ScrollOfRage\"}]},\n"
                + "\"labelPools\":[{" + CITE + ",\"family\":\"Potion\",\"labels\":[{" + CITE + ",\"exoticCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},"
                + "\"exoticName\":\"exotic crimson potion\",\"key\":\"crimson\",\"name\":\"crimson potion\","
                + "\"nameCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}},"
                + "{" + CITE + ",\"exoticName\":\"\",\"key\":\"amber\",\"name\":\"amber potion\","
                + "\"nameCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}}]}]\n"
                + "}\n", text);
        List<String> keys = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("\"")) {
                keys.add(line.substring(1, line.indexOf('"', 1)));
            }
        }
        assertEquals(keys.stream().sorted().toList(), keys, "the top-level keys are in order, as the writer would sort them");
        assertEquals(List.of("items.potions.PotionOfHealing", "items.scrolls.ScrollOfRage"), exotic.pairs().stream().map(Codex.ExoticPair::regular).toList(),
                "the pairs are sorted by the regular class");
    }

    @Test
    @DisplayName("the deck and item records refuse what the tables cannot mean")
    void the_item_records_refuse() {
        assertThrows(IllegalArgumentException.class, () -> new Codex.Weighted("X", 1, 2, 4), "the total is the sum");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Weighted("X", -1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Weighted("", 1, 0, 1));
        Codex.Weighted x = new Codex.Weighted("X", 1, 0, 1);
        Codex.Weighted y = new Codex.Weighted("Y", 1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", 8, 8, "P", 1, List.of(x, x), AT, AT, AT, null), "a class once per category");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", -1, 8, "P", 1, List.of(), AT, AT, AT, null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("", 8, 8, "P", 1, List.of(), AT, AT, AT, null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", 8, 8, "P", 1, List.of(), AT, null, AT, null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", 8, 8, "P", 3, List.of(), AT, AT, AT, AT), "no third deck");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("ARMOR", 2, 1, "A", 0, List.of(x), AT, AT, null, null), "no deck, no weights");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("ARMOR", 2, 1, "A", 0, List.of(), AT, AT, AT, null), "no deck, no weights citation");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("WAND", 1, 1, "W", 1, List.of(y), AT, AT, AT, null), "one deck, no second weight");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("WAND", 1, 1, "W", 1, List.of(x), AT, AT, null, null), "one deck, its weights cited");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("WAND", 1, 1, "W", 1, List.of(x), AT, AT, AT, AT), "one deck, no second citation");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", 8, 8, "P", 2, List.of(y), AT, AT, AT, null), "two decks, both cited");
        Codex.Label crimson = new Codex.Label("crimson", "crimson potion", "exotic crimson potion", AT, AT, AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.LabelPool("Potion", List.of(), AT), "a pool has labels");
        assertThrows(IllegalArgumentException.class, () -> new Codex.LabelPool("Potion", List.of(crimson, crimson), AT), "a label once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Label("", "crimson potion", "", AT, AT, null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Label("crimson", "crimson potion", "", AT, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Label("crimson", "crimson potion", "exotic", AT, AT, null), "an exotic name is cited");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Label("crimson", "crimson potion", "", AT, AT, AT), "no exotic name, no citation");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ExoticPair("X", "X"), "a pair is two classes");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ExoticSwap(List.of(), "x", 1001, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ExoticSwap(List.of(), "x", 0, null));
        Codex.ExoticSwap swap = new Codex.ExoticSwap(List.of(), "x", 0, AT);
        Codex.CategoryEntry potion = new Codex.CategoryEntry("POTION", 8, 8, "P", 1, List.of(), AT, AT, AT, null);
        Codex.LabelPool pool = new Codex.LabelPool("Potion", List.of(crimson), AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.Decks(List.of(potion, potion), List.of(), swap), "a category once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Decks(List.of(potion), List.of(pool, pool), swap), "a family has one pool");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Decks(List.of(potion), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(false, 3, 0, "", null), "an absent strength carries no tier");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(false, 0, 0, "x", null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(true, 3, 14, "", AT), "a present strength has its formula");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(true, 3, 14, "x", null));
        Codex.Strength none = Codex.Strength.none();
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "return 1;", none, List.of(), true, "", AT),
                "a constructed item carries no expression");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "", none, List.of(), false, "why", AT),
                "a read item carries its expression");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "", none, List.of(), true, "why", AT),
                "a constructed item has no reason");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "return 1;", none, List.of(), false, "", AT),
                "a read item says why");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, -2, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 0, 1, "", none, List.of(), true, "", AT), "a quantity");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "", none, List.of("DROP", "DROP"), true, "", AT),
                "an action once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("", "x", false, AT, "", 1, 1, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "", false, AT, "", 1, 1, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, null, "", 1, 1, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "", null, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", false, AT, "", 1, 1, "", none, List.of(), true, "", null));
        assertThrows(NullPointerException.class, () -> CodexJson.items(null));
        assertThrows(NullPointerException.class, () -> CodexJson.decks(null));
    }

    @Test
    @DisplayName("the guarantees, the tiers and the rooms render their keys in order, their drops, rows and rooms one per line")
    void the_guarantees_tiers_and_rooms_render() {
        String cite = "{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";
        Codex.DropSchedule pos = new Codex.DropSchedule("STRENGTH_POTIONS", "items.potions.PotionOfStrength", "posNeeded", false, 2, "return x;", AT, AT,
                List.of(new Codex.ScheduleEntry(1, 0, 500, 500, 500), new Codex.ScheduleEntry(2, 0, 1000, 0, 0)));
        Codex.Guarantees guarantees = new Codex.Guarantees(List.of("STRENGTH_POTIONS", "LAB_ROOM"), AT, List.of(10, 5), AT, "!bossLevel()", AT,
                "count%2 != 0", AT, List.of(new Codex.Placement(1, "levels.SewerLevel", true, AT), new Codex.Placement(2, "levels.LastLevel", false, AT)),
                List.of(pos));
        assertEquals("{\n"
                + "\"bossCitation\":" + cite + ",\n"
                + "\"bossDepths\":[5,10],\n"
                + "\"counters\":[\"STRENGTH_POTIONS\",\"LAB_ROOM\"],\n"
                + "\"countersCitation\":" + cite + ",\n"
                + "\"drops\":[\n"
                + "  {" + CITE + ",\"entries\":[{\"count\":0,\"depth\":1,\"neededPerMille\":500,\"placedNoScrollsPerMille\":500,\"placedPerMille\":500},"
                + "{\"count\":0,\"depth\":2,\"neededPerMille\":1000,\"placedNoScrollsPerMille\":0,\"placedPerMille\":0}],"
                + "\"expression\":\"return x;\",\"item\":\"items.potions.PotionOfStrength\",\"method\":\"posNeeded\",\"name\":\"STRENGTH_POTIONS\","
                + "\"once\":false,\"perSet\":2,\"placementCitation\":" + cite + "}\n"
                + "],\n"
                + "\"gateExpression\":\"!bossLevel()\",\n"
                + "\"noScrollsCitation\":" + cite + ",\n"
                + "\"noScrollsExpression\":\"count%2 != 0\",\n"
                + "\"placementCitation\":" + cite + ",\n"
                + "\"placements\":[\n"
                + "  {" + CITE + ",\"depth\":1,\"levelClass\":\"levels.SewerLevel\",\"placesSpawnList\":true},\n"
                + "  {" + CITE + ",\"depth\":2,\"levelClass\":\"levels.LastLevel\",\"placesSpawnList\":false}\n"
                + "]\n"
                + "}\n", CodexJson.guarantees(guarantees));
        Codex.Rule armor = new Codex.Rule("armor", "classes[index]", AT);
        Codex.Tiers tiers = new Codex.Tiers(List.of(new Codex.TierRow(0, 1, 4, List.of(0, 75, 20, 4, 1)), new Codex.TierRow(1, 5, 9, List.of(0, 25, 50, 20, 5))),
                AT, new Codex.Rule("gate", "gate(0, floorSet, 4)", AT), armor, new Codex.Rule("weapon", "wepTiers[index]", AT), new Codex.Rule("missile", "misTiers[index]", AT),
                List.of(new Codex.Rule("wepTiers", "WEP_T1, WEP_T2", AT)));
        assertEquals("{\n"
                + "\"armor\":{" + CITE + ",\"expression\":\"classes[index]\",\"what\":\"armor\"},\n"
                + "\"arrays\":[{" + CITE + ",\"expression\":\"WEP_T1, WEP_T2\",\"what\":\"wepTiers\"}],\n"
                + "\"citation\":" + cite + ",\n"
                + "\"gate\":{" + CITE + ",\"expression\":\"gate(0, floorSet, 4)\",\"what\":\"gate\"},\n"
                + "\"missile\":{" + CITE + ",\"expression\":\"misTiers[index]\",\"what\":\"missile\"},\n"
                + "\"rows\":[\n"
                + "  {\"depthFrom\":1,\"depthTo\":4,\"floorSet\":0,\"weights\":[0,75,20,4,1]},\n"
                + "  {\"depthFrom\":5,\"depthTo\":9,\"floorSet\":1,\"weights\":[0,25,50,20,5]}\n"
                + "],\n"
                + "\"weapon\":{" + CITE + ",\"expression\":\"wepTiers[index]\",\"what\":\"weapon\"}\n"
                + "}\n", CodexJson.tiers(tiers));
        Codex.RoomEntry fire = new Codex.RoomEntry("levels.rooms.special.MagicalFireRoom", false,
                List.of(new Codex.Spawn("items.potions.PotionOfFrost", 1, false, false, AT), new Codex.Spawn("items.Honeypot", 1, true, true, AT)),
                List.of(new Codex.Draw("Generator.random(x)", AT)), AT);
        Codex.RoomEntry maze = new Codex.RoomEntry("levels.rooms.secret.SecretMazeRoom", true, List.of(), List.of(), AT);
        Codex.Rooms rooms = new Codex.Rooms(List.of(fire), List.of(maze), List.of(new Codex.RoomList("POTION_SPAWN_ROOMS", List.of("levels.rooms.special.MagicalFireRoom"), AT)),
                List.of(2000, 2250), AT, new Codex.Rule("queue", "chances(6, 3, 1)", AT));
        assertEquals("{\n"
                + "\"baseSecretsPerRegionThousandths\":[2000,2250],\n"
                + "\"lists\":[\n"
                + "  {" + CITE + ",\"members\":[\"levels.rooms.special.MagicalFireRoom\"],\"name\":\"POTION_SPAWN_ROOMS\"}\n"
                + "],\n"
                + "\"queue\":{" + CITE + ",\"expression\":\"chances(6, 3, 1)\",\"what\":\"queue\"},\n"
                + "\"secrets\":[\n"
                + "  {" + CITE + ",\"className\":\"levels.rooms.secret.SecretMazeRoom\",\"draws\":[],\"secret\":true,\"spawns\":[]}\n"
                + "],\n"
                + "\"secretsCitation\":" + cite + ",\n"
                + "\"specials\":[\n"
                + "  {" + CITE + ",\"className\":\"levels.rooms.special.MagicalFireRoom\",\"draws\":[{" + CITE + ",\"expression\":\"Generator.random(x)\"}],"
                + "\"secret\":false,\"spawns\":[{" + CITE + ",\"className\":\"items.Honeypot\",\"conditional\":true,\"count\":1,\"floorDrop\":true},"
                + "{" + CITE + ",\"className\":\"items.potions.PotionOfFrost\",\"conditional\":false,\"count\":1,\"floorDrop\":false}]}\n"
                + "]\n"
                + "}\n", CodexJson.rooms(rooms));
        for (String text : new String[] {CodexJson.guarantees(guarantees), CodexJson.tiers(tiers), CodexJson.rooms(rooms)}) {
            List<String> keys = new java.util.ArrayList<>();
            for (String line : text.split("\n")) {
                if (line.startsWith("\"")) {
                    keys.add(line.substring(1, line.indexOf('"', 1)));
                }
            }
            assertEquals(keys.stream().sorted().toList(), keys, "the top-level keys are in order");
            assertTrue(text.endsWith("\n") && !text.contains("\r"));
        }
    }

    @Test
    @DisplayName("the guarantee, tier and room records refuse what the tables cannot mean")
    void the_guarantee_records_refuse() {
        assertThrows(IllegalArgumentException.class, () -> new Codex.ScheduleEntry(0, 0, 500, 500, 500), "a depth is a floor");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ScheduleEntry(1, -1, 500, 500, 500));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ScheduleEntry(1, 0, 1001, 500, 500));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ScheduleEntry(1, 0, 500, 300, 300), "a floor places all of it or none");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ScheduleEntry(1, 0, 500, 500, 300), "Forbidden Runes withholds all of it or none");
        Codex.ScheduleEntry one = new Codex.ScheduleEntry(1, 0, 500, 500, 500);
        assertThrows(IllegalArgumentException.class, () -> new Codex.DropSchedule("X", "i", "m", true, 2, "t", AT, AT, List.of(one)), "once means once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.DropSchedule("X", "i", "m", false, 0, "t", AT, AT, List.of(one)));
        assertThrows(IllegalArgumentException.class, () -> new Codex.DropSchedule("X", "i", "m", false, 1, "t", AT, AT, List.of()), "a schedule has entries");
        assertThrows(IllegalArgumentException.class, () -> new Codex.DropSchedule("X", "i", "m", false, 1, "t", AT, AT, List.of(one, one)), "a state once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.DropSchedule("X", "i", "m", false, 1, "", AT, AT, List.of(one)));
        assertThrows(IllegalArgumentException.class, () -> new Codex.DropSchedule("X", "i", "m", false, 1, "t", AT, AT,
                List.of(one, new Codex.ScheduleEntry(2, 1, 0, 0, 0))), "a schedule covers its grid");
        Codex.DropSchedule x = new Codex.DropSchedule("X", "i", "m", false, 1, "t", AT, AT, List.of(one));
        Codex.Placement sewers = new Codex.Placement(1, "levels.SewerLevel", true, AT);
        Codex.Placement amulet = new Codex.Placement(2, "levels.LastLevel", false, AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.Guarantees(List.of("Y"), AT, List.of(5), AT, "g", AT, "n", AT, List.of(sewers), List.of(x)),
                "a drop's counter is kept");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Guarantees(List.of("X"), AT, List.of(5, 5), AT, "g", AT, "n", AT, List.of(sewers), List.of(x)),
                "a boss depth once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Guarantees(List.of("X", "X"), AT, List.of(5), AT, "g", AT, "n", AT, List.of(sewers), List.of(x)),
                "a counter once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Guarantees(List.of("X"), AT, List.of(5), AT, "g", AT, "n", AT, List.of(sewers), List.of(x, x)),
                "a drop once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Guarantees(List.of("X"), AT, List.of(5), AT, "g", AT, "n", AT, List.of(amulet), List.of(x)),
                "the placements start at depth one");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Guarantees(List.of("X"), AT, List.of(5), AT, "", AT, "n", AT, List.of(sewers), List.of(x)),
                "the gate has text");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Placement(0, "L", true, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Placement(1, "", true, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.TierRow(0, 1, 4, List.of(0, 75, 20, 4)), "five tiers");
        assertThrows(IllegalArgumentException.class, () -> new Codex.TierRow(0, 1, 4, List.of(0, 0, 0, 0, 0)), "some tier");
        assertThrows(IllegalArgumentException.class, () -> new Codex.TierRow(0, 4, 1, List.of(0, 75, 20, 4, 1)));
        Codex.TierRow first = new Codex.TierRow(0, 1, 4, List.of(0, 75, 20, 4, 1));
        Codex.TierRow gap = new Codex.TierRow(1, 6, 9, List.of(0, 25, 50, 20, 5));
        Codex.Rule rule = new Codex.Rule("r", "e", AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tiers(List.of(first, gap), AT, rule, rule, rule, rule, List.of()), "the ranges abut");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tiers(List.of(gap), AT, rule, rule, rule, rule, List.of()), "the rows are the floor sets from zero");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tiers(List.of(), AT, rule, rule, rule, rule, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tiers(List.of(first), AT, rule, rule, rule, rule, List.of(rule, rule)), "a tier array once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Rule("", "e", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Spawn("K", 0, false, false, AT));
        Codex.Spawn key = new Codex.Spawn("K", 1, false, false, AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.RoomEntry("R", false, List.of(key, key), List.of(), AT), "a spawned class once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.RoomEntry("R", false, List.of(new Codex.Spawn("K", 1, false, true, AT)), List.of(), AT),
                "a spawn-list item is not conditional");
        Codex.RoomEntry dropped = new Codex.RoomEntry("R", false, List.of(key, new Codex.Spawn("K", 1, true, true, AT)), List.of(), AT);
        assertEquals(2, dropped.spawns().size(), "the same class added to the list and dropped on the floor is two entries");
        Codex.RoomEntry special = new Codex.RoomEntry("R", false, List.of(key), List.of(), AT);
        Codex.RoomEntry secret = new Codex.RoomEntry("S", true, List.of(), List.of(), AT);
        Codex.RoomEntry other = new Codex.RoomEntry("T", false, List.of(), List.of(), AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.Rooms(List.of(secret), List.of(), List.of(), List.of(2000), AT, rule), "a special is not a secret");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Rooms(List.of(special, special), List.of(), List.of(), List.of(2000), AT, rule), "a room once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Rooms(List.of(special), List.of(secret), List.of(new Codex.RoomList("L", List.of("T"), AT)),
                List.of(2000), AT, rule), "a listed room is in the table");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Rooms(List.of(special), List.of(secret), List.of(), List.of(), AT, rule), "secrets per region");
        assertThrows(IllegalArgumentException.class, () -> new Codex.RoomList("L", List.of("R", "R"), AT), "a member once");
        assertThrows(NullPointerException.class, () -> CodexJson.guarantees(null));
        assertThrows(NullPointerException.class, () -> CodexJson.tiers(null));
        assertThrows(NullPointerException.class, () -> CodexJson.rooms(null));
    }

    @Test
    @DisplayName("the combat tables render their keys in order, the cells and the rolls one per line, and an unmeasured hit table says why")
    void the_combat_tables_render() {
        String cite = "{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";
        Codex.Grid accuracy = new Codex.Grid("accuracy", 0, 2, 2);
        Codex.Grid evasion = new Codex.Grid("evasion", 0, 2, 2);
        Codex.HitTable measured = new Codex.HitTable("Char.hit", AT, accuracy, evasion, true, "",
                List.of(new Codex.HitCell(0, 0, 10, 1000), new Codex.HitCell(0, 2, 10, 0),
                        new Codex.HitCell(2, 0, 10, 1000), new Codex.HitCell(2, 2, 10, 500)));
        Codex.RollEntry sword = new Codex.RollEntry("items.weapon.melee.Sword", 3, 0, new Codex.Spread(3, 20, 11529, 20000), "damageRoll", AT);
        Codex.RollEntry plate = new Codex.RollEntry("items.armor.PlateArmor", 5, 0, new Codex.Spread(0, 10, 4996, 20000), "Hero.drRoll", AT);
        Codex.RollEntry rat = new Codex.RollEntry("actors.mobs.Rat", 0, 0, new Codex.Spread(0, 1, 500, 20000), "drRoll", AT);
        String text = CodexJson.combat(new Codex.Combat(0xC0FFEEL, measured, List.of(sword), List.of(plate), List.of(rat)));
        assertEquals("{\n"
                + "\"armours\":[\n"
                + "  {" + CITE + ",\"className\":\"items.armor.PlateArmor\",\"level\":0,\"method\":\"Hero.drRoll\","
                + "\"spread\":{\"max\":10,\"meanPerMille\":4996,\"min\":0,\"samples\":20000},\"tier\":5}\n"
                + "],\n"
                + "\"hit\":{\"accuracy\":{\"from\":0,\"step\":2,\"to\":2,\"what\":\"accuracy\"}," + CITE
                + ",\"evasion\":{\"from\":0,\"step\":2,\"to\":2,\"what\":\"evasion\"},\"measured\":true,\"method\":\"Char.hit\",\"reason\":\"\"},\n"
                + "\"hitCells\":[\n"
                + "  {\"accuracy\":0,\"evasion\":0,\"hitPerMille\":1000,\"samples\":10},\n"
                + "  {\"accuracy\":0,\"evasion\":2,\"hitPerMille\":0,\"samples\":10},\n"
                + "  {\"accuracy\":2,\"evasion\":0,\"hitPerMille\":1000,\"samples\":10},\n"
                + "  {\"accuracy\":2,\"evasion\":2,\"hitPerMille\":500,\"samples\":10}\n"
                + "],\n"
                + "\"mobs\":[\n"
                + "  {" + CITE + ",\"className\":\"actors.mobs.Rat\",\"level\":0,\"method\":\"drRoll\","
                + "\"spread\":{\"max\":1,\"meanPerMille\":500,\"min\":0,\"samples\":20000},\"tier\":0}\n"
                + "],\n"
                + "\"seed\":12648430,\n"
                + "\"weapons\":[\n"
                + "  {" + CITE + ",\"className\":\"items.weapon.melee.Sword\",\"level\":0,\"method\":\"damageRoll\","
                + "\"spread\":{\"max\":20,\"meanPerMille\":11529,\"min\":3,\"samples\":20000},\"tier\":3}\n"
                + "]\n"
                + "}\n", text);
        List<String> keys = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("\"")) {
                keys.add(line.substring(1, line.indexOf('"', 1)));
            }
        }
        assertEquals(keys.stream().sorted().toList(), keys, "the top-level keys are in order");
        Codex.HitTable unmeasured = new Codex.HitTable("Char.hit", AT, accuracy, evasion, false, "it needs the toolkit", List.of());
        assertTrue(CodexJson.combat(new Codex.Combat(1L, unmeasured, List.of(), List.of(), List.of()))
                .contains("\"measured\":false,\"method\":\"Char.hit\",\"reason\":\"it needs the toolkit\""));
    }

    @Test
    @DisplayName("the combat records refuse what a measurement cannot mean")
    void the_combat_records_refuse() {
        Codex.Grid one = new Codex.Grid("accuracy", 0, 2, 2);
        assertThrows(IllegalArgumentException.class, () -> new Codex.Grid("", 0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Grid("a", 0, 2, 0), "a step is positive");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Grid("a", 3, 2, 1), "an axis runs forwards");
        assertEquals(2, one.size());
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitCell(-1, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitCell(0, 0, 0, 0), "a cell was sampled");
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitCell(0, 0, 10, 1001));
        Codex.HitCell cell = new Codex.HitCell(0, 0, 10, 1000);
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitTable("m", AT, one, one, true, "", List.of(cell)), "a measured table covers its grid");
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitTable("m", AT, one, one, false, "why", List.of(cell)), "an unmeasured table has no cell");
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitTable("m", AT, one, one, true, "why", List.of()), "a measured table says no why");
        assertThrows(IllegalArgumentException.class, () -> new Codex.HitTable("m", AT, one, one, false, "", List.of()), "an unmeasured table says why");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Spread(3, 1, 2000, 10), "a spread runs forwards");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Spread(1, 3, 500, 10), "a mean lies inside its bounds");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Spread(1, 3, 3500, 10));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Spread(1, 3, 2000, 0));
        Codex.Spread spread = new Codex.Spread(1, 3, 2000, 10);
        assertThrows(IllegalArgumentException.class, () -> new Codex.RollEntry("", 0, 0, spread, "m", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.RollEntry("X", -1, 0, spread, "m", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.RollEntry("X", 0, 0, spread, "", AT));
        Codex.RollEntry entry = new Codex.RollEntry("X", 0, 0, spread, "m", AT);
        Codex.HitTable table = new Codex.HitTable("m", AT, one, one, false, "why", List.of());
        assertThrows(IllegalArgumentException.class, () -> new Codex.Combat(1L, table, List.of(entry, entry), List.of(), List.of()),
                "a class and level once per table");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Combat(1L, null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> CodexJson.combat(null));
    }

    @Test
    @DisplayName("the mob and rotation records refuse what the tables cannot mean")
    void the_mob_records_refuse() {
        assertThrows(IllegalArgumentException.class, () -> new Codex.Variant(5, "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Variant(5, "DARKNESS", List.of(new Codex.Field("ht", "1"))));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Variant(1, "", List.of(new Codex.Field("ht", "1"))));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Variant(1, "NOT_A_CHALLENGE", List.of(new Codex.Field("ht", "1"))));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Field("weight", "1"));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Roll(Codex.RollKind.CONSTANT, 1, 2, "return 1;", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Roll(Codex.RollKind.NORMAL, 4, 1, "x", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Roll(Codex.RollKind.NORMAL, 1, 4, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Roll(Codex.RollKind.OTHER, 1, 4, "x", AT), "OTHER carries no bounds");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Loot(Codex.LootKind.NONE, "Gold", "null", 0, "0", false, false, false, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Loot(Codex.LootKind.CLASS, "Gold", "Gold.class", 1001, "0", false, false, false, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Loot(Codex.LootKind.CLASS, "Gold", "Gold.class", 0, "0", true, false, false, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Loot(Codex.LootKind.CLASS, "Gold", "", 0, "0", false, false, false, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.RotationEntry("Rat", 0, false));
        assertThrows(IllegalArgumentException.class, () -> new Codex.RotationDepth(1, List.of(), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.RotationDepth(1, List.of(new Codex.RotationEntry("Rat", 1, false),
                new Codex.RotationEntry("Rat", 2, false)), AT), "a class once per depth");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Odds("Rat", 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Odds("Rat", 1001, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Family("Shaman", List.of(new Codex.Odds("Red", 400, "x"), new Codex.Odds("Blue", 500, "y")), AT), "odds sum to a thousand");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Family("Shaman", List.of(new Codex.Odds("Red", 500, "x"), new Codex.Odds("Red", 500, "y")), AT), "a member once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.RareMob(4, "Thief", 0, AT));
        Codex.Roll roll = new Codex.Roll(Codex.RollKind.CONSTANT, 1, 1, "return 1;", AT);
        Codex.Loot none = new Codex.Loot(Codex.LootKind.NONE, "", "null", 0, "0", false, false, false, AT);
        Codex.MobEntry rat = new Codex.MobEntry("actors.mobs.Rat", Alignment.ENEMY, List.of(), false, List.of(), false, false, List.of(), 8, 2, 1, 5,
                roll, roll, roll, none, List.of(), AT);
        assertThrows(IllegalArgumentException.class, () -> CodexJson.mobs(List.of(rat, rat)), "a mob twice");
        assertThrows(IllegalArgumentException.class, () -> new Codex.MobEntry("actors.mobs.Rat", Alignment.ENEMY, List.of("BOSS"), true, List.of(), false, false,
                List.of(AT), 8, 2, 1, 5, roll, roll, roll, none, List.of(), AT), "random properties are not listed");
        assertThrows(IllegalArgumentException.class, () -> new Codex.MobEntry("actors.mobs.Rat", Alignment.ENEMY, List.of(), true, List.of(), false, false,
                List.of(), 8, 2, 1, 5, roll, roll, roll, none, List.of(), AT), "random properties cite the draw");
        assertThrows(IllegalArgumentException.class, () -> new Codex.MobEntry("actors.mobs.Rat", Alignment.ENEMY, List.of(), false, List.of("alignment"), false, false,
                List.of(), 8, 2, 1, 5, roll, roll, roll, none, List.of(), AT), "a run-dependent field is a number");
        assertThrows(IllegalArgumentException.class, () -> new Codex.MobEntry("actors.mobs.Rat", Alignment.ENEMY, List.of(), false, List.of("ht"), false, false,
                List.of(), 8, 2, 1, 5, roll, roll, roll, none, List.of(), AT), "a run-dependent field is dumped as zero");
        Codex.RotationDepth one = new Codex.RotationDepth(1, List.of(new Codex.RotationEntry("actors.mobs.Rat", 1, false)), AT);
        Codex.RotationDepth withFamily = new Codex.RotationDepth(2, List.of(new Codex.RotationEntry("Shaman", 1, true)), AT);
        Codex.ChampionRule rule = new Codex.ChampionRule(Challenge.CHAMPION_ENEMIES, List.of("Blazing"), List.of(), "x", AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.SpawnRotation(List.of(one, one), 1, List.of(), List.of(), 20, "x", List.of(), rule), "a depth twice");
        assertThrows(IllegalArgumentException.class, () -> new Codex.SpawnRotation(List.of(one), 2, List.of(), List.of(), 20, "x", List.of(), rule), "the default depth is a depth");
        assertThrows(IllegalArgumentException.class, () -> new Codex.SpawnRotation(List.of(one, withFamily), 1, List.of(), List.of(), 20, "x", List.of(), rule), "a family drawn from is listed");
    }
}
