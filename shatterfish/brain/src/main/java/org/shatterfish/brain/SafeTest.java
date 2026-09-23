package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The worst-case check (story 4.3, FR-30): what is the worst that can happen if the hero tries an
 * unidentified item here, and is it survivable?
 *
 * <p>An item is tried where the hero stands: a potion drunk seeds its effect on the hero's cell
 * (a harmful potion's {@code apply} is its {@code shatter} at {@code hero.pos}), a scroll read
 * acts from there, and a wand zapped with a hidden curse turns on the hero. So the check reads the
 * hero's cell and its neighbours, the enemies in view, the hero's hit points and the depth -- all
 * on the screen -- and the candidate identities with their odds, from {@link Beliefs}.
 *
 * <p>Every candidate goes through the same code: potions, scrolls and wands are each a list of
 * {@link Candidate}s, and {@link #harm} knows each identity's worst case by class. The verdict
 * looks only at the worst case, never at the mean: an item that is healing nine times in ten and
 * liquid flame the tenth is refused if the flame would kill, however good the mean.
 *
 * <p>The worst cases are a hand-written table read from the pinned code, not generated; each is
 * cited below and in {@code docs/rules/identification.md}. Two numbers are assumptions and say so:
 * how long the hero stays in a toxic cloud ({@link #GAS_TURNS}) and that being disabled beside an
 * enemy is treated as lethal. The function is pure: it reads its arguments and nothing else.
 */
public final class SafeTest {

    /** One identity the item may have, its class as the Codex names it, and its probability. */
    public record Candidate(String className, String name, double probability) {
    }

    /**
     * The worst an identity can do here: the hit points it can cost, whether it leaves the hero
     * unable to act or to see (paralysed, frozen, blinded), whether it draws enemies, and why.
     */
    public record Harm(String name, int damage, boolean disables, boolean draws, String why) {

        /** An identity that does the hero no harm. */
        static Harm none(String name) {
            return new Harm(name, 0, false, false, "harmless");
        }
    }

    /**
     * The check's answer: whether to try the item, the worst case and what it would cost, the mean
     * damage over the candidates, and each candidate's harm, worst first.
     */
    public record Verdict(boolean safe, Harm worst, double meanDamage, List<Harm> harms, String why) {
    }

    /** The class a cursed wand's zap is modelled as; not a Codex class, a stand-in. */
    public static final String CURSED_WAND = "wand (cursed)";

    /** An uncursed wand's zap: it fires at its target and never at the hero. */
    public static final String WAND = "wand";

    /**
     * How many turns a hero caught in its own toxic cloud stays in it: an assumption. A potion seeds
     * 1000 units of gas on the hero's cell (PotionOfToxicGas.java:49), which spreads over the room
     * and lasts well past the turns a hero needs to walk out of it; ten is the walk out of a room.
     */
    static final int GAS_TURNS = 10;

    /** A wand's chance to be cursed when generated (Wand.java:562-563), for the mean only. */
    static final double WAND_CURSE_CHANCE = 0.3;

    private SafeTest() {
    }

    /** The candidates for an unidentified potion or scroll, from its guess and the Codex's class names. */
    public static List<Candidate> candidates(Beliefs.Guess guess, Codex.Knowledge knowledge) {
        List<Candidate> candidates = new ArrayList<>();
        for (Beliefs.Odds odds : guess.odds()) {
            String className = knowledge.families().stream()
                    .filter(family -> family.kind() == guess.kind())
                    .flatMap(family -> family.candidates().stream())
                    .filter(candidate -> candidate.name().equals(odds.name()))
                    .map(Codex.Candidate::className).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("the Codex names no " + odds.name()));
            candidates.add(new Candidate(className, odds.name(), odds.probability()));
        }
        return candidates;
    }

    /**
     * The candidates for a wand. A wand's kind is never hidden -- wands are not shuffled -- but
     * whether it is cursed is, until it is used; a zap of a cursed wand runs a cursed effect instead
     * (Wand.java:753-765). A wand whose curse is shown has one candidate; one whose curse is hidden
     * has two, cursed with the generator's chance.
     */
    public static List<Candidate> wand(String name, boolean curseKnown, boolean visiblyCursed) {
        if (curseKnown) {
            return List.of(new Candidate(visiblyCursed ? CURSED_WAND : WAND, name, 1));
        }
        return List.of(new Candidate(WAND, name, 1 - WAND_CURSE_CHANCE),
                new Candidate(CURSED_WAND, name, WAND_CURSE_CHANCE));
    }

    /** The verdict on trying an item with {@code candidates} where the hero stands in {@code observation}. */
    public static Verdict of(List<Candidate> candidates, Observation observation) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("an item to test has at least one identity");
        }
        int hp = observation.hero().hp();
        boolean enemies = observation.actors().actors().stream().anyMatch(actor -> actor.alignment() == Alignment.ENEMY);
        List<Harm> harms = new ArrayList<>();
        double mean = 0;
        for (Candidate candidate : candidates) {
            Harm harm = harm(candidate, observation);
            harms.add(harm);
            mean += candidate.probability() * harm.damage();
        }
        harms.sort(Comparator.comparingInt((Harm harm) -> severity(harm, enemies)).reversed()
                .thenComparing(Harm::name));
        Harm worst = harms.get(0);
        String why;
        boolean safe;
        if (worst.damage() >= hp) {
            safe = false;
            why = "lethal: " + worst.name() + " " + worst.damage() + " >= hp " + hp;
        } else if ((worst.disables() || worst.draws()) && enemies) {
            safe = false;
            why = (worst.disables() ? "disabled" : "drawn on") + " with an enemy in view: " + worst.name();
        } else {
            safe = true;
            why = "survivable: worst " + worst.name() + " " + worst.damage() + " < hp " + hp;
        }
        return new Verdict(safe, worst, mean, List.copyOf(harms), why);
    }

    /** How bad a harm is, for ordering: lethal-with-enemies conditions weigh as much as any damage. */
    private static int severity(Harm harm, boolean enemies) {
        int condition = (harm.disables() || harm.draws()) && enemies ? 1_000_000 : 0;
        return condition + harm.damage();
    }

    /**
     * The worst {@code candidate} can do to a hero who tries it here.
     *
     * <ul>
     *   <li>Liquid flame seeds fire on the hero's cell and its neighbours (PotionOfLiquidFlame.java:50-55),
     *       which sets the hero burning for eight turns (Burning.java:54), relit while the fire
     *       lasts, at up to 3 + depth/4 a turn (Burning.java:105-117). Burning ends at the first turn
     *       after its first hit that the hero stands in water (Burning.java:98-99, :178-181): on
     *       water, one hit; beside water, two (the hit, the step); otherwise the eight turns and the
     *       two the fire lasts.
     *   <li>Toxic gas deals 1 + depth/5 a turn to whoever stands in it (ToxicGas.java:39-52), for
     *       {@link #GAS_TURNS} turns.
     *   <li>Paralytic gas paralyses for ten turns, renewed while the hero stands in it
     *       (ParalyticGas.java:52, Paralysis.java:34); frost chills and then freezes the hero
     *       (Freezing.java:68-88). Both disable.
     *   <li>Retribution weakens and blinds the reader (ScrollOfRetribution.java:74-75): disables.
     *       Rage beckons every mob on the floor to the reader (ScrollOfRage.java:47-49): draws.
     *   <li>A cursed wand's zap runs a cursed effect; two that land on the hero are the worst of the
     *       common and uncommon ones: burning (CursedWand.java:220-230), as liquid flame's without
     *       the fire, and a lightning bolt through the hero's own neighbourhood for up to
     *       10 + depth/4 and a stun (CursedWand.java:550-580). The rare and very rare ones (10% of
     *       cursed zaps, CursedWand.java:163) are not modelled.
     * </ul>
     */
    static Harm harm(Candidate candidate, Observation observation) {
        int depth = Math.max(1, observation.header().depth());
        int burn = 3 + depth / 4;
        int cell = observation.hero().cell();
        MapSection map = observation.map();
        String name = candidate.name();
        switch (candidate.className()) {
            case "items.potions.PotionOfLiquidFlame": {
                int turns = water(map, cell) ? 1 : besideWater(map, cell) ? 2 : 8 + 2;
                return new Harm(name, turns * burn, false, false, "burns " + turns + " turns");
            }
            case "items.potions.PotionOfToxicGas":
                return new Harm(name, GAS_TURNS * (1 + depth / 5), false, false, "gas " + GAS_TURNS + " turns");
            case "items.potions.PotionOfParalyticGas":
                return new Harm(name, 0, true, false, "paralysed");
            case "items.potions.PotionOfFrost":
                return new Harm(name, 0, true, false, "frozen");
            case "items.scrolls.ScrollOfRetribution":
                return new Harm(name, 0, true, false, "blinded");
            case "items.scrolls.ScrollOfRage":
                return new Harm(name, 0, false, true, "draws the floor");
            case CURSED_WAND: {
                int turns = water(map, cell) ? 1 : besideWater(map, cell) ? 2 : 8;
                int damage = Math.max(turns * burn, 10 + depth / 4);
                return new Harm(name + " (cursed)", damage, true, false, "cursed zap: burns or shocks");
            }
            default:
                return Harm.none(name);
        }
    }

    private static boolean water(MapSection map, int cell) {
        return map.tiles().get(cell) == Tile.WATER;
    }

    private static boolean besideWater(MapSection map, int cell) {
        int x = cell % map.width();
        int y = cell / map.width();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < map.width() && ny < map.height()
                        && map.tiles().get(nx + ny * map.width()) == Tile.WATER) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The enemies in view, for callers that want to name them. */
    static List<ActorView> enemies(Observation observation) {
        return observation.actors().actors().stream().filter(actor -> actor.alignment() == Alignment.ENEMY).toList();
    }
}
