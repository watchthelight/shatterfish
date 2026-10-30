package org.shatterfish.harness.knowledge;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tunables;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Brain's Playbook, read from disk for it (story 7.2).
 *
 * <p>The Brain may not open a file, so its caller reads the Playbook and hands it over as an
 * {@code api} value at construction, as it does the weights ({@code WeightsFile}). The file is
 * refused unless {@link PlaybookValidator} accepts it and it names the Brain it is for. Unlike the
 * weights it need not be written canonically, because a section it leaves out means the section's
 * reference values and a class entry states only what it changes; what the Rig hashes is the
 * {@linkplain Playbook#canonical() canonical text of the value}, in which every section is whole, so
 * the configuration hash is a function of the values and not of how somebody spaced or omitted them.
 * A test holds the committed files to the canonical spelling, so a reader sees all the values.
 */
public final class PlaybookFile {

    /** Where the Playbooks live, under the repository root: one file per Brain. */
    public static final String FOLDER = "playbooks";

    private PlaybookFile() {
    }

    /** The committed Playbook of the Brain named {@code brain}, under {@code root}. */
    public static Path of(Path root, String brain) {
        return root.resolve(FOLDER).resolve(brain + ".json");
    }

    /** The hero classes {@code hero-classes.json} lists, in the Codex folder {@code codex}. */
    public static Set<String> heroClasses(Path codex) {
        Path file = codex.resolve("hero-classes.json");
        try {
            Set<String> classes = new LinkedHashSet<>();
            for (String entry : Json.array(CodexKnowledge.compact(Files.readString(file, StandardCharsets.UTF_8)))) {
                classes.add(Json.string(Json.required(Json.object(entry), "heroClass", "hero class")));
            }
            return classes;
        } catch (IOException e) {
            throw new UncheckedIOException("the hero classes in " + file + " could not be read", e);
        }
    }

    /** The text of {@code file}, refusing a byte-order mark as the weights do. */
    public static String text(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("the Playbook in " + file + " could not be read", e);
        }
        if (text.startsWith("﻿")) {
            throw new IllegalArgumentException("the Playbook in " + file + " begins with a byte-order mark;"
                    + " save the file as UTF-8 without one");
        }
        return text;
    }

    /**
     * The Playbook in {@code file}, which must be valid and the one for {@code brain}.
     *
     * @param codex the Codex folder whose {@code hero-classes.json} says which classes exist
     */
    public static Playbook read(Path file, String brain, Path codex) {
        String text = text(file);
        List<String> problems = PlaybookValidator.problems(text, heroClasses(codex));
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("the Playbook in " + file + " is not valid:\n  "
                    + String.join("\n  ", problems));
        }
        Map<String, String> held = Json.object(text);
        Playbook.Identify identify = Playbook.Identify.REFERENCE;
        Playbook.Upgrade upgrade = Playbook.Upgrade.REFERENCE;
        Playbook.Kit kit = Playbook.Kit.REFERENCE;
        if (held.containsKey("kit")) {
            kit = kit.with(fields(held.get("kit"), "kit"));
        }
        if (held.containsKey("identify")) {
            identify = identify.with(fields(held.get("identify"), "identify"));
        }
        if (held.containsKey("upgrade")) {
            upgrade = upgrade.with(fields(held.get("upgrade"), "upgrade"));
        }
        Playbook.Bosses bosses = Playbook.Bosses.REFERENCE;
        if (held.containsKey("bosses")) {
            Map<String, String> entries = Json.object(held.get("bosses"));
            Playbook.Goo goo = entries.containsKey("goo")
                    ? bosses.goo().with(fields(entries.get("goo"), Playbook.Goo.SPECS)) : bosses.goo();
            Playbook.Tengu tengu = entries.containsKey("tengu")
                    ? bosses.tengu().with(fields(entries.get("tengu"), Playbook.Tengu.SPECS)) : bosses.tengu();
            Playbook.Later dm300 = entries.containsKey("dm300")
                    ? bosses.dm300().with(fields(entries.get("dm300"), Playbook.Later.SPECS)) : bosses.dm300();
            bosses = new Playbook.Bosses(goo, tengu, dm300);
        }
        List<Playbook.ClassEntry> classes = new ArrayList<>();
        if (held.containsKey("classes")) {
            for (Map.Entry<String, String> entry : Json.object(held.get("classes")).entrySet()) {
                Map<String, String> sections = Json.object(entry.getValue());
                Playbook.Identify classIdentify = sections.containsKey("identify")
                        ? identify.with(fields(sections.get("identify"), "identify")) : identify;
                Playbook.Upgrade classUpgrade = sections.containsKey("upgrade")
                        ? upgrade.with(fields(sections.get("upgrade"), "upgrade")) : upgrade;
                Playbook.Kit classKit = sections.containsKey("kit")
                        ? kit.with(fields(sections.get("kit"), "kit")) : kit;
                classes.add(new Playbook.ClassEntry(HeroClass.valueOf(entry.getKey()), classIdentify, classKit, classUpgrade));
            }
        }
        Map<String, Long> stated = new LinkedHashMap<>();
        if (held.containsKey("tunables")) {
            for (Map.Entry<String, String> entry : Json.object(held.get("tunables")).entrySet()) {
                stated.put(entry.getKey(), Json.number(entry.getValue()));
            }
        }
        Playbook playbook = new Playbook(Json.string(Json.required(held, "name", "Playbook")),
                Json.integer(Json.required(held, "version", "Playbook")), identify, upgrade, kit, classes, bosses,
                new Tunables.Vector(stated));
        if (!playbook.name().equals(brain)) {
            throw new IllegalArgumentException("the Playbook in " + file + " is for " + playbook.name()
                    + ", not for " + brain);
        }
        return playbook;
    }

    /** One section's stated fields, typed by the format's declarations. */
    private static Map<String, Object> fields(String raw, String section) {
        return fields(raw, Playbook.specs(section));
    }

    private static Map<String, Object> fields(String raw, List<Playbook.Spec> specs) {
        Map<String, Object> given = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : Json.object(raw).entrySet()) {
            boolean choice = specs.stream()
                    .anyMatch(spec -> spec.key().equals(field.getKey()) && spec.isChoice());
            given.put(field.getKey(), choice ? (Object) Json.string(field.getValue()) : (Object) Json.number(field.getValue()));
        }
        return given;
    }
}
