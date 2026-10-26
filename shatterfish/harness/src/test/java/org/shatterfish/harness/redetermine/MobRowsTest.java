package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Emote;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a mob in view keeps beyond the screen (fairness re-review, 6.2): its buffs without an icon go, its ability
 * cooldowns are reset, and a health bar that no hit points fit is refused rather than leaving the true ones.
 */
class MobRowsTest {

    private static final String BUFFS = "com.shatteredpixel.shatteredpixeldungeon.actors.buffs.";

    private HeadlessDriver driver;

    @AfterEach
    void end() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static JSONObject buff(String name) {
        return new JSONObject().put("__className", BUFFS + name);
    }

    @Test
    @DisplayName("a mob in view loses its iconless buffs and its rolled cooldowns, and keeps the buffs the screen lists")
    void iconless_buffs_and_cooldowns_go() {
        driver = HeadlessDriver.start(3343871708117L, HeroClass.WARRIOR, 1L);
        driver.stepToInputWait();
        JSONObject mob = new JSONObject()
                .put("beamCooldown", 5)
                .put("vent_cooldown", 17)
                .put("leap_cd", 3)
                .put("HP", 4)
                .put("buffs", new JSONArray().put(buff("Poison")).put(buff("Awareness")));
        MobRows.hidden(mob);
        assertEquals(0, mob.getInt("beamCooldown"), "the beam is ready");
        assertEquals(0, mob.getInt("vent_cooldown"));
        assertEquals(0, mob.getInt("leap_cd"));
        assertEquals(4, mob.getInt("HP"), "nothing else is touched");
        List<String> kept = Ctx.objects(mob.getJSONArray("buffs")).stream().map(Ctx::className).toList();
        assertEquals(List.of(BUFFS + "Poison"), kept, "the poison the screen lists stays, the awareness it does not is gone");
    }

    @Test
    @DisplayName("a health bar that no hit points fit is refused, not left with the true hit points")
    void a_bar_no_hit_points_fit_is_refused() {
        JSONObject mob = new JSONObject().put("HT", 10).put("HP", 7);
        ActorView impossible = new ActorView(5, "rat", Alignment.ENEMY, 0, false, Emote.NONE, List.of());
        Scrub.Refused refused = assertThrows(Scrub.Refused.class, () -> MobRows.health(mob, impossible, new SplittableRandom(1)));
        assertTrue(refused.getMessage().contains("health bar"), refused.getMessage());
        assertEquals(7, mob.getInt("HP"), "the refusal changed nothing");
    }
}
