package org.shatterfish.api;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The readable form (ADR-0005, option 10; ADR-0011): canonical JSON that carries the hash and the
 * section hashes, names every component, is read by a strict reader that accepts nothing but the
 * canonical shape, and is never read back into an Observation by anything in {@code api}.
 */
class JsonRenderingTest {

    @Test
    @DisplayName("the rendering carries the hash and the section hashes as fields")
    void the_rendering_carries_the_hash() {
        for (Observation observation : List.of(Corpus.observation(), Corpus.promptObservation())) {
            Map<?, ?> root = (Map<?, ?>) StrictJson.parse(observation.json());
            assertEquals(observation.hash(), root.get(ObservationJson.HASH));
            assertEquals(observation.sectionHashes(), root.get(ObservationJson.SECTION_HASHES));
            List<String> keys = new ArrayList<>();
            for (Object key : root.keySet()) {
                keys.add((String) key);
            }
            List<String> expected = new ArrayList<>(ObservationCodec.SECTIONS);
            expected.add(ObservationJson.HASH);
            expected.add(ObservationJson.SECTION_HASHES);
            expected.sort(null);
            assertEquals(expected, keys, "the root's keys are the sections, the hash and the section hashes, sorted");
        }
    }

    @Test
    @DisplayName("the JSON is canonical: sorted keys, no whitespace, integers, and it parses as the same values")
    void the_json_is_canonical() {
        Observation observation = Corpus.observation();
        String json = observation.json();
        assertFalse(json.contains("\n") || json.contains(" :") || json.contains(": ") || json.contains(", "),
                "no whitespace outside strings");
        Map<?, ?> root = (Map<?, ?>) StrictJson.parse(json);
        Map<?, ?> hero = (Map<?, ?>) root.get(ObservationCodec.HERO);
        assertEquals((long) observation.hero().hp(), hero.get("hp"));
        assertEquals(observation.hero().name(), hero.get("name"));
        assertEquals(observation.hero().hunger().name(), hero.get("hunger"));
        Map<?, ?> map = (Map<?, ?>) root.get(ObservationCodec.MAP);
        assertEquals((long) Corpus.WIDTH, map.get("width"));
        assertEquals(observation.map().tiles().size(), ((List<?>) map.get("tiles")).size());
        Map<?, ?> actions = (Map<?, ?>) root.get(ObservationCodec.ACTIONS);
        List<?> list = (List<?>) actions.get("actions");
        assertEquals(observation.actions().actions().size(), list.size());
        for (int i = 0; i < list.size(); i++) {
            assertEquals(observation.actions().actions().get(i).kind(), ((Map<?, ?>) list.get(i)).get(ObservationJson.KIND));
        }
        // The same Observation renders the same text, and a different one differently.
        assertEquals(json, Corpus.observation().json());
        assertTrue(!json.equals(Corpus.promptObservation().json()));
    }

    @Test
    @DisplayName("every component of every record is a key named after it, and an Action's kind is first")
    void every_component_is_a_key() {
        for (Class<?> record : Variants.records()) {
            Object sample = Corpus.samples(record).get(0);
            String json = ObservationJson.renderValue(sample);
            Map<?, ?> object = (Map<?, ?>) StrictJson.parse(json);
            List<String> expected = new ArrayList<>();
            for (RecordComponent component : record.getRecordComponents()) {
                expected.add(component.getName());
            }
            if (sample instanceof Action) {
                expected.add(ObservationJson.KIND);
            } else if (sample instanceof Observation) {
                expected.add(ObservationJson.HASH);
                expected.add(ObservationJson.SECTION_HASHES);
            }
            expected.sort(null);
            List<String> keys = new ArrayList<>();
            for (Object key : object.keySet()) {
                keys.add((String) key);
            }
            assertEquals(expected, keys, record.getSimpleName() + " renders its components as keys");
        }
    }

    @Test
    @DisplayName("strings come out with the escapes JSON requires and nothing else, and read back the same")
    void strings_are_escaped() {
        String text = "a\"b\\c/d\be\ff\ng\rh\tijk é é 😀 z";
        assertEquals("\"a\\\"b\\\\c/d\\be\\ff\\ng\\rh\\ti\\u0001j\\u001fk é é 😀 z\"", JsonWriter.quote(text));
        assertEquals(text, StrictJson.parse(JsonWriter.quote(text)));
        LogSection log = new LogSection(List.of(new LogLine(LogTone.PLAIN, text)));
        Map<?, ?> rendered = (Map<?, ?>) StrictJson.parse(ObservationJson.renderValue(log));
        assertEquals(text, ((Map<?, ?>) ((List<?>) rendered.get("lines")).get(0)).get("text"));
    }

    @Test
    @DisplayName("the writer refuses a value without a key, a key twice, and an unbalanced end")
    void the_writer_refuses_misuse() {
        assertThrows(IllegalArgumentException.class, () -> new JsonWriter().beginObject().value(1));
        assertThrows(IllegalArgumentException.class, () -> new JsonWriter().beginObject().key("a").value(1).key("a"));
        assertThrows(IllegalArgumentException.class, () -> new JsonWriter().beginObject().endArray());
        assertThrows(IllegalArgumentException.class, () -> new JsonWriter().beginObject().toJson());
        assertThrows(IllegalArgumentException.class, () -> new JsonWriter().value(1).value(2));
        assertThrows(IllegalArgumentException.class, () -> new JsonWriter().toJson());
        assertEquals("{\"a\":[1,true,\"x\",{}],\"b\":{\"c\":-2}}", new JsonWriter().beginObject()
                .key("b").beginObject().key("c").value(-2L).endObject()
                .key("a").beginArray().value(1).value(true).value("x").beginObject().endObject().endArray()
                .endObject().toJson());
    }

    @Test
    @DisplayName("nothing in api reads text or bytes back into a record of the schema")
    void nothing_reads_json_back() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.api");
        List<String> readers = new ArrayList<>();
        for (JavaClass type : classes) {
            for (JavaMethod method : type.getMethods()) {
                if (type.isEnum() && method.getName().equals("valueOf")) {
                    continue; // a name to a constant, which every enum has and which reads no record
                }
                boolean takesText = false;
                for (JavaClass parameter : method.getRawParameterTypes()) {
                    takesText |= parameter.isEquivalentTo(String.class) || parameter.isEquivalentTo(CharSequence.class)
                            || parameter.isEquivalentTo(byte[].class) || parameter.getName().startsWith("java.io.");
                }
                JavaClass returns = method.getRawReturnType();
                boolean returnsSchema = returns.getPackageName().equals("org.shatterfish.api")
                        && !returns.isEquivalentTo(JsonWriter.class);
                if (takesText && returnsSchema) {
                    readers.add(method.getFullName());
                }
            }
        }
        assertEquals(List.of(), readers, "a method that turns text or bytes into a record of the schema is a reader");
    }
}
