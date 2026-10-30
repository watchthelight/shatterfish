package org.shatterfish.brain;

import org.shatterfish.api.Heuristic;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Throw what the pack holds at an enemy coming for the hero, before it arrives (issue #219, step 7:
 * use of known resources). Of 40 Warriors on Profile 5, nearly every one died holding its starting
 * throwing stones, and the thrown weapons it picked up on the way, without having thrown one.
 *
 * <p><b>Why it pays.</b> A thrown weapon is a missile weapon's hit from where the hero stands: it takes
 * one turn (Item.java:66, :705-707; MissileWeapon.java:403-409, the weapon's delay when it has a target),
 * rolls the weapon's damage ({@code 2 * tier} to {@code 5 * tier} at level 0, MissileWeapon.java:110-128)
 * and, from a cell that is not adjacent, hits one and a half times as often as it would beside the enemy
 * (MissileWeapon.java:215-232). An enemy that walks to the hero spends one turn per cell it crosses, so
 * each cell of distance is a free throw. A missile lies where it fell unless it breaks
 * (MissileWeapon.java:411-440), and the pick-up Policy collects it.
 *
 * <p><b>What it takes.</b> No Prompt is open, the nearest hostile in view that is awake and mobile is
 * between 2 and {@link #REACH} cells away, none is adjacent, the line to it is clear of walls and of any
 * other character, the fight is {@link Fight#favourable} (otherwise the fight Policy retreats, which a
 * throw would only delay), and Goo is not among the enemies, whose pump the fight Policy dodges. A
 * sleeping enemy is left asleep: it is not coming, and a throw wakes it.
 *
 * <p><b>What it throws.</b> A missile the screen names plainly ({@link #TIERS}, the plain missiles of
 * the item table), not shown cursed and not shown upgraded (a thrown weapon of known level above zero
 * asks first, MissileWeapon.java:233-250), whose strength requirement {@code 2 * tier + 7}
 * (MissileWeapon.java:130-135) is at most one over the hero's: a shortfall of one costs the accuracy a
 * factor of 1.5 (Weapon.java:289-303), which the non-adjacent factor repays. Of those, the highest tier,
 * then the most held. Not the incendiary dart (fire on the floor the hero stands on), the tipped darts
 * (their kinds are not named plainly) and nothing equipped.
 *
 * <p>It is a Choice of one: {@code UseItemAt(item, THROW, enemy cell)}, which {@link
 * org.shatterfish.api.ValidActions} offers for every character in view.
 */
final class Ranged implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "ranged";

    /** The item action that throws (Item.java:71). */
    static final String THROW = "THROW";

    /** The farthest an enemy is thrown at: far enough for several free throws, near enough to see well. */
    @Heuristic(Heuristic.UNLINKED)
    static final int REACH = 6;

    /**
     * The most steps walked in a fight to a missile the hero threw (issue #412), with no enemy beside the hero and
     * none near enough to reach it before the pick-up is done. With an enemy in view arriving on a heap does not take
     * it (Hero.java:1982-1990), so the walk is these steps and a PickUp.
     * Off (0) as committed: the rig table of the #412 pull request played worse with it on (on value 2), so it waits for the owner's word and a tuning run.
     */
    @Heuristic("missile-recover")
    static final int FETCH_STEPS = 0;

    /**
     * The least damage a throw is expected to do, in thousandths of a hit point, for it to be thrown (issue #412).
     * A throw is free while the enemy walks up, but the missile is not: a throwing stone is spent after five hits
     * (ThrowingStone.java:37) and every other one has to be picked up again. The chance to hit is the melee
     * formula's (Char.java:646-678) on the hero's accuracy times one and a half from a cell that is not adjacent
     * (MissileWeapon.java:223-233), cut by 1.5 for each point of strength short (Weapon.java:289-304), against the
     * enemy's defense skill; a stone at a snake (defense 25) from a first-level hero lands three times in ten.
     * Off (0) as committed: the rig table of the #412 pull request played worse with it on (on value 1500), so it waits for the owner's word and a tuning run.
     */
    @Heuristic("missile-throw")
    static final int MIN_DAMAGE_PER_MILLE = 0;

    /**
     * The plain missiles by the name the screen shows and their tier (the {@code tier} field of each class in
     * {@code items/weapon/missiles/}: ThrowingStone.java:36, ThrowingKnife.java:41, ThrowingSpike.java:37,
     * Dart.java:57, ThrowingClub.java:34, Shuriken.java:38, FishingSpear.java:36, Bolas.java:37, Kunai.java:39,
     * ThrowingSpear.java:34, Javelin.java:34, Tomahawk.java:41).
     */
    static final Map<String, Integer> TIERS = Map.ofEntries(Map.entry("throwing stone", 1), Map.entry("dart", 1),
            Map.entry("throwing knife", 1), Map.entry("throwing spike", 1), Map.entry("throwing club", 2),
            Map.entry("shuriken", 2), Map.entry("fishing spear", 2), Map.entry("bolas", 3), Map.entry("kunai", 3),
            Map.entry("throwing spear", 3), Map.entry("javelin", 4), Map.entry("tomahawk", 4));

    /** The tiles a thrown weapon crosses (Ballistica.PROJECTILE stops at solid ground, Ballistica.java:47). */
    private static final Set<Tile> OPEN = Set.of(Tile.EMPTY, Tile.GRASS, Tile.HIGH_GRASS, Tile.FURROWED_GRASS,
            Tile.EMBERS, Tile.EMPTY_SP, Tile.EMPTY_DECO, Tile.WATER, Tile.OPEN_DOOR, Tile.ENTRANCE, Tile.ENTRANCE_SP,
            Tile.EXIT, Tile.CHASM, Tile.PEDESTAL, Tile.EMPTY_WELL, Tile.REGION_DECO, Tile.REGION_DECO_ALT);

    private final Codex.Knowledge knowledge;
    private final org.shatterfish.api.Tunables.Vector tunables;

    Ranged(Codex.Knowledge knowledge) {
        this(knowledge, org.shatterfish.api.Tunables.Vector.REFERENCE);
    }

    /** {@link #Ranged(Codex.Knowledge)} judging a fight by the vector's {@code fight.margin_per_mille} (story 10.35). */
    Ranged(Codex.Knowledge knowledge, org.shatterfish.api.Tunables.Vector tunables) {
        this.knowledge = knowledge;
        this.tunables = tunables;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "ranged: throw";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && !Fight.enemies(observation, knowledge).isEmpty()
                && (observation.inventory().items().stream().anyMatch(item -> item.kind() == ItemKind.MISSILE)
                || !memory.missiles().isEmpty());
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        if (enemies.isEmpty() || enemies.stream().anyMatch(enemy -> Goo.NAME.equals(enemy.name()))) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        ActorView target = null;
        int best = Integer.MAX_VALUE;
        for (ActorView enemy : enemies) {
            int distance = Fight.chebyshev(map, hero, enemy.cell());
            if (distance <= 1) {
                return null;
            }
            if (enemy.emote() == Emote.SLEEP || Bestiary.immobile(knowledge, observation.header().depth(),
                    observation.header().branch(), enemy.name())) {
                continue;
            }
            if (distance < best || (distance == best && enemy.cell() < target.cell())) {
                best = distance;
                target = enemy;
            }
        }
        if (!Fight.favourable(observation, knowledge, enemies, tunables)) {
            return null;
        }
        RunLog.Choice thrown = target == null || best > REACH || !clear(observation, hero, target.cell()) ? null
                : throwAt(observation, offered, target);
        return thrown != null ? thrown : fetch(observation, memory, offered, enemies);
    }

    /** The throw at {@code target} that is worth its missile, or null. */
    private RunLog.Choice throwAt(Observation observation, List<Action> offered, ActorView target) {
        int strength = observation.hero().strength() + observation.hero().strengthBonus();
        List<ItemView> items = observation.inventory().items();
        Action throwing = null;
        String thrown = "";
        int bestTier = 0;
        int bestQuantity = 0;
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            Integer tier = item.kind() == ItemKind.MISSILE ? TIERS.get(item.name()) : null;
            if (tier == null || item.slot() != EquipSlot.NONE || (item.cursedKnown() && item.visiblyCursed())
                    || (item.levelKnown() && item.visiblyUpgraded() != 0) || 2 * tier + 7 > strength + 1
                    || 1000 * expected(observation, target, item.name(), tier, strength)
                    < tunables.getInt(org.shatterfish.api.Tunables.RANGED_MIN_DAMAGE)) {
                continue;
            }
            Action throwIt = new Action.UseItemAt(new ItemRef(index, item.name(), item.quantity()), THROW, target.cell());
            if (offered.contains(throwIt) && (tier > bestTier || (tier == bestTier && item.quantity() > bestQuantity))) {
                throwing = throwIt;
                thrown = item.name();
                bestTier = tier;
                bestQuantity = item.quantity();
            }
        }
        return throwing == null ? null : new RunLog.Choice(throwing, Policies.CERTAIN, "throw: " + thrown);
    }

    /**
     * The damage a throw of {@code name}, of {@code tier}, is expected to do to {@code target} from a cell that is not
     * adjacent: the chance to hit times the damage that gets through its armour. A dart rolls 1 to 2
     * (Dart.java:95-97), any other plain missile {@code 2 * tier} to {@code 5 * tier} (MissileWeapon.java:110-128).
     */
    double expected(Observation observation, ActorView target, String name, int tier, int strength) {
        Codex.Threat threat = Fight.threat(observation, knowledge, target);
        int encumbrance = Math.max(0, 2 * tier + 7 - strength);
        long accuracy = Math.round(Fight.heroAccuracy(observation.hero()) * 1.5 / Math.pow(1.5, encumbrance));
        int min = name.equals("dart") ? 1 : 2 * tier;
        int max = name.equals("dart") ? 2 : 5 * tier;
        return Fight.hitChance((int) accuracy, threat.defense()) * Fight.landed(min, max, threat.drMin(), threat.drMax());
    }

    /**
     * The Step toward, or the PickUp of, the nearest missile the hero threw that lies within {@link #FETCH_STEPS} and that
     * no awake enemy can reach before the pick-up is done, or null (issue #412).
     */
    private RunLog.Choice fetch(Observation observation, Memory memory, List<Action> offered, List<ActorView> enemies) {
        int steps = tunables.getInt(org.shatterfish.api.Tunables.RANGED_FETCH_STEPS);
        if (steps <= 0 || memory.missiles().isEmpty()) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        boolean[] walk = Explore.walkable(observation, memory, 0, false);
        int[] distance = Pickup.distances(map, walk, hero);
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        HeapView best = null;
        for (HeapView heap : map.heaps()) {
            int d = heap.cell() < distance.length ? distance[heap.cell()] : -1;
            if (d < 0 || d > steps || !Missiles.own(memory, observation, heap)
                    || !Pickup.takeable(heap, observation, memory, tunables)
                    || (best != null && (d > distance[best.cell()] || (d == distance[best.cell()] && heap.cell() > best.cell())))) {
                continue;
            }
            boolean safe = true;
            for (ActorView enemy : enemies) {
                if (enemy.emote() == Emote.SLEEP || Bestiary.immobile(knowledge, depth, branch, enemy.name())) {
                    continue;
                }
                int away = Fight.chebyshev(map, hero, enemy.cell());
                int turns = d + 1;
                if (away < (Bestiary.fast(knowledge, depth, branch, enemy.name()) ? 2 * turns + 1 : turns + 2)) {
                    safe = false;
                }
            }
            if (safe) {
                best = heap;
            }
        }
        if (best == null) {
            return null;
        }
        String label = Beliefs.untitled(best.item());
        if (best.cell() == hero) {
            Action take = new Action.PickUp();
            return offered.contains(take) ? new RunLog.Choice(take, Policies.CERTAIN, "fetch: " + label) : null;
        }
        Action step = Pickup.firstStep(map, walk, hero, best.cell(), offered);
        return step == null ? null : new RunLog.Choice(step, Policies.CERTAIN, "fetch: " + label + " " + distance[best.cell()]);
    }

    /**
     * Whether the straight line from {@code from} to {@code to} crosses only open ground and no character. The
     * game's own line (Ballistica) differs from this one by a cell here and there; a throw that stops short
     * costs a turn, no more.
     */
    static boolean clear(Observation observation, int from, int to) {
        MapSection map = observation.map();
        int width = map.width();
        int x0 = from % width;
        int y0 = from / width;
        int x1 = to % width;
        int y1 = to / width;
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        List<Integer> occupied = new ArrayList<>();
        for (ActorView actor : observation.actors().actors()) {
            occupied.add(actor.cell());
        }
        for (int step = 1; step < steps; step++) {
            int x = x0 + Math.round((float) (x1 - x0) * step / steps);
            int y = y0 + Math.round((float) (y1 - y0) * step / steps);
            int cell = x + y * width;
            if (!OPEN.contains(map.tiles().get(cell)) || occupied.contains(cell)) {
                return false;
            }
        }
        return true;
    }
}
