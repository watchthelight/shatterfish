package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.harness.agent.ActionContext;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A human-readable label for every {@link Action} kind (story 5.3's review): {@code Action.toString()}
 * ({@code "Step[cell=659]"}) is fine for {@code StrategyLog} and wrong for a screen a person reads
 * (UX-DR13). Every kind {@link Action}'s sealed interface permits is covered here, the same
 * discipline {@code Highlights}'s test holds in {@code brain}.
 */
class ActionTextTest {

    private static final long SEED = 12345;
    private static final long SALT = 0x5A17_5A17L;

    @Test
    @DisplayName("every Action kind reads as a word, with no Observation to add detail")
    void every_kind_without_an_observation() {
        assertEquals("step 42", ActionText.of(new Action.Step(42), (Observation) null));
        assertEquals("move 42", ActionText.of(new Action.MoveTo(42), (Observation) null));
        assertEquals("attack", ActionText.of(new Action.Attack(42), (Observation) null));
        assertEquals("interact", ActionText.of(new Action.Interact(42), (Observation) null));
        assertEquals("pick up", ActionText.of(new Action.PickUp(), (Observation) null));
        assertEquals("open", ActionText.of(new Action.OpenChest(42), (Observation) null));
        assertEquals("buy", ActionText.of(new Action.Buy(42), (Observation) null));
        assertEquals("unlock", ActionText.of(new Action.Unlock(42), (Observation) null));
        assertEquals("descend", ActionText.of(new Action.Descend(), (Observation) null));
        assertEquals("ascend", ActionText.of(new Action.Ascend(), (Observation) null));
        assertEquals("read scroll of identify", ActionText.of(
                new Action.UseItem(new ItemRef(0, "scroll of identify", 1), "read"), (Observation) null));
        assertEquals("read scroll of identify", ActionText.of(
                new Action.UseItemAt(new ItemRef(0, "scroll of identify", 1), "read", 42), (Observation) null));
        assertEquals("upgrade wand of magic missile", ActionText.of(new Action.UseItemOn(
                new ItemRef(0, "scroll of upgrade", 1), "upgrade", new ItemRef(1, "wand of magic missile", 1)),
                (Observation) null));
        assertEquals("rest", ActionText.of(new Action.Rest(true), (Observation) null));
        assertEquals("search", ActionText.of(new Action.Search(), (Observation) null));
        assertEquals("talent: Point-Blank", ActionText.of(new Action.Talent("Point-Blank"), (Observation) null));
        assertEquals("ability: Endure", ActionText.of(new Action.Ability("Endure"), (Observation) null));
        assertEquals("ability: Endure", ActionText.of(new Action.AbilityAt("Endure", 42), (Observation) null));
        assertEquals("answer: 0", ActionText.of(new Action.AnswerPrompt(0), (Observation) null));
        assertEquals("dismiss", ActionText.of(new Action.DismissPrompt(), (Observation) null));
        assertEquals("wait", ActionText.of(new Action.Wait(), (Observation) null));
        assertEquals("none", ActionText.of(null, (Observation) null));
    }

    @Test
    @DisplayName("a Step or a MoveTo reads as the compass direction from the hero's cell, all eight points")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void step_direction() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT)) {
            driver.stepToInputWait();
            Observation observation = new Observer().observe();
            int width = observation.map().width();
            int from = observation.hero().cell();

            assertEquals("step N", ActionText.of(new Action.Step(from - width), observation));
            assertEquals("step S", ActionText.of(new Action.Step(from + width), observation));
            assertEquals("step E", ActionText.of(new Action.Step(from + 1), observation));
            assertEquals("step W", ActionText.of(new Action.Step(from - 1), observation));
            assertEquals("step NE", ActionText.of(new Action.Step(from - width + 1), observation));
            assertEquals("step NW", ActionText.of(new Action.Step(from - width - 1), observation));
            assertEquals("step SE", ActionText.of(new Action.Step(from + width + 1), observation));
            assertEquals("step SW", ActionText.of(new Action.Step(from + width - 1), observation));
            assertEquals("step here", ActionText.of(new Action.Step(from), observation));
            assertEquals("move N", ActionText.of(new Action.MoveTo(from - width), observation));
        }
    }

    @Test
    @DisplayName("an Attack names the enemy in view at the target cell, when one is cheaply found there")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void attack_names_the_target_when_found() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT)) {
            driver.stepToInputWait();
            Observation base = new Observer().observe();
            int hero = base.hero().cell();
            int target = -1;
            for (int cell = 0; cell < base.map().fog().size(); cell++) {
                if (cell != hero && base.map().fog().get(cell) == Fog.VISIBLE) {
                    target = cell;
                    break;
                }
            }
            assertEquals("attack", ActionText.of(new Action.Attack(target), base), "no actor there yet");

            ActorView gnoll = new ActorView(target, "gnoll scout", Alignment.ENEMY, 10, false, Emote.NONE, List.of());
            Observation withEnemy = new Observation(base.header(), base.map(), new ActorsSection(List.of(gnoll)),
                    base.hero(), base.inventory(), base.journal(), base.log(), base.actions(), base.prompt());

            assertEquals("attack gnoll scout", ActionText.of(new Action.Attack(target), withEnemy));
            assertEquals("attack", ActionText.of(new Action.Attack(hero == 0 ? 1 : 0), withEnemy),
                    "a cell with no actor still falls back");
        }
    }

    @Test
    @DisplayName("an AnswerPrompt reads the open Prompt's button label; the index with no Prompt to read")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void answer_prompt_reads_the_option_text() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT)) {
            driver.stepToInputWait();
            Observation base = new Observer().observe();
            assertEquals("answer: 1", ActionText.of(new Action.AnswerPrompt(1), base), "no Prompt open");

            HeaderSection header = base.header();
            HeaderSection withPrompt = new HeaderSection(header.version(), header.upstreamTag(),
                    header.codexVersion(), header.heroClass(), header.challenges(), header.depth(), header.branch(),
                    header.sealed(), header.oracle(), PromptKind.OTHER);
            PromptSection prompt = new PromptSection(PromptKind.OTHER, "Take it?", "There is gold here.",
                    List.of("Take the gold", "Leave it"));
            Observation withOpenPrompt = new Observation(withPrompt, base.map(), base.actors(), base.hero(),
                    base.inventory(), base.journal(), base.log(), base.actions(), prompt);

            assertEquals("answer: Take the gold", ActionText.of(new Action.AnswerPrompt(0), withOpenPrompt));
            assertEquals("answer: Leave it", ActionText.of(new Action.AnswerPrompt(1), withOpenPrompt));
            assertEquals("answer: 2", ActionText.of(new Action.AnswerPrompt(2), withOpenPrompt),
                    "an index the Prompt does not offer falls back to the index itself");
        }
    }

    /**
     * Story 5.4's review round: the Decision log reads every Action through {@code ActionContext}
     * (what {@code EmbeddedRun} captured at the wait) rather than the whole Observation the Decision
     * card reads; this holds that the two routes read the same screen the same way, for every kind
     * this class's own labels depend on the screen for (a Step's direction, an Attack's target, an
     * AnswerPrompt's option), not only that each compiles and returns something.
     */
    @Test
    @DisplayName("of(Action, ActionContext) reads the same labels as of(Action, Observation) built from the same screen")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void action_context_matches_observation() {
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT)) {
            driver.stepToInputWait();
            Observation base = new Observer().observe();
            int width = base.map().width();
            int from = base.hero().cell();
            ActorView gnoll = new ActorView(from - width, "gnoll scout", Alignment.ENEMY, 10, false, Emote.NONE, List.of());
            Observation observation = new Observation(base.header(), base.map(), new ActorsSection(List.of(gnoll)),
                    base.hero(), base.inventory(), base.journal(), base.log(), base.actions(), base.prompt());
            ActionContext context = ActionContext.of(observation);

            List<Action> actions = List.of(new Action.Step(from - width), new Action.Step(from + width + 1),
                    new Action.MoveTo(from - 1), new Action.Attack(from - width), new Action.Attack(from + 1),
                    new Action.Wait(), new Action.Search());
            for (Action action : actions) {
                assertEquals(ActionText.of(action, observation), ActionText.of(action, context), action.toString());
            }
            // Concretely, not only "the same as each other": the compass direction and the named
            // target are the real thing, from both routes, not two routes that happen to agree by
            // both falling back to a raw cell.
            assertEquals("step N", ActionText.of(new Action.Step(from - width), context));
            assertEquals("attack gnoll scout", ActionText.of(new Action.Attack(from - width), context));
        }
    }
}
