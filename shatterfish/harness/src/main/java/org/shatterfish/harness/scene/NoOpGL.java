package org.shatterfish.harness.scene;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A graphics binding that accepts every call and does nothing, so that classes which touch GL
 * during construction can be created without a context.
 *
 * <p>Every texture the game builds calls {@code glGenTexture} in its constructor
 * ({@code SPD-classes/.../glwrap/Texture.java:47}) and uploads its pixels through {@code Gdx.gl}
 * after that, and libGDX's own {@code Texture}, which FreeType glyph pages use, does the same
 * through {@code Gdx.gl20}. Neither reads anything back that matters: a generated name of zero
 * is a legal texture name to bind, and nothing in the game queries GL state to decide behaviour.
 * So a binding whose every method returns its zero value lets the whole scene construct, with
 * its atlases loaded as {@code Pixmap}s through {@code Gdx.files}, exactly as ADR-0015 decided.
 *
 * <p>The interface has upwards of three hundred methods and the game only needs them to not
 * throw, so this is a dynamic proxy rather than a hand-written stub. Reflection is fine here:
 * this is {@code harness}, and the module that forbids reflection is {@code brain}.
 * {@link HeadlessScene} refuses to construct unless {@link #isNoOp(Object)} holds for
 * {@code Gdx.gl}, which is what makes "installed before any texture" a property of the code
 * rather than of the order tests happen to run in.
 */
public final class NoOpGL {

    private NoOpGL() {
    }

    public static GL20 gl20() {
        return (GL20) proxy(GL20.class);
    }

    public static GL30 gl30() {
        return (GL30) proxy(GL30.class);
    }

    /** Whether {@code binding} is one of ours rather than a real context or nothing at all. */
    public static boolean isNoOp(Object binding) {
        return binding != null
                && Proxy.isProxyClass(binding.getClass())
                && Proxy.getInvocationHandler(binding) instanceof ZeroValues;
    }

    private static Object proxy(Class<?> iface) {
        return Proxy.newProxyInstance(
                NoOpGL.class.getClassLoader(),
                new Class<?>[]{iface},
                new ZeroValues());
    }

    private static final class ZeroValues implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return zeroOf(method.getReturnType());
        }
    }

    /**
     * The zero value of a return type. Texture creation reads the generated name as an int, so 0
     * is a valid answer; string queries get an empty string rather than null so that callers which
     * parse a version string do not fail on a null.
     */
    private static Object zeroOf(Class<?> type) {
        if (!type.isPrimitive()) {
            return type == String.class ? "" : null;
        }
        if (type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        throw new IllegalStateException("unhandled primitive: " + type);
    }
}
