package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BuffView;
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
 * hero's cell and its neighbours, the enemies in view, the hero's hit points, the depth and the
 * hero's buffs -- all on the screen -- and the candidate identities with their odds, from
 * {@link Beliefs}.
 *
 * <p>Every candidate goes through the same code: potions, scrolls and wands are each a list of
 * {@link Candidate}s, and {@link #harm} knows each identity's worst case by class. The verdict
 * looks only at the worst case, never at the mean: an item that is healing nine times in ten and
 * liquid flame the tenth is refused if the flame would kill, however good the mean.
 *
 * <p>The worst cases are a hand-written table read from the pinned code, not generated; each is
 * cited below and in {@code docs/rules/identification.md}, and {@link #CLASSES} is held to the
 * Codex's item classes by the rig's {@code SafeTestCodexTest}, so a class renamed at an upgrade
 * fails a test rather than silently scoring as harmless. Two numbers are assumptions and say so:
 * how long the hero stays in a toxic cloud ({@link #GAS_TURNS}) and that being disabled beside an
 * enemy is treated as lethal. The function is pure: it reads its arguments and nothing else.
 */
public final class SafeTest {

    /** One identity the item may have, its class as the Codex names it, and its probability. */
    public record Candidate(String className, String name, double probability) {
    }

    /**
     * The worst an identity can do here: the hit points it can cost, whether it leaves the hero
     * unable to act or to see (paralysed, asleep, frozen, blinded), whether it draws enemies, and why.
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

    /**
     * An uncursed wand's zap. Only a hidden curse is scored: what a known wand does by its own
     * effect -- a wand of lightning's arc strikes its user for half (WandOfLightning.java:92-93) --
     * is the caller's to weigh, since the wand's kind is shown.
     */
    public static final String WAND = "wand";

    /** A weapon or armour worn uncursed: harmless to put on. */
    public static final String GEAR = "gear";

    /** A melee weapon worn cursed; not a Codex class, a stand-in (story 4.8). */
    public static final String CURSED_WEAPON = "weapon (cursed)";

    /** A suit of armour worn cursed; not a Codex class, a stand-in (story 4.8). */
    public static final String CURSED_ARMOR = "armor (cursed)";

    /**
     * A melee weapon's chance to be cursed, given that it shows no enchantment. The generator curses
     * 30% and enchants 10% (Weapon.java:439-447); an enchantment shows in the name
     * (Weapon.java:411-419), so a piece whose name shows none is one of the other 90%, cursed 0.3/0.9.
     * The Parchment Scrap trinket scales the 30% (Weapon.java:441); it is not scaled here (issue #136).
     */
    static final double WEAPON_CURSE_CHANCE = 0.3 / 0.9;

    /**
     * A suit of armour's chance to be cursed, given that it shows no glyph: 30% cursed, 15% inscribed
     * (Armor.java:671-679), a glyph shown in the name (Armor.java:578), so 0.3/0.85. The Parchment
     * Scrap trinket scales the 30% (Armor.java:673); not scaled here (issue #136).
     */
    static final double ARMOR_CURSE_CHANCE = 0.3 / 0.85;

    /** A hidden curse's chance on a piece of this kind that shows no enchantment or glyph. */
    static double curseChance(org.shatterfish.api.ItemKind kind) {
        return kind == org.shatterfish.api.ItemKind.WEAPON ? WEAPON_CURSE_CHANCE : ARMOR_CURSE_CHANCE;
    }

    /** The Codex item classes the table scores; every other class is harmless to try. */
    public static final List<String> CLASSES = List.of("items.potions.PotionOfLiquidFlame",
            "items.potions.PotionOfToxicGas", "items.potions.PotionOfParalyticGas", "items.potions.PotionOfFrost",
            "items.scrolls.ScrollOfRetribution", "items.scrolls.ScrollOfRage", "items.scrolls.ScrollOfLullaby");

    /**
     * How many turns a hero caught in its own toxic cloud stays in it: an assumption. A potion seeds
     * 1000 units of gas on the hero's cell (PotionOfToxicGas.java:49), which spreads over the room
     * and lasts well past the turns a hero needs to walk out of it; ten is the walk out of a room.
     */
    static final int GAS_TURNS = 10;

    /** A wand's chance to be cursed when generated (Wand.java:562-563), for the mean only. */
    static final double WAND_CURSE_CHANCE = 0.3;

    /** The buff the Ascension challenge shows while the Amulet is carried up (actors.properties:85). */
    static final String ASCENDING = "amulet's curse";

    /** The buff a levitating hero shows (actors.properties:280); a flyer is not put out by water. */
    static final String LEVITATING = "levitating";

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

    /**
     * The candidates for putting on a weapon or armour (story 4.8). Its kind is shown; whether it is
     * cursed is not, until it is worn: equipping reveals the curse and keeps the piece on
     * (KindOfWeapon.java:130-139, Armor.java:248-254, EquipableItem.java:126-129). A piece whose
     * curse is shown has one candidate; one whose curse is hidden has two, cursed with the
     * generator's chance.
     */
    public static List<Candidate> gear(String name, org.shatterfish.api.ItemKind kind, boolean curseKnown,
                                       boolean visiblyCursed) {
        String cursed = kind == org.shatterfish.api.ItemKind.WEAPON ? CURSED_WEAPON : CURSED_ARMOR;
        if (curseKnown) {
            return List.of(new Candidate(visiblyCursed ? cursed : GEAR, name, 1));
        }
        double chance = curseChance(kind);
        return List.of(new Candidate(GEAR, name, 1 - chance), new Candidate(cursed, name, chance));
    }

    /**
     * The verdict on trying an item with {@code candidates} where the hero stands in
     * {@code observation}. Build the candidates with {@link #candidates} or {@link #wand} only: they
     * carry the Codex's class names the table is keyed by, and odds that are a distribution, which
     * this refuses otherwise.
     *
     * <p>Rage with nobody in view is judged safe: its harm is the floor it draws, and a Policy that
     * reads it alone at low hit points is choosing to fight what comes, which is its call.
     */
    public static Verdict of(List<Candidate> candidates, Observation observation) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("an item to test has at least one identity");
        }
        double total = 0;
        for (Candidate candidate : candidates) {
            if (!(candidate.probability() >= 0)) {
                throw new IllegalArgumentException("a probability is not negative: " + candidate);
            }
            total += candidate.probability();
        }
        if (Math.abs(total - 1) > 1e-6) {
            throw new IllegalArgumentException("the candidates' odds sum to " + total + ", not 1");
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
        harms.sort(Comparator.comparingLong((Harm harm) -> severity(harm, enemies, hp)).reversed()
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

    /**
     * How bad a harm is, for ordering: a lethal one first, then one that disables or draws with an
     * enemy in view, then by damage.
     */
    private static long severity(Harm harm, boolean enemies, int hp) {
        long lethal = harm.damage() >= hp ? 2_000_000L : 0;
        long condition = (harm.disables() || harm.draws()) && enemies ? 1_000_000L : 0;
        return lethal + condition + harm.damage();
    }

    /**
     * The worst {@code candidate} can do to a hero who tries it here. Depth-scaled damage uses the
     * game's scaling depth: the depth, or 26 while the Ascension challenge is on
     * (Dungeon.java:447-452), which the hero's buffs show.
     *
     * <ul>
     *   <li>Liquid flame seeds fire of amount 2 on the hero's cell and its neighbours
     *       (PotionOfLiquidFlame.java:50-55), which sets the hero burning for eight turns
     *       (Burning.java:54) at up to 3 + depth/4 a turn (Burning.java:105-117). Burning ends at an
     *       act on water after it has already hit (Burning.java:98-99, :178-181), but the fire burns
     *       twice (Fire.java:52-62) and each burn relights it and clears that mark
     *       (Burning.java:214-218). On water: two hits. Beside water, the step lands in the
     *       neighbouring fire too: three. Dry: the eight turns and the two the fire lasts. A
     *       levitating hero gets no credit for water (the {@code !target.flying} tests), and a water
     *       cell another creature stands on is no refuge.
     *   <li>Toxic gas deals 1 + depth/5 a turn to whoever stands in it (ToxicGas.java:39-52), for
     *       {@link #GAS_TURNS} turns.
     *   <li>Paralytic gas paralyses for ten turns, renewed while the hero stands in it
     *       (ParalyticGas.java:52, Paralysis.java:34); frost chills and then freezes the hero
     *       (Freezing.java:68-88). Both disable.
     *   <li>Retribution weakens and blinds the reader (ScrollOfRetribution.java:74-75); Lullaby
     *       makes the reader drowsy (ScrollOfLullaby.java:55) and then asleep (Drowsy.java:54-55),
     *       paralysed unless at full health (MagicalSleep.java:37-50). Both disable. Rage beckons
     *       every mob on the floor to the reader (ScrollOfRage.java:47-49): draws.
     *   <li>A cursed wand's zap runs a cursed effect, which may land beside the hero; the worst of
     *       the common and uncommon ones (90% of cursed zaps, CursedWand.java:163) is scored:
     *       burning on the user (CursedWand.java:220-230) or a burning trap's fire at the bolt's end
     *       (CursedWand.java:302-320, BurningTrap.java:47-49), as liquid flame's; toxic gas at the
     *       bolt's end (CursedWand.java:280-299), as the potion's; a lightning bolt through the
     *       user's neighbourhood for up to 10 + depth/4 and a stun (CursedWand.java:550-580); a
     *       conjured bomb at the bolt's end, radius 1 (CursedWand.java:518-525, Bomb.java:93-95,
     *       :160), for up to 12 + 3 x depth before armour (Bomb.java:196-197); and a health
     *       transfer that costs the user 2 x depth half the time (CursedWand.java:477-492). It
     *       disables: paralytic gas and the bolt's stun. The rare and very rare effects are not
     *       modelled.
     *   <li>A cursed weapon or armour carries a curse that acts while the hero fights (story 4.8);
     *       the worst that can land on the wearer at one proc is scored. A weapon's Wondrous curse
     *       runs a random cursed-wand effect from the wielder (Wondrous.java:38-47), so a cursed
     *       weapon is scored as a cursed wand's zap, which includes the Explosive curse's blast
     *       beside its target (Explosive.java:71-85). Armour's Anti-Entropy sets the wearer burning
     *       for four turns off water (AntiEntropy.java:44-52), Stench seeds 250 units of toxic gas on
     *       the wearer's cell (Stench.java:39-42), and Overgrowth grows a random seed's plant under
     *       the wearer (Overgrowth.java:40-52): a Firebloom's fire of amount 2, as liquid flame's
     *       (Firebloom.java:57), or a Blindweed's blinding. The worst of fire and gas is scored, and
     *       it disables.
     * </ul>
     *
     * <p>{@link Tile#WATER} is what the screen draws as water, which includes one decoration the
     * sheet draws as water (DungeonTileSheet.java:436): a hero there would be misjudged as on water.
     */
    static Harm harm(Candidate candidate, Observation observation) {
        int depth = scalingDepth(observation);
        int burn = 3 + depth / 4;
        int gas = GAS_TURNS * (1 + depth / 5);
        int flame = flameTurns(observation) * burn;
        String name = candidate.name();
        switch (candidate.className()) {
            case "items.potions.PotionOfLiquidFlame":
                return new Harm(name, flame, false, false, "burns " + flameTurns(observation) + " turns");
            case "items.potions.PotionOfToxicGas":
                return new Harm(name, gas, false, false, "gas " + GAS_TURNS + " turns");
            case "items.potions.PotionOfParalyticGas":
                return new Harm(name, 0, true, false, "paralysed");
            case "items.potions.PotionOfFrost":
                return new Harm(name, 0, true, false, "frozen");
            case "items.scrolls.ScrollOfRetribution":
                return new Harm(name, 0, true, false, "blinded");
            case "items.scrolls.ScrollOfLullaby":
                return new Harm(name, 0, true, false, "asleep");
            case "items.scrolls.ScrollOfRage":
                return new Harm(name, 0, false, true, "draws the floor");
            case CURSED_WAND: {
                int damage = Math.max(Math.max(flame, gas),
                        Math.max(10 + depth / 4, Math.max(12 + 3 * depth, 2 * depth)));
                return new Harm(name + " (cursed)", damage, true, false, "cursed zap: fire, gas, bolt or blast");
            }
            case CURSED_WEAPON:
                return new Harm(name + " (cursed)", Math.max(Math.max(flame, gas),
                        Math.max(10 + depth / 4, Math.max(12 + 3 * depth, 2 * depth))), true, false,
                        "cursed: a wand's cursed effect or a blast");
            case CURSED_ARMOR:
                return new Harm(name + " (cursed)", Math.max(flame, gas), true, false, "cursed: fire, gas or blinding");
            default:
                return Harm.none(name);
        }
    }

    /** The depth damage scales by: 26 while the Ascension challenge is on, else the floor. */
    static int scalingDepth(Observation observation) {
        return has(observation, ASCENDING) ? 26 : Math.max(1, observation.header().depth());
    }

    /** How many turns fire seeded on and around the hero burns the hero before water puts it out. */
    static int flameTurns(Observation observation) {
        if (has(observation, LEVITATING)) {
            return 8 + 2;
        }
        MapSection map = observation.map();
        int cell = observation.hero().cell();
        if (map.tiles().get(cell) == Tile.WATER) {
            return 2;
        }
        return refugeBeside(observation) ? 3 : 8 + 2;
    }

    private static boolean has(Observation observation, String buff) {
        for (BuffView view : observation.hero().buffs()) {
            if (view.name().equals(buff)) {
                return true;
            }
        }
        return false;
    }

    /** Water on a neighbouring cell that no creature stands on. */
    private static boolean refugeBeside(Observation observation) {
        MapSection map = observation.map();
        int cell = observation.hero().cell();
        int x = cell % map.width();
        int y = cell / map.width();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= map.width() || ny >= map.height()) {
                    continue;
                }
                int neighbour = nx + ny * map.width();
                boolean taken = false;
                for (ActorView actor : observation.actors().actors()) {
                    taken |= actor.cell() == neighbour;
                }
                if (map.tiles().get(neighbour) == Tile.WATER && !taken) {
                    return true;
                }
            }
        }
        return false;
    }
}
