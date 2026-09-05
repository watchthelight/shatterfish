package org.shatterfish.harness.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.gltextures.SmartTexture;
import com.watabou.gltextures.TextureCache;
import com.watabou.input.GameAction;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scene's other acceptance criteria, and the two row-5 sites story 1.2 could not reach.
 */
class HeadlessSceneTest {

    private static final long SEED = 2_718_281L;
    private static final int FRAME_LIMIT = 2_000;

    private HeadlessBoot boot;
    private HeadlessDriver driver;
    private HeadlessScene scene;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
            scene = null;
        }
    }

    @Test
    @DisplayName("the no-op binding is installed by the boot and required by the scene")
    void the_binding_comes_before_any_texture() {
        boot = HeadlessBoot.ensure();
        assertTrue(NoOpGL.isNoOp(Gdx.gl), "the boot installs the binding before anything else runs");
        assertSame(Gdx.gl, Gdx.gl20);

        GL20 installed = Gdx.gl;
        try {
            Gdx.gl = null;
            assertThrows(IllegalStateException.class, HeadlessScene::new,
                    "a scene must not be constructible without the binding: its create() builds"
                            + " textures, and every texture calls glGenTexture in its constructor");
        } finally {
            Gdx.gl = installed;
        }
    }

    @Test
    @DisplayName("atlases load through the Pixmap path, with no graphics context")
    void atlases_load_without_a_graphics_context() throws Exception {
        startARun();

        SmartTexture tiles = TextureCache.get(Dungeon.level.tilesTex());
        assertNotNull(tiles.bitmap, "the atlas was read from the classpath as a Pixmap");
        assertTrue(tiles.width > 0 && tiles.height > 0, "the atlas has a size: " + tiles.width + "x" + tiles.height);
        assertNotNull(Dungeon.hero.sprite, "the hero's sprite was built from its atlas");
        assertSame(Dungeon.hero, Dungeon.hero.sprite.ch, "and linked itself to the hero (HeroSprite.java:58)");
    }

    @Test
    @DisplayName("the scene is the one the game's statics see")
    void the_scene_is_the_one_the_statics_see() throws Exception {
        startARun();
        stepToTheFirstInputWait();

        // GameScene.add(Mob, float) registers the actor and builds its sprite only when a scene
        // exists (GameScene.java:1153-1162). That is the branch a spawned mob depends on to ever
        // act, and the reason a scene that is not GameScene.scene plays a different game.
        Rat rat = new Rat();
        rat.pos = freeCellBeside(Dungeon.hero.pos);
        GameScene.add(rat);

        assertTrue(Actor.chars().contains(rat), "the mob was added to the actor list");
        assertNotNull(rat.sprite, "the mob got a sprite");
        assertSame(rat, rat.sprite.ch, "linked to it");
    }

    @Test
    @DisplayName("row 5: selectCell and resetKeyHold run their vanilla branches with a scene")
    void the_remaining_row_5_sites_run_their_vanilla_branches() throws Exception {
        startARun();
        stepToTheFirstInputWait();

        // selectCell installs the listener on the real CellSelector; handleCell then reaches it.
        RecordingListener listener = new RecordingListener();
        GameScene.selectCell(listener);
        assertTrue(listener.prompted, "selectCell asked the listener for its prompt, which is the vanilla"
                + " statement the guard encloses");
        GameScene.handleCell(freeCellBeside(Dungeon.hero.pos));
        assertTrue(listener.selected, "the installed listener received the click");
        GameScene.ready();

        // resetKeyHold clears the held actions on the real CellSelector.
        CellSelector cellSelector = privateStatic("cellSelector");
        Field held = CellSelector.class.getDeclaredField("heldAction1");
        held.setAccessible(true);
        held.set(cellSelector, SPDAction.N);
        GameScene.resetKeyHold();
        assertSame(SPDAction.NONE, (GameAction) held.get(cellSelector),
                "resetKeyHold reached the selector and cleared the held action (CellSelector.java:482-483)");
    }

    @Test
    @DisplayName("a turn resolves through the real scene with no renderer")
    void a_turn_resolves_without_a_renderer() throws Exception {
        startARun();
        stepToTheFirstInputWait();
        Hero hero = Dungeon.hero;
        int from = hero.pos;
        float timeBefore = Actor.now();

        GameScene.handleCell(freeCellBeside(from));
        assertNotNull(hero.curAction, "the click became an action");

        long start = scene.stepper().frames();
        while (!(atInputWait(hero) && hero.pos != from)) {
            scene.step();
            assertTrue(scene.stepper().frames() - start < FRAME_LIMIT,
                    "the move did not complete within " + FRAME_LIMIT + " frames: pos=" + hero.pos
                            + " ready=" + hero.ready + " curAction=" + hero.curAction);
        }

        assertTrue(Actor.now() > timeBefore, "the step cost the hero time");
        assertEquals(scene.stepper().frames(), scene.updates(),
                "every frame the stepper stepped was one update of the scene, and nothing else updated it");
        assertFalse(GameScene.showingWindow());
    }

    private void startARun() throws Exception {
        boot = HeadlessBoot.ensure();
        FreshRun.forget();
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        scene = driver.scene();
    }

    private void stepToTheFirstInputWait() {
        HeadlessDriver.Halt halt = driver.stepToInputWait(FRAME_LIMIT);
        assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason(), "the Run stopped for another reason: " + halt);
    }

    private static boolean atInputWait(Hero hero) {
        return hero.ready && hero.curAction == null && !hero.resting;
    }

    private static int freeCellBeside(int cell) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int candidate = cell + offset;
            if (candidate >= 0 && candidate < Dungeon.level.length()
                    && Dungeon.level.passable[candidate] && Actor.findChar(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("no free cell beside " + cell);
    }

    @SuppressWarnings("unchecked")
    private static <T> T privateStatic(String name) throws ReflectiveOperationException {
        Field field = GameScene.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(null);
    }

    private static final class RecordingListener extends CellSelector.Listener {
        private boolean selected;
        private boolean prompted;

        @Override
        public void onSelect(Integer cell) {
            selected = true;
        }

        @Override
        public String prompt() {
            prompted = true;
            return "recording";
        }
    }
}
