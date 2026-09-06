package org.shatterfish.api;

/**
 * One talent as the talents pane shows it: its tier, its name and the points in it
 * ({@code core/.../actors/hero/Hero.java:210}; {@code core/.../ui/TalentsPane.java:162-183}). The
 * points a talent can take are the Codex's knowledge, not the Observation's.
 *
 * @param tier 1 to {@link HeroSection#TALENT_TIERS}
 * @param name the talent's title
 * @param points the points spent in it, 0 or more
 */
public record TalentView(int tier, String name, int points) {

    public TalentView {
        Canon.require(tier >= 1 && tier <= HeroSection.TALENT_TIERS,
                "a talent tier is 1 to " + HeroSection.TALENT_TIERS + ": " + tier);
        name = Canon.text(name, "talent name");
        Canon.require(!name.isEmpty(), "a talent has a name");
        Canon.require(points >= 0, "talent points are not negative: " + points);
    }
}
