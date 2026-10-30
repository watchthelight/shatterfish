package org.shatterfish.harness.practice;

import org.shatterfish.api.HeroClass;

import java.util.Objects;

/**
 * A practice start: which boss floor a Run begins on and which kit profile its hero arrives with.
 *
 * <p>This is the whole definition of the difference between a practice Run and a Run that began on
 * depth 1. A practice Run is determined by (upstream tag, seed, salt, hero class, this, action
 * list); the kit profile is resolved to a {@link KitSpec} for the hero class, and the stamp the
 * header carries names the profile and the hash of the kit it resolved to, so a profile edited after
 * a Run was played is a refusal at replay and not a silent difference (ADR-0024).
 *
 * @param boss    the boss in front of whom the Run starts
 * @param profile the name of the kit profile, from {@link KitProfiles#names()}
 */
public record Practice(PracticeBoss boss, String profile) {

    public Practice {
        Objects.requireNonNull(boss, "boss");
        Objects.requireNonNull(profile, "profile");
        if (!KitProfiles.names().contains(profile)) {
            throw new IllegalArgumentException("there is no kit profile " + profile + "; the ones there are: "
                    + KitProfiles.names());
        }
    }

    /** The kit this start gives a hero of {@code heroClass}. */
    public KitSpec kit(HeroClass heroClass) {
        return KitProfiles.kit(profile, boss, heroClass);
    }

    /** What the header of a Run of {@code heroClass} states: {@code <boss>/<profile>@<kit hash>}. */
    public String stamp(HeroClass heroClass) {
        return boss.id() + "/" + profile + "@" + kit(heroClass).hash();
    }

    /**
     * The start a header's stamp names, for a hero of {@code heroClass}, refusing a stamp whose kit
     * hash is not the one this build resolves the profile to: the profile was edited since, and the
     * Run it stamped is not the Run this build would play.
     */
    public static Practice parse(String stamp, HeroClass heroClass) {
        if (stamp == null || !stamp.matches("[a-z]+/[a-z0-9._]+@[0-9a-f]{16}")) {
            throw new IllegalArgumentException("a practice stamp is <boss>/<profile>@<kit hash>: " + stamp);
        }
        int slash = stamp.indexOf('/');
        int at = stamp.indexOf('@');
        Practice practice = new Practice(PracticeBoss.of(stamp.substring(0, slash)), stamp.substring(slash + 1, at));
        if (!practice.stamp(heroClass).equals(stamp)) {
            throw new IllegalArgumentException("the practice stamp " + stamp + " does not match this build's kit for a "
                    + heroClass + ": the profile " + practice.profile() + " now resolves to "
                    + practice.stamp(heroClass));
        }
        return practice;
    }
}
