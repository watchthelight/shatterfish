package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Playbook;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.knowledge.PlaybookValidator;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Playbook's {@code classes} section (story 7.6): an entry overrides the fields it names for one hero
 * class and nothing else, a class without an entry plays the base, a class the game does not have is
 * refused, and the Warrior's entry is empty so the Warrior's Runs are the Runs they were.
 */
class ClassPlaybookTest {

    private static final Path CODEX = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER)
            .resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag());

    private static Playbook committed() {
        return PlaybookFile.read(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH, CODEX);
    }

    private static Playbook.ClassEntry entry(Playbook playbook, HeroClass heroClass) {
        return playbook.classes().stream().filter(candidate -> candidate.heroClass() == heroClass).findFirst().orElse(null);
    }

    @Test
    @DisplayName("an entry overrides the fields it names over the base, and a class without an entry plays the base")
    void the_override_rule(@TempDir Path root) throws IOException {
        Path file = root.resolve("shatterfish.json");
        Files.writeString(file, "{\"classes\":{\"MAGE\":{\"kit\":{\"zap_reach\":4}},\"ROGUE\":{\"identify\":{\"rest_waits\":20}}},"
                + "\"format\":1,\"identify\":{\"reach\":6},\"name\":\"shatterfish\",\"version\":3}", StandardCharsets.UTF_8);
        Playbook playbook = PlaybookFile.read(file, Brains.SHATTERFISH, CODEX);
        // The Mage states one field; everything else is the base's, the base's own change (reach 6) included.
        assertEquals(4, playbook.kit(HeroClass.MAGE).zapReach());
        assertEquals(6, playbook.identify(HeroClass.MAGE).reach());
        assertEquals(playbook.upgrade(), playbook.upgrade(HeroClass.MAGE));
        assertEquals(50, playbook.identify(HeroClass.MAGE).restWaits());
        // The Rogue states a different one and gets no zap.
        assertEquals(20, playbook.identify(HeroClass.ROGUE).restWaits());
        assertEquals(0, playbook.kit(HeroClass.ROGUE).zapReach());
        // A class with no entry plays the base, whatever the other entries say.
        for (HeroClass heroClass : new HeroClass[] {HeroClass.WARRIOR, HeroClass.HUNTRESS, HeroClass.DUELIST, HeroClass.CLERIC}) {
            assertEquals(playbook.identify(), playbook.identify(heroClass), heroClass.name());
            assertEquals(playbook.kit(), playbook.kit(heroClass), heroClass.name());
            assertEquals(playbook.upgrade(), playbook.upgrade(heroClass), heroClass.name());
        }
    }

    @Test
    @DisplayName("a class the game does not have, a section a class entry cannot hold and a key a section lacks are each refused, by field")
    void refusals() {
        Set<String> classes = PlaybookFile.heroClasses(CODEX);
        assertEquals(Set.of("WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC"), classes);
        String head = "{\"classes\":%s,\"format\":1,\"name\":\"shatterfish\",\"version\":1}";
        List<String> unknown = PlaybookValidator.problems(head.formatted("{\"WIZARD\":{}}"), classes);
        assertEquals(1, unknown.size(), unknown.toString());
        assertTrue(unknown.get(0).contains("classes.WIZARD"), unknown.get(0));
        List<String> section = PlaybookValidator.problems(head.formatted("{\"MAGE\":{\"bosses\":{}}}"), classes);
        assertEquals(1, section.size(), section.toString());
        assertTrue(section.get(0).contains("classes.MAGE.bosses"), section.get(0));
        List<String> field = PlaybookValidator.problems(head.formatted("{\"MAGE\":{\"kit\":{\"zap_reach\":11}}}"), classes);
        assertEquals(1, field.size(), field.toString());
        assertTrue(field.get(0).contains("classes.MAGE.kit.zap_reach"), field.get(0));
        List<String> key = PlaybookValidator.problems(head.formatted("{\"MAGE\":{\"kit\":{\"zap\":3}}}"), classes);
        assertEquals(1, key.size(), key.toString());
        assertTrue(key.get(0).contains("classes.MAGE.kit.zap"), key.get(0));
        assertEquals(List.of(), PlaybookValidator.problems(head.formatted("{\"MAGE\":{\"kit\":{\"zap_reach\":10}}}"), classes));
    }

    @Test
    @DisplayName("the committed Playbook: the Warrior's entry is empty, and each other entry differs from the base in the fields it names")
    void the_committed_entries() {
        Playbook playbook = committed();
        Playbook.ClassEntry warrior = entry(playbook, HeroClass.WARRIOR);
        assertTrue(warrior != null, "the Warrior's entry is stated, so it is visibly empty");
        assertEquals(new Playbook.ClassEntry(HeroClass.WARRIOR, playbook.identify(), playbook.kit(), playbook.upgrade()), warrior,
                "empty: it changes nothing of the base");
        for (Playbook.ClassEntry entry : playbook.classes()) {
            if (entry.heroClass() == HeroClass.WARRIOR) {
                continue;
            }
            assertNotEquals(new Playbook.ClassEntry(entry.heroClass(), playbook.identify(), playbook.kit(), playbook.upgrade()), entry,
                    entry.heroClass() + ": an entry that says nothing is not stated");
        }
        // The Mage's entry is the staff and nothing else.
        Playbook.ClassEntry mage = entry(playbook, HeroClass.MAGE);
        assertTrue(mage.kit().zapReach() > 0, "the Mage zaps");
        assertEquals(playbook.identify(), mage.identify());
        assertEquals(playbook.upgrade(), mage.upgrade());
        assertEquals(0, playbook.kit().zapReach(), "the base never zaps");
    }

    @Test
    @DisplayName("the Warrior's smoke Runs under the committed Playbook are the Runs the empty Playbook plays, wait for wait")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void the_warrior_is_unchanged(@TempDir Path scratch) throws IOException {
        Set<HeroClass> warriors = EnumSet.of(HeroClass.WARRIOR);
        Playbook committed = PlaybookFile.read(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH, CODEX);
        Playbook empty = new Playbook(committed.name(), committed.version(), committed.identify(), committed.upgrade(),
                committed.kit(), List.of());
        Map<String, Path> without = PlaybookEquivalenceTest.play(
                PlaybookEquivalenceTest.root(scratch.resolve("without"), empty.canonical() + "\n"),
                scratch.resolve("without-out"), warriors);
        Map<String, Path> with = PlaybookEquivalenceTest.play(
                PlaybookEquivalenceTest.root(scratch.resolve("with"),
                        Files.readString(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), StandardCharsets.UTF_8)),
                scratch.resolve("with-out"), warriors);
        assertTrue(!without.isEmpty(), "the smoke set holds Warriors");
        assertEquals(without.keySet(), with.keySet());
        for (Map.Entry<String, Path> log : without.entrySet()) {
            assertEquals(HeroClass.WARRIOR, RunLogReader.of(log.getValue()).header().heroClass());
            assertEquals(PlaybookEquivalenceTest.waits(log.getValue()), PlaybookEquivalenceTest.waits(with.get(log.getKey())),
                    log.getKey() + ": the same waits");
        }
    }
}
