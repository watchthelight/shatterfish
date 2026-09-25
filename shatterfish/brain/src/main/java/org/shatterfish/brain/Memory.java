package org.shatterfish.brain;

import org.shatterfish.api.Belief;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Brain carries from one Input wait to the next, and nothing else (stories 4.1, 4.2).
 *
 * <p>It is written into a {@link Belief}'s bytes, so the harness can hash it for the Run log without
 * knowing its shape, and it holds what the Brain has <em>seen</em>. Absent is any plan: a human may
 * take any turn, and a Brain that remembered its intention would act on an intention the game never
 * carried out (FR-27, FR-28). One thing the Brain did is kept (story 4.7): the <em>kind</em> of the
 * last Action it handed over, {@link #last}, and never as a fact about the game. It is read only to
 * interpret the next screen -- a hero standing where it stood after a Step had the Step refused;
 * after a Search, the spot was searched; after an Attack or a pick-up, nothing was refused -- and
 * when a human took the turn instead, the reading is at worst one cell wrongly marked blocked or one
 * spot wrongly marked searched. Nothing in the Brain assumes the Action was applied.
 *
 * <p>What an unidentified item may be is not here: it is a function of the screen at hand (the
 * appearances in view and the journal's identified list) and the Codex, and is recomputed at every
 * wait ({@link Beliefs}). What is here is what the screen stops showing: a floor fact once the
 * room that implies it is out of view, a guaranteed drop already found, a monster that walked out
 * of sight, the appearances picked up before anyone knew what they were, and where the hero has been
 * seen to stand still (story 4.6).
 *
 * @param waits    the Observations folded in so far: one per {@link Brain#update}, which the Brain's
 *                 driver calls once per Input wait it is asked about
 * @param deepest  the deepest floor any Observation has shown
 * @param facts    what the floors seen imply, each once
 * @param found    the guaranteed drops found, per counter and set of floors
 * @param held     how many of each guaranteed item the inventory last showed identified
 * @param known    the counters whose item the journal listed as identified at the last wait
 * @param labels   how many of each unidentified appearance the inventory last showed
 * @param pending  the rises in an unidentified appearance's quantity, per set of floors, not yet
 *                 attributed to an identity
 * @param monsters the enemies seen, the latest sighting of each, fresh or stale
 * @param at       where the hero stood at the last wait, or {@link Spot#NOWHERE} before the first
 * @param streak   how many waits in a row, before this one, the hero has been seen on {@code at}
 * @param calm     whether the last wait's screen had no Prompt open and no enemy in view: a screen the
 *                 explore Policy acts on
 * @param dwelt    the cells the hero has been seen on at two waits in a row, the first of them calm,
 *                 per floor: the cells a search could have been made from. Seen, not intended:
 *                 whether the hero searched, rested or had its step refused there, it stood there on a
 *                 calm screen, and that is all the screen says (FR-27)
 * @param blocked  the cells, per floor, that the explore Policy's Step pointed at on a screen where the
 *                 hero had already stood still for {@link Explore#STUCK} - 1 waits: a Step the game
 *                 refuses. A function of the screens and the memory, recomputed, not an intention
 * @param last     the kind of the last Action this Brain handed over ({@code "Step"}, {@code "Search"},
 *                 ...), or empty before the first; see the note above
 * @param holds    how many waits in a row the fight Policy has held its cell with an enemy in view
 * @param near     the distance from the hero to the nearest enemy in view on this screen, or -1
 * @param before   the distance from where the hero stands on this screen to the nearest enemy seen on
 *                 the screen before, where it stood then, or -1: less {@code near} than this, and the
 *                 enemies came closer
 * @param flights  how many times the hero has left each floor by the stairs with an enemy in view,
 *                 keyed {@code "depth:branch"} (the set is unused and 0)
 * @param avoid    regions the fight Policy retreated from, which the explore Policy keeps out of until
 *                 they lapse
 */
record Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
              List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
              List<Spot> dwelt, List<Spot> blocked, String last, int holds, int near, int before,
              List<Found> flights, List<Avoid> avoid) {

    /**
     * The meaning of the bytes; bumped when it changes (3: where the hero stood, story 4.6; 4: the
     * last Action's kind, holds, distances, flights and regions avoided, story 4.7).
     */
    static final int VERSION = 4;

    /** The most regions avoided at once; the oldest is forgotten first. */
    static final int AVOIDED = 16;

    /** The most cells remembered as dwelt on, or as blocked; the oldest is forgotten first. */
    static final int DWELT = 256;

    /** The most sightings remembered; the oldest is forgotten first. */
    static final int MONSTERS = 64;

    static final Memory START = new Memory(0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of());

    /**
     * A region of a floor to keep out of: every cell within {@code radius} of {@code cell}, until the
     * wait {@code until}.
     */
    record Avoid(int depth, int branch, int cell, int radius, long until) {

        Avoid {
            require(depth >= 0 && branch >= 0 && cell >= 0 && radius >= 0 && until >= 0, "region");
        }

        /** Whether {@code at} on this floor lies inside the region, on a map {@code width} wide. */
        boolean covers(int depth, int branch, int at, int width) {
            return this.depth == depth && this.branch == branch
                    && Math.max(Math.abs(at % width - cell % width), Math.abs(at / width - cell / width)) <= radius;
        }
    }

    /** This memory with the Action handed over recorded as {@code kind} (story 4.7). */
    Memory handed(String kind) {
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, kind, holds, near, before, flights, avoid);
    }

    /** This memory with {@code region} avoided as well, the oldest forgotten past {@link #AVOIDED}. */
    Memory avoiding(Avoid region) {
        List<Avoid> more = new ArrayList<>(avoid);
        more.add(region);
        while (more.size() > AVOIDED) {
            more.remove(0);
        }
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, last, holds, near, before, flights, more);
    }

    /** The regions still avoided at wait {@code now} on the floor at {@code depth} and {@code branch}. */
    List<Avoid> avoided(int depth, int branch, long now) {
        return avoid.stream().filter(region -> region.depth() == depth && region.branch() == branch
                && region.until() >= now).toList();
    }

    /** A cell on a floor: its depth, its branch and the cell. */
    record Spot(int depth, int branch, int cell) {

        /** Where the hero was before the first wait: nowhere. */
        static final Spot NOWHERE = new Spot(-1, -1, -1);

        Spot {
            require((depth >= 0 && branch >= 0 && cell >= 0) || (depth == -1 && branch == -1 && cell == -1),
                    "a floor and a cell");
        }

        /** Whether this is a cell of the floor at {@code depth} and {@code branch}. */
        boolean on(int depth, int branch) {
            return this.depth == depth && this.branch == branch;
        }
    }

    /** An item a floor is known to hold, and why: "potion of invisibility" on depth 3, from a pool room. */
    record Fact(int depth, String item, String because) {

        Fact {
            require(depth >= 0, "a depth");
        }
    }

    /** How many of something were found in a set of floors (set = depth / floors per set). */
    record Found(String key, int set, int count) {

        Found {
            require(set >= 0 && count >= 0, "a set and a count");
        }
    }

    /** How many of something the inventory last showed. */
    record Held(String key, int quantity) {

        Held {
            require(quantity >= 0, "a quantity");
        }
    }

    /** An enemy's latest sighting: its name, floor and cell, and the wait it was last seen at. */
    record Seen(String name, int depth, int cell, long at) {

        Seen {
            require(depth >= 0 && cell >= 0 && at >= 0, "a floor, a cell and a wait");
        }
    }

    Memory {
        require(waits >= 0 && deepest >= 0, "a memory counts from zero");
        facts = List.copyOf(facts);
        found = List.copyOf(found);
        held = List.copyOf(held);
        known = List.copyOf(known);
        labels = List.copyOf(labels);
        pending = List.copyOf(pending);
        monsters = List.copyOf(monsters);
        require(at != null && streak >= 0, "a position and a streak");
        dwelt = List.copyOf(dwelt);
        blocked = List.copyOf(blocked);
        require(last != null && holds >= 0 && near >= -1 && before >= -1, "last Action, holds and distances");
        flights = List.copyOf(flights);
        avoid = List.copyOf(avoid);
    }

    private static void require(boolean held, String what) {
        if (!held) {
            throw new IllegalArgumentException("a memory holds no negative " + what);
        }
    }

    /** The count in {@code of} for {@code key} in {@code set}. */
    static int count(List<Found> of, String key, int set) {
        return of.stream().filter(one -> one.key().equals(key) && one.set() == set).mapToInt(Found::count).sum();
    }

    /** The quantity in {@code of} for {@code key}. */
    static int quantity(List<Held> of, String key) {
        return of.stream().filter(one -> one.key().equals(key)).mapToInt(Held::quantity).sum();
    }

    Belief belief() {
        Bytes.Writer out = new Bytes.Writer().number(waits).integer(deepest);
        out.integer(facts.size());
        for (Fact fact : facts) {
            out.integer(fact.depth()).text(fact.item()).text(fact.because());
        }
        founds(out, found);
        helds(out, held);
        out.integer(known.size());
        for (String counter : known) {
            out.text(counter);
        }
        helds(out, labels);
        founds(out, pending);
        out.integer(monsters.size());
        for (Seen seen : monsters) {
            out.text(seen.name()).integer(seen.depth()).integer(seen.cell()).number(seen.at());
        }
        out.integer(at.depth()).integer(at.branch()).integer(at.cell()).integer(streak).integer(calm ? 1 : 0);
        spots(out, dwelt);
        spots(out, blocked);
        out.text(last).integer(holds).integer(near).integer(before);
        founds(out, flights);
        out.integer(avoid.size());
        for (Avoid region : avoid) {
            out.integer(region.depth()).integer(region.branch()).integer(region.cell()).integer(region.radius())
                    .number(region.until());
        }
        return new Belief(VERSION, out.bytes());
    }

    private static void founds(Bytes.Writer out, List<Found> founds) {
        out.integer(founds.size());
        for (Found one : founds) {
            out.text(one.key()).integer(one.set()).integer(one.count());
        }
    }

    private static void helds(Bytes.Writer out, List<Held> helds) {
        out.integer(helds.size());
        for (Held one : helds) {
            out.text(one.key()).integer(one.quantity());
        }
    }

    /** The memory a Belief holds; the start for none. Refuses a Belief of another version or shape. */
    static Memory of(Belief belief) {
        if (belief == null) {
            return START;
        }
        if (belief.version() != VERSION) {
            throw new IllegalArgumentException("not a Belief this Brain wrote: " + belief);
        }
        try {
            Bytes.Reader in = new Bytes.Reader(belief.bytes());
            long waits = in.number();
            int deepest = in.integer();
            List<Fact> facts = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                facts.add(new Fact(in.integer(), in.text(), in.text()));
            }
            List<Found> found = founds(in);
            List<Held> held = helds(in);
            List<String> known = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                known.add(in.text());
            }
            List<Held> labels = helds(in);
            List<Found> pending = founds(in);
            List<Seen> monsters = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                monsters.add(new Seen(in.text(), in.integer(), in.integer(), in.number()));
            }
            Spot at = new Spot(in.integer(), in.integer(), in.integer());
            int streak = in.integer();
            int calm = in.integer();
            if (calm != 0 && calm != 1) {
                throw new IllegalArgumentException("calm is 0 or 1: " + calm);
            }
            List<Spot> dwelt = spots(in);
            List<Spot> blocked = spots(in);
            String last = in.text();
            int holds = in.integer();
            int near = in.integer();
            int before = in.integer();
            List<Found> flights = founds(in);
            List<Avoid> avoid = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                avoid.add(new Avoid(in.integer(), in.integer(), in.integer(), in.integer(), in.number()));
            }
            in.end();
            return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak,
                    calm == 1, dwelt, blocked, last, holds, near, before, flights, avoid);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("not a Belief this Brain wrote: " + belief + ": " + malformed.getMessage());
        }
    }

    private static List<Found> founds(Bytes.Reader in) {
        List<Found> founds = new ArrayList<>();
        for (int i = count(in); i > 0; i--) {
            founds.add(new Found(in.text(), in.integer(), in.integer()));
        }
        return founds;
    }

    private static List<Held> helds(Bytes.Reader in) {
        List<Held> helds = new ArrayList<>();
        for (int i = count(in); i > 0; i--) {
            helds.add(new Held(in.text(), in.integer()));
        }
        return helds;
    }

    private static void spots(Bytes.Writer out, List<Spot> spots) {
        out.integer(spots.size());
        for (Spot spot : spots) {
            out.integer(spot.depth()).integer(spot.branch()).integer(spot.cell());
        }
    }

    private static List<Spot> spots(Bytes.Reader in) {
        List<Spot> spots = new ArrayList<>();
        for (int i = count(in); i > 0; i--) {
            spots.add(new Spot(in.integer(), in.integer(), in.integer()));
        }
        return spots;
    }

    /** {@code spots} with {@code spot} added once, the oldest forgotten past {@link #DWELT}. */
    static List<Spot> with(List<Spot> spots, Spot spot) {
        if (spots.contains(spot)) {
            return spots;
        }
        List<Spot> more = new ArrayList<>(spots);
        more.add(spot);
        while (more.size() > DWELT) {
            more.remove(0);
        }
        return more;
    }

    private static int count(Bytes.Reader in) {
        int count = in.integer();
        if (count < 0) {
            throw new IllegalArgumentException("a negative count: " + count);
        }
        return count;
    }
}
