package org.shatterfish.api;

/**
 * Marker for the {@code api} module: plain data types shared by every other Shatterfish module.
 *
 * <p>This module depends on nothing but {@code java.lang} and {@code java.util}
 * ({@code ApiBoundaryTest}). Its contents are the {@link Observation} record tree and its
 * {@link ObservationCodec} (story 1.6 onward); the {@code Action}, {@code Decision} and run-log
 * records arrive with their stories.
 */
public final class ShatterfishApi {

    /** Schema version of the Observation's encoding; see {@link ObservationCodec#SCHEMA_VERSION}. */
    public static final int SCHEMA_VERSION = ObservationCodec.SCHEMA_VERSION;

    private ShatterfishApi() {
    }
}
