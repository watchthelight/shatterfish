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
 * <p>Where a trap appears is the level's: every regular level class declares its own trap classes
 * and their weights, as literals in two methods that are protected and so read from source. A
 * level whose two lists differ in length, or whose shape the reader does not know, fails the
 * generation rather than being guessed at.
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

    private static final Pattern CLASS_TOKEN = Pattern.compile("\\b(\\w+)\\.class\\b");
    private static final Pattern WEIGHT = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)f?\\b");

    private Traps() {
    }

    /** Every array literal of {@code opening}'s shape in one method's text, each without its braces. */
    private static List<String> literals(String text, String opening) {
        List<String> literals = new ArrayList<>();
        int at = text.indexOf(opening);
        while (at >= 0) {
            int open = text.indexOf('{', at);
            int close = text.indexOf('}', at);
            if (open < 0 || close < open) {
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

    /** The condition a method's ternary chooses its first arm by, or an empty string. */
    private static String condition(String text) {
        Matcher ternary = Pattern.compile("return\\s+(.*?)\\s*\\?").matcher(text);
        return ternary.find() ? ternary.group(1).trim() : "";
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
     * One trap: what the game's own initialiser set, its name, and the text of its effect. A trap
     * the game's own initialiser deactivates (the vault's flame jets, the toxic gas room's vent)
     * overrides its effect with nothing and is carried as inactive with no text, since a reader
     * is owed the fact that stepping on it does nothing rather than a blank it must interpret.
     */
    static Codex.TrapEntry entry(Path root, Trap trap) {
        Class<?> type = trap.getClass();
        Sources.Body body = Sources.body(root, type);
        Names.Named name = Names.of(root, type);
        Sources.Declared activate = Sources.declared(root, type, Trap.class, "public void activate\\s*\\(\\s*\\)");
        if (activate == null) {
            throw new IllegalStateException(type.getName() + " declares no activate() up to Trap");
        }
        return new Codex.TrapEntry(Sources.name(type), name.value(), name.citation(), trap.canBeHidden, trap.canBeSearched,
                trap.active, Sources.text(activate.block()), body.citation(body.declaration(type.getSimpleName())));
    }

    /**
     * The trap pool of every level class that declares one: the classes it draws and the weight
     * it gives each, read from the two literals its own methods return. A class list and a weight
     * list of different lengths, or a method whose shape the reader does not know, fails naming
     * the level.
     */
    static List<Codex.TrapPool> pools(Path root, List<Class<?>> levels) {
        Map<String, String> byName = new TreeMap<>();
        for (Supplier<Trap> make : ALL) {
            Class<?> type = GameContext.under(1, 0, make).getClass();
            byName.put(type.getSimpleName(), Sources.name(type));
        }
        List<Codex.TrapPool> pools = new ArrayList<>();
        for (Class<?> level : levels) {
            Sources.Body body = Sources.body(root, level);
            int classes = body.find("protected Class<\\?>\\[\\] trapClasses\\(\\)");
            int chances = body.find("protected float\\[\\] trapChances\\(\\)");
            if (classes < 0 || chances < 0) {
                continue;
            }
            List<List<String>> arms = new ArrayList<>();
            for (String literal : literals(Sources.text(body.block(classes)), "new Class")) {
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
            for (String literal : literals(Sources.text(body.block(chances)), "new float")) {
                List<Integer> weights = new ArrayList<>();
                Matcher weight = WEIGHT.matcher(literal);
                while (weight.find()) {
                    weights.add(Sources.thousandths(weight.group(1)) / 1000);
                }
                weightArms.add(weights);
            }
            if (arms.size() != weightArms.size()) {
                throw new IllegalStateException(level.getSimpleName() + " draws from " + arms.size() + " pools with " + weightArms.size()
                        + " sets of weights; the reader does not know the shape of its pool");
            }
            String condition = condition(Sources.text(body.block(classes)));
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
                pools.add(new Codex.TrapPool(Sources.name(level), arms.size() == 1 ? "" : arm == 0 ? condition : "otherwise",
                        weighted, body.citation(classes)));
            }
        }
        return pools;
    }
}
