package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Observation's hash is SHA-256 over the schema version and the section hashes (ADR-0005,
 * option 9), and the encoding of the corpus is pinned so that a change to any of it is a change
 * to the schema version.
 */
class ObservationHashTest {

    /**
     * The corpus Observation's hash under schema version 2. A different value here means the
     * encoding changed: bump {@link ObservationCodec#SCHEMA_VERSION}, record why in ADR-0005, and
     * only then repin.
     */
    private static final String PINNED = "da5e41b1b8b9bbb6f9b4cdef6925c1b46174e9639d4ff5eb9172418dc8b75d51";

    @Test
    @DisplayName("the hash is SHA-256 over the version and the nine section hashes, in order")
    void the_hash_is_over_the_version_and_the_section_hashes() throws Exception {
        Observation observation = Corpus.observation();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        ByteBuffer input = ByteBuffer.allocate(4 + ObservationCodec.SECTIONS.size() * 32);
        input.putInt(ObservationCodec.SCHEMA_VERSION);
        for (String section : ObservationCodec.SECTIONS) {
            input.put(sha.digest(ObservationCodec.encodeSection(observation, section)));
        }
        String expected = HexFormat.of().formatHex(sha.digest(input.array()));

        assertEquals(expected, observation.hash());
        assertEquals(ObservationCodec.SECTIONS, new ArrayList<>(observation.sectionHashes().keySet()));
        Map<String, String> hashes = observation.sectionHashes();
        for (String section : ObservationCodec.SECTIONS) {
            assertEquals(HexFormat.of().formatHex(sha.digest(ObservationCodec.encodeSection(observation, section))),
                    hashes.get(section), section);
        }
        assertThrows(IllegalArgumentException.class, () -> ObservationCodec.encodeSection(observation, "belief"));
    }

    @Test
    @DisplayName("the whole encoding is the version followed by the sections")
    void the_encoding_is_the_version_then_the_sections() {
        Observation observation = Corpus.observation();
        ByteBuffer expected = ByteBuffer.allocate(1 << 16);
        expected.putInt(ObservationCodec.SCHEMA_VERSION);
        for (String section : ObservationCodec.SECTIONS) {
            expected.put(ObservationCodec.encodeSection(observation, section));
        }
        byte[] bytes = new byte[expected.position()];
        expected.flip();
        expected.get(bytes);
        assertEquals(HexFormat.of().formatHex(bytes), HexFormat.of().formatHex(ObservationCodec.encode(observation)));
    }

    @Test
    @DisplayName("the encoding of the corpus is pinned to the schema version")
    void the_encoding_is_pinned() {
        assertEquals(PINNED, Corpus.observation().hash(),
                "the encoding changed under schema version " + ObservationCodec.SCHEMA_VERSION
                        + "; bump the version and record why in ADR-0005 before repinning");
    }

    @Test
    @DisplayName("an Observation of another schema version is refused by this codec")
    void another_version_is_refused() {
        HeaderSection h = Corpus.header();
        Observation other = Corpus.with(Corpus.observation(), new HeaderSection(h.version() + 1, h.upstreamTag(),
                h.codexVersion(), h.heroClass(), h.challenges(), h.depth(), h.branch(), h.sealed(), h.oracle(), h.prompt()));
        assertThrows(IllegalArgumentException.class, other::hash);
        assertThrows(IllegalArgumentException.class, () -> ObservationCodec.encode(other));
        assertThrows(IllegalArgumentException.class, other::json);
    }

    @Test
    @DisplayName("health is quantised as the bar draws it, in integers")
    void health_pips() {
        assertEquals(0, ObservationCodec.healthPips(0, 20));
        assertEquals(1, ObservationCodec.healthPips(1, 20));
        assertEquals(6, ObservationCodec.healthPips(10, 20));
        assertEquals(11, ObservationCodec.healthPips(19, 20));
        assertEquals(11, ObservationCodec.healthPips(20, 20));
        assertEquals(ObservationCodec.MAX_HEALTH_PIPS, ObservationCodec.healthPips(1_000_000, 1_000_000));
        for (int max = 1; max <= 200; max++) {
            for (int hp = 0; hp <= max; hp++) {
                int pips = ObservationCodec.healthPips(hp, max);
                assertTrue(pips >= 0 && pips <= ObservationCodec.MAX_HEALTH_PIPS, hp + "/" + max + " -> " + pips);
                assertEquals((int) Math.ceil(hp * 32.0 / (3.0 * max)), pips, hp + "/" + max);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ObservationCodec.healthPips(21, 20));
        assertThrows(IllegalArgumentException.class, () -> ObservationCodec.healthPips(0, 0));
    }

    @Test
    @DisplayName("the sections are the nine of ADR-0005, in the decision's order")
    void the_sections() {
        assertEquals(List.of("header", "map", "actors", "hero", "inventory", "journal", "log", "actions", "prompt"),
                ObservationCodec.SECTIONS);
        assertEquals(2, ObservationCodec.SCHEMA_VERSION, "story 1.7's nine sections are version 2");
    }
}
