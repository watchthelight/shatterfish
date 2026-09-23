package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the Brain believes at a wait (story 4.2, FR-29): what each unidentified appearance may be,
 * what the floor is known to hold, how many guaranteed drops the current set of floors still owes,
 * and where the enemies it has seen were last.
 *
 * <p>Every input is general game knowledge (the Codex's {@link Codex.Knowledge}) or something the
 * screen showed: the inventory and the heaps drawn, the journal's identified list, the actors in
 * view. No identity is read; each is inferred from the appearance's own name and the ones already
 * identified.
 *
 * @param identities what each unidentified appearance in view may be, one entry per appearance
 * @param floor      the items this floor is known to hold, and why
 * @param chapters   each guaranteed drop in the current set of floors: found so far and still owed
 * @param monsters   the enemies seen on this floor, the latest sighting of each
 */
public record Beliefs(List<Guess> identities, List<FloorItem> floor, List<Chapter> chapters, List<Monster> monsters) {

    /** One unidentified appearance and the identities it may have, most likely first. */
    public record Guess(String label, ItemKind kind, List<Odds> odds) {
    }

    /** One identity and its probability. */
    public record Odds(String name, double probability) {
    }

    /** An item the floor holds somewhere, and the sight that implies it. */
    public record FloorItem(String item, String because) {
    }

    /** A guaranteed drop in the current set of floors. */
    public record Chapter(String counter, String item, int set, int found, int owed) {
    }

    /** An enemy's latest sighting; stale when it was not in view at this wait. */
    public record Monster(String name, int cell, long seenAt, boolean stale) {
    }

    /** The room whose sight implies its floor's spawns: the pool room (PoolRoom.java:91). */
    static final String POOL_ROOM = "PoolRoom";

    /** The memory after seeing {@code observation}, given the memory before. */
    static Memory fold(Memory memory, Observation observation, Codex.Knowledge knowledge) {
        long waits = memory.waits() + 1;
        int depth = observation.header().depth();

        List<Memory.Fact> facts = new ArrayList<>(memory.facts());
        for (Memory.Fact fact : implied(observation, knowledge)) {
            if (!facts.contains(fact)) {
                facts.add(fact);
            }
        }

        List<Memory.Found> found = new ArrayList<>(memory.found());
        List<Memory.Held> held = new ArrayList<>();
        for (Codex.Guarantee guarantee : knowledge.guarantees()) {
            int now = identifiedQuantity(observation, guarantee.name());
            int before = memory.held(guarantee.counter());
            if (now > before) {
                // A rise in what the inventory shows identified: found, in the set this floor
                // belongs to. A drop and a pick-up of the same item would count twice; the Brain
                // does not drop items yet.
                int set = depth / guarantee.floorsPerSet();
                int already = memory.found(guarantee.counter(), set);
                found.removeIf(one -> one.counter().equals(guarantee.counter()) && one.set() == set);
                found.add(new Memory.Found(guarantee.counter(), set, already + now - before));
            }
            if (now > 0) {
                held.add(new Memory.Held(guarantee.counter(), now));
            }
        }

        return new Memory(waits, Math.max(memory.deepest(), depth), facts, found, held,
                sightings(memory.monsters(), observation, waits));
    }

    /** What the Brain believes, given the memory after {@link #fold} and the same observation. */
    static Beliefs view(Memory memory, Observation observation, Codex.Knowledge knowledge) {
        int depth = observation.header().depth();
        List<FloorItem> floor = memory.facts().stream().filter(fact -> fact.depth() == depth)
                .map(fact -> new FloorItem(fact.item(), fact.because())).toList();
        List<Chapter> chapters = new ArrayList<>();
        for (Codex.Guarantee guarantee : knowledge.guarantees()) {
            int set = depth / guarantee.floorsPerSet();
            int found = memory.found(guarantee.counter(), set);
            chapters.add(new Chapter(guarantee.counter(), guarantee.name(), set, found,
                    Math.max(0, guarantee.perSet() - found)));
        }
        List<Monster> monsters = memory.monsters().stream().filter(seen -> seen.depth() == depth)
                .map(seen -> new Monster(seen.name(), seen.cell(), seen.at(), seen.at() < memory.waits())).toList();
        return new Beliefs(identities(observation, knowledge), floor, chapters, monsters);
    }

    /**
     * What each unidentified appearance in view may be. The candidates are the family's identities
     * less those the journal lists as identified -- an identified identity wears its own name, so
     * no appearance still unidentified can be it -- each weighted by its deck weight. An identity
     * the decks never draw but a guarantee places (the potion of strength, the scroll of upgrade)
     * is weighted as the family's commonest identity: an assumption, not a Codex fact, and the one
     * number here a later story should measure.
     */
    static List<Guess> identities(Observation observation, Codex.Knowledge knowledge) {
        Set<String> labels = new LinkedHashSet<>();
        for (ItemView item : observation.inventory().items()) {
            labels.add(item.name());
        }
        for (HeapView heap : observation.map().heaps()) {
            if (!heap.item().isEmpty()) {
                labels.add(heap.item());
            }
        }
        List<Guess> guesses = new ArrayList<>();
        for (Codex.Identities family : knowledge.families()) {
            List<Codex.Candidate> open = new ArrayList<>();
            for (Codex.Candidate candidate : family.candidates()) {
                if (!identified(observation, family.kind(), candidate.name())) {
                    open.add(candidate);
                }
            }
            int commonest = family.candidates().stream().mapToInt(Codex.Candidate::weight).max().orElse(0);
            List<Integer> weights = new ArrayList<>();
            long total = 0;
            for (Codex.Candidate candidate : open) {
                int weight = candidate.weight();
                if (weight == 0 && guaranteed(knowledge, candidate.className())) {
                    weight = commonest;
                }
                weights.add(weight);
                total += weight;
            }
            for (String label : family.labels()) {
                if (!labels.contains(label)) {
                    continue;
                }
                List<Odds> odds = new ArrayList<>();
                for (int i = 0; i < open.size(); i++) {
                    if (weights.get(i) > 0) {
                        odds.add(new Odds(open.get(i).name(), (double) weights.get(i) / total));
                    }
                }
                odds.sort((a, b) -> a.probability() != b.probability()
                        ? Double.compare(b.probability(), a.probability()) : a.name().compareTo(b.name()));
                guesses.add(new Guess(label, family.kind(), List.copyOf(odds)));
            }
        }
        return guesses;
    }

    private static boolean identified(Observation observation, ItemKind kind, String name) {
        for (KnownAppearance known : observation.journal().known()) {
            if (known.kind() == kind && known.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean guaranteed(Codex.Knowledge knowledge, String className) {
        return knowledge.guarantees().stream().anyMatch(guarantee -> guarantee.className().equals(className));
    }

    private static int identifiedQuantity(Observation observation, String name) {
        int quantity = 0;
        for (ItemView item : observation.inventory().items()) {
            if (item.name().equals(name)) {
                quantity += item.quantity();
            }
        }
        return quantity;
    }

    /**
     * The floor facts this screen implies. A pool room is a chest on a pedestal with water on three
     * sides and a wall on the fourth: the room fills its inside with water (PoolRoom.java:52), sets
     * its prize as a chest on a pedestal beside the wall opposite its door (:59-89), and adds its
     * spawns to the floor's list (:91). The wall is what tells it from the suspicious chest room's
     * pedestal, which sits at its room's centre (SuspiciousChestRoom.java:61-63), never beside a
     * wall, and which the level's water can surround (RegularPainter.java:366-377).
     */
    static List<Memory.Fact> implied(Observation observation, Codex.Knowledge knowledge) {
        MapSection map = observation.map();
        List<Memory.Fact> facts = new ArrayList<>();
        for (HeapView heap : map.heaps()) {
            if (heap.kind() != HeapKind.CHEST || map.tiles().get(heap.cell()) != Tile.PEDESTAL
                    || !inAPool(map, heap.cell())) {
                continue;
            }
            for (Codex.RoomSpawn spawn : knowledge.rooms()) {
                if (spawn.room().endsWith("." + POOL_ROOM)) {
                    Memory.Fact fact = new Memory.Fact(observation.header().depth(), spawn.name(), "pool room");
                    if (!facts.contains(fact)) {
                        facts.add(fact);
                    }
                }
            }
        }
        return facts;
    }

    /** Water on three of the four sides and a wall on the fourth. */
    private static boolean inAPool(MapSection map, int cell) {
        int x = cell % map.width();
        int y = cell / map.width();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int water = 0;
        int wall = 0;
        for (int[] step : steps) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (nx < 0 || ny < 0 || nx >= map.width() || ny >= map.height()) {
                continue;
            }
            Tile tile = map.tiles().get(nx + ny * map.width());
            if (tile == Tile.WATER) {
                water++;
            } else if (tile == Tile.WALL || tile == Tile.WALL_DECO) {
                wall++;
            }
        }
        return water == 3 && wall == 1;
    }

    /**
     * The sightings after this screen: every enemy in view, fresh, then every earlier sighting not
     * accounted for by an enemy of the same name in view, kept as it was. A sighting is matched by
     * name because the screen gives an enemy no identity; two rats in view and one remembered leave
     * no rat remembered.
     */
    static List<Memory.Seen> sightings(List<Memory.Seen> before, Observation observation, long waits) {
        int depth = observation.header().depth();
        List<Memory.Seen> fresh = new ArrayList<>();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY && !actor.invisible()) {
                fresh.add(new Memory.Seen(actor.name(), depth, actor.cell(), waits));
            }
        }
        List<Memory.Seen> unmatched = new ArrayList<>(fresh);
        List<Memory.Seen> kept = new ArrayList<>();
        for (Memory.Seen old : before) {
            Memory.Seen match = null;
            if (old.depth() == depth) {
                for (Memory.Seen now : unmatched) {
                    if (now.name().equals(old.name())) {
                        match = now;
                        break;
                    }
                }
            }
            if (match != null) {
                unmatched.remove(match);
            } else {
                kept.add(old);
            }
        }
        List<Memory.Seen> all = new ArrayList<>(fresh);
        all.addAll(kept);
        return all.size() > Memory.MONSTERS ? List.copyOf(all.subList(0, Memory.MONSTERS)) : all;
    }
}
