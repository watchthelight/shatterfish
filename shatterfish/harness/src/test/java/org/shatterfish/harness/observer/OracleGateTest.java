package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Decider;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.ValidActions;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Oracle mode is gated and marked (FR-11; ADR-0005, ADR-0006): the oracle read is the fair read
 * with the header's oracle bit set and nothing else, so its hash differs; the sidecar carries what
 * the screen hides and the fair bytes carry none of it; and with no flag there is no code path from
 * true identities or unseen positions into anything a Brain can hold, held three ways — by
 * ArchUnit over the harness's main classes, by reflection over every type an Observation or a
 * Decider can reach, and by the launcher being the only constructor of the oracle. The rules are
 * shown to bite on fixtures that break them. The Rig's refusal of an oracle Run is story 3.3's
 * (ADR-0012) and is named, not tested, here; the rules see the harness module, and a module built
 * on it carries its own.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class OracleGateTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 14_142_135L;

    /** Only the oracle and the launcher may depend on the oracle's types. */
    static final ArchRule NO_ONE_ELSE_HOLDS_THE_SIDECAR = noClasses()
            .that().resideInAPackage("org.shatterfish.harness..")
            .and().doNotBelongToAnyOf(OracleObserver.class, OracleObserver.Read.class, OracleView.class)
            .and().doNotHaveFullyQualifiedName(Launcher.class.getName())
            .and().doNotHaveFullyQualifiedName(Launcher.Benchmark.class.getName())
            .should().dependOnClassesThat().haveNameMatching("org[.]shatterfish[.]harness[.]observer[.]Oracle(View|Observer).*")
            .because("the oracle is a debugging mode no measured Run, agent or executor may reach (FR-11)");

    /**
     * Only the launcher constructs an oracle observer: its --oracle branch, and the benchmark's
     * tactics half nested in it, which runs only under --oracle (story 1.21). The two classes
     * are named exactly; a new nested class of the launcher is not admitted by being nested.
     */
    static final ArchRule ONLY_THE_LAUNCHER_MAKES_AN_ORACLE = noClasses()
            .that().doNotHaveFullyQualifiedName(Launcher.class.getName())
            .and().doNotHaveFullyQualifiedName(Launcher.Benchmark.class.getName())
            .should().callConstructor(OracleObserver.class)
            .because("the launcher's --oracle branch and its benchmark are the only places an oracle observer is made");

    /**
     * No harness class reaches a class by its name: {@code Class.forName}, the game's
     * {@code Reflection.forName} and libGDX's {@code ClassReflection.forName} all answer to the
     * name, and a class-name string is the one way to construct the oracle that neither rule above
     * would see.
     */
    static final ArchRule NO_CLASS_BY_NAME = noClasses()
            .that().resideInAPackage("org.shatterfish.harness..")
            .should().accessTargetWhere(target(name("forName")))
            .because("a class reached by its name is a constructor call the dependency rules cannot see");

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
    @DisplayName("the oracle read is the fair read with the header's oracle bit set, a different hash, and the game untouched")
    void the_oracle_read_is_marked() throws Exception {
        atTheFirstWait();
        Observation fair = new Observer().observe();
        Random.pushGenerator(7L);
        long predicted = Random.Long();
        Random.popGenerator();
        Random.pushGenerator(7L);
        OracleObserver.Read read;
        try {
            int depth = generatorDepth();
            read = new OracleObserver().observe();
            assertEquals(depth, generatorDepth(), "an oracle read left a generator on the stack, or took one off");
            assertEquals(predicted, Random.Long(), "an oracle read consumed a draw");
        } finally {
            Random.popGenerator();
        }
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
        // The visible mark, which E5's border and story 3.3's refusal key on.
        assertTrue(marked.json().contains("\"oracle\":true"), marked.json().substring(0, 200));
        assertTrue(fair.json().contains("\"oracle\":false"), fair.json().substring(0, 200));

        // The game is untouched: a fair read after the oracle's is the fair read before it.
        assertArrayEquals(ObservationCodec.encode(fair), ObservationCodec.encode(new Observer().observe()),
                "an oracle read changed the screen");
    }

    @Test
    @DisplayName("the sidecar holds what the screen hides, and the fair bytes hold none of it")
    void the_sidecar_holds_what_the_screen_hides() {
        atTheFirstWait();
        // Three secrets in view, so that the floor hides something with a name to look for: a
        // secret door, a hidden trap, and a mimic drawn as a chest.
        int door = wallInView();
        List<Integer> floors = floorsInView(2);
        int trapCell = floors.get(0);
        int mimicCell = floors.get(1);
        Level.set(door, Terrain.SECRET_DOOR);
        Trap trap = new FrostTrap();
        level.setTrap(trap.hide(), trapCell);
        Level.set(trapCell, Terrain.SECRET_TRAP);
        Mimic mimic = Mimic.spawnAt(mimicCell, Mimic.class, new Torch());
        GameScene.add(mimic);
        assertTrue(Observer.hiddenMimic(mimic), "spawned neutral and passive (Mimic.java:62-64)");
        // And one mob the hero cannot see, hurt and hunting, so the fields have something to show.
        Mob far = mobOutOfView();
        far.HP = 1;
        far.aggro(hero);
        assertTrue(far.state == far.HUNTING);

        OracleObserver.Read read = new OracleObserver().observe();
        OracleView view = read.view();
        Observation fairRead = new Observer().observe();
        Skeleton.Serialized fair = Skeleton.Serialized.of(fairRead);
        Skeleton.Serialized marked = Skeleton.Serialized.of(read.observation());

        assertEquals(Dungeon.seed, view.seed());
        fair.assertAbsent(String.valueOf(Dungeon.seed));
        fair.assertAbsent(DungeonSeed.convertToCode(Dungeon.seed));

        // The identities: every unknown class of the three families, under its appearance.
        assertEquals(expectedIdentities(), new TreeSet<>(describe(view.identities())), "every unknown class, once");
        for (OracleView.Identity identity : view.identities()) {
            assertNotEquals(identity.appearance(), identity.trueName());
            fair.assertAbsent(identity.trueName());
            marked.assertAbsent(identity.trueName());
            fair.assertAbsent(identity.type());
        }

        // The mobs: every one, drawn or not, with its fields as the game holds them.
        assertEquals(level.mobs.size(), view.mobs().size(), "every mob, drawn or not");
        boolean unseen = false;
        for (OracleView.Presence mob : view.mobs()) {
            unseen |= !mob.seen();
            assertEquals(mob.seen(), fairRead.actors().actors().stream().anyMatch(a -> a.cell() == mob.cell()),
                    "the sidecar says drawn exactly where the fair read draws an actor: " + mob);
        }
        assertTrue(unseen, "the first floor has a mob the hero cannot see");
        OracleView.Presence hurt = presenceAt(view, far.pos);
        assertEquals(far.getClass().getSimpleName(), hurt.type());
        assertEquals(far.name(), hurt.name());
        assertEquals(1, hurt.hp());
        assertEquals(far.HT, hurt.ht());
        assertEquals("HUNTING", hurt.state());
        assertFalse(hurt.seen());
        OracleView.Presence chest = presenceAt(view, mimicCell);
        assertEquals("Mimic", chest.type());
        assertFalse(chest.seen(), "a hidden mimic in view is a chest to the fair read, not an actor");
        assertEquals("PASSIVE", chest.state());
        assertEquals(List.of(mimicCell), view.hiddenMimics());
        assertTrue(fairRead.map().heaps().stream().anyMatch(h -> h.cell() == mimicCell && h.kind() == HeapKind.CHEST),
                "the fair read draws the chest");
        fair.assertAbsent("Mimic");

        // The secrets: the door and the trap, by cell, and the fair map draws neither.
        assertTrue(view.secretDoors().contains(door));
        assertTrue(view.hiddenTraps().contains(new OracleView.Secret(trapCell, "FrostTrap", trap.name(), true)));
        assertTrue(fairRead.map().traps().stream().noneMatch(t -> t.cell() == trapCell), "no trap drawn at " + trapCell);
        fair.assertAbsent(trap.name());
        marked.assertAbsent(trap.name());
        fair.assertAbsent("SECRET");
    }

    @Test
    @DisplayName("no fair path reaches the sidecar: only the oracle and the launcher name it, nothing reaches a class by name, and nothing an Observation or a Decider reaches lives outside api")
    void no_fair_path_reaches_the_sidecar() {
        JavaClasses harness = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.shatterfish.harness");
        NO_ONE_ELSE_HOLDS_THE_SIDECAR.check(harness);
        ONLY_THE_LAUNCHER_MAKES_AN_ORACLE.check(harness);
        NO_CLASS_BY_NAME.check(harness);

        // The Brain-facing type has no path to the sidecar: every type an Observation is made of,
        // through its records, lists and sealed interfaces, lives in api or the JDK, and a Decider
        // takes an Observation and nothing else.
        Set<Class<?>> reached = new LinkedHashSet<>();
        reach(Observation.class, reached);
        assertTrue(reached.size() > 40, "the Observation's types were walked, Actions included: " + reached.size());
        for (Class<?> type : reached) {
            String pkg = type.getPackageName();
            assertTrue(pkg.equals("org.shatterfish.api") || pkg.equals("java.lang") || pkg.equals("java.util"),
                    type.getName() + " is reachable from an Observation and lives outside api and the JDK");
        }
        assertFalse(reached.contains(OracleView.class));
        List<Method> decide = new ArrayList<>();
        for (Method method : Decider.class.getMethods()) {
            if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                decide.add(method);
            }
        }
        assertEquals(1, decide.size(), "one seam: " + decide);
        assertEquals(List.of(Observation.class), List.of(decide.get(0).getParameterTypes()), "an Observation and nothing else");
    }

    @Test
    @DisplayName("the gate bites: a class holding the sidecar, one making an oracle, and one reaching a class by name each violate")
    void the_launcher_has_the_nested_classes_the_gate_names() {
        java.util.Set<String> nested = new java.util.TreeSet<>();
        for (Class<?> inner : Launcher.class.getDeclaredClasses()) {
            nested.add(inner.getSimpleName());
        }
        assertEquals(java.util.Set.of("Launch", "Benchmark"), nested,
                "a new nested class of the launcher is reviewed here before the gate names it, or it is not an oracle consumer");
        java.util.Set<String> inBenchmark = new java.util.TreeSet<>();
        for (Class<?> inner : Launcher.Benchmark.class.getDeclaredClasses()) {
            inBenchmark.add(inner.getSimpleName());
        }
        assertEquals(java.util.Set.of("Report"), inBenchmark);
    }

    @Test
    void the_gate_bites() {
        assertTrue(NO_ONE_ELSE_HOLDS_THE_SIDECAR.evaluate(new ClassFileImporter().importClasses(HoldsTheSidecar.class)).hasViolation(),
                "a harness class with an OracleView field passes the sidecar rule");
        assertTrue(ONLY_THE_LAUNCHER_MAKES_AN_ORACLE.evaluate(new ClassFileImporter().importClasses(MakesAnOracle.class)).hasViolation(),
                "a class other than the launcher constructing an oracle passes the constructor rule");
        assertTrue(NO_CLASS_BY_NAME.evaluate(new ClassFileImporter().importClasses(ReachesByName.class)).hasViolation(),
                "a harness class calling forName passes the name rule");
    }

    @Test
    @DisplayName("the flag is the launcher's: --oracle picks the oracle branch, nothing else does, and anything else is refused by name")
    void the_flag_is_the_launchers() {
        assertEquals(new Launcher.Launch(42L, true), Launcher.Launch.parse(new String[] {"--oracle", "42"}));
        assertEquals(new Launcher.Launch(42L, true), Launcher.Launch.parse(new String[] {"42", "--oracle"}));
        assertEquals(new Launcher.Launch(7L, false), Launcher.Launch.parse(new String[] {"7"}));
        assertFalse(Launcher.Launch.parse(new String[0]).oracle(), "off by default");
        refused("--oracel", new String[] {"--oracel"});
        refused("two seeds", new String[] {"7", "8"});
        refused("out of range", new String[] {"99999999999999999999"});
        refused("out of range", new String[] {"-1"});
        refused("out of range", new String[] {String.valueOf(DungeonSeed.TOTAL_SEEDS)});

        // The branch itself, at a wait: the read follows the flag, and the sidecar is printed
        // only under it.
        atTheFirstWait();
        ByteArrayOutputStream fairOut = new ByteArrayOutputStream();
        Observation fair = Launcher.read(new Launcher.Launch(SEED, false), new PrintStream(fairOut, true, StandardCharsets.UTF_8));
        assertFalse(fair.header().oracle());
        assertFalse(fairOut.toString(StandardCharsets.UTF_8).contains("ORACLE"), fairOut.toString(StandardCharsets.UTF_8));
        ByteArrayOutputStream oracleOut = new ByteArrayOutputStream();
        Observation marked = Launcher.read(new Launcher.Launch(SEED, true), new PrintStream(oracleOut, true, StandardCharsets.UTF_8));
        assertTrue(marked.header().oracle());
        String printed = oracleOut.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Launcher: ORACLE seed " + Dungeon.seed), printed);
        assertTrue(printed.contains("not drawn"), "an unseen mob is printed: " + printed);
        assertEquals(Set.of("header"), ObservationDiff.of(fair, marked));
    }

    private static void refused(String reason, String[] args) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Launcher.Launch.parse(args),
                reason);
        assertTrue(refused.getMessage().contains(reason) || refused.getMessage().contains(args[0]), refused.getMessage());
    }

    // --- the fixtures the gate is shown to bite on

    static final class HoldsTheSidecar {
        OracleView view;
    }

    static final class MakesAnOracle {
        Object oracle = new OracleObserver();
    }

    static final class ReachesByName {
        Object reached() throws ClassNotFoundException {
            return Class.forName("org.shatterfish.harness.observer.OracleObserver");
        }
    }

    // --- the walk

    /**
     * Every class a record's components can reach, through lists, arrays, wildcards, nested
     * records and sealed interfaces; an open interface or a type variable fails, since what stands
     * behind it cannot be walked.
     */
    private static void reach(Class<?> type, Set<Class<?>> into) {
        if (type.isPrimitive()) {
            return;
        }
        if (type.isArray()) {
            reach(type.getComponentType(), into);
            return;
        }
        if (!into.add(type)) {
            return;
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                reachType(component.getGenericType(), into);
            }
        } else if (type.isSealed()) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                reach(permitted, into);
            }
        } else if (type.isInterface() && type.getPackageName().equals("org.shatterfish.api")) {
            fail(type.getName() + " is an open interface: what stands behind it cannot be walked");
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
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                reachType(bound, into);
            }
        } else if (type instanceof GenericArrayType array) {
            reachType(array.getGenericComponentType(), into);
        } else {
            fail(type + " is a type variable: what stands behind it cannot be walked");
        }
    }

    // --- expectations built from the game

    private static Set<String> expectedIdentities() {
        Set<String> expected = new TreeSet<>();
        for (Class<? extends Item> type : Potion.getUnknown()) {
            expected.add(describe("potion", type));
        }
        for (Class<? extends Item> type : Scroll.getUnknown()) {
            expected.add(describe("scroll", type));
        }
        for (Class<? extends Item> type : Ring.getUnknown()) {
            expected.add(describe("ring", type));
        }
        assertTrue(expected.size() >= 3, "the Warrior starts with unknown potions, scrolls and rings: " + expected);
        return expected;
    }

    private static String describe(String family, Class<? extends Item> type) {
        Item item = Reflection.newInstance(type);
        return family + " " + item.name() + " = " + item.trueName() + " " + type.getSimpleName();
    }

    private static List<String> describe(List<OracleView.Identity> identities) {
        List<String> described = new ArrayList<>();
        for (OracleView.Identity identity : identities) {
            described.add(identity.family() + " " + identity.appearance() + " = " + identity.trueName() + " " + identity.type());
        }
        assertEquals(described.size(), new TreeSet<>(described).size(), "each identity once");
        return described;
    }

    private static OracleView.Presence presenceAt(OracleView view, int cell) {
        for (OracleView.Presence mob : view.mobs()) {
            if (mob.cell() == cell) {
                return mob;
            }
        }
        throw new AssertionError("no mob at " + cell + " in " + view.mobs());
    }

    /** The generator stack's depth (SPD-classes/.../utils/Random.java:37), private to the game. */
    private static int generatorDepth() throws Exception {
        java.lang.reflect.Field field = Random.class.getDeclaredField("generators");
        field.setAccessible(true);
        return ((java.util.Collection<?>) field.get(null)).size();
    }

    private Mob mobOutOfView() {
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob) && mob.sprite != null) {
                return mob;
            }
        }
        throw new AssertionError("no mob out of view");
    }

    private int wallInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                return cell;
            }
        }
        throw new AssertionError("no wall in view");
    }

    private List<Integer> floorsInView(int count) {
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < level.length() && cells.size() < count; cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null) {
                cells.add(cell);
            }
        }
        assertEquals(count, cells.size(), "enough free floor in view");
        return cells;
    }
}
