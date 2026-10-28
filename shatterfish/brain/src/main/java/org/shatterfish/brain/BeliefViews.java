package org.shatterfish.brain;

import org.shatterfish.api.BeliefView;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns this Brain's own {@link Beliefs} into the api's {@link BeliefView} (story 8.6, FR-46): all of what the
 * Brain believes at a wait, where {@link BeliefSummaries} keeps three items. A pure function, built from the
 * {@code Beliefs} (which the Memory and the Observation made) and the hash of the Belief the Wait record stores,
 * and from nothing else.
 *
 * <p>An appearance's candidates are the identities it may still be, in the Brain's own order, each with the
 * Brain's own marginal; the hidden identity is not among the inputs, so it cannot be among the outputs.
 */
final class BeliefViews {

    private BeliefViews() {
    }

    /** {@code beliefs}, whole, tagged with {@code hash}; both come from the one Belief the decider holds after the wait. */
    static BeliefView of(Beliefs beliefs, String hash) {
        List<BeliefView.Appearance> appearances = new ArrayList<>();
        for (Beliefs.Guess guess : beliefs.identities()) {
            if (guess.odds().isEmpty()) {
                // Nothing this appearance could still be: not a belief, the summary skips it too.
                continue;
            }
            appearances.add(new BeliefView.Appearance(guess.label(), guess.odds().stream()
                    .map(odds -> new BeliefView.Candidate(odds.name(), odds.probability())).toList()));
        }
        List<String> floor = beliefs.floor().stream()
                .map(item -> item.item() + " (" + item.because() + ")").toList();
        List<String> chapters = beliefs.chapters().stream()
                .map(chapter -> chapter.item() + " " + chapter.found() + "/" + (chapter.found() + chapter.owed()))
                .toList();
        // The unseen-monster memory: the enemies remembered and not in view at this wait. The wait a sighting
        // was last made is Monster.seenAt, as Memory counts the Brain's own waits.
        List<String> monsters = beliefs.monsters().stream().filter(Beliefs.Monster::stale)
                .map(monster -> monster.name() + ", last seen at wait " + monster.seenAt() + ", cell " + monster.cell())
                .toList();
        return new BeliefView(hash, appearances, floor, monsters, chapters);
    }
}
