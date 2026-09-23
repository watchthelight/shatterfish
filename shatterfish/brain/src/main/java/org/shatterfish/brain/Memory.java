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
 * of sight.
 *
 * @param waits    the Observations folded in so far: one per {@link Brain#update}, which the Brain's
 *                 driver calls once per Input wait it is asked about
 * @param deepest  the deepest floor any Observation has shown
 * @param facts    what the floors seen imply, each once
 * @param found    the guaranteed drops found, per counter and set of floors
 * @param held     how many of each guaranteed item the inventory last showed identified
 * @param monsters the enemies seen, the latest sighting of each, fresh or stale
 */
record Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<Seen> monsters) {

    /** The meaning of the bytes; bumped when it changes. */
    static final int VERSION = 2;

    /** The most sightings remembered; the oldest is forgotten first. */
    static final int MONSTERS = 64;

    static final Memory START = new Memory(0, 0, List.of(), List.of(), List.of(), List.of());

    /** An item a floor is known to hold, and why: "potion of invisibility" on depth 3, from a pool room. */
    record Fact(int depth, String item, String because) {
    }

    /** How many of a guaranteed drop were found in a set of floors (set = depth / floors per set). */
    record Found(String counter, int set, int count) {
    }

    /** How many identified items of a guaranteed drop the inventory last showed. */
    record Held(String counter, int quantity) {
    }

    /** An enemy's latest sighting: its name, floor and cell, and the wait it was last seen at. */
    record Seen(String name, int depth, int cell, long at) {
    }

    Memory {
        if (waits < 0 || deepest < 0) {
            throw new IllegalArgumentException("a memory counts from zero: " + waits + ", " + deepest);
        }
        facts = List.copyOf(facts);
        found = List.copyOf(found);
        held = List.copyOf(held);
        monsters = List.copyOf(monsters);
    }

    /** The count found for {@code counter} in {@code set}. */
    int found(String counter, int set) {
        return found.stream().filter(one -> one.counter().equals(counter) && one.set() == set)
                .mapToInt(Found::count).sum();
    }

    /** The identified quantity of {@code counter}'s item last held. */
    int held(String counter) {
        return held.stream().filter(one -> one.counter().equals(counter)).mapToInt(Held::quantity).sum();
    }

    Belief belief() {
        Bytes.Writer out = new Bytes.Writer().number(waits).integer(deepest);
        out.integer(facts.size());
        for (Fact fact : facts) {
            out.integer(fact.depth()).text(fact.item()).text(fact.because());
        }
        out.integer(found.size());
        for (Found one : found) {
            out.text(one.counter()).integer(one.set()).integer(one.count());
        }
        out.integer(held.size());
        for (Held one : held) {
            out.text(one.counter()).integer(one.quantity());
        }
        out.integer(monsters.size());
        for (Seen seen : monsters) {
            out.text(seen.name()).integer(seen.depth()).integer(seen.cell()).number(seen.at());
        }
        return new Belief(VERSION, out.bytes());
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
            List<Found> found = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                found.add(new Found(in.text(), in.integer(), in.integer()));
            }
            List<Held> held = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                held.add(new Held(in.text(), in.integer()));
            }
            List<Seen> monsters = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                monsters.add(new Seen(in.text(), in.integer(), in.integer(), in.number()));
            }
            in.end();
            return new Memory(waits, deepest, facts, found, held, monsters);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("not a Belief this Brain wrote: " + belief + ": " + malformed.getMessage());
        }
    }

    private static int count(Bytes.Reader in) {
        int count = in.integer();
        if (count < 0) {
            throw new IllegalArgumentException("a negative count: " + count);
        }
        return count;
    }
}
