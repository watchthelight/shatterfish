package org.shatterfish.harness.isolate;

/**
 * Opens a {@link RolloutWorld} inside the class loader that loaded the factory. The live side finds the isolated copy of
 * the provider with {@link java.util.ServiceLoader} over an {@link IsolationLoader}, so no reflection names a member of
 * the game (the rule of {@code HarnessReflectionTest}), and the provider that answers is the harness's own copy in that
 * loader.
 */
public interface WorldFactory {

    /**
     * Boots the game in this loader, if it is not booted, and starts a Run of {@code heroClass} on a placeholder tuple
     * that belongs to the world and to no real Run. The Run is never played: every rollout replaces it with a scrubbed copy.
     */
    RolloutWorld open(String heroClass);
}
