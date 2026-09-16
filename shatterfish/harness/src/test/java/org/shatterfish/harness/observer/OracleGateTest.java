package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ValidActions;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle mode is gated and marked (FR-11; ADR-0005, ADR-0006): the oracle read is the fair read
 * with the header's oracle bit set and nothing else, so its hash differs; the sidecar carries what
 * the screen hides and the fair bytes carry none of it; and with no flag there is no code path from
 * true identities or unseen positions into anything a Brain can hold, held three ways — by
 * ArchUnit over the harness's main classes, by reflection over every type an Observation or a
 * Decider can reach, and by the launcher being the only constructor of the oracle. The Rig's
 * refusal of an oracle Run is story 3.3's (ADR-0012) and is named, not tested, here.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class OracleGateTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 14_142_135L;

    private HeadlessDriver driver;
    private Level level;
    private Hero hero;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        level = Dungeon.level;
        hero = Dungeon.hero;
    }

    @Test
    @DisplayName("the oracle read is the fair read with the header's oracle bit set, and a different hash")
    void the_oracle_read_is_marked() {
        atTheFirstWait();
        Observation fair = new Observer().observe();
        OracleObserver.Read read = new OracleObserver().observe();
        Observation marked = read.observation();
        assertFalse(fair.header().oracle(), "the fair read is not marked");
        assertTrue(marked.header().oracle(), "the oracle read is marked");
        assertEquals(Set.of("header"), ObservationDiff.of(fair, marked), "nothing but the header differs");
        assertEquals(fair.actions(), marked.actions(), "the same Actions");
        assertEquals(ValidActions.of(marked), marked.actions(), "and the ones the marked read implies itself");
        assertNotEquals(fair.hash(), marked.hash(), "an oracle Run's hashes differ from a fair Run's (ADR-0005)");
        assertNotEquals(fair.sectionHashes().get("header"), marked.sectionHashes().get("header"));
        for (String section : fair.sectionHashes().keySet()) {
            if (!section.equals("header")) {
                assertEquals(fair.sectionHashes().get(section), marked.sectionHashes().get(section), section);
            }
        }
    }

    @Test
    @DisplayName("the sidecar holds what the screen hides, and the fair bytes hold none of it")
    void the_sidecar_holds_what_the_screen_hides() {
        atTheFirstWait();
        // Two secrets in view, so that the floor hides something with a name to look for.
        int door = wallInView();
        int trapCell = floorInView();
        Level.set(door, Terrain.SECRET_DOOR);
        Trap trap = new FrostTrap();
        level.setTrap(trap.hide(), trapCell);
        Level.set(trapCell, Terrain.SECRET_TRAP);

        OracleObserver.Read read = new OracleObserver().observe();
        OracleView view = read.view();
        Skeleton.Serialized fair = Skeleton.Serialized.of(new Observer().observe());
        Skeleton.Serialized marked = Skeleton.Serialized.of(read.observation());

        assertEquals(Dungeon.seed, view.seed());
        fair.assertAbsent(String.valueOf(Dungeon.seed));
        assertFalse(view.identities().isEmpty(), "the Warrior starts with unknown potions, scrolls and rings");
        for (OracleView.Identity identity : view.identities()) {
            assertNotEquals(identity.appearance(), identity.trueName());
            fair.assertAbsent(identity.trueName());
            marked.assertAbsent(identity.trueName());
        }
        assertEquals(level.mobs.size(), view.mobs().size(), "every mob, seen or not");
        boolean unseen = false;
        for (OracleView.Presence mob : view.mobs()) {
            unseen |= !mob.seen();
            assertEquals(level.heroFOV[mob.cell()], mob.seen());
            assertEquals(mob.seen(), read.observation().actors().actors().stream().anyMatch(a -> a.cell() == mob.cell()),
                    "the marked read draws a mob exactly where the fair one does: " + mob);
        }
        assertTrue(unseen, "the first floor has a mob the hero cannot see");
        assertTrue(view.secretDoors().contains(door));
        assertTrue(view.hiddenTraps().contains(new OracleView.Secret(trapCell, trap.name())));
        fair.assertAbsent(trap.name());
        marked.assertAbsent(trap.name());
        fair.assertAbsent("SECRET");
    }

    @Test
    @DisplayName("no fair path reaches the sidecar: only the oracle and the launcher name it, and nothing an Observation or a Decider reaches lives outside api")
    void no_fair_path_reaches_the_sidecar() {
        JavaClasses harness = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.shatterfish.harness");
        noClasses().that().resideInAPackage("org.shatterfish.harness..")
                .and().doNotBelongToAnyOf(OracleObserver.class, OracleObserver.Read.class, OracleView.class, Launcher.class)
                .should().dependOnClassesThat().haveNameMatching("org[.]shatterfish[.]harness[.]observer[.]Oracle(View|Observer).*")
                .because("the oracle is a debugging mode no measured Run, agent or executor may reach (FR-11)")
                .check(harness);
        noClasses().that().doNotBelongToAnyOf(Launcher.class)
                .should().callConstructor(OracleObserver.class)
                .because("the launcher's --oracle branch is the only place an oracle observer is made")
                .check(harness);

        // The Brain-facing type has no path to the sidecar: every type an Observation is made of
        // lives in api or the JDK, and a Decider takes an Observation and nothing else.
        Set<Class<?>> reached = new LinkedHashSet<>();
        reach(Observation.class, reached);
        assertTrue(reached.size() > 20, "the Observation's types were walked: " + reached.size());
        for (Class<?> type : reached) {
            String pkg = type.getPackageName();
            assertTrue(pkg.equals("org.shatterfish.api") || pkg.equals("java.lang") || pkg.equals("java.util"),
                    type.getName() + " is reachable from an Observation and lives outside api and the JDK");
        }
        assertFalse(reached.contains(OracleView.class));
        List<Method> decide = new ArrayList<>();
        for (Method method : Decider.class.getMethods()) {
            if (!method.isDefault() && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                decide.add(method);
            }
        }
        assertEquals(1, decide.size(), "one seam: " + decide);
        assertEquals(List.of(Observation.class), List.of(decide.get(0).getParameterTypes()), "an Observation and nothing else");
    }

    @Test
    @DisplayName("the flag is the launcher's: --oracle picks the oracle branch, nothing else does, and anything else is refused by name")
    void the_flag_is_the_launchers() {
        assertEquals(new Launcher.Launch(42L, true), Launcher.Launch.parse(new String[] {"--oracle", "42"}));
        assertEquals(new Launcher.Launch(42L, true), Launcher.Launch.parse(new String[] {"42", "--oracle"}));
        assertEquals(new Launcher.Launch(7L, false), Launcher.Launch.parse(new String[] {"7"}));
        assertFalse(Launcher.Launch.parse(new String[0]).oracle(), "off by default");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Launcher.Launch.parse(new String[] {"--oracel"}));
        assertTrue(refused.getMessage().contains("--oracel"), refused.getMessage());
    }

    /** Every class a record's components can reach, through lists and nested records. */
    private static void reach(Class<?> type, Set<Class<?>> into) {
        if (type.isPrimitive() || !into.add(type)) {
            return;
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                reachType(component.getGenericType(), into);
            }
        }
    }

    private static void reachType(Type type, Set<Class<?>> into) {
        if (type instanceof Class<?> plain) {
            reach(plain, into);
        } else if (type instanceof ParameterizedType parameterized) {
            reachType(parameterized.getRawType(), into);
            for (Type argument : parameterized.getActualTypeArguments()) {
                reachType(argument, into);
            }
        }
    }

    private int wallInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                return cell;
            }
        }
        throw new AssertionError("no wall in view");
    }

    private int floorInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no free floor in view");
    }
}
