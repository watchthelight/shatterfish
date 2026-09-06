package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two Observations are equal exactly when their hashes are equal (ADR-0005; story 1.7), over a
 * corpus: the two base Observations, each built twice, and every Observation that differs from
 * one of them in a single component of a single section, as far as the records' rules allow.
 * The same holds for the bytes and for the JSON, which carries the hash.
 */
class CodecEqualityTest {

    /** The corpus: the bases twice, then one Observation per accepted variation of a section's component. */
    static List<Observation> corpus() {
        List<Observation> corpus = new ArrayList<>();
        for (Observation base : List.of(Corpus.observation(), Corpus.observation(), Corpus.promptObservation(),
                Corpus.promptObservation())) {
            corpus.add(base);
        }
        for (Observation base : List.of(Corpus.observation(), Corpus.promptObservation())) {
            for (RecordComponent sectionComponent : Observation.class.getRecordComponents()) {
                Object section;
                try {
                    section = sectionComponent.getAccessor().invoke(base);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                for (RecordComponent component : section.getClass().getRecordComponents()) {
                    for (Object variant : Variants.variantsOf(section, component, new ArrayList<>())) {
                        try {
                            Observation varied = Corpus.with(base, variant);
                            varied.hash();
                            corpus.add(varied);
                        } catch (IllegalArgumentException e) {
                            // The section accepts the variant on its own but the Observation refuses
                            // it against another section, which is what the Observation is for; or
                            // the header names another schema version, which the codec refuses.
                        }
                    }
                }
            }
        }
        return corpus;
    }

    @Test
    @DisplayName("equal Observations have equal hashes, and different ones differ, over the corpus")
    void equality_and_hash_agree() {
        List<Observation> corpus = corpus();
        Set<Observation> distinct = new HashSet<>(corpus);
        System.out.println("equality corpus: " + corpus.size() + " Observations, " + distinct.size() + " distinct");
        assertTrue(distinct.size() >= 100, "the corpus is too small to mean much: " + distinct.size() + " distinct");
        assertTrue(distinct.size() < corpus.size(), "the corpus holds no two equal Observations");
        List<String> hashes = new ArrayList<>();
        List<byte[]> bytes = new ArrayList<>();
        List<String> json = new ArrayList<>();
        for (Observation observation : corpus) {
            hashes.add(observation.hash());
            bytes.add(ObservationCodec.encode(observation));
            json.add(observation.json());
        }
        for (int i = 0; i < corpus.size(); i++) {
            for (int j = 0; j < corpus.size(); j++) {
                boolean equal = corpus.get(i).equals(corpus.get(j));
                assertEquals(equal, hashes.get(i).equals(hashes.get(j)),
                        "equals and hash disagree between corpus entries " + i + " and " + j);
                assertEquals(equal, Arrays.equals(bytes.get(i), bytes.get(j)),
                        "equals and the bytes disagree between corpus entries " + i + " and " + j);
                assertEquals(equal, json.get(i).equals(json.get(j)),
                        "equals and the JSON disagree between corpus entries " + i + " and " + j);
            }
        }
    }

    @Test
    @DisplayName("the corpus varies every section")
    void the_corpus_varies_every_section() {
        Observation base = Corpus.observation();
        for (RecordComponent sectionComponent : Observation.class.getRecordComponents()) {
            boolean varied = false;
            for (Observation observation : corpus()) {
                try {
                    Object mine = sectionComponent.getAccessor().invoke(observation);
                    Object theirs = sectionComponent.getAccessor().invoke(base);
                    varied |= !mine.equals(theirs);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            assertTrue(varied, "no corpus entry varies " + sectionComponent.getName());
        }
    }
}
