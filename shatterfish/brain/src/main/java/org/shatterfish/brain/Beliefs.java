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

    /** The most identities the exact computation enumerates by subsets: more than any family has. */
    private static final int EXACT = 16;

    /** The kinds of Action the fold reads {@link Memory#last()} for. */
    static final String STEP = "Step";
    static final String SEARCH = "Search";
    static final String WAIT = "Wait";
    static final String ASCEND = "Ascend";
    static final String DESCEND = "Descend";

    /** The kind of an Action as the Memory keeps it: its record's name. */
    static String kind(org.shatterfish.api.Action action) {
        if (action == null) {
            return "";
        }
        String shown = action.toString();
        int bracket = shown.indexOf('[');
        return bracket < 0 ? shown : shown.substring(0, bracket);
    }

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

        // The unidentified appearances of the families a guarantee places into, as held now, and
        // every rise in them, set aside until an identity claims it.
        List<Memory.Held> labels = new ArrayList<>();
        List<Memory.Found> pending = new ArrayList<>(memory.pending());
        for (Codex.Identities family : knowledge.families()) {
            if (knowledge.guarantees().stream().noneMatch(guarantee -> places(family, guarantee))) {
                continue;
            }
            for (String label : family.labels()) {
                int now = heldQuantity(observation, label);
                int before = Memory.quantity(memory.labels(), label);
                if (now > before) {
                    add(pending, label, depth / floorsPerSet(knowledge), now - before);
                }
                if (now > 0) {
                    labels.add(new Memory.Held(label, now));
                }
            }
        }

        List<Memory.Found> found = new ArrayList<>(memory.found());
        List<Memory.Held> held = new ArrayList<>();
        List<String> known = new ArrayList<>();
        for (Codex.Guarantee guarantee : knowledge.guarantees()) {
            int now = heldQuantity(observation, guarantee.name());
            int before = Memory.quantity(memory.held(), guarantee.counter());
            boolean identified = identifiedAnywhere(observation, guarantee.name());
            String claimed = identified && !memory.known().contains(guarantee.counter())
                    ? vanished(memory, labels, pending, knowledge, guarantee) : null;
            if (claimed != null) {
                // The identity was learned at this wait, and exactly one appearance of its family
                // that was held a wait ago is held no more: drunk, read or identified, it is this
                // one. What was picked up under that appearance was this identity, in the sets it
                // was picked up in; the copies still held now wear the identity's name and were
                // counted among those, so their rise is not a find.
                for (Memory.Found rise : List.copyOf(pending)) {
                    if (rise.key().equals(claimed)) {
                        add(found, guarantee.counter(), rise.set(), rise.count());
                        pending.remove(rise);
                    }
                }
            } else if (now > before) {
                // A rise in what the inventory shows identified: found, in the set this floor
                // belongs to. A drop and a pick-up of the same item would count twice; the Brain
                // does not drop items yet.
                add(found, guarantee.counter(), depth / guarantee.floorsPerSet(), now - before);
            }
            if (now > 0) {
                held.add(new Memory.Held(guarantee.counter(), now));
            }
            if (identified) {
                known.add(guarantee.counter());
            }
        }

        // Where the hero stands, and whether it stood here at the last wait too (story 4.6). What a
        // still hero means depends on what the Brain last handed over (story 4.7): after a Step on a
        // calm screen, the Step was refused, and the streak of refusals grows; after a Search on a
        // calm screen, the spot was searched. After anything else -- an attack, a pick-up, a wait --
        // standing still is what the Action does, and it counts for neither.
        int branch = observation.header().branch();
        Memory.Spot here = new Memory.Spot(depth, branch, observation.hero().cell());
        boolean still = here.equals(memory.at());
        List<Memory.Spot> dwelt = still && memory.calm() && memory.last().equals(SEARCH)
                ? Memory.with(memory.dwelt(), here) : memory.dwelt();
        int streak = still && memory.calm() && memory.last().equals(STEP) ? memory.streak() + 1 : 0;
        boolean calm = Explore.calm(observation);
        // The fight Policy's holds, and how near the nearest enemy is now and was a wait ago.
        int holds = !memory.calm() && memory.last().equals(WAIT) && !calm ? memory.holds() + 1 : 0;
        // Both distances are measured from where the hero stands now: to the enemies in view, and to
        // where the enemies seen a wait ago stood then. The hero's own steps change neither side
        // alike, so near < before says the enemies came closer.
        int near = Fight.nearest(observation.map(), observation.hero().cell(), Fight.enemies(observation));
        int before = -1;
        int width = observation.map().width();
        for (Memory.Seen seen : memory.monsters()) {
            if (seen.depth() == depth && seen.at() == memory.waits() && memory.at().on(depth, branch)
                    && seen.cell() < observation.map().tiles().size() && !Fight.PASSIVE.contains(seen.name())) {
                int hero = observation.hero().cell();
                int distance = Math.max(Math.abs(hero % width - seen.cell() % width),
                        Math.abs(hero / width - seen.cell() / width));
                before = before < 0 ? distance : Math.min(before, distance);
            }
        }
        // A floor left by the stairs while an enemy was in view has been fled.
        List<Memory.Found> flights = new ArrayList<>(memory.flights());
        if (!memory.calm() && (memory.last().equals(ASCEND) || memory.last().equals(DESCEND))
                && !memory.at().on(depth, branch) && memory.at().depth() >= 0) {
            add(flights, memory.at().depth() + ":" + memory.at().branch(), 0, 1);
        }
        // On the floor above one fled: a rest counts toward Explore.RESTS, and full health settles
        // every flight so far (set 1 catches up with set 0), so the next flight owes a rest again.
        String below = Explore.below(observation);
        int fled = Memory.count(flights, below, 0);
        if (fled > Memory.count(flights, below, 1)) {
            if (observation.hero().hp() >= observation.hero().ht()) {
                add(flights, below, 1, fled - Memory.count(flights, below, 1));
                flights.removeIf(one -> one.key().equals(below) && one.set() == 2);
            } else if (memory.last().equals("Rest") && memory.at().on(depth, branch)) {
                add(flights, below, 2, 1);
            }
        }
        List<Memory.Avoid> avoid = memory.avoid().stream().filter(region -> region.until() >= waits).toList();
        Memory after = new Memory(waits, Math.max(memory.deepest(), depth), facts, found, held, known, labels, pending,
                sightings(memory.monsters(), observation, waits), here, streak, calm, dwelt, memory.blocked(),
                memory.last(), holds, near, before, flights, avoid, memory.drank());
        // Two Steps refused in a row: explore yields this wait, and the cell its Step points at on this
        // screen is blocked on this floor.
        if (streak == Explore.STUCK - 1 && calm) {
            Integer cell = Explore.stepCell(observation, after);
            if (cell != null) {
                after = new Memory(after.waits(), after.deepest(), facts, found, held, known, labels, pending,
                        after.monsters(), here, streak, calm, dwelt,
                        Memory.with(after.blocked(), new Memory.Spot(depth, branch, cell)), after.last(), holds, near,
                        before, flights, avoid, memory.drank());
            }
        }
        return after;
    }

    /** What the Brain believes, given the memory after {@link #fold} and the same observation. */
    static Beliefs view(Memory memory, Observation observation, Codex.Knowledge knowledge) {
        int depth = observation.header().depth();
        List<FloorItem> floor = memory.facts().stream().filter(fact -> fact.depth() == depth)
                .map(fact -> new FloorItem(fact.item(), fact.because())).toList();
        List<Chapter> chapters = new ArrayList<>();
        for (Codex.Guarantee guarantee : knowledge.guarantees()) {
            int set = depth / guarantee.floorsPerSet();
            int found = Memory.count(memory.found(), guarantee.counter(), set);
            chapters.add(new Chapter(guarantee.counter(), guarantee.name(), set, found,
                    Math.max(0, guarantee.perSet() - found)));
        }
        List<Monster> monsters = memory.monsters().stream().filter(seen -> seen.depth() == depth)
                .map(seen -> new Monster(seen.name(), seen.cell(), seen.at(), seen.at() < memory.waits())).toList();
        return new Beliefs(identities(observation, knowledge), floor, chapters, monsters);
    }

    /**
     * What each unidentified appearance in view may be.
     *
     * <p>The appearances are shuffled over the identities once per Run, so before anything is seen
     * every unidentified appearance is as likely to be any identity not yet identified as any
     * other. What moves the odds is that an appearance was <em>found</em>: items are drawn by deck
     * weight, so an appearance in view is likelier to be a heavily weighted identity. Each
     * appearance in view is taken as one draw, and distinct appearances are distinct identities, so
     * the joint weight of an assignment is the product of the weights it assigns, and each
     * appearance's odds are its marginal over every assignment -- computed exactly, by subsets of
     * the identities. With one appearance in view that is its deck weight over the total; with
     * every remaining appearance in view it is uniform, because every assignment has the same
     * product.
     *
     * <p>An identity the decks never draw but a guarantee places (the potion of strength, the
     * scroll of upgrade) is weighted as the heaviest identity in its family's deck: an assumption,
     * not a Codex fact, and the one number here a later story should measure.
     */
    static List<Guess> identities(Observation observation, Codex.Knowledge knowledge) {
        Set<String> inView = new LinkedHashSet<>();
        for (ItemView item : observation.inventory().items()) {
            inView.add(item.name());
        }
        for (HeapView heap : observation.map().heaps()) {
            if (!heap.item().isEmpty()) {
                inView.add(untitled(heap.item()));
            }
        }
        List<Guess> guesses = new ArrayList<>();
        for (Codex.Identities family : knowledge.families()) {
            int heaviest = family.candidates().stream().mapToInt(Codex.Candidate::weight).max().orElse(0);
            List<String> names = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            for (Codex.Candidate candidate : family.candidates()) {
                int weight = candidate.weight();
                if (weight == 0 && knowledge.guarantees().stream()
                        .anyMatch(guarantee -> guarantee.className().equals(candidate.className()))) {
                    weight = heaviest;
                }
                if (weight > 0 && !identified(observation, family.kind(), candidate.name())) {
                    names.add(candidate.name());
                    weights.add(weight);
                }
            }
            List<String> labels = family.labels().stream().filter(inView::contains).toList();
            if (labels.isEmpty() || names.isEmpty()) {
                continue;
            }
            double[] marginal = marginal(weights, labels.size());
            for (String label : labels) {
                List<Odds> odds = new ArrayList<>();
                for (int x = 0; x < names.size(); x++) {
                    if (marginal[x] > 0) {
                        odds.add(new Odds(names.get(x), marginal[x]));
                    }
                }
                odds.sort((a, b) -> a.probability() != b.probability()
                        ? Double.compare(b.probability(), a.probability()) : a.name().compareTo(b.name()));
                guesses.add(new Guess(label, family.kind(), List.copyOf(odds)));
            }
        }
        return guesses;
    }

    /**
     * One of {@code k} distinct appearances' odds over identities of the given weights, when the
     * joint weight of an assignment is the product of the weights it assigns. The appearances are
     * interchangeable, so each has the same odds. Beyond {@link #EXACT} identities, or with more
     * appearances than identities (which a consistent screen never shows), it is the weight over
     * the total.
     */
    static double[] marginal(List<Integer> weights, int k) {
        int n = weights.size();
        double[] row = new double[n];
        if (n > EXACT || k > n) {
            double total = weights.stream().mapToDouble(Integer::doubleValue).sum();
            for (int x = 0; x < n; x++) {
                row[x] = weights.get(x) / total;
            }
            return row;
        }
        // f[S]: the summed weight of every way to give the other k-1 appearances exactly the
        // identities in S. The odds of x are then w_x times the sum of f over the sets without x.
        double[] f = new double[1 << n];
        f[0] = 1;
        for (int placed = 0; placed < k - 1; placed++) {
            double[] next = new double[1 << n];
            for (int s = 0; s < f.length; s++) {
                if (f[s] == 0) {
                    continue;
                }
                for (int x = 0; x < n; x++) {
                    if ((s & (1 << x)) == 0) {
                        next[s | (1 << x)] += f[s] * weights.get(x);
                    }
                }
            }
            f = next;
        }
        double total = 0;
        for (int x = 0; x < n; x++) {
            double sum = 0;
            for (int s = 0; s < f.length; s++) {
                if ((s & (1 << x)) == 0) {
                    sum += f[s];
                }
            }
            row[x] = weights.get(x) * sum;
            total += row[x];
        }
        for (int x = 0; x < n; x++) {
            row[x] /= total;
        }
        return row;
    }

    /**
     * A heap's title as its item's name. A heap shows its top item's title (Observer.java:302),
     * which is the name with " x" and the quantity for a stack and a signed level for a visible
     * one (Item.java:485-497).
     */
    static String untitled(String title) {
        String name = title;
        for (int round = 0; round < 2; round++) {
            int space = name.lastIndexOf(' ');
            if (space <= 0) {
                break;
            }
            String tail = name.substring(space + 1);
            if (tail.length() > 1 && (tail.charAt(0) == 'x' || tail.charAt(0) == '+' || tail.charAt(0) == '-')
                    && tail.substring(1).chars().allMatch(c -> c >= '0' && c <= '9')) {
                name = name.substring(0, space);
            } else {
                break;
            }
        }
        return name;
    }

    private static boolean identified(Observation observation, ItemKind kind, String name) {
        for (KnownAppearance known : observation.journal().known()) {
            if (known.kind() == kind && known.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean identifiedAnywhere(Observation observation, String name) {
        return observation.journal().known().stream().anyMatch(known -> known.name().equals(name));
    }

    private static boolean places(Codex.Identities family, Codex.Guarantee guarantee) {
        return family.candidates().stream().anyMatch(candidate -> candidate.className().equals(guarantee.className()));
    }

    private static int floorsPerSet(Codex.Knowledge knowledge) {
        return knowledge.guarantees().get(0).floorsPerSet();
    }

    /** Adds {@code count} under {@code key} in {@code set}. */
    private static void add(List<Memory.Found> into, String key, int set, int count) {
        int already = Memory.count(into, key, set);
        into.removeIf(one -> one.key().equals(key) && one.set() == set);
        into.add(new Memory.Found(key, set, already + count));
    }

    /**
     * The one appearance of {@code guarantee}'s family that was held at the last wait, is held no
     * more, and has finds set aside -- or null when there is not exactly one.
     */
    private static String vanished(Memory memory, List<Memory.Held> labelsNow, List<Memory.Found> pending,
                                   Codex.Knowledge knowledge, Codex.Guarantee guarantee) {
        String only = null;
        for (Codex.Identities family : knowledge.families()) {
            if (!places(family, guarantee)) {
                continue;
            }
            for (String label : family.labels()) {
                boolean was = Memory.quantity(memory.labels(), label) > 0;
                boolean is = Memory.quantity(labelsNow, label) > 0;
                boolean set = pending.stream().anyMatch(rise -> rise.key().equals(label));
                if (was && !is && set) {
                    if (only != null) {
                        return null;
                    }
                    only = label;
                }
            }
        }
        return only;
    }

    private static int heldQuantity(Observation observation, String name) {
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
     * name, the nearest first, because the screen gives an enemy no identity; two rats in view and
     * one remembered leave no rat remembered. An enemy killed out of the Brain's reckoning stays remembered until a
     * same-named enemy is seen or the oldest sightings are forgotten; reading kills from the log is
     * for the fight Policy (docs/ideas.md).
     */
    static List<Memory.Seen> sightings(List<Memory.Seen> before, Observation observation, long waits) {
        int depth = observation.header().depth();
        List<Memory.Seen> fresh = new ArrayList<>();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY && !actor.invisible()) {
                fresh.add(new Memory.Seen(actor.name(), depth, actor.cell(), waits));
            }
        }
        // Each enemy in view accounts for the nearest remembered sighting of its name on this floor.
        List<Memory.Seen> kept = new ArrayList<>(before);
        int width = observation.map().width();
        for (Memory.Seen now : fresh) {
            Memory.Seen nearest = null;
            int best = Integer.MAX_VALUE;
            for (Memory.Seen old : kept) {
                if (old.depth() == depth && old.name().equals(now.name())) {
                    int distance = Math.max(Math.abs(old.cell() % width - now.cell() % width),
                            Math.abs(old.cell() / width - now.cell() / width));
                    if (distance < best) {
                        best = distance;
                        nearest = old;
                    }
                }
            }
            if (nearest != null) {
                kept.remove(nearest);
            }
        }
        List<Memory.Seen> all = new ArrayList<>(fresh);
        all.addAll(kept);
        return all.size() > Memory.MONSTERS ? List.copyOf(all.subList(0, Memory.MONSTERS)) : all;
    }
}
