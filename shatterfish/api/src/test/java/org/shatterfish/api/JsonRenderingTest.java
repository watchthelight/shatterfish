package org.shatterfish.api;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        assertEquals("\"a\\ud800b\\udfffc\"", JsonWriter.quote("a\uD800b\uDFFFc"), "a lone surrogate is escaped");
        assertEquals("\"\uD83D\uDE00\"", JsonWriter.quote("\uD83D\uDE00"), "a pair stays raw");
        assertEquals("a\uD800b\uDFFFc", StrictJson.parse(JsonWriter.quote("a\uD800b\uDFFFc")));
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

    /**
     * The classes of {@code api} that are neither a record or enum of the schema nor {@link Action}.
     * A new class fails the test until it is added here after review, so a reader cannot arrive as
     * a helper.
     */
    private static final Set<String> HELPERS = Set.of("Canon", "Encoder", "Sha256", "Utf8", "JsonWriter",
            "JsonWriter$Frame", "JsonWriter$ObjectFrame", "JsonWriter$ArrayFrame", "ObservationCodec", "ObservationJson",
            "Belief", "ShatterfishApi");

    @Test
    @DisplayName("api is the schema, Action and twelve named helpers, and nothing in it reads text or bytes into a record")
    void nothing_reads_json_back() throws Exception {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.api");
        Set<String> schema = new HashSet<>();
        for (Class<?> record : Variants.records()) {
            schema.add(record.getName());
        }
        for (Class<?> type : Variants.enums()) {
            schema.add(type.getName());
        }
        schema.add(Action.class.getName());
        List<String> strangers = new ArrayList<>();
        List<String> readers = new ArrayList<>();
        int seen = 0;
        for (JavaClass type : classes) {
            seen++;
            String simple = type.getName().substring(type.getPackageName().length() + 1);
            if (!schema.contains(type.getName()) && !HELPERS.contains(simple)) {
                strangers.add(type.getName());
            }
            Class<?> real = Class.forName(type.getName());
            List<JavaCodeUnit> units = new ArrayList<>(type.getMethods());
            units.addAll(type.getConstructors());
            for (JavaCodeUnit unit : units) {
                if (real.isEnum() && (unit instanceof JavaConstructor || unit.getName().equals("valueOf"))) {
                    continue; // a name to a constant, which every enum has and which reads no record
                }
                if (unit instanceof JavaConstructor && real.isRecord() && isCanonical(real, unit)) {
                    continue; // the record's own constructor over its own components
                }
                boolean producesSchema = unit instanceof JavaConstructor
                        ? schema.contains(type.getName())
                        : schema.contains(unit.getRawReturnType().getName());
                if (producesSchema && takesAnOpenType(unit)) {
                    readers.add(unit.getFullName());
                }
            }
        }
        assertTrue(seen > 40, "the importer saw too few classes to mean anything: " + seen);
        assertEquals(List.of(), strangers,
                "a class api did not have: add it to HELPERS after review, or it is a reader in waiting");
        assertEquals(List.of(), readers,
                "a method or constructor that turns text, bytes or an open type into a record of the schema is a reader");
    }

    /** Whether a parameter could carry text or bytes: a string, an array, a collection, a stream, or anything at all. */
    private static boolean takesAnOpenType(JavaCodeUnit unit) {
        for (JavaClass parameter : unit.getRawParameterTypes()) {
            if (parameter.isEquivalentTo(String.class) || parameter.isEquivalentTo(CharSequence.class)
                    || parameter.isEquivalentTo(Object.class) || parameter.isArray()
                    || parameter.isAssignableTo(Iterable.class) || parameter.isAssignableTo(Map.class)
                    || parameter.getPackageName().startsWith("java.io") || parameter.getPackageName().startsWith("java.nio")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCanonical(Class<?> record, JavaCodeUnit constructor) {
        RecordComponent[] components = record.getRecordComponents();
        List<JavaClass> parameters = constructor.getRawParameterTypes();
        if (components.length != parameters.size()) {
            return false;
        }
        for (int i = 0; i < components.length; i++) {
            if (!parameters.get(i).isEquivalentTo(components[i].getType())) {
                return false;
            }
        }
        return true;
    }
}
