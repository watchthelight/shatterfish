package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;

/**
 * The note the person types at the Overlay (story 5.9): the game's own text-input window
 * ({@code core/.../windows/WndTextInput.java:48-49}, non-negotiable 6), opened by the notes key. Saving
 * writes a {@code note} record at the wait that is open, or the last one confirmed, so the reasoning
 * sits next to the moment in the Run log; cancelling writes nothing. It is not a game Action: the
 * window reaches the game only as a window in front, which no wait is confirmed under, and nothing
 * it does is heard by the record of the person's turns.
 */
final class NoteWindow extends WndTextInput {

    static final String TITLE = "Note";
    static final String BODY = "Why this move? Saved in the Run log at this turn.";

    private final EmbeddedRun run;

    NoteWindow(EmbeddedRun run) {
        super(TITLE, BODY, "", RunLog.Note.MOST, true, "Save", "Cancel");
        this.run = run;
    }

    @Override
    public void onSelect(boolean positive, String text) {
        if (positive) {
            run.note(text);
        }
    }
}
