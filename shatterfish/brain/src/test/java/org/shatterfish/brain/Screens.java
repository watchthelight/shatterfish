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

    static final Codex.Manifest CODEX = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json"));

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
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, kind);
        MapSection map = new MapSection(3, 1, List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY),
                List.of(Fog.VISIBLE, Fog.VISIBLE, Fog.VISIBLE), List.of(), List.of(), List.of(), Feeling.NONE,
                List.of());
        HeroSection hero = new HeroSection(1, "", HeroSubclass.NONE, "", 1, 0, 1, 1, 1, 0, 1, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        return new Observation(header, map, new ActorsSection(List.of()), hero, new InventorySection(List.of()),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()),
                new ActionsSection(List.of(actions)), prompt);
    }
}
