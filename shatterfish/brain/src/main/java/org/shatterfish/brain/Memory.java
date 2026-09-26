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
 * carried out (FR-27, FR-28). Two things the Brain did are kept. One (story 4.7) is the <em>kind</em>
 * of the last Action it handed over, {@link #last}, and never as a fact about the game. It is read only
 * to interpret the next screen -- a hero standing where it stood after a Step had the Step refused;
 * after a Search, the spot was searched; after an Attack or a pick-up, nothing was refused -- and
 * when a human took the turn instead, the reading is at worst one cell wrongly marked blocked or one
 * spot wrongly marked searched. Nothing in the Brain assumes the Action was applied. The other is the
 * wait of the last drink the heal Policy handed over, {@link #drank} (story 4.9), read only to wait
 * before another; when the drink was not taken, the Brain waits a few waits it did not need to. A
 * third (story 4.12) is how many rests the descend Policy handed over on this floor, {@link #rests},
 * read only to stop resting before going down after a bound; a rest a human did not take shortens it.
 *
 * <p>What an unidentified item may be is not here: it is a function of the screen at hand (the
 * appearances in view and the journal's identified list) and the Codex, and is recomputed at every
 * wait ({@link Beliefs}). What is here is what the screen stops showing: a floor fact once the
 * room that implies it is out of view, a guaranteed drop already found, a monster that walked out
 * of sight, the appearances picked up before anyone knew what they were, and where the hero has been
 * seen to stand still (story 4.6), and the heaps the game would not let the hero take (story 4.8).
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
 * @param underfoot the title of the plain heap the hero stood on at the last wait, or empty (story 4.8)
 * @param refused  the heaps, per floor, the game would not let the hero take: the pick-up Policy's
 *                 target at the last wait was the heap underfoot on a calm screen, and at this wait
 *                 the hero stands there still, the heap shows the same title and the pack is
 *                 unchanged (a dewdrop with nothing to fill, a full pack; Hero.java:1162-1188)
 * @param pack     the pack the last wait's screen showed, as far as a pick-up changes it: the items
 *                 listed, their total quantity and the gold (story 4.8)
 * @param aim      where the pick-up Policy's plan on the last wait's screen went: the heap it targeted
 *                 and the cell of the Step it would take, {@link Aim#NONE} for none (story 4.8). What
 *                 the Policy computes from the screen and the memory, not what was handed over
 * @param drank    the wait at which the heal Policy last handed over a drink, or -1 (story 4.9): the heal
 *                 lands over several turns with no buff icon, and the floating heal text the game shows is
 *                 not in the Observation
 * @param trial    the test the test-item Policy handed over at the last wait, {@link Trial#NONE} for
 *                 none (story 4.10): read only to interpret this screen, as {@code last} is
 * @param balked   the unidentified appearances, per floor, whose test the game did not carry out: the
 *                 trial's appearance still held in the same quantity after a drink or a read (blind,
 *                 immune to magic, a cursed spellbook's charge; Scroll.java:172-192), or the hero still
 *                 where it stood after a Step toward the testing cell, or walked more than
 *                 {@link TestItem#WALKS} Steps in a row toward testing cells
 * @param walking  how many Steps in a row the test-item Policy has handed over toward a testing cell
 *                 and seen carried out (story 4.10)
 * @param tested   the wait at which the test-item Policy last handed over a drink or a read, or -1
 *                 (story 4.10): the escape from its own fire or gas is bounded from there
 * @param clouds   the cells, per floor, seen showing fire or a harmful gas, until a later sight of the
 *                 cell clear or {@link #CLOUD_WAITS} waits pass (story 4.10); no Policy walks onto
 *                 them. None is kept on a shut door's cell, which is solid and holds no gas
 * @param refuge   the cell beyond the shut door the last test was credited with, or -1 (story 4.10):
 *                 the cell the escape from its own gas makes for
 * @param arrived  the wait at which the hero was first seen on the floor it stands on now (story 4.12):
 *                 how long it has stayed there is {@code waits - arrived}
 * @param rests    how many rests the descend Policy has handed over on this floor, before going down
 *                 (story 4.12); 0 on arrival
 * @param stepped  the cell of the Step this Brain handed over at the last wait, or -1 when it handed over
 *                 anything else (story 4.12): a hero still where it stood after it had that Step refused,
 *                 and that cell, whatever Policy chose the Step and whatever the screen showed, is the one
 *                 blocked
 */
record Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
              List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
              List<Spot> dwelt, List<Spot> blocked, String last, int holds, int near, int before,
              List<Found> flights, List<Avoid> avoid, String underfoot, List<Refused> refused, Pack pack, Aim aim,
              long drank, Trial trial, List<Balk> balked, int walking, long tested, List<Cloud> clouds,
              int refuge, long arrived, int rests, int stepped) {

    /**
     * The meaning of the bytes; bumped when it changes (3: where the hero stood, story 4.6; 4: the
     * last Action's kind, holds, distances, flights and regions avoided, story 4.7; 5: the heap
     * underfoot, the heaps refused, the pack and the pick-up Policy's aim, story 4.8; 6: the last
     * drink, story 4.9; 7: the test handed over, the appearances a floor balked at, the walk toward a
     * testing cell, the wait of the last test, the cells seen clouded and the refuge, story 4.10; 8:
     * the wait the hero came to this floor, the rests before going down and the Step handed over, story
     * 4.12).
     *
     * <p>Version 8 carries, in this order: waits, deepest, facts, found, held, known, labels,
     * pending, monsters, at, streak, calm, dwelt, blocked, last, holds, near, before, flights, avoid,
     * underfoot, refused, pack, aim, drank, trial, balked, walking, tested, clouds, refuge, arrived,
     * rests and stepped.
     */
    static final int VERSION = 8;

    /**
     * How many waits a cell seen showing fire or gas is kept out of at most, unseen (story 4.10): the
     * life of a potion's cloud. A potion seeds 1000 units of gas (PotionOfToxicGas.java:49,
     * PotionOfParalyticGas.java:49), and every clouded cell loses at least one a turn
     * (Blob.java:186), so a cloud spread over ten cells or more is gone within a hundred turns. A
     * cell seen clear before then is cleared at once.
     */
    static final int CLOUD_WAITS = 100;

    /** The most cells remembered clouded; the oldest is forgotten first. */
    static final int CLOUDS = 256;

    /** The most regions avoided at once; the oldest is forgotten first. */
    static final int AVOIDED = 16;

    /** The most cells remembered as dwelt on, or as blocked; the oldest is forgotten first. */
    static final int DWELT = 256;

    /** The most sightings remembered; the oldest is forgotten first. */
    static final int MONSTERS = 64;

    static final Memory START = new Memory(0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
            Pack.NONE, Aim.NONE, -1, Trial.NONE, List.of(), 0, -1, List.of(), -1, 0, 0, -1);

    /** Story 4.10's shape: arrived at the start, no rest before going down, no Step handed over. */
    Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
           List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
           List<Spot> dwelt, List<Spot> blocked, String last, int holds, int near, int before, List<Found> flights,
           List<Avoid> avoid, String underfoot, List<Refused> refused, Pack pack, Aim aim, long drank, Trial trial,
           List<Balk> balked, int walking, long tested, List<Cloud> clouds, int refuge) {
        this(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
                last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank, trial, balked, walking,
                tested, clouds, refuge, 0, 0, -1);
    }

    /** Story 4.9's shape: no test handed over, nothing balked at, no clouds. */
    Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
           List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
           List<Spot> dwelt, List<Spot> blocked, String last, int holds, int near, int before, List<Found> flights,
           List<Avoid> avoid, String underfoot, List<Refused> refused, Pack pack, Aim aim, long drank) {
        this(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
                last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank, Trial.NONE, List.of(),
                0, -1, List.of(), -1);
    }

    /** Story 4.8's shape: no drink handed over, and story 4.9's shape's defaults after it. */
    Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
           List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
           List<Spot> dwelt, List<Spot> blocked, String last, int holds, int near, int before, List<Found> flights,
           List<Avoid> avoid, String underfoot, List<Refused> refused, Pack pack, Aim aim) {
        this(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
                last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, -1);
    }

    /**
     * A test the test-item Policy handed over (story 4.10): the appearance it was for, how many of it
     * the pack held then, and the cell of the Step toward the testing cell, or -1 for the drink or
     * read itself.
     */
    record Trial(String label, int quantity, int step) {

        /** No test handed over. */
        static final Trial NONE = new Trial("", 0, -1);

        Trial {
            require(label != null && quantity >= 0 && step >= -1, "trial");
        }
    }

    /** A cell of a floor seen showing fire or a harmful gas, kept out of until the wait {@code until} (story 4.10). */
    record Cloud(int depth, int branch, int cell, long until) {

        Cloud {
            require(depth >= 0 && branch >= 0 && cell >= 0 && until >= 0, "cloud");
        }
    }

    /** Whether {@code cell} of this floor is remembered clouded at wait {@code now}. */
    boolean clouded(int depth, int branch, int cell, long now) {
        for (Cloud cloud : clouds) {
            if (cloud.cell() == cell && cloud.depth() == depth && cloud.branch() == branch && cloud.until() >= now) {
                return true;
            }
        }
        return false;
    }

    /**
     * An unidentified appearance whose test the game did not carry out on a floor (story 4.10): the
     * drink or read itself ({@code walk} false), which stops testing it on the floor, or the walk to a
     * better testing cell ({@code walk} true), which stops only the walking.
     */
    record Balk(int depth, int branch, String label, boolean walk) {

        Balk {
            require(depth >= 0 && branch >= 0 && label != null, "a floor");
        }
    }

    /** Whether testing the appearance {@code label} was balked at on this floor. */
    boolean balks(int depth, int branch, String label) {
        return balked.contains(new Balk(depth, branch, label, false));
    }

    /** Whether walking to a testing cell for the appearance {@code label} was balked at on this floor. */
    boolean balksWalking(int depth, int branch, String label) {
        return balked.contains(new Balk(depth, branch, label, true));
    }

    /** This memory with {@code trial} as the test handed over (story 4.10), and no refuge. */
    Memory trying(Trial trial) {
        return trying(trial, -1);
    }

    /**
     * This memory with {@code trial} as the test handed over (story 4.10). A drink or a read records
     * its wait and {@code refuge}, the cell beyond the door its testing cell was credited with (-1 for
     * none); a Step toward a testing cell leaves both as they were.
     */
    Memory trying(Trial trial, int refuge) {
        boolean test = !trial.label().isEmpty() && trial.step() < 0;
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank, trial,
                balked, walking, test ? waits : tested, clouds, test ? refuge : this.refuge, arrived, rests, stepped);
    }

    /** Story 4.7's shape: nothing underfoot, nothing refused, no pack seen, no aim. */
    Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
           List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
           List<Spot> dwelt, List<Spot> blocked, String last, int holds, int near, int before, List<Found> flights,
           List<Avoid> avoid) {
        this(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
                last, holds, near, before, flights, avoid, "", List.of(), Pack.NONE, Aim.NONE, -1);
    }

    /** Story 4.6's shape: nothing handed over, no fight, and story 4.7's shape's defaults after it. */
    Memory(long waits, int deepest, List<Fact> facts, List<Found> found, List<Held> held, List<String> known,
           List<Held> labels, List<Found> pending, List<Seen> monsters, Spot at, int streak, boolean calm,
           List<Spot> dwelt, List<Spot> blocked) {
        this(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
                "", 0, -1, -1, List.of(), List.of());
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

    /** The pack as far as a pick-up changes it: the items listed, their total quantity, the gold. */
    record Pack(int items, int quantity, int gold) {

        /** No screen seen yet. */
        static final Pack NONE = new Pack(-1, -1, -1);
    }

    /**
     * Where the pick-up Policy's plan went on a screen: the cell of the heap it targeted and the cell
     * of the Step it would take toward it, -1 for none (the hero on the heap takes no Step).
     */
    record Aim(int target, int step) {

        /** No plan. */
        static final Aim NONE = new Aim(-1, -1);

        Aim {
            require(target >= -1 && step >= -1, "a cell");
        }
    }

    /** This memory with {@code aim} as where the pick-up Policy's plan went (story 4.8). */
    Memory aiming(Aim aim) {
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank, trial,
                balked, walking, tested, clouds, refuge, arrived, rests, stepped);
    }

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
    Memory handed(String kind, int stepped) {
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, kind, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank, trial,
                balked, walking, tested, clouds, refuge, arrived, rests, stepped);
    }

    /** This memory with a drink handed over at this wait (story 4.9). */
    Memory drinking() {
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, waits, trial,
                balked, walking, tested, clouds, refuge, arrived, rests, stepped);
    }

    /** This memory with one more rest handed over by the descend Policy on this floor (story 4.12). */
    Memory resting() {
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank, trial,
                balked, walking, tested, clouds, refuge, arrived, rests + 1, stepped);
    }

    /** This memory with {@code region} avoided as well, the oldest forgotten past {@link #AVOIDED}. */
    Memory avoiding(Avoid region) {
        List<Avoid> more = new ArrayList<>(avoid);
        more.add(region);
        while (more.size() > AVOIDED) {
            more.remove(0);
        }
        return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm,
                dwelt, blocked, last, holds, near, before, flights, more, underfoot, refused, pack, aim, drank, trial,
                balked, walking, tested, clouds, refuge, arrived, rests, stepped);
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
        require(underfoot != null && pack != null && aim != null, "a heap underfoot, a pack and an aim");
        refused = List.copyOf(refused);
        require(drank >= -1 && drank <= waits, "last drink");
        require(trial != null && walking >= 0 && tested >= -1 && refuge >= -1, "trial, walk, test and refuge");
        require(arrived >= 0 && arrived <= waits && rests >= 0 && stepped >= -1, "arrival, rests and Step");
        clouds = List.copyOf(clouds);
        balked = List.copyOf(balked);
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
        out.text(underfoot);
        out.integer(refused.size());
        for (Refused one : refused) {
            out.integer(one.depth()).integer(one.branch()).integer(one.cell()).text(one.title());
        }
        out.integer(pack.items()).integer(pack.quantity()).integer(pack.gold());
        out.integer(aim.target()).integer(aim.step());
        out.number(drank);
        out.text(trial.label()).integer(trial.quantity()).integer(trial.step());
        out.integer(balked.size());
        for (Balk balk : balked) {
            out.integer(balk.depth()).integer(balk.branch()).text(balk.label()).integer(balk.walk() ? 1 : 0);
        }
        out.integer(walking).number(tested);
        out.integer(clouds.size());
        for (Cloud cloud : clouds) {
            out.integer(cloud.depth()).integer(cloud.branch()).integer(cloud.cell()).number(cloud.until());
        }
        out.integer(refuge);
        out.number(arrived).integer(rests).integer(stepped);
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
            String underfoot = in.text();
            List<Refused> refused = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                refused.add(new Refused(in.integer(), in.integer(), in.integer(), in.text()));
            }
            Pack pack = new Pack(in.integer(), in.integer(), in.integer());
            Aim aim = new Aim(in.integer(), in.integer());
            long drank = in.number();
            Trial trial = new Trial(in.text(), in.integer(), in.integer());
            List<Balk> balked = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                int depth = in.integer();
                int branch = in.integer();
                String label = in.text();
                int walk = in.integer();
                if (walk != 0 && walk != 1) {
                    throw new IllegalArgumentException("a balk's walk is 0 or 1: " + walk);
                }
                balked.add(new Balk(depth, branch, label, walk == 1));
            }
            int walking = in.integer();
            long tested = in.number();
            List<Cloud> clouds = new ArrayList<>();
            for (int i = count(in); i > 0; i--) {
                clouds.add(new Cloud(in.integer(), in.integer(), in.integer(), in.number()));
            }
            int refuge = in.integer();
            long arrived = in.number();
            int rests = in.integer();
            int stepped = in.integer();
            in.end();
            return new Memory(waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak,
                    calm == 1, dwelt, blocked, last, holds, near, before, flights, avoid, underfoot, refused, pack, aim,
                    drank, trial, balked, walking, tested, clouds, refuge, arrived, rests, stepped);
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
