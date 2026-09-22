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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The citation checker's own rules, and the sweep over the real documentation (story 2.10).
 *
 * <p>The rules are checked against a repository written here: two tags, a file that exists at one
 * of them and not the other, two files that share a name, and a documentation tree whose every page
 * is a case. Reading the real {@code docs/} for them would be reading whatever the project happens
 * to have written today, which is a test that stops testing the moment someone fixes a page.
 *
 * <p>The sweep is the other half, and it is the one that makes {@code ./gradlew build} a gate: the
 * committed documentation reports nothing, so a citation that stops resolving — at a tag merge, at
 * a rewrite, at a careless edit — turns this test red with the page, the line and the reason.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DocsCitationTest {

    /** The repository root this checkout sits in. */
    private static final Path ROOT = DocsCitations.root();

    /** The pin of the written repository, and the tag it was at before. */
    private static final String PIN = "v1.0.0";
    private static final String BEFORE = "v0.9.0";

    /** The files the written repository holds, and what a citation into them looks like. */
    private static final String WIDGET = "core/src/main/java/com/example/Widget.java";
    private static final String OTHER_WIDGET = "core/src/main/java/com/example/parts/Widget.java";
    private static final String ONLY = "core/src/main/java/com/example/Only.java";
    private static final String GONE = "core/src/main/java/com/example/Gone.java";

    @Test
    @DisplayName("the committed docs/ resolve: every citation, at the tag it names")
    void the_committed_documentation_reports_nothing() {
        List<DocsCitations.Finding> findings = DocsCitations.sweep(ROOT);
        assertTrue(findings.isEmpty(),
                "a citation in docs/ no longer resolves at the tag it names. Read the code at that tag"
                        + " and correct the citation, or flip the rules row to " + DocsCitations.FLAG
                        + " and leave its link where it was true (docs/UPSTREAM.md). Never re-cite a"
                        + " document to make this pass. Run `" + DocsCitations.COMMAND + "` for the"
                        + " report:\n" + DocsCitations.report(ROOT, findings));
    }

    @Test
    @DisplayName("the sweep reads what the project actually wrote, so it is not a check of nothing")
    void the_sweep_reads_every_page_and_finds_citations() {
        List<DocsCitations.Finding> ignored = new ArrayList<>();
        int pages = DocsCitations.pages(ROOT).size();
        int citations = DocsCitations.read(ROOT, ignored).size();
        assertTrue(pages >= 50, "docs/ holds " + pages + " pages; the sweep reads Markdown only");
        assertTrue(citations >= 1_500,
                "the sweep found " + citations + " citations in docs/, which is fewer than the"
                        + " project has written. A lexer that stopped matching would make this test"
                        + " green by checking nothing");
    }

    @Test
    @DisplayName("a line past the end, a file gone, a disagreeing span, an older tag, an ambiguous name")
    void the_checker_reports_each_way_a_citation_fails(@TempDir Path repository) throws IOException {
        Repository written = new Repository(repository);
        Map<String, String> expected = written.cases();

        Set<String> found = new LinkedHashSet<>();
        for (DocsCitations.Finding finding : DocsCitations.sweep(repository)) {
            found.add(finding.page() + ":" + finding.line() + "  " + finding.rule().name());
        }
        StringBuilder wrote = new StringBuilder();
        for (Map.Entry<String, String> one : expected.entrySet()) {
            wrote.append("  ").append(one.getValue()).append(" -> ").append(one.getKey()).append('\n');
        }
        assertEquals(new LinkedHashSet<>(expected.keySet()), found,
                "the checker reported something other than the cases written here. Written, by case:\n"
                        + wrote);
    }

    @Test
    @DisplayName("the report names the page, the file, the line and how the citation failed")
    void the_report_says_what_to_go_and_look_at(@TempDir Path repository) throws IOException {
        new Repository(repository);
        String report = DocsCitations.report(repository, DocsCitations.sweep(repository));
        assertTrue(report.contains("docs/rules/made-up.md:"), "the report names the page:\n" + report);
        assertTrue(report.contains(ONLY), "the report names the file:\n" + report);
        assertTrue(report.contains("has 8 lines"),
                "the report says how long the file at that tag actually is:\n" + report);
        assertTrue(report.contains(DocsCitations.Rule.NOT_IN_THE_TREE.said),
                "the report says which way each citation failed:\n" + report);
    }

    @Test
    @DisplayName("the pinned tag is resolved by the ledger's commit, not by a tag name CI may not have")
    void the_pin_resolves_without_the_tag(@TempDir Path repository) throws IOException {
        new Repository(repository);
        String ledger = DocsCitations.pinnedRevision(repository);
        DocsCitations.git(repository, "tag", "-d", PIN);
        assertEquals(Optional.of(ledger), DocsCitations.revision(repository, PIN, PIN),
                "with the pinned tag deleted — which is what a clone of this fork looks like — the pin"
                        + " still resolves through the Commit row of " + DocsCitations.LEDGER);
        assertEquals(Optional.empty(), DocsCitations.revision(repository, "v9.9.9", PIN),
                "a ref the repository does not have resolves to nothing, and is reported by name");
    }

    /**
     * A repository written for the checker to read: two tags, a file deleted between them, two
     * files that share a name, and a documentation tree whose every page is a case.
     */
    private static final class Repository {

        private final Path root;
        private final Map<String, String> cases = new LinkedHashMap<>();

        Repository(Path root) throws IOException {
            this.root = root;
            git("init", "-q");
            git("config", "user.email", "story@shatterfish.invalid");
            git("config", "user.name", "story 2.10");
            git("config", "commit.gpgsign", "false");
            // The line counts are the whole point, so git must not rewrite the endings it stores.
            git("config", "core.autocrlf", "false");

            write(WIDGET, java(12));
            write(OTHER_WIDGET, java(6));
            write(ONLY, java(8));
            write(GONE, java(5));
            write("build.gradle", "ext {\n    appVersionName = '1.0.0'\n}\n");
            git("add", "-A", "-f");
            git("commit", "-q", "-m", "the tag before");
            git("tag", BEFORE);

            Files.delete(root.resolve(GONE));
            git("add", "-A", "-f");
            git("commit", "-q", "-m", "the pin");
            git("tag", PIN);

            ledger();
            rules();
            prose();
        }

        /** Each expected finding — {@code page:line  RULE} — against the case that wrote it. */
        Map<String, String> cases() {
            return cases;
        }

        private void ledger() throws IOException {
            String commit = git("rev-parse", PIN + "^{commit}").get(0).trim();
            write("docs/UPSTREAM.md", "# Upstream\n\n| | |\n|---|---|\n| Tag | `" + PIN + "` |\n"
                    + "| Commit | `" + commit + "` |\n");
        }

        /** The rules table: the only place the tag-age convention has force. */
        private void rules() throws IOException {
            Page page = new Page("docs/rules/made-up.md");
            page.line("# Made up rules");
            page.line("");
            page.line("| Rule | Cites | Test | Tier | Since |");
            page.line("|---|---|---|---|---|");

            page.line(row(cite(WIDGET, 1, 12, PIN, 1, 12), "1"));
            page.line(row(cite(ONLY, 1, 9_999, PIN, 1, 9_999), "1"),
                    "a line past the end of the file", "PAST_THE_END");
            page.line(row(cite(WIDGET, 1, 12, BEFORE, 1, 12), DocsCitations.FLAG));
            page.line(row(cite(WIDGET, 1, 12, BEFORE, 1, 12), "1"),
                    "an older tag on a row nobody flagged", "A_TAG_NOBODY_FLAGGED");
            page.line(row(cite(WIDGET, 1, 12, PIN, 1, 12), DocsCitations.FLAG),
                    "a flag that outlived its re-citation", "A_FLAG_THAT_OUTLIVED_ITS_RE_CITATION");
            page.line(row(cite(GONE, 1, 5, PIN, 1, 5), "1"),
                    "a file that is not in the tree at that tag", "NOT_IN_THE_TREE");
            page.line(row(cite(WIDGET, 1, 4, PIN, 1, 8), "1"),
                    "a span and a link that state different lines", "SPAN_AND_LINK");
            page.line(row(cite(WIDGET, 1, 12, "v9.9.9", 1, 12), DocsCitations.FLAG),
                    "a ref this repository does not have", "NO_SUCH_REF");
            page.write();
        }

        /** Prose: citations that name no tag, and one that names an older one outside a table. */
        private void prose() throws IOException {
            Page page = new Page("docs/notes.md");
            page.line("# Notes");
            page.line("");
            page.line("One file carries this name, so `Only.java:3` is that file.");
            page.line("Two files carry this one, so `Widget.java:3` is a guess.",
                    "a bare name that is more than one file", "AMBIGUOUS");
            page.line("No file carries `Missing.java:3` at all.",
                    "a bare name that is no file", "NOT_IN_THE_TREE");
            page.line("An elided middle still resolves: `core/…/example/Only.java:3`.");
            page.line("Outside a table an older tag is evidence, not a finding: "
                    + cite(WIDGET, 1, 12, BEFORE, 1, 12) + ".");
            page.write();
        }

        private static String row(String cites, String tier) {
            return "| a rule about the game | " + cites + " | none yet | " + tier + " | story 2.10 |";
        }

        /** A citation as the rules pages write one: a code span linked at a tag. */
        private static String cite(String path, int from, int to, String ref, int anchorFrom, int anchorTo) {
            return "[`" + path + ":" + from + "-" + to + "`](" + DocsCitations.BLOB + ref + "/" + path
                    + "#L" + anchorFrom + "-L" + anchorTo + ")";
        }

        /** A Java file of exactly the given number of lines, so a line count is a known number. */
        private static String java(int lines) {
            StringBuilder text = new StringBuilder("package com.example;\n");
            for (int i = 2; i <= lines; i++) {
                text.append("// line ").append(i).append('\n');
            }
            return text.toString();
        }

        private void write(String path, String content) throws IOException {
            Path file = root.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }

        private List<String> git(String... args) {
            return DocsCitations.git(root, args);
        }

        /** One page being written, which remembers the line each case landed on. */
        private final class Page {

            private final String path;
            private final List<String> lines = new ArrayList<>();

            Page(String path) {
                this.path = path;
            }

            void line(String text) {
                lines.add(text);
            }

            void line(String text, String what, String rule) {
                lines.add(text);
                cases.put(path + ":" + lines.size() + "  " + rule, what);
            }

            void write() throws IOException {
                Path file = root.resolve(path);
                Files.createDirectories(file.getParent());
                Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            }
        }
    }
}
