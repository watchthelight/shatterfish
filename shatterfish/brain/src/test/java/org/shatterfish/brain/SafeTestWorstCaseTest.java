package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worst-case check (story 4.3, FR-30): a lethal worst case is refused whatever the mean, water
 * beside the hero changes the verdict on a fire candidate, and potions, scrolls and wands go
 * through the same code. Built from {@code api} records alone, so it runs without a game.
 */
class SafeTestWorstCaseTest {

    private static final String FLAME = "items.potions.PotionOfLiquidFlame";
    private static final String HEALING = "items.potions.PotionOfHealing";

    /**
     * A 3x3 floor, the hero on the centre (cell 4) with {@code hp} of 20, at {@code depth}, the
     * tiles given, and a rat on cell 0 when {@code enemy}.
     */
    private static Observation standing(int depth, int hp, List<Tile> tiles, boolean enemy) {
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, PromptKind.NONE);
        MapSection map = new MapSection(3, 3, tiles, Collections.nCopies(9, Fog.VISIBLE), List.of(), List.of(),
                List.of(), Feeling.NONE, List.of());
        HeroSection hero = new HeroSection(4, "", HeroSubclass.NONE, "", 1, 0, 1, hp, 20, 0, 10, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        List<ActorView> actors = enemy
                ? List.of(new ActorView(0, "rat", Alignment.ENEMY, 5, false, Emote.NONE, List.of()))
                : List.of();
        return new Observation(header, map, new ActorsSection(actors), hero, new InventorySection(List.of()),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), new ActionsSection(List.of()),
                PromptSection.NONE);
    }

    private static List<Tile> dry() {
        return Collections.nCopies(9, Tile.EMPTY);
    }

    private static List<Tile> waterAt(int cell) {
        List<Tile> tiles = new ArrayList<>(dry());
        tiles.set(cell, Tile.WATER);
        return tiles;
    }

    private static List<SafeTest.Candidate> mostlyHealing() {
        return List.of(new SafeTest.Candidate(HEALING, "potion of healing", 0.9),
                new SafeTest.Candidate(FLAME, "potion of liquid flame", 0.1));
    }

    @Test
    @DisplayName("a lethal worst case is refused regardless of the mean")
    void lethal_worst_refused() {
        // Depth 4: burning deals up to 3 + 4/4 = 4 a turn for ten turns; 40 against 20 hit points.
        SafeTest.Verdict verdict = SafeTest.of(mostlyHealing(), standing(4, 20, dry(), false));
        assertFalse(verdict.safe(), verdict.why());
        assertEquals("potion of liquid flame", verdict.worst().name());
        assertEquals(40, verdict.worst().damage());
        assertTrue(verdict.meanDamage() < 20, "the mean is survivable: " + verdict.meanDamage());
        assertEquals(4.0, verdict.meanDamage(), 1e-9, "a tenth of forty");
        assertTrue(verdict.why().startsWith("lethal"), verdict.why());
    }

    @Test
    @DisplayName("standing next to water changes the verdict for a fire candidate")
    void water_beside() {
        assertFalse(SafeTest.of(mostlyHealing(), standing(4, 20, dry(), false)).safe());
        SafeTest.Verdict beside = SafeTest.of(mostlyHealing(), standing(4, 20, waterAt(1), false));
        assertTrue(beside.safe(), beside.why());
        assertEquals(8, beside.worst().damage(), "the hit, then the step into the water");
        SafeTest.Verdict diagonal = SafeTest.of(mostlyHealing(), standing(4, 20, waterAt(0), false));
        assertTrue(diagonal.safe(), "a diagonal step is a step: " + diagonal.why());
        SafeTest.Verdict on = SafeTest.of(mostlyHealing(), standing(4, 20, waterAt(4), false));
        assertEquals(4, on.worst().damage(), "standing in water, one hit");
        // Water far off is no help: a 3x3 floor has none beyond the neighbours, so dry is the control.
        assertFalse(SafeTest.of(mostlyHealing(), standing(4, 20, dry(), false)).safe());
    }

    @Test
    @DisplayName("the verdict turns on the hero's hit points, not on the odds")
    void hit_points_decide() {
        List<SafeTest.Candidate> rare = List.of(new SafeTest.Candidate(HEALING, "potion of healing", 0.999),
                new SafeTest.Candidate(FLAME, "potion of liquid flame", 0.001));
        assertFalse(SafeTest.of(rare, standing(1, 20, dry(), false)).safe(), "30 at depth 1 against 20");
        List<SafeTest.Candidate> sure = List.of(new SafeTest.Candidate(FLAME, "potion of liquid flame", 1));
        assertTrue(SafeTest.of(sure, standing(1, 20, waterAt(3), false)).safe(), "6 against 20");
        assertFalse(SafeTest.of(sure, standing(1, 6, waterAt(3), false)).safe(), "6 against 6 is lethal");
    }

    @Test
    @DisplayName("potions, scrolls and wands go through the same code: a disabling worst case is refused beside an enemy")
    void one_code_path() {
        List<SafeTest.Candidate> potion = List.of(new SafeTest.Candidate(HEALING, "potion of healing", 0.8),
                new SafeTest.Candidate("items.potions.PotionOfParalyticGas", "potion of paralytic gas", 0.2));
        List<SafeTest.Candidate> scroll = List.of(new SafeTest.Candidate("items.scrolls.ScrollOfIdentify", "scroll of identify", 0.7),
                new SafeTest.Candidate("items.scrolls.ScrollOfRage", "scroll of rage", 0.3));
        List<SafeTest.Candidate> blinding = List.of(
                new SafeTest.Candidate("items.scrolls.ScrollOfRetribution", "scroll of retribution", 1));
        List<SafeTest.Candidate> wand = SafeTest.wand("wand of magic missile", false, false);

        for (List<SafeTest.Candidate> item : List.of(potion, scroll, blinding, wand)) {
            assertFalse(SafeTest.of(item, standing(1, 20, dry(), true)).safe(), "with a rat in view: " + item);
        }
        assertTrue(SafeTest.of(potion, standing(1, 20, dry(), false)).safe(), "paralysed alone is survivable");
        assertTrue(SafeTest.of(scroll, standing(1, 20, dry(), false)).safe(), "rage with nobody in view");
        assertTrue(SafeTest.of(blinding, standing(1, 20, dry(), false)).safe(), "blinded alone");
    }

    @Test
    @DisplayName("a wand whose curse is shown is judged on what it is; a hidden curse is a candidate")
    void wands() {
        List<SafeTest.Candidate> hidden = SafeTest.wand("wand of lightning", false, false);
        assertEquals(2, hidden.size());
        assertEquals(1.0, hidden.stream().mapToDouble(SafeTest.Candidate::probability).sum(), 1e-12);
        assertTrue(SafeTest.of(SafeTest.wand("wand of lightning", true, false), standing(1, 20, dry(), true)).safe(),
                "an uncursed wand fires at its target");
        SafeTest.Verdict cursed = SafeTest.of(SafeTest.wand("wand of lightning", true, true), standing(1, 20, dry(), false));
        // Burning on the hero, eight turns at up to 3: 24, more than the bolt's 10.
        assertEquals(24, cursed.worst().damage());
        assertFalse(cursed.safe(), cursed.why());
        assertTrue(SafeTest.of(SafeTest.wand("wand of lightning", true, true), standing(1, 20, waterAt(5), false)).safe(),
                "beside water the burn is two hits, and the bolt's ten is the worst");
    }

    @Test
    @DisplayName("a guess's candidates carry the Codex's class names, and an item has at least one")
    void from_beliefs() {
        Beliefs.Guess guess = new Beliefs.Guess("crimson potion", ItemKind.POTION,
                List.of(new Beliefs.Odds("potion of healing", 0.6), new Beliefs.Odds("potion of frost", 0.4)));
        List<SafeTest.Candidate> candidates = SafeTest.candidates(guess, Screens.CODEX);
        assertEquals(List.of(new SafeTest.Candidate(HEALING, "potion of healing", 0.6),
                new SafeTest.Candidate("items.potions.PotionOfFrost", "potion of frost", 0.4)), candidates);
        assertFalse(SafeTest.of(candidates, standing(1, 20, dry(), true)).safe(), "frozen beside a rat");
        assertThrows(IllegalArgumentException.class, () -> SafeTest.candidates(new Beliefs.Guess("crimson potion",
                ItemKind.POTION, List.of(new Beliefs.Odds("potion of nothing", 1))), Screens.CODEX));
        assertThrows(IllegalArgumentException.class, () -> SafeTest.of(List.of(), standing(1, 20, dry(), false)));
    }

    @Test
    @DisplayName("the worst case is ordered worst first, and toxic gas scales with depth")
    void ordering_and_gas() {
        List<SafeTest.Candidate> gas = List.of(new SafeTest.Candidate("items.potions.PotionOfToxicGas", "potion of toxic gas", 1));
        assertEquals(10, SafeTest.of(gas, standing(1, 20, dry(), false)).worst().damage(), "1 a turn for ten turns");
        assertEquals(30, SafeTest.of(gas, standing(10, 20, dry(), false)).worst().damage(), "1 + 10/5 a turn at depth 10");
        SafeTest.Verdict both = SafeTest.of(List.of(new SafeTest.Candidate(HEALING, "potion of healing", 0.5),
                gas.get(0)), standing(1, 20, dry(), false));
        assertEquals("potion of toxic gas", both.harms().get(0).name());
        assertEquals("potion of healing", both.harms().get(1).name());
    }
}
