package org.shatterfish.api;

import java.util.Map;
import java.util.Objects;

/**
 * What the brain sees: an immutable record tree, section by section, of what the screen, the HUD,
 * the log and the journal show at one Input wait (ADR-0005, AD-2): the header, the map, the
 * actors, the hero, the inventory, the journal, the log, the valid Actions and the open Prompt.
 *
 * <p>Equality is structural, as for every record, and every list is in the one order its record
 * fixes, so two Observations of the same screen are equal and have equal bytes and hashes
 * whatever order their parts were collected in; a test holds {@code equals} and {@link #hash()}
 * to each other over a corpus. The record holds its sections to each other: every character
 * stands on a cell in view and not on the hero's, the hero stands in view, the header and the
 * prompt section name the same Prompt, and every Action's parameter is a value the Observation
 * carries (ADR-0014).
 *
 * <p>The valid Actions are computed from the other sections, so an Observer builds the record
 * with {@link ActionsSection#NONE} first and {@link #withActions} once they are known.
 */
public record Observation(HeaderSection header, MapSection map, ActorsSection actors, HeroSection hero,
                          InventorySection inventory, JournalSection journal, LogSection log, ActionsSection actions,
                          PromptSection prompt) {

    public Observation {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(actors, "actors");
        Objects.requireNonNull(hero, "hero");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(prompt, "prompt");
        int cells = map.cells();
        Canon.require(hero.cell() < cells, "the hero is off the map at cell " + hero.cell());
        Canon.require(map.fog().get(hero.cell()) == Fog.VISIBLE,
                "the hero stands on cell " + hero.cell() + ", which is " + map.fog().get(hero.cell())
                        + ": the hero sees its own cell");
        for (ActorView actor : actors.actors()) {
            Canon.require(actor.cell() < cells, "an actor is off the map at cell " + actor.cell());
            Canon.require(map.fog().get(actor.cell()) == Fog.VISIBLE,
                    "an actor stands on cell " + actor.cell() + ", which is " + map.fog().get(actor.cell())
                            + ": a character is drawn only in view (ADR-0006)");
            Canon.require(actor.cell() != hero.cell(), "an actor stands on the hero's cell " + actor.cell());
        }
        Canon.require(prompt.kind() == header.prompt(),
                "the header names the open Prompt " + header.prompt() + " and the prompt section " + prompt.kind());
        for (Action action : actions.actions()) {
            check(action, cells, inventory, hero, prompt);
        }
    }

    /** This Observation with its valid Actions filled in. */
    public Observation withActions(ActionsSection actions) {
        return new Observation(header, map, actors, hero, inventory, journal, log, actions, prompt);
    }

    /** The Observation's hash: SHA-256 over the schema version and the section hashes, in hex. */
    public String hash() {
        return ObservationCodec.hash(this);
    }

    /** The hash of each section's canonical bytes, in section order, for the differential test. */
    public Map<String, String> sectionHashes() {
        return ObservationCodec.sectionHashes(this);
    }

    /** The readable form: canonical JSON carrying the hash, never read back (ADR-0005, option 10). */
    public String json() {
        return ObservationJson.render(this);
    }

    private static void check(Action action, int cells, InventorySection inventory, HeroSection hero,
                              PromptSection prompt) {
        if (action instanceof Action.Step step) {
            cell(step.cell(), cells, action);
        } else if (action instanceof Action.MoveTo move) {
            cell(move.cell(), cells, action);
        } else if (action instanceof Action.Attack attack) {
            cell(attack.cell(), cells, action);
        } else if (action instanceof Action.Interact interact) {
            cell(interact.cell(), cells, action);
        } else if (action instanceof Action.OpenChest chest) {
            cell(chest.cell(), cells, action);
        } else if (action instanceof Action.Buy buy) {
            cell(buy.cell(), cells, action);
        } else if (action instanceof Action.Unlock unlock) {
            cell(unlock.cell(), cells, action);
        } else if (action instanceof Action.UseItem use) {
            item(use.item(), use.action(), inventory, action);
        } else if (action instanceof Action.UseItemAt use) {
            item(use.item(), use.action(), inventory, action);
            cell(use.cell(), cells, action);
        } else if (action instanceof Action.UseItemOn use) {
            item(use.item(), use.action(), inventory, action);
            item(use.target(), "", inventory, action);
        } else if (action instanceof Action.Talent talent) {
            boolean named = false;
            for (TalentView view : hero.talents()) {
                named |= view.name().equals(talent.talent());
            }
            Canon.require(named, action + " names a talent the hero section does not list");
        } else if (action instanceof Action.Ability ability) {
            ability(ability.ability(), hero, action);
        } else if (action instanceof Action.AbilityAt ability) {
            ability(ability.ability(), hero, action);
            cell(ability.cell(), cells, action);
        } else if (action instanceof Action.AnswerPrompt answer) {
            Canon.require(answer.option() < prompt.options().size(),
                    action + " answers with option " + answer.option() + " of " + prompt.options().size());
        }
    }

    private static void cell(int cell, int cells, Action action) {
        Canon.require(cell < cells, action + " names cell " + cell + " off a map of " + cells + " cells");
    }

    private static void item(ItemRef ref, String action, InventorySection inventory, Action whole) {
        Canon.require(ref.index() < inventory.items().size(),
                whole + " names item " + ref.index() + " of an inventory of " + inventory.items().size());
        ItemView item = inventory.items().get(ref.index());
        Canon.require(ref.matches(item), whole + " names " + ref.name() + " x" + ref.quantity() + " at index "
                + ref.index() + ", where the inventory lists " + item.name() + " x" + item.quantity());
        Canon.require(action.isEmpty() || item.actions().contains(action),
                whole + " uses action " + action + ", which " + item.name() + " does not offer: " + item.actions());
    }

    private static void ability(String ability, HeroSection hero, Action action) {
        Canon.require(!hero.ability().isEmpty() && hero.ability().equals(ability),
                action + " names ability " + ability + ", where the hero section says " + hero.ability());
    }
}
