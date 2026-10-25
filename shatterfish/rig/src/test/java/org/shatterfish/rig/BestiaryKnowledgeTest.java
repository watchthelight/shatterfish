package org.shatterfish.rig;

import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bestiary as the rig hands it to a Brain (lever 0 of the bestiary's Brain levers,
 * docs/ideas/): {@code tactics/bestiary.json} read beside the Codex, joined with the mob table's
 * alignments and the spawn rotation's depths, and resolved by the name the screen shows plus the depth.
 */
class BestiaryKnowledgeTest {

    private static final String TAG = HeadlessBoot.pinnedTag();
    private static final Path FOLDER = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(TAG);

    private static Codex.Knowledge read() {
        return CodexKnowledge.read(FOLDER, TAG);
    }

    private static Codex.Tactics entry(Codex.Knowledge knowledge, String className) {
        return knowledge.bestiary().stream().filter(t -> t.className().equals(className)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("every entry of the file arrives, with its tags, the alignment its class is created with and its rotation depths")
    void reads_every_entry() {
        Codex.Knowledge knowledge = read();
        assertEquals(103, knowledge.bestiary().size(), "one entry per class of tactics/bestiary.json");
        Codex.Tactics crab = entry(knowledge, "actors.mobs.Crab");
        assertEquals("sewer crab", crab.name());
        assertEquals("fast", crab.speed(), "Crab.java: baseSpeed 2");
        assertEquals(false, crab.outrunnable());
        assertEquals(Alignment.ENEMY, crab.alignment());
        assertEquals(List.of(3, 4), crab.depths(), "the rotation places crabs on 3 to 5, and 5 is Goo's floor");
        assertEquals(List.of(3, 4), entry(knowledge, "actors.mobs.HermitCrab").depths(), "an alternate is placed where its base is");
        assertEquals(List.of(4, 6, 7, 8, 9), entry(knowledge, "actors.mobs.Thief").depths(), "the rotation's 6 to 9 and the rare 4");
        assertEquals(List.of(11, 12, 13, 14), entry(knowledge, "actors.mobs.Shaman.PurpleShaman").depths(), "a family's member");
        assertEquals(List.of(), entry(knowledge, "actors.mobs.DwarfKing.DKGolem").depths(), "a summon has no rotation depth");
        assertEquals(Alignment.NEUTRAL, entry(knowledge, "actors.mobs.Mimic").alignment(), "a mimic is created neutral");
        assertEquals(Alignment.ENEMY, entry(knowledge, "actors.mobs.Elemental.AllyNewBornElemental").alignment(),
                "the mob table creates the summoned elemental an enemy; the spell makes it an ally at run time");
        Codex.Tactics statue = entry(knowledge, "actors.mobs.Statue");
        assertEquals("passive", statue.ai());
        assertEquals(List.of("door", "out-of-sight", "stairs"), statue.breakContact());
        assertTrue(knowledge.bestiary().stream().allMatch(t -> t.depths().stream().noneMatch(d -> d % 5 == 0 && d <= 25)),
                "no entry is placed on a boss floor");
    }

    @Test
    @DisplayName("a name shared by several classes resolves by the depth: placed there first, then placed nowhere, then by class")
    void shared_names_resolve_by_depth() {
        Codex.Knowledge knowledge = read();
        Map<String, Set<String>> byName = new TreeMap<>();
        for (Codex.Tactics tactics : knowledge.bestiary()) {
            byName.computeIfAbsent(tactics.name(), k -> new TreeSet<>()).add(tactics.className());
        }
        assertEquals(14, byName.values().stream().filter(classes -> classes.size() > 1).count(),
                "fourteen display names belong to more than one class (docs/bestiary/index.md)");
        assertEquals("actors.mobs.Golem", knowledge.tactics("golem", 18).className());
        assertEquals("actors.mobs.DwarfKing.DKGolem", knowledge.tactics("golem", 20).className(), "the King's floor");
        assertEquals("actors.mobs.Eye", knowledge.tactics("evil eye", 22).className());
        assertEquals("actors.mobs.YogDzewa.YogEye", knowledge.tactics("evil eye", 25).className(), "Yog-Dzewa's floor");
        assertEquals("actors.mobs.YogDzewa.YogScorpio", knowledge.tactics("scorpio", 25).className());
        assertEquals("actors.mobs.DwarfKing.DKGhoul", knowledge.tactics("dwarven ghoul", 20).className());
        assertEquals("actors.mobs.Ghoul", knowledge.tactics("dwarven ghoul", 17).className());
        assertEquals("actors.mobs.Rat", knowledge.tactics("marsupial rat", 2).className());
        assertEquals("actors.mobs.quest.vault.VaultRat", knowledge.tactics("marsupial rat", 7).className(),
                "off the rat's depths, the copy placed nowhere");
        assertEquals("actors.mobs.Wraith", knowledge.tactics("wraith", 3).className());
        Codex.Tactics newborn = knowledge.tactics("newborn fire elemental", 8);
        assertEquals("actors.mobs.Elemental.AllyNewBornElemental", newborn.className(), "first by class name");
        Codex.Tactics ritual = entry(knowledge, "actors.mobs.Elemental.NewbornFireElemental");
        assertEquals(List.of(ritual.speed(), ritual.flying(), ritual.ai(), ritual.outrunnable()),
                List.of(newborn.speed(), newborn.flying(), newborn.ai(), newborn.outrunnable()),
                "the ritual's elemental reads the same to the Brain: their attacks differ (melee, ranged), but both fly,"
                        + " so both are at range");
        assertEquals("actors.mobs.Shaman.BlueShaman", knowledge.tactics("gnoll shaman", 12).className(), "first by class");
        assertEquals(null, knowledge.tactics("no such enemy", 3));
    }

    @Test
    @DisplayName("where the rule cannot tell classes apart, the tags the Brain reads agree")
    void ties_agree_on_what_the_brain_reads() {
        Codex.Knowledge knowledge = read();
        for (int depth = 1; depth <= 26; depth++) {
            Map<String, List<Codex.Tactics>> tied = new TreeMap<>();
            for (Codex.Tactics tactics : knowledge.bestiary()) {
                if (tactics.alignment() == Alignment.ENEMY && tactics.spawnsOn(depth)) {
                    tied.computeIfAbsent(tactics.name(), k -> new java.util.ArrayList<>()).add(tactics);
                }
            }
            for (Map.Entry<String, List<Codex.Tactics>> name : tied.entrySet()) {
                Set<String> read = name.getValue().stream()
                        .map(t -> t.speed() + "/" + t.attack() + "/" + t.flying() + "/" + t.ai() + "/" + t.outrunnable())
                        .collect(Collectors.toSet());
                assertEquals(1, read.size(), name.getKey() + " on depth " + depth + " reads differently by class: " + read);
            }
        }
    }

    @Test
    @DisplayName("every class of a shared name answers the Brain's questions alike, so the resolution rule decides nothing it reads")
    void shared_names_read_alike() {
        Codex.Knowledge knowledge = read();
        // Each class alone in a Knowledge, so the lookup cannot choose, and asked through the Brain's
        // own predicates (Brain.bestiaryAnswers) by its name, on every depth and branch.
        List<String> differ = new java.util.ArrayList<>();
        for (int depth = 1; depth <= 26; depth++) {
            for (int branch = 0; branch <= 1; branch++) {
                Map<String, Set<String>> answers = new TreeMap<>();
                for (Codex.Tactics t : knowledge.bestiary()) {
                    answers.computeIfAbsent(t.name(), k -> new TreeSet<>()).add(org.shatterfish.brain.Brain
                            .bestiaryAnswers(knowledge.withBestiary(List.of(t)), depth, branch, t.name()));
                }
                for (Map.Entry<String, Set<String>> e : answers.entrySet()) {
                    if (e.getValue().size() > 1) {
                        differ.add(e.getKey() + " on " + depth + ":" + branch + " " + e.getValue());
                    }
                }
            }
        }
        // No known exception at v4.0.0. A tag that splits a shared name (the vault golem is outrunnable
        // and the King's is not, which no Brain question reads yet) has to be listed here, with the
        // depth and branch that tell the classes apart, before a Brain question reads it.
        assertEquals(List.of(), differ, "shared names whose classes answer differently");
    }

    @Test
    @DisplayName("the tags carry the Brain's former hand lists: the passive trio, every enemy that shot or flew, the Codex's immovable enemies")
    void the_tags_carry_the_hand_lists() {
        Codex.Knowledge knowledge = read();
        assertEquals(Set.of("animated statue", "armored statue", "gnoll exile"),
                org.shatterfish.brain.Brain.passiveEnemies(knowledge), "Fight.PASSIVE before the bestiary");
        Set<String> atRange = new TreeSet<>();
        Set<String> immobile = new TreeSet<>();
        for (Codex.Tactics tactics : knowledge.bestiary()) {
            if (tactics.alignment() != Alignment.ENEMY) {
                continue;
            }
            if (tactics.attack().equals("ranged") || tactics.attack().equals("bolt") || tactics.flying()) {
                atRange.add(tactics.name());
            }
            if (tactics.speed().equals("immobile")) {
                immobile.add(tactics.name());
            }
        }
        assertTrue(atRange.containsAll(Set.of("gnoll shaman", "DM-100", "dwarf warlock", "evil eye", "scorpio",
                "acidic scorpio", "vampire bat")), "Heal.AT_RANGE before the bestiary: " + atRange);
        assertEquals(new TreeSet<>(knowledge.immovable()), immobile, "the Codex's IMMOVABLE enemies are the immobile ones");
    }

    @Test
    @DisplayName("the api's closed vocabulary is the one BestiaryTest holds to docs/bestiary/index.md")
    void the_api_speaks_the_documented_vocabulary() {
        assertEquals(BestiaryTest.VOCABULARY.get("speed"), Set.copyOf(Codex.Tactics.SPEEDS));
        assertEquals(BestiaryTest.VOCABULARY.get("attack"), Set.copyOf(Codex.Tactics.ATTACKS));
        assertEquals(BestiaryTest.VOCABULARY.get("ai"), Set.copyOf(Codex.Tactics.AIS));
        assertEquals(BestiaryTest.VOCABULARY.get("approach"), Set.copyOf(Codex.Tactics.APPROACHES));
        assertEquals(BestiaryTest.VOCABULARY.get("breakContact"), Set.copyOf(Codex.Tactics.BREAK_CONTACT));
    }

    @Test
    @DisplayName("the knowledge digest covers the Codex tables and the bestiary: a changed byte of either changes it")
    void the_digest_covers_what_is_read(@org.junit.jupiter.api.io.TempDir Path copy) throws java.io.IOException {
        String digest = CodexKnowledge.digest(FOLDER);
        assertEquals(digest, CodexKnowledge.digest(FOLDER), "the same files, the same digest");
        Path codex = java.nio.file.Files.createDirectories(copy.resolve("codex").resolve(TAG));
        for (java.io.File table : FOLDER.toFile().listFiles()) {
            java.nio.file.Files.copy(table.toPath(), codex.resolve(table.getName()));
        }
        Path tactics = java.nio.file.Files.createDirectories(copy.resolve("tactics"));
        java.nio.file.Files.copy(SeedSetsTest.ROOT.resolve("tactics/bestiary.json"), tactics.resolve("bestiary.json"));
        assertEquals(digest, CodexKnowledge.digest(codex), "a copy digests alike");
        String bestiary = java.nio.file.Files.readString(tactics.resolve("bestiary.json"));
        java.nio.file.Files.writeString(tactics.resolve("bestiary.json"),
                bestiary.replaceFirst("\"outrunnable\":false", "\"outrunnable\":true"));
        assertTrue(!digest.equals(CodexKnowledge.digest(codex)), "an edited bestiary is other knowledge");
        java.nio.file.Files.writeString(tactics.resolve("bestiary.json"), bestiary);
        String mobs = java.nio.file.Files.readString(codex.resolve("mobs.json"));
        String edited = mobs.replaceFirst("\"ht\":12", "\"ht\":13");
        assertTrue(!edited.equals(mobs), "the edit applies");
        java.nio.file.Files.writeString(codex.resolve("mobs.json"), edited);
        assertTrue(!digest.equals(CodexKnowledge.digest(codex)), "an edited table is other knowledge");
    }

    @Test
    @DisplayName("a bestiary read at another tag is refused, and the file lies two folders above the Codex")
    void refused_at_another_tag() {
        assertEquals(SeedSetsTest.ROOT.resolve("tactics/bestiary.json").toAbsolutePath().normalize(),
                CodexKnowledge.bestiaryFile(FOLDER));
        assertThrows(IllegalArgumentException.class, () -> CodexKnowledge.bestiary(FOLDER, "v0.0.1"));
    }
}
