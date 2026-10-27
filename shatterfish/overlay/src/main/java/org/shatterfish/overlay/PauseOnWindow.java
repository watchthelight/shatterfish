package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.CheckBox;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Pause-on section of the game's settings screen (story 8.4, FR-45, UX-DR15): a toggle for each of the six
 * conditions and a stepper for the HP threshold, drawn with the game's own window, {@link CheckBox} and
 * {@link RedButton}, with every word from {@link OverlayMessages} (non-negotiable 6). It edits an
 * {@link OverlaySettings} value and hands each new one to {@code sink}; it stores nothing and starts nothing.
 * The Overlay opens it from the button the settings screen's Interface tab shows (hook row 12) and the sink
 * stores the value and gives it to the Run's controls, which look at it at the next Input wait.
 *
 * <p>Each handler works from the window's own current value, not from the one it was opened with, so two
 * quick presses are two changes. The threshold moves in steps of {@value #STEP} percent and stops at
 * {@link OverlaySettings#HP_MIN} and {@link OverlaySettings#HP_MAX}.
 */
final class PauseOnWindow extends Window {

    static final int WIDTH = 120;
    static final int STEP = 5;
    private static final int BTN_HEIGHT = 16;
    private static final int STEPPER_WIDTH = 20;
    private static final int GAP = 2;

    private final Consumer<OverlaySettings> sink;
    private final Map<PauseOn.Condition, Toggle> toggles = new EnumMap<>(PauseOn.Condition.class);
    private final RenderedTextBlock hpLabel;
    private final Step less;
    private final Step more;
    private OverlaySettings settings;

    PauseOnWindow(OverlaySettings current, Consumer<OverlaySettings> sink) {
        super();
        this.settings = current;
        this.sink = sink;

        RenderedTextBlock title = PixelScene.renderTextBlock(OverlayMessages.get("settings.title"), 9);
        title.hardlight(TITLE_COLOR);
        title.setPos((WIDTH - title.width()) / 2, GAP);
        add(title);

        RenderedTextBlock hint = PixelScene.renderTextBlock(OverlayMessages.get("settings.pause_hint"), 6);
        hint.maxWidth(WIDTH);
        hint.setPos(0, title.bottom() + GAP * 2);
        add(hint);

        float y = hint.bottom() + GAP * 2;
        for (PauseOn.Condition condition : PauseOn.Condition.values()) {
            Toggle toggle = new Toggle(condition);
            toggle.checked(current.pauseOn().contains(condition));
            toggle.setRect(0, y, WIDTH, BTN_HEIGHT);
            add(toggle);
            toggles.put(condition, toggle);
            y = toggle.bottom() + GAP;
        }

        less = new Step(OverlayMessages.get("settings.hp_less"), -STEP);
        less.setRect(0, y, STEPPER_WIDTH, BTN_HEIGHT);
        add(less);
        more = new Step(OverlayMessages.get("settings.hp_more"), STEP);
        more.setRect(WIDTH - STEPPER_WIDTH, y, STEPPER_WIDTH, BTN_HEIGHT);
        add(more);
        hpLabel = PixelScene.renderTextBlock(hpText(), 8);
        add(hpLabel);
        centreLabel(y);

        resize(WIDTH, (int) Math.ceil(y + BTN_HEIGHT));
    }

    private String hpText() {
        return OverlayMessages.get("settings.hp_label", settings.hpThresholdPercent());
    }

    private void centreLabel(float rowTop) {
        hpLabel.text(hpText());
        hpLabel.setPos((WIDTH - hpLabel.width()) / 2, rowTop + (BTN_HEIGHT - hpLabel.height()) / 2);
    }

    /** The settings as the window holds them now. */
    OverlaySettings settings() {
        return settings;
    }

    /** A toggle was changed to {@code on}: the settings change, and the sink hears them. */
    private void toggled(PauseOn.Condition condition, boolean on) {
        Set<PauseOn.Condition> conditions = EnumSet.noneOf(PauseOn.Condition.class);
        conditions.addAll(settings.pauseOn());
        if (on) {
            conditions.add(condition);
        } else {
            conditions.remove(condition);
        }
        changed(settings.withPauseOn(conditions));
    }

    /** The threshold moved by {@code delta} percent, held inside its range. */
    void stepHp(int delta) {
        int moved = Math.max(OverlaySettings.HP_MIN,
                Math.min(OverlaySettings.HP_MAX, settings.hpThresholdPercent() + delta));
        if (moved != settings.hpThresholdPercent()) {
            changed(settings.withHpThreshold(moved));
            centreLabel(less.top());
        }
    }

    private void changed(OverlaySettings next) {
        settings = next;
        sink.accept(next);
    }

    /** The toggle for {@code condition}, for the test that presses it through its own handler. */
    Toggle toggle(PauseOn.Condition condition) {
        return toggles.get(condition);
    }

    Step lessButton() {
        return less;
    }

    Step moreButton() {
        return more;
    }

    String hpLabelText() {
        return hpLabel.text();
    }

    /** One condition's check box: its own click handler flips it and tells the window. */
    final class Toggle extends CheckBox {

        private final PauseOn.Condition condition;

        Toggle(PauseOn.Condition condition) {
            super(OverlayMessages.get("settings.cond." + condition.name().toLowerCase(java.util.Locale.ROOT)));
            this.condition = condition;
        }

        @Override
        protected void onClick() {
            super.onClick();
            toggled(condition, checked());
        }

        /** Presses the toggle as a tap on it does: the component's own handler. */
        void click() {
            onClick();
        }
    }

    /** One end of the threshold stepper. */
    final class Step extends RedButton {

        private final int delta;

        Step(String label, int delta) {
            super(label);
            this.delta = delta;
        }

        @Override
        protected void onClick() {
            super.onClick();
            stepHp(delta);
        }

        void click() {
            onClick();
        }
    }
}
