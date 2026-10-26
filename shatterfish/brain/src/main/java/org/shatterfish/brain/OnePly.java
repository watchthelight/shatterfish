package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The one-ply expectimax over Codex tables (story 6.1, ADR-0010 option 2, FR-34): the fight Policy
 * that scores each way of spending the hero's next turn against what the enemies in view are expected
 * to do about it, and takes the best. It needs no engine and reads nothing the screen does not show:
 * an {@link Observation} and the {@link Codex.Knowledge}, whose hit table is the Codex's
 * ({@code combat.json}, tabulated from the two draws of {@code Char.hit}, Char.java:646, :662, :678)
 * and whose damage ranges are the Codex's measured gear and mobs.
 *
 * <p><b>The options</b> are the Actions the screen offers that a fight turns on, not a hand-picked
 * list: an {@link Action.Attack} on each enemy in view; holding the cell ({@link Action.Wait}, or a
 * search where a wait is not offered); a {@link Action.Step} onto a cell the hero may walk on, which
 * is every way of closing, repositioning or retreating along a visible path; the stairs when the
 * fight Policy's own retreat would take them ({@link Fight#retreat}); and a drink of a healing
 * potion the inventory shows identified.
 *
 * <p><b>The score</b> of an option is the hero's expected hit points after the turn, less the
 * expected hit points the enemies in view have left, less the hero's whole maximum for the chance it
 * dies (an assumption). The turn is a chance tree. The hero's swing, if the option is an attack, lands
 * with the table's chance for the hero's accuracy against the enemy's evasion and then takes off the
 * expected landed damage ({@link Fight#landed}) of the weapon's measured range less the enemy's
 * reduction; an enemy it kills does not reply. Each enemy still standing and awake then replies if it
 * can reach the hero's cell after the option: it stands beside it, or it is tagged to hurt from where
 * it stands or fly ({@link Bestiary#atRange}), or it is tagged fast and stands two cells away
 * (docs/bestiary/index.md: a speed-2 mob closes two cells and still attacks, Mob.java:790-794,
 * Char.java:770-783). Its reply lands with the table's chance for its accuracy against the hero's
 * evasion and takes off the expected landed damage of its range less the hero's armour. The four
 * strongest repliers are enumerated, hit and miss; the rest are taken at their expectation. An enemy
 * not beside the hero spends its turn walking, so a hero that steps out of reach takes no reply: that
 * is what makes a retreat worth something, and a hold worth more than an approach, since the enemy
 * that walks into reach gives the hero the first swing.
 *
 * <p><b>The enemy's health</b> is its health-bar bucket, rounded up, of the hit points the Codex
 * records for its kind ({@link Fight#health}), never a true value; it never targets a character the
 * Observation does not show; an enemy the game keeps passive until provoked is scenery
 * ({@link Fight#enemies}).
 *
 * <p><b>Ties</b> are broken by what the cell costs once the enemies have closed on it (the damage the
 * awake enemies that can engage it at once are expected to deal, as {@link Fight#favourable} counts
 * them), then, with two or more awake, by how many cells can engage it (so a corridor beats an open
 * floor, which is the cell the fight Policy's chokepoint takes), then by a fixed order of the kinds of
 * option (attack, drink, hold, step, stairs), then, among steps, the farther from the enemies in view,
 * then by the order the screen offers them.
 *
 * <p><b>When it acts.</b> At an Input wait, with no Prompt open, when an enemy is beside the hero, or an
 * awake enemy that walks (not tagged immobile, not tagged to hurt from afar, which the ranged Policy and
 * the fight Policy's approach answer) is within {@link #ENGAGE} cells. Quiet turns are the other
 * Policies', and so are the Goo fight ({@link Goo#engage}) and a hold that has gone on for
 * {@link Fight#HOLDS} waits with nothing beside the hero: an enemy that does not come is approached by
 * the fight Policy below it. A sealed floor with no retreat left to make is fought, not fled in circles
 * (issue #180).
 *
 * <p>Assumptions, each a simplification and not a Codex fact: every attack takes one turn on both
 * sides; the hero's accuracy and evasion are its level's, without the weapon's or armour's factors, rings
 * or talents; special attacks, status effects and abilities are not modelled; an enemy with no Codex
 * figures is taken at {@link Fight#assumed}; and the hero's death costs its maximum hit points.
 */
final class OnePly implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames(Brain.Model)} lists it. */
    static final String NAME = "one-ply";

    /** How near, in cells, an awake enemy that walks must be for the Policy to take the wait. */
    static final int ENGAGE = 3;

    /** The most repliers whose hits and misses are enumerated; the rest are taken at their expectation. */
    static final int ENUMERATED = 4;

    /** The cost of dying, in the unit of hit points, as a share of the hero's maximum, in thousandths. An assumption. */
    static final int DEATH_PER_MILLE = 1000;

    private final Codex.Knowledge knowledge;
    private final Codex.HitTable hits;

    OnePly(Codex.Knowledge knowledge) {
        if (knowledge.hit() == null) {
            throw new IllegalArgumentException("the one-ply model reads the Codex's hit table (combat.json), and the Codex it was"
                    + " given recorded none; regenerate it with ./gradlew :codex:generate");
        }
        this.knowledge = knowledge;
        this.hits = knowledge.hit();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "fight: expected hp";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        if (observation.header().prompt() != PromptKind.NONE) {
            return false;
        }
        int hero = observation.hero().cell();
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (ActorView enemy : Fight.enemies(observation, knowledge)) {
            int distance = Fight.chebyshev(map, hero, enemy.cell());
            if (distance <= 1) {
                return true;
            }
            if (distance <= ENGAGE && enemy.emote() != Emote.SLEEP && !Bestiary.immobile(knowledge, depth, branch, enemy.name())
                    && !Bestiary.atRange(knowledge, depth, branch, enemy.name())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<RunLog.Choice> ranked = ranked(observation, memory, offered, stream);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** One enemy as the model sees it: its Codex figures, its bar, and what each side's swing is worth. */
    private record Foe(ActorView enemy, double health, boolean awake, boolean afar, int reach, double heroHit, double heroDamage,
                       double hit, double damage) {

        /** What its reply is expected to take off the hero. */
        double expected() {
            return hit * damage;
        }
    }

    /** One way of spending the turn. */
    private record Option(Action action, String why, int rank, int cell, ActorView target, int heal, boolean leaves) {
    }

    /** An option with its scores. */
    private record Scored(Option option, long score, long exposure, int crowding, int farness, int order) {
    }

    @Override
    public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        if (enemies.isEmpty()) {
            return List.of();
        }
        // The Goo fight is the fight Policy's own (story 4.13, issue #219): its dodge is not a thing hit
        // points can score.
        if (Goo.engage(observation, memory, offered, enemies) != null) {
            return List.of();
        }
        HeroSection hero = observation.hero();
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<Foe> foes = new ArrayList<>();
        for (ActorView enemy : enemies) {
            foes.add(foe(observation, enemy, depth, branch));
        }
        boolean beside = foes.stream().anyMatch(foe -> Fight.chebyshev(map, hero.cell(), foe.enemy().cell()) <= 1);
        // An enemy that does not come: held for as long as the fight Policy holds, then approached by it.
        if (!beside && memory.holds() >= Fight.HOLDS) {
            return List.of();
        }
        RunLog.Choice flee = Fight.retreat(observation, memory, offered, enemies, knowledge);
        List<Option> options = options(observation, memory, offered, foes, flee);
        if (options.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            scored.add(new Scored(option, micro(value(hero, foes, option, map)), micro(exposure(foes, option.cell(), map)),
                    crowding(map, foes, option.cell()), farness(map, foes, option.cell()), i));
        }
        scored.sort(Comparator.comparingLong(Scored::score).reversed()
                .thenComparingLong(Scored::exposure)
                .thenComparingInt(Scored::crowding)
                .thenComparingInt((Scored s) -> s.option().rank())
                .thenComparing(Comparator.comparingInt(Scored::farness).reversed())
                .thenComparingInt(Scored::order));
        List<RunLog.Choice> ranked = new ArrayList<>();
        for (Scored s : scored) {
            if (ranked.stream().noneMatch(choice -> choice.action().equals(s.option().action()))) {
                ranked.add(new RunLog.Choice(s.option().action(), ranked.isEmpty() ? Policies.CERTAIN : 0, s.option().why()));
            }
        }
        return ranked;
    }

    /** The model's figures for one enemy in view. */
    private Foe foe(Observation observation, ActorView enemy, int depth, int branch) {
        Codex.Threat threat = Fight.threat(observation, knowledge, enemy);
        int[] damage = Fight.heroDamage(observation, knowledge);
        int[] armour = Fight.heroArmour(observation, knowledge);
        Codex.Tactics tactics = Bestiary.of(knowledge, depth, branch, enemy.name());
        boolean afar = Bestiary.atRange(knowledge, depth, branch, enemy.name());
        int reach = 1;
        if (tactics != null) {
            reach = (tactics.attack().equals(Bestiary.RANGED) || tactics.attack().equals(Bestiary.BOLT) ? 1 : Math.max(1, tactics.reach()))
                    + (tactics.speed().equals("fast") ? 1 : 0);
        }
        return new Foe(enemy, Math.max(1, Fight.health(threat, enemy)), enemy.emote() != Emote.SLEEP, afar, reach,
                chance(Fight.heroAccuracy(observation.hero()), threat.defense()),
                Fight.landed(damage[0], damage[1], threat.drMin(), threat.drMax()),
                chance(threat.attack(), Fight.heroEvasion(observation.hero())),
                Fight.landed(threat.damageMin(), threat.damageMax(), armour[0], armour[1]));
    }

    /** The table's chance that an attack of {@code accuracy} lands on {@code evasion}. */
    private double chance(int accuracy, int evasion) {
        return hits.chance(accuracy, evasion).orElseThrow();
    }

    /** The options this screen offers that a fight turns on, in the fixed order of their kinds. */
    private List<Option> options(Observation observation, Memory memory, List<Action> offered, List<Foe> foes, RunLog.Choice flee) {
        HeroSection hero = observation.hero();
        MapSection map = observation.map();
        List<Option> options = new ArrayList<>();
        for (Foe foe : foes) {
            Action.Attack attack = new Action.Attack(foe.enemy().cell());
            if (offered.contains(attack)) {
                options.add(new Option(attack, "attack: " + foe.enemy().name(), 0, hero.cell(), foe.enemy(), 0, false));
            }
        }
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size() && hero.hp() < hero.ht(); index++) {
            ItemView item = items.get(index);
            if (item.name().equals(Heal.POTION)) {
                Action drink = new Action.UseItem(new ItemRef(index, item.name(), item.quantity()), "DRINK");
                if (offered.contains(drink)) {
                    options.add(new Option(drink, "heal " + hero.hp() + "/" + hero.ht(), 1, hero.cell(), null,
                            Math.min(Heal.firstTurn(hero.ht()), hero.ht() - hero.hp()), false));
                    break;
                }
            }
        }
        for (Action hold : List.of(new Action.Wait(), new Action.Search())) {
            if (offered.contains(hold)) {
                options.add(new Option(hold, "hold: wait", 2, hero.cell(), null, 0, false));
                break;
            }
        }
        // On a sealed floor a walk away cannot break contact, and the fight Policy stops retreating after a
        // few (issue #180); with no retreat left, no Step is a retreat either.
        boolean sealedOut = observation.header().sealed() && flee == null;
        if (!sealedOut) {
            boolean[] walk = Explore.walkable(observation, memory, false);
            int here = nearestAwake(map, foes, hero.cell());
            for (Action action : offered) {
                if (action instanceof Action.Step step && step.cell() < walk.length && walk[step.cell()]
                        && map.transitions().stream().noneMatch(t -> t.cell() == step.cell())) {
                    int there = nearestAwake(map, foes, step.cell());
                    options.add(new Option(step, there > here ? "retreat " + there : "reposition " + Fight.engage(map, step.cell()),
                            3, step.cell(), null, 0, false));
                }
            }
        }
        if (flee != null && (flee.action() instanceof Action.Descend || flee.action() instanceof Action.Ascend)) {
            options.add(new Option(flee.action(), flee.why(), 4, hero.cell(), null, 0, true));
        }
        return options;
    }

    /** The Chebyshev distance from {@code cell} to the nearest awake enemy in view, or a large number with none. */
    private static int nearestAwake(MapSection map, List<Foe> foes, int cell) {
        int best = Integer.MAX_VALUE / 2;
        for (Foe foe : foes) {
            if (foe.awake()) {
                best = Math.min(best, Fight.chebyshev(map, cell, foe.enemy().cell()));
            }
        }
        return best;
    }

    private static int farness(MapSection map, List<Foe> foes, int cell) {
        int total = 0;
        for (Foe foe : foes) {
            total += Fight.chebyshev(map, cell, foe.enemy().cell());
        }
        return total;
    }

    /** Whether {@code foe} replies to a hero standing on {@code cell}: awake, and in reach of it. */
    private static boolean replies(MapSection map, Foe foe, int cell) {
        return foe.awake() && (foe.afar() || Fight.chebyshev(map, cell, foe.enemy().cell()) <= foe.reach());
    }

    /**
     * The expected value of an option: the hero's hit points after the turn less the enemies', over the
     * chance tree of the hero's swing and the replies, less the cost of the hero dying.
     */
    private double value(HeroSection hero, List<Foe> foes, Option option, MapSection map) {
        double total = 0;
        double[][] swings = option.target() == null ? new double[][] {{1, 0}}
                : swing(foes, option.target());
        for (double[] swing : swings) {
            double probability = swing[0];
            boolean landed = swing[1] > 0;
            double enemyHealth = 0;
            List<Foe> repliers = new ArrayList<>();
            for (Foe foe : foes) {
                double health = foe.health();
                if (option.target() != null && foe.enemy().equals(option.target()) && landed) {
                    health = Math.max(0, health - foe.heroDamage());
                }
                enemyHealth += health;
                if (health > 0 && !option.leaves() && replies(map, foe, option.cell())) {
                    repliers.add(foe);
                }
            }
            repliers.sort(Comparator.comparingDouble(Foe::expected).reversed());
            double rest = 0;
            for (int i = ENUMERATED; i < repliers.size(); i++) {
                rest += repliers.get(i).expected();
            }
            int enumerated = Math.min(ENUMERATED, repliers.size());
            double heroValue = 0;
            for (int mask = 0; mask < (1 << enumerated); mask++) {
                double chance = 1;
                double taken = rest;
                for (int i = 0; i < enumerated; i++) {
                    Foe foe = repliers.get(i);
                    if ((mask & (1 << i)) != 0) {
                        chance *= foe.hit();
                        taken += foe.damage();
                    } else {
                        chance *= 1 - foe.hit();
                    }
                }
                double left = hero.hp() - taken;
                heroValue += chance * (left <= 0 ? -(double) hero.ht() * DEATH_PER_MILLE / 1000
                        : Math.min(hero.ht(), left + option.heal()));
            }
            total += probability * (heroValue - enemyHealth);
        }
        return total;
    }

    /** The two outcomes of the hero's swing at {@code target}: {probability, 1 if it landed}. */
    private static double[][] swing(List<Foe> foes, ActorView target) {
        for (Foe foe : foes) {
            if (foe.enemy().equals(target)) {
                return new double[][] {{foe.heroHit(), 1}, {1 - foe.heroHit(), 0}};
            }
        }
        return new double[][] {{1, 0}};
    }

    /**
     * What the hero is expected to take a turn once every awake enemy in view has closed on it, standing
     * on {@code cell}: the strongest of them, as many as can engage the cell at once ({@link Fight#engage},
     * as {@link Fight#favourable} counts them).
     */
    private double exposure(List<Foe> foes, int cell, MapSection map) {
        List<Double> incoming = new ArrayList<>();
        for (Foe foe : foes) {
            if (foe.awake()) {
                incoming.add(foe.expected());
            }
        }
        incoming.sort(Comparator.reverseOrder());
        int engaging = Math.min(incoming.size(), Math.max(1, Fight.engage(map, cell)));
        double total = 0;
        for (int i = 0; i < engaging; i++) {
            total += incoming.get(i);
        }
        return total;
    }

    /**
     * How many cells enemies could engage {@code cell} from, when two or more are awake and the number
     * matters (a corridor lets them come one at a time, which the expectation above cannot see); 0 with
     * fewer, so that a lone enemy never draws the hero toward a wall.
     */
    private static int crowding(MapSection map, List<Foe> foes, int cell) {
        return foes.stream().filter(Foe::awake).count() >= 2 ? Fight.engage(map, cell) : 0;
    }

    /** A score in millionths of a hit point, so that float noise cannot reorder two options that are equal. */
    private static long micro(double value) {
        return Math.round(value * 1_000_000d);
    }
}
