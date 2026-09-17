package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.levels.traps.AlarmTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BlazingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ChillingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ConfusionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CorrosionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CursingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisarmingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisintegrationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DistortionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ExplosiveTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlashingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlockTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GatewayTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GeyserTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GnollRockfallTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrimTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrippingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GuardianTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.OozeTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.PitfallTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.PoisonDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.RockfallTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ShockingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.StormTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.SummoningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TeleportationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TenguDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WarpingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WeakeningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WornDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ToxicGasRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The traps (story 2.6): every concrete trap class the game declares, constructed bare under
 * {@link GameContext} and asked what it is, since a trap is a bundled thing and not an actor and
 * its initialiser only sets fields. The two facts a player can act on are whether it can be
 * hidden and whether it can be searched; the effect itself needs a level and the toolkit, so the
 * table carries the text of the trap's own {@code activate} and cites it, as story 2.5's hit
 * table carries a method it cannot run. Each trap's display name is the bundle's, by the game's
 * key rule.
 *
 * <p>Where a trap appears is the level's: a level's trap classes and their weights are two
 * protected methods, read from the class that declares them, which is not always the level's own
 * (the sewers' boss floor and the vault inherit theirs). Each pool carries how many traps that
 * floor lays, so a floor that lays none says so rather than being absent. A level whose two lists
 * differ in length, whose two methods disagree about the condition that chooses an arm, or whose
 * shape the reader does not know, fails the generation rather than being guessed at.
 */
final class Traps {

    /** Every concrete trap class, one constructor each; the completeness test holds the list whole. */
    static final List<Supplier<Trap>> ALL = List.of(
            AlarmTrap::new, BlazingTrap::new, BurningTrap::new, ChillingTrap::new, ConfusionTrap::new,
            CorrosionTrap::new, CursingTrap::new, DisarmingTrap::new, DisintegrationTrap::new,
            DistortionTrap::new, ExplosiveTrap::new, FlashingTrap::new, FlockTrap::new, FrostTrap::new,
            GatewayTrap::new, GeyserTrap::new, GnollRockfallTrap::new, GrimTrap::new, GrippingTrap::new,
            GuardianTrap::new, OozeTrap::new, PitfallTrap::new, PoisonDartTrap::new, RockfallTrap::new,
            ShockingTrap::new, StormTrap::new, SummoningTrap::new, TeleportationTrap::new, TenguDartTrap::new,
            ToxicTrap::new, WarpingTrap::new, WeakeningTrap::new, WornDartTrap::new,
            // Two traps the game declares inside the place that uses them rather than beside the
            // rest: the vault's flame jets and the toxic gas room's vent. A player meets both, so
            // the table carries both.
            VaultLevel.VaultFlameTrap::new, ToxicGasRoom.ToxicVent::new);

    /**
     * The trap classes whose construction reads a Run, a Profile, a quest, the clock or a
     * generator, each with the reason it is allowed to. Empty at this tag: a trap's initialiser
     * sets fields and nothing else, which is why a trap can be constructed bare at all. The audit
     * holds this list against the compiled hierarchy, so a tag that gives a trap a constructor
     * which reads a Run cannot reach a table unnoticed.
     */
    static final List<java.util.Map.Entry<Class<?>, String>> READS = List.of();

    private static final Pattern CLASS_TOKEN = Pattern.compile("\\b(\\w+)\\.class\\b");
    private static final Pattern WEIGHT = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)f?\\b");
    private static final Pattern SEED = Pattern.compile("\\bBlob\\.seed\\s*\\([^;]*?(\\w+)\\.class");

    private Traps() {
    }

    /**
     * Every array literal of {@code opening}'s shape in one method's text, each without its outer
     * braces. The closing brace is the one that matches the opening brace, counted, not the first
     * one after it: a nested literal or an anonymous class inside a list would otherwise close it
     * early and truncate the pool without a word.
     */
    static List<String> literals(String text, String opening) {
        List<String> literals = new ArrayList<>();
        int at = text.indexOf(opening);
        while (at >= 0) {
            int open = text.indexOf('{', at);
            if (open < 0) {
                throw new IllegalStateException("an array literal does not open: " + text);
            }
            int depth = 0;
            int close = -1;
            for (int i = open; i < text.length(); i++) {
                if (text.charAt(i) == '{') {
                    depth++;
                } else if (text.charAt(i) == '}') {
                    depth--;
                    if (depth == 0) {
                        close = i;
                        break;
                    }
                }
            }
            if (close < 0) {
                throw new IllegalStateException("an array literal does not close: " + text);
            }
            literals.add(text.substring(open + 1, close));
            at = text.indexOf(opening, close);
        }
        if (literals.isEmpty()) {
            throw new IllegalStateException("no " + opening + " literal in: " + text);
        }
        return literals;
    }

    /**
     * The condition a method's ternary chooses its first arm by. The question mark is the one
     * before the first array literal, not the first question mark in the text, since a method that
     * returns a {@code Class<?>[]} has one in its own type.
     */
    static String condition(String text, String opening) {
        int literal = text.indexOf(opening);
        if (literal < 0) {
            throw new IllegalStateException("no " + opening + " literal in: " + text);
        }
        int mark = text.lastIndexOf('?', literal);
        int returns = text.lastIndexOf("return", mark < 0 ? 0 : mark);
        if (mark < 0 || returns < 0 || mark <= returns) {
            return "";
        }
        return text.substring(returns + "return".length(), mark).trim();
    }

    /** Every trap's entry, by class name. */
    static List<Codex.TrapEntry> entries(Path root) {
        List<Codex.TrapEntry> entries = new ArrayList<>();
        for (Supplier<Trap> make : ALL) {
            entries.add(entry(root, GameContext.under(1, 0, make)));
        }
        return entries;
    }

    /**
     * One trap: what the game's own initialiser set, its name, and the text of its effect. Both
     * the name and the effect may be another class's — the tengu's darts have no bundle key and no
     * effect of their own, and are the poison dart trap's on both counts — so each is cited to the
     * class it was read from and the entry says whose it is.
     *
     * <p>A trap the game deactivates carries what else stands on its cell. Its own effect being
     * empty does not make the tile harmless: the vault's flame jets and the toxic gas room's vent
     * are both seeded with a blob by the class that places them, and a table that said only
     * "inactive" would tell a reader the most dangerous cell on the floor is decoration.
     */
    static Codex.TrapEntry entry(Path root, Trap trap) {
        Class<?> type = trap.getClass();
        Sources.Body body = Sources.body(root, type);
        Names.Sourced name = Names.sourced(root, type);
        Sources.Declared activate = Sources.declared(root, type, Trap.class, "public void activate\\s*\\(\\s*\\)");
        if (activate == null) {
            throw new IllegalStateException(type.getName() + " declares no activate() up to Trap");
        }
        Sources.Body owner = Sources.body(root, activate.owner());
        String text = Sources.text(activate.block());
        List<Codex.Rule> alsoOnTheCell = new ArrayList<>();
        if (text.contains("super.activate()")) {
            Sources.Declared inherited = Sources.declared(root, activate.owner().getSuperclass(), Trap.class,
                    "public void activate\\s*\\(\\s*\\)");
            if (inherited == null) {
                throw new IllegalStateException(type.getName() + " calls a sealing it does not inherit");
            }
            alsoOnTheCell.add(new Codex.Rule("super.activate()", Sources.text(inherited.block()),
                    Sources.body(root, inherited.owner()).citation(inherited.line())));
        }
        alsoOnTheCell.addAll(seeded(root, type));
        return new Codex.TrapEntry(Sources.name(type), name.named().value(),
                name.owner() == type ? "" : Sources.name(name.owner()), name.named().citation(),
                trap.canBeHidden, trap.canBeSearched, trap.active,
                activate.owner() == type ? "" : Sources.name(activate.owner()), text, owner.citation(activate.line()),
                body.citation(body.declaration(type.getSimpleName())), alsoOnTheCell);
    }

    /**
     * What the class that places a trap seeds on the same cell: a blob, which acts on whatever
     * stands there whether or not the trap itself is active. Read from the placing class's own
     * source, which for the two traps the game nests inside their place is the class they are
     * nested in.
     */
    private static List<Codex.Rule> seeded(Path root, Class<?> type) {
        Class<?> placing = type.getEnclosingClass();
        if (placing == null) {
            return List.of();
        }
        Sources.Body body = Sources.body(root, placing);
        List<String> lines = Sources.stripped(body);
        List<Codex.Rule> seeded = new ArrayList<>();
        Matcher seed = SEED.matcher("");
        for (int i = body.from(); i < body.to(); i++) {
            String text = lines.get(i - body.from()).trim();
            seed.reset(text);
            if (seed.find()) {
                seeded.add(new Codex.Rule(seed.group(1), text.replaceAll("\\s+", " "), body.citation(i)));
            }
        }
        return seeded;
    }

    /**
     * The trap pool of every level class that lays traps: the classes it draws and the weight it
     * gives each, read from the two literals the methods return in whichever class declares them.
     * A level that inherits its pool has one — the sewers' boss floor and the vault both do — and
     * carries the class it inherited from, so a floor is never missing from the table because the
     * reader looked in one file. Each pool carries how many traps that floor lays, cited, so a
     * floor with a pool and no traps is a stated fact rather than an absence. A class list and a
     * weight list of different lengths, two methods that disagree about the condition choosing an
     * arm, a weight that is not whole, or a shape with more arms than the reader can name, all
     * fail naming the level.
     */
    static List<Codex.TrapPool> pools(Path root, List<Class<?>> levels) {
        Map<String, String> byName = new TreeMap<>();
        for (Supplier<Trap> make : ALL) {
            Class<?> type = GameContext.under(1, 0, make).getClass();
            byName.put(type.getSimpleName(), Sources.name(type));
        }
        List<Codex.TrapPool> pools = new ArrayList<>();
        for (Class<?> level : levels) {
            if (!RegularLevel.class.isAssignableFrom(level) || !lays(root, level)) {
                continue;
            }
            Sources.Declared classes = declared(root, level, "protected Class<\\?>\\[\\] trapClasses\\(\\)", "trapClasses");
            Sources.Declared chances = declared(root, level, "protected float\\[\\] trapChances\\(\\)", "trapChances");
            Sources.Declared count = declared(root, level, "protected int nTraps\\(\\)", "nTraps");
            String classesText = Sources.text(classes.block());
            String chancesText = Sources.text(chances.block());
            List<List<String>> arms = new ArrayList<>();
            for (String literal : literals(classesText, "new Class")) {
                List<String> drawn = new ArrayList<>();
                Matcher token = CLASS_TOKEN.matcher(literal);
                while (token.find()) {
                    String named = byName.get(token.group(1));
                    if (named == null) {
                        throw new IllegalStateException(level.getSimpleName() + " draws " + token.group(1)
                                + ", which the generator does not name; add it to Traps.ALL");
                    }
                    drawn.add(named);
                }
                arms.add(drawn);
            }
            List<List<Integer>> weightArms = new ArrayList<>();
            for (String literal : literals(chancesText, "new float")) {
                List<Integer> weights = new ArrayList<>();
                Matcher weight = WEIGHT.matcher(literal);
                while (weight.find()) {
                    int thousandths = Sources.thousandths(weight.group(1));
                    if (thousandths % 1000 != 0) {
                        throw new IllegalStateException(level.getSimpleName() + " weights a trap at " + weight.group(1)
                                + "; the table carries whole weights and will not round one away");
                    }
                    weights.add(thousandths / 1000);
                }
                weightArms.add(weights);
            }
            if (arms.size() != weightArms.size()) {
                throw new IllegalStateException(level.getSimpleName() + " draws from " + arms.size() + " pools with " + weightArms.size()
                        + " sets of weights; the reader does not know the shape of its pool");
            }
            if (arms.size() > 2) {
                throw new IllegalStateException(level.getSimpleName() + " draws from " + arms.size()
                        + " pools; the reader names the condition of two and cannot name a third");
            }
            String condition = condition(classesText, "new Class");
            String weightCondition = condition(chancesText, "new float");
            if (!condition.equals(weightCondition)) {
                throw new IllegalStateException(level.getSimpleName() + " chooses its traps by " + quoted(condition)
                        + " and their weights by " + quoted(weightCondition) + "; the reader cannot pair the arms");
            }
            if (arms.size() > 1 && condition.isEmpty()) {
                throw new IllegalStateException(level.getSimpleName()
                        + " draws from two pools and states no condition the reader can read; the arms cannot be told apart");
            }
            Class<?> owner = classes.owner();
            if (chances.owner() != owner) {
                throw new IllegalStateException(level.getSimpleName() + " takes its traps from " + owner.getSimpleName()
                        + " and their weights from " + chances.owner().getSimpleName() + "; the reader will not pair two classes");
            }
            Sources.Body body = Sources.body(root, owner);
            Sources.Body counting = Sources.body(root, count.owner());
            for (int arm = 0; arm < arms.size(); arm++) {
                List<String> drawn = arms.get(arm);
                List<Integer> weights = weightArms.get(arm);
                if (drawn.size() != weights.size()) {
                    throw new IllegalStateException(level.getSimpleName() + " draws " + drawn.size() + " traps with " + weights.size()
                            + " weights; the reader does not know the shape of its pool");
                }
                List<Codex.Weighted> weighted = new ArrayList<>();
                for (int i = 0; i < drawn.size(); i++) {
                    weighted.add(new Codex.Weighted(drawn.get(i), weights.get(i), 0, weights.get(i)));
                }
                pools.add(new Codex.TrapPool(Sources.name(level), owner == level ? "" : Sources.name(owner),
                        arms.size() == 1 ? "" : arm == 0 ? condition : "otherwise", weighted,
                        Sources.text(count.block()), counting.citation(count.line()), body.citation(classes.line())));
            }
        }
        return pools;
    }

    /**
     * Whether a level lays traps at all, read from the painter it actually uses: the mining floors
     * build their own painter and never ask it for traps, so they draw from no pool however much
     * their parent does, and the table would be wrong to give them one.
     */
    private static boolean lays(Path root, Class<?> level) {
        Sources.Declared painter = Sources.declared(root, level, RegularLevel.class, "protected Painter painter\\(\\)");
        if (painter == null) {
            throw new IllegalStateException(level.getSimpleName() + " reaches no painter(); the reader cannot say whether it lays traps");
        }
        return Sources.text(painter.block()).contains("setTraps(");
    }

    /** One of a level's trap methods, in whichever class up to the regular level declares it. */
    private static Sources.Declared declared(Path root, Class<?> level, String anchor, String what) {
        Sources.Declared declared = Sources.declared(root, level, RegularLevel.class, anchor);
        if (declared == null) {
            throw new IllegalStateException(level.getSimpleName() + " lays traps and reaches no " + what
                    + "(); the reader does not know what it draws");
        }
        return declared;
    }

    /** A condition as a message shows it, so an empty one reads as empty rather than as nothing. */
    private static String quoted(String condition) {
        return condition.isEmpty() ? "no condition" : "\"" + condition + "\"";
    }
}
