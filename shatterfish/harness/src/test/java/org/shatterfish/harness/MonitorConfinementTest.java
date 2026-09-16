package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.harness.scene.SceneStepper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No Shatterfish code takes a monitor on a game type (ADR-0013's deadlock rule; FR-12), held as an
 * ArchUnit rule whose condition reads each class's bytecode: every {@code monitorenter} whose
 * operand is statically a game type, and every synchronized method on a class that is a game
 * type, is a violation. Two classes are exempt by name, each for a reason written here and in
 * ADR-0013's amendment, and each is shown to need its exemption:
 *
 * <ul>
 * <li>{@link SceneStepper}: the fence of story 1.3 holds the actor thread's monitor and every
 * moving sprite's across a frame, on purpose, so that the frame and the actor thread cannot
 * interleave; {@code FenceInvariantTest} holds that design.</li>
 * <li>{@link HeadlessScene}: it is the scene, and the game locks its scene on the render thread —
 * {@code GameScene.update} is synchronized ({@code …/scenes/GameScene.java:867}), as are
 * {@code erase} ({@code :967}) and {@code addMobSprite} ({@code :1087}), the actor thread takes
 * {@code synchronized (scene)} ({@code :1098}), and every member-list method of {@code Group} is
 * synchronized on the group ({@code SPD-classes/…/noosa/Group.java:49}, {@code :99}, {@code :124},
 * {@code :201}); the override of {@code update()} keeps the game's lock, and {@code openWindow()}
 * reads the member list under the lock the group's own writers take. That is the game's rule for
 * its scene, not a monitor Shatterfish invented.</li>
 * </ul>
 *
 * <p>The rule sees the monitors Shatterfish code declares, a {@code monitorenter} or a
 * synchronized method of its own, not the ones a synchronized game method takes on its behalf:
 * every read of the window in front goes through {@code Group.shatterfishMembers()}, which is
 * synchronized on the scene ({@code Group.java:49}), so the fair path does hold the scene monitor
 * for the length of that call. That is the game's method and the game's lock, held and released
 * inside one call with nothing of Shatterfish's nested in it; the deadlock rule is about monitors
 * held across Shatterfish code, and that is what this rule holds.
 *
 * <p>The operand's type is the static type of what produced it: a field's declared type, a
 * parameter's, a local's from the debug table, a call's return type, or the class of a
 * {@code new}; javac's {@code dup; astore; monitorenter} is followed back through the {@code dup}.
 * A producer the analysis cannot type is reported as a violation rather than passed, so the rule
 * fails loud rather than silent. A game object held in a variable declared as {@code Object}
 * passes by static type; that is the rule's limit, and the fixture that shows it is named.
 */
@AnalyzeClasses(packages = "org.shatterfish", importOptions = ImportOption.DoNotIncludeTests.class)
class MonitorConfinementTest {

    /** The packages of the game and its engine: a type under them, or a subtype of one, is a game type. */
    private static final List<String> GAME_PACKAGES = List.of("com.shatteredpixel.", "com.watabou.");

    // The rule reads local variable tables, which java-module.gradle pins with -g:source,lines,vars.

    @ArchTest
    static final ArchRule no_shatterfish_code_takes_a_monitor_on_a_game_type = classes()
            .that().resideInAPackage("org.shatterfish..")
            .and().doNotHaveFullyQualifiedName(SceneStepper.class.getName())
            .and().doNotHaveFullyQualifiedName(HeadlessScene.class.getName())
            .should(takeNoMonitorOnAGameType())
            .because("Shatterfish code never takes the scene monitor or any game object's monitor (ADR-0013);"
                    + " the stepper's fence and the scene's own methods are the two exemptions, each with its reason");

    @Test
    @DisplayName("the exemptions are load-bearing: the stepper and the scene each take a monitor the rule would refuse")
    void the_exemptions_are_load_bearing() {
        ArchRule bare = classes().should(takeNoMonitorOnAGameType());
        assertTrue(bare.evaluate(new ClassFileImporter().importClasses(SceneStepper.class)).hasViolation(),
                "SceneStepper's fence takes the actor thread's and the sprites' monitors");
        assertTrue(bare.evaluate(new ClassFileImporter().importClasses(HeadlessScene.class)).hasViolation(),
                "HeadlessScene's synchronized methods lock the scene");
    }

    @Test
    @DisplayName("the rule bites: a monitor on a game object and a synchronized method on a game type violate; a monitor on an own field passes")
    void the_rule_bites() {
        assertTrue(violates(SynchronizesOnAGameField.class), "synchronized (Dungeon.hero)");
        assertTrue(violates(SynchronizesOnAGameParameter.class), "synchronized (hero) with a Hero parameter");
        assertTrue(violates(SynchronizesOnAGameLocal.class), "synchronized (level) with a Level local");
        assertTrue(violates(SynchronizesOnACallResult.class), "synchronized (hero.belongings.backpack)");
        assertTrue(violates(ExtendsAGameTypeAndSynchronizes.class), "a synchronized method on an Item subclass");
        assertFalse(violates(SynchronizesOnItsOwnField.class), "synchronized (lock) on an Object of its own");
        assertFalse(violates(SynchronizesOnItself.class), "synchronized (this) on a Shatterfish class");
        assertFalse(violates(ImplementsAGameInterfaceAndSynchronizes.class), "a Shatterfish object behind a game interface is its own");
        assertFalse(violates(SynchronizesOnAnInterfaceTypedField.class), "a variable declared as a game interface is not a game object");
        assertFalse(violates(StaticSynchronizedOnAGameSubclass.class), "a static synchronized method locks the Class object");
        // The limit: a game object behind a variable declared Object passes by static type.
        assertFalse(violates(HidesAGameObjectBehindObject.class), "the rule sees static types only");
    }

    private static boolean violates(Class<?> fixture) {
        EvaluationResult result = classes().should(takeNoMonitorOnAGameType())
                .evaluate(new ClassFileImporter().importClasses(fixture));
        return result.hasViolation();
    }

    @Test
    @DisplayName("a violation names the class, the method and the type")
    void a_violation_names_the_class_the_method_and_the_type() {
        List<String> found = monitorsOnGameTypes(new ClassFileImporter().importClasses(SynchronizesOnAGameField.class)
                .get(SynchronizesOnAGameField.class));
        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).contains(SynchronizesOnAGameField.class.getName() + ".f"), found.get(0));
        assertTrue(found.get(0).contains(Hero.class.getName() + ", a game type"), found.get(0));
        List<String> method = monitorsOnGameTypes(new ClassFileImporter().importClasses(ExtendsAGameTypeAndSynchronizes.class)
                .get(ExtendsAGameTypeAndSynchronizes.class));
        assertEquals(1, method.size(), method.toString());
        assertTrue(method.get(0).contains(".f is synchronized"), method.get(0));
    }

    // --- the fixtures

    static final class SynchronizesOnAGameField {
        void f() {
            synchronized (Dungeon.hero) {
                f();
            }
        }
    }

    static final class SynchronizesOnAGameParameter {
        void f(Hero hero) {
            synchronized (hero) {
                f(hero);
            }
        }
    }

    static final class SynchronizesOnAGameLocal {
        void f() {
            com.shatteredpixel.shatteredpixeldungeon.levels.Level level = Dungeon.level;
            synchronized (level) {
                f();
            }
        }
    }

    static final class SynchronizesOnACallResult {
        void f() {
            synchronized (Dungeon.hero.belongings.backpack) {
                f();
            }
        }
    }

    static final class ExtendsAGameTypeAndSynchronizes extends Item {
        synchronized void f() {
        }
    }

    static final class SynchronizesOnItsOwnField {
        private final Object lock = new Object();

        void f() {
            synchronized (lock) {
                f();
            }
        }
    }

    static final class SynchronizesOnItself {
        synchronized void f() {
            synchronized (this) {
                f();
            }
        }
    }

    static final class ImplementsAGameInterfaceAndSynchronizes implements com.watabou.utils.Signal.Listener<String> {
        @Override
        public synchronized boolean onSignal(String s) {
            return false;
        }
    }

    static final class SynchronizesOnAnInterfaceTypedField {
        private final com.watabou.utils.Signal.Listener<String> listener = s -> false;

        void f() {
            synchronized (listener) {
                f();
            }
        }
    }

    static final class StaticSynchronizedOnAGameSubclass extends Item {
        static synchronized void f() {
        }
    }

    static final class HidesAGameObjectBehindObject {
        void f() {
            Object o = Dungeon.level;
            synchronized (o) {
                f();
            }
        }
    }

    // --- the condition

    static ArchCondition<JavaClass> takeNoMonitorOnAGameType() {
        return new ArchCondition<>("take no monitor on a game type") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (String violation : monitorsOnGameTypes(item)) {
                    events.add(SimpleConditionEvent.violated(item, violation));
                }
            }
        };
    }

    /** Every monitor {@code item} takes on a game type, described for a person. */
    static List<String> monitorsOnGameTypes(JavaClass item) {
        List<String> found = new ArrayList<>();
        ClassNode node = read(item);
        boolean ownerIsGame = isGameType(item.getName());
        for (MethodNode method : node.methods) {
            // A static synchronized method locks the Class object, which is this class's own.
            if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0 && (method.access & Opcodes.ACC_STATIC) == 0 && ownerIsGame) {
                found.add(item.getName() + "." + method.name + " is synchronized, and " + item.getSimpleName()
                        + " is a game type: the method locks a game object");
            }
            if (method.instructions.size() == 0) {
                continue;
            }
            Frame<SourceValue>[] frames;
            try {
                frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, method);
            } catch (AnalyzerException unreadable) {
                found.add(item.getName() + "." + method.name + " could not be analysed: " + unreadable.getMessage());
                continue;
            }
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode insn = method.instructions.get(i);
                if (insn.getOpcode() != Opcodes.MONITORENTER || frames[i] == null) {
                    continue;
                }
                Frame<SourceValue> frame = frames[i];
                SourceValue operand = frame.getStack(frame.getStackSize() - 1);
                for (String type : producerTypes(node, method, frames, operand, 0)) {
                    if (type.startsWith("?")) {
                        found.add(item.getName() + "." + method.name + " takes a monitor on a value the rule cannot type ("
                                + type.substring(1) + "): name the type or take no monitor");
                    } else if (!loadable(type)) {
                        found.add(item.getName() + "." + method.name + " takes a monitor on a " + type
                                + ", which the rule cannot load to type: name a type on the classpath or take no monitor");
                    } else if (isGameType(type)) {
                        found.add(item.getName() + "." + method.name + " takes a monitor on a " + type + ", a game type");
                    }
                }
            }
        }
        return found;
    }

    /** The static types of the producers of {@code value}, followed back through dup and checkcast. */
    private static List<String> producerTypes(ClassNode node, MethodNode method, Frame<SourceValue>[] frames,
                                              SourceValue value, int depth) {
        List<String> types = new ArrayList<>();
        if (depth > 8) {
            types.add("?a producer chain deeper than eight");
            return types;
        }
        for (AbstractInsnNode producer : value.insns) {
            int at = method.instructions.indexOf(producer);
            switch (producer.getOpcode()) {
                case Opcodes.DUP, Opcodes.CHECKCAST -> {
                    Frame<SourceValue> before = frames[at];
                    if (before == null) {
                        types.add("?an unreachable dup");
                    } else if (producer.getOpcode() == Opcodes.CHECKCAST) {
                        types.add(Type.getObjectType(((TypeInsnNode) producer).desc).getClassName());
                    } else {
                        types.addAll(producerTypes(node, method, frames, before.getStack(before.getStackSize() - 1), depth + 1));
                    }
                }
                case Opcodes.ALOAD -> types.add(localType(node, method, (VarInsnNode) producer, at));
                case Opcodes.GETFIELD, Opcodes.GETSTATIC -> types.add(Type.getType(((FieldInsnNode) producer).desc).getClassName());
                case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESPECIAL ->
                        types.add(Type.getReturnType(((MethodInsnNode) producer).desc).getClassName());
                case Opcodes.NEW -> types.add(Type.getObjectType(((TypeInsnNode) producer).desc).getClassName());
                case Opcodes.ACONST_NULL -> types.add("java.lang.Object");
                default -> types.add("?opcode " + producer.getOpcode() + " at " + at);
            }
        }
        return types;
    }

    /** The static type of local {@code var} at instruction {@code at}: this, a parameter, or the debug table's entry. */
    private static String localType(ClassNode node, MethodNode method, VarInsnNode load, int at) {
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int slot = 0;
        if (!isStatic) {
            if (load.var == 0) {
                return Type.getObjectType(node.name).getClassName();
            }
            slot = 1;
        }
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            if (slot == load.var) {
                return argument.getClassName();
            }
            slot += argument.getSize();
        }
        if (method.localVariables != null) {
            for (LocalVariableNode local : method.localVariables) {
                int start = method.instructions.indexOf(local.start);
                int end = method.instructions.indexOf(local.end);
                if (local.index == load.var && start <= at && at < end) {
                    return Type.getType(local.desc).getClassName();
                }
            }
        }
        return "?local " + load.var + " with no debug entry at " + at;
    }

    private static final Set<String> PRIMITIVES = Set.of("int", "long", "boolean", "byte", "short", "char", "float",
            "double", "void");

    /** Whether the rule can load {@code className} to look at what it extends; a primitive or array needs no loading. */
    static boolean loadable(String className) {
        return className.endsWith("[]") || PRIMITIVES.contains(className) || load(className) != null;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, MonitorConfinementTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    /**
     * Whether {@code className} or a class it extends lives under a game package. Interfaces do not
     * count, as a type or as a declared type: a Shatterfish object that implements a game interface,
     * the log listener on the game's signal, is still Shatterfish's own object, and a monitor on it
     * is a monitor the game never takes; an object of a class the game defines, or a subclass of
     * one, is the game's. A type the rule cannot load is reported by the caller, never passed here.
     */
    static boolean isGameType(String className) {
        if (className.endsWith("[]") || PRIMITIVES.contains(className)) {
            return false;
        }
        for (String prefix : GAME_PACKAGES) {
            if (className.startsWith(prefix)) {
                Class<?> named = load(className);
                return named == null || !named.isInterface();
            }
        }
        Class<?> type = load(className);
        if (type == null || type.isInterface()) {
            return false;
        }
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (under(c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean under(Class<?> c) {
        String name = c.getName();
        for (String prefix : GAME_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode read(JavaClass item) {
        URI uri = item.getSource().orElseThrow(() -> new AssertionError(item.getName() + " has no source")).getUri();
        try (InputStream in = uri.toURL().openStream()) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + uri, unreadable);
        } catch (IllegalArgumentException unsupported) {
            throw new AssertionError(item.getName() + ": a class file version ASM 9.9 does not read; raise org.ow2.asm in"
                    + " shatterfish/harness/build.gradle", unsupported);
        }
    }
}
