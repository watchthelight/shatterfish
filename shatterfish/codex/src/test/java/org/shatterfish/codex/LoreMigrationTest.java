package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conversion of the 144 graded claims from eight grouped files to one file each (story 7.7),
 * held to a manifest {@code tools/lore-split.py} wrote from the grouped files before it removed
 * them: for every claim, the SHA-256 of each of its ten fields as the grouped file wrote it.
 *
 * <p>The test rebuilds each field from the claim file it now lives in, through the three
 * re-spellings the script documents (the tier label, the source in angle brackets, the date that
 * was not stated) and compares hashes, so a field that was dropped, shortened or re-worded fails
 * by name. A claim is allowed to differ in the three things the conversion added or may change
 * later: its {@code status}, its {@code implemented_in} and its {@code source_type}; a claim added
 * after the conversion is not in the manifest and is not this test's business.
 *
 * <p>Re-grading a converted claim on purpose (a new verdict, a promoted Tier) changes its hash. That
 * edit updates the manifest line in the same pull request, which is the reviewer's cue that a
 * verdict moved.
 */
class LoreMigrationTest {

    private static final Path ROOT = DocsCitations.root();

    /** The claims per grouped file before the conversion, in file-name order. */
    private static final Map<String, Integer> BEFORE = new LinkedHashMap<>();

    static {
        BEFORE.put("bosses", 31);
        BEFORE.put("caves", 12);
        BEFORE.put("city", 15);
        BEFORE.put("halls", 8);
        BEFORE.put("prison", 5);
        BEFORE.put("quest-branch", 39);
        BEFORE.put("sewers", 10);
        BEFORE.put("specials", 24);
    }

    /** The order the manifest hashes the fields in. */
    private static final List<String> FIELDS =
            List.of("Claim", "Tier", "Source", "Variant", "Version", "Date", "Retrieved", "Verdict", "Cites", "Card");

    private record Row(String id, String group, String heading, List<String> hashes) {
    }

    private static List<Row> manifest() throws IOException {
        try (InputStream in = LoreMigrationTest.class.getResourceAsStream("/lore-migration.tsv")) {
            assertNotNull(in, "lore-migration.tsv is on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).map(l -> {
                String[] c = l.split("\t", -1);
                return new Row(c[0], c[1], c[2], List.of(c).subList(3, c.length));
            }).toList();
        }
    }

    @Test
    @DisplayName("the count per group before and after is equal: 31, 12, 15, 8, 5, 39, 10, 24")
    void the_counts_are_equal() throws IOException {
        Map<String, Integer> manifested = new TreeMap<>();
        Map<String, Integer> filed = new TreeMap<>();
        for (Row row : manifest()) {
            manifested.merge(row.group(), 1, Integer::sum);
            Path file = ROOT.resolve("lore/bestiary/" + row.id() + ".md");
            assertTrue(Files.isRegularFile(file), row.id() + " has no file");
            filed.merge(row.group(), 1, Integer::sum);
        }
        assertEquals(new TreeMap<>(BEFORE), manifested, "the manifest holds the grouped files' counts");
        assertEquals(new TreeMap<>(BEFORE), filed, "and every one of them is a file now");
        assertEquals(144, manifested.values().stream().mapToInt(Integer::intValue).sum());
        for (String group : BEFORE.keySet()) {
            assertFalse(Files.exists(ROOT.resolve("lore/bestiary/" + group + ".md")), "the grouped file " + group + ".md is removed");
        }
    }

    @Test
    @DisplayName("no field was dropped or altered: every converted claim carries its ten fields as the grouped file wrote them")
    void no_field_was_dropped() throws Exception {
        for (Row row : manifest()) {
            Lore.Doc doc = Lore.parse(row.id(), Lore.read(ROOT.resolve("lore/bestiary/" + row.id() + ".md")));
            assertEquals(row.group(), doc.get("group"), row.id());
            assertEquals(row.heading().replaceAll("^## (.*) \\(`([^`]+)`\\)$", "$1|$2"),
                    doc.get("subject") + "|" + doc.get("subject_class"), row.id() + ": the mob the claim sits under");
            String tier = doc.get("tier");
            Map<String, String> original = new LinkedHashMap<>();
            original.put("Claim", doc.get("claim"));
            original.put("Tier", tier + " (" + Map.of("1", "TIER1", "F", "CONTRADICTED", "3", "UNVERIFIED").get(tier) + ")");
            original.put("Source", "<" + doc.get("source_url") + ">");
            original.put("Variant", doc.get("variant"));
            original.put("Version", doc.get("version_claimed"));
            original.put("Date", doc.get("date").equals("unknown") ? "not stated by the source" : doc.get("date"));
            original.put("Retrieved", doc.get("retrieved"));
            original.put("Verdict", verdict(doc));
            original.put("Cites", String.join(", ", doc.list("cites").stream().map(c -> "`" + c + "`").toList()));
            original.put("Card", "`" + doc.get("card") + "`");
            for (int i = 0; i < FIELDS.size(); i++) {
                assertEquals(row.hashes().get(i), sha(original.get(FIELDS.get(i))),
                        row.id() + ": the field " + FIELDS.get(i) + " is not what the grouped file held");
            }
            assertEquals("v4.0.0", doc.get("graded_against"), row.id() + " keeps the tag it was graded against");
        }
    }

    @Test
    @DisplayName("the old verdicts are kept as written, and the ones whose cited lines moved say needs-review")
    void moved_verdicts_are_marked() throws IOException {
        long flagged = 0;
        for (Row row : manifest()) {
            try {
                Lore.Doc doc = Lore.parse(row.id(), Lore.read(ROOT.resolve("lore/bestiary/" + row.id() + ".md")));
                if ("needs-review".equals(doc.get("status"))) {
                    flagged++;
                }
            } catch (Lore.Unreadable bad) {
                throw new AssertionError(bad);
            }
        }
        // The checker holds each status to the lines (LoreCheckTest); this holds that the
        // conversion did the marking, so that no verdict claims v4.0.2 for lines it never read there.
        assertTrue(flagged > 0, "some cited lines moved between v4.0.0 and v4.0.2, and the conversion marked them");
    }

    private static String verdict(Lore.Doc doc) {
        String body = doc.body();
        String heading = "## Verdict against `" + doc.get("graded_against") + "`";
        assertTrue(body.stripLeading().startsWith(heading), doc.name());
        return body.stripLeading().substring(heading.length()).strip();
    }

    private static String sha(String text) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
