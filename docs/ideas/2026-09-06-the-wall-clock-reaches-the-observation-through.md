# The wall clock reaches the Observation through holiday items (found in story 1.10's review)

`Pasty.name()` and its sprite switch on `Holiday.getCurrentHoliday()`
(`core/.../items/food/Pasty.java:56-90`, `:175-193`), which reads the calendar
(`core/.../utils/Holiday.java:54-59`), so a hero holding a pasty, a common food drop, names it
differently on a holiday and two Runs of one tuple hash differently by date, against non-negotiable
5. Predates the Observer; nothing in the harness pins the holiday. The clean fix is a pinned holiday
in the Profile (story 1.15) or a hook that lets the harness fix the date; `Blandfruit` and the
other holiday-dressed sprites are the same question.

Resolved in issue #139: `RunStatics.reset()` pins `Holiday.cached` to `NONE` at every Run start, by the same reflection the other leftover statics use; no upstream file changes.
