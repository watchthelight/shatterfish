package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.watabou.utils.Bundle;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The forms of a mob whose {@code attackSkill} and {@code damageRoll} branch on a state of the
 * mob itself (issue #219): the mob table's roll for such a method is OTHER with its text, because
 * the source states no single range. This class runs the engine's own two methods with the state
 * set, for each state the source branches on, and records the attack skill and the damage range
 * each gave, with the source's own test quoted and checked against the pinned file so that a
 * tag whose test changed fails here and not silently.
 *
 * <p>Only a state the game itself sets on the mob and the player can see is a form. Goo's two are
 * its health at half or less (Goo.java:69, 83: the screen shows the health bar) and its pump-up
 * (Goo.java:71, 86: announced in the log and drawn). A mob whose methods branch on something
 * else is not given a form here: the brute's damage on a rage buff it gains on dying
 * (Brute.java:58-61), the gnoll guard's and the Tengu's on how far the target is
 * (GnollGuard.java:96, Tengu.java:108), and the rest of the table's OTHER rolls on the depth, the
 * level or the hit points. Those stay OTHER, and the Brain guesses them as before.
 */
final class Forms {

    /** How many attacks a form's damage is measured over. */
    static final int SAMPLES = 20_000;

    /** One state a mob's methods branch on: the form's name, the source's own test, and how to put a mob in it. */
    private record State(String name, String condition, Consumer<Mob> apply) {
    }

    /** A mob with forms: the lines its tests are written on (each checked to be there) and its states. */
    private record Spec(Class<? extends Mob> type, List<String> tests, List<State> states) {
    }

    private Forms() {
    }

    /**
     * Goo's pump-up counter is private, and reflection is shut at this gate. The game's own door to
     * it is the bundle: {@code storeInBundle} writes it under "pumpedup" and {@code restoreFromBundle}
     * reads it back (Goo.java:325-343), so the state is put in by the save format's own round trip.
     */
    private static void goo(Mob mob, boolean enraged, boolean pumped) {
        Bundle bundle = new Bundle();
        mob.storeInBundle(bundle);
        bundle.put("pumpedup", pumped ? 1 : 0);
        mob.restoreFromBundle(bundle);
        // Goo.java:69 and 83 test HP*2 <= HT; the pump is a counter read as > 0 (Goo.java:71, 86).
        mob.HP = enraged ? mob.HT / 2 : mob.HT;
    }

    private static final List<Spec> SPECS = List.of(
            new Spec(Goo.class,
                    List.of("int max = \\(HP\\*2 <= HT\\) \\? 12 : 8;", "if \\(HP\\*2 <= HT\\) attack = 15;",
                            "if \\(pumpedUp > 0\\) attack \\*= 2;", "return Random\\.NormalIntRange\\( min\\*3, max\\*3 \\);"),
                    List.of(new State(Codex.Form.CALM, "HP*2 > HT and not pumped", mob -> goo(mob, false, false)),
                            new State("enraged", "HP*2 <= HT", mob -> goo(mob, true, false)),
                            new State("pumped", "pumpedUp > 0", mob -> goo(mob, false, true)),
                            new State("enraged pumped", "HP*2 <= HT and pumpedUp > 0", mob -> goo(mob, true, true)))));

    /** The classes that have forms. */
    static List<Class<? extends Mob>> classes() {
        List<Class<? extends Mob>> classes = new ArrayList<>();
        for (Spec spec : SPECS) {
            classes.add(spec.type());
        }
        return classes;
    }

    /** The forms of the mob {@code type}, measured by running its methods; empty when it has none. */
    static List<Codex.Form> of(Path root, Class<?> type, Supplier<Mob> make) {
        for (Spec spec : SPECS) {
            if (spec.type() != type) {
                continue;
            }
            Sources.Body body = Sources.body(root, type);
            for (String test : spec.tests()) {
                if (body.find(test) < 0) {
                    throw new IllegalStateException(Sources.name(type) + " no longer states `" + test + "`; its forms no longer name what they mean");
                }
            }
            List<Codex.Form> forms = new ArrayList<>();
            for (State state : spec.states()) {
                forms.add(measure(spec, state, make));
            }
            return forms;
        }
        return List.of();
    }

    private static Codex.Form measure(Spec spec, State state, Supplier<Mob> make) {
        long key = Sources.name(spec.type()).hashCode() * 29L + state.name().hashCode();
        return GameContext.measure(key, hero -> {
            Mob mob = GameContext.under(1, 0, make);
            state.apply().accept(mob);
            int attack = mob.attackSkill(hero);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i = 0; i < SAMPLES; i++) {
                // damageRoll spends the pump (Goo.java:72), so the state is put back before each roll.
                state.apply().accept(mob);
                int roll = mob.damageRoll();
                min = Math.min(min, roll);
                max = Math.max(max, roll);
            }
            return new Codex.Form(state.name(), state.condition(), attack, min, max);
        });
    }
}
