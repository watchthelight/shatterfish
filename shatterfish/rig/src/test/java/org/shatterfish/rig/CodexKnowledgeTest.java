package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Codex;
import org.shatterfish.api.ItemKind;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Codex's general knowledge, as the rig reads it for a Brain (story 4.2). */
class CodexKnowledgeTest {

    private static Codex.Knowledge read() {
        String tag = HeadlessBoot.pinnedTag();
        return CodexKnowledge.read(SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), tag);
    }

    @Test
    @DisplayName("the potions, scrolls and rings, each with twelve appearances and its identities by deck weight")
    void the_families() {
        Codex.Knowledge knowledge = read();
        assertEquals(List.of(ItemKind.RING, ItemKind.POTION, ItemKind.SCROLL).stream().sorted().toList(),
                knowledge.families().stream().map(Codex.Identities::kind).toList());
        Codex.Identities potions = knowledge.families().stream().filter(family -> family.kind() == ItemKind.POTION)
                .findFirst().orElseThrow();
        assertEquals(12, potions.labels().size());
        assertTrue(potions.labels().contains("crimson potion"), potions.labels().toString());
        assertTrue(potions.candidates().contains(new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6)),
                potions.candidates().toString());
        assertTrue(potions.candidates().contains(new Codex.Candidate("items.potions.PotionOfStrength", "potion of strength", 0)),
                "the decks never draw strength; a guarantee places it");
    }

    @Test
    @DisplayName("the pool room places a potion of invisibility, and strength and upgrade are owed per five floors")
    void rooms_and_guarantees() {
        Codex.Knowledge knowledge = read();
        assertTrue(knowledge.rooms().contains(new Codex.RoomSpawn("levels.rooms.special.PoolRoom",
                "items.potions.PotionOfInvisibility", "potion of invisibility")), knowledge.rooms().toString());
        assertTrue(knowledge.guarantees().contains(new Codex.Guarantee("STRENGTH_POTIONS",
                "items.potions.PotionOfStrength", "potion of strength", 2, 5)), knowledge.guarantees().toString());
        assertTrue(knowledge.guarantees().contains(new Codex.Guarantee("UPGRADE_SCROLLS",
                "items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 3, 5)), knowledge.guarantees().toString());
        assertTrue(knowledge.guarantees().stream().noneMatch(guarantee -> guarantee.counter().equals("ENCH_STONE")),
                "a drop placed once in a Run is not owed per set");
    }

    @Test
    @DisplayName("the combat tables: enemies by the name the screen shows, weapons and armour by theirs")
    void combat() {
        Codex.Knowledge knowledge = read();
        assertEquals(new Codex.Threat("marsupial rat", 8, 8, 2, 1, 4, 0, 1), knowledge.threat("marsupial rat"),
                "the rat's figures (Rat.java: HT 8, attackSkill 8, defenseSkill 2, damage 1-4, armour 0-1)");
        assertTrue(knowledge.threats().size() > 40, "most enemies have fixed figures: " + knowledge.threats().size());
        assertEquals(null, knowledge.threat("golden bee"), "a bee's figures depend on the Run and are not listed");
        assertTrue(knowledge.weapons().contains(new Codex.Gear("worn shortsword", 0, 5485)), "the Warrior's sword, measured");
        assertTrue(knowledge.armours().contains(new Codex.Gear("cloth armor", 0, 1004)), "cloth armour, measured");
    }

    @Test
    @DisplayName("a Codex for another tag is refused before a table is read")
    void refused() {
        String tag = HeadlessBoot.pinnedTag();
        assertThrows(IllegalArgumentException.class,
                () -> CodexKnowledge.read(SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), "v0.0.1"));
    }
}
