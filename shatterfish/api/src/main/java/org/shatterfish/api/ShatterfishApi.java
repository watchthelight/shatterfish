package org.shatterfish.api;

/**
 * Marker for the {@code api} module: plain data types shared by every other Shatterfish module.
 *
 * <p>This module depends on nothing but the JDK. The real contents ({@code Observation},
 * {@code Action}, {@code Decision}, run-log records) arrive with the architecture (E0) and the
 * harness (E1); until then this class only pins the module's identity.
 */
public final class ShatterfishApi {

    /** Schema version of the DTOs; bumped whenever a serialized form changes. */
    public static final int SCHEMA_VERSION = 0;

    private ShatterfishApi() {
    }
}
