package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Observation;

/**
 * A human-readable label for an {@link Action} (story 5.3's review): {@code Action.toString()} is the
 * record's own machine form ({@code "Step[cell=659]"}, {@code "Search[]"}), which is right for
 * {@code StrategyLog} and wrong for a screen a person reads (UX-DR13: labels and numbers, in the
 * game's own vocabulary, never a class name). Every kind is named, with no default, so a kind added to
 * the sealed {@link Action} does not compile here until someone has said what it reads as -- the same
 * discipline {@code brain}'s {@code Highlights} holds itself to.
 *
 * <p>{@code observation} is the one the Decision was made on (carried by
 * {@code EmbeddedRun.Snapshot}), read only for what a label needs: the hero's cell and the map's
 * width, for a {@link Action.Step} or {@link Action.MoveTo}'s compass direction; the actor in view at
 * a cell, for an {@link Action.Attack}'s target name, when one is in view there; and the open
 * Prompt's button labels, for an {@link Action.AnswerPrompt}'s option text. Null (no Decision yet, or
 * a constructed one with no Observation behind it) falls back to a plainer label for those kinds --
 * the raw cell, "attack" alone, the option's index -- never to a blank one.
 */
final class ActionText {

    private ActionText() {
    }

    /** {@code action} in words; {@code "none"} for {@code null}, which nothing in the Panel ever passes today. */
    static String of(Action action, Observation observation) {
        return switch (action) {
            case null -> "none";
            case Action.Step step -> "step " + direction(step.cell(), observation);
            case Action.MoveTo move -> "move " + direction(move.cell(), observation);
            case Action.Attack attack -> "attack" + target(attack.cell(), observation);
            case Action.Interact interact -> "interact";
            case Action.PickUp pickUp -> "pick up";
            case Action.OpenChest chest -> "open";
            case Action.Buy buy -> "buy";
            case Action.Unlock unlock -> "unlock";
            case Action.Descend descend -> "descend";
            case Action.Ascend ascend -> "ascend";
            case Action.UseItem use -> use.action() + " " + use.item().name();
            case Action.UseItemAt use -> use.action() + " " + use.item().name();
            // The item's action opened the bag and picked another item to act on (a scroll of
            // upgrade choosing the weapon it upgrades): the target is what the label names, since the
            // action already says what the item itself does.
            case Action.UseItemOn use -> use.action() + " " + use.target().name();
            case Action.Rest rest -> "rest";
            case Action.Search search -> "search";
            case Action.Talent talent -> "talent: " + talent.talent();
            case Action.Ability ability -> "ability: " + ability.ability();
            case Action.AbilityAt ability -> "ability: " + ability.ability();
            case Action.AnswerPrompt answer -> "answer: " + option(answer.option(), observation);
            case Action.DismissPrompt dismiss -> "dismiss";
            case Action.Wait wait -> "wait";
        };
    }

    /**
     * The compass direction from the hero's cell to {@code cell}, one of the eight a Step ever targets
     * (ADR-0014: one step per Action, always to an adjacent cell); {@code cell}'s own index when
     * {@code observation} is null.
     */
    private static String direction(int cell, Observation observation) {
        if (observation == null) {
            return String.valueOf(cell);
        }
        int width = observation.map().width();
        int from = observation.hero().cell();
        int dx = Integer.signum(cell % width - from % width);
        int dy = Integer.signum(cell / width - from / width);
        return switch (dy) {
            case -1 -> switch (dx) {
                case -1 -> "NW";
                case 1 -> "NE";
                default -> "N";
            };
            case 1 -> switch (dx) {
                case -1 -> "SW";
                case 1 -> "SE";
                default -> "S";
            };
            default -> switch (dx) {
                case -1 -> "W";
                case 1 -> "E";
                default -> "here";
            };
        };
    }

    /** {@code " <name>"} of the actor in view at {@code cell}, or {@code ""} when there is none, or no Observation. */
    private static String target(int cell, Observation observation) {
        if (observation != null) {
            for (ActorView actor : observation.actors().actors()) {
                if (actor.cell() == cell) {
                    return " " + actor.name();
                }
            }
        }
        return "";
    }

    /** The open Prompt's button label at {@code option}, or the index itself with no Observation to read it from. */
    private static String option(int option, Observation observation) {
        if (observation != null && option >= 0 && option < observation.prompt().options().size()) {
            return observation.prompt().options().get(option);
        }
        return String.valueOf(option);
    }
}
