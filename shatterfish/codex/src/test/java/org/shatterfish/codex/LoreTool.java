package org.shatterfish.codex;

import org.shatterfish.brain.Brain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code ./gradlew :codex:lore --args="check|docs|intake <folder>"} (story 7.7): the one command
 * line over the lore pipeline, in the Codex tooling and not in a module of its own (ADR-0003's
 * layout stands, ADR-0021).
 *
 * <ul>
 *   <li>{@code check} prints the checker's report and exits 1 on a finding.
 *   <li>{@code review} sets each claim's {@code status} to what its citations say, after a re-pin.
 *   <li>{@code docs} writes {@code docs/lore/index.md} from the files under {@code lore/}.
 *   <li>{@code variants} writes the Results page of the variant classifier (story 7.8, ADR-0022).
 *   <li>{@code dates} writes {@code lore/release-dates.json} from the changelog (story 7.9).
 *   <li>{@code gate} writes the Results page of the date gate (story 7.9, ADR-0021 amendment).
 *   <li>{@code intake <folder>} enters each draft in the folder or writes a rejection note beside
 *       it, and exits 1 if any draft was rejected. It never reaches the network.
 * </ul>
 */
public final class LoreTool {

    private LoreTool() {
    }

    /** The command that prints the report, named in messages. */
    static final String COMMAND = "./gradlew :codex:lore --args=check";

    /** The checkout's registry: the Brain's Policies, its Rules index and the weights files. */
    static Lore.Registry registry(Path root) {
        return Lore.Registry.of(root, Brain.policyNames());
    }

    /** Every finding over {@code root/lore}. */
    static List<LoreCheck.Finding> check(Path root) {
        return LoreCheck.check(root, root.resolve(Lore.DIR), registry(root));
    }

    /** How many claim files {@code root/lore} holds. */
    static int claims(Path root) {
        Path loreDir = root.resolve(Lore.DIR);
        return (int) Lore.files(loreDir).stream().filter(f -> LoreCheck.isClaimPath(loreDir, f)).count();
    }

    /**
     * Sets the {@code status} of every claim to what its citations say (see {@link
     * LoreCheck#expected}), and returns the ids it changed. The step of the upgrade procedure that
     * follows a re-pin: a verdict is never rewritten, only flagged or cleared.
     */
    static List<String> review(Path root, Path loreDir) {
        java.util.Map<Lore.Doc, Path> files = new java.util.LinkedHashMap<>();
        for (Path file : Lore.files(loreDir)) {
            if (!LoreCheck.isClaimPath(loreDir, file)) {
                continue;
            }
            try {
                files.put(Lore.parse(file.getFileName().toString(), Lore.read(file)), file);
            } catch (Lore.Unreadable bad) {
                throw new IllegalStateException(file + ": " + bad.getMessage() + "; run check first");
            }
        }
        List<String> changed = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Lore.Doc, LoreCheck.Expected> entry : LoreCheck.expected(root, List.copyOf(files.keySet())).entrySet()) {
            Lore.Doc doc = entry.getKey();
            if (entry.getValue().status().equals(doc.get("status"))) {
                continue;
            }
            java.util.Map<String, String> scalars = new java.util.LinkedHashMap<>(doc.scalars());
            scalars.put("status", entry.getValue().status());
            Lore.Doc now = new Lore.Doc(doc.name(), scalars, doc.lists(), doc.body());
            try {
                Files.writeString(files.get(doc), Lore.render(now), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(files.get(doc) + " could not be written", e);
            }
            changed.add(doc.get("id") + " -> " + entry.getValue().status());
        }
        return changed;
    }

    public static void main(String[] args) {
        String usage = "usage: LoreTool check | review | docs | variants | dates | gate | intake <folder>";
        if (args.length == 0) {
            throw new IllegalArgumentException(usage);
        }
        Path root = DocsCitations.root();
        switch (args[0]) {
            case "check" -> {
                List<LoreCheck.Finding> findings = check(root);
                System.out.println(LoreCheck.report(root.resolve(Lore.DIR), claims(root), findings));
                if (!findings.isEmpty()) {
                    System.exit(1);
                }
            }
            case "review" -> {
                List<String> changed = review(root, root.resolve(Lore.DIR));
                System.out.println(changed.size() + " claims changed status" + (changed.isEmpty() ? "" : ": " + String.join(", ", changed)));
            }
            case "docs" -> {
                Path page = root.resolve(LoreDocs.PAGE);
                try {
                    Files.createDirectories(page.getParent());
                    Files.writeString(page, LoreDocs.render(root.resolve(Lore.DIR)), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(page + " could not be written", e);
                }
                System.out.println("wrote " + LoreDocs.PAGE);
            }
            case "variants" -> {
                Path page = root.resolve(VariantReport.PAGE);
                try {
                    Files.createDirectories(page.getParent());
                    Files.writeString(page, VariantReport.render(root), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(page + " could not be written", e);
                }
                System.out.println("wrote " + VariantReport.PAGE);
            }
            case "dates" -> {
                Path table = root.resolve(DateGate.TABLE);
                String tag = Upstream.tag(root);
                try {
                    Files.writeString(table, DateGate.render(tag, DateGate.derive(DateGate.read(root, tag))), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(table + " could not be written", e);
                }
                System.out.println("wrote " + DateGate.TABLE);
            }
            case "gate" -> {
                Path page = root.resolve(DateGateReport.PAGE);
                try {
                    Files.createDirectories(page.getParent());
                    Files.writeString(page, DateGateReport.render(root), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(page + " could not be written", e);
                }
                System.out.println("wrote " + DateGateReport.PAGE);
            }
            case "intake" -> {
                if (args.length != 2) {
                    throw new IllegalArgumentException(usage);
                }
                boolean rejected = false;
                List<LoreIntake.Outcome> outcomes =
                        LoreIntake.run(root, root.resolve(Lore.DIR), Path.of(args[1]).toAbsolutePath(), registry(root));
                for (LoreIntake.Outcome outcome : outcomes) {
                    if (outcome.accepted()) {
                        System.out.println("entered " + outcome.id() + " from " + outcome.draft().getFileName());
                    } else {
                        rejected = true;
                        System.out.println("rejected " + outcome.draft().getFileName() + ": " + String.join("; ", outcome.reasons()));
                    }
                }
                System.out.println(outcomes.size() + " drafts read");
                if (rejected) {
                    System.exit(1);
                }
            }
            default -> throw new IllegalArgumentException(usage);
        }
    }
}
