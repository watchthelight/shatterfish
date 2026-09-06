package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Slow;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.effects.EmoIcon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Emote;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Skeleton.Serialized;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actors section carries what the sprites draw and nothing behind them (ADR-0006, Mobs, Mob
 * state, Mob buffs): a mob is present exactly when its sprite is, an invisible one with its flag;
 * health is the bar's pips, never the value; the only state is the emote the sprite shows, so a
 * mob's AI state, target and seen flag change nothing; buffs are the ones with an icon.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ActorLeakTest {

    private static final long SEED = 14_142_135L;

    private HeadlessDriver driver;
    private Level level;
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
        level = Dungeon.level;
        hero = Dungeon.hero;
        assertFalse(level.mobs.isEmpty(), "the floor has mobs");
    }

    @Test
    @DisplayName("a mob is present exactly when its sprite is drawn: in the hero's field of view")
    void mobs_are_drawn_only_in_view() {
        atTheFirstWait();
        ActorsSection actors = new Observer().actors();
        boolean someoneOutOfView = false;
        for (Mob mob : level.mobs) {
            boolean drawn = level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob);
            assertEquals(drawn, actorAt(actors, mob.pos).isPresent(), mob.name() + " at " + mob.pos
                    + " (GameScene.java:1447; Char.java:1272-1274)");
            someoneOutOfView |= !level.heroFOV[mob.pos];
        }
        assertTrue(someoneOutOfView, "the first floor has a mob the hero cannot see");

        Mob far = mobOutOfView();
        int cell = floorInView();
        far.pos = cell;
        if (far.sprite != null) {
            far.sprite.place(cell);
        }
        ActorView view = actorAt(new Observer().actors(), cell).orElseThrow();
        assertEquals(far.name(), view.name());
        assertEquals(Alignment.valueOf(far.alignment.name()), view.alignment());
        assertEquals(Observer.healthPips(far), view.healthPips());
        assertEquals(hero.pos, new Observer().hero().cell(), "the hero is not an actor; its cell is the hero section's");
        assertTrue(actorAt(new Observer().actors(), hero.pos).isEmpty());
    }

    @Test
    @DisplayName("an invisible mob in view is present, with its flag, since the sprite is drawn faint")
    void an_invisible_mob_is_present_with_its_flag() {
        atTheFirstWait();
        Mob mob = mobInView();
        assertFalse(actorAt(new Observer().actors(), mob.pos).orElseThrow().invisible());
        Buff.affect(mob, Invisibility.class, 10f);
        assertTrue(mob.invisible > 0, "Invisibility.java:46-48");
        ActorView view = actorAt(new Observer().actors(), mob.pos).orElseThrow();
        assertTrue(view.invisible(), "drawn at alpha 0.4 (CharSprite.java:401-407), so present and flagged");
        assertEquals(mob.name(), view.name());
    }

    @Test
    @DisplayName("health is the bar's pips over the greater of maximum and health plus shield, never the value")
    void health_is_pips_never_the_value() {
        atTheFirstWait();
        Mob mob = mobInView();
        assertEquals(mob.HT, mob.HP, "a fresh mob is at full health");
        assertEquals(ObservationCodec.MAX_HEALTH_PIPS, actorAt(new Observer().actors(), mob.pos).orElseThrow().healthPips(),
                "the bar is hidden at full health (CharHealthIndicator.java:55), which reads as full");

        mob.HP = 5;
        Buff.affect(mob, Barrier.class).setShield(5);
        int max = Math.max(mob.HP + mob.shielding(), mob.HT);
        int pips = ObservationCodec.healthPips(5, max);
        assertEquals(pips, actorAt(new Observer().actors(), mob.pos).orElseThrow().healthPips(),
                "HealthBar.level(Char): health over max(health + shield, HT) (HealthBar.java:82-88)");
        String json = Skeleton.around(new Observer().header(), new Observer().map(), new Observer().actors(),
                new Observer().hero()).json();
        String actors = json.substring(json.indexOf("\"actors\":{"));
        actors = actors.substring(0, actors.indexOf("\"header\":"));
        assertFalse(actors.contains("\"hp\"") || actors.contains("\"ht\"") || actors.contains("\"HP\""),
                "an actor carries pips and no health value: " + actors);
        assertTrue(actors.contains("\"healthPips\":" + pips));
    }

    @Test
    @DisplayName("the only AI state is the emote the sprite shows: the state, target and seen flag change nothing")
    void the_only_state_is_the_emote() throws Exception {
        atTheFirstWait();
        Mob mob = mobInView();
        mob.state = mob.SLEEPING;
        driver.step();
        assertTrue(mob.sprite.shatterfishEmote() instanceof EmoIcon.Sleep,
                "a sleeping mob's sprite shows the sleep icon after a frame (MobSprite.java:39; CharSprite.java:635-636)");
        Observer observer = new Observer();
        ActorView asleep = actorAt(observer.actors(), mob.pos).orElseThrow();
        assertEquals(Emote.SLEEP, asleep.emote());

        // The state, the target and the seen flag change with no frame drawn: the icon stays, and
        // so does the Observation.
        mob.state = mob.HUNTING;
        Field target = Mob.class.getDeclaredField("target");
        target.setAccessible(true);
        target.setInt(mob, hero.pos);
        Field enemySeen = Mob.class.getDeclaredField("enemySeen");
        enemySeen.setAccessible(true);
        enemySeen.setBoolean(mob, true);
        ActorView hunting = actorAt(observer.actors(), mob.pos).orElseThrow();
        assertEquals(asleep, hunting, "Mob.state, Mob.target and Mob.enemySeen are not in the Observation");
        Serialized serialized = Serialized.of(Skeleton.around(observer.header(), observer.map(), observer.actors(),
                observer.hero()));
        for (String hidden : List.of("Sleeping", "Hunting", "Wandering", "Fleeing", "Passive", "target", "enemySeen",
                "alerted")) {
            serialized.assertAbsent(hidden);
        }

        // The icon changes when the sprite's does.
        mob.sprite.showAlert();
        assertEquals(Emote.ALERT, actorAt(observer.actors(), mob.pos).orElseThrow().emote());
        mob.sprite.hideAlert();
        assertEquals(Emote.NONE, actorAt(observer.actors(), mob.pos).orElseThrow().emote());
    }

    @Test
    @DisplayName("a mob's buffs are the ones with an icon, a flavour buff with its turns")
    void buffs_with_icons_only() {
        atTheFirstWait();
        Mob mob = mobInView();
        Buff.affect(mob, Slow.class, 10f);
        Buff.affect(mob, Poison.class).set(4f);
        Buff plain = Buff.affect(mob, Buff.class);
        assertEquals(BuffIndicator.NONE, plain.icon(), "a plain buff has no icon (Buff.java:94-96)");

        List<BuffView> views = actorAt(new Observer().actors(), mob.pos).orElseThrow().buffs();
        List<String> expected = new ArrayList<>();
        for (Buff buff : mob.buffs()) {
            if (buff.icon() != BuffIndicator.NONE) {
                expected.add(buff.name());
            }
        }
        assertEquals(expected.stream().sorted().toList(), views.stream().map(BuffView::name).toList(),
                "every buff with an icon, as WndInfoMob's row shows them (WndInfoMob.java:63-64, :80), and no other");
        Slow slow = mob.buff(Slow.class);
        assertTrue(views.contains(new BuffView(slow.name(), true, Math.round(slow.visualcooldown() * 100f))),
                "a flavour buff shows its visual cooldown to two decimals (FlavourBuff.java:35-42)");
        Poison poison = mob.buff(Poison.class);
        assertTrue(views.contains(new BuffView(poison.name(), false, 0)), "a buff that is not a flavour buff shows no turns");
        assertFalse(views.stream().anyMatch(v -> v.name().equals(plain.name())));
    }

    private Mob mobInView() {
        for (Mob mob : level.mobs) {
            if (level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob) && mob.sprite != null) {
                return mob;
            }
        }
        Mob far = mobOutOfView();
        int cell = floorInView();
        far.pos = cell;
        far.sprite.place(cell);
        return far;
    }

    private Mob mobOutOfView() {
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && !Observer.hiddenMimic(mob) && mob.sprite != null) {
                return mob;
            }
        }
        throw new AssertionError("no mob out of view");
    }

    private int floorInView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && Actor.findChar(cell) == null && level.heaps.get(cell, null) == null) {
                return cell;
            }
        }
        throw new AssertionError("no free floor in view");
    }

    private static Optional<ActorView> actorAt(ActorsSection actors, int cell) {
        return actors.actors().stream().filter(a -> a.cell() == cell).findFirst();
    }

    static {
        // Referenced so an unused-import check cannot drop it: the seen flag's owner.
        assertNotEquals(null, Mob.class);
    }
}
