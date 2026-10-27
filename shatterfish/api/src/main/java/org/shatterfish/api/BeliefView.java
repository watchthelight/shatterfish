package org.shatterfish.api;

import java.util.List;

/**
 * Everything the Brain believes at an Input wait, in a form the Overlay may show (story 8.6, FR-46): the
 * full view behind the three-item {@link BeliefSummary}.
 *
 * <p>It is {@code api} for the reason the summary is: the brain module builds it from its own {@code Beliefs}
 * (which came from the Observations and the Memory they fed, and from nothing else), and the harness and the
 * Overlay read it through types both can see. A candidate is one of the appearance's own possible identities
 * with the Brain's own marginal for it, never the hidden one.
 *
 * @param hash        the hash of the Belief the Wait record stores for this wait ({@link Belief#hash()}), so the
 *                    view over a log checks itself against the log; the contents are the Beliefs of that same Belief,
 *                    taken after the wait (the hash does not by itself prove the contents, the shared source does)
 * @param appearances every unidentified appearance the Brain tracks, with its candidates in the Brain's own
 *                    order (most likely first); the order of the appearances is the Brain's own and the Panel
 *                    sorts for display
 * @param floor       the items this floor is known to hold, and why, one formatted line each
 * @param monsters    the enemies remembered but not in view at this wait, and the wait each was last seen, one
 *                    formatted line each (FR-29)
 * @param chapters    each guaranteed drop's counter for the current set of floors, one formatted line each
 */
public record BeliefView(String hash, List<Appearance> appearances, List<String> floor, List<String> monsters,
                         List<String> chapters) {

    /** One unidentified appearance and the identities it may have, most likely first. */
    public record Appearance(String label, List<Candidate> candidates) {

        public Appearance {
            Canon.text(label, "an appearance's label");
            candidates = Canon.positional(candidates, "an appearance's candidates");
        }
    }

    /** One possible identity and the Brain's probability for it. */
    public record Candidate(String name, double probability) {

        public Candidate {
            Canon.text(name, "a candidate's name");
            Canon.require(probability >= 0 && probability <= 1,
                    "a probability is between 0 and 1: " + probability);
        }
    }

    public BeliefView {
        Canon.text(hash, "a belief view's hash");
        appearances = Canon.positional(appearances, "a belief view's appearances");
        floor = Canon.positional(floor, "a belief view's floor facts");
        monsters = Canon.positional(monsters, "a belief view's remembered monsters");
        chapters = Canon.positional(chapters, "a belief view's chapter counters");
    }
}
