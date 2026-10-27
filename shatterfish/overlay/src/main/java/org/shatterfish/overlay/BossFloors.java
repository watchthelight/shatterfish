package org.shatterfish.overlay;

import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * The boss floors as the Codex names them (story 8.3, FR-45): the {@code bossDepths} of the committed
 * {@code guarantees.json}, which cites {@code Dungeon.java:441}. Read once when the Run is attached; a
 * missing file stops the launch, as the Brain's Codex does.
 */
final class BossFloors {

    private BossFloors() {
    }

    /** The boss depths of the Codex in {@code folder}. */
    static Set<Integer> read(Path folder) {
        Path file = folder.resolve("guarantees.json");
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the boss floors are read from " + file + ", which could not be read", e);
        }
        Set<Integer> depths = new HashSet<>();
        for (String depth : Json.array(Json.required(Json.object(CodexKnowledge.compact(text)), "bossDepths", "guarantees"))) {
            depths.add(Json.integer(depth));
        }
        if (depths.isEmpty()) {
            throw new IllegalArgumentException("the Codex in " + folder + " names no boss depth");
        }
        return Set.copyOf(depths);
    }
}
