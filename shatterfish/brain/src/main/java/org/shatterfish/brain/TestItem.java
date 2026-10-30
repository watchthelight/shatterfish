package org.shatterfish.brain;

import org.shatterfish.api.Playbook;

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
 * it is healing are at least the Playbook's {@code healing_odds_per_mille}; or, at any health, the odds that it is strength or
 * experience ({@link #GAINS}) are at least the Playbook's {@code gain_odds_per_mille}, whose use is the drink (story 4.13). Drunk then, a healing potion heals at once, and once
 * known the heal Policy (story 4.9) drinks the rest when a fight turns. A scroll is read only at full
 * health -- Lullaby's sleep is then harmless (MagicalSleep.java:37-50) and Rage's draw is met whole --
 * and only while the four identities that spend a read on their item picker (identify, remove curse,
 * transmutation, upgrade: InventoryScroll's subclasses) hold under the Playbook's {@code inventory_odds_per_mille} of its
 * odds: read plainly, such a scroll is identified and consumed with no effect
 * (InventoryScroll.java:39-50, :137-139). After a test, while the hero is short of full health,
 * it rests (up to the Playbook's {@code rest_waits}) before anything else on a calm screen acts.
 *
 * <p>Every test is one {@link SafeTest} calls safe at the cell it happens on. A potion's worst case
 * depends on the cell -- water shortens liquid flame's burn, a closed door beside it toxic gas's
 * cloud -- so when a cell within the Playbook's {@code reach} Steps scores a strictly smaller worst case than the
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
 * the Playbook's {@code escape_waits} waits, while the hero's own cell shows a harmful blob, or the hero shows
 * "burning", it Steps toward the nearest cell with none -- the nearest water while burning. No
 * calm-screen Policy ranks above it, so nothing walks the hero deeper into the cloud first.
 */
final class TestItem implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "test-item";

    /** The item actions that test: a potion's drink (Potion.java:84), a scroll's read (Scroll.java). */
    static final String DRINK = "DRINK";
    static final String READ = "READ";

    /**
     * The scrolls whose read opens an item picker (InventoryScroll's subclasses among the regular
     * scrolls: ScrollOfIdentify.java, ScrollOfRemoveCurse.java, ScrollOfTransmutation.java,
     * ScrollOfUpgrade.java), by the Codex's class names.
     */
    static final Set<String> INVENTORY_SCROLLS = Set.of("items.scrolls.ScrollOfIdentify",
            "items.scrolls.ScrollOfRemoveCurse", "items.scrolls.ScrollOfTransmutation", "items.scrolls.ScrollOfUpgrade");

    /** The scroll of upgrade, by the Codex's class name. */
    static final String UPGRADE_CLASS = "items.scrolls.ScrollOfUpgrade";

    /**
     * The odds of upgrade, in thousandths, at which an unknown scroll is read before the rest (issue #410): the
     * read does what a known upgrade does, onto the worn armour, so it is not worth putting behind a scroll
     * that is likelier something else. An assumption; the Rig measures it.
     */
    @org.shatterfish.api.Heuristic("identity-from-counts")
    static final int UPGRADE_FIRST_PER_MILLE = 500;

    /** The known scroll of identify, by the name the inventory shows (every class starts knowing it: HeroClass.java:117). */
    static final String IDENTIFY = "scroll of identify";

    /** The potion of healing, by the Codex's class name. */
    static final String HEALING = "items.potions.PotionOfHealing";

    /**
     * The potions that make the hero stronger for good when drunk, by the Codex's class names: strength,
     * one more strength point (PotionOfStrength.java), and experience, a level (PotionOfExperience.java).
     * Their use is the drink itself, at any health (story 4.13): of 40 Warriors, every one died at its
     * starting strength of 10 holding unknown potions, the owed strength potions among them.
     */
    static final Set<String> GAINS = Set.of("items.potions.PotionOfStrength", "items.potions.PotionOfExperience");

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

    private final Codex.Knowledge knowledge;
    private final Playbook playbook;

    /** The Policy under the {@linkplain Playbook#reference reference Playbook}: what the tests build. */
    TestItem(Codex.Knowledge knowledge) {
        this(knowledge, Playbook.reference("shatterfish"));
    }

    TestItem(Codex.Knowledge knowledge, Playbook playbook) {
        this.knowledge = knowledge;
        this.playbook = playbook;
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
        if (!Calm.quiet(observation, knowledge)) {
            return false;
        }
        Playbook.Identify identify = playbook.identify(observation.header().heroClass());
        Playbook.Upgrade upgrader = playbook.upgrade(observation.header().heroClass());
        if (escaping(observation, memory, identify)) {
            return true;
        }
        if (!settled(observation, memory) && !passing(observation, memory)) {
            return false;
        }
        return upgrade(observation, observation.actions().actions(), upgrader, knowledge) != null
                || gain(observation, observation.actions().actions(), identify) != null
                || identifying(observation, memory, observation.actions().actions()) != null
                || !testable(observation, memory).isEmpty() || restOwed(observation, memory, identify);
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        Plan plan = plan(observation, memory, offered);
        return plan == null ? null : plan.choice();
    }

    /** The plan on this screen, or null: the same one {@link #choose} takes, which the Brain records. */
    Plan plan(Observation observation, Memory memory, List<Action> offered) {
        if (!Calm.quiet(observation, knowledge)) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        Playbook.Identify identify = playbook.identify(observation.header().heroClass());
        Playbook.Upgrade upgrader = playbook.upgrade(observation.header().heroClass());
        if (escaping(observation, memory, identify)) {
            // Out of harm first, and clear of the cloud's edge, which spreads (Blob.java:158-186).
            Action step = escape(observation, memory, offered, identify);
            String what = inHarm(observation) ? harm(observation) : "edge";
            return step == null ? null
                    : new Plan("", 0, new RunLog.Choice(step, Policies.CERTAIN, "escape: " + what + " (escape_waits)"));
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
        Action upgrade = settled ? upgrade(observation, offered, upgrader, knowledge) : null;
        if (upgrade != null) {
            return new Plan("", 0, new RunLog.Choice(upgrade, Policies.CERTAIN, "upgrade: " + target(observation, upgrader, knowledge).name()));
        }
        // A known potion of strength or experience is drunk at once when the Playbook says so (story 7.3): it
        // does the same at any health and nothing else in the pack drinks it.
        Action gain = settled ? gain(observation, offered, identify) : null;
        if (gain != null) {
            return new Plan("", 0, new RunLog.Choice(gain, Policies.CERTAIN,
                    "drink: " + ((Action.UseItem) gain).item().name() + " (known_gains)"));
        }
        // A known scroll of identify goes onto an unknown potion before any is drunk to learn it (issue #410): the
        // read costs a turn and nothing else, a drink may cost health, and a potion drunk was spent.
        Action.UseItemOn identifying = settled ? identifying(observation, memory, offered) : null;
        if (identifying != null) {
            return new Plan("", 0, new RunLog.Choice(identifying, Policies.CERTAIN,
                    "identify: " + identifying.target().name() + " (identify-held)"));
        }
        // An unknown scroll goes where a known upgrade would, but never onto the Mage's staff: an
        // unknown scroll of transmutation changes the item it is read onto (ScrollOfTransmutation.java:
        // 71-75): for a melee weapon, another of its tier with its level, enchantment and curse (:231-253),
        // no loss; but it takes the staff's wand (:158-159).
        ItemRef armour = target(observation, upgrader, knowledge);
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
                    if (distance[cell] < 1 || distance[cell] > identify.reach() || !harmfulOn(map, cell).isEmpty()) {
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
                                new RunLog.Choice(step, Policies.CERTAIN, "cell: " + item.guess().label() + " (reach)"));
                    }
                }
            }
            if (hereFits && settled) {
                return new Plan(item.guess().label(), item.quantity(),
                        new RunLog.Choice(use, Policies.CERTAIN,
                                "test: " + item.guess().label() + " (" + item.field() + ")"));
            }
        }
        if (settled && restOwed(observation, memory, identify)) {
            for (Action rest : List.of(new Action.Rest(true), new Action.Search())) {
                if (offered.contains(rest)) {
                    return new Plan("", 0, new RunLog.Choice(rest, Policies.CERTAIN, "rest: after-test (rest_waits)"));
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
     * Whether a rest is owed after a test (story 4.10's review): within the Playbook's {@code rest_waits} waits of a
     * drink or read this Policy handed over, the hero hurt to the Playbook's {@code rest_hp_per_mille} or below and neither hungry nor
     * starving -- a starving hero does not regenerate (Regeneration.java:56).
     */
    static boolean restOwed(Observation observation, Memory memory, Playbook.Identify identify) {
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        int hero = observation.hero().cell();
        int width = observation.map().width();
        return memory.tested() >= 0 && memory.waits() - memory.tested() <= identify.restWaits()
                && observation.hero().hp() < observation.hero().ht()
                && identify.restHp(observation.hero().hp(), observation.hero().ht())
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

    /**
     * The offered drink of a known potion of strength or experience, or null: only when the Playbook's
     * {@code known_gains} is on (story 7.3). Strength adds a point and experience a level whatever the
     * hero's health (PotionOfStrength.java:43-47), so nothing is gained by holding one.
     */
    static Action gain(Observation observation, List<Action> offered, Playbook.Identify identify) {
        if (identify.knownGains() == 0) {
            return null;
        }
        List<ItemView> pack = observation.inventory().items();
        for (int index = 0; index < pack.size(); index++) {
            ItemView item = pack.get(index);
            if (item.name().equals(KNOWN_STRENGTH) || item.name().equals(KNOWN_EXPERIENCE)) {
                Action drink = new Action.UseItem(new ItemRef(index, item.name(), item.quantity()), DRINK);
                if (offered.contains(drink)) {
                    return drink;
                }
            }
        }
        return null;
    }

    /** The known potions of strength and experience, by the name the inventory shows (items.properties). */
    static final String KNOWN_STRENGTH = "potion of strength";
    static final String KNOWN_EXPERIENCE = "potion of experience";

    /** The known scroll of upgrade, by the name the inventory shows (items.properties). */
    static final String UPGRADE = "scroll of upgrade";

    /**
     * The offered read of a known scroll of upgrade onto the {@link #target}, or null (story 4.13). It
     * makes this Policy enter, as a testable item does: a known upgrade is not one.
     */
    static Action upgrade(Observation observation, List<Action> offered, Playbook.Upgrade upgrader) {
        return upgrade(observation, offered, upgrader, null);
    }

    /** As above, with the Codex the {@code HEAVIEST} armour is told by (#305); null reads onto the worn armour only. */
    static Action upgrade(Observation observation, List<Action> offered, Playbook.Upgrade upgrader,
                          Codex.Knowledge knowledge) {
        // Blinded or immune to magic, the game refuses the read with no time spent (Scroll.java:179-182),
        // and the refusal would be handed over forever.
        if (has(observation, BLINDED) || has(observation, MAGIC_IMMUNE)) {
            return null;
        }
        // Held for a better piece to be worn first, when the Playbook says so (story 7.4, upgrade-allocation).
        if (observation.header().depth() < upgrader.holdUntilDepth()) {
            return null;
        }
        ItemRef armour = target(observation, upgrader, knowledge);
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
     * about three armour levels a hit. The weapon whose level the screen does not show is a target only
     * when the Playbook's {@code unknown_weapon} says so, because its level is then not known to sit
     * below the armour's (story 7.4); every target is a worn piece (docs/rules/upgrades.md).
     */
    static ItemRef target(Observation observation, Playbook.Upgrade upgrader) {
        return target(observation, upgrader, null);
    }

    /**
     * As above, the armour being the piece {@link #heaviest} names when the Playbook's {@code armour_pick}
     * is {@code HEAVIEST} and {@code knowledge} is given (#305).
     */
    static ItemRef target(Observation observation, Playbook.Upgrade upgrader, Codex.Knowledge knowledge) {
        ItemRef armour = armour(observation);
        if (armour != null && upgrader.heaviestArmour() && knowledge != null) {
            armour = heaviest(observation, armour, knowledge);
        }
        ItemRef weapon = worn(observation, org.shatterfish.api.EquipSlot.WEAPON);
        if (armour == null || weapon == null) {
            return armour;
        }
        ItemView a = observation.inventory().items().get(armour.index());
        ItemView w = observation.inventory().items().get(weapon.index());
        boolean upgradable = w.kind() == ItemKind.WEAPON && (w.levelKnown() || upgrader.unknownWeapon());
        if (!upgradable) {
            return armour;
        }
        // The weapon while its level sits below the armour's plus the lead, and the tie slot on the line.
        int gap = w.visiblyUpgraded() - a.visiblyUpgraded();
        return gap < upgrader.weaponLead() || gap == upgrader.weaponLead() && upgrader.tie() == org.shatterfish.api.EquipSlot.WEAPON
                ? weapon : armour;
    }

    /**
     * The armour in the pack a scroll of upgrade does the most for (#305), the worn one {@code worn} unless
     * a spare is worth more worn one level higher, by the mean roll the equip Policy rates it at, after the
     * strength it asks. A spare is a candidate only when its level is shown and not negative: a level the
     * screen does not show is rated 0 by the equip Policy, which would never see the scroll and so never
     * put the piece on, and one shown cursed is never put on. It must also outdo the worn piece as it is now
     * by {@link Equip#swapMargin}, which is what the equip Policy asks before it swaps, or the scroll would
     * be spent on a piece that stays in the pack. On equal worth the worn piece, then the earlier one.
     */
    static ItemRef heaviest(Observation observation, ItemRef worn, Codex.Knowledge knowledge) {
        Equip equip = Equip.namer(knowledge);
        List<ItemView> pack = observation.inventory().items();
        int strength = observation.hero().strength() + observation.hero().strengthBonus();
        ItemView now = pack.get(worn.index());
        Equip.Piece wornNow = equip.piece(now);
        if (wornNow == null) {
            return worn;
        }
        long wornEffective = Equip.effective(wornNow, strength);
        long bestWorth = Equip.effective(wornNow.atLevel(level(now) + 1), strength);
        ItemRef best = worn;
        for (int index = 0; index < pack.size(); index++) {
            ItemView item = pack.get(index);
            Equip.Piece piece = equip.piece(item);
            if (index == worn.index() || item.kind() != ItemKind.ARMOR || piece == null || !item.levelKnown()
                    || item.visiblyUpgraded() < 0 || (item.cursedKnown() && item.visiblyCursed())) {
                continue;
            }
            long after = Equip.effective(piece.atLevel(item.visiblyUpgraded() + 1), strength);
            if (after > bestWorth && after - wornEffective > Equip.swapMargin(item.cursedKnown())) {
                best = new ItemRef(index, item.name(), item.quantity());
                bestWorth = after;
            }
        }
        return best;
    }

    private static int level(ItemView item) {
        return item.levelKnown() ? item.visiblyUpgraded() : 0;
    }

    /** Whether {@code ref} is the worn armour or the worn weapon: an item a read may go onto (story 4.13). */
    static boolean worn(Observation observation, ItemRef ref) {
        return ref.equals(armour(observation)) || ref.equals(worn(observation, org.shatterfish.api.EquipSlot.WEAPON));
    }

    /**
     * As above, and under a Playbook whose {@code armour_pick} is {@code HEAVIEST} an armour in the pack too:
     * the read may have gone onto a spare (#305), and the window it opens is the same chained one.
     */
    static boolean worn(Observation observation, ItemRef ref, Playbook.Upgrade upgrader) {
        List<ItemView> pack = observation.inventory().items();
        return worn(observation, ref) || upgrader.heaviestArmour() && ref.index() >= 0 && ref.index() < pack.size()
                && pack.get(ref.index()).kind() == ItemKind.ARMOR && pack.get(ref.index()).name().equals(ref.name());
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
    record Testable(ItemRef ref, int quantity, Beliefs.Guess guess, String verb, String field) {
    }

    /**
     * The appearances held that may be tested here, worth most first: a potion that can be drunk or a
     * scroll that can be read, with two candidates or more, not balked at on this floor, and not a
     * scroll while the hero is blinded or immune to magic.
     */
    List<Testable> testable(Observation observation, Memory memory) {
        Map<String, Beliefs.Guess> guesses = new LinkedHashMap<>();
        for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge, memory, playbook.tunables())) {
            guesses.put(guess.label(), guess);
        }
        Playbook.Identify identify = playbook.identify(observation.header().heroClass());
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
            // The Playbook field that lets the item be tried, which the strategy log names (story 7.3): the
            // healing odds when the hero is hurt enough, else the gain odds; for a scroll, the odds of the
            // item-picker scrolls, or the worn armour a read may go onto.
            String verb = null;
            String field = "";
            if (item.kind() == ItemKind.POTION && guess.kind() == ItemKind.POTION) {
                if (identify.potionHp(observation.hero().hp(), observation.hero().ht())
                        && odds(guess, Set.of(HEALING)) >= identify.healingOdds()) {
                    verb = DRINK;
                    field = "healing_odds_per_mille";
                } else if (odds(guess, GAINS) >= identify.gainOdds()) {
                    verb = DRINK;
                    field = "gain_odds_per_mille";
                }
            } else if (item.kind() == ItemKind.SCROLL && guess.kind() == ItemKind.SCROLL && !unreadable
                    && identify.scrollHp(observation.hero().hp(), observation.hero().ht())) {
                if (odds(guess, INVENTORY_SCROLLS) < identify.inventoryOdds()) {
                    verb = READ;
                    field = "inventory_odds_per_mille";
                } else if (armour(observation) != null) {
                    verb = READ;
                    field = "scroll_hp_per_mille";
                }
            }
            if (verb == null || !item.actions().contains(verb)) {
                continue;
            }
            testable.add(new Testable(new ItemRef(index, item.name(), item.quantity()), item.quantity(), guess, verb, field));
        }
        // A scroll likelier upgrade than not first (issue #410), then worth most first; the sort is stable, so
        // equal worths keep the pack's order.
        int upgradeFirst = playbook.tunables().getInt(org.shatterfish.api.Tunables.TEST_UPGRADE_FIRST);
        testable.sort(Comparator.<Testable>comparingInt(item -> 1000 * odds(item.guess(), Set.of(UPGRADE_CLASS)) >= upgradeFirst ? 0 : 1)
                .thenComparing(Comparator.comparingLong(TestItem::worth).reversed()));
        return testable;
    }

    /**
     * The read of a known scroll of identify onto the unknown potion worth knowing most, or null (issue #410).
     * Every class starts knowing the scroll (HeroClass.java:117), so one in the pack is a certain way to learn a
     * potion with no test: held, the potion is neither drunk to learn it nor left unknown. The target is the
     * potion likeliest to be strength or experience (a drink with a use), then the one with the most copies and
     * candidates (the same worth the tests rank by). A scroll is not a target: it is read, and a read scroll of
     * upgrade lands on the armour. A scroll not yet in the pack is not waited for: one random scroll in about six
     * is identify (Generator.java:362-377, six cards of thirty), a floor holds fewer than one scroll, so the wait
     * is about six floors, longer than a potion's use lasts; the tests therefore go on when none is held.
     */
    Action.UseItemOn identifying(Observation observation, Memory memory, List<Action> offered) {
        if (has(observation, BLINDED) || has(observation, MAGIC_IMMUNE)) {
            return null;
        }
        List<ItemView> items = observation.inventory().items();
        ItemRef scroll = null;
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.name().equals(IDENTIFY) && item.actions().contains(READ)) {
                scroll = new ItemRef(index, item.name(), item.quantity());
                break;
            }
        }
        if (scroll == null) {
            return null;
        }
        Map<String, Beliefs.Guess> guesses = new LinkedHashMap<>();
        for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge, memory, playbook.tunables())) {
            guesses.put(guess.label(), guess);
        }
        List<Testable> targets = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            Beliefs.Guess guess = guesses.get(item.name());
            if (item.kind() == ItemKind.POTION && guess != null && guess.kind() == ItemKind.POTION && guess.odds().size() >= 2) {
                targets.add(new Testable(new ItemRef(index, item.name(), item.quantity()), item.quantity(), guess, READ, "identify"));
            }
        }
        targets.sort(Comparator.<Testable>comparingDouble(item -> -odds(item.guess(), GAINS))
                .thenComparing(Comparator.comparingLong(TestItem::worth).reversed()));
        for (Testable target : targets) {
            Action.UseItemOn read = new Action.UseItemOn(scroll, READ, target.ref());
            if (offered.contains(read)) {
                return read;
            }
        }
        return null;
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
     * Whether, within the Playbook's {@code escape_waits} waits of a test this Policy handed over, the hero still has
     * to leave its own fire or gas: it is in harm, or at the cloud's edge ({@link #settled} fails for a
     * cloud), or a cloud is in sight or remembered on the floor and the hero is not yet on the refuge
     * beyond the door the test was credited with.
     */
    static boolean escaping(Observation observation, Memory memory, Playbook.Identify identify) {
        if (memory.tested() < 0 || memory.waits() - memory.tested() > identify.escapeWaits()) {
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
        // Beside a flame of the magical fire room is as bad as on one (MagicalFireRoom.java:205-220).
        if (Explore.ignited(map).contains(cell)) {
            return Explore.ETERNAL_FIRE;
        }
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
     * burning, toward the nearest water within the Playbook's {@code reach}, which puts the burning out
     * (Burning.java:98-99). Every Step brings the hero strictly nearer, so it cannot stand still.
     */
    static Action escape(Observation observation, Memory memory, List<Action> offered, Playbook.Identify identify) {
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
            target = nearest(map, distance, cell -> map.tiles().get(cell) == Tile.WATER && clean.test(cell), identify.reach());
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
