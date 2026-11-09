package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Heuristic;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Use what the pack holds against an enemy the hero will not outfight (story 10.48, issue #458). Of the 24 heroes a
 * Prison Guard killed in the story 10.43 Runs, most carried liquid flame, frost or an attack wand and had thrown or
 * zapped none: the only Policies that use an item against an enemy throw plain missile weapons ({@link Ranged}) and
 * zap the Mage's staff ({@link Zap}).
 *
 * <p><b>What the game does.</b> A thrown known potion shatters on the first character or wall on its line
 * ({@code Item.java:638-640}, {@code Ballistica.java:47}). Liquid flame seeds fire of 2 on the 3 by 3 around the
 * landing cell ({@code PotionOfLiquidFlame.java:50-54}); fire spreads only from a burning cell to a flammable
 * orthogonal neighbour ({@code Fire.java:72-80}), so on bare floor it stays in the 3 by 3. A character in fire is set
 * burning for eight turns ({@code Burning.java:54}, {@code Fire.java:99-103}), for 1 to {@code 3 + depth / 4} a
 * turn that no armour takes from ({@code Burning.java:104-108}, {@code :149}), and a burning mob goes on attacking
 * ({@code Mob.java} has no Burning rule); a burning character on flammable ground seeds fire under itself
 * ({@code Burning.java:171-173}), and standing on water puts the burning out ({@code Burning.java:99}). Frost seeds
 * a cloud of 10 on the same 3 by 3 that lasts about ten turns and does not spread; each turn in it adds 3 turns of
 * chill, which slows by 10 percent a turn left up to 50, and a character that reaches 10 turns of chill is frozen for
 * 10 more ({@code PotionOfFrost.java:49-53}, {@code Freezing.java:39-94}, {@code Chill.java:45-48}). The wands
 * are bolts: magic missile and disintegration roll 2 to 8 at level 0 ({@code WandOfMagicMissile.java:47-52},
 * {@code WandOfDisintegration.java:55-60}), lightning 5 to 10 and strikes the hero only when it stands next to a
 * character the arc reaches ({@code WandOfLightning.java:62-67}, {@code :93}), frost 2 to 8 with chill
 * ({@code WandOfFrost.java:50-55}). A zap with no charge fizzles and takes no time ({@code Wand.java:158-162}); a
 * wand starts with 2 charges, magic missile 3 ({@code Wand.java:433-435}); and a wand that shows no level shows no
 * count, so the charges are found out by the fizzle.
 *
 * <p><b>What it takes.</b> No Prompt is open and the floor is not sealed (the boss fights have their Policies); the
 * enemies in view are not a Goo; the nearest hostile that is awake and mobile is 2 to {@link #REACH} cells away on a
 * clear line, none is beside the hero; the fight is not {@link Fight#favourable} (otherwise the hero takes it, and
 * the pack is kept for one it will not); and the hero would need {@link #STRONG_TURNS} turns or more to kill it (a rat dies in two or three blows
 * and is not worth a potion; a crab, a gnoll or a Guard is).
 *
 * <p><b>What it uses, in order.</b> Known liquid flame, thrown from {@link #CLOUD_MIN} cells or more, at an enemy that
 * is not burning, not frozen or chilled and not standing on water, and only when the fire cannot come back: the
 * hero's own cell is not flammable and no flammable ground joined to the landing cells (grass, high grass, a door,
 * as drawn) lies within one cell of the hero. Known frost, from the same distance, at an enemy that is neither
 * burning nor chilled nor frozen. Then a known wand of lightning, disintegration, magic missile or frost that is not
 * shown cursed and does not show an empty count, at an enemy that is not frozen (damage thaws it,
 * {@code Char.java:898-900}). The wands that heal, summon, root or burn the ground are left alone. A zap that the
 * log shows fizzling in its last {@link #FIZZLE_LINES} lines is not tried again.
 */
final class Pack implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "pack";

    /** The known potions thrown, by the name the inventory shows once their kind is known (items.properties). */
    static final String FLAME = "potion of liquid flame";
    static final String FROST = "potion of frost";

    /** What a bolt wand needs from the line to its target. */
    private record Bolt(int reach, int nearest) {
    }

    /**
     * The wands zapped, best first, with the farthest cell the bolt carries and the nearest cell it is zapped from:
     * disintegration reaches {@code 6 + 2 * level} cells (WandOfDisintegration.java:137-139), lightning is kept three
     * cells off so its arc cannot reach the hero (WandOfLightning.java:93, :145-168).
     */
    private static final Map<String, Bolt> BOLTS = Map.of(
            "wand of lightning", new Bolt(7, 3),
            "wand of disintegration", new Bolt(6, 2),
            "wand of magic missile", new Bolt(7, 2),
            "wand of frost", new Bolt(7, 2));

    private static final List<String> BOLT_ORDER = List.of("wand of lightning", "wand of disintegration",
            "wand of magic missile", "wand of frost");

    /** The buffs an enemy shows, by name (actors.properties). */
    private static final String BURNING = "burning";
    private static final String CHILLED = "chilled";
    private static final String FROZEN = "frozen";

    /** The frost cloud, by the blob class name the Observation carries. */
    static final String FREEZING = "Freezing";

    /** The farthest cell used on, in Chebyshev cells. */
    @Heuristic("pack-use")
    static final int REACH = 7;

    /**
     * The nearest cell a potion is thrown at: the cloud is the 3 by 3 around the landing cell, so from three cells off
     * the hero is two cells from its edge (PotionOfLiquidFlame.java:50-54, PotionOfFrost.java:49-53).
     */
    @Heuristic("pack-use")
    static final int CLOUD_MIN = 3;

    /** Turns the hero is expected to need to kill an enemy for it to count as strong enough to spend the pack on. */
    @Heuristic("pack-use")
    static final int STRONG_TURNS = 4;

    /** The newest log lines searched for a fizzle (Wand.java:158-162): a wand that fizzled takes about 45 turns to recharge (Wand.java:803-809). */
    @Heuristic("pack-use")
    static final int FIZZLE_LINES = 12;

    /** Whether the Goo fight uses the pack too ({@link Goo#volley}). */
    static final boolean GOO = false;

    private final Codex.Knowledge knowledge;
    private final org.shatterfish.api.Tunables.Vector tunables;

    Pack(Codex.Knowledge knowledge, org.shatterfish.api.Tunables.Vector tunables) {
        this.knowledge = knowledge;
        this.tunables = tunables;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "pack: use";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && !observation.header().sealed()
                && holds(observation) && !Fight.enemies(observation, knowledge).isEmpty();
    }

    /** Whether the pack holds a potion or a wand this Policy knows how to use. */
    private static boolean holds(Observation observation) {
        for (ItemView item : observation.inventory().items()) {
            if ((item.kind() == ItemKind.POTION && (item.name().equals(FLAME) || item.name().equals(FROST)))
                    || (item.kind() == ItemKind.WAND && BOLTS.containsKey(item.name()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        if (enemies.isEmpty() || observation.header().sealed()
                || enemies.stream().anyMatch(enemy -> Goo.NAME.equals(enemy.name()))) {
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
        if (target == null || best > REACH || !Ranged.clear(observation, hero, target.cell())
                || Fight.turnsToKill(observation, knowledge, target) < STRONG_TURNS
                || Fight.favourable(observation, knowledge, enemies, tunables)) {
            return null;
        }
        return useAt(observation, memory, offered, target, "");
    }

    /**
     * The best use of the pack on {@code target}, a clear line away, or null: known liquid flame, then known frost,
     * then a bolt wand. The reason is {@code why} and the item's name. Shared with the Goo fight when {@link #GOO}.
     */
    static RunLog.Choice useAt(Observation observation, Memory memory, List<Action> offered, ActorView target, String why) {
        int distance = Fight.chebyshev(observation.map(), observation.hero().cell(), target.cell());
        boolean burning = has(target, BURNING);
        boolean cold = has(target, CHILLED) || has(target, FROZEN);
        if (distance >= CLOUD_MIN && !burning && !cold) {
            if (fireSafe(observation, target.cell())) {
                RunLog.Choice flame = hurl(observation, offered, target, FLAME, "hurl: " + why);
                if (flame != null) {
                    return flame;
                }
            }
        }
        if (distance >= CLOUD_MIN && !burning && !cold) {
            RunLog.Choice frost = hurl(observation, offered, target, FROST, "hurl: " + why);
            if (frost != null) {
                return frost;
            }
        }
        if (has(target, FROZEN) || fizzled(observation) || Zap.zappedWithoutEffect(memory)) {
            return null;
        }
        List<ItemView> items = observation.inventory().items();
        for (String kind : BOLT_ORDER) {
            Bolt bolt = BOLTS.get(kind);
            if (distance < bolt.nearest() || distance > bolt.reach() || (kind.equals("wand of frost") && cold)) {
                continue;
            }
            for (int index = 0; index < items.size(); index++) {
                ItemView item = items.get(index);
                if (item.kind() != ItemKind.WAND || !item.name().equals(kind) || (item.cursedKnown() && item.visiblyCursed())
                        || !(item.status().isEmpty() || item.status().startsWith("?") || Zap.charged(item.status()))) {
                    continue;
                }
                Action zap = new Action.UseItemAt(new ItemRef(index, item.name(), item.quantity()), Zap.ZAP, target.cell());
                if (offered.contains(zap)) {
                    return new RunLog.Choice(zap, Policies.CERTAIN, "zap: " + why + item.name());
                }
            }
        }
        return null;
    }

    /** The throw of the known potion called {@code name} at {@code target}, or null when none is held or offered. */
    private static RunLog.Choice hurl(Observation observation, List<Action> offered, ActorView target, String name, String why) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            if (item.kind() == ItemKind.POTION && item.name().equals(name)) {
                Action throwIt = new Action.UseItemAt(new ItemRef(index, item.name(), item.quantity()), Ranged.THROW, target.cell());
                if (offered.contains(throwIt)) {
                    return new RunLog.Choice(throwIt, Policies.CERTAIN, why + name);
                }
            }
        }
        return null;
    }

    private static boolean has(ActorView actor, String buff) {
        for (BuffView view : actor.buffs()) {
            if (view.name().equals(buff)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the newest lines of the log show a wand fizzling (Wand.java:158-162; items.properties: "Your wand fizzles"). */
    static boolean fizzled(Observation observation) {
        List<LogLine> lines = observation.log().lines();
        for (int i = Math.max(0, lines.size() - FIZZLE_LINES); i < lines.size(); i++) {
            if (lines.get(i).text().contains("fizzles")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether liquid flame landing on {@code impact} cannot set the hero alight, from the tiles as drawn: fire runs only
     * over flammable ground (Fire.java:72-80), and a burning enemy seeds fire under itself on flammable ground
     * (Burning.java:171-173). So the hero's own cell must not be flammable, nor the landing cell's water (which puts
     * the burning out at once, Burning.java:99), and no flammable cell connected by orthogonal neighbours to the 3 by 3
     * around the landing cell may lie within one cell of the hero.
     */
    static boolean fireSafe(Observation observation, int impact) {
        MapSection map = observation.map();
        int width = map.width();
        int cells = map.tiles().size();
        int hero = observation.hero().cell();
        if (Douse.FLAMMABLE.contains(map.tiles().get(hero)) || map.tiles().get(impact) == Tile.WATER) {
            return false;
        }
        Set<Integer> reached = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = impact % width + dx;
                int y = impact / width + dy;
                int cell = x + y * width;
                if (x >= 0 && x < width && y >= 0 && cell >= 0 && cell < cells
                        && Douse.FLAMMABLE.contains(map.tiles().get(cell)) && reached.add(cell)) {
                    queue.add(cell);
                }
            }
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            int x = cell % width;
            int[] next = {x > 0 ? cell - 1 : -1, x < width - 1 ? cell + 1 : -1, cell - width, cell + width};
            for (int n : next) {
                if (n >= 0 && n < cells && Douse.FLAMMABLE.contains(map.tiles().get(n)) && reached.add(n)) {
                    queue.add(n);
                }
            }
        }
        for (int cell : reached) {
            if (Fight.chebyshev(map, hero, cell) <= 1) {
                return false;
            }
        }
        return true;
    }
}
