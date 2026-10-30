package org.shatterfish.overlay;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.Brain;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;
import org.shatterfish.overlay.PauseOn.Condition;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Autoexplore (story 8.10, FR-47): the stop rules as a function, and what the controls do with them against a Run
 * that is a list of calls. The rules are pure, so each is a constructed Observation and a Decision; the controls
 * are driven through the same seam the Run implements, with a person flag the test turns as the Run does when a
 * hand back or a takeover applies. The same controls over a real Run on {@code EmbeddedHost} are
 * {@code AutoexploreRunTest}.
 *
 * <p>Every stop is asserted as control returned at the wait before the Action that the person would have
 * refused: the wait is never in the Run's executed list, and a takeover was asked for at it.
 */
class AutoexploreTest {

    private static final Set<Integer> BOSSES = Set.of(5, 10, 15, 20, 25);

    private static Observation base;

    @BeforeAll
    static void boot() {
        base = PauseOnTest.firstWait();
    }

    private static RunLog.Decision by(String policy, Action action) {
        return new RunLog.Decision("goal", new RunLog.Choice(action, 1, "why"), List.of(), List.of(), policy);
    }

    private static RunLog.Decision exploring() {
        return by("explore", new Action.Step(base.hero().cell()));
    }

    private static Optional<Autoexplore.Stop> stop(OverlaySettings settings, Observation observation,
                                                   RunLog.Decision decision, PauseOn.Previous previous) {
        return Autoexplore.stop(settings, observation, decision, previous, BOSSES);
    }

    // ---- the list and the rules, as a function ----

    @Test
    @DisplayName("the list names Policies the Brain has, and none that fights, descends, uses or throws")
    void the_list_names_real_policies() {
        List<String> brain = Brain.policyNames(Brain.Model.ONE_PLY);
        for (String name : Autoexplore.POLICIES) {
            assertTrue(brain.contains(name), name + " is a Policy of the Brain");
        }
        assertEquals(Set.of("explore", "pick-up"), Autoexplore.POLICIES);
        for (String never : List.of("fight", "one-ply", "ranged", "shoot", "zap", "descend", "plunge", "test-item", "equip", "heal",
                "eat", "douse", "open-chest", "answer-prompt", "fallback")) {
            assertFalse(Autoexplore.POLICIES.contains(never), never + " is never autoexplore's");
        }
    }

    @Test
    @DisplayName("a quiet screen and a Decision of the explore or pick-up Policy go on")
    void a_quiet_wait_goes_on() {
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, base, exploring(), null));
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, base, by("pick-up", new Action.PickUp()), null));
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, base, by("explore", new Action.Rest(true)),
                PauseOn.Previous.of(base)), "resting to heal is on the list");
    }

    @Test
    @DisplayName("a hostile actor in view stops it; an ally or a neutral one does not")
    void an_enemy_in_view_stops() {
        Optional<Autoexplore.Stop> found = stop(OverlaySettings.DEFAULTS, PauseOnTest.withEnemies(base, "rat"), exploring(), null);
        assertEquals(Autoexplore.Kind.ENEMY_IN_VIEW, found.orElseThrow().kind());
        assertEquals("enemy in view", found.orElseThrow().why());
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, base, exploring(), null), "no actor, no stop");
    }

    @Test
    @DisplayName("a Policy off the list stops it, whatever the screen shows, and names the Policy")
    void a_policy_off_the_list_stops() {
        for (String policy : List.of("fight", "descend", "test-item", "ranged", "shoot", "zap", "plunge", "equip", "eat", "heal")) {
            Autoexplore.Stop found = stop(OverlaySettings.DEFAULTS, base, by(policy, new Action.Wait()), null).orElseThrow();
            assertEquals(Autoexplore.Kind.OFF_LIST, found.kind(), policy);
            assertTrue(found.why().startsWith(policy), found.why());
        }
    }

    @Test
    @DisplayName("an Action a person would refuse stops it even under a Policy that is on the list")
    void the_backstop_under_the_list() {
        for (Action refused : List.of(new Action.Attack(5), new Action.Descend(), new Action.Ascend(),
                new Action.UseItem(new org.shatterfish.api.ItemRef(0, "potion", 1), "DRINK"),
                new Action.UseItemAt(new org.shatterfish.api.ItemRef(0, "dart", 1), "THROW", 4),
                new Action.Unlock(7), new Action.OpenChest(8), new Action.Buy(9), new Action.AnswerPrompt(0),
                new Action.DismissPrompt())) {
            assertEquals(Autoexplore.Kind.OFF_LIST, stop(OverlaySettings.DEFAULTS, base, by("explore", refused), null)
                    .orElseThrow().kind(), refused.toString());
        }
    }

    @Test
    @DisplayName("a boss floor stops it when the condition is enabled, and not when it is not")
    void a_boss_floor_stops() {
        Observation boss = PauseOnTest.onFloor(base, 5, 0);
        OverlaySettings enabled = OverlaySettings.DEFAULTS.withPauseOn(EnumSet.of(Condition.BOSS_FLOOR));
        Autoexplore.Stop found = stop(enabled, boss, exploring(), PauseOn.Previous.of(base)).orElseThrow();
        assertEquals(Autoexplore.Kind.PAUSE_ON, found.kind());
        assertEquals("on a boss floor", found.why());
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, boss, exploring(), PauseOn.Previous.of(base)));
    }

    @Test
    @DisplayName("hit points that fell since the wait before stop it; steady or rising ones do not")
    void a_low_hp_stretch_stops() {
        Observation full = PauseOnTest.withHp(base, 20, 20);
        Observation hurt = PauseOnTest.withHp(base, 17, 20);
        assertEquals(Autoexplore.Kind.HP_FELL,
                stop(OverlaySettings.DEFAULTS, hurt, exploring(), PauseOn.Previous.of(full)).orElseThrow().kind());
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, hurt, exploring(), PauseOn.Previous.of(hurt)));
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, full, exploring(), PauseOn.Previous.of(hurt)));
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, hurt, exploring(), null), "no wait before, nothing fell");
        // The fraction, not the number: a higher maximum with the same fraction is not a fall.
        assertEquals(Optional.empty(), stop(OverlaySettings.DEFAULTS, PauseOnTest.withHp(base, 30, 40), exploring(),
                PauseOn.Previous.of(PauseOnTest.withHp(base, 15, 20))));
    }

    @Test
    @DisplayName("no Decision to look at stops it: a step nobody can name is not on the list")
    void no_decision_stops() {
        assertEquals(Autoexplore.Kind.NO_DECISION, stop(OverlaySettings.DEFAULTS, base, null, null).orElseThrow().kind());
    }

    @Test
    @DisplayName("an enemy and a Policy off the list at once report the enemy, the first of the order")
    void two_at_once_report_the_first() {
        Autoexplore.Stop found = stop(OverlaySettings.DEFAULTS, PauseOnTest.withEnemies(base, "rat"),
                by("fight", new Action.Attack(3)), null).orElseThrow();
        assertEquals(Autoexplore.Kind.ENEMY_IN_VIEW, found.kind());
    }

    // ---- the oracle ----

    private static Observation oracleMarked(Observation from) {
        HeaderSection h = from.header();
        return new Observation(new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(),
                h.challenges(), h.depth(), h.branch(), h.sealed(), true, h.prompt()), from.map(), from.actors(),
                from.hero(), from.inventory(), from.journal(), from.log(), from.actions(), from.prompt());
    }

    @Test
    @DisplayName("an enemy at the edge of vision is the same stop on the same Observation with and without the oracle flag")
    void the_oracle_flag_changes_no_stop() {
        OverlaySettings every = OverlaySettings.all();
        List<Observation> screens = List.of(base, PauseOnTest.withEnemies(base, "rat"), PauseOnTest.onFloor(base, 5, 0),
                PauseOnTest.withHp(base, 3, 20));
        List<RunLog.Decision> decisions = List.of(exploring(), by("fight", new Action.Attack(2)),
                by("descend", new Action.Descend()), by("pick-up", new Action.PickUp()));
        int compared = 0;
        for (Observation screen : screens) {
            for (RunLog.Decision decision : decisions) {
                for (PauseOn.Previous previous : new PauseOn.Previous[]{null, PauseOn.Previous.of(base)}) {
                    for (OverlaySettings settings : List.of(OverlaySettings.DEFAULTS, every)) {
                        assertEquals(stop(settings, screen, decision, previous),
                                stop(settings, oracleMarked(screen), decision, previous),
                                "the oracle bit is not a reason to stop or to go on");
                        compared++;
                    }
                }
            }
        }
        assertTrue(oracleMarked(base).header().oracle() && !base.header().oracle(), "the copies differ in the flag");
        assertTrue(compared >= 64, "compared " + compared);
    }

    @Test
    @DisplayName("the stop rules cannot name the oracle, the harness or the game: they read the Observation and the Decision")
    void the_rules_cannot_see_the_oracle() {
        JavaClasses overlay = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.overlay");
        noClasses().that().haveFullyQualifiedName(Autoexplore.class.getName())
                .should().dependOnClassesThat().resideInAnyPackage("org.shatterfish.harness..", "com.shatteredpixel..",
                        "com.watabou..")
                .because("the stop rules read the Observation and the Decision only (non-negotiable 1)")
                .check(overlay);
    }

    // ---- the controls ----

    /** A Run that is a list of calls: one pending wait at a time, and a person who holds the game or does not. */
    static final class Run implements ControlState.Seam {
        final List<String> calls = new ArrayList<>();
        final List<Long> executed = new ArrayList<>();
        boolean held;
        int credits;
        boolean person = true;
        boolean ended;
        boolean thinking;
        ControlState.Pending pending;
        int handBacks;
        int takeOvers;

        void land(long wait, Observation observation, RunLog.Decision decision) {
            pending = new ControlState.Pending(wait, observation, decision);
        }

        /** The Run's frame: a held Run with a credit executes the pending Decision, as the Run does. */
        void serve() {
            if (pending != null && !person && held && credits > 0) {
                credits--;
                executed.add(pending.index());
                pending = null;
            }
        }

        @Override
        public void hold(boolean held) {
            this.held = held;
            this.credits = 0;
        }

        @Override
        public void advance(int waits) {
            calls.add("advance");
            if (held) {
                credits += waits;
            }
        }

        @Override
        public int credits() {
            return held ? credits : 0;
        }

        @Override
        public boolean thinking() {
            return thinking;
        }

        @Override
        public boolean ready() {
            return pending != null && !person && held && credits == 0;
        }

        @Override
        public boolean ended() {
            return ended;
        }

        @Override
        public void takeOver(String speed) {
            takeOvers++;
            calls.add("takeOver");
        }

        @Override
        public void handBack(String speed) {
            handBacks++;
            calls.add("handBack");
        }

        @Override
        public boolean humanTurn() {
            return person;
        }

        @Override
        public ControlState.Pending pending() {
            return ready() ? pending : null;
        }
    }

    private static ControlState controls(Run run, OverlaySettings settings) {
        return new ControlState(run, Capabilities.NOW, settings, BOSSES);
    }

    /** A Run whose person has the game: the controls opened PAUSED with Next Step, then Take over applied. */
    private static ControlState inHuman(Run run, OverlaySettings settings) {
        ControlState controls = controls(run, settings);
        assertTrue(controls.takeOverHandBack());
        run.takeOvers = 0;
        run.calls.clear();
        controls.frame(0.0);
        assertEquals(Mode.HUMAN, controls.mode());
        return controls;
    }

    /** Presses autoexplore and lets the Run apply the hand back, as it does at the person's open wait. */
    private static void start(Run run, ControlState controls) {
        assertTrue(controls.autoexplore());
        assertEquals(1, run.handBacks, "the hand back is the one Hand back asks for");
        run.person = false;
        controls.frame(0.0);
    }

    /** One frame of the Overlay: the controls look, then the Run serves a credit if it has one. */
    private static void frame(Run run, ControlState controls) {
        controls.frame(1.0);
        run.serve();
    }

    /** The Run applies a takeover asked for, as it does at the wait in front. */
    private static void handOver(Run run, ControlState controls) {
        run.person = true;
        controls.frame(0.0);
    }

    @Test
    @DisplayName("autoexplore is enabled from HUMAN only, and a press anywhere else does nothing")
    void only_from_human() {
        Run run = new Run();
        ControlState controls = controls(run, OverlaySettings.DEFAULTS);
        assertFalse(controls.enabled(Control.AUTOEXPLORE), "PAUSED");
        assertFalse(controls.autoexplore());
        controls.pauseResume();
        assertFalse(controls.enabled(Control.AUTOEXPLORE), "RUNNING");
        assertFalse(controls.autoexplore());
        assertEquals(0, run.handBacks);
        controls.takeOverHandBack();
        run.takeOvers = 0;
        controls.frame(0.0);
        assertTrue(controls.enabled(Control.AUTOEXPLORE), "HUMAN");
        assertFalse(ControlEnablement.of(Mode.HUMAN, false, new Capabilities(true, false)).contains(Control.AUTOEXPLORE),
                "a Run that cannot change hands cannot autoexplore");
        run.ended = true;
        assertFalse(controls.enabled(Control.AUTOEXPLORE), "Run over");
    }

    @Test
    @DisplayName("a stretch plays the Brain's Decisions one wait at a time, and the strip says autoexplore")
    void a_quiet_stretch_plays_on() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        assertTrue(controls.autoexploring());
        assertEquals(Mode.RUNNING, controls.mode());
        assertTrue(run.held, "held, so every Decision is looked at before it executes");
        for (long wait = 10; wait < 14; wait++) {
            run.land(wait, base, exploring());
            frame(run, controls);
            frame(run, controls);
        }
        assertEquals(List.of(10L, 11L, 12L, 13L), run.executed);
        assertTrue(controls.autoexploring());
        assertEquals(0, run.takeOvers);
        ModeState view = controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 100, 1, false));
        assertTrue(ModeStripContent.text(view).startsWith("RUNNING: autoexplore"), ModeStripContent.text(view));
    }

    @Test
    @DisplayName("an enemy walking into view: control returns at that wait, before its Decision executes, and the strip names it")
    void an_enemy_walks_into_view() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        run.land(20, base, exploring());
        frame(run, controls);
        frame(run, controls);
        assertEquals(List.of(20L), run.executed);
        // The next wait shows a rat, and the Brain's Decision is to walk on; the person would have looked first.
        run.land(21, PauseOnTest.withEnemies(base, "rat"), exploring());
        frame(run, controls);
        frame(run, controls);
        assertEquals(List.of(20L), run.executed, "the wait with the rat was not executed");
        assertEquals(1, run.takeOvers, "control was asked back at that wait");
        assertFalse(controls.autoexploring());
        assertEquals("enemy in view", controls.stoppedFor());
        handOver(run, controls);
        assertEquals(Mode.HUMAN, controls.mode(), "the person has the game, not a PAUSED Run");
        ModeState view = controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 100, 1, false));
        assertEquals("HUMAN: autoexplore stopped, enemy in view", ModeStripContent.text(view).substring(0,
                "HUMAN: autoexplore stopped, enemy in view".length()));
        // The reason stays until the person's first turn, then goes.
        ModeState later = controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 101, 1, false));
        assertNull(later.note());
    }

    @Test
    @DisplayName("the first wait of a stretch is looked at as well: a rat already in view is no stretch at all")
    void the_first_wait_is_looked_at() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        run.land(30, PauseOnTest.withEnemies(base, "rat"), exploring());
        frame(run, controls);
        assertEquals(List.of(), run.executed);
        assertEquals("enemy in view", controls.stoppedFor());
    }

    @Test
    @DisplayName("a stretch that reaches the end of the level stops there, at the descend it would not take")
    void the_end_of_the_level() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        for (long wait = 40; wait < 43; wait++) {
            run.land(wait, base, exploring());
            frame(run, controls);
            frame(run, controls);
        }
        assertEquals(3, run.executed.size());
        // Explored: the Brain's next Decision is the descend, which is not autoexplore's.
        run.land(43, base, by("descend", new Action.Descend()));
        frame(run, controls);
        frame(run, controls);
        assertEquals(3, run.executed.size(), "the descend was not executed");
        assertEquals(1, run.takeOvers);
        assertTrue(controls.stoppedFor().startsWith("descend is next"), controls.stoppedFor());
    }

    @Test
    @DisplayName("a Policy off the list stops it at the wait before its Action")
    void a_policy_off_the_list() {
        for (String policy : List.of("fight", "test-item", "ranged", "shoot")) {
            Run run = new Run();
            ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
            start(run, controls);
            run.land(50, base, by(policy, new Action.Wait()));
            frame(run, controls);
            frame(run, controls);
            assertEquals(List.of(), run.executed, policy);
            assertEquals(1, run.takeOvers, policy);
        }
    }

    @Test
    @DisplayName("a low HP stretch: hit points that fell stop it at the wait where they are seen")
    void a_low_hp_stretch() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        run.land(60, PauseOnTest.withHp(base, 20, 20), exploring());
        frame(run, controls);
        frame(run, controls);
        run.land(61, PauseOnTest.withHp(base, 20, 20), exploring());
        frame(run, controls);
        frame(run, controls);
        run.land(62, PauseOnTest.withHp(base, 16, 20), exploring());
        frame(run, controls);
        frame(run, controls);
        assertEquals(List.of(60L, 61L), run.executed);
        assertEquals("HP fell", controls.stoppedFor());
    }

    @Test
    @DisplayName("a boss floor stops it when the person has that condition on, at the wait they arrive")
    void a_boss_floor() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS.withPauseOn(EnumSet.of(Condition.BOSS_FLOOR)));
        start(run, controls);
        run.land(70, base, exploring());
        frame(run, controls);
        frame(run, controls);
        run.land(71, PauseOnTest.onFloor(base, 5, 0), exploring());
        frame(run, controls);
        frame(run, controls);
        assertEquals(List.of(70L), run.executed);
        assertEquals("on a boss floor", controls.stoppedFor());
    }

    @Test
    @DisplayName("any enabled Pause-on condition ends it, and one that is not enabled does not")
    void pause_on_conditions_end_it() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS.withPauseOn(EnumSet.of(Condition.UNKNOWN_ITEM)));
        start(run, controls);
        run.land(80, base, exploring());
        frame(run, controls);
        frame(run, controls);
        assertEquals(List.of(80L), run.executed, "unknown-item is not about a step");
    }

    @Test
    @DisplayName("a wait the Run dropped as stale and asked again is looked at before it executes, not run on the credit left over")
    void a_stale_re_ask_is_looked_at() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        run.land(120, base, exploring());
        controls.frame(1.0);
        assertEquals(1, run.credits, "the quiet wait was paid for");
        // Before the Run serves it, the screen goes stale: the answer is dropped and the Brain asked afresh, with the
        // credit still out. The new answer is for a screen with a rat on it and is the Brain's walk on.
        run.pending = null;
        run.thinking = true;
        controls.frame(1.0);
        assertEquals(0, run.credits, "the credit is withdrawn while the Brain is deciding again");
        run.thinking = false;
        run.land(120, PauseOnTest.withEnemies(base, "rat"), exploring());
        controls.frame(1.0);
        run.serve();
        assertEquals(List.of(), run.executed, "the re-asked Decision was not run unlooked");
        assertEquals("enemy in view", controls.stoppedFor());
    }

    @Test
    @DisplayName("Pause during a stretch returns control to the person, in HUMAN, and the Decision in hand is not executed")
    void pause_returns_control() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        run.land(90, base, exploring());
        frame(run, controls);
        frame(run, controls);
        run.land(91, base, exploring());
        assertTrue(controls.enabled(Control.PAUSE_RESUME));
        assertTrue(controls.pauseResume());
        assertEquals(1, run.takeOvers);
        assertEquals("you paused", controls.stoppedFor());
        frame(run, controls);
        assertEquals(List.of(90L), run.executed);
        handOver(run, controls);
        assertEquals(Mode.HUMAN, controls.mode());
        assertFalse(controls.autoexploring());
    }

    @Test
    @DisplayName("Take over and Next Step during a stretch are also control coming back; a Brain in trouble too")
    void the_other_ways_back() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        assertTrue(controls.takeOverHandBack());
        assertEquals("you took over", controls.stoppedFor());
        assertEquals(1, run.takeOvers);

        Run second = new Run();
        ControlState again = inHuman(second, OverlaySettings.DEFAULTS);
        start(second, again);
        assertTrue(again.nextStep());
        assertEquals("you paused", again.stoppedFor());

        Run third = new Run();
        ControlState trouble = inHuman(third, OverlaySettings.DEFAULTS);
        start(third, trouble);
        trouble.brainTrouble(1, true);
        assertEquals("the bot could not decide", trouble.stoppedFor());
        assertEquals(1, third.takeOvers);
    }

    @Test
    @DisplayName("Human play speed paces the stretch by its interval, as it paces a RUNNING Run")
    void the_speed_modes_are_the_same() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        assertEquals(SpeedMode.NEXT_STEP, controls.speed());
        start(run, controls);
        assertEquals(SpeedMode.HUMAN_PLAY, controls.speed(), "the speed that waits for a press becomes the readable pace");
        run.land(100, base, exploring());
        controls.frame(0.0);
        assertEquals(List.of("handBack", "advance"), run.calls, "the first credit at once, as Resume gives it");
        run.serve();
        run.land(101, base, exploring());
        controls.frame(0.2);
        assertEquals(1, run.calls.stream().filter("advance"::equals).count(), "no second credit before the interval");
        controls.frame(1.0);
        run.serve();
        assertEquals(List.of(100L, 101L), run.executed);
    }

    @Test
    @DisplayName("a stretch asks nothing new of the Run: only the hand back, credits, and the takeover that ends it")
    void the_log_calls_are_existing_ones() {
        Run run = new Run();
        ControlState controls = inHuman(run, OverlaySettings.DEFAULTS);
        start(run, controls);
        run.land(110, PauseOnTest.withEnemies(base, "rat"), exploring());
        frame(run, controls);
        assertEquals(List.of("handBack", "takeOver"), run.calls);
    }
}
