package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.ui.Component;
import org.shatterfish.overlay.ControlEnablement.Control;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Panel's Controls row (story 5.6, FR-39, UX-DR7, {@code DESIGN.md} Components): Pause and Resume,
 * Next Step, Run N with its count stepper, the speed selector with its interval stepper, Take over and
 * Hand back, and Explain, in one row that wraps to a second when the Panel is too narrow for one.
 *
 * <p>Only the game's own toolkit: {@code RedButton} for every button (a stepper is two small ones
 * flanking a number), the pixel font through {@code PixelScene.renderTextBlock}, and no custom shape.
 * The speed selector is one {@code RedButton} that cycles Next Step, Human, Fast and shows the current
 * one, where {@code DESIGN.md} sketches tabs: the cycle is what EXPERIENCE.md's F7 does, and a button is
 * one toolkit part where three tabs would be three. Recorded in the story's decisions.
 *
 * <p>A control whose action is impossible in the current state is dim ({@code ink-dim}) and takes no
 * press: {@link ControlEnablement} says which, and this row draws it. While a window of the game's is in
 * front, every control is off whatever the matrix says, so a tap the executor posts for the window's
 * button cannot land on one (the fairness review of story 5.3, applied to every hot area of the Panel).
 */
final class ControlsRow extends Component {

    /** Small size ({@code DESIGN.md} Typography: controls take the body size; the Panel's width takes this one). */
    static final int TEXT_SIZE = 6;
    static final float BUTTON_HEIGHT = 11;
    static final float GAP = 2;
    /** A stepper's minus and plus: a square the button's height. */
    static final float STEP_WIDTH = BUTTON_HEIGHT;
    /** Ink and ink-dim ({@code DESIGN.md} Colors): a control that acts, and one that does not. */
    static final int INK = 0xF0F0F0;
    static final int INK_DIM = 0x5E5E5E;

    private RedButton pause;
    private RedButton next;
    private RedButton runN;
    private RedButton countMinus;
    private RedButton countPlus;
    private RedButton speed;
    private RedButton intervalMinus;
    private RedButton intervalPlus;
    private RedButton takeOver;
    private RedButton explain;
    private RenderedTextBlock countValue;
    private RenderedTextBlock intervalValue;
    private float countSlot;
    private float intervalSlot;

    private ControlState state;
    private Runnable explainAction = () -> { };
    private ModeState mode;
    private Set<Control> enabled = Set.of();
    private boolean locked;
    private boolean explainable;

    @Override
    protected void createChildren() {
        pause = button("Pause", () -> state.pauseResume());
        next = button("Next Step", () -> state.nextStep());
        runN = button("Run N", () -> state.runN());
        countMinus = button("-", () -> state.countDown());
        countPlus = button("+", () -> state.countUp());
        speed = button("Next Step", () -> state.cycleSpeed());
        intervalMinus = button("-", () -> state.intervalDown());
        intervalPlus = button("+", () -> state.intervalUp());
        takeOver = button("Take over", () -> state.takeOverHandBack());
        explain = new RedButton("Explain", TEXT_SIZE) {
            @Override
            protected void onClick() {
                explainAction.run();
            }
        };
        add(explain);
        countValue = value();
        intervalValue = value();
        // The widest each number gets, so the row does not shift as a value changes.
        countSlot = widest("888");
        intervalSlot = widest("8.8s");
    }

    private RedButton button(String label, java.util.function.BooleanSupplier press) {
        RedButton button = new RedButton(label, TEXT_SIZE) {
            @Override
            protected void onClick() {
                if (state != null) {
                    press.getAsBoolean();
                }
            }
        };
        add(button);
        return button;
    }

    private RenderedTextBlock value() {
        RenderedTextBlock block = PixelScene.renderTextBlock(TEXT_SIZE);
        block.hardlight(INK);
        add(block);
        return block;
    }

    private static float widest(String text) {
        return PixelScene.renderTextBlock(text, TEXT_SIZE).width();
    }

    /**
     * Binds the Brain's Run's controls, and what Explain does. Without a bound state (a HUMAN Run, a
     * test) the buttons draw by the matrix and a press does nothing: only Explain acts.
     */
    void bind(ControlState state, Runnable explainAction) {
        this.state = state;
        this.explainAction = explainAction;
    }

    /**
     * Sets what the row shows: {@code mode} is the Mode strip's state, {@code enabled} the matrix for it
     * (less what is not built, {@link ControlEnablement#of}), {@code locked} whether a window of the game's
     * is in front, and {@code explainable} whether there is a Decision to explain.
     */
    void content(ModeState mode, Set<Control> enabled, boolean locked, boolean explainable) {
        this.mode = mode;
        this.enabled = enabled;
        this.locked = locked;
        this.explainable = explainable;
        pause.text(ControlEnablement.pauseLabel(mode.mode()));
        takeOver.text(ControlEnablement.takeOverLabel(mode.mode()));
        speed.text(speedLabel(mode));
        countValue.text(String.valueOf(state == null ? ControlState.COUNT_DEFAULT : state.count()));
        intervalValue.text(Columns.seconds(state == null ? ControlState.INTERVAL_DEFAULT : state.interval()));
        set(pause, Control.PAUSE_RESUME);
        set(next, Control.NEXT_STEP);
        set(runN, Control.RUN_N);
        set(countMinus, Control.RUN_N_COUNT);
        set(countPlus, Control.RUN_N_COUNT);
        set(speed, Control.SPEED);
        set(intervalMinus, Control.INTERVAL);
        set(intervalPlus, Control.INTERVAL);
        set(takeOver, Control.TAKE_OVER);
        set(explain, Control.EXPLAIN, explainable);
        countValue.hardlight(on(Control.RUN_N_COUNT, true) ? INK : INK_DIM);
        intervalValue.hardlight(on(Control.INTERVAL, true) ? INK : INK_DIM);
        if (width > 0) {
            layout();
        }
    }

    /** The speed selector's label: the current speed mode's word, with the interval when it has one and one is built. */
    private static String speedLabel(ModeState mode) {
        return mode.speed().word;
    }

    private boolean on(Control control, boolean also) {
        return also && !locked && enabled.contains(control);
    }

    private void set(RedButton button, Control control) {
        set(button, control, true);
    }

    /** A control that acts is ink and takes presses; one that cannot is ink-dim and does not. */
    private void set(RedButton button, Control control, boolean also) {
        boolean acts = on(control, also);
        button.active = acts;
        button.textColor(acts ? INK : INK_DIM);
    }

    /** Whether {@code control} acts now: the matrix, no window in front, and (for Explain) a Decision. */
    boolean acts(Control control) {
        return on(control, control != Control.EXPLAIN || explainable);
    }

    /**
     * Presses {@code control} as its button would, for a hotkey (story 5.11): only if it acts now (the
     * matrix, no window in front, and for Explain a Decision), and by the same call the button makes, so a
     * key can do nothing a press on the button could not. A control with no hotkey is never pressed here.
     *
     * @return whether the control acted
     */
    boolean press(Control control) {
        if (!acts(control)) {
            return false;
        }
        switch (control) {
            case EXPLAIN:
                explainAction.run();
                return true;
            case PAUSE_RESUME:
                return state != null && state.pauseResume();
            case NEXT_STEP:
                return state != null && state.nextStep();
            case SPEED:
                return state != null && state.cycleSpeed();
            case TAKE_OVER:
                return state != null && state.takeOverHandBack();
            default:
                return false;
        }
    }

    /** The row's atoms, left to right, each the width it asks for; a stepper's number is one atom of its slot. */
    private List<Atom> atoms() {
        List<Atom> atoms = new ArrayList<>();
        atoms.add(new Atom(width(pause, "Resume"), pause));
        atoms.add(new Atom(width(next), next));
        atoms.add(new Atom(width(runN), runN));
        atoms.add(stepper(countMinus, countValue, countPlus, countSlot));
        atoms.add(new Atom(width(speed, "Next Step"), speed));
        atoms.add(stepper(intervalMinus, intervalValue, intervalPlus, intervalSlot));
        atoms.add(new Atom(width(takeOver, "Hand back"), takeOver));
        atoms.add(new Atom(width(explain), explain));
        return atoms;
    }

    private static Atom stepper(RedButton minus, RenderedTextBlock value, RedButton plus, float slot) {
        return new Atom(STEP_WIDTH + GAP + slot + GAP + STEP_WIDTH, minus, value, plus);
    }

    private static float width(RedButton button) {
        return button.reqWidth() + 4;
    }

    /** A button that changes its label, as wide as its widest label, so the row does not shift when it changes. */
    private static float width(RedButton button, String widest) {
        return Math.max(width(button), PixelScene.renderTextBlock(widest, TEXT_SIZE).width() + 6);
    }

    /** One placed thing: its width, and the parts drawn in it (a stepper is three). */
    private record Atom(float width, Component... parts) {
    }

    /** The height this row needs at the width it has, before it is positioned. */
    float contentHeight() {
        int rows = rows();
        return rows * BUTTON_HEIGHT + (rows - 1) * GAP;
    }

    private int rowsAt(float available) {
        int count = 1;
        float used = 0;
        for (Atom atom : atoms()) {
            if (used > 0 && used + GAP + atom.width() > available) {
                count++;
                used = 0;
            }
            used += (used > 0 ? GAP : 0) + atom.width();
        }
        return count;
    }

    /** How many rows the buttons take at the row's width now: UX-DR7 says one, wrapping to two. */
    int rows() {
        return rowsAt(width > 0 ? width : Float.MAX_VALUE);
    }

    @Override
    protected void layout() {
        if (explain == null || mode == null) {
            return;
        }
        float rowX = 0;
        float rowY = y;
        for (Atom atom : atoms()) {
            if (rowX > 0 && rowX + GAP + atom.width() > width) {
                rowX = 0;
                rowY += BUTTON_HEIGHT + GAP;
            }
            float left = x + rowX + (rowX > 0 ? GAP : 0);
            if (atom.parts.length == 1) {
                ((RedButton) atom.parts[0]).setRect(left, rowY, atom.width(), BUTTON_HEIGHT);
            } else {
                RedButton minus = (RedButton) atom.parts[0];
                RenderedTextBlock value = (RenderedTextBlock) atom.parts[1];
                RedButton plus = (RedButton) atom.parts[2];
                float slot = atom.width() - 2 * (STEP_WIDTH + GAP);
                minus.setRect(left, rowY, STEP_WIDTH, BUTTON_HEIGHT);
                value.setPos(left + STEP_WIDTH + GAP + (slot - value.width()) / 2f, rowY + (BUTTON_HEIGHT - value.height()) / 2f);
                PixelScene.align(value);
                plus.setRect(left + STEP_WIDTH + GAP + slot + GAP, rowY, STEP_WIDTH, BUTTON_HEIGHT);
            }
            rowX += (rowX > 0 ? GAP : 0) + atom.width();
        }
    }

    /** The controls, for tests. */
    RedButton pause() {
        return pause;
    }

    RedButton next() {
        return next;
    }

    RedButton runN() {
        return runN;
    }

    RedButton countMinus() {
        return countMinus;
    }

    RedButton countPlus() {
        return countPlus;
    }

    RedButton speed() {
        return speed;
    }

    RedButton intervalMinus() {
        return intervalMinus;
    }

    RedButton intervalPlus() {
        return intervalPlus;
    }

    RedButton takeOver() {
        return takeOver;
    }

    RedButton explain() {
        return explain;
    }

    RenderedTextBlock countValue() {
        return countValue;
    }

    RenderedTextBlock intervalValue() {
        return intervalValue;
    }

    /** The button for {@code control}, for tests that walk the matrix: a stepper answers with its plus button. */
    RedButton button(Control control) {
        return switch (control) {
            case PAUSE_RESUME -> pause;
            case NEXT_STEP -> next;
            case RUN_N -> runN;
            case RUN_N_COUNT -> countPlus;
            case SPEED -> speed;
            case INTERVAL -> intervalPlus;
            case TAKE_OVER -> takeOver;
            case EXPLAIN -> explain;
        };
    }
}
