package org.shatterfish.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The schema by reflection, for the tests that walk it: every record reachable from
 * {@link Observation} through components, list elements and the permitted kinds of a sealed
 * interface, every enum among them, and copies of any record with one component varied by
 * whatever candidate the record accepts.
 */
final class Variants {

    private Variants() {
    }

    /** Every record reachable from the Observation, through components, list elements and sealed kinds. */
    static Set<Class<?>> records() {
        Set<Class<?>> found = new LinkedHashSet<>();
        collect(Observation.class, found);
        return found;
    }

    private static void collect(Class<?> type, Set<Class<?>> found) {
        if (type.isSealed()) {
            for (Class<?> kind : type.getPermittedSubclasses()) {
                collect(kind, found);
            }
        } else if (type.isRecord() && found.add(type)) {
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
    static Class<?> elementType(RecordComponent component) {
        if (component.getType() == List.class) {
            Type generic = component.getGenericType();
            return (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
        }
        return component.getType();
    }

    /** Values a component might take instead of {@code current}; not all of them pass the record's rules. */
    static List<Object> candidates(RecordComponent component, Object current) {
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
    static List<Object> variantsOf(Object base, RecordComponent component, List<Object> tried) {
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
}
