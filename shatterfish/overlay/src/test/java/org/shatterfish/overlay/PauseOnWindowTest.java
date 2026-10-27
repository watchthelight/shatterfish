package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndSettings;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.MemoryPreferences;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.PauseOn.Condition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Pause-on section, built headless against the game's own toolkit (story 8.4, FR-45, UX-DR15): the window
 * is a real {@code Window} of {@code CheckBox}es and {@code RedButton}es on a {@code HeadlessScene}, each
 * control is pressed through its own click handler, and what the window sets is what the store keeps and what
 * the Run's controls act on at the next Input wait. The settings screen's button (hook row 12) is checked on
 * a real {@code WndSettings}. No window is opened on a screen.
 */
class PauseOnWindowTest {

    private static final long SEED = 12345;

    private static Observation base;

    private final List<OverlaySettings> heard = new ArrayList<>();

    @BeforeAll
    static void boot() {
        base = PauseOnTest.firstWait();
    }

    @BeforeEach
    void aScene() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        boot.game().switchTo(new HeadlessScene());
    }

    @AfterEach
    void tidy() {
        Hooks.settingsSection = null;
        HeadlessBoot.ensure().game().destroy();
    }

    private PauseOnWindow window(OverlaySettings settings) {
        return new PauseOnWindow(settings, heard::add);
    }

    @Test
    @DisplayName("a toggle per condition: pressing each through its own handler sets it, then clears it, and the sink hears both")
    void each_toggle_flips_its_condition() {
        PauseOnWindow window = window(OverlaySettings.DEFAULTS);
        assertEquals(Condition.values().length, EnumSet.allOf(Condition.class).stream()
                .filter(c -> window.toggle(c) != null).count(), "one toggle for each of the six");
        for (Condition condition : Condition.values()) {
            window.toggle(condition).click();
            assertTrue(window.settings().pauseOn().contains(condition), condition + " on");
            assertEquals(window.settings(), heard.get(heard.size() - 1), "the sink heard it");
            assertTrue(window.toggle(condition).checked(), "and the box shows it");
        }
        assertEquals(EnumSet.allOf(Condition.class), EnumSet.copyOf(window.settings().pauseOn()), "all six on");
        for (Condition condition : Condition.values()) {
            window.toggle(condition).click();
            assertFalse(window.settings().pauseOn().contains(condition), condition + " off");
        }
        assertEquals(OverlaySettings.DEFAULTS, window.settings());
        assertEquals(12, heard.size(), "one change per press");
    }

    @Test
    @DisplayName("the boxes open as the settings were")
    void the_boxes_open_as_set() {
        PauseOnWindow window = window(new OverlaySettings(EnumSet.of(Condition.BOSS_FLOOR), 60));
        for (Condition condition : Condition.values()) {
            assertEquals(condition == Condition.BOSS_FLOOR, window.toggle(condition).checked(), condition.toString());
        }
        assertTrue(window.hpLabelText().contains("60"), window.hpLabelText());
    }

    @Test
    @DisplayName("the stepper moves the threshold by five and stops at both ends")
    void the_stepper_steps_and_stops() {
        PauseOnWindow window = window(OverlaySettings.DEFAULTS);
        assertTrue(window.hpLabelText().contains("30"), window.hpLabelText());
        window.moreButton().click();
        assertEquals(35, window.settings().hpThresholdPercent());
        assertTrue(window.hpLabelText().contains("35"), "the label follows: " + window.hpLabelText());
        window.lessButton().click();
        window.lessButton().click();
        assertEquals(25, window.settings().hpThresholdPercent());
        assertEquals(3, heard.size());

        for (int i = 0; i < 40; i++) {
            window.moreButton().click();
        }
        assertEquals(OverlaySettings.HP_MAX, window.settings().hpThresholdPercent(), "held at the top");
        int heardAtTop = heard.size();
        window.moreButton().click();
        assertEquals(heardAtTop, heard.size(), "a press that changes nothing tells nobody");
        for (int i = 0; i < 40; i++) {
            window.lessButton().click();
        }
        assertEquals(OverlaySettings.HP_MIN, window.settings().hpThresholdPercent(), "held at the bottom");
    }

    @Test
    @DisplayName("what the window sets is stored, and a new store reads it back (the restart)")
    void what_is_set_is_stored() {
        MemoryPreferences preferences = new MemoryPreferences();
        OverlayStore store = new OverlayStore(preferences);
        PauseOnWindow window = new PauseOnWindow(store.load(), store::save);
        window.toggle(Condition.STAIRS).click();
        window.toggle(Condition.HP_BELOW).click();
        window.lessButton().click();

        OverlaySettings expected = new OverlaySettings(EnumSet.of(Condition.STAIRS, Condition.HP_BELOW), 25);
        assertEquals(expected, window.settings());
        assertEquals(expected, new OverlayStore(preferences).load(), "read by a store that did not write it");
        assertEquals(expected, new PauseOnWindow(new OverlayStore(preferences).load(), s -> { }).settings(),
                "and the window opened next time shows it");
    }

    @Test
    @DisplayName("a change in the section during a Run takes effect at the next Input wait")
    void a_change_takes_effect_at_the_next_wait() {
        PauseOnControlsTest.Run run = new PauseOnControlsTest.Run();
        ControlState controls = new ControlState(run, Capabilities.NOW, OverlaySettings.DEFAULTS, Set.of(5, 10, 15));
        PauseOnWindow window = new PauseOnWindow(OverlaySettings.DEFAULTS, controls::settings);
        controls.pauseResume();
        assertEquals(Mode.RUNNING, controls.mode());

        run.land(1, base, new Action.Descend());
        controls.frame(0.016);
        run.serve();
        assertEquals(Mode.RUNNING, controls.mode(), "nothing enabled: the stairs wait goes by");
        assertEquals(List.of(1L), run.executed);

        window.toggle(Condition.STAIRS).click();
        assertEquals(Mode.RUNNING, controls.mode(), "the change alone stops nothing");
        run.land(2, base, new Action.Descend());
        controls.frame(0.016);
        run.serve();
        assertEquals(Mode.PAUSED, controls.mode(), "the next wait is held for the condition just set");
        assertEquals("before stairs", controls.pausedFor());
        assertEquals(List.of(1L), run.executed, "before it executes");
    }

    @Test
    @DisplayName("every string the section shows is in the Overlay's message file, and no key is missing")
    void every_string_is_in_the_message_file() {
        List<String> asked = new ArrayList<>(List.of("settings.section", "settings.title", "settings.pause_hint",
                "settings.hp_label", "settings.hp_less", "settings.hp_more"));
        for (Condition condition : Condition.values()) {
            asked.add("settings.cond." + condition.name().toLowerCase(java.util.Locale.ROOT));
        }
        for (String key : asked) {
            assertTrue(OverlayMessages.keys().contains(key), key + " is not in overlay.properties");
            assertFalse(OverlayMessages.get(key, 1).startsWith(OverlayMessages.NO_TEXT_FOUND), key);
        }
        assertEquals(new java.util.TreeSet<>(asked), new java.util.TreeSet<>(OverlayMessages.keys()),
                "the file holds the strings the section asks for and none it does not");
        assertTrue(OverlayMessages.get("not.a.key").startsWith(OverlayMessages.NO_TEXT_FOUND), "a missing key says so");
        assertEquals("HP below: 45%", OverlayMessages.get("settings.hp_label", 45));
    }

    // --- The hook (row 12) on the game's real settings screen ---

    private static List<com.shatteredpixel.shatteredpixeldungeon.ui.RedButton> buttons(Group group) {
        List<com.shatteredpixel.shatteredpixeldungeon.ui.RedButton> found = new ArrayList<>();
        for (Gizmo member : group.shatterfishMembers()) {
            if (member instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RedButton button) {
                found.add(button);
            }
            if (member instanceof Group inner) {
                found.addAll(buttons(inner));
            }
        }
        return found;
    }

    private static com.shatteredpixel.shatteredpixeldungeon.ui.RedButton sectionButton(String title) {
        WndSettings settings = new WndSettings();
        for (var button : buttons(settings)) {
            if (title.equals(button.text())) {
                return button;
            }
        }
        return null;
    }

    @Test
    @DisplayName("the game's settings screen shows the section's button only when the Overlay registered one, and it opens the window")
    void the_settings_screen_shows_the_button() throws Exception {
        assertNull(sectionButton(OverlayMessages.get("settings.section")), "the unmodified game shows no button");

        List<String> opened = new ArrayList<>();
        Hooks.settingsSection = new PauseOnSection(() -> OverlaySettings.DEFAULTS, heard::add) {
            @Override
            public void open() {
                opened.add("open");
                assertNotNull(window());
            }
        };
        var button = sectionButton(OverlayMessages.get("settings.section"));
        assertNotNull(button, "with a section registered the Interface tab shows its button");
        Method click = com.shatteredpixel.shatteredpixeldungeon.ui.Button.class.getDeclaredMethod("onClick");
        click.setAccessible(true);
        click.invoke(button);
        assertEquals(List.of("open"), opened, "pressing the button opens the section");

        Hooks.clear();
        assertNull(sectionButton(OverlayMessages.get("settings.section")), "and a cleared registry shows none");
        assertNotNull(Game.scene(), "the scene stood throughout");
    }
}
