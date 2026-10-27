package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lore checker's rules (story 7.7), one case per rule on a claim written here, and the sweep
 * over the committed claims, which is the part that makes {@code ./gradlew build} a gate.
 *
 * <p>The rule cases cite two lines of the pinned game that are known to behave: {@code Rat.java}
 * 36-62 reads the same at {@code v4.0.0} and at the pin, and {@code Mob.java} 558-568 does not.
 * They are real lines because the checker's whole point is that a citation is read, not trusted.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LoreCheckTest {

    private static final Path ROOT = DocsCitations.root();

    /** A registry that knows one of each, so a rule case does not depend on the Brain's current Policies. */
    private static final Lore.Registry REGISTRY =
            new Lore.Registry(Set.of("fight"), Set.of("chasm-step"), Set.of("hp"));

    /** A cite that reads the same at v4.0.0 and at the pin, and one that does not. */
    private static final String STEADY = "core/…/mobs/Rat.java:36-62";
    private static final String MOVED = "core/…/mobs/Mob.java:558-568";

    @TempDir
    Path temp;

    // ------------------------------------------------------------------------------ the sweep

    @Test
    @DisplayName("the committed lore reports nothing, and the check read every claim file")
    void the_committed_lore_reports_nothing() {
        List<LoreCheck.Finding> findings = LoreTool.check(ROOT);
        assertTrue(findings.isEmpty(), "a lore claim is invalid. Fix the file, never the checker (docs/adr/0021-lore-claim-format.md)."
                + " Run `" + LoreTool.COMMAND + "` for the report:\n" + LoreCheck.report(ROOT.resolve(Lore.DIR), LoreTool.claims(ROOT), findings));
        long files = Lore.files(ROOT.resolve(Lore.DIR)).stream().filter(f -> f.toString().endsWith(".md")).count();
        assertEquals(files, LoreTool.claims(ROOT), "every Markdown file under lore/ is a claim file the check looked at");
        assertTrue(files >= 144, "the 144 converted claims are there: " + files);
    }

    @Test
    @DisplayName("every committed claim is in the writer's own form, so intake and review rewrite nothing they did not change")
    void the_committed_claims_round_trip() {
        Path loreDir = ROOT.resolve(Lore.DIR);
        for (Path file : Lore.files(loreDir)) {
            String text = Lore.read(file);
            try {
                assertEquals(text, Lore.render(Lore.parse(file.getFileName().toString(), text)), file + " is not canonical");
            } catch (Lore.Unreadable bad) {
                throw new AssertionError(file + ": " + bad.getMessage(), bad);
            }
        }
    }

    @Test
    @DisplayName("the real registry knows the Brain's Policies, its Rules index slugs and the weights fields")
    void the_real_registry_knows_what_exists() {
        Lore.Registry registry = LoreTool.registry(ROOT);
        assertEquals(null, registry.problem("policy:fight"));
        assertEquals(null, registry.problem("rule:adjacent-chebyshev"));
        assertEquals(null, registry.problem("playbook:hp"));
        assertTrue(registry.problem("policy:no-such-policy").contains("no Policy"));
        assertTrue(registry.problem("rule:no-such-row").contains("docs/brain-rules.md"));
        assertTrue(registry.problem("playbook:nothing").contains("Playbook"));
        assertTrue(registry.problem("fight").contains("policy:<name>"), "a bare name is ambiguous, so it is refused");
    }

    // --------------------------------------------------------------------------- the field rules

    @Test
    @DisplayName("a well-formed claim, with and without citations, reports nothing")
    void a_good_claim_passes() throws IOException {
        assertEquals(List.of(), check(claim(Map.of())));
        assertEquals(List.of(), check(claim(Map.of("tier", "1", "cites", "  - " + STEADY, "implemented_in", "  - policy:fight\n  - rule:chasm-step\n  - playbook:hp"))));
        assertEquals(List.of(), check(claim(Map.of("variant", "mod:remixed-pd", "date", "2024-03-01"))));
    }

    @Test
    @DisplayName("a required field that is missing is named")
    void a_missing_field_is_named() throws IOException {
        for (String key : LoreCheck.REQUIRED) {
            Map<String, String> without = new LinkedHashMap<>(base());
            without.remove(key);
            List<String> findings = check(write("bestiary", "sewers-1", render(without, body("v4.0.2"))));
            assertTrue(findings.stream().anyMatch(f -> f.contains("`" + key + "` is missing")), key + " -> " + findings);
        }
    }

    @Test
    @DisplayName("each enum and shape is held: variant, tier, source type, status, tag, URL, dates, slugs")
    void the_values_are_held() throws IOException {
        expect(Map.of("variant", "vanilla"), "variant");
        expect(Map.of("variant", "mod:"), "variant");
        expect(Map.of("tier", "4"), "tier");
        expect(Map.of("tier", "f"), "tier");
        expect(Map.of("source_type", "blog"), "source_type");
        expect(Map.of("status", "stale"), "status");
        expect(Map.of("graded_against", "4.0.2"), "graded_against");
        expect(Map.of("source_url", "wiki/Rat"), "source_url");
        expect(Map.of("source_url", "ftp://example.org/rat"), "source_url");
        expect(Map.of("retrieved", "26 September 2026"), "retrieved");
        expect(Map.of("retrieved", "2026-02-30"), "retrieved");
        expect(Map.of("date", "last year"), "date");
        expect(Map.of("topic", "Bestiary"), "topic");
        expect(Map.of("group", "Sewers"), "group");
        expect(Map.of("version_claimed", ""), "version_claimed");
        expect(Map.of("claim", ""), "claim");
        expect(Map.of("cites", "  - Rat.java"), "path:line");
        expect(Map.of("surprise", "yes"), "unknown key");
    }

    @Test
    @DisplayName("the claim is a paraphrase of bounded length and the quote is short")
    void the_body_rule_is_held() throws IOException {
        expect(Map.of("claim", "\"" + "x".repeat(LoreCheck.CLAIM_MAX + 1) + "\""), "a paraphrase is at most");
        expect(Map.of("quote", "\"" + "x".repeat(LoreCheck.QUOTE_MAX + 1) + "\""), "quote is");
        assertEquals(List.of(), check(claim(Map.of("quote", "\"" + "x".repeat(LoreCheck.QUOTE_MAX) + "\""))));
        assertTrue(check(write("bestiary", "sewers-1", render(base(), ""))).stream().anyMatch(f -> f.contains("body is empty")));
        assertTrue(check(write("bestiary", "sewers-1", render(base(), "\nJust a verdict.\n"))).stream()
                .anyMatch(f -> f.contains("Verdict against")));
        assertTrue(check(write("bestiary", "sewers-1", render(base(), body("v4.0.0")))).stream()
                .anyMatch(f -> f.contains("Verdict against `v4.0.2`")), "a verdict names the tag the file says it was graded at");
    }

    @Test
    @DisplayName("a seed, a layout, a coordinate and a level feeling tied to a Run are refused in a stored claim too")
    void a_claim_about_one_run_is_refused() throws IOException {
        expect(Map.of("claim", "\"On seed 4821 the rat room is next to the stairs.\""), "refused at intake");
        expect(Map.of("claim", "\"The dungeon layout puts the key in the north-east corner.\""), "refused at intake");
    }

    @Test
    @DisplayName("an id is the group and a number, the file is named for it and sits in its topic's folder, and an id is used once")
    void identity_is_held() throws IOException {
        List<String> misnamed = check(write("bestiary", "sewers-2", claimText(Map.of("id", "sewers-1"))));
        assertTrue(misnamed.stream().anyMatch(f -> f.contains("the file is named sewers-2 and the id says sewers-1")), misnamed.toString());
        List<String> elsewhere = check(write("prison", "sewers-1", claimText(Map.of())));
        assertTrue(elsewhere.stream().anyMatch(f -> f.contains("the folder is prison and the topic says bestiary")), elsewhere.toString());
        expect(Map.of("id", "rats-1"), "is not `sewers-<n>`");
        expect(Map.of("id", "sewers-0"), "is not `sewers-<n>`");
        write("bestiary", "sewers-1", claimText(Map.of()));
        List<String> twice = check(write("prison", "sewers-1", claimText(Map.of("topic", "prison"))));
        assertTrue(twice.stream().anyMatch(f -> f.contains("is also")), twice.toString());
    }

    @Test
    @DisplayName("nothing but claim files lives under lore/, and a file that is not frontmatter is read as such")
    void strays_and_unreadable_files() throws IOException {
        Files.createDirectories(temp.resolve("lore/bestiary"));
        Files.writeString(temp.resolve("lore/README.md"), "# hello\n");
        Files.writeString(temp.resolve("lore/bestiary/sewers-1.md"), "no frontmatter\n");
        Files.writeString(temp.resolve("lore/bestiary/sewers-2.md"), "---\nclaim: \"x\r\n---\n");
        Files.writeString(temp.resolve("lore/bestiary/sewers-3.md"), "---\nclaim: \"x\"\nclaim: \"y\"\n---\n");
        List<String> findings = check();
        assertTrue(findings.stream().anyMatch(f -> f.contains("README.md") && f.contains("not a claim file")), findings.toString());
        assertTrue(findings.stream().anyMatch(f -> f.contains("sewers-1.md") && f.contains("does not open with")), findings.toString());
        assertTrue(findings.stream().anyMatch(f -> f.contains("sewers-2.md") && f.contains("carriage return")), findings.toString());
        assertTrue(findings.stream().anyMatch(f -> f.contains("sewers-3.md") && f.contains("written twice")), findings.toString());
    }

    @Test
    @DisplayName("the release table is the one data file allowed beside the claim folders (story 7.9)")
    void the_release_table_is_not_a_stray() throws IOException {
        Files.createDirectories(temp.resolve("lore/bestiary"));
        Files.writeString(temp.resolve("lore/release-dates.json"), "{}\n");
        assertEquals(List.of(), check(), "release-dates.json is not a stray");
        Files.writeString(temp.resolve("lore/other.json"), "{}\n");
        List<String> findings = check();
        assertTrue(findings.stream().anyMatch(f -> f.contains("other.json") && f.contains("not a claim file")), findings.toString());
        assertEquals(1, findings.size(), findings.toString());
    }

    // --------------------------------------------------------------------- tiers and citations

    @Test
    @DisplayName("Tier 1 and Tier 2 need a citation, Tier 3 and F do not")
    void a_tier_needs_its_citation() throws IOException {
        expect(Map.of("tier", "1"), "tier 1 needs at least one path:line citation");
        expect(Map.of("tier", "2"), "tier 2 needs at least one path:line citation");
        assertEquals(List.of(), check(claim(Map.of("tier", "3"))));
        assertEquals(List.of(), check(claim(Map.of("tier", "F"))));
    }

    @Test
    @DisplayName("a citation that does not resolve at the tag the claim was graded against is a finding with the reason")
    void a_citation_must_resolve() throws IOException {
        expect(Map.of("tier", "1", "cites", "  - core/…/mobs/Rat.java:900000"), "past the end");
        expect(Map.of("tier", "1", "cites", "  - core/…/mobs/NoSuchMob.java:1-5"), "not in the tree");
        expect(Map.of("tier", "1", "cites", "  - Rat.java:0"), "no file has a line 0");
        expect(Map.of("tier", "1", "cites", "  - core/…/mobs/Rat.java:36-62", "graded_against", "v9.9.9"), "not a ref");
        assertEquals(List.of(), check(claim(Map.of("tier", "1", "cites", "  - core/…/mobs/Rat.java:36-62\n  - Mob.java:558-568"))));
    }

    @Test
    @DisplayName("a claim graded at an older tag is needs-review exactly when a cited line reads differently at the pin")
    void status_follows_the_cited_lines() throws IOException {
        Map<String, String> old = Map.of("graded_against", "v4.0.0", "tier", "1");
        // Cited lines that read the same at both tags: the claim stays current, and says so.
        assertEquals(List.of(), check(claim(with(old, "cites", "  - " + STEADY), "v4.0.0")));
        List<String> flagged = check(claim(with(old, "cites", "  - " + STEADY, "status", "needs-review"), "v4.0.0"));
        assertTrue(flagged.stream().anyMatch(f -> f.contains("every cited line reads the same") && f.contains("the status is current")), flagged.toString());
        // One cited line moved: the claim is needs-review, and the finding names the line.
        assertEquals(List.of(), check(claim(with(old, "cites", "  - " + STEADY + "\n  - " + MOVED, "status", "needs-review"), "v4.0.0")));
        List<String> unflagged = check(claim(with(old, "cites", "  - " + STEADY + "\n  - " + MOVED), "v4.0.0"));
        assertTrue(unflagged.stream().anyMatch(f -> f.contains(MOVED) && f.contains("so the status is needs-review, not current")), unflagged.toString());
        // The verdict is never rewritten: a claim graded at the pin has no older tag to be old against.
        List<String> atPin = check(claim(Map.of("status", "needs-review")));
        assertTrue(atPin.stream().anyMatch(f -> f.contains("a verdict at the pin is current")), atPin.toString());
    }

    @Test
    @DisplayName("review sets the status the citations say and nothing else, and a second review changes nothing")
    void review_rewrites_only_the_status() throws IOException {
        Map<String, String> old = Map.of("graded_against", "v4.0.0", "tier", "1", "cites", "  - " + MOVED);
        Path file = claim(old, "v4.0.0");
        String before = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(List.of("sewers-1 -> needs-review"), LoreTool.review(ROOT, temp.resolve("lore")));
        String after = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(before.replace("status: current", "status: needs-review"), after);
        assertEquals(List.of(), LoreTool.review(ROOT, temp.resolve("lore")));
        assertEquals(List.of(), check());
    }

    // ------------------------------------------------------------------------ implemented_in

    @Test
    @DisplayName("an implemented_in name has to be a Policy, a Rules-index slug or a Playbook field that exists")
    void implemented_in_names_exist() throws IOException {
        expect(Map.of("implemented_in", "  - policy:nothing"), "no Policy named nothing");
        expect(Map.of("implemented_in", "  - rule:nothing"), "no row of docs/brain-rules.md has the slug nothing");
        expect(Map.of("implemented_in", "  - playbook:nothing"), "no Playbook field named nothing");
        expect(Map.of("implemented_in", "  - fight"), "policy:<name>, rule:<slug> or playbook:<field>");
    }

    // -------------------------------------------------------------------------------- the helpers

    private void expect(Map<String, String> change, String fragment) throws IOException {
        Path file = claim(change);
        List<String> findings = check();
        assertFalse(findings.isEmpty(), change + " should have been refused");
        assertTrue(findings.stream().anyMatch(f -> f.contains(fragment)), change + " -> expected '" + fragment + "' in " + findings);
        Files.delete(file);
    }

    private static Map<String, String> with(Map<String, String> map, String... more) {
        Map<String, String> out = new LinkedHashMap<>(map);
        for (int i = 0; i < more.length; i += 2) {
            out.put(more[i], more[i + 1]);
        }
        return out;
    }

    /** A valid Tier 3 claim at the pin, in file order; the values are the file's text, already quoted where free text. */
    private static Map<String, String> base() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", "sewers-1");
        m.put("topic", "bestiary");
        m.put("group", "sewers");
        m.put("subject", "\"Marsupial rat\"");
        m.put("source_url", "https://pixeldungeon.fandom.com/wiki/Marsupial_rat");
        m.put("source_type", "wiki");
        m.put("date", "unknown");
        m.put("retrieved", "2026-09-26");
        m.put("variant", "spd");
        m.put("version_claimed", "\"unspecified\"");
        m.put("tier", "3");
        m.put("graded_against", "v4.0.2");
        m.put("status", "current");
        m.put("claim", "\"A lone rat is not worth a consumable.\"");
        m.put("cites", "[]");
        m.put("implemented_in", "[]");
        return m;
    }

    private static String body(String tag) {
        return "\n## Verdict against `" + tag + "`\n\nNot graded against the pinned code.\n";
    }

    private static String render(Map<String, String> fields, String body) {
        StringBuilder out = new StringBuilder("---\n");
        for (Map.Entry<String, String> e : fields.entrySet()) {
            boolean list = e.getValue().startsWith("  - ");
            out.append(e.getKey()).append(':').append(list ? "\n" : " ").append(e.getValue()).append('\n');
        }
        return out.append("---\n").append(body).toString();
    }

    private String claimText(Map<String, String> change) {
        return claimText(change, "v4.0.2");
    }

    private String claimText(Map<String, String> change, String bodyTag) {
        Map<String, String> fields = base();
        fields.putAll(change);
        return render(fields, body(change.getOrDefault("graded_against", bodyTag)));
    }

    private Path claim(Map<String, String> change) throws IOException {
        return write("bestiary", change.getOrDefault("id", "sewers-1"), claimText(change));
    }

    private Path claim(Map<String, String> change, String bodyTag) throws IOException {
        return write("bestiary", change.getOrDefault("id", "sewers-1"), claimText(change, bodyTag));
    }

    private Path write(String topic, String id, String text) throws IOException {
        Path file = temp.resolve("lore").resolve(topic).resolve(id + ".md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private List<String> check() {
        return LoreCheck.check(ROOT, temp.resolve("lore"), REGISTRY).stream().map(LoreCheck.Finding::toString)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> check(Path ignored) {
        return check();
    }
}
