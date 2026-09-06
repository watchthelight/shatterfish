package org.shatterfish.api;

/**
 * Marker for the {@code api} module: plain data types shared by every other Shatterfish module.
 *
 * <p>This module depends on nothing but {@code java.lang} and {@code java.util}
 * ({@code ApiBoundaryTest}). Its contents are the {@link Observation} record tree with its
 * {@link ObservationCodec} and {@link ObservationJson}, the {@link Action} kinds, the opaque
 * {@link Belief}, and the {@link JsonWriter} the Run log shares; the {@code Decision} and run-log
 * records arrive with their stories.
 */
public final class ShatterfishApi {

    /** Schema version of the Observation's encoding; see {@link ObservationCodec#SCHEMA_VERSION}. */
    public static final int SCHEMA_VERSION = ObservationCodec.SCHEMA_VERSION;

    private ShatterfishApi() {
    }
}
