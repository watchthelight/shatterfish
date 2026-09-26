package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The bestiary's api record ({@link Codex.Tactics}) and how {@link Codex.Knowledge} resolves a name and a depth. */
class TacticsTest {

    private static final Codex.Manifest MANIFEST = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json"));

    private static Codex.Tactics tactics(String className, String name, Alignment alignment, List<Integer> depths, String speed) {
        return new Codex.Tactics(className, name, alignment, depths, speed, "melee", 1, false, false, List.of(), List.of(),
                "sleeping", false, false, false, "close", false, false, List.of("stairs"));
    }

    @Test
    @DisplayName("a tag outside the closed vocabulary is refused, and the depths are sorted")
    void closed_vocabulary() {
        assertThrows(IllegalArgumentException.class, () -> tactics("a.Crab", "crab", Alignment.ENEMY, List.of(), "quick"));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tactics("a.Crab", "crab", Alignment.ENEMY, List.of(),
                "fast", "claws", 1, false, false, List.of(), List.of(), "sleeping", false, false, false, "close", false,
                false, List.of("stairs")));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tactics("a.Crab", "crab", Alignment.ENEMY, List.of(),
                "fast", "melee", 1, false, false, List.of(), List.of(), "dozing", false, false, false, "close", false,
                false, List.of("stairs")));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tactics("a.Crab", "crab", Alignment.ENEMY, List.of(),
                "fast", "melee", 1, false, false, List.of(), List.of(), "sleeping", false, false, false, "charge", false,
                false, List.of("stairs")));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tactics("a.Crab", "crab", Alignment.ENEMY, List.of(),
                "fast", "melee", 1, false, false, List.of(), List.of(), "sleeping", false, false, false, "close", false,
                false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Tactics("a.Crab", "crab", Alignment.ENEMY, List.of(),
                "fast", "melee", 1, false, false, List.of(), List.of(), "sleeping", false, false, false, "close", false,
                false, List.of("teleport")));
        assertThrows(IllegalArgumentException.class, () -> tactics("a.Crab", "crab", null, List.of(), "fast"));
        assertThrows(IllegalArgumentException.class, () -> tactics("a.Crab", "crab", Alignment.ENEMY, List.of(0), "fast"));
        assertThrows(IllegalArgumentException.class, () -> tactics("a.Crab", "crab", Alignment.ENEMY, List.of(3, 3), "fast"));
        assertEquals(List.of(3, 4), tactics("a.Crab", "crab", Alignment.ENEMY, List.of(4, 3), "fast").depths());
    }

    @Test
    @DisplayName("the old constructors carry no bestiary, and a class is listed once")
    void old_shapes_and_distinct_classes() {
        Codex.Knowledge plain = new Codex.Knowledge(MANIFEST, List.of(), List.of(), List.of());
        assertEquals(List.of(), plain.bestiary());
        assertEquals(List.of(), new Codex.Knowledge(MANIFEST, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of()).bestiary());
        assertNull(plain.tactics("sewer crab", 3));
        Codex.Tactics crab = tactics("a.Crab", "sewer crab", Alignment.ENEMY, List.of(3, 4), "fast");
        assertThrows(IllegalArgumentException.class, () -> plain.withBestiary(List.of(crab, crab)));
        assertEquals(crab, plain.withBestiary(List.of(crab)).tactics("sewer crab", 9), "one class answers on any depth");
    }

    @Test
    @DisplayName("a shared name resolves to the class the rotation places on the depth, then one it places nowhere, then by class name")
    void resolution_rule() {
        Codex.Tactics city = tactics("a.Golem", "golem", Alignment.ENEMY, List.of(18, 19), "normal");
        Codex.Tactics king = tactics("a.YogKing.KGolem", "golem", Alignment.ENEMY, List.of(), "normal");
        Codex.Tactics vault = tactics("a.Vault.VGolem", "golem", Alignment.ENEMY, List.of(), "normal");
        Codex.Tactics early = tactics("a.AGolem", "golem", Alignment.ENEMY, List.of(2), "normal");
        Codex.Knowledge knowledge = new Codex.Knowledge(MANIFEST, List.of(), List.of(), List.of())
                .withBestiary(List.of(king, city, early, vault));
        assertEquals(city, knowledge.tactics("golem", 18), "placed there, though another sorts first");
        assertEquals(early, knowledge.tactics("golem", 2));
        assertEquals(vault, knowledge.tactics("golem", 20),
                "placed nowhere before placed elsewhere, and of two placed nowhere the first by class name");
        assertNull(knowledge.tactics("Golem", 18), "names match exactly");
    }
}
