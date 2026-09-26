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
import org.shatterfish.api.PromptKind;
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
    static final String PICK_UP = "PickUp";

    /**
     * The refusals in a row at one cell that block it while the hero shows vertigo (story 4.13).
     * A Step under vertigo goes to a random one of eight neighbours and stays put when that one is
     * blocked (Char.java:1298-1305); in a corridor six of the eight are walls, and six misses in a row
     * there happen about one time in six (0.75^6, 0.18), against a Step at a held cell, which is refused
     * every time. An assumption, chosen so a fleeting block (a fight screen's) is rarely set by chance.
     */
    static final int VERTIGO_REFUSALS = 6;

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
        // still hero means depends on what the Brain last handed over (story 4.7): after a Step, the
        // Step was refused, and the streak of refusals grows, on a calm screen or not -- a Step the
        // game refuses in a fight spends no time either, and would be handed over again forever
        // (story 4.12); after a Search on a calm screen, the spot was searched. After anything else
        // -- an attack, a pick-up, a wait -- standing still is what the Action does, and it counts for
        // neither.
        int branch = observation.header().branch();
        Memory.Spot here = new Memory.Spot(depth, branch, observation.hero().cell());
        boolean still = here.equals(memory.at());
        List<Memory.Spot> dwelt = still && memory.calm() && memory.last().equals(SEARCH)
                ? Memory.with(memory.dwelt(), here) : memory.dwelt();
        // A still hero after a Step is a refusal, unless the screen shows the hero rooted, which refuses
        // every Step until the roots wear off (Hero.java:1822-1825): that says nothing about the cell.
        // Under vertigo a Step goes to a random neighbour, and a blocked one spends the Step
        // (Char.java:1296-1305); but a Step at a cell an undrawn character holds spends no time at all
        // (Hero.java:1831-1834) and would be handed over forever. So vertigo's refusals count too, and
        // it takes VERTIGO_REFUSALS of them in a row, not two, to block the cell (story 4.13).
        // Refusals count toward a block only while they are aimed at one cell.
        boolean refusedStep = still && memory.last().equals(STEP) && memory.stepped() >= 0 && !Explore.rooted(observation);
        int streak = !refusedStep ? 0 : memory.tried() == memory.stepped() ? memory.streak() + 1 : 1;
        int tried = refusedStep ? memory.stepped() : -1;
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
        // The plain heap underfoot, and whether it is one the game would not let the hero take
        // (story 4.8): the pick-up Policy's target on the last screen was this heap, that screen was
        // calm, the hero stands on it still, it shows the same title, and the pack is unchanged. A
        // heap of several like items shows the next one's title after a pick-up, but the pack grows;
        // a heap the Policy was not going for was not tried.
        String underfoot = "";
        for (HeapView heap : observation.map().heaps()) {
            if (heap.cell() == here.cell() && heap.kind() == HeapKind.HEAP) {
                underfoot = heap.item();
            }
        }
        List<Memory.Refused> refused = memory.refused();
        Memory.Refused refusal = new Memory.Refused(depth, branch, here.cell(), underfoot);
        Memory.Pack pack = pack(observation);
        // And the Brain handed over the pick-up: a turn a human spent otherwise refuses nothing.
        if (still && memory.calm() && memory.last().equals(PICK_UP) && !underfoot.isEmpty()
                && underfoot.equals(memory.underfoot()) && memory.aim().target() == here.cell()
                && pack.equals(memory.pack()) && !refused.contains(refusal)) {
            refused = new ArrayList<>(refused);
            refused.add(refusal);
            if (refused.size() > Memory.DWELT) {
                refused.remove(0);
            }
        }
        // The wait the hero came to this floor, and the rests the descend Policy handed over on it
        // (story 4.12): a floor first seen at this wait starts both afresh.
        boolean arriving = !memory.at().on(depth, branch);
        long arrived = arriving ? waits : memory.arrived();
        int rests = arriving ? 0 : memory.rests();
        List<Memory.Balk> balked = balked(memory, observation, here, still);
        int walking = walking(memory, still);
        List<Memory.Cloud> fleeting = memory.fleeting().stream().filter(block -> block.until() >= waits).toList();
        // Back and forth between two cells (story 4.13): the hero is back on the cell of two waits ago,
        // having left it. Counted only on one floor; anything else starts the count again.
        boolean sameFloor = !arriving && memory.at().on(depth, branch);
        boolean bounced = sameFloor && !still && memory.prior() == here.cell();
        int bounces = bounced ? memory.bounces() + 1 : 0;
        int prior = sameFloor ? memory.at().cell() : -1;
        // Bounced BOUNCES times: the cell the Step back would return to is blocked for BOUNCE_WAITS waits, as a
        // fleeting block (issue #174), so every Policy plans around it and the loop ends; Brain.decide still
        // withholds the Step itself this wait, from the Policies that plan without the walkable cells too.
        if (bounces >= Memory.BOUNCES && prior >= 0) {
            List<Memory.Cloud> blocking = new ArrayList<>(fleeting);
            blocking.add(new Memory.Cloud(depth, branch, prior, waits + Memory.BOUNCE_WAITS));
            while (blocking.size() > Memory.DWELT) {
                blocking.remove(0);
            }
            fleeting = blocking;
        }
        // The enemy the fight Policy last approached (issue #174): kept on its floor for Fight.CHASE_WAITS waits
        // after the approach, until the hero stands beside the cell it was seen on.
        Memory.Spot chase = memory.chase();
        long chased = memory.chased();
        if (chase.cell() >= 0 && (!chase.on(depth, branch) || waits - chased > Fight.CHASE_WAITS
                || chase.cell() >= observation.map().tiles().size()
                || Fight.chebyshev(observation.map(), here.cell(), chase.cell()) <= 1)) {
            chase = Memory.Spot.NOWHERE;
            chased = -1;
        }
        // The hunger clock (story 4.13, Larder): what the last Action cost, less a meal, clamped by the icon.
        int hp = observation.hero().hp();
        int food = Larder.food(observation);
        int gained = memory.hp() < 0 ? 0 : hp - memory.hp();
        int eaten = memory.food() < 0 ? 0 : Math.max(0, memory.food() - food);
        // A rest while a drunk potion's heal still lands is billed at most the turns that heal takes.
        int ht = observation.hero().ht();
        int restCap = memory.drank() >= 0 && memory.waits() - memory.drank() <= Heal.healingTurns(ht)
                ? Heal.healingTurns(ht) : Integer.MAX_VALUE;
        boolean locked = observation.hero().buffs().stream().anyMatch(buff -> buff.name().equals(Larder.LOCKED));
        int hunger = Larder.clock(memory.hunger(), memory.last(), !still, gained, eaten, restCap, locked,
                observation.hero().hunger());
        // A Goo pump-up the log announces (story 4.13, Goo): new when the log changed and one of its
        // last Goo.RECENT lines is the announcement; it lapses once Goo has moved, which drops it (Goo.java:244-250), or
        // after PUMP_WAITS waits.
        int tail = Goo.tail(observation);
        int gooCell = Goo.cell(observation);
        int pump = memory.pump();
        long pumpWait = memory.pumpWait();
        if (gooCell >= 0 && tail != memory.tail() && Goo.announced(observation)) {
            pump = gooCell;
            pumpWait = waits;
        } else if (pump >= 0 && (gooCell != pump || waits - pumpWait > Goo.PUMP_WAITS || arriving)) {
            pump = -1;
            pumpWait = -1;
        }
        Memory after = new Memory(waits, Math.max(memory.deepest(), depth), facts, found, held, known, labels, pending,
                sightings(memory.monsters(), observation, waits), here, streak, calm, dwelt, memory.blocked(),
                memory.last(), holds, near, before, flights, avoid, underfoot, refused, pack, Memory.Aim.NONE,
                memory.drank(), Memory.Trial.NONE, balked, walking, memory.tested(), clouds(memory, observation, waits),
                memory.refuge(), arrived, rests, memory.stepped(), tried, fleeting, opened(memory, observation), prior,
                bounces, hunger, hp, food, tail, pump, pumpWait, chase, chased);
        // Two Steps in a row refused at one cell: the stepping Policy yields this wait, and that cell is
        // blocked on this floor, whichever Policy chose it (story 4.12; stories 4.8 and 4.10 recomputed
        // it from their own plans). Refused on a calm screen, for good; refused with an enemy in view,
        // for FLEETING_WAITS waits, since the fight may have been the reason. The count then starts
        // again, so a second cell refused twice is blocked in turn.
        if (streak >= (Explore.has(observation, Explore.VERTIGO) ? VERTIGO_REFUSALS : Explore.STUCK - 1)) {
            Memory.Spot cell = new Memory.Spot(depth, branch, memory.stepped());
            List<Memory.Spot> blocked = memory.calm() ? Memory.with(after.blocked(), cell) : after.blocked();
            List<Memory.Cloud> lapsing = new ArrayList<>(fleeting);
            if (!memory.calm()) {
                lapsing.add(new Memory.Cloud(depth, branch, memory.stepped(), waits + Memory.FLEETING_WAITS));
                while (lapsing.size() > Memory.DWELT) {
                    lapsing.remove(0);
                }
            }
            after = new Memory(after.waits(), after.deepest(), facts, found, held, known, labels, pending,
                    after.monsters(), here, streak, calm, dwelt, blocked, after.last(),
                    holds, near, before, flights, avoid, underfoot, refused, pack, Memory.Aim.NONE, memory.drank(),
                    Memory.Trial.NONE, balked, walking, memory.tested(), after.clouds(),
                    memory.refuge(), arrived, rests, memory.stepped(), -1, lapsing, after.windows(), prior, bounces,
                    hunger, hp, food, tail, pump, pumpWait, chase, chased);
        }
        return after;
    }

    /**
     * The windows after seeing {@code observation} (story 4.11): a shop, a guess or a spell list that
     * appears after an Action other than an answer was opened by that Action, which is remembered
     * while the window stays open and forgotten once none of the three is.
     */
    static Memory.Windows opened(Memory memory, Observation observation) {
        Memory.Windows windows = memory.windows();
        PromptKind kind = observation.prompt().kind();
        boolean leavable = kind == PromptKind.SHOP || kind == PromptKind.GUESS || kind == PromptKind.SPELL;
        String opener = windows.opener();
        if (!leavable) {
            opener = "";
        } else if (opener.isEmpty() && !memory.last().equals(ANSWER) && !memory.last().equals(DISMISS)) {
            opener = windows.action();
        }
        return new Memory.Windows(windows.action(), windows.target(), windows.worn(), opener, windows.shunned());
    }

    /** The kinds of the two Actions that answer a Prompt, as {@link #kind} names them. */
    static final String ANSWER = "AnswerPrompt";
    static final String DISMISS = "DismissPrompt";

    /**
     * The appearances balked at after this screen (story 4.10): the test the test-item Policy handed
     * over at the last wait, read against what this screen shows. A drink or a read that leaves the
     * appearance held in the same quantity did not happen -- the game refuses a read without spending
     * a turn while the hero is blind, immune to magic or under a cursed spellbook's charge
     * (Scroll.java:172-192) -- and a Step toward the testing cell that leaves the hero where it stood
     * was refused. Either way the appearance is not tried again on the floor. A test that happened
     * takes the item out of the pack under that name: a drink detaches it (Potion.java:288-291), an
     * inventory scroll detaches itself before its picker opens (InventoryScroll.java:41-44), and
     * every other read spends it; identified, the rest go by their identity's name.
     */
    static List<Memory.Balk> balked(Memory memory, Observation observation, Memory.Spot here, boolean still) {
        Memory.Trial trial = memory.trial();
        if (trial.label().isEmpty() || !memory.at().on(here.depth(), here.branch())) {
            return memory.balked();
        }
        boolean untried = trial.step() >= 0 ? still || walking(memory, still) > TestItem.WALKS
                : heldQuantity(observation, trial.label()) >= trial.quantity();
        // A walk balked at stops only the walking: the test where the hero stands may still be safe.
        Memory.Balk balk = new Memory.Balk(here.depth(), here.branch(), trial.label(), trial.step() >= 0);
        if (!untried || memory.balked().contains(balk)) {
            return memory.balked();
        }
        List<Memory.Balk> balked = new ArrayList<>(memory.balked());
        balked.add(balk);
        while (balked.size() > Memory.DWELT) {
            balked.remove(0);
        }
        return balked;
    }

    /**
     * How many Steps in a row the test-item Policy has walked toward testing cells, this one included
     * when the hero moved (story 4.10): a walk that goes on past {@link TestItem#WALKS} is one whose
     * cell keeps moving away, and its appearance is balked at.
     */
    static int walking(Memory memory, boolean still) {
        return !memory.trial().label().isEmpty() && memory.trial().step() >= 0 && !still ? memory.walking() + 1 : 0;
    }

    /**
     * The clouded cells after this screen (story 4.10): every cell in view that shows fire or a harmful
     * gas is kept out of for up to {@link Memory#CLOUD_WAITS} more waits, a cell in view that shows
     * none is cleared, a shut door's cell is never kept, and a lapsed one is forgotten.
     */
    static List<Memory.Cloud> clouds(Memory memory, Observation observation, long waits) {
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        org.shatterfish.api.MapSection map = observation.map();
        java.util.Set<Integer> clouded = new java.util.HashSet<>();
        for (org.shatterfish.api.BlobCell blob : map.blobs()) {
            if (blob.kinds().stream().anyMatch(Explore.HARMFUL::contains)) {
                clouded.add(blob.cell());
            }
        }
        List<Memory.Cloud> clouds = new ArrayList<>();
        for (Memory.Cloud cloud : memory.clouds()) {
            boolean here = cloud.depth() == depth && cloud.branch() == branch;
            if (cloud.until() < waits || (here && clouded.contains(cloud.cell()))) {
                continue;
            }
            // Seen clear, or a shut door now: a shut door is solid and holds no gas (Terrain.java:90,
            // Blob.java:158-164), and a cloud kept on one would block the room behind it for as
            // long as the memory lasts.
            if (here && cloud.cell() < map.tiles().size()
                    && (map.fog().get(cloud.cell()) == org.shatterfish.api.Fog.VISIBLE
                    || map.tiles().get(cloud.cell()) == org.shatterfish.api.Tile.DOOR)) {
                continue;
            }
            clouds.add(cloud);
        }
        // In cell order, so the list does not depend on the blobs' order.
        clouded.stream().sorted().forEach(cell -> clouds.add(new Memory.Cloud(depth, branch, cell,
                waits + Memory.CLOUD_WAITS)));
        while (clouds.size() > Memory.CLOUDS) {
            clouds.remove(0);
        }
        return clouds;
    }

    /** The pack as far as a pick-up changes it (story 4.8). */
    static Memory.Pack pack(Observation observation) {
        int quantity = 0;
        for (org.shatterfish.api.ItemView item : observation.inventory().items()) {
            quantity += item.quantity();
        }
        return new Memory.Pack(observation.inventory().items().size(), quantity, observation.hero().gold());
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
