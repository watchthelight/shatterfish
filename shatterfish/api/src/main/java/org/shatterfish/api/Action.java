package org.shatterfish.api;

import java.util.Objects;

/**
 * One human input, as ADR-0014 fixes the kinds: a click, a key, or a button, plus the answer to
 * the selector that input opens. Every parameter is a value the Observation carries, a cell it
 * includes, an {@link ItemRef} into its inventory, a name from its hero section, or an index into
 * its prompt's options, which is what lets the valid set be computed from the Observation alone
 * (story 1.12) and keeps the leak surface at zero; the Observation refuses an action whose
 * parameter it does not carry.
 *
 * <p>The item use the decision wrote as one kind with an optional target is three kinds here, one
 * per shape of target, so that every switch over actions is exhaustive and every component is a
 * value. The option index the decision allowed as a target is not a kind: a window of options an
 * item opens, an enchantment to choose, lists labels that are not known at the Input wait the item
 * is used from, and a recognised window in front is an Input wait of its own (ADR-0015, story
 * 1.5), so that window is a Prompt and its answer an {@link AnswerPrompt} at the next wait.
 * {@link MoveTo} is the human's click on a distant cell, recorded as they made it; it is never in
 * a valid set, since the bot moves one step at a time, and {@link ActionsSection} refuses it.
 */
public sealed interface Action permits Action.Step, Action.MoveTo, Action.Attack, Action.Interact, Action.PickUp,
        Action.OpenChest, Action.Buy, Action.Unlock, Action.Descend, Action.Ascend, Action.UseItem, Action.UseItemAt,
        Action.UseItemOn, Action.Rest, Action.Search, Action.Talent, Action.Ability,
        Action.AbilityAt, Action.AnswerPrompt, Action.Wait {

    /** The kind's name, the record's own, which the codec and the JSON write first. */
    String kind();

    /** One step to an adjacent cell: one click or one direction key. */
    record Step(int cell) implements Action {
        public Step {
            Canon.cell(cell, "a step");
        }

        @Override
        public String kind() {
            return "Step";
        }
    }

    /** A human's click on a distant cell, which the game walks to over several turns; never valid for the bot. */
    record MoveTo(int cell) implements Action {
        public MoveTo {
            Canon.cell(cell, "a move");
        }

        @Override
        public String kind() {
            return "MoveTo";
        }
    }

    /** A click on a visible enemy. */
    record Attack(int cell) implements Action {
        public Attack {
            Canon.cell(cell, "an attack");
        }

        @Override
        public String kind() {
            return "Attack";
        }
    }

    /** A click on an NPC, or on an ally to swap places. */
    record Interact(int cell) implements Action {
        public Interact {
            Canon.cell(cell, "an interaction");
        }

        @Override
        public String kind() {
            return "Interact";
        }
    }

    /** A click on the hero's own cell over a heap. */
    record PickUp() implements Action {
        @Override
        public String kind() {
            return "PickUp";
        }
    }

    /** A click on a container heap, adjacent or under the hero. */
    record OpenChest(int cell) implements Action {
        public OpenChest {
            Canon.cell(cell, "a chest");
        }

        @Override
        public String kind() {
            return "OpenChest";
        }
    }

    /** A click on shop stock, then the trade window's buy button. */
    record Buy(int cell) implements Action {
        public Buy {
            Canon.cell(cell, "a purchase");
        }

        @Override
        public String kind() {
            return "Buy";
        }
    }

    /** A click on a locked door or exit with the key held. */
    record Unlock(int cell) implements Action {
        public Unlock {
            Canon.cell(cell, "an unlock");
        }

        @Override
        public String kind() {
            return "Unlock";
        }
    }

    /** A click on the transition the hero stands on, downward. */
    record Descend() implements Action {
        @Override
        public String kind() {
            return "Descend";
        }
    }

    /** A click on the transition the hero stands on, upward. */
    record Ascend() implements Action {
        @Override
        public String kind() {
            return "Ascend";
        }
    }

    /** An item button, then one of its actions, needing no target. */
    record UseItem(ItemRef item, String action) implements Action {
        public UseItem {
            Objects.requireNonNull(item, "item");
            action = Canon.text(action, "item action");
            Canon.require(!action.isEmpty(), "an item use names its action");
        }

        @Override
        public String kind() {
            return "UseItem";
        }
    }

    /** An item action that opens the cell selector, answered with a cell. */
    record UseItemAt(ItemRef item, String action, int cell) implements Action {
        public UseItemAt {
            Objects.requireNonNull(item, "item");
            action = Canon.text(action, "item action");
            Canon.require(!action.isEmpty(), "an item use names its action");
            Canon.cell(cell, "an item's target");
        }

        @Override
        public String kind() {
            return "UseItemAt";
        }
    }

    /** An item action that opens the bag, answered with another item. */
    record UseItemOn(ItemRef item, String action, ItemRef target) implements Action {
        public UseItemOn {
            Objects.requireNonNull(item, "item");
            action = Canon.text(action, "item action");
            Canon.require(!action.isEmpty(), "an item use names its action");
            Objects.requireNonNull(target, "target");
        }

        @Override
        public String kind() {
            return "UseItemOn";
        }
    }

    /** The rest button ({@code full}) or the wait button held to rest. */
    record Rest(boolean full) implements Action {
        @Override
        public String kind() {
            return "Rest";
        }
    }

    /** The search button. */
    record Search() implements Action {
        @Override
        public String kind() {
            return "Search";
        }
    }

    /** A point spent in a talent from the talents pane, named as the hero section names it. */
    record Talent(String talent) implements Action {
        public Talent {
            talent = Canon.text(talent, "talent");
            Canon.require(!talent.isEmpty(), "a talent action names its talent");
        }

        @Override
        public String kind() {
            return "Talent";
        }
    }

    /** The armour ability from the action indicator, needing no target. */
    record Ability(String ability) implements Action {
        public Ability {
            ability = Canon.text(ability, "ability");
            Canon.require(!ability.isEmpty(), "an ability action names its ability");
        }

        @Override
        public String kind() {
            return "Ability";
        }
    }

    /** The armour ability, answered with a cell. */
    record AbilityAt(String ability, int cell) implements Action {
        public AbilityAt {
            ability = Canon.text(ability, "ability");
            Canon.require(!ability.isEmpty(), "an ability action names its ability");
            Canon.cell(cell, "an ability's target");
        }

        @Override
        public String kind() {
            return "AbilityAt";
        }
    }

    /** A button of the open Prompt, by its index in the prompt section's options. */
    record AnswerPrompt(int option) implements Action {
        public AnswerPrompt {
            Canon.require(option >= 0, "an answer is an index: " + option);
        }

        @Override
        public String kind() {
            return "AnswerPrompt";
        }
    }

    /** The wait button: one turn passes. */
    record Wait() implements Action {
        @Override
        public String kind() {
            return "Wait";
        }
    }
}
