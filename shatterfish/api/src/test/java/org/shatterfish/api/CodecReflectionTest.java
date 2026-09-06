package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The codec and the JSON renderer are hand-written, so this holds them to the records: every
 * component of every record reachable from {@link Observation} reaches both, found by varying
 * each one and asking for different output. And the schema is what ADR-0005 says: no floats, no
 * secret members, a header that carries neither the seed, the salt nor a turn counter, and one
 * Action record per kind of ADR-0014, each naming itself.
 */
class CodecReflectionTest {

    @TestFactory
    @DisplayName("every component of every record changes the bytes when it changes")
    List<DynamicTest> every_component_is_encoded() {
        return factory("encoded", value -> HexFormat.of().formatHex(ObservationCodec.encodeValue(value)));
    }

    @TestFactory
    @DisplayName("every component of every record changes the JSON when it changes")
    List<DynamicTest> every_component_is_rendered() {
        return factory("rendered", ObservationJson::renderValue);
    }

    private static List<DynamicTest> factory(String verb, Function<Object, String> render) {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> record : Variants.records()) {
            for (RecordComponent component : record.getRecordComponents()) {
                tests.add(DynamicTest.dynamicTest(record.getSimpleName() + "." + component.getName(),
                        () -> assertVaries(record, component, verb, render)));
            }
        }
        return tests;
    }

    private static void assertVaries(Class<?> record, RecordComponent component, String verb,
                                     Function<Object, String> render) {
        if (record == MapSection.class && (component.getName().equals("width") || component.getName().equals("height"))) {
            // The size cannot change with the tiles held fixed, so the layout is read instead: the
            // map's bytes begin with its width and its height, and its JSON names both.
            MapSection map = Corpus.map();
            byte[] expected = {0, 0, 0, (byte) map.width(), 0, 0, 0, (byte) map.height()};
            assertArrayEquals(expected, Arrays.copyOf(ObservationCodec.encodeValue(map), 8),
                    "the map's bytes begin with width, height");
            String json = ObservationJson.renderValue(map);
            assertTrue(json.contains("\"width\":" + map.width()) && json.contains("\"height\":" + map.height()),
                    "the map's JSON names its width and height: " + json);
            return;
        }
        int constructed = 0;
        List<Object> tried = new ArrayList<>();
        for (Object base : Corpus.samples(record)) {
            String baseOut = render.apply(base);
            for (Object variant : Variants.variantsOf(base, component, tried)) {
                if (variant.equals(base)) {
                    continue;
                }
                String variantOut;
                try {
                    variantOut = render.apply(variant);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                constructed++;
                assertFalse(baseOut.equals(variantOut), record.getSimpleName() + "." + component.getName()
                        + " is not " + verb + ": " + base + " and " + variant + " come out the same");
            }
        }
        assertTrue(constructed > 0, "no variant of " + record.getSimpleName() + "." + component.getName()
                + " that differs from a sample could be constructed from " + tried + "; teach Corpus one");
    }

    @Test
    @DisplayName("no enum has a member naming hidden state: a secret door has no representation")
    void no_enum_names_hidden_state() {
        for (Class<?> type : Variants.enums()) {
            for (Object constant : type.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                boolean announced = type == Feeling.class && name.equals("SECRETS");
                assertTrue(announced || !name.contains("SECRET"), type.getSimpleName() + "." + name
                        + " names hidden state. The only exception is the floor feeling the game logs on arrival"
                        + " and titles in the menu pane, GameScene.java:663-685, MenuPane.java:112-115");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Tile.valueOf("SECRET_DOOR"));
        assertThrows(IllegalArgumentException.class, () -> Tile.valueOf("SECRET_TRAP"));
        assertEquals(Set.of(HeroClass.class, Challenge.class, PromptKind.class, Tile.class, Fog.class, HeapKind.class,
                        Feeling.class, TransitionKind.class, Alignment.class, Emote.class, HeroSubclass.class, Hunger.class,
                        ItemKind.class, EquipSlot.class, NoteKind.class, LogTone.class), Variants.enums(),
                "the enums this test scans are the ones the schema has; add a new one here");
    }

    @Test
    @DisplayName("no component is a float, and every one is an int, a boolean, a string, an enum, a list or a record")
    void no_component_is_a_float() {
        for (Class<?> record : Variants.records()) {
            for (RecordComponent component : record.getRecordComponents()) {
                Class<?> type = component.getType();
                assertTrue(type == int.class || type == boolean.class || type == String.class || type.isEnum()
                                || type == List.class || type.isRecord(),
                        record.getSimpleName() + "." + component.getName() + " is a " + type.getName());
                if (type == List.class) {
                    Class<?> element = Variants.elementType(component);
                    assertTrue(element == String.class || element == Integer.class || element.isEnum()
                                    || element.isRecord() || element.isSealed(),
                            record.getSimpleName() + "." + component.getName() + " lists " + element.getName());
                }
            }
        }
    }

    @Test
    @DisplayName("the header carries neither the seed, the salt, a turn counter nor the wait index")
    void the_header_carries_no_seed_salt_or_turn() {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : HeaderSection.class.getRecordComponents()) {
            names.add(component.getName());
            String lower = component.getName().toLowerCase(Locale.ROOT);
            for (String hidden : List.of("seed", "salt", "turn", "wait", "index", "time", "clock")) {
                assertFalse(lower.contains(hidden), "HeaderSection." + component.getName() + " names " + hidden);
            }
        }
        assertEquals(List.of("version", "upstreamTag", "codexVersion", "heroClass", "challenges", "depth", "branch",
                "sealed", "oracle", "prompt"), names, "the header's components, in encoding order");
    }

    @Test
    @DisplayName("the Observation is the nine sections of ADR-0005, in the codec's order, and no Belief")
    void the_observation_is_nine_sections() {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : Observation.class.getRecordComponents()) {
            names.add(component.getName());
            assertFalse(component.getType() == Belief.class, "the Belief is not a field of the Observation");
        }
        assertEquals(ObservationCodec.SECTIONS, names);
        for (Class<?> record : Variants.records()) {
            for (RecordComponent component : record.getRecordComponents()) {
                assertFalse(Variants.elementType(component) == Belief.class,
                        record.getSimpleName() + "." + component.getName() + " carries the Belief");
            }
        }
    }

    @Test
    @DisplayName("one Action record per kind of ADR-0014, each naming itself as the codec and the JSON write it")
    void one_action_record_per_kind() throws Exception {
        List<String> kinds = new ArrayList<>();
        for (Class<?> kind : Action.class.getPermittedSubclasses()) {
            assertTrue(kind.isRecord(), kind.getName() + " is not a record");
            assertTrue(Variants.records().contains(kind), kind.getSimpleName() + " is not reachable from the Observation");
            Action sample = (Action) Corpus.samples(kind).get(0);
            assertEquals(kind.getSimpleName(), sample.kind());
            assertTrue(ObservationJson.renderValue(sample).contains("\"" + ObservationJson.KIND + "\":\"" + sample.kind() + "\""),
                    "the JSON of an Action names its kind: " + ObservationJson.renderValue(sample));
            assertTrue(HexFormat.of().formatHex(ObservationCodec.encodeValue(sample))
                            .startsWith("000000" + HexFormat.of().toHexDigits((byte) sample.kind().length())
                                    + HexFormat.of().formatHex(sample.kind().getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                    "the bytes of an Action open with its kind");
            kinds.add(sample.kind());
        }
        assertEquals(List.of("Step", "MoveTo", "Attack", "Interact", "PickUp", "OpenChest", "Buy", "Unlock", "Descend",
                "Ascend", "UseItem", "UseItemAt", "UseItemOn", "Rest", "Search", "Talent", "Ability",
                "AbilityAt", "AnswerPrompt", "Wait"), kinds, "the kinds of ADR-0014, item use split by target shape, no option index");
    }
}
