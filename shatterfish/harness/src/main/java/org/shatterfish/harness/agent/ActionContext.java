package org.shatterfish.harness.agent;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Observation;

import java.util.List;

/**
 * The pieces of an Observation {@code org.shatterfish.overlay.ActionText} needs to label an Action
 * in words -- the hero's cell and the map's width (a Step or MoveTo's compass direction), the actors
 * in view (an Attack's target name), and the open Prompt's option labels (an AnswerPrompt's option
 * text) -- kept per {@link BoundedLog.Entry} instead of the whole Observation (story 5.4, review
 * round: the Decision log read every {@code Action} with a null Observation, so its rows showed a
 * raw cell number where the Decision card, given the real Observation, showed a compass direction;
 * this is what lets a history entry -- which does not keep the Observation itself, since {@link
 * org.shatterfish.api.RunLog.Wait} only carries its hash -- still label its Action the same way).
 *
 * <p>Public because {@code EmbeddedRun.Snapshot.history()} hands {@link BoundedLog.Entry} (and so
 * this) to the Overlay, a different module; nothing here is more than what {@code ActionText}
 * already reads out of a real Observation (ADR-0014: it carries nothing the Decision could not
 * already see), so no new door from game state is opened by capturing it here instead of there.
 */
public record ActionContext(int heroCell, int mapWidth, List<ActorView> actors, List<String> promptOptions) {

    /** The pieces of {@code observation} a label needs, or null when {@code observation} is null. */
    public static ActionContext of(Observation observation) {
        if (observation == null) {
            return null;
        }
        return new ActionContext(observation.hero().cell(), observation.map().width(),
                observation.actors().actors(), observation.prompt().options());
    }
}
