package org.shatterfish.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The Codex's canonical JSON (story 2.1): one text per file, written with the same
 * {@link JsonWriter} as an Observation's rendering, so that the same records give the same bytes
 * on every machine. The writer sorts every object's keys; lists keep the order the records hold;
 * a table is an array with one entry per line, so that a drift diff names the entry; no floats,
 * nothing that names a machine or a time; line feeds only, and one at the end. A table naming a
 * key twice is refused here, since the records validate entries and this is where a table is.
 */
public final class CodexJson {

    private CodexJson() {
    }

    /** The manifest file's text. */
    public static String manifest(Codex.Manifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("codexVersion").value(manifest.version());
        out.key("upstreamTag").value(manifest.upstreamTag());
        out.key("tables").beginArray();
        for (String table : manifest.tables()) {
            out.value(table);
        }
        out.endArray();
        out.endObject();
        return out.toJson() + "\n";
    }

    /** The hero classes file's text; every class and every subclass once. */
    public static String heroClasses(List<Codex.HeroClassEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<HeroClass> classes = new HashSet<>();
        Set<HeroSubclass> subclasses = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < entries.size(); i++) {
            Codex.HeroClassEntry entry = entries.get(i);
            Codex.distinct(classes, entry.heroClass(), "a hero class");
            for (HeroSubclass subclass : entry.subclasses()) {
                Codex.distinct(subclasses, subclass, "a subclass");
            }
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("heroClass").value(entry.heroClass().name());
            out.key("subclasses").beginArray();
            for (HeroSubclass subclass : entry.subclasses()) {
                out.value(subclass.name());
            }
            out.endArray();
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == entries.size());
        }
        return close(table);
    }

    /** The challenges file's text; every challenge and every mask once. */
    public static String challenges(List<Codex.ChallengeEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<Challenge> challenges = new HashSet<>();
        Set<Integer> masks = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < entries.size(); i++) {
            Codex.ChallengeEntry entry = entries.get(i);
            Codex.distinct(challenges, entry.challenge(), "a challenge");
            Codex.distinct(masks, entry.mask(), "a mask");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("challenge").value(entry.challenge().name());
            out.key("mask").value(entry.mask());
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == entries.size());
        }
        return close(table);
    }

    /** The mobs file's text (story 2.2); every class once. */
    public static String mobs(List<Codex.MobEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<String> classes = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < entries.size(); i++) {
            Codex.MobEntry entry = entries.get(i);
            Codex.distinct(classes, entry.className(), "a mob class");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(entry.className());
            out.key("alignment").value(entry.alignment().name());
            out.key("properties").beginArray();
            for (String property : entry.properties()) {
                out.value(property);
            }
            out.endArray();
            out.key("propertiesRandom").value(entry.propertiesRandom());
            out.key("runDependent").beginArray();
            for (String field : entry.runDependent()) {
                out.value(field);
            }
            out.endArray();
            out.key("statsSetLater").value(entry.statsSetLater());
            out.key("customDefense").value(entry.customDefense());
            out.key("draws").beginArray();
            for (Codex.Citation draw : entry.draws()) {
                out.beginObject();
                out.key("path").value(draw.path());
                out.key("line").value(draw.line());
                out.endObject();
            }
            out.endArray();
            out.key("ht").value(entry.ht());
            out.key("defenseSkill").value(entry.defenseSkill());
            out.key("exp").value(entry.exp());
            out.key("maxLvl").value(entry.maxLvl());
            out.key("damage");
            roll(out, entry.damage());
            out.key("attack");
            roll(out, entry.attack());
            out.key("dr");
            roll(out, entry.dr());
            out.key("loot").beginObject();
            out.key("kind").value(entry.loot().kind().name());
            out.key("name").value(entry.loot().name());
            out.key("declaration").value(entry.loot().declaration());
            out.key("chanceThousandths").value(entry.loot().chanceThousandths());
            out.key("chanceExpression").value(entry.loot().chanceExpression());
            out.key("random").value(entry.loot().random());
            out.key("customLoot").value(entry.loot().customLoot());
            out.key("customChance").value(entry.loot().customChance());
            citation(out, entry.loot().citation());
            out.endObject();
            out.key("variants").beginArray();
            for (Codex.Variant variant : entry.variants()) {
                out.beginObject();
                out.key("depth").value(variant.depth());
                out.key("challenge").value(variant.challenge());
                out.key("fields").beginObject();
                for (Codex.Field field : variant.fields()) {
                    out.key(field.name()).value(field.value());
                }
                out.endObject();
                out.endObject();
            }
            out.endArray();
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == entries.size());
        }
        return close(table);
    }

    /** The spawn rotation file's text (story 2.2): one object, its keys sorted, its depths one per line. */
    public static String spawnRotation(Codex.SpawnRotation rotation) {
        Objects.requireNonNull(rotation, "rotation");
        StringBuilder text = new StringBuilder("{\n");
        JsonWriter alternates = new JsonWriter();
        alternates.beginArray();
        for (Codex.RareAlt alt : rotation.alternates()) {
            alternates.beginObject();
            alternates.key("className").value(alt.className());
            alternates.key("alternate").value(alt.alternate());
            alternates.key("reachable").value(alt.reachable());
            citation(alternates, alt.citation());
            alternates.endObject();
        }
        alternates.endArray();
        text.append("\"alternateChanceExpression\":").append(JsonWriter.quote(rotation.alternateChanceExpression())).append(",\n");
        text.append("\"alternateChancePerMille\":").append(rotation.alternateChancePerMille()).append(",\n");
        text.append("\"alternates\":").append(alternates.toJson()).append(",\n");
        JsonWriter champion = new JsonWriter();
        champion.beginObject();
        champion.key("challenge").value(rotation.champion().challenge().name());
        champion.key("buffs").beginArray();
        for (String buff : rotation.champion().buffs()) {
            champion.value(buff);
        }
        champion.endArray();
        champion.key("exclusions").beginArray();
        for (Codex.Exclusion exclusion : rotation.champion().exclusions()) {
            champion.beginObject();
            champion.key("className").value(exclusion.className());
            champion.key("maxDepth").value(exclusion.maxDepth());
            champion.endObject();
        }
        champion.endArray();
        champion.key("counterExpression").value(rotation.champion().counterExpression());
        citation(champion, rotation.champion().citation());
        champion.endObject();
        text.append("\"champion\":").append(champion.toJson()).append(",\n");
        text.append("\"defaultDepth\":").append(rotation.defaultDepth()).append(",\n");
        text.append("\"depths\":[\n");
        List<Codex.RotationDepth> depths = rotation.depths();
        for (int i = 0; i < depths.size(); i++) {
            Codex.RotationDepth depth = depths.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("depth").value(depth.depth());
            out.key("entries").beginArray();
            for (Codex.RotationEntry entry : depth.entries()) {
                out.beginObject();
                out.key("className").value(entry.className());
                out.key("count").value(entry.count());
                out.key("family").value(entry.family());
                out.endObject();
            }
            out.endArray();
            citation(out, depth.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == depths.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        JsonWriter families = new JsonWriter();
        families.beginArray();
        for (Codex.Family family : rotation.families()) {
            families.beginObject();
            families.key("className").value(family.className());
            families.key("odds").beginArray();
            for (Codex.Odds odds : family.odds()) {
                families.beginObject();
                families.key("className").value(odds.className());
                families.key("perMille").value(odds.perMille());
                families.key("expression").value(odds.expression());
                families.endObject();
            }
            families.endArray();
            citation(families, family.citation());
            families.endObject();
        }
        families.endArray();
        text.append("\"families\":").append(families.toJson()).append(",\n");
        JsonWriter rare = new JsonWriter();
        rare.beginArray();
        for (Codex.RareMob mob : rotation.rareMobs()) {
            rare.beginObject();
            rare.key("depth").value(mob.depth());
            rare.key("className").value(mob.className());
            rare.key("perMille").value(mob.perMille());
            citation(rare, mob.citation());
            rare.endObject();
        }
        rare.endArray();
        text.append("\"rareMobs\":").append(rare.toJson()).append("\n");
        text.append("}\n");
        return text.toString();
    }

    /** The items file's text (story 2.3); every class once. */
    public static String items(List<Codex.ItemEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<String> classes = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < entries.size(); i++) {
            Codex.ItemEntry entry = entries.get(i);
            Codex.distinct(classes, entry.className(), "an item class");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(entry.className());
            out.key("name").value(entry.name());
            out.key("customName").value(entry.customName());
            out.key("nameCitation").beginObject();
            out.key("path").value(entry.nameCitation().path());
            out.key("line").value(entry.nameCitation().line());
            out.endObject();
            out.key("category").value(entry.category());
            out.key("quantity").value(entry.quantity());
            out.key("value").value(entry.value());
            out.key("valueExpression").value(entry.valueExpression());
            out.key("strength").beginObject();
            out.key("present").value(entry.strength().present());
            out.key("tier").value(entry.strength().tier());
            out.key("atLevel0").value(entry.strength().atLevel0());
            out.key("formula").value(entry.strength().formula());
            if (entry.strength().present()) {
                citation(out, entry.strength().citation());
            }
            out.endObject();
            out.key("actions").beginArray();
            for (String action : entry.actions()) {
                out.value(action);
            }
            out.endArray();
            out.key("constructed").value(entry.constructed());
            out.key("reason").value(entry.reason());
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == entries.size());
        }
        return close(table);
    }

    /** The decks file's text (story 2.3): one object, its keys in order, the categories one per line. */
    public static String decks(Codex.Decks decks) {
        Objects.requireNonNull(decks, "decks");
        StringBuilder text = new StringBuilder("{\n\"categories\":[\n");
        List<Codex.CategoryEntry> categories = decks.categories();
        for (int i = 0; i < categories.size(); i++) {
            Codex.CategoryEntry category = categories.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("name").value(category.name());
            out.key("firstProb").value(category.firstProb());
            out.key("secondProb").value(category.secondProb());
            out.key("superClass").value(category.superClass());
            out.key("decks").value(category.decks());
            out.key("classes").beginArray();
            for (Codex.Weighted weighted : category.classes()) {
                out.beginObject();
                out.key("className").value(weighted.className());
                out.key("firstDeck").value(weighted.firstDeck());
                out.key("secondDeck").value(weighted.secondDeck());
                out.key("total").value(weighted.total());
                out.endObject();
            }
            out.endArray();
            citation(out, category.citation());
            out.key("classesCitation").beginObject();
            out.key("path").value(category.classesCitation().path());
            out.key("line").value(category.classesCitation().line());
            out.endObject();
            if (category.weightsCitation() != null) {
                out.key("weightsCitation").beginObject();
                out.key("path").value(category.weightsCitation().path());
                out.key("line").value(category.weightsCitation().line());
                out.endObject();
            }
            if (category.weights2Citation() != null) {
                out.key("weights2Citation").beginObject();
                out.key("path").value(category.weights2Citation().path());
                out.key("line").value(category.weights2Citation().line());
                out.endObject();
            }
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == categories.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        JsonWriter exotic = new JsonWriter();
        exotic.beginObject();
        exotic.key("chanceExpression").value(decks.exotic().chanceExpression());
        exotic.key("chanceWithoutTrinketPerMille").value(decks.exotic().chanceWithoutTrinketPerMille());
        exotic.key("pairs").beginArray();
        for (Codex.ExoticPair pair : decks.exotic().pairs()) {
            exotic.beginObject();
            exotic.key("regular").value(pair.regular());
            exotic.key("exotic").value(pair.exotic());
            exotic.endObject();
        }
        exotic.endArray();
        citation(exotic, decks.exotic().citation());
        exotic.endObject();
        text.append("\"exotic\":").append(exotic.toJson()).append(",\n");
        JsonWriter pools = new JsonWriter();
        pools.beginArray();
        for (Codex.LabelPool pool : decks.labelPools()) {
            pools.beginObject();
            pools.key("family").value(pool.family());
            pools.key("labels").beginArray();
            for (Codex.Label label : pool.labels()) {
                pools.beginObject();
                pools.key("key").value(label.key());
                pools.key("name").value(label.name());
                pools.key("exoticName").value(label.exoticName());
                citation(pools, label.citation());
                pools.key("nameCitation").beginObject();
                pools.key("path").value(label.nameCitation().path());
                pools.key("line").value(label.nameCitation().line());
                pools.endObject();
                if (label.exoticCitation() != null) {
                    pools.key("exoticCitation").beginObject();
                    pools.key("path").value(label.exoticCitation().path());
                    pools.key("line").value(label.exoticCitation().line());
                    pools.endObject();
                }
                pools.endObject();
            }
            pools.endArray();
            citation(pools, pool.citation());
            pools.endObject();
        }
        pools.endArray();
        text.append("\"labelPools\":").append(pools.toJson()).append("\n");
        text.append("}\n");
        return text.toString();
    }

    private static void roll(JsonWriter out, Codex.Roll roll) {
        out.beginObject();
        out.key("kind").value(roll.kind().name());
        out.key("min").value(roll.min());
        out.key("max").value(roll.max());
        out.key("expression").value(roll.expression());
        citation(out, roll.citation());
        out.endObject();
    }

    private static void citation(JsonWriter out, Codex.Citation citation) {
        out.key("citation").beginObject();
        out.key("path").value(citation.path());
        out.key("line").value(citation.line());
        out.endObject();
    }

    private static StringBuilder table() {
        return new StringBuilder("[\n");
    }

    private static void row(StringBuilder table, JsonWriter entry, boolean last) {
        table.append("  ").append(entry.toJson()).append(last ? "\n" : ",\n");
    }

    private static String close(StringBuilder table) {
        return table.append("]\n").toString();
    }
}
