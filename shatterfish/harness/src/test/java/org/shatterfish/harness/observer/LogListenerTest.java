package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.LogTone;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.scene.HeadlessScene;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The log section is the game's own message signal (ADR-0006, Log): every message {@code GLog}
 * dispatches, as it dispatches it, never the pane's merged and trimmed entries; and the listener
 * survives the scene's recreation, where the pane replaces every listener on the signal
 * ({@code …/ui/GameLog.java:47}), through hook row 3.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LogListenerTest {

    private static final long SEED = 31_415_926L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
    }

    /** The line the scene emits as it is created for a descent ({@code …/scenes/GameScene.java:596-599}). */
    private static LogLine descendLine(int depth) {
        return new LogLine(LogTone.HIGHLIGHT, Messages.format(Messages.get(GameScene.class, "descend"), depth));
    }

    /**
     * What the game does to take the hero down one floor: the play scene is destroyed when the
     * loading scene replaces it ({@code SPD-classes/…/noosa/Game.java:212-220}, {@code :246-258}),
     * the loading scene switches the level ({@code …/scenes/InterlevelScene.java:646-669}), and a
     * new play scene is created ({@code HeadlessGame.switchTo}). The actor thread is ended first,
     * since the headless game refuses to destroy a scene under a live one, and the old scene goes
     * before the level is rebuilt, since the level's construction updates a live scene's map.
     */
    private void descend() throws IOException {
        driver.stepper().endActorThread();
        HeadlessBoot.ensure().game().destroy();
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
        Mob.holdAllies(Dungeon.level);
        Dungeon.saveAll();
        Dungeon.depth++;
        Level level = Dungeon.newLevel();
        Dungeon.switchLevel(level, level.getTransition(LevelTransition.Type.REGULAR_ENTRANCE).cell());
        HeadlessBoot.ensure().game().switchTo(new HeadlessScene());
    }

    @Test
    @DisplayName("the first floor's lines come from the signal, with the pane's tones and the marker dropped")
    void the_signal_on_the_first_floor() {
        atTheFirstWait();
        Observer observer = new Observer();
        List<LogLine> lines = observer.log().lines();
        assertTrue(lines.contains(descendLine(1)), "the scene's own descent line is captured: " + lines);
        assertEquals(2, GLog.update.numListeners(), "the pane and the Observer's listener, and no other");

        int before = lines.size();
        GLog.i("plain words");
        GLog.p("good news");
        GLog.n("bad news");
        GLog.w("a warning");
        GLog.h("a highlight");
        GLog.newLine();
        List<LogLine> after = observer.log().lines();
        assertEquals(before + 5, after.size(), "five messages and no line for the marker: " + after);
        assertEquals(List.of(new LogLine(LogTone.PLAIN, "plain words"), new LogLine(LogTone.POSITIVE, "good news"),
                        new LogLine(LogTone.NEGATIVE, "bad news"), new LogLine(LogTone.WARNING, "a warning"),
                        new LogLine(LogTone.HIGHLIGHT, "a highlight")),
                after.subList(before, after.size()));
    }

    @Test
    @DisplayName("the listener is re-registered on every scene creation: two floor changes, and the log still receives")
    void the_listener_survives_two_floor_changes() throws IOException {
        atTheFirstWait();
        Observer observer = new Observer();
        assertTrue(observer.log().lines().contains(descendLine(1)));

        descend();
        assertEquals(2, Dungeon.depth);
        List<LogLine> second = observer.log().lines();
        assertTrue(second.contains(descendLine(2)),
                "the new scene's descent line, emitted inside create() after the pane replaced the listener: " + second);
        assertEquals(2, GLog.update.numListeners(), "the new pane and the one listener, re-added once");
        GLog.i("on the second floor");
        assertEquals(new LogLine(LogTone.PLAIN, "on the second floor"), last(observer.log()));

        descend();
        assertEquals(3, Dungeon.depth);
        List<LogLine> third = observer.log().lines();
        assertTrue(third.contains(descendLine(3)), third.toString());
        assertTrue(third.contains(descendLine(2)), "the earlier floors' lines are kept: " + third);
        assertEquals(2, GLog.update.numListeners());
        GLog.w("on the third floor");
        assertEquals(new LogLine(LogTone.WARNING, "on the third floor"), last(observer.log()));

        // The pane, by contrast, starts each scene from its wiped static entries and merges same-tone
        // messages (GameLog.java:89-95); the section holds every message as its own line.
        GLog.p("one");
        GLog.p("two");
        List<LogLine> lines = observer.log().lines();
        assertEquals(new LogLine(LogTone.POSITIVE, "two"), lines.get(lines.size() - 1));
        assertEquals(new LogLine(LogTone.POSITIVE, "one"), lines.get(lines.size() - 2));
    }

    @Test
    @DisplayName("a burst keeps the newest lines up to the section's cap, and a new Run starts empty")
    void the_cap_and_the_reset() {
        atTheFirstWait();
        Observer observer = new Observer();
        for (int i = 0; i < 100; i++) {
            GLog.i("line " + i);
        }
        List<LogLine> lines = observer.log().lines();
        assertEquals(LogSection.MAX_LINES, lines.size());
        assertEquals(new LogLine(LogTone.PLAIN, "line " + (100 - LogSection.MAX_LINES)), lines.get(0));
        assertEquals(new LogLine(LogTone.PLAIN, "line 99"), lines.get(lines.size() - 1));

        driver.close();
        driver = null;
        // The last pane stays on the signal until the next one replaces it (GameLog.java:47); the
        // Observer's listener has left, so a message dispatched now is not kept.
        int kept = GameLogListener.INSTANCE.lines().size();
        GLog.i("after the Run");
        assertEquals(kept, GameLogListener.INSTANCE.lines().size(), "closing the Run leaves the signal");
        atTheFirstWait();
        List<LogLine> fresh = new Observer().log().lines();
        assertFalse(fresh.stream().anyMatch(line -> line.text().startsWith("line ")), "the last Run's lines are gone");
        assertTrue(fresh.contains(descendLine(1)));
        GameLog.wipe();
        assertTrue(new Observer().log().lines().contains(descendLine(1)), "the pane's wipe is the pane's, not the signal's");
    }

    @Test
    @DisplayName("a message dispatched while the first floor is built, before any scene, is heard")
    void the_first_floor_being_built() {
        // newGame() builds the first floor and creates no scene (HeadlessDriver.newGame); the pane of
        // the last Run, if any, is still on the signal, and the next pane will draw the signal's
        // static buffer, so what is dispatched now reaches a human and must reach the Observer.
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        try {
            GLog.i("while the floor is built");
            assertTrue(GameLogListener.INSTANCE.lines().contains(new LogLine(LogTone.PLAIN, "while the floor is built")),
                    GameLogListener.INSTANCE.lines().toString());
        } finally {
            Hooks.clear();
            GameLogListener.uninstall();
        }
    }

    private static LogLine last(LogSection section) {
        return section.lines().get(section.lines().size() - 1);
    }
}
