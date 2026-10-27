package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The variant classifier (story 7.8): one case per rule on a table read from the committed Codex, the
 * labelled fixture with the thresholds the story file stated before the first run, and the source
 * check (no network, no game class).
 */
class VariantClassifierTest {

    private static final Path ROOT = DocsCitations.root();

    private static final VariantClassifier CLASSIFIER = VariantClassifier.of(ROOT, Upstream.tag(ROOT));

    private static VariantClassifier.Result run(String text, String source, String... subjects) {
        return CLASSIFIER.classify(text, List.of(subjects), source);
    }

    @Test
    @DisplayName("the table has the 449 names the story counts, and the tag it was read at")
    void the_table_is_read() {
        assertEquals(449, CLASSIFIER.rows());
        assertEquals(Upstream.tag(ROOT), CLASSIFIER.tag);
    }

    @Test
    @DisplayName("a name only this game gives is spd, and the evidence says which table")
    void spd_only_name() {
        VariantClassifier.Result r = run("Necromancers summon a skeleton.", "wiki", "Necromancer");
        assertEquals("spd", r.variant());
        assertEquals("name-only-here", r.evidence().get(0).rule());
    }

    @Test
    @DisplayName("a name only vanilla gives is pd")
    void pd_only_name() {
        VariantClassifier.Result r = run("The Wand of Firebolt burns grass.", "forum", "Wand of Firebolt");
        assertEquals("pd", r.variant());
        assertEquals("name-only-vanilla", r.evidence().get(0).rule());
    }

    @Test
    @DisplayName("a class name finds the row when the display name does not")
    void a_class_name_is_a_subject() {
        assertEquals("spd", run("It heals allies.", "wiki", "actors.mobs.Necromancer").variant());
    }

    @Test
    @DisplayName("a shared name with a figure that matches one side gives that side")
    void shared_name_with_a_matching_figure() {
        assertEquals("spd", run("A golem has 120 HP.", "wiki", "Golem").variant());
        assertEquals("pd", run("The golem has HP 85 and is slow.", "wiki", "Golem").variant());
        assertEquals("spd", run("Goo has 8 defence.", "wiki", "Goo").variant());
        VariantClassifier.Result r = run("Scorpio has 95 health.", "wiki", "Scorpio");
        assertEquals("pd", r.variant());
        assertTrue(r.why().contains("vanilla's"), r.why());
    }

    @Test
    @DisplayName("a shared name with no figure, or none that differs, is unknown")
    void shared_name_without_a_figure_is_unknown() {
        assertEquals("unknown", run("Goo pumps up before it attacks.", "wiki", "Goo").variant());
        VariantClassifier.Result ankh = run("The ankh revives the hero.", "wiki", "Ankh");
        assertEquals("unknown", ankh.variant());
        assertEquals("shared-no-figure", ankh.evidence().get(0).rule());
    }

    @Test
    @DisplayName("a figure that matches neither side, or both, is unknown")
    void a_figure_matching_neither_side_is_unknown() {
        VariantClassifier.Result neither = run("Scorpio has 200 HP.", "wiki", "Scorpio");
        assertEquals("unknown", neither.variant());
        assertTrue(neither.why().contains("matches neither"), neither.why());
        VariantClassifier.Result both = run("Scorpio has 110 HP here and 95 HP there.", "wiki", "Scorpio");
        assertEquals("unknown", both.variant());
    }

    @Test
    @DisplayName("evidence that points both ways is unknown and shows both")
    void conflicting_evidence_is_unknown() {
        VariantClassifier.Result r = run("Both appear in the thread.", "wiki", "Necromancer", "Wand of Firebolt");
        assertEquals("unknown", r.variant());
        assertTrue(r.why().contains("name-only-here") && r.why().contains("name-only-vanilla"), r.why());
        assertTrue(r.why().contains("conflict"), r.why());
        assertEquals("unknown", run("A Wand of Firebolt, from a developer.", "developer", "Wand of Firebolt").variant());
    }

    @Test
    @DisplayName("a developer source is spd by construction, a mechanic with no name is unknown otherwise")
    void mechanics_classify_by_source_and_declaration() {
        assertEquals("spd", run("Searching reveals adjacent secrets.", "developer").variant());
        assertEquals("unknown", run("Searching reveals adjacent secrets.", "forum").variant());
        assertEquals("spd", run("In Shattered Pixel Dungeon searching reveals secrets.", "forum").variant());
        assertEquals("pd", run("In vanilla, searching reveals secrets.", "forum").variant());
        assertEquals("unknown", run("Vanilla Shattered searching reveals secrets.", "forum").variant());
    }

    @Test
    @DisplayName("a mod named in the text wins over the names, and a possessive or an article is not a mod")
    void mods() {
        assertEquals("mod:remixed", run("In Remixed Pixel Dungeon the Necromancer is slower.", "forum", "Necromancer").variant());
        assertEquals("mod:summoning", run("Summoning PD has a Necromancer.", "wiki", "Necromancer").variant());
        assertEquals("spd", run("In Shattered Pixel Dungeon the Necromancer is slow.", "wiki", "Necromancer").variant());
        assertEquals("pd", run("In Watabou's Pixel Dungeon, the original Pixel Dungeon, there is a Wand of Firebolt.",
                "wiki", "Wand of Firebolt").variant());
        assertEquals("unknown", run("Remixed Pixel Dungeon and Unleashed Pixel Dungeon both change it.", "forum").variant());
    }

    @Test
    @DisplayName("with no subjects the names are found in the text, longest first, and a figure is tied to one name only")
    void names_found_in_the_text() {
        VariantClassifier.Result r = run("A rot lasher grows in the garden.", "forum");
        assertEquals("spd", r.variant());
        assertTrue(r.why().contains("found in the text"), r.why());
        VariantClassifier.Result untied = run("The golem and the scorpio both have 120 HP.", "forum");
        assertEquals("unknown", untied.variant());
        assertTrue(untied.why().contains("shared-figure-untied"), untied.why());
    }

    @Test
    @DisplayName("every result carries evidence, whatever it says")
    void evidence_is_never_empty() {
        for (VariantReport.Case c : fixture()) {
            assertFalse(CLASSIFIER.classify(c.text(), c.subjects(), c.source()).evidence().isEmpty(), c.text());
        }
        assertEquals("no-signal", run("Hello.", "other").evidence().get(0).rule());
        assertEquals("name-unlisted", run("A thing.", "other", "Nonexistent mob").evidence().get(0).rule());
    }

    private static List<VariantReport.Case> fixture() {
        return VariantReport.fixture(Lore.read(ROOT.resolve(VariantReport.FIXTURE)));
    }

    @Test
    @DisplayName("the labelled fixture: at least 40 claims, and precision and recall at the thresholds the story stated")
    void the_fixture_holds_the_thresholds() {
        List<VariantReport.Case> cases = fixture();
        assertTrue(cases.size() >= 40, "the fixture has " + cases.size() + " claims");
        for (String label : VariantReport.CLASSES) {
            assertTrue(cases.stream().anyMatch(c -> VariantReport.kind(c.label()).equals(label)), "the fixture has a " + label + " case");
        }
        VariantReport.Scores scores = VariantReport.score(CLASSIFIER, cases);
        for (String c : VariantReport.CLASSES) {
            assertTrue(scores.precision(c) >= VariantReport.MIN_PRECISION,
                    c + " precision " + scores.precision(c) + " is below " + VariantReport.MIN_PRECISION + ": " + scores.wrong());
            assertTrue(scores.recall(c) >= VariantReport.MIN_RECALL,
                    c + " recall " + scores.recall(c) + " is below " + VariantReport.MIN_RECALL + ": " + scores.wrong());
        }
    }

    @Test
    @DisplayName("the story file states the same thresholds the test enforces")
    void the_thresholds_are_the_ones_the_story_wrote() throws IOException {
        String story = Files.readString(ROOT.resolve("_bmad-output/implementation-artifacts/7-8-the-variant-classifier-from-the-vocabulary-diff.md"),
                StandardCharsets.UTF_8);
        assertTrue(story.contains("**" + String.format("%.2f", VariantReport.MIN_PRECISION) + "**"), "precision threshold in the story file");
        assertTrue(story.contains("**" + String.format("%.2f", VariantReport.MIN_RECALL) + "**"), "recall threshold in the story file");
    }

    @Test
    @DisplayName("the classifier and the report name no network class and no game class")
    void no_network_and_no_game_code() throws IOException {
        Path dir = ROOT.resolve("shatterfish/codex/src/test/java/org/shatterfish/codex");
        for (String file : List.of("VariantClassifier.java", "VariantReport.java")) {
            String source = Files.readString(dir.resolve(file), StandardCharsets.UTF_8);
            for (String banned : List.of("java.net", "HttpClient", "URLConnection", "com.shatteredpixel", "com.watabou", "java.lang.Process")) {
                assertFalse(source.lines().filter(l -> l.startsWith("import")).anyMatch(l -> l.contains(banned)),
                        file + " imports " + banned);
            }
        }
    }
}
