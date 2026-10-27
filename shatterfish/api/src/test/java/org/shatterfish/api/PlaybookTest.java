package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Playbook as an {@code api} value (story 7.2, FR-35). */
class PlaybookTest {

    @Test
    @DisplayName("the reference Playbook holds the values the Policies decided by before they were data, and writes them canonically")
    void the_reference() {
        Playbook playbook = Playbook.reference("shatterfish");
        assertEquals("{\"bosses\":{},\"classes\":{},\"format\":1,"
                + "\"identify\":{\"escape_waits\":12,\"gain_odds_per_mille\":200,\"healing_odds_per_mille\":200,"
                + "\"inventory_odds_per_mille\":250,\"reach\":8,\"rest_waits\":50},\"kit\":{\"zap_reach\":0},"
                + "\"name\":\"shatterfish\",\"upgrade\":{\"hold_until_depth\":0,\"tie\":\"WEAPON\",\"unknown_weapon\":\"NO\",\"weapon_lead\":0},\"version\":1}",
                playbook.canonical());
        assertEquals(0.2, playbook.identify().healingOdds(), 0.0, "200 per mille is the double 0.2, bit for bit");
        assertEquals(0.25, playbook.identify().inventoryOdds(), 0.0);
        assertEquals(16, playbook.identify().walks(), "twice the reach");
    }

    @Test
    @DisplayName("a class entry replaces the base for that class only, and the Playbook says which it is")
    void classes() {
        Playbook.Identify wide = Playbook.Identify.REFERENCE.with(Map.of("reach", 12L));
        Playbook playbook = new Playbook("shatterfish", 2, Playbook.Identify.REFERENCE, Playbook.Upgrade.REFERENCE,
                List.of(new Playbook.ClassEntry(HeroClass.MAGE, wide, Playbook.Upgrade.REFERENCE)));
        assertEquals(12, playbook.identify(HeroClass.MAGE).reach());
        assertEquals(8, playbook.identify(HeroClass.WARRIOR).reach());
        assertEquals(8, playbook.identify().reach());
        assertNotEquals(Playbook.reference("shatterfish").canonical(), playbook.canonical(),
                "a class entry is part of the Playbook's hash");
        assertThrows(IllegalArgumentException.class, () -> new Playbook("shatterfish", 1, Playbook.Identify.REFERENCE,
                Playbook.Upgrade.REFERENCE, List.of(new Playbook.ClassEntry(HeroClass.MAGE, wide, Playbook.Upgrade.REFERENCE),
                        new Playbook.ClassEntry(HeroClass.MAGE, wide, Playbook.Upgrade.REFERENCE))), "a class is stated once");
    }

    @Test
    @DisplayName("a value outside the range its field declares, or an enum the field does not allow, is refused")
    void ranges() {
        assertThrows(IllegalArgumentException.class, () -> Playbook.Identify.REFERENCE.with(Map.of("reach", 41L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Identify.REFERENCE.with(Map.of("healing_odds_per_mille", 0L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Identify.REFERENCE.with(Map.of("gain_odds_per_mille", 1001L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Upgrade.REFERENCE.with(Map.of("tie", "RING")),
                "RING is a slot of the Observation, but not one the scroll of upgrade is read onto");
        assertThrows(IllegalArgumentException.class, () -> Playbook.Upgrade.REFERENCE.with(Map.of("weapon_lead", 6L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Upgrade.REFERENCE.with(Map.of("hold_until_depth", 27L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Upgrade.REFERENCE.with(Map.of("hold_until_depth", -1L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Upgrade.REFERENCE.with(Map.of("unknown_weapon", "MAYBE")));
        assertThrows(IllegalArgumentException.class, () -> new Playbook("Shatter Fish", 1, Playbook.Identify.REFERENCE,
                Playbook.Upgrade.REFERENCE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Playbook("shatterfish", 0, Playbook.Identify.REFERENCE,
                Playbook.Upgrade.REFERENCE, List.of()));
    }

    @Test
    @DisplayName("every field of a section has a declaration, in the order the canonical text writes them")
    void declarations() {
        assertEquals(Playbook.Identify.SPECS.stream().map(Playbook.Spec::key).toList(),
                List.copyOf(Playbook.Identify.REFERENCE.values().keySet()));
        assertEquals(Playbook.Upgrade.SPECS.stream().map(Playbook.Spec::key).toList(),
                List.copyOf(Playbook.Upgrade.REFERENCE.values().keySet()));
        assertEquals(Playbook.Kit.SPECS.stream().map(Playbook.Spec::key).toList(),
                List.copyOf(Playbook.Kit.REFERENCE.values().keySet()));
    }

    @Test
    @DisplayName("the kit section: the reference uses no kit item, a class entry sets the reach, and 11 is refused")
    void kit() {
        assertEquals(0, Playbook.reference("shatterfish").kit(HeroClass.MAGE).zapReach());
        Playbook.Kit zapping = Playbook.Kit.REFERENCE.with(Map.of("zap_reach", 6L));
        Playbook playbook = new Playbook("shatterfish", 2, Playbook.Identify.REFERENCE, Playbook.Upgrade.REFERENCE,
                Playbook.Kit.REFERENCE, List.of(new Playbook.ClassEntry(HeroClass.MAGE, Playbook.Identify.REFERENCE, zapping,
                        Playbook.Upgrade.REFERENCE)));
        assertEquals(6, playbook.kit(HeroClass.MAGE).zapReach());
        assertEquals(0, playbook.kit(HeroClass.WARRIOR).zapReach());
        assertEquals(0, playbook.kit().zapReach());
        assertTrue(playbook.canonical().contains("\"kit\":{\"zap_reach\":6}"));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Kit.REFERENCE.with(Map.of("zap_reach", 11L)));
        assertThrows(IllegalArgumentException.class, () -> Playbook.Kit.REFERENCE.with(Map.of("zap_reach", -1L)));
    }
}
