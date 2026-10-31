package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.Brain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coach's sentence (story 8.8, FR-47, UJ-4), against constructed Decisions: one case per Policy the Brain
 * arbitrates (a Policy with no sentence fails the build), the shape of a line, the closed vocabulary, the docs
 * page that names the Rule each reason rests on, and the Decision with no usable reason.
 */
class CoachLineTest {

    /** A representative {@code why} of every template, as the Policy writes it ({@code DecisionShapeTest}'s grammar). */
    private static final Map<String, String> SAMPLES = Map.ofEntries(
            Map.entry("answer-prompt/jump", "jump: Yes"),
            Map.entry("answer-prompt/affirm", "affirm: Yes, I'm positive"),
            Map.entry("answer-prompt/decline", "decline: No, I changed my mind"),
            Map.entry("answer-prompt/answer", "answer: Yes, I'm positive"),
            Map.entry("answer-prompt/dismiss", "dismiss"),
            Map.entry("answer-prompt/subclass", "subclass: Gladiator"),
            Map.entry("answer-prompt/upgrade", "upgrade: dagger"),
            Map.entry("answer-prompt/guess", "guess: potion"),
            Map.entry("answer-prompt/leave", "leave: Back"),
            Map.entry("heal/heal", "heal 12/30"),
            Map.entry("douse/douse/water", "douse: water"),
            Map.entry("douse/douse/ground", "douse: bare ground"),
            Map.entry("zap/zap", "zap: staff of magic missile"),
            Map.entry("shoot/shoot", "shoot: Spirit Bow"),
            Map.entry("ranged/throw", "throw: dart"),
            Map.entry("one-ply/heal", "heal 12/30"),
            Map.entry("one-ply/retreat", "retreat 5"),
            Map.entry("one-ply/reposition", "reposition 2"),
            Map.entry("one-ply/attack", "attack: rat"),
            Map.entry("fight/chase", "chase 4"),
            Map.entry("fight/hold", "hold: chokepoint"),
            Map.entry("fight/retreat/stairs", "retreat: stairs"),
            Map.entry("fight/retreat/water", "retreat: leave water 3"),
            Map.entry("fight/retreat", "retreat 6"),
            Map.entry("fight/attack", "attack: gnoll"),
            Map.entry("fight/chokepoint", "chokepoint 2"),
            Map.entry("fight/approach", "approach 3"),
            Map.entry("fight/cornered", "cornered: gnoll"),
            Map.entry("fight/dodge", "dodge: pump"),
            Map.entry("fight/goo/attack", "goo: attack"),
            Map.entry("fight/goo/wait", "goo: wait"),
            Map.entry("fight/goo/approach", "goo: approach 3"),
            Map.entry("eat/eat", "eat: ration"),
            Map.entry("eat/eat/water", "eat: water"),
            Map.entry("test-item/escape", "escape: fire (escape_waits)"),
            Map.entry("test-item/upgrade", "upgrade: dagger"),
            Map.entry("test-item/drink", "drink: potion (known_gains)"),
            Map.entry("test-item/identify", "identify: potion (identify-held)"),
            Map.entry("test-item/cell", "cell: scroll (reach)"),
            Map.entry("test-item/rest", "rest: after-test (rest_waits)"),
            Map.entry("test-item/test/healing", "test: potion (healing_odds_per_mille)"),
            Map.entry("test-item/test/gain", "test: potion (gain_odds_per_mille)"),
            Map.entry("test-item/test/inventory", "test: scroll (inventory_odds_per_mille)"),
            Map.entry("test-item/test/scroll", "test: scroll (scroll_hp_per_mille)"),
            Map.entry("test-item/test", "test: potion ()"),
            Map.entry("pick-up/item", "item: dart 4"),
            Map.entry("pick-up/take", "take: dart"),
            Map.entry("open-chest/open", "open: chest 3"),
            Map.entry("open-chest/chest", "chest: 3"),
            Map.entry("equip/wear", "wear: mail armor"),
            Map.entry("descend/spent", "descend: spent"),
            Map.entry("descend/hungry", "exit: hungry 12"),
            Map.entry("descend/overstayed", "boss exit: overstayed 9"),
            Map.entry("descend/unlock", "unlock: spent"),
            Map.entry("descend/no exit", "no exit: search"),
            Map.entry("descend/rest", "rest: descent"),
            Map.entry("tengu/rest", "tengu: rest"),
            Map.entry("tengu/unlock", "tengu: unlock"),
            Map.entry("tengu/door", "tengu: door 4"),
            Map.entry("tengu/attack", "tengu: attack"),
            Map.entry("tengu/approach", "tengu: approach 4"),
            Map.entry("tengu/across", "tengu: across 3"),
            Map.entry("tengu/dodge", "tengu: dodge 2"),
            Map.entry("tengu/hold", "tengu: hold"),
            Map.entry("explore/frontier", "frontier 7"),
            Map.entry("explore/search", "search 2/5"),
            Map.entry("explore/away", "away 3"),
            Map.entry("explore/stuck", "rooted"),
            Map.entry("explore/rest", "rest: before-descent"),
            Map.entry("plunge/jump", "jump 4"),
            Map.entry("plunge/rest", "rest: chasm"),
            Map.entry("fallback/fallback", "act: nothing better applies"));

    private static final Action ACTION = new Action.UseItem(new ItemRef(0, "scroll (KHIT)", 1), "read");

    private static RunLog.Decision decision(String policy, String why, String... flags) {
        return new RunLog.Decision("goal: any", new RunLog.Choice(ACTION, 10_000, why), List.of(), List.of(flags), policy);
    }

    private static String id(CoachLine.Template template) {
        return template.policy() + "/" + template.key();
    }

    private static String policyOf(String sample) {
        return sample.substring(0, sample.indexOf('/'));
    }

    @Test
    @DisplayName("every Policy the Brain arbitrates has a sentence, and no template names a Policy it has not")
    void one_case_per_policy() {
        Set<String> arbitrated = new LinkedHashSet<>(Brain.policyNames(Brain.Model.ONE_PLY));
        arbitrated.addAll(Brain.policyNames());
        Set<String> phrased = new LinkedHashSet<>(CoachLine.policies());
        for (String policy : arbitrated) {
            assertTrue(phrased.contains(policy), "Policy '" + policy + "' has no coach sentence: add a template and a sample");
        }
        for (String policy : phrased) {
            assertTrue(arbitrated.contains(policy), "template for '" + policy + "', which the Brain does not arbitrate");
        }
    }

    @Test
    @DisplayName("each template is reached by the why its Policy writes, and the samples name no template that is not there")
    void every_template_is_reached() {
        Set<String> listed = new TreeSet<>();
        for (CoachLine.Template template : CoachLine.templates()) {
            assertTrue(listed.add(id(template)), "two templates for " + id(template));
        }
        assertEquals(listed, new TreeSet<>(SAMPLES.keySet()), "a sample for each template and a template for each sample");
        for (Map.Entry<String, String> sample : SAMPLES.entrySet()) {
            CoachLine.Template template = CoachLine.template(decision(policyOf(sample.getKey()), sample.getValue()));
            assertNotNull(template, sample.getKey() + ": '" + sample.getValue() + "' reaches no template");
            assertEquals(sample.getKey(), id(template), "'" + sample.getValue() + "'");
        }
    }

    @Test
    @DisplayName("UJ-4: the line is the Action in ActionText's words, a colon, and one clause")
    void the_line() {
        assertEquals("read scroll (KHIT): worst case survivable",
                CoachLine.of(decision("test-item", "test: scroll ()"), null));
        assertEquals("read scroll (KHIT): full health, so a read is safe",
                CoachLine.of(decision("test-item", "test: scroll (scroll_hp_per_mille)"), null));
        assertEquals(ActionText.of(ACTION, (Observation) null) + ": nothing better to do",
                CoachLine.of(decision("fallback", "act: nothing better applies"), null));
    }

    @Test
    @DisplayName("a Safety flag the clause does not already say follows it; one it says is not repeated")
    void flags() {
        assertEquals("read scroll (KHIT): to rest after a test, hurt",
                CoachLine.of(decision("test-item", "rest: after-test (rest_waits)", "hp-low", "enemy-in-view"), null));
        assertEquals("read scroll (KHIT): hurt, with enemies near",
                CoachLine.of(decision("heal", "heal 3/30", "hp-low"), null));
        assertEquals("read scroll (KHIT): to rest after a test, starving",
                CoachLine.of(decision("test-item", "rest: after-test (rest_waits)", "starving", "hp-low"), null));
        assertEquals("read scroll (KHIT): to rest after a test",
                CoachLine.of(decision("test-item", "rest: after-test (rest_waits)", "enemy-in-view"), null));
    }

    @Test
    @DisplayName("a Decision with no usable reason yields the Action alone, and never an invented reason")
    void no_usable_reason() {
        String action = ActionText.of(ACTION, (Observation) null);
        assertEquals(action, CoachLine.of(decision("fight", ""), null));
        assertEquals(action, CoachLine.of(decision("fight", "   "), null));
        assertEquals(action, CoachLine.of(decision("fight", "no such lead 4"), null));
        assertEquals(action, CoachLine.of(decision("a-policy-nobody-wrote", "attack: rat"), null));
        assertNull(CoachLine.of(null, null), "no Decision, no line");
    }

    @Test
    @DisplayName("no line is longer than two wraps of the Panel's narrowest text, and none holds a digit past the Action's own words")
    void fits_and_decodes_nothing() {
        Action attack = new Action.Attack(5);
        for (Map.Entry<String, String> sample : SAMPLES.entrySet()) {
            for (String[] flags : new String[][] {{}, {"starving", "hp-low", "hungry"}}) {
                RunLog.Decision decision = new RunLog.Decision("goal: any",
                        new RunLog.Choice(attack, 10_000, sample.getValue()), List.of(), List.of(flags),
                        policyOf(sample.getKey()));
                String line = CoachLine.of(decision, null);
                assertTrue(line.length() <= CoachLine.MAX_LENGTH, line.length() + " characters: " + line);
                String reason = line.contains(": ") ? line.substring(line.indexOf(": ") + 2) : "";
                assertFalse(reason.matches(".*\\d.*"), "a number to decode in '" + line + "'");
            }
        }
        // A long Action label drops the flag first and the reason last, so the line still fits.
        RunLog.Decision longLabel = new RunLog.Decision("goal: any", new RunLog.Choice(new Action.UseItem(
                new ItemRef(0, "scroll (a very long appearance indeed, and more)", 1), "read"), 10_000, "rest: after-test"),
                List.of(), List.of("hp-low"), "test-item");
        assertTrue(CoachLine.of(longLabel, null).length() <= CoachLine.MAX_LENGTH);
    }

    private static Set<String> closedList() throws IOException {
        try (InputStream in = CoachLineTest.class.getResourceAsStream("coach-words.txt")) {
            assertNotNull(in, "coach-words.txt is committed with this test");
            Set<String> words = new TreeSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    words.add(line.strip().toLowerCase(Locale.ROOT));
                }
            }
            return words;
        }
    }

    @Test
    @DisplayName("every word of every reason is on the closed list committed with the test, and every listed word is used")
    void closed_vocabulary() throws IOException {
        Set<String> allowed = closedList();
        Set<String> used = new TreeSet<>();
        for (CoachLine.Template template : CoachLine.templates()) {
            Matcher word = Pattern.compile("[A-Za-z']+").matcher(template.text());
            while (word.find()) {
                String w = word.group().toLowerCase(Locale.ROOT);
                used.add(w);
                assertTrue(allowed.contains(w), "'" + w + "' (" + id(template) + ": " + template.text() + ") is not on the closed list");
            }
        }
        Set<String> stale = new TreeSet<>(allowed);
        stale.removeAll(used);
        assertTrue(stale.isEmpty(), "listed but used by no reason: " + stale);
    }

    /** The repository root, above the test's working directory. */
    static Path root() {
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            if (Files.exists(at.resolve("vanilla.pin"))) {
                return at;
            }
        }
        throw new IllegalStateException("no repository root");
    }

    @Test
    @DisplayName("every template names the Rule row it rests on, the page lists each one, and the page lists nothing else")
    void the_docs_page_and_the_rules() throws IOException {
        String rules = Files.readString(root().resolve("docs").resolve("brain-rules.md"), StandardCharsets.UTF_8);
        String page = Files.readString(root().resolve("docs").resolve("coach-line.md"), StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        for (String line : page.split("\n")) {
            if (line.startsWith("| `")) {
                rows.add(line.stripTrailing());
            }
        }
        assertEquals(CoachLine.templates().size(), rows.size(), "one row per template on docs/coach-line.md");
        for (CoachLine.Template template : CoachLine.templates()) {
            if (!template.rule().equals(CoachLine.NO_CLAIM)) {
                assertTrue(rules.contains("<a id=\"" + template.rule() + "\"></a>"),
                        id(template) + " rests on '" + template.rule() + "', which is no row of docs/brain-rules.md");
            }
            String expected = "| `" + id(template) + "` | " + template.text() + " | "
                    + (template.rule().equals(CoachLine.NO_CLAIM) ? "none: it says only that nothing above fired"
                    : "[`" + template.rule() + "`](brain-rules.md#" + template.rule() + ")") + " |";
            assertTrue(rows.contains(expected), "docs/coach-line.md lacks the row: " + expected);
        }
    }
}
