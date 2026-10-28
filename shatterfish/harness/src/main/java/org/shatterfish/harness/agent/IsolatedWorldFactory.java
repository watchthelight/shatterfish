package org.shatterfish.harness.agent;

import org.shatterfish.harness.isolate.RolloutWorld;
import org.shatterfish.harness.isolate.WorldFactory;

/**
 * The provider an {@code IsolationLoader} finds through {@link java.util.ServiceLoader} (E10 spike, ADR-0024). It is
 * public with no arguments because the service loader requires both; the instance that answers is the isolated loader's
 * own copy of this class, and its worlds drive that copy of the game.
 */
public final class IsolatedWorldFactory implements WorldFactory {

    public IsolatedWorldFactory() {
    }

    @Override
    public RolloutWorld open(String heroClass) {
        return new IsolatedWorld(heroClass);
    }
}
