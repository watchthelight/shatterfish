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

    /** The guarantees file's text (story 2.4): one object, its keys in order, the drops one per line. */
    public static String guarantees(Codex.Guarantees guarantees) {
        Objects.requireNonNull(guarantees, "guarantees");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"bossCitation\":").append(citationJson(guarantees.bossCitation())).append(",\n");
        text.append("\"bossDepths\":").append(ints(guarantees.bossDepths())).append(",\n");
        text.append("\"counters\":").append(values(guarantees.counters())).append(",\n");
        text.append("\"countersCitation\":").append(citationJson(guarantees.countersCitation())).append(",\n");
        text.append("\"drops\":[\n");
        List<Codex.DropSchedule> drops = guarantees.drops();
        for (int i = 0; i < drops.size(); i++) {
            Codex.DropSchedule drop = drops.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("name").value(drop.name());
            out.key("item").value(drop.item());
            out.key("method").value(drop.method());
            out.key("once").value(drop.once());
            out.key("perSet").value(drop.perSet());
            out.key("expression").value(drop.expression());
            citation(out, drop.citation());
            out.key("placementCitation").beginObject();
            out.key("path").value(drop.placementCitation().path());
            out.key("line").value(drop.placementCitation().line());
            out.endObject();
            out.key("entries").beginArray();
            for (Codex.ScheduleEntry entry : drop.entries()) {
                out.beginObject();
                out.key("depth").value(entry.depth());
                out.key("count").value(entry.count());
                out.key("neededPerMille").value(entry.neededPerMille());
                out.key("placedPerMille").value(entry.placedPerMille());
                out.key("placedNoScrollsPerMille").value(entry.placedNoScrollsPerMille());
                out.endObject();
            }
            out.endArray();
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == drops.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"gateExpression\":").append(string(guarantees.gateExpression())).append(",\n");
        text.append("\"noScrollsCitation\":").append(citationJson(guarantees.noScrollsCitation())).append(",\n");
        text.append("\"noScrollsExpression\":").append(string(guarantees.noScrollsExpression())).append(",\n");
        text.append("\"placementCitation\":").append(citationJson(guarantees.placementCitation())).append(",\n");
        text.append("\"placements\":[\n");
        List<Codex.Placement> placements = guarantees.placements();
        for (int i = 0; i < placements.size(); i++) {
            Codex.Placement placement = placements.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("depth").value(placement.depth());
            out.key("levelClass").value(placement.levelClass());
            out.key("placesSpawnList").value(placement.placesSpawnList());
            citation(out, placement.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == placements.size() ? "\n" : ",\n");
        }
        text.append("]\n");
        text.append("}\n");
        return text.toString();
    }

    /** The tiers file's text (story 2.4): one object, its keys in order, the rows one per line. */
    public static String tiers(Codex.Tiers tiers) {
        Objects.requireNonNull(tiers, "tiers");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"armor\":").append(rule(tiers.armor())).append(",\n");
        JsonWriter arrays = new JsonWriter();
        arrays.beginArray();
        for (Codex.Rule array : tiers.arrays()) {
            arrays.beginObject();
            arrays.key("what").value(array.what());
            arrays.key("expression").value(array.expression());
            citation(arrays, array.citation());
            arrays.endObject();
        }
        arrays.endArray();
        text.append("\"arrays\":").append(arrays.toJson()).append(",\n");
        text.append("\"citation\":").append(citationJson(tiers.citation())).append(",\n");
        text.append("\"gate\":").append(rule(tiers.gate())).append(",\n");
        text.append("\"missile\":").append(rule(tiers.missile())).append(",\n");
        text.append("\"rows\":[\n");
        List<Codex.TierRow> rows = tiers.rows();
        for (int i = 0; i < rows.size(); i++) {
            Codex.TierRow row = rows.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("floorSet").value(row.floorSet());
            out.key("depthFrom").value(row.depthFrom());
            out.key("depthTo").value(row.depthTo());
            out.key("weights").beginArray();
            for (int weight : row.weights()) {
                out.value(weight);
            }
            out.endArray();
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == rows.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"weapon\":").append(rule(tiers.weapon())).append("\n");
        text.append("}\n");
        return text.toString();
    }

    /** The rooms file's text (story 2.4): one object, its keys in order, the lists and the rooms one per line. */
    public static String rooms(Codex.Rooms rooms) {
        Objects.requireNonNull(rooms, "rooms");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"baseSecretsPerRegionThousandths\":").append(ints(rooms.baseSecretsPerRegionThousandths())).append(",\n");
        text.append("\"lists\":[\n");
        List<Codex.RoomList> lists = rooms.lists();
        for (int i = 0; i < lists.size(); i++) {
            Codex.RoomList list = lists.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("name").value(list.name());
            out.key("members").beginArray();
            for (String member : list.members()) {
                out.value(member);
            }
            out.endArray();
            citation(out, list.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == lists.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"queue\":").append(rule(rooms.queue())).append(",\n");
        text.append("\"secrets\":[\n");
        roomRows(text, rooms.secrets());
        text.append("],\n");
        text.append("\"secretsCitation\":").append(citationJson(rooms.secretsCitation())).append(",\n");
        text.append("\"specials\":[\n");
        roomRows(text, rooms.specials());
        text.append("]\n");
        text.append("}\n");
        return text.toString();
    }

    private static void roomRows(StringBuilder text, List<Codex.RoomEntry> rooms) {
        for (int i = 0; i < rooms.size(); i++) {
            Codex.RoomEntry room = rooms.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(room.className());
            out.key("secret").value(room.secret());
            out.key("spawns").beginArray();
            for (Codex.Spawn spawn : room.spawns()) {
                out.beginObject();
                out.key("className").value(spawn.className());
                out.key("count").value(spawn.count());
                out.key("conditional").value(spawn.conditional());
                out.key("floorDrop").value(spawn.floorDrop());
                citation(out, spawn.citation());
                out.endObject();
            }
            out.endArray();
            out.key("draws").beginArray();
            for (Codex.Draw draw : room.draws()) {
                out.beginObject();
                out.key("expression").value(draw.expression());
                citation(out, draw.citation());
                out.endObject();
            }
            out.endArray();
            citation(out, room.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == rooms.size() ? "\n" : ",\n");
        }
    }

    private static String rule(Codex.Rule rule) {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("what").value(rule.what());
        out.key("expression").value(rule.expression());
        citation(out, rule.citation());
        out.endObject();
        return out.toJson();
    }

    private static String citationJson(Codex.Citation citation) {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("path").value(citation.path());
        out.key("line").value(citation.line());
        out.endObject();
        return out.toJson();
    }

    private static String ints(List<Integer> values) {
        JsonWriter out = new JsonWriter();
        out.beginArray();
        for (int value : values) {
            out.value(value);
        }
        out.endArray();
        return out.toJson();
    }

    private static String values(List<String> values) {
        JsonWriter out = new JsonWriter();
        out.beginArray();
        for (String value : values) {
            out.value(value);
        }
        out.endArray();
        return out.toJson();
    }

    private static String string(String value) {
        JsonWriter out = new JsonWriter();
        out.beginArray();
        out.value(value);
        out.endArray();
        String array = out.toJson();
        return array.substring(1, array.length() - 1);
    }

    /** The combat file's text (story 2.5): one object, its keys in order, the cells and the rolls one per line. */
    public static String combat(Codex.Combat combat) {
        Objects.requireNonNull(combat, "combat");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"armours\":[\n");
        rollRows(text, combat.armours());
        text.append("],\n");
        JsonWriter head = new JsonWriter();
        head.beginObject();
        head.key("method").value(combat.hit().method());
        head.key("measured").value(combat.hit().measured());
        head.key("reason").value(combat.hit().reason());
        citation(head, combat.hit().citation());
        head.key("accuracy").beginObject();
        axis(head, combat.hit().accuracy());
        head.endObject();
        head.key("evasion").beginObject();
        axis(head, combat.hit().evasion());
        head.endObject();
        head.endObject();
        text.append("\"hit\":").append(head.toJson()).append(",\n");
        text.append("\"hitCells\":[\n");
        List<Codex.HitCell> cells = combat.hit().cells();
        for (int i = 0; i < cells.size(); i++) {
            Codex.HitCell cell = cells.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("accuracy").value(cell.accuracy());
            out.key("evasion").value(cell.evasion());
            out.key("hitPerMille").value(cell.hitPerMille());
            out.key("samples").value(cell.samples());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == cells.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"mobs\":[\n");
        rollRows(text, combat.mobs());
        text.append("],\n");
        JsonWriter seed = new JsonWriter();
        seed.beginArray();
        seed.value(combat.seed());
        seed.endArray();
        String seedText = seed.toJson();
        text.append("\"seed\":").append(seedText, 1, seedText.length() - 1).append(",\n");
        text.append("\"weapons\":[\n");
        rollRows(text, combat.weapons());
        text.append("]\n");
        text.append("}\n");
        return text.toString();
    }

    private static void axis(JsonWriter out, Codex.Grid grid) {
        out.key("what").value(grid.what());
        out.key("from").value(grid.from());
        out.key("to").value(grid.to());
        out.key("step").value(grid.step());
    }

    private static void rollRows(StringBuilder text, List<Codex.RollEntry> rolls) {
        for (int i = 0; i < rolls.size(); i++) {
            Codex.RollEntry entry = rolls.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(entry.className());
            out.key("tier").value(entry.tier());
            out.key("level").value(entry.level());
            out.key("method").value(entry.method());
            out.key("spread").beginObject();
            out.key("min").value(entry.spread().min());
            out.key("max").value(entry.spread().max());
            out.key("meanPerMille").value(entry.spread().meanPerMille());
            out.key("samples").value(entry.spread().samples());
            out.endObject();
            citation(out, entry.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == rolls.size() ? "\n" : ",\n");
        }
    }

    /** The traps file's text (story 2.6): the pools and then the traps, one per line. */
    public static String traps(List<Codex.TrapEntry> traps, List<Codex.TrapPool> pools) {
        Objects.requireNonNull(traps, "traps");
        Objects.requireNonNull(pools, "pools");
        Set<String> classes = new HashSet<>();
        StringBuilder text = new StringBuilder("{\n\"pools\":[\n");
        Set<String> levels = new HashSet<>();
        for (int i = 0; i < pools.size(); i++) {
            Codex.TrapPool pool = pools.get(i);
            Codex.distinct(levels, pool.levelClass() + " " + pool.condition(), "a level's trap pool");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("levelClass").value(pool.levelClass());
            out.key("declaredBy").value(pool.declaredBy());
            out.key("condition").value(pool.condition());
            out.key("nTrapsExpression").value(pool.nTrapsExpression());
            out.key("traps").beginArray();
            for (Codex.Weighted trap : pool.traps()) {
                out.beginObject();
                out.key("className").value(trap.className());
                out.key("weightPerMille").value(trap.firstDeck());
                out.endObject();
            }
            out.endArray();
            citation(out, pool.citation());
            out.key("nTrapsCitation").beginObject();
            out.key("path").value(pool.nTrapsCitation().path());
            out.key("line").value(pool.nTrapsCitation().line());
            out.endObject();
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == pools.size() ? "\n" : ",\n");
        }
        text.append("],\n\"traps\":[\n");
        for (int i = 0; i < traps.size(); i++) {
            Codex.TrapEntry trap = traps.get(i);
            Codex.distinct(classes, trap.className(), "a trap");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(trap.className());
            out.key("name").value(trap.name());
            out.key("nameFrom").value(trap.nameFrom());
            out.key("canBeHidden").value(trap.canBeHidden());
            out.key("canBeSearched").value(trap.canBeSearched());
            out.key("active").value(trap.active());
            out.key("activateFrom").value(trap.activateFrom());
            out.key("activateExpression").value(trap.activateExpression());
            out.key("alsoOnTheCell").beginArray();
            for (Codex.Rule rule : trap.alsoOnTheCell()) {
                out.beginObject();
                out.key("what").value(rule.what());
                out.key("expression").value(rule.expression());
                citation(out, rule.citation());
                out.endObject();
            }
            out.endArray();
            citation(out, trap.citation());
            out.key("activateCitation").beginObject();
            out.key("path").value(trap.activateCitation().path());
            out.key("line").value(trap.activateCitation().line());
            out.endObject();
            out.key("nameCitation").beginObject();
            out.key("path").value(trap.nameCitation().path());
            out.key("line").value(trap.nameCitation().line());
            out.endObject();
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == traps.size() ? "\n" : ",\n");
        }
        text.append("]\n}\n");
        return text.toString();
    }

    /** The recipes file's text (story 2.6): one recipe per line, in the order the pot tries them. */
    public static String recipes(List<Codex.RecipeEntry> recipes) {
        Objects.requireNonNull(recipes, "recipes");
        Set<String> classes = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < recipes.size(); i++) {
            Codex.RecipeEntry recipe = recipes.get(i);
            Codex.distinct(classes, recipe.className(), "a recipe");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(recipe.className());
            out.key("ingredients").value(recipe.ingredients());
            out.key("simple").value(recipe.simple());
            out.key("inputs").beginArray();
            for (Codex.Ingredient input : recipe.inputs()) {
                out.beginObject();
                out.key("className").value(input.className());
                out.key("quantity").value(input.quantity());
                out.endObject();
            }
            out.endArray();
            out.key("output").value(recipe.output());
            out.key("outQuantity").value(recipe.outQuantity());
            out.key("cost").value(recipe.cost());
            out.key("answers").beginArray();
            for (Codex.Rule answer : recipe.answers()) {
                out.beginObject();
                out.key("what").value(answer.what());
                out.key("expression").value(answer.expression());
                citation(out, answer.citation());
                out.endObject();
            }
            out.endArray();
            citation(out, recipe.citation());
            out.key("registryCitation").beginObject();
            out.key("path").value(recipe.registryCitation().path());
            out.key("line").value(recipe.registryCitation().line());
            out.endObject();
            out.endObject();
            row(table, out, i + 1 == recipes.size());
        }
        return close(table);
    }

    /** The level structure's text (story 2.6): one object, its keys in order, the floors one per line. */
    public static String structure(Codex.Structure structure) {
        Objects.requireNonNull(structure, "structure");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"bossCitation\":").append(citationJson(structure.bossCitation())).append(",\n");
        text.append("\"bossExpression\":").append(string(structure.bossExpression())).append(",\n");
        text.append("\"feelingCitation\":").append(citationJson(structure.feelingCitation())).append(",\n");
        text.append("\"feelingGate\":").append(string(structure.feelingGate())).append(",\n");
        text.append("\"feelings\":[\n");
        List<Codex.FeelingEntry> feelings = structure.feelings();
        for (int i = 0; i < feelings.size(); i++) {
            Codex.FeelingEntry feeling = feelings.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("what").value(feeling.what());
            out.key("chancePerMille").value(feeling.chancePerMille());
            out.key("expression").value(feeling.expression());
            out.key("effects").beginArray();
            for (Codex.Rule effect : feeling.effects()) {
                out.beginObject();
                out.key("what").value(effect.what());
                out.key("expression").value(effect.expression());
                citation(out, effect.citation());
                out.endObject();
            }
            out.endArray();
            citation(out, feeling.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == feelings.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"levels\":[\n");
        List<Codex.LevelEntry> levels = structure.levels();
        for (int i = 0; i < levels.size(); i++) {
            Codex.LevelEntry level = levels.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("branch").value(level.branch());
            out.key("depth").value(level.depth());
            out.key("levelClass").value(level.levelClass());
            out.key("boss").value(level.boss());
            out.key("shop").value(level.shop());
            out.key("sealed").value(level.sealed());
            out.key("sealedBy").value(level.sealedBy());
            citation(out, level.citation());
            out.key("shopCitation").beginObject();
            out.key("path").value(level.shopCitation().path());
            out.key("line").value(level.shopCitation().line());
            out.endObject();
            if (level.sealed()) {
                out.key("sealCitation").beginObject();
                out.key("path").value(level.sealCitation().path());
                out.key("line").value(level.sealCitation().line());
                out.endObject();
            }
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == levels.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        JsonWriter others = new JsonWriter();
        others.beginArray();
        for (Codex.Rule other : structure.otherFeelingSources()) {
            others.beginObject();
            others.key("what").value(other.what());
            others.key("expression").value(other.expression());
            citation(others, other.citation());
            others.endObject();
        }
        others.endArray();
        text.append("\"otherFeelingSources\":").append(others.toJson()).append(",\n");
        text.append("\"otherwiseCitation\":").append(citationJson(structure.otherwiseCitation())).append(",\n");
        text.append("\"otherwiseClass\":").append(string(structure.otherwiseClass())).append(",\n");
        text.append("\"roomsCitation\":").append(citationJson(structure.roomsCitation())).append(",\n");
        text.append("\"sealedCitation\":").append(citationJson(structure.sealedCitation())).append(",\n");
        text.append("\"shopCitation\":").append(citationJson(structure.shopCitation())).append(",\n");
        text.append("\"shopExpression\":").append(string(structure.shopExpression())).append("\n");
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

    /** The documents file's text (story 2.7): one document per line, its pages in the game's own order. */
    public static String documents(List<Codex.DocumentEntry> documents) {
        Objects.requireNonNull(documents, "documents");
        Set<String> named = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < documents.size(); i++) {
            Codex.DocumentEntry document = documents.get(i);
            Codex.distinct(named, document.document(), "a document");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("document").value(document.document());
            out.key("lore").value(document.lore());
            out.key("title").value(document.title());
            out.key("hint").value(document.hint());
            out.key("titleCitation").beginObject();
            out.key("path").value(document.titleCitation().path());
            out.key("line").value(document.titleCitation().line());
            out.endObject();
            if (!document.hint().isEmpty()) {
                out.key("hintCitation").beginObject();
                out.key("path").value(document.hintCitation().path());
                out.key("line").value(document.hintCitation().line());
                out.endObject();
            }
            out.key("pages").beginArray();
            for (Codex.DocumentPage page : document.pages()) {
                out.beginObject();
                out.key("page").value(page.page());
                out.key("title").value(page.title());
                out.key("body").value(page.body());
                citation(out, page.citation());
                out.key("titleCitation").beginObject();
                out.key("path").value(page.titleCitation().path());
                out.key("line").value(page.titleCitation().line());
                out.endObject();
                out.endObject();
            }
            out.endArray();
            citation(out, document.citation());
            out.endObject();
            row(table, out, i + 1 == documents.size());
        }
        return close(table);
    }

    /**
     * The changelog file's text (story 2.7): the version record, then one entry per line in the
     * order the game's own package writes them.
     */
    public static String changelog(Codex.VersionRecord version, List<Codex.ChangeEntry> entries) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(entries, "entries");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"entries\":[\n");
        for (int i = 0; i < entries.size(); i++) {
            Codex.ChangeEntry entry = entries.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("className").value(entry.className());
            out.key("tab").value(entry.tab());
            out.key("title").value(entry.title());
            out.key("titleKey").value(entry.titleKey());
            out.key("titleExpression").value(entry.titleExpression());
            out.key("major").value(entry.major());
            out.key("text").value(entry.text());
            out.key("conditionExpression").value(entry.conditionExpression());
            out.key("dates").beginArray();
            for (String date : entry.dates()) {
                out.value(date);
            }
            out.endArray();
            out.key("headings").beginArray();
            for (Codex.ChangeHeading heading : entry.headings()) {
                out.beginObject();
                out.key("title").value(heading.title());
                out.key("titleKey").value(heading.titleKey());
                out.key("titleExpression").value(heading.titleExpression());
                out.key("conditionExpression").value(heading.conditionExpression());
                out.key("dates").beginArray();
                for (String date : heading.dates()) {
                    out.value(date);
                }
                out.endArray();
                citation(out, heading.citation());
                out.endObject();
            }
            out.endArray();
            citation(out, entry.citation());
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == entries.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        JsonWriter saves = new JsonWriter();
        saves.beginArray();
        for (Codex.Rule saveCode : version.saveCodes()) {
            saves.beginObject();
            saves.key("what").value(saveCode.what());
            saves.key("expression").value(saveCode.expression());
            citation(saves, saveCode.citation());
            saves.endObject();
        }
        saves.endArray();
        text.append("\"saveCodes\":").append(saves.toJson()).append(",\n");
        text.append("\"versionCitation\":").append(citationJson(version.citation())).append(",\n");
        text.append("\"versionCode\":").append(version.code()).append(",\n");
        text.append("\"versionName\":").append(string(version.name())).append("\n");
        text.append("}\n");
        return text.toString();
    }

    /** The assets file's text (story 2.7): one asset per line, the constants first and then the literals. */
    public static String assets(List<Codex.AssetEntry> assets) {
        Objects.requireNonNull(assets, "assets");
        Set<String> paths = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < assets.size(); i++) {
            Codex.AssetEntry asset = assets.get(i);
            Codex.distinct(paths, asset.path(), "an asset");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("path").value(asset.path());
            out.key("group").value(asset.group());
            out.key("constant").value(asset.constant());
            out.key("present").value(asset.present());
            out.key("assetRoot").value(asset.assetRoot());
            citation(out, asset.citation());
            out.endObject();
            row(table, out, i + 1 == assets.size());
        }
        return close(table);
    }

    /** The vocabulary file's text (story 2.8): one name per line, in the order the join sorts them. */
    public static String vocabulary(Codex.Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        StringBuilder text = new StringBuilder("{\n");
        text.append("\"consumer\":").append(string(vocabulary.consumer())).append(",\n");
        text.append("\"entries\":[\n");
        List<Codex.VocabularyEntry> entries = vocabulary.entries();
        for (int i = 0; i < entries.size(); i++) {
            Codex.VocabularyEntry entry = entries.get(i);
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("kind").value(entry.kind());
            out.key("name").value(entry.name());
            out.key("shared").value(entry.shared());
            side(out, "here", entry.here());
            side(out, "there", entry.there());
            out.key("differences").beginArray();
            for (Codex.MechanicDifference difference : entry.differences()) {
                out.beginObject();
                out.key("what").value(difference.what());
                out.key("here").value(difference.here());
                out.key("there").value(difference.there());
                out.key("comparable").value(difference.comparable());
                out.endObject();
            }
            out.endArray();
            out.endObject();
            text.append("  ").append(out.toJson()).append(i + 1 == entries.size() ? "\n" : ",\n");
        }
        text.append("],\n");
        text.append("\"tag\":").append(string(vocabulary.tag())).append(",\n");
        text.append("\"vanillaTag\":").append(string(vocabulary.vanillaTag())).append("\n");
        text.append("}\n");
        return text.toString();
    }

    /** One game's side of a name, or nothing where that game does not have it. */
    private static void side(JsonWriter out, String key, Codex.VocabularySide side) {
        if (side == null) {
            return;
        }
        out.key(key).beginObject();
        out.key("name").value(side.name());
        out.key("classNames").beginArray();
        for (String className : side.classNames()) {
            out.value(className);
        }
        out.endArray();
        out.key("facts").beginArray();
        for (Codex.Rule fact : side.facts()) {
            out.beginObject();
            out.key("what").value(fact.what());
            out.key("expression").value(fact.expression());
            citation(out, fact.citation());
            out.endObject();
        }
        out.endArray();
        out.key("citations").beginArray();
        for (Codex.Citation citation : side.citations()) {
            out.beginObject();
            out.key("path").value(citation.path());
            out.key("line").value(citation.line());
            out.endObject();
        }
        out.endArray();
        out.endObject();
    }

    /** The strings file's text (story 2.7): one line of the game's own text per line, in bundle order. */
    public static String strings(List<Codex.StringEntry> strings) {
        Objects.requireNonNull(strings, "strings");
        Set<String> keys = new HashSet<>();
        StringBuilder table = table();
        for (int i = 0; i < strings.size(); i++) {
            Codex.StringEntry entry = strings.get(i);
            Codex.distinct(keys, entry.key(), "a text key");
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("key").value(entry.key());
            out.key("value").value(entry.value());
            out.key("bundle").value(entry.bundle());
            out.key("className").value(entry.className());
            out.key("suffix").value(entry.suffix());
            out.key("reason").value(entry.reason());
            citation(out, entry.citation());
            out.endObject();
            row(table, out, i + 1 == strings.size());
        }
        return close(table);
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
