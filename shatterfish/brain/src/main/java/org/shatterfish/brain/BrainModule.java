package org.shatterfish.brain;

import org.shatterfish.api.ShatterfishApi;

/**
 * Marker for the {@code brain} module.
 *
 * <p>Information parity is the only rule of play: the brain sees the game exclusively through
 * {@code org.shatterfish.api} types produced by the harness's {@code Observer}. It may not
 * import {@code com.shatteredpixel.*} or {@code com.watabou.*}; the Gradle dependency graph,
 * a resolution check in {@code build.gradle}, and {@code BrainImportsNoGameCodeTest} all
 * enforce this. The brain itself is not written until E1-E3 exist.
 */
public final class BrainModule {

    /** The api schema this brain was written against. */
    public static final int API_SCHEMA_VERSION = ShatterfishApi.SCHEMA_VERSION;

    private BrainModule() {
    }
}
