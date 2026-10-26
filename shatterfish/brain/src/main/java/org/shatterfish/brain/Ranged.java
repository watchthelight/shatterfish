package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
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
    static final int REACH = 6;

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

    Ranged(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
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
                && observation.inventory().items().stream().anyMatch(item -> item.kind() == ItemKind.MISSILE);
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
        if (target == null || best > REACH || !Fight.favourable(observation, knowledge, enemies)
                || !clear(observation, hero, target.cell())) {
            return null;
        }
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
                    || (item.levelKnown() && item.visiblyUpgraded() != 0) || 2 * tier + 7 > strength + 1) {
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
