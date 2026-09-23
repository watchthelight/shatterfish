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
 * table is read.
 */
public final class CodexKnowledge {

    /** The families a Brain reasons about the identity of: the ones whose appearances are shuffled per Run. */
    private static final List<ItemKind> FAMILIES = List.of(ItemKind.POTION, ItemKind.SCROLL, ItemKind.RING);

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
        return new Codex.Knowledge(manifest, families, spawns, drops);
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
