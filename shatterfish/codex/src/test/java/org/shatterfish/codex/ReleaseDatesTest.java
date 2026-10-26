package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code lore/release-dates.json} (story 7.9): data whose provenance is the point. */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ReleaseDatesTest {

    private static final Path ROOT = DocsCitations.root();
    private static final String TAG = Upstream.tag(ROOT);

    private static DateGate.Table committed() {
        return DateGate.readTable(Lore.read(ROOT.resolve(DateGate.TABLE)));
    }

    @Test
    @DisplayName("the committed table is what the changelog derives, byte for byte")
    void the_committed_table_is_the_changelogs() {
        String expected = DateGate.render(TAG, DateGate.derive(DateGate.read(ROOT, TAG)));
        assertEquals(expected, Lore.read(ROOT.resolve(DateGate.TABLE)),
                DateGate.TABLE + " has drifted from the changelog. Run `./gradlew :codex:lore --args=dates` and commit it.");
        assertEquals(TAG, committed().tag());
        assertEquals(53, committed().rows().size());
    }

    @Test
    @DisplayName("every row's source resolves at the tag and its line states the date, and the order is held")
    void every_source_resolves_and_states_its_date() {
        assertEquals(List.of(), DateGate.problems(ROOT, TAG, committed().rows()));
    }

    @Test
    @DisplayName("a row with no source is not accepted")
    void a_row_with_no_source_is_refused() {
        List<DateGate.Row> rows = List.of(new DateGate.Row("v3.3", "2025-12-04", "December 4th, 2025", ""));
        List<String> problems = DateGate.problems(ROOT, TAG, rows);
        assertTrue(problems.stream().anyMatch(p -> p.contains("no source") && p.contains("not accepted")), problems.toString());
        DateGate.Table missing = DateGate.readTable("{\n\"releases\":[\n  {\"as_written\":\"December 4th, 2025\","
                + "\"released\":\"2025-12-04\",\"version\":\"v3.3\"}\n],\n\"tag\":\"v4.0.2\"\n}\n");
        assertEquals(1, missing.rows().size());
        assertTrue(DateGate.problems(ROOT, TAG, missing.rows()).stream().anyMatch(p -> p.contains("no source")),
                "a row that leaves the key out is a row with no source");
    }

    @Test
    @DisplayName("a wrong date, a wrong line, a repeat and a misorder are each named")
    void the_rows_are_ordered_and_unique() {
        DateGate.Table good = committed();
        DateGate.Row first = good.rows().get(0);
        DateGate.Row second = good.rows().get(1);
        List<String> wrongDate = DateGate.problems(ROOT, TAG,
                List.of(new DateGate.Row(first.version(), "2014-08-06", first.asWritten(), first.source())));
        assertTrue(wrongDate.stream().anyMatch(p -> p.contains("is not the date")), wrongDate.toString());
        List<String> wrongLine = DateGate.problems(ROOT, TAG, List.of(new DateGate.Row(first.version(), first.released(),
                first.asWritten(), first.source().replaceAll(":\\d+$", ":1"))));
        assertTrue(wrongLine.stream().anyMatch(p -> p.contains("does not say")), wrongLine.toString());
        List<String> repeat = DateGate.problems(ROOT, TAG, List.of(first, first));
        assertTrue(repeat.stream().anyMatch(p -> p.contains("listed twice")), repeat.toString());
        List<String> backwards = DateGate.problems(ROOT, TAG, List.of(second, first));
        assertTrue(backwards.stream().anyMatch(p -> p.contains("oldest first")), backwards.toString());
        List<String> notAVersion = DateGate.problems(ROOT, TAG,
                List.of(new DateGate.Row("3.3", "2025-12-04", "December 4th, 2025", "a.java:1")));
        assertTrue(notAVersion.stream().anyMatch(p -> p.contains("version is not")), notAVersion.toString());
    }

    @Test
    @DisplayName("dates are parsed as the game writes them, abbreviated months and ordinals included")
    void dates_are_parsed_as_the_game_writes_them() {
        assertEquals(LocalDate.of(2019, 3, 18), DateGate.date("Mar 18th, 2019"));
        assertEquals(LocalDate.of(2026, 9, 9), DateGate.date("September 9th, 2026"));
        assertEquals(LocalDate.of(2014, 8, 5), DateGate.date("August 5th, 2014"));
        assertEquals(LocalDate.of(2018, 12, 18), DateGate.date("Dec 18th, 2018"));
        assertThrows(IllegalArgumentException.class, () -> DateGate.date("sometime in 2019"));
        assertThrows(IllegalArgumentException.class, () -> DateGate.date("Smarch 3rd, 2019"));
    }

    @Test
    @DisplayName("a table rendered and read again is the same table")
    void the_table_round_trips() {
        DateGate.Table t = committed();
        assertEquals(t.rows(), DateGate.readTable(DateGate.render(t.tag(), t.rows())).rows());
        assertFalse(t.rows().isEmpty());
    }

    @Test
    @DisplayName("the two patch releases of v4.0 are labelled and undated, and so are not rows")
    void undated_releases_are_not_rows() {
        List<String> labels = DateGate.read(ROOT, TAG).releases().stream().filter(r -> !r.dated())
                .map(DateGate.Release::label).toList();
        assertEquals(List.of("v4.0.2", "v4.0.1"), labels);
        assertTrue(committed().rows().stream().noneMatch(r -> labels.contains(r.version())));
    }
}
