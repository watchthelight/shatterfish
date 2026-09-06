package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Every list has a canonical order fixed by the schema, never the order an input arrived in
 * (ADR-0005): each set-like list of the corpus is given to its record reversed, and the section
 * hashes, the records and the bytes come out the same. The positional lists are the exception
 * the schema names: tiles and fog are the map, the inventory is the belongings' order, the log
 * and a prompt's options are the order the game emitted them, quickslots and talent points are
 * per slot and per tier.
 */
class CodecCanonicalTest {

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        assertNotEquals(list, copy, "a list of one cannot be reordered; give the corpus two");
        return copy;
    }

    private static Map<String, String> hashes(Object section) {
        return Corpus.with(Corpus.observation(), section).sectionHashes();
    }

    @Test
    @DisplayName("the header's challenges in any order are the same header")
    void challenges_in_any_order() {
        HeaderSection base = Corpus.header();
        HeaderSection shuffled = new HeaderSection(base.version(), base.upstreamTag(), base.codexVersion(),
                base.heroClass(), reversed(base.challenges()), base.depth(), base.branch(), base.sealed(), base.oracle(),
                base.prompt());
        assertEquals(base, shuffled);
        assertEquals(hashes(base), hashes(shuffled));
    }

    @Test
    @DisplayName("the map's traps, heaps, blobs, blob kinds and transitions in any order are the same map")
    void map_lists_in_any_order() {
        MapSection base = Corpus.map();
        List<BlobCell> blobs = new ArrayList<>();
        for (BlobCell blob : reversed(base.blobs())) {
            blobs.add(blob.kinds().size() > 1 ? new BlobCell(blob.cell(), reversed(blob.kinds())) : blob);
        }
        MapSection shuffled = new MapSection(base.width(), base.height(), base.tiles(), base.fog(),
                reversed(base.traps()), reversed(base.heaps()), blobs, base.feeling(), reversed(base.transitions()));
        assertEquals(base, shuffled);
        assertEquals(hashes(base), hashes(shuffled));
    }

    @Test
    @DisplayName("the actors and each actor's buffs in any order are the same actors")
    void actors_in_any_order() {
        ActorsSection base = Corpus.actorsSection();
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : reversed(base.actors())) {
            actors.add(actor.buffs().size() > 1
                    ? new ActorView(actor.cell(), actor.name(), actor.alignment(), actor.healthPips(), actor.invisible(),
                            actor.emote(), reversed(actor.buffs()))
                    : actor);
        }
        ActorsSection shuffled = new ActorsSection(actors);
        assertEquals(base, shuffled);
        assertEquals(hashes(base), hashes(shuffled));
    }

    @Test
    @DisplayName("the hero's buffs and talents in any order are the same hero")
    void hero_lists_in_any_order() {
        HeroSection base = Corpus.hero();
        HeroSection shuffled = new HeroSection(base.cell(), base.name(), base.subclass(), base.ability(), base.level(),
                base.exp(), base.expToLevel(), base.hp(), base.ht(), base.shield(), base.strength(), base.strengthBonus(),
                base.gold(), base.energy(), base.hunger(), reversed(base.buffs()), reversed(base.talents()),
                base.talentPointsAvailable(), base.quickslots());
        assertEquals(base, shuffled);
        assertEquals(hashes(base), hashes(shuffled));
    }

    @Test
    @DisplayName("two hero buffs of one name in either order are the same hero")
    void hero_buffs_of_one_name_in_any_order() {
        HeroSection base = Corpus.hero();
        List<BuffView> twice = List.of(new BuffView("Poisoned", true, 300), new BuffView("Poisoned", true, 500));
        HeroSection a = new HeroSection(base.cell(), base.name(), base.subclass(), base.ability(), base.level(),
                base.exp(), base.expToLevel(), base.hp(), base.ht(), base.shield(), base.strength(), base.strengthBonus(),
                base.gold(), base.energy(), base.hunger(), twice, base.talents(), base.talentPointsAvailable(),
                base.quickslots());
        HeroSection b = new HeroSection(base.cell(), base.name(), base.subclass(), base.ability(), base.level(),
                base.exp(), base.expToLevel(), base.hp(), base.ht(), base.shield(), base.strength(), base.strengthBonus(),
                base.gold(), base.energy(), base.hunger(), reversed(twice), base.talents(), base.talentPointsAvailable(),
                base.quickslots());
        assertEquals(a, b);
        assertEquals(hashes(a), hashes(b));
    }

    @Test
    @DisplayName("an item's actions in any order are the same item")
    void item_actions_in_any_order() {
        ItemView base = Corpus.items().get(3);
        ItemView shuffled = new ItemView(base.kind(), base.name(), base.quantity(), base.levelKnown(),
                base.visiblyUpgraded(), base.cursedKnown(), base.visiblyCursed(), base.status(), base.slot(),
                reversed(base.actions()), base.defaultAction());
        assertEquals(base, shuffled);
        List<ItemView> items = new ArrayList<>(Corpus.items());
        items.set(3, shuffled);
        assertEquals(hashes(Corpus.inventory()), hashes(new InventorySection(items)));
    }

    @Test
    @DisplayName("the journal's notes and known appearances in any order are the same journal")
    void journal_lists_in_any_order() {
        JournalSection base = Corpus.journal();
        JournalSection shuffled = new JournalSection(reversed(base.notes()), reversed(base.known()));
        assertEquals(base, shuffled);
        assertEquals(hashes(base), hashes(shuffled));
    }

    @Test
    @DisplayName("the valid Actions in any order are the same set")
    void actions_in_any_order() {
        ActionsSection base = Corpus.actionsSection();
        ActionsSection shuffled = new ActionsSection(reversed(base.actions()));
        assertEquals(base, shuffled);
        assertEquals(hashes(base), hashes(shuffled));
        List<Action> rotated = new ArrayList<>(Corpus.actions());
        Collections.rotate(rotated, 7);
        assertEquals(base, new ActionsSection(rotated));
    }

    @Test
    @DisplayName("tiles and fog are positional: two cells swapped is a different map")
    void the_positional_map_lists_are_not_reordered() {
        MapSection base = Corpus.map();
        List<Tile> tiles = new ArrayList<>(base.tiles());
        Collections.swap(tiles, 0, 1);
        MapSection swapped = new MapSection(base.width(), base.height(), tiles, base.fog(), base.traps(), base.heaps(),
                base.blobs(), base.feeling(), base.transitions());
        assertNotEquals(base, swapped);
        assertNotEquals(hashes(base).get(ObservationCodec.MAP), hashes(swapped).get(ObservationCodec.MAP));
    }

    @Test
    @DisplayName("the inventory, the log, the quickslots, the talent points and a prompt's options are positional")
    void the_other_positional_lists_are_not_reordered() {
        List<ItemView> items = new ArrayList<>(Corpus.items());
        Collections.swap(items, 3, 4);
        // Without the Actions, whose item references name positions in the inventory.
        Observation noActions = Corpus.with(Corpus.observation(), ActionsSection.NONE);
        assertNotEquals(noActions.sectionHashes().get(ObservationCodec.INVENTORY),
                Corpus.with(noActions, new InventorySection(items)).sectionHashes().get(ObservationCodec.INVENTORY));

        List<LogLine> lines = new ArrayList<>(Corpus.lines());
        Collections.swap(lines, 0, 1);
        assertNotEquals(hashes(Corpus.log()).get(ObservationCodec.LOG),
                hashes(new LogSection(lines)).get(ObservationCodec.LOG));

        HeroSection hero = Corpus.hero();
        List<QuickslotView> quickslots = new ArrayList<>(hero.quickslots());
        Collections.swap(quickslots, 0, 1);
        List<Integer> points = new ArrayList<>(hero.talentPointsAvailable());
        Collections.swap(points, 0, 1);
        HeroSection swappedSlots = new HeroSection(hero.cell(), hero.name(), hero.subclass(), hero.ability(), hero.level(),
                hero.exp(), hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(), hero.strength(), hero.strengthBonus(),
                hero.gold(), hero.energy(), hero.hunger(), hero.buffs(), hero.talents(), hero.talentPointsAvailable(),
                quickslots);
        HeroSection swappedPoints = new HeroSection(hero.cell(), hero.name(), hero.subclass(), hero.ability(), hero.level(),
                hero.exp(), hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(), hero.strength(), hero.strengthBonus(),
                hero.gold(), hero.energy(), hero.hunger(), hero.buffs(), hero.talents(), points, hero.quickslots());
        assertNotEquals(hashes(hero).get(ObservationCodec.HERO), hashes(swappedSlots).get(ObservationCodec.HERO));
        assertNotEquals(hashes(hero).get(ObservationCodec.HERO), hashes(swappedPoints).get(ObservationCodec.HERO));

        PromptSection prompt = Corpus.chasmPrompt();
        PromptSection swappedOptions = new PromptSection(prompt.kind(), prompt.title(), prompt.text(),
                reversed(prompt.options()));
        Observation withPrompt = Corpus.promptObservation();
        assertNotEquals(withPrompt.sectionHashes().get(ObservationCodec.PROMPT),
                Corpus.with(withPrompt, swappedOptions).sectionHashes().get(ObservationCodec.PROMPT));
    }
}
