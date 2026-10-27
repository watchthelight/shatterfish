package org.shatterfish.codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The fixture and the Results page of story 7.8: the classifier run over the labelled fixture
 * (precision and recall per variant) and over the 144 claims under {@code lore/} (how many it
 * decides, how many it leaves unknown, and where it disagrees with the variant a claim carries).
 * It reads and never writes a claim; a disagreement is listed, not applied.
 *
 * <p>{@code ./gradlew :codex:lore --args=variants} writes {@link #PAGE}; {@code VariantReportTest}
 * fails when the committed page and a fresh run differ.
 */
final class VariantReport {

    private VariantReport() {
    }

    /** The Results page, relative to the repository root. */
    static final String PAGE = "docs/results/2026-10-26-7-8-the-variant-classifier.md";

    /** The fixture, relative to the repository root. */
    static final String FIXTURE = "shatterfish/codex/src/test/resources/variant-fixture.txt";

    /** The thresholds the story file states before the first run. */
    static final double MIN_PRECISION = 0.95;
    static final double MIN_RECALL = 0.90;

    /** The four classes, in the order the page shows them. {@code mod} stands for every {@code mod:<name>}. */
    static final List<String> CLASSES = List.of("spd", "pd", "mod", "unknown");

    /** One labelled claim of the fixture. */
    record Case(String label, String source, List<String> subjects, String text, String why) {
    }

    /** The class of a variant: every mod is {@code mod}. */
    static String kind(String variant) {
        return variant.startsWith("mod:") ? "mod" : variant;
    }

    /** The fixture's cases. */
    static List<Case> fixture(String text) {
        List<Case> cases = new ArrayList<>();
        Map<String, String> block = new LinkedHashMap<>();
        for (String line : (text + "\n\n").split("\n")) {
            if (line.startsWith("#")) {
                continue;
            }
            if (line.isBlank()) {
                if (!block.isEmpty()) {
                    List<String> subjects = new ArrayList<>();
                    for (String s : block.getOrDefault("subjects", "").split(",")) {
                        if (!s.isBlank()) {
                            subjects.add(s.strip());
                        }
                    }
                    cases.add(new Case(block.get("label"), block.get("source"), subjects, block.get("text"), block.get("why")));
                    block.clear();
                }
                continue;
            }
            int colon = line.indexOf(':');
            block.put(line.substring(0, colon), line.substring(colon + 1).strip());
        }
        return cases;
    }

    /** Counts of (label, predicted) over the fixture. */
    record Scores(Map<String, Map<String, Integer>> matrix, List<String> wrong, int misnamed) {

        int right(String c) {
            return count(c, c) - (c.equals("mod") ? misnamed : 0);
        }

        int count(String label, String predicted) {
            return matrix.getOrDefault(label, Map.of()).getOrDefault(predicted, 0);
        }

        int labelled(String label) {
            return matrix.getOrDefault(label, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
        }

        int predicted(String predicted) {
            int n = 0;
            for (String label : CLASSES) {
                n += count(label, predicted);
            }
            return n;
        }

        /** Of what was called {@code c}, the share that is {@code c}; 1 when nothing was called {@code c}. */
        double precision(String c) {
            return predicted(c) == 0 ? 1.0 : (double) right(c) / predicted(c);
        }

        /** Of what is {@code c}, the share that was called {@code c}; 1 when nothing is {@code c}. */
        double recall(String c) {
            return labelled(c) == 0 ? 1.0 : (double) right(c) / labelled(c);
        }
    }

    /** Scores the classifier on the cases. A mod counts as right when the name matches the label's name. */
    static Scores score(VariantClassifier classifier, List<Case> cases) {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        List<String> wrong = new ArrayList<>();
        int misnamed = 0;
        for (Case c : cases) {
            VariantClassifier.Result result = classifier.classify(c.text(), c.subjects(), c.source());
            String label = kind(c.label());
            String predicted = kind(result.variant());
            if (label.equals("mod") && predicted.equals("mod") && !c.label().equals(result.variant())) {
                misnamed++;
            }
            matrix.computeIfAbsent(label, k -> new TreeMap<>()).merge(predicted, 1, Integer::sum);
            if (!c.label().equals(result.variant())) {
                wrong.add(c.label() + " expected, " + result.variant() + " returned: " + c.text() + " | " + result.why());
            }
        }
        return new Scores(matrix, wrong, misnamed);
    }

    /** One of the 144 claims, classified. */
    record Classified(String id, String carried, VariantClassifier.Result result) {
    }

    /** Classifies every claim under {@code loreDir} from its subject (and its class when the subject is unlisted). */
    static List<Classified> claims(VariantClassifier classifier, Path loreDir) {
        List<Classified> out = new ArrayList<>();
        for (Path file : Lore.files(loreDir)) {
            if (!LoreCheck.isClaimPath(loreDir, file)) {
                continue;
            }
            Lore.Doc doc;
            try {
                doc = Lore.parse(file.getFileName().toString(), Lore.read(file));
            } catch (Lore.Unreadable bad) {
                throw new IllegalStateException(file + ": " + bad.getMessage());
            }
            List<String> subjects = new ArrayList<>();
            String subject = doc.get("subject");
            String subjectClass = doc.get("subject_class");
            if (subject != null) {
                subjects.add(subject);
            }
            if (subjectClass != null && (subject == null || !classifier.knows(subject))) {
                subjects.add(subjectClass);
            }
            String text = doc.get("claim") + (doc.get("quote") == null ? "" : " " + doc.get("quote"));
            out.add(new Classified(doc.get("id"), doc.get("variant"), classifier.classify(text, subjects, doc.get("source_type"))));
        }
        out.sort(Comparator.comparing((Classified c) -> c.id().replaceAll("-\\d+$", "")).thenComparingInt(c -> Lore.number(c.id())));
        return out;
    }

    /** The page. */
    static String render(Path root) {
        VariantClassifier classifier = VariantClassifier.of(root, Upstream.tag(root));
        List<Case> cases = fixture(Lore.read(root.resolve(FIXTURE)));
        Scores scores = score(classifier, cases);
        List<Classified> claims = claims(classifier, root.resolve(Lore.DIR));

        StringBuilder out = new StringBuilder();
        out.append("# The variant classifier\n\n");
        out.append("<!-- Generated by `./gradlew :codex:lore --args=variants`. Do not edit; VariantReportTest fails when this\n")
                .append("     page and a fresh run disagree. -->\n\n");
        out.append("Story 7.8 ([ADR-0022](../adr/0022-variant-classifier.md)). `VariantClassifier` reads\n")
                .append("`codex/").append(classifier.tag).append("/vocabulary.json` and the text of a claim and says `spd`, `pd`,\n")
                .append("`mod:<name>` or `unknown`, with the evidence. It never guesses: no evidence, or evidence that points\n")
                .append("both ways, is `unknown`. Nothing below changed a claim file; a disagreement is listed and a variant a\n")
                .append("person set stays.\n\n");

        out.append("## The labelled fixture\n\n");
        out.append(cases.size()).append(" claims whose variant is fixed by the source (`").append(FIXTURE).append("`). The thresholds were\n")
                .append("written in the story file before the first run: precision at least ").append(MIN_PRECISION)
                .append(" and recall at least ").append(MIN_RECALL).append(" for every class.\n\n");
        out.append("| Class | In fixture | Called | Right | Precision | Recall |\n|---|---:|---:|---:|---:|---:|\n");
        for (String c : CLASSES) {
            out.append("| ").append(c).append(" | ").append(scores.labelled(c)).append(" | ").append(scores.predicted(c))
                    .append(" | ").append(scores.right(c)).append(" | ").append(pct(scores.precision(c)))
                    .append(" | ").append(pct(scores.recall(c))).append(" |\n");
        }
        out.append("\nWhat the fixture cannot show: its cases either carry a signal or were written to carry none, so it\n")
                .append("guards the rules against regression and says nothing about forum text in the wild.\n\n");
        if (!scores.wrong().isEmpty()) {
            out.append("Misses:\n\n");
            for (String w : scores.wrong()) {
                out.append("- ").append(w.replace("|", "\\|")).append('\n');
            }
            out.append('\n');
        }

        out.append("## The ").append(claims.size()).append(" claims under `lore/`\n\n");
        Map<String, Integer> byResult = new TreeMap<>();
        Map<String, Integer> byCarried = new TreeMap<>();
        Map<String, Integer> pairs = new TreeMap<>();
        for (Classified c : claims) {
            byResult.merge(kind(c.result().variant()), 1, Integer::sum);
            byCarried.merge(kind(c.carried()), 1, Integer::sum);
            pairs.merge(kind(c.carried()) + " -> " + kind(c.result().variant()), 1, Integer::sum);
        }
        int decided = claims.size() - byResult.getOrDefault("unknown", 0);
        out.append("The classifier decides ").append(decided).append(" of ").append(claims.size()).append(" and leaves ")
                .append(byResult.getOrDefault("unknown", 0)).append(" `unknown`. Result: ").append(counts(byResult))
                .append(". Variants the claims carry (set by a person): ").append(counts(byCarried)).append(".\n\n");
        out.append("| Carried -> classified | Claims |\n|---|---:|\n");
        for (Map.Entry<String, Integer> e : pairs.entrySet()) {
            out.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        out.append('\n');

        List<Classified> disagreements = claims.stream()
                .filter(c -> !c.result().variant().equals("unknown") && !c.carried().equals("unknown")
                        && !c.result().variant().equals(c.carried())).toList();
        List<Classified> narrower = claims.stream()
                .filter(c -> !c.result().variant().equals("unknown") && c.carried().equals("unknown")).toList();
        List<Classified> carriedOnly = claims.stream()
                .filter(c -> c.result().variant().equals("unknown") && !c.carried().equals("unknown")).toList();
        out.append("### Disagreements\n\n");
        out.append("A claim the classifier decides one way and the claim carries the other (").append(disagreements.size())
                .append("). Not applied.\n\n");
        listing(out, disagreements);
        out.append("### Carried unknown, classifier decides\n\n");
        out.append(narrower.size()).append(" claims carry `unknown` and the table names their subject. Not applied: a variant a\n")
                .append("person set stays unless the file says otherwise, and these are listed for that person to decide.\n\n");
        listing(out, narrower);
        out.append("### Carried a variant, classifier says unknown\n\n");
        out.append(carriedOnly.size()).append(" claims carry `spd` or `pd` that the classifier cannot confirm from the text, the subjects\n")
                .append("and the source type: a shared name with no differing figure, or a variant the person took from the page the\n")
                .append("source URL names, which is not an input. They are not disagreements; the carried variant stands.\n\n");
        return out.toString().stripTrailing() + "\n";
    }

    private static void listing(StringBuilder out, List<Classified> rows) {
        if (rows.isEmpty()) {
            out.append("None.\n\n");
            return;
        }
        out.append("| Claim | Carries | Classifier | Evidence |\n|---|---|---|---|\n");
        for (Classified c : rows) {
            out.append("| `").append(c.id()).append("` | ").append(c.carried()).append(" | ").append(c.result().variant())
                    .append(" | ").append(c.result().why().replace("|", "\\|").replace("<", "&lt;")).append(" |\n");
        }
        out.append('\n');
    }

    private static String counts(Map<String, Integer> counts) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.append(out.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
        }
        return out.toString();
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
