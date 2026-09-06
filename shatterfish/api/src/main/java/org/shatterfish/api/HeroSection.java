package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The hero as the status pane, the hero window, the talents pane, the bag window and the
 * quickslots show it (ADR-0005; ADR-0006, Hero buffs). Paths abbreviate
 * {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}, at the tag.
 *
 * <p>The hero is not an actor of the actors section; its cell is here, and the Observation holds
 * that cell to be in view and free of any other character. The health is exact, as the status
 * pane prints it ({@code …/ui/StatusPane.java:322-327}), unlike an actor's, which is quantised to
 * its bar.
 *
 * @param cell the hero's cell, drawn by its sprite
 * @param name the hero's name, as the hero window titles it ({@code …/actors/hero/Hero.java:417};
 *             {@code …/windows/WndHero.java:162})
 * @param subclass the subclass, which names the hero once chosen ({@code Hero.java:412-414})
 * @param ability the armour ability's name once chosen, shown as the fourth talent tier and the
 *                action indicator ({@code Hero.java:209}, {@code :390}, {@code :402}); empty before
 * @param level the level, in the hero window's title ({@code WndHero.java:160})
 * @param exp the experience toward the next level, as the exp bar and its text show on either pane
 *            ({@code StatusPane.java:334-345}; {@code WndHero.java:195})
 * @param expToLevel the experience the next level needs, the bar's full value
 * @param hp the health ({@code StatusPane.java:322-327}; {@code WndHero.java:193-194})
 * @param ht the maximum health
 * @param shield the shielding drawn ahead of the health, 0 when none
 * @param strength the strength as the hero window prints it ({@code WndHero.java:190-192})
 * @param strengthBonus the bonus or penalty printed after it, 0 when none
 * @param gold the gold, as the bag window prints it ({@code …/windows/WndBag.java:186})
 * @param energy the alchemical energy, as the bag window prints it when any
 *               ({@code WndBag.java:179}, {@code :219})
 * @param hunger the hunger icon's state ({@code …/actors/buffs/Hunger.java:179-187})
 * @param buffs every buff with an icon, with the turns its description shows, by name then timed
 *              then turns, as an actor's ({@code WndHero.java:301-314}; {@code …/ui/BuffIndicator.java:192-196})
 * @param talents every talent of every tier the pane shows, with its points, by tier then name. The
 *                pane shows a tier from one level below its threshold, the third only with a
 *                subclass and the fourth only with an ability ({@code …/ui/TalentsPane.java:75-84}),
 *                while the hero holds the first two tiers from creation
 *                ({@code …/actors/hero/Talent.java:968-970}; {@code Hero.java:210}); the section
 *                carries what is drawn
 * @param talentPointsAvailable the unspent points per tier, one entry per tier, drawn as open
 *                              stars: 0 below one level under the tier's threshold or without its
 *                              gate, else the tier's level range less the points spent plus any bonus
 *                              ({@code Hero.java:387-396}; {@code TalentsPane.java:183}, {@code :259})
 * @param quickslots the six quickslots in order ({@code …/QuickSlot.java:40})
 */
public record HeroSection(int cell, String name, HeroSubclass subclass, String ability, int level, int exp,
                          int expToLevel, int hp, int ht, int shield, int strength, int strengthBonus, int gold,
                          int energy, Hunger hunger, List<BuffView> buffs, List<TalentView> talents,
                          List<Integer> talentPointsAvailable, List<QuickslotView> quickslots) {

    /** The talent tiers the pane shows ({@code …/actors/hero/Talent.java:957}). */
    public static final int TALENT_TIERS = 4;

    /** The quickslots the toolbar has ({@code …/QuickSlot.java:40}). */
    public static final int QUICKSLOTS = 6;

    public HeroSection {
        Canon.cell(cell, "the hero");
        name = Canon.text(name, "hero name");
        Objects.requireNonNull(subclass, "subclass");
        ability = Canon.text(ability, "ability");
        Canon.require(level >= 1, "a hero is at least level 1: " + level);
        Canon.require(exp >= 0, "experience is not negative: " + exp);
        Canon.require(expToLevel >= 1, "the next level needs some experience: " + expToLevel);
        Canon.require(ht >= 1, "maximum health is at least 1: " + ht);
        Canon.require(hp >= 0 && hp <= ht, "health is between 0 and " + ht + ": " + hp);
        Canon.require(shield >= 0, "shielding is not negative: " + shield);
        Canon.require(strength >= 1, "strength is at least 1: " + strength);
        Canon.require(gold >= 0, "gold is not negative: " + gold);
        Canon.require(energy >= 0, "energy is not negative: " + energy);
        Objects.requireNonNull(hunger, "hunger");
        buffs = Canon.sorted(buffs, Comparator.comparing(BuffView::name).thenComparing(BuffView::timed)
                .thenComparingInt(BuffView::turnsHundredths), "hero buffs");
        Canon.noRepeats(buffs, "hero buffs");
        talents = Canon.sorted(talents, Comparator.comparingInt(TalentView::tier).thenComparing(TalentView::name),
                "talents");
        for (int i = 1; i < talents.size(); i++) {
            TalentView a = talents.get(i - 1);
            TalentView b = talents.get(i);
            Canon.require(a.tier() != b.tier() || !a.name().equals(b.name()),
                    "talents list " + b.name() + " of tier " + b.tier() + " twice");
        }
        talentPointsAvailable = Canon.positional(talentPointsAvailable, "talent points available");
        Canon.require(talentPointsAvailable.size() == TALENT_TIERS,
                "talent points available has one entry per tier, " + TALENT_TIERS + ": " + talentPointsAvailable.size());
        for (int points : talentPointsAvailable) {
            Canon.require(points >= 0, "talent points available are not negative: " + points);
        }
        quickslots = Canon.positional(quickslots, "quickslots");
        Canon.require(quickslots.size() == QUICKSLOTS, "the toolbar has " + QUICKSLOTS + " quickslots: " + quickslots.size());
    }
}
