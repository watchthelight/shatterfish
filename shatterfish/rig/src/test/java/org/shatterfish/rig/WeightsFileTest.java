package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Weights;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed weight set and its reader (story 4.5): read by the caller, refused unless it is the
 * set for the Brain in this build's shape and canonical spelling, and part of the Brain's
 * configuration hash.
 */
class WeightsFileTest {

    private static Weights committed() {
        return WeightsFile.read(WeightsFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH);
    }

    @Test
    @DisplayName("the committed weights read, and the Brain is built on them")
    void the_committed_set() {
        Weights weights = committed();
        assertEquals(Brains.SHATTERFISH, weights.name());
        assertEquals(1, weights.version());
        assertEquals(1, weights.weight("hp"));
        assertEquals(List.of("act_attack", "act_descend", "act_rest_hurt", "act_search", "act_wait", "depth",
                "enemies", "hp", "hunger", "level", "strength"), weights.features());
        assertTrue(Brains.readsWeights(Brains.SHATTERFISH));
        assertTrue(!Brains.readsWeights(Brains.RANDOM));
    }

    @Test
    @DisplayName("two weight sets are two configurations, and the random agents have none to state")
    void configured_by_the_weights() {
        Weights weights = committed();
        String hash = Brains.configHash(SeedSetsTest.ROOT, Brains.SHATTERFISH);
        assertEquals(hash, Brains.configHash(Brains.SHATTERFISH, weights));
        List<Weights.Term> terms = new ArrayList<>(weights.terms());
        terms.set(0, new Weights.Term(terms.get(0).feature(), terms.get(0).weight() + 1));
        assertNotEquals(hash, Brains.configHash(Brains.SHATTERFISH, new Weights(Brains.SHATTERFISH, 1, terms)),
                "a changed weight is a changed Brain");
        assertNotEquals(hash, Brains.configHash(Brains.SHATTERFISH, new Weights(Brains.SHATTERFISH, 2, weights.terms())),
                "and so is a changed version");
        assertThrows(IllegalStateException.class, () -> Brains.configHash(Brains.SHATTERFISH),
                "a Brain configured by its weights is not described without them");
        assertEquals("0".repeat(64), Brains.configHash(SeedSetsTest.ROOT, Brains.RANDOM));
    }

    @Test
    @DisplayName("a file for another Brain, in another format or spelled otherwise, is refused")
    void refusals(@TempDir Path dir) throws IOException {
        String canonical = committed().canonical();
        assertEquals(canonical, Files.readString(WeightsFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH),
                StandardCharsets.UTF_8).strip(), "the committed file is its canonical form");

        Path file = dir.resolve("w.json");
        Files.writeString(file, canonical.replace("\"name\":\"shatterfish\"", "\"name\":\"other\""));
        assertThrows(IllegalArgumentException.class, () -> WeightsFile.read(file, Brains.SHATTERFISH));

        Files.writeString(file, canonical.replace("\"format\":1", "\"format\":2"));
        assertThrows(IllegalArgumentException.class, () -> WeightsFile.read(file, Brains.SHATTERFISH));

        Files.writeString(file, canonical.replace("{\"format\"", "{ \"format\""));
        assertThrows(IllegalArgumentException.class, () -> WeightsFile.read(file, Brains.SHATTERFISH));

        Files.writeString(file, canonical + "\n");
        assertEquals(committed(), WeightsFile.read(file, Brains.SHATTERFISH), "a trailing newline is not a spelling");
    }
}
