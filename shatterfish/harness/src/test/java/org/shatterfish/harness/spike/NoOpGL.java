package org.shatterfish.harness.spike;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;

import java.lang.reflect.Proxy;

/**
 * A graphics binding that accepts every call and does nothing, so that classes which touch GL
 * during construction can be created without a context.
 *
 * <p>Story 1.1 spike. The interface has upwards of three hundred methods and the game only needs
 * them to not throw, so this is a dynamic proxy returning each method's zero value rather than a
 * hand-written stub. Reflection is fine here: this is {@code harness} test code, and the module
 * that forbids reflection is {@code brain} (AD-1). If E1.S3 wants a stub with no reflection at
 * all, the same shape written out by hand is a mechanical translation of this.
 */
final class NoOpGL {

    private NoOpGL() {
    }

    static GL20 gl20() {
        return (GL20) proxy(GL20.class);
    }

    static GL30 gl30() {
        return (GL30) proxy(GL30.class);
    }

    private static Object proxy(Class<?> iface) {
        return Proxy.newProxyInstance(
                NoOpGL.class.getClassLoader(),
                new Class<?>[]{iface},
                (p, method, args) -> zeroOf(method.getReturnType()));
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
