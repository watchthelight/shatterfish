package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The test-item Policy (story 4.10, FR-31, FR-30): learn what an unidentified potion or scroll is by
 * drinking or reading it, where the worst it can do is survivable.
 *
 * <p>It enters only on a calm screen, no Prompt and no enemy in view but a passive one, so it never
 * tests during a fight; and it ranks first among the calm-screen Policies -- above pick-up, equip and
 * explore -- so it tests at the first calm moment after the item is held, which is as early on the
 * floor as the item allows, and its escape from its own fire or gas comes before anything else. Of the appearances
 * held, it tries first the one worth most to know: the most copies held (a test identifies them
 * all) times the candidates left beyond one. An appearance with a single candidate left is known by
 * elimination and not tried.
 *
 * <p>A test must pay for itself in play (story 4.10's review). A potion is drunk only when the
 * knowledge has a use now, and its worst case leaves at least a quarter of the hero's hit points
 * ({@link #reserved}): the hero is at half its hit points or below on a calm screen and the odds that
 * it is healing are at least {@link #HEALING_ODDS}; or, at any health, the odds that it is strength or
 * experience ({@link #GAINS}) are at least {@link #GAIN_ODDS}, whose use is the drink (story 4.13). Drunk then, a healing potion heals at once, and once
 * known the heal Policy (story 4.9) drinks the rest when a fight turns. A scroll is read only at full
 * health -- Lullaby's sleep is then harmless (MagicalSleep.java:37-50) and Rage's draw is met whole --
 * and only while the four identities that spend a read on their item picker (identify, remove curse,
 * transmutation, upgrade: InventoryScroll's subclasses) hold under {@link #INVENTORY_ODDS} of its
 * odds: read plainly, such a scroll is identified and consumed with no effect
 * (InventoryScroll.java:39-50, :137-139). After a test, while the hero is short of full health,
 * it rests (up to {@link #REST_WAITS} waits) before anything else on a calm screen acts.
 *
 * <p>Every test is one {@link SafeTest} calls safe at the cell it happens on. A potion's worst case
 * depends on the cell -- water shortens liquid flame's burn, a closed door beside it toxic gas's
 * cloud -- so when a cell within {@link #REACH} Steps scores a strictly smaller worst case than the
 * hero's own, it walks there first, one offered Step a wait, and drinks there. A scroll's worst case
 * does not depend on the cell and is read where the hero stands.
 *
 * <p>A scroll is read plainly, never onto another item (story 4.10, design D3): an unknown
 * inventory scroll identifies itself and leaves the pack before its item picker opens
 * (InventoryScroll.java:39-49), the executor sends the picker away, and the scroll asks to confirm
 * the cancel (InventoryScroll.java:137-139, :52-80), which answer-prompt affirms. Read onto an item,
 * a scroll of upgrade would open the upgrade window (ScrollOfUpgrade.java:60-66), which no Action
 * answers. It does not read while the screen shows the hero blinded or immune to magic, which the
 * game refuses without spending a turn (Scroll.java:172-192); a refusal the screen does not show
 * beforehand is the Memory's to record ({@link Memory#balked}), and the appearance is not tried
 * again on that floor.
 *
 * <p>After a test, it carries out the escape {@code SafeTest}'s smaller figures assume: for
 * {@link #ESCAPE_WAITS} waits, while the hero's own cell shows a harmful blob, or the hero shows
 * "burning", it Steps toward the nearest cell with none -- the nearest water while burning. No
 * calm-screen Policy ranks above it, so nothing walks the hero deeper into the cloud first.
 */
final class TestItem implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "test-item";

    /** The item actions that test: a potion's drink (Potion.java:84), a scroll's read (Scroll.java). */
    static final String DRINK = "DRINK";
    static final String READ = "READ";

    /** The least odds of healing an unknown potion is drunk at (story 4.10's review). */
    static final double HEALING_ODDS = 0.2;

    /** The most odds the inventory scrolls may hold of an unknown scroll that is read. */
    static final double INVENTORY_ODDS = 0.25;

    /**
     * The scrolls whose read opens an item picker (InventoryScroll's subclasses among the regular
     * scrolls: ScrollOfIdentify.java, ScrollOfRemoveCurse.java, ScrollOfTransmutation.java,
     * ScrollOfUpgrade.java), by the Codex's class names.
     */
    static final Set<String> INVENTORY_SCROLLS = Set.of("items.scrolls.ScrollOfIdentify",
            "items.scrolls.ScrollOfRemoveCurse", "items.scrolls.ScrollOfTransmutation", "items.scrolls.ScrollOfUpgrade");

    /** The potion of healing, by the Codex's class name. */
    static final String HEALING = "items.potions.PotionOfHealing";

    /**
     * The potions that make the hero stronger for good when drunk, by the Codex's class names: strength,
     * one more strength point (PotionOfStrength.java), and experience, a level (PotionOfExperience.java).
     * Their use is the drink itself, at any health (story 4.13): of 40 Warriors, every one died at its
     * starting strength of 10 holding unknown potions, the owed strength potions among them.
     */
    static final Set<String> GAINS = Set.of("items.potions.PotionOfStrength", "items.potions.PotionOfExperience");

    /**
     * The least odds of strength or experience an unknown potion is drunk at, whatever the hero's
     * health (story 4.13). An assumption, tuned against the rig: the worst case must still leave the
     * reserve ({@link #reserved}), and a healing potion drunk at full health is spent, but identified.
     */
    static final double GAIN_ODDS = 0.2;

    /** The most waits after a test it rests toward full health (as the explore Policy's RESTS). */
    static final int REST_WAITS = Explore.RESTS;

    /** The most Steps it walks to a better testing cell. */
    static final int REACH = 8;

    /**
     * The most Steps in a row it walks toward testing cells before it balks at the appearance: twice
     * {@link #REACH}, which a walk to a cell that stays put never needs.
     */
    static final int WALKS = 2 * REACH;

    /** The buffs a read is refused under, as the screen names them (actors.properties:131, :305). */
    static final String BLINDED = "blinded";
    static final String MAGIC_IMMUNE = "immune to magic";

    /** The burning buff, as the screen names it (actors.properties:137). */
    static final String BURNING = "burning";

    /**
     * The blobs whose cells the escape leaves, by the class names the Observation carries: the fire
     * and the gases a tested potion can seed (Potion.java:332-334 and the harmful potions' shatter).
     */
    static final Set<String> HARMFUL = Explore.HARMFUL;

    /**
     * How many waits after a test it steps out of fire or gas: the toxic cloud's modelled
     * {@link SafeTest#GAS_TURNS} and two. A cloud it did not make is not its to leave: seen from
     * outside a shut door a gassed room shows no gas at all (a closed door is solid and draws what
     * is behind it unseen), so a Policy that stepped out of every cloud would step out of the doorway,
     * see none, and let explore walk back in, for as long as the gas lasts.
     */
    static final int ESCAPE_WAITS = SafeTest.GAS_TURNS + 2;

    private final Codex.Knowledge knowledge;

    TestItem(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * What the Policy would do on a screen: the appearance it acts for (empty for an escape), how many
     * of it the pack holds, and the Choice.
     */
    record Plan(String label, int quantity, RunLog.Choice choice) {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "items: identify";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        if (!Explore.calm(observation)) {
            return false;
        }
        if (escaping(observation, memory)) {
            return true;
        }
        if (!settled(observation, memory) && !passing(observation, memory)) {
            return false;
        }
        return upgrade(observation, observation.actions().actions()) != null
                || !testable(observation, memory).isEmpty() || restOwed(observation, memory);
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        Plan plan = plan(observation, memory, offered);
        return plan == null ? null : plan.choice();
    }

    /** The plan on this screen, or null: the same one {@link #choose} takes, which the Brain records. */
    Plan plan(Observation observation, Memory memory, List<Action> offered) {
        if (!Explore.calm(observation)) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        if (escaping(observation, memory)) {
            // Out of harm first, and clear of the cloud's edge, which spreads (Blob.java:158-186).
            Action step = escape(observation, memory, offered);
            String what = inHarm(observation) ? harm(observation) : "edge";
            return step == null ? null
                    : new Plan("", 0, new RunLog.Choice(step, Policies.CERTAIN, "escape: " + what));
        }
        // Never a test or a rest in harm, at a cloud's edge, or holding a door open; but a walk to a testing
        // cell goes on through a doorway (passing).
        boolean settled = settled(observation, memory);
        if (!settled && !passing(observation, memory)) {
            return null;
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        // A known scroll of upgrade goes onto the worn weapon or armour (story 4.13, target): the window it opens
        // is answered when the Brain's last Action was this read onto an item (story 4.11).
        Action upgrade = settled ? upgrade(observation, offered) : null;
        if (upgrade != null) {
            return new Plan("", 0, new RunLog.Choice(upgrade, Policies.CERTAIN, "upgrade: " + target(observation).name()));
        }
        // An unknown scroll goes where a known upgrade would, but never onto the Mage's staff: an
        // unknown scroll of transmutation changes the item it is read onto (ScrollOfTransmutation.java:
        // 71-75): for a melee weapon, another of its tier with its level, enchantment and curse (:231-253),
        // no loss; but it takes the staff's wand (:158-159).
        ItemRef armour = target(observation);
        if (armour != null && armour.name().startsWith(Fight.MAGES_STAFF)) {
            armour = armour(observation);
        }
        for (Testable item : testable(observation, memory)) {
            // An unknown scroll is read onto the worn armour (story 4.13): a scroll of upgrade then
            // upgrades it, and any other scroll either ignores the target or, an item-picker scroll that
            // does not take the armour, is sent away and identified, as a plain read is
            // (ActionExecutor, the unselectable target; InventoryScroll.java:137-139).
            Action use = item.verb().equals(READ) && armour != null
                    ? new Action.UseItemOn(item.ref(), READ, armour)
                    : new Action.UseItem(item.ref(), item.verb());
            if (!offered.contains(use)) {
                continue;
            }
            List<SafeTest.Candidate> candidates = SafeTest.candidates(item.guess(), knowledge);
            SafeTest.Verdict here = SafeTest.of(candidates, observation);
            boolean hereFits = fits(item, here, observation);
            // No walk to a better testing cell while food is tight (story 4.13, Larder): the walks cost a
            // hundred turns a Run, a third of a floor's food.
            if (item.guess().kind() == ItemKind.POTION && !Pickup.stuck(memory) && !Explore.frugal(observation, memory)
                    && !memory.balksWalking(depth, branch, item.guess().label())) {
                boolean[] walk = Explore.walkable(observation, memory);
                int[] distance = Pickup.distances(map, walk, hero);
                int best = -1;
                int bestDamage = hereFits && settled ? here.worst().damage() : Integer.MAX_VALUE;
                int bestDistance = Integer.MAX_VALUE;
                java.util.function.IntPredicate clean = clean(observation, memory);
                for (int cell = 0; cell < distance.length; cell++) {
                    if (distance[cell] < 1 || distance[cell] > REACH || !harmfulOn(map, cell).isEmpty()) {
                        continue;
                    }
                    // Only a cell a test may happen on once the hero is there (settled): never a
                    // doorway, never beside a cloud. Walking to one, the hero would find the test
                    // refused there and walk back and forth with another Policy (story 4.13).
                    Tile tile = map.tiles().get(cell);
                    if (tile == Tile.OPEN_DOOR || tile == Tile.DOOR || !clean.test(cell) || !clear(map, cell, clean)) {
                        continue;
                    }
                    SafeTest.Verdict there = SafeTest.of(candidates, observation, cell);
                    int damage = there.worst().damage();
                    if (fits(item, there, observation) && (damage < bestDamage || (best >= 0 && damage == bestDamage
                            && distance[cell] < bestDistance))) {
                        best = cell;
                        bestDamage = damage;
                        bestDistance = distance[cell];
                    }
                }
                if (best >= 0) {
                    Action step = Pickup.firstStep(map, walk, hero, best, offered);
                    if (step != null) {
                        return new Plan(item.guess().label(), item.quantity(),
                                new RunLog.Choice(step, Policies.CERTAIN, "cell: " + item.guess().label()));
                    }
                }
            }
            if (hereFits && settled) {
                return new Plan(item.guess().label(), item.quantity(),
                        new RunLog.Choice(use, Policies.CERTAIN, "test: " + item.guess().label()));
            }
        }
        if (settled && restOwed(observation, memory)) {
            for (Action rest : List.of(new Action.Rest(true), new Action.Search())) {
                if (offered.contains(rest)) {
                    return new Plan("", 0, new RunLog.Choice(rest, Policies.CERTAIN, "rest: after-test"));
                }
            }
        }
        return null;
    }

    /**
     * Whether a verdict lets {@code item} be tried: safe, and for a potion, with a quarter of the
     * hero's hit points left after the worst case ({@link #reserved}).
     */
    static boolean fits(Testable item, SafeTest.Verdict verdict, Observation observation) {
        return verdict.safe() && (item.guess().kind() != ItemKind.POTION
                || reserved(observation.hero().hp(), observation.hero().ht(), verdict.worst().damage()));
    }

    /** Whether {@code hp} less {@code damage} leaves at least a quarter of {@code ht}. */
    static boolean reserved(int hp, int ht, int damage) {
        return 4L * ((long) hp - damage) >= ht;
    }

    /**
     * Whether a rest is owed after a test (story 4.10's review): within {@link #REST_WAITS} waits of a
     * drink or read this Policy handed over, the hero short of full health and neither hungry nor
     * starving -- a starving hero does not regenerate (Regeneration.java:56).
     */
    static boolean restOwed(Observation observation, Memory memory) {
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        int hero = observation.hero().cell();
        int width = observation.map().width();
        return memory.tested() >= 0 && memory.waits() - memory.tested() <= REST_WAITS
                && observation.hero().hp() < observation.hero().ht()
                && observation.hero().hunger() == org.shatterfish.api.Hunger.NONE
                // A locked boss floor stops regeneration and hunger (LockedFloor.java:62-64,
                // Regeneration.java:112-117): nothing would end the rest, and the Run would stall.
                && !has(observation, LOCKED)
                // Not where the fight Policy just retreated from: the enemy comes back mid-rest.
                && memory.avoided(depth, branch, memory.waits()).stream()
                        .noneMatch(region -> region.covers(depth, branch, hero, width));
    }

    /** The buff a locked boss floor shows (actors.properties:290). */
    static final String LOCKED = "floor is locked";

    /** What counts as clean at a cell: no harmful blob drawn there and no cloud remembered there. */
    static java.util.function.IntPredicate clean(Observation observation, Memory memory) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        return cell -> harmfulOn(map, cell).isEmpty() && !memory.clouded(depth, branch, cell, memory.waits());
    }

    /**
     * Whether the hero stands where a test or a rest may happen: its cell and every neighbour clean, so
     * no cloud is about to spread onto it and interrupt a rest (Hero.java:1644-1646), and not in a
     * doorway, which a hero standing in holds open (Door.java:45-58).
     */
    static boolean settled(Observation observation, Memory memory) {
        return clean(observation, memory, observation.hero().cell())
                && observation.map().tiles().get(observation.hero().cell()) != Tile.OPEN_DOOR;
    }

    /** Whether the hero is out of harm and its cell and every neighbour clean. */
    private static boolean clean(Observation observation, Memory memory, int hero) {
        java.util.function.IntPredicate clean = clean(observation, memory);
        return !inHarm(observation) && clean.test(hero) && clear(observation.map(), hero, clean);
    }

    /**
     * Whether the hero stands in a doorway partway along a walk to a testing cell (issue #174): the last
     * wait's Step toward one was carried out ({@link Memory#walking}), and all is clean around it. The
     * walk goes on through the doorway; nothing is tested there. Before, the Policy stood aside in every
     * doorway, the pick-up Policy walked the hero back out of it, and the walk stepped in again.
     */
    static boolean passing(Observation observation, Memory memory) {
        return memory.walking() > 0 && observation.map().tiles().get(observation.hero().cell()) == Tile.OPEN_DOOR
                && clean(observation, memory, observation.hero().cell());
    }

    /**
     * The trial the Memory records when this Policy's {@code action} is handed over on this screen
     * (story 4.10), or {@link Memory.Trial#NONE} when it is not one this Policy plans here.
     */
    Memory.Trial trial(Observation observation, Memory memory, Action action) {
        Plan plan = plan(observation, memory, observation.actions().actions());
        if (plan == null || plan.label().isEmpty() || !plan.choice().action().equals(action)) {
            return Memory.Trial.NONE;
        }
        return new Memory.Trial(plan.label(), plan.quantity(),
                action instanceof Action.Step step ? step.cell() : -1);
    }

    /** The known scroll of upgrade, by the name the inventory shows (items.properties). */
    static final String UPGRADE = "scroll of upgrade";

    /**
     * The offered read of a known scroll of upgrade onto the {@link #target}, or null (story 4.13). It
     * makes this Policy enter, as a testable item does: a known upgrade is not one.
     */
    static Action upgrade(Observation observation, List<Action> offered) {
        // Blinded or immune to magic, the game refuses the read with no time spent (Scroll.java:179-182),
        // and the refusal would be handed over forever.
        if (has(observation, BLINDED) || has(observation, MAGIC_IMMUNE)) {
            return null;
        }
        ItemRef armour = target(observation);
        List<ItemView> pack = observation.inventory().items();
        for (int index = 0; armour != null && index < pack.size(); index++) {
            ItemView item = pack.get(index);
            if (item.name().equals(UPGRADE)) {
                Action upgrade = new Action.UseItemOn(new ItemRef(index, item.name(), item.quantity()), READ, armour);
                if (offered.contains(upgrade)) {
                    return upgrade;
                }
            }
        }
        return null;
    }

    /** The worn armour's pack reference, or null when none is worn. */
    static ItemRef armour(Observation observation) {
        return worn(observation, org.shatterfish.api.EquipSlot.ARMOR);
    }

    /**
     * Where a scroll of upgrade goes (story 4.13): the worn melee weapon while the level it shows is
     * no higher than the worn armour's, else the worn armour, so the two climb together, the weapon
     * first; null when no armour is worn. A level adds one to a melee weapon's least damage and its
     * tier plus one to its most (MeleeWeapon.java:250-259), and its tier to an armour's most damage
     * absorbed (Armor.java:379-384): for the tier-1 pieces a Run starts in, a weapon level is worth
     * about three armour levels a hit.
     */
    static ItemRef target(Observation observation) {
        ItemRef armour = armour(observation);
        ItemRef weapon = worn(observation, org.shatterfish.api.EquipSlot.WEAPON);
        if (armour == null || weapon == null) {
            return armour;
        }
        ItemView a = observation.inventory().items().get(armour.index());
        ItemView w = observation.inventory().items().get(weapon.index());
        boolean upgradable = w.kind() == ItemKind.WEAPON && w.levelKnown();
        return upgradable && w.visiblyUpgraded() <= a.visiblyUpgraded() ? weapon : armour;
    }

    /** Whether {@code ref} is the worn armour or the worn weapon: an item a read may go onto (story 4.13). */
    static boolean worn(Observation observation, ItemRef ref) {
        return ref.equals(armour(observation)) || ref.equals(worn(observation, org.shatterfish.api.EquipSlot.WEAPON));
    }

    /** The pack reference of the item worn in {@code slot}, or null. */
    private static ItemRef worn(Observation observation, org.shatterfish.api.EquipSlot slot) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.slot() == slot) {
                return new ItemRef(index, item.name(), item.quantity());
            }
        }
        return null;
    }

    /** An appearance held that this Policy may test: the pack's reference, its guess and its verb. */
    record Testable(ItemRef ref, int quantity, Beliefs.Guess guess, String verb) {
    }

    /**
     * The appearances held that may be tested here, worth most first: a potion that can be drunk or a
     * scroll that can be read, with two candidates or more, not balked at on this floor, and not a
     * scroll while the hero is blinded or immune to magic.
     */
    List<Testable> testable(Observation observation, Memory memory) {
        Map<String, Beliefs.Guess> guesses = new LinkedHashMap<>();
        for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge)) {
            guesses.put(guess.label(), guess);
        }
        boolean unreadable = has(observation, BLINDED) || has(observation, MAGIC_IMMUNE);
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<ItemView> items = observation.inventory().items();
        List<Testable> testable = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            Beliefs.Guess guess = guesses.get(item.name());
            if (guess == null || guess.odds().size() < 2 || memory.balks(depth, branch, item.name())) {
                continue;
            }
            String verb = item.kind() == ItemKind.POTION && guess.kind() == ItemKind.POTION
                    && (low(observation) && odds(guess, Set.of(HEALING)) >= HEALING_ODDS
                        || odds(guess, GAINS) >= GAIN_ODDS) ? DRINK
                    : item.kind() == ItemKind.SCROLL && guess.kind() == ItemKind.SCROLL && !unreadable
                    && observation.hero().hp() >= observation.hero().ht()
                    && (odds(guess, INVENTORY_SCROLLS) < INVENTORY_ODDS || armour(observation) != null) ? READ : null;
            if (verb == null || !item.actions().contains(verb)) {
                continue;
            }
            testable.add(new Testable(new ItemRef(index, item.name(), item.quantity()), item.quantity(), guess, verb));
        }
        // Worth most first; the sort is stable, so equal worths keep the pack's order.
        testable.sort(Comparator.comparingLong(TestItem::worth).reversed());
        return testable;
    }

    /** Whether the hero is at half its hit points or below, when an unknown potion is drunk. */
    static boolean low(Observation observation) {
        return 2L * observation.hero().hp() <= observation.hero().ht();
    }

    /** The odds {@code guess} gives the identities of {@code classes}, by the Codex's class names. */
    double odds(Beliefs.Guess guess, Set<String> classes) {
        double sum = 0;
        for (SafeTest.Candidate candidate : SafeTest.candidates(guess, knowledge)) {
            if (classes.contains(candidate.className())) {
                sum += candidate.probability();
            }
        }
        return sum;
    }

    /** What knowing an appearance is worth: every copy held, times the candidates beyond one. */
    static long worth(Testable item) {
        return (long) item.quantity() * (item.guess().odds().size() - 1);
    }

    /** Whether the hero's own cell shows a harmful blob, or the hero is burning. */
    static boolean inHarm(Observation observation) {
        return !harm(observation).isEmpty();
    }

    /**
     * Whether, within {@link #ESCAPE_WAITS} waits of a test this Policy handed over, the hero still has
     * to leave its own fire or gas: it is in harm, or at the cloud's edge ({@link #settled} fails for a
     * cloud), or a cloud is in sight or remembered on the floor and the hero is not yet on the refuge
     * beyond the door the test was credited with.
     */
    static boolean escaping(Observation observation, Memory memory) {
        if (memory.tested() < 0 || memory.waits() - memory.tested() > ESCAPE_WAITS) {
            return false;
        }
        if (inHarm(observation)) {
            return true;
        }
        int hero = observation.hero().cell();
        java.util.function.IntPredicate clean = clean(observation, memory);
        if (!clean.test(hero) || !clear(observation.map(), hero, clean)) {
            return true;
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        boolean cloud = observation.map().blobs().stream().anyMatch(blob -> blob.kinds().stream().anyMatch(HARMFUL::contains))
                || memory.clouds().stream().anyMatch(c -> c.depth() == depth && c.branch() == branch
                        && c.until() >= memory.waits());
        return cloud && memory.refuge() >= 0 && hero != memory.refuge();
    }

    /**
     * What harms the hero where it stands: "burning" off water (burning ends at an act on water,
     * Burning.java:98-99), or a harmful blob on its cell, or empty.
     */
    static String harm(Observation observation) {
        if (has(observation, BURNING) && observation.map().tiles().get(observation.hero().cell()) != Tile.WATER) {
            return BURNING;
        }
        return harmfulOn(observation.map(), observation.hero().cell());
    }

    private static String harmfulOn(MapSection map, int cell) {
        for (BlobCell blob : map.blobs()) {
            if (blob.cell() == cell) {
                for (String kind : blob.kinds()) {
                    if (HARMFUL.contains(kind)) {
                        return kind;
                    }
                }
            }
        }
        return "";
    }

    /**
     * The offered Step out of harm: toward the nearest walkable cell with no harmful blob, or, while
     * burning, toward the nearest water within {@link #REACH}, which puts the burning out
     * (Burning.java:98-99). Every Step brings the hero strictly nearer, so it cannot stand still.
     */
    static Action escape(Observation observation, Memory memory, List<Action> offered) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        // Through the cloud: a hero in the middle of one has no clean neighbour to start from.
        boolean[] walk = Explore.walkable(observation, memory, true, true);
        int[] distance = Pickup.distances(map, walk, hero);
        boolean burning = has(observation, BURNING);
        java.util.function.IntPredicate clean = clean(observation, memory);
        int target = -1;
        // Through the door the test was credited with, when there was one and it can be reached.
        int refuge = memory.refuge();
        if (!burning && refuge >= 0 && refuge < distance.length && distance[refuge] >= 1 && clean.test(refuge)) {
            target = refuge;
        }
        if (target < 0 && burning) {
            target = nearest(map, distance, cell -> map.tiles().get(cell) == Tile.WATER && clean.test(cell), REACH);
        }
        if (target < 0) {
            // Clear of the cloud's edge, which spreads: a clean cell with no cloud beside it, which
            // through a door is the far side of it.
            target = nearest(map, distance, cell -> clean.test(cell) && clear(map, cell, clean), Integer.MAX_VALUE);
        }
        if (target < 0) {
            target = nearest(map, distance, clean, Integer.MAX_VALUE);
        }
        if (target < 0 || target == hero) {
            return null;
        }
        return Pickup.firstStep(map, walk, hero, target, offered);
    }

    /** Whether every neighbour of {@code cell} on the map is {@code clean}. */
    private static boolean clear(MapSection map, int cell, java.util.function.IntPredicate clean) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()
                        && !clean.test(nx + ny * width)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int nearest(MapSection map, int[] distance, java.util.function.IntPredicate fits, int reach) {
        int best = -1;
        for (int cell = 0; cell < distance.length; cell++) {
            if (distance[cell] >= 1 && distance[cell] <= reach && fits.test(cell)
                    && (best < 0 || distance[cell] < distance[best])) {
                best = cell;
            }
        }
        return best;
    }

    private static boolean has(Observation observation, String buff) {
        for (BuffView view : observation.hero().buffs()) {
            if (view.name().equals(buff)) {
                return true;
            }
        }
        return false;
    }
}
