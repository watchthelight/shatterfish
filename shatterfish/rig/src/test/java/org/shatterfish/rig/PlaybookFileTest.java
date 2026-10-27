package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Playbook;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed Playbooks and their reader (story 7.2): read by the caller, refused unless valid and
 * the one for the Brain, and part of the Brain's configuration hash, which the Run log's header carries.
 */
class PlaybookFileTest {

    private static final Path CODEX = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER)
            .resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag());

    /** The 4.x Brain's Playbook as story 7.4 left it: the reference with the armour taking the scroll on a tie. */
    private static final Playbook STORY_7_4 = new Playbook("shatterfish", 3, Playbook.Identify.REFERENCE,
            Playbook.Upgrade.REFERENCE.with(java.util.Map.of("tie", "ARMOR")), Playbook.Kit.REFERENCE, java.util.List.of());

    private static Playbook committed(String brain) {
        return PlaybookFile.read(PlaybookFile.of(SeedSetsTest.ROOT, brain), brain, CODEX);
    }

    @Test
    @DisplayName("each Brain that plays by a Playbook has one committed, written canonically, holding the values the Java held")
    void the_committed_files() throws IOException {
        for (String brain : new String[] {Brains.SHATTERFISH, Brains.ONE_PLY}) {
            assertTrue(Brains.readsPlaybook(brain));
            Playbook playbook = committed(brain);
            assertEquals(brain, playbook.name());
            // The oneply Playbook is still the first one; the 4.x Brain's base has moved only by the allocation story 7.4
            // measured (class entries are ClassPlaybookTest's).
            assertEquals(brain.equals(Brains.SHATTERFISH) ? STORY_7_4 : Playbook.reference(brain),
                    new Playbook(playbook.name(), playbook.version(), playbook.identify(), playbook.upgrade(), playbook.kit(), java.util.List.of()),
                    brain + ": the base holds the values the constants held, and shatterfish's the ones stories since have kept");
            assertEquals(playbook.canonical(), Files.readString(PlaybookFile.of(SeedSetsTest.ROOT, brain), StandardCharsets.UTF_8).strip(),
                    "the committed file is its own canonical text, so a reader sees every value");
        }
        assertTrue(!Brains.readsPlaybook(Brains.RANDOM));
    }

    @Test
    @DisplayName("a Playbook is part of the Brain's configuration, and the sections a file leaves out are not")
    void configured_by_the_playbook(@TempDir Path root) throws IOException {
        String hash = Brains.configHash(SeedSetsTest.ROOT, Brains.SHATTERFISH);
        String knowledge = CodexKnowledge.digest(CODEX);
        org.shatterfish.api.Weights weights = WeightsFile.read(WeightsFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH);
        Playbook playbook = committed(Brains.SHATTERFISH);
        assertEquals(hash, Brains.configHash(Brains.SHATTERFISH, weights, playbook, knowledge));
        Playbook edited = new Playbook(Brains.SHATTERFISH, 1, playbook.identify().with(java.util.Map.of("reach", 9L)),
                playbook.upgrade(), java.util.List.of());
        assertNotEquals(hash, Brains.configHash(Brains.SHATTERFISH, weights, edited, knowledge), "a changed value is a changed Brain");
        assertNotEquals(hash, Brains.configHash(Brains.SHATTERFISH, weights, new Playbook(Brains.SHATTERFISH, playbook.version() + 1,
                playbook.identify(), playbook.upgrade(), java.util.List.of()), knowledge), "and so is a changed version");
        // The same values with the sections left out are the same Playbook and the same hash.
        Path loose = root.resolve("shatterfish.json");
        Files.writeString(loose, "{\"format\":1,\"name\":\"shatterfish\",\"version\":1}\n", StandardCharsets.UTF_8);
        assertEquals(Playbook.reference(Brains.SHATTERFISH), PlaybookFile.read(loose, Brains.SHATTERFISH, CODEX),
                "a section left out is its reference values");
    }

    @Test
    @DisplayName("a class entry states only what it changes, and the Playbook it makes plays that class differently")
    void class_entries(@TempDir Path root) throws IOException {
        Path file = root.resolve("shatterfish.json");
        Files.writeString(file, "{\"classes\":{\"MAGE\":{\"identify\":{\"healing_odds_per_mille\":500},\"upgrade\":{\"tie\":\"ARMOR\"}}},"
                + "\"format\":1,\"identify\":{\"reach\":6},\"name\":\"shatterfish\",\"version\":3}", StandardCharsets.UTF_8);
        Playbook playbook = PlaybookFile.read(file, Brains.SHATTERFISH, CODEX);
        assertEquals(6, playbook.identify().reach());
        assertEquals(6, playbook.identify(HeroClass.MAGE).reach(), "the class inherits the base's change");
        assertEquals(500, playbook.identify(HeroClass.MAGE).healingOddsPerMille());
        assertEquals(200, playbook.identify(HeroClass.WARRIOR).healingOddsPerMille());
        assertEquals(org.shatterfish.api.EquipSlot.ARMOR, playbook.upgrade(HeroClass.MAGE).tie());
        assertEquals(org.shatterfish.api.EquipSlot.WEAPON, playbook.upgrade(HeroClass.ROGUE).tie());
    }

    @Test
    @DisplayName("a file that is invalid, or is another Brain's, is refused and the message says which field")
    void refusals(@TempDir Path root) throws IOException {
        Path file = root.resolve("shatterfish.json");
        Files.writeString(file, "{\"format\":1,\"identify\":{\"reach\":99},\"name\":\"shatterfish\",\"version\":1}", StandardCharsets.UTF_8);
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> PlaybookFile.read(file, Brains.SHATTERFISH, CODEX));
        assertTrue(invalid.getMessage().contains("identify.reach"), invalid.getMessage());
        Files.writeString(file, "{\"format\":1,\"name\":\"oneply\",\"version\":1}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> PlaybookFile.read(file, Brains.SHATTERFISH, CODEX));
        Files.writeString(file, "﻿{\"format\":1,\"name\":\"shatterfish\",\"version\":1}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> PlaybookFile.read(file, Brains.SHATTERFISH, CODEX));
        // And the Rig refuses it before a Registration is accepted or a Run starts.
        Path playbooks = root.resolve(PlaybookFile.FOLDER);
        Files.createDirectories(playbooks);
        Files.writeString(playbooks.resolve("shatterfish.json"), "{\"format\":1,\"name\":\"shatterfish\",\"seed\":1,\"version\":1}", StandardCharsets.UTF_8);
        Path link = root.resolve(CodexManifest.FOLDER).resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag());
        Files.createDirectories(link);
        Files.copy(CODEX.resolve("hero-classes.json"), link.resolve("hero-classes.json"));
        Files.createDirectories(root.resolve(WeightsFile.FOLDER));
        Files.copy(WeightsFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), WeightsFile.of(root, Brains.SHATTERFISH));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.weighed(root, java.util.List.of(Brains.SHATTERFISH)));
        assertTrue(refused.getMessage().contains("seed"), refused.getMessage());
    }
}
