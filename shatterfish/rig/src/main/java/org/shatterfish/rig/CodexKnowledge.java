package org.shatterfish.rig;

import org.shatterfish.api.Codex;
import org.shatterfish.api.ItemKind;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of the Codex a Brain is built on, read from disk and handed over as one {@code api}
 * value (story 4.2): the identifiable families with their appearances and deck weights, the items
 * special rooms place, and the drops guaranteed per set of floors.
 *
 * <p>Everything here is general game knowledge, the same for every Run: the committed tables under
 * {@code codex/<tag>/}, which the Codex generator reads from the pinned code and which
 * {@code CodexSeedFreeTest} holds free of any seed. The manifest is checked by
 * {@link CodexManifest#read} first, so a Codex of another version or tag is refused before any
 * table is read. The bestiary (tactics/bestiary.json) rides along, joined with the mob table and the
 * spawn rotation (the bestiary's lever 0, docs/ideas.md).
 */
public final class CodexKnowledge {

    /** The families a Brain reasons about the identity of: the ones whose appearances are shuffled per Run. */
    private static final List<ItemKind> FAMILIES = List.of(ItemKind.POTION, ItemKind.SCROLL, ItemKind.RING);

    /**
     * The guaranteed drops a Brain counts (story 4.2). A drop whose item the Codex does not name --
     * the laboratory room, which is a room -- is skipped; these two may not be.
     */
    static final List<String> REQUIRED = List.of("STRENGTH_POTIONS", "UPGRADE_SCROLLS");

    private CodexKnowledge() {
    }

    /** The knowledge in {@code folder} ({@code codex/<tag>}), checked against this build. */
    public static Codex.Knowledge read(Path folder, String tag) {
        Codex.Manifest manifest = CodexManifest.read(folder, tag);
        Map<String, String> names = new HashMap<>();
        for (String raw : Json.array(table(folder, "items.json"))) {
            Map<String, String> item = Json.object(raw);
            names.put(Json.string(Json.required(item, "className", "item")), Json.string(Json.required(item, "name", "item")));
        }

        Map<String, String> decks = Json.object(table(folder, "decks.json"));
        Map<String, List<String>> labels = new HashMap<>();
        for (String raw : Json.array(Json.required(decks, "labelPools", "decks"))) {
            Map<String, String> pool = Json.object(raw);
            List<String> shown = new ArrayList<>();
            for (String label : Json.array(Json.required(pool, "labels", "label pool"))) {
                shown.add(Json.string(Json.required(Json.object(label), "name", "label")));
            }
            labels.put(Json.string(Json.required(pool, "family", "label pool")).toUpperCase(java.util.Locale.ROOT), shown);
        }
        List<Codex.Identities> families = new ArrayList<>();
        for (String raw : Json.array(Json.required(decks, "categories", "decks"))) {
            Map<String, String> category = Json.object(raw);
            String name = Json.string(Json.required(category, "name", "category"));
            ItemKind kind = FAMILIES.stream().filter(family -> family.name().equals(name)).findFirst().orElse(null);
            if (kind == null) {
                continue;
            }
            List<Codex.Candidate> candidates = new ArrayList<>();
            for (String weighted : Json.array(Json.required(category, "classes", "category"))) {
                Map<String, String> one = Json.object(weighted);
                String className = Json.string(Json.required(one, "className", "weighted class"));
                candidates.add(new Codex.Candidate(className, named(names, className),
                        Json.integer(Json.required(one, "total", "weighted class"))));
            }
            List<String> shown = labels.get(name);
            if (shown == null) {
                throw new IllegalArgumentException("the Codex in " + folder + " has no appearances for " + name);
            }
            families.add(new Codex.Identities(kind, shown, candidates));
        }
        families.sort(java.util.Comparator.comparing(Codex.Identities::kind));

        Map<String, String> rooms = Json.object(table(folder, "rooms.json"));
        List<Codex.RoomSpawn> spawns = new ArrayList<>();
        for (String list : List.of("specials", "secrets")) {
            for (String raw : Json.array(Json.required(rooms, list, "rooms"))) {
                Map<String, String> room = Json.object(raw);
                for (String spawnRaw : Json.array(Json.required(room, "spawns", "room"))) {
                    Map<String, String> spawn = Json.object(spawnRaw);
                    // The unconditional items a room adds to its floor's spawn list; a floor drop
                    // or a conditional one is not a fact about the floor.
                    if (Json.bool(Json.required(spawn, "conditional", "spawn")) || Json.bool(Json.required(spawn, "floorDrop", "spawn"))) {
                        continue;
                    }
                    String className = Json.string(Json.required(spawn, "className", "spawn"));
                    spawns.add(new Codex.RoomSpawn(Json.string(Json.required(room, "className", "room")),
                            className, named(names, className)));
                }
            }
        }

        Map<String, String> guarantees = Json.object(table(folder, "guarantees.json"));
        List<String> bosses = Json.array(Json.required(guarantees, "bossDepths", "guarantees"));
        // A set of floors ends at a boss: the drop rules count sets as depth / 5
        // (Dungeon.java:531, :548), and the boss depths are 5, 10, 15, 20, 25 (Dungeon.java:441).
        if (bosses.isEmpty()) {
            throw new IllegalArgumentException("the Codex in " + folder + " names no boss depth, so no set of floors");
        }
        int floorsPerSet = Json.integer(bosses.get(0));
        List<Codex.Guarantee> drops = new ArrayList<>();
        for (String raw : Json.array(Json.required(guarantees, "drops", "guarantees"))) {
            Map<String, String> drop = Json.object(raw);
            String item = Json.string(Json.required(drop, "item", "drop"));
            if (Json.bool(Json.required(drop, "once", "drop")) || !names.containsKey(item)) {
                continue;
            }
            drops.add(new Codex.Guarantee(Json.string(Json.required(drop, "name", "drop")), item, names.get(item),
                    Json.integer(Json.required(drop, "perSet", "drop")), floorsPerSet));
        }
        for (String counter : REQUIRED) {
            if (drops.stream().noneMatch(drop -> drop.counter().equals(counter))) {
                throw new IllegalArgumentException("the Codex in " + folder + " has no guaranteed drop " + counter
                        + " for an item it names; a Brain built on it would count nothing owed");
            }
        }
        return new Codex.Knowledge(manifest, families, spawns, drops, threats(folder), gear(folder, names, "weapons"),
                gear(folder, names, "armours"), immovable(folder), bestiary(folder, tag));
    }

    /** The bestiary's file, beside the Codex: {@code codex/<tag>} is two folders below the repository root. */
    static final String BESTIARY = "tactics/bestiary.json";

    /** Where the bestiary lies for the Codex in {@code folder} ({@code <root>/codex/<tag>}). */
    static Path bestiaryFile(Path folder) {
        Path root = folder.toAbsolutePath().normalize().getParent().getParent();
        return root.resolve(BESTIARY);
    }

    /**
     * The bestiary's tags per enemy class ({@code tactics/bestiary.json}, docs/bestiary/index.md), with
     * two Codex facts joined in so that a Brain can tell apart the classes sharing a display name
     * ({@link Codex.Knowledge#tactics}): the alignment the mob table creates each class with, and the
     * depths the spawn rotation places it on, its own, its family's (the gnoll shamans, the
     * elementals) and its rare alternate's base (an albino rat is placed where a rat is), plus the
     * rare-mob depths; the boss floors are left out, since they create no rotation mobs
     * (docs/bestiary/index.md, "How it was made and checked"). The file is refused when it was read
     * at another tag or names a class the mob table does not have.
     */
    static List<Codex.Tactics> bestiary(Path folder, String tag) {
        Path file = bestiaryFile(folder);
        String text;
        try {
            text = compact(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("the bestiary " + file + " could not be read", e);
        }
        Map<String, String> bestiary = Json.object(text);
        if (Json.integer(Json.required(bestiary, "version", "bestiary")) != 1) {
            throw new IllegalArgumentException("the bestiary " + file + " is not format 1");
        }
        String read = Json.string(Json.required(bestiary, "tag", "bestiary"));
        if (!read.equals(tag)) {
            throw new IllegalArgumentException("the bestiary " + file + " was read at " + read + ", not " + tag);
        }
        Map<String, org.shatterfish.api.Alignment> alignments = new HashMap<>();
        for (String raw : Json.array(table(folder, "mobs.json"))) {
            Map<String, String> mob = Json.object(raw);
            alignments.put(Json.string(Json.required(mob, "className", "mob")),
                    org.shatterfish.api.Alignment.valueOf(Json.string(Json.required(mob, "alignment", "mob"))));
        }
        Map<String, java.util.TreeSet<Integer>> depths = rotationDepths(folder);
        List<Codex.Tactics> entries = new ArrayList<>();
        for (String raw : Json.array(Json.required(bestiary, "entries", "bestiary"))) {
            Map<String, String> entry = Json.object(raw);
            String className = Json.string(Json.required(entry, "className", "bestiary entry"));
            org.shatterfish.api.Alignment alignment = alignments.get(className);
            if (alignment == null) {
                throw new IllegalArgumentException("the bestiary names " + className + ", which the Codex's mob table does not have");
            }
            entries.add(new Codex.Tactics(className, Json.string(Json.required(entry, "displayName", className)), alignment,
                    List.copyOf(depths.getOrDefault(className, new java.util.TreeSet<>())),
                    Json.string(Json.required(entry, "speed", className)), Json.string(Json.required(entry, "attack", className)),
                    Json.integer(Json.required(entry, "reach", className)), Json.bool(Json.required(entry, "flying", className)),
                    Json.bool(Json.required(entry, "amphibious", className)), strings(Json.required(entry, "immune", className)),
                    strings(Json.required(entry, "inflicts", className)), Json.string(Json.required(entry, "ai", className)),
                    Json.bool(Json.required(entry, "evasive", className)), Json.bool(Json.required(entry, "splits", className)),
                    Json.bool(Json.required(entry, "telegraph", className)), Json.string(Json.required(entry, "approach", className)),
                    Json.bool(Json.required(entry, "rangedFirst", className)), Json.bool(Json.required(entry, "outrunnable", className)),
                    strings(Json.required(entry, "breakContact", className))));
        }
        return entries;
    }

    /**
     * The depths the spawn rotation places each class on, boss floors left out: the rotation's own
     * entries, a family's members for a family entry, the rare mobs at their depth, and a rare
     * alternate wherever its base is placed (MobSpawner.java:62-65, :71-212, :215-240, :244-275).
     */
    static Map<String, java.util.TreeSet<Integer>> rotationDepths(Path folder) {
        java.util.Set<Integer> bosses = new java.util.HashSet<>();
        for (String raw : Json.array(Json.required(Json.object(table(folder, "levels.json")), "levels", "levels"))) {
            Map<String, String> level = Json.object(raw);
            if (Json.bool(Json.required(level, "boss", "level"))) {
                bosses.add(Json.integer(Json.required(level, "depth", "level")));
            }
        }
        Map<String, String> rotation = Json.object(table(folder, "spawn-rotation.json"));
        Map<String, List<String>> families = new HashMap<>();
        for (String raw : Json.array(Json.required(rotation, "families", "rotation"))) {
            Map<String, String> family = Json.object(raw);
            List<String> members = new ArrayList<>();
            for (String odds : Json.array(Json.required(family, "odds", "family"))) {
                members.add(Json.string(Json.required(Json.object(odds), "className", "odds")));
            }
            families.put(Json.string(Json.required(family, "className", "family")), members);
        }
        Map<String, java.util.TreeSet<Integer>> depths = new java.util.TreeMap<>();
        for (String raw : Json.array(Json.required(rotation, "depths", "rotation"))) {
            Map<String, String> row = Json.object(raw);
            int depth = Json.integer(Json.required(row, "depth", "rotation depth"));
            if (bosses.contains(depth)) {
                continue;
            }
            for (String spawn : Json.array(Json.required(row, "entries", "rotation depth"))) {
                Map<String, String> one = Json.object(spawn);
                String className = Json.string(Json.required(one, "className", "rotation entry"));
                List<String> placed = Json.bool(Json.required(one, "family", "rotation entry"))
                        ? families.getOrDefault(className, List.of()) : List.of(className);
                for (String member : placed) {
                    depths.computeIfAbsent(member, k -> new java.util.TreeSet<>()).add(depth);
                }
            }
        }
        for (String raw : Json.array(Json.required(rotation, "rareMobs", "rotation"))) {
            Map<String, String> rare = Json.object(raw);
            int depth = Json.integer(Json.required(rare, "depth", "rare mob"));
            if (!bosses.contains(depth)) {
                depths.computeIfAbsent(Json.string(Json.required(rare, "className", "rare mob")), k -> new java.util.TreeSet<>())
                        .add(depth);
            }
        }
        for (String raw : Json.array(Json.required(rotation, "alternates", "rotation"))) {
            Map<String, String> alternate = Json.object(raw);
            String base = Json.string(Json.required(alternate, "className", "alternate"));
            java.util.TreeSet<Integer> where = new java.util.TreeSet<>();
            for (String member : families.getOrDefault(base, List.of(base))) {
                where.addAll(depths.getOrDefault(member, new java.util.TreeSet<>()));
            }
            if (!where.isEmpty()) {
                depths.computeIfAbsent(Json.string(Json.required(alternate, "alternate", "alternate")),
                        k -> new java.util.TreeSet<>()).addAll(where);
            }
        }
        return depths;
    }

    private static List<String> strings(String raw) {
        List<String> out = new ArrayList<>();
        for (String element : Json.array(raw)) {
            out.add(Json.string(element));
        }
        return out;
    }

    /**
     * The enemies' combat figures (story 4.7), by the display name the strings table gives each
     * class (the name the screen shows). Only an enemy whose accuracy is a constant and whose damage
     * and damage reduction are rolled ranges the Codex could read; one whose figures depend on the
     * Run (a bee, a wraith) or are computed otherwise is left out, and the Brain says so when it
     * meets one. A figure computed at run time, or an evasion that is the enemy's own rule
     * (customDefense), is marked unknown, and the rest stand. When two classes share a display name,
     * the first is kept.
     */
    private static List<Codex.Threat> threats(Path folder) {
        Map<String, String> shown = shown(folder);
        List<Codex.Threat> threats = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String raw : Json.array(table(folder, "mobs.json"))) {
            Map<String, String> mob = Json.object(raw);
            String name = shown.get(Json.string(Json.required(mob, "className", "mob")));
            if (!Json.string(Json.required(mob, "alignment", "mob")).equals("ENEMY") || name == null
                    || Json.bool(Json.required(mob, "statsSetLater", "mob"))
                    || Json.integer(Json.required(mob, "ht", "mob")) <= 0 || !seen.add(name)) {
                continue;
            }
            Map<String, String> attack = Json.object(Json.required(mob, "attack", "mob"));
            Map<String, String> damage = Json.object(Json.required(mob, "damage", "mob"));
            Map<String, String> dr = Json.object(Json.required(mob, "dr", "mob"));
            List<String> unknown = new ArrayList<>();
            // A figure the Codex could not read is named, and the Brain supplies its own guess for it;
            // the figures it could read, the hit points first, stand (Goo: HT 100, Goo.java:54).
            boolean attackKnown = Json.string(Json.required(attack, "kind", "attack")).equals("CONSTANT");
            boolean damageKnown = !Json.string(Json.required(damage, "kind", "damage")).equals("OTHER");
            boolean drKnown = !Json.string(Json.required(dr, "kind", "dr")).equals("OTHER");
            if (!attackKnown) {
                unknown.add("attack");
            }
            if (!damageKnown) {
                unknown.add("damage");
            }
            if (Json.bool(Json.required(mob, "customDefense", "mob"))) {
                unknown.add("defense");
            }
            if (!drKnown) {
                unknown.add("dr");
            }
            threats.add(new Codex.Threat(name, Json.integer(Json.required(mob, "ht", "mob")),
                    attackKnown ? Json.integer(Json.required(attack, "min", "attack")) : 0,
                    Json.integer(Json.required(mob, "defenseSkill", "mob")),
                    damageKnown ? Json.integer(Json.required(damage, "min", "damage")) : 0,
                    damageKnown ? Json.integer(Json.required(damage, "max", "damage")) : 0,
                    drKnown ? Json.integer(Json.required(dr, "min", "dr")) : 0,
                    drKnown ? Json.integer(Json.required(dr, "max", "dr")) : 0, unknown));
        }
        return threats;
    }

    /** The enemies the Codex marks IMMOVABLE, by display name: they fight only what stands beside them. */
    private static List<String> immovable(Path folder) {
        Map<String, String> shown = shown(folder);
        List<String> names = new ArrayList<>();
        for (String raw : Json.array(table(folder, "mobs.json"))) {
            Map<String, String> mob = Json.object(raw);
            String name = shown.get(Json.string(Json.required(mob, "className", "mob")));
            if (name != null && Json.string(Json.required(mob, "alignment", "mob")).equals("ENEMY")
                    && Json.array(Json.required(mob, "properties", "mob")).stream().map(Json::string)
                    .anyMatch("IMMOVABLE"::equals) && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Every class's display name, from the strings table. */
    private static Map<String, String> shown(Path folder) {
        Map<String, String> shown = new HashMap<>();
        for (String raw : Json.array(table(folder, "strings.json"))) {
            Map<String, String> string = Json.object(raw);
            if (Json.string(Json.required(string, "suffix", "string")).equals("name")) {
                shown.put(Json.string(Json.required(string, "className", "string")),
                        Json.string(Json.required(string, "value", "string")));
            }
        }
        return shown;
    }

    /**
     * The weapons' damage or the armours' damage reduction the combat table measured, by display name
     * and level, with each item's tier and level-0 strength from the item table (story 4.8), 0 and 0
     * where the item table states no strength.
     */
    private static List<Codex.Gear> gear(Path folder, Map<String, String> names, String list) {
        Map<String, int[]> strengths = new HashMap<>();
        for (String raw : Json.array(table(folder, "items.json"))) {
            Map<String, String> item = Json.object(raw);
            Map<String, String> strength = Json.object(Json.required(item, "strength", "item"));
            if (Json.bool(Json.required(strength, "present", "strength"))) {
                strengths.put(Json.string(Json.required(item, "className", "item")),
                        new int[] {Json.integer(Json.required(strength, "tier", "strength")),
                                Json.integer(Json.required(strength, "atLevel0", "strength"))});
            }
        }
        List<Codex.Gear> gear = new ArrayList<>();
        for (String raw : Json.array(Json.required(Json.object(table(folder, "combat.json")), list, "combat"))) {
            Map<String, String> entry = Json.object(raw);
            String className = Json.string(Json.required(entry, "className", list));
            String name = names.get(className);
            if (name == null) {
                continue;
            }
            Map<String, String> spread = Json.object(Json.required(entry, "spread", list));
            int[] strength = strengths.getOrDefault(className, new int[] {0, 0});
            gear.add(new Codex.Gear(name, Json.integer(Json.required(entry, "level", list)),
                    Json.integer(Json.required(spread, "min", "spread")), Json.integer(Json.required(spread, "max", "spread")),
                    Json.integer(Json.required(spread, "meanPerMille", "spread")), strength[0], strength[1]));
        }
        return gear;
    }

    private static String named(Map<String, String> names, String className) {
        String name = names.get(className);
        if (name == null) {
            throw new IllegalArgumentException("the Codex names no item " + className);
        }
        return name;
    }

    /**
     * The table without the whitespace between its tokens. The Codex writes its tables indented,
     * one entry per line, for a reader of the diff; the log reader this borrows accepts only the
     * canonical form, whose one difference is that whitespace. A string's own spaces are kept.
     */
    static String compact(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == (char) 92) {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String table(Path folder, String file) {
        try {
            return compact(Files.readString(folder.resolve(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("the Codex table " + file + " in " + folder + " could not be read", e);
        }
    }
}
