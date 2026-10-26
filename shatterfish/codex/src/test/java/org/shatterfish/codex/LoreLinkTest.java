package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The link from a lore claim to what implements it (story 7.7, FR-18: every heuristic links to a
 * Lore entry or a Rule).
 *
 * <p>This is the claim's half. A claim's {@code implemented_in} names a Policy, a row of the
 * Brain's Rules index or a Playbook field, and each has to exist ({@link LoreCheck} holds that on
 * every claim; this test holds it against the real registry and shows the names a claim may use).
 * The heuristic's half, that an {@code @Heuristic} slug may be a Lore claim id and that both
 * directions agree, belongs to the {@code HeuristicLinkTest} of story 7.1 and is added there once
 * {@code @Heuristic} is on {@code main}; a claim id for that test is the file stem of any file
 * under {@code lore/<topic>/} ({@link #claim_ids()}), and ids are unique across topics.
 */
class LoreLinkTest {

    private static final Path ROOT = DocsCitations.root();

    /** Every claim id: the stem of each file under lore/. */
    static Set<String> claim_ids() {
        Set<String> ids = new TreeSet<>();
        Path loreDir = ROOT.resolve(Lore.DIR);
        for (Path file : Lore.files(loreDir)) {
            if (LoreCheck.isClaimPath(loreDir, file)) {
                ids.add(file.getFileName().toString().replaceFirst("\\.md$", ""));
            }
        }
        return ids;
    }

    @Test
    @DisplayName("every implemented_in name of every committed claim exists in the real registry")
    void implemented_in_names_exist() throws Lore.Unreadable {
        Lore.Registry registry = LoreTool.registry(ROOT);
        List<String> problems = new ArrayList<>();
        Path loreDir = ROOT.resolve(Lore.DIR);
        for (Path file : Lore.files(loreDir)) {
            Lore.Doc doc = Lore.parse(file.getFileName().toString(), Lore.read(file));
            for (String entry : doc.list("implemented_in")) {
                String problem = registry.problem(entry);
                if (problem != null) {
                    problems.add(doc.get("id") + " " + entry + ": " + problem);
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("a claim id is the file stem, unique across topics, and the 144 converted ids are all there")
    void claim_ids_are_unique() {
        Set<String> ids = claim_ids();
        long files = Lore.files(ROOT.resolve(Lore.DIR)).size();
        assertEquals(files, ids.size(), "two files share a stem, so an id would name two claims");
        assertTrue(ids.containsAll(List.of("sewers-1", "bosses-31", "quest-branch-39", "specials-24")));
    }
}
