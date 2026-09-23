package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
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

    static final Codex.Manifest CODEX = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json"));

    /** The committed weight set's values (weights/shatterfish.json), which WeightsFileTest holds to the file. */
    static final Weights WEIGHTS = weights(Map.of());

    /** The committed weights with {@code changed} weights replaced. */
    static Weights weights(Map<String, Long> changed) {
        Map<String, Long> terms = new java.util.TreeMap<>(Map.of(
                "act_attack", 0L, "act_descend", 0L, "act_rest_hurt", 0L, "act_search", 0L, "act_wait", 0L,
                "depth", 10000L, "enemies", -3000L, "hp", 10L, "hunger", -5000L, "level", 5000L));
        terms.put("strength", 2000L);
        terms.putAll(changed);
        return new Weights("shatterfish", 1,
                terms.entrySet().stream().map(term -> new Weights.Term(term.getKey(), term.getValue())).toList());
    }

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

    /** A screen at {@code depth} with this hero and these actors in view, offering {@code actions}. */
    static Observation showing(int depth, HeroSection hero, List<ActorView> actors, Action... actions) {
        return screen(depth, PromptKind.NONE, PromptSection.NONE, hero, actors, actions);
    }

    private static Observation screen(int depth, PromptKind kind, PromptSection prompt, Action... actions) {
        return screen(depth, kind, prompt, hero(), List.of(), actions);
    }

    private static Observation screen(int depth, PromptKind kind, PromptSection prompt, HeroSection hero,
                                      List<ActorView> actors, Action... actions) {
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, kind);
        MapSection map = new MapSection(3, 1, List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY),
                List.of(Fog.VISIBLE, Fog.VISIBLE, Fog.VISIBLE), List.of(), List.of(), List.of(), Feeling.NONE,
                List.of());
        return new Observation(header, map, new ActorsSection(actors), hero, new InventorySection(List.of()),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()),
                new ActionsSection(List.of(actions)), prompt);
    }
}
