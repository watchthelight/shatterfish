package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Brain reads of the bestiary's tags ({@link Bestiary}), and that every value it compares is one the vocabulary has. */
class BestiaryTagsTest {

    @Test
    @DisplayName("every tag value the Brain compares against is in the api's closed vocabulary")
    void reads_only_the_vocabulary() {
        Map<String, List<String>> vocabulary = Map.of("speed", Codex.Tactics.SPEEDS, "ai", Codex.Tactics.AIS,
                "attack", Codex.Tactics.ATTACKS, "breakContact", Codex.Tactics.BREAK_CONTACT);
        assertEquals(vocabulary.keySet(), Bestiary.READS.keySet(), "the tags the Brain compares");
        for (Map.Entry<String, List<String>> tag : Bestiary.READS.entrySet()) {
            assertTrue(vocabulary.get(tag.getKey()).containsAll(tag.getValue()), tag.getKey() + ": " + tag.getValue());
        }
    }

    @Test
    @DisplayName("passive means created passive as an enemy and walking: not a mimic, not a rot heart")
    void passive_rule() {
        Codex.Tactics mimic = new Codex.Tactics("actors.mobs.Mimic", "mimic", Alignment.NEUTRAL, List.of(), "normal", "melee",
                1, false, false, List.of(), List.of(), "passive", false, false, true, "keep-away", true, false, List.of("door"));
        Codex.Tactics heart = Screens.tactics("actors.mobs.RotHeart", "rot heart", "immobile", "none", false, "passive", true);
        Codex.Knowledge knowledge = FightPolicyTest.KNOWLEDGE.withBestiary(
                java.util.stream.Stream.concat(Screens.BESTIARY.stream(), java.util.stream.Stream.of(mimic, heart)).toList());
        assertTrue(Bestiary.passive(knowledge, 7, 0, "animated statue"));
        assertTrue(Bestiary.passive(knowledge, 3, 0, "gnoll exile"));
        assertFalse(Bestiary.passive(knowledge, 3, 0, "mimic"), "created neutral: drawn as an enemy only once it hunts");
        assertFalse(Bestiary.passive(knowledge, 8, 0, "rot heart"), "immobile: fought where it stands");
        assertFalse(Bestiary.passive(knowledge, 3, 0, "marsupial rat"), "no entry: not passive");
        assertTrue(Bestiary.immobile(knowledge, 8, 0, "rot heart"));
        assertTrue(Bestiary.atRange(knowledge, 22, 0, "evil eye"));
        assertTrue(Bestiary.atRange(knowledge, 12, 0, "vampire bat"), "it flies");
        assertFalse(Bestiary.atRange(knowledge, 7, 0, "animated statue"));
        assertTrue(Bestiary.passive(knowledge, 17, 1, "animated statue"), "on a branch floor too");
    }
}
