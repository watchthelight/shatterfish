package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.Tile;

import java.util.Collections;
import java.util.List;

/**
 * Observations built from {@code api} records alone, which is all a Brain's test can reach: a
 * three-cell floor, a stand-in hero, and whatever Actions and Prompt a case needs.
 */
final class Screens {

    static final Codex.Manifest MANIFEST = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json"));

    /**
     * A small Codex: three potion appearances over four identities (strength weighted zero, as the
     * decks weight it), two scroll appearances over two, the pool room's potion and the two
     * guaranteed drops, as the real tables give them.
     */
    static final Codex.Knowledge CODEX = new Codex.Knowledge(MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion", "amber potion", "golden potion"),
                            List.of(new Codex.Candidate("items.potions.PotionOfStrength", "potion of strength", 0),
                                    new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6),
                                    new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 4),
                                    new Codex.Candidate("items.potions.PotionOfFrost", "potion of frost", 3))),
                    new Codex.Identities(ItemKind.SCROLL, List.of("scroll \"KAUNAN\"", "scroll \"SOWILO\""),
                            List.of(new Codex.Candidate("items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 0),
                                    new Codex.Candidate("items.scrolls.ScrollOfIdentify", "scroll of identify", 6)))),
            List.of(new Codex.RoomSpawn("levels.rooms.special.PoolRoom", "items.potions.PotionOfInvisibility",
                    "potion of invisibility")),
            List.of(new Codex.Guarantee("STRENGTH_POTIONS", "items.potions.PotionOfStrength", "potion of strength", 2, 5),
                    new Codex.Guarantee("UPGRADE_SCROLLS", "items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 3, 5)));

    private Screens() {
    }

    /** A screen at {@code depth} offering {@code actions}, with no Prompt open. */
    static Observation offering(int depth, Action... actions) {
        return screen(depth, PromptKind.NONE, PromptSection.NONE, actions);
    }

    /** A screen with the chasm Prompt open, its buttons "Yes" and "No", offering {@code actions}. */
    static Observation prompting(Action... actions) {
        return asking(List.of("Yes", "No"), actions);
    }

    /** A screen with the chasm Prompt open, its buttons labelled {@code labels}, offering {@code actions}. */
    static Observation asking(List<String> labels, Action... actions) {
        return screen(1, PromptKind.CHASM_JUMP, new PromptSection(PromptKind.CHASM_JUMP, "Chasm",
                "Do you really want to jump into the chasm?", labels), actions);
    }

    private static Observation screen(int depth, PromptKind kind, PromptSection prompt, Action... actions) {
        return world(depth, kind, prompt, 3, List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY), List.of(), List.of(),
                List.of(), List.of(), actions);
    }

    /** An unidentified or identified item in the backpack. */
    static ItemView item(ItemKind kind, String name, int quantity) {
        return new ItemView(kind, name, quantity, false, 0, false, false, "", org.shatterfish.api.EquipSlot.NONE,
                List.of(), "");
    }

    /**
     * A one-row floor of {@code tiles}, all in view, the hero on cell 1, with the heaps, enemies,
     * backpack and identified names given, and nothing offered.
     */
    static Observation world(int depth, List<Tile> tiles, List<HeapView> heaps, List<ActorView> actors,
                             List<ItemView> items, List<KnownAppearance> known) {
        return world(depth, PromptKind.NONE, PromptSection.NONE, tiles.size(), tiles, heaps, actors, items, known);
    }

    /** As {@link #world}, on a floor {@code width} cells wide, with nobody on it and nothing held. */
    static Observation grid(int depth, int width, List<Tile> tiles, List<HeapView> heaps) {
        return world(depth, PromptKind.NONE, PromptSection.NONE, width, tiles, heaps, List.of(), List.of(), List.of());
    }

    /** An enemy in view on {@code cell}. */
    static ActorView enemy(String name, int cell) {
        return new ActorView(cell, name, org.shatterfish.api.Alignment.ENEMY, 1, false, org.shatterfish.api.Emote.NONE,
                List.of());
    }

    private static Observation world(int depth, PromptKind kind, PromptSection prompt, int width, List<Tile> tiles,
                                     List<HeapView> heaps, List<ActorView> actors, List<ItemView> items,
                                     List<KnownAppearance> known, Action... actions) {
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, kind);
        MapSection map = new MapSection(width, tiles.size() / width, tiles, Collections.nCopies(tiles.size(), Fog.VISIBLE),
                List.of(), heaps, List.of(), Feeling.NONE, List.of());
        HeroSection hero = new HeroSection(1, "", HeroSubclass.NONE, "", 1, 0, 1, 1, 1, 0, 1, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        return new Observation(header, map, new ActorsSection(actors), hero, new InventorySection(items),
                new JournalSection(List.of(), known), new LogSection(List.of()),
                new ActionsSection(List.of(actions)), prompt);
    }
}
