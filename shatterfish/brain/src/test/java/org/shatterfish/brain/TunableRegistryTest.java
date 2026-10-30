package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tunables;
import org.shatterfish.api.Tunables.Tunable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry of tunable parameters is honest (story 10.35, ADR-0026): every default is the number the
 * code decided by before the registry, every range holds its default, a name is registered once, and
 * every entry is read by the Brain, so a tuner never moves a number nothing looks at.
 */
class TunableRegistryTest {

    /**
     * What each number was before the story, written out here and not computed from the registry, so a
     * default moved by mistake fails against a figure that is not the registry's own.
     */
    private static final Map<String, Long> BEFORE = new LinkedHashMap<>();

    static {
        // Playbook fields: Playbook.Identify.REFERENCE, Upgrade.REFERENCE, Goo.REFERENCE (stories 7.2, 7.5).
        BEFORE.put("identify.escape_waits", 12L);
        BEFORE.put("identify.gain_odds_per_mille", 200L);
        BEFORE.put("identify.healing_odds_per_mille", 200L);
        BEFORE.put("identify.inventory_odds_per_mille", 250L);
        BEFORE.put("identify.potion_hp_per_mille", 500L);
        BEFORE.put("identify.reach", 8L);
        BEFORE.put("identify.rest_waits", 50L);
        BEFORE.put("identify.scroll_hp_per_mille", 1000L);
        BEFORE.put("upgrade.hold_until_depth", 0L);
        BEFORE.put("upgrade.weapon_lead", 0L);
        BEFORE.put("bosses.goo.descent_rest_per_mille", 1000L);
        BEFORE.put("bosses.goo.drink_per_mille", 400L);
        BEFORE.put("bosses.goo.meet_range", 6L);
        BEFORE.put("bosses.goo.wash_range", 4L);
        BEFORE.put("bosses.tengu.adjacent_cost", 6L);
        BEFORE.put("bosses.tengu.bomb_cost", 22L);
        BEFORE.put("bosses.tengu.fire_cost", 18L);
        BEFORE.put("bosses.tengu.shock_cost", 12L);
        // Policy constants, as they stood on main before this story.
        BEFORE.put("fight.margin_per_mille", 500L);
        BEFORE.put("fight.flights", 2L);
        BEFORE.put("fight.outpaced_range", 6L);
        BEFORE.put("fight.outpaced_stairs", 2L);
        BEFORE.put("fight.chase_waits", 10L);
        BEFORE.put("fight.recall_waits", 20L);
        BEFORE.put("descend.allowance", 500L);
        BEFORE.put("descend.per_drop", 250L);
        BEFORE.put("descend.rests", 20L);
        BEFORE.put("descend.rest_per_mille", 1000L);
        BEFORE.put("descend.rest_tight_per_mille", 1000L);
        BEFORE.put("explore.rests", 50L);
        BEFORE.put("explore.searches", 12L);
        BEFORE.put("explore.frugal_searches", 4L);
        BEFORE.put("larder.frugal", 600L);
        BEFORE.put("larder.fallback_rests", 0L);
        BEFORE.put("heal.redrink_share_per_mille", 250L);
        BEFORE.put("heal.horizon", 10L);
        BEFORE.put("plunge.safe_hp", 15L);
        BEFORE.put("plunge.fit_percent", 80L);
        BEFORE.put("brain.avoid_waits", 40L);
        BEFORE.put("brain.avoid_radius", 5L);
        BEFORE.put("chests.mimic_margin_per_mille", 1250L);
        // Issue #412: the reaches and floors of the missile, dew and tall-grass levers; each new, so its default is its first value.
        BEFORE.put("pickup.missile_reach", 0L);
        BEFORE.put("pickup.dew_skin_below", 0L);
        BEFORE.put("ranged.fetch_steps", 0L);
        BEFORE.put("ranged.min_damage_per_mille", 0L);
        BEFORE.put("descend.trample_reach", 0L);
        BEFORE.put("explore.grass_skin_below", 0L);
    }

    @Test
    @DisplayName("the registry lists exactly the numbers that were written down before the story, each once, with the default it had")
    void defaults_are_the_numbers_the_code_used() {
        Map<String, Long> registered = new LinkedHashMap<>();
        for (Tunable tunable : Tunables.ALL) {
            assertEquals(null, registered.put(tunable.name(), tunable.def()), tunable.name() + " appears twice");
        }
        assertEquals(new java.util.TreeMap<>(BEFORE), new java.util.TreeMap<>(registered));
    }

    @Test
    @DisplayName("every bound contains its default, and every step fits inside its range")
    void bounds_contain_the_default() {
        for (Tunable tunable : Tunables.ALL) {
            assertTrue(tunable.min() <= tunable.def() && tunable.def() <= tunable.max(), tunable.name() + " bounds");
            assertTrue(tunable.min() < tunable.max(), tunable.name() + " has room to move");
            assertTrue(tunable.step() >= 1 && tunable.step() <= tunable.max() - tunable.min(), tunable.name() + " step");
            assertTrue(!tunable.reason().isBlank(), tunable.name() + " says why");
        }
    }

    @Test
    @DisplayName("a Policy entry names the constant it replaces and the constant still holds the default")
    void policy_constants_hold_the_registered_default() throws ReflectiveOperationException {
        int checked = 0;
        for (Tunable tunable : Tunables.ALL) {
            if (tunable.owner() != Tunables.Owner.POLICY) {
                continue;
            }
            String[] origin = tunable.origin().split("\\.");
            Class<?> type = Class.forName("org.shatterfish.brain." + origin[0]);
            Field constant = type.getDeclaredField(origin[1]);
            constant.setAccessible(true);
            assertTrue(Modifier.isStatic(constant.getModifiers()), tunable.origin() + " is a constant");
            assertEquals(tunable.def(), ((Number) constant.get(null)).longValue(), tunable.origin() + " against " + tunable.name());
            checked++;
        }
        assertTrue(checked >= 23, "the Policy entries were reached: " + checked);
    }

    @Test
    @DisplayName("a Playbook entry's default is the value of the reference Playbook")
    void playbook_entries_match_the_reference() {
        Playbook reference = Playbook.reference("shatterfish");
        int checked = 0;
        for (Tunable tunable : Tunables.ALL) {
            if (tunable.owner() == Tunables.Owner.PLAYBOOK) {
                assertEquals(tunable.def(), Tunables.value(reference, tunable), tunable.name());
                checked++;
            }
        }
        assertTrue(checked >= 14, "the Playbook entries were reached: " + checked);
    }

    @Test
    @DisplayName("every registered tunable is read by the Brain: a Policy entry through its registry constant, a Playbook entry through an accessor")
    void every_entry_is_read() throws IOException {
        String sources = brainSources();
        Map<Tunable, String> names = new LinkedHashMap<>();
        for (Field field : Tunables.class.getDeclaredFields()) {
            if (field.getType() == Tunable.class && Modifier.isStatic(field.getModifiers())) {
                try {
                    names.put((Tunable) field.get(null), field.getName());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        assertEquals(Tunables.ALL.size(), names.size(), "every entry is a constant of Tunables");
        Set<String> dead = new HashSet<>();
        for (Tunable tunable : Tunables.ALL) {
            if (tunable.owner() == Tunables.Owner.POLICY) {
                if (!java.util.regex.Pattern.compile("Tunables\\." + names.get(tunable) + "\\b").matcher(sources).find()) {
                    dead.add(tunable.name());
                }
            } else if (readers(tunable).stream().noneMatch(reader -> java.util.regex.Pattern
                    .compile("\\.\\s*" + reader + "\\b").matcher(sources).find())) {
                dead.add(tunable.name());
            }
        }
        assertEquals(Set.of(), dead, "entries no Brain code reads");
    }

    /** The accessors through which the Brain reads a Playbook field: the record component, and the helper that wraps it. */
    private static Set<String> readers(Tunable tunable) {
        String key = tunable.name().substring(tunable.name().lastIndexOf('.') + 1);
        StringBuilder camel = new StringBuilder();
        boolean up = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                camel.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        Set<String> readers = new HashSet<>();
        readers.add(camel.toString());
        switch (key) {
            case "healing_odds_per_mille" -> readers.add("healingOdds");
            case "gain_odds_per_mille" -> readers.add("gainOdds");
            case "inventory_odds_per_mille" -> readers.add("inventoryOdds");
            case "potion_hp_per_mille" -> readers.add("potionHp");
            case "scroll_hp_per_mille" -> readers.add("scrollHp");
            case "reach" -> readers.add("walks");
            case "descent_rest_per_mille" -> readers.add("descentRest");
            case "drink_per_mille" -> readers.add("danger");
            default -> {
            }
        }
        return readers;
    }

    private static String brainSources() throws IOException {
        Path root = Path.of("src", "main", "java", "org", "shatterfish", "brain");
        try (Stream<Path> files = Files.walk(root)) {
            StringBuilder all = new StringBuilder();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                // Code only: a comment that names a tunable does not read it.
                String text = Files.readString(file);
                all.append(text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "")).append('\n');
            }
            assertTrue(all.length() > 10_000, "the Brain's sources were found under " + root.toAbsolutePath());
            return all.toString();
        }
    }

    @Test
    @DisplayName("a vector refuses a name that is not registered, a Playbook field, and a number outside its bounds, and drops a default")
    void the_vector_is_checked() {
        assertThrows(IllegalArgumentException.class, () -> new Tunables.Vector(Map.of("fight.nonsense", 1L)));
        assertThrows(IllegalArgumentException.class, () -> new Tunables.Vector(Map.of("identify.reach", 8L)));
        assertThrows(IllegalArgumentException.class, () -> new Tunables.Vector(Map.of("fight.flights", 99L)));
        assertTrue(new Tunables.Vector(Map.of("fight.flights", 2L)).isReference(), "a stated default is no vector");
        assertEquals(3, new Tunables.Vector(Map.of("fight.flights", 3L)).getInt(Tunables.FIGHT_FLIGHTS));
        assertEquals(Tunables.FIGHT_FLIGHTS.defInt(), Tunables.Vector.REFERENCE.getInt(Tunables.FIGHT_FLIGHTS));
        assertNotNull(Tunables.named("descend.rests"));
    }

    @Test
    @DisplayName("the names are the section paths the Playbook file uses, with no entry shadowing another")
    void names_are_unique_paths() {
        Set<String> names = Tunables.ALL.stream().map(Tunable::name).collect(Collectors.toSet());
        assertEquals(Tunables.ALL.size(), names.size());
        for (String name : names) {
            assertTrue(name.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"), name);
        }
    }
}
