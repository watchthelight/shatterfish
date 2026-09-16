package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Codex's records refuse what a Codex cannot carry, its tables refuse a key twice, and their
 * JSON is the golden text (story 2.1): a change here is a change every consumer sees, and the
 * Codex version says so.
 */
class CodexJsonTest {

    private static final Codex.Citation AT = new Codex.Citation("core/src/main/java/X.java", 7);

    @Test
    @DisplayName("the manifest renders its version, its tag and its tables, keys and tables sorted")
    void the_manifest_renders() {
        Codex.Manifest manifest = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("hero-classes.json", "challenges.json"));
        assertEquals("{\"codexVersion\":1,\"tables\":[\"challenges.json\",\"hero-classes.json\"],\"upstreamTag\":\"v4.0.0\"}\n",
                CodexJson.manifest(manifest));
        assertEquals("v4.1.0-beta2", new Codex.Manifest(1, "v4.1.0-beta2", List.of()).upstreamTag(), "a pre-release tag is a tag");
    }

    @Test
    @DisplayName("a table renders one entry per line with its citation, and ends with one line feed")
    void the_entries_render() {
        Codex.HeroClassEntry warrior = new Codex.HeroClassEntry(HeroClass.WARRIOR, List.of(HeroSubclass.BERSERKER, HeroSubclass.GLADIATOR), AT);
        Codex.HeroClassEntry mage = new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.BATTLEMAGE, HeroSubclass.WARLOCK), AT);
        String heroes = CodexJson.heroClasses(List.of(warrior, mage));
        assertEquals("[\n"
                + "  {\"citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},\"heroClass\":\"WARRIOR\",\"subclasses\":[\"BERSERKER\",\"GLADIATOR\"]},\n"
                + "  {\"citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},\"heroClass\":\"MAGE\",\"subclasses\":[\"BATTLEMAGE\",\"WARLOCK\"]}\n"
                + "]\n", heroes);
        Codex.ChallengeEntry darkness = new Codex.ChallengeEntry(Challenge.DARKNESS, 32, AT);
        String challenges = CodexJson.challenges(List.of(darkness));
        assertEquals("[\n  {\"challenge\":\"DARKNESS\",\"citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},\"mask\":32}\n]\n", challenges);
        assertEquals("[\n]\n", CodexJson.challenges(List.of()));
        for (String text : new String[] {heroes, challenges, CodexJson.manifest(new Codex.Manifest(1, "v4.0.0", List.of()))}) {
            assertFalse(text.contains("\r"), "line feeds only");
            assertEquals('\n', text.charAt(text.length() - 1));
        }
        assertEquals("core/src/main/java/X.java:7", AT.reference());
    }

    @Test
    @DisplayName("the records refuse a bad path, a zero line, a tag that is not one, a two-bit mask, NONE or a repeated subclass, and a table refuses a key twice")
    void the_records_refuse() {
        for (String bad : new String[] {"/abs/X.java", "core\\X.java", "C:/X.java", "core/../X.java", "./X.java", "core//X.java", "core/X.java/", ""}) {
            assertThrows(IllegalArgumentException.class, () -> new Codex.Citation(bad, 1), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new Codex.Citation("core/X.java", 0));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(0, "v4.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(1, "4.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(1, "v4.0.0", List.of("a.json", "a.json")));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ChallengeEntry(Challenge.NO_FOOD, 3, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ChallengeEntry(Challenge.NO_FOOD, 0, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.NONE), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.WARLOCK, HeroSubclass.WARLOCK), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.WARLOCK), null));
        Codex.HeroClassEntry mage = new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.BATTLEMAGE), AT);
        Codex.HeroClassEntry mageAgain = new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.WARLOCK), AT);
        Codex.HeroClassEntry rogue = new Codex.HeroClassEntry(HeroClass.ROGUE, List.of(HeroSubclass.BATTLEMAGE), AT);
        assertThrows(IllegalArgumentException.class, () -> CodexJson.heroClasses(List.of(mage, mageAgain)), "a class twice");
        assertThrows(IllegalArgumentException.class, () -> CodexJson.heroClasses(List.of(mage, rogue)), "a subclass under two classes");
        Codex.ChallengeEntry food = new Codex.ChallengeEntry(Challenge.NO_FOOD, 1, AT);
        Codex.ChallengeEntry foodAgain = new Codex.ChallengeEntry(Challenge.NO_FOOD, 2, AT);
        Codex.ChallengeEntry armorSameMask = new Codex.ChallengeEntry(Challenge.NO_ARMOR, 1, AT);
        assertThrows(IllegalArgumentException.class, () -> CodexJson.challenges(List.of(food, foodAgain)), "a challenge twice");
        assertThrows(IllegalArgumentException.class, () -> CodexJson.challenges(List.of(food, armorSameMask)), "a mask twice");
        assertThrows(NullPointerException.class, () -> CodexJson.challenges(null));
    }
}
