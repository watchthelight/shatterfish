package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.Ratmogrify;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.TalentView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The most points a talent holds is what {@link TalentView#maxPoints()} says for its tier, for every
 * talent the game can put in a tier (issue #162).
 *
 * <p>The valid set withholds a talent at its most rather than offering it and letting the executor
 * refuse it, which ends a Run. It works that out from the tier the Observation shows, because the rule
 * is general game knowledge, two points in tiers 1 and 2 and the tier itself in tiers 3 and 4
 * ({@code core/.../actors/hero/Talent.java:98-110}, {@code :438-445}). This walks the game's own tier
 * lists, every class's four, every subclass's third and every armour ability's fourth, Ratmogrify's
 * included, the way a hero's talents are built ({@code Talent.initClassTalents}, {@code initSubclassTalents},
 * {@code initArmorTalents}), and holds each talent's own {@code maxPoints} to the rule; a talent an
 * upgrade adds with a different most fails here by name.
 */
class TalentTiersTest {

    @Test
    void every_talent_holds_what_its_tier_says() {
        Map<Talent, Integer> tierOf = new EnumMap<>(Talent.class);
        List<String> wrong = new ArrayList<>();
        for (HeroClass cls : HeroClass.values()) {
            record(tiers(lists -> Talent.initClassTalents(cls, lists)), tierOf, wrong, cls.name());
            for (HeroSubClass sub : cls.subClasses()) {
                record(tiers(lists -> Talent.initSubclassTalents(sub, lists)), tierOf, wrong, sub.name());
            }
            for (ArmorAbility ability : cls.armorAbilities()) {
                record(tiers(lists -> Talent.initArmorTalents(ability, lists)), tierOf, wrong,
                        ability.getClass().getSimpleName());
            }
        }
        // Ratmogrify is no class's armour ability; its talents are tier 4 all the same
        // (core/.../actors/hero/abilities/Ratmogrify.java:187-189, core/.../actors/hero/Talent.java:203).
        record(tiers(lists -> Talent.initArmorTalents(new Ratmogrify(), lists)), tierOf, wrong, "Ratmogrify");
        assertEquals(List.of(), wrong, "talents whose most is not what their tier says");
        assertTrue(tierOf.size() > 100, "the walk found too few talents to mean anything: " + tierOf.size());

        // Every talent the game has is in some tier, so none escapes the check.
        TreeSet<String> orphans = new TreeSet<>();
        for (Talent talent : Talent.values()) {
            if (!tierOf.containsKey(talent)) {
                orphans.add(talent.name());
            }
        }
        assertEquals(new TreeSet<>(), orphans, "talents in no tier list this walk reaches");
    }

    private interface Init {
        void into(ArrayList<LinkedHashMap<Talent, Integer>> lists);
    }

    private static ArrayList<LinkedHashMap<Talent, Integer>> tiers(Init init) {
        ArrayList<LinkedHashMap<Talent, Integer>> lists = new ArrayList<>();
        init.into(lists);
        return lists;
    }

    private static void record(List<LinkedHashMap<Talent, Integer>> lists, Map<Talent, Integer> tierOf,
                               List<String> wrong, String source) {
        for (int i = 0; i < lists.size(); i++) {
            int tier = i + 1;
            for (Talent talent : lists.get(i).keySet()) {
                Integer before = tierOf.put(talent, tier);
                if (before != null && before != tier) {
                    wrong.add(talent.name() + " is in tier " + before + " and tier " + tier + " (" + source + ")");
                }
                int rule = new TalentView(tier, talent.name(), 0).maxPoints();
                if (talent.maxPoints() != rule) {
                    wrong.add(talent.name() + " of tier " + tier + " (" + source + ") holds " + talent.maxPoints()
                            + ", the rule says " + rule);
                }
            }
        }
    }
}
