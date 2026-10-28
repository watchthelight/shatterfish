package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Overlay's side of hook row 12 (story 8.4): the button the game's settings screen shows on its Interface
 * tab, and what it opens. The listener is registered for the life of the Overlay's process, not of a Run, so
 * the section is there in a Run and in any scene the game shows its settings from; the unmodified game
 * registers nothing and shows no button.
 */
class PauseOnSection implements Hooks.SettingsSection {

    private final Supplier<OverlaySettings> current;
    private final Consumer<OverlaySettings> sink;

    /**
     * @param current the settings as they are now, read each time the window opens
     * @param sink    told each new value as it is set; it stores it and hands it to the Run's controls
     */
    PauseOnSection(Supplier<OverlaySettings> current, Consumer<OverlaySettings> sink) {
        this.current = current;
        this.sink = sink;
    }

    @Override
    public String title() {
        return OverlayMessages.get("settings.section");
    }

    @Override
    public void open() {
        ShatteredPixelDungeon.scene().addToFront(window());
    }

    /** The window the button opens. */
    PauseOnWindow window() {
        return new PauseOnWindow(current.get(), sink);
    }
}
