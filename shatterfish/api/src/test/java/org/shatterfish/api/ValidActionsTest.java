package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The valid Actions are a function of the Observation and of nothing else (ADR-0014; story 1.12).
 * This suite runs in {@code api}, which the build forbids from seeing a line of the game
 * ({@code ApiBoundaryTest}), so "with no game running" is not a thing the test arranges: it is the
 * only thing that can happen here. What the suite adds is that the function is total over the
 * schema, that every parameter it produces is a value the Observation carries, and that the rules
 * ADR-0014 names hold.
 */
class ValidActionsTest {

    @Test
    @DisplayName("under a Prompt the options are the only Actions, and waiting is not one of them")
    void a_prompt_takes_the_whole_wait() {
        Observation observation = Corpus.promptObservation();
        ActionsSection valid = ValidActions.of(observation);

        assertEquals(observation.prompt().options().size(), valid.actions().size(),
                "one Action per button: " + valid.actions());
        for (int option = 0; option < observation.prompt().options().size(); option++) {
            assertTrue(valid.actions().contains(new Action.AnswerPrompt(option)), "option " + option);
        }
        assertFalse(valid.actions().contains(new Action.Wait()),
                "a Brain that waits at a Prompt is a Brain error, not a stall (ADR-0014)");
        for (Action action : valid.actions()) {
            assertTrue(action instanceof Action.AnswerPrompt, action + " is not an answer");
        }
    }

    @Test
    @DisplayName("a move is one step to a cell beside the hero, and never a click on a distant one")
    void a_move_is_one_step() {
        Observation observation = Corpus.observation();
        ActionsSection valid = ValidActions.of(observation);
        int width = observation.map().width();
        int hero = observation.hero().cell();

        int steps = 0;
        for (Action action : valid.actions()) {
            assertFalse(action instanceof Action.MoveTo, "a click on a distant cell is a human's, never valid");
            if (action instanceof Action.Step step) {
                steps++;
                int dx = Math.abs(step.cell() % width - hero % width);
                int dy = Math.abs(step.cell() / width - hero / width);
                assertTrue(dx <= 1 && dy <= 1 && (dx + dy) > 0,
                        "a step is to one of the eight cells around the hero: " + step.cell() + " from " + hero);
            }
        }
        assertTrue(steps > 0, "the hero has somewhere to go: " + valid.actions());
    }

    @Test
    @DisplayName("every parameter is a value the Observation carries, which the record itself holds")
    void every_parameter_is_carried() {
        Observation observation = Corpus.observation();
        // The Observation's own constructor checks every Action against the sections: a cell it
        // includes, an item reference its inventory lists with that name and quantity, an action
        // that item offers, a talent its hero has, its own ability, an option of its prompt
        // (ADR-0014). Building the record with the computed set is therefore the assertion.
        Observation whole = observation.withActions(ValidActions.of(observation));
        assertFalse(whole.actions().actions().isEmpty());
        assertEquals(whole.actions(), ValidActions.of(whole), "the set does not change by being carried");

        // And the same holds for the screen with the Prompt open.
        Observation prompt = Corpus.promptObservation();
        assertEquals(prompt.prompt().options().size(),
                prompt.withActions(ValidActions.of(prompt)).actions().actions().size());
    }

    @Test
    @DisplayName("the same Observation gives the same set, whatever order its parts were collected in")
    void the_set_is_a_function_of_the_value() {
        Observation observation = Corpus.observation();
        Observation copy = new Observation(observation.header(), observation.map(), observation.actors(),
                observation.hero(), observation.inventory(), observation.journal(), observation.log(),
                ActionsSection.NONE, observation.prompt());
        assertEquals(observation.map(), copy.map(), "the same value, built again");

        ActionsSection first = ValidActions.of(observation);
        ActionsSection second = ValidActions.of(copy);
        assertEquals(first, second);
        assertEquals(first.actions(), second.actions(), "in the same order, which is the section's own");
        assertEquals(observation.withActions(first).hash(), copy.withActions(second).hash(),
                "so the bytes and the hash are the same");
    }

    @Test
    @DisplayName("the boss lock takes the stairs away")
    void a_sealed_floor_offers_no_transition() {
        Observation observation = Corpus.observation();
        TransitionView under = new TransitionView(observation.hero().cell(), TransitionKind.REGULAR_EXIT);
        MapSection map = withTransition(observation.map(), under);
        Observation open = Corpus.with(observation, map);
        assertTrue(ValidActions.of(open).actions().contains(new Action.Descend()),
                "the hero stands on the way down");

        HeaderSection header = observation.header();
        Observation sealed = Corpus.with(open, new HeaderSection(header.version(), header.upstreamTag(),
                header.codexVersion(), header.heroClass(), header.challenges(), header.depth(), header.branch(),
                true, header.oracle(), header.prompt()));
        assertTrue(sealed.header().sealed());
        assertFalse(ValidActions.of(sealed).actions().contains(new Action.Descend()),
                "a boss fight locks the floor, so the set never offers a descent the game refuses");
    }

    @Test
    @DisplayName("a character beside the hero is an attack or an interaction, never a step")
    void a_neighbour_is_not_a_step() {
        Observation plain = Corpus.observation();
        int hero = plain.hero().cell();
        int width = plain.map().width();
        // The corpus keeps its characters away from the hero, so the test brings two of them
        // beside it, one of each alignment, at cells the map draws as floor.
        List<ActorView> beside = List.of(
                new ActorView(hero + 1, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of()),
                new ActorView(hero + width, "Ghost", Alignment.NEUTRAL, 11, false, Emote.NONE, List.of()));
        Observation observation = Corpus.with(plain, new ActorsSection(beside));
        for (ActorView actor : beside) {
            assertEquals(Fog.VISIBLE, observation.map().fog().get(actor.cell()), "drawn where it stands");
        }

        Set<Integer> stepped = new HashSet<>();
        for (Action action : ValidActions.of(observation).actions()) {
            if (action instanceof Action.Step step) {
                stepped.add(step.cell());
            }
        }
        for (ActorView actor : beside) {
            assertFalse(stepped.contains(actor.cell()), actor.name() + " stands there");
            Action expected = actor.alignment() == Alignment.ENEMY
                    ? new Action.Attack(actor.cell())
                    : new Action.Interact(actor.cell());
            assertTrue(ValidActions.of(observation).actions().contains(expected), "expected " + expected);
        }
    }

    @Test
    @DisplayName("an item's actions are offered in the shape their target has")
    void an_item_is_offered_as_its_target_needs() {
        Observation observation = Corpus.observation();
        ActionsSection valid = ValidActions.of(observation);
        List<ItemView> items = observation.inventory().items();

        boolean plain = false;
        boolean targeted = false;
        for (Action action : valid.actions()) {
            if (action instanceof Action.UseItem use) {
                plain = true;
                assertTrue(items.get(use.item().index()).actions().contains(use.action()));
            } else if (action instanceof Action.UseItemAt use) {
                targeted = true;
                assertTrue(Set.of("THROW", "ZAP").contains(use.action()),
                        use.action() + " is not an action that opens the cell selector");
                assertTrue(use.cell() == observation.hero().cell()
                                || observation.actors().actors().stream().anyMatch(a -> a.cell() == use.cell()),
                        "a targeted use is offered at a character in view or at the hero's own cell");
            }
        }
        assertTrue(plain, "the pack holds something to use: " + items);
        assertTrue(targeted, "and something to throw");
    }

    @Test
    @DisplayName("a cell the hero cannot walk onto is no step")
    void a_wall_is_not_a_step() {
        Observation observation = Corpus.observation();
        int hero = observation.hero().cell();
        int width = observation.map().width();
        // The corpus stands the hero on the entrance at the top left, so its neighbours are the
        // cell to the right, the one below, and the one diagonally between them.
        int right = hero + 1;
        int below = hero + width;
        assertEquals(Tile.EMPTY, observation.map().tiles().get(right));

        List<Action> open = ValidActions.of(observation).actions();
        assertTrue(open.contains(new Action.Step(right)), "floor beside the hero is a step");

        // Every tile whose terrain the game flags SOLID: a wall, a barricade, an alchemy pot, a
        // statue, a mine crystal, a bookshelf (Terrain.java:85-128). A click on one is no step.
        for (Tile solid : List.of(Tile.WALL, Tile.WALL_DECO, Tile.BARRICADE, Tile.BOOKSHELF, Tile.ALCHEMY,
                Tile.STATUE, Tile.STATUE_SP, Tile.MINE_CRYSTAL, Tile.MINE_BOULDER, Tile.REGION_DECO,
                Tile.REGION_DECO_ALT)) {
            Observation walled = Corpus.with(observation, withTile(observation.map(), right, solid));
            assertFalse(ValidActions.of(walled).actions().contains(new Action.Step(right)),
                    solid + " is not a cell a click walks onto");
        }

        // The two the game flags avoid rather than passable are steps all the same, since the
        // hero's own rule is passable or avoid (Hero.java:1832-1835): the chasm, which asks before
        // the jump, and the well, which is how a person drinks it.
        for (Tile avoid : List.of(Tile.CHASM, Tile.WELL)) {
            Observation there = Corpus.with(observation, withTile(observation.map(), right, avoid));
            assertTrue(ValidActions.of(there).actions().contains(new Action.Step(right)),
                    avoid + " is a cell the hero can step onto; what happens then is the game's business");
        }

        // A locked door and the boss floor's exit are not steps but unlocks (Hero.java:1993-1998).
        for (Tile locked : List.of(Tile.LOCKED_DOOR, Tile.CRYSTAL_DOOR, Tile.LOCKED_EXIT)) {
            Observation shut = Corpus.with(observation, withTile(observation.map(), right, locked));
            List<Action> valid = ValidActions.of(shut).actions();
            assertFalse(valid.contains(new Action.Step(right)), locked + " is not walked onto");
            assertTrue(valid.contains(new Action.Unlock(right)), locked + " is unlocked");
        }

        // A cell the player has never seen is drawn as nothing and is no step either.
        Observation unseen = Corpus.with(observation, withTile(observation.map(), below, Tile.NONE));
        assertFalse(ValidActions.of(unseen).actions().contains(new Action.Step(below)));
    }

    @Test
    @DisplayName("what lies under the hero is offered by the kind of heap it is")
    void the_heap_underfoot_is_offered_by_kind() {
        Observation observation = Corpus.observation();
        int hero = observation.hero().cell();
        assertTrue(ValidActions.of(observation).actions().stream().noneMatch(a -> a instanceof Action.PickUp),
                "the corpus leaves the hero's own cell bare");

        for (HeapKind kind : List.of(HeapKind.HEAP, HeapKind.FOR_SALE, HeapKind.LOCKED_CHEST, HeapKind.CHEST,
                HeapKind.CRYSTAL_CHEST, HeapKind.TOMB, HeapKind.SKELETON, HeapKind.REMAINS, HeapKind.EBONY_CHEST)) {
            String item = kind == HeapKind.HEAP || kind == HeapKind.FOR_SALE ? "Ration of food" : "";
            int price = kind == HeapKind.FOR_SALE ? 50 : 0;
            // A for-sale heap of several items has no price and is picked up, not bought.
            Observation under = Corpus.with(observation,
                    withHeap(observation.map(), new HeapView(hero, kind, false, item, price, "")));
            List<Action> valid = ValidActions.of(under).actions();
            // What the hero itself would make of a click there (Hero.java:1974-1991): a plain heap
            // is picked up, a for-sale heap of one priced item is bought, and every other kind,
            // the locked chest included, is opened.
            Action expected = switch (kind) {
                case HEAP -> new Action.PickUp();
                case FOR_SALE -> new Action.Buy(hero);
                default -> new Action.OpenChest(hero);
            };
            assertTrue(valid.contains(expected), kind + " under the hero should offer " + expected);
            assertFalse(valid.contains(new Action.Unlock(hero)),
                    "an unlock is for a door, never for a heap (Action.java, Unlock)");
        }

        Observation stacked = Corpus.with(observation,
                withHeap(observation.map(), new HeapView(hero, HeapKind.FOR_SALE, false, "Ration of food", 0, "")));
        List<Action> valid = ValidActions.of(stacked).actions();
        assertTrue(valid.contains(new Action.PickUp()), "a shop heap the section gives no price is picked up");
        assertFalse(valid.contains(new Action.Buy(hero)));
    }

    @Test
    @DisplayName("a talent is offered only while its tier has a point to spend")
    void a_talent_needs_a_point() {
        Observation observation = Corpus.observation();
        List<Integer> points = observation.hero().talentPointsAvailable();
        ActionsSection valid = ValidActions.of(observation);
        for (TalentView talent : observation.hero().talents()) {
            boolean offered = valid.actions().contains(new Action.Talent(talent.name()));
            assertEquals(points.get(talent.tier() - 1) > 0, offered,
                    talent.name() + " of tier " + talent.tier() + ", with " + points + " to spend");
        }
    }

    private static MapSection withTile(MapSection map, int cell, Tile tile) {
        List<Tile> tiles = new java.util.ArrayList<>(map.tiles());
        tiles.set(cell, tile);
        List<Fog> fog = new java.util.ArrayList<>(map.fog());
        fog.set(cell, tile == Tile.NONE ? Fog.UNKNOWN : Fog.VISIBLE);
        List<HeapView> heaps = new java.util.ArrayList<>(map.heaps());
        List<TrapView> traps = new java.util.ArrayList<>(map.traps());
        List<BlobCell> blobs = new java.util.ArrayList<>(map.blobs());
        List<TransitionView> transitions = new java.util.ArrayList<>(map.transitions());
        if (tile == Tile.NONE) {
            // The record refuses anything standing on a cell the player has never seen.
            heaps.removeIf(h -> h.cell() == cell);
            traps.removeIf(t -> t.cell() == cell);
            blobs.removeIf(b -> b.cell() == cell);
            transitions.removeIf(t -> t.cell() == cell);
        }
        return new MapSection(map.width(), map.height(), tiles, fog, traps, heaps, blobs, map.feeling(), transitions);
    }

    private static MapSection withHeap(MapSection map, HeapView heap) {
        List<HeapView> heaps = new java.util.ArrayList<>(map.heaps());
        heaps.removeIf(existing -> existing.cell() == heap.cell());
        heaps.add(heap);
        return new MapSection(map.width(), map.height(), map.tiles(), map.fog(), map.traps(), heaps, map.blobs(),
                map.feeling(), map.transitions());
    }

    private static MapSection withTransition(MapSection map, TransitionView transition) {
        List<TransitionView> transitions = new java.util.ArrayList<>(map.transitions());
        transitions.removeIf(existing -> existing.cell() == transition.cell());
        transitions.add(transition);
        return new MapSection(map.width(), map.height(), map.tiles(), map.fog(), map.traps(), map.heaps(),
                map.blobs(), map.feeling(), transitions);
    }
}
