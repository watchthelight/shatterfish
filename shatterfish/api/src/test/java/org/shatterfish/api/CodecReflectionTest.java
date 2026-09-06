package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The codec is hand-written, so this holds it to the records: every component of every record
 * reachable from {@link Observation} is encoded, found by varying each one and asking the codec
 * for different bytes. And the schema is what ADR-0005 says: no floats, no secret members, and a
 * header that carries neither the seed, the salt nor a turn counter.
 */
class CodecReflectionTest {

    /** Every record reachable from the Observation, through components and list elements. */
    static Set<Class<?>> records() {
        Set<Class<?>> found = new LinkedHashSet<>();
        collect(Observation.class, found);
        return found;
    }

    private static void collect(Class<?> type, Set<Class<?>> found) {
        if (type.isRecord() && found.add(type)) {
            for (RecordComponent component : type.getRecordComponents()) {
                collect(elementType(component), found);
            }
        }
    }

    /** Every enum a record component or list element is. */
    static Set<Class<?>> enums() {
        Set<Class<?>> found = new LinkedHashSet<>();
        for (Class<?> record : records()) {
            for (RecordComponent component : record.getRecordComponents()) {
                Class<?> type = elementType(component);
                if (type.isEnum()) {
                    found.add(type);
                }
            }
        }
        return found;
    }

    /** The component's type, or the element type of a list component. */
    private static Class<?> elementType(RecordComponent component) {
        if (component.getType() == List.class) {
            Type generic = component.getGenericType();
            return (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
        }
        return component.getType();
    }

    @TestFactory
    @DisplayName("every component of every record changes the bytes when it changes")
    List<DynamicTest> every_component_is_encoded() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> record : records()) {
            for (RecordComponent component : record.getRecordComponents()) {
                tests.add(DynamicTest.dynamicTest(record.getSimpleName() + "." + component.getName(),
                        () -> assertEncoded(record, component)));
            }
        }
        return tests;
    }

    private static void assertEncoded(Class<?> record, RecordComponent component) throws Exception {
        if (record == MapSection.class && (component.getName().equals("width") || component.getName().equals("height"))) {
            // The size cannot change with the tiles held fixed, so the layout is read instead: the
            // map's bytes begin with its width and its height.
            MapSection map = Corpus.map();
            byte[] expected = {0, 0, 0, (byte) map.width(), 0, 0, 0, (byte) map.height()};
            assertArrayEquals(expected, Arrays.copyOf(ObservationCodec.encodeValue(map), 8),
                    "the map's bytes begin with width, height");
            return;
        }
        int constructed = 0;
        List<Object> tried = new ArrayList<>();
        for (Object base : Corpus.samples(record)) {
            byte[] baseBytes = ObservationCodec.encodeValue(base);
            for (Object variant : variantsOf(base, component, tried)) {
                if (variant.equals(base)) {
                    continue;
                }
                byte[] variantBytes;
                try {
                    variantBytes = ObservationCodec.encodeValue(variant);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                constructed++;
                assertFalse(Arrays.equals(baseBytes, variantBytes), record.getSimpleName() + "." + component.getName()
                        + " is not encoded: " + base + " and " + variant + " have the same bytes");
            }
        }
        assertTrue(constructed > 0, "no variant of " + record.getSimpleName() + "." + component.getName()
                + " that differs from a sample could be constructed from " + tried + "; teach Corpus one");
    }

    /** Values a component might take instead of {@code current}; not all of them pass the record's rules. */
    private static List<Object> candidates(RecordComponent component, Object current) {
        Class<?> type = component.getType();
        List<Object> out = new ArrayList<>();
        if (type == int.class) {
            int v = (Integer) current;
            out.addAll(List.of(v + 1, v - 1, v + 2, 0, 1, 2, 11, 100));
        } else if (type == boolean.class) {
            out.add(!(Boolean) current);
        } else if (type == String.class) {
            String s = (String) current;
            // A codec that wrote the length would pass on strings of another length, so one of the
            // same length with one letter changed is tried too.
            String sameLength = s.isEmpty() ? "" : (s.charAt(0) == 'z' ? "a" : "z") + s.substring(1);
            out.addAll(List.of(sameLength, s + "x", "x", "", "y"));
        } else if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (constant != current) {
                    out.add(constant);
                }
            }
        } else if (type == List.class) {
            List<?> list = (List<?>) current;
            Class<?> element = elementType(component);
            if (!list.isEmpty()) {
                out.add(list.subList(1, list.size()));
                for (Object replacement : Corpus.extras(element)) {
                    List<Object> replaced = new ArrayList<>(list);
                    replaced.set(0, replacement);
                    out.add(replaced);
                }
            }
            for (Object extra : Corpus.extras(element)) {
                List<Object> grown = new ArrayList<>(list);
                grown.add(extra);
                out.add(grown);
            }
        } else if (type.isRecord()) {
            for (RecordComponent inner : type.getRecordComponents()) {
                out.addAll(variantsOf(current, inner, new ArrayList<>()));
            }
        } else {
            fail("no way to vary a " + type.getName() + "; the schema is ints, booleans, strings, enums, lists and records");
        }
        return out;
    }

    /** Copies of a record with one component varied, by whatever candidate the record accepts. */
    private static List<Object> variantsOf(Object base, RecordComponent component, List<Object> tried) {
        List<Object> out = new ArrayList<>();
        try {
            RecordComponent[] components = base.getClass().getRecordComponents();
            Object[] args = new Object[components.length];
            int index = -1;
            for (int i = 0; i < components.length; i++) {
                args[i] = components[i].getAccessor().invoke(base);
                if (components[i].getName().equals(component.getName())) {
                    index = i;
                }
            }
            Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Constructor<?> constructor = base.getClass().getDeclaredConstructor(types);
            for (Object candidate : candidates(component, args[index])) {
                tried.add(candidate);
                Object[] varied = args.clone();
                varied[index] = candidate;
                try {
                    out.add(constructor.newInstance(varied));
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof IllegalArgumentException)
                            && !(e.getCause() instanceof NullPointerException)) {
                        throw e;
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    @Test
    @DisplayName("no enum has a member naming hidden state: a secret door has no representation")
    void no_enum_names_hidden_state() {
        for (Class<?> type : enums()) {
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
                        Feeling.class, TransitionKind.class, Alignment.class, Emote.class), enums(),
                "the enums this test scans are the ones the schema has; add a new one here");
    }

    @Test
    @DisplayName("no component is a float, and every one is an int, a boolean, a string, an enum, a list or a record")
    void no_component_is_a_float() {
        for (Class<?> record : records()) {
            for (RecordComponent component : record.getRecordComponents()) {
                Class<?> type = component.getType();
                assertTrue(type == int.class || type == boolean.class || type == String.class || type.isEnum()
                                || type == List.class || type.isRecord(),
                        record.getSimpleName() + "." + component.getName() + " is a " + type.getName());
                if (type == List.class) {
                    Class<?> element = elementType(component);
                    assertTrue(element == String.class || element.isEnum() || element.isRecord(),
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
}
