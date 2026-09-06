package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The header section of an Observation (ADR-0005): what a Run is, as the screens before and
 * around the play area show it. It carries the schema version, the upstream release, the Codex
 * version, the hero's class, the challenges chosen, the depth and branch, whether the floor is
 * sealed by a boss fight ({@code Level.locked}, {@code core/.../levels/Level.java:180}, drawn as
 * the locked stairs and the boss bar), whether the Observation is an oracle one, and the kind of
 * Prompt open if any.
 *
 * <p>It carries neither the wait index, nor the seed, nor the salt, nor a turn counter: the brain
 * counts waits itself, the seed would let a brain fingerprint published seeds, and the game draws
 * no turn counter. A test holds the component names to that.
 *
 * @param version the schema version, {@link ObservationCodec#SCHEMA_VERSION} for an Observation
 *                this codec encodes; the Run log records it and a Replay refuses to compare across
 *                versions
 * @param upstreamTag the upstream release the game is, as {@code docs/UPSTREAM.md} pins it
 * @param codexVersion the Codex version the Observation was taken against; empty until E2
 * @param challenges the challenges chosen, in name order, each at most once
 * @param sealed whether the floor is locked by a boss fight
 * @param oracle whether an {@code OracleObserver} produced this Observation
 * @param prompt the kind of the open Prompt, or {@link PromptKind#NONE}
 */
public record HeaderSection(int version, String upstreamTag, String codexVersion, HeroClass heroClass,
                            List<Challenge> challenges, int depth, int branch, boolean sealed, boolean oracle,
                            PromptKind prompt) {

    public HeaderSection {
        Canon.require(version >= 1, "the schema version starts at 1: " + version);
        upstreamTag = Canon.text(upstreamTag, "upstreamTag");
        codexVersion = Canon.text(codexVersion, "codexVersion");
        Objects.requireNonNull(heroClass, "heroClass");
        Objects.requireNonNull(prompt, "prompt");
        challenges = Canon.sorted(challenges, Comparator.comparing(Challenge::name), "challenges");
        Canon.noRepeats(challenges, "challenges");
        Canon.require(depth >= 0, "depth is counted from the surface: " + depth);
        Canon.require(branch >= 0, "branch is an index: " + branch);
    }
}
