package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tunables;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.knowledge.PlaybookValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Playbook file and the Rig's flag name only registered tunables (story 10.35): the validator refuses
 * the rest with a message that names the field, a vector survives the file and the canonical text, and
 * the canonical text of a Playbook that moves nothing is the one it had before the registry.
 */
class TunablePlaybookTest {

    private static final Set<String> CLASSES = Set.of("WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC");

    private static String document(String tunables) {
        return "{\"format\":1,\"name\":\"shatterfish\",\"tunables\":" + tunables + ",\"version\":1}";
    }

    private static List<String> problems(String tunables) {
        return PlaybookValidator.problems(document(tunables), CLASSES);
    }

    private static void rejects(String tunables, String... named) {
        List<String> problems = problems(tunables);
        assertEquals(1, problems.size(), tunables + " -> " + problems);
        for (String part : named) {
            assertTrue(problems.get(0).contains(part), "the message names '" + part + "': " + problems.get(0));
        }
    }

    @Test
    @DisplayName("a registered Policy tunable inside its bounds is accepted, and a file with none is accepted")
    void registered_names_are_accepted() {
        assertEquals(List.of(), problems("{\"descend.rests\":30,\"fight.margin_per_mille\":400}"));
        assertEquals(List.of(), problems("{}"));
        assertEquals(List.of(), PlaybookValidator.problems("{\"format\":1,\"name\":\"shatterfish\",\"version\":1}", CLASSES));
    }

    @Test
    @DisplayName("a tunable the registry does not list is refused, and the message names it")
    void an_unregistered_tunable_is_refused() {
        rejects("{\"fight.aggression\":3}", "tunables.fight.aggression", "not a registered tunable");
    }

    @Test
    @DisplayName("a Playbook field named in tunables is refused: it has its own section")
    void a_playbook_field_is_stated_in_its_section() {
        rejects("{\"identify.reach\":8}", "tunables.identify.reach", "own section");
    }

    @Test
    @DisplayName("a number outside the registered bounds, or not whole, is refused")
    void bounds_and_whole_numbers_are_checked() {
        rejects("{\"fight.flights\":99}", "tunables.fight.flights", "from 0 to 6");
        rejects("{\"fight.flights\":-1}", "tunables.fight.flights", "from 0 to 6");
        rejects("{\"fight.flights\":1.5}", "tunables.fight.flights", "whole number");
    }

    @Test
    @DisplayName("a vector survives the file: read, stated in the canonical text, and absent when it states nothing")
    void the_vector_survives_the_file(@TempDir Path scratch) throws IOException {
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        Path codex = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag);
        String committed = Files.readString(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), StandardCharsets.UTF_8);
        Playbook plain = PlaybookFile.read(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH, codex);
        assertFalse(plain.canonical().contains("tunables"), "a Playbook that moves nothing has the text it had before the registry");
        assertTrue(plain.tunables().isReference());

        Path file = scratch.resolve("shatterfish.json");
        Files.writeString(file, committed.replace("\"name\":\"shatterfish\",\"upgrade\"", "\"name\":\"shatterfish\",\"tunables\":{\"descend.rests\":20,\"fight.margin_per_mille\":400},\"upgrade\""),
                StandardCharsets.UTF_8);
        Playbook moved = PlaybookFile.read(file, Brains.SHATTERFISH, codex);
        assertEquals(400, moved.tunables().getInt(Tunables.FIGHT_MARGIN));
        assertEquals(20, moved.tunables().getInt(Tunables.DESCEND_RESTS), "a stated default is the default");
        assertEquals(Map.of("fight.margin_per_mille", 400L), moved.tunables().values(), "and is dropped from the vector");
        assertTrue(moved.canonical().contains("\"tunables\":{\"fight.margin_per_mille\":400}"), moved.canonical());
        assertNotEquals(plain.canonical(), moved.canonical());
    }

    @Test
    @DisplayName("playing a vector over a Playbook moves a Playbook field in every class entry and a Policy constant in the vector, within bounds")
    void a_vector_is_played_over_a_playbook() {
        Playbook base = new Playbook("shatterfish", 1, Playbook.Identify.REFERENCE, Playbook.Upgrade.REFERENCE, Playbook.Kit.REFERENCE,
                List.of(new Playbook.ClassEntry(HeroClass.MAGE, Playbook.Identify.REFERENCE, Playbook.Upgrade.REFERENCE)),
                Playbook.Bosses.REFERENCE);
        Playbook moved = Tunables.with(base, Map.of("identify.reach", 12L, "bosses.goo.meet_range", 3L, "fight.flights", 1L));
        assertEquals(12, moved.identify().reach());
        assertEquals(12, moved.identify(HeroClass.MAGE).reach(), "a class entry is stored resolved, so it moves too");
        assertEquals(3, moved.bosses().goo().meetRange());
        assertEquals(1, moved.tunables().getInt(Tunables.FIGHT_FLIGHTS));
        assertEquals(12L, Tunables.value(moved, Tunables.IDENTIFY_REACH));
        assertEquals(base, Tunables.with(base, Map.of()), "an empty vector is the Playbook");
        assertEquals(base, Tunables.with(base, Map.of("identify.reach", 8L, "fight.flights", 2L)), "a vector of the defaults is the Playbook");
        assertThrows(IllegalArgumentException.class, () -> Tunables.with(base, Map.of("identify.reach", 41L)));
        assertThrows(IllegalArgumentException.class, () -> Tunables.with(base, Map.of("no.such", 1L)));
    }

    @Test
    @DisplayName("the Run's flag reads name=value pairs and refuses what is not one")
    void the_flag_is_checked() {
        assertEquals(Map.of("fight.flights", 1L, "identify.reach", 9L), RunOne.tunables("fight.flights=1, identify.reach=9"));
        assertEquals(Map.of(), RunOne.tunables(""));
        assertThrows(IllegalArgumentException.class, () -> RunOne.tunables("fight.flights"));
        assertThrows(IllegalArgumentException.class, () -> RunOne.tunables("fight.flights=two"));
        assertThrows(IllegalArgumentException.class, () -> RunOne.tunables("fight.flights=1,fight.flights=2"));
        assertTrue(RunOne.KNOWN.contains(RunOne.TUNABLES));
    }
}
