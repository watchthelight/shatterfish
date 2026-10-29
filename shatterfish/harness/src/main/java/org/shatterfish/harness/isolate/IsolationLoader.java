package org.shatterfish.harness.isolate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * A second copy of the game in the same JVM (E10 spike, ADR-0025): a class loader that defines the game's classes, and the
 * harness's own, itself, from the same class files the parent loads, so that every static the game keeps is the copy's
 * and a rollout can run without swapping the live Run's state out of the process.
 *
 * <p><b>What is the copy's.</b> Classes of the game ({@code com.shatteredpixel.}), of its toolkit ({@code com.watabou.}),
 * of the harness ({@code org.shatterfish.harness.}), and libGDX's reflection helpers ({@code com.badlogic.gdx.utils.reflect.}).
 * The last is not a convenience: the game turns the class names in a save into classes through
 * {@code ClassReflection.forName}, and {@code Class.forName} resolves a name by the loader of its caller, so libGDX's helper
 * left in the parent would instantiate the <em>parent's</em> mobs and items into the copy's level.
 *
 * <p><b>What stays the parent's.</b> The JDK; libGDX and its natives, because the JNI specification forbids loading one
 * native library into two loaders (ADR-0009 option 12), which also makes {@code Gdx.app}, {@code Gdx.files} and
 * {@code Gdx.gl} one set of statics for every world (the consequence is recorded in ADR-0025); {@code org.json};
 * {@code org.shatterfish.api}; and the classes in {@link #SHARED}, which must be one class for both sides: the interfaces
 * between the loaders, the backend and the no-op GL binding that live in libGDX's statics, {@code ScrubbedStart} and {@code RunHistory}.
 *
 * <p>The loader reads class bytes as resources of its parent, so it needs no class path of its own. It defines nothing it
 * has not been asked for, so a world pays for the classes a Run touches.
 */
public final class IsolationLoader extends ClassLoader {

    private static final List<String> OWN_PREFIXES = List.of(
            "com.shatteredpixel.", "com.watabou.", "org.shatterfish.harness.", "com.badlogic.gdx.utils.reflect.");

    /** Classes, with their nested classes, that both sides must see as the same class. */
    static final Set<String> SHARED = Set.of(
            "org.shatterfish.harness.isolate.RolloutWorld",
            "org.shatterfish.harness.isolate.WorldFactory",
            "org.shatterfish.harness.driver.ScrubbedStart",
            "org.shatterfish.harness.driver.RunHistory",
            "org.shatterfish.harness.boot.SharedBackend",
            "org.shatterfish.harness.scene.NoOpGL");

    private long defined;

    public IsolationLoader(ClassLoader parent) {
        super("isolated-world", parent);
    }

    /** Whether this loader defines {@code className} itself. */
    public static boolean isOwn(String className) {
        boolean own = false;
        for (String prefix : OWN_PREFIXES) {
            if (className.startsWith(prefix)) {
                own = true;
                break;
            }
        }
        if (!own) {
            return false;
        }
        for (String shared : SHARED) {
            if (className.equals(shared) || className.startsWith(shared + "$")) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!isOwn(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = define(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> define(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = getParent().getResourceAsStream(path)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        defined++;
        return defineClass(name, bytes, 0, bytes.length);
    }

    /** How many classes this loader has defined, for the measurement. */
    public long definedClasses() {
        return defined;
    }
}
