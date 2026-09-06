package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The records refuse what the screen would not draw, so the leaks ADR-0006 names have no way into
 * an Observation: a container's contents, a heap or trap on a cell never seen, a character out of
 * view, a tile on an unknown cell, a price on what is not for sale, an unknown item's level; and
 * the Observation refuses what its sections would contradict: a Prompt the header does not name,
 * an Action naming what the Observation does not carry (ADR-0014).
 */
class SchemaRulesTest {

    private static MapSection map(List<Tile> tiles, List<Fog> fog, List<TrapView> traps, List<HeapView> heaps,
                                  List<BlobCell> blobs, List<TransitionView> transitions) {
        return new MapSection(Corpus.WIDTH, Corpus.HEIGHT, tiles, fog, traps, heaps, blobs, Feeling.NONE, transitions);
    }

    private static HeroSection heroAt(int cell, String ability) {
        HeroSection h = Corpus.hero();
        return new HeroSection(cell, h.name(), h.subclass(), ability, h.level(), h.exp(), h.expToLevel(), h.hp(), h.ht(),
                h.shield(), h.strength(), h.strengthBonus(), h.gold(), h.energy(), h.hunger(), h.buffs(), h.talents(),
                h.talentPointsAvailable(), h.quickslots());
    }

    private static Observation withActions(Action... actions) {
        return Corpus.with(Corpus.observation(), new ActionsSection(List.of(actions)));
    }

    @Test
    @DisplayName("an unknown cell shows nothing, and a known cell shows something")
    void unknown_cells_carry_no_tile() {
        List<Tile> tiles = new ArrayList<>(Corpus.tiles());
        tiles.set(21, Tile.WALL);
        assertThrows(IllegalArgumentException.class,
                () -> map(tiles, Corpus.fog(), List.of(), List.of(), List.of(), List.of()));
        List<Tile> blank = new ArrayList<>(Corpus.tiles());
        blank.set(0, Tile.NONE);
        assertThrows(IllegalArgumentException.class,
                () -> map(blank, Corpus.fog(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("one tile and one fog level per cell")
    void one_entry_per_cell() {
        List<Tile> tiles = new ArrayList<>(Corpus.tiles());
        tiles.remove(0);
        assertThrows(IllegalArgumentException.class,
                () -> map(tiles, Corpus.fog(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("nothing stands on a cell the player has never seen")
    void nothing_on_an_unknown_cell() {
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(),
                List.of(new TrapView(22, "Gripping trap", true)), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(),
                List.of(new HeapView(22, HeapKind.CHEST, false, "", 0, "")), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(new BlobCell(22, List.of("Fire"))), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(), List.of(new TransitionView(22, TransitionKind.REGULAR_EXIT))));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(),
                List.of(new TrapView(24, "Gripping trap", true)), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("a blob stands only in view: the emitter draws it nowhere else")
    void a_blob_stands_in_view() {
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(new BlobCell(12, List.of("ToxicGas"))), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(new BlobCell(19, List.of("ToxicGas"))), List.of()));
        map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(), List.of(new BlobCell(1, List.of("ToxicGas"))), List.of());
    }

    @Test
    @DisplayName("a cell is named at most once per list")
    void one_entry_per_cell_per_list() {
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(),
                List.of(new TrapView(2, "Toxic gas trap", true), new TrapView(2, "Alarm trap", true)),
                List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ActorsSection(List.of(
                new ActorView(4, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of()),
                new ActorView(4, "Crab", Alignment.ENEMY, 11, false, Emote.NONE, List.of()))));
        assertThrows(IllegalArgumentException.class, () -> new HeaderSection(1, "v3.3.8", "", HeroClass.MAGE,
                List.of(Challenge.NO_FOOD, Challenge.NO_FOOD), 1, 0, false, false, PromptKind.NONE));
        assertThrows(IllegalArgumentException.class, () -> new BlobCell(5, List.of("Fire", "Fire")));
    }

    @Test
    @DisplayName("a container shows nothing but itself: no item, no price, no category")
    void a_container_shows_only_itself() {
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.CHEST, false, "Potion of Strength", 0, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.LOCKED_CHEST, false, "", 0, "artifact"));
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.HEAP, false, "Dart", 10, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.FOR_SALE, false, "Dart", -1, ""));
        new HeapView(3, HeapKind.CRYSTAL_CHEST, false, "", 0, "ring");
        new HeapView(3, HeapKind.FOR_SALE, false, "Dart", 0, "");
    }

    @Test
    @DisplayName("a character is drawn only in view, and never on the hero's cell")
    void an_actor_stands_in_view() {
        for (int cell : List.of(13, 21, 24)) {
            assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.observation(),
                    new ActorsSection(List.of(new ActorView(cell, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of())))));
        }
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.observation(),
                new ActorsSection(List.of(new ActorView(Corpus.hero().cell(), "Rat", Alignment.ENEMY, 11, false,
                        Emote.NONE, List.of())))));
    }

    @Test
    @DisplayName("the hero stands in view, on the map")
    void the_hero_stands_in_view() {
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.observation(), heroAt(13, "Heroic Leap")));
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.observation(), heroAt(24, "Heroic Leap")));
        Corpus.with(Corpus.observation(), heroAt(1, "Heroic Leap"));
    }

    @Test
    @DisplayName("health is pips of the bar, a buff shows turns only when timed")
    void bounded_numbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActorView(4, "Rat", Alignment.ENEMY, 12, false, Emote.NONE, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ActorView(4, "Rat", Alignment.ENEMY, -1, false, Emote.NONE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BuffView("Poisoned", false, 100));
        assertThrows(IllegalArgumentException.class, () -> new BuffView("Poisoned", true, -1));
        assertThrows(IllegalArgumentException.class, () -> new BuffView("", true, 100));
    }

    @Test
    @DisplayName("the header and the prompt section name one Prompt")
    void the_header_and_the_prompt_agree() {
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.observation(), Corpus.chasmPrompt()));
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.promptObservation(), PromptSection.NONE));
        assertThrows(IllegalArgumentException.class, () -> new PromptSection(PromptKind.NONE, "Chasm", "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PromptSection(PromptKind.NONE, "", "", List.of("Yes")));
    }

    @Test
    @DisplayName("an Action names only what the Observation carries: a cell on the map, an item as listed, an option offered")
    void an_action_names_what_the_observation_carries() {
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.Step(24)));
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.UseItemAt(Corpus.wand(), "ZAP", 24)));
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.AbilityAt("Heroic Leap", 24)));
        assertThrows(IllegalArgumentException.class,
                () -> withActions(new Action.UseItem(new ItemRef(99, "Turquoise potion", 2), "DRINK")));
        assertThrows(IllegalArgumentException.class,
                () -> withActions(new Action.UseItem(new ItemRef(4, "Potion of healing", 2), "DRINK")));
        assertThrows(IllegalArgumentException.class,
                () -> withActions(new Action.UseItem(new ItemRef(4, "Turquoise potion", 1), "DRINK")));
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.UseItem(Corpus.potion(), "ZAP")));
        assertThrows(IllegalArgumentException.class,
                () -> withActions(new Action.UseItemOn(Corpus.scroll(), "READ", new ItemRef(0, "Shortsword", 1))));
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.AnswerPrompt(0)));
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(Corpus.promptObservation(),
                new ActionsSection(List.of(new Action.AnswerPrompt(2)))));
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.Talent("Nope")));
        assertThrows(IllegalArgumentException.class, () -> withActions(new Action.Ability("Nope")));
        assertThrows(IllegalArgumentException.class, () -> Corpus.with(withActions(new Action.Ability("Heroic Leap")),
                heroAt(0, "")));
        withActions(new Action.Step(23), new Action.UseItem(Corpus.potion(), "DRINK"), new Action.Talent("Iron Will"));
    }

    @Test
    @DisplayName("the valid Actions come out in one order, each once, and a human's move is never among them")
    void actions_are_canonical() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActionsSection(List.of(new Action.Wait(), new Action.Wait())));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionsSection(List.of(new Action.Step(1), new Action.Search(), new Action.Step(1))));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionsSection(List.of(new Action.Wait(), new Action.MoveTo(17))));
        ActionsSection section = new ActionsSection(List.of(new Action.Step(2), new Action.Wait(), new Action.Step(1)));
        assertEquals(List.of(new Action.Step(1), new Action.Step(2), new Action.Wait()), section.actions());
    }

    @Test
    @DisplayName("the inventory is the belongings' order: equipped items first, in slot order, each slot once")
    void the_inventory_is_in_belongings_order() {
        ItemView weapon = Corpus.items().get(0);
        ItemView armor = Corpus.items().get(1);
        ItemView food = Corpus.items().get(5);
        assertThrows(IllegalArgumentException.class, () -> new InventorySection(List.of(food, weapon)));
        assertThrows(IllegalArgumentException.class, () -> new InventorySection(List.of(armor, weapon)));
        assertThrows(IllegalArgumentException.class, () -> new InventorySection(List.of(weapon, weapon)));
        new InventorySection(List.of(weapon, armor, food, food));
        new InventorySection(List.of());
    }

    @Test
    @DisplayName("an unidentified item shows neither its level nor its curse")
    void an_unidentified_item_shows_no_level_or_curse() {
        assertThrows(IllegalArgumentException.class, () -> new ItemView(ItemKind.WEAPON, "Longsword", 1, false, 1, true,
                false, "", EquipSlot.NONE, List.of("DROP"), ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemView(ItemKind.WEAPON, "Longsword", 1, true, 1, false,
                true, "", EquipSlot.NONE, List.of("DROP"), ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemView(ItemKind.WEAPON, "", 1, true, 0, true,
                false, "", EquipSlot.NONE, List.of("DROP"), ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemView(ItemKind.WEAPON, "Longsword", 0, true, 0, true,
                false, "", EquipSlot.NONE, List.of("DROP"), ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemView(ItemKind.WEAPON, "Longsword", 1, true, 0, true,
                false, "", EquipSlot.NONE, List.of("DROP", "DROP"), ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemRef(0, "Longsword", 0));
    }

    @Test
    @DisplayName("the hero's numbers are bounded as the HUD draws them, six quickslots, four tiers")
    void the_hero_is_bounded() {
        HeroSection h = Corpus.hero();
        assertThrows(IllegalArgumentException.class, () -> new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(),
                h.level(), h.exp(), h.expToLevel(), h.ht() + 1, h.ht(), h.shield(), h.strength(), h.strengthBonus(),
                h.gold(), h.energy(), h.hunger(), h.buffs(), h.talents(), h.talentPointsAvailable(), h.quickslots()));
        assertThrows(IllegalArgumentException.class, () -> new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(),
                0, h.exp(), h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(),
                h.gold(), h.energy(), h.hunger(), h.buffs(), h.talents(), h.talentPointsAvailable(), h.quickslots()));
        assertThrows(IllegalArgumentException.class, () -> new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(),
                h.level(), h.exp(), h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(),
                h.gold(), h.energy(), h.hunger(), h.buffs(), h.talents(), List.of(0, 0, 0), h.quickslots()));
        assertThrows(IllegalArgumentException.class, () -> new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(),
                h.level(), h.exp(), h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(),
                h.gold(), h.energy(), h.hunger(), h.buffs(), h.talents(), h.talentPointsAvailable(),
                h.quickslots().subList(0, 5)));
        List<TalentView> twice = new ArrayList<>(h.talents());
        twice.add(new TalentView(1, "Iron Will", 1));
        assertThrows(IllegalArgumentException.class, () -> new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(),
                h.level(), h.exp(), h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(),
                h.gold(), h.energy(), h.hunger(), h.buffs(), twice, h.talentPointsAvailable(), h.quickslots()));
        assertThrows(IllegalArgumentException.class, () -> new TalentView(5, "Iron Will", 0));
        assertThrows(IllegalArgumentException.class, () -> new TalentView(1, "Iron Will", -1));
        assertThrows(IllegalArgumentException.class, () -> new QuickslotView("", true));
    }

    @Test
    @DisplayName("a note and a known appearance have their shapes, and the log its bound")
    void notes_appearances_and_the_log() {
        assertThrows(IllegalArgumentException.class, () -> new NoteView(NoteKind.LANDMARK, 2, "Shop", "text", 1));
        assertThrows(IllegalArgumentException.class, () -> new NoteView(NoteKind.LANDMARK, 2, "Shop", "", 2));
        assertThrows(IllegalArgumentException.class, () -> new NoteView(NoteKind.KEY, 2, "Iron key", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new KnownAppearance(ItemKind.WEAPON, "Longsword"));
        assertThrows(IllegalArgumentException.class, () -> new JournalSection(List.of(), List.of(
                new KnownAppearance(ItemKind.POTION, "Potion of healing"),
                new KnownAppearance(ItemKind.POTION, "Potion of healing"))));
        List<LogLine> lines = new ArrayList<>(Collections.nCopies(LogSection.MAX_LINES, new LogLine(LogTone.PLAIN, "x")));
        new LogSection(lines);
        lines.add(new LogLine(LogTone.PLAIN, "one too many"));
        assertThrows(IllegalArgumentException.class, () -> new LogSection(lines));
    }

    @Test
    @DisplayName("lists come out sorted, immutable and null-free")
    void lists_are_canonical() {
        ActorsSection actors = new ActorsSection(List.of(
                new ActorView(11, "Ghost", Alignment.NEUTRAL, 11, true, Emote.NONE, List.of()),
                new ActorView(4, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of())));
        assertEquals(List.of(4, 11), actors.actors().stream().map(ActorView::cell).toList());
        assertThrows(UnsupportedOperationException.class, () -> actors.actors().add(null));
        List<Challenge> withNull = new ArrayList<>();
        withNull.add(Challenge.DARKNESS);
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new HeaderSection(1, "v3.3.8", "", HeroClass.MAGE, withNull,
                1, 0, false, false, PromptKind.NONE));
        List<LogLine> lines = new ArrayList<>();
        lines.add(null);
        assertThrows(NullPointerException.class, () -> new LogSection(lines));
        assertThrows(UnsupportedOperationException.class, () -> Corpus.inventory().items().add(null));
    }
}
