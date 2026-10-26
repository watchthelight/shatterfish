package org.shatterfish.codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The Results page of story 7.9: the finding about the changelog (what it carries and what can be
 * tied to a claim), the release table, how the gate behaves when every claim is given the oldest,
 * a middle and the newest anchor, the verdicts on the 144 claims under {@code lore/}, and the five
 * flagged claims a person should read first. It reads and never writes a claim, and the gate it
 * runs cannot change a Tier.
 *
 * <p>{@code ./gradlew :codex:lore --args=gate} writes {@link #PAGE}; {@code DateGateReportTest}
 * fails when the committed page and a fresh run differ.
 */
final class DateGateReport {

    private DateGateReport() {
    }

    /** The Results page, relative to the repository root. */
    static final String PAGE = "docs/results/2026-10-26-7-9-date-gating-from-the-changelog.md";

    /** The anchors the sensitivity table gives every claim in turn: the oldest release, a middle one, the newest. */
    static final List<String> PROBES = List.of("v0.1.0", "v3.3", "v4.0.2");

    /** Tier order for ranking: 1 first, F last. */
    private static int rank(String tier) {
        return switch (tier) {
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            default -> 4;
        };
    }

    /** The statuses of a set of verdicts, in the order the gate decides them, zero included. */
    static Map<String, Integer> counts(List<DateGate.Verdict> verdicts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : List.of(DateGate.UNDATED, DateGate.CHANGED_SINCE, DateGate.CLEAN, DateGate.NO_ENTRY)) {
            counts.put(s, 0);
        }
        for (DateGate.Verdict v : verdicts) {
            counts.merge(v.status(), 1, Integer::sum);
        }
        return counts;
    }

    /** Every claim given the same anchor, gated: the check that the gate neither flags nothing nor everything. */
    static Map<String, Integer> probe(DateGate.Gate gate, List<DateGate.Claim> claims, String version) {
        List<DateGate.Verdict> verdicts = new ArrayList<>();
        for (DateGate.Claim c : claims) {
            verdicts.add(gate.check(new DateGate.Claim(c.id(), c.subject(), c.subjectClass(), "unknown", version, c.tier(),
                    c.cites(), c.text())));
        }
        return counts(verdicts);
    }

    /**
     * The five flagged claims to read first: the ones a change is known to follow, then by Tier (a
     * claim the Brain could lean on before one it could not), then by how many later headings
     * name the subject, then by how many headings name it at all.
     */
    static List<Map.Entry<DateGate.Claim, DateGate.Verdict>> consequential(Map<DateGate.Claim, DateGate.Verdict> verdicts, int n) {
        List<Map.Entry<DateGate.Claim, DateGate.Verdict>> flagged = new ArrayList<>();
        for (Map.Entry<DateGate.Claim, DateGate.Verdict> e : verdicts.entrySet()) {
            if (e.getValue().flagged()) {
                flagged.add(e);
            }
        }
        flagged.sort(Comparator
                .comparing((Map.Entry<DateGate.Claim, DateGate.Verdict> e) -> e.getValue().status().equals(DateGate.CHANGED_SINCE) ? 0 : 1)
                .thenComparingInt(e -> rank(e.getKey().tier()))
                .thenComparing(e -> -e.getValue().later().size())
                .thenComparing(e -> -e.getValue().history())
                .thenComparing(e -> e.getKey().id()));
        return flagged.subList(0, Math.min(n, flagged.size()));
    }

    /** The page. */
    static String render(Path root) {
        String tag = Upstream.tag(root);
        DateGate.Log log = DateGate.read(root, tag);
        DateGate.Stats s = log.stats();
        DateGate.Names names = DateGate.Names.of(root, tag);
        DateGate.Table table = DateGate.readTable(Lore.read(root.resolve(DateGate.TABLE)));
        DateGate.Gate gate = DateGate.Gate.of(root, tag);
        List<DateGate.Claim> claims = DateGate.claims(root.resolve(Lore.DIR));

        // What a heading names through the vocabulary: by its title alone, and by title and body.
        List<Pattern> patterns = new ArrayList<>();
        for (String name : names.all()) {
            patterns.add(DateGate.word(name));
        }
        Set<Integer> titleEntries = new TreeSet<>();
        Set<Integer> bodyEntries = new TreeSet<>();
        int titleHeadings = 0;
        int bodyHeadings = 0;
        for (DateGate.Change c : log.changes()) {
            String title = c.heading().toLowerCase(java.util.Locale.ROOT);
            boolean inTitle = patterns.stream().anyMatch(p -> p.matcher(title).find());
            boolean inBody = inTitle || patterns.stream().anyMatch(p -> p.matcher(c.text()).find());
            if (inTitle) {
                titleHeadings++;
                titleEntries.add(c.entry());
            }
            if (inBody) {
                bodyHeadings++;
                bodyEntries.add(c.entry());
            }
        }
        List<DateGate.Release> undated = log.releases().stream().filter(r -> !r.dated()).toList();
        int unreleased = (int) log.changes().stream().filter(c -> c.release() == null).map(DateGate.Change::entry).distinct().count();

        Map<DateGate.Claim, DateGate.Verdict> verdicts = new LinkedHashMap<>();
        for (DateGate.Claim c : claims) {
            verdicts.put(c, gate.check(c));
        }
        Map<String, Integer> counts = counts(List.copyOf(verdicts.values()));
        int exceeding = 0;
        for (Map.Entry<DateGate.Claim, DateGate.Verdict> e : verdicts.entrySet()) {
            if (DateGate.Gate.exceeds(e.getKey(), e.getValue())) {
                exceeding++;
            }
        }
        int anchored = (int) verdicts.values().stream().filter(v -> v.anchor() != null).count();
        int withHistory = (int) verdicts.values().stream().filter(v -> v.history() > 0).count();
        int capped = (int) verdicts.values().stream().filter(v -> v.flagged() && !v.codeCited()).count();

        StringBuilder out = new StringBuilder();
        out.append("# Date gating from the changelog\n\n");
        out.append("<!-- Generated by `./gradlew :codex:lore --args=gate`. Do not edit; DateGateReportTest fails when this\n")
                .append("     page and a fresh run disagree. -->\n\n");
        out.append("Story 7.9 ([ADR-0021](../adr/0021-lore-claim-format.md), amendment). `DateGate` checks a claim's date and the\n")
                .append("version it names against when the thing it describes changed, using `codex/").append(tag)
                .append("/changelog.json` and the release table `lore/release-dates.json`. It flags and never changes a Tier: a Tier\n")
                .append("changes by a person or by a citation.\n\n");

        out.append("## The finding\n\n");
        out.append("The first deliverable of the story is what the changelog can and cannot say.\n\n");
        out.append("| What | Count |\n|---|---:|\n");
        row(out, "Entries in `changelog.json`, in " + s.classes() + " changelist classes", s.entries());
        row(out, "Entries that carry a calendar date in their own text", s.entryDates());
        row(out, "Headings", s.headings());
        row(out, "Headings that state a date (the game's \"Released ...\" line), in " + s.entriesWithDatedHeading() + " entries", s.datedHeadings());
        row(out, "Entries under a class whose name gives a version (`v0_1_X` to `v4_X`)", s.versionClassEntries());
        row(out, "Entries under `Pixel_Dungeon_Changes` (the vanilla history: dated, no version)", s.entries() - s.versionClassEntries());
        row(out, "Entries that belong to a release the changelog labels (`v3.3`, `v0.4.3`)", s.labelledEntries());
        row(out, "Entries that belong to no release (`Coming Soon`, the vanilla history, the `v0.x.X` banners)", unreleased);
        row(out, "Releases the changelog labels", s.releases());
        row(out, "Releases with a date, and so a row in the table", s.datedReleases());
        row(out, "Headings whose title names an item or mob of the vocabulary, in " + titleEntries.size() + " entries", titleHeadings);
        row(out, "Headings whose title or text names an item or mob of the vocabulary, in " + bodyEntries.size() + " entries", bodyHeadings);
        out.append("\nThe game never dates an entry in its own text: every date it states is in a heading, and what it dates is a\n")
                .append("release. So the gate works by release first. A mechanic has no name in the vocabulary (ADR-0022: the vanilla\n")
                .append("tree has no mechanics table to diff against), so a claim about a mechanic cannot be tied to a heading and\n")
                .append("is gated on its version alone; all 144 claims here are about mobs and items.\n\n");
        out.append("A subject is tied to a heading by word, in the heading's title or the text it shows, using the subject, the\n")
                .append("vocabulary's names for the subject's class and the class's own name. The text of a heading is read from\n")
                .append("the changelist file the heading cites, so a heading titled \"Buffs\" that says \"Goo's pump-up\" counts.\n")
                .append("A word shared with ordinary prose (\"gold\", \"dart\") matches there too; a false tie costs a flag a person\n")
                .append("reads, and a missed one costs a stale claim passing, which is why the match errs toward the first.\n\n");

        out.append("## The release table\n\n");
        out.append("`lore/release-dates.json` has one row per dated release, oldest first: the version, the date in ISO form, the\n")
                .append("date as the game words it, and the `path:line` of the changelist line that states it at `")
                .append(table.tag()).append("`. The game's own changelog is the release notes, kept in the repository, and no date is\n")
                .append("typed from memory: `ReleaseDatesTest` derives the rows from the changelog and fails when the file and the\n")
                .append("derivation differ, resolves every source, and rejects a row with none.\n\n");
        out.append(table.rows().size()).append(" rows, ").append(table.rows().get(0).version()).append(" (")
                .append(table.rows().get(0).released()).append(") to ").append(table.rows().get(table.rows().size() - 1).version())
                .append(" (").append(table.rows().get(table.rows().size() - 1).released()).append("). ");
        out.append("Labelled in the changelog and not in the table because the game gives them no date: ");
        for (int i = 0; i < undated.size(); i++) {
            out.append(i > 0 ? ", " : "").append('`').append(undated.get(i).label()).append('`');
        }
        out.append(". A claim dated after `").append(table.rows().get(table.rows().size() - 1).version())
                .append("` is therefore flagged for a change in either, since neither can be shown to precede it.\n\n");

        out.append("## Does the gate flag nothing, or everything\n\n");
        out.append("The 144 claims carry almost no version, so their own verdicts cannot show it. The same gate, run with every\n")
                .append("claim given the same anchor, can. The expectations were written in the story file before the first run.\n\n");
        out.append("| Every claim anchored at | changed-since | clean | no-entry |\n|---|---:|---:|---:|\n");
        for (String probe : PROBES) {
            Map<String, Integer> p = probe(gate, claims, probe);
            out.append("| ").append(probe).append(" | ").append(p.get(DateGate.CHANGED_SINCE)).append(" | ").append(p.get(DateGate.CLEAN))
                    .append(" | ").append(p.get(DateGate.NO_ENTRY)).append(" |\n");
        }
        out.append('\n');

        out.append("## The ").append(claims.size()).append(" claims under `lore/`\n\n");
        out.append("| Verdict | Claims |\n|---|---:|\n");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.append("| `").append(e.getKey()).append("` | ").append(e.getValue()).append(" |\n");
        }
        out.append("\nFlagged: ").append(counts.get(DateGate.UNDATED) + counts.get(DateGate.CHANGED_SINCE)).append(" (`undated` ")
                .append(counts.get(DateGate.UNDATED)).append(", `changed-since` ").append(counts.get(DateGate.CHANGED_SINCE))
                .append("). Unflagged: ").append(counts.get(DateGate.CLEAN) + counts.get(DateGate.NO_ENTRY))
                .append(" (`clean` ").append(counts.get(DateGate.CLEAN)).append(", `no-entry` ").append(counts.get(DateGate.NO_ENTRY))
                .append(").\n\n");
        out.append(anchored).append(" claims carry a version or a date the gate can use; every other claim names no version, or ")
                .append("only an unusable one (\"not stated\", \"old wiki page\", \"unspecified\"), and has no date. ")
                .append(withHistory).append(" of ").append(claims.size()).append(" have at least one heading that names their subject, ")
                .append("so the changelog could say something about them if they were dated.\n\n");
        out.append("A flagged claim with no code citation is capped at Tier 3 until a person or a citation says otherwise: ")
                .append(capped).append(" claims here are in that case, and ").append(exceeding)
                .append(" of them carry a Tier above 3 (that is the number the gate exists to hold at zero, and what holds it is ")
                .append("that LoreCheck already refuses a Tier 1 or 2 with no code citation).\n\n");

        out.append("### The five to read first\n\n");
        out.append("Ranked: a claim with a known later change first, then by Tier (a claim the Brain could lean on before one it\n")
                .append("could not), then by how many later headings name its subject, then by how many headings name it at all. For an\n")
                .append("undated claim the last count is exposure, not proof: the headings may all predate the claim.\n\n");
        out.append("| Claim | Tier | Verdict | Subject | Headings naming it | Newest | Claim |\n|---|---|---|---|---:|---|---|\n");
        for (Map.Entry<DateGate.Claim, DateGate.Verdict> e : consequential(verdicts, 5)) {
            DateGate.Claim c = e.getKey();
            DateGate.Verdict v = e.getValue();
            DateGate.Change newest = newest(gate, c);
            out.append("| `").append(c.id()).append("` | ").append(c.tier()).append(" | `").append(v.status()).append("` | ")
                    .append(cell(c.subject())).append(" | ").append(v.history()).append(" | ")
                    .append(newest == null ? "none" : "`" + newest.citation() + "` (" + newest.release().label() + ", "
                            + cell(newest.heading()) + ")")
                    .append(" | ").append(cell(excerpt(c.text()))).append(" |\n");
        }
        out.append("\n## What this does not show\n\n");
        out.append("- A flag says a claim could not be shown current by its date, not that it is wrong. Some claims are already\n")
                .append("  `needs-review` for a different reason (a cited line moved), and the two sets are not the same.\n");
        out.append("- The tie between a subject and a heading is a word match. It cannot tell a heading that changes the subject\n")
                .append("  from one that only mentions it.\n");
        out.append("- A forum or wiki claim states its date or version rarely; the gate cannot invent one, and the retrieval date is only\n")
                .append("  an upper bound on when a claim was written, so it is not used.\n");
        out.append("- Vanilla Pixel Dungeon claims are not gated: the vanilla history in the changelog has dates and no versions.\n");
        return out.toString().stripTrailing() + "\n";
    }

    /** The newest change that names a claim's subject, or null. */
    private static DateGate.Change newest(DateGate.Gate gate, DateGate.Claim claim) {
        DateGate.Verdict v = gate.check(new DateGate.Claim(claim.id(), claim.subject(), claim.subjectClass(), "unknown", "v0.0.1",
                claim.tier(), claim.cites(), claim.text()));
        return v.later().isEmpty() ? null : v.later().get(v.later().size() - 1);
    }

    private static void row(StringBuilder out, String what, int count) {
        out.append("| ").append(what).append(" | ").append(count).append(" |\n");
    }

    private static String excerpt(String text) {
        String t = text.replaceAll("[“”]", "\"").strip();
        return t.length() <= 110 ? t : t.substring(0, 109).stripTrailing() + "…";
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("<", "&lt;");
    }
}
