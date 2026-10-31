package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the tuner refuses (story 10.36, ADR-0026): the sets it may never play, the salts it may not use, and a
 * vector or a flag it cannot read. One case per refused set, each reaching the refusal by the command's own door
 * ({@link Tune#run}) and not only by the check, so a refactor that bypassed the check would fail here.
 */
class TuneRefusalTest {

    private static Map<String, String> args(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static String refusal(Map<String, String> a) {
        return assertThrows(IllegalArgumentException.class, () -> Tune.run(a)).getMessage();
    }

    @Test
    @DisplayName("goo is refused as the set, by the command, with the reason")
    void goo() {
        String why = refusal(args(Tune.SET, "goo", Tune.ROOT, SeedSetsTest.ROOT.toString()));
        assertTrue(why.contains("goo") && why.contains("Registration"), why);
    }

    @Test
    @DisplayName("holdout is refused as the set, by the command, with the reason")
    void holdout() {
        String why = refusal(args(Tune.SET, "holdout", Tune.ROOT, SeedSetsTest.ROOT.toString()));
        assertTrue(why.contains("holdout") && why.contains("never run during development"), why);
    }

    @Test
    @DisplayName("tengu is refused as the set, by the command, with the reason, although no such set exists yet")
    void tengu() {
        String why = refusal(args(Tune.SET, "tengu", Tune.ROOT, SeedSetsTest.ROOT.toString()));
        assertTrue(why.contains("tengu") && why.contains("Registration"), why);
    }

    @Test
    @DisplayName("a set that is not standard or smoke is refused, so a new set is a decision made here")
    void everything_else() {
        for (String set : List.of("bosses", "nonsense", "")) {
            String why = refusal(args(Tune.SET, set, Tune.ROOT, SeedSetsTest.ROOT.toString()));
            assertTrue(why.contains("standard") && why.contains("smoke"), why);
        }
    }

    @Test
    @DisplayName("standard and smoke are allowed")
    void the_allowed_sets() {
        assertDoesNotThrow(() -> Tune.allow("standard"));
        assertDoesNotThrow(() -> Tune.allow("smoke"));
    }

    @Test
    @DisplayName("a salt base under 5000 is refused: those salts are the direction tables' and the confirmation slice's")
    void the_confirmation_salts() {
        for (long base : new long[] {0, 1000, 3000, 4999, -1}) {
            String why = refusal(args(Tune.SALT_BASE, Long.toString(base), Tune.ROOT, SeedSetsTest.ROOT.toString()));
            assertTrue(why.contains("5000") && why.contains("3000"), why);
        }
    }

    @Test
    @DisplayName("a run whose salts reach 7000 or more is refused: those are the confirmation tiebreaks'")
    void the_tiebreak_salts() {
        double[] start = {500};
        List<String> one = List.of("fight.margin_per_mille");
        org.shatterfish.api.HeroClass warrior = org.shatterfish.api.HeroClass.WARRIOR;
        // 5000..6999 is the whole range: 2000 salts fit exactly, 2001 do not.
        assertDoesNotThrow(() -> new Tune.Config("smoke", List.of(warrior), 20, 5000, 100, one, start, Spsa.Config.of(100)));
        String why = assertThrows(IllegalArgumentException.class,
                () -> new Tune.Config("smoke", List.of(warrior), 3, 5000, 667, one, start, Spsa.Config.of(667))).getMessage();
        assertTrue(why.contains("6999") && why.contains("7000"), why);
        // The default shape for all six classes does not fit 20 iterations of 20; it fits 16.
        assertThrows(IllegalArgumentException.class, () -> new Tune.Config("standard", List.of(org.shatterfish.api.HeroClass.values()), 20, 5000,
                17, one, start, Spsa.Config.of(17)));
        assertDoesNotThrow(() -> new Tune.Config("standard", List.of(org.shatterfish.api.HeroClass.values()), 20, 5000,
                16, one, start, Spsa.Config.of(16)));
        // A base near the top, and the PR #393 tiebreak shape (Warrior, first 120, base 7000), are refused by the command.
        assertThrows(IllegalArgumentException.class, () -> Tune.allowSaltRange(6990, 20));
        assertThrows(IllegalArgumentException.class, () -> Tune.allowSaltRange(7000, 120));
        String run = refusal(args(Tune.SALT_BASE, "6990", Tune.FIRST, "20", Tune.CLASS, "WARRIOR", Tune.ITERATIONS, "2",
                Tune.ROOT, SeedSetsTest.ROOT.toString()));
        assertTrue(run.contains("7000"), run);
        // A resume cannot walk past the ceiling either.
        assertThrows(IllegalArgumentException.class, () -> new Tune.Config("smoke", List.of(warrior), 20, 5000, 100, one, start,
                Spsa.Config.of(100)).withIterations(101));
    }

    @Test
    @DisplayName("5000 is the first salt the tuner may use")
    void the_floor_is_inclusive() {
        assertDoesNotThrow(() -> Tune.allowSalts(5000));
        assertEquals(5000, Tune.SALT_FLOOR);
    }

    @Test
    @DisplayName("a configuration cannot be built on a refused set or salt, whatever door it comes through")
    void the_configuration_refuses_too() {
        double[] start = {500};
        assertThrows(IllegalArgumentException.class, () -> new Tune.Config("goo", List.of(org.shatterfish.api.HeroClass.WARRIOR), 3, 5000, 1,
                List.of("fight.margin_per_mille"), start, Spsa.Config.of(1)));
        assertThrows(IllegalArgumentException.class, () -> new Tune.Config("smoke", List.of(org.shatterfish.api.HeroClass.WARRIOR), 3, 3000, 1,
                List.of("fight.margin_per_mille"), start, Spsa.Config.of(1)));
    }

    @Test
    @DisplayName("a number that is not registered is refused by name, and so is one named twice")
    void unregistered_parameters() {
        String why = assertThrows(IllegalArgumentException.class, () -> Tune.params("fight.margin_per_mille,fight.aggression")).getMessage();
        assertTrue(why.contains("fight.aggression") && why.contains("not registered"), why);
        assertThrows(IllegalArgumentException.class, () -> Tune.params("identify.reach,identify.reach"));
        assertEquals(org.shatterfish.api.Tunables.ALL.size(), Tune.params("all").size());
        assertEquals(List.of("identify.reach", "descend.rests"), Tune.params("identify.reach,descend.rests"));
    }

    @Test
    @DisplayName("a resume takes its configuration from the folder and refuses a flag that would change it")
    void resume_refuses_new_settings() {
        String why = refusal(args(Tune.RESUME, Path.of("nowhere").toString(), Tune.FIRST, "5", Tune.ROOT, SeedSetsTest.ROOT.toString()));
        assertTrue(why.contains(Tune.FIRST) && why.contains(Tune.RESUME), why);
    }

    @Test
    @DisplayName("an unknown flag, a flag with no value and a flag given twice are refused")
    void the_command_line() {
        assertThrows(IllegalArgumentException.class, () -> Tune.arguments(new String[] {"--oracle", "on"}));
        assertThrows(IllegalArgumentException.class, () -> Tune.arguments(new String[] {"--set"}));
        assertThrows(IllegalArgumentException.class, () -> Tune.arguments(new String[] {"--set", "smoke", "--set", "standard"}));
        assertEquals(Map.of("--set", "smoke", "--keep-logs", "true"), Tune.arguments(new String[] {"--set", "smoke", "--keep-logs"}));
        for (String flag : Tune.KNOWN) {
            assertTrue(!flag.toLowerCase(java.util.Locale.ROOT).contains("oracle"), flag);
        }
    }

    @Test
    @DisplayName("the Rig's own --tunables is checked in the parent: unregistered, out of bounds and malformed vectors are refused")
    void the_forwarded_vector_is_checked() {
        assertDoesNotThrow(() -> RunOne.checkTunables("fight.margin_per_mille=400,identify.reach=12"));
        assertThrows(IllegalArgumentException.class, () -> RunOne.checkTunables("fight.aggression=3"));
        assertThrows(IllegalArgumentException.class, () -> RunOne.checkTunables("identify.reach=999"));
        assertThrows(IllegalArgumentException.class, () -> RunOne.checkTunables("identify.reach"));
        assertTrue(Runner.KNOWN.contains(Runner.TUNABLES));
        assertTrue(Direction.KNOWN.contains(Direction.TUNABLES));
        assertEquals(Map.of(Runner.TUNABLES, "identify.reach=12"), Runner.arguments(new String[] {"--tunables", "identify.reach=12"}));
    }
}
