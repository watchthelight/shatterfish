package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;

/**
 * The actors section of an Observation (ADR-0005): every character the player can see other than
 * the hero, by cell, each cell at most once. The hero is the hero section's (story 1.7), which
 * carries its cell; a neutral, passive mimic is a heap, not an actor (ADR-0006).
 */
public record ActorsSection(List<ActorView> actors) {

    public ActorsSection {
        actors = Canon.sorted(actors, Comparator.comparingInt(ActorView::cell), "actors");
        Canon.distinctBy(actors, ActorView::cell, "actors");
    }
}
