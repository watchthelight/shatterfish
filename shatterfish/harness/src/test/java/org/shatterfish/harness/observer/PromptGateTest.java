package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLiquidFlame;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Prompts;
import org.shatterfish.harness.driver.Windows;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prompt section and the gate (ADR-0006, Prompt): a window the game opened on its own is
 * exposed as its kind, the title and text it draws and its buttons' labels, the header carries
 * the kind, and any other window at an Input wait fails every read.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class PromptGateTest {

    private static final long SEED = 22_360_679L;

    private HeadlessDriver driver;
    private Hero hero;

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
        hero = Dungeon.hero;
    }

    @Test
    @DisplayName("with no window in front the prompt is none, and so is the header's kind")
    void no_window() {
        atTheFirstWait();
        Observer observer = new Observer();
        assertNull(Windows.front());
        assertEquals(PromptSection.NONE, observer.prompt());
        assertEquals(PromptKind.NONE, observer.header().prompt());
    }

    @Test
    @DisplayName("the chasm's jump prompt: its kind, title, text and the two labels, as the game draws them")
    void the_chasm_prompt() {
        atTheFirstWait();
        // Chasm.java:57-96 posts the window to the render thread; the driver drains that queue in its loop.
        Chasm.heroJump(hero);
        HeadlessBoot.ensure().drainPostedRunnables();
        assertNotNull(Windows.front(), "the window is in front once the posted runnable ran");

        Observer observer = new Observer();
        PromptSection prompt = observer.prompt();
        assertEquals(PromptKind.CHASM_JUMP, prompt.kind());
        assertEquals(Messages.get(Chasm.class, "chasm"), prompt.title());
        assertEquals(Messages.get(Chasm.class, "jump"), prompt.text());
        assertEquals(List.of(Messages.get(Chasm.class, "yes"), Messages.get(Chasm.class, "no")), prompt.options());
        assertEquals(PromptKind.CHASM_JUMP, observer.header().prompt());

        // Under a Prompt every section reads, and the Observation holds the header to the section.
        Observation observation = Skeleton.everything(observer);
        assertEquals(prompt, observation.prompt());
        assertEquals(observation, Skeleton.everything(new Observer()));
    }

    @Test
    @DisplayName("a known harmful potion asks before it is drunk: the harmful-potion kind with yes and no")
    void the_harmful_potion_prompt() {
        atTheFirstWait();
        PotionOfLiquidFlame potion = new PotionOfLiquidFlame();
        potion.identify();
        assertTrue(potion.collect());
        // Potion.java:238-252: known and among the potions that must be thrown, so the window opens.
        potion.execute(hero, Potion.AC_DRINK);
        assertNotNull(Windows.front());

        PromptSection prompt = new Observer().prompt();
        assertEquals(PromptKind.HARMFUL_POTION, prompt.kind());
        assertEquals(Messages.get(Potion.class, "harmful"), prompt.title());
        assertEquals(Messages.get(Potion.class, "sure_drink"), prompt.text());
        assertEquals(List.of(Messages.get(Potion.class, "yes"), Messages.get(Potion.class, "no")), prompt.options());
    }

    @Test
    @DisplayName("an options window from an origin the table does not name is OTHER, with what it draws")
    void an_options_window_of_unlisted_origin() {
        atTheFirstWait();
        GameScene.show(new WndOptions("A question", "Which way?", "Left", "Right", "Stay") {
            @Override
            protected void onSelect(int index) {
            }
        });
        PromptSection prompt = new Observer().prompt();
        assertEquals(PromptKind.OTHER, prompt.kind());
        assertEquals("A question", prompt.title());
        assertEquals("Which way?", prompt.text());
        assertEquals(List.of("Left", "Right", "Stay"), prompt.options());
        assertEquals(PromptKind.OTHER, Prompts.kind(Windows.front()));
    }

    @Test
    @DisplayName("an options window with no title has an empty title and the message as its text")
    void an_untitled_options_window() {
        atTheFirstWait();
        GameScene.show(new WndOptions((String) null, "Only a message", "Fine") {
            @Override
            protected void onSelect(int index) {
            }
        });
        PromptSection prompt = new Observer().prompt();
        assertEquals("", prompt.title());
        assertEquals("Only a message", prompt.text());
        assertEquals(List.of("Fine"), prompt.options());
    }

    @Test
    @DisplayName("a Prompt in front of a hero who is not waiting under it is not an Input wait")
    void a_prompt_over_a_busy_hero() {
        atTheFirstWait();
        Chasm.heroJump(hero);
        HeadlessBoot.ensure().drainPostedRunnables();
        assertEquals(PromptKind.CHASM_JUMP, new Observer().prompt().kind());
        // Between the chasm's interruption and the next act the hero may still hold an action
        // (Hero.java:1838-1850); the driver confirms no wait then, and neither does the Observer.
        hero.ready = false;
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> new Observer().prompt());
        assertTrue(refused.getMessage().contains("not waiting under it"), refused.getMessage());
        hero.ready = true;
        assertEquals(PromptKind.CHASM_JUMP, new Observer().header().prompt());
    }

    @Test
    @DisplayName("a window that is not a Prompt at an Input wait fails every read, naming the window")
    void not_a_prompt() {
        atTheFirstWait();
        GameScene.show(new WndMessage("The game says something and asks nothing."));
        assertNotNull(Windows.front());
        assertEquals(PromptKind.NONE, Prompts.kind(Windows.front()));
        Observer observer = new Observer();
        for (Runnable read : List.<Runnable>of(observer::prompt, observer::header, observer::map, observer::actors,
                observer::hero, observer::inventory, observer::journal, observer::log)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, read::run);
            assertTrue(refused.getMessage().contains("WndMessage"), refused.getMessage());
            assertTrue(refused.getMessage().contains("not a Prompt"), refused.getMessage());
        }
    }
}
