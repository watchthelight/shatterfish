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
        assertEquals("{\"codexVersion\":3,\"tables\":[\"challenges.json\",\"hero-classes.json\"],\"upstreamTag\":\"v4.0.0\"}\n",
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
        Codex.ItemEntry sword = new Codex.ItemEntry("items.weapon.melee.Sword", "sword", AT, "WEP_T3", 60, "", strength,
                List.of("DROP", "THROW", "EQUIP"), true, "", AT);
        Codex.ItemEntry healing = new Codex.ItemEntry("items.potions.PotionOfHealing", "potion of healing", AT, "POTION", -1,
                "return isKnown() ? 30 * quantity : super.value();", Codex.Strength.none(), List.of("DROP", "THROW", "DRINK"), false,
                "its icon needs the toolkit", AT);
        String text = CodexJson.items(List.of(sword, healing));
        String nameCite = "\"nameCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";
        assertEquals("[\n"
                + "  {\"actions\":[\"DROP\",\"THROW\",\"EQUIP\"],\"category\":\"WEP_T3\"," + CITE + ",\"className\":\"items.weapon.melee.Sword\","
                + "\"constructed\":true,\"name\":\"sword\"," + nameCite + ",\"reason\":\"\","
                + "\"strength\":{\"atLevel0\":14," + CITE + ",\"formula\":\"return req;\",\"present\":true,\"tier\":3},\"value\":60,\"valueExpression\":\"\"},\n"
                + "  {\"actions\":[\"DROP\",\"THROW\",\"DRINK\"],\"category\":\"POTION\"," + CITE + ",\"className\":\"items.potions.PotionOfHealing\","
                + "\"constructed\":false,\"name\":\"potion of healing\"," + nameCite + ",\"reason\":\"its icon needs the toolkit\","
                + "\"strength\":{\"atLevel0\":0,\"formula\":\"\",\"present\":false,\"tier\":0},\"value\":-1,"
                + "\"valueExpression\":\"return isKnown() ? 30 * quantity : super.value();\"}\n"
                + "]\n", text);
        assertTrue(text.endsWith("\n") && !text.contains("\r"));
        assertThrows(IllegalArgumentException.class, () -> CodexJson.items(List.of(sword, sword)), "an item twice");
    }

    @Test
    @DisplayName("the decks render their keys in order, the categories one per line with their weighted classes, then the exotic swap and the label pools")
    void the_decks_render() {
        Codex.CategoryEntry potion = new Codex.CategoryEntry("POTION", 8, 8, "items.potions.Potion",
                List.of(new Codex.Weighted("items.potions.PotionOfHealing", 3, 3, 6)), AT, AT);
        Codex.CategoryEntry weapon = new Codex.CategoryEntry("WEAPON", 2, 2, "items.weapon.melee.MeleeWeapon", List.of(), AT, AT);
        Codex.LabelPool colors = new Codex.LabelPool("Potion", List.of(new Codex.Label("crimson", "crimson potion", AT, AT)), AT);
        Codex.ExoticSwap exotic = new Codex.ExoticSwap(List.of(new Codex.ExoticPair("items.scrolls.ScrollOfRage", "items.scrolls.exotic.ScrollOfChallenge"),
                new Codex.ExoticPair("items.potions.PotionOfHealing", "items.potions.exotic.PotionOfShielding")), "return 0f; | return 0.2f + 0.2f*level;", 0, AT);
        String text = CodexJson.decks(new Codex.Decks(List.of(potion, weapon), List.of(colors), exotic));
        String classesCite = "\"classesCitation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"}";
        assertEquals("{\n"
                + "\"categories\":[\n"
                + "  {" + CITE + ",\"classes\":[{\"className\":\"items.potions.PotionOfHealing\",\"firstDeck\":3,\"secondDeck\":3,\"total\":6}],"
                + classesCite + ",\"firstProb\":8,\"name\":\"POTION\",\"secondProb\":8,\"superClass\":\"items.potions.Potion\"},\n"
                + "  {" + CITE + ",\"classes\":[]," + classesCite + ",\"firstProb\":2,\"name\":\"WEAPON\",\"secondProb\":2,\"superClass\":\"items.weapon.melee.MeleeWeapon\"}\n"
                + "],\n"
                + "\"exotic\":{\"chanceExpression\":\"return 0f; | return 0.2f + 0.2f*level;\",\"chanceWithoutTrinketPerMille\":0," + CITE + ","
                + "\"pairs\":[{\"exotic\":\"items.potions.exotic.PotionOfShielding\",\"regular\":\"items.potions.PotionOfHealing\"},"
                + "{\"exotic\":\"items.scrolls.exotic.ScrollOfChallenge\",\"regular\":\"items.scrolls.ScrollOfRage\"}]},\n"
                + "\"labelPools\":[{" + CITE + ",\"family\":\"Potion\",\"labels\":[{" + CITE + ",\"key\":\"crimson\",\"name\":\"crimson potion\","
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
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", 8, 8, "P", List.of(x, x), AT, AT), "a class once per category");
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", -1, 8, "P", List.of(), AT, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("", 8, 8, "P", List.of(), AT, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.CategoryEntry("POTION", 8, 8, "P", List.of(), AT, null));
        Codex.Label crimson = new Codex.Label("crimson", "crimson potion", AT, AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.LabelPool("Potion", List.of(), AT), "a pool has labels");
        assertThrows(IllegalArgumentException.class, () -> new Codex.LabelPool("Potion", List.of(crimson, crimson), AT), "a label once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Label("", "crimson potion", AT, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Label("crimson", "crimson potion", AT, null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ExoticSwap(List.of(), "x", 1001, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ExoticSwap(List.of(), "x", 0, null));
        Codex.ExoticSwap swap = new Codex.ExoticSwap(List.of(), "x", 0, AT);
        Codex.CategoryEntry potion = new Codex.CategoryEntry("POTION", 8, 8, "P", List.of(), AT, AT);
        assertThrows(IllegalArgumentException.class, () -> new Codex.Decks(List.of(potion, potion), List.of(), swap), "a category once");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Decks(List.of(potion), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(false, 3, 0, "", null), "an absent strength carries no tier");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(false, 0, 0, "x", null));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(true, 3, 14, "", AT), "a present strength has its formula");
        assertThrows(IllegalArgumentException.class, () -> new Codex.Strength(true, 3, 14, "x", null));
        Codex.Strength none = Codex.Strength.none();
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", 1, "return 1;", none, List.of(), true, "", AT),
                "a constructed item carries no expression");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", 1, "", none, List.of(), false, "why", AT),
                "a read item carries its expression");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", 1, "", none, List.of(), true, "why", AT),
                "a constructed item has no reason");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", 1, "return 1;", none, List.of(), false, "", AT),
                "a read item says why");
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", -2, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("", "x", AT, "", 1, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "", AT, "", 1, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", null, "", 1, "", none, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", 1, "", null, List.of(), true, "", AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ItemEntry("X", "x", AT, "", 1, "", none, List.of(), true, "", null));
        assertThrows(NullPointerException.class, () -> CodexJson.items(null));
        assertThrows(NullPointerException.class, () -> CodexJson.decks(null));
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
