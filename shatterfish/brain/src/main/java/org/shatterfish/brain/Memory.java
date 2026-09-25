package org.shatterfish.brain;

import org.shatterfish.api.Belief;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Brain carries from one Input wait to the next, and nothing else (stories 4.1, 4.2).
 *
 * <p>It is written into a {@link Belief}'s bytes, so the harness can hash it for the Run log without
 * knowing its shape, and it holds only what the Brain has <em>seen</em>. Deliberately absent is
 * anything the Brain <em>did</em> -- its last Action, its plan -- because a human may take any
 * turn, and a Brain that remembered its intention would act on an intention the game never carried
 * out (FR-27, FR-28).
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
 * @param underfoot the title of the plain heap the hero stood on at the last wait, or empty (story 4.8)
 * @param refused  the heaps, per floor, the hero was seen standing on at two waits in a row, the first
 *                 of them calm, under the same title: an item the game would not let it take. Seen,
 *                 not intended, as {@code dwelt} is: on a calm screen with a takeable heap underfoot
 *                 the pick-up Policy takes it, and a heap still there under the same title a wait
 *                 later was refused (a dewdrop with nothing to fill, a full pack; Hero.java:1162-1188)
 */
record Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
              List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
              List<Spot> dwelt, List<Spot> blocked, String underfoot, List<Refused> refused) {

    /**
     * The meaning of the bytes; bumped when it changes (3: where the hero stood, story 4.6; 4: the
     * heap underfoot and the heaps refused, story 4.8).
     */
    static final int VERSION = 4;

    /** The most cells remembered as dwelt on, or as blocked; the oldest is forgotten first. */
    static final int DWELT = 256;

    /** The most sightings remembered; the oldest is forgotten first. */
    static final int MONSTERS = 64;

    static final Memory START = new Memory(0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), Spot.NOWHERE, 0, false, List.of(), List.of(), "", List.of());

    /** A memory with no heap underfoot and none refused: story 4.6's shape, for its callers. */
    Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
           List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
           List<Spot> dwelt, List<Spot> blocked) {
        this(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
                "", List.of());
    }

    /** A heap the game would not let the hero take: its floor, its cell and the title it showed. */
    record Refused(int depth, int branch, int cell, String title) {

        Refused {
            require(depth >= 0 && branch >= 0 && cell >= 0, "a floor and a cell");
        }
    }

    /** Whether the heap titled {@code title} on {@code cell} of this floor was refused. */
    boolean refuses(int depth, int branch, int cell, String title) {
        return refused.contains(new Refused(depth, branch, cell, title));
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
        require(underfoot != null, "a heap underfoot");
        refused = List.copyOf(refused);
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
        out.text(underfoot);
        out.integer(refused.size());
        for (Refused one : refused) {
            out.integer(one.depth()).integer(one.branch()).integer(one.cell()).text(one.title());
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
            String underfoot = in.text();
            List<Refused> refused = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                refused.add(new Refused(in.integer(), in.integer(), in.integer(), in.text()));
            }
            in.end();
            return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak,
                    calm == 1, dwelt, blocked, underfoot, refused);
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
