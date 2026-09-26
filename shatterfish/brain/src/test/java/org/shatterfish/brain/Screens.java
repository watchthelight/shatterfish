package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Emote;
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
import org.shatterfish.api.Weights;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Observations built from {@code api} records alone, which is all a Brain's test can reach: a
 * three-cell floor, a stand-in hero, and whatever Actions and Prompt a case needs.
 */
final class Screens {

    static final Codex.Manifest MANIFEST = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json"));

    /**
     * A bestiary entry with the tags the Brain reads (speed, attack, flying, ai, outrunnable) and
     * neutral values for the rest, created as an enemy, placed by the rotation on {@code depths}.
     */
    static Codex.Tactics tactics(String className, String name, String speed, String attack, boolean flying, String ai,
                                 boolean outrunnable, Integer... depths) {
        return new Codex.Tactics(className, name, Alignment.ENEMY, List.of(depths), speed, attack,
                attack.equals("melee") ? 1 : 8, flying, false, List.of(), List.of(), ai, false, false, false, "close",
                false, outrunnable, List.of("stairs"));
    }

    /**
     * The bestiary entries for the enemies the Brain's hand lists named before the bestiary (lever 0),
     * tagged as tactics/bestiary.json tags them: the passive statues and exile, the enemies that shoot
     * or fly, and a lasher that never walks. Nothing that walks at the hero's pace or faster is here, so
     * the speed-aware retreat (lever 1) leaves every screen that does not add one alone: every entry
     * here is outrunnable, the bat included, whatever the real file says of its speed.
     */
    static final List<Codex.Tactics> BESTIARY = List.of(
            tactics("actors.mobs.Statue", "animated statue", "normal", "melee", false, "passive", true),
            tactics("actors.mobs.ArmoredStatue", "armored statue", "normal", "melee", false, "passive", true),
            tactics("actors.mobs.GnollExile", "gnoll exile", "normal", "melee", false, "passive", true, 2, 3, 4),
            tactics("actors.mobs.Eye", "evil eye", "normal", "bolt", true, "sleeping", true, 21, 22, 23, 24),
            tactics("actors.mobs.Shaman.RedShaman", "gnoll shaman", "normal", "bolt", false, "sleeping", true, 11, 12, 13, 14),
            tactics("actors.mobs.Warlock", "dwarf warlock", "normal", "bolt", false, "sleeping", true, 16, 17, 18, 19),
            tactics("actors.mobs.DM100", "DM-100", "normal", "bolt", false, "sleeping", true, 7, 8, 9),
            tactics("actors.mobs.Scorpio", "scorpio", "normal", "ranged", false, "sleeping", true, 23, 24),
            tactics("actors.mobs.Acidic", "acidic scorpio", "normal", "ranged", false, "sleeping", true, 23, 24),
            tactics("actors.mobs.Bat", "vampire bat", "normal", "melee", true, "sleeping", true, 9, 11, 12, 13, 14),
            tactics("actors.mobs.RotLasher", "lasher", "immobile", "melee", false, "hunting", true));

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
                    new Codex.Identities(ItemKind.SCROLL, List.of("scroll of KAUNAN", "scroll of SOWILO"),
                            List.of(new Codex.Candidate("items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 0),
                                    new Codex.Candidate("items.scrolls.ScrollOfIdentify", "scroll of identify", 6)))),
            List.of(new Codex.RoomSpawn("levels.rooms.special.PoolRoom", "items.potions.PotionOfInvisibility",
                    "potion of invisibility")),
            List.of(new Codex.Guarantee("STRENGTH_POTIONS", "items.potions.PotionOfStrength", "potion of strength", 2, 5),
                    new Codex.Guarantee("UPGRADE_SCROLLS", "items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 3, 5)),
            List.of(),
            // Story 4.8: the level-0 rolls the committed combat table measured, with the item
            // table's tier and strength.
            List.of(new Codex.Gear("worn shortsword", 0, 1, 10, 5485, 1, 10),
                    new Codex.Gear("shortsword", 0, 2, 15, 8485, 2, 12),
                    new Codex.Gear("greataxe", 0, 5, 45, 25035, 5, 18),
                    new Codex.Gear("mage's staff", 0, 1, 6, 3501, 1, 10)),
            List.of(new Codex.Gear("cloth armor", 0, 0, 2, 1004, 1, 10),
                    new Codex.Gear("leather armor", 0, 0, 4, 2013, 2, 12)),
            List.of()).withBestiary(BESTIARY);

    /** The committed weight set's values (weights/shatterfish.json), which WeightsFileTest holds to the file. */
    static final Weights WEIGHTS = weights(Map.of());

    /** The committed weights with {@code changed} weights replaced. */
    static Weights weights(Map<String, Long> changed) {
        Map<String, Long> terms = new java.util.TreeMap<>(Map.of(
                "act_attack", 0L, "act_descend", 0L, "act_rest_hurt", 0L, "act_search", 0L, "act_wait", 0L,
                "depth", 10000L, "enemies", -3000L, "hp", 10L, "hunger", -5000L, "level", 5000L));
        terms.put("strength", 2000L);
        terms.putAll(Map.of("item", 2000L, "gold", 10L, "turn", -150L, "weapon", 2L, "armor", 4L, "cursed", -10000L));
        terms.putAll(changed);
        return new Weights("shatterfish", 2,
                terms.entrySet().stream().map(term -> new Weights.Term(term.getKey(), term.getValue())).toList());
    }

    private Screens() {
    }

    /**
     * The Belief {@code brain} holds at the last of {@code screens}: every screen before it seen,
     * decided on and handed over, as the Brain's driver does it (story 4.7), and the last seen.
     */
    static org.shatterfish.api.Belief drive(Brain brain, Observation... screens) {
        org.shatterfish.api.Belief belief = null;
        for (int i = 0; i < screens.length; i++) {
            belief = brain.update(screens[i], belief);
            if (i < screens.length - 1) {
                belief = brain.handed(screens[i], belief, brain.decide(screens[i], belief));
            }
        }
        return belief;
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
    static Observation titled(String title, String text, List<String> labels, Action... actions) {
        return screen(1, PromptKind.OTHER, new PromptSection(PromptKind.OTHER, title, text, labels), actions);
    }

    /** A screen with a Prompt of {@code kind} open, titled and worded so, its buttons {@code labels} (story 4.10). */
    static Observation prompted(PromptKind kind, String title, String text, List<String> labels, Action... actions) {
        return screen(1, kind, new PromptSection(kind, title, text, labels), actions);
    }

    /** A screen with the chasm Prompt open, its buttons labelled {@code labels}, offering {@code actions}. */
    static Observation asking(List<String> labels, Action... actions) {
        return screen(1, PromptKind.CHASM_JUMP, new PromptSection(PromptKind.CHASM_JUMP, "Chasm",
                "Do you really want to jump into the chasm?", labels), actions);
    }

    /**
     * A screen with a Prompt of {@code kind} open, titled {@code title}, drawing {@code labels}, for a
     * hero of {@code heroClass} holding {@code items}, offering what {@link org.shatterfish.api.ValidActions}
     * offers under it (story 4.11).
     */
    static Observation asked(HeroClass heroClass, PromptKind kind, String title, List<String> labels,
                             List<ItemView> items) {
        return asked(heroClass, kind, title, "", labels, items);
    }

    /** As {@link #asked(HeroClass, PromptKind, String, List, List)}, with the Prompt's text. */
    static Observation asked(HeroClass heroClass, PromptKind kind, String title, String text, List<String> labels,
                             List<ItemView> items) {
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", heroClass,
                List.of(), 1, 0, false, false, kind);
        List<Tile> tiles = List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY);
        MapSection map = new MapSection(3, 1, tiles, Collections.nCopies(3, Fog.VISIBLE), List.of(), List.of(),
                List.of(), Feeling.NONE, List.of());
        Observation bare = new Observation(header, map, new ActorsSection(List.of()), hero(),
                new InventorySection(items), new JournalSection(List.of(), List.of()), new LogSection(List.of()),
                ActionsSection.NONE, new PromptSection(kind, title, text, labels));
        return bare.withActions(org.shatterfish.api.ValidActions.of(bare));
    }

    /** A hero at full health, level 1, strength 1, fed. */
    static HeroSection hero() {
        return hero(1, 1, 1, 1, Hunger.NONE);
    }

    /** A stand-in hero with these numbers. */
    static HeroSection hero(int hp, int ht, int level, int strength, Hunger hunger) {
        return new HeroSection(1, "", HeroSubclass.NONE, "", level, 0, 1, hp, ht, 0, strength, 0, 0, 0,
                hunger, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
    }

    /** A screen at {@code depth} with this hero and these actors in view, offering {@code actions} (story 4.5). */
    static Observation showing(int depth, HeroSection hero, List<ActorView> actors, Action... actions) {
        return world(depth, PromptKind.NONE, PromptSection.NONE, hero, 3, List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY),
                List.of(), actors, List.of(), List.of(), actions);
    }

    /**
     * A starving hero at {@code hp} of {@code ht}, with an enemy rat in view on the cell beside it,
     * offering {@code actions}: every Safety flag's inputs at once.
     */
    static Observation hurt(int hp, int ht, Action... actions) {
        return screen(1, PromptKind.NONE, PromptSection.NONE, hp, ht, Hunger.STARVING,
                List.of(new ActorView(2, "rat", Alignment.ENEMY, 5, false, Emote.NONE, List.of())), actions);
    }

    private static Observation screen(int depth, PromptKind kind, PromptSection prompt, Action... actions) {
        return screen(depth, kind, prompt, 1, 1, Hunger.NONE, List.of(), actions);
    }

    private static Observation screen(int depth, PromptKind kind, PromptSection prompt, int hp, int ht,
                                      Hunger hunger, List<ActorView> actors, Action... actions) {
        return world(depth, kind, prompt, hp, ht, hunger, 3, List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY), List.of(),
                actors, List.of(), List.of(), actions);
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
        return world(depth, PromptKind.NONE, PromptSection.NONE, 1, 1, Hunger.NONE, tiles.size(), tiles, heaps, actors,
                items, known);
    }

    /**
     * A one-row floor of {@code tiles}, all in view, with this hero (on its own cell), these heaps
     * and this backpack, offering {@code actions} (story 4.8).
     */
    static Observation floor(int depth, HeroSection hero, List<Tile> tiles, List<HeapView> heaps, List<ItemView> items,
                             Action... actions) {
        return world(depth, PromptKind.NONE, PromptSection.NONE, hero, tiles.size(), tiles, heaps, List.of(), items,
                List.of(), actions);
    }

    /** A hero standing on {@code cell}, at full health (20), of this strength. */
    static HeroSection heroAt(int cell, int strength) {
        return heroAt(cell, strength, 20, 20);
    }

    /** A hero standing on {@code cell}, of this strength, with {@code hp} of {@code ht}. */
    static HeroSection heroAt(int cell, int strength, int hp, int ht) {
        return new HeroSection(cell, "", HeroSubclass.NONE, "", 1, 0, 1, hp, ht, 0, strength, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
    }

    /** A weapon or armour in the pack or worn, its curse shown or not. */
    static ItemView gear(ItemKind kind, String name, org.shatterfish.api.EquipSlot slot, boolean cursedKnown,
                         boolean cursed) {
        return new ItemView(kind, name, 1, false, 0, cursedKnown, cursed, "", slot,
                List.of(slot == org.shatterfish.api.EquipSlot.NONE ? "EQUIP" : "UNEQUIP"), "");
    }

    /** As {@link #world}, on a floor {@code width} cells wide, with nobody on it and nothing held. */
    static Observation grid(int depth, int width, List<Tile> tiles, List<HeapView> heaps) {
        return world(depth, PromptKind.NONE, PromptSection.NONE, 1, 1, Hunger.NONE, width, tiles, heaps, List.of(),
                List.of(), List.of());
    }

    /** An enemy in view on {@code cell}. */
    static ActorView enemy(String name, int cell) {
        return new ActorView(cell, name, org.shatterfish.api.Alignment.ENEMY, 1, false, org.shatterfish.api.Emote.NONE,
                List.of());
    }

    /** An item in the backpack offering {@code actions}, of a kind no Policy acts on (story 4.10). */
    static ItemView using(String name, String... actions) {
        return new ItemView(ItemKind.ARTIFACT, name, 1, false, 0, false, false, "", org.shatterfish.api.EquipSlot.NONE,
                List.of(actions), "");
    }

    /** An unidentified potion or scroll in the backpack, offering what the game offers for it (story 4.10). */
    static ItemView unknown(ItemKind kind, String name, int quantity) {
        List<String> actions = kind == ItemKind.POTION ? List.of("DRINK", "DROP", "THROW") : List.of("DROP", "READ", "THROW");
        return new ItemView(kind, name, quantity, false, 0, false, false, "", org.shatterfish.api.EquipSlot.NONE, actions,
                "");
    }

    /** A three-cell floor, the hero on cell 1, holding {@code items}, offering {@code actions} (story 4.10). */
    static Observation holding(List<ItemView> items, Action... actions) {
        return world(1, PromptKind.NONE, PromptSection.NONE, hero(1, 1, 1, 1, Hunger.NONE), 3,
                List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY), List.of(), List.of(), items, List.of(), actions);
    }

    /** A hero on {@code cell} with {@code hp} of {@code ht} and these buffs shown (story 4.10). */
    static HeroSection heroAt(int cell, int hp, int ht, List<org.shatterfish.api.BuffView> buffs) {
        return new HeroSection(cell, "", HeroSubclass.NONE, "", 1, 0, 1, hp, ht, 0, 10, 0, 0, 0,
                Hunger.NONE, buffs, List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
    }

    /**
     * A floor for the test-item Policy (story 4.10): {@code width} wide, every tile in view, these
     * blobs drawn, this hero and pack, these actors in view and these names identified, offering
     * {@code actions}.
     */
    static Observation lab(int depth, int width, List<Tile> tiles, List<org.shatterfish.api.BlobCell> blobs,
                           HeroSection hero, List<ItemView> items, List<ActorView> actors, List<KnownAppearance> known,
                           Action... actions) {
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, PromptKind.NONE);
        MapSection map = new MapSection(width, tiles.size() / width, tiles,
                Collections.nCopies(tiles.size(), Fog.VISIBLE), List.of(), List.of(), blobs, Feeling.NONE, List.of());
        return new Observation(header, map, new ActorsSection(actors), hero, new InventorySection(items),
                new JournalSection(List.of(), known), new LogSection(List.of()),
                new ActionsSection(List.of(actions)), PromptSection.NONE);
    }

    private static Observation world(int depth, PromptKind kind, PromptSection prompt, int hp, int ht, Hunger hunger,
                                     int width, List<Tile> tiles, List<HeapView> heaps, List<ActorView> actors,
                                     List<ItemView> items, List<KnownAppearance> known, Action... actions) {
        return world(depth, kind, prompt, hero(hp, ht, 1, 1, hunger), width, tiles, heaps, actors, items, known,
                actions);
    }

    private static Observation world(int depth, PromptKind kind, PromptSection prompt, HeroSection hero,
                                     int width, List<Tile> tiles, List<HeapView> heaps, List<ActorView> actors,
                                     List<ItemView> items, List<KnownAppearance> known, Action... actions) {
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, kind);
        MapSection map = new MapSection(width, tiles.size() / width, tiles, Collections.nCopies(tiles.size(), Fog.VISIBLE),
                List.of(), heaps, List.of(), Feeling.NONE, List.of());
        return new Observation(header, map, new ActorsSection(actors), hero, new InventorySection(items),
                new JournalSection(List.of(), known), new LogSection(List.of()),
                new ActionsSection(List.of(actions)), prompt);
    }
}
