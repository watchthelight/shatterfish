package org.shatterfish.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * What every record of the schema does to its inputs: refuse what cannot be drawn, and put every
 * list into the one order the codec encodes, so that two Observations of the same screen are
 * equal as records and equal as bytes, whatever order their lists arrived in (ADR-0005).
 */
final class Canon {

    private Canon() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static String text(String value, String what) {
        return Objects.requireNonNull(value, what);
    }

    static int cell(int cell, String what) {
        require(cell >= 0, what + " is at a negative cell: " + cell);
        return cell;
    }

    /** An immutable copy of {@code in} in {@code order}, refusing nulls. */
    static <T> List<T> sorted(List<T> in, Comparator<? super T> order, String what) {
        Objects.requireNonNull(in, what);
        List<T> copy = new ArrayList<>(in);
        for (T element : copy) {
            Objects.requireNonNull(element, what + " holds a null");
        }
        copy.sort(order);
        return List.copyOf(copy);
    }

    /** An immutable copy of {@code in} in its own order, refusing nulls: for the lists whose order is the screen's. */
    static <T> List<T> positional(List<T> in, String what) {
        Objects.requireNonNull(in, what);
        for (T element : in) {
            Objects.requireNonNull(element, what + " holds a null");
        }
        return List.copyOf(in);
    }

    /** Refuses two entries with the same key in a list already sorted by it. */
    static <T> void distinctBy(List<T> sorted, ToIntFunction<T> key, String what) {
        for (int i = 1; i < sorted.size(); i++) {
            require(key.applyAsInt(sorted.get(i)) != key.applyAsInt(sorted.get(i - 1)),
                    what + " names one cell twice: " + key.applyAsInt(sorted.get(i)));
        }
    }

    /** Refuses two equal adjacent entries in a sorted list. */
    static <T> void noRepeats(List<T> sorted, String what) {
        for (int i = 1; i < sorted.size(); i++) {
            require(!sorted.get(i).equals(sorted.get(i - 1)), what + " lists " + sorted.get(i) + " twice");
        }
    }
}
